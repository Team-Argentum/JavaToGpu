package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * CI-facing accept/reject decision for optimizer-gate artifact consistency.
 */
public record GpuIrOptimizerGateArtifactAcceptance(
        String verdict,
        boolean accepted,
        boolean failBuild,
        String reason,
        String firstConsistencyFailedCheck,
        String ciSummaryLine
) {
    private static final String ACCEPTED = "accepted";
    private static final String REJECTED = "rejected";
    private static final String ACCEPTED_CONSISTENT_ARTIFACT = "accepted/consistentArtifact";
    private static final String REJECTED_INCONSISTENT_ARTIFACT = "rejected/inconsistentArtifact";

    public GpuIrOptimizerGateArtifactAcceptance {
        verdict = requireNonBlank(verdict, "verdict");
        reason = requireNonBlank(reason, "reason");
        firstConsistencyFailedCheck = Objects.requireNonNull(firstConsistencyFailedCheck, "firstConsistencyFailedCheck");
        ciSummaryLine = requireNonBlank(ciSummaryLine, "ciSummaryLine");
        if (accepted && failBuild) {
            throw new IllegalArgumentException("accepted optimizer gate artifacts must not fail the build");
        }
        if (accepted && reason.startsWith("rejected/")) {
            throw new IllegalArgumentException("accepted optimizer gate artifacts must not use rejected reasons");
        }
        if (!accepted && reason.startsWith("accepted/")) {
            throw new IllegalArgumentException("rejected optimizer gate artifacts must not use accepted reasons");
        }
    }

    public static GpuIrOptimizerGateArtifactAcceptance from(GpuIrOptimizerGateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return from(snapshot.consistencyReport());
    }

    public static GpuIrOptimizerGateArtifactAcceptance from(GpuIrOptimizerGateConsistencyReport consistencyReport) {
        Objects.requireNonNull(consistencyReport, "consistencyReport");
        boolean accepted = consistencyReport.consistent();
        boolean failBuild = !accepted;
        String reason = accepted ? ACCEPTED_CONSISTENT_ARTIFACT : REJECTED_INCONSISTENT_ARTIFACT;
        String firstFailedCheck = consistencyReport.firstFailedCheck().orElse("");
        return new GpuIrOptimizerGateArtifactAcceptance(
                accepted ? ACCEPTED : REJECTED,
                accepted,
                failBuild,
                reason,
                firstFailedCheck,
                summaryLine(consistencyReport, accepted, failBuild, reason, firstFailedCheck)
        );
    }

    public boolean rejected() {
        return !accepted;
    }

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerGateAcceptance");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Verdict", verdict);
        values.put(prefix + "Accepted", Boolean.toString(accepted));
        values.put(prefix + "Rejected", Boolean.toString(rejected()));
        values.put(prefix + "FailBuild", Boolean.toString(failBuild));
        values.put(prefix + "Reason", reason);
        values.put(prefix + "FirstConsistencyFailedCheck", firstConsistencyFailedCheck);
        values.put(prefix + "CiSummaryLine", ciSummaryLine);
        return Collections.unmodifiableMap(values);
    }

    private static String summaryLine(
            GpuIrOptimizerGateConsistencyReport consistencyReport,
            boolean accepted,
            boolean failBuild,
            String reason,
            String firstFailedCheck
    ) {
        return "optimizer gate artifact acceptance verdict=" + (accepted ? ACCEPTED : REJECTED)
                + " accepted=" + accepted
                + " failBuild=" + failBuild
                + " reason=" + reason
                + " consistent=" + consistencyReport.consistent()
                + " failedChecks=" + consistencyReport.failedCheckCount()
                + (firstFailedCheck.isBlank() ? "" : " firstConsistencyFailedCheck=" + firstFailedCheck);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
