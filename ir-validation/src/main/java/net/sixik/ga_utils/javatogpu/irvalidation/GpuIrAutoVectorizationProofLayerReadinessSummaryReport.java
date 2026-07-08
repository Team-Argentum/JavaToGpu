package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Compact CI-facing rollup for the typed auto-vectorization proof layers.
 */
public record GpuIrAutoVectorizationProofLayerReadinessSummaryReport(
        GpuIrAutoVectorizationProofBundle proofBundle
) {
    private static final List<String> LAYER_ORDER = List.of(
            "unknownVector",
            "backend",
            "mutation",
            "controlFlowBoundary",
            "memoryLegality",
            "sideEffect"
    );

    public GpuIrAutoVectorizationProofLayerReadinessSummaryReport {
        proofBundle = Objects.requireNonNull(proofBundle, "proofBundle");
    }

    public static GpuIrAutoVectorizationProofLayerReadinessSummaryReport from(GpuIrAutoVectorizationProofBundle proofBundle) {
        return new GpuIrAutoVectorizationProofLayerReadinessSummaryReport(proofBundle);
    }

    public boolean allLayersReady() {
        return blockingLayers().isEmpty();
    }

    public boolean hasBlockingLayers() {
        return !allLayersReady();
    }

    public List<String> layerOrder() {
        return LAYER_ORDER;
    }

    public List<String> presentLayers() {
        return LAYER_ORDER.stream()
                .filter(this::hasLayer)
                .toList();
    }

    public List<String> blockingLayers() {
        return LAYER_ORDER.stream()
                .filter(this::layerBlocksRewrite)
                .toList();
    }

    public int blockingLayerCount() {
        return blockingLayers().size();
    }

    public int readyLayerCount() {
        return LAYER_ORDER.size() - blockingLayerCount();
    }

    public String firstBlockingLayer() {
        return blockingLayers().stream().findFirst().orElse("none");
    }

    public String verdict() {
        return allLayersReady() ? "ready" : "blocked";
    }

    public Map<String, String> layerStates() {
        Map<String, String> states = new LinkedHashMap<>();
        LAYER_ORDER.forEach(layer -> states.put(layer, layerState(layer)));
        return Collections.unmodifiableMap(states);
    }

    public Map<String, String> artifactFields(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Verdict", verdict());
        values.put(prefix + "AllLayersReady", Boolean.toString(allLayersReady()));
        values.put(prefix + "HasBlockingLayers", Boolean.toString(hasBlockingLayers()));
        values.put(prefix + "LayerOrder", String.join(",", LAYER_ORDER));
        values.put(prefix + "PresentLayers", String.join(",", presentLayers()));
        values.put(prefix + "BlockingLayers", String.join(",", blockingLayers()));
        values.put(prefix + "BlockingLayerCount", Integer.toString(blockingLayerCount()));
        values.put(prefix + "ReadyLayerCount", Integer.toString(readyLayerCount()));
        values.put(prefix + "FirstBlockingLayer", firstBlockingLayer());
        values.put(prefix + "LayerStates", layerStates().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}")));
        layerStates().forEach((layer, state) -> values.put(prefix + "Layer." + layer, state));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorizationProofLayerReadiness");
    }

    public String ciSummaryLine() {
        return "auto-vectorization proof layers " + verdict()
                + " blockingLayers=" + blockingLayers()
                + " firstBlockingLayer=" + firstBlockingLayer();
    }

    public String summary() {
        return ciSummaryLine()
                + " presentLayers=" + presentLayers()
                + " layerStates=" + layerStates();
    }

    private String layerState(String layer) {
        if (!hasLayer(layer)) {
            return "notPresent";
        }
        return layerBlocksRewrite(layer) ? "blocked" : "ready";
    }

    private boolean hasLayer(String layer) {
        return proofBundle.proofKindCounts().containsKey(layer);
    }

    private boolean layerBlocksRewrite(String layer) {
        return proofBundle.unsafeProofKindCounts().containsKey(layer);
    }
}
