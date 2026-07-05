package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only policy decision for future optimizer enablement reviews.
 *
 * <p>This is deliberately one step more conservative than the readiness handoff: a clean handoff
 * can allow a review gate, but production mutation remains disabled until an explicit future
 * policy flips that final switch.</p>
 */
public record GpuIrOptimizationValidationOptimizerEnablementPolicyDecision(
        String methodName,
        String verdict,
        boolean allowOptimizerEnablementReview,
        boolean productionMutationEnabled,
        String handoffVerdict,
        boolean handoffReadyForOptimizerEnablement,
        String firstBlockingReason,
        String firstRemainingWork,
        String summary
) {
    private static final String VERDICT_REVIEW_ALLOWED_PRODUCTION_DISABLED = "reviewAllowed/productionMutationDisabled";
    private static final String VERDICT_BLOCKED_BY_HANDOFF = "blocked/handoffNotReady";
    private static final String PRODUCTION_MUTATION_DISABLED_WORK = "enableProductionMutationPolicy";

    public GpuIrOptimizationValidationOptimizerEnablementPolicyDecision {
        methodName = requireNonBlank(methodName, "methodName");
        verdict = requireNonBlank(verdict, "verdict");
        handoffVerdict = requireNonBlank(handoffVerdict, "handoffVerdict");
        firstBlockingReason = Objects.requireNonNull(firstBlockingReason, "firstBlockingReason");
        firstRemainingWork = requireNonBlank(firstRemainingWork, "firstRemainingWork");
        summary = requireNonBlank(summary, "summary");
        if (productionMutationEnabled) {
            throw new IllegalArgumentException("production mutation must stay disabled for this read-only policy");
        }
        if (allowOptimizerEnablementReview && !handoffReadyForOptimizerEnablement) {
            throw new IllegalArgumentException("review cannot be allowed when handoff is not ready");
        }
        if (allowOptimizerEnablementReview && !VERDICT_REVIEW_ALLOWED_PRODUCTION_DISABLED.equals(verdict)) {
            throw new IllegalArgumentException("allowed review decisions must use reviewAllowed verdict");
        }
        if (!allowOptimizerEnablementReview && VERDICT_REVIEW_ALLOWED_PRODUCTION_DISABLED.equals(verdict)) {
            throw new IllegalArgumentException("blocked decisions must not use reviewAllowed verdict");
        }
    }

    public static GpuIrOptimizationValidationOptimizerEnablementPolicyDecision from(
            GpuIrOptimizationValidationOptimizerReadinessHandoffReport handoffReport
    ) {
        Objects.requireNonNull(handoffReport, "handoffReport");
        boolean handoffReady = handoffReport.readyForOptimizerEnablement();
        String verdict = handoffReady ? VERDICT_REVIEW_ALLOWED_PRODUCTION_DISABLED : VERDICT_BLOCKED_BY_HANDOFF;
        String firstBlockingReason = handoffReady ? "" : handoffReport.firstBlockingReason().orElse("handoffNotReady");
        String firstRemainingWork = handoffReady
                ? PRODUCTION_MUTATION_DISABLED_WORK
                : handoffReport.firstRemainingWork().orElse("produceReadyOptimizerHandoff");
        return new GpuIrOptimizationValidationOptimizerEnablementPolicyDecision(
                handoffReport.methodName(),
                verdict,
                handoffReady,
                false,
                handoffReport.verdict(),
                handoffReady,
                firstBlockingReason,
                firstRemainingWork,
                summary(handoffReport, verdict, handoffReady, firstBlockingReason, firstRemainingWork)
        );
    }

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerEnablementPolicy");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName);
        values.put(prefix + "Verdict", verdict);
        values.put(prefix + "AllowOptimizerEnablementReview", Boolean.toString(allowOptimizerEnablementReview));
        values.put(prefix + "ProductionMutationEnabled", Boolean.toString(productionMutationEnabled));
        values.put(prefix + "HandoffVerdict", handoffVerdict);
        values.put(prefix + "HandoffReadyForOptimizerEnablement", Boolean.toString(handoffReadyForOptimizerEnablement));
        values.put(prefix + "FirstBlockingReason", firstBlockingReason);
        values.put(prefix + "FirstRemainingWork", firstRemainingWork);
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary);
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "optimizer enablement policy method=" + methodName
                + " verdict=" + verdict
                + " allowReview=" + allowOptimizerEnablementReview
                + " productionMutationEnabled=" + productionMutationEnabled
                + (firstBlockingReason.isBlank() ? "" : " firstBlockingReason=" + firstBlockingReason)
                + " firstRemainingWork=" + firstRemainingWork;
    }

    private static String summary(
            GpuIrOptimizationValidationOptimizerReadinessHandoffReport handoffReport,
            String verdict,
            boolean allowReview,
            String firstBlockingReason,
            String firstRemainingWork
    ) {
        return "optimizer enablement policy method=" + handoffReport.methodName()
                + " verdict=" + verdict
                + " allowOptimizerEnablementReview=" + allowReview
                + " productionMutationEnabled=false"
                + " handoffVerdict=" + handoffReport.verdict()
                + (firstBlockingReason.isBlank() ? "" : " firstBlockingReason=" + firstBlockingReason)
                + " firstRemainingWork=" + firstRemainingWork;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
