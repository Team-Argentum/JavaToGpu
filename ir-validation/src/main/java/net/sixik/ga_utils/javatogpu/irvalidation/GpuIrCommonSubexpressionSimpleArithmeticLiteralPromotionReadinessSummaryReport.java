package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * One-line read-only readiness answer for future literal arithmetic CSE promotion.
 *
 * <p>The report intentionally stays above the mutating optimizer path. It collects the proof,
 * runtime, fingerprint blast-radius, and production-disablement blockers into one CI-friendly
 * artifact so promotion reviews do not need to infer readiness from several lower-level reports.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport(
        String methodName,
        String verdict,
        int previewCandidateCount,
        int uniqueCanonicalKeyCount,
        int typedNumericBlockedCandidateCount,
        int runtimeEquivalenceDiagnosticCount,
        int previewOnlyKeyCount,
        boolean typedNumericBlockersClear,
        boolean runtimeEquivalenceSuccessful,
        boolean previewOnlyBlastRadiusClear,
        boolean productionFingerprintIntegrationEnabled,
        boolean productionMutationEnabled,
        String typedNumericReadiness,
        String runtimeEquivalenceReadiness,
        String fingerprintParityReadiness,
        String promotionChecklistVerdict,
        List<String> blockingReasons,
        List<String> remainingWork
) {
    private static final String VERDICT_NO_PREVIEW = "notReady/noPreviewCandidates";
    private static final String VERDICT_TYPED_NUMERIC_BLOCKED = "notReady/typedNumericBlockers";
    private static final String VERDICT_RUNTIME_MISSING = "notReady/runtimeMissing";
    private static final String VERDICT_PREVIEW_ONLY = "notReady/previewOnlyBlastRadius";
    private static final String VERDICT_PRODUCTION_DISABLED = "evidenceCompleteButProductionDisabled";
    private static final String VERDICT_READY = "readyForProductionMutation";

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport {
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
        if (typedNumericBlockedCandidateCount < 0) {
            throw new IllegalArgumentException("typedNumericBlockedCandidateCount must be non-negative");
        }
        if (runtimeEquivalenceDiagnosticCount < 0) {
            throw new IllegalArgumentException("runtimeEquivalenceDiagnosticCount must be non-negative");
        }
        if (previewOnlyKeyCount < 0) {
            throw new IllegalArgumentException("previewOnlyKeyCount must be non-negative");
        }
        if (typedNumericReadiness == null || typedNumericReadiness.isBlank()) {
            throw new IllegalArgumentException("typedNumericReadiness must not be blank");
        }
        if (runtimeEquivalenceReadiness == null || runtimeEquivalenceReadiness.isBlank()) {
            throw new IllegalArgumentException("runtimeEquivalenceReadiness must not be blank");
        }
        if (fingerprintParityReadiness == null || fingerprintParityReadiness.isBlank()) {
            throw new IllegalArgumentException("fingerprintParityReadiness must not be blank");
        }
        if (promotionChecklistVerdict == null || promotionChecklistVerdict.isBlank()) {
            throw new IllegalArgumentException("promotionChecklistVerdict must not be blank");
        }
        blockingReasons = List.copyOf(Objects.requireNonNull(blockingReasons, "blockingReasons"));
        remainingWork = List.copyOf(Objects.requireNonNull(remainingWork, "remainingWork"));
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport from(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport canonicalizationReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport typedNumericBlockerSummaryReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalenceReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintDecisionReport fingerprintDecisionReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralFingerprintParityReport fingerprintParityReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport promotionChecklistReport
    ) {
        Objects.requireNonNull(canonicalizationReport, "canonicalizationReport");
        Objects.requireNonNull(typedNumericBlockerSummaryReport, "typedNumericBlockerSummaryReport");
        Objects.requireNonNull(runtimeEquivalenceReport, "runtimeEquivalenceReport");
        Objects.requireNonNull(fingerprintDecisionReport, "fingerprintDecisionReport");
        Objects.requireNonNull(fingerprintParityReport, "fingerprintParityReport");
        Objects.requireNonNull(promotionChecklistReport, "promotionChecklistReport");
        boolean hasPreviewCandidates = canonicalizationReport.hasCandidates();
        boolean typedNumericClear = !typedNumericBlockerSummaryReport.hasBlockers();
        boolean runtimeSuccessful = runtimeEquivalenceReport.successful();
        boolean previewOnlyClear = !fingerprintParityReport.hasPreviewOnlyKeys();
        boolean productionFingerprintEnabled = fingerprintDecisionReport.readyForProductionFingerprintIntegration();
        boolean productionMutationEnabled = promotionChecklistReport.readyForProductionMutation();
        List<String> blockingReasons = blockingReasons(
                hasPreviewCandidates,
                typedNumericClear,
                runtimeSuccessful,
                previewOnlyClear,
                productionFingerprintEnabled,
                productionMutationEnabled
        );
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport(
                canonicalizationReport.methodName(),
                verdict(hasPreviewCandidates, typedNumericClear, runtimeSuccessful, previewOnlyClear, productionFingerprintEnabled, productionMutationEnabled),
                canonicalizationReport.candidateCount(),
                canonicalizationReport.uniqueCanonicalKeyCount(),
                typedNumericBlockerSummaryReport.totalBlockedCandidateCount(),
                runtimeEquivalenceReport.diagnosticCount(),
                fingerprintParityReport.previewOnlyKeyCount(),
                typedNumericClear,
                runtimeSuccessful,
                previewOnlyClear,
                productionFingerprintEnabled,
                productionMutationEnabled,
                typedNumericBlockerSummaryReport.readiness(),
                runtimeEquivalenceReport.readiness(),
                fingerprintParityReport.readiness(),
                promotionChecklistReport.verdict(),
                blockingReasons,
                remainingWork(blockingReasons, promotionChecklistReport)
        );
    }

    public boolean readyForProductionMutation() {
        return VERDICT_READY.equals(verdict);
    }

    public int blockingReasonCount() {
        return blockingReasons.size();
    }

    public int remainingWorkCount() {
        return remainingWork.size();
    }

    public Optional<String> firstBlockingReason() {
        return blockingReasons.stream().findFirst();
    }

    public Optional<String> firstRemainingWork() {
        return remainingWork.stream().findFirst();
    }

    public Map<String, Long> blockingReasonCounts() {
        return blockingReasons.stream()
                .collect(Collectors.groupingBy(
                        reason -> reason,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Verdict", verdict);
        values.put(prefix + "ReadyForProductionMutation", Boolean.toString(readyForProductionMutation()));
        values.put(prefix + "PreviewCandidates", Integer.toString(previewCandidateCount));
        values.put(prefix + "UniqueCanonicalKeys", Integer.toString(uniqueCanonicalKeyCount));
        values.put(prefix + "TypedNumericBlockedCandidates", Integer.toString(typedNumericBlockedCandidateCount));
        values.put(prefix + "RuntimeEquivalenceDiagnostics", Integer.toString(runtimeEquivalenceDiagnosticCount));
        values.put(prefix + "PreviewOnlyKeys", Integer.toString(previewOnlyKeyCount));
        values.put(prefix + "TypedNumericBlockersClear", Boolean.toString(typedNumericBlockersClear));
        values.put(prefix + "RuntimeEquivalenceSuccessful", Boolean.toString(runtimeEquivalenceSuccessful));
        values.put(prefix + "PreviewOnlyBlastRadiusClear", Boolean.toString(previewOnlyBlastRadiusClear));
        values.put(prefix + "ProductionFingerprintIntegrationEnabled", Boolean.toString(productionFingerprintIntegrationEnabled));
        values.put(prefix + "ProductionMutationEnabled", Boolean.toString(productionMutationEnabled));
        values.put(prefix + "TypedNumericReadiness", typedNumericReadiness);
        values.put(prefix + "RuntimeEquivalenceReadiness", runtimeEquivalenceReadiness);
        values.put(prefix + "FingerprintParityReadiness", fingerprintParityReadiness);
        values.put(prefix + "PromotionChecklistVerdict", promotionChecklistVerdict);
        values.put(prefix + "BlockingReasons", listSummary(blockingReasons));
        values.put(prefix + "BlockingReasonCount", Integer.toString(blockingReasonCount()));
        values.put(prefix + "BlockingReasonCounts", mapSummary(blockingReasonCounts()));
        values.put(prefix + "RemainingWork", listSummary(remainingWork));
        values.put(prefix + "RemainingWorkCount", Integer.toString(remainingWorkCount()));
        firstBlockingReason().ifPresent(reason -> values.put(prefix + "FirstBlockingReason", reason));
        firstRemainingWork().ifPresent(work -> values.put(prefix + "FirstRemainingWork", work));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseSimpleArithmeticLiteralPromotionReadiness");
    }

    public String ciSummaryLine() {
        if (readyForProductionMutation()) {
            return "literal promotion readiness ready";
        }
        return "literal promotion readiness " + verdict
                + " blockers=" + blockingReasonCount()
                + firstBlockingReason().map(reason -> " first=" + reason).orElse("");
    }

    public String summary() {
        return "CSE simple arithmetic literal promotion readiness method=" + methodName
                + " verdict=" + verdict
                + " readyForProductionMutation=" + readyForProductionMutation()
                + " previewCandidates=" + previewCandidateCount
                + " uniqueCanonicalKeys=" + uniqueCanonicalKeyCount
                + " typedNumericBlockedCandidates=" + typedNumericBlockedCandidateCount
                + " runtimeEquivalenceSuccessful=" + runtimeEquivalenceSuccessful
                + " runtimeEquivalenceDiagnostics=" + runtimeEquivalenceDiagnosticCount
                + " previewOnlyKeys=" + previewOnlyKeyCount
                + " productionFingerprintIntegrationEnabled=" + productionFingerprintIntegrationEnabled
                + " productionMutationEnabled=" + productionMutationEnabled
                + " promotionChecklistVerdict=" + promotionChecklistVerdict
                + " blockingReasons=" + listSummary(blockingReasons)
                + " remainingWork=" + listSummary(remainingWork);
    }

    private static String verdict(
            boolean hasPreviewCandidates,
            boolean typedNumericClear,
            boolean runtimeSuccessful,
            boolean previewOnlyClear,
            boolean productionFingerprintEnabled,
            boolean productionMutationEnabled
    ) {
        if (!hasPreviewCandidates) {
            return VERDICT_NO_PREVIEW;
        }
        if (!typedNumericClear) {
            return VERDICT_TYPED_NUMERIC_BLOCKED;
        }
        if (!runtimeSuccessful) {
            return VERDICT_RUNTIME_MISSING;
        }
        if (!previewOnlyClear) {
            return VERDICT_PREVIEW_ONLY;
        }
        if (!productionFingerprintEnabled || !productionMutationEnabled) {
            return VERDICT_PRODUCTION_DISABLED;
        }
        return VERDICT_READY;
    }

    private static List<String> blockingReasons(
            boolean hasPreviewCandidates,
            boolean typedNumericClear,
            boolean runtimeSuccessful,
            boolean previewOnlyClear,
            boolean productionFingerprintEnabled,
            boolean productionMutationEnabled
    ) {
        java.util.LinkedHashSet<String> reasons = new java.util.LinkedHashSet<>();
        if (!hasPreviewCandidates) {
            reasons.add("noPreviewCandidates");
        }
        if (!typedNumericClear) {
            reasons.add("typedNumericBlockersPresent");
        }
        if (!runtimeSuccessful) {
            reasons.add("runtimeEquivalenceNotProven");
        }
        if (!previewOnlyClear) {
            reasons.add("previewOnlyKeysNotInProductionFingerprints");
        }
        if (!productionFingerprintEnabled) {
            reasons.add("productionFingerprintIntegrationDisabled");
        }
        if (!productionMutationEnabled) {
            reasons.add("productionMutationDisabled");
        }
        return List.copyOf(reasons);
    }

    private static List<String> remainingWork(
            List<String> blockingReasons,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport promotionChecklistReport
    ) {
        java.util.LinkedHashSet<String> work = new java.util.LinkedHashSet<>(promotionChecklistReport.remainingWork());
        for (String reason : blockingReasons) {
            switch (reason) {
                case "noPreviewCandidates" -> work.add("collectPreviewCandidates");
                case "typedNumericBlockersPresent" -> work.add("clearTypedNumericBlockers");
                case "runtimeEquivalenceNotProven" -> work.add("runRuntimeEquivalenceEvidence");
                case "previewOnlyKeysNotInProductionFingerprints" -> work.add("resolvePreviewOnlyFingerprintBlastRadius");
                case "productionFingerprintIntegrationDisabled" -> work.add("enableProductionFingerprintIntegration");
                case "productionMutationDisabled" -> work.add("enableProductionMutation");
                default -> work.add("reviewLiteralPromotionBlocker:" + reason);
            }
        }
        return List.copyOf(work);
    }

    private static String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String listSummary(List<String> values) {
        return values.stream().collect(Collectors.joining(",", "[", "]"));
    }
}
