package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.codegen.opencl.EmitOptions;
import com.sixik.gpu.codegen.opencl.OpenClEmitter;
import com.sixik.gpu.codegen.opencl.Target;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Validator;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Дифференциальный фаззинг: случайные корректные ядра ({@link RandomPrograms}) до и после оптимизации на всех уровнях
 * дают побитово одинаковый результат, а оптимизированный IR проходит генерацию OpenCL C.
 */
class RandomProgramTest {

    private static final int THREADS = 4;
    private static final OptimizeOptions[] LEVELS = {
            OptimizeOptions.DEFAULT.withLevel(1),
            OptimizeOptions.DEFAULT,
            OptimizeOptions.DEFAULT.withLevel(3).withRegisterBudget(4),
            OptimizeOptions.DEFAULT.withRegisterBudget(1),
    };

    static LongStream seeds() {
        return LongStream.range(0, 400);
    }

    private static Supplier<Object[]> args(long seed) {
        return () -> {
            Random r = new Random(seed * 31 + 7);
            int n = THREADS * RandomPrograms.SLOTS;
            int[] inI = new int[n];
            float[] inF = new float[n];
            for (int i = 0; i < n; i++) {
                inI[i] = switch (r.nextInt(5)) {
                    case 0 -> Integer.MIN_VALUE;
                    case 1 -> r.nextInt(16) - 8;
                    default -> r.nextInt();
                };
                inF[i] = switch (r.nextInt(6)) {
                    case 0 -> Float.NaN;
                    case 1 -> -0f;
                    case 2 -> Float.POSITIVE_INFINITY;
                    default -> (r.nextFloat() - 0.5f) * 100f;
                };
            }
            return new Object[]{inI, inF, new int[n], new float[n]};
        };
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    void randomKernelBehavesTheSameAfterOptimization(long seed) {
        IrModule m = RandomPrograms.generate(seed);
        Validator.Report v = Validator.validate(m);
        assertFalse(v.hasErrors(), () -> "generator produced an invalid module: " + v.diagnostics());
        for (OptimizeOptions o : LEVELS) {
            IrModule opt = OptTest.opt(m, o);
            String label = "seed " + seed + " level " + o.level() + " budget " + o.registerBudget();
            OptTest.assertSameBehavior(label, m, opt, NdRange.of1D(THREADS), args(seed));
            Result<?> emitted = OpenClEmitter.emit(opt, OptTest.kernelIndex(opt), Target.GENERIC, EmitOptions.RELEASE);
            assertTrue(emitted.isOk(), () -> label + ": codegen rejected the optimized module: " + emitted.diagnostics());
        }
    }

    @Test
    void optimizedSamplesPassCodegen() {
        for (SampleKernels.Kernel k : SampleKernels.all()) {
            for (OptimizeOptions o : LEVELS) {
                IrModule opt = OptTest.opt(k.module(), o);
                Result<?> emitted = OpenClEmitter.emit(opt, opt.functionCount() - 1, Target.GENERIC, EmitOptions.RELEASE);
                assertTrue(emitted.isOk(), () -> k.label() + ": " + emitted.diagnostics());
            }
        }
    }
}
