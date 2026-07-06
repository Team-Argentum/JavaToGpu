package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only comparison between a stored readiness baseline snapshot and a current report.
 */
public record GpuIrOptimizationValidationOptimizerLayerReadinessBaselineComparisonReport(
        GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot baseline,
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport current
) {
    public static final String DEFAULT_PREFIX = "optimizerLayerReadinessBaselineComparison";

    public GpuIrOptimizationValidationOptimizerLayerReadinessBaselineComparisonReport {
        baseline = Objects.requireNonNull(baseline, "baseline");
        current = Objects.requireNonNull(current, "current");
        if (!baseline.methodName().equals(current.methodName())) {
            throw new IllegalArgumentException("current method must match stored baseline method");
        }
        if (!baseline.layerOrder().equals(current.layerOrder())) {
            throw new IllegalArgumentException("current layer order must match stored baseline layer order");
        }
    }

    public static GpuIrOptimizationValidationOptimizerLayerReadinessBaselineComparisonReport from(
            GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot baseline,
            GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport current
    ) {
        return new GpuIrOptimizationValidationOptimizerLayerReadinessBaselineComparisonReport(baseline, current);
    }

    public String methodName() {
        return baseline.methodName();
    }

    public String outcome() {
        int delta = readinessScore(current.layerStates()) - readinessScore(baseline.layerStates());
        if (delta > 0) {
            return "improved";
        }
        if (delta < 0) {
            return "regressed";
        }
        return changedLayers().isEmpty() ? "unchanged" : "changed";
    }

    public boolean improved() {
        return "improved".equals(outcome());
    }

    public boolean regressed() {
        return "regressed".equals(outcome());
    }

    public boolean unchanged() {
        return "unchanged".equals(outcome());
    }

    public int blockingLayerDelta() {
        return current.blockingLayerCount() - baseline.blockingLayerCount();
    }

    public int readyLayerDelta() {
        return current.readyLayerCount() - baseline.readyLayerCount();
    }

    public List<String> improvedLayers() {
        return baseline.layerOrder().stream()
                .filter(layer -> layerScore(current.layerStates().get(layer)) > layerScore(baseline.layerStates().get(layer)))
                .toList();
    }

    public List<String> regressedLayers() {
        return baseline.layerOrder().stream()
                .filter(layer -> layerScore(current.layerStates().get(layer)) < layerScore(baseline.layerStates().get(layer)))
                .toList();
    }

    public List<String> changedLayers() {
        return baseline.layerOrder().stream()
                .filter(layer -> !Objects.equals(baseline.layerStates().get(layer), current.layerStates().get(layer)))
                .toList();
    }

    public Optional<String> firstChangedLayer() {
        return changedLayers().stream().findFirst();
    }

    public Optional<String> firstImprovedLayer() {
        return improvedLayers().stream().findFirst();
    }

    public Optional<String> firstRegressedLayer() {
        return regressedLayers().stream().findFirst();
    }

    public Map<String, String> layerTransitions() {
        Map<String, String> transitions = new LinkedHashMap<>();
        baseline.layerOrder().forEach(layer -> transitions.put(
                layer,
                baseline.layerStates().get(layer) + "->" + current.layerStates().get(layer)
        ));
        return Collections.unmodifiableMap(transitions);
    }

    public Map<String, String> artifactFields() {
        return artifactFields(DEFAULT_PREFIX);
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName());
        values.put(prefix + "Outcome", outcome());
        values.put(prefix + "Improved", Boolean.toString(improved()));
        values.put(prefix + "Regressed", Boolean.toString(regressed()));
        values.put(prefix + "Unchanged", Boolean.toString(unchanged()));
        values.put(prefix + "BaselineVerdict", baseline.verdict());
        values.put(prefix + "CurrentVerdict", current.verdict());
        values.put(prefix + "BlockingLayerDelta", Integer.toString(blockingLayerDelta()));
        values.put(prefix + "ReadyLayerDelta", Integer.toString(readyLayerDelta()));
        values.put(prefix + "ChangedLayers", listSummary(changedLayers()));
        values.put(prefix + "ChangedLayerCount", Integer.toString(changedLayers().size()));
        values.put(prefix + "ImprovedLayers", listSummary(improvedLayers()));
        values.put(prefix + "ImprovedLayerCount", Integer.toString(improvedLayers().size()));
        values.put(prefix + "RegressedLayers", listSummary(regressedLayers()));
        values.put(prefix + "RegressedLayerCount", Integer.toString(regressedLayers().size()));
        firstChangedLayer().ifPresent(layer -> values.put(prefix + "FirstChangedLayer", layer));
        firstImprovedLayer().ifPresent(layer -> values.put(prefix + "FirstImprovedLayer", layer));
        firstRegressedLayer().ifPresent(layer -> values.put(prefix + "FirstRegressedLayer", layer));
        values.put(prefix + "LayerTransitions", layerTransitionSummary());
        layerTransitions().forEach((layer, transition) -> values.put(prefix + "Layer." + layer, transition));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "optimizer layer readiness baseline comparison method=" + methodName()
                + " outcome=" + outcome()
                + " blockingLayerDelta=" + blockingLayerDelta()
                + " readyLayerDelta=" + readyLayerDelta()
                + firstChangedLayer().map(layer -> " firstChangedLayer=" + layer).orElse("");
    }

    public String summary() {
        return ciSummaryLine()
                + " baseline=" + baseline.verdict() + "/" + baseline.blockingLayers()
                + " current=" + current.verdict() + "/" + current.blockingLayers()
                + " transitions=" + layerTransitions();
    }

    private String layerTransitionSummary() {
        return layerTransitions().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static int readinessScore(Map<String, String> states) {
        int ready = (int) states.values().stream().filter("ready"::equals).count();
        int blocked = (int) states.values().stream().filter("blocked"::equals).count();
        return ready - blocked;
    }

    private static int layerScore(String state) {
        return switch (state) {
            case "ready" -> 2;
            case "notPresent" -> 1;
            case "blocked" -> 0;
            default -> throw new IllegalArgumentException("unknown layer state: " + state);
        };
    }

    private static String listSummary(List<String> values) {
        return values.stream().collect(Collectors.joining(",", "[", "]"));
    }
}
