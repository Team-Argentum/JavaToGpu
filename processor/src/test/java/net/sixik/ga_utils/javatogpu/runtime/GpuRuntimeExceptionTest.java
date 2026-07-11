package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeExceptionTest {

    @Test
    void rendersStableRustLikeDiagnosticWithSourceAndCompileContext() {
        IllegalStateException cause = new IllegalStateException("driver rejected generated source");
        GpuRuntimeDiagnosticContext context = new GpuRuntimeDiagnosticContext(
                GpuBackendTarget.OPENCL,
                "jtg_kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "javatogpu/sample/Demo/kernel.irgpu.properties",
                "opencl-0",
                "Fake GPU",
                "NVIDIA",
                List.of("-cl-fast-relaxed-math"),
                "diagnostic",
                new IrGpuSourceLocation("java-source", "sample.Demo", "kernel", 12, 9, 12, 21)
        );

        GpuRuntimeKernelCompilationException exception = new GpuRuntimeKernelCompilationException(
                "OpenCL kernel build failed",
                context,
                cause
        );

        assertEquals("JTG-RUNTIME-COMPILE-001", exception.code());
        assertEquals(GpuRuntimeFailurePhase.KERNEL_COMPILATION, exception.phase());
        assertEquals("OpenCL kernel build failed", exception.summary());
        assertSame(cause, exception.getCause());
        assertTrue(exception.diagnosticText().contains("error[JTG-RUNTIME-COMPILE-001]"));
        assertTrue(exception.diagnosticText().contains("--> sample.Demo#kernel:12:9"));
        assertTrue(exception.diagnosticText().contains("^^^^^^^^^^^^^ kernel compilation failed"));
        assertTrue(exception.diagnosticText().contains("= device: Fake GPU (vendor=NVIDIA, id=opencl-0)"));
        assertTrue(exception.diagnosticText().contains("= compile args: -cl-fast-relaxed-math"));
        assertEquals("jtg_kernel", exception.context().kernelName());
        assertEquals(
                "12",
                exception.context().artifactFields("failure").get("failure.sourceBeginLine")
        );
    }

    @Test
    void selectionFailuresShareThePublicRuntimeBaseType() {
        GpuRuntimeDeviceSelection selection = new GpuRuntimeDeviceSelection(
                java.util.Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                true,
                false,
                "compatible-device-missing",
                List.of("no candidate matched")
        );

        GpuRuntimeException exception = new GpuRuntimeDeviceSelectionException(
                "GPU device selection failed",
                selection
        );

        assertEquals("JTG-RUNTIME-DEVICE-001", exception.code());
        assertEquals(GpuRuntimeFailurePhase.DEVICE_SELECTION, exception.phase());
        assertTrue(exception.diagnosticText().contains("runtime-device-selection.properties"));
    }
}
