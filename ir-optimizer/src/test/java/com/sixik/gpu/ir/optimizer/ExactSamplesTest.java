package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.api.WorkItem;
import com.sixik.gpu.api.WorkItemContext;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.interpreter.samples.ExactKernels;
import com.sixik.gpu.ir.interpreter.samples.SampleArgs;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Критерий B5 (FRONTEND_ASM_RUNTIME.md, 18.7): F64-образцы {@code exact} в интерпретаторе побитово равны JVM на
 * {@code opt=0..3}. Аргументы - случайные значения образцов с {@code 0, -0, NaN, ±Inf}; сравниваются биты, а не
 * значения ({@code NaN} и знак нуля тоже).
 */
class ExactSamplesTest {

    private static final long[] SEEDS = {1L, 20261118L, 77L};

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"doubles", "floatsAndInts"})
    void interpreterEqualsTheJvmBitwiseAtEveryLevel(String name) {
        SampleKernels.Kernel k = SampleKernels.kernel(ExactKernels.class, name);
        IrModule m = k.module();
        assertEquals(IrModule.FP_EXACT, m.fFp(m.functionCount() - 1), "the sample is exact");
        NdRange range = NdRange.of1D(16);
        for (long seed : SEEDS) {
            Supplier<Object[]> args = SampleArgs.random(k, seed);
            Object[] jvm = args.get();
            assertNull(runOnJvm(k, range, jvm), name + ": the JVM threw");
            for (int level = 0; level <= 3; level++) {
                IrModule opt = level == 0 ? m : OptTest.opt(m, OptimizeOptions.DEFAULT.withLevel(level));
                Object[] ir = args.get();
                Result<Interpreter.Execution> r = Interpreter.run(opt, opt.functionCount() - 1, range,
                        Interpreter.Options.DEFAULT, ir);
                assertTrue(r.isOk(), name + " opt=" + level + ": " + r.diagnostics());
                for (int i = 0; i < jvm.length; i++) {
                    assertEquals(bits(jvm[i]), bits(ir[i]), name + " opt=" + level + " seed " + seed + " argument " + i);
                }
            }
        }
    }

    /** Биты массива одной строкой: {@code double/float} - сырые биты, без нормализации NaN. */
    private static String bits(Object a) {
        StringBuilder sb = new StringBuilder();
        if (a instanceof double[] d) {
            for (double v : d) {
                sb.append(Long.toHexString(Double.doubleToRawLongBits(v))).append(',');
            }
        } else if (a instanceof float[] f) {
            for (float v : f) {
                sb.append(Integer.toHexString(Float.floatToRawIntBits(v))).append(',');
            }
        } else if (a instanceof int[] i) {
            sb.append(java.util.Arrays.toString(i));
        } else if (a instanceof long[] l) {
            sb.append(java.util.Arrays.toString(l));
        } else {
            sb.append(a);
        }
        return sb.toString();
    }

    /** Исполняет оригинальный Java-метод по всем work-item'ам (как {@code Differential} в {@code ir-interpreter}). */
    private static Throwable runOnJvm(SampleKernels.Kernel k, NdRange range, Object[] args) {
        int[] global = {range.global(0), range.global(1), range.global(2)};
        WorkItemContext ctx = new WorkItemContext(range.dim(), global, new int[]{1, 1, 1});
        Throwable[] failure = new Throwable[1];
        WorkItem.run(ctx, () -> {
            try {
                for (int x = 0; x < global[0]; x++) {
                    ctx.setGlobalId(x, 0, 0);
                    k.method().invoke(null, args);
                }
            } catch (InvocationTargetException e) {
                failure[0] = e.getCause();
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        });
        return failure[0];
    }
}
