package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit artifact for opt-in CSE rewrite equivalence checks.
 *
 * <p>This report stores evidence produced by tests or integration runners. It does not execute
 * rewrites and does not connect the mutating prototype CSE path to normal validation.</p>
 */
public record GpuIrCommonSubexpressionRuntimeEquivalenceReport(
        GpuIrCommonSubexpressionRewritePlanReport rewritePlanReport,
        boolean equivalent,
        int inputCaseCount,
        List<String> comparedOutputs,
        List<String> diagnostics
) {
    public GpuIrCommonSubexpressionRuntimeEquivalenceReport {
        rewritePlanReport = Objects.requireNonNull(rewritePlanReport, "rewritePlanReport");
        if (inputCaseCount < 0) {
            throw new IllegalArgumentException("inputCaseCount must be non-negative");
        }
        Objects.requireNonNull(comparedOutputs, "comparedOutputs");
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (comparedOutputs.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("comparedOutputs must not contain blank entries");
        }
        if (diagnostics.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("diagnostics must not contain null entries");
        }
        comparedOutputs = List.copyOf(comparedOutputs);
        diagnostics = List.copyOf(diagnostics);
    }

    public static GpuIrCommonSubexpressionRuntimeEquivalenceReport equivalent(
            GpuIrCommonSubexpressionRewritePlanReport rewritePlanReport,
            int inputCaseCount,
            List<String> comparedOutputs
    ) {
        return new GpuIrCommonSubexpressionRuntimeEquivalenceReport(
                rewritePlanReport,
                true,
                inputCaseCount,
                comparedOutputs,
                List.of()
        );
    }

    public static GpuIrCommonSubexpressionRuntimeEquivalenceReport failed(
            GpuIrCommonSubexpressionRewritePlanReport rewritePlanReport,
            int inputCaseCount,
            List<String> comparedOutputs,
            List<String> diagnostics
    ) {
        return new GpuIrCommonSubexpressionRuntimeEquivalenceReport(
                rewritePlanReport,
                false,
                inputCaseCount,
                comparedOutputs,
                diagnostics
        );
    }

    public boolean successful() {
        return equivalent && diagnostics.isEmpty();
    }

    public boolean hasDiagnostics() {
        return !diagnostics.isEmpty();
    }

    public int comparedOutputCount() {
        return comparedOutputs.size();
    }

    public int diagnosticCount() {
        return diagnostics.size();
    }

    public String firstDiagnostic() {
        return diagnostics.isEmpty() ? "" : diagnostics.get(0);
    }

    /**
     * Exposes stable string fields for explicit CSE runtime-equivalence artifact writers.
     */
    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(prefix + "Successful", Boolean.toString(successful()));
        values.put(prefix + "Equivalent", Boolean.toString(equivalent));
        values.put(prefix + "InputCases", Integer.toString(inputCaseCount));
        values.put(prefix + "ComparedOutputs", Integer.toString(comparedOutputCount()));
        values.put(prefix + "ComparedOutputNames", String.join(",", comparedOutputs));
        values.put(prefix + "Diagnostics", Integer.toString(diagnosticCount()));
        values.put(prefix + "HasDiagnostics", Boolean.toString(hasDiagnostics()));
        GpuIrRuntimeEquivalenceDiagnosticFamilies.putArtifactFields(values, prefix, diagnostics);
        if (hasDiagnostics()) {
            values.put(prefix + "FirstDiagnostic", firstDiagnostic());
            values.put(prefix + "AllDiagnostics", String.join(" | ", diagnostics));
            for (int index = 0; index < diagnostics.size(); index++) {
                values.put(prefix + "Diagnostic." + index, diagnostics.get(index));
            }
        }
        values.put(prefix + "Plans", Integer.toString(rewritePlanReport.plans().size()));
        values.put(prefix + "Insertions", Integer.toString(rewritePlanReport.insertionCount()));
        values.put(prefix + "Replacements", Integer.toString(rewritePlanReport.replacementEditCount()));
        values.put(prefix + "Skipped", Integer.toString(rewritePlanReport.skippedCandidateCount()));
        values.put(prefix + "SkippedDominanceStatusCounts", dominanceStatusCountsSummary());
        rewritePlanReport.skippedDominanceStatusCounts().entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().artifactValue()))
                .forEach(entry -> values.put(
                        prefix + "SkippedDominanceStatus." + entry.getKey().artifactValue(),
                        Long.toString(entry.getValue())
                ));
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("cseRuntimeEquivalence");
    }

    public String summary() {
        return "CSE runtime-equivalence successful=" + successful()
                + " equivalent=" + equivalent
                + " inputCases=" + inputCaseCount
                + " comparedOutputs=" + comparedOutputCount()
                + " diagnostics=" + diagnosticCount()
                + " plans=" + rewritePlanReport.plans().size()
                + " insertions=" + rewritePlanReport.insertionCount()
                + " replacements=" + rewritePlanReport.replacementEditCount()
                + " skipped=" + rewritePlanReport.skippedCandidateCount()
                + " skippedDominanceStatusCounts=" + dominanceStatusCountsSummary()
                + (hasDiagnostics() ? " firstDiagnostic=" + firstDiagnostic() : "");
    }

    private String dominanceStatusCountsSummary() {
        return rewritePlanReport.skippedDominanceStatusCounts().entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().artifactValue()))
                .map(entry -> entry.getKey().artifactValue() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
}
