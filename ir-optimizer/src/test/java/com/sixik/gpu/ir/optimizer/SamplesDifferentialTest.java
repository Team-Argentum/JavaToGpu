package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Validator;
import com.sixik.gpu.ir.interpreter.NdRange;
import com.sixik.gpu.ir.interpreter.samples.SampleArgs;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import com.sixik.gpu.ir.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Главный критерий приёмки: на каждом образце-ядре (настоящие Java-методы, собранные фронтендом) результат
 * оптимизированного модуля совпадает с исходным побитово, а сам модуль валиден. Проверяются все уровни.
 */
class SamplesDifferentialTest {

    static Stream<String> labels() {
        return SampleKernels.all().stream().map(SampleKernels.Kernel::label);
    }

    private static SampleKernels.Kernel find(String label) {
        return SampleKernels.all().stream().filter(k -> k.label().equals(label)).findFirst().orElseThrow();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("labels")
    void optimizedSampleBehavesLikeTheOriginal(String label) {
        SampleKernels.Kernel k = find(label);
        IrModule m = k.module();
        int f = m.functionCount() - 1;
        NdRange range = m.fReqWorkGroup(f, 0) > 0 ? NdRange.of2D(8, 4) : NdRange.of1D(16);
        for (OptimizeOptions o : new OptimizeOptions[]{
                OptimizeOptions.DEFAULT.withLevel(1),
                OptimizeOptions.DEFAULT,
                OptimizeOptions.DEFAULT.withLevel(3),
                OptimizeOptions.DEFAULT.withRegisterBudget(8)}) {
            IrModule opt = OptTest.opt(m, o);
            OptTest.assertSameBehavior(label + " @level" + o.level() + "/budget" + o.registerBudget(), m, opt, range,
                    SampleArgs.random(k, 4242));
        }
    }

    @Test
    void richFixtureSurvivesTheRoundTrip() {
        IrModule rich = Fixtures.rich();
        IrModule once = Codec.lower(Codec.lift(rich));
        assertFalse(Validator.validate(once).hasErrors());
        IrModule twice = Codec.lower(Codec.lift(once));
        assertTrue(java.util.Arrays.equals(com.sixik.gpu.ir.IrBinary.hash(once), com.sixik.gpu.ir.IrBinary.hash(twice)),
                "lift/lower must be a fixed point (the hash ignores debug info)");
        IrModule o = OptTest.opt(rich);
        assertFalse(Validator.validate(o).hasErrors());
    }
}
