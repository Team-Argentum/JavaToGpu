package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Compact CI decision for a stored optimizer-layer readiness baseline comparison.
 *
 * <p>This summary is intentionally stricter than the raw comparison fields: a stored baseline
 * only fails the CI-facing decision when the current report regresses. Improvements and neutral
 * changes are exported as accepted states so CI can log movement without blocking forward work.</p>
 */
public record GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary(
        GpuIrOptimizationValidationOptimizerLayerReadinessBaselineComparisonReport comparison
) {
    public GpuIrOptimizationValidationOptimizerLayerReadinessBaselineCiSummary {
        comparison = Objects.requireNonNull(comparison, "comparison");
    }

    public String methodName() {
        return comparison.methodName();
    }

    public String outcome() {
        return comparison.outcome();
    }

    public boolean failBuild() {
        return comparison.regressed();
    }

    public boolean accepted() {
        return !failBuild();
    }

    public boolean rejected() {
        return failBuild();
    }

    public String decision() {
        return failBuild() ? "fail/regressionDetected" : "accepted/noRegression";
    }

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerLayerReadinessBaselineCi");
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", methodName());
        values.put(prefix + "Outcome", outcome());
        values.put(prefix + "Decision", decision());
        values.put(prefix + "Accepted", Boolean.toString(accepted()));
        values.put(prefix + "Rejected", Boolean.toString(rejected()));
        values.put(prefix + "FailBuild", Boolean.toString(failBuild()));
        values.put(prefix + "BaselineVerdict", comparison.baseline().verdict());
        values.put(prefix + "CurrentVerdict", comparison.current().verdict());
        values.put(prefix + "BlockingLayerDelta", Integer.toString(comparison.blockingLayerDelta()));
        values.put(prefix + "ReadyLayerDelta", Integer.toString(comparison.readyLayerDelta()));
        values.put(prefix + "ChangedLayerCount", Integer.toString(comparison.changedLayers().size()));
        values.put(prefix + "ImprovedLayerCount", Integer.toString(comparison.improvedLayers().size()));
        values.put(prefix + "RegressedLayerCount", Integer.toString(comparison.regressedLayers().size()));
        comparison.firstChangedLayer().ifPresent(layer -> values.put(prefix + "FirstChangedLayer", layer));
        comparison.firstImprovedLayer().ifPresent(layer -> values.put(prefix + "FirstImprovedLayer", layer));
        comparison.firstRegressedLayer().ifPresent(layer -> values.put(prefix + "FirstRegressedLayer", layer));
        values.put(prefix + "CiSummaryLine", ciSummaryLine());
        values.put(prefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "optimizer layer readiness baseline ci method=" + methodName()
                + " outcome=" + outcome()
                + " decision=" + decision()
                + " failBuild=" + failBuild()
                + " blockingLayerDelta=" + comparison.blockingLayerDelta()
                + " readyLayerDelta=" + comparison.readyLayerDelta()
                + comparison.firstChangedLayer().map(layer -> " firstChangedLayer=" + layer).orElse("");
    }

    public String summary() {
        return ciSummaryLine()
                + " comparisonSummary={" + comparison.ciSummaryLine() + "}";
    }

    public static Map<String, String> invalidBaselineArtifactFields(
            String methodName,
            RuntimeException failure
    ) {
        return invalidBaselineArtifactFields(
                "optimizerLayerReadinessBaselineCi",
                methodName,
                methodName,
                failure
        );
    }

    public static Map<String, String> invalidBaselineArtifactFields(
            String prefix,
            String methodName,
            RuntimeException failure
    ) {
        return invalidBaselineArtifactFields(prefix, methodName, methodName, failure);
    }

    public static Map<String, String> invalidBaselineArtifactFields(
            String prefix,
            String methodName,
            String baselineSourceMethod,
            RuntimeException failure
    ) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Objects.requireNonNull(failure, "failure");
        String resolvedMethodName = methodName == null || methodName.isBlank() ? "unknown" : methodName;
        String resolvedBaselineSourceMethod = baselineSourceMethod == null || baselineSourceMethod.isBlank()
                ? "unknown"
                : baselineSourceMethod;
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Method", resolvedMethodName);
        values.put(prefix + "CurrentMethod", resolvedMethodName);
        values.put(prefix + "BaselineSourceMethod", resolvedBaselineSourceMethod);
        values.put(prefix + "Outcome", "invalidBaseline");
        values.put(prefix + "Decision", "fail/invalidBaseline");
        values.put(prefix + "Accepted", "false");
        values.put(prefix + "Rejected", "true");
        values.put(prefix + "FailBuild", "true");
        values.put(prefix + "InvalidBaseline", "true");
        values.put(prefix + "FailureType", failure.getClass().getSimpleName());
        values.put(prefix + "FailureMessage", failure.getMessage() == null ? "" : failure.getMessage());
        values.put(prefix + "CiSummaryLine", invalidBaselineCiSummaryLine(
                resolvedMethodName,
                resolvedBaselineSourceMethod,
                failure
        ));
        values.put(prefix + "Summary", invalidBaselineCiSummaryLine(
                resolvedMethodName,
                resolvedBaselineSourceMethod,
                failure
        ));
        return Collections.unmodifiableMap(values);
    }

    private static String invalidBaselineCiSummaryLine(
            String methodName,
            String baselineSourceMethod,
            RuntimeException failure
    ) {
        return "optimizer layer readiness baseline ci method=" + methodName
                + " currentMethod=" + methodName
                + " baselineSourceMethod=" + baselineSourceMethod
                + " outcome=invalidBaseline"
                + " decision=fail/invalidBaseline"
                + " failBuild=true"
                + " failureType=" + failure.getClass().getSimpleName()
                + " failureMessage=" + (failure.getMessage() == null ? "" : failure.getMessage());
    }
}
