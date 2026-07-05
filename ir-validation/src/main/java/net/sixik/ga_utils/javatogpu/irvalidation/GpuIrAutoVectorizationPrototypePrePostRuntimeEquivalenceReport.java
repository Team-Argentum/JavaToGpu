package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Opt-in pre/post runtime-equivalence artifact for auto-vectorization prototype rewrites.
 *
 * <p>This report wraps the existing prototype artifact so CI can consume a stable pre/post
 * optimizer evidence surface without connecting prototype rewrites to normal validation.</p>
 */
public record GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceReport(
        GpuIrAutoVectorizationPrototypeArtifactReport artifactReport
) {
    public GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceReport {
        artifactReport = Objects.requireNonNull(artifactReport, "artifactReport");
    }

    public boolean successful() {
        return artifactReport.successful();
    }

    public int preOptimizationRewriteCandidates() {
        return artifactReport.appliedRewriteCount();
    }

    public int postOptimizationAppliedRewrites() {
        return artifactReport.appliedRewriteCount();
    }

    public int diagnosticCount() {
        return artifactReport.diagnosticCount();
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "PreOptimizationRewriteCandidates", Integer.toString(preOptimizationRewriteCandidates()));
        values.put(prefix + "PostOptimizationAppliedRewrites", Integer.toString(postOptimizationAppliedRewrites()));
        values.put(prefix + "AppliedRewriteFamilies", artifactReport.rewriteReport().appliedRewriteFamilyCountersSummary());
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
        return artifactFields("autoVectorizationPrototypePrePostRuntimeEquivalence");
    }

    public String summary() {
        return "auto-vectorization prototype pre/post runtime-equivalence successful=" + successful()
                + " preOptimizationRewriteCandidates=" + preOptimizationRewriteCandidates()
                + " postOptimizationAppliedRewrites=" + postOptimizationAppliedRewrites()
                + " runtimeEquivalenceSuccessful=" + artifactReport.runtimeEquivalenceReport().successful()
                + " diagnostics=" + diagnosticCount()
                + " appliedRewriteFamilies=" + artifactReport.rewriteReport().appliedRewriteFamilyCountersSummary()
                + " diagnosticFamilyCounts=" + GpuIrRuntimeEquivalenceDiagnosticFamilies.countsSummary(
                artifactReport.runtimeEquivalenceReport().diagnostics()
        );
    }
}
