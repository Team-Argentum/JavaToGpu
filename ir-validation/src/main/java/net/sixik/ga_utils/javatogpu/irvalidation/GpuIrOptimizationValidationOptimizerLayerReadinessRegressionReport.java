package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only baseline/current comparison for optimizer-layer readiness.
 *
 * <p>The report lets CI show whether a validation snapshot improved, regressed, or stayed the
 * same without parsing the full readiness maps. It compares only already-computed readiness
 * surfaces and never runs optimizer mutation.</p>
 */
public record GpuIrOptimizationValidationOptimizerLayerReadinessRegressionReport(
        String methodName,
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline,
        GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport current
) {
    public GpuIrOptimizationValidationOptimizerLayerReadinessRegressionReport {
        methodName = requireNonBlank(methodName, "methodName");
        baseline = Objects.requireNonNull(baseline, "baseline");
        current = Objects.requireNonNull(current, "current");
        if (!methodName.equals(baseline.methodName())) {
            throw new IllegalArgumentException("baseline method must match regression method");
        }
        if (!methodName.equals(current.methodName())) {
            throw new IllegalArgumentException("current method must match regression method");
        }
        if (!baseline.layerOrder().equals(current.layerOrder())) {
            throw new IllegalArgumentException("baseline and current layer orders must match");
        }
    }

    public static GpuIrOptimizationValidationOptimizerLayerReadinessRegressionReport from(
            GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport baseline,
            GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport current
    ) {
        Objects.requireNonNull(baseline, "baseline");
        return new GpuIrOptimizationValidationOptimizerLayerReadinessRegressionReport(
                baseline.methodName(),
                baseline,
                current
        );
    }

    public String outcome() {
        int delta = readinessScore(current) - readinessScore(baseline);
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

    public int baselineBlockingLayerCount() {
        return baseline.blockingLayerCount();
    }

    public int currentBlockingLayerCount() {
        return current.blockingLayerCount();
    }

    public int blockingLayerDelta() {
        return currentBlockingLayerCount() - baselineBlockingLayerCount();
    }

    public int baselineReadyLayerCount() {
        return baseline.readyLayerCount();
    }

    public int currentReadyLayerCount() {
        return current.readyLayerCount();
    }

    public int readyLayerDelta() {
        return currentReadyLayerCount() - baselineReadyLayerCount();
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

    public int improvedLayerCount() {
        return improvedLayers().size();
    }

    public int regressedLayerCount() {
        return regressedLayers().size();
    }

    public int changedLayerCount() {
        return changedLayers().size();
    }

    public Optional<String> firstImprovedLayer() {
        return improvedLayers().stream().findFirst();
    }

    public Optional<String> firstRegressedLayer() {
        return regressedLayers().stream().findFirst();
    }

    public Optional<String> firstChangedLayer() {
        return changedLayers().stream().findFirst();
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
        return artifactFields("optimizerLayerReadinessRegression");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName);
        values.put(prefix + "Outcome", outcome());
        values.put(prefix + "Improved", Boolean.toString(improved()));
        values.put(prefix + "Regressed", Boolean.toString(regressed()));
        values.put(prefix + "Unchanged", Boolean.toString(unchanged()));
        values.put(prefix + "BaselineVerdict", baseline.verdict());
        values.put(prefix + "CurrentVerdict", current.verdict());
        values.put(prefix + "BaselineBlockingLayers", listSummary(baseline.blockingLayers()));
        values.put(prefix + "CurrentBlockingLayers", listSummary(current.blockingLayers()));
        values.put(prefix + "BaselineBlockingLayerCount", Integer.toString(baselineBlockingLayerCount()));
        values.put(prefix + "CurrentBlockingLayerCount", Integer.toString(currentBlockingLayerCount()));
        values.put(prefix + "BlockingLayerDelta", Integer.toString(blockingLayerDelta()));
        values.put(prefix + "BaselineReadyLayerCount", Integer.toString(baselineReadyLayerCount()));
        values.put(prefix + "CurrentReadyLayerCount", Integer.toString(currentReadyLayerCount()));
        values.put(prefix + "ReadyLayerDelta", Integer.toString(readyLayerDelta()));
        values.put(prefix + "ImprovedLayers", listSummary(improvedLayers()));
        values.put(prefix + "ImprovedLayerCount", Integer.toString(improvedLayerCount()));
        values.put(prefix + "RegressedLayers", listSummary(regressedLayers()));
        values.put(prefix + "RegressedLayerCount", Integer.toString(regressedLayerCount()));
        values.put(prefix + "ChangedLayers", listSummary(changedLayers()));
        values.put(prefix + "ChangedLayerCount", Integer.toString(changedLayerCount()));
        firstImprovedLayer().ifPresent(layer -> values.put(prefix + "FirstImprovedLayer", layer));
        firstRegressedLayer().ifPresent(layer -> values.put(prefix + "FirstRegressedLayer", layer));
        firstChangedLayer().ifPresent(layer -> values.put(prefix + "FirstChangedLayer", layer));
        values.put(prefix + "LayerTransitions", layerTransitionSummary());
        layerTransitions().forEach((layer, transition) -> values.put(prefix + "Layer." + layer, transition));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "optimizer layer readiness regression method=" + methodName
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

    private static int readinessScore(GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport readiness) {
        return readiness.readyLayerCount() - readiness.blockingLayerCount();
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

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
