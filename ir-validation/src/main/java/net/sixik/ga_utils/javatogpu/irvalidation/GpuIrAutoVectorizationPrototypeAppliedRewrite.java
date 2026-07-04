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
        List<String> sourceArrays
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

    public String summary() {
        return "prototype vector rewrite at " + loopLocation
                + " statementIndex=" + statementIndex
                + " vectorType=" + vectorType
                + " lanes=" + startInclusive + ".." + (endExclusive - 1)
                + " targetArrays=" + targetArrays
                + " sourceArrays=" + sourceArrays;
    }
}
