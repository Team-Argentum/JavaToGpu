package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stable result emitted by one optimizer validation rule.
 */
public record GpuIrOptimizationValidationRuleResult(
        String ruleId,
        boolean passed,
        GpuIrOptimizationValidationRuleStatus status,
        String message,
        Map<String, String> metadata
) {
    public GpuIrOptimizationValidationRuleResult {
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("ruleId must not be blank");
        }
        status = Objects.requireNonNull(status, "status");
        if (passed != (status != GpuIrOptimizationValidationRuleStatus.FAIL)) {
            throw new IllegalArgumentException("passed must match non-fail rule status");
        }
        message = Objects.requireNonNull(message, "message");
        Objects.requireNonNull(metadata, "metadata");
        metadata.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("metadata keys must not be blank");
            }
            Objects.requireNonNull(value, "metadata values must not be null");
        });
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public GpuIrOptimizationValidationRuleResult(
            String ruleId,
            boolean passed,
            String message,
            Map<String, String> metadata
    ) {
        this(
                ruleId,
                passed,
                passed ? GpuIrOptimizationValidationRuleStatus.PASS : GpuIrOptimizationValidationRuleStatus.FAIL,
                message,
                metadata
        );
    }

    public static GpuIrOptimizationValidationRuleResult passed(String ruleId, String message) {
        return passed(ruleId, message, Map.of());
    }

    public static GpuIrOptimizationValidationRuleResult passed(
            String ruleId,
            String message,
            Map<String, String> metadata
    ) {
        return new GpuIrOptimizationValidationRuleResult(
                ruleId,
                true,
                GpuIrOptimizationValidationRuleStatus.PASS,
                message,
                metadata
        );
    }

    public static GpuIrOptimizationValidationRuleResult warned(String ruleId, String message) {
        return warned(ruleId, message, Map.of());
    }

    public static GpuIrOptimizationValidationRuleResult warned(
            String ruleId,
            String message,
            Map<String, String> metadata
    ) {
        return new GpuIrOptimizationValidationRuleResult(
                ruleId,
                true,
                GpuIrOptimizationValidationRuleStatus.WARN,
                message,
                metadata
        );
    }

    public static GpuIrOptimizationValidationRuleResult failed(String ruleId, String message) {
        return failed(ruleId, message, Map.of());
    }

    public static GpuIrOptimizationValidationRuleResult failed(
            String ruleId,
            String message,
            Map<String, String> metadata
    ) {
        return new GpuIrOptimizationValidationRuleResult(
                ruleId,
                false,
                GpuIrOptimizationValidationRuleStatus.FAIL,
                message,
                metadata
        );
    }

    public boolean blocking() {
        return status.blocking();
    }

    public Map<String, String> artifactFields(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(prefix + "RuleId", ruleId);
        values.put(prefix + "Passed", Boolean.toString(passed));
        values.put(prefix + "Status", status.artifactValue());
        values.put(prefix + "Blocking", Boolean.toString(blocking()));
        values.put(prefix + "Message", message);
        values.put(prefix + "MetadataCount", Integer.toString(metadata.size()));
        values.put(prefix + "MetadataPresent", Boolean.toString(!metadata.isEmpty()));
        metadata.forEach((key, value) -> values.put(prefix + "Metadata." + key, value));
        return Collections.unmodifiableMap(values);
    }
}
