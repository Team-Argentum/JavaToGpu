package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * CI-facing acceptance decision for the aggregate production-readiness artifact.
 *
 * <p>The decision fails closed on inconsistent nested contracts, rejects blocked production
 * readiness states, and only accepts artifacts where selected rewrite promotion is allowed.</p>
 */
public record GpuIrOptimizationValidationProductionReadinessArtifactAcceptance(
        String methodName,
        String verdict,
        boolean accepted,
        String reason,
        String firstBlockingReason,
        String firstRemainingWork,
        String firstConsistencyFailedCheck,
        String ciSummaryLine
) {
    private static final String ACCEPTED_PRODUCTION_READY = "accepted/productionReadinessReady";
    private static final String REJECTED_INCONSISTENT_ARTIFACT = "rejected/inconsistentArtifact";
    private static final String REJECTED_PRODUCTION_READINESS_BLOCKED = "rejected/productionReadinessBlocked";

    public GpuIrOptimizationValidationProductionReadinessArtifactAcceptance {
        methodName = requireNonBlank(methodName, "methodName");
        verdict = requireNonBlank(verdict, "verdict");
        reason = requireNonBlank(reason, "reason");
        firstBlockingReason = Objects.requireNonNull(firstBlockingReason, "firstBlockingReason");
        firstRemainingWork = Objects.requireNonNull(firstRemainingWork, "firstRemainingWork");
        firstConsistencyFailedCheck = Objects.requireNonNull(firstConsistencyFailedCheck, "firstConsistencyFailedCheck");
        ciSummaryLine = requireNonBlank(ciSummaryLine, "ciSummaryLine");
        if (accepted && reason.startsWith("rejected/")) {
            throw new IllegalArgumentException("accepted production-readiness decisions must not use rejected reasons");
        }
        if (!accepted && reason.startsWith("accepted/")) {
            throw new IllegalArgumentException("rejected production-readiness decisions must not use accepted reasons");
        }
    }

    public static GpuIrOptimizationValidationProductionReadinessArtifactAcceptance from(
            GpuIrOptimizationValidationProductionReadinessArtifact artifact
    ) {
        Objects.requireNonNull(artifact, "artifact");
        GpuIrOptimizationValidationProductionReadinessArtifactConsistencyReport consistency =
                artifact.consistencyReport();
        boolean accepted = consistency.consistent() && artifact.promotionAllowed();
        String reason = reason(artifact, consistency, accepted);
        String firstConsistencyFailedCheck = consistency.failedCheckList().stream().findFirst().orElse("");
        return new GpuIrOptimizationValidationProductionReadinessArtifactAcceptance(
                artifact.methodName(),
                artifact.verdict(),
                accepted,
                reason,
                artifact.firstBlockingReason().orElse(""),
                artifact.firstRemainingWork().orElse(""),
                firstConsistencyFailedCheck,
                summaryLine(artifact, consistency, accepted, reason, firstConsistencyFailedCheck)
        );
    }

    public boolean rejected() {
        return !accepted;
    }

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerProductionReadinessAcceptance");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + ".Method", methodName);
        values.put(prefix + ".Verdict", verdict);
        values.put(prefix + ".Accepted", Boolean.toString(accepted));
        values.put(prefix + ".Rejected", Boolean.toString(rejected()));
        values.put(prefix + ".Reason", reason);
        values.put(prefix + ".FirstBlockingReason", firstBlockingReason);
        values.put(prefix + ".FirstRemainingWork", firstRemainingWork);
        values.put(prefix + ".FirstConsistencyFailedCheck", firstConsistencyFailedCheck);
        values.put(prefix + ".CiSummaryLine", ciSummaryLine);
        return Collections.unmodifiableMap(values);
    }

    private static String reason(
            GpuIrOptimizationValidationProductionReadinessArtifact artifact,
            GpuIrOptimizationValidationProductionReadinessArtifactConsistencyReport consistency,
            boolean accepted
    ) {
        if (!consistency.consistent()) {
            return REJECTED_INCONSISTENT_ARTIFACT;
        }
        if (accepted) {
            return ACCEPTED_PRODUCTION_READY;
        }
        return REJECTED_PRODUCTION_READINESS_BLOCKED;
    }

    private static String summaryLine(
            GpuIrOptimizationValidationProductionReadinessArtifact artifact,
            GpuIrOptimizationValidationProductionReadinessArtifactConsistencyReport consistency,
            boolean accepted,
            String reason,
            String firstConsistencyFailedCheck
    ) {
        return "optimizer production readiness acceptance method=" + artifact.methodName()
                + " verdict=" + artifact.verdict()
                + " accepted=" + accepted
                + " reason=" + reason
                + " consistent=" + consistency.consistent()
                + " blocked=" + artifact.blocked()
                + " promotionAllowed=" + artifact.promotionAllowed()
                + artifact.firstBlockingReason().map(blocker -> " firstBlockingReason=" + blocker).orElse("")
                + (firstConsistencyFailedCheck.isBlank() ? "" : " firstConsistencyFailedCheck=" + firstConsistencyFailedCheck);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
