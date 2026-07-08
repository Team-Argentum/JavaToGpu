package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only local-expression dominance proof summary for same-statement CSE candidates.
 */
public record GpuIrCommonSubexpressionLocalExpressionDominanceReport(
        GpuIrCommonSubexpressionRewritePreview preview
) {
    public GpuIrCommonSubexpressionLocalExpressionDominanceReport {
        preview = Objects.requireNonNull(preview, "preview");
    }

    public int provenCandidateCount() {
        return (int) preview.replacementEdits().stream()
                .filter(this::isLocalExpressionReplacement)
                .map(GpuIrCommonSubexpressionRewriteEdit::insertionAnchorLocation)
                .distinct()
                .count();
    }

    public int provenReplacementCount() {
        return (int) preview.replacementEdits().stream()
                .filter(this::isLocalExpressionReplacement)
                .count();
    }

    public int blockedCandidateCount() {
        return (int) preview.skippedDiagnostics().stream()
                .filter(this::isBlockedLocalExpressionCandidate)
                .count();
    }

    public boolean hasLocalExpressionEvidence() {
        return provenCandidateCount() > 0 || blockedCandidateCount() > 0;
    }

    public Map<String, String> artifactFields(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "ProvenCandidates", Integer.toString(provenCandidateCount()));
        values.put(prefix + "ProvenReplacements", Integer.toString(provenReplacementCount()));
        values.put(prefix + "BlockedCandidates", Integer.toString(blockedCandidateCount()));
        values.put(prefix + "HasEvidence", Boolean.toString(hasLocalExpressionEvidence()));
        firstBlockedCandidate().ifPresent(diagnostic -> {
            values.put(prefix + "FirstBlockedReason", diagnostic.reason().name());
            values.put(prefix + "FirstBlockedDominanceStatus", diagnostic.dominanceStatus().artifactValue());
            values.put(prefix + "FirstBlockedSummary", diagnostic.summary());
        });
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseLocalExpression");
    }

    public String summary() {
        return "CSE local-expression dominance provenCandidates=" + provenCandidateCount()
                + " provenReplacements=" + provenReplacementCount()
                + " blockedCandidates=" + blockedCandidateCount()
                + " hasEvidence=" + hasLocalExpressionEvidence()
                + firstBlockedCandidate()
                .map(diagnostic -> " firstBlocked=" + diagnostic.summary())
                .orElse("");
    }

    private java.util.Optional<GpuIrCommonSubexpressionSkippedDiagnostic> firstBlockedCandidate() {
        return preview.skippedDiagnostics().stream()
                .filter(this::isBlockedLocalExpressionCandidate)
                .findFirst();
    }

    private boolean isLocalExpressionReplacement(GpuIrCommonSubexpressionRewriteEdit edit) {
        return sameTopLevelStatement(edit.insertionAnchorLocation(), edit.replacementLocation())
                && isSupportedSameStatementValueLocation(edit.insertionAnchorLocation())
                && isSupportedSameStatementValueLocation(edit.replacementLocation());
    }

    private boolean isBlockedLocalExpressionCandidate(GpuIrCommonSubexpressionSkippedDiagnostic diagnostic) {
        return diagnostic.dominanceStatus() == GpuIrCommonSubexpressionDominanceStatus.REQUIRES_LOCAL_EXPRESSION_DOMINANCE
                && diagnostic.locations().size() > 1
                && diagnostic.locations().stream().allMatch(this::isSupportedTopLevelLocation)
                && sameTopLevelStatement(diagnostic.locations().get(0), diagnostic.locations().get(diagnostic.locations().size() - 1));
    }

    private boolean isSupportedSameStatementValueLocation(String location) {
        return isSupportedTopLevelLocation(location)
                && (location.contains(".initializer") || location.contains(".value") || location.contains(".return"));
    }

    private boolean isSupportedTopLevelLocation(String location) {
        return GpuIrCommonSubexpressionLocation.parse(location).topLevelStatementIndex().isPresent();
    }

    private boolean sameTopLevelStatement(String left, String right) {
        java.util.OptionalInt leftIndex = GpuIrCommonSubexpressionLocation.parse(left).topLevelStatementIndex();
        java.util.OptionalInt rightIndex = GpuIrCommonSubexpressionLocation.parse(right).topLevelStatementIndex();
        return leftIndex.isPresent() && rightIndex.isPresent() && leftIndex.getAsInt() == rightIndex.getAsInt();
    }
}
