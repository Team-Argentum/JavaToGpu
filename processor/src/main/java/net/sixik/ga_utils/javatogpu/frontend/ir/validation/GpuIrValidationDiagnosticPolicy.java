package net.sixik.ga_utils.javatogpu.frontend.ir.validation;

import java.util.Locale;

/**
 * Controls how much read-only validation context is emitted as compiler diagnostics.
 */
public enum GpuIrValidationDiagnosticPolicy {
    QUIET,
    SUMMARY,
    DETAILED;

    public static GpuIrValidationDiagnosticPolicy parse(String value) {
        if (value == null || value.isBlank()) {
            return SUMMARY;
        }
        String normalized = value.trim()
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "QUIET", "OFF", "NONE" -> QUIET;
            case "SUMMARY", "COMPACT", "SHORT" -> SUMMARY;
            case "DETAILED", "DETAIL", "FULL", "VERBOSE" -> DETAILED;
            default -> throw new IllegalArgumentException(
                    "Unsupported JavaToGpu IR validation diagnostic policy: "
                            + value
                            + "; expected quiet, summary, or detailed"
            );
        };
    }
}
