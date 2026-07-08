package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * One read-only CI artifact for the full optimizer production-readiness chain.
 *
 * <p>This layer keeps the existing lower-level decisions intact while exposing a compact
 * top-level answer for A1/A2 review: validation bundle, production preflight, future mutation
 * switch contract, and selected-rewrite promotion confidence.</p>
 */
public record GpuIrOptimizationValidationProductionReadinessArtifact(
        GpuIrOptimizationValidationOptimizerValidationBundle bundle,
        GpuIrOptimizationValidationProductionEnablementPreflightDecision preflight,
        GpuIrOptimizationValidationProductionMutationSwitchContract switchContract,
        GpuIrOptimizationValidationOptimizerPromotionConfidenceContract promotionConfidence
) {
    public GpuIrOptimizationValidationProductionReadinessArtifact {
        bundle = Objects.requireNonNull(bundle, "bundle");
        preflight = Objects.requireNonNull(preflight, "preflight");
        switchContract = Objects.requireNonNull(switchContract, "switchContract");
        promotionConfidence = Objects.requireNonNull(promotionConfidence, "promotionConfidence");
        if (!bundle.methodName().equals(preflight.methodName())) {
            throw new IllegalArgumentException("preflight method must match validation bundle method");
        }
        if (!bundle.methodName().equals(switchContract.methodName())) {
            throw new IllegalArgumentException("switch contract method must match validation bundle method");
        }
        if (!bundle.methodName().equals(promotionConfidence.methodName())) {
            throw new IllegalArgumentException("promotion confidence method must match validation bundle method");
        }
    }

    public static GpuIrOptimizationValidationProductionReadinessArtifact from(
            GpuIrOptimizationValidationOptimizerValidationBundle bundle
    ) {
        Objects.requireNonNull(bundle, "bundle");
        GpuIrOptimizationValidationProductionEnablementPreflightDecision preflight =
                bundle.productionPreflightDecision();
        GpuIrOptimizationValidationProductionMutationSwitchContract switchContract =
                GpuIrOptimizationValidationProductionMutationSwitchContract.from(preflight);
        GpuIrOptimizationValidationOptimizerPromotionConfidenceContract promotionConfidence =
                GpuIrOptimizationValidationOptimizerPromotionConfidenceContract.from(switchContract);
        return new GpuIrOptimizationValidationProductionReadinessArtifact(
                bundle,
                preflight,
                switchContract,
                promotionConfidence
        );
    }

    public String methodName() {
        return bundle.methodName();
    }

    public String verdict() {
        return promotionConfidence.verdict();
    }

    public boolean blocked() {
        return !promotionAllowed();
    }

    public boolean reviewReady() {
        return preflight.reviewReady() || switchContract.eligibleForSwitchReview();
    }

    public boolean productionMutationEnabled() {
        return promotionConfidence.productionMutationEnabled();
    }

    public boolean promotionAllowed() {
        return promotionConfidence.promotionAllowed();
    }

    public String bundleVerdict() {
        return bundle.verdict();
    }

    public String preflightVerdict() {
        return preflight.verdict();
    }

    public String switchVerdict() {
        return switchContract.verdict();
    }

    public String promotionConfidenceVerdict() {
        return promotionConfidence.verdict();
    }

    public List<String> blockingReasons() {
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        promotionConfidence.blockingReasons().forEach(reasons::add);
        switchContract.blockingReasons().forEach(reason -> reasons.add("switch:" + reason));
        if (!preflight.firstBlockingReason().isBlank()) {
            reasons.add("preflight:" + preflight.firstBlockingReason());
        }
        bundle.enablementGate().firstBlockingReason().ifPresent(reason -> reasons.add("gate:" + reason));
        return List.copyOf(reasons);
    }

    public int blockingReasonCount() {
        return blockingReasons().size();
    }

    public Optional<String> firstBlockingReason() {
        return blockingReasons().stream().findFirst();
    }

    public String firstBlockingStage() {
        if (!bundle.readyForProductionMutation()) {
            return "validationBundle";
        }
        if (preflight.blocked()) {
            return "productionPreflight";
        }
        if (!switchContract.productionMutationEnabled()) {
            return "productionSwitch";
        }
        if (!promotionConfidence.runtimeConfidenceStable()) {
            return "runtimeConfidence";
        }
        if (!promotionAllowed()) {
            return "promotionConfidence";
        }
        return "none";
    }

    public List<String> remainingWork() {
        LinkedHashSet<String> work = new LinkedHashSet<>();
        promotionConfidence.remainingWork().forEach(work::add);
        switchContract.remainingWork().forEach(work::add);
        work.add(preflight.firstRemainingWork());
        bundle.enablementGate().remainingWork().forEach(work::add);
        return List.copyOf(work);
    }

    public int remainingWorkCount() {
        return remainingWork().size();
    }

    public Optional<String> firstRemainingWork() {
        return remainingWork().stream().findFirst();
    }

    public GpuIrOptimizationValidationProductionReadinessArtifactConsistencyReport consistencyReport() {
        return GpuIrOptimizationValidationProductionReadinessArtifactConsistencyReport.from(this);
    }

    public GpuIrOptimizationValidationProductionReadinessArtifactAcceptance acceptanceDecision() {
        return GpuIrOptimizationValidationProductionReadinessArtifactAcceptance.from(this);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerProductionReadiness");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName());
        values.put(prefix + "Verdict", verdict());
        values.put(prefix + "Blocked", Boolean.toString(blocked()));
        values.put(prefix + "ReviewReady", Boolean.toString(reviewReady()));
        values.put(prefix + "ProductionMutationEnabled", Boolean.toString(productionMutationEnabled()));
        values.put(prefix + "PromotionAllowed", Boolean.toString(promotionAllowed()));
        values.put(prefix + "BundleVerdict", bundleVerdict());
        values.put(prefix + "PreflightVerdict", preflightVerdict());
        values.put(prefix + "SwitchVerdict", switchVerdict());
        values.put(prefix + "PromotionConfidenceVerdict", promotionConfidenceVerdict());
        values.put(prefix + "BlockingReasons", listSummary(blockingReasons()));
        values.put(prefix + "BlockingReasonCount", Integer.toString(blockingReasonCount()));
        values.put(prefix + "FirstBlockingStage", firstBlockingStage());
        values.put(prefix + "StageVerdicts", stageVerdicts());
        values.put(prefix + "RemainingWork", listSummary(remainingWork()));
        values.put(prefix + "RemainingWorkCount", Integer.toString(remainingWorkCount()));
        firstBlockingReason().ifPresent(reason -> values.put(prefix + "FirstBlockingReason", reason));
        firstRemainingWork().ifPresent(work -> values.put(prefix + "FirstRemainingWork", work));
        values.put(prefix + "Consistent", Boolean.toString(consistencyReport().consistent()));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        bundle.putArtifactFields(values);
        values.putAll(preflight.artifactFields());
        values.putAll(switchContract.artifactFields());
        values.putAll(promotionConfidence.artifactFields());
        values.putAll(consistencyReport().artifactFields(prefix + "Consistency"));
        values.putAll(acceptanceDecision().artifactFields(prefix + "Acceptance"));
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "optimizer production readiness method=" + methodName()
                + " verdict=" + verdict()
                + " blocked=" + blocked()
                + " reviewReady=" + reviewReady()
                + " productionMutationEnabled=" + productionMutationEnabled()
                + " promotionAllowed=" + promotionAllowed()
                + " firstBlockingStage=" + firstBlockingStage()
                + firstBlockingReason().map(reason -> " firstBlockingReason=" + reason).orElse("")
                + " firstRemainingWork=" + firstRemainingWork().orElse("none");
    }

    public String summary() {
        return "optimizer production readiness method=" + methodName()
                + " verdict=" + verdict()
                + " bundleVerdict=" + bundleVerdict()
                + " preflightVerdict=" + preflightVerdict()
                + " switchVerdict=" + switchVerdict()
                + " promotionConfidenceVerdict=" + promotionConfidenceVerdict()
                + " firstBlockingStage=" + firstBlockingStage()
                + " blockingReasons=" + listSummary(blockingReasons())
                + " remainingWork=" + listSummary(remainingWork());
    }

    private String stageVerdicts() {
        return "{bundle=" + bundleVerdict()
                + ",preflight=" + preflightVerdict()
                + ",switch=" + switchVerdict()
                + ",promotionConfidence=" + promotionConfidenceVerdict()
                + "}";
    }

    private static String listSummary(List<String> values) {
        return values.stream().collect(Collectors.joining(",", "[", "]"));
    }
}
