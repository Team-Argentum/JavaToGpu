package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only consistency check across the literal arithmetic evidence stack.
 *
 * <p>This report catches internal artifact drift between enablement, preflight, operation-preview,
 * and promotion-checklist layers before any production mutation path is enabled.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReport(
        String methodName,
        String verdict,
        boolean consistent,
        int checkCount,
        int failedCheckCount,
        List<String> failedChecks
) {
    private static final String VERDICT_OK = "consistent";
    private static final String VERDICT_FAILED = "inconsistent";

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (verdict == null || verdict.isBlank()) {
            throw new IllegalArgumentException("verdict must not be blank");
        }
        if (checkCount < 0) {
            throw new IllegalArgumentException("checkCount must be non-negative");
        }
        if (failedCheckCount < 0) {
            throw new IllegalArgumentException("failedCheckCount must be non-negative");
        }
        failedChecks = List.copyOf(Objects.requireNonNull(failedChecks, "failedChecks"));
        if (failedCheckCount != failedChecks.size()) {
            throw new IllegalArgumentException("failedCheckCount must match failedChecks size");
        }
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReport from(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport enablementReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport preflightReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport operationPreviewReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport promotionChecklistReport
    ) {
        Objects.requireNonNull(enablementReport, "enablementReport");
        Objects.requireNonNull(preflightReport, "preflightReport");
        Objects.requireNonNull(operationPreviewReport, "operationPreviewReport");
        Objects.requireNonNull(promotionChecklistReport, "promotionChecklistReport");

        List<String> failedChecks = failedChecks(enablementReport, preflightReport, operationPreviewReport, promotionChecklistReport);
        boolean consistent = failedChecks.isEmpty();
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReport(
                enablementReport.methodName(),
                consistent ? VERDICT_OK : VERDICT_FAILED,
                consistent,
                12,
                failedChecks.size(),
                failedChecks
        );
    }

    public boolean hasFailures() {
        return failedCheckCount > 0;
    }

    public Optional<String> firstFailedCheck() {
        return failedChecks.stream().findFirst();
    }

    public Optional<String> firstFailureExplanation() {
        return firstFailedCheck().map(GpuIrCommonSubexpressionSimpleArithmeticLiteralConsistencyCheckReport::failureExplanation);
    }

    public String ciSummaryLine() {
        if (consistent) {
            return "literal consistency check passed: " + checkCount + " checks";
        }
        return "literal consistency check failed: " + failedCheckCount + "/" + checkCount
                + " checks failed; first=" + firstFailedCheck().orElse("unknown")
                + "; explanation=" + firstFailureExplanation().orElse("unknown consistency drift");
    }

    public Map<String, Long> failedCheckCounts() {
        return failedChecks.stream()
                .collect(Collectors.groupingBy(
                        check -> check,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Verdict", verdict);
        values.put(prefix + "Consistent", Boolean.toString(consistent));
        values.put(prefix + "Checks", Integer.toString(checkCount));
        values.put(prefix + "FailedChecks", Integer.toString(failedCheckCount));
        values.put(prefix + "FailedCheckList", listSummary(failedChecks));
        values.put(prefix + "FailedCheckCounts", mapSummary(failedCheckCounts()));
        firstFailedCheck().ifPresent(check -> values.put(prefix + "FirstFailedCheck", check));
        firstFailureExplanation().ifPresent(explanation -> values.put(prefix + "FirstFailureExplanation", explanation));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseSimpleArithmeticLiteralConsistencyCheck");
    }

    public String summary() {
        return "CSE simple arithmetic literal consistency check method=" + methodName
                + " verdict=" + verdict
                + " consistent=" + consistent
                + " checks=" + checkCount
                + " failedChecks=" + failedCheckCount
                + " failures=" + listSummary(failedChecks);
    }

    private static String failureExplanation(String failedCheck) {
        return switch (failedCheck) {
            case "enablementPreflightCandidateCount" -> "enablement and preflight disagree on preview candidate count";
            case "enablementOperationPreviewCandidateCount" -> "enablement and operation preview disagree on preview candidate count";
            case "enablementChecklistCandidateCount" -> "enablement and promotion checklist disagree on preview candidate count";
            case "enablementPreflightUniqueKeyCount" -> "enablement and preflight disagree on unique canonical key count";
            case "enablementOperationPreviewUniqueKeyCount" -> "enablement and operation preview disagree on unique canonical key count";
            case "enablementChecklistUniqueKeyCount" -> "enablement and promotion checklist disagree on unique canonical key count";
            case "enablementPreflightEvidenceComplete" -> "enablement and preflight disagree on evidence completeness";
            case "enablementChecklistEvidenceComplete" -> "enablement and promotion checklist disagree on evidence completeness";
            case "enablementChecklistReadyForProduction" -> "enablement and promotion checklist disagree on production readiness";
            case "preflightChecklistEligibleCandidates" -> "preflight and promotion checklist disagree on eligible candidate count";
            case "preflightOperationPreviewBlockedCount" -> "preflight and operation preview disagree on blocked operation count";
            case "operationPreviewChecklistBlockedOperations" -> "operation preview and promotion checklist disagree on blocked operation count";
            default -> "unknown literal consistency drift: " + failedCheck;
        };
    }

    private static List<String> failedChecks(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport enablementReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport preflightReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport operationPreviewReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport promotionChecklistReport
    ) {
        java.util.ArrayList<String> failures = new java.util.ArrayList<>();
        check(enablementReport.previewCandidateCount() == preflightReport.candidateCount(), "enablementPreflightCandidateCount", failures);
        check(enablementReport.previewCandidateCount() == operationPreviewReport.candidateCount(), "enablementOperationPreviewCandidateCount", failures);
        check(enablementReport.previewCandidateCount() == promotionChecklistReport.candidateCount(), "enablementChecklistCandidateCount", failures);
        check(enablementReport.uniqueCanonicalKeyCount() == preflightReport.uniqueCanonicalKeyCount(), "enablementPreflightUniqueKeyCount", failures);
        check(enablementReport.uniqueCanonicalKeyCount() == operationPreviewReport.uniqueCanonicalKeyCount(), "enablementOperationPreviewUniqueKeyCount", failures);
        check(enablementReport.uniqueCanonicalKeyCount() == promotionChecklistReport.uniqueCanonicalKeyCount(), "enablementChecklistUniqueKeyCount", failures);
        check(enablementReport.evidenceComplete() == preflightReport.evidenceComplete(), "enablementPreflightEvidenceComplete", failures);
        check(enablementReport.evidenceComplete() == promotionChecklistReport.evidenceComplete(), "enablementChecklistEvidenceComplete", failures);
        check(enablementReport.readyForProduction() == promotionChecklistReport.readyForProduction(), "enablementChecklistReadyForProduction", failures);
        check(preflightReport.eligibleCandidateCount() == promotionChecklistReport.eligibleCandidateCount(), "preflightChecklistEligibleCandidates", failures);
        check(preflightReport.blockedCandidateCount() == operationPreviewReport.blockedOperationCount(), "preflightOperationPreviewBlockedCount", failures);
        check(operationPreviewReport.blockedOperationCount() == promotionChecklistReport.blockedOperationCount(), "operationPreviewChecklistBlockedOperations", failures);
        return List.copyOf(failures);
    }

    private static void check(boolean condition, String failure, List<String> failures) {
        if (!condition) {
            failures.add(failure);
        }
    }

    private static String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String listSummary(List<String> values) {
        return values.stream().collect(Collectors.joining(",", "[", "]"));
    }
}
