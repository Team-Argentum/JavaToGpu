package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Opt-in pre/post optimizer equivalence artifact for CSE rewrite validation.
 *
 * <p>This report packages the original read-only planning snapshot with the existing explicit
 * runtime-equivalence evidence. It does not connect CSE mutation to normal validation.</p>
 */
public record GpuIrCommonSubexpressionPrePostRuntimeEquivalenceReport(
        GpuIrCommonSubexpressionArtifactReport artifactReport
) {
    public GpuIrCommonSubexpressionPrePostRuntimeEquivalenceReport {
        artifactReport = Objects.requireNonNull(artifactReport, "artifactReport");
    }

    public boolean successful() {
        return artifactReport.successful();
    }

    public int preOptimizationInsertionCandidates() {
        return artifactReport.snapshot().insertionCount();
    }

    public int postOptimizationReplacementChecks() {
        return artifactReport.runtimeEquivalenceReport().rewritePlanReport().replacementEditCount();
    }

    public int diagnosticCount() {
        return artifactReport.diagnosticCount();
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "PreOptimizationInsertionCandidates", Integer.toString(preOptimizationInsertionCandidates()));
        values.put(prefix + "PostOptimizationReplacementChecks", Integer.toString(postOptimizationReplacementChecks()));
        GpuIrPrePostRuntimeEquivalenceArtifactFields.putCommonFields(
                values,
                prefix,
                successful(),
                artifactReport.runtimeEquivalenceReport().successful(),
                diagnosticCount(),
                artifactReport.runtimeEquivalenceReport().diagnostics(),
                summary(),
                artifactReport.artifactFields(prefix + "Artifact.")
        );
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("csePrePostRuntimeEquivalence");
    }

    public String summary() {
        return "CSE pre/post runtime-equivalence successful=" + successful()
                + " preOptimizationInsertionCandidates=" + preOptimizationInsertionCandidates()
                + " postOptimizationReplacementChecks=" + postOptimizationReplacementChecks()
                + " runtimeEquivalenceSuccessful=" + artifactReport.runtimeEquivalenceReport().successful()
                + " diagnostics=" + diagnosticCount()
                + " diagnosticFamilyCounts=" + GpuIrRuntimeEquivalenceDiagnosticFamilies.countsSummary(
                artifactReport.runtimeEquivalenceReport().diagnostics()
        );
    }
}
