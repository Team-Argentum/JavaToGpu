package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.Locale;
import java.util.Optional;

/**
 * Runtime integration mode for method-level {@code @GPUTest} probes.
 */
public enum GpuRuntimeMethodTestProbeMode {
    /** Do not use method-test probe evidence during runtime selection. */
    DISABLED("disabled"),

    /** Use only already-recorded probe evidence from cache; never execute probes during selection. */
    CACHE_ONLY("cache-only");

    private final String optionValue;

    GpuRuntimeMethodTestProbeMode(String optionValue) {
        this.optionValue = optionValue;
    }

    public String optionValue() {
        return optionValue;
    }

    public static Optional<GpuRuntimeMethodTestProbeMode> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(DISABLED);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "disabled", "off", "none" -> Optional.of(DISABLED);
            case "cache-only", "cached" -> Optional.of(CACHE_ONLY);
            default -> Optional.empty();
        };
    }
}
