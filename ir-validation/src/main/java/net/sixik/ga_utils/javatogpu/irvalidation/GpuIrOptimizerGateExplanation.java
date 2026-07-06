package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Stable read-only explanation for the first optimizer gate that blocks validation.
 */
public record GpuIrOptimizerGateExplanation(
        boolean blocked,
        String source,
        String family,
        String summary
) {
    public GpuIrOptimizerGateExplanation {
        source = requireNonBlank(source, "source");
        family = requireNonBlank(family, "family");
        summary = requireNonBlank(summary, "summary");
        if (!blocked && !("none".equals(source) && "none".equals(family))) {
            throw new IllegalArgumentException("unblocked gate explanations must use none source and family");
        }
    }

    public static GpuIrOptimizerGateExplanation from(GpuIrOptimizationValidationReport report) {
        Objects.requireNonNull(report, "report");
        if (report.hasSafetyError()) {
            String safetyError = report.safetyError().orElseThrow();
            return blocked("safety", safetyFamily(safetyError), safetyError);
        }
        Optional<String> autoVectorizationSummary = report.autoVectorizationPreview().firstBlockingDiagnosticSummary();
        if (autoVectorizationSummary.isPresent()) {
            return blocked(
                    "autoVectorization",
                    report.autoVectorizationPreview().firstBlockingDiagnosticFamily().orElse("autoVectorization.unknown"),
                    autoVectorizationSummary.orElseThrow()
            );
        }
        GpuIrCommonSubexpressionRewritePolicy csePolicy = report.commonSubexpressionArtifactSnapshot().rewritePolicy();
        if (csePolicy.hasBlockingSkippedCandidates()) {
            return blocked(
                    "cseRewritePolicy",
                    "cseRewritePolicy." + csePolicy.readiness().artifactValue(),
                    csePolicy.firstBlockingSkippedCandidate().orElseThrow().diagnostic().summary()
            );
        }
        return allowed();
    }

    public static GpuIrOptimizerGateExplanation allowed() {
        return new GpuIrOptimizerGateExplanation(false, "none", "none", "optimizer gate allows validation");
    }

    public static GpuIrOptimizerGateExplanation blocked(String source, String family, String summary) {
        return new GpuIrOptimizerGateExplanation(true, source, family, summary);
    }

    static String safetyFamily(String safetyError) {
        return GpuIrSafetyGateFamily.fromSafetyError(safetyError).artifactValue();
    }

    public Map<String, String> artifactFields(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(prefix + "Blocked", Boolean.toString(blocked));
        values.put(prefix + "Source", source);
        values.put(prefix + "Family", family);
        values.put(prefix + "Summary", summary);
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerGate");
    }

    public String compactSummary() {
        return "optimizer gate blocked=" + blocked
                + " source=" + source
                + " family=" + family
                + " summary=" + summary;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
