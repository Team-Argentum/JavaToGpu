package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only policy decision that explains whether a validation mode blocks on the optimizer gate.
 */
public record GpuIrOptimizerGatePolicyDecision(
        GpuIrOptimizationValidationMode mode,
        boolean blocked,
        String source,
        String family,
        String summary
) {
    public GpuIrOptimizerGatePolicyDecision {
        mode = Objects.requireNonNull(mode, "mode");
        source = requireNonBlank(source, "source");
        family = requireNonBlank(family, "family");
        summary = requireNonBlank(summary, "summary");
        if (!blocked && !("none".equals(source) && "none".equals(family))) {
            throw new IllegalArgumentException("unblocked optimizer gate policy decisions must use none source and family");
        }
    }

    public static GpuIrOptimizerGatePolicyDecision from(
            GpuIrOptimizationValidationMode mode,
            GpuIrOptimizationValidationReport report
    ) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(report, "report");
        GpuIrOptimizerGateSnapshot snapshot = report.optimizerGateSnapshot();
        GpuIrOptimizerGateExplanation explanation = snapshot.explanation();
        if (mode == GpuIrOptimizationValidationMode.DIAGNOSTIC_ONLY) {
            return allowed(mode, "diagnostic mode records optimizer gate state without failing validation");
        }
        if (mode == GpuIrOptimizationValidationMode.STRICT_FAIL_ON_SAFETY_ERROR) {
            if (report.hasSafetyError()) {
                return blocked(mode, explanation.source(), explanation.family(), explanation.summary());
            }
            return allowed(mode, "strict safety mode allows optimizer diagnostics but blocks safety errors");
        }
        if (report.hasBlockingDiagnostics()) {
            return blocked(mode, explanation.source(), explanation.family(), snapshot.compactSummary());
        }
        return allowed(mode, "strict optimizer mode found no blocking optimizer gate diagnostics");
    }

    public static GpuIrOptimizerGatePolicyDecision allowed(
            GpuIrOptimizationValidationMode mode,
            String summary
    ) {
        return new GpuIrOptimizerGatePolicyDecision(mode, false, "none", "none", summary);
    }

    public static GpuIrOptimizerGatePolicyDecision blocked(
            GpuIrOptimizationValidationMode mode,
            String source,
            String family,
            String summary
    ) {
        return new GpuIrOptimizerGatePolicyDecision(mode, true, source, family, summary);
    }

    public Map<String, String> artifactFields(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "Mode", mode.name());
        values.put(prefix + "Blocked", Boolean.toString(blocked));
        values.put(prefix + "Source", source);
        values.put(prefix + "Family", family);
        values.put(prefix + "Summary", summary);
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> artifactFields() {
        return artifactFields("optimizerGatePolicy");
    }

    public String compactSummary() {
        return "optimizer gate policy mode=" + mode
                + " blocked=" + blocked
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
