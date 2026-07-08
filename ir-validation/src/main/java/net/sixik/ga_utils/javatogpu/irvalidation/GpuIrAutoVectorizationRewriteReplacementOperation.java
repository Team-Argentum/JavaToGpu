package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Objects;

/**
 * Typed preview of the scalar lane loop replacement a future vector rewrite may perform.
 */
public record GpuIrAutoVectorizationRewriteReplacementOperation(
        String loopLocation,
        String inductionVariable,
        int startInclusive,
        int endExclusive,
        List<String> plannedVectorWrites
) {
    public GpuIrAutoVectorizationRewriteReplacementOperation {
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
        plannedVectorWrites = List.copyOf(Objects.requireNonNull(plannedVectorWrites, "plannedVectorWrites"));
        if (plannedVectorWrites.isEmpty() || plannedVectorWrites.stream().anyMatch(write -> write == null || write.isBlank())) {
            throw new IllegalArgumentException("plannedVectorWrites must contain non-blank entries");
        }
    }

    public static GpuIrAutoVectorizationRewriteReplacementOperation from(
            GpuIrAutoVectorizationRewriteCandidatePreview candidate
    ) {
        Objects.requireNonNull(candidate, "candidate");
        return new GpuIrAutoVectorizationRewriteReplacementOperation(
                candidate.loopLocation(),
                candidate.inductionVariable(),
                candidate.startInclusive(),
                candidate.endExclusive(),
                candidate.plannedVectorWrites()
        );
    }

    public String summary() {
        return "replace lane loop " + loopLocation
                + " with vector writes " + plannedVectorWrites;
    }
}
