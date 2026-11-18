package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.Features;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.interpreter.samples.ExactKernels;
import com.sixik.gpu.ir.interpreter.samples.SampleKernels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Шаг B5 (FRONTEND_ASM_RUNTIME.md, 18.2-S6): ядро {@code exact} с {@code float}-делением требует от устройства
 * {@code EXACT_DIVIDE_SQRT} (рантайм иначе отвечает {@code RT-D02}) и собирается с опцией точного деления; JDK-интринсики
 * печатаются точными функциями OpenCL C.
 */
class ExactEmitTest {

    private static Artifact emit(String kernel) {
        IrModule m = SampleKernels.kernel(ExactKernels.class, kernel).module();
        Result<Artifact> r = OpenClEmitter.emit(m, m.functionCount() - 1, ProgramTest.NV, EmitOptions.RELEASE);
        assertTrue(r.isOk(), r.diagnostics().toString());
        return r.value();
    }

    @Test
    void floatDivisionInAnExactKernelRequiresCorrectRounding() {
        Artifact a = emit("floatsAndInts");
        assertEquals(Features.EXACT_DIVIDE_SQRT, a.requiredFeatures() & Features.EXACT_DIVIDE_SQRT);
        assertEquals(Target.EXACT_DIVIDE_SQRT_OPTION, a.buildOptions());
        assertTrue(a.source().contains("gpu_floordiv_i32(") && a.source().contains("mul_hi("), a.source());
    }

    @Test
    void exactKernelWithoutFloatDivisionRequiresNothingMore() {
        Artifact a = emit("doubles");
        assertEquals(0, a.requiredFeatures() & Features.EXACT_DIVIDE_SQRT);
        assertEquals(Features.FP64, a.requiredFeatures() & Features.FP64);
        assertFalse(a.source().matches("(?s).*[^y]sign[(].*"), "OpenCL sign() differs from Math.signum for NaN");
        assertTrue(a.source().contains("nextafter(v1, ((double)INFINITY))"), a.source());
    }
}
