package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only decision artifact for future literal canonicalization fingerprint integration.
 *
 * <p>The decision report explains whether the preview evidence is complete enough to consider a
 * future production fingerprint change. It intentionally never mutates IR, never changes canonical
 * fingerprints, and never enables rewrites by itself.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport(
        String methodName,
        int previewCandidateCount,
        int uniqueCanonicalKeyCount,
        boolean numericSemanticsFullyProven,
        boolean runtimeEquivalenceSuccessful,
        boolean canonicalizationGateAllowsPromotion,
        List<String> blockingReasons
) {
    private static final String REASON_NO_PREVIEW_CANDIDATES = "noPreviewCandidates";
    private static final String REASON_NUMERIC_SEMANTICS = "typedNumericSemanticsNotProven";
    private static final String REASON_RUNTIME_EQUIVALENCE = "runtimeEquivalenceNotProven";
    private static final String REASON_PRODUCTION_FINGERPRINT_DISABLED = "productionFingerprintIntegrationDisabled";

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport {
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

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport from(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalenceReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate canonicalizationGate
    ) {
        Objects.requireNonNull(canonicalizationReport, "canonicalizationReport");
        Objects.requireNonNull(numericSemanticsProofReport, "numericSemanticsProofReport");
        Objects.requireNonNull(runtimeEquivalenceReport, "runtimeEquivalenceReport");
        Objects.requireNonNull(canonicalizationGate, "canonicalizationGate");
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport(
                canonicalizationReport.methodName(),
                canonicalizationReport.candidateCount(),
                canonicalizationReport.uniqueCanonicalKeyCount(),
                numericSemanticsProofReport.fullyProven(),
                runtimeEquivalenceReport.successful(),
                canonicalizationGate.canPromoteToFingerprint(),
                blockingReasons(
                        canonicalizationReport,
                        numericSemanticsProofReport,
                        runtimeEquivalenceReport,
                        canonicalizationGate
                )
        );
    }

    public boolean hasPreviewCandidates() {
        return previewCandidateCount > 0;
    }

    public boolean readyForProductionFingerprintIntegration() {
        return false;
    }

    public boolean evidenceComplete() {
        return hasPreviewCandidates()
                && numericSemanticsFullyProven
                && runtimeEquivalenceSuccessful;
    }

    public boolean blockedByProductionDisablementOnly() {
        return evidenceComplete()
                && !canonicalizationGateAllowsPromotion
                && blockingReasons.equals(List.of(REASON_PRODUCTION_FINGERPRINT_DISABLED));
    }

    public String readiness() {
        if (!hasPreviewCandidates()) {
            return "none";
        }
        return evidenceComplete() ? "evidenceCompleteButDisabled" : "blockedPreview";
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

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "ReadyForProduction", Boolean.toString(readyForProductionFingerprintIntegration()));
        values.put(prefix + "Readiness", readiness());
        values.put(prefix + "EvidenceComplete", Boolean.toString(evidenceComplete()));
        values.put(prefix + "BlockedByProductionDisablementOnly", Boolean.toString(blockedByProductionDisablementOnly()));
        values.put(prefix + "PreviewCandidates", Integer.toString(previewCandidateCount));
        values.put(prefix + "UniqueCanonicalKeys", Integer.toString(uniqueCanonicalKeyCount));
        values.put(prefix + "NumericSemanticsFullyProven", Boolean.toString(numericSemanticsFullyProven));
        values.put(prefix + "RuntimeEquivalenceSuccessful", Boolean.toString(runtimeEquivalenceSuccessful));
        values.put(prefix + "CanonicalizationGateAllowsPromotion", Boolean.toString(canonicalizationGateAllowsPromotion));
        values.put(prefix + "BlockingReasons", blockingReasonSummary());
        values.put(prefix + "BlockingReasonCount", Integer.toString(blockingReasonCount()));
        firstBlockingReason().ifPresent(reason -> values.put(prefix + "FirstBlockingReason", reason));
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseSimpleArithmeticLiteralFingerprintDecision");
    }

    public String summary() {
        return "CSE simple arithmetic literal fingerprint decision method=" + methodName
                + " previewCandidates=" + previewCandidateCount
                + " uniqueCanonicalKeys=" + uniqueCanonicalKeyCount
                + " readiness=" + readiness()
                + " readyForProduction=" + readyForProductionFingerprintIntegration()
                + " evidenceComplete=" + evidenceComplete()
                + " numericSemanticsFullyProven=" + numericSemanticsFullyProven
                + " runtimeEquivalenceSuccessful=" + runtimeEquivalenceSuccessful
                + " gateAllowsPromotion=" + canonicalizationGateAllowsPromotion
                + " blockingReasons=" + blockingReasonSummary()
                + firstBlockingReason()
                .map(reason -> " firstBlockingReason=" + reason)
                .orElse("");
    }

    private String blockingReasonSummary() {
        return blockingReasons.stream().collect(Collectors.joining(",", "[", "]"));
    }

    private static List<String> blockingReasons(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalenceReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate canonicalizationGate
    ) {
        if (!canonicalizationReport.hasCandidates()) {
            return List.of(REASON_NO_PREVIEW_CANDIDATES);
        }
        if (!numericSemanticsProofReport.fullyProven()) {
            return List.of(REASON_NUMERIC_SEMANTICS, REASON_RUNTIME_EQUIVALENCE, REASON_PRODUCTION_FINGERPRINT_DISABLED);
        }
        if (!runtimeEquivalenceReport.successful()) {
            return List.of(REASON_RUNTIME_EQUIVALENCE, REASON_PRODUCTION_FINGERPRINT_DISABLED);
        }
        if (!canonicalizationGate.canPromoteToFingerprint()) {
            return List.of(REASON_PRODUCTION_FINGERPRINT_DISABLED);
        }
        return List.of();
    }
}
