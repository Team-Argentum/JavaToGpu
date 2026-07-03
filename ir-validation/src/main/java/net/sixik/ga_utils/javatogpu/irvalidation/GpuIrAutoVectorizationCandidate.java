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
        List<String> repeatedTargetWarnings,
        List<String> crossLaneReadWarnings,
        List<String> nonLaneReadWarnings,
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
        repeatedTargetWarnings = List.copyOf(Objects.requireNonNull(repeatedTargetWarnings, "repeatedTargetWarnings"));
        crossLaneReadWarnings = List.copyOf(Objects.requireNonNull(crossLaneReadWarnings, "crossLaneReadWarnings"));
        nonLaneReadWarnings = List.copyOf(Objects.requireNonNull(nonLaneReadWarnings, "nonLaneReadWarnings"));
        if (assignmentCount <= 0) {
            throw new IllegalArgumentException("assignmentCount must be positive");
        }
    }

    public boolean hasAliasWarnings() {
        return !aliasWarnings.isEmpty();
    }

    public boolean hasRepeatedTargetWarnings() {
        return !repeatedTargetWarnings.isEmpty();
    }

    public boolean hasCrossLaneReadWarnings() {
        return !crossLaneReadWarnings.isEmpty();
    }

    public boolean hasNonLaneReadWarnings() {
        return !nonLaneReadWarnings.isEmpty();
    }

    public boolean hasWarnings() {
        return hasAliasWarnings() || hasRepeatedTargetWarnings() || hasCrossLaneReadWarnings() || hasNonLaneReadWarnings();
    }

    public int warningCount() {
        return aliasWarnings.size()
                + repeatedTargetWarnings.size()
                + crossLaneReadWarnings.size()
                + nonLaneReadWarnings.size();
    }

    /**
     * Conservative diagnostic score for ranking candidates before any mutating vector rewrite exists.
     */
    public int priorityScore() {
        int warningPenalty = aliasWarnings.size() * 25
                + repeatedTargetWarnings.size() * 45
                + crossLaneReadWarnings.size() * 40
                + nonLaneReadWarnings.size() * 35;
        return Math.max(0, laneCount * Math.max(1, assignmentCount) - warningPenalty);
    }

    public boolean isRewritePriorityCandidate() {
        return !hasWarnings() && priorityScore() > 0;
    }

    public String summary() {
        return "loop " + loopLocation + " lanes=" + laneCount + " induction=" + inductionVariable
                + " targets=" + targetArrays + " sources=" + sourceArrays
                + " priorityScore=" + priorityScore()
                + (warningCount() == 0 ? "" : " warningCount=" + warningCount())
                + (aliasWarnings.isEmpty() ? "" : " aliasWarnings=" + aliasWarnings)
                + (repeatedTargetWarnings.isEmpty() ? "" : " repeatedTargetWarnings=" + repeatedTargetWarnings)
                + (crossLaneReadWarnings.isEmpty() ? "" : " crossLaneReadWarnings=" + crossLaneReadWarnings)
                + (nonLaneReadWarnings.isEmpty() ? "" : " nonLaneReadWarnings=" + nonLaneReadWarnings);
    }
}
