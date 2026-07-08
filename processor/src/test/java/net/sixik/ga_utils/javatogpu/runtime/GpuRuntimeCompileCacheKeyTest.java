package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClBackendLowerer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GpuRuntimeCompileCacheKeyTest {

    @Test
    void cacheKeySeparatesDescriptorReviewAndProductionIrGpuSourceModes() {
        GpuKernelDescriptor descriptor = descriptor();
        GpuRuntimeDeviceProfile deviceProfile = GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL");
        IrGpuArtifact artifact = irGpuArtifact(descriptor.kernelResource());

        GpuRuntimeCompileRequest descriptorRequest = request(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                deviceProfile,
                artifact
        );
        GpuBackendModuleArtifact descriptorModule = GpuBackendModuleArtifact.openClSource(
                descriptor.kernelSource(),
                descriptor.kernelResource(),
                OpenClBackendLowerer.VERSION,
                "descriptor-opencl-source",
                "opencl-descriptor-source-compile"
        );

        GpuRuntimeCompileRequest reviewRequest = request(
                descriptor,
                GpuRuntimeCompileOptions.openClIrGpuSourceReview(List.of("-cl-fast-relaxed-math")),
                deviceProfile,
                artifact
        );
        GpuBackendModuleArtifact reviewModule = reconstructedModule(descriptor);

        GpuRuntimeCompileRequest productionRequest = request(
                descriptor,
                GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of("-cl-fast-relaxed-math"), "vendor-tuned")
                        .withProductionPromotionDecision(productionEnabledDecision()),
                deviceProfile,
                artifact
        );
        GpuBackendModuleArtifact productionModule = reconstructedModule(descriptor);

        GpuRuntimeCompileCacheKey descriptorKey = cacheKey(descriptorRequest, descriptorModule);
        GpuRuntimeCompileCacheKey reviewKey = cacheKey(reviewRequest, reviewModule);
        GpuRuntimeCompileCacheKey productionKey = cacheKey(productionRequest, productionModule);

        assertNotEquals(descriptorKey, reviewKey);
        assertNotEquals(descriptorKey, productionKey);
        assertNotEquals(reviewKey, productionKey);
        assertEquals("descriptor-opencl-source", descriptorKey.moduleArtifact().sourceOrigin());
        assertEquals("irgpu-backend-neutral-source", reviewKey.moduleArtifact().sourceOrigin());
        assertEquals("irgpu-backend-neutral-source", productionKey.moduleArtifact().sourceOrigin());
        assertEquals(
                GpuProductionPromotionDecision.DIAGNOSTIC_ONLY,
                reviewKey.options().backendOptions().productionPromotionDecisionMode()
        );
        assertEquals(
                GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                productionKey.options().backendOptions().productionPromotionDecisionMode()
        );
    }

    private static GpuRuntimeCompileCacheKey cacheKey(
            GpuRuntimeCompileRequest request,
            GpuBackendModuleArtifact module
    ) {
        return GpuRuntimeCompileCacheKey.from(
                request,
                module,
                GpuRuntimeCompileInvalidationStamp.from(request, module, "optimizer:test-v1")
        );
    }

    private static GpuRuntimeCompileRequest request(
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileOptions options,
            GpuRuntimeDeviceProfile deviceProfile,
            IrGpuArtifact artifact
    ) {
        return new GpuRuntimeCompileRequest(descriptor, options, deviceProfile, Optional.of(artifact));
    }

    private static GpuBackendModuleArtifact reconstructedModule(GpuKernelDescriptor descriptor) {
        return GpuBackendModuleArtifact.openClSource(
                descriptor.kernelSource(),
                descriptor.kernelResource() + "#irgpu-reconstructed",
                OpenClBackendLowerer.VERSION,
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
    }

    private static GpuProductionPromotionDecision productionEnabledDecision() {
        return new GpuProductionPromotionDecision(
                GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                "production-ready",
                true,
                true,
                true,
                "none",
                "none",
                "test fixture enables production IrGpu source switching"
        );
    }

    private static GpuKernelDescriptor descriptor() {
        return new GpuKernelDescriptor(
                "jtg_kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void jtg_kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.irgpu.properties",
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
                                "body\n  set output[0] = 1\n",
                                List.of()
                        ))
                ),
                List.of(new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of())),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(IrGpuBackendOutput.openClSource(derivedOpenClResource)),
                "opencl",
                "off"
        );
    }
}
