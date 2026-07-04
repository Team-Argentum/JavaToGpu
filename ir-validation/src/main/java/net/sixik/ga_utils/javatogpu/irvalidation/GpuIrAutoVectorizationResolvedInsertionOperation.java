package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Objects;

/**
 * Read-only resolved insertion metadata for a future auto-vectorization rewrite.
 */
public record GpuIrAutoVectorizationResolvedInsertionOperation(
        String loopLocation,
        int statementIndex,
        String vectorType,
        int startInclusive,
        int endExclusive,
        int loopBodyStatementCount,
        int loopBodyAssignmentCount,
        List<String> plannedVectorReads
) {
    public GpuIrAutoVectorizationResolvedInsertionOperation {
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
        if (loopBodyStatementCount < 0) {
            throw new IllegalArgumentException("loopBodyStatementCount must be non-negative");
        }
        if (loopBodyAssignmentCount < 0) {
            throw new IllegalArgumentException("loopBodyAssignmentCount must be non-negative");
        }
        plannedVectorReads = List.copyOf(Objects.requireNonNull(plannedVectorReads, "plannedVectorReads"));
        if (plannedVectorReads.stream().anyMatch(read -> read == null || read.isBlank())) {
            throw new IllegalArgumentException("plannedVectorReads must not contain blank entries");
        }
    }

    public int laneCount() {
        return endExclusive - startInclusive;
    }

    public String summary() {
        return "resolved insertion " + loopLocation
                + " statementIndex=" + statementIndex
                + " vectorType=" + vectorType
                + " lanes=" + startInclusive + ".." + (endExclusive - 1)
                + " bodyStatements=" + loopBodyStatementCount
                + " bodyAssignments=" + loopBodyAssignmentCount
                + " reads=" + plannedVectorReads;
    }
}
