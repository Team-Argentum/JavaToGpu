package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.canonicalizationReport;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.autoVectorizationPrePostEvidence;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.reportWithLiteralEvidence;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationRuleRuntimeEvidenceArtifactTest {
    @Test
    void exportsRejectedArtifactWhenLiteralRuntimeEvidenceIsMissing() {
        GpuIrOptimizationValidationRuleArtifactReport report = GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                reportWithLiteralEvidence(canonicalizationReport(), false, List.of()),
                GpuIrOptimizationValidationRules.runtimeEquivalenceEvidenceRegistry()
        );
        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptance(report);

        assertFalse(report.passed());
        assertEquals("fail", report.verdict());
        assertEquals(1, report.warningCount());
        assertEquals(1, report.failedCount());
        assertEquals(1, report.blockingCount());
        assertEquals("cse.literalRuntimeEquivalenceEvidence", report.warningRuleIds());
        assertEquals("cse.literalPromotionRuntimeEquivalenceGate", report.failedRuleIds());
        assertEquals("cse.literalPromotionRuntimeEquivalenceGate", report.blockingRuleIds());
        assertEquals("fail", fields.get("validationRulesVerdict"));
        assertEquals("false", fields.get("validationRulesPassed"));
        assertEquals("true", fields.get("validationRulesHasWarnings"));
        assertEquals("true", fields.get("validationRulesHasBlockingResults"));
        assertEquals("[cse.literalRuntimeEquivalenceEvidence=warn]", fields.get("validationRulesWarningRuleIndex"));
        assertEquals("[cse.literalPromotionRuntimeEquivalenceGate=fail]", fields.get("validationRulesBlockingRuleIndex"));
        assertEquals("cse.literalRuntimeEquivalenceEvidence", fields.get("validationRulesFirstWarningRuleId"));
        assertEquals("cse.literalPromotionRuntimeEquivalenceGate", fields.get("validationRulesFirstFailedRuleId"));
        assertEquals("cse.literalPromotionRuntimeEquivalenceGate", fields.get("validationRulesFirstBlockingRuleId"));
        assertEquals("notProven", fields.get("validationRulesRegistryResult.0.Metadata.runtimeEquivalenceReadiness"));
        assertEquals("literal canonicalization runtime equivalence not run", fields.get("validationRulesRegistryResult.0.Metadata.runtimeEquivalenceFirstDiagnostic"));
        assertEquals("notReady/runtimeMissing", fields.get("validationRulesRegistryResult.1.Metadata.promotionReadinessVerdict"));
        assertEquals("runtimeEquivalenceNotProven", fields.get("validationRulesRegistryResult.1.Metadata.promotionFirstBlockingReason"));
        assertEquals("false", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("true", fields.get("validationRulesAcceptanceRejected"));
        assertEquals("rejected/blockingResultsPresent", fields.get("validationRulesAcceptanceReason"));
        assertTrue(fields.get("validationRulesAcceptanceCiSummaryLine").contains("firstBlockingRuleId=cse.literalPromotionRuntimeEquivalenceGate"));
    }

    @Test
    void exportsAcceptedArtifactWhenLiteralRuntimeEvidenceIsSuccessful() {
        GpuIrOptimizationValidationRuleArtifactReport report = GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                reportWithLiteralEvidence(
                        canonicalizationReport(),
                        true,
                        List.of(
                                "literal_assoc_preview(plus:int,int,int;literals=1)",
                                "literal_assoc_preview(plus:int,int,int;literals=2)"
                        )
                ),
                GpuIrOptimizationValidationRules.runtimeEquivalenceEvidenceRegistry()
        );
        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptance(report);

        assertTrue(report.passed());
        assertEquals("pass", report.verdict());
        assertEquals(0, report.warningCount());
        assertEquals(0, report.failedCount());
        assertEquals(0, report.blockingCount());
        assertEquals("[cse.literalRuntimeEquivalenceEvidence=pass,cse.literalPromotionRuntimeEquivalenceGate=pass]", fields.get("validationRulesRuleIndex"));
        assertEquals("proven", fields.get("validationRulesRegistryResult.0.Metadata.runtimeEquivalenceReadiness"));
        assertEquals("true", fields.get("validationRulesRegistryResult.0.Metadata.runtimeEquivalenceSuccessful"));
        assertEquals("evidenceCompleteButProductionDisabled", fields.get("validationRulesRegistryResult.1.Metadata.promotionReadinessVerdict"));
        assertEquals("true", fields.get("validationRulesRegistryResult.1.Metadata.promotionRuntimeEquivalenceSuccessful"));
        assertEquals("true", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("false", fields.get("validationRulesAcceptanceRejected"));
        assertEquals("false", fields.get("validationRulesAcceptanceAcceptedWithWarnings"));
        assertEquals("accepted/pass", fields.get("validationRulesAcceptanceReason"));
        assertTrue(fields.get("validationRulesAcceptanceCiSummaryLine").contains("accepted=true"));
    }

    @Test
    void exportsRejectedArtifactWhenAutoVectorizationPrePostEvidenceFails() {
        GpuIrOptimizationValidationRuleArtifactReport report = GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                validationReport("kernel"),
                GpuIrOptimizationValidationRules.autoVectorizationPrototypePrePostRuntimeEquivalenceRegistry(
                        autoVectorizationPrePostEvidence(false, List.of("case case-a output out differs"))
                )
        );
        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptance(report);

        assertFalse(report.passed());
        assertEquals("fail", report.verdict());
        assertEquals(1, report.warningCount());
        assertEquals(1, report.failedCount());
        assertEquals(1, report.blockingCount());
        assertEquals("autoVectorization.prototypePrePostRuntimeEquivalenceEvidence", report.warningRuleIds());
        assertEquals("autoVectorization.prototypePrePostRuntimeEquivalenceGate", report.failedRuleIds());
        assertEquals("autoVectorization.prototypePrePostRuntimeEquivalenceGate", report.blockingRuleIds());
        assertEquals("fail", fields.get("validationRulesVerdict"));
        assertEquals("false", fields.get("validationRulesPassed"));
        assertEquals("true", fields.get("validationRulesHasWarnings"));
        assertEquals("true", fields.get("validationRulesHasBlockingResults"));
        assertEquals(
                "[autoVectorization.prototypePrePostRuntimeEquivalenceEvidence=warn]",
                fields.get("validationRulesWarningRuleIndex")
        );
        assertEquals(
                "[autoVectorization.prototypePrePostRuntimeEquivalenceGate=fail]",
                fields.get("validationRulesBlockingRuleIndex")
        );
        assertEquals(
                "autoVectorization.prototypePrePostRuntimeEquivalenceEvidence",
                fields.get("validationRulesFirstWarningRuleId")
        );
        assertEquals(
                "autoVectorization.prototypePrePostRuntimeEquivalenceGate",
                fields.get("validationRulesFirstFailedRuleId")
        );
        assertEquals(
                "autoVectorization.prototypePrePostRuntimeEquivalenceGate",
                fields.get("validationRulesFirstBlockingRuleId")
        );
        assertEquals("false", fields.get("validationRulesRegistryResult.0.Metadata.prePostSuccessful"));
        assertEquals("false", fields.get("validationRulesRegistryResult.0.Metadata.runtimeEquivalenceSuccessful"));
        assertEquals("1", fields.get("validationRulesRegistryResult.0.Metadata.runtimeEquivalenceDiagnostics"));
        assertEquals("{outputDiffers=1}", fields.get("validationRulesRegistryResult.0.Metadata.runtimeEquivalenceDiagnosticFamilyCounts"));
        assertEquals("case case-a output out differs", fields.get("validationRulesRegistryResult.0.Metadata.runtimeEquivalenceFirstDiagnostic"));
        assertEquals("false", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("true", fields.get("validationRulesAcceptanceRejected"));
        assertEquals("rejected/blockingResultsPresent", fields.get("validationRulesAcceptanceReason"));
        assertTrue(fields.get("validationRulesAcceptanceCiSummaryLine").contains("firstBlockingRuleId=autoVectorization.prototypePrePostRuntimeEquivalenceGate"));
    }

    @Test
    void exportsAcceptedArtifactWhenAutoVectorizationPrePostEvidenceIsSuccessful() {
        GpuIrOptimizationValidationRuleArtifactReport report = GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                validationReport("kernel"),
                GpuIrOptimizationValidationRules.autoVectorizationPrototypePrePostRuntimeEquivalenceRegistry(
                        autoVectorizationPrePostEvidence(true, List.of())
                )
        );
        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptance(report);

        assertTrue(report.passed());
        assertEquals("pass", report.verdict());
        assertEquals(0, report.warningCount());
        assertEquals(0, report.failedCount());
        assertEquals(0, report.blockingCount());
        assertEquals(
                "[autoVectorization.prototypePrePostRuntimeEquivalenceEvidence=pass,autoVectorization.prototypePrePostRuntimeEquivalenceGate=pass]",
                fields.get("validationRulesRuleIndex")
        );
        assertEquals("true", fields.get("validationRulesRegistryResult.0.Metadata.prePostSuccessful"));
        assertEquals("true", fields.get("validationRulesRegistryResult.0.Metadata.runtimeEquivalenceSuccessful"));
        assertEquals("1", fields.get("validationRulesRegistryResult.0.Metadata.preOptimizationRewriteCandidates"));
        assertEquals("1", fields.get("validationRulesRegistryResult.0.Metadata.postOptimizationAppliedRewrites"));
        assertEquals("{laneCopy=1,unaryLaneOp=0,binaryLaneOp=0,laneLiteralBinaryOp=0}", fields.get("validationRulesRegistryResult.0.Metadata.appliedRewriteFamilies"));
        assertEquals("true", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("false", fields.get("validationRulesAcceptanceRejected"));
        assertEquals("accepted/pass", fields.get("validationRulesAcceptanceReason"));
    }

    @Test
    void exportsFamilyRollupsForCombinedRuntimeEvidenceRegistry() {
        GpuIrOptimizationValidationRuleArtifactReport report = GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                reportWithLiteralEvidence(canonicalizationReport(), false, List.of()),
                GpuIrOptimizationValidationRules.runtimeEquivalenceEvidenceRegistry(
                        autoVectorizationPrePostEvidence(false, List.of("case case-a output out differs"))
                )
        );
        Map<String, String> fields = GpuIrOptimizationValidationRuleArtifactFields.fieldsWithAcceptance(report);

        assertFalse(report.passed());
        assertEquals("fail", report.verdict());
        assertEquals(4, report.ruleCount());
        assertEquals(2, report.warningCount());
        assertEquals(2, report.failedCount());
        assertEquals(2, report.blockingCount());
        assertEquals("{cse=2,autoVectorization=2}", fields.get("validationRulesRuleFamilyCounts"));
        assertEquals("2", fields.get("validationRulesRuleFamily.cse"));
        assertEquals("2", fields.get("validationRulesRuleFamily.autoVectorization"));
        assertEquals("{cse=1,autoVectorization=1}", fields.get("validationRulesWarningRuleFamilyCounts"));
        assertEquals("1", fields.get("validationRulesWarningRuleFamily.cse"));
        assertEquals("1", fields.get("validationRulesWarningRuleFamily.autoVectorization"));
        assertEquals("{cse=1,autoVectorization=1}", fields.get("validationRulesBlockingRuleFamilyCounts"));
        assertEquals("1", fields.get("validationRulesBlockingRuleFamily.cse"));
        assertEquals("1", fields.get("validationRulesBlockingRuleFamily.autoVectorization"));
        assertEquals("{cse=1,autoVectorization=1}", fields.get("validationRulesFailedRuleFamilyCounts"));
        assertEquals("1", fields.get("validationRulesFailedRuleFamily.cse"));
        assertEquals("1", fields.get("validationRulesFailedRuleFamily.autoVectorization"));
        assertEquals("{warn=2,fail=2}", fields.get("validationRulesStatusCounts"));
        assertEquals("2", fields.get("validationRulesStatus.warn"));
        assertEquals("2", fields.get("validationRulesStatus.fail"));
        assertTrue(fields.get("validationRulesSummary").contains("ruleFamilyCounts={cse=2,autoVectorization=2}"));
        assertTrue(fields.get("validationRulesSummary").contains("warningRuleFamilyCounts={cse=1,autoVectorization=1}"));
        assertTrue(fields.get("validationRulesSummary").contains("blockingRuleFamilyCounts={cse=1,autoVectorization=1}"));
        assertTrue(fields.get("validationRulesSummary").contains("failedRuleFamilyCounts={cse=1,autoVectorization=1}"));
        assertEquals("false", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("rejected/blockingResultsPresent", fields.get("validationRulesAcceptanceReason"));
    }
}
