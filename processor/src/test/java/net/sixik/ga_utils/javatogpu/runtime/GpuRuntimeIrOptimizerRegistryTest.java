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
        assertEquals("rewrite-engine-not-implemented", passReport.proofArtifact().fields().get("firstBlocker"));
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
}
