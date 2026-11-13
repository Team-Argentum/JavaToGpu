package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.interpreter.samples.SampleArgs;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import com.sixik.gpu.ir.optimizer.OptimizeOptions;
import com.sixik.gpu.ir.optimizer.OptimizeResult;
import com.sixik.gpu.ir.optimizer.Optimizer;
import com.sixik.gpu.tools.IrDump;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Критерий приёмки 17.14 на корпусе образцов {@code ir-interpreter}: их class-файлы собраны javac с процессором
 * {@code frontend-java}, IR_java лежит в ресурсах, а {@code frontend-asm} строит IR_asm из тех же class-файлов.
 *
 * <p>Шаги A2-A4 (17.15): прямолинейные ядра, управление потоком и вызовы device-функций. Остальные ядра корпуса пока дают
 * {@code GPU0902} «not supported by frontend-asm yet» - тест следит, чтобы ни одно допустимое ядро не получило другую
 * ошибку или неверный IR.</p>
 */
class CorpusTest {

    /** Ядра корпуса без объектов (A2 - прямолинейные, A3 - с управлением потоком, A4 - с device-функциями). */
    private static final Set<String> BUILT = Set.of(
            "ControlKernels.parameters", "FloatKernels.fromIntegers", "IndexKernels.grouped", "IndexKernels.sine",
            "LocalKernels.transpose", "MemoryKernels.aliasing", "MemoryKernels.lengths",
            "SmallTypeKernels.compound",
            "ControlKernels.earlyReturn", "ControlKernels.loops", "FloatKernels.doubleOps", "FloatKernels.floatOps",
            "IndexKernels.ids", "IntKernels.ops", "LocalKernels.scan", "LongKernels.ops", "MemoryKernels.prefixSum",
            "SmallTypeKernels.storage",
            "ControlKernels.effects", "LocalKernels.reduceSum", "MemoryKernels.helpers", "MemoryKernels.manyCalls",
            "MemoryKernels.privateArrays");

    /**
     * Пункт (д) закрытого списка 17.14-3: IR отличается только константами, которые свернул javac ({@code c /= 3} -
     * в байткоде уже {@code 3.0f}, {@code (byte) 200} - уже {@code -56}, у {@code long}-элемента
     * {@code x < y ? 1 : 0} ветки уже {@code lconst_1/lconst_0}, {@code f[r] * 10} - уже {@code 10.0f}), а
     * {@code frontend-java} строит {@code convert}
     * константы или {@code convert(select)}. После оптимизатора (свёртка {@code convert(const)} и
     * {@code convert(select(c, k1, k2))}) IR совпадает.
     */
    private static final Set<String> JAVAC_FOLDED = Set.of("ControlKernels.parameters", "SmallTypeKernels.compound",
            "LongKernels.ops", "MemoryKernels.privateArrays");

    /**
     * Пункт (г) закрытого списка 17.14-3: локаль без инициализатора ({@code int c1;}) в байткоде не видна до первой
     * записи, а {@code frontend-java} объявляет её нулём в месте объявления; у {@code effects} к этому добавляется (д).
     * IR отличается, поведение (17.14-2) проверяет {@link #builtKernelsBehaveLikeFrontendJava}.
     */
    private static final Set<String> UNINITIALIZED_DECLS = Set.of("ControlKernels.effects");

    /**
     * Запуски, которых требуют сами ядра (как в тестах {@code ir-interpreter}): {@code reqd_work_group_size},
     * длины массивов, один work-item для ядер, пишущих в общую ячейку. Остальные - {@code of1D(16, 16)}: массивы
     * {@link SampleArgs} рассчитаны на 16-24 work-item'а.
     */
    private static final Map<String, NdRange> RANGES = Map.of(
            "IndexKernels.grouped", NdRange.of2D(8, 6),
            "LocalKernels.transpose", NdRange.of2D(24, 16),
            "MemoryKernels.lengths", NdRange.of1D(3),
            "SmallTypeKernels.compound", NdRange.of1D(1),
            "ControlKernels.parameters", NdRange.of1D(1),
            "MemoryKernels.manyCalls", NdRange.of1D(2),
            "LocalKernels.reduceSum", NdRange.of1D(128, 64));

    private record Pair(SampleKernels.Kernel java, AsmFrontend.Kernel asm, List<AsmDiagnostic> diags) {
    }

    private static Pair build(SampleKernels.Kernel k) {
        String desc = MethodType.methodType(k.method().getReturnType(), k.method().getParameterTypes())
                .toMethodDescriptorString();
        AsmFrontend.Result r = AsmFrontend.compile(ClassSource.of(CorpusTest.class.getClassLoader()),
                List.of(k.owner().getName()), AsmFrontend.Options.DEFAULT);
        AsmFrontend.Kernel mine = r.kernels().stream()
                .filter(x -> x.entry().method().equals(k.method().getName()) && x.entry().descriptor().equals(desc))
                .findFirst().orElse(null);
        List<AsmDiagnostic> diags = r.diagnostics().stream()
                .filter(d -> d.method().equals(k.method().getName())).toList();
        return new Pair(k, mine, diags);
    }

    private static List<Pair> corpus() {
        List<Pair> r = new ArrayList<>();
        for (SampleKernels.Kernel k : SampleKernels.all()) {
            r.add(build(k));
        }
        return r;
    }

    @Test
    void builtKernelsGiveTheSameIr() {
        List<String> built = new ArrayList<>();
        for (Pair p : corpus()) {
            String label = p.java().label();
            if (!BUILT.contains(label)) {
                continue;
            }
            assertTrue(p.asm() != null, label + ": " + p.diags());
            built.add(label);
            if (JAVAC_FOLDED.contains(label)) {
                continue;
            }
            if (UNINITIALIZED_DECLS.contains(label)) {
                assertNotEquals(IrDump.dump(p.java().module(), false), IrDump.dump(p.asm().module(), false),
                        label + ": the IR is the same, the kernel must leave UNINITIALIZED_DECLS");
                continue;
            }
            assertEquals(IrDump.dump(p.java().module(), false), IrDump.dump(p.asm().module(), false), label);
            assertEquals(IrBinary.kernelId(p.java().module()), p.asm().entry().kernelId(), label + ": kernelId");
        }
        assertEquals(BUILT.size(), built.size(), "every expected kernel is in the corpus: " + built);
    }

    /** 17.14-3 (д): расхождение есть и целиком снимается свёрткой констант. */
    @Test
    void javacFoldedConstantsAreTheOnlyDifference() {
        int seen = 0;
        for (Pair p : corpus()) {
            String label = p.java().label();
            if (!JAVAC_FOLDED.contains(label)) {
                continue;
            }
            assertTrue(p.asm() != null, label + ": " + p.diags());
            seen++;
            assertNotEquals(IrDump.dump(p.java().module(), false), IrDump.dump(p.asm().module(), false),
                    label + ": the IR is the same, the kernel must leave JAVAC_FOLDED");
            assertEquals(IrDump.dump(optimized(p.java().module()), false), IrDump.dump(optimized(p.asm().module()), false),
                    label + ": after constant folding");
        }
        assertEquals(JAVAC_FOLDED.size(), seen);
    }

    private static IrModule optimized(IrModule m) {
        OptimizeResult r = Optimizer.optimize(m, OptimizeOptions.DEFAULT);
        assertTrue(r.ok(), r.failures().toString());
        // оптимизатор не перевыпускает модуль, который не изменил: порядок узлов выравнивается отдельно
        return Optimizer.canonical(r.module());
    }

    @Test
    void everyCorpusKernelIsBuiltOrNotSupportedYet() {
        for (Pair p : corpus()) {
            if (p.asm() != null) {
                continue;
            }
            assertFalse(p.diags().isEmpty(), p.java().label());
            for (AsmDiagnostic d : p.diags()) {
                // корпус - допустимые ядра: единственная причина отказа - конструкция следующего шага 17.15
                assertEquals("GPU0902", d.code(), p.java().label() + ": " + d);
                assertTrue(d.message().contains("is not supported by frontend-asm yet"), p.java().label() + ": " + d);
            }
        }
    }

    /** 17.14-2: IR_asm исполняется интерпретатором побитово так же, как IR_java, на одних и тех же входах. */
    @Test
    void builtKernelsBehaveLikeFrontendJava() {
        int checked = 0;
        for (Pair p : corpus()) {
            if (p.asm() == null) {
                continue;
            }
            for (long seed : new long[]{1, 42}) {
                Object[] a = args(p.java(), seed);
                Object[] j = args(p.java(), seed);
                NdRange range = RANGES.getOrDefault(p.java().label(), NdRange.of1D(16, 16));
                String ra = outcome(p.asm().module(), range, a);
                String rj = outcome(p.java().module(), range, j);
                assertEquals(rj, ra, p.java().label() + ": interpreter outcome");
                if (BUILT.contains(p.java().label())) {
                    assertEquals("ok", rj, p.java().label() + ": the comparison must run the kernel, not two failures");
                }
                assertTrue(Arrays.deepEquals(j, a), p.java().label() + ": arguments after the run differ (seed " + seed + ")");
            }
            checked++;
        }
        assertTrue(checked >= BUILT.size(), "checked " + checked);
    }

    /**
     * Случайные аргументы; у {@code transpose} ширина и высота должны совпадать с запуском, у {@code reduceSum} вход -
     * по элементу на work-item двух групп по 64.
     */
    private static Object[] args(SampleKernels.Kernel k, long seed) {
        Object[] a = SampleArgs.random(k, seed).get();
        if (k.label().equals("LocalKernels.transpose")) {
            a[2] = 24;
            a[3] = 16;
        }
        if (k.label().equals("LocalKernels.reduceSum")) {
            java.util.Random rnd = new java.util.Random(seed);
            int[] in = new int[128];
            for (int i = 0; i < in.length; i++) {
                in[i] = rnd.nextInt();
            }
            a[0] = in;
            a[1] = new int[2];
        }
        return a;
    }

    private static String outcome(IrModule m, NdRange range, Object[] args) {
        Result<Interpreter.Execution> r = Interpreter.run(m, range, args);
        if (r.isOk()) {
            return "ok";
        }
        return r.diagnostics().stream().map(Diagnostic::code).toList().toString();
    }
}
