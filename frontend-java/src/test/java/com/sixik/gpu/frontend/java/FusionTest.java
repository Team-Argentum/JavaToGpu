package com.sixik.gpu.frontend.java;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.optimizer.Fusion;
import com.sixik.gpu.ir.optimizer.Reductions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Fusion} на IR из Java-кода: ядро-производитель подставляется в свёртку, и слитая свёртка (на интерпретаторе, в
 * параллельной форме) даёт то же, что производитель, записавший массив, и последовательная свёртка по нему.
 */
class FusionTest {

    private static final String HEAD = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.*;
            import com.sixik.gpu.api.types.*;

            public final class F {
            """;

    private static final String PRODUCER = """
            @DeviceFunction
            static float wave(int i, int seed) {
                int h = (i * 747796405 + seed) ^ (i >>> 7);
                return (h & 1023) / 512f - 1f;
            }

            @Kernel
            public static void produce(GpuFloatArray out, GpuIntArray base, int seed) {
                int i = Gpu.getGlobalId(0);
                int b = base.get(i % base.length());
                out.set(i, wave(i, seed) + b * 0.25f);
            }
            """;

    private static final String POSITIVES = """
            @Reduction
            public static int positives(int bias, GpuFloatArray v) {
                int count = bias;
                for (int i = 0; i < v.length(); i++) {
                    if (v.get(i) > 0f) {
                        count++;
                    }
                }
                return count;
            }
            """;

    private static IrModule module(String members) {
        FrontendHarness.Outcome r = FrontendHarness.compile(HEAD + members + "\n}\n");
        assertTrue(r.success(), r.describe());
        return r.module();
    }

    @Test
    void producerIsRecognized() {
        assertEquals(Optional.of(new Fusion.Producer(0)), Fusion.producer(module(PRODUCER)));
        assertTrue(Fusion.producer(module(POSITIVES)).isEmpty(), "a reduction is not a producer");
    }

    @Test
    void fusedReductionEqualsProducerThenReduction() {
        IrModule producer = module(PRODUCER);
        IrModule reduction = module(POSITIVES);
        Result<IrModule> fused = Fusion.fuse(producer, reduction, 1);
        assertTrue(fused.isOk(), String.valueOf(fused.diagnostics()));
        int[] base = {-3, 1, 4, -1, 5};
        for (int n : new int[]{1, 7, 300, 1000}) {
            for (int seed : new int[]{0, 12345}) {
                float[] out = new float[n];
                assertTrue(Interpreter.run(producer, NdRange.of1D(n), new Object[]{out, base, seed}).isOk());
                int want = 3;
                for (float x : out) {
                    want += x > 0f ? 1 : 0;
                }
                // параметры слитой свёртки: bias, затем входы производителя (base, seed), затем длина
                assertEquals(want, parallel(fused.value(), 3, new Object[]{3, base, seed, n}), "n=" + n);
            }
        }
    }

    /** Слитая свёртка в параллельной форме на интерпретаторе: 3 группы по 16, свёртка сумм групп. */
    private static int parallel(IrModule fused, int groups, Object[] args) {
        Result<Reductions.Lowered> l = Reductions.lower(fused, 16);
        assertTrue(l.isOk(), String.valueOf(l.diagnostics()));
        Object[] full = Arrays.copyOf(args, args.length + 1);
        int[] partial = new int[groups];
        full[l.value().partialParam()] = partial;
        var run = Interpreter.run(l.value().module(), NdRange.of1D(groups * 16, 16), full);
        assertTrue(run.isOk(), String.valueOf(run.diagnostics()));
        return Arrays.stream(partial).sum();
    }

    @Test
    void kernelsThatAreNotElementWiseProducersAreRejected() {
        IrModule reduction = module(POSITIVES);
        String[] kernels = {
                // условная запись
                """
                @Kernel
                public static void k(GpuFloatArray out) {
                    int i = Gpu.getGlobalId(0);
                    if (i > 3) {
                        out.set(i, 1f);
                    }
                }
                """,
                // читает свой выход
                """
                @Kernel
                public static void k(GpuFloatArray out) {
                    int i = Gpu.getGlobalId(0);
                    out.set(i, out.get(i) + 1f);
                }
                """,
                // пишет ещё один массив
                """
                @Kernel
                public static void k(GpuFloatArray out, GpuFloatArray log) {
                    int i = Gpu.getGlobalId(0);
                    log.set(0, 1f);
                    out.set(i, 2f);
                }
                """,
                // сдвинутый индекс
                """
                @Kernel
                public static void k(GpuFloatArray out) {
                    int i = Gpu.getGlobalId(0);
                    out.set(i + 1, 2f);
                }
                """,
                // зависит от размера запуска
                """
                @Kernel
                public static void k(GpuFloatArray out) {
                    int i = Gpu.getGlobalId(0);
                    out.set(i, (float) Gpu.getGlobalSize(0));
                }
                """};
        for (String k : kernels) {
            IrModule m = module(k);
            assertTrue(Fusion.producer(m).isEmpty(), k);
            assertFalse(Fusion.fuse(m, reduction, 1).isOk(), k);
        }
    }

    @Test
    void reductionsThatPassTheArrayOnAreRejected() {
        IrModule producer = module(PRODUCER);
        IrModule passes = module("""
                @DeviceFunction
                static float first(GpuFloatArray v) {
                    return v.get(0);
                }

                @Reduction
                public static float sum(GpuFloatArray v) {
                    float s = first(v);
                    for (int i = 0; i < v.length(); i++) {
                        s += v.get(i);
                    }
                    return s;
                }
                """);
        Result<IrModule> r = Fusion.fuse(producer, passes, 0);
        assertFalse(r.isOk());
        assertTrue(r.diagnostics().get(0).message().contains("other than as `a.get(i)`"), r.diagnostics().toString());
        IrModule floats = module("""
                @Reduction
                public static int count(GpuIntArray v) {
                    int c = 0;
                    for (int i = 0; i < v.length(); i++) {
                        c += v.get(i);
                    }
                    return c;
                }
                """);
        assertFalse(Fusion.fuse(producer, floats, 0).isOk(), "element types differ");
    }
}
