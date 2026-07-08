package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only confidence contract for promoting selected optimizer rewrites.
 *
 * <p>This layer sits above the future production mutation switch contract and intentionally keeps
 * optimizer rewrites blocked until A1/A2 runtime confidence is stable. It records the required
 * confidence evidence for CI/tooling without enabling production mutation.</p>
 */
public record GpuIrOptimizationValidationOptimizerPromotionConfidenceContract(
        String methodName,
        String verdict,
        boolean promotionAllowed,
        boolean productionMutationEnabled,
        String switchVerdict,
        boolean switchReviewEligible,
        boolean runtimeConfidenceStable,
        List<String> requiredConfidenceEvidence,
        List<String> blockingReasons,
        List<String> remainingWork
) {
    private static final String VERDICT_BLOCKED_SWITCH = "blocked/productionSwitchNotReady";
    private static final String VERDICT_BLOCKED_CONFIDENCE = "blocked/runtimeConfidenceNotStable";
    private static final String VERDICT_READY = "ready/selectedRewritePromotionAllowed";

    private static final List<String> DEFAULT_REQUIRED_CONFIDENCE_EVIDENCE = List.of(
            "a1A2RuntimeEquivalenceHistoryStable",
            "longRunningOperationalStressStable",
            "nvidiaInterimCoverageStable",
            "crossVendorPromotionGateDefined",
            "selectedRewriteRollbackVerified"
    );

    public GpuIrOptimizationValidationOptimizerPromotionConfidenceContract {
        methodName = requireNonBlank(methodName, "methodName");
        verdict = requireNonBlank(verdict, "verdict");
        switchVerdict = requireNonBlank(switchVerdict, "switchVerdict");
        requiredConfidenceEvidence = List.copyOf(Objects.requireNonNull(requiredConfidenceEvidence, "requiredConfidenceEvidence"));
        blockingReasons = List.copyOf(Objects.requireNonNull(blockingReasons, "blockingReasons"));
        remainingWork = List.copyOf(Objects.requireNonNull(remainingWork, "remainingWork"));
        if (promotionAllowed && !VERDICT_READY.equals(verdict)) {
            throw new IllegalArgumentException("allowed promotion contracts must use the ready verdict");
        }
        if (!promotionAllowed && VERDICT_READY.equals(verdict)) {
            throw new IllegalArgumentException("ready promotion contracts must allow promotion");
        }
        if (promotionAllowed && !productionMutationEnabled) {
            throw new IllegalArgumentException("selected rewrite promotion requires production mutation enabled");
        }
        if (promotionAllowed && !runtimeConfidenceStable) {
            throw new IllegalArgumentException("selected rewrite promotion requires stable runtime confidence");
        }
        if (promotionAllowed && !blockingReasons.isEmpty()) {
            throw new IllegalArgumentException("allowed promotion contracts must not have blocking reasons");
        }
    }

    public static GpuIrOptimizationValidationOptimizerPromotionConfidenceContract from(
            GpuIrOptimizationValidationProductionMutationSwitchContract switchContract
    ) {
        Objects.requireNonNull(switchContract, "switchContract");
        boolean runtimeStable = false;
        boolean promotionAllowed = switchContract.productionMutationEnabled() && runtimeStable;
        List<String> blockingReasons = blockingReasons(switchContract, runtimeStable, promotionAllowed);
        List<String> remainingWork = remainingWork(switchContract, runtimeStable, blockingReasons);
        return new GpuIrOptimizationValidationOptimizerPromotionConfidenceContract(
                switchContract.methodName(),
                verdict(switchContract, runtimeStable, promotionAllowed),
                promotionAllowed,
                switchContract.productionMutationEnabled(),
                switchContract.verdict(),
                switchContract.eligibleForSwitchReview(),
                runtimeStable,
                DEFAULT_REQUIRED_CONFIDENCE_EVIDENCE,
                blockingReasons,
                remainingWork
        );
    }

    public int requiredConfidenceEvidenceCount() {
        return requiredConfidenceEvidence.size();
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

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerPromotionConfidence");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName);
        values.put(prefix + "Verdict", verdict);
        values.put(prefix + "PromotionAllowed", Boolean.toString(promotionAllowed));
        values.put(prefix + "ProductionMutationEnabled", Boolean.toString(productionMutationEnabled));
        values.put(prefix + "SwitchVerdict", switchVerdict);
        values.put(prefix + "SwitchReviewEligible", Boolean.toString(switchReviewEligible));
        values.put(prefix + "RuntimeConfidenceStable", Boolean.toString(runtimeConfidenceStable));
        values.put(prefix + "RequiredConfidenceEvidence", listSummary(requiredConfidenceEvidence));
        values.put(prefix + "RequiredConfidenceEvidenceCount", Integer.toString(requiredConfidenceEvidenceCount()));
        values.put(prefix + "BlockingReasons", listSummary(blockingReasons));
        values.put(prefix + "BlockingReasonCount", Integer.toString(blockingReasonCount()));
        values.put(prefix + "RemainingWork", listSummary(remainingWork));
        values.put(prefix + "RemainingWorkCount", Integer.toString(remainingWorkCount()));
        firstBlockingReason().ifPresent(reason -> values.put(prefix + "FirstBlockingReason", reason));
        firstRemainingWork().ifPresent(work -> values.put(prefix + "FirstRemainingWork", work));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "optimizer promotion confidence method=" + methodName
                + " verdict=" + verdict
                + " promotionAllowed=" + promotionAllowed
                + " runtimeConfidenceStable=" + runtimeConfidenceStable
                + firstBlockingReason().map(reason -> " firstBlockingReason=" + reason).orElse("")
                + " firstRemainingWork=" + firstRemainingWork().orElse("none");
    }

    public String summary() {
        return "optimizer promotion confidence method=" + methodName
                + " verdict=" + verdict
                + " promotionAllowed=" + promotionAllowed
                + " productionMutationEnabled=" + productionMutationEnabled
                + " switchVerdict=" + switchVerdict
                + " runtimeConfidenceStable=" + runtimeConfidenceStable
                + " requiredConfidenceEvidence=" + listSummary(requiredConfidenceEvidence)
                + " blockingReasons=" + listSummary(blockingReasons)
                + " remainingWork=" + listSummary(remainingWork);
    }

    private static String verdict(
            GpuIrOptimizationValidationProductionMutationSwitchContract switchContract,
            boolean runtimeStable,
            boolean promotionAllowed
    ) {
        if (promotionAllowed) {
            return VERDICT_READY;
        }
        if (!switchContract.productionMutationEnabled()) {
            return VERDICT_BLOCKED_SWITCH;
        }
        if (!runtimeStable) {
            return VERDICT_BLOCKED_CONFIDENCE;
        }
        return VERDICT_BLOCKED_CONFIDENCE;
    }

    private static List<String> blockingReasons(
            GpuIrOptimizationValidationProductionMutationSwitchContract switchContract,
            boolean runtimeStable,
            boolean promotionAllowed
    ) {
        if (promotionAllowed) {
            return List.of();
        }
        java.util.LinkedHashSet<String> reasons = new java.util.LinkedHashSet<>();
        if (!switchContract.productionMutationEnabled()) {
            reasons.add("productionMutationSwitchNotReady");
            switchContract.firstBlockingReason().ifPresent(reason -> reasons.add("productionSwitch:" + reason));
        }
        if (!runtimeStable) {
            reasons.add("a1A2RuntimeConfidenceNotStable");
        }
        return List.copyOf(reasons);
    }

    private static List<String> remainingWork(
            GpuIrOptimizationValidationProductionMutationSwitchContract switchContract,
            boolean runtimeStable,
            List<String> blockingReasons
    ) {
        java.util.LinkedHashSet<String> work = new java.util.LinkedHashSet<>();
        if (!switchContract.productionMutationEnabled()) {
            work.addAll(switchContract.remainingWork());
        }
        if (!runtimeStable) {
            work.add("stabilizeA1A2RuntimeEquivalenceHistory");
            work.add("stabilizeLongRunningOperationalStress");
            work.add("keepNvidiaInterimCoverageGreen");
            work.add("defineCrossVendorPromotionGate");
            work.add("verifySelectedRewriteRollback");
        }
        for (String reason : blockingReasons) {
            if (reason.startsWith("productionSwitch:")) {
                work.add("clearProductionSwitchBlocker:" + reason.substring("productionSwitch:".length()));
            }
        }
        return List.copyOf(work);
    }

    private static String listSummary(List<String> values) {
        return values.stream().collect(Collectors.joining(",", "[", "]"));
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
