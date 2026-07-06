package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Self-check for optimizer-gate explanation and grouped counter artifacts.
 */
public record GpuIrOptimizerGateConsistencyReport(
        String verdict,
        boolean consistent,
        int checkCount,
        int failedCheckCount,
        List<String> failedChecks
) {
    private static final String VERDICT_OK = "consistent";
    private static final String VERDICT_FAILED = "inconsistent";

    public GpuIrOptimizerGateConsistencyReport {
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

    public static GpuIrOptimizerGateConsistencyReport from(GpuIrOptimizerGateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        java.util.ArrayList<String> failures = new java.util.ArrayList<>();
        int checks = 0;
        GpuIrOptimizerGateExplanation explanation = snapshot.explanation();

        checks++;
        check(!explanation.blocked() || snapshot.sourceCounts().containsKey(explanation.source()), "blockedSourcePresent", failures);
        checks++;
        check(!explanation.blocked() || snapshot.familyCounts().containsKey(explanation.family()), "blockedFamilyPresent", failures);
        checks++;
        check(explanation.blocked() || snapshot.sourceCounts().isEmpty(), "unblockedSourceCountsEmpty", failures);
        checks++;
        check(explanation.blocked() || snapshot.familyCounts().isEmpty(), "unblockedFamilyCountsEmpty", failures);
        checks++;
        check(snapshot.sourceCounts().values().stream().allMatch(count -> count > 0), "sourceCountsPositive", failures);
        checks++;
        check(snapshot.familyCounts().values().stream().allMatch(count -> count > 0), "familyCountsPositive", failures);

        boolean consistent = failures.isEmpty();
        return new GpuIrOptimizerGateConsistencyReport(
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
        return firstFailedCheck().map(GpuIrOptimizerGateConsistencyReport::failureExplanation);
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
        return artifactFields("optimizerGateConsistency");
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

    public String ciSummaryLine() {
        if (consistent) {
            return "optimizer gate consistency check passed: " + checkCount + " checks";
        }
        return "optimizer gate consistency check failed: " + failedCheckCount + "/" + checkCount
                + " checks failed; first=" + firstFailedCheck().orElse("unknown")
                + "; explanation=" + firstFailureExplanation().orElse("unknown optimizer gate artifact drift");
    }

    public String summary() {
        return "optimizer gate consistency verdict=" + verdict
                + " consistent=" + consistent
                + " checks=" + checkCount
                + " failedChecks=" + failedCheckCount
                + " failures=" + listSummary(failedChecks);
    }

    private static void check(boolean condition, String failure, List<String> failures) {
        if (!condition) {
            failures.add(failure);
        }
    }

    private static String failureExplanation(String failedCheck) {
        return switch (failedCheck) {
            case "blockedSourcePresent" -> "blocked gate source must appear in optimizer gate source counts";
            case "blockedFamilyPresent" -> "blocked gate family must appear in optimizer gate family counts";
            case "unblockedSourceCountsEmpty" -> "unblocked optimizer gate must not export source counts";
            case "unblockedFamilyCountsEmpty" -> "unblocked optimizer gate must not export family counts";
            case "sourceCountsPositive" -> "optimizer gate source counts must contain only positive counts";
            case "familyCountsPositive" -> "optimizer gate family counts must contain only positive counts";
            default -> "unknown optimizer gate consistency failure";
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
