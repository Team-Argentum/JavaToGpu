package com.sixik.gpu.frontend.java;

import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrBinary;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Сквозная проверка основного примера из README. */
class SmokeTest {

    static final String COMPUTE_SINE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.Global;
            import com.sixik.gpu.api.annotations.Kernel;
            import com.sixik.gpu.api.annotations.WorkGroup;

            public final class Sine {
                @Kernel
                @WorkGroup(x = 256)
                public static void computeSine(@Global float[] input, @Global float[] output) {
                    int id = Gpu.getGlobalId(0);
                    output[id] = Gpu.sin(input[id]) + 2.0f;
                }
            }
            """;

    @Test
    void computeSineEqualsHandBuiltReferenceModule() {
        FrontendHarness.Outcome r = FrontendHarness.compile(COMPUTE_SINE);
        assertTrue(r.success(), r.describe());
        assertTrue(r.gpu().isEmpty(), r.describe());
        IrModule m = r.module();
        assertFalse(Validator.validate(m).hasErrors());
        // фронтенд даёт тот же канонический IR (по хэшу, без отладочной секции), что собранный вручную эталон 5.11
        assertEquals(IrBinary.kernelId(Fixtures.computeSine()), IrBinary.kernelId(m));
        assertTrue(r.resources().containsKey(KernelResources.irPath(IrBinary.kernelId(m))));
    }
}
