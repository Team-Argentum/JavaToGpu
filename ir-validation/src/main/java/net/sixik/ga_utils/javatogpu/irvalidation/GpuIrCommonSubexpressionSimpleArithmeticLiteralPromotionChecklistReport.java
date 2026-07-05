package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Final read-only promotion checklist for future literal arithmetic CSE mutation.
 *
 * <p>This report intentionally does not enable fingerprints or rewrites. It only combines the
 * enablement verdict, typed rewrite preflight, and operation-shape preview into one CI gate.</p>
 */
public record GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport(
        String methodName,
        String verdict,
        int candidateCount,
        int eligibleCandidateCount,
        int blockedCandidateCount,
        int eligibleOperationCount,
        int blockedOperationCount,
        int uniqueCanonicalKeyCount,
        boolean evidenceComplete,
        boolean readyForProduction,
        String enablementVerdict,
        String preflightReadiness,
        String operationPreviewReadiness,
        List<String> remainingWork
) {
    private static final String VERDICT_NO_PREVIEW = "notReady/noPreviewCandidates";
    private static final String VERDICT_EVIDENCE_INCOMPLETE = "notReady/evidenceIncomplete";
    private static final String VERDICT_PREFLIGHT_BLOCKED = "notReady/rewritePreflightBlocked";
    private static final String VERDICT_OPERATION_PREVIEW_BLOCKED = "notReady/rewriteOperationPreviewBlocked";
    private static final String VERDICT_DISABLED = "evidenceCompleteButProductionDisabled";
    private static final String VERDICT_READY = "readyForProductionMutation";

    public GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        if (verdict == null || verdict.isBlank()) {
            throw new IllegalArgumentException("verdict must not be blank");
        }
        if (candidateCount < 0) {
            throw new IllegalArgumentException("candidateCount must be non-negative");
        }
        if (eligibleCandidateCount < 0) {
            throw new IllegalArgumentException("eligibleCandidateCount must be non-negative");
        }
        if (blockedCandidateCount < 0) {
            throw new IllegalArgumentException("blockedCandidateCount must be non-negative");
        }
        if (eligibleOperationCount < 0) {
            throw new IllegalArgumentException("eligibleOperationCount must be non-negative");
        }
        if (blockedOperationCount < 0) {
            throw new IllegalArgumentException("blockedOperationCount must be non-negative");
        }
        if (uniqueCanonicalKeyCount < 0) {
            throw new IllegalArgumentException("uniqueCanonicalKeyCount must be non-negative");
        }
        if (enablementVerdict == null || enablementVerdict.isBlank()) {
            throw new IllegalArgumentException("enablementVerdict must not be blank");
        }
        if (preflightReadiness == null || preflightReadiness.isBlank()) {
            throw new IllegalArgumentException("preflightReadiness must not be blank");
        }
        if (operationPreviewReadiness == null || operationPreviewReadiness.isBlank()) {
            throw new IllegalArgumentException("operationPreviewReadiness must not be blank");
        }
        remainingWork = List.copyOf(Objects.requireNonNull(remainingWork, "remainingWork"));
    }

    public static GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport from(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport enablementReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport preflightReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport operationPreviewReport
    ) {
        Objects.requireNonNull(enablementReport, "enablementReport");
        Objects.requireNonNull(preflightReport, "preflightReport");
        Objects.requireNonNull(operationPreviewReport, "operationPreviewReport");
        List<String> remainingWork = remainingWork(enablementReport, preflightReport, operationPreviewReport);
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionChecklistReport(
                enablementReport.methodName(),
                verdict(enablementReport, preflightReport, operationPreviewReport, remainingWork),
                preflightReport.candidateCount(),
                preflightReport.eligibleCandidateCount(),
                preflightReport.blockedCandidateCount(),
                operationPreviewReport.eligibleOperationCount(),
                operationPreviewReport.blockedOperationCount(),
                preflightReport.uniqueCanonicalKeyCount(),
                enablementReport.evidenceComplete(),
                enablementReport.readyForProduction(),
                enablementReport.verdict(),
                preflightReport.readiness(),
                operationPreviewReport.readiness(),
                remainingWork
        );
    }

    public boolean readyForProductionMutation() {
        return VERDICT_READY.equals(verdict);
    }

    public int remainingWorkCount() {
        return remainingWork.size();
    }

    public Optional<String> firstRemainingWork() {
        return remainingWork.stream().findFirst();
    }

    public Map<String, Long> remainingWorkCounts() {
        return remainingWork.stream()
                .collect(Collectors.groupingBy(
                        item -> item,
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
        values.put(prefix + "Candidates", Integer.toString(candidateCount));
        values.put(prefix + "EligibleCandidates", Integer.toString(eligibleCandidateCount));
        values.put(prefix + "BlockedCandidates", Integer.toString(blockedCandidateCount));
        values.put(prefix + "EligibleOperations", Integer.toString(eligibleOperationCount));
        values.put(prefix + "BlockedOperations", Integer.toString(blockedOperationCount));
        values.put(prefix + "UniqueCanonicalKeys", Integer.toString(uniqueCanonicalKeyCount));
        values.put(prefix + "EvidenceComplete", Boolean.toString(evidenceComplete));
        values.put(prefix + "ReadyForProduction", Boolean.toString(readyForProduction));
        values.put(prefix + "EnablementVerdict", enablementVerdict);
        values.put(prefix + "PreflightReadiness", preflightReadiness);
        values.put(prefix + "OperationPreviewReadiness", operationPreviewReadiness);
        values.put(prefix + "RemainingWork", listSummary(remainingWork));
        values.put(prefix + "RemainingWorkCount", Integer.toString(remainingWorkCount()));
        values.put(prefix + "RemainingWorkCounts", mapSummary(remainingWorkCounts()));
        firstRemainingWork().ifPresent(item -> values.put(prefix + "FirstRemainingWork", item));
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseSimpleArithmeticLiteralPromotionChecklist");
    }

    public String summary() {
        return "CSE simple arithmetic literal promotion checklist method=" + methodName
                + " verdict=" + verdict
                + " candidates=" + candidateCount
                + " eligibleCandidates=" + eligibleCandidateCount
                + " blockedCandidates=" + blockedCandidateCount
                + " eligibleOperations=" + eligibleOperationCount
                + " blockedOperations=" + blockedOperationCount
                + " uniqueCanonicalKeys=" + uniqueCanonicalKeyCount
                + " evidenceComplete=" + evidenceComplete
                + " readyForProduction=" + readyForProduction
                + " enablementVerdict=" + enablementVerdict
                + " preflightReadiness=" + preflightReadiness
                + " operationPreviewReadiness=" + operationPreviewReadiness
                + " remainingWork=" + listSummary(remainingWork);
    }

    private static String verdict(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport enablementReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport preflightReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport operationPreviewReport,
            List<String> remainingWork
    ) {
        if (!enablementReport.hasPreviewCandidates()) {
            return VERDICT_NO_PREVIEW;
        }
        if (!enablementReport.evidenceComplete()) {
            return VERDICT_EVIDENCE_INCOMPLETE;
        }
        if (preflightReport.hasBlockedCandidates()) {
            return VERDICT_PREFLIGHT_BLOCKED;
        }
        if (operationPreviewReport.hasBlockedOperations()) {
            return VERDICT_OPERATION_PREVIEW_BLOCKED;
        }
        if (!enablementReport.readyForProduction()) {
            return VERDICT_DISABLED;
        }
        return remainingWork.isEmpty() ? VERDICT_READY : VERDICT_DISABLED;
    }

    private static List<String> remainingWork(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralEnablementReport enablementReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRewritePreflightReport preflightReport,
            GpuIrCommonSubexpressionSimpleArithmeticLiteralRewriteOperationPreviewReport operationPreviewReport
    ) {
        java.util.LinkedHashSet<String> work = new java.util.LinkedHashSet<>();
        if (!enablementReport.hasPreviewCandidates()) {
            work.add("collectPreviewCandidates");
        }
        if (!enablementReport.numericSemanticsFullyProven()) {
            work.add("proveTypedNumericSemantics");
        }
        if (!enablementReport.runtimeEquivalenceSuccessful()) {
            work.add("runRuntimeEquivalenceEvidence");
        }
        if (enablementReport.hasPreviewOnlyKeys()) {
            work.add("resolvePreviewOnlyFingerprintBlastRadius");
        }
        if (preflightReport.hasBlockedCandidates()) {
            work.add("clearRewritePreflightBlockers");
        }
        if (operationPreviewReport.hasBlockedOperations()) {
            work.add("clearRewriteOperationPreviewBlockers");
        }
        if (!enablementReport.readyForProduction()) {
            work.add("enableProductionFingerprintIntegration");
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
