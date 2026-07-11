package net.sixik.ga_utils.javatogpu.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpuBackendSourcePromotionWorkloadGateFormatterTest {

    @TempDir
    Path tempDir;

    @Test
    void mergesKernelEntriesAndAggregatesBlockerFamilies() throws IOException {
        Path gateFile = tempDir.resolve("backend-source-promotion-workload-gate.properties");

        writeGate(gateFile, GpuBackendSourcePromotionWorkloadGateFormatter.merge(
                gateFile,
                "kernel-a.cl",
                blockedGateProperties(
                        "backend source must be reconstructed from IrGpu before promotion review",
                        "runtime equivalence must execute and pass before backend source promotion"
                )
        ));

        Properties gate = loadProperties(GpuBackendSourcePromotionWorkloadGateFormatter.merge(
                gateFile,
                "kernel-b.cl",
                blockedGateProperties(
                        "reconstructed source must match descriptor source before promotion review"
                )
        ));

        assertEquals("blocked", gate.getProperty("status"));
        assertEquals("false", gate.getProperty("reviewReady"));
        assertEquals("runtime-snapshot", gate.getProperty("realWorkloadEvidence"));
        assertEquals("real-workload", gate.getProperty("scope"));
        assertEquals("false", gate.getProperty("productionSourceSwitching"));
        assertEquals("2", gate.getProperty("sourceSwitching.count"));
        assertEquals("1", gate.getProperty("sourceSwitching.sourcePromotionFirstBlocker.count"));
        assertEquals(
                "source-switching-decision-not-recorded",
                gate.getProperty("sourceSwitching.sourcePromotionFirstBlocker.0.name")
        );
        assertEquals("2", gate.getProperty("sourceSwitching.sourcePromotionFirstBlocker.0.count"));
        assertEquals("1", gate.getProperty("sourceSwitching.sourcePromotionFirstBlockerFamily.count"));
        assertEquals("other", gate.getProperty("sourceSwitching.sourcePromotionFirstBlockerFamily.0.name"));
        assertEquals("2", gate.getProperty("sourceSwitching.sourcePromotionFirstBlockerFamily.0.count"));
        assertEquals("2", gate.getProperty("kernel.count"));
        assertEquals("kernel-a.cl", gate.getProperty("kernel.0.sourceKernelResource"));
        assertEquals("kernel-b.cl", gate.getProperty("kernel.1.sourceKernelResource"));
        assertEquals("false", gate.getProperty("kernel.0.ready"));
        assertEquals("false", gate.getProperty("kernel.0.i3Readiness.sourceReady"));
        assertEquals("not-recorded", gate.getProperty("kernel.0.sourceSwitching.decision"));
        assertEquals("false", gate.getProperty("kernel.0.sourceSwitching.productionSourceSwitchingEnabled"));
        assertEquals("diagnostic-only", gate.getProperty("kernel.0.sourceSwitching.productionPromotionDecisionMode"));
        assertEquals(
                "source-switching-decision-not-recorded",
                gate.getProperty("kernel.0.sourceSwitching.sourcePromotionFirstBlocker")
        );
        assertEquals("not-recorded", gate.getProperty("kernel.0.runtimeIrHandoff.status"));
        assertEquals("original", gate.getProperty("kernel.0.runtimeIrHandoff.selectedStage"));
        assertEquals("false", gate.getProperty("kernel.0.runtimeIrHandoff.optimizedDiffersFromOriginal"));
        assertEquals("none", gate.getProperty("kernel.0.runtimeIrHandoff.fallbackDecision"));
        assertEquals(
                "runtime IR handoff artifact was not recorded; original runtime source remains selected",
                gate.getProperty("kernel.0.runtimeIrHandoff.diagnostic.0")
        );
        assertEquals("not-recorded", gate.getProperty("kernel.0.runtimeProductionMutationSafety.status"));
        assertEquals("false", gate.getProperty("kernel.0.runtimeProductionMutationSafety.productionMutationEnabled"));
        assertEquals("not-recorded", gate.getProperty("kernel.0.runtimeProductionMutationSafety.productionGateStatus"));
        assertEquals("original", gate.getProperty("kernel.0.runtimeProductionMutationSafety.selectedStage"));
        assertEquals(
                "production mutation remains disabled because runtime production safety evidence was not recorded",
                gate.getProperty("kernel.0.runtimeProductionMutationSafety.diagnostic.0")
        );
        assertEquals("blocked", gate.getProperty("kernel.0.i3Readiness.status"));
        assertEquals("original", gate.getProperty("kernel.0.i3Readiness.selectedRuntimeIrStage"));
        assertEquals("blocked", gate.getProperty("kernel.0.i3Readiness.sourcePromotionStatus"));
        assertEquals("not-recorded", gate.getProperty("kernel.0.i3Readiness.optimizerProductionGateStatus"));
        assertEquals("false", gate.getProperty("kernel.0.i3Readiness.productionMutationEnabled"));
        assertEquals("runtime-i3-readiness-artifact-missing", gate.getProperty("kernel.0.i3Readiness.blocker.0"));
        assertEquals(
                "I3 readiness evidence was not recorded for this workload kernel; treating it as blocked",
                gate.getProperty("kernel.0.i3Readiness.diagnostic.0")
        );
        assertEquals("not-run", gate.getProperty("kernel.0.runtimeEquivalence.status"));
        assertEquals("false", gate.getProperty("kernel.0.runtimeEquivalence.executed"));
        assertEquals("1", gate.getProperty("kernel.0.runtimeEquivalence.diagnostic.count"));
        assertEquals(
                "runtime equivalence was not executed",
                gate.getProperty("kernel.0.runtimeEquivalence.diagnostic.0")
        );
        assertEquals("3", gate.getProperty("blockerFamily.count"));
        assertEquals("reconstruction", gate.getProperty("blockerFamily.0.name"));
        assertEquals("1", gate.getProperty("blockerFamily.0.count"));
        assertEquals("runtime-equivalence", gate.getProperty("blockerFamily.1.name"));
        assertEquals("1", gate.getProperty("blockerFamily.1.count"));
        assertEquals("source-parity", gate.getProperty("blockerFamily.2.name"));
        assertEquals("1", gate.getProperty("blockerFamily.2.count"));
        assertEquals("1", gate.getProperty("kernel.0.reconstruction.blocker.count"));
        assertEquals("irgpu-artifact-missing", gate.getProperty("kernel.0.reconstruction.blocker.0"));
        assertEquals("1", gate.getProperty("kernel.0.reconstruction.diagnostic.count"));
        assertEquals(
                "OpenCL reconstruction preview skipped because no IrGpu artifact was available",
                gate.getProperty("kernel.0.reconstruction.diagnostic.0")
        );
        assertEquals("1", gate.getProperty("kernel.1.reconstruction.blocker.count"));
        assertEquals("irgpu-artifact-missing", gate.getProperty("kernel.1.reconstruction.blocker.0"));
        assertEquals("reconstruction", gate.getProperty("kernel.0.blockerFamily.0.name"));
        assertEquals("runtime-equivalence", gate.getProperty("kernel.0.blockerFamily.1.name"));
        assertEquals("source-parity", gate.getProperty("kernel.1.blockerFamily.0.name"));
    }

    @Test
    void updatesExistingKernelResourceInsteadOfDuplicatingIt() throws IOException {
        Path gateFile = tempDir.resolve("backend-source-promotion-workload-gate.properties");

        writeGate(gateFile, GpuBackendSourcePromotionWorkloadGateFormatter.merge(
                gateFile,
                "kernel-a.cl",
                blockedGateProperties(
                        "runtime equivalence must execute and pass before backend source promotion"
                )
        ));

        Properties gate = loadProperties(GpuBackendSourcePromotionWorkloadGateFormatter.merge(
                gateFile,
                "kernel-a.cl",
                blockedGateProperties(
                        "fallback descriptor source must remain clean before backend source promotion"
                )
        ));

        assertEquals("1", gate.getProperty("kernel.count"));
        assertEquals("kernel-a.cl", gate.getProperty("kernel.0.sourceKernelResource"));
        assertEquals("1", gate.getProperty("blockerFamily.count"));
        assertEquals("fallback-clean", gate.getProperty("blockerFamily.0.name"));
        assertEquals("1", gate.getProperty("blockerFamily.0.count"));
        assertEquals("fallback-clean", gate.getProperty("kernel.0.blockerFamily.0.name"));
        assertEquals(
                "fallback descriptor source must remain clean before backend source promotion",
                gate.getProperty("kernel.0.diagnostic.0")
        );
    }

    @Test
    void mergesSourceSwitchingDecisionEvidencePerKernel() throws IOException {
        Path gateFile = tempDir.resolve("backend-source-promotion-workload-gate.properties");

        writeGate(gateFile, GpuBackendSourcePromotionWorkloadGateFormatter.merge(
                gateFile,
                "kernel-a.cl",
                blockedGateProperties(
                        "backend source must be reconstructed from IrGpu before promotion review"
                ),
                sourceSwitchingDecisionProperties(
                        "review-ready",
                        "compile-irgpu-source-review",
                        GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE,
                        "false",
                        "disabled",
                        "false",
                        "review-ready",
                        "false",
                        "none",
                        "IrGpu source was explicitly selected for review or smoke validation"
                ),
                runtimeIrHandoffProperties(
                        "optimized",
                        "true",
                        "false",
                        "none",
                        "false",
                        "optimized IrGpu is selected for backend lowering after runtime optimizer passes"
                ),
                runtimeProductionMutationSafetyProperties(
                        "disabled",
                        "false",
                        "not-requested",
                        "false",
                        "optimized",
                        "runtime IR participates in diagnostics, but production mutation is disabled because no production profile was requested"
                ),
                i3ReadinessSummaryProperties(
                        "review-ready",
                        "optimized",
                        "review-ready",
                        "true",
                        "not-requested",
                        "false",
                        "I3 source pipeline is review-ready, but production mutation remains disabled until production gates are accepted"
                ),
                runtimeOptimizerDriftProperties(
                        "2",
                        "2",
                        "0",
                        "0",
                        "none",
                        "optimized",
                        "false",
                        GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE,
                        "not-requested",
                        "false"
                )
        ));

        Properties gate = loadProperties(GpuBackendSourcePromotionWorkloadGateFormatter.merge(
                gateFile,
                "kernel-b.cl",
                blockedGateProperties(
                        "runtime equivalence must execute and pass before backend source promotion"
                ),
                sourceSwitchingDecisionProperties(
                        "blocked",
                        "reject-production-irgpu-source",
                        "vendor-tuned",
                        "true",
                        "disabled",
                        "false",
                        "diagnostic-only",
                        "false",
                        "runtime equivalence must execute and pass before backend source promotion",
                        "production-like profile requested IrGpu source but opencl.productionSourceSwitching is disabled"
                ),
                runtimeIrHandoffProperties(
                        "original",
                        "false",
                        "false",
                        "production-ir-gate-blocked",
                        "true",
                        "optimized IrGpu was rejected by the production IR acceptance gate; original IrGpu remains selected"
                ),
                runtimeProductionMutationSafetyProperties(
                        "disabled",
                        "false",
                        "blocked",
                        "true",
                        "original",
                        "runtime IR participates in diagnostics, but production mutation remains fail-closed until production optimizer gates pass"
                ),
                i3ReadinessSummaryProperties(
                        "blocked",
                        "original",
                        "blocked",
                        "false",
                        "blocked",
                        "false",
                        "I3 pipeline is active for diagnostics, but source promotion or production mutation is still blocked"
                ),
                runtimeOptimizerDriftProperties(
                        "3",
                        "1",
                        "0",
                        "1",
                        "production-ir-gate-blocked",
                        "original",
                        "true",
                        "vendor-tuned",
                        "blocked",
                        "true"
                )
        ));

        assertEquals("2", gate.getProperty("sourceSwitching.count"));
        assertEquals("1", gate.getProperty("sourceSwitching.sourcePromotionFirstBlocker.count"));
        assertEquals(
                "runtime equivalence must execute and pass before backend source promotion",
                gate.getProperty("sourceSwitching.sourcePromotionFirstBlocker.0.name")
        );
        assertEquals("1", gate.getProperty("sourceSwitching.sourcePromotionFirstBlocker.0.count"));
        assertEquals("1", gate.getProperty("sourceSwitching.sourcePromotionFirstBlockerFamily.count"));
        assertEquals("runtime-equivalence", gate.getProperty("sourceSwitching.sourcePromotionFirstBlockerFamily.0.name"));
        assertEquals("1", gate.getProperty("sourceSwitching.sourcePromotionFirstBlockerFamily.0.count"));
        assertEquals("compile-irgpu-source-review", gate.getProperty("kernel.0.sourceSwitching.decision"));
        assertEquals(
                GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE,
                gate.getProperty("kernel.0.sourceSwitching.optimizationProfile")
        );
        assertEquals("false", gate.getProperty("kernel.0.sourceSwitching.productionProfileRequested"));
        assertEquals("disabled", gate.getProperty("kernel.0.sourceSwitching.productionSourceSwitching"));
        assertEquals("review-ready", gate.getProperty("kernel.0.sourceSwitching.productionPromotionDecisionMode"));
        assertEquals("none", gate.getProperty("kernel.0.sourceSwitching.sourcePromotionFirstBlocker"));
        assertEquals("blocked", gate.getProperty("kernel.1.sourceSwitching.status"));
        assertEquals("reject-production-irgpu-source", gate.getProperty("kernel.1.sourceSwitching.decision"));
        assertEquals("vendor-tuned", gate.getProperty("kernel.1.sourceSwitching.optimizationProfile"));
        assertEquals("true", gate.getProperty("kernel.1.sourceSwitching.productionProfileRequested"));
        assertEquals("false", gate.getProperty("kernel.1.sourceSwitching.productionSourceSwitchingEnabled"));
        assertEquals("diagnostic-only", gate.getProperty("kernel.1.sourceSwitching.productionPromotionDecisionMode"));
        assertEquals(
                "runtime equivalence must execute and pass before backend source promotion",
                gate.getProperty("kernel.1.sourceSwitching.sourcePromotionFirstBlocker")
        );
        assertEquals(
                "production-like profile requested IrGpu source but opencl.productionSourceSwitching is disabled",
                gate.getProperty("kernel.1.sourceSwitching.diagnostic.0")
        );
        assertEquals("selected", gate.getProperty("kernel.0.runtimeIrHandoff.status"));
        assertEquals("optimized", gate.getProperty("kernel.0.runtimeIrHandoff.selectedStage"));
        assertEquals("true", gate.getProperty("kernel.0.runtimeIrHandoff.optimizedDiffersFromOriginal"));
        assertEquals("none", gate.getProperty("kernel.0.runtimeIrHandoff.fallbackDecision"));
        assertEquals("false", gate.getProperty("kernel.0.runtimeIrHandoff.optimizedIrRejected"));
        assertEquals(
                "optimized IrGpu is selected for backend lowering after runtime optimizer passes",
                gate.getProperty("kernel.0.runtimeIrHandoff.diagnostic.0")
        );
        assertEquals("original", gate.getProperty("kernel.1.runtimeIrHandoff.selectedStage"));
        assertEquals("false", gate.getProperty("kernel.1.runtimeIrHandoff.optimizationRequiresRollback"));
        assertEquals("production-ir-gate-blocked", gate.getProperty("kernel.1.runtimeIrHandoff.fallbackDecision"));
        assertEquals("true", gate.getProperty("kernel.1.runtimeIrHandoff.optimizedIrRejected"));
        assertEquals(
                "optimized IrGpu was rejected by the production IR acceptance gate; original IrGpu remains selected",
                gate.getProperty("kernel.1.runtimeIrHandoff.diagnostic.0")
        );
        assertEquals("disabled", gate.getProperty("kernel.0.runtimeProductionMutationSafety.status"));
        assertEquals("false", gate.getProperty("kernel.0.runtimeProductionMutationSafety.productionMutationEnabled"));
        assertEquals("not-requested", gate.getProperty("kernel.0.runtimeProductionMutationSafety.productionGateStatus"));
        assertEquals("false", gate.getProperty("kernel.0.runtimeProductionMutationSafety.productionProfileRequested"));
        assertEquals("optimized", gate.getProperty("kernel.0.runtimeProductionMutationSafety.selectedStage"));
        assertEquals(
                "runtime IR participates in diagnostics, but production mutation is disabled because no production profile was requested",
                gate.getProperty("kernel.0.runtimeProductionMutationSafety.diagnostic.0")
        );
        assertEquals("disabled", gate.getProperty("kernel.1.runtimeProductionMutationSafety.status"));
        assertEquals("false", gate.getProperty("kernel.1.runtimeProductionMutationSafety.productionMutationEnabled"));
        assertEquals("blocked", gate.getProperty("kernel.1.runtimeProductionMutationSafety.productionGateStatus"));
        assertEquals("true", gate.getProperty("kernel.1.runtimeProductionMutationSafety.productionProfileRequested"));
        assertEquals("original", gate.getProperty("kernel.1.runtimeProductionMutationSafety.selectedStage"));
        assertEquals(
                "runtime IR participates in diagnostics, but production mutation remains fail-closed until production optimizer gates pass",
                gate.getProperty("kernel.1.runtimeProductionMutationSafety.diagnostic.0")
        );
        assertEquals("review-ready", gate.getProperty("kernel.0.i3Readiness.status"));
        assertEquals("optimized", gate.getProperty("kernel.0.i3Readiness.selectedRuntimeIrStage"));
        assertEquals("false", gate.getProperty("kernel.0.ready"));
        assertEquals("true", gate.getProperty("kernel.0.i3Readiness.sourceReady"));
        assertEquals("review-ready", gate.getProperty("kernel.0.i3Readiness.sourcePromotionStatus"));
        assertEquals("not-requested", gate.getProperty("kernel.0.i3Readiness.optimizerProductionGateStatus"));
        assertEquals("false", gate.getProperty("kernel.0.i3Readiness.productionMutationEnabled"));
        assertEquals("production-mutation-disabled", gate.getProperty("kernel.0.i3Readiness.blocker.0"));
        assertEquals(
                "I3 source pipeline is review-ready, but production mutation remains disabled until production gates are accepted",
                gate.getProperty("kernel.0.i3Readiness.diagnostic.0")
        );
        assertEquals("blocked", gate.getProperty("kernel.1.i3Readiness.status"));
        assertEquals("original", gate.getProperty("kernel.1.i3Readiness.selectedRuntimeIrStage"));
        assertEquals("blocked", gate.getProperty("kernel.1.i3Readiness.sourcePromotionStatus"));
        assertEquals("blocked", gate.getProperty("kernel.1.i3Readiness.optimizerProductionGateStatus"));
        assertEquals("false", gate.getProperty("kernel.1.i3Readiness.productionMutationEnabled"));
        assertEquals("backend-source-promotion-not-review-ready", gate.getProperty("kernel.1.i3Readiness.blocker.0"));
        assertEquals(
                "I3 pipeline is active for diagnostics, but source promotion or production mutation is still blocked",
                gate.getProperty("kernel.1.i3Readiness.diagnostic.0")
        );
        assertEquals("recorded", gate.getProperty("kernel.0.runtimeOptimizerDrift.status"));
        assertEquals("2", gate.getProperty("kernel.0.runtimeOptimizerDrift.pass.count"));
        assertEquals("2", gate.getProperty("kernel.0.runtimeOptimizerDrift.pass.applied.count"));
        assertEquals("0", gate.getProperty("kernel.0.runtimeOptimizerDrift.pass.rolledBack.count"));
        assertEquals("2", gate.getProperty("kernel.0.runtimeOptimizerDrift.proofArtifact.count"));
        assertEquals("1", gate.getProperty("kernel.0.runtimeOptimizerDrift.proofArtifact.accepted.count"));
        assertEquals("1", gate.getProperty("kernel.0.runtimeOptimizerDrift.proofArtifact.blocking.count"));
        assertEquals("2", gate.getProperty("kernel.0.runtimeOptimizerDrift.optimizerFamily.count"));
        assertEquals("1", gate.getProperty("kernel.0.runtimeOptimizerDrift.optimizerFamily.promotionReady.count"));
        assertEquals("cse[passes=1, acceptedProof=1, blockingProof=0, rolledBack=0, failed=0, promotionReady=true], vector[passes=1, acceptedProof=0, blockingProof=1, rolledBack=0, failed=0, promotionReady=false]", gate.getProperty("kernel.0.runtimeOptimizerDrift.optimizerFamily.summary"));
        assertEquals("none", gate.getProperty("kernel.0.runtimeOptimizerDrift.fallbackDecision"));
        assertEquals("optimized", gate.getProperty("kernel.0.runtimeOptimizerDrift.selectedRuntimeIrStage"));
        assertEquals("false", gate.getProperty("kernel.0.runtimeOptimizerDrift.optimizedIrRejected"));
        assertEquals(
                GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE,
                gate.getProperty("kernel.0.runtimeOptimizerDrift.selectedProfile")
        );
        assertEquals("not-requested", gate.getProperty("kernel.0.runtimeOptimizerDrift.productionGateStatus"));
        assertEquals("recorded", gate.getProperty("kernel.1.runtimeOptimizerDrift.status"));
        assertEquals("3", gate.getProperty("kernel.1.runtimeOptimizerDrift.pass.count"));
        assertEquals("1", gate.getProperty("kernel.1.runtimeOptimizerDrift.pass.applied.count"));
        assertEquals("0", gate.getProperty("kernel.1.runtimeOptimizerDrift.pass.rolledBack.count"));
        assertEquals("2", gate.getProperty("kernel.1.runtimeOptimizerDrift.proofArtifact.count"));
        assertEquals("1", gate.getProperty("kernel.1.runtimeOptimizerDrift.proofArtifact.accepted.count"));
        assertEquals("1", gate.getProperty("kernel.1.runtimeOptimizerDrift.proofArtifact.blocking.count"));
        assertEquals("1", gate.getProperty("kernel.1.runtimeOptimizerDrift.optimizerFamily.count"));
        assertEquals("0", gate.getProperty("kernel.1.runtimeOptimizerDrift.optimizerFamily.promotionReady.count"));
        assertEquals("vector[passes=2, acceptedProof=1, blockingProof=1, rolledBack=1, failed=0, promotionReady=false]", gate.getProperty("kernel.1.runtimeOptimizerDrift.optimizerFamily.summary"));
        assertEquals("production-ir-gate-blocked", gate.getProperty("kernel.1.runtimeOptimizerDrift.fallbackDecision"));
        assertEquals("original", gate.getProperty("kernel.1.runtimeOptimizerDrift.selectedRuntimeIrStage"));
        assertEquals("true", gate.getProperty("kernel.1.runtimeOptimizerDrift.optimizedIrRejected"));
        assertEquals("vendor-tuned", gate.getProperty("kernel.1.runtimeOptimizerDrift.selectedProfile"));
        assertEquals("blocked", gate.getProperty("kernel.1.runtimeOptimizerDrift.productionGateStatus"));
    }

    @Test
    void marksWorkloadProductionEnabledWhenEveryKernelUsesAcceptedIrGpuSourceSwitching() throws IOException {
        Path gateFile = tempDir.resolve("backend-source-promotion-workload-gate.properties");

        Properties gate = loadProperties(GpuBackendSourcePromotionWorkloadGateFormatter.merge(
                gateFile,
                "kernel-production.cl",
                reviewReadyGateProperties(),
                sourceSwitchingDecisionProperties(
                        "production-switch-enabled",
                        "compile-irgpu-source-production",
                        "vendor-tuned",
                        "true",
                        "enabled",
                        "true",
                        GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                        "true",
                        "none",
                        "production source switching was explicitly enabled for a production-like profile"
                ),
                runtimeIrHandoffProperties(
                        "optimized",
                        "true",
                        "false",
                        "none",
                        "false",
                        "optimized IrGpu is selected for backend lowering after runtime optimizer passes"
                ),
                runtimeProductionMutationSafetyProperties(
                        "enabled",
                        "true",
                        "accepted",
                        "true",
                        "optimized",
                        "production mutation is enabled after runtime production gates accepted optimized IrGpu"
                ),
                i3ReadinessSummaryProperties(
                        "production-enabled",
                        "optimized",
                        "review-ready",
                        "true",
                        "accepted",
                        "true",
                        "I3 pipeline is production-enabled for this runtime compile snapshot"
                ),
                runtimeOptimizerDriftProperties(
                        "3",
                        "3",
                        "0",
                        "0",
                        "none",
                        "optimized",
                        "false",
                        "vendor-tuned",
                        "accepted",
                        "true"
                )
        ));

        assertEquals("production-enabled", gate.getProperty("status"));
        assertEquals("true", gate.getProperty("reviewReady"));
        assertEquals("enabled", gate.getProperty("productionSourceSwitching"));
        assertEquals("1", gate.getProperty("productionSourceSwitchingEnabled.count"));
        assertEquals("true", gate.getProperty("productionSourceSwitchingEnabled.all"));
        assertEquals("1", gate.getProperty("productionPromotionDecisionMode.productionEnabled.count"));
        assertEquals("true", gate.getProperty("productionPromotionDecisionMode.productionEnabled.all"));
        assertEquals("1", gate.getProperty("productionPromotionOperatorAccepted.count"));
        assertEquals("true", gate.getProperty("productionPromotionOperatorAccepted.all"));
        assertEquals("1", gate.getProperty("sourceSwitching.productionDecision.count"));
        assertEquals("true", gate.getProperty("sourceSwitching.productionDecision.all"));
        assertEquals("0", gate.getProperty("sourceSwitching.sourcePromotionFirstBlocker.count"));
        assertEquals("0", gate.getProperty("sourceSwitching.sourcePromotionFirstBlockerFamily.count"));
        assertEquals(
                "all workload kernels reached production IrGpu source switching with accepted promotion evidence",
                gate.getProperty("reason")
        );
        assertEquals("enabled", gate.getProperty("kernel.0.productionSourceSwitching"));
        assertEquals("production-switch-enabled", gate.getProperty("kernel.0.sourceSwitching.status"));
        assertEquals("compile-irgpu-source-production", gate.getProperty("kernel.0.sourceSwitching.decision"));
        assertEquals(GpuProductionPromotionDecision.PRODUCTION_ENABLED, gate.getProperty("kernel.0.sourceSwitching.productionPromotionDecisionMode"));
        assertEquals("true", gate.getProperty("kernel.0.sourceSwitching.productionPromotionOperatorAccepted"));
        assertEquals("none", gate.getProperty("kernel.0.sourceSwitching.sourcePromotionFirstBlocker"));
    }

    @Test
    void keepsWorkloadReviewReadyUntilOperatorAcceptsProductionPromotion() throws IOException {
        Path gateFile = tempDir.resolve("backend-source-promotion-workload-gate.properties");

        Properties gate = loadProperties(GpuBackendSourcePromotionWorkloadGateFormatter.merge(
                gateFile,
                "kernel-production.cl",
                reviewReadyGateProperties(),
                sourceSwitchingDecisionProperties(
                        "blocked",
                        "reject-production-irgpu-source",
                        "vendor-tuned",
                        "true",
                        "enabled",
                        "true",
                        GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                        "false",
                        "none",
                        "production-like profile requested IrGpu source but production promotion was not explicitly accepted by the operator"
                ),
                runtimeIrHandoffProperties(
                        "optimized",
                        "true",
                        "false",
                        "none",
                        "false",
                        "optimized IrGpu is selected for backend lowering after runtime optimizer passes"
                ),
                runtimeProductionMutationSafetyProperties(
                        "disabled",
                        "false",
                        "blocked",
                        "true",
                        "optimized",
                        "production mutation remains disabled until operator acceptance is explicit"
                ),
                i3ReadinessSummaryProperties(
                        "review-ready",
                        "optimized",
                        "review-ready",
                        "true",
                        "blocked",
                        "false",
                        "I3 source pipeline is review-ready, but operator acceptance is missing"
                ),
                runtimeOptimizerDriftProperties(
                        "3",
                        "3",
                        "0",
                        "0",
                        "production-ir-gate-blocked",
                        "original",
                        "true",
                        "vendor-tuned",
                        "blocked",
                        "true"
                )
        ));

        assertEquals("review-ready", gate.getProperty("status"));
        assertEquals("false", gate.getProperty("productionSourceSwitching"));
        assertEquals("1", gate.getProperty("productionSourceSwitchingEnabled.count"));
        assertEquals("true", gate.getProperty("productionSourceSwitchingEnabled.all"));
        assertEquals("1", gate.getProperty("productionPromotionDecisionMode.productionEnabled.count"));
        assertEquals("true", gate.getProperty("productionPromotionDecisionMode.productionEnabled.all"));
        assertEquals("0", gate.getProperty("productionPromotionOperatorAccepted.count"));
        assertEquals("false", gate.getProperty("productionPromotionOperatorAccepted.all"));
        assertEquals("0", gate.getProperty("sourceSwitching.productionDecision.count"));
        assertEquals("false", gate.getProperty("sourceSwitching.productionDecision.all"));
        assertEquals("false", gate.getProperty("kernel.0.productionSourceSwitching"));
        assertEquals("false", gate.getProperty("kernel.0.sourceSwitching.productionPromotionOperatorAccepted"));
    }

    private static void writeGate(Path gateFile, String properties) throws IOException {
        Files.writeString(gateFile, properties, StandardCharsets.UTF_8);
    }

    private static Properties loadProperties(String propertiesText) throws IOException {
        Properties properties = new Properties();
        try (StringReader reader = new StringReader(propertiesText)) {
            properties.load(reader);
        }
        return properties;
    }

    private static String blockedGateProperties(String... diagnostics) {
        StringBuilder builder = new StringBuilder();
        builder.append("status=blocked\n");
        builder.append("reviewReady=false\n");
        builder.append("ready=false\n");
        builder.append("reconstructed=false\n");
        builder.append("sourceAvailable=false\n");
        builder.append("sourceParityChecked=false\n");
        builder.append("sourceParityMatched=false\n");
        builder.append("runtimeEquivalencePassed=false\n");
        builder.append("runtimeEquivalence.status=not-run\n");
        builder.append("runtimeEquivalence.executed=false\n");
        builder.append("runtimeEquivalence.equivalent=false\n");
        builder.append("runtimeEquivalence.inputCase.count=0\n");
        builder.append("runtimeEquivalence.comparedOutput.count=0\n");
        builder.append("runtimeEquivalence.diagnostic.count=1\n");
        builder.append("runtimeEquivalence.diagnostic.0=runtime equivalence was not executed\n");
        builder.append("fallbackClean=true\n");
        builder.append("selectedSource=descriptor-opencl-source\n");
        builder.append("payloadFormat=unknown\n");
        builder.append("runtimeLoadMode=opencl-descriptor-source-compile\n");
        builder.append("reconstruction.blocker.count=1\n");
        builder.append("reconstruction.blocker.0=irgpu-artifact-missing\n");
        builder.append("reconstruction.diagnostic.count=1\n");
        builder.append("reconstruction.diagnostic.0=OpenCL reconstruction preview skipped because no IrGpu artifact was available\n");
        builder.append("diagnostic.count=").append(diagnostics.length).append('\n');
        for (int index = 0; index < diagnostics.length; index++) {
            builder.append("diagnostic.").append(index).append('=').append(diagnostics[index]).append('\n');
        }
        return builder.toString();
    }

    private static String reviewReadyGateProperties() {
        return String.join("\n",
                "status=review-ready",
                "reviewReady=true",
                "ready=true",
                "reconstructed=true",
                "sourceAvailable=true",
                "sourceParityChecked=true",
                "sourceParityMatched=true",
                "runtimeEquivalencePassed=true",
                "runtimeEquivalence.status=passed",
                "runtimeEquivalence.executed=true",
                "runtimeEquivalence.equivalent=true",
                "runtimeEquivalence.inputCase.count=3",
                "runtimeEquivalence.comparedOutput.count=1",
                "runtimeEquivalence.diagnostic.count=1",
                "runtimeEquivalence.diagnostic.0=deterministic pre/post outputs matched",
                "fallbackClean=true",
                "selectedSource=irgpu-backend-neutral-source",
                "payloadFormat=ir-text-v1",
                "runtimeLoadMode=opencl-irgpu-source-compile",
                "reconstruction.blocker.count=0",
                "reconstruction.diagnostic.count=2",
                "reconstruction.diagnostic.0=sourceParity.checked=true",
                "reconstruction.diagnostic.1=sourceParity.matched=true",
                "diagnostic.count=1",
                "diagnostic.0=backend source reconstruction is ready for promotion review",
                ""
        );
    }

    private static String sourceSwitchingDecisionProperties(
            String status,
            String decision,
            String optimizationProfile,
            String productionProfileRequested,
            String productionSourceSwitching,
            String productionSourceSwitchingEnabled,
            String productionPromotionDecisionMode,
            String productionPromotionOperatorAccepted,
            String sourcePromotionFirstBlocker,
            String diagnostic
    ) {
        return String.join("\n",
                "status=" + status,
                "decision=" + decision,
                "backendTarget=OPENCL",
                "backendFormat=opencl-c",
                "backendResource=kernel.cl",
                "sourceOrigin=irgpu-backend-neutral-source",
                "runtimeLoadMode=opencl-irgpu-source-compile",
                "optimizationProfile=" + optimizationProfile,
                "productionProfileRequested=" + productionProfileRequested,
                "sourceSelection=irgpu",
                "irGpuSourceRequested=true",
                "productionSourceSwitching=" + productionSourceSwitching,
                "productionSourceSwitchingEnabled=" + productionSourceSwitchingEnabled,
                "productionPromotionDecisionMode=" + productionPromotionDecisionMode,
                "productionPromotionOperatorAccepted=" + productionPromotionOperatorAccepted,
                "sourcePromotionFirstBlocker=" + sourcePromotionFirstBlocker,
                "diagnostic.count=1",
                "diagnostic.0=" + diagnostic,
                ""
        );
    }

    private static String runtimeIrHandoffProperties(
            String selectedStage,
            String optimizedDiffersFromOriginal,
            String optimizationRequiresRollback,
            String fallbackDecision,
            String optimizedIrRejected,
            String diagnostic
    ) {
        return String.join("\n",
                "status=selected",
                "selectedStage=" + selectedStage,
                "original.present=true",
                "optimized.present=true",
                "selected.present=true",
                "original.identity=irgpu:sha256:original",
                "optimized.identity=irgpu:sha256:optimized",
                "selected.identity=irgpu:sha256:selected",
                "optimizedDiffersFromOriginal=" + optimizedDiffersFromOriginal,
                "optimizationReportPresent=true",
                "optimizationRequiresRollback=" + optimizationRequiresRollback,
                "fallbackDecision=" + fallbackDecision,
                "optimizedIrRejected=" + optimizedIrRejected,
                "backendTarget=OPENCL",
                "backendFormat=opencl-c",
                "backendResource=kernel.cl",
                "runtimeLoadMode=opencl-source-compile",
                "diagnostic.count=1",
                "diagnostic.0=" + diagnostic,
                ""
        );
    }

    private static String runtimeProductionMutationSafetyProperties(
            String status,
            String productionMutationEnabled,
            String productionGateStatus,
            String productionProfileRequested,
            String selectedStage,
            String diagnostic
    ) {
        return String.join("\n",
                "status=" + status,
                "productionMutationEnabled=" + productionMutationEnabled,
                "productionGateStatus=" + productionGateStatus,
                "productionProfileRequested=" + productionProfileRequested,
                "selectedStage=" + selectedStage,
                "optimizedSelected=" + Boolean.toString("optimized".equals(selectedStage)),
                "optimizedDiffersFromOriginal=" + Boolean.toString("optimized".equals(selectedStage)),
                "optimizedIrRejected=" + Boolean.toString("original".equals(selectedStage)),
                "fallbackDecision=" + ("original".equals(selectedStage) ? "optimizer-rollback" : "none"),
                "runtimeEquivalencePassed=false",
                "fallbackClean=" + Boolean.toString(!"original".equals(selectedStage)),
                "strategyEvidenceBacked=false",
                "vendorPromotionEligible=false",
                "rollbackClean=" + Boolean.toString(!"original".equals(selectedStage)),
                "diagnostic.count=1",
                "diagnostic.0=" + diagnostic,
                ""
        );
    }

    private static String i3ReadinessSummaryProperties(
            String status,
            String selectedRuntimeIrStage,
            String sourcePromotionStatus,
            String sourcePromotionReviewReady,
            String optimizerProductionGateStatus,
            String productionMutationEnabled,
            String diagnostic
    ) {
        return String.join("\n",
                "status=" + status,
                "backendTarget=OPENCL",
                "backendFormat=opencl-c",
                "backendResource=kernel.cl",
                "selectedRuntimeIrStage=" + selectedRuntimeIrStage,
                "selectedRuntimeIrIdentity=irgpu:sha256:selected",
                "optimizedIrRejected=" + Boolean.toString("original".equals(selectedRuntimeIrStage)),
                "fallbackDecision=" + ("original".equals(selectedRuntimeIrStage) ? "optimizer-rollback" : "none"),
                "sourceReconstructed=" + sourcePromotionReviewReady,
                "sourceReady=" + sourcePromotionReviewReady,
                "sourceAvailable=true",
                "sourceParityChecked=true",
                "sourceParityMatched=" + sourcePromotionReviewReady,
                "runtimeEquivalencePassed=" + sourcePromotionReviewReady,
                "sourcePromotionStatus=" + sourcePromotionStatus,
                "sourcePromotionReviewReady=" + sourcePromotionReviewReady,
                "optimizerProductionGateStatus=" + optimizerProductionGateStatus,
                "productionProfileRequested=" + Boolean.toString(!"not-requested".equals(optimizerProductionGateStatus)),
                "productionMutationEnabled=" + productionMutationEnabled,
                "blocker.count=" + ("review-ready".equals(status) ? "1" : "3"),
                "blocker.0=" + ("review-ready".equals(status) ? "production-mutation-disabled" : "backend-source-promotion-not-review-ready"),
                "blocker.1=production-optimizer-gate-not-accepted",
                "blocker.2=production-mutation-disabled",
                "diagnostic.count=1",
                "diagnostic.0=" + diagnostic,
                ""
        );
    }

    private static String runtimeOptimizerDriftProperties(
            String passCount,
            String appliedCount,
            String rolledBackCount,
            String failedCount,
            String fallbackDecision,
            String selectedRuntimeIrStage,
            String optimizedIrRejected,
            String selectedProfile,
            String productionGateStatus,
            String productionProfileRequested
    ) {
        return String.join("\n",
                "pass.count=" + passCount,
                "pass.applied.count=" + appliedCount,
                "pass.skipped.count=0",
                "pass.rolledBack.count=" + rolledBackCount,
                "pass.failed.count=" + failedCount,
                "proofArtifact.count=2",
                "proofArtifact.accepted.count=1",
                "proofArtifact.blocking.count=1",
                "optimizerFamily.count=" + ("optimized".equals(selectedRuntimeIrStage) ? "2" : "1"),
                "optimizerFamily.promotionReady.count=" + ("optimized".equals(selectedRuntimeIrStage) ? "1" : "0"),
                "optimizerFamily.summary=" + ("optimized".equals(selectedRuntimeIrStage)
                        ? "cse[passes=1, acceptedProof=1, blockingProof=0, rolledBack=0, failed=0, promotionReady=true], vector[passes=1, acceptedProof=0, blockingProof=1, rolledBack=0, failed=0, promotionReady=false]"
                        : "vector[passes=2, acceptedProof=1, blockingProof=1, rolledBack=1, failed=0, promotionReady=false]"),
                "fallbackDecision=" + fallbackDecision,
                "selectedRuntimeIrStage=" + selectedRuntimeIrStage,
                "selectedRuntimeIrIdentity=irgpu:sha256:selected",
                "optimizedIrRejected=" + optimizedIrRejected,
                "strategyName=runtime-optimizer-fixture",
                "selectedProfile=" + selectedProfile,
                "baselineStatus=matched",
                "promotionEligible=false",
                "productionGateStatus=" + productionGateStatus,
                "productionProfileRequested=" + productionProfileRequested,
                ""
        );
    }
}
