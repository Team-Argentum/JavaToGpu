package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.canonicalizationReport;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.emptyCanonicalizationReport;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.autoVectorizationPrePostEvidence;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.emptyAutoVectorizationPrePostEvidence;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.fixedWidthLoop;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.reportWithLiteralEvidence;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationRulesTest {
    @Test
    void defaultRegistryPassesForCleanMethodAndExportsStableMetadata() {
        GpuIrOptimizationValidationReport report = validate(new GpuIrMethod("cleanKernel", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1"))),
                new GpuIrReturn(null)
        )));

        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules.defaultRegistry();
        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(report));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals(3, results.size());
        assertEquals("safety.clean", results.get(0).ruleId());
        assertEquals("optimizer.noBlockingDiagnostics", results.get(1).ruleId());
        assertEquals("optimizer.advisoryDiagnostics", results.get(2).ruleId());
        assertTrue(results.get(0).passed());
        assertTrue(results.get(1).passed());
        assertTrue(results.get(2).passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.PASS, results.get(0).status());
        assertFalse(results.get(0).blocking());
        assertEquals("true", fields.get("rulesPassed"));
        assertEquals("pass", fields.get("rulesResult.0.Status"));
        assertEquals("false", fields.get("rulesResult.0.Blocking"));
        assertEquals("cleanKernel", fields.get("rulesResult.0.Metadata.method"));
        assertEquals("false", fields.get("rulesResult.0.Metadata.hasSafetyError"));
        assertEquals("false", fields.get("rulesResult.1.Metadata.hasOptimizerDiagnostics"));
        assertEquals("0", fields.get("rulesResult.1.Metadata.optimizerDiagnostics"));
        assertEquals("false", fields.get("rulesResult.1.Metadata.optimizerGateBlocked"));
        assertEquals("none", fields.get("rulesResult.1.Metadata.optimizerGateSource"));
        assertEquals("none", fields.get("rulesResult.1.Metadata.optimizerGateFamily"));
        assertEquals("pass", fields.get("rulesResult.2.Status"));
        assertEquals("false", fields.get("rulesResult.2.Blocking"));
        assertEquals("false", fields.get("rulesResult.2.Metadata.hasAdvisoryOptimizerSignals"));
    }

    @Test
    void safetyCleanFailsOnlyOnSafetyError() {
        GpuIrOptimizationValidationReport report = validate(new GpuIrMethod("brokenKernel", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrVariableRef("missing"))
        )));

        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules.defaultRegistry();
        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(report));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals("safety.clean", results.get(0).ruleId());
        assertFalse(results.get(0).passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.FAIL, results.get(0).status());
        assertTrue(results.get(0).blocking());
        assertEquals("optimizer.noBlockingDiagnostics", results.get(1).ruleId());
        assertTrue(results.get(1).passed());
        assertEquals("optimizer.advisoryDiagnostics", results.get(2).ruleId());
        assertTrue(results.get(2).passed());
        assertEquals("false", fields.get("rulesPassed"));
        assertEquals("true", fields.get("rulesResult.0.Metadata.hasSafetyError"));
        assertTrue(fields.get("rulesResult.0.Metadata.safetyError").contains("unknown variable reference: missing"));
        assertEquals("false", fields.get("rulesResult.1.Metadata.hasOptimizerDiagnostics"));
        assertEquals("safety", fields.get("rulesResult.1.Metadata.optimizerGateSource"));
        assertEquals("safety.error", fields.get("rulesResult.1.Metadata.optimizerGateFamily"));
        assertEquals("pass", fields.get("rulesResult.2.Status"));
        assertEquals("false", fields.get("rulesResult.2.Metadata.hasAdvisoryOptimizerSignals"));
    }

    @Test
    void optimizerNoBlockingDiagnosticsFailsOnOptimizerGateWithoutTreatingSafetyAsOptimizerFailure() {
        GpuIrOptimizationValidationReport report = validate(new GpuIrMethod("vectorBlockedKernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                ))),
                new GpuIrReturn(null)
        )));

        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules.defaultRegistry();
        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(report));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals("safety.clean", results.get(0).ruleId());
        assertTrue(results.get(0).passed());
        assertEquals("optimizer.noBlockingDiagnostics", results.get(1).ruleId());
        assertFalse(results.get(1).passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.FAIL, results.get(1).status());
        assertTrue(results.get(1).blocking());
        assertEquals("optimizer.advisoryDiagnostics", results.get(2).ruleId());
        assertTrue(results.get(2).passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.PASS, results.get(2).status());
        assertEquals("true", fields.get("rulesResult.1.Metadata.hasOptimizerDiagnostics"));
        assertEquals("fail", fields.get("rulesResult.1.Status"));
        assertEquals("true", fields.get("rulesResult.1.Blocking"));
        assertEquals("1", fields.get("rulesResult.1.Metadata.optimizerDiagnostics"));
        assertEquals("true", fields.get("rulesResult.1.Metadata.optimizerGateBlocked"));
        assertEquals("autoVectorization", fields.get("rulesResult.1.Metadata.optimizerGateSource"));
        assertEquals("guard.earlyExitBoundary", fields.get("rulesResult.1.Metadata.optimizerGateFamily"));
        assertEquals("{autoVectorization=1}", fields.get("rulesResult.1.Metadata.optimizerGateSourceCounts"));
        assertEquals("{guard.earlyExitBoundary=1}", fields.get("rulesResult.1.Metadata.optimizerGateFamilyCounts"));
        assertEquals("pass", fields.get("rulesResult.2.Status"));
        assertEquals("false", fields.get("rulesResult.2.Metadata.hasAdvisoryOptimizerSignals"));
    }

    @Test
    void optimizerAdvisoryDiagnosticsWarnsForNonBlockingOptimizerOpportunities() {
        GpuIrOptimizationValidationReport report = validate(new GpuIrMethod("cseOpportunityKernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("x"))),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("x"))),
                new GpuIrReturn(null)
        )));

        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules.defaultRegistry();
        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(report));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals("optimizer.advisoryDiagnostics", results.get(2).ruleId());
        assertTrue(results.get(2).passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.WARN, results.get(2).status());
        assertFalse(results.get(2).blocking());
        assertEquals("true", fields.get("rulesPassed"));
        assertEquals("warn", fields.get("rulesResult.2.Status"));
        assertEquals("false", fields.get("rulesResult.2.Blocking"));
        assertEquals("true", fields.get("rulesResult.2.Metadata.hasAdvisoryOptimizerSignals"));
        assertEquals("1", fields.get("rulesResult.2.Metadata.cseInsertions"));
        assertEquals("1", fields.get("rulesResult.2.Metadata.cseReplacements"));
    }

    @Test
    void runtimeEquivalenceEvidenceRegistryPassesWhenNoLiteralCandidatesExist() {
        GpuIrOptimizationValidationReport report = reportWithLiteralEvidence(
                emptyCanonicalizationReport(),
                false,
                List.of()
        );

        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules.runtimeEquivalenceEvidenceRegistry();
        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(report));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals(2, results.size());
        assertEquals("cse.literalRuntimeEquivalenceEvidence", results.get(0).ruleId());
        assertEquals(GpuIrOptimizationValidationRuleStatus.PASS, results.get(0).status());
        assertEquals("cse.literalPromotionRuntimeEquivalenceGate", results.get(1).ruleId());
        assertEquals(GpuIrOptimizationValidationRuleStatus.PASS, results.get(1).status());
        assertEquals("true", fields.get("rulesPassed"));
        assertEquals("0", fields.get("rulesResult.0.Metadata.previewCandidates"));
        assertEquals("none", fields.get("rulesResult.0.Metadata.runtimeEquivalenceReadiness"));
        assertEquals("false", fields.get("rulesResult.0.Metadata.runtimeEquivalenceSuccessful"));
        assertEquals("notReady/noPreviewCandidates", fields.get("rulesResult.1.Metadata.promotionReadinessVerdict"));
        assertEquals("4", fields.get("rulesResult.1.Metadata.promotionBlockingReasonCount"));
        assertEquals("noPreviewCandidates,runtimeEquivalenceNotProven,productionFingerprintIntegrationDisabled,productionMutationDisabled", fields.get("rulesResult.1.Metadata.promotionBlockingReasons"));
        assertEquals(Integer.toString(report.commonSubexpressionLiteralPromotionReadinessSummaryReport().remainingWork().size()), fields.get("rulesResult.1.Metadata.promotionRemainingWorkCount"));
    }

    @Test
    void runtimeEquivalenceEvidenceRegistryWarnsAndBlocksWhenLiteralEvidenceIsMissing() {
        GpuIrOptimizationValidationReport report = reportWithLiteralEvidence(
                canonicalizationReport(),
                false,
                List.of()
        );

        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules.runtimeEquivalenceEvidenceRegistry();
        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(report));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals("cse.literalRuntimeEquivalenceEvidence", results.get(0).ruleId());
        assertTrue(results.get(0).passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.WARN, results.get(0).status());
        assertFalse(results.get(0).blocking());
        assertEquals("cse.literalPromotionRuntimeEquivalenceGate", results.get(1).ruleId());
        assertFalse(results.get(1).passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.FAIL, results.get(1).status());
        assertTrue(results.get(1).blocking());
        assertEquals("false", fields.get("rulesPassed"));
        assertEquals("2", fields.get("rulesResult.0.Metadata.previewCandidates"));
        assertEquals("notProven", fields.get("rulesResult.0.Metadata.runtimeEquivalenceReadiness"));
        assertEquals("false", fields.get("rulesResult.0.Metadata.runtimeEquivalenceSuccessful"));
        assertEquals("1", fields.get("rulesResult.0.Metadata.runtimeEquivalenceDiagnostics"));
        assertEquals("literal canonicalization runtime equivalence not run", fields.get("rulesResult.0.Metadata.runtimeEquivalenceFirstDiagnostic"));
        assertEquals("notReady/runtimeMissing", fields.get("rulesResult.1.Metadata.promotionReadinessVerdict"));
        assertEquals("false", fields.get("rulesResult.1.Metadata.promotionRuntimeEquivalenceSuccessful"));
        assertEquals(Integer.toString(report.commonSubexpressionLiteralPromotionReadinessSummaryReport().blockingReasons().size()), fields.get("rulesResult.1.Metadata.promotionBlockingReasonCount"));
        assertEquals(Integer.toString(report.commonSubexpressionLiteralPromotionReadinessSummaryReport().remainingWork().size()), fields.get("rulesResult.1.Metadata.promotionRemainingWorkCount"));
        assertEquals("runtimeEquivalenceNotProven", fields.get("rulesResult.1.Metadata.promotionFirstBlockingReason"));
        assertEquals("runRuntimeEquivalenceEvidence", fields.get("rulesResult.1.Metadata.promotionFirstRemainingWork"));
    }

    @Test
    void runtimeEquivalenceEvidenceRegistryPassesWhenLiteralEvidenceIsSuccessful() {
        GpuIrOptimizationValidationReport report = reportWithLiteralEvidence(
                canonicalizationReport(),
                true,
                List.of(
                        "literal_assoc_preview(plus:int,int,int;literals=1)",
                        "literal_assoc_preview(plus:int,int,int;literals=2)"
                )
        );

        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules.runtimeEquivalenceEvidenceRegistry();
        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(report));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals(GpuIrOptimizationValidationRuleStatus.PASS, results.get(0).status());
        assertEquals(GpuIrOptimizationValidationRuleStatus.PASS, results.get(1).status());
        assertEquals("true", fields.get("rulesPassed"));
        assertEquals("2", fields.get("rulesResult.0.Metadata.previewCandidates"));
        assertEquals("proven", fields.get("rulesResult.0.Metadata.runtimeEquivalenceReadiness"));
        assertEquals("true", fields.get("rulesResult.0.Metadata.runtimeEquivalenceSuccessful"));
        assertEquals("true", fields.get("rulesResult.0.Metadata.numericSemanticsFullyProven"));
        assertEquals("evidenceCompleteButProductionDisabled", fields.get("rulesResult.1.Metadata.promotionReadinessVerdict"));
        assertEquals("true", fields.get("rulesResult.1.Metadata.promotionRuntimeEquivalenceSuccessful"));
        assertEquals("2", fields.get("rulesResult.1.Metadata.promotionBlockingReasonCount"));
        assertEquals(Integer.toString(report.commonSubexpressionLiteralPromotionReadinessSummaryReport().remainingWork().size()), fields.get("rulesResult.1.Metadata.promotionRemainingWorkCount"));
        assertEquals("productionFingerprintIntegrationDisabled,productionMutationDisabled", fields.get("rulesResult.1.Metadata.promotionBlockingReasons"));
    }

    @Test
    void autoVectorizationPrePostRegistryPassesWhenNoPrototypeCandidatesExist() {
        GpuIrOptimizationValidationReport report = validate(new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))));
        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules
                .autoVectorizationPrototypePrePostRuntimeEquivalenceRegistry(emptyAutoVectorizationPrePostEvidence());

        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(report));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals(2, results.size());
        assertEquals("autoVectorization.prototypePrePostRuntimeEquivalenceEvidence", results.get(0).ruleId());
        assertEquals(GpuIrOptimizationValidationRuleStatus.PASS, results.get(0).status());
        assertEquals("autoVectorization.prototypePrePostRuntimeEquivalenceGate", results.get(1).ruleId());
        assertEquals(GpuIrOptimizationValidationRuleStatus.PASS, results.get(1).status());
        assertEquals("true", fields.get("rulesPassed"));
        assertEquals("0", fields.get("rulesResult.0.Metadata.preOptimizationRewriteCandidates"));
        assertEquals("0", fields.get("rulesResult.0.Metadata.postOptimizationAppliedRewrites"));
        assertEquals("true", fields.get("rulesResult.0.Metadata.prePostSuccessful"));
        assertEquals("{}", fields.get("rulesResult.0.Metadata.runtimeEquivalenceDiagnosticFamilyCounts"));
    }

    @Test
    void autoVectorizationPrePostRegistryWarnsAndBlocksWhenPrototypeEvidenceFails() {
        GpuIrOptimizationValidationReport report = validate(new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))));
        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules
                .autoVectorizationPrototypePrePostRuntimeEquivalenceRegistry(autoVectorizationPrePostEvidence(
                        false,
                        List.of("case case-a output out differs")
                ));

        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(report));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals("autoVectorization.prototypePrePostRuntimeEquivalenceEvidence", results.get(0).ruleId());
        assertTrue(results.get(0).passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.WARN, results.get(0).status());
        assertFalse(results.get(0).blocking());
        assertEquals("autoVectorization.prototypePrePostRuntimeEquivalenceGate", results.get(1).ruleId());
        assertFalse(results.get(1).passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.FAIL, results.get(1).status());
        assertTrue(results.get(1).blocking());
        assertEquals("false", fields.get("rulesPassed"));
        assertEquals("1", fields.get("rulesResult.0.Metadata.preOptimizationRewriteCandidates"));
        assertEquals("1", fields.get("rulesResult.0.Metadata.postOptimizationAppliedRewrites"));
        assertEquals("2", fields.get("rulesResult.0.Metadata.autoVectorizationReadinessBlockingReasonCount"));
        assertEquals("noRewriteCandidates,rewritePolicyBlocksRewrite", fields.get("rulesResult.0.Metadata.autoVectorizationReadinessBlockingReasons"));
        assertEquals("false", fields.get("rulesResult.0.Metadata.prePostSuccessful"));
        assertEquals("false", fields.get("rulesResult.0.Metadata.runtimeEquivalenceSuccessful"));
        assertEquals("1", fields.get("rulesResult.0.Metadata.runtimeEquivalenceDiagnostics"));
        assertEquals("{outputDiffers=1}", fields.get("rulesResult.0.Metadata.runtimeEquivalenceDiagnosticFamilyCounts"));
        assertEquals("case case-a output out differs", fields.get("rulesResult.0.Metadata.runtimeEquivalenceFirstDiagnostic"));
        assertEquals("{laneCopy=1,unaryLaneOp=0,binaryLaneOp=0,laneLiteralBinaryOp=0}", fields.get("rulesResult.0.Metadata.appliedRewriteFamilies"));
    }

    @Test
    void autoVectorizationPrePostRegistryPassesWhenPrototypeEvidenceIsSuccessful() {
        GpuIrOptimizationValidationReport report = validate(new GpuIrMethod("kernel", List.of(new GpuIrReturn(null))));
        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules
                .autoVectorizationPrototypePrePostRuntimeEquivalenceRegistry(autoVectorizationPrePostEvidence(true, List.of()));

        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(report));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals(GpuIrOptimizationValidationRuleStatus.PASS, results.get(0).status());
        assertEquals(GpuIrOptimizationValidationRuleStatus.PASS, results.get(1).status());
        assertEquals("true", fields.get("rulesPassed"));
        assertEquals("true", fields.get("rulesResult.0.Metadata.prePostSuccessful"));
        assertEquals("true", fields.get("rulesResult.0.Metadata.runtimeEquivalenceSuccessful"));
        assertEquals("1", fields.get("rulesResult.0.Metadata.runtimeEquivalenceInputCases"));
        assertEquals("1", fields.get("rulesResult.0.Metadata.runtimeEquivalenceComparedOutputs"));
        assertEquals("0", fields.get("rulesResult.0.Metadata.runtimeEquivalenceDiagnostics"));
        assertEquals("{laneCopy=1,unaryLaneOp=0,binaryLaneOp=0,laneLiteralBinaryOp=0}", fields.get("rulesResult.0.Metadata.appliedRewriteFamilies"));
    }

    @Test
    void combinedRuntimeEvidenceRegistryIncludesLiteralAndAutoVectorizationRules() {
        GpuIrOptimizationValidationReport report = reportWithLiteralEvidence(canonicalizationReport(), false, List.of());
        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules.runtimeEquivalenceEvidenceRegistry(
                autoVectorizationPrePostEvidence(false, List.of("case case-a output out differs"))
        );

        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(report));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals(4, results.size());
        assertEquals("cse.literalRuntimeEquivalenceEvidence", results.get(0).ruleId());
        assertEquals("cse.literalPromotionRuntimeEquivalenceGate", results.get(1).ruleId());
        assertEquals("autoVectorization.prototypePrePostRuntimeEquivalenceEvidence", results.get(2).ruleId());
        assertEquals("autoVectorization.prototypePrePostRuntimeEquivalenceGate", results.get(3).ruleId());
        assertEquals("false", fields.get("rulesPassed"));
        assertEquals("warn", fields.get("rulesResult.0.Status"));
        assertEquals("fail", fields.get("rulesResult.1.Status"));
        assertEquals("warn", fields.get("rulesResult.2.Status"));
        assertEquals("fail", fields.get("rulesResult.3.Status"));
        assertEquals("[cse.literalRuntimeEquivalenceEvidence=warn,autoVectorization.prototypePrePostRuntimeEquivalenceEvidence=warn]", fields.get("rulesWarningRuleIndex"));
        assertEquals("[cse.literalPromotionRuntimeEquivalenceGate=fail,autoVectorization.prototypePrePostRuntimeEquivalenceGate=fail]", fields.get("rulesBlockingRuleIndex"));
    }

    @Test
    void optimizerLayerReadinessRegressionRegistryPassesWhenSnapshotIsUnchanged() {
        GpuIrOptimizationValidationReport report = validate(new GpuIrMethod("layerUnchangedKernel", List.of(new GpuIrReturn(null))));
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline =
                report.optimizerLayerReadinessSummaryReport();

        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules
                .optimizerLayerReadinessRegressionRegistry(baseline);
        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(report));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals(1, results.size());
        assertEquals("optimizer.layerReadinessRegressionGate", results.get(0).ruleId());
        assertEquals(GpuIrOptimizationValidationRuleStatus.PASS, results.get(0).status());
        assertFalse(results.get(0).blocking());
        assertEquals("true", fields.get("rulesPassed"));
        assertEquals("unchanged", fields.get("rulesResult.0.Metadata.regressionOutcome"));
        assertEquals("true", fields.get("rulesResult.0.Metadata.regressionUnchanged"));
        assertEquals("0", fields.get("rulesResult.0.Metadata.blockingLayerDelta"));
        assertEquals("0", fields.get("rulesResult.0.Metadata.readyLayerDelta"));
    }

    @Test
    void optimizerLayerReadinessRegressionRegistryWarnsWhenSnapshotImproves() {
        GpuIrOptimizationValidationReport baselineReport = validate(new GpuIrMethod("layerImprovedKernel", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrVariableRef("missing"))
        )));
        GpuIrOptimizationValidationReport current = validate(new GpuIrMethod("layerImprovedKernel", List.of(
                new GpuIrReturn(null)
        )));
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline =
                baselineReport.optimizerLayerReadinessSummaryReport();

        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules
                .optimizerLayerReadinessRegressionRegistry(baseline);
        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(current));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals(GpuIrOptimizationValidationRuleStatus.WARN, results.get(0).status());
        assertFalse(results.get(0).blocking());
        assertEquals("true", fields.get("rulesPassed"));
        assertEquals("improved", fields.get("rulesResult.0.Metadata.regressionOutcome"));
        assertEquals("true", fields.get("rulesResult.0.Metadata.regressionImproved"));
        assertEquals("-2", fields.get("rulesResult.0.Metadata.blockingLayerDelta"));
        assertEquals("1", fields.get("rulesResult.0.Metadata.readyLayerDelta"));
        assertEquals("safety,optimizerGate", fields.get("rulesResult.0.Metadata.improvedLayers"));
        assertEquals("safety", fields.get("rulesResult.0.Metadata.firstImprovedLayer"));
    }

    @Test
    void optimizerLayerReadinessRegressionRegistryFailsWhenSnapshotRegresses() {
        GpuIrOptimizationValidationReport baselineReport = validate(new GpuIrMethod("layerRegressedKernel", List.of(
                new GpuIrReturn(null)
        )));
        GpuIrOptimizationValidationReport current = validate(new GpuIrMethod("layerRegressedKernel", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrVariableRef("missing"))
        )));
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline =
                baselineReport.optimizerLayerReadinessSummaryReport();

        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules
                .optimizerLayerReadinessRegressionRegistry(baseline);
        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(current));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals(GpuIrOptimizationValidationRuleStatus.FAIL, results.get(0).status());
        assertTrue(results.get(0).blocking());
        assertEquals("false", fields.get("rulesPassed"));
        assertEquals("regressed", fields.get("rulesResult.0.Metadata.regressionOutcome"));
        assertEquals("true", fields.get("rulesResult.0.Metadata.regressionRegressed"));
        assertEquals("2", fields.get("rulesResult.0.Metadata.blockingLayerDelta"));
        assertEquals("-1", fields.get("rulesResult.0.Metadata.readyLayerDelta"));
        assertEquals("safety,optimizerGate", fields.get("rulesResult.0.Metadata.regressedLayers"));
        assertEquals("safety", fields.get("rulesResult.0.Metadata.firstRegressedLayer"));
    }

}
