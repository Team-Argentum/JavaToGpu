package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Objects;

/**
 * Compact read-only preview for a warning-free candidate that may be rewritten later.
 */
public record GpuIrAutoVectorizationRewriteCandidatePreview(
        String loopLocation,
        int laneCount,
        int assignmentCount,
        int priorityScore,
        List<String> targetArrays,
        List<String> sourceArrays
) {
    public GpuIrAutoVectorizationRewriteCandidatePreview {
        if (loopLocation == null || loopLocation.isBlank()) {
            throw new IllegalArgumentException("loopLocation must not be blank");
        }
        if (laneCount <= 0) {
            throw new IllegalArgumentException("laneCount must be positive");
        }
        if (assignmentCount <= 0) {
            throw new IllegalArgumentException("assignmentCount must be positive");
        }
        if (priorityScore <= 0) {
            throw new IllegalArgumentException("priorityScore must be positive");
        }
        targetArrays = List.copyOf(Objects.requireNonNull(targetArrays, "targetArrays"));
        if (targetArrays.isEmpty() || targetArrays.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("targetArrays must contain non-blank array names");
        }
        sourceArrays = List.copyOf(Objects.requireNonNull(sourceArrays, "sourceArrays"));
        if (sourceArrays.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("sourceArrays must not contain blank array names");
        }
    }

    public static GpuIrAutoVectorizationRewriteCandidatePreview from(GpuIrAutoVectorizationCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (!candidate.isRewritePriorityCandidate()) {
            throw new IllegalArgumentException("candidate must be warning-free and rewrite-priority eligible");
        }
        return new GpuIrAutoVectorizationRewriteCandidatePreview(
                candidate.loopLocation(),
                candidate.laneCount(),
                candidate.assignmentCount(),
                candidate.priorityScore(),
                candidate.targetArrays(),
                candidate.sourceArrays()
        );
    }

    public String summary() {
        return "auto-vectorization rewrite candidate at " + loopLocation
                + " lanes=" + laneCount
                + " assignments=" + assignmentCount
                + " priorityScore=" + priorityScore
                + " targets=" + targetArrays
                + " sources=" + sourceArrays;
    }
}
