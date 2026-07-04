package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Objects;

/**
 * Read-only resolved replacement metadata for a future auto-vectorization rewrite.
 */
public record GpuIrAutoVectorizationResolvedReplacementOperation(
        String loopLocation,
        int statementIndex,
        String inductionVariable,
        int startInclusive,
        int endExclusive,
        int loopBodyStatementCount,
        int loopBodyAssignmentCount,
        List<String> plannedVectorWrites
) {
    public GpuIrAutoVectorizationResolvedReplacementOperation {
        if (loopLocation == null || loopLocation.isBlank()) {
            throw new IllegalArgumentException("loopLocation must not be blank");
        }
        if (statementIndex < 0) {
            throw new IllegalArgumentException("statementIndex must be non-negative");
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
        if (loopBodyStatementCount < 0) {
            throw new IllegalArgumentException("loopBodyStatementCount must be non-negative");
        }
        if (loopBodyAssignmentCount < 0) {
            throw new IllegalArgumentException("loopBodyAssignmentCount must be non-negative");
        }
        plannedVectorWrites = List.copyOf(Objects.requireNonNull(plannedVectorWrites, "plannedVectorWrites"));
        if (plannedVectorWrites.isEmpty() || plannedVectorWrites.stream().anyMatch(write -> write == null || write.isBlank())) {
            throw new IllegalArgumentException("plannedVectorWrites must contain non-blank entries");
        }
    }

    public int laneCount() {
        return endExclusive - startInclusive;
    }

    public String summary() {
        return "resolved replacement " + loopLocation
                + " statementIndex=" + statementIndex
                + " induction=" + inductionVariable
                + " lanes=" + startInclusive + ".." + (endExclusive - 1)
                + " bodyStatements=" + loopBodyStatementCount
                + " bodyAssignments=" + loopBodyAssignmentCount
                + " writes=" + plannedVectorWrites;
    }
}
