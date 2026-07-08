package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Map;
import java.util.Objects;

/**
 * Shared helpers for composing nested, properties-friendly artifact maps.
 */
final class GpuIrOptimizationValidationArtifactFieldMaps {
    private GpuIrOptimizationValidationArtifactFieldMaps() {
    }

    static void putNestedFields(Map<String, String> target, String nestedPrefix, Map<String, String> source) {
        Objects.requireNonNull(target, "target");
        if (nestedPrefix == null || nestedPrefix.isBlank()) {
            throw new IllegalArgumentException("nestedPrefix must not be blank");
        }
        Objects.requireNonNull(source, "source")
                .forEach((key, value) -> target.put(nestedPrefix + key, value));
    }
}
