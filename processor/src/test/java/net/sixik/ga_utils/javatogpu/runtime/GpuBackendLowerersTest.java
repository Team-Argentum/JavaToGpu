package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModuleMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClBackendLowerer;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrGpuParityChecker;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrGpuParityResult;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrGpuReconstructionPlan;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrGpuReconstructionPreview;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrGpuSourceReconstructor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendLowerersTest {

    private static final String SIMPLE_IRGPU_SOURCE_RESOURCE = "javatogpu/runtime/opencl/integration/simple-irgpu-source-kernel.irgpu.properties";

    @Test
    void openClLowererProducesSourceModuleArtifact() {
        GpuKernelDescriptor descriptor = sampleDescriptor();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL")
        );

        GpuBackendLowerer lowerer = GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL);
        GpuBackendSourceSelectionPlan sourceSelectionPlan = lowerer.sourceSelectionPlan(compileRequest);
        GpuBackendModuleArtifact artifact = lowerer.lower(compileRequest);

        assertSame(lowerer, GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL));
        assertEquals(GpuBackendTarget.OPENCL, lowerer.backendTarget());
        assertEquals(OpenClBackendLowerer.VERSION, lowerer.lowererVersion());
        assertEquals(GpuBackendTarget.OPENCL, sourceSelectionPlan.backendTarget());
        assertTrue(!sourceSelectionPlan.irGpuSourceSelected());
        assertEquals("descriptor-opencl-source", sourceSelectionPlan.selectedSource());
        assertEquals("unknown", sourceSelectionPlan.payloadFormat());
        assertEquals("opencl-descriptor-source-compile", sourceSelectionPlan.runtimeLoadMode());
        assertTrue(sourceSelectionPlan.blockers().contains("irgpu-artifact-missing"));
        assertTrue(sourceSelectionPlan.toLine().contains("selectedSource=descriptor-opencl-source"));
        assertEquals(GpuBackendTarget.OPENCL, artifact.backendTarget());
        assertEquals("source", artifact.kind());
        assertEquals("opencl-c", artifact.format());
        assertEquals("opencl:source:opencl-c:v1", artifact.artifactVersion());
        assertEquals(descriptor.kernelSource(), artifact.source());
        assertEquals(descriptor.kernelResource(), artifact.resource());
        assertEquals(OpenClBackendLowerer.VERSION, artifact.lowererVersion());
        assertEquals("descriptor-opencl-source", artifact.sourceOrigin());
        assertTrue(artifact.sourceAvailable());
        assertTrue(!artifact.binaryAvailable());
        assertEquals("", artifact.compileLogResource());
        assertEquals("", artifact.sourceMapResource());
        assertEquals("opencl-descriptor-source-compile", artifact.runtimeLoadMode());
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
        OpenClIrGpuReconstructionPlan reconstructionPlan = OpenClIrGpuReconstructionPlan.from(parityResult);
        GpuBackendSourceSelectionPlan sourceSelectionPlan = GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL)
                .sourceSelectionPlan(compileRequest);
        GpuBackendModuleArtifact artifact = GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL).lower(compileRequest);

        assertTrue(parityResult.checked());
        assertTrue(parityResult.compatible());
        assertTrue(!parityResult.backendNeutralSourceReady());
        assertEquals("ir-text-v1", parityResult.regenerationPayloadFormat());
        assertEquals("derived-opencl-source", parityResult.regenerationFallbackSource());
        assertEquals(List.of("typed-body-regeneration-not-yet-available"), parityResult.regenerationBlockers());
        assertTrue(parityResult.toLine().contains("backendNeutralSourceReady=false"));
        assertTrue(parityResult.toLine().contains("regenerationBlockers=typed-body-regeneration-not-yet-available"));
        assertTrue(!reconstructionPlan.irGpuSourceSelected());
        assertEquals("derived-opencl-source", reconstructionPlan.selectedSource());
        assertEquals("ir-text-v1", reconstructionPlan.payloadFormat());
        assertEquals(List.of("typed-body-regeneration-not-yet-available"), reconstructionPlan.blockers());
        assertTrue(reconstructionPlan.toLine().contains("irGpuSourceSelected=false"));
        assertEquals(GpuBackendTarget.OPENCL, sourceSelectionPlan.backendTarget());
        assertTrue(!sourceSelectionPlan.irGpuSourceSelected());
        assertEquals("derived-opencl-source", sourceSelectionPlan.selectedSource());
        assertEquals("ir-text-v1", sourceSelectionPlan.payloadFormat());
        assertEquals("opencl-source-compile", sourceSelectionPlan.runtimeLoadMode());
        assertEquals(List.of("typed-body-regeneration-not-yet-available"), sourceSelectionPlan.blockers());
        assertEquals(descriptor.kernelResource(), parityResult.derivedOpenClResource());
        assertEquals(descriptor.kernelSource(), artifact.source());
        assertEquals(descriptor.kernelResource(), artifact.resource());
        assertEquals("derived-opencl-source", artifact.sourceOrigin());
        assertEquals("opencl-source-compile", artifact.runtimeLoadMode());
    }

    @Test
    void openClReconstructionPreviewKeepsTransitionalIrGpuOnFallbackPath() {
        GpuKernelDescriptor descriptor = sampleDescriptor();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(irGpuArtifact(descriptor.kernelResource()))
        );

        OpenClIrGpuReconstructionPreview preview = OpenClIrGpuReconstructionPreview.inspect(compileRequest);
        GpuBackendSourceReconstructionResult sourceResult = preview.toSourceReconstructionResult();
        GpuBackendSourceReconstructionResult reconstructorResult = OpenClIrGpuSourceReconstructor.INSTANCE
                .reconstruct(compileRequest);

        assertTrue(preview.attempted());
        assertTrue(!preview.reconstructable());
        assertEquals("derived-opencl-source", preview.selectedSource());
        assertEquals("ir-text-v1", preview.payloadFormat());
        assertEquals("jtg_kernel", preview.entryEmittedName());
        assertEquals(1, preview.methodBodyCount());
        assertEquals(List.of("typed-body-regeneration-not-yet-available"), preview.blockers());
        assertTrue(preview.toLine().contains("reconstructable=false"));
        assertTrue(preview.toLine().contains("runtime must use generated OpenCL fallback"));
        assertTrue(sourceResult.attempted());
        assertTrue(!sourceResult.ready());
        assertTrue(!sourceResult.reconstructed());
        assertTrue(!sourceResult.sourceAvailable());
        assertEquals("derived-opencl-source", sourceResult.selectedSource());
        assertEquals("opencl-source-compile", sourceResult.runtimeLoadMode());
        assertEquals(List.of("typed-body-regeneration-not-yet-available"), sourceResult.blockers());
        assertEquals(GpuBackendTarget.OPENCL, OpenClIrGpuSourceReconstructor.INSTANCE.backendTarget());
        assertEquals(OpenClIrGpuSourceReconstructor.VERSION, OpenClIrGpuSourceReconstructor.INSTANCE.version());
        assertTrue(reconstructorResult.attempted());
        assertTrue(!reconstructorResult.ready());
        assertTrue(!reconstructorResult.reconstructed());
        assertTrue(!reconstructorResult.sourceAvailable());
        assertEquals("derived-opencl-source", reconstructorResult.selectedSource());
        assertEquals("opencl-source-compile", reconstructorResult.runtimeLoadMode());
        assertTrue(reconstructorResult.blockers().contains("irgpu-entry-parameter-metadata-missing"));
        assertTrue(reconstructorResult.diagnostics().contains("irgpu-entry-jtg_kernel-parsed.statement.count=1"));
        assertTrue(reconstructorResult.diagnostics().stream().anyMatch(diagnostic -> diagnostic.startsWith("irgpu-entry-jtg_kernel-emitted.body.length=")));
    }

    @Test
    void openClReconstructionPreviewRecognizesSyntheticReadyIrGpuWithoutSwitchingLowererSource() {
        GpuKernelDescriptor descriptor = sampleDescriptor();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(irGpuArtifact(descriptor.kernelResource(), IrGpuRegenerationMetadata.backendNeutralReady()))
        );

        GpuBackendModuleArtifact artifact = GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL).lower(compileRequest);
        OpenClIrGpuReconstructionPreview preview = OpenClIrGpuReconstructionPreview.inspect(compileRequest);
        GpuBackendSourceReconstructionResult sourceResult = preview.toSourceReconstructionResult();
        GpuBackendSourceReconstructionResult reconstructorResult = OpenClIrGpuSourceReconstructor.INSTANCE
                .reconstruct(compileRequest);

        assertTrue(preview.attempted());
        assertTrue(preview.reconstructable());
        assertEquals("irgpu-backend-neutral-source", preview.selectedSource());
        assertEquals("ir-text-v1", preview.payloadFormat());
        assertEquals("jtg_kernel", preview.entryEmittedName());
        assertEquals(1, preview.methodBodyCount());
        assertTrue(preview.blockers().isEmpty());
        assertTrue(preview.toLine().contains("reconstructable=true"));
        assertTrue(preview.toLine().contains("OpenCL source can be reconstructed from IrGpu"));
        assertEquals(descriptor.kernelSource(), artifact.source());
        assertEquals("irgpu-backend-neutral-source", artifact.sourceOrigin());
        assertEquals("opencl-irgpu-source-compile", artifact.runtimeLoadMode());
        assertTrue(sourceResult.attempted());
        assertTrue(sourceResult.ready());
        assertTrue(!sourceResult.reconstructed());
        assertTrue(!sourceResult.sourceAvailable());
        assertEquals("irgpu-backend-neutral-source", sourceResult.selectedSource());
        assertEquals("opencl-irgpu-source-compile", sourceResult.runtimeLoadMode());
        assertTrue(sourceResult.blockers().isEmpty());
        assertTrue(sourceResult.diagnostics().contains("OpenCL source can be reconstructed from IrGpu when the runtime source path is enabled"));
        assertTrue(reconstructorResult.attempted());
        assertTrue(!reconstructorResult.ready());
        assertTrue(!reconstructorResult.reconstructed());
        assertTrue(!reconstructorResult.sourceAvailable());
        assertEquals("irgpu-backend-neutral-source", reconstructorResult.selectedSource());
        assertEquals("opencl-irgpu-source-compile", reconstructorResult.runtimeLoadMode());
        assertTrue(reconstructorResult.blockers().contains("irgpu-entry-parameter-metadata-missing"));
        assertTrue(reconstructorResult.diagnostics().contains("irgpu-entry-jtg_kernel-parsed.statement.count=1"));
        assertTrue(reconstructorResult.diagnostics().stream().anyMatch(diagnostic -> diagnostic.startsWith("irgpu-entry-jtg_kernel-emitted.body.length=")));
    }

    @Test
    void openClSourceReconstructorAssemblesSimpleEntrySourceWhenParameterMetadataExists() {
        GpuKernelDescriptor descriptor = sampleDescriptor();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(irGpuArtifact(
                        descriptor.kernelResource(),
                        IrGpuRegenerationMetadata.backendNeutralReady(),
                        List.of(new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of()))
                ))
        );

        GpuBackendModuleArtifact artifact = GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL).lower(compileRequest);
        GpuBackendSourceReconstructionResult reconstructorResult = OpenClIrGpuSourceReconstructor.INSTANCE
                .reconstruct(compileRequest);

        assertEquals(descriptor.kernelSource(), artifact.source());
        assertEquals("irgpu-backend-neutral-source", artifact.sourceOrigin());
        assertTrue(reconstructorResult.ready());
        assertTrue(reconstructorResult.reconstructed());
        assertTrue(reconstructorResult.sourceAvailable());
        assertTrue(reconstructorResult.blockers().isEmpty());
        assertEquals("""
                __kernel void jtg_kernel(__global int* output) {
                    return output[0];
                }
                """, reconstructorResult.source());
        assertTrue(reconstructorResult.diagnostics().contains("OpenCL source assembler emitted entry kernel jtg_kernel"));
        assertTrue(reconstructorResult.diagnostics().contains("OpenCL source assembler emitted 1 entry parameter(s)"));
        assertTrue(reconstructorResult.diagnostics().contains("sourceParity.checked=true"));
        assertTrue(reconstructorResult.diagnostics().contains("sourceParity.matched=false"));
    }

    @Test
    void openClLowererKeepsDescriptorSourceByDefaultWhenIrGpuReconstructionIsReady() {
        GpuKernelDescriptor descriptor = roundTripDescriptor();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(roundTripIrGpuArtifact(descriptor.kernelResource()))
        );

        GpuBackendModuleArtifact artifact = GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL).lower(compileRequest);

        assertEquals(descriptor.kernelSource(), artifact.source());
        assertEquals(descriptor.kernelResource(), artifact.resource());
        assertEquals("irgpu-backend-neutral-source", artifact.sourceOrigin());
        assertEquals("opencl-irgpu-source-compile", artifact.runtimeLoadMode());
    }

    @Test
    void openClLowererUsesReconstructedIrGpuSourceOnlyWhenExplicitlyRequestedAndParityMatched() {
        GpuKernelDescriptor descriptor = roundTripDescriptor();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.openClIrGpuSourceReview(List.of()),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(roundTripIrGpuArtifact(descriptor.kernelResource()))
        );

        GpuBackendModuleArtifact artifact = GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL).lower(compileRequest);

        assertEquals(descriptor.kernelSource(), artifact.source());
        assertEquals(descriptor.kernelResource() + "#irgpu-reconstructed", artifact.resource());
        assertEquals("irgpu-backend-neutral-source", artifact.sourceOrigin());
        assertEquals("opencl-irgpu-source-compile", artifact.runtimeLoadMode());
    }

    @Test
    void openClLowererKeepsPackagedIrGpuDescriptorSourceByDefault() {
        GpuKernelDescriptor descriptor = simpleIrGpuSourceDescriptor();
        IrGpuArtifact artifact = GpuRuntimeIrArtifactLoader.load(SIMPLE_IRGPU_SOURCE_RESOURCE, getClass().getClassLoader())
                .orElseThrow();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(artifact)
        );

        GpuBackendModuleArtifact moduleArtifact = GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL).lower(compileRequest);

        assertEquals(descriptor.kernelSource(), moduleArtifact.source());
        assertEquals(descriptor.kernelResource(), moduleArtifact.resource());
        assertEquals("irgpu-backend-neutral-source", moduleArtifact.sourceOrigin());
        assertEquals("opencl-irgpu-source-compile", moduleArtifact.runtimeLoadMode());
    }

    @Test
    void openClLowererUsesPackagedIrGpuSourceInExplicitReviewMode() {
        GpuKernelDescriptor descriptor = simpleIrGpuSourceDescriptor();
        IrGpuArtifact artifact = GpuRuntimeIrArtifactLoader.load(SIMPLE_IRGPU_SOURCE_RESOURCE, getClass().getClassLoader())
                .orElseThrow();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.openClIrGpuSourceReview(List.of()),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(artifact)
        );

        GpuBackendModuleArtifact moduleArtifact = GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL).lower(compileRequest);

        assertEquals(descriptor.kernelSource(), moduleArtifact.source());
        assertEquals(descriptor.kernelResource() + "#irgpu-reconstructed", moduleArtifact.resource());
        assertEquals("irgpu-backend-neutral-source", moduleArtifact.sourceOrigin());
        assertEquals("opencl-irgpu-source-compile", moduleArtifact.runtimeLoadMode());
    }

    @Test
    void openClLowererUsesReconstructedPrivateArraySourceWhenExplicitlyRequestedAndParityMatched() {
        GpuKernelDescriptor descriptor = privateArrayRoundTripDescriptor();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.openClIrGpuSourceReview(List.of()),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(privateArrayRoundTripIrGpuArtifact(descriptor.kernelResource()))
        );

        GpuBackendModuleArtifact artifact = GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL).lower(compileRequest);

        assertEquals(descriptor.kernelSource(), artifact.source());
        assertEquals(descriptor.kernelResource() + "#irgpu-reconstructed", artifact.resource());
        assertEquals("irgpu-backend-neutral-source", artifact.sourceOrigin());
        assertEquals("opencl-irgpu-source-compile", artifact.runtimeLoadMode());
    }

    @Test
    void openClLowererRejectsProductionIrGpuSourceWhenProductionSwitchingIsDisabled() {
        GpuKernelDescriptor descriptor = roundTripDescriptor();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.openClIrGpuSource(List.of(), "vendor-tuned"),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(roundTripIrGpuArtifact(descriptor.kernelResource()))
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL).lower(compileRequest)
        );

        assertTrue(exception.getMessage().contains("production-like optimization profile 'vendor-tuned'"));
        assertTrue(exception.getMessage().contains("backend source switching is disabled"));
        assertTrue(exception.getMessage().contains("opencl.productionSourceSwitching=enabled"));
        assertTrue(exception.getMessage().contains("current decision mode=diagnostic-only"));
    }

    @Test
    void openClLowererRejectsProductionIrGpuSourceWhenDecisionIsNotProductionEnabled() {
        GpuKernelDescriptor descriptor = roundTripDescriptor();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of(), "vendor-tuned"),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(roundTripIrGpuArtifact(descriptor.kernelResource()))
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL).lower(compileRequest)
        );

        assertTrue(exception.getMessage().contains("production promotion decision mode is not production-enabled"));
        assertTrue(exception.getMessage().contains("current decision mode=diagnostic-only"));
    }

    @Test
    void openClLowererAllowsProductionIrGpuSourceOnlyWhenProductionSwitchingAndDecisionAreEnabled() {
        GpuKernelDescriptor descriptor = roundTripDescriptor();
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions
                .openClProductionIrGpuSource(List.of(), "vendor-tuned")
                .withProductionPromotionDecision(new GpuProductionPromotionDecision(
                        GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                        "production-ready",
                        true,
                        true,
                        true,
                        "none",
                        "none",
                        "production promotion is explicitly enabled by accepted evidence"
                ));
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                options,
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(roundTripIrGpuArtifact(descriptor.kernelResource()))
        );

        GpuBackendModuleArtifact artifact = GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL).lower(compileRequest);

        assertEquals(descriptor.kernelSource(), artifact.source());
        assertEquals(descriptor.kernelResource() + "#irgpu-reconstructed", artifact.resource());
        assertEquals("irgpu-backend-neutral-source", artifact.sourceOrigin());
        assertEquals("opencl-irgpu-source-compile", artifact.runtimeLoadMode());
    }

    @Test
    void openClLowererRejectsRequestedIrGpuSourceWhenParityHasNotMatched() {
        GpuKernelDescriptor descriptor = sampleDescriptor();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.openClIrGpuSourceReview(List.of()),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(irGpuArtifact(
                        descriptor.kernelResource(),
                        IrGpuRegenerationMetadata.backendNeutralReady(),
                        List.of(new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of()))
                ))
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL).lower(compileRequest)
        );

        assertTrue(exception.getMessage().contains("reconstructed source parity has not matched descriptor source"));
        assertTrue(exception.getMessage().contains("opencl.sourceSelection=irgpu"));
    }

    @Test
    void openClSourceReconstructorAssemblesFlatHelperSourceWhenHelperMetadataExists() {
        GpuKernelDescriptor descriptor = sampleDescriptor();
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(new IrGpuModuleMethod(
                                "square",
                                "jtg_fn_square_float",
                                "float",
                                List.of(new IrGpuEntryParameter("value", "float", "PRIVATE", false, List.of()))
                        )),
                        List.of(),
                        List.of(
                                IrGpuMethodBody.entry(
                                        "kernel",
                                        "jtg_kernel",
                                        "body\n  set output[0] = helper(jtg_fn_square_float args=[input[0]])\n  return\n",
                                        List.of("jtg_fn_square_float")
                                ),
                                IrGpuMethodBody.helper(
                                        "square",
                                        "jtg_fn_square_float",
                                        "body\n  return (value * value)\n",
                                        List.of()
                                )
                        )
                ),
                List.of(
                        new IrGpuEntryParameter("input", "float[]", "GLOBAL", false, List.of()),
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of())
                ),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(IrGpuBackendOutput.openClSource(descriptor.kernelResource())),
                "opencl",
                "off"
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(artifact)
        );

        GpuBackendSourceReconstructionResult reconstructorResult = OpenClIrGpuSourceReconstructor.INSTANCE
                .reconstruct(compileRequest);

        assertTrue(reconstructorResult.reconstructed());
        assertTrue(reconstructorResult.blockers().isEmpty());
        assertTrue(reconstructorResult.source().contains("float jtg_fn_square_float(float value)"));
        assertTrue(reconstructorResult.source().contains("return (value * value);"));
        assertTrue(reconstructorResult.source().contains("output[0] = jtg_fn_square_float(input[0]);"));
        assertTrue(reconstructorResult.source().contains("__kernel void jtg_kernel(__global float* input, __global float* output)"));
        assertTrue(reconstructorResult.diagnostics().contains("OpenCL source assembler emitted 1 helper function(s)"));
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
        GpuBackendSourceSelectionPlan sourceSelectionPlan = lowerer.sourceSelectionPlan(compileRequest);
        String backendName = backendTarget.name().toLowerCase(java.util.Locale.ROOT);
        assertEquals(backendTarget, sourceSelectionPlan.backendTarget());
        assertEquals(backendName + "-lowerer-unavailable", sourceSelectionPlan.selectedSource());
        assertEquals("irgpu-unlowered", sourceSelectionPlan.payloadFormat());
        assertEquals(backendName + "-unsupported", sourceSelectionPlan.runtimeLoadMode());
        assertTrue(sourceSelectionPlan.blockers().contains(backendName + "-lowerer-not-implemented"));
        assertTrue(sourceSelectionPlan.diagnostics().contains("GPU backend lowerer is not implemented for " + backendTarget));
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

    private static GpuKernelDescriptor roundTripDescriptor() {
        return new GpuKernelDescriptor(
                "jtg_kernel",
                "javatogpu/sample/Demo/kernel.cl",
                """
                        __kernel void jtg_kernel(__global int* output) {
                            output[0] = 1;
                        }
                        """,
                List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
    }

    private static GpuKernelDescriptor simpleIrGpuSourceDescriptor() {
        return new GpuKernelDescriptor(
                "gpu_irgpu_entry",
                "inline://integration/simple-irgpu-source-kernel.cl",
                """
                        __kernel void gpu_irgpu_entry(__global const float* input, float scale, __global float* output) {
                            int id = get_global_id(0);
                            output[id] = input[id] + scale;
                        }
                        """,
                SIMPLE_IRGPU_SOURCE_RESOURCE,
                List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scale", "float", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    private static GpuKernelDescriptor privateArrayRoundTripDescriptor() {
        return new GpuKernelDescriptor(
                "jtg_kernel",
                "javatogpu/sample/Demo/private-array-kernel.cl",
                """
                        __kernel void jtg_kernel(__global float* input, __global float* output) {
                            float scratch[4];
                            scratch[0] = input[0];
                            output[0] = scratch[0];
                            return;
                        }
                        """,
                List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    private static IrGpuArtifact irGpuArtifact(String derivedOpenClResource) {
        return irGpuArtifact(derivedOpenClResource, IrGpuRegenerationMetadata.transitionalIrText());
    }

    private static IrGpuArtifact irGpuArtifact(
            String derivedOpenClResource,
            IrGpuRegenerationMetadata regenerationMetadata
    ) {
        return irGpuArtifact(derivedOpenClResource, regenerationMetadata, List.of());
    }

    private static IrGpuArtifact irGpuArtifact(
            String derivedOpenClResource,
            IrGpuRegenerationMetadata regenerationMetadata,
            List<IrGpuEntryParameter> entryParameters
    ) {
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
                entryParameters,
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                regenerationMetadata,
                List.of(IrGpuBackendOutput.openClSource(derivedOpenClResource)),
                "opencl",
                "off"
        );
    }

    private static IrGpuArtifact roundTripIrGpuArtifact(String derivedOpenClResource) {
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

    private static IrGpuArtifact privateArrayRoundTripIrGpuArtifact(String derivedOpenClResource) {
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
                                """
                                        body
                                          private-array float scratch[4]
                                          set scratch[0] = input[0]
                                          set output[0] = scratch[0]
                                          return
                                        """,
                                List.of()
                        ))
                ),
                List.of(
                        new IrGpuEntryParameter("input", "float[]", "GLOBAL", false, List.of()),
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of())
                ),
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
