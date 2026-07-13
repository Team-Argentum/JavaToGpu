package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeIrSelectionTest {

    @Test
    void selectsOptimizedArtifactWhenNoFallbackOrRollbackExists() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuRuntimeCompileArtifactSnapshot snapshot = snapshot(original, optimized, GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized)));

        GpuRuntimeIrSelection selection = GpuRuntimeIrSelection.from(snapshot);

        assertEquals(selection, snapshot.runtimeIrSelection());
        assertEquals("optimized", selection.selectedStage());
        assertTrue(selection.selectedArtifact().isPresent());
        assertTrue(selection.transformed());
        assertFalse(selection.optimizedRejected());
        assertEquals(GpuRuntimeCompileProvenance.NO_FALLBACK, selection.fallbackDecision());
        assertTrue(selection.diagnostic().contains("optimized IrGpu is selected"));
    }

    @Test
    void materializedCandidateCanRemainReviewOnlyWhenReportSelectsOriginal() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact candidate = artifact("body\n  return optimized candidate\n");
        GpuRuntimeIrOptimizationPassReport passReport = new GpuRuntimeIrOptimizationPassReport(
                GpuRuntimeIrOptimizationStage.CANDIDATE_DISCOVERY,
                "optimizer:proposal-only",
                GpuRuntimeIrOptimizationOutcome.SKIPPED,
                IrGpuArtifactIdentity.stableIdentity(original),
                IrGpuArtifactIdentity.stableIdentity(candidate),
                "proposal-only",
                "",
                GpuRuntimeIrOptimizationProofArtifact.fromFields("test", "candidate-ready", java.util.Map.of()),
                List.of("candidate materialized for review only")
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = snapshot(
                original,
                candidate,
                new GpuRuntimeIrOptimizationReport(
                        Optional.of(original),
                        Optional.of(candidate),
                        List.of(passReport)
                )
        );

        GpuRuntimeIrSelection selection = GpuRuntimeIrSelection.from(snapshot);

        assertEquals("original", selection.selectedStage());
        assertEquals(IrGpuArtifactIdentity.stableIdentity(original), selection.selectedIdentity());
        assertEquals(IrGpuArtifactIdentity.stableIdentity(candidate), selection.optimizedIdentity());
        assertTrue(selection.transformed());
        assertFalse(selection.optimizedRejected());
        assertTrue(selection.diagnostic().contains("candidate is materialized for review"));
    }

    @Test
    void selectsOriginalArtifactWhenRuntimeEquivalenceFailed() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuRuntimeCompileRequest originalRequest = request(original);
        GpuRuntimeCompileRequest optimizedRequest = request(optimized);
        GpuBackendModuleArtifact backendArtifact = backendArtifact();
        GpuRuntimeIrOptimizationReport report = GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized));
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                originalRequest,
                optimizedRequest,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(optimizedRequest, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(optimizedRequest),
                report,
                GpuRuntimeEquivalenceEvidence.failed(optimizedRequest, 1, 1, List.of("output differs"))
        );

        GpuRuntimeIrSelection selection = GpuRuntimeIrSelection.from(snapshot);

        assertEquals(selection, snapshot.runtimeIrSelection());
        assertEquals("original", selection.selectedStage());
        assertTrue(selection.optimizedRejected());
        assertEquals("runtime-equivalence-failed", selection.fallbackDecision());
        assertTrue(selection.diagnostic().contains("optimized IrGpu was rejected"));
    }

    @Test
    void selectsOriginalArtifactWhenOptimizerRequiresRollback() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuRuntimeIrOptimizationReport report = new GpuRuntimeIrOptimizationReport(
                Optional.of(optimized),
                List.of(GpuRuntimeIrOptimizationPassReport.rolledBack(
                        "optimizer:rollback",
                        "irgpu:sha256:optimized",
                        "irgpu:sha256:unsafe",
                        "proof:failed",
                        "unsafe proof",
                        List.of("rolled back unsafe transform")
                )),
                GpuOptimizationStrategyDecision.none(request(optimized))
        );
        GpuRuntimeCompileArtifactSnapshot snapshot = snapshot(original, optimized, report);

        GpuRuntimeIrSelection selection = GpuRuntimeIrSelection.from(snapshot);

        assertEquals(selection, snapshot.runtimeIrSelection());
        assertEquals("original", selection.selectedStage());
        assertTrue(selection.optimizedRejected());
        assertEquals("optimizer-rollback", selection.fallbackDecision());
    }

    @Test
    void missingSnapshotProducesMissingSelection() {
        GpuRuntimeIrSelection selection = GpuRuntimeIrSelection.from(null);

        assertEquals("missing", selection.selectedStage());
        assertTrue(selection.selectedArtifact().isEmpty());
        assertTrue(selection.diagnostic().contains("no IrGpu artifact"));
    }

    @Test
    void withCompileProvenanceRefreshesProductionGateAndStoredSelection() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuRuntimeCompileArtifactSnapshot snapshot = snapshot(
                original,
                optimized,
                GpuRuntimeIrOptimizationReport.empty(Optional.of(optimized))
        );
        GpuRuntimeCompileProvenance vendorTunedProvenance = GpuRuntimeCompileProvenance.from(
                new GpuRuntimeCompileRequest(
                        descriptor(),
                        GpuRuntimeCompileOptions.openClIrGpuSource(List.of(), "vendor-tuned"),
                        GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                        Optional.of(optimized)
                )
        );

        GpuRuntimeCompileArtifactSnapshot refreshed = snapshot.withCompileProvenance(vendorTunedProvenance);

        assertEquals("vendor-tuned", refreshed.compileProvenance().optimizationProfile());
        assertTrue(refreshed.productionOptimizerGate().productionProfileRequested());
        assertEquals("blocked", refreshed.productionOptimizerGate().status());
        assertEquals("blocked", refreshed.runtimeIrSelection().productionIrGate().status());
        assertEquals("diagnostic-only", refreshed.runtimeIrSelection().productionIrGate().decisionMode());
        assertTrue(refreshed.runtimeIrSelection().productionIrGate().diagnostic().contains("vendor-tuned"));
        assertEquals("original", refreshed.runtimeIrSelection().selectedStage());
        assertTrue(refreshed.runtimeIrSelection().optimizedRejected());
        assertEquals("production-ir-gate-blocked", refreshed.runtimeIrSelection().fallbackDecision());
        assertTrue(refreshed.runtimeIrSelection().diagnostic().contains("production IR acceptance gate"));
    }

    @Test
    void productionEnabledDecisionSelectsOptimizedArtifactWhenAllGatesPass() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuRuntimeCompileRequest originalRequest = request(original);
        GpuRuntimeCompileRequest optimizedRequest = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of(), "vendor-tuned")
                        .withProductionPromotionDecision(productionEnabledDecision())
                        .withProductionPromotionOperatorAccepted(true),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeIrOptimizationReport report = new GpuRuntimeIrOptimizationReport(
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
        GpuBackendModuleArtifact backendArtifact = backendArtifact();
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                originalRequest,
                optimizedRequest,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(optimizedRequest, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(optimizedRequest),
                report,
                GpuRuntimeEquivalenceEvidence.passed(optimizedRequest, 2, 2, List.of("production fixture equivalent"))
        );

        GpuRuntimeIrSelection selection = GpuRuntimeIrSelection.from(snapshot);

        assertEquals(selection, snapshot.runtimeIrSelection());
        assertEquals("accepted", snapshot.productionOptimizerGate().status());
        assertEquals("production-enabled", selection.productionIrGate().status());
        assertEquals(GpuProductionPromotionDecision.PRODUCTION_ENABLED, selection.productionIrGate().decisionMode());
        assertEquals("optimized", selection.selectedStage());
        assertTrue(selection.transformed());
        assertFalse(selection.optimizedRejected());
        assertEquals(GpuRuntimeCompileProvenance.NO_FALLBACK, selection.fallbackDecision());
    }

    @Test
    void productionEnabledDecisionStillSelectsOriginalWhenAcceptedProofArtifactIsMissing() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuRuntimeCompileRequest originalRequest = request(original);
        GpuRuntimeCompileRequest optimizedRequest = new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.openClProductionIrGpuSource(List.of(), "vendor-tuned")
                        .withProductionPromotionDecision(productionEnabledDecision())
                        .withProductionPromotionOperatorAccepted(true),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(optimized)
        );
        GpuRuntimeIrOptimizationReport report = new GpuRuntimeIrOptimizationReport(
                Optional.of(optimized),
                List.of(GpuRuntimeIrOptimizationPassReport.applied(
                        "optimizer:production-safe",
                        "irgpu:sha256:original",
                        "irgpu:sha256:optimized",
                        "proof:production-fixture",
                        List.of("production fixture applied safe transform without accepted proof artifact")
                )),
                productionBackedStrategy()
        );
        GpuBackendModuleArtifact backendArtifact = backendArtifact();
        GpuRuntimeCompileArtifactSnapshot snapshot = GpuRuntimeCompileArtifactSnapshot.from(
                originalRequest,
                optimizedRequest,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(optimizedRequest, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(optimizedRequest),
                report,
                GpuRuntimeEquivalenceEvidence.passed(optimizedRequest, 2, 2, List.of("production fixture equivalent"))
        );

        GpuRuntimeIrSelection selection = GpuRuntimeIrSelection.from(snapshot);

        assertEquals("blocked", snapshot.productionOptimizerGate().status());
        assertTrue(snapshot.productionOptimizerGate().diagnostics().contains(
                "accepted optimizer proof artifact is required before production promotion"
        ));
        assertEquals("original", selection.selectedStage());
        assertTrue(selection.optimizedRejected());
        assertEquals("production-ir-gate-blocked", selection.fallbackDecision());
    }

    private static GpuRuntimeCompileArtifactSnapshot snapshot(
            IrGpuArtifact original,
            IrGpuArtifact optimized,
            GpuRuntimeIrOptimizationReport report
    ) {
        GpuRuntimeCompileRequest originalRequest = request(original);
        GpuRuntimeCompileRequest optimizedRequest = request(optimized);
        GpuBackendModuleArtifact backendArtifact = backendArtifact();
        return GpuRuntimeCompileArtifactSnapshot.from(
                originalRequest,
                optimizedRequest,
                backendArtifact,
                GpuRuntimeCompileInvalidationStamp.from(optimizedRequest, backendArtifact, "optimizer:test-v1"),
                GpuRuntimeCompileProvenance.from(optimizedRequest),
                report
        );
    }

    private static GpuRuntimeCompileRequest request(IrGpuArtifact artifact) {
        return new GpuRuntimeCompileRequest(
                descriptor(),
                GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                Optional.of(artifact)
        );
    }

    private static GpuBackendModuleArtifact backendArtifact() {
        return GpuBackendModuleArtifact.openClSource(
                "__kernel void kernel(__global int* out) { out[0] = 2; }",
                "runtime/lowered/kernel.cl",
                "test-lowerer-v1"
        );
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
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry("kernel", "jtg_kernel", body, List.of()))
                ),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
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
}
