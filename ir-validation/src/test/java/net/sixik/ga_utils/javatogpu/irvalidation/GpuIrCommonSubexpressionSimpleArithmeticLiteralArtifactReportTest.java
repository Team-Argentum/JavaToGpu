package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrCommonSubexpressionSimpleArithmeticLiteralTestFixtures.mixedCanonicalizationReport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReportTest {
    @Test
    void combinesPreviewProofRuntimeEvidenceAndGateFields() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalence =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.equivalent(
                        mixedCanonicalizationReport(),
                        numericProofReport(),
                        3,
                        List.of("out", "return")
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport report =
                new GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport(runtimeEquivalence);
        Map<String, String> fields = report.artifactFields("literalArtifact");

        assertTrue(report.successful());
        assertEquals(2, report.previewCandidateCount());
        assertEquals(2, report.uniqueCanonicalKeyCount());
        assertEquals(0, report.diagnosticCount());
        assertEquals("true", fields.get("literalArtifactSuccessful"));
        assertEquals("2", fields.get("literalArtifactPreviewCandidates"));
        assertEquals("2", fields.get("literalArtifactUniqueCanonicalKeys"));
        assertEquals("true", fields.get("literalArtifactNumericSemanticsFullyProven"));
        assertEquals("true", fields.get("literalArtifactRuntimeEquivalenceSuccessful"));
        assertEquals("0", fields.get("literalArtifactRuntimeEquivalenceDiagnostics"));
        assertEquals("false", fields.get("literalArtifactGateCanPromoteToFingerprint"));
        assertEquals("blockedPreview", fields.get("literalArtifactGateReadiness"));
        assertEquals("[fingerprintIntegrationDisabled]", fields.get("literalArtifactGateBlockingReasons"));
        assertEquals("evidenceCompleteButDisabled", fields.get("literalArtifactFingerprintDecisionReadiness"));
        assertEquals("false", fields.get("literalArtifactFingerprintDecisionReadyForProduction"));
        assertEquals("previewOnly", fields.get("literalArtifactFingerprintParityReadiness"));
        assertEquals("2", fields.get("literalArtifactFingerprintParityPreviewOnlyKeys"));
        assertEquals("consistent", fields.get("literalArtifactConsistency.Verdict"));
        assertEquals("true", fields.get("literalArtifactConsistency.Consistent"));
        assertEquals("literal artifact consistency check passed: 12 checks", fields.get("literalArtifactConsistency.CiSummaryLine"));
        assertEquals("2", fields.get("literalArtifactCanonicalization.Candidates"));
        assertEquals("true", fields.get("literalArtifactNumericSemanticsProof.FullyProven"));
        assertEquals("true", fields.get("literalArtifactRuntimeEquivalence.Successful"));
        assertEquals("[fingerprintIntegrationDisabled]", fields.get("literalArtifactGate.BlockingReasons"));
        assertEquals("[productionFingerprintIntegrationDisabled]", fields.get("literalArtifactFingerprintDecision.BlockingReasons"));
        assertEquals("[previewOnlyKeysNotInProductionFingerprints,fingerprintDecisionNotReadyForProduction]", fields.get("literalArtifactFingerprintParity.Blockers"));
        assertTrue(fields.get("literalArtifactSummary").contains("runtimeEquivalenceSuccessful=true"));
        assertTrue(report.summary().contains("gate={"));
        assertTrue(report.summary().contains("fingerprintDecision={"));
        assertTrue(report.summary().contains("fingerprintParity={"));
        assertTrue(report.summary().contains("consistency={"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void reportsFailedRuntimeEvidenceInAggregateFields() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalence =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.failed(
                        mixedCanonicalizationReport(),
                        numericProofReport(),
                        1,
                        List.of("out"),
                        List.of("case case-a output out differs")
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport report =
                new GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport(runtimeEquivalence);
        Map<String, String> fields = report.artifactFields("literalArtifact");

        assertFalse(report.successful());
        assertEquals(1, report.diagnosticCount());
        assertEquals("false", fields.get("literalArtifactSuccessful"));
        assertEquals("false", fields.get("literalArtifactRuntimeEquivalenceSuccessful"));
        assertEquals("1", fields.get("literalArtifactRuntimeEquivalenceDiagnostics"));
        assertEquals("[runtimeEquivalenceNotProven,fingerprintIntegrationDisabled]", fields.get("literalArtifactGateBlockingReasons"));
        assertEquals("blockedPreview", fields.get("literalArtifactFingerprintDecisionReadiness"));
        assertEquals("[runtimeEquivalenceNotProven,productionFingerprintIntegrationDisabled]", fields.get("literalArtifactFingerprintDecision.BlockingReasons"));
        assertEquals("previewOnly", fields.get("literalArtifactFingerprintParityReadiness"));
        assertEquals("consistent", fields.get("literalArtifactConsistency.Verdict"));
        assertEquals("literal artifact consistency check passed: 12 checks", fields.get("literalArtifactConsistency.CiSummaryLine"));
        assertTrue(fields.get("literalArtifactSummary").contains("firstDiagnostic=case case-a output out differs"));
    }

    @Test
    void rejectsInvalidArtifactPrefixAndNullComponents() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport report =
                new GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport(
                        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.equivalent(
                                mixedCanonicalizationReport(),
                                numericProofReport(),
                                1,
                                List.of("out")
                        )
                );

        assertThrows(IllegalArgumentException.class, () -> report.artifactFields(""));
        assertThrows(NullPointerException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport(
                null,
                numericProofReport(),
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.equivalent(
                        mixedCanonicalizationReport(),
                        numericProofReport(),
                        1,
                        List.of("out")
                ),
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate.from(mixedCanonicalizationReport()),
                fingerprintDecisionReport(),
                fingerprintParityReport()
        ));
    }

    private GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericProofReport() {
        return GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.from(mixedCanonicalizationReport());
    }

    private GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport fingerprintDecisionReport() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalence =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.equivalent(
                        mixedCanonicalizationReport(),
                        numericProofReport(),
                        1,
                        List.of("out")
                );
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate gate =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate.from(
                        mixedCanonicalizationReport(),
                        numericProofReport(),
                        runtimeEquivalence
                );
        return GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport.from(
                mixedCanonicalizationReport(),
                numericProofReport(),
                runtimeEquivalence,
                gate
        );
    }

    private GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport fingerprintParityReport() {
        return GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport.from(
                new GpuIrCommonSubexpressionArtifactSnapshot(
                        "kernel",
                        new GpuIrCommonSubexpressionRewritePreview(List.of(), List.of(), List.of())
                ),
                mixedCanonicalizationReport(),
                fingerprintDecisionReport()
        );
    }

}
