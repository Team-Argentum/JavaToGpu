package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Self-check report for the aggregate optimizer production-readiness artifact.
 *
 * <p>The production-readiness artifact intentionally combines several nested contracts. This
 * report lets CI fail closed if those contracts drift apart while still keeping normal validation
 * read-only and detached from production mutation.</p>
 */
public record GpuIrOptimizationValidationProductionReadinessArtifactConsistencyReport(
        String methodName,
        boolean consistent,
        int checks,
        List<String> failedCheckList
) {
    public GpuIrOptimizationValidationProductionReadinessArtifactConsistencyReport {
        methodName = requireNonBlank(methodName, "methodName");
        failedCheckList = List.copyOf(Objects.requireNonNull(failedCheckList, "failedCheckList"));
        if (failedCheckList.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("failedCheckList must not contain blank entries");
        }
        if (checks < 0) {
            throw new IllegalArgumentException("checks must not be negative");
        }
        if (consistent != failedCheckList.isEmpty()) {
            throw new IllegalArgumentException("consistent must match failed check list emptiness");
        }
    }

    public static GpuIrOptimizationValidationProductionReadinessArtifactConsistencyReport from(
            GpuIrOptimizationValidationProductionReadinessArtifact artifact
    ) {
        Objects.requireNonNull(artifact, "artifact");
        java.util.ArrayList<String> failures = new java.util.ArrayList<>();
        int checks = 0;

        checks++;
        if (!artifact.bundle().methodName().equals(artifact.preflight().methodName())) {
            failures.add("bundleMethodMatchesPreflight");
        }
        checks++;
        if (!artifact.bundle().methodName().equals(artifact.switchContract().methodName())) {
            failures.add("bundleMethodMatchesSwitchContract");
        }
        checks++;
        if (!artifact.bundle().methodName().equals(artifact.promotionConfidence().methodName())) {
            failures.add("bundleMethodMatchesPromotionConfidence");
        }
        checks++;
        if (!artifact.bundle().verdict().equals(artifact.preflight().bundleVerdict())) {
            failures.add("preflightBundleVerdictMatchesBundle");
        }
        checks++;
        if (!artifact.preflight().verdict().equals(artifact.switchContract().preflightVerdict())) {
            failures.add("switchPreflightVerdictMatchesPreflight");
        }
        checks++;
        if (artifact.preflight().reviewReady() != artifact.switchContract().preflightReviewReady()) {
            failures.add("switchPreflightReviewReadyMatchesPreflight");
        }
        checks++;
        if (artifact.preflight().readyForProductionMutation()
                != artifact.switchContract().preflightReadyForProductionMutation()) {
            failures.add("switchPreflightReadyForMutationMatchesPreflight");
        }
        checks++;
        if (!artifact.switchContract().verdict().equals(artifact.promotionConfidence().switchVerdict())) {
            failures.add("promotionSwitchVerdictMatchesSwitchContract");
        }
        checks++;
        if (artifact.switchContract().eligibleForSwitchReview()
                != artifact.promotionConfidence().switchReviewEligible()) {
            failures.add("promotionSwitchReviewEligibilityMatchesSwitchContract");
        }
        checks++;
        if (artifact.switchContract().productionMutationEnabled()
                != artifact.promotionConfidence().productionMutationEnabled()) {
            failures.add("promotionProductionMutationEnabledMatchesSwitchContract");
        }
        checks++;
        if (!artifact.verdict().equals(artifact.promotionConfidence().verdict())) {
            failures.add("artifactVerdictMatchesPromotionConfidence");
        }

        return new GpuIrOptimizationValidationProductionReadinessArtifactConsistencyReport(
                artifact.methodName(),
                failures.isEmpty(),
                checks,
                failures
        );
    }

    public int failedChecks() {
        return failedCheckList.size();
    }

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerProductionReadinessConsistency");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + ".Method", methodName);
        values.put(prefix + ".Consistent", Boolean.toString(consistent));
        values.put(prefix + ".Checks", Integer.toString(checks));
        values.put(prefix + ".FailedChecks", Integer.toString(failedChecks()));
        values.put(prefix + ".FailedCheckList", listSummary(failedCheckList));
        values.put(prefix + ".CiSummaryLine", ciSummaryLine());
        values.put(prefix + ".Summary", summary());
        for (int index = 0; index < failedCheckList.size(); index++) {
            values.put(prefix + ".FailedCheck." + index, failedCheckList.get(index));
        }
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "optimizer production readiness consistency method=" + methodName
                + " consistent=" + consistent
                + " failedChecks=" + failedChecks()
                + (failedCheckList.isEmpty() ? "" : " firstFailedCheck=" + failedCheckList.get(0));
    }

    public String summary() {
        return "optimizer production readiness consistency method=" + methodName
                + " checks=" + checks
                + " failedChecks=" + failedChecks()
                + " failedCheckList=" + listSummary(failedCheckList);
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
