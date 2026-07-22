package net.sixik.ga_utils.javatogpu.runtime.diagnostics;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCallSite;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDiagnosticContext;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDiagnosticRenderer;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeFailurePhase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GpuRuntimeDiagnosticRendererSupportTest {

    @Test
    void rootDiagnosticRendererFacadeDelegatesToDiagnosticsSupport() {
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
                new IrGpuSourceLocation("java-source", "sample.Demo", "kernel", 12, 9, 12, 21),
                new GpuRuntimeCallSite(
                        "sample.Demo",
                        "run",
                        "Demo.java",
                        30,
                        17,
                        30,
                        31,
                        "kernel(input)",
                        "sample.Demo",
                        "kernel",
                        "compiler-index"
                )
        );

        String rootRendered = GpuRuntimeDiagnosticRenderer.render(
                "JTG-RUNTIME-COMPILE-001",
                GpuRuntimeFailurePhase.KERNEL_COMPILATION,
                "OpenCL kernel build failed",
                context,
                List.of("check OpenCL build log")
        );
        String supportRendered = GpuRuntimeDiagnosticRendererSupport.render(
                "JTG-RUNTIME-COMPILE-001",
                GpuRuntimeFailurePhase.KERNEL_COMPILATION,
                "OpenCL kernel build failed",
                context,
                List.of("check OpenCL build log")
        );

        assertEquals(supportRendered, rootRendered);
        assertTrue(rootRendered.contains("error[JTG-RUNTIME-COMPILE-001]: OpenCL kernel build failed"));
        assertTrue(rootRendered.contains("--> Demo.java:30:17"));
        assertTrue(rootRendered.contains("kernel(input)"));
        assertTrue(rootRendered.contains("= help: check OpenCL build log"));
    }
}
