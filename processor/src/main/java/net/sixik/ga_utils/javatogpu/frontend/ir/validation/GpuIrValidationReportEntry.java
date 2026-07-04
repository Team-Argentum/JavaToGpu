package net.sixik.ga_utils.javatogpu.frontend.ir.validation;

import java.util.Map;
import java.util.Objects;

/**
 * Machine-readable validation report entry emitted by optional validation providers.
 */
public record GpuIrValidationReportEntry(
        String provider,
        String methodName,
        boolean entryPoint,
        Map<String, String> values
) {
    public GpuIrValidationReportEntry {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        values = Map.copyOf(Objects.requireNonNull(values, "values"));
    }
}
