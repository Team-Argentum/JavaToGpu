package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.rule;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationOptimizerReadinessHandoffReportTest {
    @Test
    void acceptsCleanRuleArtifactsAsReadyForOptimizerEnablementReview() {
        GpuIrOptimizationValidationRuleArtifactReport artifact = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("readyKernel"));

        GpuIrOptimizationValidationOptimizerReadinessHandoffReport report =
                GpuIrOptimizationValidationOptimizerReadinessHandoffReport.from(artifact);
        Map<String, String> fields = report.artifactFields();

        assertTrue(report.readyForOptimizerEnablement());
        assertEquals("readyForOptimizerEnablementReview", report.verdict());
        assertEquals(0, report.blockingReasonCount());
        assertEquals("true", fields.get("optimizerReadinessHandoffReadyForOptimizerEnablement"));
        assertEquals("pass", fields.get("optimizerReadinessHandoffRuleArtifactVerdict"));
        assertEquals("true", fields.get("optimizerReadinessHandoffRuleArtifactAccepted"));
        assertEquals("accepted/pass", fields.get("optimizerReadinessHandoffAcceptanceReason"));
        assertEquals("[]", fields.get("optimizerReadinessHandoffBlockingReasons"));
        assertEquals("0", fields.get("optimizerReadinessHandoffRemainingWorkCount"));
    }

    @Test
    void blocksAcceptedWarningArtifactsUntilWarningsAreReviewed() {
        GpuIrOptimizationValidationRuleArtifactReport artifact = GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                validationReport("warningKernel"),
                GpuIrOptimizationValidationRuleRegistry.of(List.of(rule(
                        "optimizer.warning",
                        context -> GpuIrOptimizationValidationRuleResult.warned(
                                "optimizer.warning",
                                "non-blocking optimizer signal"
                        )
                )))
        );

        GpuIrOptimizationValidationOptimizerReadinessHandoffReport report =
                GpuIrOptimizationValidationOptimizerReadinessHandoffReport.from(artifact);
        Map<String, String> fields = report.artifactFields("handoff.");

        assertFalse(report.readyForOptimizerEnablement());
        assertEquals("notReady/warningsPresent", report.verdict());
        assertEquals("ruleArtifactWarningsPresent", report.firstBlockingReason().orElseThrow());
        assertEquals("reviewValidationRuleWarnings", report.firstRemainingWork().orElseThrow());
        assertEquals("false", fields.get("handoff.ReadyForOptimizerEnablement"));
        assertEquals("true", fields.get("handoff.RuleArtifactAccepted"));
        assertEquals("true", fields.get("handoff.RuleArtifactAcceptedWithWarnings"));
        assertEquals("[ruleArtifactWarningsPresent]", fields.get("handoff.BlockingReasons"));
        assertEquals("[reviewValidationRuleWarnings]", fields.get("handoff.RemainingWork"));
    }

    @Test
    void rejectsFailedBlockingArtifactsWithStableHandoffBlockers() {
        GpuIrOptimizationValidationRuleArtifactReport artifact = GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                validationReport("failedKernel"),
                GpuIrOptimizationValidationRuleRegistry.of(List.of(rule(
                        "safety.failure",
                        context -> GpuIrOptimizationValidationRuleResult.failed(
                                "safety.failure",
                                "blocking safety issue"
                        )
                )))
        );

        GpuIrOptimizationValidationOptimizerReadinessHandoffReport report =
                GpuIrOptimizationValidationOptimizerReadinessHandoffReport.from(artifact);
        Map<String, String> fields = report.artifactFields();

        assertFalse(report.readyForOptimizerEnablement());
        assertEquals("notReady/ruleArtifactRejected", report.verdict());
        assertEquals("ruleArtifactRejected", report.firstBlockingReason().orElseThrow());
        assertEquals("safety.failure", report.firstBlockingRuleId());
        assertEquals("safety.failure", report.firstFailedRuleId());
        assertEquals("false", fields.get("optimizerReadinessHandoffRuleArtifactAccepted"));
        assertEquals("rejected/blockingResultsPresent", fields.get("optimizerReadinessHandoffAcceptanceReason"));
        assertEquals("[ruleArtifactRejected,ruleArtifactFailed,blockingRuleResultsPresent]", fields.get("optimizerReadinessHandoffBlockingReasons"));
        assertEquals("[produceAcceptedRuleArtifact,clearFailedValidationRules,clearBlockingValidationRules]", fields.get("optimizerReadinessHandoffRemainingWork"));
    }

    @Test
    void rejectsExplicitlyInconsistentArtifactsFailClosed() {
        GpuIrOptimizationValidationRuleArtifactReport artifact = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("inconsistentKernel"));
        GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport =
                new GpuIrOptimizationValidationRuleArtifactConsistencyReport(
                        "inconsistentKernel",
                        "inconsistent",
                        false,
                        14,
                        1,
                        List.of("summaryVerdict")
                );

        GpuIrOptimizationValidationOptimizerReadinessHandoffReport report =
                GpuIrOptimizationValidationOptimizerReadinessHandoffReport.from(artifact, consistencyReport);
        Map<String, String> fields = report.artifactFields();

        assertFalse(report.readyForOptimizerEnablement());
        assertEquals("notReady/ruleArtifactRejected", report.verdict());
        assertEquals("rejected/inconsistentArtifact", report.acceptanceReason());
        assertEquals("[ruleArtifactRejected,ruleArtifactInconsistent]", fields.get("optimizerReadinessHandoffBlockingReasons"));
        assertEquals("[produceAcceptedRuleArtifact,fixRuleArtifactConsistency]", fields.get("optimizerReadinessHandoffRemainingWork"));
    }

    @Test
    void rejectsInvalidInputsAndReturnsImmutableFields() {
        GpuIrOptimizationValidationRuleArtifactReport artifact = new GpuIrOptimizationValidationRuleArtifactRunner()
                .run(validationReport("invalidKernel"));
        GpuIrOptimizationValidationOptimizerReadinessHandoffReport report =
                GpuIrOptimizationValidationOptimizerReadinessHandoffReport.from(artifact);
        Map<String, String> fields = report.artifactFields();

        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationOptimizerReadinessHandoffReport.from(null));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationOptimizerReadinessHandoffReport.from(artifact, null));
        assertThrows(IllegalArgumentException.class, () -> report.artifactFields(""));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationOptimizerReadinessHandoffReport(
                "kernel",
                "readyForOptimizerEnablementReview",
                false,
                "pass",
                true,
                true,
                false,
                "accepted/pass",
                "",
                "",
                "",
                List.of(),
                List.of()
        ));
    }
}
