package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only parity artifact between literal preview keys and current production CSE output.
 *
 * <p>The report is intentionally evidence-only. It compares preview-key coverage against the
 * current CSE snapshot so future fingerprint changes can estimate blast radius before production
 * canonical expression behavior is changed.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport(
        String methodName,
        int previewCandidateCount,
        int uniquePreviewKeyCount,
        int productionCandidateCount,
        int productionCandidateOverlapCount,
        int previewOnlyKeyCount,
        boolean evidenceComplete,
        boolean readyForProduction,
        String readiness,
        List<String> previewOnlyKeys,
        List<String> productionOverlappingFingerprints,
        List<String> blockers
) {
    private static final String REASON_NO_PREVIEW_CANDIDATES = "noPreviewCandidates";
    private static final String REASON_PREVIEW_ONLY_KEYS = "previewOnlyKeysNotInProductionFingerprints";
    private static final String REASON_FINGERPRINT_DECISION_NOT_READY = "fingerprintDecisionNotReadyForProduction";

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (previewCandidateCount < 0) {
            throw new IllegalArgumentException("previewCandidateCount must be non-negative");
        }
        if (uniquePreviewKeyCount < 0) {
            throw new IllegalArgumentException("uniquePreviewKeyCount must be non-negative");
        }
        if (productionCandidateCount < 0) {
            throw new IllegalArgumentException("productionCandidateCount must be non-negative");
        }
        if (productionCandidateOverlapCount < 0) {
            throw new IllegalArgumentException("productionCandidateOverlapCount must be non-negative");
        }
        if (previewOnlyKeyCount < 0) {
            throw new IllegalArgumentException("previewOnlyKeyCount must be non-negative");
        }
        if (readiness == null || readiness.isBlank()) {
            throw new IllegalArgumentException("readiness must not be blank");
        }
        previewOnlyKeys = List.copyOf(Objects.requireNonNull(previewOnlyKeys, "previewOnlyKeys"));
        productionOverlappingFingerprints = List.copyOf(Objects.requireNonNull(productionOverlappingFingerprints, "productionOverlappingFingerprints"));
        blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport from(
            GpuIrCommonSubexpressionArtifactSnapshot cseSnapshot,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport fingerprintDecisionReport
    ) {
        Objects.requireNonNull(cseSnapshot, "cseSnapshot");
        Objects.requireNonNull(canonicalizationReport, "canonicalizationReport");
        Objects.requireNonNull(fingerprintDecisionReport, "fingerprintDecisionReport");
        Set<String> productionFingerprints = productionFingerprints(cseSnapshot);
        List<String> previewKeys = canonicalizationReport.canonicalKeyCounts().keySet().stream().toList();
        List<String> overlapping = previewKeys.stream()
                .filter(productionFingerprints::contains)
                .toList();
        List<String> previewOnly = previewKeys.stream()
                .filter(key -> !productionFingerprints.contains(key))
                .toList();
        List<String> blockers = blockers(canonicalizationReport, fingerprintDecisionReport, previewOnly);
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport(
                canonicalizationReport.methodName(),
                canonicalizationReport.candidateCount(),
                canonicalizationReport.uniqueCanonicalKeyCount(),
                productionFingerprints.size(),
                overlapping.size(),
                previewOnly.size(),
                fingerprintDecisionReport.evidenceComplete(),
                fingerprintDecisionReport.readyForProductionFingerprintIntegration(),
                readiness(canonicalizationReport, fingerprintDecisionReport, previewOnly),
                previewOnly,
                overlapping,
                blockers
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

    public Optional<String> firstPreviewOnlyKey() {
        return previewOnlyKeys.stream().findFirst();
    }

    public Optional<String> firstBlocker() {
        return blockers.stream().findFirst();
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Readiness", readiness);
        values.put(prefix + "PreviewCandidates", Integer.toString(previewCandidateCount));
        values.put(prefix + "UniquePreviewKeys", Integer.toString(uniquePreviewKeyCount));
        values.put(prefix + "ProductionCandidates", Integer.toString(productionCandidateCount));
        values.put(prefix + "ProductionCandidateOverlaps", Integer.toString(productionCandidateOverlapCount));
        values.put(prefix + "PreviewOnlyKeys", Integer.toString(previewOnlyKeyCount));
        values.put(prefix + "HasPreviewOnlyKeys", Boolean.toString(hasPreviewOnlyKeys()));
        values.put(prefix + "EvidenceComplete", Boolean.toString(evidenceComplete));
        values.put(prefix + "ReadyForProduction", Boolean.toString(readyForProduction));
        values.put(prefix + "Blockers", listSummary(blockers));
        values.put(prefix + "BlockerCount", Integer.toString(blockerCount()));
        firstPreviewOnlyKey().ifPresent(key -> values.put(prefix + "FirstPreviewOnlyKey", key));
        firstBlocker().ifPresent(blocker -> values.put(prefix + "FirstBlocker", blocker));
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseSimpleArithmeticLiteralFingerprintParity");
    }

    public String summary() {
        return "CSE simple arithmetic literal fingerprint parity method=" + methodName
                + " readiness=" + readiness
                + " previewCandidates=" + previewCandidateCount
                + " uniquePreviewKeys=" + uniquePreviewKeyCount
                + " productionCandidates=" + productionCandidateCount
                + " productionOverlaps=" + productionCandidateOverlapCount
                + " previewOnlyKeys=" + previewOnlyKeyCount
                + " evidenceComplete=" + evidenceComplete
                + " readyForProduction=" + readyForProduction
                + " blockers=" + listSummary(blockers)
                + firstPreviewOnlyKey().map(key -> " firstPreviewOnlyKey=" + key).orElse("");
    }

    private static Set<String> productionFingerprints(GpuIrCommonSubexpressionArtifactSnapshot snapshot) {
        java.util.LinkedHashSet<String> fingerprints = new java.util.LinkedHashSet<>();
        snapshot.preview().insertions().stream()
                .map(GpuIrCommonSubexpressionRewriteInsertion::fingerprint)
                .forEach(fingerprints::add);
        snapshot.preview().skippedDiagnostics().stream()
                .map(GpuIrCommonSubexpressionSkippedDiagnostic::fingerprint)
                .forEach(fingerprints::add);
        return fingerprints;
    }

    private static String readiness(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport fingerprintDecisionReport,
            List<String> previewOnlyKeys
    ) {
        if (!canonicalizationReport.hasCandidates()) {
            return "none";
        }
        if (previewOnlyKeys.isEmpty() && fingerprintDecisionReport.readyForProductionFingerprintIntegration()) {
            return "productionParity";
        }
        return "previewOnly";
    }

    private static List<String> blockers(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport fingerprintDecisionReport,
            List<String> previewOnlyKeys
    ) {
        java.util.ArrayList<String> blockers = new java.util.ArrayList<>();
        if (!canonicalizationReport.hasCandidates()) {
            blockers.add(REASON_NO_PREVIEW_CANDIDATES);
        }
        if (!previewOnlyKeys.isEmpty()) {
            blockers.add(REASON_PREVIEW_ONLY_KEYS);
        }
        if (!fingerprintDecisionReport.readyForProductionFingerprintIntegration()) {
            blockers.add(REASON_FINGERPRINT_DECISION_NOT_READY);
        }
        return List.copyOf(blockers);
    }

    private static String listSummary(List<String> values) {
        return values.stream().collect(Collectors.joining(",", "[", "]"));
    }
}
