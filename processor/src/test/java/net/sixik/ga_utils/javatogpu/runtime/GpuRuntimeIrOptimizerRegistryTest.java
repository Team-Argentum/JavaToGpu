package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeIrOptimizerRegistryTest {

    @Test
    void legacyOptimizerProducesAppliedReportWhenArtifactChanges() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact optimized = artifact("body\n  return optimized\n");
        GpuRuntimeIrOptimizer optimizer = new GpuRuntimeIrOptimizer() {
            @Override
            public Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request) {
                return Optional.of(optimized);
            }

            @Override
            public String optimizerVersion() {
                return "optimizer:legacy-test";
            }
        };
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.of(List.of(optimizer));

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(original));

        assertSame(optimized, report.artifact().orElseThrow());
        assertEquals(1, report.passReports().size());
        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        assertEquals(GpuRuntimeIrOptimizationStage.TRANSFORM, passReport.stage());
        assertEquals("optimizer:legacy-test", passReport.optimizerVersion());
        assertEquals(GpuRuntimeIrOptimizationOutcome.APPLIED, passReport.outcome());
        assertEquals("legacy-optimizer-result", passReport.proofStatus());
        assertTrue(passReport.toLine().contains("optimizer used legacy Optional<IrGpuArtifact> API"));
    }

    @Test
    void structuredOptimizerCanReportRollbackWithoutChangingArtifact() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact attempted = artifact("body\n  return attempted\n");
        GpuRuntimeIrOptimizer optimizer = new GpuRuntimeIrOptimizer() {
            @Override
            public Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request) {
                return request.artifact();
            }

            @Override
            public GpuRuntimeIrOptimizationReport optimizeWithReport(GpuRuntimeIrOptimizationRequest request) {
                return new GpuRuntimeIrOptimizationReport(
                        request.artifact(),
                        List.of(GpuRuntimeIrOptimizationPassReport.rolledBack(
                                "optimizer:rollback-test",
                                GpuRuntimeIrOptimizerRegistry.identityOf(request.artifact()),
                                GpuRuntimeIrOptimizerRegistry.identityOf(Optional.of(attempted)),
                                "proof:runtime-equivalence-missing",
                                "runtime equivalence evidence is not available",
                                List.of("kept original IR for safety")
                        ))
                );
            }
        };
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.of(List.of(optimizer));

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(original));

        assertSame(original, report.artifact().orElseThrow());
        assertEquals(2, report.passReports().size());
        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        assertEquals(GpuRuntimeIrOptimizationStage.TRANSFORM, passReport.stage());
        assertEquals(GpuRuntimeIrOptimizationOutcome.ROLLED_BACK, passReport.outcome());
        assertEquals("proof:runtime-equivalence-missing", passReport.proofStatus());
        assertEquals("runtime equivalence evidence is not available", passReport.rollbackReason());
        assertEquals("registry-rollback-safety", report.passReports().get(1).proofStatus());
        assertTrue(report.toText().contains("kept original IR for safety"));
    }

    @Test
    void rolledBackPassCannotLeakMutatedArtifactToFollowingPasses() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        IrGpuArtifact attempted = artifact("body\n  return attempted\n");
        GpuRuntimeIrOptimizer rollbackOptimizer = new GpuRuntimeIrOptimizer() {
            @Override
            public Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request) {
                return request.artifact();
            }

            @Override
            public GpuRuntimeIrOptimizationReport optimizeWithReport(GpuRuntimeIrOptimizationRequest request) {
                return new GpuRuntimeIrOptimizationReport(
                        Optional.of(attempted),
                        List.of(GpuRuntimeIrOptimizationPassReport.rolledBack(
                                "optimizer:unsafe-rollback-test",
                                GpuRuntimeIrOptimizerRegistry.identityOf(request.artifact()),
                                GpuRuntimeIrOptimizerRegistry.identityOf(Optional.of(attempted)),
                                "proof:rejected",
                                "post-transform validation rejected artifact",
                                List.of("attempted artifact must be discarded")
                        ))
                );
            }
        };
        GpuRuntimeIrOptimizer followingOptimizer = request -> {
            throw new AssertionError("rollback must stop the pipeline before the next optimizer");
        };
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.of(List.of(
                rollbackOptimizer,
                followingOptimizer
        ));

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(original));

        assertSame(original, report.artifact().orElseThrow());
        assertEquals(2, report.passReports().size());
        assertEquals(GpuRuntimeIrOptimizationOutcome.ROLLED_BACK, report.passReports().get(0).outcome());
        assertEquals("registry-rollback-safety", report.passReports().get(1).proofStatus());
        assertTrue(report.toText().contains("runtime optimizer registry discarded pass artifact"));
    }

    @Test
    void passReportCarriesI2ArtifactFieldMapAsProofEvidence() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        Map<String, String> i2Fields = Map.of(
                "validationRulesPassed", "false",
                "validationRulesFirstBlockingRuleId", "cse.literalPromotionRuntimeEquivalenceGate",
                "optimizerProductionPreflightProductionMutationEnabled", "false"
        );
        GpuRuntimeIrOptimizer optimizer = new GpuRuntimeIrOptimizer() {
            @Override
            public Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request) {
                return request.artifact();
            }

            @Override
            public GpuRuntimeIrOptimizationReport optimizeWithReport(GpuRuntimeIrOptimizationRequest request) {
                GpuRuntimeIrOptimizationPassReport passReport = GpuRuntimeIrOptimizationPassReport.rolledBack(
                        "optimizer:i2-proof-test",
                        GpuRuntimeIrOptimizerRegistry.identityOf(request.artifact()),
                        GpuRuntimeIrOptimizerRegistry.identityOf(request.artifact()),
                        "i2-proof-blocked",
                        "I2 validation artifact rejected production mutation",
                        List.of("I2 proof fields are attached")
                ).withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "i2.validationRules",
                        "rejected/blockingResultsPresent",
                        i2Fields
                ));
                return new GpuRuntimeIrOptimizationReport(request.artifact(), List.of(passReport));
            }
        };
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.of(List.of(optimizer));

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(original));

        assertSame(original, report.artifact().orElseThrow());
        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        assertEquals("i2.validationRules", passReport.proofArtifact().source());
        assertEquals("rejected/blockingResultsPresent", passReport.proofArtifact().verdict());
        assertEquals("false", passReport.proofArtifact().fields().get("validationRulesPassed"));
        assertTrue(report.toText().contains("proofArtifact source=i2.validationRules"));
        assertTrue(report.toText().contains("validationRulesFirstBlockingRuleId:cse.literalPromotionRuntimeEquivalenceGate"));
    }

    @Test
    void optimizerFailureIsCapturedAsRollbackSafeReport() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        GpuRuntimeIrOptimizer optimizer = new GpuRuntimeIrOptimizer() {
            @Override
            public Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request) {
                throw new IllegalStateException("optimizer exploded");
            }

            @Override
            public String optimizerVersion() {
                return "optimizer:failing-test";
            }
        };
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.of(List.of(optimizer));

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(original));

        assertSame(original, report.artifact().orElseThrow());
        assertEquals(1, report.passReports().size());
        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        assertEquals(GpuRuntimeIrOptimizationStage.TRANSFORM, passReport.stage());
        assertEquals("optimizer:failing-test", passReport.optimizerVersion());
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, passReport.outcome());
        assertEquals("extension-failure-isolated", passReport.proofStatus());
        assertEquals("", passReport.rollbackReason());
        assertTrue(passReport.toLine().contains("optimizer exploded"));
        assertEquals(1, report.extensionExecutionReports().size());
        assertEquals(GpuExtensionExecutionOutcome.FAILED_CONTINUED, report.extensionExecutionReports().get(0).outcome());
        assertTrue(report.extensionExecutionReports().get(0).pipelineContinued());
        assertFalse(report.requiresRollback());
    }

    @Test
    void advisoryOptimizerFailureDoesNotPreventFollowingPass() {
        AtomicBoolean followingInvoked = new AtomicBoolean();
        GpuRuntimeIrOptimizationPass failing = request -> {
            throw new IllegalStateException("advisory failure");
        };
        GpuRuntimeIrOptimizationPass following = request -> {
            followingInvoked.set(true);
            return GpuRuntimeIrOptimizationReport.empty(request.artifact());
        };

        GpuRuntimeIrOptimizationReport report = GpuRuntimeIrOptimizerRegistry.ofPasses(List.of(failing, following))
                .optimizeWithReport(request(artifact("body\n  return original\n")));

        assertTrue(followingInvoked.get());
        assertEquals(2, report.extensionExecutionReports().size());
        assertEquals(GpuExtensionExecutionOutcome.FAILED_CONTINUED, report.extensionExecutionReports().get(0).outcome());
        assertEquals(GpuExtensionExecutionOutcome.SUCCEEDED, report.extensionExecutionReports().get(1).outcome());
        assertFalse(report.requiresRollback());
    }

    @Test
    void productionProfileOptimizerFailureStopsFollowingPasses() {
        AtomicBoolean followingInvoked = new AtomicBoolean();
        GpuRuntimeIrOptimizationPass failing = request -> {
            throw new IllegalStateException("production failure");
        };
        GpuRuntimeIrOptimizationPass following = request -> {
            followingInvoked.set(true);
            return GpuRuntimeIrOptimizationReport.empty(request.artifact());
        };
        IrGpuArtifact artifact = artifact("body\n  return original\n");

        GpuRuntimeIrOptimizationReport report = GpuRuntimeIrOptimizerRegistry.ofPasses(List.of(failing, following))
                .optimizeWithReport(productionRequest(artifact, GpuProductionPromotionDecision.diagnosticOnly()));

        assertFalse(followingInvoked.get());
        assertEquals(GpuRuntimeIrOptimizationOutcome.FAILED, report.passReports().get(0).outcome());
        assertEquals(1, report.extensionExecutionReports().size());
        assertEquals(GpuExtensionExecutionOutcome.FAILED_CLOSED, report.extensionExecutionReports().get(0).outcome());
        assertTrue(report.requiresRollback());
    }

    @Test
    void optimizerRegistryExposesExplicitTransformPassesForLegacyHooks() {
        GpuRuntimeIrOptimizer optimizer = new GpuRuntimeIrOptimizer() {
            @Override
            public Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request) {
                return request.artifact();
            }

            @Override
            public String optimizerVersion() {
                return "optimizer:pass-adapter-test";
            }
        };
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.of(List.of(optimizer));

        List<GpuRuntimeIrOptimizationPass> passes = registry.optimizerPasses();

        assertEquals(1, passes.size());
        assertEquals(GpuRuntimeIrOptimizationStage.TRANSFORM, passes.get(0).stage());
        assertEquals("optimizer:pass-adapter-test", passes.get(0).passVersion());
    }

    @Test
    void registryAcceptsExplicitOptimizationPassesWithoutLegacyAdapter() {
        AtomicBoolean invoked = new AtomicBoolean();
        GpuRuntimeIrOptimizationPass pass = new GpuRuntimeIrOptimizationPass() {
            @Override
            public GpuRuntimeIrOptimizationReport run(GpuRuntimeIrOptimizationRequest request) {
                invoked.set(true);
                return GpuRuntimeIrOptimizationReport.empty(request.artifact());
            }

            @Override
            public String passVersion() {
                return "pass:explicit-test";
            }
        };
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.ofPasses(List.of(pass));

        registry.optimizeWithReport(request(artifact("body\n  return original\n")));

        assertTrue(invoked.get());
        assertEquals(1, registry.optimizerCount());
        assertEquals("pass:explicit-test", registry.optimizerPasses().get(0).passVersion());
        assertTrue(registry.optimizerPipelineVersion().contains("TRANSFORM:pass:explicit-test"));
    }

    @Test
    void commonSubexpressionReviewPassRecordsSingleCseFamilyEvidenceWithoutMutation() {
        IrGpuArtifact artifact = repeatedCseTypedArtifact();
        GpuRuntimeIrOptimizationReport report = GpuRuntimeIrOptimizerRegistry.ofPasses(
                List.of(new GpuRuntimeCommonSubexpressionReviewPass())
        ).optimizeWithReport(request(artifact));

        assertSame(artifact, report.artifact().orElseThrow());
        assertFalse(report.requiresRollback());
        assertEquals(1, report.passReports().size());
        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        assertEquals(GpuRuntimeIrOptimizationOutcome.APPLIED, passReport.outcome());
        assertEquals("optimizer-family:cse:review-v1", passReport.optimizerVersion());
        assertEquals("cse-review-evidence-ready", passReport.proofStatus());
        assertEquals("runtime.cse.review", passReport.proofArtifact().source());
        assertEquals("review-ready", passReport.proofArtifact().verdict());
        assertEquals("cse", passReport.proofArtifact().fields().get("optimizerFamily"));
        assertEquals("none", passReport.proofArtifact().fields().get("firstBlocker"));
        assertEquals("false", passReport.proofArtifact().fields().get("mutationEnabled"));
        assertEquals(
                "optimizer-family:cse:original-vs-optimized",
                passReport.proofArtifact().fields().get("runtimeEquivalenceMode")
        );
        assertEquals("true", passReport.proofArtifact().fields().get("runtimeEquivalencePayload.present"));
    }

    @Test
    void commonSubexpressionReviewPassKeepsNoCandidateKernelsDiagnosticOnly() {
        IrGpuArtifact artifact = fastMathTypedArtifact();
        GpuRuntimeIrOptimizationReport report = GpuRuntimeIrOptimizerRegistry.ofPasses(
                List.of(new GpuRuntimeCommonSubexpressionReviewPass())
        ).optimizeWithReport(request(artifact));

        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, passReport.outcome());
        assertTrue(passReport.analysisOnly());
        assertEquals("diagnostic-only", passReport.proofArtifact().verdict());
        assertFalse(passReport.proofArtifact().fields().containsKey("optimizerFamily"));
        assertEquals("no-cse-candidates", passReport.proofArtifact().fields().get("firstBlocker"));
        assertEquals("0", passReport.proofArtifact().fields().get("candidate.count"));
        assertEquals("true", passReport.proofArtifact().fields().get("analysisOnly"));
        assertEquals("false", passReport.proofArtifact().fields().get("runtimeEquivalencePayload.present"));
        assertFalse(report.requiresRollback());
    }

    @Test
    void registryExportsDeterministicExtensionMetadataWithoutReorderingExplicitPasses() {
        GpuRuntimeIrOptimizationPass later = extensionPass("pass:zeta", "2", 20);
        GpuRuntimeIrOptimizationPass earlier = extensionPass("pass:alpha", "1", 10);

        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.ofPasses(List.of(later, earlier));

        assertSame(later, registry.optimizerPasses().get(0));
        assertSame(earlier, registry.optimizerPasses().get(1));
        assertEquals("pass:alpha", registry.extensionRegistry().descriptors().get(0).id());
        assertEquals("pass:zeta", registry.extensionRegistry().descriptors().get(1).id());
        assertEquals(
                List.of(GpuExtensionCapability.IR_OPTIMIZATION_PROPOSAL),
                registry.extensionRegistry().descriptors().get(0).capabilities()
        );
        assertEquals(GpuExtensionPhase.RUNTIME_IR_OPTIMIZATION, registry.extensionRegistry().descriptors().get(0).phase());
        assertEquals(GpuExtensionPermission.MUTATION_PROPOSAL, registry.extensionRegistry().descriptors().get(0).permission());
        assertEquals("2", registry.extensionArtifactFields().get("runtimeOptimizerExtension.count"));
        assertEquals("pass:alpha", registry.extensionArtifactFields().get("runtimeOptimizerExtension.0.id"));
        assertEquals("RUNTIME_IR_OPTIMIZATION", registry.extensionArtifactFields().get(
                "runtimeOptimizerExtension.0.phase"
        ));
        assertEquals("MUTATION_PROPOSAL", registry.extensionArtifactFields().get(
                "runtimeOptimizerExtension.0.permission"
        ));
        assertEquals("IR_OPTIMIZATION_PROPOSAL", registry.extensionArtifactFields().get(
                "runtimeOptimizerExtension.0.capability.0"
        ));
    }

    @Test
    void registryRejectsDuplicateExtensionIdsFailClosed() {
        GpuRuntimeIrOptimizationPass first = extensionPass("pass:duplicate", "1", 0);
        GpuRuntimeIrOptimizationPass second = extensionPass("pass:duplicate", "2", 1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GpuRuntimeIrOptimizerRegistry.ofPasses(List.of(first, second))
        );

        assertTrue(exception.getMessage().contains("Duplicate GPU extension id 'pass:duplicate'"));
    }

    @Test
    void productionAffectingOptimizerExtensionIsSkippedWithoutBoundAuthorization() {
        AtomicBoolean invoked = new AtomicBoolean();
        GpuRuntimeIrOptimizationPass pass = extensionPass(
                "pass:production-affecting",
                "1",
                0,
                GpuExtensionPermission.PRODUCTION_AFFECTING,
                invoked
        );
        IrGpuArtifact artifact = artifact("body\n  return original\n");
        GpuRuntimeIrOptimizationRequest request = productionRequest(artifact, GpuProductionPromotionDecision.diagnosticOnly());
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.ofPasses(List.of(pass));

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request);

        assertFalse(invoked.get());
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, report.passReports().get(0).outcome());
        assertTrue(report.passReports().get(0).toLine().contains("has no authorization"));
    }

    @Test
    void productionAffectingOptimizerExtensionRunsWithMatchingAcceptedAuthorization() {
        AtomicBoolean invoked = new AtomicBoolean();
        GpuRuntimeIrOptimizationPass pass = extensionPass(
                "pass:production-authorized",
                "1",
                0,
                GpuExtensionPermission.PRODUCTION_AFFECTING,
                invoked
        );
        IrGpuArtifact artifact = artifact("body\n  return original\n");
        GpuProductionPromotionDecision promotionDecision = productionEnabledDecision();
        GpuRuntimeIrOptimizationRequest request = productionRequest(artifact, promotionDecision);
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.ofPasses(List.of(pass));
        GpuRuntimeIrOptimizationReport evidenceReport = acceptedEvidenceReport(artifact, request.strategyDecision());
        GpuProductionExtensionAuthorizationDecision authorization = registry.authorizeProductionExtension(
                pass.extensionId(),
                request.compileRequest(),
                evidenceReport,
                GpuRuntimeEquivalenceEvidence.passed(request.compileRequest(), 8, 8, List.of("outputs matched")),
                GpuRuntimeFallbackEvidence.none(),
                promotionDecision,
                true
        );

        GpuRuntimeIrOptimizationReport report = registry
                .withProductionAuthorizations(List.of(authorization))
                .optimizeWithReport(request);

        assertTrue(authorization.authorized());
        assertEquals("true", authorization.artifactFields("authorization").get("authorization.authorized"));
        assertTrue(invoked.get());
        assertTrue(report.passReports().isEmpty());
    }

    @Test
    void productionAuthorizationCannotBeReusedForDifferentIrArtifact() {
        AtomicBoolean invoked = new AtomicBoolean();
        GpuRuntimeIrOptimizationPass pass = extensionPass(
                "pass:production-bound",
                "1",
                0,
                GpuExtensionPermission.PRODUCTION_AFFECTING,
                invoked
        );
        IrGpuArtifact authorizedArtifact = artifact("body\n  return authorized\n");
        IrGpuArtifact differentArtifact = artifact("body\n  return different\n");
        GpuProductionPromotionDecision promotionDecision = productionEnabledDecision();
        GpuRuntimeIrOptimizationRequest authorizationRequest = productionRequest(authorizedArtifact, promotionDecision);
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.ofPasses(List.of(pass));
        GpuProductionExtensionAuthorizationDecision authorization = registry.authorizeProductionExtension(
                pass.extensionId(),
                authorizationRequest.compileRequest(),
                acceptedEvidenceReport(authorizedArtifact, authorizationRequest.strategyDecision()),
                GpuRuntimeEquivalenceEvidence.passed(
                        authorizationRequest.compileRequest(),
                        8,
                        8,
                        List.of("outputs matched")
                ),
                GpuRuntimeFallbackEvidence.none(),
                promotionDecision,
                true
        );

        GpuRuntimeIrOptimizationReport report = registry
                .withProductionAuthorizations(List.of(authorization))
                .optimizeWithReport(productionRequest(differentArtifact, promotionDecision));

        assertTrue(authorization.authorized());
        assertFalse(invoked.get());
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, report.passReports().get(0).outcome());
        assertTrue(report.passReports().get(0).toLine().contains("does not match this runtime compile context"));
    }

    @Test
    void diagnosticPeepholePassRefusesTextBasedRewrites() {
        IrGpuArtifact artifact = fastMathArtifact("body\n  return ((a * b) + c)\n");
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.ofPasses(
                List.of(new GpuRuntimeIrPeepholePass())
        );

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(artifact));

        assertSame(artifact, report.artifact().orElseThrow());
        assertEquals(1, report.passReports().size());
        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, passReport.outcome());
        assertEquals("runtime.peephole.preflight", passReport.proofArtifact().source());
        assertEquals("blocked", passReport.proofArtifact().verdict());
        assertEquals("peephole", passReport.proofArtifact().fields().get("optimizerFamily"));
        assertEquals("typed-ir-unavailable", passReport.proofArtifact().fields().get("firstBlocker"));
        assertEquals("1", passReport.proofArtifact().fields().get("irTextBody.count"));
        assertEquals("0", passReport.proofArtifact().fields().get("rule.madFma.candidate.count"));
        assertTrue(passReport.toLine().contains("text-based peephole rewriting is disabled"));
    }

    @Test
    void builtInPeepholeRegistryKeepsStableDiagnosticRuleOrder() {
        GpuRuntimeIrPeepholeRuleRegistry registry = GpuRuntimeIrPeepholeRuleRegistry.loadWithBuiltIns();

        assertEquals(
                List.of("madFma", "clamp", "dot", "mix", "step"),
                registry.rules().stream().map(GpuRuntimeIrPeepholeRule::ruleId).toList()
        );
        assertEquals(
                List.of(
                        "peephole-rule:mad-fma-v1",
                        "peephole-rule:clamp-v1",
                        "peephole-rule:dot-v1",
                        "peephole-rule:mix-v1",
                        "peephole-rule:step-v1"
                ),
                registry.rules().stream().map(GpuRuntimeIrPeepholeRule::ruleVersion).toList()
        );
        assertEquals(
                List.of(
                        "javatogpu.peephole.mad-fma",
                        "javatogpu.peephole.clamp",
                        "javatogpu.peephole.dot",
                        "javatogpu.peephole.mix",
                        "javatogpu.peephole.step"
                ),
                registry.rules().stream().map(GpuRuntimeIrPeepholeRule::extensionId).toList()
        );
    }

    @Test
    void diagnosticPeepholePassFindsTypedMadFmaCandidateWithoutMutatingIr() {
        IrGpuArtifact artifact = fastMathTypedArtifact();
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.ofPasses(
                List.of(new GpuRuntimeIrPeepholePass())
        );

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(artifact));

        assertSame(artifact, report.artifact().orElseThrow());
        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, passReport.outcome());
        assertEquals("true", passReport.proofArtifact().fields().get("typedIrAvailable"));
        assertEquals("1", passReport.proofArtifact().fields().get("candidate.count"));
        assertEquals("1", passReport.proofArtifact().fields().get("rule.madFma.candidate.count"));
        assertEquals("5", passReport.proofArtifact().fields().get("rule.count"));
        assertEquals("1", passReport.proofArtifact().fields().get("rule.0.replacementPlan.count"));
        assertEquals("1", passReport.proofArtifact().fields().get("rule.0.replacementPlan.complete.count"));
        assertEquals("0", passReport.proofArtifact().fields().get("rule.0.replacementPlan.partial.count"));
        assertEquals("none", passReport.proofArtifact().fields().get("rule.0.replacementPlan.firstBlocker"));
        assertEquals("madFma", passReport.proofArtifact().fields().get("rule.0.replacementPlan.0.ruleId"));
        assertEquals("kernel", passReport.proofArtifact().fields().get("rule.0.replacementPlan.0.methodName"));
        assertEquals("1", passReport.proofArtifact().fields().get("rule.0.replacementPlan.0.rootNodeId"));
        assertEquals("mad-fma", passReport.proofArtifact().fields().get("rule.0.replacementPlan.0.replacementKind"));
        assertEquals("1,2", passReport.proofArtifact().fields().get("rule.0.replacementPlan.0.coveredNodeIds"));
        assertEquals("3,4,5", passReport.proofArtifact().fields().get("rule.0.replacementPlan.0.inputNodeIds"));
        assertEquals("true", passReport.proofArtifact().fields().get("rule.0.replacementPlan.0.complete"));
        assertEquals("none", passReport.proofArtifact().fields().get("rule.0.replacementPlan.0.firstBlocker"));
        assertEquals("0", passReport.proofArtifact().fields().get("replacementPlan.validation.invalid.count"));
        assertEquals("none", passReport.proofArtifact().fields().get("replacementPlan.validation.firstBlocker"));
        assertEquals("1", passReport.proofArtifact().fields().get("rule.0.replacementPlan.validation.count"));
        assertEquals("1", passReport.proofArtifact().fields().get("rule.0.replacementPlan.validation.valid.count"));
        assertEquals("0", passReport.proofArtifact().fields().get("rule.0.replacementPlan.validation.invalid.count"));
        assertEquals("true", passReport.proofArtifact().fields().get("rule.0.replacementPlan.validation.0.valid"));
        assertEquals("true", passReport.proofArtifact().fields().get("rule.0.replacementPlan.validation.0.rootExists"));
        assertEquals("true", passReport.proofArtifact().fields().get("rule.0.replacementPlan.validation.0.coveredIncludesRoot"));
        assertEquals("none", passReport.proofArtifact().fields().get("rule.0.replacementPlan.validation.0.missingCoveredNodeIds"));
        assertEquals("none", passReport.proofArtifact().fields().get("rule.0.replacementPlan.validation.0.missingInputNodeIds"));
        assertEquals("1", passReport.proofArtifact().fields().get("rewriteVisitor.count"));
        assertEquals("1", passReport.proofArtifact().fields().get("rewriteVisitor.ready.count"));
        assertEquals("0", passReport.proofArtifact().fields().get("rewriteVisitor.blocked.count"));
        assertEquals("none", passReport.proofArtifact().fields().get("rewriteVisitor.firstBlocker"));
        assertEquals("true", passReport.proofArtifact().fields().get("rewriteVisitor.visitorImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteVisitor.replacementBuilderImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteVisitor.transformedIrBuilt"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteVisitor.mutationAllowed"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteVisitor.selectedIrReplacement"));
        assertEquals("visitor-ready", passReport.proofArtifact().fields().get("rule.0.rewriteVisitor.0.status"));
        assertEquals("1,2,3,4,5", passReport.proofArtifact().fields().get("rule.0.rewriteVisitor.0.visitOrderNodeIds"));
        assertEquals("5", passReport.proofArtifact().fields().get("rule.0.rewriteVisitor.0.graphNodeMaxId"));
        assertEquals("true", passReport.proofArtifact().fields().get("rule.0.rewriteVisitor.0.visitorReady"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.rewriteVisitor.0.transformedIrBuilt"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.rewriteVisitor.0.selectedIrReplacement"));
        assertEquals("1", passReport.proofArtifact().fields().get("replacementBlueprint.count"));
        assertEquals("1", passReport.proofArtifact().fields().get("replacementBlueprint.ready.count"));
        assertEquals("0", passReport.proofArtifact().fields().get("replacementBlueprint.blocked.count"));
        assertEquals("none", passReport.proofArtifact().fields().get("replacementBlueprint.firstBlocker"));
        assertEquals("true", passReport.proofArtifact().fields().get("replacementBlueprint.blueprintImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("replacementBlueprint.replacementBuilderImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("replacementBlueprint.transformedIrBuilt"));
        assertEquals("false", passReport.proofArtifact().fields().get("replacementBlueprint.selectedIrReplacement"));
        assertEquals("blueprint-ready", passReport.proofArtifact().fields().get("rule.0.replacementBlueprint.0.status"));
        assertEquals("GpuIrIntrinsicCall", passReport.proofArtifact().fields().get("rule.0.replacementBlueprint.0.targetNodeKind"));
        assertEquals("mad-fma", passReport.proofArtifact().fields().get("rule.0.replacementBlueprint.0.targetOperation"));
        assertEquals("3,4,5", passReport.proofArtifact().fields().get("rule.0.replacementBlueprint.0.argumentNodeIds"));
        assertEquals("arg0,arg1,arg2", passReport.proofArtifact().fields().get("rule.0.replacementBlueprint.0.argumentRoles"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.replacementBlueprint.0.transformedIrBuilt"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.replacementBlueprint.0.selectedIrReplacement"));
        assertEquals("1", passReport.proofArtifact().fields().get("rewriteTransaction.count"));
        assertEquals("1", passReport.proofArtifact().fields().get("rewriteTransaction.ready.count"));
        assertEquals("0", passReport.proofArtifact().fields().get("rewriteTransaction.blocked.count"));
        assertEquals("none", passReport.proofArtifact().fields().get("rewriteTransaction.firstBlocker"));
        assertEquals("true", passReport.proofArtifact().fields().get("rewriteTransaction.transactionPreflightImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteTransaction.nodeIdAllocatorImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteTransaction.graphRewriteImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteTransaction.transformedIrBuilt"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteTransaction.selectedIrReplacement"));
        assertEquals("transaction-ready", passReport.proofArtifact().fields().get("rule.0.rewriteTransaction.0.status"));
        assertEquals("1", passReport.proofArtifact().fields().get("rule.0.rewriteTransaction.0.replacedNodeIds"));
        assertEquals("1,2", passReport.proofArtifact().fields().get("rule.0.rewriteTransaction.0.removedNodeIds"));
        assertEquals("3,4,5", passReport.proofArtifact().fields().get("rule.0.rewriteTransaction.0.retainedInputNodeIds"));
        assertEquals("1", passReport.proofArtifact().fields().get("rule.0.rewriteTransaction.0.plannedAddedNode.count"));
        assertEquals("not-allocated", passReport.proofArtifact().fields().get("rule.0.rewriteTransaction.0.plannedAddedNodeIds"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.rewriteTransaction.0.graphRewriteImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.rewriteTransaction.0.selectedIrReplacement"));
        assertEquals("1", passReport.proofArtifact().fields().get("nodeIdAllocation.count"));
        assertEquals("1", passReport.proofArtifact().fields().get("nodeIdAllocation.ready.count"));
        assertEquals("0", passReport.proofArtifact().fields().get("nodeIdAllocation.blocked.count"));
        assertEquals("none", passReport.proofArtifact().fields().get("nodeIdAllocation.firstBlocker"));
        assertEquals("true", passReport.proofArtifact().fields().get("nodeIdAllocation.allocationPreflightImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("nodeIdAllocation.nodeIdsReserved"));
        assertEquals("false", passReport.proofArtifact().fields().get("nodeIdAllocation.nodeIdAllocatorApplied"));
        assertEquals("false", passReport.proofArtifact().fields().get("nodeIdAllocation.graphRewriteImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("nodeIdAllocation.selectedIrReplacement"));
        assertEquals("allocation-ready", passReport.proofArtifact().fields().get("rule.0.nodeIdAllocation.0.status"));
        assertEquals("5", passReport.proofArtifact().fields().get("rule.0.nodeIdAllocation.0.graphNodeMaxId"));
        assertEquals("1", passReport.proofArtifact().fields().get("rule.0.nodeIdAllocation.0.plannedAddedNode.count"));
        assertEquals("6", passReport.proofArtifact().fields().get("rule.0.nodeIdAllocation.0.candidateNodeIds"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.nodeIdAllocation.0.nodeIdsReserved"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.nodeIdAllocation.0.selectedIrReplacement"));
        assertEquals("1", passReport.proofArtifact().fields().get("replacementNode.count"));
        assertEquals("1", passReport.proofArtifact().fields().get("replacementNode.ready.count"));
        assertEquals("0", passReport.proofArtifact().fields().get("replacementNode.blocked.count"));
        assertEquals("none", passReport.proofArtifact().fields().get("replacementNode.firstBlocker"));
        assertEquals("true", passReport.proofArtifact().fields().get("replacementNode.replacementNodePreflightImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("replacementNode.replacementNodeBuilt"));
        assertEquals("false", passReport.proofArtifact().fields().get("replacementNode.replacementBuilderImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("replacementNode.graphRewriteImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("replacementNode.selectedIrReplacement"));
        assertEquals("replacement-node-ready", passReport.proofArtifact().fields().get("rule.0.replacementNode.0.status"));
        assertEquals("6", passReport.proofArtifact().fields().get("rule.0.replacementNode.0.candidateReplacementNodeId"));
        assertEquals("GpuIrIntrinsicCall", passReport.proofArtifact().fields().get("rule.0.replacementNode.0.targetNodeKind"));
        assertEquals("mad-fma", passReport.proofArtifact().fields().get("rule.0.replacementNode.0.targetOperation"));
        assertEquals("3,4,5", passReport.proofArtifact().fields().get("rule.0.replacementNode.0.argumentNodeIds"));
        assertEquals("arg0,arg1,arg2", passReport.proofArtifact().fields().get("rule.0.replacementNode.0.argumentRoles"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.replacementNode.0.replacementNodeBuilt"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.replacementNode.0.selectedIrReplacement"));
        assertEquals("1", passReport.proofArtifact().fields().get("graphPatch.count"));
        assertEquals("1", passReport.proofArtifact().fields().get("graphPatch.ready.count"));
        assertEquals("0", passReport.proofArtifact().fields().get("graphPatch.blocked.count"));
        assertEquals("none", passReport.proofArtifact().fields().get("graphPatch.firstBlocker"));
        assertEquals("true", passReport.proofArtifact().fields().get("graphPatch.graphPatchPreflightImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("graphPatch.graphPatchApplied"));
        assertEquals("false", passReport.proofArtifact().fields().get("graphPatch.graphRewriteImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("graphPatch.selectedIrReplacement"));
        assertEquals("graph-patch-ready", passReport.proofArtifact().fields().get("rule.0.graphPatch.0.status"));
        assertEquals("6", passReport.proofArtifact().fields().get("rule.0.graphPatch.0.replacementNodeId"));
        assertEquals("1", passReport.proofArtifact().fields().get("rule.0.graphPatch.0.replacedNodeIds"));
        assertEquals("1,2", passReport.proofArtifact().fields().get("rule.0.graphPatch.0.removedNodeIds"));
        assertEquals("3,4,5", passReport.proofArtifact().fields().get("rule.0.graphPatch.0.retainedInputNodeIds"));
        assertEquals("6", passReport.proofArtifact().fields().get("rule.0.graphPatch.0.insertedNodeIds"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.graphPatch.0.graphPatchApplied"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.graphPatch.0.selectedIrReplacement"));
        assertEquals("1", passReport.proofArtifact().fields().get("transformedGraph.count"));
        assertEquals("1", passReport.proofArtifact().fields().get("transformedGraph.ready.count"));
        assertEquals("0", passReport.proofArtifact().fields().get("transformedGraph.blocked.count"));
        assertEquals("none", passReport.proofArtifact().fields().get("transformedGraph.firstBlocker"));
        assertEquals("true", passReport.proofArtifact().fields().get("transformedGraph.materializationPreflightImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("transformedGraph.transformedGraphBuilt"));
        assertEquals("false", passReport.proofArtifact().fields().get("transformedGraph.transformedIrBuilt"));
        assertEquals("false", passReport.proofArtifact().fields().get("transformedGraph.graphPatchApplied"));
        assertEquals("false", passReport.proofArtifact().fields().get("transformedGraph.selectedIrReplacement"));
        assertEquals("materialization-ready", passReport.proofArtifact().fields().get("rule.0.transformedGraph.0.status"));
        assertEquals("not-built", passReport.proofArtifact().fields().get("rule.0.transformedGraph.0.transformedGraphIdentity"));
        assertEquals(passReport.originalIrIdentity(), passReport.proofArtifact().fields().get("rule.0.transformedGraph.0.originalIrIdentity"));
        String materializationKey = "madFma|kernel|1|mad-fma|replacement=6|replace=1|remove=1,2|retain=3,4,5|insert=6";
        assertEquals(materializationKey, passReport.proofArtifact().fields().get("rule.0.transformedGraph.0.materializationKey"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.transformedGraph.0.transformedGraphBuilt"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.transformedGraph.0.selectedIrReplacement"));
        assertEquals("1", passReport.proofArtifact().fields().get("irArtifactEnvelope.count"));
        assertEquals("1", passReport.proofArtifact().fields().get("irArtifactEnvelope.ready.count"));
        assertEquals("0", passReport.proofArtifact().fields().get("irArtifactEnvelope.blocked.count"));
        assertEquals("none", passReport.proofArtifact().fields().get("irArtifactEnvelope.firstBlocker"));
        assertEquals("true", passReport.proofArtifact().fields().get("irArtifactEnvelope.artifactEnvelopePreflightImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("irArtifactEnvelope.artifactEnvelopeBuilt"));
        assertEquals("false", passReport.proofArtifact().fields().get("irArtifactEnvelope.optimizedArtifactBuilt"));
        assertEquals("false", passReport.proofArtifact().fields().get("irArtifactEnvelope.selectedIrReplacement"));
        assertEquals("artifact-envelope-ready", passReport.proofArtifact().fields().get("rule.0.irArtifactEnvelope.0.status"));
        assertEquals("not-built", passReport.proofArtifact().fields().get("rule.0.irArtifactEnvelope.0.optimizedArtifactIdentity"));
        assertEquals(passReport.originalIrIdentity(), passReport.proofArtifact().fields().get("rule.0.irArtifactEnvelope.0.originalIrIdentity"));
        assertEquals(materializationKey, passReport.proofArtifact().fields().get("rule.0.irArtifactEnvelope.0.materializationKey"));
        assertEquals("runtime-equivalence-required", passReport.proofArtifact().fields().get("rule.0.irArtifactEnvelope.0.proofAnchor"));
        assertEquals("original-ir", passReport.proofArtifact().fields().get("rule.0.irArtifactEnvelope.0.rollbackAnchor"));
        assertEquals(
                "madFma|kernel|1|mad-fma|original=" + passReport.originalIrIdentity()
                        + "|graph=not-built|materialization=" + materializationKey
                        + "|proof=runtime-equivalence-required|rollback=original-ir",
                passReport.proofArtifact().fields().get("rule.0.irArtifactEnvelope.0.envelopeKey")
        );
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.irArtifactEnvelope.0.artifactEnvelopeBuilt"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.irArtifactEnvelope.0.selectedIrReplacement"));
        assertEquals("1", passReport.proofArtifact().fields().get("artifactProofBinding.count"));
        assertEquals("0", passReport.proofArtifact().fields().get("artifactProofBinding.ready.count"));
        assertEquals("1", passReport.proofArtifact().fields().get("artifactProofBinding.blocked.count"));
        assertEquals("rewrite-builder-not-implemented", passReport.proofArtifact().fields().get("artifactProofBinding.firstBlocker"));
        assertEquals("true", passReport.proofArtifact().fields().get("artifactProofBinding.bindingPreflightImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("artifactProofBinding.proofBound"));
        assertEquals("false", passReport.proofArtifact().fields().get("artifactProofBinding.rollbackBound"));
        assertEquals("false", passReport.proofArtifact().fields().get("artifactProofBinding.approvalBound"));
        assertEquals("false", passReport.proofArtifact().fields().get("artifactProofBinding.optimizedArtifactBuilt"));
        assertEquals("false", passReport.proofArtifact().fields().get("artifactProofBinding.selectedIrReplacement"));
        assertEquals("blocked", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.status"));
        assertEquals("rewrite-builder-not-implemented", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.firstBlocker"));
        assertEquals(passReport.originalIrIdentity(), passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.originalIrIdentity"));
        assertEquals("not-built", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.optimizedArtifactIdentity"));
        assertEquals("runtime-equivalence-required", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.proofAnchor"));
        assertEquals("original-ir", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.rollbackAnchor"));
        assertEquals("blocked", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.proofStatus"));
        assertEquals("rewrite-builder-not-implemented", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.proofFirstBlocker"));
        assertEquals("blocked", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.reviewPackageStatus"));
        assertEquals("runtime-equivalence-payload-missing", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.reviewPackageFirstBlocker"));
        assertEquals("true", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.artifactEnvelopeReady"));
        assertEquals("true", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.proofRequired"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.proofAccepted"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.runtimeEquivalencePayload.complete"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.rollbackEvidence.present"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.rollbackClean"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.approvalAccepted"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.reviewPackageComplete"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.bindingReady"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.proofBound"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.rollbackBound"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.approvalBound"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.optimizedArtifactBuilt"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.transformedIrBuilt"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactProofBinding.0.selectedIrReplacement"));
        assertEquals("1", passReport.proofArtifact().fields().get("artifactSelection.count"));
        assertEquals("0", passReport.proofArtifact().fields().get("artifactSelection.ready.count"));
        assertEquals("1", passReport.proofArtifact().fields().get("artifactSelection.blocked.count"));
        assertEquals("rewrite-builder-not-implemented", passReport.proofArtifact().fields().get("artifactSelection.firstBlocker"));
        assertEquals("true", passReport.proofArtifact().fields().get("artifactSelection.selectionPreflightImplemented"));
        assertEquals("true", passReport.proofArtifact().fields().get("artifactSelection.productionGateRequired"));
        assertEquals("false", passReport.proofArtifact().fields().get("artifactSelection.productionGateAccepted"));
        assertEquals("false", passReport.proofArtifact().fields().get("artifactSelection.selectionApplied"));
        assertEquals("false", passReport.proofArtifact().fields().get("artifactSelection.optimizedArtifactSelected"));
        assertEquals("false", passReport.proofArtifact().fields().get("artifactSelection.selectedIrReplacement"));
        assertEquals("blocked", passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.status"));
        assertEquals("rewrite-builder-not-implemented", passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.firstBlocker"));
        assertEquals(passReport.originalIrIdentity(), passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.originalIrIdentity"));
        assertEquals("not-built", passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.optimizedArtifactIdentity"));
        assertEquals("blocked", passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.proofBindingStatus"));
        assertEquals("rewrite-builder-not-implemented", passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.proofBindingFirstBlocker"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.proofBindingReady"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.proofBound"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.rollbackBound"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.approvalBound"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.optimizedArtifactBuilt"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.transformedIrBuilt"));
        assertEquals("true", passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.productionGateRequired"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.productionGateAccepted"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.mutationPolicyAllowed"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.selectionReady"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.selectionApplied"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.artifactSelection.0.optimizedArtifactSelected"));
        assertEquals("1", passReport.proofArtifact().fields().get("rewriteSketch.count"));
        assertEquals("1", passReport.proofArtifact().fields().get("rewriteSketch.ready.count"));
        assertEquals("0", passReport.proofArtifact().fields().get("rewriteSketch.blocked.count"));
        assertEquals("none", passReport.proofArtifact().fields().get("rewriteSketch.firstBlocker"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteSketch.rewriteBuilderImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteSketch.mutationAllowed"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteSketch.selectedIrReplacement"));
        assertEquals("true", passReport.proofArtifact().fields().get("rewriteSketch.runtimeEquivalenceRequired"));
        assertEquals("true", passReport.proofArtifact().fields().get("rewriteSketch.approvalRequired"));
        assertEquals("0", passReport.proofArtifact().fields().get("rewriteSketch.conflict.count"));
        assertEquals("none", passReport.proofArtifact().fields().get("rewriteSketch.conflict.firstBlocker"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteSketch.conflict.conflictResolutionImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteSketch.conflict.selectionApplied"));
        assertEquals("blocked", passReport.proofArtifact().fields().get("rewriteSelection.status"));
        assertEquals("rewrite-builder-not-implemented", passReport.proofArtifact().fields().get("rewriteSelection.firstBlocker"));
        assertEquals("1", passReport.proofArtifact().fields().get("rewriteSelection.sketch.count"));
        assertEquals("1", passReport.proofArtifact().fields().get("rewriteSelection.sketch.ready.count"));
        assertEquals("0", passReport.proofArtifact().fields().get("rewriteSelection.sketch.blocked.count"));
        assertEquals("0", passReport.proofArtifact().fields().get("rewriteSelection.conflict.count"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteSelection.rewriteBuilderImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteSelection.conflictResolutionImplemented"));
        assertEquals("true", passReport.proofArtifact().fields().get("rewriteSelection.runtimeEquivalenceRequired"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteSelection.runtimeEquivalenceProven"));
        assertEquals("true", passReport.proofArtifact().fields().get("rewriteSelection.approvalRequired"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteSelection.approvalAccepted"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteSelection.mutationAllowed"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteSelection.selectionApplied"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteSelection.selectedIrReplacement"));
        assertEquals("blocked", passReport.proofArtifact().fields().get("rewriteProof.status"));
        assertEquals("rewrite-builder-not-implemented", passReport.proofArtifact().fields().get("rewriteProof.firstBlocker"));
        assertEquals("true", passReport.proofArtifact().fields().get("rewriteProof.proofRequired"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteProof.proofAccepted"));
        assertEquals("true", passReport.proofArtifact().fields().get("rewriteProof.runtimeEquivalenceRequired"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteProof.runtimeEquivalencePayload.present"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteProof.runtimeEquivalencePayload.complete"));
        assertEquals("true", passReport.proofArtifact().fields().get("rewriteProof.rollbackRequired"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteProof.rollbackEvidence.present"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteProof.rollbackClean"));
        assertEquals("true", passReport.proofArtifact().fields().get("rewriteProof.approvalRequired"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteProof.approvalAccepted"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteProof.mutationAllowed"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteProof.selectedIrReplacement"));
        assertEquals("not-built", passReport.proofArtifact().fields().get("rewriteProof.transformedIrIdentity"));
        assertEquals("blocked", passReport.proofArtifact().fields().get("rewriteReviewPackage.status"));
        assertEquals("runtime-equivalence-payload-missing", passReport.proofArtifact().fields().get("rewriteReviewPackage.firstBlocker"));
        assertEquals("true", passReport.proofArtifact().fields().get("rewriteReviewPackage.required"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteReviewPackage.complete"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteReviewPackage.proofAccepted"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteReviewPackage.runtimeEquivalencePayload.complete"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteReviewPackage.rollbackClean"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteReviewPackage.approvalAccepted"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteReviewPackage.mutationAllowed"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteReviewPackage.selectionApplied"));
        assertEquals("false", passReport.proofArtifact().fields().get("rewriteReviewPackage.selectedIrReplacement"));
        assertEquals("true", passReport.proofArtifact().fields().get("rewriteReviewPackage.manualReviewOnly"));
        assertEquals("sketch-ready", passReport.proofArtifact().fields().get("rule.0.rewriteSketch.0.status"));
        assertEquals("true", passReport.proofArtifact().fields().get("rule.0.rewriteSketch.0.planComplete"));
        assertEquals("true", passReport.proofArtifact().fields().get("rule.0.rewriteSketch.0.planValid"));
        assertEquals("true", passReport.proofArtifact().fields().get("rule.0.rewriteSketch.0.sketchReady"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.rewriteSketch.0.rewriteBuilderImplemented"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.rewriteSketch.0.mutationAllowed"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.rewriteSketch.0.selectedIrReplacement"));
        assertEquals("blocked", passReport.proofArtifact().fields().get("rule.0.rewriteSelection.status"));
        assertEquals("rewrite-builder-not-implemented", passReport.proofArtifact().fields().get("rule.0.rewriteSelection.firstBlocker"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.rewriteSelection.selectionApplied"));
        assertEquals("blocked", passReport.proofArtifact().fields().get("rule.0.rewriteProof.status"));
        assertEquals("rewrite-builder-not-implemented", passReport.proofArtifact().fields().get("rule.0.rewriteProof.firstBlocker"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.rewriteProof.proofAccepted"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.rewriteProof.runtimeEquivalencePayload.complete"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.rewriteProof.rollbackClean"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.rewriteProof.selectedIrReplacement"));
        assertEquals("blocked", passReport.proofArtifact().fields().get("rule.0.rewriteReviewPackage.status"));
        assertEquals("runtime-equivalence-payload-missing", passReport.proofArtifact().fields().get("rule.0.rewriteReviewPackage.firstBlocker"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.rewriteReviewPackage.complete"));
        assertEquals("false", passReport.proofArtifact().fields().get("rule.0.rewriteReviewPackage.selectedIrReplacement"));
        assertEquals("clamp", passReport.proofArtifact().fields().get("rule.1.id"));
        assertEquals("dot", passReport.proofArtifact().fields().get("rule.2.id"));
        assertEquals("mix", passReport.proofArtifact().fields().get("rule.3.id"));
        assertEquals("step", passReport.proofArtifact().fields().get("rule.4.id"));
        assertEquals("1", passReport.proofArtifact().fields().get("rule.1.skipped.count"));
        assertEquals("no-candidate", passReport.proofArtifact().fields().get("rule.1.proofStatus"));
        assertEquals("rewrite-engine-not-implemented", passReport.proofArtifact().fields().get("firstBlocker"));
        assertTrue(passReport.toLine().contains("structural rewrite and proof emission are not implemented"));
    }

    @Test
    void diagnosticPeepholePreflightsKeepProductionEnablingFieldsDisabled() {
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.ofPasses(
                List.of(new GpuRuntimeIrPeepholePass())
        );
        for (IrGpuArtifact artifact : List.of(
                fastMathTypedArtifact(),
                fastMathPartialMadFmaTypedArtifact(),
                fastMathClampTypedArtifact(),
                fastMathStepTypedArtifact(),
                fastMathDotTypedArtifact(),
                fastMathMixTypedArtifact()
        )) {
            GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(artifact));
            GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);

            assertSame(artifact, report.artifact().orElseThrow());
            assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, passReport.outcome());
            assertNoProductionEnablingPreflightFields(passReport.proofArtifact().fields());
        }
    }

    @Test
    void diagnosticPeepholePassReportsPartialMadFmaPlanWithoutCandidate() {
        IrGpuArtifact artifact = fastMathPartialMadFmaTypedArtifact();
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.ofPasses(
                List.of(new GpuRuntimeIrPeepholePass())
        );

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(artifact));

        assertSame(artifact, report.artifact().orElseThrow());
        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        Map<String, String> fields = passReport.proofArtifact().fields();
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, passReport.outcome());
        assertEquals("0", fields.get("candidate.count"));
        assertEquals("0", fields.get("rule.madFma.candidate.count"));
        assertEquals("3", fields.get("replacementPlan.partial.count"));
        assertEquals("multiply-operands-incomplete", fields.get("replacementPlan.firstBlocker"));
        assertEquals("1", fields.get("rule.0.replacementPlan.count"));
        assertEquals("0", fields.get("rule.0.replacementPlan.complete.count"));
        assertEquals("1", fields.get("rule.0.replacementPlan.partial.count"));
        assertEquals("multiply-operands-incomplete", fields.get("rule.0.replacementPlan.firstBlocker"));
        assertEquals("1", fields.get("rule.0.replacementPlan.0.rootNodeId"));
        assertEquals("1,2", fields.get("rule.0.replacementPlan.0.coveredNodeIds"));
        assertEquals("5", fields.get("rule.0.replacementPlan.0.inputNodeIds"));
        assertEquals("false", fields.get("rule.0.replacementPlan.0.complete"));
        assertEquals("multiply-operands-incomplete", fields.get("rule.0.replacementPlan.0.firstBlocker"));
        assertEquals("dot", fields.get("rule.2.id"));
        assertEquals("1", fields.get("rule.2.replacementPlan.count"));
        assertEquals("0", fields.get("rule.2.replacementPlan.complete.count"));
        assertEquals("1", fields.get("rule.2.replacementPlan.partial.count"));
        assertEquals("dot-multiply-operands-incomplete", fields.get("rule.2.replacementPlan.firstBlocker"));
        assertEquals("mix", fields.get("rule.3.id"));
        assertEquals("1", fields.get("rule.3.replacementPlan.count"));
        assertEquals("0", fields.get("rule.3.replacementPlan.complete.count"));
        assertEquals("1", fields.get("rule.3.replacementPlan.partial.count"));
        assertEquals("mix-multiply-operands-incomplete", fields.get("rule.3.replacementPlan.firstBlocker"));
        assertEquals("multiply-operands-incomplete", fields.get("firstBlocker"));
        assertTrue(passReport.toLine().contains("replacement plan is incomplete"));
    }

    @Test
    void diagnosticPeepholePassFindsTypedClampCandidateWithoutMutatingIr() {
        IrGpuArtifact artifact = fastMathClampTypedArtifact();
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.ofPasses(
                List.of(new GpuRuntimeIrPeepholePass())
        );

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(artifact));

        assertSame(artifact, report.artifact().orElseThrow());
        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        Map<String, String> fields = passReport.proofArtifact().fields();
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, passReport.outcome());
        assertEquals("1", fields.get("candidate.count"));
        assertEquals("0", fields.get("rule.madFma.candidate.count"));
        assertEquals("1", fields.get("rule.clamp.candidate.count"));
        assertEquals("clamp", fields.get("rule.1.id"));
        assertEquals("1", fields.get("rule.1.candidate.count"));
        assertEquals("1", fields.get("rule.1.replacementPlan.count"));
        assertEquals("1", fields.get("rule.1.replacementPlan.complete.count"));
        assertEquals("0", fields.get("rule.1.replacementPlan.partial.count"));
        assertEquals("none", fields.get("rule.1.replacementPlan.firstBlocker"));
        assertEquals("clamp", fields.get("rule.1.replacementPlan.0.ruleId"));
        assertEquals("kernel", fields.get("rule.1.replacementPlan.0.methodName"));
        assertEquals("1", fields.get("rule.1.replacementPlan.0.rootNodeId"));
        assertEquals("clamp", fields.get("rule.1.replacementPlan.0.replacementKind"));
        assertEquals("1,2", fields.get("rule.1.replacementPlan.0.coveredNodeIds"));
        assertEquals("3,4,5", fields.get("rule.1.replacementPlan.0.inputNodeIds"));
        assertEquals("true", fields.get("rule.1.replacementPlan.0.complete"));
        assertEquals("rewrite-engine-not-implemented", fields.get("firstBlocker"));
        assertTrue(passReport.toLine().contains("structural rewrite and proof emission are not implemented"));
    }

    @Test
    void diagnosticPeepholePassFindsTypedStepCandidateWithoutMutatingIr() {
        IrGpuArtifact artifact = fastMathStepTypedArtifact();
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.ofPasses(
                List.of(new GpuRuntimeIrPeepholePass())
        );

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(artifact));

        assertSame(artifact, report.artifact().orElseThrow());
        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        Map<String, String> fields = passReport.proofArtifact().fields();
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, passReport.outcome());
        assertEquals("1", fields.get("candidate.count"));
        assertEquals("0", fields.get("rule.madFma.candidate.count"));
        assertEquals("0", fields.get("rule.clamp.candidate.count"));
        assertEquals("1", fields.get("rule.step.candidate.count"));
        assertEquals("step", fields.get("rule.4.id"));
        assertEquals("1", fields.get("rule.4.candidate.count"));
        assertEquals("1", fields.get("rule.4.replacementPlan.count"));
        assertEquals("1", fields.get("rule.4.replacementPlan.complete.count"));
        assertEquals("0", fields.get("rule.4.replacementPlan.partial.count"));
        assertEquals("none", fields.get("rule.4.replacementPlan.firstBlocker"));
        assertEquals("step", fields.get("rule.4.replacementPlan.0.ruleId"));
        assertEquals("kernel", fields.get("rule.4.replacementPlan.0.methodName"));
        assertEquals("1", fields.get("rule.4.replacementPlan.0.rootNodeId"));
        assertEquals("step", fields.get("rule.4.replacementPlan.0.replacementKind"));
        assertEquals("1,2", fields.get("rule.4.replacementPlan.0.coveredNodeIds"));
        assertEquals("4,3", fields.get("rule.4.replacementPlan.0.inputNodeIds"));
        assertEquals("true", fields.get("rule.4.replacementPlan.0.complete"));
        assertEquals("rewrite-engine-not-implemented", fields.get("firstBlocker"));
        assertTrue(passReport.toLine().contains("structural rewrite and proof emission are not implemented"));
    }

    @Test
    void diagnosticPeepholePassFindsTypedDotCandidateWithoutMutatingIr() {
        IrGpuArtifact artifact = fastMathDotTypedArtifact();
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.ofPasses(
                List.of(new GpuRuntimeIrPeepholePass())
        );

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(artifact));

        assertSame(artifact, report.artifact().orElseThrow());
        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        Map<String, String> fields = passReport.proofArtifact().fields();
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, passReport.outcome());
        assertEquals("2", fields.get("candidate.count"));
        assertEquals("1", fields.get("rule.madFma.candidate.count"));
        assertEquals("0", fields.get("rule.clamp.candidate.count"));
        assertEquals("1", fields.get("rule.dot.candidate.count"));
        assertEquals("dot", fields.get("rule.2.id"));
        assertEquals("1", fields.get("rule.2.candidate.count"));
        assertEquals("1", fields.get("rule.2.replacementPlan.count"));
        assertEquals("1", fields.get("rule.2.replacementPlan.complete.count"));
        assertEquals("0", fields.get("rule.2.replacementPlan.partial.count"));
        assertEquals("none", fields.get("rule.2.replacementPlan.firstBlocker"));
        assertEquals("dot", fields.get("rule.2.replacementPlan.0.ruleId"));
        assertEquals("kernel", fields.get("rule.2.replacementPlan.0.methodName"));
        assertEquals("1", fields.get("rule.2.replacementPlan.0.rootNodeId"));
        assertEquals("dot", fields.get("rule.2.replacementPlan.0.replacementKind"));
        assertEquals("1,2,5", fields.get("rule.2.replacementPlan.0.coveredNodeIds"));
        assertEquals("3,4,6,7", fields.get("rule.2.replacementPlan.0.inputNodeIds"));
        assertEquals("true", fields.get("rule.2.replacementPlan.0.complete"));
        assertEquals("2", fields.get("rewriteSketch.count"));
        assertEquals("2", fields.get("rewriteSketch.ready.count"));
        assertEquals("1", fields.get("rewriteSketch.conflict.count"));
        assertEquals("rewrite-sketch-covered-node-overlap", fields.get("rewriteSketch.conflict.firstBlocker"));
        assertEquals("false", fields.get("rewriteSketch.conflict.conflictResolutionImplemented"));
        assertEquals("false", fields.get("rewriteSketch.conflict.selectionApplied"));
        assertEquals("false", fields.get("rewriteSketch.conflict.mutationAllowed"));
        assertEquals("false", fields.get("rewriteSketch.conflict.selectedIrReplacement"));
        assertEquals("kernel", fields.get("rewriteSketch.conflict.0.methodName"));
        assertEquals("madFma", fields.get("rewriteSketch.conflict.0.firstRuleId"));
        assertEquals("1", fields.get("rewriteSketch.conflict.0.firstRootNodeId"));
        assertEquals("mad-fma", fields.get("rewriteSketch.conflict.0.firstReplacementKind"));
        assertEquals("dot", fields.get("rewriteSketch.conflict.0.secondRuleId"));
        assertEquals("1", fields.get("rewriteSketch.conflict.0.secondRootNodeId"));
        assertEquals("dot", fields.get("rewriteSketch.conflict.0.secondReplacementKind"));
        assertEquals("1,2", fields.get("rewriteSketch.conflict.0.overlappingNodeIds"));
        assertEquals("blocked", fields.get("rewriteSelection.status"));
        assertEquals("rewrite-sketch-conflict-resolution-required", fields.get("rewriteSelection.firstBlocker"));
        assertEquals("2", fields.get("rewriteSelection.sketch.count"));
        assertEquals("2", fields.get("rewriteSelection.sketch.ready.count"));
        assertEquals("0", fields.get("rewriteSelection.sketch.blocked.count"));
        assertEquals("1", fields.get("rewriteSelection.conflict.count"));
        assertEquals("false", fields.get("rewriteSelection.conflictResolutionImplemented"));
        assertEquals("false", fields.get("rewriteSelection.selectionApplied"));
        assertEquals("false", fields.get("rewriteSelection.selectedIrReplacement"));
        assertEquals("blocked", fields.get("rewriteProof.status"));
        assertEquals("rewrite-sketch-conflict-resolution-required", fields.get("rewriteProof.firstBlocker"));
        assertEquals("true", fields.get("rewriteProof.proofRequired"));
        assertEquals("false", fields.get("rewriteProof.proofAccepted"));
        assertEquals("true", fields.get("rewriteProof.runtimeEquivalenceRequired"));
        assertEquals("false", fields.get("rewriteProof.runtimeEquivalencePayload.present"));
        assertEquals("false", fields.get("rewriteProof.rollbackEvidence.present"));
        assertEquals("true", fields.get("rewriteProof.rollbackRequired"));
        assertEquals("false", fields.get("rewriteProof.rollbackClean"));
        assertEquals("false", fields.get("rewriteProof.selectedIrReplacement"));
        assertEquals("blocked", fields.get("rewriteReviewPackage.status"));
        assertEquals("rewrite-sketch-conflict-resolution-required", fields.get("rewriteReviewPackage.firstBlocker"));
        assertEquals("false", fields.get("rewriteReviewPackage.complete"));
        assertEquals("false", fields.get("rewriteReviewPackage.selectedIrReplacement"));
        assertEquals("rewrite-engine-not-implemented", fields.get("firstBlocker"));
        assertTrue(passReport.toLine().contains("structural rewrite and proof emission are not implemented"));
    }

    @Test
    void diagnosticPeepholePassFindsTypedMixCandidateWithoutMutatingIr() {
        IrGpuArtifact artifact = fastMathMixTypedArtifact();
        GpuRuntimeIrOptimizerRegistry registry = GpuRuntimeIrOptimizerRegistry.ofPasses(
                List.of(new GpuRuntimeIrPeepholePass())
        );

        GpuRuntimeIrOptimizationReport report = registry.optimizeWithReport(request(artifact));

        assertSame(artifact, report.artifact().orElseThrow());
        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        Map<String, String> fields = passReport.proofArtifact().fields();
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, passReport.outcome());
        assertEquals("2", fields.get("candidate.count"));
        assertEquals("1", fields.get("rule.madFma.candidate.count"));
        assertEquals("0", fields.get("rule.clamp.candidate.count"));
        assertEquals("0", fields.get("rule.dot.candidate.count"));
        assertEquals("1", fields.get("rule.mix.candidate.count"));
        assertEquals("mix", fields.get("rule.3.id"));
        assertEquals("1", fields.get("rule.3.candidate.count"));
        assertEquals("1", fields.get("rule.3.replacementPlan.count"));
        assertEquals("1", fields.get("rule.3.replacementPlan.complete.count"));
        assertEquals("0", fields.get("rule.3.replacementPlan.partial.count"));
        assertEquals("none", fields.get("rule.3.replacementPlan.firstBlocker"));
        assertEquals("mix", fields.get("rule.3.replacementPlan.0.ruleId"));
        assertEquals("kernel", fields.get("rule.3.replacementPlan.0.methodName"));
        assertEquals("1", fields.get("rule.3.replacementPlan.0.rootNodeId"));
        assertEquals("mix", fields.get("rule.3.replacementPlan.0.replacementKind"));
        assertEquals("1,2,3", fields.get("rule.3.replacementPlan.0.coveredNodeIds"));
        assertEquals("4,5,6", fields.get("rule.3.replacementPlan.0.inputNodeIds"));
        assertEquals("true", fields.get("rule.3.replacementPlan.0.complete"));
        assertEquals("rewrite-engine-not-implemented", fields.get("firstBlocker"));
        assertTrue(passReport.toLine().contains("structural rewrite and proof emission are not implemented"));
    }

    @Test
    void diagnosticPeepholePassRunsThirdPartyTypedRuleAndExportsRuleMetadata() {
        GpuRuntimeIrPeepholeRule customRule = new GpuRuntimeIrPeepholeRule() {
            @Override
            public GpuRuntimeIrPeepholeRuleReport analyze(GpuRuntimeIrPeepholeRuleContext context) {
                return GpuRuntimeIrPeepholeRuleReport.diagnosticCandidates(
                        this,
                        context.methodBody().name(),
                        2,
                        Map.of("family", "custom")
                );
            }

            @Override
            public String ruleId() {
                return "customRule";
            }

            @Override
            public String ruleVersion() {
                return "custom-rule-v3";
            }

            @Override
            public String extensionId() {
                return "test.peephole.custom";
            }
        };
        GpuRuntimeIrPeepholePass pass = new GpuRuntimeIrPeepholePass(
                GpuRuntimeIrPeepholeRuleRegistry.of(List.of(customRule))
        );
        GpuRuntimeIrOptimizationReport report = GpuRuntimeIrOptimizerRegistry.ofPasses(List.of(pass))
                .optimizeWithReport(request(fastMathTypedArtifact()));

        Map<String, String> fields = report.passReports().get(0).proofArtifact().fields();
        assertEquals("2", fields.get("candidate.count"));
        assertEquals("customRule", fields.get("rule.0.id"));
        assertEquals("custom-rule-v3", fields.get("rule.0.version"));
        assertEquals("test.peephole.custom", fields.get("rule.0.extensionId"));
        assertEquals("custom-rule-v3", fields.get("rule.0.extensionVersion"));
        assertEquals("2", fields.get("rule.0.candidate.count"));
        assertEquals("1", fields.get("rule.execution.count"));
    }

    @Test
    void diagnosticPeepholePassRejectsStructurallyInvalidReplacementPlan() {
        GpuRuntimeIrPeepholeRule invalidPlanRule = new GpuRuntimeIrPeepholeRule() {
            @Override
            public GpuRuntimeIrPeepholeRuleReport analyze(GpuRuntimeIrPeepholeRuleContext context) {
                return GpuRuntimeIrPeepholeRuleReport.diagnosticCandidates(
                        this,
                        context.methodBody().name(),
                        1,
                        Map.of("family", "invalid-plan-test"),
                        List.of(GpuRuntimeIrPeepholeReplacementPlan.complete(
                                ruleId(),
                                context.methodBody().name(),
                                999,
                                "invalid-test",
                                List.of(999),
                                List.of(1)
                        ))
                );
            }

            @Override
            public String ruleId() {
                return "invalidPlanRule";
            }

            @Override
            public String extensionId() {
                return "test.peephole.invalid-plan";
            }
        };
        GpuRuntimeIrPeepholePass pass = new GpuRuntimeIrPeepholePass(
                GpuRuntimeIrPeepholeRuleRegistry.of(List.of(invalidPlanRule))
        );
        IrGpuArtifact artifact = fastMathTypedArtifact();

        GpuRuntimeIrOptimizationReport report = GpuRuntimeIrOptimizerRegistry.ofPasses(List.of(pass))
                .optimizeWithReport(request(artifact));

        assertSame(artifact, report.artifact().orElseThrow());
        GpuRuntimeIrOptimizationPassReport passReport = report.passReports().get(0);
        Map<String, String> fields = passReport.proofArtifact().fields();
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, passReport.outcome());
        assertEquals("1", fields.get("candidate.count"));
        assertEquals("1", fields.get("replacementPlan.validation.invalid.count"));
        assertEquals("replacement-plan-root-missing", fields.get("replacementPlan.validation.firstBlocker"));
        assertEquals("1", fields.get("rewriteSketch.count"));
        assertEquals("1", fields.get("rewriteVisitor.count"));
        assertEquals("0", fields.get("rewriteVisitor.ready.count"));
        assertEquals("1", fields.get("rewriteVisitor.blocked.count"));
        assertEquals("replacement-plan-root-missing", fields.get("rewriteVisitor.firstBlocker"));
        assertEquals("blocked", fields.get("rule.0.rewriteVisitor.0.status"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.rewriteVisitor.0.firstBlocker"));
        assertEquals("false", fields.get("rule.0.rewriteVisitor.0.rootVisitable"));
        assertEquals("5", fields.get("rule.0.rewriteVisitor.0.graphNodeMaxId"));
        assertEquals("false", fields.get("rule.0.rewriteVisitor.0.visitorReady"));
        assertEquals("false", fields.get("rule.0.rewriteVisitor.0.selectedIrReplacement"));
        assertEquals("true", fields.get("rewriteVisitor.visitorImplemented"));
        assertEquals("false", fields.get("rewriteVisitor.replacementBuilderImplemented"));
        assertEquals("1", fields.get("replacementBlueprint.count"));
        assertEquals("0", fields.get("replacementBlueprint.ready.count"));
        assertEquals("1", fields.get("replacementBlueprint.blocked.count"));
        assertEquals("replacement-plan-root-missing", fields.get("replacementBlueprint.firstBlocker"));
        assertEquals("blocked", fields.get("rule.0.replacementBlueprint.0.status"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.replacementBlueprint.0.firstBlocker"));
        assertEquals("false", fields.get("rule.0.replacementBlueprint.0.blueprintReady"));
        assertEquals("false", fields.get("rule.0.replacementBlueprint.0.selectedIrReplacement"));
        assertEquals("false", fields.get("replacementBlueprint.replacementBuilderImplemented"));
        assertEquals("1", fields.get("rewriteTransaction.count"));
        assertEquals("0", fields.get("rewriteTransaction.ready.count"));
        assertEquals("1", fields.get("rewriteTransaction.blocked.count"));
        assertEquals("replacement-plan-root-missing", fields.get("rewriteTransaction.firstBlocker"));
        assertEquals("blocked", fields.get("rule.0.rewriteTransaction.0.status"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.rewriteTransaction.0.firstBlocker"));
        assertEquals("false", fields.get("rule.0.rewriteTransaction.0.transactionReady"));
        assertEquals("0", fields.get("rule.0.rewriteTransaction.0.plannedAddedNode.count"));
        assertEquals("none", fields.get("rule.0.rewriteTransaction.0.plannedAddedNodeIds"));
        assertEquals("false", fields.get("rewriteTransaction.nodeIdAllocatorImplemented"));
        assertEquals("false", fields.get("rewriteTransaction.graphRewriteImplemented"));
        assertEquals("false", fields.get("rewriteTransaction.selectedIrReplacement"));
        assertEquals("1", fields.get("nodeIdAllocation.count"));
        assertEquals("0", fields.get("nodeIdAllocation.ready.count"));
        assertEquals("1", fields.get("nodeIdAllocation.blocked.count"));
        assertEquals("replacement-plan-root-missing", fields.get("nodeIdAllocation.firstBlocker"));
        assertEquals("blocked", fields.get("rule.0.nodeIdAllocation.0.status"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.nodeIdAllocation.0.firstBlocker"));
        assertEquals("none", fields.get("rule.0.nodeIdAllocation.0.candidateNodeIds"));
        assertEquals("false", fields.get("rule.0.nodeIdAllocation.0.allocationReady"));
        assertEquals("false", fields.get("rule.0.nodeIdAllocation.0.nodeIdsReserved"));
        assertEquals("false", fields.get("nodeIdAllocation.nodeIdAllocatorApplied"));
        assertEquals("false", fields.get("nodeIdAllocation.graphRewriteImplemented"));
        assertEquals("1", fields.get("replacementNode.count"));
        assertEquals("0", fields.get("replacementNode.ready.count"));
        assertEquals("1", fields.get("replacementNode.blocked.count"));
        assertEquals("replacement-plan-root-missing", fields.get("replacementNode.firstBlocker"));
        assertEquals("blocked", fields.get("rule.0.replacementNode.0.status"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.replacementNode.0.firstBlocker"));
        assertEquals("none", fields.get("rule.0.replacementNode.0.candidateReplacementNodeId"));
        assertEquals("false", fields.get("rule.0.replacementNode.0.replacementNodeReady"));
        assertEquals("false", fields.get("rule.0.replacementNode.0.replacementNodeBuilt"));
        assertEquals("false", fields.get("replacementNode.replacementBuilderImplemented"));
        assertEquals("false", fields.get("replacementNode.graphRewriteImplemented"));
        assertEquals("1", fields.get("graphPatch.count"));
        assertEquals("0", fields.get("graphPatch.ready.count"));
        assertEquals("1", fields.get("graphPatch.blocked.count"));
        assertEquals("replacement-plan-root-missing", fields.get("graphPatch.firstBlocker"));
        assertEquals("blocked", fields.get("rule.0.graphPatch.0.status"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.graphPatch.0.firstBlocker"));
        assertEquals("none", fields.get("rule.0.graphPatch.0.insertedNodeIds"));
        assertEquals("false", fields.get("rule.0.graphPatch.0.graphPatchReady"));
        assertEquals("false", fields.get("rule.0.graphPatch.0.graphPatchApplied"));
        assertEquals("false", fields.get("graphPatch.graphRewriteImplemented"));
        assertEquals("1", fields.get("transformedGraph.count"));
        assertEquals("0", fields.get("transformedGraph.ready.count"));
        assertEquals("1", fields.get("transformedGraph.blocked.count"));
        assertEquals("replacement-plan-root-missing", fields.get("transformedGraph.firstBlocker"));
        assertEquals("blocked", fields.get("rule.0.transformedGraph.0.status"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.transformedGraph.0.firstBlocker"));
        assertEquals("not-built", fields.get("rule.0.transformedGraph.0.transformedGraphIdentity"));
        assertEquals("false", fields.get("rule.0.transformedGraph.0.materializationReady"));
        assertEquals("false", fields.get("rule.0.transformedGraph.0.transformedGraphBuilt"));
        assertEquals("false", fields.get("transformedGraph.graphPatchApplied"));
        assertEquals("false", fields.get("transformedGraph.graphRewriteImplemented"));
        assertEquals("1", fields.get("irArtifactEnvelope.count"));
        assertEquals("0", fields.get("irArtifactEnvelope.ready.count"));
        assertEquals("1", fields.get("irArtifactEnvelope.blocked.count"));
        assertEquals("replacement-plan-root-missing", fields.get("irArtifactEnvelope.firstBlocker"));
        assertEquals("blocked", fields.get("rule.0.irArtifactEnvelope.0.status"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.irArtifactEnvelope.0.firstBlocker"));
        assertEquals("not-built", fields.get("rule.0.irArtifactEnvelope.0.optimizedArtifactIdentity"));
        assertEquals("false", fields.get("rule.0.irArtifactEnvelope.0.artifactEnvelopeReady"));
        assertEquals("false", fields.get("rule.0.irArtifactEnvelope.0.artifactEnvelopeBuilt"));
        assertEquals("false", fields.get("irArtifactEnvelope.optimizedArtifactBuilt"));
        assertEquals("false", fields.get("irArtifactEnvelope.selectedIrReplacement"));
        assertEquals("1", fields.get("artifactProofBinding.count"));
        assertEquals("0", fields.get("artifactProofBinding.ready.count"));
        assertEquals("1", fields.get("artifactProofBinding.blocked.count"));
        assertEquals("replacement-plan-root-missing", fields.get("artifactProofBinding.firstBlocker"));
        assertEquals("blocked", fields.get("rule.0.artifactProofBinding.0.status"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.artifactProofBinding.0.firstBlocker"));
        assertEquals("false", fields.get("rule.0.artifactProofBinding.0.artifactEnvelopeReady"));
        assertEquals("not-required", fields.get("rule.0.artifactProofBinding.0.proofStatus"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.artifactProofBinding.0.proofFirstBlocker"));
        assertEquals("not-required", fields.get("rule.0.artifactProofBinding.0.reviewPackageStatus"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.artifactProofBinding.0.reviewPackageFirstBlocker"));
        assertEquals("false", fields.get("rule.0.artifactProofBinding.0.proofRequired"));
        assertEquals("false", fields.get("rule.0.artifactProofBinding.0.bindingReady"));
        assertEquals("false", fields.get("rule.0.artifactProofBinding.0.proofBound"));
        assertEquals("false", fields.get("rule.0.artifactProofBinding.0.optimizedArtifactBuilt"));
        assertEquals("false", fields.get("rule.0.artifactProofBinding.0.transformedIrBuilt"));
        assertEquals("false", fields.get("artifactProofBinding.optimizedArtifactBuilt"));
        assertEquals("false", fields.get("artifactProofBinding.selectedIrReplacement"));
        assertEquals("1", fields.get("artifactSelection.count"));
        assertEquals("0", fields.get("artifactSelection.ready.count"));
        assertEquals("1", fields.get("artifactSelection.blocked.count"));
        assertEquals("replacement-plan-root-missing", fields.get("artifactSelection.firstBlocker"));
        assertEquals("blocked", fields.get("rule.0.artifactSelection.0.status"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.artifactSelection.0.firstBlocker"));
        assertEquals("blocked", fields.get("rule.0.artifactSelection.0.proofBindingStatus"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.artifactSelection.0.proofBindingFirstBlocker"));
        assertEquals("false", fields.get("rule.0.artifactSelection.0.proofBindingReady"));
        assertEquals("false", fields.get("rule.0.artifactSelection.0.optimizedArtifactBuilt"));
        assertEquals("false", fields.get("rule.0.artifactSelection.0.transformedIrBuilt"));
        assertEquals("true", fields.get("rule.0.artifactSelection.0.productionGateRequired"));
        assertEquals("false", fields.get("rule.0.artifactSelection.0.productionGateAccepted"));
        assertEquals("false", fields.get("rule.0.artifactSelection.0.mutationPolicyAllowed"));
        assertEquals("false", fields.get("rule.0.artifactSelection.0.selectionReady"));
        assertEquals("false", fields.get("artifactSelection.selectionApplied"));
        assertEquals("false", fields.get("artifactSelection.optimizedArtifactSelected"));
        assertEquals("false", fields.get("artifactSelection.selectedIrReplacement"));
        assertEquals("0", fields.get("rewriteSketch.ready.count"));
        assertEquals("1", fields.get("rewriteSketch.blocked.count"));
        assertEquals("replacement-plan-root-missing", fields.get("rewriteSketch.firstBlocker"));
        assertEquals("blocked", fields.get("rule.0.rewriteSketch.0.status"));
        assertEquals("true", fields.get("rule.0.rewriteSketch.0.planComplete"));
        assertEquals("false", fields.get("rule.0.rewriteSketch.0.planValid"));
        assertEquals("false", fields.get("rule.0.rewriteSketch.0.sketchReady"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.rewriteSketch.0.firstBlocker"));
        assertEquals("false", fields.get("rule.0.rewriteSketch.0.mutationAllowed"));
        assertEquals("false", fields.get("rule.0.rewriteSketch.0.selectedIrReplacement"));
        assertEquals("blocked", fields.get("rewriteSelection.status"));
        assertEquals("replacement-plan-root-missing", fields.get("rewriteSelection.firstBlocker"));
        assertEquals("1", fields.get("rewriteSelection.sketch.count"));
        assertEquals("0", fields.get("rewriteSelection.sketch.ready.count"));
        assertEquals("1", fields.get("rewriteSelection.sketch.blocked.count"));
        assertEquals("0", fields.get("rewriteSelection.conflict.count"));
        assertEquals("false", fields.get("rewriteSelection.runtimeEquivalenceRequired"));
        assertEquals("false", fields.get("rewriteSelection.approvalRequired"));
        assertEquals("false", fields.get("rewriteSelection.selectedIrReplacement"));
        assertEquals("not-required", fields.get("rewriteProof.status"));
        assertEquals("replacement-plan-root-missing", fields.get("rewriteProof.firstBlocker"));
        assertEquals("false", fields.get("rewriteProof.proofRequired"));
        assertEquals("false", fields.get("rewriteProof.runtimeEquivalenceRequired"));
        assertEquals("false", fields.get("rewriteProof.rollbackRequired"));
        assertEquals("false", fields.get("rewriteProof.selectedIrReplacement"));
        assertEquals("not-required", fields.get("rewriteReviewPackage.status"));
        assertEquals("replacement-plan-root-missing", fields.get("rewriteReviewPackage.firstBlocker"));
        assertEquals("false", fields.get("rewriteReviewPackage.required"));
        assertEquals("false", fields.get("rewriteReviewPackage.complete"));
        assertEquals("blocked", fields.get("rule.0.rewriteSelection.status"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.rewriteSelection.firstBlocker"));
        assertEquals("not-required", fields.get("rule.0.rewriteProof.status"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.rewriteProof.firstBlocker"));
        assertEquals("false", fields.get("rule.0.rewriteProof.proofAccepted"));
        assertEquals("false", fields.get("rule.0.rewriteProof.selectedIrReplacement"));
        assertEquals("not-required", fields.get("rule.0.rewriteReviewPackage.status"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.rewriteReviewPackage.firstBlocker"));
        assertEquals("false", fields.get("rule.0.rewriteReviewPackage.complete"));
        assertEquals("false", fields.get("rule.0.rewriteReviewPackage.selectedIrReplacement"));
        assertEquals("replacement-plan-root-missing", fields.get("firstBlocker"));
        assertEquals("replacement-plan-root-missing", fields.get("rule.0.firstBlocker"));
        assertEquals("1", fields.get("rule.0.replacementPlan.validation.count"));
        assertEquals("0", fields.get("rule.0.replacementPlan.validation.valid.count"));
        assertEquals("1", fields.get("rule.0.replacementPlan.validation.invalid.count"));
        assertEquals("false", fields.get("rule.0.replacementPlan.validation.0.valid"));
        assertEquals("false", fields.get("rule.0.replacementPlan.validation.0.rootExists"));
        assertEquals("true", fields.get("rule.0.replacementPlan.validation.0.coveredIncludesRoot"));
        assertEquals("999", fields.get("rule.0.replacementPlan.validation.0.missingCoveredNodeIds"));
        assertEquals("none", fields.get("rule.0.replacementPlan.validation.0.missingInputNodeIds"));
        assertTrue(passReport.toLine().contains("structural validation"));
    }

    @Test
    void advisoryPeepholeRuleFailureIsIsolatedAndFollowingRuleStillRuns() {
        GpuRuntimeIrPeepholeRule failing = new GpuRuntimeIrPeepholeRule() {
            @Override
            public GpuRuntimeIrPeepholeRuleReport analyze(GpuRuntimeIrPeepholeRuleContext context) {
                throw new IllegalStateException("custom rule exploded");
            }

            @Override
            public String ruleId() {
                return "failingRule";
            }

            @Override
            public String extensionId() {
                return "test.peephole.failing";
            }
        };
        GpuRuntimeIrPeepholeRule following = new GpuRuntimeIrPeepholeRule() {
            @Override
            public GpuRuntimeIrPeepholeRuleReport analyze(GpuRuntimeIrPeepholeRuleContext context) {
                return GpuRuntimeIrPeepholeRuleReport.diagnosticCandidates(
                        this,
                        context.methodBody().name(),
                        1,
                        Map.of()
                );
            }

            @Override
            public String ruleId() {
                return "followingRule";
            }

            @Override
            public String extensionId() {
                return "test.peephole.following";
            }
        };
        GpuRuntimeIrPeepholePass pass = new GpuRuntimeIrPeepholePass(
                GpuRuntimeIrPeepholeRuleRegistry.of(List.of(failing, following))
        );

        GpuRuntimeIrOptimizationReport report = GpuRuntimeIrOptimizerRegistry.ofPasses(List.of(pass))
                .optimizeWithReport(request(fastMathTypedArtifact()));

        Map<String, String> fields = report.passReports().get(0).proofArtifact().fields();
        assertEquals("1", fields.get("candidate.count"));
        assertEquals("1", fields.get("rule.execution.failedContinued.count"));
        assertEquals("rule-execution-failed-continued", fields.get("rule.0.proofStatus"));
        assertEquals("1", fields.get("rule.1.candidate.count"));
        assertEquals("rewrite-engine-not-implemented", fields.get("firstBlocker"));
    }

    @Test
    void peepholeRuleRegistryRejectsDuplicateRuleIds() {
        GpuRuntimeIrPeepholeRule first = peepholeRule("duplicate", "test.peephole.first");
        GpuRuntimeIrPeepholeRule second = peepholeRule("duplicate", "test.peephole.second");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GpuRuntimeIrPeepholeRuleRegistry.of(List.of(first, second))
        );

        assertTrue(exception.getMessage().contains("Duplicate peephole rule id 'duplicate'"));
    }

    @Test
    void optimizationReportCarriesAdvisoryStrategyDecision() {
        IrGpuArtifact original = artifact("body\n  return original\n");
        GpuOptimizationStrategyDecision decision = GpuOptimizationStrategyDecision.advisory(
                "strategy:opencl-nvidia-advisory",
                "nvidia",
                "off",
                "NVIDIA remains scalar-safe until evidence-backed",
                List.of("strategy is diagnostic-only")
        );
        GpuRuntimeIrOptimizationRequest request = new GpuRuntimeIrOptimizationRequest(
                request(original).compileRequest(),
                Optional.of(original),
                decision
        );

        GpuRuntimeIrOptimizationReport report = GpuRuntimeIrOptimizerRegistry.noOp().optimizeWithReport(request);

        assertEquals(decision, report.strategyDecision());
        assertTrue(report.toText().contains("strategy:opencl-nvidia-advisory"));
        assertTrue(report.toText().contains("advisoryOnly=true"));
    }

    @Test
    void optimizationRequestExposesFastMathPolicyFromIrGpuArtifact() {
        IrGpuArtifact fastMathArtifact = new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry("kernel", "jtg_kernel", "body\n  return original\n", List.of()))
                ),
                List.of(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuOptimizerPolicyMetadata.fromGpuOptimize(true),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );

        GpuRuntimeIrOptimizationRequest request = request(fastMathArtifact);
        GpuRuntimeIrOptimizationRequest missingArtifactRequest = new GpuRuntimeIrOptimizationRequest(
                request.compileRequest().withIrGpuArtifact(Optional.empty()),
                Optional.empty()
        );

        assertTrue(request.fastMathEnabled());
        assertEquals("GPUOptimize", request.optimizerPolicy().source());
        assertFalse(missingArtifactRequest.fastMathEnabled());
        assertEquals("default-strict", missingArtifactRequest.optimizerPolicy().source());
    }

    @Test
    void fastMathOptimizerIsSkippedUntilMethodPolicyOptsIn() {
        IrGpuArtifact strictArtifact = artifact("body\n  return strict\n");
        IrGpuArtifact optimizedArtifact = artifact("body\n  return optimized\n");
        AtomicBoolean invoked = new AtomicBoolean();
        GpuRuntimeIrOptimizer optimizer = new GpuRuntimeIrOptimizer() {
            @Override
            public Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request) {
                invoked.set(true);
                return Optional.of(optimizedArtifact);
            }

            @Override
            public String optimizerVersion() {
                return "optimizer:fast-math-required";
            }

            @Override
            public boolean requiresFastMath() {
                return true;
            }
        };

        GpuRuntimeIrOptimizationReport report = GpuRuntimeIrOptimizerRegistry.of(List.of(optimizer))
                .optimizeWithReport(request(strictArtifact));

        assertFalse(invoked.get());
        assertSame(strictArtifact, report.artifact().orElseThrow());
        assertEquals(1, report.passReports().size());
        assertEquals(GpuRuntimeIrOptimizationOutcome.SKIPPED, report.passReports().get(0).outcome());
        assertTrue(report.passReports().get(0).toLine().contains("fast-math policy disabled"));
        assertTrue(report.passReports().get(0).toLine().contains("policySource=default-strict"));
    }

    @Test
    void fastMathOptimizerRunsWhenMethodPolicyOptsIn() {
        IrGpuArtifact fastMathArtifact = fastMathArtifact("body\n  return original\n");
        IrGpuArtifact optimizedArtifact = fastMathArtifact("body\n  return optimized\n");
        AtomicBoolean invoked = new AtomicBoolean();
        GpuRuntimeIrOptimizer optimizer = new GpuRuntimeIrOptimizer() {
            @Override
            public Optional<IrGpuArtifact> optimize(GpuRuntimeIrOptimizationRequest request) {
                invoked.set(true);
                return Optional.of(optimizedArtifact);
            }

            @Override
            public String optimizerVersion() {
                return "optimizer:fast-math-enabled";
            }

            @Override
            public boolean requiresFastMath() {
                return true;
            }
        };

        GpuRuntimeIrOptimizationReport report = GpuRuntimeIrOptimizerRegistry.of(List.of(optimizer))
                .optimizeWithReport(request(fastMathArtifact));

        assertTrue(invoked.get());
        assertSame(optimizedArtifact, report.artifact().orElseThrow());
        assertEquals(GpuRuntimeIrOptimizationOutcome.APPLIED, report.passReports().get(0).outcome());
    }

    @Test
    void defaultVendorStrategiesStayAdvisoryUntilEvidenceBacked() {
        assertAdvisoryVendorStrategy(
                "NVIDIA Corporation",
                "strategy:opencl-nvidia-advisory",
                "nvidia"
        );
        assertAdvisoryVendorStrategy(
                "Advanced Micro Devices",
                "strategy:opencl-amd-advisory",
                "amd"
        );
        assertAdvisoryVendorStrategy(
                "Intel(R) Corporation",
                "strategy:opencl-intel-advisory",
                "intel"
        );
    }

    private static void assertAdvisoryVendorStrategy(
            String vendor,
            String expectedStrategyName,
            String expectedDeviceFamily
    ) {
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor(),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "vendor-tuned"),
                GpuRuntimeDeviceProfile.openCl(
                        "OpenCL",
                        vendor + " GPU",
                        vendor,
                        "mock-driver",
                        "OpenCL 3.0 Mock",
                        64L,
                        65_536L,
                        512L,
                        1L,
                        true,
                        true,
                        false
                ),
                Optional.of(artifact("body\n  return original\n"))
        );

        GpuOptimizationStrategyDecision decision = GpuOptimizationStrategy.advisoryDefault().select(compileRequest);

        assertEquals(expectedStrategyName, decision.strategyName());
        assertEquals(expectedDeviceFamily, decision.deviceFamily());
        assertEquals("vendor-tuned", decision.selectedProfile());
        assertTrue(decision.advisoryOnly());
        assertFalse(decision.evidenceBacked());
    }

    @Test
    void defaultVendorStrategiesExposePerVendorBaselineState() {
        GpuOptimizationStrategyDecision nvidia = vendorDecision("NVIDIA Corporation");
        GpuOptimizationStrategyDecision amd = vendorDecision("Advanced Micro Devices");
        GpuOptimizationStrategyDecision intel = vendorDecision("Intel(R) Corporation");

        assertTrue(nvidia.vendorBaseline().recorded());
        assertEquals("recorded-nvidia-only", nvidia.vendorBaseline().status());
        assertFalse(nvidia.vendorBaseline().promotionEligible());
        assertTrue(nvidia.toLine().contains("docs-project-plan/nvidia-rtx5070-baselines.md"));
        assertFalse(amd.vendorBaseline().recorded());
        assertEquals("pending-hardware", amd.vendorBaseline().status());
        assertFalse(amd.vendorBaseline().promotionEligible());
        assertFalse(intel.vendorBaseline().recorded());
        assertEquals("pending-hardware", intel.vendorBaseline().status());
        assertFalse(intel.vendorBaseline().promotionEligible());
    }

    private static GpuOptimizationStrategyDecision vendorDecision(String vendor) {
        return GpuOptimizationStrategy.advisoryDefault().select(new GpuRuntimeCompileRequest(
                descriptor(),
                new GpuRuntimeCompileOptions(GpuBackendTarget.OPENCL, List.of(), "vendor-tuned"),
                GpuRuntimeDeviceProfile.openCl(
                        "OpenCL",
                        vendor + " GPU",
                        vendor,
                        "mock-driver",
                        "OpenCL 3.0 Mock",
                        64L,
                        65_536L,
                        512L,
                        1L,
                        true,
                        true,
                        false
                ),
                Optional.of(artifact("body\n  return original\n"))
        ));
    }

    private static GpuRuntimeIrOptimizationRequest request(IrGpuArtifact artifact) {
        return new GpuRuntimeIrOptimizationRequest(
                new GpuRuntimeCompileRequest(
                        descriptor(),
                        GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL),
                        GpuRuntimeDeviceProfile.generic(GpuBackendTarget.OPENCL, "OpenCL"),
                        Optional.of(artifact)
                ),
                Optional.of(artifact)
        );
    }

    private static void assertNoProductionEnablingPreflightFields(Map<String, String> fields) {
        List<String> forbiddenTrueSuffixes = List.of(
                ".replacementBuilderImplemented",
                ".nodeIdsReserved",
                ".nodeIdAllocatorApplied",
                ".replacementNodeBuilt",
                ".graphPatchApplied",
                ".graphRewriteImplemented",
                ".transformedGraphBuilt",
                ".artifactEnvelopeBuilt",
                ".optimizedArtifactBuilt",
                ".transformedIrBuilt",
                ".rewriteBuilderImplemented",
                ".conflictResolutionImplemented",
                ".runtimeEquivalenceProven",
                ".proofAccepted",
                ".runtimeEquivalencePayload.present",
                ".runtimeEquivalencePayload.complete",
                ".rollbackEvidence.present",
                ".rollbackClean",
                ".approvalAccepted",
                ".reviewAccepted",
                ".proofBound",
                ".rollbackBound",
                ".approvalBound",
                ".bindingReady",
                ".productionGateAccepted",
                ".mutationPolicyAllowed",
                ".selectionApplied",
                ".optimizedArtifactSelected",
                ".selectionReady",
                ".mutationAllowed",
                ".selectedIrReplacement"
        );
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            boolean forbidden = forbiddenTrueSuffixes.stream().anyMatch(entry.getKey()::endsWith);
            assertFalse(forbidden && "true".equals(entry.getValue()),
                    () -> "production-enabling preflight field unexpectedly true: " + entry.getKey());
        }
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

    private static GpuRuntimeIrOptimizationPass extensionPass(String id, String version, int order) {
        return extensionPass(id, version, order, GpuExtensionPermission.MUTATION_PROPOSAL);
    }

    private static GpuRuntimeIrPeepholeRule peepholeRule(String ruleId, String extensionId) {
        return new GpuRuntimeIrPeepholeRule() {
            @Override
            public GpuRuntimeIrPeepholeRuleReport analyze(GpuRuntimeIrPeepholeRuleContext context) {
                return GpuRuntimeIrPeepholeRuleReport.diagnosticCandidates(
                        this,
                        context.methodBody().name(),
                        0,
                        Map.of()
                );
            }

            @Override
            public String ruleId() {
                return ruleId;
            }

            @Override
            public String extensionId() {
                return extensionId;
            }
        };
    }

    private static GpuRuntimeIrOptimizationPass extensionPass(
            String id,
            String version,
            int order,
            GpuExtensionPermission permission
    ) {
        return extensionPass(id, version, order, permission, new AtomicBoolean());
    }

    private static GpuRuntimeIrOptimizationPass extensionPass(
            String id,
            String version,
            int order,
            GpuExtensionPermission permission,
            AtomicBoolean invoked
    ) {
        return new GpuRuntimeIrOptimizationPass() {
            @Override
            public GpuRuntimeIrOptimizationReport run(GpuRuntimeIrOptimizationRequest request) {
                invoked.set(true);
                return GpuRuntimeIrOptimizationReport.empty(request.artifact());
            }

            @Override
            public String passName() {
                return id;
            }

            @Override
            public String passVersion() {
                return version;
            }

            @Override
            public int extensionOrder() {
                return order;
            }

            @Override
            public GpuExtensionPermission extensionPermission() {
                return permission;
            }
        };
    }

    private static GpuRuntimeIrOptimizationRequest productionRequest(
            IrGpuArtifact artifact,
            GpuProductionPromotionDecision promotionDecision
    ) {
        GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions.openClProductionIrGpuSource(
                        List.of(),
                        "production"
                )
                .withProductionPromotionDecision(promotionDecision)
                .withProductionPromotionOperatorAccepted(true);
        GpuRuntimeCompileRequest compileRequest = new GpuRuntimeCompileRequest(
                descriptor(),
                options,
                GpuRuntimeDeviceProfile.openCl(
                        "OpenCL",
                        "Authorization GPU",
                        "nvidia",
                        "test-driver",
                        "OpenCL 3.0 Test",
                        64L,
                        65_536L,
                        512L,
                        1L,
                        true,
                        true,
                        false
                ),
                Optional.of(artifact)
        );
        GpuOptimizationStrategyDecision strategyDecision = new GpuOptimizationStrategyDecision(
                "strategy:production-authorization-test",
                "nvidia",
                "production",
                false,
                true,
                "test strategy has accepted evidence",
                new GpuOptimizationVendorBaseline(
                        "nvidia",
                        "promotion-eligible-test",
                        true,
                        true,
                        "test",
                        List.of()
                ),
                List.of()
        );
        return new GpuRuntimeIrOptimizationRequest(compileRequest, Optional.of(artifact), strategyDecision);
    }

    private static GpuRuntimeIrOptimizationReport acceptedEvidenceReport(
            IrGpuArtifact artifact,
            GpuOptimizationStrategyDecision strategyDecision
    ) {
        String identity = GpuRuntimeIrOptimizerRegistry.identityOf(Optional.of(artifact));
        GpuRuntimeIrOptimizationPassReport passReport = GpuRuntimeIrOptimizationPassReport.applied(
                "pass:production-authorized",
                identity,
                identity,
                "accepted-runtime-proof",
                List.of("rollback path verified")
        ).withProofArtifact(GpuRuntimeIrOptimizationProofArtifact.fromFields(
                "runtime-equivalence-test",
                "accepted",
                Map.of("runtimeEquivalencePassed", "true")
        ));
        return new GpuRuntimeIrOptimizationReport(Optional.of(artifact), List.of(passReport), strategyDecision);
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
                "production extension promotion accepted for test"
        );
    }

    private static IrGpuArtifact fastMathArtifact(String body) {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry("kernel", "jtg_kernel", body, List.of()))
                ),
                List.of(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuOptimizerPolicyMetadata.fromGpuOptimize(true),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuArtifact fastMathTypedArtifact() {
        IrGpuTypedBody typedBody = new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0),
                List.of(
                        new IrGpuTypedNode(0, "GpuIrReturn", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(2),
                                "right", List.of(5)
                        )),
                        new IrGpuTypedNode(2, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                                "left", List.of(3),
                                "right", List.of(4)
                        )),
                        new IrGpuTypedNode(3, "GpuIrVariableRef", Map.of("name", "a"), Map.of()),
                        new IrGpuTypedNode(4, "GpuIrVariableRef", Map.of("name", "b"), Map.of()),
                        new IrGpuTypedNode(5, "GpuIrVariableRef", Map.of("name", "c"), Map.of())
                )
        );
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(new IrGpuMethodBody(
                                "entry",
                                "kernel",
                                "jtg_kernel",
                                "ir-text-v1",
                                "body\n  return ((a * b) + c)\n",
                                typedBody,
                                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBodyIndex.empty(),
                                List.of(),
                                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation.unknown("kernel")
                        ))
                ),
                List.of(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuOptimizerPolicyMetadata.fromGpuOptimize(true),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuArtifact fastMathPartialMadFmaTypedArtifact() {
        IrGpuTypedBody typedBody = new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0),
                List.of(
                        new IrGpuTypedNode(0, "GpuIrReturn", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(2),
                                "right", List.of(5)
                        )),
                        new IrGpuTypedNode(2, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                                "left", List.of(3)
                        )),
                        new IrGpuTypedNode(3, "GpuIrVariableRef", Map.of("name", "a"), Map.of()),
                        new IrGpuTypedNode(5, "GpuIrVariableRef", Map.of("name", "c"), Map.of())
                )
        );
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(new IrGpuMethodBody(
                                "entry",
                                "kernel",
                                "jtg_kernel",
                                "ir-text-v1",
                                "body\n  return ((a * <missing>) + c)\n",
                                typedBody,
                                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBodyIndex.empty(),
                                List.of(),
                                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation.unknown("kernel")
                        ))
                ),
                List.of(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuOptimizerPolicyMetadata.fromGpuOptimize(true),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuArtifact fastMathClampTypedArtifact() {
        IrGpuTypedBody typedBody = new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0),
                List.of(
                        new IrGpuTypedNode(0, "GpuIrReturn", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrIntrinsicCall", Map.of("name", "min"), Map.of(
                                "args", List.of(2, 5)
                        )),
                        new IrGpuTypedNode(2, "GpuIrIntrinsicCall", Map.of("name", "max"), Map.of(
                                "args", List.of(3, 4)
                        )),
                        new IrGpuTypedNode(3, "GpuIrVariableRef", Map.of("name", "x"), Map.of()),
                        new IrGpuTypedNode(4, "GpuIrVariableRef", Map.of("name", "lo"), Map.of()),
                        new IrGpuTypedNode(5, "GpuIrVariableRef", Map.of("name", "hi"), Map.of())
                )
        );
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(new IrGpuMethodBody(
                                "entry",
                                "kernel",
                                "jtg_kernel",
                                "ir-text-v1",
                                "body\n  return min(max(x, lo), hi)\n",
                                typedBody,
                                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBodyIndex.empty(),
                                List.of(),
                                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation.unknown("kernel")
                        ))
                ),
                List.of(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuOptimizerPolicyMetadata.fromGpuOptimize(true),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuArtifact fastMathStepTypedArtifact() {
        IrGpuTypedBody typedBody = new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0),
                List.of(
                        new IrGpuTypedNode(0, "GpuIrReturn", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrConditional", Map.of(), Map.of(
                                "condition", List.of(2),
                                "then", List.of(5),
                                "else", List.of(6)
                        )),
                        new IrGpuTypedNode(2, "GpuIrBinary", Map.of("operator", "<"), Map.of(
                                "left", List.of(3),
                                "right", List.of(4)
                        )),
                        new IrGpuTypedNode(3, "GpuIrVariableRef", Map.of("name", "x"), Map.of()),
                        new IrGpuTypedNode(4, "GpuIrVariableRef", Map.of("name", "edge"), Map.of()),
                        new IrGpuTypedNode(5, "GpuIrLiteral", Map.of("sourceText", "0.0f"), Map.of()),
                        new IrGpuTypedNode(6, "GpuIrLiteral", Map.of("sourceText", "1.0f"), Map.of())
                )
        );
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(new IrGpuMethodBody(
                                "entry",
                                "kernel",
                                "jtg_kernel",
                                "ir-text-v1",
                                "body\n  return x < edge ? 0.0f : 1.0f\n",
                                typedBody,
                                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBodyIndex.empty(),
                                List.of(),
                                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation.unknown("kernel")
                        ))
                ),
                List.of(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuOptimizerPolicyMetadata.fromGpuOptimize(true),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuArtifact fastMathDotTypedArtifact() {
        IrGpuTypedBody typedBody = new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0),
                List.of(
                        new IrGpuTypedNode(0, "GpuIrReturn", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(2),
                                "right", List.of(5)
                        )),
                        new IrGpuTypedNode(2, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                                "left", List.of(3),
                                "right", List.of(4)
                        )),
                        new IrGpuTypedNode(3, "GpuIrVariableRef", Map.of("name", "a0"), Map.of()),
                        new IrGpuTypedNode(4, "GpuIrVariableRef", Map.of("name", "b0"), Map.of()),
                        new IrGpuTypedNode(5, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                                "left", List.of(6),
                                "right", List.of(7)
                        )),
                        new IrGpuTypedNode(6, "GpuIrVariableRef", Map.of("name", "a1"), Map.of()),
                        new IrGpuTypedNode(7, "GpuIrVariableRef", Map.of("name", "b1"), Map.of())
                )
        );
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(new IrGpuMethodBody(
                                "entry",
                                "kernel",
                                "jtg_kernel",
                                "ir-text-v1",
                                "body\n  return (a0 * b0) + (a1 * b1)\n",
                                typedBody,
                                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBodyIndex.empty(),
                                List.of(),
                                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation.unknown("kernel")
                        ))
                ),
                List.of(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuOptimizerPolicyMetadata.fromGpuOptimize(true),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuArtifact fastMathMixTypedArtifact() {
        IrGpuTypedBody typedBody = new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0),
                List.of(
                        new IrGpuTypedNode(0, "GpuIrReturn", Map.of(), Map.of("value", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(4),
                                "right", List.of(2)
                        )),
                        new IrGpuTypedNode(2, "GpuIrBinary", Map.of("operator", "*"), Map.of(
                                "left", List.of(6),
                                "right", List.of(3)
                        )),
                        new IrGpuTypedNode(3, "GpuIrBinary", Map.of("operator", "-"), Map.of(
                                "left", List.of(5),
                                "right", List.of(4)
                        )),
                        new IrGpuTypedNode(4, "GpuIrVariableRef", Map.of("name", "a"), Map.of()),
                        new IrGpuTypedNode(5, "GpuIrVariableRef", Map.of("name", "b"), Map.of()),
                        new IrGpuTypedNode(6, "GpuIrVariableRef", Map.of("name", "t"), Map.of())
                )
        );
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(new IrGpuMethodBody(
                                "entry",
                                "kernel",
                                "jtg_kernel",
                                "ir-text-v1",
                                "body\n  return a + t * (b - a)\n",
                                typedBody,
                                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBodyIndex.empty(),
                                List.of(),
                                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation.unknown("kernel")
                        ))
                ),
                List.of(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuOptimizerPolicyMetadata.fromGpuOptimize(true),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }

    private static IrGpuArtifact repeatedCseTypedArtifact() {
        IrGpuTypedBody typedBody = new IrGpuTypedBody(
                IrGpuTypedBody.FORMAT,
                List.of(0, 5, 10),
                List.of(
                        new IrGpuTypedNode(0, "GpuIrVariableDeclaration", Map.of("typeName", "int", "name", "first"), Map.of("initializer", List.of(1))),
                        new IrGpuTypedNode(1, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(2),
                                "right", List.of(3)
                        )),
                        new IrGpuTypedNode(2, "GpuIrVariableRef", Map.of("name", "a"), Map.of()),
                        new IrGpuTypedNode(3, "GpuIrVariableRef", Map.of("name", "b"), Map.of()),
                        new IrGpuTypedNode(4, "GpuIrVariableRef", Map.of("name", "first"), Map.of()),
                        new IrGpuTypedNode(5, "GpuIrVariableDeclaration", Map.of("typeName", "int", "name", "second"), Map.of("initializer", List.of(6))),
                        new IrGpuTypedNode(6, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(7),
                                "right", List.of(8)
                        )),
                        new IrGpuTypedNode(7, "GpuIrVariableRef", Map.of("name", "a"), Map.of()),
                        new IrGpuTypedNode(8, "GpuIrVariableRef", Map.of("name", "b"), Map.of()),
                        new IrGpuTypedNode(9, "GpuIrVariableRef", Map.of("name", "second"), Map.of()),
                        new IrGpuTypedNode(10, "GpuIrReturn", Map.of(), Map.of("value", List.of(11))),
                        new IrGpuTypedNode(11, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                                "left", List.of(4),
                                "right", List.of(9)
                        ))
                )
        );
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "jtg_kernel",
                        List.of(),
                        List.of(),
                        List.of(new IrGpuMethodBody(
                                "entry",
                                "kernel",
                                "jtg_kernel",
                                "ir-text-v1",
                                "body\n  var int first = (a + b)\n  var int second = (a + b)\n  return (first + second)\n",
                                typedBody,
                                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBodyIndex.empty(),
                                List.of(),
                                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation.unknown("kernel")
                        ))
                ),
                List.of(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuLaunchMetadata.defaultOneDimensional(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuValidationMetadata.frontendSubset(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuFeatureMetadata.none(),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuOptimizerPolicyMetadata.fromGpuOptimize(true),
                net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata.transitionalIrText(),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }
}
