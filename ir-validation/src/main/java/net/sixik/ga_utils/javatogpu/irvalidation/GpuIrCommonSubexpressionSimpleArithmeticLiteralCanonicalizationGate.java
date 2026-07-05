package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only promotion gate for literal canonicalization preview keys.
 *
 * <p>The preview can prove that a stable key shape exists, but it still must not be treated as a
 * production fingerprint until numeric semantics, backend behavior, and runtime equivalence have
 * stronger evidence. Keeping that decision explicit makes CI artifacts harder to misread.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate(
        String methodName,
        int previewCandidateCount,
        int uniqueCanonicalKeyCount,
        List<String> blockingReasons
) {
    private static final String REASON_NUMERIC_SEMANTICS = "typedNumericSemanticsNotProven";
    private static final String REASON_RUNTIME_EQUIVALENCE = "runtimeEquivalenceNotProven";
    private static final String REASON_FINGERPRINT_DISABLED = "fingerprintIntegrationDisabled";
    private static final String REASON_NO_PREVIEW_CANDIDATES = "noPreviewCandidates";

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (previewCandidateCount < 0) {
            throw new IllegalArgumentException("previewCandidateCount must be non-negative");
        }
        if (uniqueCanonicalKeyCount < 0) {
            throw new IllegalArgumentException("uniqueCanonicalKeyCount must be non-negative");
        }
        blockingReasons = List.copyOf(Objects.requireNonNull(blockingReasons, "blockingReasons"));
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate from(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport report
    ) {
        return from(
                report,
                GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.from(report)
        );
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate from(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport report,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport
    ) {
        return from(
                report,
                numericSemanticsProofReport,
                GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport.notRun(report, numericSemanticsProofReport)
        );
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate from(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport report,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalenceReport
    ) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(numericSemanticsProofReport, "numericSemanticsProofReport");
        Objects.requireNonNull(runtimeEquivalenceReport, "runtimeEquivalenceReport");
        List<String> blockingReasons = blockingReasons(report, numericSemanticsProofReport, runtimeEquivalenceReport);
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate(
                report.methodName(),
                report.candidateCount(),
                report.uniqueCanonicalKeyCount(),
                blockingReasons
        );
    }

    public boolean hasPreviewCandidates() {
        return previewCandidateCount > 0;
    }

    public boolean canPromoteToFingerprint() {
        return false;
    }

    public boolean blocksRewriteReadiness() {
        return hasPreviewCandidates();
    }

    public int blockingReasonCount() {
        return blockingReasons.size();
    }

    public Optional<String> firstBlockingReason() {
        if (blockingReasons.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(blockingReasons.get(0));
    }

    public String readiness() {
        return hasPreviewCandidates() ? "blockedPreview" : "none";
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "CanPromoteToFingerprint", Boolean.toString(canPromoteToFingerprint()));
        values.put(prefix + "Readiness", readiness());
        values.put(prefix + "PreviewCandidates", Integer.toString(previewCandidateCount));
        values.put(prefix + "UniqueCanonicalKeys", Integer.toString(uniqueCanonicalKeyCount));
        values.put(prefix + "BlocksRewriteReadiness", Boolean.toString(blocksRewriteReadiness()));
        values.put(prefix + "BlockingReasons", blockingReasonSummary());
        values.put(prefix + "BlockingReasonCount", Integer.toString(blockingReasonCount()));
        firstBlockingReason().ifPresent(reason -> values.put(prefix + "FirstBlockingReason", reason));
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseSimpleArithmeticLiteralCanonicalizationGate");
    }

    public String summary() {
        return "CSE simple arithmetic literal canonicalization gate method=" + methodName
                + " previewCandidates=" + previewCandidateCount
                + " uniqueCanonicalKeys=" + uniqueCanonicalKeyCount
                + " readiness=" + readiness()
                + " canPromoteToFingerprint=" + canPromoteToFingerprint()
                + " blocksRewriteReadiness=" + blocksRewriteReadiness()
                + " blockingReasons=" + blockingReasonSummary()
                + firstBlockingReason()
                .map(reason -> " firstBlockingReason=" + reason)
                .orElse("");
    }

    private String blockingReasonSummary() {
        return blockingReasons.stream().collect(Collectors.joining(",", "[", "]"));
    }

    private static List<String> blockingReasons(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport report,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalenceReport
    ) {
        if (!report.hasCandidates()) {
            return List.of(REASON_NO_PREVIEW_CANDIDATES);
        }
        if (!numericSemanticsProofReport.fullyProven()) {
            return List.of(REASON_NUMERIC_SEMANTICS, REASON_RUNTIME_EQUIVALENCE, REASON_FINGERPRINT_DISABLED);
        }
        if (runtimeEquivalenceReport.successful()) {
            return List.of(REASON_FINGERPRINT_DISABLED);
        }
        return List.of(REASON_RUNTIME_EQUIVALENCE, REASON_FINGERPRINT_DISABLED);
    }
}
