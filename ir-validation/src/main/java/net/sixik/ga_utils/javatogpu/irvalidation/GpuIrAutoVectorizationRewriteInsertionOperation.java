package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Objects;

/**
 * Typed preview of vector temporary setup before a scalar lane loop is replaced.
 */
public record GpuIrAutoVectorizationRewriteInsertionOperation(
        String loopLocation,
        String vectorType,
        int startInclusive,
        int endExclusive,
        List<String> plannedVectorReads
) {
    public GpuIrAutoVectorizationRewriteInsertionOperation {
        if (loopLocation == null || loopLocation.isBlank()) {
            throw new IllegalArgumentException("loopLocation must not be blank");
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
        plannedVectorReads = List.copyOf(Objects.requireNonNull(plannedVectorReads, "plannedVectorReads"));
        if (plannedVectorReads.stream().anyMatch(read -> read == null || read.isBlank())) {
            throw new IllegalArgumentException("plannedVectorReads must not contain blank entries");
        }
    }

    public static GpuIrAutoVectorizationRewriteInsertionOperation from(
            GpuIrAutoVectorizationRewriteCandidatePreview candidate
    ) {
        Objects.requireNonNull(candidate, "candidate");
        return new GpuIrAutoVectorizationRewriteInsertionOperation(
                candidate.loopLocation(),
                candidate.vectorType(),
                candidate.startInclusive(),
                candidate.endExclusive(),
                candidate.plannedVectorReads()
        );
    }

    public String summary() {
        return "insert vector temporaries before " + loopLocation
                + " type=" + vectorType
                + " lanes=" + startInclusive + ".." + (endExclusive - 1)
                + " reads=" + plannedVectorReads;
    }
}
