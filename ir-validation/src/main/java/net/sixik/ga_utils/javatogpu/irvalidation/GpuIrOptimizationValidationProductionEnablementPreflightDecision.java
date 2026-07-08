package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Final read-only preflight decision before future optimizer production enablement.
 *
 * <p>This decision intentionally sits above the validation bundle. It classifies the current
 * state as blocked, review-ready with production mutation still disabled, or future-ready. It does
 * not enable mutation and only exports stable CI/tooling fields for A1/A2 enablement reviews.</p>
 */
public record GpuIrOptimizationValidationProductionEnablementPreflightDecision(
        String methodName,
        String verdict,
        boolean blocked,
        boolean reviewReady,
        boolean readyForProductionMutation,
        boolean productionMutationEnabled,
        String bundleVerdict,
        String firstBlockingReason,
        String firstRemainingWork,
        String summary
) {
    private static final String VERDICT_BLOCKED = "blocked/optimizerValidationBundleNotReady";
    private static final String VERDICT_REVIEW_READY_PRODUCTION_DISABLED = "reviewReady/productionMutationDisabled";
    private static final String VERDICT_READY = "ready/productionMutationEnabled";

    public GpuIrOptimizationValidationProductionEnablementPreflightDecision {
        methodName = requireNonBlank(methodName, "methodName");
        verdict = requireNonBlank(verdict, "verdict");
        bundleVerdict = requireNonBlank(bundleVerdict, "bundleVerdict");
        firstBlockingReason = Objects.requireNonNull(firstBlockingReason, "firstBlockingReason");
        firstRemainingWork = requireNonBlank(firstRemainingWork, "firstRemainingWork");
        summary = requireNonBlank(summary, "summary");
        if (readyForProductionMutation && !productionMutationEnabled) {
            throw new IllegalArgumentException("ready production preflight requires production mutation enabled");
        }
        if (readyForProductionMutation && !VERDICT_READY.equals(verdict)) {
            throw new IllegalArgumentException("ready preflight decisions must use the ready verdict");
        }
        if (blocked && (reviewReady || readyForProductionMutation)) {
            throw new IllegalArgumentException("blocked preflight decisions cannot also be review-ready or ready");
        }
        if (!blocked && !reviewReady && !readyForProductionMutation) {
            throw new IllegalArgumentException("preflight decision must be blocked, review-ready, or ready");
        }
    }

    public static GpuIrOptimizationValidationProductionEnablementPreflightDecision from(
            GpuIrOptimizationValidationOptimizerValidationBundle bundle
    ) {
        Objects.requireNonNull(bundle, "bundle");
        GpuIrOptimizationValidationOptimizerEnablementGateReport gate = bundle.enablementGate();
        boolean ready = gate.readyForProductionMutation();
        boolean productionEnabled = gate.productionMutationEnabled();
        boolean reviewReady = reviewReady(gate, ready, productionEnabled);
        boolean blocked = !ready && !reviewReady;
        String verdict = verdict(blocked, reviewReady, ready);
        String firstBlockingReason = blocked ? gate.firstBlockingReason().orElse("optimizerValidationBundleNotReady") : "";
        String firstRemainingWork = gate.firstRemainingWork().orElse(productionEnabled ? "none" : "enableProductionMutationPolicy");
        return new GpuIrOptimizationValidationProductionEnablementPreflightDecision(
                bundle.methodName(),
                verdict,
                blocked,
                reviewReady,
                ready,
                productionEnabled,
                bundle.verdict(),
                firstBlockingReason,
                firstRemainingWork,
                summary(bundle, verdict, blocked, reviewReady, ready, productionEnabled, firstBlockingReason, firstRemainingWork)
        );
    }

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerProductionPreflight");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName);
        values.put(prefix + "Verdict", verdict);
        values.put(prefix + "Blocked", Boolean.toString(blocked));
        values.put(prefix + "ReviewReady", Boolean.toString(reviewReady));
        values.put(prefix + "ReadyForProductionMutation", Boolean.toString(readyForProductionMutation));
        values.put(prefix + "ProductionMutationEnabled", Boolean.toString(productionMutationEnabled));
        values.put(prefix + "BundleVerdict", bundleVerdict);
        values.put(prefix + "FirstBlockingReason", firstBlockingReason);
        values.put(prefix + "FirstRemainingWork", firstRemainingWork);
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary);
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "optimizer production preflight method=" + methodName
                + " verdict=" + verdict
                + " blocked=" + blocked
                + " reviewReady=" + reviewReady
                + " readyForProductionMutation=" + readyForProductionMutation
                + " productionMutationEnabled=" + productionMutationEnabled
                + (firstBlockingReason.isBlank() ? "" : " firstBlockingReason=" + firstBlockingReason)
                + " firstRemainingWork=" + firstRemainingWork;
    }

    private static String verdict(boolean blocked, boolean reviewReady, boolean ready) {
        if (ready) {
            return VERDICT_READY;
        }
        if (reviewReady) {
            return VERDICT_REVIEW_READY_PRODUCTION_DISABLED;
        }
        if (blocked) {
            return VERDICT_BLOCKED;
        }
        throw new IllegalArgumentException("invalid production preflight state");
    }

    private static boolean reviewReady(
            GpuIrOptimizationValidationOptimizerEnablementGateReport gate,
            boolean ready,
            boolean productionEnabled
    ) {
        return !ready
                && gate.cseReadyForEnablementReview()
                && gate.autoVectorizationReadyForPrototypeRewrite()
                && gate.optimizerEnablementReviewAllowed()
                && !productionEnabled;
    }

    private static String summary(
            GpuIrOptimizationValidationOptimizerValidationBundle bundle,
            String verdict,
            boolean blocked,
            boolean reviewReady,
            boolean ready,
            boolean productionEnabled,
            String firstBlockingReason,
            String firstRemainingWork
    ) {
        return "optimizer production preflight method=" + bundle.methodName()
                + " verdict=" + verdict
                + " blocked=" + blocked
                + " reviewReady=" + reviewReady
                + " readyForProductionMutation=" + ready
                + " productionMutationEnabled=" + productionEnabled
                + " bundleVerdict=" + bundle.verdict()
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
