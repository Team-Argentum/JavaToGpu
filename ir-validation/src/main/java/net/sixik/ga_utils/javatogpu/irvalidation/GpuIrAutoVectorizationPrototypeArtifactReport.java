package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Combined export artifact for explicit prototype auto-vectorization integration runs.
 *
 * <p>This type only packages reports produced by opt-in callers. It does not execute rewrites,
 * run equivalence checks, or connect the prototype rewrite path to normal compiler validation.</p>
 */
public record GpuIrAutoVectorizationPrototypeArtifactReport(
        GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport runtimeEquivalenceReport
) {
    public GpuIrAutoVectorizationPrototypeArtifactReport {
        runtimeEquivalenceReport = Objects.requireNonNull(runtimeEquivalenceReport, "runtimeEquivalenceReport");
    }

    public GpuIrAutoVectorizationPrototypeRewriteReport rewriteReport() {
        return runtimeEquivalenceReport.rewriteReport();
    }

    public boolean successful() {
        return runtimeEquivalenceReport.successful();
    }

    public boolean hasAppliedRewrites() {
        return rewriteReport().hasAppliedRewrites();
    }

    public int appliedRewriteCount() {
        return rewriteReport().appliedRewriteCount();
    }

    public int diagnosticCount() {
        return runtimeEquivalenceReport.diagnosticCount();
    }

    public GpuIrAutoVectorizationPrototypeArtifactSummary artifactSummary() {
        return new GpuIrAutoVectorizationPrototypeArtifactSummary(
                rewriteReport().method().name(),
                successful(),
                appliedRewriteCount(),
                runtimeEquivalenceReport.successful(),
                runtimeEquivalenceReport.inputCaseCount(),
                runtimeEquivalenceReport.comparedOutputCount(),
                runtimeEquivalenceReport.diagnosticCount(),
                runtimeEquivalenceReport.firstDiagnostic(),
                rewriteReport().appliedRewriteFamilyCountersSummary()
        );
    }

    /**
     * Exposes one stable field map for explicit prototype artifact writers.
     */
    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(prefix + "Successful", Boolean.toString(successful()));
        values.put(prefix + "Method", rewriteReport().method().name());
        values.put(prefix + "AppliedRewrites", Integer.toString(appliedRewriteCount()));
        values.put(prefix + "HasAppliedRewrites", Boolean.toString(hasAppliedRewrites()));
        values.put(prefix + "RuntimeEquivalenceSuccessful", Boolean.toString(runtimeEquivalenceReport.successful()));
        values.put(prefix + "RuntimeEquivalenceDiagnostics", Integer.toString(runtimeEquivalenceReport.diagnosticCount()));
        GpuIrRuntimeEquivalenceDiagnosticFamilies.putArtifactFields(
                values,
                prefix + "RuntimeEquivalence",
                runtimeEquivalenceReport.diagnostics()
        );
        values.put(prefix + "Summary", artifactSummary().summaryLine());
        values.putAll(rewriteReport().artifactFields(prefix + "Rewrite."));
        values.putAll(runtimeEquivalenceReport.artifactFields(prefix + "RuntimeEquivalence."));
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("autoVectorizationPrototypeArtifact");
    }

    public String summary() {
        return artifactSummary().summaryLine()
                + " rewrite={" + rewriteReport().summary() + "}"
                + " runtimeEquivalence={" + runtimeEquivalenceReport.summary() + "}";
    }
}
