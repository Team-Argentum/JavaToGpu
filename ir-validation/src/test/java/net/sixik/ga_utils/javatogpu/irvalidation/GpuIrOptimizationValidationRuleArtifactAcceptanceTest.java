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

class GpuIrOptimizationValidationRuleArtifactAcceptanceTest {
    @Test
    void acceptsPassingArtifacts() {
        GpuIrOptimizationValidationRuleArtifactReport report = GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                validationReport("acceptedKernel"),
                GpuIrOptimizationValidationRuleRegistry.of(List.of(rule(
                        "safety.clean",
                        context -> GpuIrOptimizationValidationRuleResult.passed("safety.clean", "clean")
                )))
        );

        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance =
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(report);
        Map<String, String> fields = acceptance.artifactFields();

        assertTrue(acceptance.accepted());
        assertFalse(acceptance.rejected());
        assertFalse(acceptance.acceptedWithWarnings());
        assertEquals("pass", acceptance.verdict());
        assertEquals("accepted/pass", acceptance.reason());
        assertEquals("", acceptance.firstWarningRuleId());
        assertEquals("true", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("false", fields.get("validationRulesAcceptanceRejected"));
        assertEquals("false", fields.get("validationRulesAcceptanceAcceptedWithWarnings"));
        assertEquals("accepted/pass", fields.get("validationRulesAcceptanceReason"));
        assertTrue(fields.get("validationRulesAcceptanceCiSummaryLine").contains("accepted=true"));
    }

    @Test
    void acceptsWarningArtifactsAsNonBlockingFollowUpWork() {
        GpuIrOptimizationValidationRuleArtifactReport report = GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                validationReport("warningKernel"),
                GpuIrOptimizationValidationRuleRegistry.of(List.of(rule(
                        "optimizer.warning",
                        context -> GpuIrOptimizationValidationRuleResult.warned(
                                "optimizer.warning",
                                "non-blocking optimizer signal"
                        )
                )))
        );

        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance =
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(report.artifactSummary());
        Map<String, String> fields = acceptance.artifactFields("artifactAcceptance.");

        assertTrue(acceptance.accepted());
        assertFalse(acceptance.rejected());
        assertTrue(acceptance.acceptedWithWarnings());
        assertEquals("warn", acceptance.verdict());
        assertEquals("accepted/warningsPresent", acceptance.reason());
        assertEquals("optimizer.warning", acceptance.firstWarningRuleId());
        assertEquals("", acceptance.firstBlockingRuleId());
        assertEquals("true", fields.get("artifactAcceptance.Accepted"));
        assertEquals("false", fields.get("artifactAcceptance.Rejected"));
        assertEquals("true", fields.get("artifactAcceptance.AcceptedWithWarnings"));
        assertEquals("accepted/warningsPresent", fields.get("artifactAcceptance.Reason"));
        assertEquals("optimizer.warning", fields.get("artifactAcceptance.FirstWarningRuleId"));
        assertTrue(fields.get("artifactAcceptance.CiSummaryLine").contains("firstWarningRuleId=optimizer.warning"));
    }

    @Test
    void rejectsFailingBlockingArtifacts() {
        GpuIrOptimizationValidationRuleArtifactReport report = GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                validationReport("blockingKernel"),
                GpuIrOptimizationValidationRuleRegistry.of(List.of(rule(
                        "safety.failure",
                        context -> GpuIrOptimizationValidationRuleResult.failed(
                                "safety.failure",
                                "blocking safety issue"
                        )
                )))
        );

        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance =
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(report);
        Map<String, String> fields = acceptance.artifactFields();

        assertFalse(acceptance.accepted());
        assertTrue(acceptance.rejected());
        assertFalse(acceptance.acceptedWithWarnings());
        assertEquals("fail", acceptance.verdict());
        assertEquals("rejected/blockingResultsPresent", acceptance.reason());
        assertEquals("safety.failure", acceptance.firstFailedRuleId());
        assertEquals("safety.failure", acceptance.firstBlockingRuleId());
        assertEquals("false", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("true", fields.get("validationRulesAcceptanceRejected"));
        assertEquals("rejected/blockingResultsPresent", fields.get("validationRulesAcceptanceReason"));
        assertTrue(fields.get("validationRulesAcceptanceCiSummaryLine").contains("accepted=false"));
        assertTrue(fields.get("validationRulesAcceptanceCiSummaryLine").contains("firstBlockingRuleId=safety.failure"));
    }

    @Test
    void rejectsInconsistentArtifactsBeforeNormalAcceptanceDecision() {
        GpuIrOptimizationValidationRuleArtifactReport report = GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                validationReport("inconsistentKernel"),
                GpuIrOptimizationValidationRuleRegistry.of(List.of(rule(
                        "safety.clean",
                        context -> GpuIrOptimizationValidationRuleResult.passed("safety.clean", "clean")
                )))
        );
        GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport =
                new GpuIrOptimizationValidationRuleArtifactConsistencyReport(
                        "inconsistentKernel",
                        "inconsistent",
                        false,
                        14,
                        1,
                        List.of("summaryVerdict")
                );

        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance =
                GpuIrOptimizationValidationRuleArtifactAcceptance.from(report, consistencyReport);
        Map<String, String> fields = acceptance.artifactFields();

        assertFalse(acceptance.accepted());
        assertTrue(acceptance.rejected());
        assertFalse(acceptance.acceptedWithWarnings());
        assertEquals("pass", acceptance.verdict());
        assertEquals("rejected/inconsistentArtifact", acceptance.reason());
        assertEquals("false", fields.get("validationRulesAcceptanceAccepted"));
        assertEquals("true", fields.get("validationRulesAcceptanceRejected"));
        assertEquals("rejected/inconsistentArtifact", fields.get("validationRulesAcceptanceReason"));
        assertTrue(fields.get("validationRulesAcceptanceCiSummaryLine").contains("consistency=false"));
        assertTrue(fields.get("validationRulesAcceptanceCiSummaryLine").contains("firstConsistencyFailedCheck=summaryVerdict"));
    }

    @Test
    void rejectsInvalidInputsAndReturnsImmutableFields() {
        GpuIrOptimizationValidationRuleArtifactAcceptance acceptance = new GpuIrOptimizationValidationRuleArtifactAcceptance(
                "kernel",
                "pass",
                true,
                "accepted/pass",
                "",
                "",
                "",
                "summary"
        );
        Map<String, String> fields = acceptance.artifactFields();

        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactAcceptance.from(
                (GpuIrOptimizationValidationRuleArtifactReport) null
        ));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactAcceptance.from(
                (GpuIrOptimizationValidationRuleArtifactSummary) null
        ));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactAcceptance.from(
                (GpuIrOptimizationValidationRuleArtifactReport) null,
                new GpuIrOptimizationValidationRuleArtifactConsistencyReport(
                        "kernel",
                        "consistent",
                        true,
                        14,
                        0,
                        List.of()
                )
        ));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactAcceptance.from(
                validationReportAcceptance("kernel"),
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleArtifactAcceptance.from(
                validationReportAcceptance("kernel").artifactSummary(),
                new GpuIrOptimizationValidationRuleArtifactConsistencyReport(
                        "differentKernel",
                        "consistent",
                        true,
                        14,
                        0,
                        List.of()
                )
        ));
        assertThrows(IllegalArgumentException.class, () -> acceptance.artifactFields(""));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationRuleArtifactAcceptance(
                "",
                "pass",
                true,
                "accepted/pass",
                "",
                "",
                "",
                "summary"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationRuleArtifactAcceptance(
                "kernel",
                "pass",
                true,
                "rejected/blockingResultsPresent",
                "",
                "",
                "",
                "summary"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationRuleArtifactAcceptance(
                "kernel",
                "fail",
                false,
                "accepted/pass",
                "",
                "",
                "",
                "summary"
        ));
    }

    private static GpuIrOptimizationValidationRuleArtifactReport validationReportAcceptance(String methodName) {
        return GpuIrOptimizationValidationRuleArtifactReport.evaluate(
                validationReport(methodName),
                GpuIrOptimizationValidationRuleRegistry.of(List.of(rule(
                        "safety.clean",
                        context -> GpuIrOptimizationValidationRuleResult.passed("safety.clean", "clean")
                )))
        );
    }
}
