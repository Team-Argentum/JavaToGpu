package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only consistency check for explicit literal runtime-equivalence artifacts.
 *
 * <p>The artifact runner packages runtime evidence, gate state, fingerprint decision state, and a
 * compact summary. This report verifies that those exported layers still describe the same evidence
 * before the data is used by CI or future rewrite promotion work.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactConsistencyReport(
        String methodName,
        String verdict,
        boolean consistent,
        int checkCount,
        int failedCheckCount,
        List<String> failedChecks
) {
    private static final String VERDICT_OK = "consistent";
    private static final String VERDICT_FAILED = "inconsistent";

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactConsistencyReport {
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

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactConsistencyReport from(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport artifactReport
    ) {
        Objects.requireNonNull(artifactReport, "artifactReport");
        List<String> failedChecks = failedChecks(artifactReport);
        boolean consistent = failedChecks.isEmpty();
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactConsistencyReport(
                artifactReport.canonicalizationReport().methodName(),
                consistent ? VERDICT_OK : VERDICT_FAILED,
                consistent,
                14,
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
        return firstFailedCheck().map(GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactConsistencyReport::failureExplanation);
    }

    public Map<String, Long> failedCheckCounts() {
        return failedChecks.stream()
                .collect(Collectors.groupingBy(
                        check -> check,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public String ciSummaryLine() {
        if (consistent) {
            return "literal artifact consistency check passed: " + checkCount + " checks";
        }
        return "literal artifact consistency check failed: " + failedCheckCount + "/" + checkCount
                + " checks failed; first=" + firstFailedCheck().orElse("unknown")
                + "; explanation=" + firstFailureExplanation().orElse("unknown artifact drift");
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
        return artifactFields("cseSimpleArithmeticLiteralArtifactConsistency");
    }

    public String summary() {
        return "CSE simple arithmetic literal artifact consistency method=" + methodName
                + " verdict=" + verdict
                + " consistent=" + consistent
                + " checks=" + checkCount
                + " failedChecks=" + failedCheckCount
                + " failures=" + listSummary(failedChecks);
    }

    private static List<String> failedChecks(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport artifactReport
    ) {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport =
                artifactReport.canonicalizationReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericProofReport =
                artifactReport.numericSemanticsProofReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeReport =
                artifactReport.runtimeEquivalenceReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate gate =
                artifactReport.canonicalizationGate();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport decisionReport =
                artifactReport.fingerprintDecisionReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport parityReport =
                artifactReport.fingerprintParityReport();
        GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactSummary summary = artifactReport.artifactSummary();

        java.util.ArrayList<String> failures = new java.util.ArrayList<>();
        check(artifactReport.successful() == runtimeReport.successful(), "artifactRuntimeSuccessful", failures);
        check(artifactReport.previewCandidateCount() == canonicalizationReport.candidateCount(), "artifactCanonicalizationCandidateCount", failures);
        check(artifactReport.uniqueCanonicalKeyCount() == canonicalizationReport.uniqueCanonicalKeyCount(), "artifactCanonicalizationUniqueKeyCount", failures);
        check(artifactReport.diagnosticCount() == runtimeReport.diagnosticCount(), "artifactRuntimeDiagnosticCount", failures);
        check(runtimeReport.canonicalizationReport() == canonicalizationReport, "runtimeCanonicalizationReference", failures);
        check(runtimeReport.numericSemanticsProofReport() == numericProofReport, "runtimeNumericProofReference", failures);
        check(gate.previewCandidateCount() == canonicalizationReport.candidateCount(), "gateCanonicalizationCandidateCount", failures);
        check(gate.uniqueCanonicalKeyCount() == canonicalizationReport.uniqueCanonicalKeyCount(), "gateCanonicalizationUniqueKeyCount", failures);
        check(decisionReport.runtimeEquivalenceSuccessful() == runtimeReport.successful(), "decisionRuntimeSuccessful", failures);
        check(decisionReport.numericSemanticsFullyProven() == numericProofReport.fullyProven(), "decisionNumericProof", failures);
        check(parityReport.evidenceComplete() == decisionReport.evidenceComplete(), "parityDecisionEvidenceComplete", failures);
        check(summary.runtimeEquivalenceSuccessful() == runtimeReport.successful(), "summaryRuntimeSuccessful", failures);
        check(summary.diagnosticCount() == runtimeReport.diagnosticCount(), "summaryRuntimeDiagnosticCount", failures);
        check(summary.firstDiagnostic().equals(runtimeReport.firstDiagnostic().orElse("")), "summaryRuntimeFirstDiagnostic", failures);
        return List.copyOf(failures);
    }

    private static void check(boolean condition, String failure, List<String> failures) {
        if (!condition) {
            failures.add(failure);
        }
    }

    private static String failureExplanation(String failedCheck) {
        return switch (failedCheck) {
            case "artifactRuntimeSuccessful" -> "artifact and runtime-equivalence reports disagree on success";
            case "artifactCanonicalizationCandidateCount" -> "artifact and canonicalization report disagree on candidate count";
            case "artifactCanonicalizationUniqueKeyCount" -> "artifact and canonicalization report disagree on unique key count";
            case "artifactRuntimeDiagnosticCount" -> "artifact and runtime-equivalence report disagree on diagnostic count";
            case "runtimeCanonicalizationReference" -> "runtime-equivalence report is not tied to the artifact canonicalization report";
            case "runtimeNumericProofReference" -> "runtime-equivalence report is not tied to the artifact numeric proof report";
            case "gateCanonicalizationCandidateCount" -> "gate and canonicalization report disagree on candidate count";
            case "gateCanonicalizationUniqueKeyCount" -> "gate and canonicalization report disagree on unique key count";
            case "decisionRuntimeSuccessful" -> "fingerprint decision and runtime-equivalence report disagree on success";
            case "decisionNumericProof" -> "fingerprint decision and numeric proof report disagree on proof status";
            case "parityDecisionEvidenceComplete" -> "fingerprint parity and decision report disagree on evidence completeness";
            case "summaryRuntimeSuccessful" -> "artifact summary and runtime-equivalence report disagree on success";
            case "summaryRuntimeDiagnosticCount" -> "artifact summary and runtime-equivalence report disagree on diagnostic count";
            case "summaryRuntimeFirstDiagnostic" -> "artifact summary and runtime-equivalence report disagree on first diagnostic";
            default -> "unknown literal artifact drift: " + failedCheck;
        };
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
