package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Read-only outline of the IR edits a future auto-vectorization rewrite may perform.
 *
 * <p>The plan deliberately stores diagnostics, not mutable IR nodes. That keeps strict
 * validation useful for CI while avoiding accidental production rewrites before the
 * vector optimizer has full dominance, aliasing, and backend support proofs.</p>
 */
public record GpuIrAutoVectorizationRewritePlan(
        String methodName,
        List<GpuIrAutoVectorizationRewriteCandidatePreview> candidates,
        List<String> insertionPreviews,
        List<String> replacementPreviews,
        List<String> guardDiagnostics
) {
    public GpuIrAutoVectorizationRewritePlan {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        insertionPreviews = List.copyOf(Objects.requireNonNull(insertionPreviews, "insertionPreviews"));
        replacementPreviews = List.copyOf(Objects.requireNonNull(replacementPreviews, "replacementPreviews"));
        guardDiagnostics = List.copyOf(Objects.requireNonNull(guardDiagnostics, "guardDiagnostics"));
        if (insertionPreviews.stream().anyMatch(preview -> preview == null || preview.isBlank())) {
            throw new IllegalArgumentException("insertionPreviews must not contain blank entries");
        }
        if (replacementPreviews.stream().anyMatch(preview -> preview == null || preview.isBlank())) {
            throw new IllegalArgumentException("replacementPreviews must not contain blank entries");
        }
        if (guardDiagnostics.stream().anyMatch(diagnostic -> diagnostic == null || diagnostic.isBlank())) {
            throw new IllegalArgumentException("guardDiagnostics must not contain blank entries");
        }
        if (candidates.size() != insertionPreviews.size() || candidates.size() != replacementPreviews.size()) {
            throw new IllegalArgumentException("plan previews must align with candidate count");
        }
    }

    public static GpuIrAutoVectorizationRewritePlan from(GpuIrAutoVectorizationPreview preview) {
        Objects.requireNonNull(preview, "preview");
        return new GpuIrAutoVectorizationRewritePlan(
                preview.methodName(),
                preview.rewriteCandidates(),
                preview.rewriteCandidates().stream()
                        .map(GpuIrAutoVectorizationRewritePlan::insertionPreview)
                        .toList(),
                preview.rewriteCandidates().stream()
                        .map(GpuIrAutoVectorizationRewritePlan::replacementPreview)
                        .toList(),
                preview.rewriteCandidates().stream()
                        .flatMap(candidate -> guardDiagnostics(candidate).stream())
                        .toList()
        );
    }

    private static String insertionPreview(GpuIrAutoVectorizationRewriteCandidatePreview candidate) {
        return "insert vector temporaries before " + candidate.loopLocation()
                + " type=" + candidate.vectorType()
                + " lanes=" + candidate.startInclusive() + ".." + (candidate.endExclusive() - 1)
                + " reads=" + candidate.plannedVectorReads();
    }

    private static String replacementPreview(GpuIrAutoVectorizationRewriteCandidatePreview candidate) {
        return "replace lane loop " + candidate.loopLocation()
                + " with vector writes " + candidate.plannedVectorWrites();
    }

    private static List<String> guardDiagnostics(GpuIrAutoVectorizationRewriteCandidatePreview candidate) {
        java.util.ArrayList<String> diagnostics = new java.util.ArrayList<>();
        if (candidate.vectorType().startsWith("unknown")) {
            diagnostics.add("guard " + candidate.loopLocation() + ": unknown vector type blocks rewrite operations");
        }
        for (String targetArray : candidate.targetArrays()) {
            if (candidate.sourceArrays().contains(targetArray)) {
                diagnostics.add("guard " + candidate.loopLocation()
                        + ": target array `" + targetArray + "` is also read by the candidate");
            }
        }
        diagnostics.addAll(candidate.memoryGuardDiagnostics());
        return diagnostics;
    }

    public boolean hasOperations() {
        return !candidates.isEmpty();
    }

    public int candidateCount() {
        return candidates.size();
    }

    public int insertionCount() {
        return hasGuardDiagnostics() ? 0 : insertionPreviews.size();
    }

    public int replacementCount() {
        return hasGuardDiagnostics() ? 0 : replacementPreviews.size();
    }

    public int operationCount() {
        return insertionCount() + replacementCount();
    }

    public boolean hasGuardDiagnostics() {
        return !guardDiagnostics.isEmpty();
    }

    public Map<String, Long> guardFamilyCounts() {
        return guardDiagnostics.stream()
                .collect(Collectors.groupingBy(
                        GpuIrAutoVectorizationRewritePlan::guardFamily,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    private static String guardFamily(String diagnostic) {
        if (diagnostic.contains("unknown vector type")) {
            return "unknownVectorType";
        }
        if (diagnostic.contains("is also read by the candidate")) {
            return "targetSourceAlias";
        }
        if (diagnostic.contains("writes source array")) {
            return "neighborSourceWrite";
        }
        if (diagnostic.contains("writes target array")) {
            return "neighborTargetWrite";
        }
        return "other";
    }

    public int rawInsertionPreviewCount() {
        return insertionPreviews.size();
    }

    public int rawReplacementPreviewCount() {
        return replacementPreviews.size();
    }

    public String summary() {
        return "auto-vectorization rewrite plan method=" + methodName
                + " candidates=" + candidateCount()
                + " insertions=" + insertionCount()
                + " replacements=" + replacementCount()
                + " operations=" + operationCount()
                + (hasGuardDiagnostics() ? " guardFamilies=" + guardFamilyCounts() + " guardDiagnostics=" + guardDiagnostics : "")
                + (hasOperations() ? " firstInsertion=" + insertionPreviews.get(0) : "");
    }
}
