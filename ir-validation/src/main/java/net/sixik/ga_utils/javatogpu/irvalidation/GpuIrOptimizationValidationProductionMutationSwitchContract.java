package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only contract for the future production optimizer mutation switch.
 *
 * <p>This object is intentionally a contract, not a switch implementation. It explains whether the
 * current A1/A2 preflight could even be considered for a future production mutation rollout and
 * lists the explicit evidence, rollback, and vendor-readiness work that must be completed before
 * any production IR mutation can be enabled.</p>
 */
public record GpuIrOptimizationValidationProductionMutationSwitchContract(
        String methodName,
        String verdict,
        boolean eligibleForSwitchReview,
        boolean productionMutationEnabled,
        String preflightVerdict,
        boolean preflightReviewReady,
        boolean preflightReadyForProductionMutation,
        List<String> requiredEvidence,
        List<String> blockingReasons,
        List<String> remainingWork
) {
    private static final String VERDICT_BLOCKED_PREFLIGHT = "blocked/preflightNotReady";
    private static final String VERDICT_REVIEW_REQUIRED = "reviewRequired/productionMutationDisabled";
    private static final String VERDICT_READY = "ready/productionMutationSwitchEnabled";

    private static final List<String> DEFAULT_REQUIRED_EVIDENCE = List.of(
            "cpuGpuRuntimeEquivalenceStable",
            "crossVendorRuntimeCoverageStable",
            "rollbackStrategyDefined",
            "productionMutationFlagDefined"
    );

    public GpuIrOptimizationValidationProductionMutationSwitchContract {
        methodName = requireNonBlank(methodName, "methodName");
        verdict = requireNonBlank(verdict, "verdict");
        preflightVerdict = requireNonBlank(preflightVerdict, "preflightVerdict");
        requiredEvidence = List.copyOf(Objects.requireNonNull(requiredEvidence, "requiredEvidence"));
        blockingReasons = List.copyOf(Objects.requireNonNull(blockingReasons, "blockingReasons"));
        remainingWork = List.copyOf(Objects.requireNonNull(remainingWork, "remainingWork"));
        if (productionMutationEnabled && !VERDICT_READY.equals(verdict)) {
            throw new IllegalArgumentException("enabled production switch contracts must use the ready verdict");
        }
        if (!productionMutationEnabled && VERDICT_READY.equals(verdict)) {
            throw new IllegalArgumentException("ready switch contracts require production mutation enabled");
        }
        if (eligibleForSwitchReview && !blockingReasons.isEmpty()) {
            throw new IllegalArgumentException("switch-review eligible contracts must not have blocking reasons");
        }
        if (preflightReadyForProductionMutation && !productionMutationEnabled) {
            throw new IllegalArgumentException("production-ready preflight cannot be represented with mutation disabled");
        }
    }

    public static GpuIrOptimizationValidationProductionMutationSwitchContract from(
            GpuIrOptimizationValidationProductionEnablementPreflightDecision preflight
    ) {
        Objects.requireNonNull(preflight, "preflight");
        boolean eligible = preflight.reviewReady()
                && !preflight.readyForProductionMutation()
                && !preflight.productionMutationEnabled();
        List<String> blockingReasons = blockingReasons(preflight, eligible);
        List<String> remainingWork = remainingWork(preflight, eligible, blockingReasons);
        return new GpuIrOptimizationValidationProductionMutationSwitchContract(
                preflight.methodName(),
                verdict(preflight, eligible),
                eligible,
                preflight.productionMutationEnabled(),
                preflight.verdict(),
                preflight.reviewReady(),
                preflight.readyForProductionMutation(),
                DEFAULT_REQUIRED_EVIDENCE,
                blockingReasons,
                remainingWork
        );
    }

    public int requiredEvidenceCount() {
        return requiredEvidence.size();
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
        return artifactFields("optimizerProductionSwitch");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName);
        values.put(prefix + "Verdict", verdict);
        values.put(prefix + "EligibleForSwitchReview", Boolean.toString(eligibleForSwitchReview));
        values.put(prefix + "ProductionMutationEnabled", Boolean.toString(productionMutationEnabled));
        values.put(prefix + "PreflightVerdict", preflightVerdict);
        values.put(prefix + "PreflightReviewReady", Boolean.toString(preflightReviewReady));
        values.put(prefix + "PreflightReadyForProductionMutation", Boolean.toString(preflightReadyForProductionMutation));
        values.put(prefix + "RequiredEvidence", listSummary(requiredEvidence));
        values.put(prefix + "RequiredEvidenceCount", Integer.toString(requiredEvidenceCount()));
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
        return "optimizer production switch method=" + methodName
                + " verdict=" + verdict
                + " eligibleForSwitchReview=" + eligibleForSwitchReview
                + " productionMutationEnabled=" + productionMutationEnabled
                + firstBlockingReason().map(reason -> " firstBlockingReason=" + reason).orElse("")
                + " firstRemainingWork=" + firstRemainingWork().orElse("none");
    }

    public String summary() {
        return "optimizer production switch method=" + methodName
                + " verdict=" + verdict
                + " eligibleForSwitchReview=" + eligibleForSwitchReview
                + " productionMutationEnabled=" + productionMutationEnabled
                + " preflightVerdict=" + preflightVerdict
                + " requiredEvidence=" + listSummary(requiredEvidence)
                + " blockingReasons=" + listSummary(blockingReasons)
                + " remainingWork=" + listSummary(remainingWork);
    }

    private static String verdict(
            GpuIrOptimizationValidationProductionEnablementPreflightDecision preflight,
            boolean eligible
    ) {
        if (preflight.readyForProductionMutation() && preflight.productionMutationEnabled()) {
            return VERDICT_READY;
        }
        if (eligible) {
            return VERDICT_REVIEW_REQUIRED;
        }
        return VERDICT_BLOCKED_PREFLIGHT;
    }

    private static List<String> blockingReasons(
            GpuIrOptimizationValidationProductionEnablementPreflightDecision preflight,
            boolean eligible
    ) {
        if (eligible) {
            return List.of();
        }
        java.util.LinkedHashSet<String> reasons = new java.util.LinkedHashSet<>();
        reasons.add("productionPreflightNotReviewReady");
        if (!preflight.firstBlockingReason().isBlank()) {
            reasons.add("preflight:" + preflight.firstBlockingReason());
        }
        return List.copyOf(reasons);
    }

    private static List<String> remainingWork(
            GpuIrOptimizationValidationProductionEnablementPreflightDecision preflight,
            boolean eligible,
            List<String> blockingReasons
    ) {
        java.util.LinkedHashSet<String> work = new java.util.LinkedHashSet<>();
        if (!eligible) {
            work.add(preflight.firstRemainingWork());
            work.addAll(blockingReasons.stream()
                    .filter(reason -> reason.startsWith("preflight:"))
                    .map(reason -> "clearProductionPreflightBlocker:" + reason.substring("preflight:".length()))
                    .toList());
        }
        work.add("proveCpuGpuRuntimeEquivalence");
        work.add("addCrossVendorRuntimeCoverage");
        work.add("defineProductionMutationRollback");
        work.add("defineProductionMutationFeatureFlag");
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
