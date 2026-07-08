package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Objects;

/**
 * Typed metadata for one applied opt-in prototype auto-vectorization rewrite.
 */
public record GpuIrAutoVectorizationPrototypeAppliedRewrite(
        String loopLocation,
        int statementIndex,
        String vectorType,
        int startInclusive,
        int endExclusive,
        List<String> targetArrays,
        List<String> sourceArrays,
        GpuIrAutoVectorizationPrototypeExpressionKind expressionKind,
        String binaryOperator,
        String unaryOperator
) {
    public GpuIrAutoVectorizationPrototypeAppliedRewrite {
        if (loopLocation == null || loopLocation.isBlank()) {
            throw new IllegalArgumentException("loopLocation must not be blank");
        }
        if (statementIndex < 0) {
            throw new IllegalArgumentException("statementIndex must be non-negative");
        }
        if (vectorType == null || vectorType.isBlank()) {
            throw new IllegalArgumentException("vectorType must not be blank");
        }
        if (startInclusive < 0) {
            throw new IllegalArgumentException("startInclusive must be non-negative");
        }
        if (endExclusive <= startInclusive) {
            throw new IllegalArgumentException("endExclusive must be greater than startInclusive");
        }
        targetArrays = List.copyOf(Objects.requireNonNull(targetArrays, "targetArrays"));
        sourceArrays = List.copyOf(Objects.requireNonNull(sourceArrays, "sourceArrays"));
        expressionKind = Objects.requireNonNull(expressionKind, "expressionKind");
        if (expressionKind.requiresBinaryOperator()) {
            if (binaryOperator == null || binaryOperator.isBlank()) {
                throw new IllegalArgumentException("binaryOperator must be set for binary lane operations");
            }
        } else if (binaryOperator != null && !binaryOperator.isBlank()) {
            throw new IllegalArgumentException("binaryOperator must be blank unless the expression kind is binary");
        }
        if (expressionKind == GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP) {
            if (unaryOperator == null || unaryOperator.isBlank()) {
                throw new IllegalArgumentException("unaryOperator must be set for unary lane operations");
            }
        } else if (unaryOperator != null && !unaryOperator.isBlank()) {
            throw new IllegalArgumentException("unaryOperator must be blank unless the expression kind is unary");
        }
        if (targetArrays.isEmpty() || targetArrays.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("targetArrays must contain non-blank entries");
        }
        if (sourceArrays.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("sourceArrays must not contain blank entries");
        }
    }

    public int laneCount() {
        return endExclusive - startInclusive;
    }

    public String expressionKindArtifactValue() {
        return expressionKind.artifactValue();
    }

    public String expressionKindSummary() {
        return expressionKind.summary();
    }

    public String summary() {
        return "prototype vector rewrite at " + loopLocation
                + " statementIndex=" + statementIndex
                + " vectorType=" + vectorType
                + " lanes=" + startInclusive + ".." + (endExclusive - 1)
                + " expressionKind=" + expressionKindSummary()
                + (binaryOperator == null || binaryOperator.isBlank() ? "" : " binaryOperator=" + binaryOperator)
                + (unaryOperator == null || unaryOperator.isBlank() ? "" : " unaryOperator=" + unaryOperator)
                + " targetArrays=" + targetArrays
                + " sourceArrays=" + sourceArrays;
    }
}
