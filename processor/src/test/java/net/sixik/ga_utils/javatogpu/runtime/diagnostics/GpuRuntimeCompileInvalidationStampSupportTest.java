package net.sixik.ga_utils.javatogpu.runtime.diagnostics;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileInvalidationStamp;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GpuRuntimeCompileInvalidationStampSupportTest {

    @Test
    void rootInvalidationStampFactoryDelegatesNoIrFallbackToDiagnosticsSupport() {
        GpuBackendModuleArtifact module = GpuBackendModuleArtifact.openClSource(
                "__kernel void jtg_kernel() {}",
                "javatogpu/sample/Demo/kernel.cl",
                "opencl-lowerer:test"
        );

        GpuRuntimeCompileInvalidationStamp rootStamp = GpuRuntimeCompileInvalidationStamp.from(
                null,
                module,
                "optimizer:test"
        );
        GpuRuntimeCompileInvalidationStamp supportStamp = GpuRuntimeCompileInvalidationStampSupport.from(
                null,
                module,
                "optimizer:test"
        );

        assertEquals(supportStamp, rootStamp);
        assertEquals(GpuRuntimeCompileInvalidationStamp.NO_IR_FORMAT, rootStamp.irFormat());
        assertEquals(module.artifactVersion(), rootStamp.backendArtifactVersion());
        assertEquals("optimizer:test", rootStamp.optimizerPipelineVersion());
    }

    @Test
    void rootInvalidationStampFactoryDelegatesIrHeaderStampToDiagnosticsSupport() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "jtg_kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void jtg_kernel() {}",
                "javatogpu/sample/Demo/kernel.irgpu.properties",
                List.of()
        );
        IrGpuArtifact artifact = artifact(descriptor.kernelResource());
        GpuBackendModuleArtifact module = GpuBackendModuleArtifact.openClSource(
                descriptor.kernelSource(),
                descriptor.kernelResource(),
                "opencl-lowerer:test"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(artifact)
        );

        GpuRuntimeCompileInvalidationStamp rootStamp = GpuRuntimeCompileInvalidationStamp.from(
                request,
                module,
                "optimizer:test"
        );
        GpuRuntimeCompileInvalidationStamp supportStamp = GpuRuntimeCompileInvalidationStampSupport.from(
                request,
                module,
                "optimizer:test"
        );

        assertEquals(supportStamp, rootStamp);
        assertEquals(IrGpuArtifactHeader.javaSourceV1().format(), rootStamp.irFormat());
        assertEquals(IrGpuArtifactHeader.javaSourceV1().schemaVersion(), rootStamp.irSchemaVersion());
        assertEquals(IrGpuArtifactHeader.javaSourceV1().compilerArtifact(), rootStamp.compilerArtifact());
        assertEquals(IrGpuArtifactHeader.javaSourceV1().sourceFrontend(), rootStamp.sourceFrontend());
    }

    private static IrGpuArtifact artifact(String derivedOpenClResource) {
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
                                "body\n  return\n",
                                List.of()
                        ))
                ),
                List.of(),
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(IrGpuBackendOutput.openClSource(derivedOpenClResource)),
                "opencl",
                "off"
        );
    }
}
