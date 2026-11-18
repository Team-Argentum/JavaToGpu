package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.codegen.opencl.EmitOptions;
import com.sixik.gpu.codegen.opencl.OpenClEmitter;
import com.sixik.gpu.codegen.opencl.Program;
import com.sixik.gpu.codegen.opencl.Target;
import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import com.sixik.gpu.tools.IrDump;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Шаг B4 (FRONTEND_ASM_RUNTIME.md, 18.7): {@link AsmJit} строит один модуль для всех точек входа - каждая функция
 * один раз, {@code kernelId} одиночного ядра не меняется, генератор печатает модуль одной программой.
 */
class AsmJitProgramTest {

    private static final ClassSource LOADER = ClassSource.of(AsmJitProgramTest.class.getClassLoader());

    private static final String SHARED = """
            package p;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.DeviceFunction;
            import com.sixik.gpu.api.annotations.Global;
            import com.sixik.gpu.api.annotations.Kernel;
            import com.sixik.gpu.api.enums.InlinePolicy;

            public class Shared {
                @DeviceFunction(inline = InlinePolicy.NEVER)
                static int sq(int x) {
                    return x * x;
                }

                @DeviceFunction
                static int inc(int x) {
                    return x + 1;
                }

                @Kernel
                public static void a(@Global int[] v) {
                    int i = Gpu.getGlobalId(0);
                    v[i] = sq(v[i]);
                }

                @Kernel
                public static void b(@Global int[] v) {
                    int i = Gpu.getGlobalId(0);
                    v[i] = inc(sq(v[i]));
                }

                @Kernel
                public static void c(@Global int[] v) {
                    int i = Gpu.getGlobalId(0);
                    v[i] = inc(i) + sq(i);
                }
            }
            """;

    private static final List<String> NAMES = List.of("a", "b", "c");

    private static int count(IrModule m, int kind) {
        int n = 0;
        for (int f = 0; f < m.functionCount(); f++) {
            n += m.fKind(f) == kind ? 1 : 0;
        }
        return n;
    }

    private static String run(IrModule m, int function, NdRange range, Object[] args) {
        Result<Interpreter.Execution> r = Interpreter.run(m, function, range, Interpreter.Options.DEFAULT, args);
        return r.isOk() ? "ok" : r.diagnostics().toString();
    }

    /**
     * Критерий B4: три ядра с общей функцией - одна копия функции в модуле и в исходнике, {@code noinline} в выводе,
     * {@code kernelId} каждого ядра - тот же, что у одиночного; IR одиночных совпадает с {@code frontend-java}
     * (политика {@code inline} - тоже).
     */
    @Test
    void threeKernelsShareOneCopyOfAFunction() {
        JavacHarness.Outcome jv = JavacHarness.compile(true, null, SHARED);
        assertTrue(jv.success(), jv.log());
        List<AsmJit.Entry> entries = NAMES.stream().map(n -> AsmJit.Entry.of("p/Shared", n, "([I)V")).toList();
        AsmJit.Result r = AsmJit.compile(new AsmJit.Unit(ClassSource.chain(ClassSource.of(jv.classes()), LOADER),
                null, entries), AsmJit.Options.DEFAULT);
        assertTrue(r.ok(), r.diagnostics().toString());
        IrModule m = r.module();
        assertEquals(3, count(m, IrModule.KERNEL));
        assertEquals(2, count(m, IrModule.DEVICE), "sq and inc, once each");
        int never = 0;
        for (int f = 0; f < m.functionCount(); f++) {
            never += m.fInline(f) == IrModule.INLINE_NEVER ? 1 : 0;
        }
        assertEquals(1, never, "@DeviceFunction(inline = NEVER) of sq");
        for (int i = 0; i < NAMES.size(); i++) {
            AsmJit.Kernel k = r.kernels().get(i);
            IrModule expected = jv.module("p.Shared", NAMES.get(i));
            assertEquals(IrDump.dump(expected, false), IrDump.dump(k.module(), false), NAMES.get(i));
            assertEquals(IrBinary.kernelId(expected), k.kernelId(), NAMES.get(i) + ": kernelId of the single kernel");
            assertEquals(IrModule.KERNEL, m.fKind(k.function()));
            // общий модуль исполняется как одиночный
            int[] x = {3, 4, 5, 6};
            int[] y = x.clone();
            assertEquals("ok", run(m, k.function(), NdRange.of1D(4), new Object[]{x}));
            assertEquals("ok", run(k.module(), k.module().functionCount() - 1, NdRange.of1D(4), new Object[]{y}));
            assertEquals(java.util.Arrays.toString(y), java.util.Arrays.toString(x), NAMES.get(i));
        }
        Result<Program> p = OpenClEmitter.emitProgram(m, Target.GENERIC, EmitOptions.RELEASE);
        assertTrue(p.isOk(), p.diagnostics().toString());
        String src = p.value().source();
        assertEquals(3, p.value().kernels().size());
        assertEquals(1, src.split("__attribute__\\(\\(noinline\\)\\)", -1).length - 1, src);
        assertEquals(3, src.split("__kernel void ", -1).length - 1, src);
    }

    private static String desc(SampleKernels.Kernel k) {
        return MethodType.methodType(k.method().getReturnType(), k.method().getParameterTypes())
                .toMethodDescriptorString();
    }

    /**
     * Корпус 17.14: все ядра класса - одна единица. {@code kernelId} каждого равен одиночному, функция общего модуля
     * исполняется интерпретатором как одиночный модуль, генератор печатает модуль программой.
     */
    @Test
    void corpusClassesBuildOneModuleEach() {
        Map<Class<?>, List<SampleKernels.Kernel>> byClass = new LinkedHashMap<>();
        for (SampleKernels.Kernel k : SampleKernels.all()) {
            byClass.computeIfAbsent(k.owner(), c -> new ArrayList<>()).add(k);
        }
        int checked = 0;
        for (Map.Entry<Class<?>, List<SampleKernels.Kernel>> e : byClass.entrySet()) {
            String owner = e.getKey().getName().replace('.', '/');
            List<AsmJit.Entry> entries = e.getValue().stream()
                    .map(k -> AsmJit.Entry.of(owner, k.method().getName(), desc(k))).toList();
            AsmJit.Result r = AsmJit.compile(new AsmJit.Unit(LOADER, null, entries), AsmJit.Options.DEFAULT);
            assertTrue(r.ok(), owner + ": " + r.diagnostics());
            assertEquals(entries.size(), r.kernels().size(), owner);
            for (int i = 0; i < entries.size(); i++) {
                SampleKernels.Kernel sk = e.getValue().get(i);
                AsmJit.Kernel k = r.kernels().get(i);
                AsmJit.Result single = AsmJit.compile(new AsmJit.Unit(LOADER, null, List.of(entries.get(i))),
                        AsmJit.Options.DEFAULT);
                assertEquals(single.kernels().get(0).kernelId(), k.kernelId(), sk.label());
                NdRange range = CorpusTest.RANGES.getOrDefault(sk.label(), NdRange.of1D(16, 16));
                Object[] a = CorpusTest.args(sk, 7);
                Object[] b = CorpusTest.args(sk, 7);
                assertEquals(run(k.module(), k.module().functionCount() - 1, range, b),
                        run(r.module(), k.function(), range, a), sk.label());
                assertTrue(CorpusTest.same(b, a), sk.label() + ": arguments after the run differ");
                checked++;
            }
            Result<Program> p = OpenClEmitter.emitProgram(r.module(), Target.GENERIC, EmitOptions.RELEASE);
            assertTrue(p.isOk() || p.diagnostics().stream().allMatch(d -> d.code().startsWith("CG-U")),
                    owner + ": " + p.diagnostics());
        }
        assertEquals(SampleKernels.all().size(), checked);
    }
}
