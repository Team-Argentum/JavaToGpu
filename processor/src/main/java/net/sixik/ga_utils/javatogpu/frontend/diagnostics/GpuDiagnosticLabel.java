package net.sixik.ga_utils.javatogpu.frontend.diagnostics;

import java.util.Objects;

/**
 * Annotated source range shown under a diagnostic snippet.
 */
public record GpuDiagnosticLabel(
        GpuSourceSpan span,
        String message,
        boolean primary
) {
    public GpuDiagnosticLabel {
        span = Objects.requireNonNull(span, "span");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    public static GpuDiagnosticLabel primary(GpuSourceSpan span, String message) {
        return new GpuDiagnosticLabel(span, message, true);
    }

    public static GpuDiagnosticLabel secondary(GpuSourceSpan span, String message) {
        return new GpuDiagnosticLabel(span, message, false);
    }
}
