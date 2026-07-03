package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Objects;

/**
 * Read-only description of a loop that may become vectorizable after stronger safety checks.
 */
public record GpuIrAutoVectorizationCandidate(
        String loopLocation,
        String inductionVariable,
        int startInclusive,
        int endExclusive,
        int laneCount,
        List<String> targetArrays,
        List<String> sourceArrays,
        List<String> aliasWarnings,
        List<String> crossLaneReadWarnings,
        int assignmentCount
) {
    public GpuIrAutoVectorizationCandidate {
        if (loopLocation == null || loopLocation.isBlank()) {
            throw new IllegalArgumentException("loopLocation must not be blank");
        }
        if (inductionVariable == null || inductionVariable.isBlank()) {
            throw new IllegalArgumentException("inductionVariable must not be blank");
        }
        if (startInclusive < 0) {
            throw new IllegalArgumentException("startInclusive must be non-negative");
        }
        if (endExclusive <= startInclusive) {
            throw new IllegalArgumentException("endExclusive must be greater than startInclusive");
        }
        if (laneCount != endExclusive - startInclusive) {
            throw new IllegalArgumentException("laneCount must match the loop bounds");
        }
        targetArrays = List.copyOf(Objects.requireNonNull(targetArrays, "targetArrays"));
        if (targetArrays.isEmpty() || targetArrays.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("targetArrays must contain non-blank array names");
        }
        sourceArrays = List.copyOf(Objects.requireNonNull(sourceArrays, "sourceArrays"));
        if (sourceArrays.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("sourceArrays must not contain blank array names");
        }
        aliasWarnings = List.copyOf(Objects.requireNonNull(aliasWarnings, "aliasWarnings"));
        crossLaneReadWarnings = List.copyOf(Objects.requireNonNull(crossLaneReadWarnings, "crossLaneReadWarnings"));
        if (assignmentCount <= 0) {
            throw new IllegalArgumentException("assignmentCount must be positive");
        }
    }

    public boolean hasAliasWarnings() {
        return !aliasWarnings.isEmpty();
    }

    public boolean hasCrossLaneReadWarnings() {
        return !crossLaneReadWarnings.isEmpty();
    }

    public boolean hasWarnings() {
        return hasAliasWarnings() || hasCrossLaneReadWarnings();
    }

    /**
     * Conservative diagnostic score for ranking candidates before any mutating vector rewrite exists.
     */
    public int priorityScore() {
        int warningPenalty = aliasWarnings.size() * 25 + crossLaneReadWarnings.size() * 40;
        return Math.max(0, laneCount * Math.max(1, assignmentCount) - warningPenalty);
    }

    public boolean isRewritePriorityCandidate() {
        return !hasWarnings() && priorityScore() > 0;
    }

    public String summary() {
        return "loop " + loopLocation + " lanes=" + laneCount + " induction=" + inductionVariable
                + " targets=" + targetArrays + " sources=" + sourceArrays
                + " priorityScore=" + priorityScore()
                + (aliasWarnings.isEmpty() ? "" : " aliasWarnings=" + aliasWarnings)
                + (crossLaneReadWarnings.isEmpty() ? "" : " crossLaneReadWarnings=" + crossLaneReadWarnings);
    }
}
