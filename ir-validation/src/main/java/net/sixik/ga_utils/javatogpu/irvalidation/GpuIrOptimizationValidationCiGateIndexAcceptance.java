package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * CI-facing accept/reject decision for the compact optimizer CI gate index.
 *
 * <p>The decision fails closed when the index self-check is inconsistent, and otherwise mirrors
 * the aggregate gate-index accepted/rejected state.</p>
 */
public record GpuIrOptimizationValidationCiGateIndexAcceptance(
        String methodName,
        String verdict,
        boolean accepted,
        boolean failBuild,
        String reason,
        String firstRejectedGate,
        String firstConsistencyFailedCheck,
        String ciSummaryLine
) {
    private static final String ACCEPTED = "accepted";
    private static final String REJECTED = "rejected";
    private static final String ACCEPTED_ALL_GATES_ACCEPTED = "accepted/allGatesAccepted";
    private static final String REJECTED_INCONSISTENT_ARTIFACT = "rejected/inconsistentArtifact";
    private static final String REJECTED_GATE_REJECTED = "rejected/gateRejected";

    public GpuIrOptimizationValidationCiGateIndexAcceptance {
        methodName = requireNonBlank(methodName, "methodName");
        verdict = requireNonBlank(verdict, "verdict");
        reason = requireNonBlank(reason, "reason");
        firstRejectedGate = Objects.requireNonNull(firstRejectedGate, "firstRejectedGate");
        firstConsistencyFailedCheck = Objects.requireNonNull(firstConsistencyFailedCheck, "firstConsistencyFailedCheck");
        ciSummaryLine = requireNonBlank(ciSummaryLine, "ciSummaryLine");
        if (accepted && failBuild) {
            throw new IllegalArgumentException("accepted CI gate index artifacts must not fail the build");
        }
        if (accepted && reason.startsWith("rejected/")) {
            throw new IllegalArgumentException("accepted CI gate index artifacts must not use rejected reasons");
        }
        if (!accepted && reason.startsWith("accepted/")) {
            throw new IllegalArgumentException("rejected CI gate index artifacts must not use accepted reasons");
        }
    }

    public static GpuIrOptimizationValidationCiGateIndexAcceptance from(
            GpuIrOptimizationValidationCiGateIndex index
    ) {
        Objects.requireNonNull(index, "index");
        return from(index, index.consistencyReport());
    }

    public static GpuIrOptimizationValidationCiGateIndexAcceptance from(
            GpuIrOptimizationValidationCiGateIndex index,
            GpuIrOptimizationValidationCiGateIndexConsistencyReport consistency
    ) {
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(consistency, "consistency");
        boolean accepted = consistency.consistent() && index.accepted();
        boolean failBuild = !accepted;
        String reason = reason(index, consistency, accepted);
        String firstConsistencyFailedCheck = consistency.firstFailedCheck().orElse("");
        return new GpuIrOptimizationValidationCiGateIndexAcceptance(
                index.methodName(),
                accepted ? ACCEPTED : REJECTED,
                accepted,
                failBuild,
                reason,
                index.firstRejectedGate(),
                firstConsistencyFailedCheck,
                summaryLine(index, consistency, accepted, failBuild, reason, firstConsistencyFailedCheck)
        );
    }

    public boolean rejected() {
        return !accepted;
    }

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerCiGateIndexAcceptance");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName);
        values.put(prefix + "Verdict", verdict);
        values.put(prefix + "Accepted", Boolean.toString(accepted));
        values.put(prefix + "Rejected", Boolean.toString(rejected()));
        values.put(prefix + "FailBuild", Boolean.toString(failBuild));
        values.put(prefix + "Reason", reason);
        values.put(prefix + "FirstRejectedGate", firstRejectedGate);
        values.put(prefix + "FirstConsistencyFailedCheck", firstConsistencyFailedCheck);
        values.put(prefix + "CiSummaryLine", ciSummaryLine);
        return Collections.unmodifiableMap(values);
    }

    private static String reason(
            GpuIrOptimizationValidationCiGateIndex index,
            GpuIrOptimizationValidationCiGateIndexConsistencyReport consistency,
            boolean accepted
    ) {
        if (!consistency.consistent()) {
            return REJECTED_INCONSISTENT_ARTIFACT;
        }
        if (accepted) {
            return ACCEPTED_ALL_GATES_ACCEPTED;
        }
        return REJECTED_GATE_REJECTED;
    }

    private static String summaryLine(
            GpuIrOptimizationValidationCiGateIndex index,
            GpuIrOptimizationValidationCiGateIndexConsistencyReport consistency,
            boolean accepted,
            boolean failBuild,
            String reason,
            String firstConsistencyFailedCheck
    ) {
        return "optimizer CI gate index acceptance method=" + index.methodName()
                + " verdict=" + (accepted ? ACCEPTED : REJECTED)
                + " accepted=" + accepted
                + " failBuild=" + failBuild
                + " reason=" + reason
                + " consistent=" + consistency.consistent()
                + " firstRejectedGate=" + index.firstRejectedGate()
                + (firstConsistencyFailedCheck.isBlank() ? "" : " firstConsistencyFailedCheck=" + firstConsistencyFailedCheck);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
