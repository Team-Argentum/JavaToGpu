package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Compact CI-facing rollup for the read-only CSE readiness layers.
 *
 * <p>This report does not enable CSE mutation. It only summarizes the already-exported proof and
 * policy surfaces into a stable layer verdict so build artifacts can show why CSE is ready, empty,
 * or still blocked without parsing individual counter families.</p>
 */
public record GpuIrCommonSubexpressionLayerReadinessSummaryReport(
        GpuIrCommonSubexpressionRewritePreview preview,
        GpuIrCommonSubexpressionRewritePolicy rewritePolicy,
        GpuIrCommonSubexpressionLocalExpressionDominanceReport localExpressionDominanceReport,
        GpuIrCommonSubexpressionSimpleArithmeticProofReport simpleArithmeticProofReport
) {
    private static final List<String> LAYER_ORDER = List.of(
            "rewritePolicy",
            "skipReason",
            "dominance",
            "localExpression",
            "simpleArithmeticProof"
    );

    public GpuIrCommonSubexpressionLayerReadinessSummaryReport {
        preview = Objects.requireNonNull(preview, "preview");
        rewritePolicy = Objects.requireNonNull(rewritePolicy, "rewritePolicy");
        localExpressionDominanceReport = Objects.requireNonNull(
                localExpressionDominanceReport,
                "localExpressionDominanceReport"
        );
        simpleArithmeticProofReport = Objects.requireNonNull(
                simpleArithmeticProofReport,
                "simpleArithmeticProofReport"
        );
    }

    public static GpuIrCommonSubexpressionLayerReadinessSummaryReport from(
            GpuIrCommonSubexpressionRewritePreview preview,
            GpuIrCommonSubexpressionRewritePolicy rewritePolicy
    ) {
        Objects.requireNonNull(preview, "preview");
        return new GpuIrCommonSubexpressionLayerReadinessSummaryReport(
                preview,
                rewritePolicy,
                new GpuIrCommonSubexpressionLocalExpressionDominanceReport(preview),
                GpuIrCommonSubexpressionSimpleArithmeticProofReport.from(preview)
        );
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
        return (int) LAYER_ORDER.stream()
                .filter(layer -> hasLayer(layer) && !layerBlocksRewrite(layer))
                .count();
    }

    public String firstBlockingLayer() {
        return blockingLayers().stream().findFirst().orElse("none");
    }

    public String verdict() {
        if (hasBlockingLayers()) {
            return "blocked";
        }
        return rewritePolicy.canRewrite() ? "ready" : "noRewriteWork";
    }

    public Map<String, String> layerStates() {
        Map<String, String> states = new LinkedHashMap<>();
        LAYER_ORDER.forEach(layer -> states.put(layer, layerState(layer)));
        return Collections.unmodifiableMap(states);
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
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
        return artifactFields("cseLayerReadiness");
    }

    public String ciSummaryLine() {
        return "CSE layers " + verdict()
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
        return switch (layer) {
            case "rewritePolicy" -> rewritePolicy.hasPlans() || rewritePolicy.hasBlockingSkippedCandidates();
            case "skipReason" -> !rewritePolicy.blockingSkipReasonCounts().isEmpty();
            case "dominance" -> !rewritePolicy.blockingDominanceStatusCounts().isEmpty();
            case "localExpression" -> localExpressionDominanceReport.hasLocalExpressionEvidence();
            case "simpleArithmeticProof" -> simpleArithmeticProofReport.hasProofs();
            default -> false;
        };
    }

    private boolean layerBlocksRewrite(String layer) {
        return switch (layer) {
            case "rewritePolicy" -> rewritePolicy.hasBlockingSkippedCandidates();
            case "skipReason" -> !rewritePolicy.blockingSkipReasonCounts().isEmpty();
            case "dominance" -> !rewritePolicy.blockingDominanceStatusCounts().isEmpty();
            case "localExpression" -> localExpressionDominanceReport.blockedCandidateCount() > 0;
            case "simpleArithmeticProof" -> false;
            default -> false;
        };
    }
}
