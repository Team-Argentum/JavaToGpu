package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * CI-facing acceptance decision for opt-in validation-rule artifacts.
 *
 * <p>The decision intentionally accepts advisory warnings: a warning means follow-up work is
 * visible, not that the artifact is unsafe. Blocking/failing rule results remain rejection
 * reasons.</p>
 */
public record GpuIrOptimizationValidationRuleArtifactAcceptance(
        String methodName,
        String verdict,
        boolean accepted,
        String reason,
        String firstBlockingRuleId,
        String firstFailedRuleId,
        String firstWarningRuleId,
        String ciSummaryLine
) {
    private static final String ACCEPTED_PASS = "accepted/pass";
    private static final String ACCEPTED_WITH_WARNINGS = "accepted/warningsPresent";
    private static final String REJECTED_INCONSISTENT_ARTIFACT = "rejected/inconsistentArtifact";
    private static final String REJECTED_BLOCKING = "rejected/blockingResultsPresent";
    private static final String REJECTED_FAILURES = "rejected/failuresPresent";

    public GpuIrOptimizationValidationRuleArtifactAcceptance {
        methodName = requireNonBlank(methodName, "methodName");
        verdict = requireNonBlank(verdict, "verdict");
        reason = requireNonBlank(reason, "reason");
        firstBlockingRuleId = Objects.requireNonNull(firstBlockingRuleId, "firstBlockingRuleId");
        firstFailedRuleId = Objects.requireNonNull(firstFailedRuleId, "firstFailedRuleId");
        firstWarningRuleId = Objects.requireNonNull(firstWarningRuleId, "firstWarningRuleId");
        ciSummaryLine = requireNonBlank(ciSummaryLine, "ciSummaryLine");
        if (accepted && reason.startsWith("rejected/")) {
            throw new IllegalArgumentException("accepted decisions must not use rejected reasons");
        }
        if (!accepted && reason.startsWith("accepted/")) {
            throw new IllegalArgumentException("rejected decisions must not use accepted reasons");
        }
    }

    public static GpuIrOptimizationValidationRuleArtifactAcceptance from(
            GpuIrOptimizationValidationRuleArtifactReport report
    ) {
        Objects.requireNonNull(report, "report");
        return from(report, report.consistencyReport());
    }

    public static GpuIrOptimizationValidationRuleArtifactAcceptance from(
            GpuIrOptimizationValidationRuleArtifactReport report,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        Objects.requireNonNull(report, "report");
        return from(report.artifactSummary(), consistencyReport);
    }

    public static GpuIrOptimizationValidationRuleArtifactAcceptance from(
            GpuIrOptimizationValidationRuleArtifactSummary summary
    ) {
        Objects.requireNonNull(summary, "summary");
        return acceptedOrRejectedFromSummary(summary);
    }

    public static GpuIrOptimizationValidationRuleArtifactAcceptance from(
            GpuIrOptimizationValidationRuleArtifactSummary summary,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(consistencyReport, "consistencyReport");
        if (!summary.methodName().equals(consistencyReport.methodName())) {
            throw new IllegalArgumentException("consistency report method must match artifact summary method");
        }
        if (!consistencyReport.consistent()) {
            return new GpuIrOptimizationValidationRuleArtifactAcceptance(
                    summary.methodName(),
                    summary.verdict(),
                    false,
                    REJECTED_INCONSISTENT_ARTIFACT,
                    summary.firstBlockingRuleId(),
                    summary.firstFailedRuleId(),
                    summary.firstWarningRuleId(),
                    inconsistentSummaryLine(summary, consistencyReport)
            );
        }
        return acceptedOrRejectedFromSummary(summary);
    }

    private static GpuIrOptimizationValidationRuleArtifactAcceptance acceptedOrRejectedFromSummary(
            GpuIrOptimizationValidationRuleArtifactSummary summary
    ) {
        boolean accepted = summary.passed() && !summary.hasBlockingResults();
        String reason = reason(summary, accepted);
        return new GpuIrOptimizationValidationRuleArtifactAcceptance(
                summary.methodName(),
                summary.verdict(),
                accepted,
                reason,
                summary.firstBlockingRuleId(),
                summary.firstFailedRuleId(),
                summary.firstWarningRuleId(),
                summaryLine(summary, accepted, reason)
        );
    }

    public boolean acceptedWithWarnings() {
        return accepted && ACCEPTED_WITH_WARNINGS.equals(reason);
    }

    public boolean rejected() {
        return !accepted;
    }

    public Map<String, String> artifactFields() {
        return artifactFields("validationRulesAcceptance");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName);
        values.put(prefix + "Verdict", verdict);
        values.put(prefix + "Accepted", Boolean.toString(accepted));
        values.put(prefix + "Rejected", Boolean.toString(rejected()));
        values.put(prefix + "AcceptedWithWarnings", Boolean.toString(acceptedWithWarnings()));
        values.put(prefix + "Reason", reason);
        values.put(prefix + "FirstBlockingRuleId", firstBlockingRuleId);
        values.put(prefix + "FirstFailedRuleId", firstFailedRuleId);
        values.put(prefix + "FirstWarningRuleId", firstWarningRuleId);
        values.put(prefix + "CiSummaryLine", ciSummaryLine);
        return Collections.unmodifiableMap(values);
    }

    private static String reason(GpuIrOptimizationValidationRuleArtifactSummary summary, boolean accepted) {
        if (!accepted && summary.hasBlockingResults()) {
            return REJECTED_BLOCKING;
        }
        if (!accepted && summary.hasFailures()) {
            return REJECTED_FAILURES;
        }
        if (summary.hasWarnings()) {
            return ACCEPTED_WITH_WARNINGS;
        }
        return ACCEPTED_PASS;
    }

    private static String summaryLine(
            GpuIrOptimizationValidationRuleArtifactSummary summary,
            boolean accepted,
            String reason
    ) {
        return "validation rule artifact acceptance method=" + summary.methodName()
                + " verdict=" + summary.verdict()
                + " accepted=" + accepted
                + " reason=" + reason
                + " warnings=" + summary.warningCount()
                + " blocking=" + summary.blockingCount()
                + " failed=" + summary.failedCount()
                + (summary.firstWarningRuleId().isBlank() ? "" : " firstWarningRuleId=" + summary.firstWarningRuleId())
                + (summary.firstBlockingRuleId().isBlank() ? "" : " firstBlockingRuleId=" + summary.firstBlockingRuleId())
                + (summary.firstFailedRuleId().isBlank() ? "" : " firstFailedRuleId=" + summary.firstFailedRuleId());
    }

    private static String inconsistentSummaryLine(
            GpuIrOptimizationValidationRuleArtifactSummary summary,
            GpuIrOptimizationValidationRuleArtifactConsistencyReport consistencyReport
    ) {
        return summaryLine(summary, false, REJECTED_INCONSISTENT_ARTIFACT)
                + " consistency=false"
                + " consistencyFailedChecks=" + consistencyReport.failedCheckCount()
                + consistencyReport.firstFailedCheck()
                .map(check -> " firstConsistencyFailedCheck=" + check)
                .orElse("");
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
