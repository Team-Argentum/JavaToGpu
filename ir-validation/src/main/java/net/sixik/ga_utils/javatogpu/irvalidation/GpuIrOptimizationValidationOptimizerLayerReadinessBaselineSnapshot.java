package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Serializable optimizer-layer readiness baseline for CI storage.
 *
 * <p>The snapshot stores only compact readiness state, so a CI job can persist it as a
 * `.properties`-friendly map and compare a later validation run without keeping the original
 * validation report object around.</p>
 */
public record GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot(
        String methodName,
        List<String> layerOrder,
        Map<String, String> layerStates
) {
    public static final String DEFAULT_PREFIX = "optimizerLayerReadinessBaselineSnapshot";

    public GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot {
        methodName = requireNonBlank(methodName, "methodName");
        layerOrder = List.copyOf(Objects.requireNonNull(layerOrder, "layerOrder"));
        layerStates = Map.copyOf(Objects.requireNonNull(layerStates, "layerStates"));
        if (layerOrder.isEmpty()) {
            throw new IllegalArgumentException("layerOrder must not be empty");
        }
        for (String layer : layerOrder) {
            requireNonBlank(layer, "layer");
            String state = layerStates.get(layer);
            if (state == null) {
                throw new IllegalArgumentException("layer state missing for: " + layer);
            }
            validateLayerState(state);
        }
    }

    public static GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot from(
            GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport readiness
    ) {
        Objects.requireNonNull(readiness, "readiness");
        return new GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot(
                readiness.methodName(),
                readiness.layerOrder(),
                readiness.layerStates()
        );
    }

    public static GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot fromArtifactFields(
            Map<String, String> fields
    ) {
        return fromArtifactFields(fields, DEFAULT_PREFIX);
    }

    public static GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot fromArtifactFields(
            Map<String, String> fields,
            String prefix
    ) {
        Objects.requireNonNull(fields, "fields");
        String fieldPrefix = requireNonBlank(prefix, "prefix");
        String methodName = requiredField(fields, fieldPrefix + "Method");
        List<String> layerOrder = commaList(requiredField(fields, fieldPrefix + "LayerOrder"));
        Map<String, String> states = new LinkedHashMap<>();
        layerOrder.forEach(layer -> states.put(layer, requiredField(fields, fieldPrefix + "Layer." + layer)));
        return new GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot(
                methodName,
                layerOrder,
                states
        );
    }

    public String verdict() {
        return blockingLayers().isEmpty() ? "ready" : "blocked";
    }

    public List<String> presentLayers() {
        return layerOrder.stream()
                .filter(layer -> !"notPresent".equals(layerStates.get(layer)))
                .toList();
    }

    public List<String> blockingLayers() {
        return layerOrder.stream()
                .filter(layer -> "blocked".equals(layerStates.get(layer)))
                .toList();
    }

    public int blockingLayerCount() {
        return blockingLayers().size();
    }

    public int readyLayerCount() {
        return (int) layerOrder.stream()
                .filter(layer -> "ready".equals(layerStates.get(layer)))
                .count();
    }

    public String firstBlockingLayer() {
        return blockingLayers().stream().findFirst().orElse("none");
    }

    public Map<String, String> artifactFields() {
        return artifactFields(DEFAULT_PREFIX);
    }

    public Map<String, String> artifactFields(String prefix) {
        String fieldPrefix = requireNonBlank(prefix, "prefix");
        Map<String, String> values = new LinkedHashMap<>();
        values.put(fieldPrefix + "Method", methodName);
        values.put(fieldPrefix + "Verdict", verdict());
        values.put(fieldPrefix + "LayerOrder", String.join(",", layerOrder));
        values.put(fieldPrefix + "PresentLayers", String.join(",", presentLayers()));
        values.put(fieldPrefix + "BlockingLayers", String.join(",", blockingLayers()));
        values.put(fieldPrefix + "BlockingLayerCount", Integer.toString(blockingLayerCount()));
        values.put(fieldPrefix + "ReadyLayerCount", Integer.toString(readyLayerCount()));
        values.put(fieldPrefix + "FirstBlockingLayer", firstBlockingLayer());
        values.put(fieldPrefix + "LayerStates", layerStates.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}")));
        layerOrder.forEach(layer -> values.put(fieldPrefix + "Layer." + layer, layerStates.get(layer)));
        values.put(fieldPrefix + "CiSummaryLine", ciSummaryLine());
        values.put(fieldPrefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "optimizer layer readiness baseline snapshot method=" + methodName
                + " verdict=" + verdict()
                + " blockingLayers=" + blockingLayers()
                + " firstBlockingLayer=" + firstBlockingLayer();
    }

    public String summary() {
        return ciSummaryLine()
                + " presentLayers=" + presentLayers()
                + " layerStates=" + layerStates;
    }

    private static List<String> commaList(String value) {
        if (value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private static String requiredField(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing baseline snapshot field: " + key);
        }
        return value;
    }

    private static void validateLayerState(String state) {
        switch (state) {
            case "ready", "blocked", "notPresent" -> {
            }
            default -> throw new IllegalArgumentException("unknown layer state: " + state);
        }
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
