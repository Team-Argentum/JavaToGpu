package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Self-check for the compact optimizer-layer readiness rollup.
 *
 * <p>The readiness summary is used by CI as a high-level optimizer status surface. This report
 * verifies that its verdict, counters, first blocker, and per-layer states remain internally
 * coherent without enabling any optimizer mutation.</p>
 */
public record GpuIrOptimizationValidationOptimizerLayerReadinessConsistencyReport(
        String methodName,
        String verdict,
        boolean consistent,
        int checkCount,
        int failedCheckCount,
        List<String> failedChecks
) {
    private static final String VERDICT_OK = "consistent";
    private static final String VERDICT_FAILED = "inconsistent";

    public GpuIrOptimizationValidationOptimizerLayerReadinessConsistencyReport {
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

    public static GpuIrOptimizationValidationOptimizerLayerReadinessConsistencyReport from(
            GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport readiness
    ) {
        Objects.requireNonNull(readiness, "readiness");
        java.util.ArrayList<String> failures = new java.util.ArrayList<>();
        int checks = 0;

        List<String> layerOrder = readiness.layerOrder();
        List<String> presentLayers = readiness.presentLayers();
        List<String> blockingLayers = readiness.blockingLayers();
        Map<String, String> layerStates = readiness.layerStates();
        long derivedReadyLayers = layerOrder.stream()
                .filter(layer -> "ready".equals(layerStates.get(layer)))
                .count();

        checks++;
        check("ready".equals(readiness.verdict()) == readiness.allLayersReady(), "verdictMatchesAllLayersReady", failures);
        checks++;
        check("blocked".equals(readiness.verdict()) == readiness.hasBlockingLayers(), "verdictMatchesHasBlockingLayers", failures);
        checks++;
        check(readiness.allLayersReady() != readiness.hasBlockingLayers(), "readyAndBlockingFlagsAreOpposites", failures);
        checks++;
        check(readiness.blockingLayerCount() == blockingLayers.size(), "blockingLayerCountMatchesList", failures);
        checks++;
        check(readiness.readyLayerCount() == derivedReadyLayers, "readyLayerCountMatchesStates", failures);
        checks++;
        check(readiness.firstBlockingLayer().equals(blockingLayers.stream().findFirst().orElse("none")), "firstBlockingLayerMatchesList", failures);
        checks++;
        check(layerStates.keySet().equals(new java.util.LinkedHashSet<>(layerOrder)), "layerStatesCoverLayerOrder", failures);
        checks++;
        check(presentLayers.stream().allMatch(layerOrder::contains), "presentLayersWithinLayerOrder", failures);
        checks++;
        check(blockingLayers.stream().allMatch(presentLayers::contains), "blockingLayersWithinPresentLayers", failures);
        checks++;
        check(blockingLayers.stream().allMatch(layer -> "blocked".equals(layerStates.get(layer))), "blockingLayersHaveBlockedState", failures);
        checks++;
        check(presentLayers.stream()
                .filter(layer -> !blockingLayers.contains(layer))
                .allMatch(layer -> "ready".equals(layerStates.get(layer))), "presentNonBlockingLayersHaveReadyState", failures);
        checks++;
        check(layerOrder.stream()
                .filter(layer -> !presentLayers.contains(layer))
                .allMatch(layer -> "notPresent".equals(layerStates.get(layer))), "absentLayersHaveNotPresentState", failures);

        boolean consistent = failures.isEmpty();
        return new GpuIrOptimizationValidationOptimizerLayerReadinessConsistencyReport(
                readiness.methodName(),
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
        return firstFailedCheck().map(GpuIrOptimizationValidationOptimizerLayerReadinessConsistencyReport::failureExplanation);
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
        return artifactFields("optimizerLayerReadinessConsistency");
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
            return "optimizer layer readiness consistency check passed: " + checkCount + " checks";
        }
        return "optimizer layer readiness consistency check failed: " + failedCheckCount + "/" + checkCount
                + " checks failed; first=" + firstFailedCheck().orElse("unknown")
                + "; explanation=" + firstFailureExplanation().orElse("unknown optimizer layer readiness drift");
    }

    public String summary() {
        return "optimizer layer readiness consistency method=" + methodName
                + " verdict=" + verdict
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
            case "verdictMatchesAllLayersReady" -> "readiness verdict and all-layers-ready flag disagree";
            case "verdictMatchesHasBlockingLayers" -> "readiness verdict and blocking-layer flag disagree";
            case "readyAndBlockingFlagsAreOpposites" -> "ready and blocking flags are not opposite states";
            case "blockingLayerCountMatchesList" -> "blocking layer count does not match blocking layer list";
            case "readyLayerCountMatchesStates" -> "ready layer count does not match per-layer states";
            case "firstBlockingLayerMatchesList" -> "first blocking layer does not match blocking layer order";
            case "layerStatesCoverLayerOrder" -> "layer state map does not match layer order";
            case "presentLayersWithinLayerOrder" -> "present layer list contains an unknown layer";
            case "blockingLayersWithinPresentLayers" -> "blocking layer list contains a non-present layer";
            case "blockingLayersHaveBlockedState" -> "blocking layers are not marked blocked";
            case "presentNonBlockingLayersHaveReadyState" -> "present non-blocking layers are not marked ready";
            case "absentLayersHaveNotPresentState" -> "absent layers are not marked notPresent";
            default -> "unknown optimizer layer readiness consistency failure";
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
