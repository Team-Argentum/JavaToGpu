package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Compact read-only rollup for the main optimizer validation layers.
 *
 * <p>The report summarizes already-computed validation artifacts only. It does not run rules,
 * does not apply optimizer rewrites, and does not enable production mutation.</p>
 */
public record GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport(
        GpuIrOptimizationValidationReport report
) {
    private static final List<String> LAYER_ORDER = List.of(
            "safety",
            "optimizerGate",
            "cse",
            "cseLiteralPromotion",
            "autoVectorizationProofLayers",
            "autoVectorization"
    );

    public GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport {
        report = Objects.requireNonNull(report, "report");
    }

    public static GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport from(
            GpuIrOptimizationValidationReport report
    ) {
        return new GpuIrOptimizationValidationOptimizerLayerReadinessSummaryReport(report);
    }

    public String methodName() {
        return report.methodName();
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
                .filter(this::layerBlocksOptimization)
                .toList();
    }

    public int blockingLayerCount() {
        return blockingLayers().size();
    }

    public int readyLayerCount() {
        return (int) LAYER_ORDER.stream()
                .filter(layer -> hasLayer(layer) && !layerBlocksOptimization(layer))
                .count();
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

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerLayerReadiness");
    }

    public GpuIrOptimizationValidationOptimizerLayerReadinessConsistencyReport consistencyReport() {
        return GpuIrOptimizationValidationOptimizerLayerReadinessConsistencyReport.from(this);
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName());
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
        values.putAll(consistencyReport().artifactFields(prefix + "Consistency"));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "optimizer layers " + verdict()
                + " method=" + methodName()
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
        return layerBlocksOptimization(layer) ? "blocked" : "ready";
    }

    private boolean hasLayer(String layer) {
        return switch (layer) {
            case "safety" -> true;
            case "optimizerGate" -> report.optimizerGateExplanation().blocked() || report.hasOptimizerDiagnostics();
            case "cse" -> !report.commonSubexpressionArtifactSnapshot()
                    .layerReadinessSummaryReport()
                    .presentLayers()
                    .isEmpty();
            case "cseLiteralPromotion" -> true;
            case "autoVectorizationProofLayers" -> !report.autoVectorizationArtifactSnapshot()
                    .proofLayerReadinessSummaryReport()
                    .presentLayers()
                    .isEmpty();
            case "autoVectorization" -> true;
            default -> false;
        };
    }

    private boolean layerBlocksOptimization(String layer) {
        return switch (layer) {
            case "safety" -> report.hasSafetyError();
            case "optimizerGate" -> report.optimizerGateExplanation().blocked();
            case "cse" -> report.commonSubexpressionArtifactSnapshot()
                    .layerReadinessSummaryReport()
                    .hasBlockingLayers();
            case "cseLiteralPromotion" -> !report.commonSubexpressionLiteralPromotionReadinessSummaryReport()
                    .readyForProductionMutation();
            case "autoVectorizationProofLayers" -> report.autoVectorizationArtifactSnapshot()
                    .proofLayerReadinessSummaryReport()
                    .hasBlockingLayers();
            case "autoVectorization" -> !report.autoVectorizationArtifactSnapshot()
                    .readinessSummaryReport()
                    .readyForPrototypeRewrite();
            default -> false;
        };
    }
}
