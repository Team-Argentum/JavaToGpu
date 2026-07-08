package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only aggregate gate for future optimizer production enablement.
 *
 * <p>The gate summarizes the currently independent CSE literal-promotion readiness,
 * auto-vectorization prototype readiness, and conservative optimizer enablement policy into one
 * CI-facing verdict. It intentionally reports what remains blocked without enabling production IR
 * mutation.</p>
 */
public record GpuIrOptimizationValidationOptimizerEnablementGateReport(
        String methodName,
        String verdict,
        boolean readyForProductionMutation,
        boolean cseReadyForProductionMutation,
        boolean cseReadyForEnablementReview,
        boolean autoVectorizationReadyForPrototypeRewrite,
        boolean optimizerEnablementReviewAllowed,
        boolean productionMutationEnabled,
        String cseVerdict,
        String autoVectorizationVerdict,
        String optimizerEnablementPolicyVerdict,
        List<String> blockingReasons,
        List<String> remainingWork
) {
    private static final String VERDICT_CSE_BLOCKED = "notReady/cseBlocked";
    private static final String VERDICT_AUTO_VECTOR_BLOCKED = "notReady/autoVectorizationBlocked";
    private static final String VERDICT_ENABLEMENT_POLICY_BLOCKED = "notReady/optimizerEnablementPolicyBlocked";
    private static final String VERDICT_PRODUCTION_DISABLED = "reviewReady/productionMutationDisabled";
    private static final String VERDICT_READY = "readyForProductionMutation";

    public GpuIrOptimizationValidationOptimizerEnablementGateReport {
        methodName = requireNonBlank(methodName, "methodName");
        verdict = requireNonBlank(verdict, "verdict");
        cseVerdict = requireNonBlank(cseVerdict, "cseVerdict");
        autoVectorizationVerdict = requireNonBlank(autoVectorizationVerdict, "autoVectorizationVerdict");
        optimizerEnablementPolicyVerdict = requireNonBlank(optimizerEnablementPolicyVerdict, "optimizerEnablementPolicyVerdict");
        blockingReasons = List.copyOf(Objects.requireNonNull(blockingReasons, "blockingReasons"));
        remainingWork = List.copyOf(Objects.requireNonNull(remainingWork, "remainingWork"));
        if (readyForProductionMutation != VERDICT_READY.equals(verdict)) {
            throw new IllegalArgumentException("readyForProductionMutation must match ready verdict");
        }
        if (productionMutationEnabled && !readyForProductionMutation) {
            throw new IllegalArgumentException("production mutation cannot be enabled while the aggregate gate is blocked");
        }
        if (cseReadyForProductionMutation && !cseReadyForEnablementReview) {
            throw new IllegalArgumentException("production-ready CSE must also be review-ready");
        }
    }

    public static GpuIrOptimizationValidationOptimizerEnablementGateReport from(
            GpuIrOptimizationValidationReport validationReport,
            GpuIrOptimizationValidationOptimizerEnablementArtifact enablementArtifact
    ) {
        Objects.requireNonNull(validationReport, "validationReport");
        Objects.requireNonNull(enablementArtifact, "enablementArtifact");
        return from(
                validationReport.commonSubexpressionLiteralPromotionReadinessSummaryReport(),
                validationReport.autoVectorizationArtifactSnapshot().readinessSummaryReport(),
                enablementArtifact.policy()
        );
    }

    public static GpuIrOptimizationValidationOptimizerEnablementGateReport from(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport cseReadiness,
            GpuIrAutoVectorizationReadinessSummaryReport autoVectorizationReadiness,
            GpuIrOptimizationValidationOptimizerEnablementPolicyDecision optimizerEnablementPolicy
    ) {
        Objects.requireNonNull(cseReadiness, "cseReadiness");
        Objects.requireNonNull(autoVectorizationReadiness, "autoVectorizationReadiness");
        Objects.requireNonNull(optimizerEnablementPolicy, "optimizerEnablementPolicy");
        if (!cseReadiness.methodName().equals(autoVectorizationReadiness.methodName())) {
            throw new IllegalArgumentException("CSE and auto-vectorization readiness methods must match");
        }
        if (!cseReadiness.methodName().equals(optimizerEnablementPolicy.methodName())) {
            throw new IllegalArgumentException("optimizer enablement policy method must match readiness reports");
        }
        List<String> blockingReasons = blockingReasons(cseReadiness, autoVectorizationReadiness, optimizerEnablementPolicy);
        List<String> remainingWork = remainingWork(cseReadiness, autoVectorizationReadiness, optimizerEnablementPolicy, blockingReasons);
        String verdict = verdict(cseReadiness, autoVectorizationReadiness, optimizerEnablementPolicy, blockingReasons);
        boolean cseReviewReady = cseReadyForEnablementReview(cseReadiness);
        return new GpuIrOptimizationValidationOptimizerEnablementGateReport(
                cseReadiness.methodName(),
                verdict,
                VERDICT_READY.equals(verdict),
                cseReadiness.readyForProductionMutation(),
                cseReviewReady,
                autoVectorizationReadiness.readyForPrototypeRewrite(),
                optimizerEnablementPolicy.allowOptimizerEnablementReview(),
                optimizerEnablementPolicy.productionMutationEnabled(),
                cseReadiness.verdict(),
                autoVectorizationReadiness.verdict(),
                optimizerEnablementPolicy.verdict(),
                blockingReasons,
                remainingWork
        );
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

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerEnablementGate");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName);
        values.put(prefix + "Verdict", verdict);
        values.put(prefix + "ReadyForProductionMutation", Boolean.toString(readyForProductionMutation));
        values.put(prefix + "CseReadyForProductionMutation", Boolean.toString(cseReadyForProductionMutation));
        values.put(prefix + "CseReadyForEnablementReview", Boolean.toString(cseReadyForEnablementReview));
        values.put(prefix + "AutoVectorizationReadyForPrototypeRewrite", Boolean.toString(autoVectorizationReadyForPrototypeRewrite));
        values.put(prefix + "OptimizerEnablementReviewAllowed", Boolean.toString(optimizerEnablementReviewAllowed));
        values.put(prefix + "ProductionMutationEnabled", Boolean.toString(productionMutationEnabled));
        values.put(prefix + "CseVerdict", cseVerdict);
        values.put(prefix + "AutoVectorizationVerdict", autoVectorizationVerdict);
        values.put(prefix + "OptimizerEnablementPolicyVerdict", optimizerEnablementPolicyVerdict);
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

    public String ciSummaryLine() {
        if (readyForProductionMutation) {
            return "optimizer enablement gate ready method=" + methodName;
        }
        return "optimizer enablement gate " + verdict
                + " method=" + methodName
                + " blockers=" + blockingReasonCount()
                + firstBlockingReason().map(reason -> " first=" + reason).orElse("");
    }

    public String summary() {
        return "optimizer enablement gate method=" + methodName
                + " verdict=" + verdict
                + " readyForProductionMutation=" + readyForProductionMutation
                + " cseVerdict=" + cseVerdict
                + " autoVectorizationVerdict=" + autoVectorizationVerdict
                + " optimizerEnablementPolicyVerdict=" + optimizerEnablementPolicyVerdict
                + " productionMutationEnabled=" + productionMutationEnabled
                + " blockingReasons=" + listSummary(blockingReasons)
                + " remainingWork=" + listSummary(remainingWork);
    }

    private static String verdict(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport cseReadiness,
            GpuIrAutoVectorizationReadinessSummaryReport autoVectorizationReadiness,
            GpuIrOptimizationValidationOptimizerEnablementPolicyDecision optimizerEnablementPolicy,
            List<String> blockingReasons
    ) {
        if (!cseReadyForEnablementReview(cseReadiness)) {
            return VERDICT_CSE_BLOCKED;
        }
        if (!autoVectorizationReadiness.readyForPrototypeRewrite()) {
            return VERDICT_AUTO_VECTOR_BLOCKED;
        }
        if (!optimizerEnablementPolicy.allowOptimizerEnablementReview()) {
            return VERDICT_ENABLEMENT_POLICY_BLOCKED;
        }
        if (!optimizerEnablementPolicy.productionMutationEnabled()) {
            return VERDICT_PRODUCTION_DISABLED;
        }
        if (blockingReasons.isEmpty()) {
            return VERDICT_READY;
        }
        return VERDICT_PRODUCTION_DISABLED;
    }

    private static List<String> blockingReasons(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport cseReadiness,
            GpuIrAutoVectorizationReadinessSummaryReport autoVectorizationReadiness,
            GpuIrOptimizationValidationOptimizerEnablementPolicyDecision optimizerEnablementPolicy
    ) {
        java.util.LinkedHashSet<String> reasons = new java.util.LinkedHashSet<>();
        if (!cseReadyForEnablementReview(cseReadiness)) {
            reasons.add("cseLiteralPromotionNotReady");
            cseReadiness.firstBlockingReason().ifPresent(reason -> reasons.add("cse:" + reason));
        }
        if (!autoVectorizationReadiness.readyForPrototypeRewrite()) {
            reasons.add("autoVectorizationNotReady");
            autoVectorizationReadiness.firstBlockingReason().ifPresent(reason -> reasons.add("autoVectorization:" + reason));
        }
        if (!optimizerEnablementPolicy.allowOptimizerEnablementReview()) {
            reasons.add("optimizerEnablementPolicyBlocked");
            if (!optimizerEnablementPolicy.firstBlockingReason().isBlank()) {
                reasons.add("optimizerEnablementPolicy:" + optimizerEnablementPolicy.firstBlockingReason());
            }
        }
        if (!optimizerEnablementPolicy.productionMutationEnabled()) {
            reasons.add("productionMutationDisabled");
        }
        return List.copyOf(reasons);
    }

    private static List<String> remainingWork(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport cseReadiness,
            GpuIrAutoVectorizationReadinessSummaryReport autoVectorizationReadiness,
            GpuIrOptimizationValidationOptimizerEnablementPolicyDecision optimizerEnablementPolicy,
            List<String> blockingReasons
    ) {
        java.util.LinkedHashSet<String> work = new java.util.LinkedHashSet<>();
        if (!cseReadyForEnablementReview(cseReadiness)) {
            work.addAll(cseReadiness.remainingWork());
        }
        work.addAll(autoVectorizationReadiness.remainingWork());
        work.add(optimizerEnablementPolicy.firstRemainingWork());
        for (String reason : blockingReasons) {
            switch (reason) {
                case "cseLiteralPromotionNotReady" -> work.add("clearCseLiteralPromotionReadiness");
                case "autoVectorizationNotReady" -> work.add("clearAutoVectorizationReadiness");
                case "optimizerEnablementPolicyBlocked" -> work.add("produceOptimizerEnablementReviewArtifact");
                case "productionMutationDisabled" -> work.add("enableProductionMutationPolicy");
                default -> {
                    if (reason.startsWith("cse:")) {
                        work.add("reviewCseBlocker:" + reason.substring("cse:".length()));
                    } else if (reason.startsWith("autoVectorization:")) {
                        work.add("reviewAutoVectorizationBlocker:" + reason.substring("autoVectorization:".length()));
                    } else if (reason.startsWith("optimizerEnablementPolicy:")) {
                        work.add("reviewOptimizerEnablementPolicyBlocker:" + reason.substring("optimizerEnablementPolicy:".length()));
                    }
                }
            }
        }
        return List.copyOf(work);
    }

    private static boolean cseReadyForEnablementReview(
            GpuIrCommonSubexpressionSimpleArithmeticLiteralPromotionReadinessSummaryReport cseReadiness
    ) {
        return cseReadiness.readyForProductionMutation()
                || "evidenceCompleteButProductionDisabled".equals(cseReadiness.verdict());
    }

    private static String mapSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
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
