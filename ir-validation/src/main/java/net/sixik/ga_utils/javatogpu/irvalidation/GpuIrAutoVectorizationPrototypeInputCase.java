package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One explicit integer-array input case for prototype rewrite equivalence checks.
 */
public record GpuIrAutoVectorizationPrototypeInputCase(
        String name,
        Map<String, int[]> arrays
) {
    public GpuIrAutoVectorizationPrototypeInputCase {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(arrays, "arrays");
        if (arrays.isEmpty()) {
            throw new IllegalArgumentException("arrays must not be empty");
        }
        Map<String, int[]> copiedArrays = new LinkedHashMap<>();
        arrays.forEach((arrayName, values) -> {
            if (arrayName == null || arrayName.isBlank()) {
                throw new IllegalArgumentException("array names must not be blank");
            }
            if (values == null) {
                throw new IllegalArgumentException("array values must not be null");
            }
            copiedArrays.put(arrayName, values.clone());
        });
        arrays = java.util.Collections.unmodifiableMap(copiedArrays);
    }

    public int[] array(String arrayName) {
        int[] values = arrays.get(arrayName);
        return values == null ? null : values.clone();
    }

    @Override
    public Map<String, int[]> arrays() {
        Map<String, int[]> copiedArrays = new LinkedHashMap<>();
        arrays.forEach((arrayName, values) -> copiedArrays.put(arrayName, values.clone()));
        return java.util.Collections.unmodifiableMap(copiedArrays);
    }
}
