package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeCompileArtifactDumperTest {

    @Test
    void dumpsOriginalOptimizedAndBackendArtifacts() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
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
                List.of(location()),
                "build ok",
                List.of("equivalence:skipped")
        );

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
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("optimizerProductionGateStatus=not-requested"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("productionMutationEnabled=false"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("blocker.0=backend-source-promotion-not-review-ready"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("blocker.1=production-optimizer-gate-not-accepted"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("blocker.2=production-mutation-disabled"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.applied.count=1"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.rolledBack.count=0"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("fallbackDecision=none"));
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

        assertFalse(dump.hasArtifact("original.irgpu.properties"));
        assertFalse(dump.hasArtifact("backend-source-map.properties"));
        assertTrue(dump.sourceLocations().isEmpty());
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
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("OpenCL source emitter skeleton is present"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("status=blocked"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("reviewReady=false"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("reconstructed=false"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("sourceParityChecked=false"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("runtimeEquivalencePassed=false"));
    }

    @Test
    void dumpRecordsBackendSourceSwitchingReviewDecision() {
        IrGpuArtifact optimized = artifact(
                "body\n  return ready\n",
                IrGpuRegenerationMetadata.backendNeutralReady()
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 2; }",
                "javatogpu/sample/Demo/kernel.cl#irgpu-reconstructed",
                "test-lowerer-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.openClIrGpuSource(List.of(), "source-reconstruction-review"),
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
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("optimizationProfile=source-reconstruction-review"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionProfileRequested=false"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceSelection=irgpu"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("irGpuSourceRequested=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionSourceSwitching=disabled"));
    }

    @Test
    void dumpRecordsBackendSourceSwitchingProductionDecision() {
        IrGpuArtifact optimized = artifact(
                "body\n  return ready\n",
                IrGpuRegenerationMetadata.backendNeutralReady()
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 2; }",
                "javatogpu/sample/Demo/kernel.cl#irgpu-reconstructed",
                "test-lowerer-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
        GpuRuntimeCompileRequest request = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of(), "vendor-tuned"),
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

        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("status=production-switch-enabled"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("decision=compile-irgpu-source-production"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("optimizationProfile=vendor-tuned"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionProfileRequested=true"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("sourceSelection=irgpu"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionSourceSwitching=enabled"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionSourceSwitchingEnabled=true"));
    }

    @Test
    void dumpRecordsBackendSourceSwitchingBlockedProductionDecision() {
        IrGpuArtifact optimized = artifact(
                "body\n  return ready\n",
                IrGpuRegenerationMetadata.backendNeutralReady()
        );
        GpuBackendModuleArtifact backendArtifact = GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 2; }",
                "javatogpu/sample/Demo/kernel.cl#irgpu-reconstructed",
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
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionSourceSwitching=disabled"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains("productionSourceSwitchingEnabled=false"));
        assertTrue(dump.artifact("backend-source-switching-decision.properties").contains(
                "diagnostic.0=production-like profile requested IrGpu source but opencl.productionSourceSwitching is disabled"
        ));
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
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceAvailable=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("blocker.count=0"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("OpenCL source assembler emitted entry kernel jtg_kernel"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceParity.checked=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceParity.matched=false"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceParity.reconstructedLength="));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceParity.descriptorLength="));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("status=blocked"));
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
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceParity.checked=true"));
        assertTrue(dump.artifact("backend-source-reconstruction.properties").contains("sourceParity.matched=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("status=review-ready"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("reviewReady=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("reconstructed=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("sourceAvailable=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("sourceParityChecked=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("sourceParityMatched=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("runtimeEquivalencePassed=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("fallbackClean=true"));
        assertTrue(dump.artifact("backend-source-promotion-gate.properties").contains("diagnostic.0=backend source reconstruction is ready for promotion review"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("status=review-ready"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("sourceReconstructed=true"));
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
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("status=blocked"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("optimizerProductionGateStatus=blocked"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("productionProfileRequested=true"));
        assertTrue(dump.artifact("i3-readiness-summary.properties").contains("productionMutationEnabled=false"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("pass.count=0"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("fallbackDecision=none"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("strategyName=strategy:opencl-nvidia-advisory"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("selectedProfile=vendor-tuned"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("productionGateStatus=blocked"));
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("productionProfileRequested=true"));
        assertTrue(dump.artifact("optimizer-report.txt").contains("strategy:opencl-nvidia-advisory"));
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
        assertTrue(dump.artifact("runtime-optimizer-drift.properties").contains("fallbackDecision=optimizer-rollback"));
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

    private static GpuKernelDescriptor descriptor() {
        return new GpuKernelDescriptor(
                "kernel",
                "javatogpu/sample/Demo/kernel.cl",
                "__kernel void kernel(__global int* output) { output[0] = 1; }",
                List.of(new GpuKernelParameterDescriptor("output", "int[]", GpuKernelParameterAccess.READ_WRITE))
        );
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
}
