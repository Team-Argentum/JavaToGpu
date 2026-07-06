package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Serializable snapshot of the first optimizer blocker for CI baseline storage.
 *
 * <p>This artifact stores only read-only blocker metadata. It is meant to help CI compare whether
 * the first actionable optimizer blocker moved between runs without enabling any rewrite path.</p>
 */
public record GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot(
        String methodName,
        String verdict,
        String source,
        String family,
        String remainingWork
) {
    public static final String DEFAULT_PREFIX = "optimizerBlockerBaselineSnapshot";

    public GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot {
        methodName = requireNonBlank(methodName, "methodName");
        verdict = requireNonBlank(verdict, "verdict");
        source = requireNonBlank(source, "source");
        family = requireNonBlank(family, "family");
        remainingWork = requireNonBlank(remainingWork, "remainingWork");
        if (!"ready".equals(verdict) && !"blocked".equals(verdict)) {
            throw new IllegalArgumentException("verdict must be ready or blocked: " + verdict);
        }
    }

    public static GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot from(
            GpuIrOptimizationValidationOptimizerBlockerIndex blocker
    ) {
        Objects.requireNonNull(blocker, "blocker");
        return new GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot(
                blocker.methodName(),
                blocker.verdict(),
                blocker.source(),
                blocker.family(),
                blocker.remainingWork()
        );
    }

    public static GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot from(
            GpuIrOptimizationValidationReport report
    ) {
        Objects.requireNonNull(report, "report");
        return from(report.optimizerBlockerIndex());
    }

    public static GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot fromArtifactFields(
            Map<String, String> fields
    ) {
        return fromArtifactFields(fields, DEFAULT_PREFIX);
    }

    public static GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot fromArtifactFields(
            Map<String, String> fields,
            String prefix
    ) {
        Objects.requireNonNull(fields, "fields");
        String fieldPrefix = requireNonBlank(prefix, "prefix");
        return new GpuIrOptimizationValidationOptimizerBlockerBaselineSnapshot(
                requiredField(fields, fieldPrefix + "Method"),
                requiredField(fields, fieldPrefix + "Verdict"),
                requiredField(fields, fieldPrefix + "Source"),
                requiredField(fields, fieldPrefix + "Family"),
                requiredField(fields, fieldPrefix + "RemainingWork")
        );
    }

    public boolean blocked() {
        return "blocked".equals(verdict);
    }

    public Map<String, String> artifactFields() {
        return artifactFields(DEFAULT_PREFIX);
    }

    public Map<String, String> artifactFields(String prefix) {
        String fieldPrefix = requireNonBlank(prefix, "prefix");
        Map<String, String> values = new LinkedHashMap<>();
        values.put(fieldPrefix + "Method", methodName);
        values.put(fieldPrefix + "Verdict", verdict);
        values.put(fieldPrefix + "Blocked", Boolean.toString(blocked()));
        values.put(fieldPrefix + "Source", source);
        values.put(fieldPrefix + "Family", family);
        values.put(fieldPrefix + "RemainingWork", remainingWork);
        values.put(fieldPrefix + "CiSummaryLine", ciSummaryLine());
        values.put(fieldPrefix + "Summary", summary());
        return Collections.unmodifiableMap(values);
    }

    public String ciSummaryLine() {
        return "optimizer blocker baseline snapshot method=" + methodName
                + " verdict=" + verdict
                + " source=" + source
                + " family=" + family
                + " remainingWork=" + remainingWork;
    }

    public String summary() {
        return ciSummaryLine();
    }

    private static String requiredField(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing optimizer blocker baseline field: " + key);
        }
        return value;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
