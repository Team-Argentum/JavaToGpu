package net.sixik.ga_utils.javatogpu.frontend.ir.validation;

import java.util.Locale;

/**
 * Compiler-facing validation levels for optional IR validation providers.
 */
public enum GpuIrValidationMode {
    OFF,
    DIAGNOSTIC,
    STRICT_SAFETY,
    STRICT_OPTIMIZER;

    public static GpuIrValidationMode parse(String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }
        String normalized = value.trim()
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "OFF", "FALSE", "DISABLED" -> OFF;
            case "DIAGNOSTIC", "DIAGNOSTIC_ONLY", "TRUE", "ENABLED" -> DIAGNOSTIC;
            case "STRICT_SAFETY", "STRICTSAFETY", "STRICT_FAIL_ON_SAFETY_ERROR" -> STRICT_SAFETY;
            case "STRICT_OPTIMIZER", "STRICTOPTIMIZER", "STRICT_FAIL_ON_OPTIMIZER_DIAGNOSTICS" -> STRICT_OPTIMIZER;
            default -> throw new IllegalArgumentException(
                    "Unsupported JavaToGpu IR validation mode: "
                            + value
                            + "; expected off, diagnostic, strictSafety, or strictOptimizer"
            );
        };
    }
}
