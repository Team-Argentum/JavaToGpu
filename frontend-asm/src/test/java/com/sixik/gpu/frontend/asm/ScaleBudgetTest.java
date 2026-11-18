package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import com.sixik.gpu.ir.optimizer.OptimizeOptions;
import com.sixik.gpu.ir.optimizer.OptimizeResult;
import com.sixik.gpu.ir.optimizer.Optimizer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;

import java.lang.invoke.MethodType;
import java.time.Duration;
import java.util.List;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Критерии шага B7 (FRONTEND_ASM_RUNTIME.md, 18.7 и S9): роутер из тысяч узлов укладывается в бюджет фронтенда и
 * оптимизатора, два прогона дают побайтово тот же IR и тот же {@code moduleId}, вложенность глубже предела javac
 * компилируется и исполняется верно.
 */
class ScaleBudgetTest {

    private static final float[] INPUT = {0.3f, 5.7f, 100.2f, -1f};

    private static AsmJit.Result jit(ClassNode cn) {
        AsmJit.Result r = AsmJit.compile(GeneratedShapes.unit(cn), GeneratedShapes.SYNTH);
        assertTrue(r.ok(), r.diagnostics().toString());
        return r;
    }

    private static float[] run(IrModule m) {
        float[] out = new float[INPUT.length];
        Result<Interpreter.Execution> r = Interpreter.run(m, m.functionCount() - 1, NdRange.of1D(INPUT.length),
                Interpreter.Options.DEFAULT, new Object[]{out, INPUT.clone()});
        assertTrue(r.isOk(), r.diagnostics().toString());
        return out;
    }

    /**
     * Критерий B7: роутер из 4096 узлов (выбор по диапазону, вложенный {@code Select}, интринсики) проходит фронтенд и
     * оптимизатор в бюджет по умолчанию (2 с) без понижения уровня. Первый прогон прогревает JIT - так в GA2: при
     * загрузке мира компилируется не одна единица.
     */
    @Test
    void routerOfThousandsOfNodesFitsTheBudget() {
        Optimizer.optimize(jit(GeneratedShapes.router(4096)).module(), OptimizeOptions.DEFAULT);
        long t0 = System.nanoTime();
        AsmJit.Result r = jit(GeneratedShapes.router(4096));
        Duration rest = r.remaining(GeneratedShapes.SYNTH);
        OptimizeResult o = Optimizer.optimize(r.module(), OptimizeOptions.DEFAULT.withTimeBudget(rest));
        Duration total = Duration.ofNanos(System.nanoTime() - t0);
        assertTrue(r.diagnostics().isEmpty(), r.diagnostics().toString());
        assertEquals(2, o.level(), "the optimizer fell back: " + o.failures());
        assertTrue(total.compareTo(AsmJit.Options.DEFAULT_BUDGET) < 0, "frontend " + r.elapsed().toMillis()
                + " ms + optimizer: " + total.toMillis() + " ms in total");
        assertArrayEquals(run(r.module()), run(o.module()));
    }

    /** Детерминизм (S9-3): два прогона по свежим деревьям классов - побайтово тот же IR, тот же {@code moduleId}. */
    @Test
    void twoRunsGiveTheSameModuleId() {
        List<IntFunction<ClassNode>> shapes = List.of(GeneratedShapes::router, GeneratedShapes::nestedSelect,
                GeneratedShapes::nestedIf, GeneratedShapes::nestedLoops);
        for (IntFunction<ClassNode> shape : shapes) {
            AsmJit.Result a = jit(shape.apply(300));
            AsmJit.Result b = jit(shape.apply(300));
            assertNotNull(a.moduleId());
            assertEquals(a.moduleId(), b.moduleId());
            assertArrayEquals(IrBinary.write(a.module()), IrBinary.write(b.module()));
            assertEquals(a.kernels().get(0).kernelId(), b.kernels().get(0).kernelId());
            IrModule oa = Optimizer.optimize(a.module(), OptimizeOptions.DEFAULT.withTimeBudget(null)).module();
            IrModule ob = Optimizer.optimize(b.module(), OptimizeOptions.DEFAULT.withTimeBudget(null)).module();
            assertArrayEquals(IrBinary.write(oa), IrBinary.write(ob), "optimized modules differ");
        }
    }

    private static String desc(SampleKernels.Kernel k) {
        return MethodType.methodType(k.method().getReturnType(), k.method().getParameterTypes())
                .toMethodDescriptorString();
    }

    /** То же для всего корпуса 17.14 одной единицей: общий модуль и {@code moduleId} не зависят от прогона. */
    @Test
    void corpusModuleIdIsStable() {
        ClassSource loader = ClassSource.of(ScaleBudgetTest.class.getClassLoader());
        List<AsmJit.Entry> entries = SampleKernels.all().stream()
                .map(k -> AsmJit.Entry.of(k.owner().getName().replace('.', '/'), k.method().getName(), desc(k)))
                .toList();
        AsmJit.Result a = AsmJit.compile(new AsmJit.Unit(loader, null, entries), AsmJit.Options.DEFAULT);
        AsmJit.Result b = AsmJit.compile(new AsmJit.Unit(loader, null, entries), AsmJit.Options.DEFAULT);
        assertTrue(a.ok(), a.diagnostics().toString());
        assertEquals(a.moduleId(), b.moduleId());
        assertArrayEquals(IrBinary.write(a.module()), IrBinary.write(b.module()));
    }

    /** {@code x > 0.5 ? (x > 1.5 ? (...) : x + 1) : x + 0}: первое ложное сравнение {@code d} даёт {@code x + d}. */
    private static float select(float x, int depth) {
        for (int d = 0; d < depth; d++) {
            if (!(x > d + 0.5f)) {
                return x + (float) d;
            }
        }
        return x * 0.5f;
    }

    /** Вложенные {@code if}: истина - {@code x = x * 0.5 + 1} и уровень глубже, ложь - {@code x = x - d} и выход. */
    private static float ifs(float x, int depth) {
        for (int d = 0; d < depth; d++) {
            if (x > d + 0.5f) {
                x = x * 0.5f + 1f;
            } else {
                return x - d;
            }
        }
        return x;
    }

    private static float[] expected(java.util.function.DoubleUnaryOperator f) {
        float[] r = new float[INPUT.length];
        for (int i = 0; i < r.length; i++) {
            r[i] = (float) f.applyAsDouble(INPUT[i]);
        }
        return r;
    }

    /**
     * Вложенность глубже, чем javac собирает (512) и чем выдерживает стек потока по умолчанию: фронтенд,
     * оптимизатор и интерпретатор идут на потоке {@code DeepStack}. Результат равен эталону на Java.
     */
    @Test
    void nestingDeeperThanJavacCompilesAndRuns() {
        int depth = 2048;
        AsmJit.Result s = jit(GeneratedShapes.nestedSelect(depth));
        float[] es = expected(x -> select((float) x, depth));
        assertArrayEquals(es, run(s.module()));
        assertArrayEquals(es, run(Optimizer.optimize(s.module(), OptimizeOptions.DEFAULT.withTimeBudget(null)).module()));

        int ifDepth = 1024;
        AsmJit.Result i = jit(GeneratedShapes.nestedIf(ifDepth));
        assertArrayEquals(expected(x -> ifs((float) x, ifDepth)), run(i.module()));

        AsmJit.Result w = jit(GeneratedShapes.nestedLoops(1024));
        float[] plusOne = expected(x -> (float) x + 1f);
        assertArrayEquals(plusOne, run(w.module()));
        assertArrayEquals(plusOne, run(Optimizer.optimize(w.module(), OptimizeOptions.DEFAULT.withTimeBudget(null))
                .module()));
    }
}
