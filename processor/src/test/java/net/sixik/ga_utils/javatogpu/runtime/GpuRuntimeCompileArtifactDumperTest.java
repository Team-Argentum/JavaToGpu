package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionReport;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionFailurePolicy;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuExtensionParticipationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeCompileArtifactDumperTest {

    private static final String SIMPLE_IRGPU_SOURCE_RESOURCE = "javatogpu/runtime/opencl/integration/simple-irgpu-source-kernel.irgpu.properties";

    @Test
    void dumpsRuntimeExtensionParticipationArtifact() {
        GpuExtensionExecutionReport optimizerExecution = execution(
                "optimizer:cse",
                GpuExtensionPhase.RUNTIME_IR_OPTIMIZATION,
                GpuExtensionPermission.MUTATION_PROPOSAL,
                GpuExtensionExecutionOutcome.SUCCEEDED,
                true
        );
        GpuExtensionExecutionReport deviceSelectionExecution = execution(
                "device-policy:test",
                GpuExtensionPhase.DEVICE_SELECTION,
                GpuExtensionPermission.READ_ONLY,
                GpuExtensionExecutionOutcome.SUCCEEDED,
                true
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.legacy(descriptor())
                .withDeviceSelection(syntheticDeviceSelection(deviceSelectionExecution))
                .withOptimizationReport(new GpuRuntimeIrOptimizationReport(
                        Optional.empty(),
                        List.of(),
                        GpuOptimizationStrategyDecision.none(null),
                        List.of(optimizerExecution)
                ))
                .withCompileLog("mock compiler resource log");
        GpuBackendCompilerFeedbackRegistry compilerFeedbackRegistry = GpuBackendCompilerFeedbackRegistry.of(List.of(
                new GpuBackendCompilerFeedbackProvider() {
                    @Override
                    public String extensionId() {
                        return "compiler-feedback:mock";
                    }

                    @Override
                    public Optional<GpuBackendCompilerFeedback> inspect(GpuBackendCompilerFeedbackRequest request) {
                        throw new IllegalStateException("mock feedback unavailable");
                    }
                }
        ));

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot, compilerFeedbackRegistry);

        assertTrue(dump.hasArtifact(GpuRuntimeCompileArtifactDumper.RUNTIME_EXTENSION_PARTICIPATION_ARTIFACT));
        String participation = dump.artifact(GpuRuntimeCompileArtifactDumper.RUNTIME_EXTENSION_PARTICIPATION_ARTIFACT);
        assertTrue(participation.contains("status=recorded"));
        assertTrue(participation.contains("backendTarget=OPENCL"));
        assertTrue(participation.contains("backendFormat=opencl-c"));
        assertTrue(participation.contains("backendResource=javatogpu/sample/Demo/kernel.cl"));
        assertTrue(participation.contains("entry.count=5"));
        assertTrue(participation.contains("succeeded.count=3"));
        assertTrue(participation.contains("skipped.count=1"));
        assertTrue(participation.contains("failedContinued.count=1"));
        assertTrue(participation.contains("failedClosed.count=0"));
        assertTrue(participation.contains("pipelineContinued.all=true"));
        assertTrue(participation.contains("firstFailure=compiler-feedback:mock:FAILED_CONTINUED"));
        assertTrue(participation.contains("entry.0.source=device-selection"));
        assertTrue(participation.contains("entry.0.extensionId=device-policy:test"));
        assertTrue(participation.contains("entry.1.source=runtime-ir-optimization"));
        assertTrue(participation.contains("entry.1.extensionId=optimizer:cse"));
        assertTrue(participation.contains("entry.2.source=runtime-equivalence"));
        assertTrue(participation.contains("entry.2.extensionId=runtime-equivalence:unknown"));
        assertTrue(participation.contains("entry.2.phase=RUNTIME_EQUIVALENCE"));
        assertTrue(participation.contains("entry.2.permission=READ_ONLY"));
        assertTrue(participation.contains("entry.2.outcome=SKIPPED"));
        assertTrue(participation.contains("entry.3.source=backend-lowerer"));
        assertTrue(participation.contains("entry.3.extensionId=backend-lowerer:opencl"));
        assertTrue(participation.contains("entry.3.phase=BACKEND_LOWERING"));
        assertTrue(participation.contains("entry.3.permission=PRODUCTION_AFFECTING"));
        assertTrue(participation.contains("entry.3.outcome=SUCCEEDED"));
        assertTrue(participation.contains("entry.4.source=backend-compiler-feedback"));
        assertTrue(participation.contains("entry.4.extensionId=compiler-feedback:mock"));
        assertTrue(participation.contains("entry.4.outcome=FAILED_CONTINUED"));
    }

    @Test
    void dumpsIrGpuExtensionParticipationIntoRuntimeParticipationArtifact() {
        IrGpuArtifact irGpuArtifact = artifact("body\n  return original\n")
                .withExtensionParticipationMetadata(List.of(new IrGpuExtensionParticipationMetadata(
                        "ir-validation",
                        "validator:shape-contract",
                        "3",
                        GpuExtensionPhase.IR_VALIDATION,
                        GpuExtensionPermission.READ_ONLY,
                        "IR validation",
                        GpuExtensionExecutionOutcome.SUCCEEDED,
                        GpuExtensionFailurePolicy.CONTINUE,
                        true,
                        "none",
                        "validator accepted IR shape",
                        List.of("validator diagnostic")
                )));
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(irGpuArtifact)
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.cl",
                "test-lowerer-v1"
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(irGpuArtifact))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(
                snapshot,
                GpuBackendCompilerFeedbackRegistry.of(List.of())
        );

        String participation = dump.artifact(GpuRuntimeCompileArtifactDumper.RUNTIME_EXTENSION_PARTICIPATION_ARTIFACT);
        assertTrue(participation.contains("status=recorded"));
        assertTrue(participation.contains("entry.count=3"));
        assertTrue(participation.contains("succeeded.count=2"));
        assertTrue(participation.contains("skipped.count=1"));
        assertTrue(participation.contains("entry.0.source=original-irgpu:ir-validation"));
        assertTrue(participation.contains("entry.0.extensionId=validator:shape-contract"));
        assertTrue(participation.contains("entry.0.extensionVersion=3"));
        assertTrue(participation.contains("entry.0.phase=IR_VALIDATION"));
        assertTrue(participation.contains("entry.0.permission=READ_ONLY"));
        assertTrue(participation.contains("entry.0.outcome=SUCCEEDED"));
        assertTrue(participation.contains("entry.1.source=runtime-equivalence"));
        assertTrue(participation.contains("entry.1.extensionId=runtime-equivalence:opencl"));
        assertTrue(participation.contains("entry.1.phase=RUNTIME_EQUIVALENCE"));
        assertTrue(participation.contains("entry.1.outcome=SKIPPED"));
        assertTrue(participation.contains("entry.2.source=backend-lowerer"));
        assertTrue(participation.contains("entry.2.extensionId=backend-lowerer:opencl"));
        assertTrue(participation.contains("entry.2.extensionVersion=test-lowerer-v1"));
        assertTrue(participation.contains("entry.2.phase=BACKEND_LOWERING"));
    }

    @Test
    void dumpsOptimizedIrGpuExtensionParticipationWhenArtifactDiffers() {
        IrGpuArtifact originalIrGpuArtifact = artifact("body\n  return original\n")
                .withExtensionParticipationMetadata(List.of(new IrGpuExtensionParticipationMetadata(
                        "ir-validation",
                        "validator:shape-contract",
                        "3",
                        GpuExtensionPhase.IR_VALIDATION,
                        GpuExtensionPermission.READ_ONLY,
                        "IR validation",
                        GpuExtensionExecutionOutcome.SUCCEEDED,
                        GpuExtensionFailurePolicy.CONTINUE,
                        true,
                        "none",
                        "validator accepted IR shape",
                        List.of()
                )));
        IrGpuArtifact optimizedIrGpuArtifact = artifact("body\n  return optimized\n")
                .withExtensionParticipationMetadata(List.of(new IrGpuExtensionParticipationMetadata(
                        "runtime-ir-optimization",
                        "optimizer:review-pass",
                        "2",
                        GpuExtensionPhase.RUNTIME_IR_OPTIMIZATION,
                        GpuExtensionPermission.MUTATION_PROPOSAL,
                        "Runtime IR optimization",
                        GpuExtensionExecutionOutcome.SKIPPED,
                        GpuExtensionFailurePolicy.CONTINUE,
                        true,
                        "none",
                        "optimizer proposed no production mutation",
                        List.of()
                )));
        GpuRuntimeCompileRequest originalRequest = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(originalIrGpuArtifact)
        );
        GpuRuntimeCompileRequest optimizedRequest = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimizedIrGpuArtifact)
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.cl",
                "test-lowerer-v1"
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                originalRequest,
                optimizedRequest,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(optimizedRequest, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(optimizedRequest),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimizedIrGpuArtifact))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(
                snapshot,
                GpuBackendCompilerFeedbackRegistry.of(List.of())
        );

        String participation = dump.artifact(GpuRuntimeCompileArtifactDumper.RUNTIME_EXTENSION_PARTICIPATION_ARTIFACT);
        assertTrue(participation.contains("entry.count=4"));
        assertTrue(participation.contains("succeeded.count=2"));
        assertTrue(participation.contains("skipped.count=2"));
        assertTrue(participation.contains("entry.0.source=original-irgpu:ir-validation"));
        assertTrue(participation.contains("entry.0.extensionId=validator:shape-contract"));
        assertTrue(participation.contains("entry.1.source=optimized-irgpu:runtime-ir-optimization"));
        assertTrue(participation.contains("entry.1.extensionId=optimizer:review-pass"));
        assertTrue(participation.contains("entry.1.permission=MUTATION_PROPOSAL"));
        assertTrue(participation.contains("entry.1.outcome=SKIPPED"));
        assertTrue(participation.contains("entry.2.source=runtime-equivalence"));
        assertTrue(participation.contains("entry.2.extensionId=runtime-equivalence:opencl"));
        assertTrue(participation.contains("entry.2.outcome=SKIPPED"));
        assertTrue(participation.contains("entry.3.source=backend-lowerer"));
        assertTrue(participation.contains("entry.3.extensionId=backend-lowerer:opencl"));
    }

    @Test
    void dumpsStructuredRuntimeEquivalenceComparisonCases() {
        GpuRuntimeEquivalenceCaseEvidence caseEvidence = new GpuRuntimeEquivalenceCaseEvidence(
                "runtime-invocation-0",
                "descriptor-source-vs-irgpu-reconstructed-source",
                java.util.Map.of("output", "[0]"),
                java.util.Map.of("output", "[7]"),
                java.util.Map.of("output", "[7]"),
                java.util.Map.of("output", "exact-int-array"),
                java.util.Map.of("output", true),
                List.of()
        );
        GpuRuntimeEquivalenceEvidence evidence = new GpuRuntimeEquivalenceEvidence(
                "passed",
                "OPENCL",
                "Mock Vendor",
                "Mock GPU",
                "review",
                true,
                true,
                1,
                1,
                List.of("outputs matched"),
                List.of(caseEvidence)
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(
                GpuRuntimeCompileArtifactSnapshot.legacy(descriptor()).withRuntimeEquivalenceEvidence(evidence)
        );
        String properties = dump.artifact("runtime-equivalence.properties");

        assertTrue(properties.contains("comparison.case.count=1"));
        assertTrue(properties.contains(
                "comparison.case.0.comparisonMode=descriptor-source-vs-irgpu-reconstructed-source"
        ));
        assertTrue(properties.contains("comparison.case.0.input.0.value=[0]"));
        assertTrue(properties.contains("comparison.case.0.output.0.reference=[7]"));
        assertTrue(properties.contains("comparison.case.0.output.0.candidate=[7]"));
        assertTrue(properties.contains("comparison.case.0.output.0.tolerance=exact-int-array"));
        assertTrue(properties.contains("comparison.case.0.output.0.equivalent=true"));
        String familyPayload = dump.artifact(
                GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD
        );
        assertTrue(familyPayload.contains("runtimeEquivalence.comparisonCase.count=1"));
        assertTrue(familyPayload.contains(
                "runtimeEquivalence.comparisonMode.summary={descriptor-source-vs-irgpu-reconstructed-source=1}"
        ));
        assertTrue(familyPayload.contains("familyBinding.status=not-bound"));
        assertTrue(familyPayload.contains("familyBinding.eligible=false"));
        assertTrue(familyPayload.contains("familyBinding.firstBlocker=optimizer-family-missing"));
    }

    @Test
    void preservesBinaryRuntimeCompileArtifacts() {
        byte[] binary = new byte[]{1, 2, 3, 4};
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.legacy(descriptor())
                .withBinaryArtifacts(List.of(new GpuRuntimeBinaryArtifact(
                        "opencl-program.bin",
                        "application/octet-stream",
                        binary
                )));

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.hasBinaryArtifact("opencl-program.bin"));
        assertEquals(4, dump.binaryArtifacts().get("opencl-program.bin").size());
    }

    @Test
    void dumpsOriginalOptimizedAndBackendArtifacts() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuBackendModuleArtifact originalBackendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 1; }",
                "runtime/original/kernel.cl",
                "test-lowerer-v1"
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 2; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );
        GpuRuntimeCompileInvalidationStamp stamp = GpuRuntimeCompileInvalidationStamp.from(
                new GpuRuntimeCompileRequest(
                        descriptor(),
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                        GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                        Optional.of(optimized)
                ),
                backendArtifact,
                "optimizer:test-v1"
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = new GpuRuntimeCompileArtifactSnapshot(
                Optional.of(original),
                Optional.of(optimized),
                backendArtifact,
                stamp,
                new GpuRuntimeCompileProvenance(
                        GpuBackendTarget.OPENCL,
                        "OpenCL",
                        "Mock GPU",
                        "Mock Vendor",
                        "Mock Driver",
                        "OpenCL 3.0 Mock",
                        48L,
                        65_536L,
                        512L,
                        1L,
                        true,
                        true,
                        false,
                        List.of("-cl-fast-relaxed-math"),
                        "fast",
                        "none"
                ),
                new GpuRuntimeIrOptimizationReport(
                        Optional.of(optimized),
                        List.of(GpuRuntimeIrOptimizationPassReport.applied(
                                "optimizer:test-v1",
                                "irgpu:sha256:original",
                                "irgpu:sha256:optimized",
                                "proof:mocked",
                                List.of("folded duplicated arithmetic")
                        )),
                        GpuOptimizationStrategyDecision.advisory(
                                "strategy:opencl-nvidia-advisory",
                                "nvidia",
                                "fast",
                                "NVIDIA remains scalar-safe until evidence-backed",
                                GpuOptimizationVendorBaseline.nvidiaRecorded(),
                                List.of("strategy is diagnostic-only")
                        )
                ),
                GpuRuntimeEquivalenceEvidence.notRun(
                        new GpuRuntimeCompileRequest(
                                descriptor(),
                                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "fast"),
                                GpuRuntimeDeviceProfile.openCl(
                                        "OpenCL",
                                        "Mock GPU",
                                        "Mock Vendor",
                                        "Mock Driver",
                                        "OpenCL 3.0 Mock",
                                        48L,
                                        65_536L,
                                        512L,
                                        1L,
                                        true,
                                        true,
                                        false
                                ),
                                Optional.of(optimized)
                        ),
                        "pre/post runtime equivalence not executed in unit test"
                ),
                GpuRuntimeFallbackEvidence.none(),
                GpuRuntimeProductionOptimizerGate.evaluate(
                        "off",
                        GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized)),
                        null,
                        null
                ),
                null,
                Optional.empty(),
                Optional.empty(),
                List.of(location()),
                "build ok",
                List.of("equivalence:skipped")
        ).withBackendStageModuleArtifacts(originalBackendArtifact, backendArtifact)
                .withDeviceSelection(deviceSelection());

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.hasArtifact("original.irgpu.properties"));
        assertTrue(dump.artifact("original.irgpu.properties").contains("return original"));
        assertTrue(dump.hasArtifact("optimized.irgpu.properties"));
        assertTrue(dump.artifact("optimized.irgpu.properties").contains("return optimized"));
        assertTrue(dump.hasArtifact("irgpu-regeneration.properties"));
        assertTrue(dump.artifact("irgpu-regeneration.properties").contains("original.present=true"));
        assertTrue(dump.artifact("irgpu-regeneration.properties").contains("original.backendNeutralSourceReady=false"));
        assertTrue(dump.artifact("irgpu-regeneration.properties").contains("original.payloadFormat=ir-text-v1"));
        assertTrue(dump.artifact("irgpu-regeneration.properties").contains("original.fallbackSource=derived-opencl-source"));
        assertTrue(dump.artifact("irgpu-regeneration.properties").contains("original.blocker.0=typed-body-regeneration-not-yet-available"));
        assertTrue(dump.artifact("irgpu-regeneration.properties").contains("optimized.present=true"));
        assertTrue(dump.artifact("irgpu-regeneration.properties").contains("optimized.backendNeutralSourceReady=false"));
        assertTrue(dump.artifact("irgpu-regeneration.properties").contains("optimized.blocker.0=typed-body-regeneration-not-yet-available"));
        assertTrue(dump.artifact("backend-source-selection.properties").contains("backendTarget=OPENCL"));
        assertTrue(dump.artifact("backend-source-selection.properties").contains("backendFormat=opencl-c"));
        assertTrue(dump.artifact("backend-source-selection.properties").contains("irGpu.present=true"));
        assertTrue(dump.artifact("backend-source-selection.properties").contains("derivedResourceMatches=false"));
        assertTrue(dump.artifact("backend-source-selection.properties").contains("irGpuSourceSelected=false"));
        assertTrue(dump.artifact("backend-source-selection.properties").contains("selectedSource=derived-opencl-source"));
        assertTrue(dump.artifact("backend-source-selection.properties").contains("payloadFormat=ir-text-v1"));
        assertTrue(dump.artifact("backend-source-selection.properties").contains("blocker.0=typed-body-regeneration-not-yet-available"));
        assertTrue(dump.artifact("backend-module.properties").contains("backendTarget=OPENCL"));
        assertTrue(dump.artifact("backend-module.properties").contains("kind=source"));
        assertTrue(dump.artifact("backend-module.properties").contains("format=opencl-c"));
        assertTrue(dump.artifact("backend-module.properties").contains("sourceOrigin=derived-opencl-source"));
        assertTrue(dump.artifact("backend-module.properties").contains("sourceAvailable=true"));
        assertTrue(dump.artifact("backend-module.properties").contains("binaryAvailable=false"));
        assertTrue(dump.artifact("backend-module.properties").contains("compileLogResource="));
        assertTrue(dump.artifact("backend-module.properties").contains("sourceMapResource="));
        assertTrue(dump.artifact("backend-module.properties").contains("runtimeLoadMode=opencl-source-compile"));
        assertTrue(dump.hasArtifact("backend-diagnostics.properties"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("backendTarget=OPENCL"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("backendFormat=opencl-c"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("sourceOrigin=derived-opencl-source"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("runtimeLoadMode=opencl-source-compile"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("sourceAvailable=true"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("binaryAvailable=false"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("compileLogAvailable=false"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("sourceMapAvailable=false"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("sourceLocation.count=1"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("methodBody.count=1"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("irGpu.present=true"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("derivedResourceMatches=false"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("irGpuSourceSelected=false"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("selectedSource=derived-opencl-source"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("payloadFormat=ir-text-v1"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("blocker.0=typed-body-regeneration-not-yet-available"));
        assertTrue(dump.hasArtifact("opencl-irgpu-reconstruction-preview.properties"));
        assertTrue(dump.artifact("opencl-irgpu-reconstruction-preview.properties").contains("attempted=true"));
        assertTrue(dump.artifact("opencl-irgpu-reconstruction-preview.properties").contains("reconstructable=false"));
        assertTrue(dump.artifact("opencl-irgpu-reconstruction-preview.properties").contains("selectedSource=derived-opencl-source"));
        assertTrue(dump.artifact("opencl-irgpu-reconstruction-preview.properties").contains("payloadFormat=ir-text-v1"));
        assertTrue(dump.artifact("opencl-irgpu-reconstruction-preview.properties").contains("entryEmittedName=jtg_kernel"));
        assertTrue(dump.artifact("opencl-irgpu-reconstruction-preview.properties").contains("methodBody.count=1"));
        assertTrue(dump.artifact("opencl-irgpu-reconstruction-preview.properties").contains("blocker.0=typed-body-regeneration-not-yet-available"));
        assertTrue(dump.artifact("opencl-irgpu-reconstruction-preview.properties").contains("blocker.1=irgpu-opencl-resource-drift"));
        assertTrue(dump.hasArtifact("backend-source-reconstruction.properties"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("backendTarget=OPENCL"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("attempted=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("ready=false"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("reconstructed=false"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("selectedSource=derived-opencl-source"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("payloadFormat=ir-text-v1"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceAvailable=false"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceOrigin=derived-opencl-source"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("runtimeLoadMode=opencl-source-compile"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("blocker.0=typed-body-regeneration-not-yet-available"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("blocker.1=irgpu-opencl-resource-drift"));
        assertTrue(dump.hasArtifact("backend-source-promotion-gate.properties"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("status=blocked"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("reviewReady=false"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("ready=false"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("reconstructed=false"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("runtimeEquivalencePassed=false"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("fallbackClean=true"));
        assertTrue(dump.hasArtifact("backend-source-switching-decision.properties"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("status=descriptor-default"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("decision=compile-descriptor-source"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceSelection=descriptor"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("irGpuSourceRequested=false"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionSourceSwitching=disabled"));
        assertTrue(dump.artifact("backend-source-map.properties").contains("backendTarget=OPENCL"));
        assertTrue(dump.artifact("backend-source-map.properties").contains("backendResource=runtime/lowered/kernel.cl"));
        assertTrue(dump.artifact("backend-source-map.properties").contains("sourceLocation.count=1"));
        assertTrue(dump.artifact("backend-source-map.properties").contains("sourceLocation.0.sourceKind=java-source"));
        assertTrue(dump.artifact("backend-source-map.properties").contains("sourceLocation.0.ownerQualifiedName=sample.Demo"));
        assertTrue(dump.artifact("backend-source-map.properties").contains("sourceLocation.0.methodName=kernel"));
        assertTrue(dump.artifact("backend-source-map.properties").contains("sourceLocation.0.beginLine=4"));
        assertTrue(dump.artifact("backend-source-map.properties").contains("methodBody.count=1"));
        assertTrue(dump.artifact("backend-source-map.properties").contains("methodBody.0.role=entry"));
        assertTrue(dump.artifact("backend-source-map.properties").contains("methodBody.0.name=kernel"));
        assertTrue(dump.artifact("backend-source-map.properties").contains("methodBody.0.emittedName=jtg_kernel"));
        assertTrue(dump.artifact("backend-source-map.properties").contains("methodBody.0.format=ir-text-v1"));
        assertTrue(dump.artifact("backend-source-map.properties").contains("methodBody.0.sourceKind=java-source"));
        assertEquals(originalBackendArtifact.source(), dump.artifact("original.backend.opencl-c"));
        assertEquals(backendArtifact.source(), dump.artifact("optimized.backend.opencl-c"));
        assertEquals(backendArtifact.source(), dump.artifact("backend.opencl-c"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("backendTarget=OPENCL"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("deviceLabel=Mock GPU"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("computeUnits=48"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("localMemoryBytes=65536"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("maxWorkGroupSize=512"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("preferredVectorWidthFloat=1"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("supportsDoublePrecision=true"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("supportsImages=true"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("supportsSubgroups=false"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("compileArg.0=-cl-fast-relaxed-math"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("backendOption.target=OPENCL"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("backendOption.flag.0=-cl-fast-relaxed-math"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("backendOption.property.count=0"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("optimizationProfile=fast"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("fallbackDecision=none"));
        assertTrue(dump.hasArtifact(GpuRuntimeCompileArtifactDumper.RUNTIME_DEVICE_SELECTION_ARTIFACT));
        String deviceSelectionArtifact = dump.artifact(GpuRuntimeCompileArtifactDumper.RUNTIME_DEVICE_SELECTION_ARTIFACT);
        assertTrue(deviceSelectionArtifact.contains("deviceSelection.selected=true"));
        assertTrue(deviceSelectionArtifact.contains("deviceSelection.selectedDeviceKey=OPENCL:opencl-1"));
        assertTrue(deviceSelectionArtifact.contains("deviceSelection.selected.deviceLabel=Mock GPU"));
        assertTrue(deviceSelectionArtifact.contains("deviceSelection.selected.deviceClass=dgpu"));
        assertTrue(deviceSelectionArtifact.contains("deviceSelection.candidate.0.globalMemoryBytes=8589934592"));
        assertTrue(deviceSelectionArtifact.contains("deviceSelection.policy.0.policyId=javatogpu.device.backend-compatibility"));
        assertTrue(deviceSelectionArtifact.contains("deviceSelection.execution.0.extensionId=javatogpu.device.backend-compatibility"));
        assertTrue(deviceSelectionArtifact.contains("deviceSelection.execution.0.outcome=SUCCEEDED"));
        assertTrue(dump.artifact("optimizer-report.txt").contains("outcome=APPLIED"));
        assertTrue(dump.artifact("optimizer-report.txt").contains("strategy:opencl-nvidia-advisory"));
        assertTrue(dump.artifact("optimizer-report.txt").contains("advisoryOnly=true"));
        assertTrue(dump.artifact("optimizer-report.txt").contains("baselineStatus=recorded-nvidia-only"));
        assertTrue(dump.artifact("optimizer-report.txt").contains("promotionEligible=false"));
        assertTrue(dump.artifact("optimizer-report.txt").contains("proof=proof:mocked"));
        assertTrue(dump.artifact("optimizer-report.txt").contains("folded duplicated arithmetic"));
        assertTrue(dump.artifact("runtime-equivalence.properties").contains("status=not-run"));
        assertTrue(dump.artifact("runtime-equivalence.properties").contains("backendTarget=OPENCL"));
        assertTrue(dump.artifact("runtime-equivalence.properties").contains("optimizationProfile=fast"));
        assertTrue(dump.artifact("runtime-equivalence.properties").contains("executed=false"));
        assertTrue(dump.artifact("runtime-equivalence.properties").contains("diagnostic.0=pre/post runtime equivalence not executed in unit test"));
        assertTrue(dump.artifact("fallback.properties").contains("decision=none"));
        assertTrue(dump.artifact("fallback.properties").contains("originalIrSelected=false"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("status=not-requested"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("runtimeEquivalenceRequired=true"));
        assertTrue(dump.hasArtifact("runtime-ir-handoff.properties"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("status=selected"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("selectedStage=optimized"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("original.present=true"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("optimized.present=true"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("selected.present=true"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("optimizedDiffersFromOriginal=true"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("optimizationReportPresent=true"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("optimizationRequiresRollback=false"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("fallbackDecision=none"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("optimizedIrRejected=false"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("productionIrGate.status=review-profile"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("productionIrGate.accepted=true"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("productionIrGate.decisionMode=diagnostic-only"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("backendTarget=OPENCL"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("runtimeLoadMode=opencl-source-compile"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("diagnostic.0=optimized IrGpu is selected for backend lowering after runtime optimizer passes"));
        assertTrue(dump.hasArtifact("runtime-production-mutation-safety.properties"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("status=disabled"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("productionMutationEnabled=false"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("productionGateStatus=not-requested"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("productionProfileRequested=false"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("selectedStage=optimized"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("optimizedSelected=true"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("optimizedDiffersFromOriginal=true"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("fallbackDecision=none"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("diagnostic.0=runtime IR participates in diagnostics, but production mutation is disabled because no production profile was requested"));
        assertTrue(dump.hasArtifact("i3-readiness-summary.properties"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("status=blocked"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("selectedRuntimeIrStage=optimized"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("sourcePromotionStatus=blocked"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("sourcePromotionReviewReady=false"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("sourceReady=false"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("optimizerProductionGateStatus=not-requested"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("productionMutationEnabled=false"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("blocker.0=backend-source-promotion-not-review-ready"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("blocker.1=production-optimizer-gate-not-accepted"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("blocker.2=production-mutation-disabled"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.applied.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.rolledBack.count=0"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("proofArtifact.count=0"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("proofArtifact.accepted.count=0"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("proofArtifact.blocking.count=0"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("optimizerFamily.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("optimizerFamily.promotionReady.count=0"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("optimizerFamily.summary=test-v1[passes=1, acceptedProof=0, blockingProof=0, rolledBack=0, failed=0, promotionReady=false]"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("fallbackDecision=none"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("selectedRuntimeIrStage=optimized"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("optimizedIrRejected=false"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("strategyName=strategy:opencl-nvidia-advisory"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("baselineStatus=recorded-nvidia-only"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("productionGateStatus=not-requested"));
        assertEquals("build ok", dump.artifact("compile.log"));
        assertEquals("equivalence:skipped", dump.artifact("runtime-validation.txt"));
        assertEquals(List.of("java-source:sample.Demo#kernel:4:17-7:5"), dump.sourceLocations());
        assertEquals("optimizer:test-v1", dump.invalidationStamp().optimizerPipelineVersion());
    }

    @Test
    void emptyDumpHandlesMissingSnapshot() {
        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(null);
        String driftProperties = GpuRuntimeOptimizerDriftArtifact.from(null).toPropertiesText();

        assertFalse(dump.hasArtifact("original.irgpu.properties"));
        assertFalse(dump.hasArtifact("backend-source-map.properties"));
        assertTrue(dump.sourceLocations().isEmpty());
        assertTrue(driftProperties.contains("selectedRuntimeIrStage=missing"));
        assertTrue(driftProperties.contains("selectedRuntimeIrIdentity=irgpu:missing"));
        assertTrue(driftProperties.contains("optimizedIrRejected=false"));
        assertTrue(driftProperties.contains("optimizerFamily.count=0"));
        assertTrue(driftProperties.contains("optimizerFamily.summary="));
    }

    @Test
    void dumpRecordsBackendNeutralReadyIrGpuSourceSelectionArtifacts() {
        IrGpuArtifact optimized = artifact(
                "body\n  return ready\n",
                IrGpuRegenerationMetadata.backendNeutralReady()
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 2; }",
                "javatogpu/sample/Demo/kernel.cl",
                "test-lowerer-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1")
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.artifact("irgpu-regeneration.properties").contains("optimized.backendNeutralSourceReady=true"));
        assertTrue(dump.artifact("irgpu-regeneration.properties").contains("optimized.fallbackSource=irgpu-backend-neutral-source"));
        assertTrue(dump.artifact("backend-source-selection.properties").contains("irGpu.present=true"));
        assertTrue(dump.artifact("backend-source-selection.properties").contains("derivedResourceMatches=true"));
        assertTrue(dump.artifact("backend-source-selection.properties").contains("irGpuSourceSelected=true"));
        assertTrue(dump.artifact("backend-source-selection.properties").contains("selectedSource=irgpu-backend-neutral-source"));
        assertTrue(dump.artifact("backend-source-selection.properties").contains("payloadFormat=ir-text-v1"));
        assertTrue(dump.artifact("backend-source-selection.properties").contains("blocker.count=0"));
        assertTrue(dump.artifact("backend-module.properties").contains("sourceOrigin=irgpu-backend-neutral-source"));
        assertTrue(dump.artifact("backend-module.properties").contains("runtimeLoadMode=opencl-irgpu-source-compile"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("sourceOrigin=irgpu-backend-neutral-source"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("runtimeLoadMode=opencl-irgpu-source-compile"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("derivedResourceMatches=true"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("irGpuSourceSelected=true"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("selectedSource=irgpu-backend-neutral-source"));
        assertTrue(dump.artifact("backend-diagnostics.properties").contains("blocker.count=0"));
        assertTrue(dump.artifact("opencl-irgpu-reconstruction-preview.properties").contains("attempted=true"));
        assertTrue(dump.artifact("opencl-irgpu-reconstruction-preview.properties").contains("reconstructable=true"));
        assertTrue(dump.artifact("opencl-irgpu-reconstruction-preview.properties").contains("selectedSource=irgpu-backend-neutral-source"));
        assertTrue(dump.artifact("opencl-irgpu-reconstruction-preview.properties").contains("payloadFormat=ir-text-v1"));
        assertTrue(dump.artifact("opencl-irgpu-reconstruction-preview.properties").contains("entryEmittedName=jtg_kernel"));
        assertTrue(dump.artifact("opencl-irgpu-reconstruction-preview.properties").contains("methodBody.count=1"));
        assertTrue(dump.artifact("opencl-irgpu-reconstruction-preview.properties").contains("blocker.count=0"));
        assertTrue(dump.artifact("opencl-irgpu-reconstruction-preview.properties").contains("OpenCL source can be reconstructed from IrGpu"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("backendTarget=OPENCL"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("attempted=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("ready=false"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("reconstructed=false"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("selectedSource=irgpu-backend-neutral-source"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("payloadFormat=ir-text-v1"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceAvailable=false"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceOrigin=irgpu-backend-neutral-source"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("runtimeLoadMode=opencl-irgpu-source-compile"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("blocker.count=1"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("blocker.0=irgpu-entry-parameter-metadata-missing"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("irgpu-entry-jtg_kernel-parsed.statement.count=1"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("irgpu-entry-jtg_kernel-emitted.body.length="));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("OpenCL source can be reconstructed from IrGpu"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("OpenCL source assembly is blocked by missing IrGpu metadata or unsupported ir-text-v1 features"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("status=blocked"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("reviewReady=false"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("ready=false"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("reconstructed=false"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("sourceParityChecked=false"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("runtimeEquivalencePassed=false"));
    }

    @Test
    void dumpRecordsBackendSourceSwitchingReviewDecision() {
        String reconstructedDescriptorSource = reconstructedDescriptorSource();
        IrGpuArtifact optimized = artifact(
                "body\n  set output[0] = 1\n  return\n",
                IrGpuRegenerationMetadata.backendNeutralReady(),
                entryParameters()
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                reconstructedDescriptorSource,
                "javatogpu/sample/Demo/kernel.cl",
                "test-lowerer-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.openClIrGpuSourceReview(List.of()),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("status=review-ready"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("decision=compile-irgpu-source-review"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains(
                "optimizationProfile=" + GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE
        ));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionProfileRequested=false"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceSelection=irgpu"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("irGpuSourceRequested=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceReady=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceReconstructed=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceAvailable=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceParityChecked=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceParityMatched=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionSourceSwitching=disabled"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionPromotionDecisionMode=diagnostic-only"));
    }

    @Test
    void dumpRecordsBackendSourceSwitchingProductionDecision() {
        String reconstructedDescriptorSource = reconstructedDescriptorSource();
        IrGpuArtifact optimized = artifact(
                "body\n  set output[0] = 1\n  return\n",
                IrGpuRegenerationMetadata.backendNeutralReady(),
                entryParameters()
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                reconstructedDescriptorSource,
                "javatogpu/sample/Demo/kernel.cl",
                "test-lowerer-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of(), "vendor-tuned")
                        .withProductionPromotionDecision(productionEnabledDecision())
                        .withProductionPromotionOperatorAccepted(true),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot.withRuntimeEquivalenceEvidence(
                GpuRuntimeEquivalenceEvidence.passed(
                        request,
                        1,
                        1,
                        List.of("production source fixture equivalent")
                )
        ));

        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("status=production-switch-enabled"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("decision=compile-irgpu-source-production"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("optimizationProfile=vendor-tuned"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionProfileRequested=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceSelection=irgpu"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceReady=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceReconstructed=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceAvailable=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceParityChecked=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceParityMatched=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourcePromotionFirstBlocker=none"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionSourceSwitching=enabled"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionSourceSwitchingEnabled=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionPromotionDecisionMode=production-enabled"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionPromotionOperatorAccepted=true"));
    }

    @Test
    void dumpRecordsBackendSourceSwitchingBlockedProductionDecision() {
        String reconstructedDescriptorSource = reconstructedDescriptorSource();
        IrGpuArtifact optimized = artifact(
                "body\n  set output[0] = 1\n  return\n",
                IrGpuRegenerationMetadata.backendNeutralReady(),
                entryParameters()
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                reconstructedDescriptorSource,
                "javatogpu/sample/Demo/kernel.cl",
                "test-lowerer-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.openClIrGpuSource(List.of(), "vendor-tuned"),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("status=blocked"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("decision=reject-production-irgpu-source"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("optimizationProfile=vendor-tuned"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionProfileRequested=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceSelection=irgpu"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceReady=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceReconstructed=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceAvailable=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceParityChecked=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceParityMatched=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionSourceSwitching=disabled"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionSourceSwitchingEnabled=false"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionPromotionDecisionMode=diagnostic-only"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains(
                "diagnostic.0=production-like profile requested IrGpu source but opencl.productionSourceSwitching is disabled"
        ));
    }

    @Test
    void dumpRecordsProductionPromotionDecisionModeWithoutChangingSourceSwitchingGate() {
        String reconstructedDescriptorSource = reconstructedDescriptorSource();
        IrGpuArtifact optimized = artifact(
                "body\n  set output[0] = 1\n  return\n",
                IrGpuRegenerationMetadata.backendNeutralReady(),
                entryParameters()
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                reconstructedDescriptorSource,
                "javatogpu/sample/Demo/kernel.cl",
                "test-lowerer-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
        GpuProductionPromotionDecision decision = new GpuProductionPromotionDecision(
                GpuProductionPromotionDecision.REVIEW_READY,
                "blocked",
                true,
                false,
                false,
                "production-source-switching-disabled",
                "none",
                "review-ready evidence is visible to runtime diagnostics"
        );
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions
                .openClIrGpuSource(List.of(), "vendor-tuned")
                .withProductionPromotionDecision(decision);
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                options,
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("status=blocked"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("decision=reject-production-irgpu-source"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceReady=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceReconstructed=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceAvailable=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceParityChecked=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceParityMatched=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionSourceSwitching=disabled"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionSourceSwitchingEnabled=false"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionPromotionDecisionMode=review-ready"));
    }

    @Test
    void dumpBlocksProductionSourceSwitchingWhenPromotionDecisionIsOnlyReviewReady() {
        String reconstructedDescriptorSource = reconstructedDescriptorSource();
        IrGpuArtifact optimized = artifact(
                "body\n  set output[0] = 1\n  return\n",
                IrGpuRegenerationMetadata.backendNeutralReady(),
                entryParameters()
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                reconstructedDescriptorSource,
                "javatogpu/sample/Demo/kernel.cl",
                "test-lowerer-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions
                .openClProductionIrGpuSource(List.of(), "vendor-tuned")
                .withProductionPromotionDecision(reviewReadyDecision());
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                options,
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot.withRuntimeEquivalenceEvidence(
                GpuRuntimeEquivalenceEvidence.passed(
                        request,
                        1,
                        1,
                        List.of("production source fixture equivalent")
                )
        ));

        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("status=blocked"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("decision=reject-production-irgpu-source"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionSourceSwitching=enabled"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionSourceSwitchingEnabled=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionPromotionDecisionMode=review-ready"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourcePromotionFirstBlocker=none"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains(
                "diagnostic.0=production-like profile requested IrGpu source but production promotion decision is not production-enabled"
        ));
    }

    @Test
    void dumpBlocksIrGpuSourceSwitchingWhenReconstructedSourceIsUnavailable() {
        IrGpuArtifact optimized = artifact(
                "body\n  set output[0] = 1\n  return\n",
                IrGpuRegenerationMetadata.backendNeutralReady()
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                reconstructedDescriptorSource(),
                "javatogpu/sample/Demo/kernel.cl",
                "test-lowerer-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.openClIrGpuSourceReview(List.of()),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot.withRuntimeEquivalenceEvidence(
                GpuRuntimeEquivalenceEvidence.passed(
                        request,
                        1,
                        1,
                        List.of("production source fixture equivalent")
                )
        ));

        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("status=blocked"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("decision=reject-irgpu-source-unavailable"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceReady=false"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceReconstructed=false"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceAvailable=false"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceParityChecked=false"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourcePromotionReviewReady=false"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains(
                "sourcePromotionFirstBlocker=backend source must be reconstructed from IrGpu before promotion review"
        ));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains(
                "diagnostic.0=IrGpu source was requested but reconstructed source is not available"
        ));
    }

    @Test
    void dumpBlocksIrGpuSourceSwitchingWhenReconstructedParityDiffers() {
        IrGpuArtifact optimized = artifact(
                "body\n  set output[0] = 2\n  return\n",
                IrGpuRegenerationMetadata.backendNeutralReady(),
                entryParameters()
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                reconstructedDescriptorSource(),
                "javatogpu/sample/Demo/kernel.cl",
                "test-lowerer-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.openClIrGpuSourceReview(List.of()),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("status=blocked"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("decision=reject-irgpu-source-parity"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceReady=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceReconstructed=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceAvailable=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceParityChecked=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceParityMatched=false"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourcePromotionReviewReady=false"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains(
                "diagnostic.0=IrGpu source was requested but reconstructed source parity has not matched descriptor source"
        ));
    }

    @Test
    void dumpUsesSnapshotBackendSourceSwitchingDecisionWhenProvided() {
        IrGpuArtifact optimized = artifact(
                "body\n  set output[0] = 1\n  return\n",
                IrGpuRegenerationMetadata.backendNeutralReady(),
                entryParameters()
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                reconstructedDescriptorSource(),
                "javatogpu/sample/Demo/kernel.cl",
                "test-lowerer-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.openClIrGpuSourceReview(List.of()),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuBackendSourceSwitchingDecision precomputedDecision = new GpuBackendSourceSwitchingDecision(
                "review-ready",
                "compile-irgpu-source-review",
                GpuBackendTarget.OPENCL,
                "opencl-c",
                "precomputed/kernel.cl#irgpu-reconstructed",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile",
                GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE,
                false,
                "irgpu",
                true,
                true,
                true,
                true,
                true,
                true,
                "review-ready",
                true,
                "none",
                "disabled",
                false,
                "precomputed-mode",
                false,
                "precomputed source switching decision was supplied by runtime backend"
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized))
        ).withBackendSourceSwitchingDecision(precomputedDecision);

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("backendResource=precomputed/kernel.cl#irgpu-reconstructed"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionPromotionDecisionMode=precomputed-mode"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains(
                "diagnostic.0=precomputed source switching decision was supplied by runtime backend"
        ));
    }

    @Test
    void snapshotSourceEvidenceMutationsClearPrecomputedBackendSourceState() {
        IrGpuArtifact optimized = artifact(
                "body\n  set output[0] = 1\n  return\n",
                IrGpuRegenerationMetadata.backendNeutralReady(),
                entryParameters()
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                reconstructedDescriptorSource(),
                "javatogpu/sample/Demo/kernel.cl",
                "test-lowerer-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.openClIrGpuSourceReview(List.of()),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuBackendSourcePromotionGate precomputedGate = new GpuBackendSourcePromotionGate(
                "review-ready",
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                "passed",
                true,
                true,
                1,
                1,
                List.of("precomputed runtime equivalence"),
                true,
                "precomputed-source",
                "ir-text-v1",
                "opencl-irgpu-source-compile",
                List.of(),
                List.of("sourceParity.checked=true", "sourceParity.matched=true"),
                List.of("precomputed source promotion gate was supplied by runtime backend")
        );
        GpuBackendSourceSwitchingDecision precomputedDecision = new GpuBackendSourceSwitchingDecision(
                "review-ready",
                "compile-irgpu-source-review",
                GpuBackendTarget.OPENCL,
                "opencl-c",
                "precomputed/kernel.cl#irgpu-reconstructed",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile",
                GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE,
                false,
                "irgpu",
                true,
                true,
                true,
                true,
                true,
                true,
                "review-ready",
                true,
                "none",
                "disabled",
                false,
                "precomputed-mode",
                false,
                "precomputed source switching decision was supplied by runtime backend"
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized))
        ).withBackendSourceState(precomputedGate, precomputedDecision);

        GpuRuntimeCompileArtifactSnapshot refreshedSnapshot = snapshot.withRuntimeEquivalenceEvidence(
                GpuRuntimeEquivalenceEvidence.notRun(request, "fresh runtime equivalence replaced source state")
        );
        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(refreshedSnapshot);

        assertFalse(refreshedSnapshot.backendSourcePromotionGate().isPresent());
        assertFalse(refreshedSnapshot.backendSourceSwitchingDecision().isPresent());
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("status=blocked"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("runtimeEquivalencePassed=false"));
        assertFalse(dump.artifact("backend-source-promotion-gate.properties").contains("precomputed-source"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("decision=compile-irgpu-source-review"));
        assertFalse(dump.artifact("backend-source-switching-decision.properties").contains("precomputed/kernel.cl#irgpu-reconstructed"));
    }

    @Test
    void dumpRecordsReconstructedSourceParityDiagnosticsWhenParameterMetadataExists() {
        IrGpuArtifact optimized = artifact(
                "body\n  return output[0]\n",
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of()))
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.cl",
                "test-lowerer-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1")
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("reconstructed=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("ready=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceAvailable=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("blocker.count=0"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("OpenCL source assembler emitted entry kernel jtg_kernel"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceParity.checked=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceParity.matched=false"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceParity.reconstructedLength="));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceParity.descriptorLength="));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("status=blocked"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("ready=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("reconstructed=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("sourceAvailable=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("sourceParityChecked=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("sourceParityMatched=false"));
    }

    @Test
    void dumpRecordsBackendSourcePromotionReviewReadyWhenParityAndRuntimeEvidencePass() {
        String reconstructedDescriptorSource = """
                __kernel void jtg_kernel(__global int* output) {
                    output[0] = 1;
                    return;
                }
                """;
        IrGpuArtifact optimized = artifact(
                "body\n  set output[0] = 1\n  return\n",
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of()))
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                reconstructedDescriptorSource,
                "javatogpu/sample/Demo/kernel.cl",
                "test-lowerer-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized)),
                GpuRuntimeEquivalenceEvidence.passed(request, 2, 2, List.of("source parity runtime equivalence passed"))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("reconstructed=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("ready=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceParity.checked=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceParity.matched=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("status=review-ready"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("reviewReady=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("ready=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("reconstructed=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("sourceAvailable=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("sourceParityChecked=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("sourceParityMatched=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("runtimeEquivalencePassed=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("fallbackClean=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("diagnostic.0=backend source reconstruction is ready for promotion review"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("status=review-ready"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("sourceReconstructed=true"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("sourceReady=true"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("sourceParityMatched=true"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("runtimeEquivalencePassed=true"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("sourcePromotionStatus=review-ready"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("sourcePromotionReviewReady=true"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("productionMutationEnabled=false"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("blocker.0=production-optimizer-gate-not-accepted"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("blocker.1=production-mutation-disabled"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("diagnostic.0=I3 source pipeline is review-ready, but production mutation remains disabled until production gates are accepted"));
    }

    @Test
    void dumpRecordsPackagedIrGpuSourceReadinessWhenParityMatches() {
        IrGpuArtifact optimized = GpuRuntimeIrArtifactLoader.load(SIMPLE_IRGPU_SOURCE_RESOURCE, getClass().getClassLoader())
                .orElseThrow();
        GpuKernelDescriptor descriptor = simpleIrGpuSourceDescriptor();
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                descriptor.kernelSource(),
                descriptor.kernelResource(),
                "test-lowerer-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor,
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized)),
                GpuRuntimeEquivalenceEvidence.passed(request, 4, 4, List.of("packaged IrGpu source parity runtime equivalence passed"))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("ready=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("reconstructed=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceAvailable=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceParity.checked=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceParity.matched=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("OpenCL source assembler emitted entry kernel gpu_irgpu_entry"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("status=review-ready"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("ready=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("runtimeEquivalence.diagnostic.0=packaged IrGpu source parity runtime equivalence passed"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("status=review-ready"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("sourceReconstructed=true"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("sourceReady=true"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("sourceParityMatched=true"));
    }

    @Test
    void dumpRecordsPrivateArraySourceReconstructionAsReviewReadyWhenEvidencePasses() {
        String descriptorSource = """
                __kernel void jtg_kernel(__global float* input, __global float* output) {
                    float scratch[4];
                    scratch[0] = input[0];
                    output[0] = scratch[0];
                    return;
                }
                """;
        IrGpuArtifact optimized = artifact(
                """
                        body
                          private-array float scratch[4]
                          set scratch[0] = input[0]
                          set output[0] = scratch[0]
                          return
                        """,
                IrGpuRegenerationMetadata.backendNeutralReady(),
                List.of(
                        new IrGpuEntryParameter("input", "float[]", "GLOBAL", false, List.of()),
                        new IrGpuEntryParameter("output", "float[]", "GLOBAL", false, List.of())
                )
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                descriptorSource,
                "javatogpu/sample/Demo/kernel.cl",
                "test-lowerer-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized)),
                GpuRuntimeEquivalenceEvidence.passed(request, 2, 2, List.of("private-array source parity runtime equivalence passed"))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("reconstructed=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("ready=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceAvailable=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("blocker.count=0"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceParity.checked=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceParity.matched=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("OpenCL source assembler emitted entry kernel jtg_kernel"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("status=review-ready"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("reviewReady=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("ready=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("runtimeEquivalencePassed=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("runtimeEquivalence.diagnostic.0=private-array source parity runtime equivalence passed"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("status=review-ready"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("sourceReconstructed=true"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("sourceReady=true"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("sourcePromotionReviewReady=true"));
    }

    @Test
    void fallbackEvidenceRecordsRuntimeEquivalenceFailure() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 2; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "fast"),
                GpuRuntimeDeviceProfile.openCl(
                        "OpenCL",
                        "Mock GPU",
                        "Mock Vendor",
                        "Mock Driver",
                        "OpenCL 3.0 Mock",
                        48L,
                        65_536L,
                        512L,
                        1L,
                        true,
                        true,
                        false
                ),
                Optional.of(optimized)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request.withIrGpuArtifact(Optional.of(original)),
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized)),
                GpuRuntimeEquivalenceEvidence.failed(request, 2, 1, List.of("case 1 output differs"))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.artifact("runtime-equivalence.properties").contains("status=failed"));
        assertTrue(dump.artifact("runtime-equivalence.properties").contains("equivalent=false"));
        assertTrue(dump.artifact("fallback.properties").contains("decision=runtime-equivalence-failed"));
        assertTrue(dump.artifact("fallback.properties").contains("originalIrSelected=true"));
        assertTrue(dump.artifact("fallback.properties").contains("optimizedIrRejected=true"));
        assertTrue(dump.artifact("compile-provenance.properties").contains("fallbackDecision=runtime-equivalence-failed"));
    }

    @Test
    void productionOptimizerGateBlocksVendorTunedProfilesUntilA1A2EvidenceExists() {
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 2; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "vendor-tuned"),
                GpuRuntimeDeviceProfile.openCl(
                        "OpenCL",
                        "Mock NVIDIA GPU",
                        "NVIDIA Corporation",
                        "Mock Driver",
                        "OpenCL 3.0 Mock",
                        48L,
                        65_536L,
                        512L,
                        1L,
                        true,
                        true,
                        false
                ),
                Optional.of(optimized)
        );
        GpuRuntimeIrOptimizationReport optimizationReport = new GpuRuntimeIrOptimizationReport(
                Optional.of(optimized),
                List.of(),
                GpuOptimizationStrategy.advisoryDefault().select(request)
        );

        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                optimizationReport,
                GpuRuntimeEquivalenceEvidence.passed(request, 2, 2, List.of("mock equivalence passed"))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("status=blocked"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("productionProfileRequested=true"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("runtimeEquivalencePassed=true"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("fallbackClean=true"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("strategyEvidenceBacked=false"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("vendorPromotionEligible=false"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("diagnostic.0=optimization strategy must be evidence-backed and non-advisory"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("diagnostic.1=vendor baseline is not promotion-eligible under A1/A2 gates"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("diagnostic.2=accepted optimizer proof artifact is required before production promotion"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("status=disabled"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("productionMutationEnabled=false"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("productionGateStatus=blocked"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("productionProfileRequested=true"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("selectedStage=original"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("optimizedSelected=false"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("runtimeEquivalencePassed=true"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("fallbackClean=true"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("strategyEvidenceBacked=false"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("vendorPromotionEligible=false"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("diagnostic.0=runtime IR participates in diagnostics, but production mutation remains fail-closed until production optimizer gates pass"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("productionIrGate.status=blocked"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("productionIrGate.accepted=false"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("productionIrGate.decisionMode=diagnostic-only"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("productionIrGate.diagnostic=OpenCL runtime optimized IrGpu cannot be selected for production-like optimization profile 'vendor-tuned'"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("status=blocked"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("optimizerProductionGateStatus=blocked"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("productionProfileRequested=true"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("productionMutationEnabled=false"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.count=0"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("fallbackDecision=production-ir-gate-blocked"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("selectedRuntimeIrStage=original"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("optimizedIrRejected=true"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("strategyName=strategy:opencl-nvidia-advisory"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("selectedProfile=vendor-tuned"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("productionGateStatus=blocked"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("productionProfileRequested=true"));
        assertTrue(dump.artifact("optimizer-report.txt").contains("strategy:opencl-nvidia-advisory"));
    }

    @Test
    void dumpRecordsOptimizerFamilyRuntimeEquivalencePayloadCompleteness() {
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 2; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "vendor-tuned"),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeIrOptimizationPassReport completeCsePayload = GpuRuntimeIrOptimizationPassReport.applied(
                "optimizer:cse-v1",
                "irgpu:sha256:original",
                "irgpu:sha256:cse",
                "proof:accepted",
                List.of("CSE runtime-equivalence payload captured")
        ).withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                "ir-validation",
                "accepted",
                java.util.Map.ofEntries(
                        java.util.Map.entry("optimizerFamily", "cse"),
                        java.util.Map.entry("runtimeEquivalencePayload.present", "true"),
                        java.util.Map.entry("runtimeEquivalencePayload.cpuReference.present", "true"),
                        java.util.Map.entry("runtimeEquivalencePayload.preOptimizationOutput.present", "true"),
                        java.util.Map.entry("runtimeEquivalencePayload.postOptimizationOutput.present", "true"),
                        java.util.Map.entry("runtimeEquivalencePayload.tolerance.present", "true"),
                        java.util.Map.entry("runtimeEquivalencePayload.failureFixture.present", "true"),
                        java.util.Map.entry("runtimeEquivalencePayload.resource", "artifact://payload/cse"),
                        java.util.Map.entry(
                                "runtimeEquivalencePayload.cpuReference.resource",
                                "artifact://payload/cse/cpu-reference.bin"
                        ),
                        java.util.Map.entry(
                                "runtimeEquivalencePayload.preOptimizationOutput.resource",
                                "artifact://payload/cse/pre-output.bin"
                        ),
                        java.util.Map.entry(
                                "runtimeEquivalencePayload.postOptimizationOutput.resource",
                                "artifact://payload/cse/post-output.bin"
                        ),
                        java.util.Map.entry(
                                "runtimeEquivalencePayload.tolerance.resource",
                                "artifact://payload/cse/tolerance.properties"
                        ),
                        java.util.Map.entry(
                                "runtimeEquivalencePayload.failureFixture.resource",
                                "artifact://payload/cse/failure-fixture.json"
                        ),
                        java.util.Map.entry(
                                "cseRuntimeEquivalencePayload.CpuReference",
                                "inputCases=3, comparedOutputs=1, outputNames=outA"
                        ),
                        java.util.Map.entry(
                                "cseRuntimeEquivalencePayload.PreOptimizationOutput",
                                "plans=1, insertions=1, skipped=0"
                        ),
                        java.util.Map.entry(
                                "cseRuntimeEquivalencePayload.PostOptimizationOutput",
                                "replacements=1, equivalent=true, successful=true"
                        ),
                        java.util.Map.entry(
                                "cseRuntimeEquivalencePayload.Tolerance",
                                "mode=exact-int, diagnostics=0, diagnosticFamilies={}"
                        ),
                        java.util.Map.entry(
                                "cseRuntimeEquivalencePayload.FailureFixture",
                                "none"
                        ),
                        java.util.Map.entry(
                                "cseRuntimeEquivalencePayload.ReferenceMode",
                                "original-ir-interpreter"
                        ),
                        java.util.Map.entry("cseRuntimeEquivalencePayload.Case.Count", "1"),
                        java.util.Map.entry("cseRuntimeEquivalencePayload.Case.0.Name", "case-a"),
                        java.util.Map.entry("cseRuntimeEquivalencePayload.Case.0.Successful", "true"),
                        java.util.Map.entry("cseRuntimeEquivalencePayload.Case.0.Input.Count", "1"),
                        java.util.Map.entry("cseRuntimeEquivalencePayload.Case.0.Input.0.Name", "x"),
                        java.util.Map.entry("cseRuntimeEquivalencePayload.Case.0.Input.0.Value", "7"),
                        java.util.Map.entry("cseRuntimeEquivalencePayload.Case.0.Output.Count", "1"),
                        java.util.Map.entry("cseRuntimeEquivalencePayload.Case.0.Output.0.Name", "outA"),
                        java.util.Map.entry("cseRuntimeEquivalencePayload.Case.0.Output.0.CpuReference", "36"),
                        java.util.Map.entry("cseRuntimeEquivalencePayload.Case.0.Output.0.PreOptimization", "36"),
                        java.util.Map.entry("cseRuntimeEquivalencePayload.Case.0.Output.0.PostOptimization", "36"),
                        java.util.Map.entry("cseRuntimeEquivalencePayload.Case.0.Output.0.Tolerance", "exact-int"),
                        java.util.Map.entry("cseRuntimeEquivalencePayload.Case.0.Output.0.Equivalent", "true"),
                        java.util.Map.entry(
                                "cseRuntimeEquivalencePayload.Case.0.FailureFixture.Diagnostic.Count",
                                "0"
                        )
                )
        ));
        GpuRuntimeIrOptimizationPassReport incompleteVectorPayload = GpuRuntimeIrOptimizationPassReport.applied(
                "optimizer:vector-v1",
                "irgpu:sha256:cse",
                "irgpu:sha256:vector",
                "proof:accepted",
                List.of("vector payload still needs tolerance metadata")
        ).withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                "ir-validation",
                "accepted",
                java.util.Map.of(
                        "optimizerFamily", "vector",
                        "runtimeEquivalencePayload.present", "true",
                        "runtimeEquivalencePayload.cpuReference.present", "true",
                        "runtimeEquivalencePayload.preOptimizationOutput.present", "true",
                        "runtimeEquivalencePayload.postOptimizationOutput.present", "true"
                )
        ));
        GpuRuntimeIrOptimizationReport optimizationReport = new GpuRuntimeIrOptimizationReport(
                Optional.of(optimized),
                List.of(completeCsePayload, incompleteVectorPayload),
                productionBackedStrategy()
        );

        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                optimizationReport,
                GpuRuntimeEquivalenceEvidence.passed(request, 2, 2, List.of("optimizer family payload outputs matched"))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);
        String payload = dump.artifact(GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD);

        assertTrue(payload.contains("status=recorded"));
        assertTrue(payload.contains("runtimeEquivalence.passed=true"));
        assertTrue(payload.contains("runtimeEquivalence.comparisonCase.count=0"));
        assertTrue(payload.contains("runtimeEquivalence.comparisonMode.summary=none"));
        assertTrue(payload.contains("familyBinding.status=not-bound"));
        assertTrue(payload.contains("familyBinding.eligible=false"));
        assertTrue(payload.contains("familyBinding.firstBlocker=runtime-comparison-cases-missing"));
        assertTrue(payload.contains("family.count=2"));
        assertTrue(payload.contains("family.0.name=cse"));
        assertTrue(payload.contains("family.0.runtimePayload.present=true"));
        assertTrue(payload.contains("family.0.cpuReference.present=true"));
        assertTrue(payload.contains("family.0.preOptimizationOutput.present=true"));
        assertTrue(payload.contains("family.0.postOptimizationOutput.present=true"));
        assertTrue(payload.contains("family.0.tolerance.present=true"));
        assertTrue(payload.contains("family.0.failureFixture.present=true"));
        assertTrue(payload.contains("family.0.complete=true"));
        assertTrue(payload.contains("family.0.firstMissing=none"));
        assertTrue(payload.contains("family.0.proof.source.summary=ir-validation=1"));
        assertTrue(payload.contains("family.0.proof.verdict.summary=accepted=1"));
        assertTrue(payload.contains("family.0.payload.resource.summary=artifact://payload/cse=1"));
        assertTrue(payload.contains("family.0.pass.0.optimizerVersion=optimizer:cse-v1"));
        assertTrue(payload.contains("family.0.pass.0.outcome=APPLIED"));
        assertTrue(payload.contains("family.0.pass.0.proofStatus=proof:accepted"));
        assertTrue(payload.contains("family.0.pass.0.proof.source=ir-validation"));
        assertTrue(payload.contains("family.0.pass.0.proof.verdict=accepted"));
        assertTrue(payload.contains("family.0.pass.0.payload.resource=artifact://payload/cse"));
        assertTrue(payload.contains("family.0.pass.0.cpuReference.resource=artifact://payload/cse/cpu-reference.bin"));
        assertTrue(payload.contains("family.0.pass.0.preOptimizationOutput.resource=artifact://payload/cse/pre-output.bin"));
        assertTrue(payload.contains("family.0.pass.0.postOptimizationOutput.resource=artifact://payload/cse/post-output.bin"));
        assertTrue(payload.contains("family.0.pass.0.tolerance.resource=artifact://payload/cse/tolerance.properties"));
        assertTrue(payload.contains("family.0.pass.0.failureFixture.resource=artifact://payload/cse/failure-fixture.json"));
        assertTrue(payload.contains("family.0.pass.0.cpuReference.payload=inputCases=3, comparedOutputs=1, outputNames=outA"));
        assertTrue(payload.contains("family.0.pass.0.preOptimizationOutput.payload=plans=1, insertions=1, skipped=0"));
        assertTrue(payload.contains("family.0.pass.0.postOptimizationOutput.payload=replacements=1, equivalent=true, successful=true"));
        assertTrue(payload.contains("family.0.pass.0.tolerance.payload=mode=exact-int, diagnostics=0, diagnosticFamilies={}"));
        assertTrue(payload.contains("family.0.pass.0.failureFixture.payload=none"));
        assertTrue(payload.contains("family.0.pass.0.firstDiagnostic=CSE runtime-equivalence payload captured"));
        assertTrue(payload.contains("family.0.pass.0.durable.directory=runtime-optimizer-family-equivalence-payload/family-0-cse/pass-0"));
        assertTrue(payload.contains("family.0.pass.0.durable.manifest.path=runtime-optimizer-family-equivalence-payload/family-0-cse/pass-0/manifest.properties"));
        assertTrue(payload.contains("family.0.pass.0.durable.diagnostics.path=runtime-optimizer-family-equivalence-payload/family-0-cse/pass-0/diagnostics.properties"));
        assertTrue(payload.contains("family.1.name=vector"));
        assertTrue(payload.contains("family.1.runtimePayload.present=true"));
        assertTrue(payload.contains("family.1.tolerance.present=false"));
        assertTrue(payload.contains("family.1.failureFixture.present=false"));
        assertTrue(payload.contains("family.1.complete=false"));
        assertTrue(payload.contains("family.1.firstMissing=tolerance-metadata"));
        assertTrue(payload.contains("family.1.proof.source.summary=ir-validation=1"));
        assertTrue(payload.contains("family.1.payload.resource.summary=not-recorded=1"));
        assertTrue(payload.contains("family.1.pass.0.payload.resource=not-recorded"));
        assertTrue(payload.contains("family.1.pass.0.tolerance.resource=not-recorded"));
        assertTrue(payload.contains("family.1.pass.0.cpuReference.payload=not-recorded"));
        assertTrue(payload.contains("family.1.pass.0.tolerance.payload=not-recorded"));
        assertTrue(payload.contains("family.1.pass.0.firstDiagnostic=vector payload still needs tolerance metadata"));
        assertTrue(payload.contains("family.complete.count=1"));
        assertTrue(payload.contains("family.complete.all=false"));

        String cseDirectory = "runtime-optimizer-family-equivalence-payload/family-0-cse/pass-0";
        assertTrue(dump.hasArtifact(cseDirectory + "/manifest.properties"));
        assertTrue(dump.artifact(cseDirectory + "/manifest.properties").contains("optimizerFamily=cse"));
        assertTrue(dump.artifact(cseDirectory + "/manifest.properties").contains("proof.verdict=accepted"));
        assertTrue(dump.artifact(cseDirectory + "/cpu-reference.properties").contains("status=recorded"));
        assertTrue(dump.artifact(cseDirectory + "/cpu-reference.properties").contains(
                "payload=inputCases=3, comparedOutputs=1, outputNames=outA"
        ));
        assertTrue(dump.artifact(cseDirectory + "/cpu-reference.properties").contains(
                "cseRuntimeEquivalencePayload.Case.0.Output.0.CpuReference"
        ));
        assertTrue(dump.artifact(cseDirectory + "/cpu-reference.properties").contains(
                "cseRuntimeEquivalencePayload.Case.0.Input.0.Value"
        ));
        assertFalse(dump.artifact(cseDirectory + "/cpu-reference.properties").contains(
                "cseRuntimeEquivalencePayload.Case.0.Output.0.PostOptimization"
        ));
        assertTrue(dump.artifact(cseDirectory + "/pre-optimization-output.properties").contains(
                "payload=plans=1, insertions=1, skipped=0"
        ));
        assertTrue(dump.artifact(cseDirectory + "/pre-optimization-output.properties").contains(
                "cseRuntimeEquivalencePayload.Case.0.Output.0.PreOptimization"
        ));
        assertTrue(dump.artifact(cseDirectory + "/post-optimization-output.properties").contains(
                "payload=replacements=1, equivalent=true, successful=true"
        ));
        assertTrue(dump.artifact(cseDirectory + "/post-optimization-output.properties").contains(
                "cseRuntimeEquivalencePayload.Case.0.Output.0.PostOptimization"
        ));
        assertTrue(dump.artifact(cseDirectory + "/post-optimization-output.properties").contains(
                "cseRuntimeEquivalencePayload.Case.0.Output.0.Equivalent"
        ));
        assertTrue(dump.artifact(cseDirectory + "/tolerance.properties").contains(
                "payload=mode=exact-int, diagnostics=0, diagnosticFamilies={}"
        ));
        assertTrue(dump.artifact(cseDirectory + "/tolerance.properties").contains(
                "cseRuntimeEquivalencePayload.Case.0.Output.0.Tolerance"
        ));
        assertTrue(dump.artifact(cseDirectory + "/failure-fixture.properties").contains("payload=none"));
        assertTrue(dump.artifact(cseDirectory + "/failure-fixture.properties").contains(
                "cseRuntimeEquivalencePayload.Case.0.FailureFixture.Diagnostic.Count"
        ));
        assertTrue(dump.artifact(cseDirectory + "/diagnostics.properties").contains(
                "firstDiagnostic=CSE runtime-equivalence payload captured"
        ));
        assertTrue(dump.artifact(cseDirectory + "/diagnostics.properties").contains(
                "cseRuntimeEquivalencePayload.CpuReference"
        ));

        String vectorDirectory = "runtime-optimizer-family-equivalence-payload/family-1-vector/pass-0";
        assertTrue(dump.hasArtifact(vectorDirectory + "/manifest.properties"));
        assertTrue(dump.artifact(vectorDirectory + "/tolerance.properties").contains("status=not-recorded"));
        assertTrue(dump.artifact(vectorDirectory + "/failure-fixture.properties").contains("payload.present=false"));
        assertTrue(dump.artifact(vectorDirectory + "/diagnostics.properties").contains(
                "firstDiagnostic=vector payload still needs tolerance metadata"
        ));
    }

    @Test
    void productionEnabledRuntimeArtifactsSelectOptimizedIrOnlyAfterAllGatesPass() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 2; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );
        GpuRuntimeCompileRequest originalRequest = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(original)
        );
        GpuRuntimeCompileRequest optimizedRequest = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of(), "vendor-tuned")
                        .withProductionPromotionDecision(productionEnabledDecision())
                        .withProductionPromotionOperatorAccepted(true),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeIrOptimizationReport optimizationReport = new GpuRuntimeIrOptimizationReport(
                Optional.of(optimized),
                List.of(GpuRuntimeIrOptimizationPassReport.applied(
                        "optimizer:production-safe",
                        "irgpu:sha256:original",
                        "irgpu:sha256:optimized",
                        "proof:production-fixture",
                        List.of("production fixture applied safe transform")
                ).withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "runtime-equivalence",
                        "accepted/passed",
                        java.util.Map.of("runtimeEquivalencePassed", "true")
                ))),
                productionBackedStrategy()
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                originalRequest,
                optimizedRequest,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(optimizedRequest, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(optimizedRequest),
                optimizationReport,
                GpuRuntimeEquivalenceEvidence.passed(optimizedRequest, 2, 2, List.of("production fixture equivalent"))
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("status=accepted"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("productionProfileRequested=true"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("runtimeEquivalencePassed=true"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("fallbackClean=true"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("strategyEvidenceBacked=true"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("vendorPromotionEligible=true"));
        assertTrue(dump.artifact("production-optimizer-gate.properties").contains("diagnostic.0=all production optimizer gates passed"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("selectedStage=optimized"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("optimizedIrRejected=false"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("fallbackDecision=none"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("productionIrGate.status=production-enabled"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("productionIrGate.accepted=true"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("productionIrGate.decisionMode=production-enabled"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("status=enabled"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("productionMutationEnabled=true"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("productionGateStatus=accepted"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("selectedStage=optimized"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("optimizedSelected=true"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("diagnostic.0=production runtime IR mutation is enabled because all production optimizer gates passed"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("status=production-enabled"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("selectedRuntimeIrStage=optimized"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("optimizerProductionGateStatus=accepted"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("productionMutationEnabled=true"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.applied.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("proofArtifact.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("proofArtifact.accepted.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("proofArtifact.blocking.count=0"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("optimizerFamily.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("optimizerFamily.promotionReady.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("optimizerFamily.summary=production-safe[passes=1, acceptedProof=1, blockingProof=0, rolledBack=0, failed=0, promotionReady=true]"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("fallbackDecision=none"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("selectedRuntimeIrStage=optimized"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("optimizedIrRejected=false"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("strategyName=strategy:production-fixture"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("promotionEligible=true"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("productionGateStatus=accepted"));
    }

    @Test
    void optimizerDriftArtifactCapturesRollbackAndFallbackCounts() {
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 2; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "fast"),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeIrOptimizationReport optimizationReport = new GpuRuntimeIrOptimizationReport(
                Optional.of(optimized),
                List.of(
                        GpuRuntimeIrOptimizationPassReport.applied(
                                "optimizer:applied",
                                "irgpu:sha256:original",
                                "irgpu:sha256:optimized",
                                "proof:mocked",
                                List.of("applied safe transform")
                        ),
                        GpuRuntimeIrOptimizationPassReport.rolledBack(
                                "optimizer:rollback",
                                "irgpu:sha256:optimized",
                                "irgpu:sha256:unsafe",
                                "proof:failed",
                                "unsafe proof",
                                List.of("rolled back unsafe transform")
                        ).withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                                "runtime-equivalence",
                                "rejected/blockingResultsPresent",
                                java.util.Map.of("runtimeEquivalencePassed", "false")
                        )
                        )
                ),
                GpuOptimizationStrategyDecision.none(request)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                optimizationReport
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.count=2"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.applied.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.rolledBack.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.failed.count=0"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("proofArtifact.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("proofArtifact.accepted.count=0"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("proofArtifact.blocking.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("optimizerFamily.count=2"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("optimizerFamily.promotionReady.count=0"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("optimizerFamily.summary=applied[passes=1, acceptedProof=0, blockingProof=0, rolledBack=0, failed=0, promotionReady=false], rollback[passes=1, acceptedProof=0, blockingProof=1, rolledBack=1, failed=0, promotionReady=false]"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("fallbackDecision=optimizer-rollback"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("selectedRuntimeIrStage=original"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("optimizedIrRejected=true"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("strategyName=strategy:none"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("productionGateStatus=not-requested"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("status=selected"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("selectedStage=original"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("optimizedDiffersFromOriginal=false"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("optimizationRequiresRollback=true"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("fallbackDecision=optimizer-rollback"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("optimizedIrRejected=true"));
        assertTrue(dump.artifact("runtime-ir-handoff.properties").contains("diagnostic.0=optimized IrGpu was rejected; original IrGpu remains selected for backend lowering"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("status=disabled"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("productionMutationEnabled=false"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("selectedStage=original"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("optimizedSelected=false"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("optimizedIrRejected=true"));
        assertTrue(dump.artifact("runtime-production-mutation-safety.properties").contains("fallbackDecision=optimizer-rollback"));
    }

    @Test
    void optimizerDriftArtifactCapturesReadOnlyReplacementPlanTelemetry() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 1; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "diagnostic"),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(original)
        );
        GpuRuntimeIrOptimizationPassReport peepholeReport = GpuRuntimeIrOptimizationPassReport.skipped(
                "optimizer:peephole-diagnostic-v1",
                "irgpu:sha256:original",
                "typed peephole replacement plans recorded without mutation"
        ).withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                "runtime.peephole.preflight",
                "blocked",
                Map.ofEntries(
                        Map.entry("optimizerFamily", "peephole"),
                        Map.entry("replacementPlan.partial.count", "1"),
                        Map.entry("replacementPlan.firstBlocker", "multiply-operands-incomplete"),
                        Map.entry("replacementPlan.validation.invalid.count", "1"),
                        Map.entry("replacementPlan.validation.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("rewriteSketch.count", "6"),
                        Map.entry("rewriteSketch.ready.count", "5"),
                        Map.entry("rewriteSketch.blocked.count", "1"),
                        Map.entry("rewriteSketch.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("rewriteVisitor.count", "6"),
                        Map.entry("rewriteVisitor.ready.count", "5"),
                        Map.entry("rewriteVisitor.blocked.count", "1"),
                        Map.entry("rewriteVisitor.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("replacementBlueprint.count", "6"),
                        Map.entry("replacementBlueprint.ready.count", "5"),
                        Map.entry("replacementBlueprint.blocked.count", "1"),
                        Map.entry("replacementBlueprint.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("rewriteTransaction.count", "6"),
                        Map.entry("rewriteTransaction.ready.count", "5"),
                        Map.entry("rewriteTransaction.blocked.count", "1"),
                        Map.entry("rewriteTransaction.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("nodeIdAllocation.count", "6"),
                        Map.entry("nodeIdAllocation.ready.count", "5"),
                        Map.entry("nodeIdAllocation.blocked.count", "1"),
                        Map.entry("nodeIdAllocation.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("replacementNode.count", "6"),
                        Map.entry("replacementNode.ready.count", "5"),
                        Map.entry("replacementNode.blocked.count", "1"),
                        Map.entry("replacementNode.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("graphPatch.count", "6"),
                        Map.entry("graphPatch.ready.count", "5"),
                        Map.entry("graphPatch.blocked.count", "1"),
                        Map.entry("graphPatch.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("transformedGraph.count", "6"),
                        Map.entry("transformedGraph.ready.count", "5"),
                        Map.entry("transformedGraph.blocked.count", "1"),
                        Map.entry("transformedGraph.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("irArtifactEnvelope.count", "6"),
                        Map.entry("irArtifactEnvelope.ready.count", "5"),
                        Map.entry("irArtifactEnvelope.blocked.count", "1"),
                        Map.entry("irArtifactEnvelope.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("artifactProofBinding.count", "6"),
                        Map.entry("artifactProofBinding.ready.count", "0"),
                        Map.entry("artifactProofBinding.blocked.count", "6"),
                        Map.entry("artifactProofBinding.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("artifactSelection.count", "6"),
                        Map.entry("artifactSelection.ready.count", "0"),
                        Map.entry("artifactSelection.blocked.count", "6"),
                        Map.entry("artifactSelection.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("rewriteSketch.conflict.count", "1"),
                        Map.entry("rewriteSketch.conflict.firstBlocker", "rewrite-sketch-covered-node-overlap"),
                        Map.entry("rewriteSelection.status", "blocked"),
                        Map.entry("rewriteSelection.firstBlocker", "rewrite-sketch-conflict-resolution-required"),
                        Map.entry("rewriteProof.status", "blocked"),
                        Map.entry("rewriteProof.firstBlocker", "rewrite-sketch-conflict-resolution-required"),
                        Map.entry("rewriteReviewPackage.status", "blocked"),
                        Map.entry("rewriteReviewPackage.firstBlocker", "rewrite-sketch-conflict-resolution-required"),
                        Map.entry("rewriteReviewPackage.complete", "false"),
                        Map.entry("rewriteReviewPackage.selectedIrReplacement", "false"),
                        Map.entry("rule.count", "2"),
                        Map.entry("rule.0.id", "madFma"),
                        Map.entry("rule.0.version", "peephole-rule:mad-fma-v1"),
                        Map.entry("rule.0.extensionId", "javatogpu.peephole.mad-fma"),
                        Map.entry("rule.0.extensionVersion", "peephole-rule:mad-fma-v1"),
                        Map.entry("rule.0.proofStatus", "candidate-detected"),
                        Map.entry("rule.0.candidate.count", "2"),
                        Map.entry("rule.0.proposal.count", "0"),
                        Map.entry("rule.0.applied.count", "0"),
                        Map.entry("rule.0.skipped.count", "0"),
                        Map.entry("rule.0.blocked.count", "1"),
                        Map.entry("rule.0.mutationProposed", "false"),
                        Map.entry("rule.0.replacementPlan.count", "3"),
                        Map.entry("rule.0.replacementPlan.complete.count", "2"),
                        Map.entry("rule.0.replacementPlan.partial.count", "1"),
                        Map.entry("rule.0.replacementPlan.firstBlocker", "multiply-operands-incomplete"),
                        Map.entry("rule.0.replacementPlan.validation.count", "3"),
                        Map.entry("rule.0.replacementPlan.validation.valid.count", "2"),
                        Map.entry("rule.0.replacementPlan.validation.invalid.count", "1"),
                        Map.entry("rule.0.replacementPlan.validation.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("rule.0.rewriteSketch.count", "3"),
                        Map.entry("rule.0.rewriteSketch.ready.count", "2"),
                        Map.entry("rule.0.rewriteSketch.blocked.count", "1"),
                        Map.entry("rule.0.rewriteSketch.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("rule.0.rewriteVisitor.count", "3"),
                        Map.entry("rule.0.rewriteVisitor.ready.count", "2"),
                        Map.entry("rule.0.rewriteVisitor.blocked.count", "1"),
                        Map.entry("rule.0.rewriteVisitor.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("rule.0.replacementBlueprint.count", "3"),
                        Map.entry("rule.0.replacementBlueprint.ready.count", "2"),
                        Map.entry("rule.0.replacementBlueprint.blocked.count", "1"),
                        Map.entry("rule.0.replacementBlueprint.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("rule.0.rewriteTransaction.count", "3"),
                        Map.entry("rule.0.rewriteTransaction.ready.count", "2"),
                        Map.entry("rule.0.rewriteTransaction.blocked.count", "1"),
                        Map.entry("rule.0.rewriteTransaction.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("rule.0.nodeIdAllocation.count", "3"),
                        Map.entry("rule.0.nodeIdAllocation.ready.count", "2"),
                        Map.entry("rule.0.nodeIdAllocation.blocked.count", "1"),
                        Map.entry("rule.0.nodeIdAllocation.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("rule.0.replacementNode.count", "3"),
                        Map.entry("rule.0.replacementNode.ready.count", "2"),
                        Map.entry("rule.0.replacementNode.blocked.count", "1"),
                        Map.entry("rule.0.replacementNode.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("rule.0.graphPatch.count", "3"),
                        Map.entry("rule.0.graphPatch.ready.count", "2"),
                        Map.entry("rule.0.graphPatch.blocked.count", "1"),
                        Map.entry("rule.0.graphPatch.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("rule.0.transformedGraph.count", "3"),
                        Map.entry("rule.0.transformedGraph.ready.count", "2"),
                        Map.entry("rule.0.transformedGraph.blocked.count", "1"),
                        Map.entry("rule.0.transformedGraph.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("rule.0.irArtifactEnvelope.count", "3"),
                        Map.entry("rule.0.irArtifactEnvelope.ready.count", "2"),
                        Map.entry("rule.0.irArtifactEnvelope.blocked.count", "1"),
                        Map.entry("rule.0.irArtifactEnvelope.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("rule.0.artifactProofBinding.count", "3"),
                        Map.entry("rule.0.artifactProofBinding.ready.count", "0"),
                        Map.entry("rule.0.artifactProofBinding.blocked.count", "3"),
                        Map.entry("rule.0.artifactProofBinding.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("rule.0.artifactSelection.count", "3"),
                        Map.entry("rule.0.artifactSelection.ready.count", "0"),
                        Map.entry("rule.0.artifactSelection.blocked.count", "3"),
                        Map.entry("rule.0.artifactSelection.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("rule.0.rewriteSelection.status", "blocked"),
                        Map.entry("rule.0.rewriteSelection.firstBlocker", "replacement-plan-root-missing"),
                        Map.entry("rule.0.rewriteProof.status", "blocked"),
                        Map.entry("rule.0.rewriteProof.firstBlocker", "runtime-equivalence-payload-missing"),
                        Map.entry("rule.0.rewriteReviewPackage.status", "blocked"),
                        Map.entry("rule.0.rewriteReviewPackage.firstBlocker", "runtime-equivalence-payload-missing"),
                        Map.entry("rule.0.rewriteReviewPackage.complete", "false"),
                        Map.entry("rule.0.rewriteReviewPackage.selectedIrReplacement", "false"),
                        Map.entry("rule.0.firstBlocker", "multiply-operands-incomplete"),
                        Map.entry("rule.1.id", "clamp"),
                        Map.entry("rule.1.version", "peephole-rule:clamp-v1"),
                        Map.entry("rule.1.extensionId", "javatogpu.peephole.clamp"),
                        Map.entry("rule.1.extensionVersion", "peephole-rule:clamp-v1"),
                        Map.entry("rule.1.proofStatus", "no-candidate"),
                        Map.entry("rule.1.candidate.count", "0"),
                        Map.entry("rule.1.proposal.count", "0"),
                        Map.entry("rule.1.applied.count", "0"),
                        Map.entry("rule.1.skipped.count", "1"),
                        Map.entry("rule.1.blocked.count", "0"),
                        Map.entry("rule.1.mutationProposed", "false"),
                        Map.entry("rule.1.replacementPlan.count", "3"),
                        Map.entry("rule.1.replacementPlan.complete.count", "3"),
                        Map.entry("rule.1.replacementPlan.partial.count", "0"),
                        Map.entry("rule.1.replacementPlan.firstBlocker", "none"),
                        Map.entry("rule.1.replacementPlan.validation.count", "3"),
                        Map.entry("rule.1.replacementPlan.validation.valid.count", "3"),
                        Map.entry("rule.1.replacementPlan.validation.invalid.count", "0"),
                        Map.entry("rule.1.replacementPlan.validation.firstBlocker", "none"),
                        Map.entry("rule.1.rewriteSketch.count", "3"),
                        Map.entry("rule.1.rewriteSketch.ready.count", "3"),
                        Map.entry("rule.1.rewriteSketch.blocked.count", "0"),
                        Map.entry("rule.1.rewriteSketch.firstBlocker", "none"),
                        Map.entry("rule.1.rewriteVisitor.count", "3"),
                        Map.entry("rule.1.rewriteVisitor.ready.count", "3"),
                        Map.entry("rule.1.rewriteVisitor.blocked.count", "0"),
                        Map.entry("rule.1.rewriteVisitor.firstBlocker", "none"),
                        Map.entry("rule.1.replacementBlueprint.count", "3"),
                        Map.entry("rule.1.replacementBlueprint.ready.count", "3"),
                        Map.entry("rule.1.replacementBlueprint.blocked.count", "0"),
                        Map.entry("rule.1.replacementBlueprint.firstBlocker", "none"),
                        Map.entry("rule.1.rewriteTransaction.count", "3"),
                        Map.entry("rule.1.rewriteTransaction.ready.count", "3"),
                        Map.entry("rule.1.rewriteTransaction.blocked.count", "0"),
                        Map.entry("rule.1.rewriteTransaction.firstBlocker", "none"),
                        Map.entry("rule.1.nodeIdAllocation.count", "3"),
                        Map.entry("rule.1.nodeIdAllocation.ready.count", "3"),
                        Map.entry("rule.1.nodeIdAllocation.blocked.count", "0"),
                        Map.entry("rule.1.nodeIdAllocation.firstBlocker", "none"),
                        Map.entry("rule.1.replacementNode.count", "3"),
                        Map.entry("rule.1.replacementNode.ready.count", "3"),
                        Map.entry("rule.1.replacementNode.blocked.count", "0"),
                        Map.entry("rule.1.replacementNode.firstBlocker", "none"),
                        Map.entry("rule.1.graphPatch.count", "3"),
                        Map.entry("rule.1.graphPatch.ready.count", "3"),
                        Map.entry("rule.1.graphPatch.blocked.count", "0"),
                        Map.entry("rule.1.graphPatch.firstBlocker", "none"),
                        Map.entry("rule.1.transformedGraph.count", "3"),
                        Map.entry("rule.1.transformedGraph.ready.count", "3"),
                        Map.entry("rule.1.transformedGraph.blocked.count", "0"),
                        Map.entry("rule.1.transformedGraph.firstBlocker", "none"),
                        Map.entry("rule.1.irArtifactEnvelope.count", "3"),
                        Map.entry("rule.1.irArtifactEnvelope.ready.count", "3"),
                        Map.entry("rule.1.irArtifactEnvelope.blocked.count", "0"),
                        Map.entry("rule.1.irArtifactEnvelope.firstBlocker", "none"),
                        Map.entry("rule.1.artifactProofBinding.count", "3"),
                        Map.entry("rule.1.artifactProofBinding.ready.count", "0"),
                        Map.entry("rule.1.artifactProofBinding.blocked.count", "3"),
                        Map.entry("rule.1.artifactProofBinding.firstBlocker", "runtime-equivalence-payload-missing"),
                        Map.entry("rule.1.artifactSelection.count", "3"),
                        Map.entry("rule.1.artifactSelection.ready.count", "0"),
                        Map.entry("rule.1.artifactSelection.blocked.count", "3"),
                        Map.entry("rule.1.artifactSelection.firstBlocker", "runtime-equivalence-payload-missing"),
                        Map.entry("rule.1.rewriteSelection.status", "blocked"),
                        Map.entry("rule.1.rewriteSelection.firstBlocker", "rewrite-builder-not-implemented"),
                        Map.entry("rule.1.rewriteProof.status", "blocked"),
                        Map.entry("rule.1.rewriteProof.firstBlocker", "runtime-equivalence-payload-missing"),
                        Map.entry("rule.1.rewriteReviewPackage.status", "blocked"),
                        Map.entry("rule.1.rewriteReviewPackage.firstBlocker", "runtime-equivalence-payload-missing"),
                        Map.entry("rule.1.rewriteReviewPackage.complete", "false"),
                        Map.entry("rule.1.rewriteReviewPackage.selectedIrReplacement", "false"),
                        Map.entry("rule.1.firstBlocker", "none")
                )
        ));
        GpuRuntimeIrOptimizationReport optimizationReport = new GpuRuntimeIrOptimizationReport(
                Optional.of(original),
                List.of(peepholeReport),
                GpuOptimizationStrategyDecision.none(request)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                optimizationReport
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);
        String drift = dump.artifact("runtime-optimizer-drift.properties");

        assertTrue(drift.contains("pass.count=1"));
        assertTrue(drift.contains("pass.skipped.count=1"));
        assertTrue(drift.contains("proofArtifact.count=1"));
        assertTrue(drift.contains("replacementPlan.complete.count=5"));
        assertTrue(drift.contains("replacementPlan.partial.count=1"));
        assertTrue(drift.contains("replacementPlan.firstBlocker=multiply-operands-incomplete"));
        assertTrue(drift.contains("replacementPlan.validation.count=6"));
        assertTrue(drift.contains("replacementPlan.validation.valid.count=5"));
        assertTrue(drift.contains("replacementPlan.validation.invalid.count=1"));
        assertTrue(drift.contains("replacementPlan.validation.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("rewriteVisitor.count=6"));
        assertTrue(drift.contains("rewriteVisitor.ready.count=5"));
        assertTrue(drift.contains("rewriteVisitor.blocked.count=1"));
        assertTrue(drift.contains("rewriteVisitor.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("rewriteVisitor.visitorImplemented=true"));
        assertTrue(drift.contains("rewriteVisitor.replacementBuilderImplemented=false"));
        assertTrue(drift.contains("rewriteVisitor.transformedIrBuilt=false"));
        assertTrue(drift.contains("rewriteVisitor.selectedIrReplacement=false"));
        assertTrue(drift.contains("replacementBlueprint.count=6"));
        assertTrue(drift.contains("replacementBlueprint.ready.count=5"));
        assertTrue(drift.contains("replacementBlueprint.blocked.count=1"));
        assertTrue(drift.contains("replacementBlueprint.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("replacementBlueprint.blueprintImplemented=true"));
        assertTrue(drift.contains("replacementBlueprint.replacementBuilderImplemented=false"));
        assertTrue(drift.contains("replacementBlueprint.transformedIrBuilt=false"));
        assertTrue(drift.contains("replacementBlueprint.selectedIrReplacement=false"));
        assertTrue(drift.contains("rewriteTransaction.count=6"));
        assertTrue(drift.contains("rewriteTransaction.ready.count=5"));
        assertTrue(drift.contains("rewriteTransaction.blocked.count=1"));
        assertTrue(drift.contains("rewriteTransaction.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("rewriteTransaction.transactionPreflightImplemented=true"));
        assertTrue(drift.contains("rewriteTransaction.nodeIdAllocatorImplemented=false"));
        assertTrue(drift.contains("rewriteTransaction.graphRewriteImplemented=false"));
        assertTrue(drift.contains("rewriteTransaction.transformedIrBuilt=false"));
        assertTrue(drift.contains("rewriteTransaction.selectedIrReplacement=false"));
        assertTrue(drift.contains("nodeIdAllocation.count=6"));
        assertTrue(drift.contains("nodeIdAllocation.ready.count=5"));
        assertTrue(drift.contains("nodeIdAllocation.blocked.count=1"));
        assertTrue(drift.contains("nodeIdAllocation.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("nodeIdAllocation.allocationPreflightImplemented=true"));
        assertTrue(drift.contains("nodeIdAllocation.nodeIdsReserved=false"));
        assertTrue(drift.contains("nodeIdAllocation.nodeIdAllocatorApplied=false"));
        assertTrue(drift.contains("nodeIdAllocation.graphRewriteImplemented=false"));
        assertTrue(drift.contains("nodeIdAllocation.transformedIrBuilt=false"));
        assertTrue(drift.contains("nodeIdAllocation.selectedIrReplacement=false"));
        assertTrue(drift.contains("replacementNode.count=6"));
        assertTrue(drift.contains("replacementNode.ready.count=5"));
        assertTrue(drift.contains("replacementNode.blocked.count=1"));
        assertTrue(drift.contains("replacementNode.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("replacementNode.replacementNodePreflightImplemented=true"));
        assertTrue(drift.contains("replacementNode.replacementNodeBuilt=false"));
        assertTrue(drift.contains("replacementNode.replacementBuilderImplemented=false"));
        assertTrue(drift.contains("replacementNode.graphRewriteImplemented=false"));
        assertTrue(drift.contains("replacementNode.transformedIrBuilt=false"));
        assertTrue(drift.contains("replacementNode.selectedIrReplacement=false"));
        assertTrue(drift.contains("graphPatch.count=6"));
        assertTrue(drift.contains("graphPatch.ready.count=5"));
        assertTrue(drift.contains("graphPatch.blocked.count=1"));
        assertTrue(drift.contains("graphPatch.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("graphPatch.graphPatchPreflightImplemented=true"));
        assertTrue(drift.contains("graphPatch.graphPatchApplied=false"));
        assertTrue(drift.contains("graphPatch.graphRewriteImplemented=false"));
        assertTrue(drift.contains("graphPatch.transformedIrBuilt=false"));
        assertTrue(drift.contains("graphPatch.selectedIrReplacement=false"));
        assertTrue(drift.contains("transformedGraph.count=6"));
        assertTrue(drift.contains("transformedGraph.ready.count=5"));
        assertTrue(drift.contains("transformedGraph.blocked.count=1"));
        assertTrue(drift.contains("transformedGraph.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("transformedGraph.materializationPreflightImplemented=true"));
        assertTrue(drift.contains("transformedGraph.transformedGraphBuilt=false"));
        assertTrue(drift.contains("transformedGraph.transformedIrBuilt=false"));
        assertTrue(drift.contains("transformedGraph.graphPatchApplied=false"));
        assertTrue(drift.contains("transformedGraph.graphRewriteImplemented=false"));
        assertTrue(drift.contains("transformedGraph.selectedIrReplacement=false"));
        assertTrue(drift.contains("irArtifactEnvelope.count=6"));
        assertTrue(drift.contains("irArtifactEnvelope.ready.count=5"));
        assertTrue(drift.contains("irArtifactEnvelope.blocked.count=1"));
        assertTrue(drift.contains("irArtifactEnvelope.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("irArtifactEnvelope.artifactEnvelopePreflightImplemented=true"));
        assertTrue(drift.contains("irArtifactEnvelope.artifactEnvelopeBuilt=false"));
        assertTrue(drift.contains("irArtifactEnvelope.optimizedArtifactBuilt=false"));
        assertTrue(drift.contains("irArtifactEnvelope.transformedGraphBuilt=false"));
        assertTrue(drift.contains("irArtifactEnvelope.transformedIrBuilt=false"));
        assertTrue(drift.contains("irArtifactEnvelope.selectedIrReplacement=false"));
        assertTrue(drift.contains("artifactProofBinding.count=6"));
        assertTrue(drift.contains("artifactProofBinding.ready.count=0"));
        assertTrue(drift.contains("artifactProofBinding.blocked.count=6"));
        assertTrue(drift.contains("artifactProofBinding.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("artifactProofBinding.bindingPreflightImplemented=true"));
        assertTrue(drift.contains("artifactProofBinding.proofBound=false"));
        assertTrue(drift.contains("artifactProofBinding.rollbackBound=false"));
        assertTrue(drift.contains("artifactProofBinding.approvalBound=false"));
        assertTrue(drift.contains("artifactProofBinding.optimizedArtifactBuilt=false"));
        assertTrue(drift.contains("artifactProofBinding.selectedIrReplacement=false"));
        assertTrue(drift.contains("artifactSelection.count=6"));
        assertTrue(drift.contains("artifactSelection.ready.count=0"));
        assertTrue(drift.contains("artifactSelection.blocked.count=6"));
        assertTrue(drift.contains("artifactSelection.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("artifactSelection.selectionPreflightImplemented=true"));
        assertTrue(drift.contains("artifactSelection.productionGateRequired=true"));
        assertTrue(drift.contains("artifactSelection.productionGateAccepted=false"));
        assertTrue(drift.contains("artifactSelection.selectionApplied=false"));
        assertTrue(drift.contains("artifactSelection.optimizedArtifactSelected=false"));
        assertTrue(drift.contains("artifactSelection.selectedIrReplacement=false"));
        assertTrue(drift.contains("rewriteSketch.count=6"));
        assertTrue(drift.contains("rewriteSketch.ready.count=5"));
        assertTrue(drift.contains("rewriteSketch.blocked.count=1"));
        assertTrue(drift.contains("rewriteSketch.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("rewriteSketch.rewriteBuilderImplemented=false"));
        assertTrue(drift.contains("rewriteSketch.mutationAllowed=false"));
        assertTrue(drift.contains("rewriteSketch.selectedIrReplacement=false"));
        assertTrue(drift.contains("rewriteSketch.conflict.count=1"));
        assertTrue(drift.contains("rewriteSketch.conflict.firstBlocker=rewrite-sketch-covered-node-overlap"));
        assertTrue(drift.contains("rewriteSketch.conflict.conflictResolutionImplemented=false"));
        assertTrue(drift.contains("rewriteSketch.conflict.selectionApplied=false"));
        assertTrue(drift.contains("rewriteSelection.sketch.count=6"));
        assertTrue(drift.contains("rewriteSelection.sketch.ready.count=5"));
        assertTrue(drift.contains("rewriteSelection.sketch.blocked.count=1"));
        assertTrue(drift.contains("rewriteSelection.conflict.count=1"));
        assertTrue(drift.contains("rewriteSelection.status=blocked"));
        assertTrue(drift.contains("rewriteSelection.firstBlocker=rewrite-sketch-conflict-resolution-required"));
        assertTrue(drift.contains("rewriteSelection.rewriteBuilderImplemented=false"));
        assertTrue(drift.contains("rewriteSelection.conflictResolutionImplemented=false"));
        assertTrue(drift.contains("rewriteSelection.runtimeEquivalenceRequired=true"));
        assertTrue(drift.contains("rewriteSelection.runtimeEquivalenceProven=false"));
        assertTrue(drift.contains("rewriteSelection.approvalRequired=true"));
        assertTrue(drift.contains("rewriteSelection.approvalAccepted=false"));
        assertTrue(drift.contains("rewriteSelection.mutationAllowed=false"));
        assertTrue(drift.contains("rewriteSelection.selectionApplied=false"));
        assertTrue(drift.contains("rewriteSelection.selectedIrReplacement=false"));
        assertTrue(drift.contains("rewriteProof.status=blocked"));
        assertTrue(drift.contains("rewriteProof.firstBlocker=rewrite-sketch-conflict-resolution-required"));
        assertTrue(drift.contains("rewriteProof.proofAccepted=false"));
        assertTrue(drift.contains("rewriteProof.runtimeEquivalencePayload.present=false"));
        assertTrue(drift.contains("rewriteProof.runtimeEquivalencePayload.complete=false"));
        assertTrue(drift.contains("rewriteProof.rollbackEvidence.present=false"));
        assertTrue(drift.contains("rewriteProof.rollbackClean=false"));
        assertTrue(drift.contains("rewriteProof.approvalAccepted=false"));
        assertTrue(drift.contains("rewriteProof.mutationAllowed=false"));
        assertTrue(drift.contains("rewriteProof.selectedIrReplacement=false"));
        assertTrue(drift.contains("rewriteReviewPackage.status=blocked"));
        assertTrue(drift.contains("rewriteReviewPackage.firstBlocker=rewrite-sketch-conflict-resolution-required"));
        assertTrue(drift.contains("rewriteReviewPackage.complete=false"));
        assertTrue(drift.contains("rewriteReviewPackage.selectedIrReplacement=false"));
        assertTrue(drift.contains("optimizerRule.count=2"));
        assertTrue(drift.contains("optimizerRule.0.id=madFma"));
        assertTrue(drift.contains("optimizerRule.0.blocked.count=1"));
        assertTrue(drift.contains("optimizerRule.0.replacementPlan.validation.count=3"));
        assertTrue(drift.contains("optimizerRule.0.replacementPlan.validation.invalid.count=1"));
        assertTrue(drift.contains("optimizerRule.0.replacementPlan.validation.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("optimizerRule.0.rewriteVisitor.count=3"));
        assertTrue(drift.contains("optimizerRule.0.rewriteVisitor.ready.count=2"));
        assertTrue(drift.contains("optimizerRule.0.rewriteVisitor.blocked.count=1"));
        assertTrue(drift.contains("optimizerRule.0.rewriteVisitor.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("optimizerRule.0.rewriteVisitor.selectedIrReplacement=false"));
        assertTrue(drift.contains("optimizerRule.0.replacementBlueprint.count=3"));
        assertTrue(drift.contains("optimizerRule.0.replacementBlueprint.ready.count=2"));
        assertTrue(drift.contains("optimizerRule.0.replacementBlueprint.blocked.count=1"));
        assertTrue(drift.contains("optimizerRule.0.replacementBlueprint.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("optimizerRule.0.replacementBlueprint.selectedIrReplacement=false"));
        assertTrue(drift.contains("optimizerRule.0.rewriteTransaction.count=3"));
        assertTrue(drift.contains("optimizerRule.0.rewriteTransaction.ready.count=2"));
        assertTrue(drift.contains("optimizerRule.0.rewriteTransaction.blocked.count=1"));
        assertTrue(drift.contains("optimizerRule.0.rewriteTransaction.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("optimizerRule.0.rewriteTransaction.selectedIrReplacement=false"));
        assertTrue(drift.contains("optimizerRule.0.nodeIdAllocation.count=3"));
        assertTrue(drift.contains("optimizerRule.0.nodeIdAllocation.ready.count=2"));
        assertTrue(drift.contains("optimizerRule.0.nodeIdAllocation.blocked.count=1"));
        assertTrue(drift.contains("optimizerRule.0.nodeIdAllocation.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("optimizerRule.0.nodeIdAllocation.nodeIdsReserved=false"));
        assertTrue(drift.contains("optimizerRule.0.nodeIdAllocation.selectedIrReplacement=false"));
        assertTrue(drift.contains("optimizerRule.0.replacementNode.count=3"));
        assertTrue(drift.contains("optimizerRule.0.replacementNode.ready.count=2"));
        assertTrue(drift.contains("optimizerRule.0.replacementNode.blocked.count=1"));
        assertTrue(drift.contains("optimizerRule.0.replacementNode.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("optimizerRule.0.replacementNode.replacementNodeBuilt=false"));
        assertTrue(drift.contains("optimizerRule.0.replacementNode.selectedIrReplacement=false"));
        assertTrue(drift.contains("optimizerRule.0.graphPatch.count=3"));
        assertTrue(drift.contains("optimizerRule.0.graphPatch.ready.count=2"));
        assertTrue(drift.contains("optimizerRule.0.graphPatch.blocked.count=1"));
        assertTrue(drift.contains("optimizerRule.0.graphPatch.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("optimizerRule.0.graphPatch.graphPatchApplied=false"));
        assertTrue(drift.contains("optimizerRule.0.graphPatch.selectedIrReplacement=false"));
        assertTrue(drift.contains("optimizerRule.0.transformedGraph.count=3"));
        assertTrue(drift.contains("optimizerRule.0.transformedGraph.ready.count=2"));
        assertTrue(drift.contains("optimizerRule.0.transformedGraph.blocked.count=1"));
        assertTrue(drift.contains("optimizerRule.0.transformedGraph.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("optimizerRule.0.transformedGraph.transformedGraphBuilt=false"));
        assertTrue(drift.contains("optimizerRule.0.transformedGraph.selectedIrReplacement=false"));
        assertTrue(drift.contains("optimizerRule.0.irArtifactEnvelope.count=3"));
        assertTrue(drift.contains("optimizerRule.0.irArtifactEnvelope.ready.count=2"));
        assertTrue(drift.contains("optimizerRule.0.irArtifactEnvelope.blocked.count=1"));
        assertTrue(drift.contains("optimizerRule.0.irArtifactEnvelope.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("optimizerRule.0.irArtifactEnvelope.artifactEnvelopeBuilt=false"));
        assertTrue(drift.contains("optimizerRule.0.irArtifactEnvelope.optimizedArtifactBuilt=false"));
        assertTrue(drift.contains("optimizerRule.0.irArtifactEnvelope.selectedIrReplacement=false"));
        assertTrue(drift.contains("optimizerRule.0.artifactProofBinding.count=3"));
        assertTrue(drift.contains("optimizerRule.0.artifactProofBinding.ready.count=0"));
        assertTrue(drift.contains("optimizerRule.0.artifactProofBinding.blocked.count=3"));
        assertTrue(drift.contains("optimizerRule.0.artifactProofBinding.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("optimizerRule.0.artifactProofBinding.proofBound=false"));
        assertTrue(drift.contains("optimizerRule.0.artifactProofBinding.selectedIrReplacement=false"));
        assertTrue(drift.contains("optimizerRule.0.artifactSelection.count=3"));
        assertTrue(drift.contains("optimizerRule.0.artifactSelection.ready.count=0"));
        assertTrue(drift.contains("optimizerRule.0.artifactSelection.blocked.count=3"));
        assertTrue(drift.contains("optimizerRule.0.artifactSelection.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("optimizerRule.0.artifactSelection.selectionApplied=false"));
        assertTrue(drift.contains("optimizerRule.0.artifactSelection.selectedIrReplacement=false"));
        assertTrue(drift.contains("optimizerRule.0.rewriteSketch.count=3"));
        assertTrue(drift.contains("optimizerRule.0.rewriteSketch.ready.count=2"));
        assertTrue(drift.contains("optimizerRule.0.rewriteSketch.blocked.count=1"));
        assertTrue(drift.contains("optimizerRule.0.rewriteSketch.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("optimizerRule.0.rewriteSelection.status=blocked"));
        assertTrue(drift.contains("optimizerRule.0.rewriteSelection.firstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("optimizerRule.0.rewriteProof.status=blocked"));
        assertTrue(drift.contains("optimizerRule.0.rewriteProof.firstBlocker=runtime-equivalence-payload-missing"));
        assertTrue(drift.contains("optimizerRule.0.rewriteProof.proofAccepted=false"));
        assertTrue(drift.contains("optimizerRule.0.rewriteProof.selectedIrReplacement=false"));
        assertTrue(drift.contains("optimizerRule.0.rewriteReviewPackage.status=blocked"));
        assertTrue(drift.contains("optimizerRule.0.rewriteReviewPackage.firstBlocker=runtime-equivalence-payload-missing"));
        assertTrue(drift.contains("optimizerRule.0.rewriteReviewPackage.complete=false"));
        assertTrue(drift.contains("optimizerRule.0.rewriteReviewPackage.selectedIrReplacement=false"));
        assertTrue(drift.contains("optimizerRule.0.firstBlocker=multiply-operands-incomplete"));
        assertTrue(drift.contains("optimizerRule.1.id=clamp"));
        assertTrue(drift.contains("optimizerRule.1.skipped.count=1"));
        assertTrue(drift.contains("optimizerRule.summary=madFma[candidates=2, proposals=0, applied=0, skipped=0, blocked=1"));
        assertTrue(drift.contains("planValidations=3, invalidPlanValidations=1, planValidationFirstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("replacementNodes=3, readyReplacementNodes=2, blockedReplacementNodes=1, replacementNodeFirstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("graphPatches=3, readyGraphPatches=2, blockedGraphPatches=1, graphPatchFirstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("transformedGraphs=3, readyTransformedGraphs=2, blockedTransformedGraphs=1, transformedGraphFirstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("irArtifactEnvelopes=3, readyIrArtifactEnvelopes=2, blockedIrArtifactEnvelopes=1, irArtifactEnvelopeFirstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("artifactProofBindings=3, readyArtifactProofBindings=0, blockedArtifactProofBindings=3, artifactProofBindingFirstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("artifactSelections=3, readyArtifactSelections=0, blockedArtifactSelections=3, artifactSelectionFirstBlocker=replacement-plan-root-missing"));
        assertTrue(drift.contains("rewriteSketches=3, readySketches=2, blockedSketches=1, rewriteSketchFirstBlocker=replacement-plan-root-missing, rewriteSelectionStatus=blocked, rewriteSelectionFirstBlocker=replacement-plan-root-missing, rewriteProofStatus=blocked, rewriteProofFirstBlocker=runtime-equivalence-payload-missing"));
        assertTrue(drift.contains("optimizerFamily.count=1"));
        assertTrue(drift.contains("optimizerFamily.summary=peephole[passes=1, acceptedProof=0, blockingProof=1, rolledBack=0, failed=0, promotionReady=false]"));
        assertTrue(drift.contains("selectedRuntimeIrStage=original"));
        assertTrue(drift.contains("optimizedIrRejected=false"));
    }

    @Test
    void dumpsRuntimeIrOptimizerEvidenceArtifactForProposalBridgeReports() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 1; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "diagnostic"),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(original)
        );
        GpuRuntimeIrOptimizationPassReport noOpReport = GpuRuntimeIrOptimizationPassReport.skipped(
                "javatogpu.ir-optimizer.noop:1",
                "irgpu:sha256:original",
                "no-op proposal provider keeps original IR selected"
        ).withStage(GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY)
                .withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "ir-optimizer",
                        "not-mutating",
                        Map.of("provider", "noop")
                ));
        GpuRuntimeIrOptimizationPassReport canonicalizationReport = new GpuRuntimeIrOptimizationPassReport(
                GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY,
                "javatogpu.ir-optimizer.text-canonicalization:1",
                GpuRuntimeIrOptimizationOutcome.SKIPPED,
                "irgpu:sha256:original",
                "irgpu:sha256:canonical",
                "proposal-only",
                "",
                GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "ir-optimizer.text-canonicalization",
                        "semantics-neutral-text-normalization",
                        Map.ofEntries(
                                Map.entry("changedMethodBodies", "1"),
                                Map.entry("mutationRequired", "false"),
                                Map.entry("normalizations", "crlf-to-lf,trailing-whitespace"),
                                Map.entry("optimizedArtifactCandidate.status", "candidate-ready"),
                                Map.entry("optimizedArtifactCandidate.candidateBuilt", "true"),
                                Map.entry("optimizedArtifactCandidate.optimizedValidationPassed", "true"),
                                Map.entry("optimizedArtifactCandidate.proofPresent", "true"),
                                Map.entry("optimizedArtifactCandidate.rollbackRequired", "true"),
                                Map.entry("optimizedArtifactCandidate.mutationAllowed", "false"),
                                Map.entry("optimizedArtifactCandidate.selectionReady", "false"),
                                Map.entry("optimizedArtifactCandidate.selectionApplied", "false"),
                                Map.entry("optimizedArtifactCandidate.selectedIrReplacement", "false"),
                                Map.entry("optimizedArtifactCandidate.firstBlocker", "none"),
                                Map.entry("optimizedArtifactCandidate.selectionFirstBlocker", "mutation-disabled")
                        )
                ),
                List.of("optimized artifact validated but mutation is disabled; original IR remains selected")
        );
        GpuRuntimeIrOptimizationPassReport previewReport = new GpuRuntimeIrOptimizationPassReport(
                GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY,
                "javatogpu.ir-optimizer.constant-folding-preview:1",
                GpuRuntimeIrOptimizationOutcome.SKIPPED,
                "irgpu:sha256:original",
                "irgpu:sha256:original",
                "not-mutating",
                "",
                GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "ir-optimizer.constant-folding-preview",
                        "preview-candidates-recorded",
                        Map.ofEntries(
                                Map.entry("candidate.count", "1"),
                                Map.entry("previewOnly", "true"),
                                Map.entry("rewrite.proposed", "false"),
                                Map.entry("skipped.nonPlainLiteral.count", "2"),
                                Map.entry("skipped.divideByZero.count", "1"),
                                Map.entry("skipped.nonEvenDivision.count", "1"),
                                Map.entry("skipped.unsupportedOperator.count", "1"),
                                Map.entry("skipped.nonLiteralOperand.count", "1"),
                                Map.entry("proof.runtimeEquivalenceRequiredBeforeRewrite", "true"),
                                Map.entry("proof.approvalRequiredBeforeRewrite", "true"),
                                Map.entry("safety.integerOverflowProven", "false"),
                                Map.entry("safety.floatingPointRoundingProven", "false")
                        )
                ),
                List.of("constant folding preview recorded evidence; no rewrite was proposed")
        );
        GpuRuntimeIrOptimizationPassReport unrelatedReport = GpuRuntimeIrOptimizationPassReport.applied(
                "optimizer:other",
                "irgpu:sha256:original",
                "irgpu:sha256:other",
                "proof:other",
                List.of("unrelated optimizer evidence")
        );
        GpuRuntimeIrOptimizationPassReport safeLocalCsePreviewReport = new GpuRuntimeIrOptimizationPassReport(
                GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY,
                "javatogpu.ir-optimizer.safe-local-cse-preview:1",
                GpuRuntimeIrOptimizationOutcome.SKIPPED,
                "irgpu:sha256:original",
                "irgpu:sha256:original",
                "not-mutating",
                "",
                GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "ir-optimizer.safe-local-cse-preview",
                        "preview-candidates-recorded",
                        Map.ofEntries(
                                Map.entry("expression.count", "5"),
                                Map.entry("candidateExpression.count", "3"),
                                Map.entry("duplicateExpression.count", "2"),
                                Map.entry("equivalenceClass.count", "1"),
                                Map.entry("blocked.unsupportedOperator.count", "1"),
                                Map.entry("blocked.impureOperand.count", "2"),
                                Map.entry("blocked.controlFlowBoundary.count", "3"),
                                Map.entry("previewOnly", "true"),
                                Map.entry("rewrite.proposed", "false"),
                                Map.entry("proof.runtimeEquivalenceRequiredBeforeRewrite", "true"),
                                Map.entry("proof.approvalRequiredBeforeRewrite", "true"),
                                Map.entry("safety.dominanceProven", "false"),
                                Map.entry("safety.sideEffectFreedomProven", "false")
                        )
                ),
                List.of("safe local CSE preview recorded evidence; no rewrite was proposed")
        );
        GpuRuntimeIrOptimizationPassReport typedDeadCodePreviewReport = new GpuRuntimeIrOptimizationPassReport(
                GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY,
                "javatogpu.ir-optimizer.typed-dead-code-preview:1",
                GpuRuntimeIrOptimizationOutcome.SKIPPED,
                "irgpu:sha256:original",
                "irgpu:sha256:original",
                "not-mutating",
                "",
                GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "ir-optimizer.typed-dead-code-preview",
                        "preview-candidates-recorded",
                        Map.ofEntries(
                                Map.entry("node.count", "8"),
                                Map.entry("reachableNode.count", "5"),
                                Map.entry("unreachableNode.count", "3"),
                                Map.entry("blocked.missingRoot.count", "1"),
                                Map.entry("blocked.missingChildReference.count", "2"),
                                Map.entry("blocked.sideEffectingUnreachableNode.count", "1"),
                                Map.entry("previewOnly", "true"),
                                Map.entry("rewrite.proposed", "false"),
                                Map.entry("proof.runtimeEquivalenceRequiredBeforeRewrite", "true"),
                                Map.entry("proof.approvalRequiredBeforeRewrite", "true"),
                                Map.entry("safety.sideEffectFreedomProven", "false")
                        )
                ),
                List.of("typed dead-code preview recorded evidence; no rewrite was proposed")
        );
        GpuRuntimeIrOptimizationReport optimizationReport = new GpuRuntimeIrOptimizationReport(
                Optional.of(original),
                List.of(noOpReport, canonicalizationReport, previewReport, safeLocalCsePreviewReport,
                        typedDeadCodePreviewReport, unrelatedReport),
                GpuOptimizationStrategyDecision.none(request)
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                request,
                request,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(request, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(request),
                optimizationReport
        );

        GpuRuntimeCompileArtifactDump dump = GpuRuntimeCompileArtifactDumper.dump(snapshot);

        assertTrue(dump.hasArtifact(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT));
        String evidence = dump.artifact(GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT);
        assertTrue(evidence.contains("status=recorded"));
        assertTrue(evidence.contains("source=runtime-ir-optimizer"));
        assertTrue(evidence.contains("providerPrefix=javatogpu.ir-optimizer"));
        assertTrue(evidence.contains("pass.count=5"));
        assertTrue(evidence.contains("skipped.count=5"));
        assertTrue(evidence.contains("applied.count=0"));
        assertTrue(evidence.contains("proposalOnly.count=1"));
        assertTrue(evidence.contains("selectedOptimized.count=0"));
        assertTrue(evidence.contains("approvalTemplate.pending.count=1"));
        assertTrue(evidence.contains("approvalTemplate.notApplicable.count=4"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.status=candidate-ready"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.count=1"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.ready.count=1"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.blocked.count=0"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.selectionReady.count=0"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.selectionApplied.count=0"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.selectedIrReplacement.count=0"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.mutationAllowed.count=0"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.firstBlocker=none"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.selectionFirstBlocker=mutation-disabled"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.selectionApplied=false"));
        assertTrue(evidence.contains("optimizedArtifactCandidate.selectedIrReplacement=false"));
        assertTrue(evidence.contains("constantFoldingPreview.pass.count=1"));
        assertTrue(evidence.contains("constantFoldingPreview.candidate.count=1"));
        assertTrue(evidence.contains("constantFoldingPreview.skipped.nonPlainLiteral.count=2"));
        assertTrue(evidence.contains("constantFoldingPreview.skipped.divideByZero.count=1"));
        assertTrue(evidence.contains("constantFoldingPreview.skipped.nonEvenDivision.count=1"));
        assertTrue(evidence.contains("constantFoldingPreview.skipped.unsupportedOperator.count=1"));
        assertTrue(evidence.contains("constantFoldingPreview.skipped.nonLiteralOperand.count=1"));
        assertTrue(evidence.contains("constantFoldingPreview.runtimeEquivalenceRequiredBeforeRewrite=true"));
        assertTrue(evidence.contains("constantFoldingPreview.approvalRequiredBeforeRewrite=true"));
        assertTrue(evidence.contains("constantFoldingPreview.integerOverflowProven=false"));
        assertTrue(evidence.contains("constantFoldingPreview.floatingPointRoundingProven=false"));
        assertTrue(evidence.contains("safeLocalCsePreview.pass.count=1"));
        assertTrue(evidence.contains("safeLocalCsePreview.expression.count=5"));
        assertTrue(evidence.contains("safeLocalCsePreview.candidateExpression.count=3"));
        assertTrue(evidence.contains("safeLocalCsePreview.duplicateExpression.count=2"));
        assertTrue(evidence.contains("safeLocalCsePreview.equivalenceClass.count=1"));
        assertTrue(evidence.contains("safeLocalCsePreview.blocked.unsupportedOperator.count=1"));
        assertTrue(evidence.contains("safeLocalCsePreview.blocked.impureOperand.count=2"));
        assertTrue(evidence.contains("safeLocalCsePreview.blocked.controlFlowBoundary.count=3"));
        assertTrue(evidence.contains("safeLocalCsePreview.runtimeEquivalenceRequiredBeforeRewrite=true"));
        assertTrue(evidence.contains("safeLocalCsePreview.approvalRequiredBeforeRewrite=true"));
        assertTrue(evidence.contains("safeLocalCsePreview.dominanceProven=false"));
        assertTrue(evidence.contains("safeLocalCsePreview.sideEffectFreedomProven=false"));
        assertTrue(evidence.contains("typedDeadCodePreview.pass.count=1"));
        assertTrue(evidence.contains("typedDeadCodePreview.node.count=8"));
        assertTrue(evidence.contains("typedDeadCodePreview.reachableNode.count=5"));
        assertTrue(evidence.contains("typedDeadCodePreview.unreachableNode.count=3"));
        assertTrue(evidence.contains("typedDeadCodePreview.blocked.missingRoot.count=1"));
        assertTrue(evidence.contains("typedDeadCodePreview.blocked.missingChildReference.count=2"));
        assertTrue(evidence.contains("typedDeadCodePreview.blocked.sideEffectingUnreachableNode.count=1"));
        assertTrue(evidence.contains("typedDeadCodePreview.runtimeEquivalenceRequiredBeforeRewrite=true"));
        assertTrue(evidence.contains("typedDeadCodePreview.approvalRequiredBeforeRewrite=true"));
        assertTrue(evidence.contains("typedDeadCodePreview.sideEffectFreedomProven=false"));
        assertTrue(evidence.contains("previewReadiness.status=blocked-by-proof"));
        assertTrue(evidence.contains("previewReadiness.family.count=3"));
        assertTrue(evidence.contains("previewReadiness.candidateFamily.count=3"));
        assertTrue(evidence.contains("previewReadiness.blockedFamily.count=3"));
        assertTrue(evidence.contains("previewReadiness.familySummary=constant-folding=blocked-by-proof, safe-local-cse=blocked-by-proof, typed-dead-code=blocked-by-proof"));
        assertTrue(evidence.contains("runtimeEquivalenceReview.status=blocked"));
        assertTrue(evidence.contains("runtimeEquivalenceReview.eligible=false"));
        assertTrue(evidence.contains("runtimeEquivalenceReview.required=true"));
        assertTrue(evidence.contains("runtimeEquivalenceReview.firstBlocker=preview-readiness-blocked-by-proof"));
        assertTrue(evidence.contains("runtimeEquivalenceReview.familySummary=constant-folding=blocked-by-proof, safe-local-cse=blocked-by-proof, typed-dead-code=blocked-by-proof"));
        assertTrue(evidence.contains("runtimeEquivalenceReview.productionMutation=disabled"));
        assertTrue(evidence.contains("runtimeEquivalenceReview.selectedIrReplacement=disabled"));
        assertTrue(evidence.contains("runtimeEquivalenceReview.manualReviewOnly=true"));
        assertTrue(evidence.contains("reviewPackage.status=pending-manual-review"));
        assertTrue(evidence.contains("reviewPackage.required=true"));
        assertTrue(evidence.contains("reviewPackage.complete=false"));
        assertTrue(evidence.contains("reviewPackage.firstBlocker=preview-readiness-blocked-by-proof"));
        assertTrue(evidence.contains("reviewPackage.proposalPass.count=1"));
        assertTrue(evidence.contains("reviewPackage.pendingApproval.count=1"));
        assertTrue(evidence.contains("reviewPackage.runtimeEquivalence.status=blocked"));
        assertTrue(evidence.contains("reviewPackage.originalIrRequired=true"));
        assertTrue(evidence.contains("reviewPackage.optimizedIrRequired=true"));
        assertTrue(evidence.contains("reviewPackage.proofSummaryRequired=true"));
        assertTrue(evidence.contains("reviewPackage.manualReviewOnly=true"));
        assertTrue(evidence.contains("reviewPackage.productionMutation=disabled"));
        assertTrue(evidence.contains("reviewPackage.selectedIrReplacement=disabled"));
        assertTrue(evidence.contains("pass.0.passVersion=javatogpu.ir-optimizer.noop:1"));
        assertTrue(evidence.contains("pass.1.passVersion=javatogpu.ir-optimizer.text-canonicalization:1"));
        assertTrue(evidence.contains("pass.2.passVersion=javatogpu.ir-optimizer.constant-folding-preview:1"));
        assertTrue(evidence.contains("pass.3.passVersion=javatogpu.ir-optimizer.safe-local-cse-preview:1"));
        assertTrue(evidence.contains("pass.4.passVersion=javatogpu.ir-optimizer.typed-dead-code-preview:1"));
        assertTrue(evidence.contains("pass.1.proofStatus=proposal-only"));
        assertTrue(evidence.contains("pass.1.transformedIrIdentity=irgpu:sha256:canonical"));
        assertTrue(evidence.contains("pass.1.proofArtifact.source=ir-optimizer.text-canonicalization"));
        assertTrue(evidence.contains("pass.1.proofArtifact.field.changedMethodBodies=1"));
        assertTrue(evidence.contains("pass.1.proofArtifact.field.mutationRequired=false"));
        assertTrue(evidence.contains("pass.1.proofArtifact.field.optimizedArtifactCandidate.status=candidate-ready"));
        assertTrue(evidence.contains("pass.1.proofArtifact.field.optimizedArtifactCandidate.selectionApplied=false"));
        assertTrue(evidence.contains("pass.1.proofArtifact.field.optimizedArtifactCandidate.selectedIrReplacement=false"));
        assertTrue(evidence.contains("pass.0.approvalTemplate.status=not-applicable"));
        assertTrue(evidence.contains("pass.0.approvalTemplate.firstBlocker=proposal-decision-not-proposed"));
        assertTrue(evidence.contains("pass.1.approvalTemplate.status=pending"));
        assertTrue(evidence.contains("pass.1.approvalTemplate.applicable=true"));
        assertTrue(evidence.contains("pass.1.approvalTemplate.resourceDirectory=META-INF/javatogpu/ir-optimization-approvals/"));
        assertTrue(evidence.contains("pass.2.approvalTemplate.status=not-applicable"));
        assertTrue(evidence.contains("pass.2.approvalTemplate.firstBlocker=proposal-decision-not-proposed"));
        assertTrue(evidence.contains("pass.2.proofArtifact.field.previewOnly=true"));
        assertTrue(evidence.contains("pass.3.approvalTemplate.status=not-applicable"));
        assertTrue(evidence.contains("pass.3.approvalTemplate.firstBlocker=proposal-decision-not-proposed"));
        assertTrue(evidence.contains("pass.3.proofArtifact.field.duplicateExpression.count=2"));
        assertTrue(evidence.contains("pass.4.approvalTemplate.status=not-applicable"));
        assertTrue(evidence.contains("pass.4.approvalTemplate.firstBlocker=proposal-decision-not-proposed"));
        assertTrue(evidence.contains("pass.4.proofArtifact.field.unreachableNode.count=3"));
        assertFalse(evidence.contains("optimizer:other"));
    }

    private static GpuKernelDescriptor descriptor() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
    }

    private static GpuExtensionExecutionReport execution(
            String extensionId,
            GpuExtensionPhase phase,
            GpuExtensionPermission permission,
            GpuExtensionExecutionOutcome outcome,
            boolean pipelineContinued
    ) {
        return new GpuExtensionExecutionReport(
                extensionId,
                "test-version",
                phase,
                permission,
                "test operation",
                outcome,
                GpuExtensionFailurePolicy.CONTINUE,
                pipelineContinued,
                "none",
                "test execution",
                List.of()
        );
    }

    private static GpuRuntimeDeviceSelection syntheticDeviceSelection(GpuExtensionExecutionReport execution) {
        return new GpuRuntimeDeviceSelection(
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(execution),
                true,
                false,
                "none",
                List.of("synthetic device-selection execution for participation artifact test")
        );
    }

    private static GpuRuntimeDeviceSelection deviceSelection() {
        GpuRuntimeDeviceProfile profile = GpuRuntimeDeviceProfile.openCl(
                "OpenCL",
                "opencl-1",
                "Mock GPU",
                "Mock Vendor",
                "Mock Driver",
                "OpenCL 3.0 Mock",
                GpuDeviceClassTarget.DGPU,
                48L,
                8L * 1024L * 1024L * 1024L,
                65_536L,
                512L,
                1L,
                false,
                true,
                true,
                false
        );
        return GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns().select(
                GpuRuntimeDevicePolicyContext.forBackendDiscovery(
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                        List.of(profile)
                )
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

    private static String reconstructedDescriptorSource() {
        return """
                __kernel void jtg_kernel(__global int* output) {
                    output[0] = 1;
                    return;
                }
                """;
    }

    private static List<IrGpuEntryParameter> entryParameters() {
        return List.of(new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of()));
    }

    private static IrGpuArtifact artifact(String body) {
        return artifact(body, IrGpuRegenerationMetadata.transitionalIrText());
    }

    private static IrGpuArtifact artifact(String body, IrGpuRegenerationMetadata regenerationMetadata) {
        return artifact(body, regenerationMetadata, List.of());
    }

    private static IrGpuArtifact artifact(
            String body,
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
                                body,
                                List.of(),
                                location()
                        ))
                ),
                entryParameters,
                IrGpuLaunchMetadata.defaultOneDimensional(),
                IrGpuValidationMetadata.frontendSubset(),
                IrGpuFeatureMetadata.none(),
                regenerationMetadata,
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuSourceLocation location() {
        return new IrGpuSourceLocation("java-source", "sample.Demo", "kernel", 4, 17, 7, 5);
    }

    private static GpuOptimizationStrategyDecision productionBackedStrategy() {
        return new GpuOptimizationStrategyDecision(
                "strategy:production-fixture",
                "test-vendor",
                "vendor-tuned",
                false,
                true,
                "test fixture carries production evidence",
                new GpuOptimizationVendorBaseline(
                        "test-vendor",
                        "production-fixture",
                        true,
                        true,
                        "test-fixture",
                        List.of("test fixture is promotion-eligible")
                ),
                List.of("production fixture is evidence-backed")
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
                "production fixture enables runtime IR mutation"
        );
    }

    private static GpuProductionPromotionDecision reviewReadyDecision() {
        return new GpuProductionPromotionDecision(
                GpuProductionPromotionDecision.REVIEW_READY,
                "blocked",
                true,
                false,
                false,
                "production-source-switching-disabled",
                "none",
                "review-ready fixture remains fail-closed"
        );
    }
}
