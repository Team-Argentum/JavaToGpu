package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit artifact for opt-in prototype rewrite equivalence checks.
 *
 * <p>The report intentionally stores the result of an external test/integration equivalence
 * runner instead of executing IR itself. This keeps runtime-equivalence evidence close to the
 * prototype rewrite metadata without making the production validation path mutating.</p>
 */
public record GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport(
        GpuIrAutoVectorizationPrototypeRewriteReport rewriteReport,
        boolean equivalent,
        int inputCaseCount,
        List<String> comparedOutputs,
        List<String> diagnostics
) {
    public GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport {
        rewriteReport = Objects.requireNonNull(rewriteReport, "rewriteReport");
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

    public static GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport equivalent(
            GpuIrAutoVectorizationPrototypeRewriteReport rewriteReport,
            int inputCaseCount,
            List<String> comparedOutputs
    ) {
        return new GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport(
                rewriteReport,
                true,
                inputCaseCount,
                comparedOutputs,
                List.of()
        );
    }

    public static GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport failed(
            GpuIrAutoVectorizationPrototypeRewriteReport rewriteReport,
            int inputCaseCount,
            List<String> comparedOutputs,
            List<String> diagnostics
    ) {
        return new GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport(
                rewriteReport,
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
     * Exposes stable string fields for explicit prototype/runtime-equivalence artifact writers.
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
        if (hasDiagnostics()) {
            values.put(prefix + "FirstDiagnostic", firstDiagnostic());
        }
        values.put(prefix + "AppliedRewrites", Integer.toString(rewriteReport.appliedRewriteCount()));
        values.put(prefix + "AppliedRewriteFamilies", rewriteReport.appliedRewriteFamilyCountersSummary());
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorizationPrototypeRuntimeEquivalence");
    }

    public String summary() {
        return "auto-vectorization prototype runtime-equivalence method=" + rewriteReport.method().name()
                + " successful=" + successful()
                + " equivalent=" + equivalent
                + " inputCases=" + inputCaseCount
                + " comparedOutputs=" + comparedOutputCount()
                + " diagnostics=" + diagnosticCount()
                + " appliedRewrites=" + rewriteReport.appliedRewriteCount()
                + " appliedRewriteFamilies=" + rewriteReport.appliedRewriteFamilyCountersSummary()
                + (hasDiagnostics() ? " firstDiagnostic=" + firstDiagnostic() : "");
    }
}
