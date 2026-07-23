package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeDiagnosticRendererSupport;

import java.util.List;

/**
 * Renders catchable runtime failures with a compact Rust-like source anchor and execution context.
 */
public final class GpuRuntimeDiagnosticRenderer {

    private GpuRuntimeDiagnosticRenderer() {
    }

    public static String render(
            String code,
            GpuRuntimeFailurePhase phase,
            String message,
            GpuRuntimeDiagnosticContext context,
            List<String> helpMessages
    ) {
        return GpuRuntimeDiagnosticRendererSupport.render(code, phase, message, context, helpMessages);
    }
}
