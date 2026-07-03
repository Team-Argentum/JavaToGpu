package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only auto-vectorization scan output. It reports candidates but never rewrites IR.
 */
public record GpuIrAutoVectorizationReport(
        String methodName,
        List<GpuIrAutoVectorizationCandidate> candidates,
        List<GpuIrAutoVectorizationRejectionDiagnostic> rejections
) {
    public GpuIrAutoVectorizationReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        candidates = List.copyOf(candidates);
        rejections = List.copyOf(rejections);
    }

    public GpuIrAutoVectorizationReport(String methodName, List<GpuIrAutoVectorizationCandidate> candidates) {
        this(methodName, candidates, List.of());
    }

    public boolean hasCandidates() {
        return !candidates.isEmpty();
    }

    public boolean hasRejections() {
        return !rejections.isEmpty();
    }

    public int candidateCount() {
        return candidates.size();
    }

    public int rejectionCount() {
        return rejections.size();
    }

    public int totalAssignmentCount() {
        return candidates.stream()
                .mapToInt(GpuIrAutoVectorizationCandidate::assignmentCount)
                .sum();
    }

    public int totalWarningCount() {
        return candidates.stream()
                .mapToInt(GpuIrAutoVectorizationCandidate::warningCount)
                .sum();
    }

    public List<GpuIrAutoVectorizationCandidate> candidatesWithAliasWarnings() {
        return candidates.stream()
                .filter(GpuIrAutoVectorizationCandidate::hasAliasWarnings)
                .toList();
    }

    public List<GpuIrAutoVectorizationCandidate> candidatesWithRepeatedTargetWarnings() {
        return candidates.stream()
                .filter(GpuIrAutoVectorizationCandidate::hasRepeatedTargetWarnings)
                .toList();
    }

    public List<GpuIrAutoVectorizationCandidate> candidatesWithCrossLaneReadWarnings() {
        return candidates.stream()
                .filter(GpuIrAutoVectorizationCandidate::hasCrossLaneReadWarnings)
                .toList();
    }

    public List<GpuIrAutoVectorizationCandidate> candidatesWithNonLaneReadWarnings() {
        return candidates.stream()
                .filter(GpuIrAutoVectorizationCandidate::hasNonLaneReadWarnings)
                .toList();
    }

    public List<GpuIrAutoVectorizationCandidate> candidatesWithWarnings() {
        return candidates.stream()
                .filter(GpuIrAutoVectorizationCandidate::hasWarnings)
                .sorted(Comparator.comparingInt(GpuIrAutoVectorizationCandidate::priorityScore).reversed())
                .toList();
    }

    public List<GpuIrAutoVectorizationCandidate> rewritePriorityCandidates() {
        return candidates.stream()
                .filter(GpuIrAutoVectorizationCandidate::isRewritePriorityCandidate)
                .sorted(Comparator.comparingInt(GpuIrAutoVectorizationCandidate::priorityScore).reversed())
                .toList();
    }

    public List<GpuIrAutoVectorizationRewriteCandidatePreview> previewRewritePriorityCandidates() {
        return rewritePriorityCandidates().stream()
                .map(GpuIrAutoVectorizationRewriteCandidatePreview::from)
                .toList();
    }

    public GpuIrAutoVectorizationPreview preview() {
        return new GpuIrAutoVectorizationPreview(
                methodName,
                previewRewritePriorityCandidates(),
                previewWarningDiagnostics(),
                rejections
        );
    }

    public List<GpuIrAutoVectorizationCandidate> topCandidates() {
        return candidates.stream()
                .sorted(Comparator.comparingInt(GpuIrAutoVectorizationCandidate::priorityScore).reversed())
                .toList();
    }

    public boolean hasAliasWarnings() {
        return !candidatesWithAliasWarnings().isEmpty();
    }

    public boolean hasRepeatedTargetWarnings() {
        return !candidatesWithRepeatedTargetWarnings().isEmpty();
    }

    public boolean hasCrossLaneReadWarnings() {
        return !candidatesWithCrossLaneReadWarnings().isEmpty();
    }

    public boolean hasNonLaneReadWarnings() {
        return !candidatesWithNonLaneReadWarnings().isEmpty();
    }

    public boolean hasCandidateWarnings() {
        return !candidatesWithWarnings().isEmpty();
    }

    public Map<String, Long> warningFamilyCounts() {
        return java.util.stream.Stream.of(
                        Map.entry("alias", (long) candidatesWithAliasWarnings().size()),
                        Map.entry("repeatedTarget", (long) candidatesWithRepeatedTargetWarnings().size()),
                        Map.entry("crossLaneRead", (long) candidatesWithCrossLaneReadWarnings().size()),
                        Map.entry("nonLaneRead", (long) candidatesWithNonLaneReadWarnings().size())
                )
                .filter(entry -> entry.getValue() > 0)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
    }

    public List<GpuIrAutoVectorizationWarningDiagnostic> previewWarningDiagnostics() {
        return candidatesWithWarnings().stream()
                .map(GpuIrAutoVectorizationWarningDiagnostic::from)
                .toList();
    }

    public Map<String, List<GpuIrAutoVectorizationWarningDiagnostic>> previewWarningDiagnosticsByLocation() {
        return previewWarningDiagnostics().stream()
                .collect(Collectors.groupingBy(
                        GpuIrAutoVectorizationWarningDiagnostic::loopLocation,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    public Map<GpuIrAutoVectorizationRejectionReason, List<GpuIrAutoVectorizationRejectionDiagnostic>> rejectionsByReason() {
        return rejections.stream()
                .collect(Collectors.groupingBy(
                        GpuIrAutoVectorizationRejectionDiagnostic::reason,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    public Map<GpuIrAutoVectorizationRejectionReason, Long> rejectionReasonCounts() {
        return rejections.stream()
                .collect(Collectors.groupingBy(
                        GpuIrAutoVectorizationRejectionDiagnostic::reason,
                        java.util.LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public String summary() {
        return "auto-vectorization method=" + methodName
                + " candidates=" + candidateCount()
                + " rewritePriorityCandidates=" + rewritePriorityCandidates().size()
                + " rewritePreviews=" + previewRewritePriorityCandidates().size()
                + " warnings=" + candidatesWithWarnings().size()
                + " totalWarnings=" + totalWarningCount()
                + (hasCandidateWarnings() ? " warningFamilies=" + warningFamilyCounts() : "")
                + " rejections=" + rejectionCount()
                + (hasRejections() ? " rejectionReasons=" + rejectionReasonCounts() : "");
    }
}
