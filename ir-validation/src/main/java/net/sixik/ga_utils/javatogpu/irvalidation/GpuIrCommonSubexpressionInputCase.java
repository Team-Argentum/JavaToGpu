package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One explicit integer input case for opt-in CSE rewrite equivalence checks.
 */
public record GpuIrCommonSubexpressionInputCase(
        String name,
        Map<String, Integer> values
) {
    public GpuIrCommonSubexpressionInputCase {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        Map<String, Integer> copiedValues = new LinkedHashMap<>();
        values.forEach((valueName, value) -> {
            if (valueName == null || valueName.isBlank()) {
                throw new IllegalArgumentException("value names must not be blank");
            }
            if (value == null) {
                throw new IllegalArgumentException("values must not contain null entries");
            }
            copiedValues.put(valueName, value);
        });
        values = java.util.Collections.unmodifiableMap(copiedValues);
    }
}
