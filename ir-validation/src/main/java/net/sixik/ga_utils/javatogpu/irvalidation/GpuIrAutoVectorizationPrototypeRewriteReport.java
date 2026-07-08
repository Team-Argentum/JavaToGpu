package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result artifact for the opt-in prototype auto-vectorization rewrite path.
 */
public record GpuIrAutoVectorizationPrototypeRewriteReport(
        GpuIrMethod method,
        List<GpuIrAutoVectorizationPrototypeAppliedRewrite> appliedRewrites
) {
    private static final List<GpuIrAutoVectorizationPrototypeExpressionKind> COUNTER_ORDER = List.of(
            GpuIrAutoVectorizationPrototypeExpressionKind.LANE_COPY,
            GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP,
            GpuIrAutoVectorizationPrototypeExpressionKind.BINARY_LANE_OP,
            GpuIrAutoVectorizationPrototypeExpressionKind.LANE_LITERAL_BINARY_OP
    );

    public GpuIrAutoVectorizationPrototypeRewriteReport {
        method = Objects.requireNonNull(method, "method");
        appliedRewrites = List.copyOf(Objects.requireNonNull(appliedRewrites, "appliedRewrites"));
        if (appliedRewrites.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("appliedRewrites must not contain null entries");
        }
    }

    public boolean hasAppliedRewrites() {
        return !appliedRewrites.isEmpty();
    }

    public int appliedRewriteCount() {
        return appliedRewrites.size();
    }

    public GpuIrAutoVectorizationPrototypeAppliedRewrite firstAppliedRewrite() {
        return appliedRewrites.isEmpty() ? null : appliedRewrites.get(0);
    }

    public String firstAppliedRewriteSummary() {
        return appliedRewrites.isEmpty() ? "" : appliedRewrites.get(0).summary();
    }

    public int appliedRewriteCount(GpuIrAutoVectorizationPrototypeExpressionKind expressionKind) {
        Objects.requireNonNull(expressionKind, "expressionKind");
        return (int) appliedRewrites.stream()
                .filter(rewrite -> rewrite.expressionKind() == expressionKind)
                .count();
    }

    public int appliedRewriteCount(String expressionKindArtifactValue) {
        if (expressionKindArtifactValue == null || expressionKindArtifactValue.isBlank()) {
            throw new IllegalArgumentException("expressionKindArtifactValue must not be blank");
        }
        return (int) appliedRewrites.stream()
                .filter(rewrite -> expressionKindArtifactValue.equals(rewrite.expressionKindArtifactValue()))
                .count();
    }

    public Map<GpuIrAutoVectorizationPrototypeExpressionKind, Integer> appliedRewriteCountsByKind() {
        Map<GpuIrAutoVectorizationPrototypeExpressionKind, Integer> counts = new java.util.LinkedHashMap<>();
        for (GpuIrAutoVectorizationPrototypeExpressionKind kind : COUNTER_ORDER) {
            counts.put(kind, 0);
        }
        for (GpuIrAutoVectorizationPrototypeAppliedRewrite rewrite : appliedRewrites) {
            counts.put(rewrite.expressionKind(), counts.get(rewrite.expressionKind()) + 1);
        }
        return Collections.unmodifiableMap(counts);
    }

    public Map<String, Integer> appliedRewriteCountsByArtifactValue() {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (GpuIrAutoVectorizationPrototypeExpressionKind kind : COUNTER_ORDER) {
            counts.put(kind.artifactValue(), appliedRewriteCount(kind));
        }
        return Collections.unmodifiableMap(counts);
    }

    public String appliedRewriteFamilyCountersSummary() {
        return appliedRewriteCountsByArtifactValue().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    /**
     * Exposes stable string fields for explicit prototype/integration artifact writers.
     *
     * <p>This helper does not integrate the mutating prototype path into normal validation.
     * Callers must still invoke {@code rewritePrototypeReport(...)} explicitly.</p>
     */
    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(prefix + "AppliedRewrites", Integer.toString(appliedRewriteCount()));
        values.put(prefix + "HasAppliedRewrites", Boolean.toString(hasAppliedRewrites()));
        values.put(prefix + "AppliedRewriteFamilies", appliedRewriteFamilyCountersSummary());
        appliedRewriteCountsByArtifactValue().forEach((family, count) ->
                values.put(prefix + "AppliedRewriteFamily." + family, Integer.toString(count))
        );
        if (hasAppliedRewrites()) {
            GpuIrAutoVectorizationPrototypeAppliedRewrite first = firstAppliedRewrite();
            values.put(prefix + "FirstAppliedRewrite", first.summary());
            values.put(prefix + "FirstAppliedRewriteExpressionKind", first.expressionKindArtifactValue());
            if (!first.binaryOperator().isBlank()) {
                values.put(prefix + "FirstAppliedRewriteBinaryOperator", first.binaryOperator());
            }
            if (!first.unaryOperator().isBlank()) {
                values.put(prefix + "FirstAppliedRewriteUnaryOperator", first.unaryOperator());
            }
        }
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorizationPrototypeRewrite");
    }

    public String summary() {
        return "auto-vectorization prototype rewrite method=" + method.name()
                + " appliedRewrites=" + appliedRewriteCount()
                + " appliedRewriteFamilies=" + appliedRewriteFamilyCountersSummary()
                + (hasAppliedRewrites() ? " firstAppliedRewrite=" + firstAppliedRewriteSummary() : "");
    }
}
