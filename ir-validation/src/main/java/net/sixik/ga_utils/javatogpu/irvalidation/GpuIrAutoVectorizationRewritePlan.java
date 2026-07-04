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
        List<GpuIrAutoVectorizationRewriteInsertionOperation> insertionOperations,
        List<GpuIrAutoVectorizationRewriteReplacementOperation> replacementOperations,
        List<String> guardDiagnostics,
        List<GpuIrAutoVectorizationRewriteGuardDiagnostic> typedGuardDiagnostics
) {
    public GpuIrAutoVectorizationRewritePlan {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        insertionOperations = List.copyOf(Objects.requireNonNull(insertionOperations, "insertionOperations"));
        replacementOperations = List.copyOf(Objects.requireNonNull(replacementOperations, "replacementOperations"));
        guardDiagnostics = List.copyOf(Objects.requireNonNull(guardDiagnostics, "guardDiagnostics"));
        typedGuardDiagnostics = List.copyOf(Objects.requireNonNull(typedGuardDiagnostics, "typedGuardDiagnostics"));
        if (insertionOperations.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("insertionOperations must not contain null entries");
        }
        if (replacementOperations.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("replacementOperations must not contain null entries");
        }
        if (guardDiagnostics.stream().anyMatch(diagnostic -> diagnostic == null || diagnostic.isBlank())) {
            throw new IllegalArgumentException("guardDiagnostics must not contain blank entries");
        }
        if (typedGuardDiagnostics.size() != guardDiagnostics.size()) {
            throw new IllegalArgumentException("typedGuardDiagnostics must align with guardDiagnostics");
        }
        for (int index = 0; index < guardDiagnostics.size(); index++) {
            if (!guardDiagnostics.get(index).equals(typedGuardDiagnostics.get(index).summary())) {
                throw new IllegalArgumentException("typedGuardDiagnostics must match guardDiagnostics summaries");
            }
        }
        if (candidates.size() != insertionOperations.size() || candidates.size() != replacementOperations.size()) {
            throw new IllegalArgumentException("plan operations must align with candidate count");
        }
    }

    public GpuIrAutoVectorizationRewritePlan(
            String methodName,
            List<GpuIrAutoVectorizationRewriteCandidatePreview> candidates,
            List<String> insertionPreviews,
            List<String> replacementPreviews,
            List<String> guardDiagnostics
    ) {
        this(
                methodName,
                candidates,
                insertionPreviews.stream()
                        .map(GpuIrAutoVectorizationRewritePlan::legacyInsertionOperation)
                        .toList(),
                replacementPreviews.stream()
                        .map(GpuIrAutoVectorizationRewritePlan::legacyReplacementOperation)
                        .toList(),
                guardDiagnostics,
                guardDiagnostics.stream()
                        .map(GpuIrAutoVectorizationRewriteGuardDiagnostic::fromLegacySummary)
                        .toList()
        );
    }

    public static GpuIrAutoVectorizationRewritePlan from(GpuIrAutoVectorizationPreview preview) {
        Objects.requireNonNull(preview, "preview");
        return new GpuIrAutoVectorizationRewritePlan(
                preview.methodName(),
                preview.rewriteCandidates(),
                preview.rewriteCandidates().stream()
                        .map(GpuIrAutoVectorizationRewriteInsertionOperation::from)
                        .toList(),
                preview.rewriteCandidates().stream()
                        .map(GpuIrAutoVectorizationRewriteReplacementOperation::from)
                        .toList(),
                preview.rewriteCandidates().stream()
                        .flatMap(candidate -> typedGuardDiagnostics(candidate).stream())
                        .map(GpuIrAutoVectorizationRewriteGuardDiagnostic::summary)
                        .toList(),
                preview.rewriteCandidates().stream()
                        .flatMap(candidate -> typedGuardDiagnostics(candidate).stream())
                        .toList()
        );
    }

    private static GpuIrAutoVectorizationRewriteInsertionOperation legacyInsertionOperation(String preview) {
        if (preview == null || preview.isBlank()) {
            throw new IllegalArgumentException("insertionPreviews must not contain blank entries");
        }
        return new GpuIrAutoVectorizationRewriteInsertionOperation(
                "legacy",
                "legacy",
                0,
                1,
                List.of(preview)
        );
    }

    private static GpuIrAutoVectorizationRewriteReplacementOperation legacyReplacementOperation(String preview) {
        if (preview == null || preview.isBlank()) {
            throw new IllegalArgumentException("replacementPreviews must not contain blank entries");
        }
        return new GpuIrAutoVectorizationRewriteReplacementOperation(
                "legacy",
                "legacy",
                0,
                1,
                List.of(preview)
        );
    }

    private static List<String> guardDiagnostics(GpuIrAutoVectorizationRewriteCandidatePreview candidate) {
        return typedGuardDiagnostics(candidate).stream()
                .map(GpuIrAutoVectorizationRewriteGuardDiagnostic::summary)
                .toList();
    }

    private static List<GpuIrAutoVectorizationRewriteGuardDiagnostic> typedGuardDiagnostics(
            GpuIrAutoVectorizationRewriteCandidatePreview candidate
    ) {
        java.util.ArrayList<GpuIrAutoVectorizationRewriteGuardDiagnostic> diagnostics = new java.util.ArrayList<>();
        if (candidate.vectorType().startsWith("unknown")) {
            diagnostics.add(guard(
                    GpuIrAutoVectorizationRewriteGuardFamily.UNKNOWN_VECTOR_TYPE,
                    candidate.loopLocation(),
                    "unknown vector type blocks rewrite operations"
            ));
        }
        if (candidate.laneCount() == 3) {
            diagnostics.add(guard(
                    GpuIrAutoVectorizationRewriteGuardFamily.BACKEND_VECTOR_WIDTH,
                    candidate.loopLocation(),
                    "backend vector width x3 requires explicit ABI support before rewrite operations"
            ));
        }
        if ("double".equals(candidate.scalarElementType())) {
            diagnostics.add(guard(
                    GpuIrAutoVectorizationRewriteGuardFamily.BACKEND_DOUBLE_VECTOR,
                    candidate.loopLocation(),
                    "backend double vector type " + candidate.vectorType()
                            + " requires explicit device capability support before rewrite operations"
            ));
        }
        for (String targetArray : candidate.targetArrays()) {
            if (candidate.sourceArrays().contains(targetArray)) {
                diagnostics.add(guard(
                        GpuIrAutoVectorizationRewriteGuardFamily.TARGET_SOURCE_ALIAS,
                        candidate.loopLocation(),
                        "target array `" + targetArray + "` is also read by the candidate"
                ));
            }
        }
        diagnostics.addAll(candidate.memoryGuardDiagnosticDetails());
        return diagnostics;
    }

    private static GpuIrAutoVectorizationRewriteGuardDiagnostic guard(
            GpuIrAutoVectorizationRewriteGuardFamily family,
            String location,
            String message
    ) {
        return new GpuIrAutoVectorizationRewriteGuardDiagnostic(family, location, message);
    }

    public boolean hasOperations() {
        return !candidates.isEmpty();
    }

    public int candidateCount() {
        return candidates.size();
    }

    public int insertionCount() {
        return hasGuardDiagnostics() ? 0 : insertionOperations.size();
    }

    public int replacementCount() {
        return hasGuardDiagnostics() ? 0 : replacementOperations.size();
    }

    public int operationCount() {
        return insertionCount() + replacementCount();
    }

    public int blockedCandidateCount() {
        return (int) candidates.stream()
                .filter(candidate -> !guardDiagnostics(candidate).isEmpty())
                .count();
    }

    public boolean hasBlockedCandidates() {
        return blockedCandidateCount() > 0;
    }

    public boolean hasGuardDiagnostics() {
        return !guardDiagnostics.isEmpty();
    }

    public Map<String, Long> guardFamilyCounts() {
        return guardFamilyTypeCounts().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().artifactValue(),
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    public Map<GpuIrAutoVectorizationRewriteGuardFamily, Long> guardFamilyTypeCounts() {
        return typedGuardDiagnostics.stream()
                .collect(Collectors.groupingBy(
                        GpuIrAutoVectorizationRewriteGuardDiagnostic::family,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public static String guardFamily(String diagnostic) {
        return guardFamilyType(diagnostic).artifactValue();
    }

    public static GpuIrAutoVectorizationRewriteGuardFamily guardFamilyType(String diagnostic) {
        return GpuIrAutoVectorizationRewriteGuardDiagnostic.familyFromLegacySummary(diagnostic);
    }

    public int rawInsertionPreviewCount() {
        return insertionOperations.size();
    }

    public int rawReplacementPreviewCount() {
        return replacementOperations.size();
    }

    public int rawInsertionOperationCount() {
        return insertionOperations.size();
    }

    public int rawReplacementOperationCount() {
        return replacementOperations.size();
    }

    public List<String> insertionPreviews() {
        return insertionOperations.stream()
                .map(GpuIrAutoVectorizationRewriteInsertionOperation::summary)
                .toList();
    }

    public List<String> replacementPreviews() {
        return replacementOperations.stream()
                .map(GpuIrAutoVectorizationRewriteReplacementOperation::summary)
                .toList();
    }

    public GpuIrAutoVectorizationRewritePolicy rewritePolicy() {
        return GpuIrAutoVectorizationRewritePolicy.from(this);
    }

    public String summary() {
        return "auto-vectorization rewrite plan method=" + methodName
                + " candidates=" + candidateCount()
                + " blockedCandidates=" + blockedCandidateCount()
                + " insertions=" + insertionCount()
                + " replacements=" + replacementCount()
                + " operations=" + operationCount()
                + (hasGuardDiagnostics() ? " guardFamilies=" + guardFamilyCounts() + " guardDiagnostics=" + guardDiagnostics : "")
                + (hasOperations() ? " firstInsertion=" + insertionOperations.get(0).summary() : "");
    }
}
