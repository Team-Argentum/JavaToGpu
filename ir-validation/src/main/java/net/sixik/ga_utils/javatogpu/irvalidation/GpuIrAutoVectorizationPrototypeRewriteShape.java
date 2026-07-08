package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;

import java.util.List;
import java.util.Objects;

/**
 * Resolved prototype rewrite shape that is narrow enough to mutate safely.
 */
record GpuIrAutoVectorizationPrototypeRewriteShape(
        String loopLocation,
        int statementIndex,
        String vectorType,
        int startInclusive,
        int endExclusive,
        String inductionVariable,
        List<String> targetArrays,
        List<String> sourceArrays,
        GpuIrAutoVectorizationPrototypeExpressionKind expressionKind,
        String binaryOperator,
        String unaryOperator,
        GpuIrAssignment assignment
) {
    GpuIrAutoVectorizationPrototypeRewriteShape {
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
        if (inductionVariable == null || inductionVariable.isBlank()) {
            throw new IllegalArgumentException("inductionVariable must not be blank");
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
        assignment = Objects.requireNonNull(assignment, "assignment");
        if (targetArrays.isEmpty() || targetArrays.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("targetArrays must contain non-blank entries");
        }
        if (sourceArrays.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("sourceArrays must not contain blank entries");
        }
    }

    int laneCount() {
        return endExclusive - startInclusive;
    }

    String expressionKindArtifactValue() {
        return expressionKind.artifactValue();
    }

    String expressionKindSummary() {
        return expressionKind.summary();
    }
}
