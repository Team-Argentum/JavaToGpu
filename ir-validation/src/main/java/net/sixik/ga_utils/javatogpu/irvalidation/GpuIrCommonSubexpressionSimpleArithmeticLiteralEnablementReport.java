package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Compact read-only checklist for future literal fingerprint enablement.
 *
 * <p>This report summarizes the proof, runtime, decision, and parity artifacts into one CI-friendly
 * verdict. It does not enable production fingerprints or rewrites.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport(
        String methodName,
        String verdict,
        int previewCandidateCount,
        int uniqueCanonicalKeyCount,
        boolean numericSemanticsFullyProven,
        boolean runtimeEquivalenceSuccessful,
        boolean evidenceComplete,
        boolean readyForProduction,
        String decisionReadiness,
        String parityReadiness,
        int previewOnlyKeyCount,
        List<String> blockers
) {
    private static final String VERDICT_NO_PREVIEW = "notReady/noPreviewCandidates";
    private static final String VERDICT_NUMERIC_MISSING = "notReady/numericSemanticsMissing";
    private static final String VERDICT_RUNTIME_MISSING = "notReady/runtimeMissing";
    private static final String VERDICT_PREVIEW_ONLY = "notReady/previewOnlyBlastRadius";
    private static final String VERDICT_DISABLED = "evidenceCompleteButDisabled";
    private static final String VERDICT_READY = "readyForProduction";

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (verdict == null || verdict.isBlank()) {
            throw new IllegalArgumentException("verdict must not be blank");
        }
        if (previewCandidateCount < 0) {
            throw new IllegalArgumentException("previewCandidateCount must be non-negative");
        }
        if (uniqueCanonicalKeyCount < 0) {
            throw new IllegalArgumentException("uniqueCanonicalKeyCount must be non-negative");
        }
        if (decisionReadiness == null || decisionReadiness.isBlank()) {
            throw new IllegalArgumentException("decisionReadiness must not be blank");
        }
        if (parityReadiness == null || parityReadiness.isBlank()) {
            throw new IllegalArgumentException("parityReadiness must not be blank");
        }
        if (previewOnlyKeyCount < 0) {
            throw new IllegalArgumentException("previewOnlyKeyCount must be non-negative");
        }
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport from(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalenceReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport fingerprintDecisionReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport fingerprintParityReport
    ) {
        Objects.requireNonNull(canonicalizationReport, "canonicalizationReport");
        Objects.requireNonNull(numericSemanticsProofReport, "numericSemanticsProofReport");
        Objects.requireNonNull(runtimeEquivalenceReport, "runtimeEquivalenceReport");
        Objects.requireNonNull(fingerprintDecisionReport, "fingerprintDecisionReport");
        Objects.requireNonNull(fingerprintParityReport, "fingerprintParityReport");
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport(
                canonicalizationReport.methodName(),
                verdict(
                        canonicalizationReport,
                        numericSemanticsProofReport,
                        runtimeEquivalenceReport,
                        fingerprintDecisionReport,
                        fingerprintParityReport
                ),
                canonicalizationReport.candidateCount(),
                canonicalizationReport.uniqueCanonicalKeyCount(),
                numericSemanticsProofReport.fullyProven(),
                runtimeEquivalenceReport.successful(),
                fingerprintDecisionReport.evidenceComplete(),
                fingerprintDecisionReport.readyForProductionFingerprintIntegration(),
                fingerprintDecisionReport.readiness(),
                fingerprintParityReport.readiness(),
                fingerprintParityReport.previewOnlyKeyCount(),
                blockers(fingerprintDecisionReport, fingerprintParityReport)
        );
    }

    public boolean hasPreviewCandidates() {
        return previewCandidateCount > 0;
    }

    public boolean hasPreviewOnlyKeys() {
        return previewOnlyKeyCount > 0;
    }

    public int blockerCount() {
        return blockers.size();
    }

    public Optional<String> firstBlocker() {
        return blockers.stream().findFirst();
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Verdict", verdict);
        values.put(prefix + "PreviewCandidates", Integer.toString(previewCandidateCount));
        values.put(prefix + "UniqueCanonicalKeys", Integer.toString(uniqueCanonicalKeyCount));
        values.put(prefix + "NumericSemanticsFullyProven", Boolean.toString(numericSemanticsFullyProven));
        values.put(prefix + "RuntimeEquivalenceSuccessful", Boolean.toString(runtimeEquivalenceSuccessful));
        values.put(prefix + "EvidenceComplete", Boolean.toString(evidenceComplete));
        values.put(prefix + "ReadyForProduction", Boolean.toString(readyForProduction));
        values.put(prefix + "DecisionReadiness", decisionReadiness);
        values.put(prefix + "ParityReadiness", parityReadiness);
        values.put(prefix + "PreviewOnlyKeys", Integer.toString(previewOnlyKeyCount));
        values.put(prefix + "HasPreviewOnlyKeys", Boolean.toString(hasPreviewOnlyKeys()));
        values.put(prefix + "Blockers", listSummary(blockers));
        values.put(prefix + "BlockerCount", Integer.toString(blockerCount()));
        firstBlocker().ifPresent(blocker -> values.put(prefix + "FirstBlocker", blocker));
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseSimpleArithmeticLiteralEnablement");
    }

    public String summary() {
        return "CSE simple arithmetic literal enablement method=" + methodName
                + " verdict=" + verdict
                + " previewCandidates=" + previewCandidateCount
                + " uniqueCanonicalKeys=" + uniqueCanonicalKeyCount
                + " numericSemanticsFullyProven=" + numericSemanticsFullyProven
                + " runtimeEquivalenceSuccessful=" + runtimeEquivalenceSuccessful
                + " evidenceComplete=" + evidenceComplete
                + " readyForProduction=" + readyForProduction
                + " decisionReadiness=" + decisionReadiness
                + " parityReadiness=" + parityReadiness
                + " previewOnlyKeys=" + previewOnlyKeyCount
                + " blockers=" + listSummary(blockers);
    }

    private static String verdict(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport numericSemanticsProofReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalenceReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport fingerprintDecisionReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport fingerprintParityReport
    ) {
        if (!canonicalizationReport.hasCandidates()) {
            return VERDICT_NO_PREVIEW;
        }
        if (!numericSemanticsProofReport.fullyProven()) {
            return VERDICT_NUMERIC_MISSING;
        }
        if (!runtimeEquivalenceReport.successful()) {
            return VERDICT_RUNTIME_MISSING;
        }
        if (fingerprintParityReport.hasPreviewOnlyKeys()) {
            return VERDICT_PREVIEW_ONLY;
        }
        if (!fingerprintDecisionReport.readyForProductionFingerprintIntegration()) {
            return VERDICT_DISABLED;
        }
        return VERDICT_READY;
    }

    private static List<String> blockers(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport fingerprintDecisionReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport fingerprintParityReport
    ) {
        java.util.LinkedHashSet<String> blockers = new java.util.LinkedHashSet<>();
        blockers.addAll(fingerprintDecisionReport.blockingReasons());
        blockers.addAll(fingerprintParityReport.blockers());
        return List.copyOf(blockers);
    }

    private static String listSummary(List<String> values) {
        return values.stream().collect(Collectors.joining(",", "[", "]"));
    }
}
