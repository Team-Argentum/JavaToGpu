package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Self-check for the compact optimizer CI gate index.
 *
 * <p>The index is a one-call CI entrypoint, so it should fail closed if its aggregate verdict,
 * rejected-gate order, or per-gate accepted flags drift from the underlying gate summaries.</p>
 */
public record GpuIrOptimizationValidationCiGateIndexConsistencyReport(
        String methodName,
        String verdict,
        boolean consistent,
        int checkCount,
        int failedCheckCount,
        List<String> failedChecks
) {
    private static final String VERDICT_OK = "consistent";
    private static final String VERDICT_FAILED = "inconsistent";

    public GpuIrOptimizationValidationCiGateIndexConsistencyReport {
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
        if (consistent != failedChecks.isEmpty()) {
            throw new IllegalArgumentException("consistent must match failed check list emptiness");
        }
    }

    public static GpuIrOptimizationValidationCiGateIndexConsistencyReport from(
            GpuIrOptimizationValidationCiGateIndex index
    ) {
        Objects.requireNonNull(index, "index");
        java.util.ArrayList<String> failures = new java.util.ArrayList<>();
        int checks = 0;

        checks++;
        check(index.accepted() != index.rejected(), "acceptedRejectedAreOpposites", failures);
        checks++;
        check("accepted".equals(index.verdict()) == index.accepted(), "acceptedVerdictMatchesFlag", failures);
        checks++;
        check("rejected".equals(index.verdict()) == index.rejected(), "rejectedVerdictMatchesFlag", failures);
        checks++;
        check(index.accepted() == (
                index.cseGate().accepted()
                        && index.autoVectorizationGate().accepted()
                        && index.optimizerGate().accepted()
        ), "acceptedMatchesGateFlags", failures);
        checks++;
        check(index.firstRejectedGate().equals(expectedFirstRejectedGate(index)), "firstRejectedGateMatchesPriority", failures);
        checks++;
        check(!index.accepted() || "none".equals(index.firstRejectedGate()), "acceptedIndexHasNoRejectedGate", failures);
        checks++;
        check(!index.rejected() || !"none".equals(index.firstRejectedGate()), "rejectedIndexHasRejectedGate", failures);
        checks++;
        check(index.methodName().equals(index.cseGate().methodName()), "cseMethodMatchesIndex", failures);
        checks++;
        check(index.methodName().equals(index.autoVectorizationGate().methodName()), "autoVectorizationMethodMatchesIndex", failures);
        checks++;
        check(index.methodName().equals(index.optimizerGate().methodName()), "optimizerMethodMatchesIndex", failures);

        boolean consistent = failures.isEmpty();
        return new GpuIrOptimizationValidationCiGateIndexConsistencyReport(
                index.methodName(),
                consistent ? VERDICT_OK : VERDICT_FAILED,
                consistent,
                checks,
                failures.size(),
                failures
        );
    }

    public boolean hasFailures() {
        return failedCheckCount > 0;
    }

    public Optional<String> firstFailedCheck() {
        return failedChecks.stream().findFirst();
    }

    public Optional<String> firstFailureExplanation() {
        return firstFailedCheck().map(GpuIrOptimizationValidationCiGateIndexConsistencyReport::failureExplanation);
    }

    public Map<String, Long> failedCheckCounts() {
        return failedChecks.stream()
                .collect(Collectors.groupingBy(
                        check -> check,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerCiGateIndexConsistency");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName);
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

    public String ciSummaryLine() {
        if (consistent) {
            return "optimizer ci gate index consistency check passed: method=" + methodName + " checks=" + checkCount;
        }
        return "optimizer ci gate index consistency check failed: method=" + methodName
                + " failedChecks=" + failedCheckCount + "/" + checkCount
                + " first=" + firstFailedCheck().orElse("unknown")
                + " explanation=" + firstFailureExplanation().orElse("unknown optimizer CI gate index drift");
    }

    public String summary() {
        return "optimizer ci gate index consistency method=" + methodName
                + " verdict=" + verdict
                + " consistent=" + consistent
                + " checks=" + checkCount
                + " failedChecks=" + failedCheckCount
                + " failures=" + listSummary(failedChecks);
    }

    private static String expectedFirstRejectedGate(GpuIrOptimizationValidationCiGateIndex index) {
        if (index.cseGate().rejected()) {
            return "cse";
        }
        if (index.autoVectorizationGate().rejected()) {
            return "autoVectorization";
        }
        if (index.optimizerGate().rejected()) {
            return "optimizer";
        }
        return "none";
    }

    private static void check(boolean condition, String failure, List<String> failures) {
        if (!condition) {
            failures.add(failure);
        }
    }

    private static String failureExplanation(String failedCheck) {
        return switch (failedCheck) {
            case "acceptedRejectedAreOpposites" -> "accepted and rejected flags must be opposite states";
            case "acceptedVerdictMatchesFlag" -> "accepted verdict must match the accepted flag";
            case "rejectedVerdictMatchesFlag" -> "rejected verdict must match the rejected flag";
            case "acceptedMatchesGateFlags" -> "index accepted flag must match all nested gate accepted flags";
            case "firstRejectedGateMatchesPriority" -> "first rejected gate must follow CSE, auto-vectorization, optimizer priority";
            case "acceptedIndexHasNoRejectedGate" -> "accepted index must not report a rejected gate";
            case "rejectedIndexHasRejectedGate" -> "rejected index must report the first rejected gate";
            case "cseMethodMatchesIndex" -> "CSE gate method must match index method";
            case "autoVectorizationMethodMatchesIndex" -> "auto-vectorization gate method must match index method";
            case "optimizerMethodMatchesIndex" -> "optimizer gate method must match index method";
            default -> "unknown optimizer CI gate index consistency failure";
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
