package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.types.floats.Float2;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModuleMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuStructFieldMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuStructMetadata;
import net.sixik.ga_utils.javatogpu.runtime.cuda.CudaBackendLowerer;
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
    private static final String IMAGE_KERNEL_IRGPU_RESOURCE = "javatogpu/runtime/opencl/integration/image-kernel.irgpu.properties";

    @Test
    void backendLowerersExposeStableExtensionMetadata() {
        GpuBackendLowerer lowerer = GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL);

        assertEquals("backend-lowerer:opencl", lowerer.extensionId());
        assertEquals(lowerer.lowererVersion(), lowerer.extensionVersion());
        assertEquals(java.util.Set.of(GpuExtensionCapability.BACKEND_LOWERING), lowerer.extensionCapabilities());
        assertEquals(GpuExtensionPhase.BACKEND_LOWERING, lowerer.extensionPhase());
        assertEquals(GpuExtensionPermission.PRODUCTION_AFFECTING, lowerer.extensionPermission());
    }

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
    void openClLowererProducesTypedLoweringStageResult() {
        GpuKernelDescriptor descriptor = sampleDescriptor();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL")
        );

        GpuBackendLoweringResult result = GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL)
                .lowerWithStageResult(compileRequest);
        java.util.Map<String, String> fields = result.artifactFields("lowering");

        assertTrue(result.lowered());
        assertEquals(GpuBackendPipelineStage.LOWER, result.stageResult().stage());
        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.stageResult().status());
        assertEquals(GpuBackendTarget.OPENCL, result.stageResult().backendTarget());
        assertEquals("descriptor-opencl-source", result.sourceSelectionPlan().selectedSource());
        assertEquals("opencl-c", result.moduleArtifact().format());
        assertEquals("true", fields.get("runtime.backend.lowering.present"));
        assertEquals("SUCCEEDED", fields.get("runtime.backend.lowering.status"));
        assertEquals("true", fields.get("runtime.backend.lowering.lowered"));
        assertEquals("opencl-c", fields.get("runtime.backend.lowering.module.format"));
        assertEquals("descriptor-opencl-source", fields.get("runtime.backend.lowering.selectedSource"));
        assertEquals("OPENCL", fields.get("runtime.backend.target"));
    }

    @Test
    void backendStageResultExposesPortableUnsupportedFields() {
        GpuBackendSourceSelectionPlan plan = GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA)
                .sourceSelectionPlan(new GpuRuntimeCompileRequest(
                        sampleDescriptor(),
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                        GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA")
                ));

        GpuBackendLoweringResult result = GpuBackendLoweringResult.unsupported(
                GpuBackendTarget.CUDA,
                plan,
                List.of("cuda-lowerer-not-implemented"),
                List.of("CUDA stays discovery-only until the execution backend lands")
        );
        java.util.Map<String, String> stageFields = result.stageResult().artifactFields("runtime.backend.stage");
        java.util.Map<String, String> loweringFields = result.artifactFields("lowering");

        assertTrue(!result.lowered());
        assertTrue(result.stageResult().blocked());
        assertEquals(GpuBackendPipelineStage.LOWER, result.stageResult().stage());
        assertEquals(GpuBackendStageStatus.UNSUPPORTED, result.stageResult().status());
        assertEquals("lower", stageFields.get("runtime.backend.stage.key"));
        assertEquals("UNSUPPORTED", stageFields.get("runtime.backend.stage.status"));
        assertEquals("CUDA", stageFields.get("runtime.backend.target"));
        assertEquals("lower-unsupported", stageFields.get("runtime.status"));
        assertEquals("UNSUPPORTED", loweringFields.get("runtime.backend.lowering.status"));
        assertEquals("false", loweringFields.get("runtime.backend.lowering.lowered"));
        assertEquals("cuda-irgpu-source-unavailable", loweringFields.get("runtime.backend.lowering.selectedSource"));
        assertEquals("CUDA", loweringFields.get("runtime.backend.target"));
    }

    @Test
    void backendCompilationPreparationAndInvocationResultsExposePortableFields() {
        GpuKernelDescriptor descriptor = sampleDescriptor();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL")
        );
        GpuBackendLoweringResult loweringResult = GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL)
                .lowerWithStageResult(compileRequest);
        GpuRuntimeBackendCompilationSummary compilationSummary = new GpuRuntimeBackendCompilationSummary(
                true,
                true,
                "opencl-c",
                true,
                1,
                2
        );
        GpuBackendCompilationResult compilationResult = GpuBackendCompilationResult.succeeded(
                loweringResult,
                compilationSummary,
                "test-cache-key",
                List.of("compiled from lowered OpenCL source")
        );
        GpuRuntimeInvocationBindingSummary bindingSummary = new GpuRuntimeInvocationBindingSummary(2, 1, 3, 6);
        GpuBackendPreparationResult preparationResult = GpuBackendPreparationResult.prepared(
                compilationResult,
                "opencl-kernel",
                bindingSummary,
                List.of("kernel arguments prepared")
        );
        GpuBackendInvocationResult invocationResult = GpuBackendInvocationResult.invoked(
                preparationResult,
                GpuExecutionConfig.twoDimensional(8, 4, 2, 2),
                2,
                2,
                List.of("kernel submitted")
        );

        java.util.Map<String, String> compilationFields = compilationResult.artifactFields("compile");
        java.util.Map<String, String> preparationFields = preparationResult.artifactFields("prepare");
        java.util.Map<String, String> invocationFields = invocationResult.artifactFields("invoke");

        assertTrue(compilationResult.compiled());
        assertEquals(GpuBackendPipelineStage.COMPILE, compilationResult.stageResult().stage());
        assertEquals(GpuBackendStageStatus.SUCCEEDED, compilationResult.stageResult().status());
        assertEquals("true", compilationFields.get("runtime.backend.compilation.compiled"));
        assertEquals("opencl-c", compilationFields.get("runtime.compilation.module.format"));
        assertEquals("true", compilationFields.get("compile.cacheKey.present"));
        assertEquals("OPENCL", compilationFields.get("runtime.backend.target"));
        assertTrue(preparationResult.prepared());
        assertEquals(GpuBackendPipelineStage.PREPARE, preparationResult.stageResult().stage());
        assertEquals("true", preparationFields.get("runtime.backend.prepare.prepared"));
        assertEquals("opencl-kernel", preparationFields.get("runtime.backend.prepare.kernel.kind"));
        assertEquals("6", preparationFields.get("prepare.binding.argument.count"));
        assertTrue(invocationResult.invoked());
        assertTrue(invocationResult.readbackComplete());
        assertEquals(GpuBackendPipelineStage.INVOKE, invocationResult.stageResult().stage());
        assertEquals("true", invocationFields.get("runtime.backend.invoke.invoked"));
        assertEquals("true", invocationFields.get("runtime.backend.invoke.readback.complete"));
        assertEquals("true", invocationFields.get("runtime.backend.prepare.present"));
        assertEquals("SUCCEEDED", invocationFields.get("runtime.backend.prepare.status"));
        assertEquals("true", invocationFields.get("runtime.backend.prepare.prepared"));
        assertEquals("opencl-kernel", invocationFields.get("runtime.backend.prepare.kernel.kind"));
        assertEquals("2", invocationFields.get("runtime.invocation.binding.buffer.count"));
        assertEquals("1", invocationFields.get("runtime.invocation.binding.local.count"));
        assertEquals("3", invocationFields.get("runtime.invocation.binding.scalar.count"));
        assertEquals("6", invocationFields.get("runtime.invocation.binding.argument.count"));
        assertEquals("2", invocationFields.get("invoke.work.dimensions"));
        assertEquals("8x4", invocationFields.get("invoke.work.globalShape"));
        assertEquals("2x2", invocationFields.get("invoke.work.localShape"));
        assertEquals("32", invocationFields.get("invoke.work.globalItem.count"));
        assertEquals("4", invocationFields.get("invoke.work.localItem.count"));
    }

    @Test
    void unsupportedBackendCompilationResultStaysBlockedAndPortable() {
        GpuBackendCompilationResult result = GpuBackendCompilationResult.unsupported(
                GpuBackendTarget.CUDA,
                null,
                List.of("cuda-compiler-not-implemented"),
                List.of("CUDA remains discovery-only until the execution adapter lands")
        );
        java.util.Map<String, String> fields = result.artifactFields("compile");

        assertTrue(!result.compiled());
        assertTrue(result.stageResult().blocked());
        assertEquals(GpuBackendPipelineStage.COMPILE, result.stageResult().stage());
        assertEquals(GpuBackendStageStatus.UNSUPPORTED, result.stageResult().status());
        assertEquals(GpuBackendTarget.CUDA, result.stageResult().backendTarget());
        assertEquals("UNSUPPORTED", fields.get("runtime.backend.compilation.status"));
        assertEquals("false", fields.get("runtime.backend.compilation.compiled"));
        assertEquals("unknown", fields.get("runtime.backend.compilation.module.format"));
        assertEquals("CUDA", fields.get("runtime.backend.target"));
        assertEquals("cuda-compiler-not-implemented", fields.get("compile.stage.blocker.0"));
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
    void cudaLowererProducesPreviewCudaSourceFromPackagedIrGpuArtifact() {
        GpuKernelDescriptor descriptor = simpleIrGpuSourceDescriptor();
        IrGpuArtifact artifact = GpuRuntimeIrArtifactLoader.load(SIMPLE_IRGPU_SOURCE_RESOURCE, getClass().getClassLoader())
                .orElseThrow();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA"),
                Optional.of(artifact)
        );

        GpuBackendLowerer lowerer = GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA);
        GpuBackendSourceSelectionPlan sourceSelectionPlan = lowerer.sourceSelectionPlan(compileRequest);
        GpuBackendLoweringResult result = lowerer.lowerWithStageResult(compileRequest);
        GpuBackendModuleArtifact moduleArtifact = result.moduleArtifact();

        assertEquals(GpuBackendTarget.CUDA, lowerer.backendTarget());
        assertEquals(CudaBackendLowerer.VERSION, lowerer.lowererVersion());
        assertTrue(sourceSelectionPlan.irGpuSourceSelected());
        assertEquals("irgpu-cuda-source", sourceSelectionPlan.selectedSource());
        assertEquals("ir-text-v1", sourceSelectionPlan.payloadFormat());
        assertEquals("cuda-c-source-preview", sourceSelectionPlan.runtimeLoadMode());
        assertTrue(sourceSelectionPlan.blockers().isEmpty());
        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.stageResult().status());
        assertTrue(result.lowered());
        assertEquals(GpuBackendTarget.CUDA, moduleArtifact.backendTarget());
        assertEquals("cuda-c", moduleArtifact.moduleFormat().key());
        assertEquals(descriptor.kernelResource() + "#irgpu-cuda-preview", moduleArtifact.resource());
        assertEquals("derived-cuda-source", moduleArtifact.sourceOrigin());
        assertTrue(moduleArtifact.source().contains("extern \"C\" __global__ void gpu_irgpu_entry("));
        assertTrue(moduleArtifact.source().contains("const float* input, float scale, float* output"));
        assertTrue(moduleArtifact.source().contains("blockIdx.x * blockDim.x + threadIdx.x"));
        assertTrue(moduleArtifact.source().contains("output[id] = input[id] + scale;"));
    }

    @Test
    void cudaLowererProducesPreviewCudaSourceForReadOnlyImage2dTextureReads() {
        GpuKernelDescriptor descriptor = readOnlyImageIrGpuSourceDescriptor();
        IrGpuArtifact artifact = readOnlyImageIrGpuArtifact();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA"),
                Optional.of(artifact)
        );

        GpuBackendLowerer lowerer = GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA);
        GpuBackendSourceSelectionPlan sourceSelectionPlan = lowerer.sourceSelectionPlan(compileRequest);
        GpuBackendLoweringResult result = lowerer.lowerWithStageResult(compileRequest);
        GpuBackendModuleArtifact moduleArtifact = result.moduleArtifact();

        assertTrue(sourceSelectionPlan.irGpuSourceSelected());
        assertEquals("irgpu-cuda-source", sourceSelectionPlan.selectedSource());
        assertTrue(sourceSelectionPlan.blockers().isEmpty());
        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.stageResult().status());
        assertTrue(result.lowered());
        assertTrue(moduleArtifact.source().contains("#include <cuda_runtime.h>"));
        assertTrue(moduleArtifact.source().contains("extern \"C\" __global__ void gpu_read_image_entry(cudaTextureObject_t inputImage, int __jtg_cuda_image_inputImage_width, int __jtg_cuda_image_inputImage_height, int* output)"));
        assertTrue(!moduleArtifact.source().contains("Sampler sampler"));
        assertTrue(!moduleArtifact.source().contains("sampler,"));
        assertTrue(moduleArtifact.source().contains("int4 pixel = tex2D<int4>(inputImage, (float)((coords).x), (float)((coords).y));"));
        assertTrue(moduleArtifact.source().contains("output[id] = pixel.x + __jtg_cuda_image_inputImage_width + __jtg_cuda_image_inputImage_height;"));
    }

    @Test
    void cudaLowererProducesPreviewCudaSourceForImage2dTextureReadsAndSurfaceWrites() {
        GpuKernelDescriptor descriptor = imageIrGpuSourceDescriptor();
        IrGpuArtifact artifact = GpuRuntimeIrArtifactLoader.load(IMAGE_KERNEL_IRGPU_RESOURCE, getClass().getClassLoader())
                .orElseThrow();
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA"),
                Optional.of(artifact)
        );

        GpuBackendLowerer lowerer = GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA);
        GpuBackendSourceSelectionPlan sourceSelectionPlan = lowerer.sourceSelectionPlan(compileRequest);
        GpuBackendLoweringResult result = lowerer.lowerWithStageResult(compileRequest);
        GpuBackendModuleArtifact moduleArtifact = result.moduleArtifact();

        assertTrue(sourceSelectionPlan.irGpuSourceSelected());
        assertEquals("irgpu-cuda-source", sourceSelectionPlan.selectedSource());
        assertTrue(sourceSelectionPlan.blockers().isEmpty());
        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.stageResult().status());
        assertTrue(result.lowered());
        assertTrue(moduleArtifact.source().contains("extern \"C\" __global__ void gpu_image_entry(cudaTextureObject_t inputImage, int __jtg_cuda_image_inputImage_width, int __jtg_cuda_image_inputImage_height, cudaSurfaceObject_t outputImage, int __jtg_cuda_image_outputImage_width, int __jtg_cuda_image_outputImage_height, int* output)"));
        assertTrue(!moduleArtifact.source().contains("Sampler sampler"));
        assertTrue(moduleArtifact.source().contains("int4 pixel = tex2D<int4>(inputImage, (float)((coords).x), (float)((coords).y));"));
        assertTrue(moduleArtifact.source().contains("surf2Dwrite(make_float4(1.0f, 0.5f, 0.25f, 1.0f), outputImage, (int)(((coords).x) * sizeof(float4)), (coords).y);"));
    }

    @Test
    void cudaLowererFailsClosedForNon2dImageSourcePreview() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_image3d_entry",
                "inline://tests/cuda-image3d-preview.cl",
                "__kernel void gpu_image3d_entry(read_only image3d_t inputImage, __global float* output) { }",
                List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image3DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "image3dKernel",
                        "gpu_image3d_entry",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry(
                                "image3dKernel",
                                "gpu_image3d_entry",
                                "body\n  set output[0] = 1.0\n",
                                List.of()
                        ))
                ),
                List.of(
                        new IrGpuEntryParameter("inputImage", "Image3DReadOnly", "PRIVATE", false, List.of()),
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of())
                ),
                List.of(IrGpuBackendOutput.openClSource("inline://tests/cuda-image3d-preview.cl")),
                "opencl",
                "off"
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA"),
                Optional.of(artifact)
        );

        GpuBackendSourceSelectionPlan sourceSelectionPlan = GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA)
                .sourceSelectionPlan(compileRequest);

        assertTrue(!sourceSelectionPlan.irGpuSourceSelected());
        assertEquals("cuda-irgpu-source-unavailable", sourceSelectionPlan.selectedSource());
        assertTrue(sourceSelectionPlan.blockers().contains("cuda-image-sampler-source-lowering-pending:inputImage:Image3DReadOnly"));
    }

    @Test
    void cudaLowererMapsSingleLocalParameterToDynamicSharedMemoryDeclaration() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_local_entry",
                "inline://tests/cuda-local-preview.cu",
                "__kernel void gpu_local_entry(__global const float* input, __local float* scratch, __global float* output) { }",
                List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("scratch", "float[]", GpuKernelParameterAccess.LOCAL),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "localKernel",
                        "gpu_local_entry",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry(
                                "localKernel",
                                "gpu_local_entry",
                                "body\n  set scratch[0] = input[0]\n  set output[0] = scratch[0]\n",
                                List.of()
                        ))
                ),
                List.of(
                        new IrGpuEntryParameter("input", "float[]", "GLOBAL", true, List.of("const")),
                        new IrGpuEntryParameter("scratch", "float[]", "LOCAL", false, List.of()),
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of())
                ),
                List.of(IrGpuBackendOutput.openClSource("inline://tests/cuda-local-preview.cl")),
                "opencl",
                "off"
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA"),
                Optional.of(artifact)
        );

        GpuBackendModuleArtifact moduleArtifact = GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA).lower(compileRequest);

        assertTrue(moduleArtifact.source().contains("extern \"C\" __global__ void gpu_local_entry(const float* input, float* output)"));
        assertTrue(moduleArtifact.source().contains("extern __shared__ float scratch[];"));
        assertTrue(!moduleArtifact.source().contains("float* scratch"));
        assertTrue(moduleArtifact.source().contains("scratch[0] = input[0];"));
    }

    @Test
    void cudaLowererMapsMultipleLocalParametersToSharedMemorySlices() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_multi_local_entry",
                "inline://tests/cuda-multi-local-preview.cu",
                "__kernel void gpu_multi_local_entry(__local float* scratchA, __local int* scratchB) { }",
                List.of(
                        new GpuKernelParameterDescriptor("scratchA", "float[]", GpuKernelParameterAccess.LOCAL),
                        new GpuKernelParameterDescriptor("scratchB", "int[]", GpuKernelParameterAccess.LOCAL)
                )
        );
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "multiLocalKernel",
                        "gpu_multi_local_entry",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry(
                                "multiLocalKernel",
                                "gpu_multi_local_entry",
                                "body\n  set scratchA[0] = 1.0\n  set scratchB[0] = 2\n",
                                List.of()
                        ))
                ),
                List.of(
                        new IrGpuEntryParameter("scratchA", "float[]", "LOCAL", false, List.of()),
                        new IrGpuEntryParameter("scratchB", "int[]", "LOCAL", false, List.of())
                ),
                List.of(IrGpuBackendOutput.openClSource("inline://tests/cuda-multi-local-preview.cl")),
                "opencl",
                "off"
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA"),
                Optional.of(artifact)
        );

        GpuBackendModuleArtifact moduleArtifact = GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA).lower(compileRequest);

        assertTrue(moduleArtifact.source().contains("extern \"C\" __global__ void gpu_multi_local_entry(unsigned int __jtg_local_scratchA_byte_offset, unsigned int __jtg_local_scratchB_byte_offset)"));
        assertTrue(moduleArtifact.source().contains("extern __shared__ __align__(16) unsigned char __jtg_cuda_dynamic_shared[];"));
        assertTrue(moduleArtifact.source().contains("float* scratchA = (float*)(__jtg_cuda_dynamic_shared + __jtg_local_scratchA_byte_offset);"));
        assertTrue(moduleArtifact.source().contains("int* scratchB = (int*)(__jtg_cuda_dynamic_shared + __jtg_local_scratchB_byte_offset);"));
        assertTrue(!moduleArtifact.source().contains("float* scratchA,"));
        assertTrue(moduleArtifact.source().contains("scratchA[0] = 1.0;"));
        assertTrue(moduleArtifact.source().contains("scratchB[0] = 2;"));
    }

    @Test
    void cudaLowererMapsStructLocalParameterToDynamicSharedMemoryDeclaration() {
        String structType = "net.example.CudaParticle";
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_struct_local_entry",
                "inline://tests/cuda-struct-local-preview.cu",
                "__kernel void gpu_struct_local_entry(__local CudaParticle* scratch, __global float* output) { }",
                List.of(
                        new GpuKernelParameterDescriptor("scratch", structType + "[]", GpuKernelParameterAccess.LOCAL),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "structLocalKernel",
                        "gpu_struct_local_entry",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry(
                                "structLocalKernel",
                                "gpu_struct_local_entry",
                                "body\n  set scratch[0].weight = 2.0\n  set output[0] = scratch[0].weight\n",
                                List.of()
                        ))
                ),
                List.of(
                        new IrGpuEntryParameter("scratch", structType + "[]", "LOCAL", false, List.of()),
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of())
                ),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(new IrGpuStructMetadata(
                        structType,
                        "CudaParticle",
                        List.of(
                                new IrGpuStructFieldMetadata("weight", "float", List.of()),
                                new IrGpuStructFieldMetadata("normal", Float2.class.getName(), List.of())
                        ),
                        List.of()
                )),
                List.of(),
                List.of(),
                List.of(IrGpuBackendOutput.openClSource("inline://tests/cuda-struct-local-preview.cl")),
                "opencl",
                "off"
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA"),
                Optional.of(artifact)
        );

        GpuBackendModuleArtifact moduleArtifact = GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA).lower(compileRequest);

        assertTrue(moduleArtifact.source().contains("typedef struct{\n    float weight;\n    float2 normal;\n} CudaParticle;"));
        assertTrue(moduleArtifact.source().contains("extern \"C\" __global__ void gpu_struct_local_entry(float* output)"));
        assertTrue(moduleArtifact.source().contains("extern __shared__ CudaParticle scratch[];"));
        assertTrue(moduleArtifact.source().contains("scratch[0].weight = 2.0;"));
        assertTrue(moduleArtifact.source().contains("output[0] = scratch[0].weight;"));
    }

    @Test
    void cudaLowererMapsMixedStructLocalParametersToSharedMemorySlices() {
        String structType = "net.example.CudaParticle";
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_mixed_struct_local_entry",
                "inline://tests/cuda-mixed-struct-local-preview.cu",
                "__kernel void gpu_mixed_struct_local_entry(__local float* scratchA, __local CudaParticle* scratchB) { }",
                List.of(
                        new GpuKernelParameterDescriptor("scratchA", "float[]", GpuKernelParameterAccess.LOCAL),
                        new GpuKernelParameterDescriptor("scratchB", structType + "[]", GpuKernelParameterAccess.LOCAL)
                )
        );
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "mixedStructLocalKernel",
                        "gpu_mixed_struct_local_entry",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry(
                                "mixedStructLocalKernel",
                                "gpu_mixed_struct_local_entry",
                                "body\n  set scratchA[0] = 1.0\n  set scratchB[0].weight = scratchA[0]\n",
                                List.of()
                        ))
                ),
                List.of(
                        new IrGpuEntryParameter("scratchA", "float[]", "LOCAL", false, List.of()),
                        new IrGpuEntryParameter("scratchB", structType + "[]", "LOCAL", false, List.of())
                ),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(new IrGpuStructMetadata(
                        structType,
                        "CudaParticle",
                        List.of(new IrGpuStructFieldMetadata("weight", "float", List.of())),
                        List.of()
                )),
                List.of(),
                List.of(),
                List.of(IrGpuBackendOutput.openClSource("inline://tests/cuda-mixed-struct-local-preview.cl")),
                "opencl",
                "off"
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA"),
                Optional.of(artifact)
        );

        GpuBackendModuleArtifact moduleArtifact = GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA).lower(compileRequest);

        assertTrue(moduleArtifact.source().contains("extern \"C\" __global__ void gpu_mixed_struct_local_entry(unsigned int __jtg_local_scratchA_byte_offset, unsigned int __jtg_local_scratchB_byte_offset)"));
        assertTrue(moduleArtifact.source().contains("extern __shared__ __align__(16) unsigned char __jtg_cuda_dynamic_shared[];"));
        assertTrue(moduleArtifact.source().contains("float* scratchA = (float*)(__jtg_cuda_dynamic_shared + __jtg_local_scratchA_byte_offset);"));
        assertTrue(moduleArtifact.source().contains("CudaParticle* scratchB = (CudaParticle*)(__jtg_cuda_dynamic_shared + __jtg_local_scratchB_byte_offset);"));
        assertTrue(moduleArtifact.source().contains("scratchB[0].weight = scratchA[0];"));
    }

    @Test
    void cudaLowererMapsVectorArrayParametersToCudaVectorPointers() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_vector_array_entry",
                "inline://tests/cuda-vector-array-preview.cu",
                "__kernel void gpu_vector_array_entry(__global const float2* input, __global float2* output) { }",
                List.of(
                        new GpuKernelParameterDescriptor("input", Float2.class.getName() + "[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", Float2.class.getName() + "[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "vectorArrayKernel",
                        "gpu_vector_array_entry",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry(
                                "vectorArrayKernel",
                                "gpu_vector_array_entry",
                                "body\n  set output[0] = input[0]\n",
                                List.of()
                        ))
                ),
                List.of(
                        new IrGpuEntryParameter("input", Float2.class.getName() + "[]", "GLOBAL", true, List.of("const")),
                        new IrGpuEntryParameter("output", Float2.class.getName() + "[]", "GLOBAL", false, List.of())
                ),
                List.of(IrGpuBackendOutput.openClSource("inline://tests/cuda-vector-array-preview.cl")),
                "opencl",
                "off"
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA"),
                Optional.of(artifact)
        );

        GpuBackendModuleArtifact moduleArtifact = GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA).lower(compileRequest);

        assertTrue(moduleArtifact.source().contains("extern \"C\" __global__ void gpu_vector_array_entry(const float2* input, float2* output)"));
        assertTrue(moduleArtifact.source().contains("output[0] = input[0];"));
    }

    @Test
    void cudaLowererEmitsStructTypedefAndStructArrayPointers() {
        String structType = "net.example.CudaParticle";
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_struct_array_entry",
                "inline://tests/cuda-struct-array-preview.cu",
                "__kernel void gpu_struct_array_entry(__global const CudaParticle* input, __global CudaParticle* output) { }",
                List.of(
                        new GpuKernelParameterDescriptor("input", structType + "[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", structType + "[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "structArrayKernel",
                        "gpu_struct_array_entry",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry(
                                "structArrayKernel",
                                "gpu_struct_array_entry",
                                "body\n  set output[0] = input[0]\n",
                                List.of()
                        ))
                ),
                List.of(
                        new IrGpuEntryParameter("input", structType + "[]", "GLOBAL", true, List.of("const")),
                        new IrGpuEntryParameter("output", structType + "[]", "GLOBAL", false, List.of())
                ),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(new IrGpuStructMetadata(
                        structType,
                        "CudaParticle",
                        List.of(
                                new IrGpuStructFieldMetadata("weight", "float", List.of()),
                                new IrGpuStructFieldMetadata("normal", Float2.class.getName(), List.of())
                        ),
                        List.of()
                )),
                List.of(),
                List.of(),
                List.of(IrGpuBackendOutput.openClSource("inline://tests/cuda-struct-array-preview.cl")),
                "opencl",
                "off"
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA"),
                Optional.of(artifact)
        );

        GpuBackendModuleArtifact moduleArtifact = GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA).lower(compileRequest);

        assertTrue(moduleArtifact.source().contains("typedef struct{\n    float weight;\n    float2 normal;\n} CudaParticle;"));
        assertTrue(moduleArtifact.source().contains("extern \"C\" __global__ void gpu_struct_array_entry(const CudaParticle* input, CudaParticle* output)"));
        assertTrue(moduleArtifact.source().contains("output[0] = input[0];"));
    }

    @Test
    void cudaLowererEmitsStructTypedefAndStructValueParameter() {
        String structType = "net.example.CudaParticle";
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "gpu_struct_value_entry",
                "inline://tests/cuda-struct-value-preview.cu",
                "__kernel void gpu_struct_value_entry(CudaParticle particle, __global float* output) { }",
                List.of(
                        new GpuKernelParameterDescriptor("particle", structType, GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "structValueKernel",
                        "gpu_struct_value_entry",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry(
                                "structValueKernel",
                                "gpu_struct_value_entry",
                                "body\n  set output[0] = particle.weight\n",
                                List.of()
                        ))
                ),
                List.of(
                        new IrGpuEntryParameter("particle", structType, "PRIVATE", false, List.of()),
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of())
                ),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(new IrGpuStructMetadata(
                        structType,
                        "CudaParticle",
                        List.of(
                                new IrGpuStructFieldMetadata("weight", "float", List.of()),
                                new IrGpuStructFieldMetadata("normal", Float2.class.getName(), List.of())
                        ),
                        List.of()
                )),
                List.of(),
                List.of(),
                List.of(IrGpuBackendOutput.openClSource("inline://tests/cuda-struct-value-preview.cl")),
                "opencl",
                "off"
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA"),
                Optional.of(artifact)
        );

        GpuBackendModuleArtifact moduleArtifact = GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA).lower(compileRequest);

        assertTrue(moduleArtifact.source().contains("typedef struct{\n    float weight;\n    float2 normal;\n} CudaParticle;"));
        assertTrue(moduleArtifact.source().contains("extern \"C\" __global__ void gpu_struct_value_entry(CudaParticle particle, float* output)"));
        assertTrue(moduleArtifact.source().contains("output[0] = particle.weight;"));
    }

    @Test
    void cudaLowererReportsMissingIrGpuArtifactWithoutExecution() {
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                sampleDescriptor(),
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA")
        );

        GpuBackendLowerer lowerer = GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA);
        GpuBackendSourceSelectionPlan sourceSelectionPlan = lowerer.sourceSelectionPlan(compileRequest);
        GpuBackendLoweringResult result = lowerer.lowerWithStageResult(compileRequest);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> lowerer.lower(compileRequest));

        assertEquals(GpuBackendTarget.CUDA, lowerer.backendTarget());
        assertEquals(CudaBackendLowerer.VERSION, lowerer.lowererVersion());
        assertTrue(!sourceSelectionPlan.irGpuSourceSelected());
        assertEquals("cuda-irgpu-source-unavailable", sourceSelectionPlan.selectedSource());
        assertEquals("irgpu-missing", sourceSelectionPlan.payloadFormat());
        assertEquals("cuda-source-preview-unavailable", sourceSelectionPlan.runtimeLoadMode());
        assertTrue(sourceSelectionPlan.blockers().contains("cuda-irgpu-artifact-missing"));
        assertEquals(GpuBackendStageStatus.UNSUPPORTED, result.stageResult().status());
        assertTrue(!result.lowered());
        assertTrue(exception.getMessage().contains("cuda-irgpu-artifact-missing"));
    }

    @Test
    void cudaLowererAssemblesFlatHelperSourceWhenHelperMetadataExists() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "jtg_kernel",
                "inline://cuda/helper-kernel.cu",
                "",
                List.of(
                        new GpuKernelParameterDescriptor("input", "float[]", GpuKernelParameterAccess.READ_ONLY),
                        new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
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
                List.of(IrGpuBackendOutput.openClSource("inline://cuda/helper-kernel.cl")),
                "opencl",
                "off"
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA"),
                Optional.of(artifact)
        );

        GpuBackendLoweringResult result = GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA)
                .lowerWithStageResult(compileRequest);
        String source = result.moduleArtifact().source();

        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.stageResult().status());
        assertTrue(result.lowered());
        assertTrue(source.contains("__device__ float jtg_fn_square_float(float value);"));
        assertTrue(source.contains("__device__ float jtg_fn_square_float(float value) {"));
        assertTrue(source.contains("return (value * value);"));
        assertTrue(source.contains("extern \"C\" __global__ void jtg_kernel(float* input, float* output)"));
        assertTrue(source.contains("output[0] = jtg_fn_square_float(input[0]);"));
        assertTrue(result.stageResult().diagnostics().contains("CUDA source assembler emitted 1 helper function(s)"));
    }

    @Test
    void cudaLowererMatchesHelperByFallbackNameWhenEmittedNameIsBlank() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "jtg_kernel",
                "inline://cuda/fallback-helper-name.cu",
                "",
                List.of(new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE))
        );
        IrGpuArtifact artifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(new IrGpuModuleMethod(
                                "square",
                                "",
                                "float",
                                List.of(new IrGpuEntryParameter("value", "float", "PRIVATE", false, List.of()))
                        )),
                        List.of(),
                        List.of(
                                IrGpuMethodBody.entry(
                                        "kernel",
                                        "jtg_kernel",
                                        "body\n  set output[0] = helper(square args=[2.0F])\n  return\n",
                                        List.of("square")
                                ),
                                IrGpuMethodBody.helper(
                                        "square",
                                        "",
                                        "body\n  return (value * value)\n",
                                        List.of()
                                )
                        )
                ),
                List.of(new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of())),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(IrGpuBackendOutput.openClSource("inline://cuda/fallback-helper-name.cl")),
                "opencl",
                "off"
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA"),
                Optional.of(artifact)
        );

        GpuBackendLoweringResult result = GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA)
                .lowerWithStageResult(compileRequest);
        String source = result.moduleArtifact().source();

        assertEquals(GpuBackendStageStatus.SUCCEEDED, result.stageResult().status());
        assertTrue(result.lowered());
        assertTrue(source.contains("__device__ float square(float value);"));
        assertTrue(source.contains("__device__ float square(float value) {"));
        assertTrue(source.contains("output[0] = square(2.0F);"));
    }

    @Test
    void cudaLowererReportsMissingHelperBodyAsStructuredUnsupported() {
        GpuKernelDescriptor descriptor = new GpuKernelDescriptor(
                "jtg_kernel",
                "inline://cuda/missing-helper-body.cu",
                "",
                List.of(new GpuKernelParameterDescriptor("output", "float[]", GpuKernelParameterAccess.READ_WRITE))
        );
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
                        List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "jtg_kernel",
                                "body\n  set output[0] = helper(jtg_fn_square_float args=[2.0F])\n  return\n",
                                List.of("jtg_fn_square_float")
                        ))
                ),
                List.of(new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of())),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(IrGpuBackendOutput.openClSource("inline://cuda/missing-helper-body.cl")),
                "opencl",
                "off"
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.CUDA),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.CUDA, "CUDA"),
                Optional.of(artifact)
        );

        GpuBackendLoweringResult result = GpuBackendLowerers.forTarget(GpuBackendTarget.CUDA)
                .lowerWithStageResult(compileRequest);

        assertEquals(GpuBackendStageStatus.UNSUPPORTED, result.stageResult().status());
        assertTrue(!result.lowered());
        assertTrue(result.stageResult().blockers().contains("cuda-entry-helper-dependency-body-missing:jtg_fn_square_float"));
        assertTrue(result.stageResult().blockers().contains("cuda-helper-jtg_fn_square_float-body-missing"));
        assertTrue(result.stageResult().diagnostics().contains(
                "CUDA IrGpu source reconstruction is limited to simple entry/helper kernels for this slice"
        ));
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
    void openClLowererRejectsLegacyOperatorBooleanWithoutIdentityBinding() {
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
                        "test production decision"
                ))
                .withProductionPromotionOperatorAccepted(true);
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                options,
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(roundTripIrGpuArtifact(descriptor.kernelResource()))
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL).lower(compileRequest)
        );

        assertTrue(exception.getMessage().contains("operator-acceptance-binding-missing"));
    }

    @Test
    void openClLowererRejectsProductionIrGpuSourceWithoutActivationToken() {
        GpuKernelDescriptor descriptor = roundTripDescriptor();
        GpuRuntimeDeviceProfile deviceProfile = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "Mock GPU",
                "Mock Vendor",
                "1.0",
                "OpenCL 3.0 Mock",
                -1L,
                32_768L,
                256L,
                -1L,
                true,
                true,
                true
        );
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
        options = options.withProductionPromotionOperatorAcceptance(
                GpuProductionPromotionOperatorAcceptance.forContext(
                        "acceptance:test-lowerer-no-activation",
                        GpuBackendTarget.OPENCL,
                        deviceProfile,
                        options.optimizationProfile(),
                        descriptor,
                        options.backendOptions().productionPromotionDecisionMode()
                )
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                options,
                deviceProfile,
                Optional.of(roundTripIrGpuArtifact(descriptor.kernelResource()))
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> GpuBackendLowerers.forTarget(GpuBackendTarget.OPENCL).lower(compileRequest)
        );

        assertTrue(exception.getMessage().contains("production activation token was not accepted"));
        assertTrue(exception.getMessage().contains("production-activation-token-missing"));
    }

    @Test
    void openClLowererAllowsProductionIrGpuSourceOnlyWhenProductionSwitchingAndDecisionAreEnabled() {
        GpuKernelDescriptor descriptor = roundTripDescriptor();
        GpuRuntimeDeviceProfile deviceProfile = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "Mock GPU",
                "Mock Vendor",
                "1.0",
                "OpenCL 3.0 Mock",
                -1L,
                32_768L,
                256L,
                -1L,
                true,
                true,
                true
        );
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
        options = options.withProductionPromotionOperatorAcceptance(
                GpuProductionPromotionOperatorAcceptance.forContext(
                        "acceptance:test-lowerer",
                        GpuBackendTarget.OPENCL,
                        deviceProfile,
                        options.optimizationProfile(),
                        descriptor,
                        options.backendOptions().productionPromotionDecisionMode()
                )
        );
        options = options.withProductionActivationToken(
                GpuProductionActivationTokenTestFixtures.token(deviceProfile, descriptor.kernelResource())
        );
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor,
                options,
                deviceProfile,
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

    private static GpuKernelDescriptor imageIrGpuSourceDescriptor() {
        return new GpuKernelDescriptor(
                "gpu_image_entry",
                "inline://integration/image-kernel.cl",
                """
                        __kernel void gpu_image_entry(read_only image2d_t inputImage, write_only image2d_t outputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            int4 pixel = read_imagei(inputImage, sampler, coords);
                            output[id] = pixel.x + pixel.y + pixel.z + pixel.w;
                            write_imagef(outputImage, coords, (float4)(1.0f, 0.5f, 0.25f, 1.0f));
                        }
                        """,
                IMAGE_KERNEL_IRGPU_RESOURCE,
                List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("outputImage", "Image2DWriteOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    private static GpuKernelDescriptor readOnlyImageIrGpuSourceDescriptor() {
        return new GpuKernelDescriptor(
                "gpu_read_image_entry",
                "inline://tests/cuda-read-image-preview.cl",
                """
                        __kernel void gpu_read_image_entry(read_only image2d_t inputImage, sampler_t sampler, __global int* output) {
                            int id = get_global_id(0);
                            int2 coords = (int2)(id, 0);
                            int4 pixel = read_imagei(inputImage, sampler, coords);
                            output[id] = pixel.x + get_image_width(inputImage) + get_image_height(inputImage);
                        }
                        """,
                List.of(
                        new GpuKernelParameterDescriptor("inputImage", "Image2DReadOnly", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("sampler", "Sampler", GpuKernelParameterAccess.VALUE),
                        new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE)
                )
        );
    }

    private static IrGpuArtifact readOnlyImageIrGpuArtifact() {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "readImageKernel",
                        "gpu_read_image_entry",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry(
                                "readImageKernel",
                                "gpu_read_image_entry",
                                """
                                        method gpu_read_image_entry source=kernel
                                        helpers -
                                        body
                                          var int id = intrinsic(get_global_id template="" args=[0])
                                          var int2 coords = init<int2>(id, 0)
                                          var int4 pixel = intrinsic(read_imagei template="read_imagei({0}, {1}, {2})" args=[inputImage, sampler, coords])
                                          set output[id] = pixel.x + intrinsic(get_image_width template="get_image_width({0})" args=[inputImage]) + intrinsic(get_image_height template="get_image_height({0})" args=[inputImage])
                                        """,
                                List.of()
                        ))
                ),
                List.of(
                        new IrGpuEntryParameter("inputImage", "Image2DReadOnly", "PRIVATE", false, List.of()),
                        new IrGpuEntryParameter("sampler", "Sampler", "PRIVATE", false, List.of()),
                        new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of())
                ),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(),
                List.of(),
                List.of(),
                List.of(IrGpuBackendOutput.openClSource("inline://tests/cuda-read-image-preview.cl")),
                "opencl",
                "off"
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
