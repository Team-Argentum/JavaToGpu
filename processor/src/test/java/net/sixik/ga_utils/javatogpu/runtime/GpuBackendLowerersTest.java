package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClBackendLowerer;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrGpuParityChecker;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrGpuParityResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

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
    void openClLowererAcceptsIrGpuWhenDerivedResourceMatchesDescriptorSource() {
        GpuKernelDescriptor descriptor = sampleDescriptor();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(irGpuArtifact(descriptor.kernelResource()))
        );

        OpenClIrGpuParityResult parityResult = OpenClIrGpuParityChecker.check(compileRequest);
        GpuBackendModuleArtifact artifact = GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL).lower(compileRequest);

        assertTrue(parityResult.checked());
        assertTrue(parityResult.compatible());
        assertEquals(descriptor.kernelResource(), parityResult.derivedOpenClResource());
        assertEquals(descriptor.kernelSource(), artifact.source());
        assertEquals(descriptor.kernelResource(), artifact.resource());
    }

    @Test
    void openClLowererRejectsIrGpuWhenDerivedOpenClResourceDriftsFromDescriptor() {
        GpuKernelDescriptor descriptor = sampleDescriptor();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(irGpuArtifact("javatogpu/sample/Demo/stale-kernel.cl"))
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL).lower(compileRequest)
        );

        assertTrue(exception.getMessage().contains("OpenCL IrGpu parity check failed"));
        assertTrue(exception.getMessage().contains("stale-kernel.cl"));
        assertTrue(exception.getMessage().contains(descriptor.kernelResource()));
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

    private static IrGpuArtifact irGpuArtifact(String derivedOpenClResource) {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "jtg_kernel",
                                "body\\n  return output[0]\\n",
                                List.of()
                        ))
                ),
                List.of(IrGpuBackendOutput.openClSource(derivedOpenClResource)),
                "opencl",
                "off"
        );
    }
}
