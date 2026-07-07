package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClBackendLowerer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendLowerersTest {

    @Test
    void openClLowererProducesSourceModuleArtifact() {
        GpuKernelDescriptor descriptor = sampleDescriptor();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL")
        );

        GpuBackendLowerer lowerer = GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL);
        GpuBackendModuleArtifact artifact = lowerer.lower(compileRequest);

        assertSame(lowerer, GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL));
        assertEquals(GpuBackendTarget.OPENCL, lowerer.backendTarget());
        assertEquals(OpenClBackendLowerer.VERSION, lowerer.lowererVersion());
        assertEquals(GpuBackendTarget.OPENCL, artifact.backendTarget());
        assertEquals("source", artifact.kind());
        assertEquals("opencl-c", artifact.format());
        assertEquals("opencl:source:opencl-c:v1", artifact.artifactVersion());
        assertEquals(descriptor.kernelSource(), artifact.source());
        assertEquals(descriptor.kernelResource(), artifact.resource());
        assertEquals(OpenClBackendLowerer.VERSION, artifact.lowererVersion());
    }

    @Test
    void plannedBackendLowerersFailWithExplicitUnsupportedDiagnostic() {
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                sampleDescriptor(),
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA")
        );

        assertUnsupported(GpuBackendTarget.CUDA, compileRequest);
        assertUnsupported(GpuBackendTarget.VULKAN, compileRequest);
        assertUnsupported(GpuBackendTarget.METAL, compileRequest);
    }

    private static void assertUnsupported(GpuBackendTarget backendTarget, GpuRuntimeCompileRequest compileRequest) {
        GpuBackendLowerer lowerer = GpuBackendLowerers.forTarget(backendTarget);

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> lowerer.lower(compileRequest)
        );

        assertEquals(backendTarget, lowerer.backendTarget());
        assertEquals("unsupported", lowerer.lowererVersion());
        assertTrue(exception.getMessage().contains("not implemented for " + backendTarget));
    }

    private static GpuKernelDescriptor sampleDescriptor() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
    }
}
