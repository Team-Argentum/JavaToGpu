package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.Objects;

/**
 * Compact read-only preview for a warning-free candidate that may be rewritten later.
 */
public record GpuIrAutoVectorizationRewriteCandidatePreview(
        String loopLocation,
        String inductionVariable,
        int startInclusive,
        int endExclusive,
        int laneCount,
        int assignmentCount,
        int priorityScore,
        String vectorWidth,
        String scalarElementType,
        String vectorType,
        List<String> plannedVectorWrites,
        List<String> plannedVectorReads,
        List<String> memoryGuardDiagnostics,
        List<String> targetArrays,
        List<String> sourceArrays
) {
    public GpuIrAutoVectorizationRewriteCandidatePreview {
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
        if (laneCount <= 0) {
            throw new IllegalArgumentException("laneCount must be positive");
        }
        if (laneCount != endExclusive - startInclusive) {
            throw new IllegalArgumentException("laneCount must match the lane range");
        }
        if (assignmentCount <= 0) {
            throw new IllegalArgumentException("assignmentCount must be positive");
        }
        if (priorityScore <= 0) {
            throw new IllegalArgumentException("priorityScore must be positive");
        }
        if (vectorWidth == null || vectorWidth.isBlank()) {
            throw new IllegalArgumentException("vectorWidth must not be blank");
        }
        if (scalarElementType == null || scalarElementType.isBlank()) {
            throw new IllegalArgumentException("scalarElementType must not be blank");
        }
        if (vectorType == null || vectorType.isBlank()) {
            throw new IllegalArgumentException("vectorType must not be blank");
        }
        plannedVectorWrites = List.copyOf(Objects.requireNonNull(plannedVectorWrites, "plannedVectorWrites"));
        if (plannedVectorWrites.isEmpty() || plannedVectorWrites.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("plannedVectorWrites must contain non-blank entries");
        }
        plannedVectorReads = List.copyOf(Objects.requireNonNull(plannedVectorReads, "plannedVectorReads"));
        if (plannedVectorReads.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("plannedVectorReads must not contain blank entries");
        }
        memoryGuardDiagnostics = List.copyOf(Objects.requireNonNull(memoryGuardDiagnostics, "memoryGuardDiagnostics"));
        if (memoryGuardDiagnostics.stream().anyMatch(diagnostic -> diagnostic == null || diagnostic.isBlank())) {
            throw new IllegalArgumentException("memoryGuardDiagnostics must not contain blank entries");
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
                candidate.inductionVariable(),
                candidate.startInclusive(),
                candidate.endExclusive(),
                candidate.laneCount(),
                candidate.assignmentCount(),
                candidate.priorityScore(),
                "x" + candidate.laneCount(),
                candidate.scalarElementType(),
                candidate.vectorType(),
                vectorAccesses("write", candidate.targetArrays(), candidate.inductionVariable(), candidate.startInclusive(), candidate.endExclusive()),
                vectorAccesses("read", candidate.sourceArrays(), candidate.inductionVariable(), candidate.startInclusive(), candidate.endExclusive()),
                candidate.memoryGuardDiagnostics(),
                candidate.targetArrays(),
                candidate.sourceArrays()
        );
    }

    private static List<String> vectorAccesses(
            String accessKind,
            List<String> arrays,
            String inductionVariable,
            int startInclusive,
            int endExclusive
    ) {
        return arrays.stream()
                .map(array -> accessKind + " " + array + "[" + inductionVariable + "=" + startInclusive + ".." + (endExclusive - 1) + "]")
                .toList();
    }

    public String summary() {
        return "auto-vectorization rewrite candidate at " + loopLocation
                + " induction=" + inductionVariable
                + " laneRange=" + startInclusive + ".." + (endExclusive - 1)
                + " lanes=" + laneCount
                + " vectorWidth=" + vectorWidth
                + " scalarElementType=" + scalarElementType
                + " vectorType=" + vectorType
                + " assignments=" + assignmentCount
                + " priorityScore=" + priorityScore
                + " plannedWrites=" + plannedVectorWrites
                + " plannedReads=" + plannedVectorReads
                + (memoryGuardDiagnostics.isEmpty() ? "" : " memoryGuardDiagnostics=" + memoryGuardDiagnostics)
                + " targets=" + targetArrays
                + " sources=" + sourceArrays;
    }
}
