package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeDiagnosticRendererSupport;

import java.util.List;

/**
 * Base type for stable, catchable JavaToGpu runtime failures.
 */
public class GpuRuntimeException extends RuntimeException {

    private final String code;
    private final GpuRuntimeFailurePhase phase;
    private final String summary;
    private final GpuRuntimeDiagnosticContext context;
    private final List<String> helpMessages;
    private final String diagnosticText;

    public GpuRuntimeException(
            String code,
            GpuRuntimeFailurePhase phase,
            String summary,
            GpuRuntimeDiagnosticContext context,
            List<String> helpMessages,
            Throwable cause
    ) {
        this(
                normalize(code, "JTG-RUNTIME-UNKNOWN"),
                phase == null ? GpuRuntimeFailurePhase.RUNTIME_SETUP : phase,
                normalize(summary, "GPU runtime failure"),
                context == null ? GpuRuntimeDiagnosticContext.unknown() : context,
                helpMessages == null ? List.of() : List.copyOf(helpMessages),
                cause,
                true
        );
    }

    private GpuRuntimeException(
            String code,
            GpuRuntimeFailurePhase phase,
            String summary,
            GpuRuntimeDiagnosticContext context,
            List<String> helpMessages,
            Throwable cause,
            boolean ignored
    ) {
        super(GpuRuntimeDiagnosticRendererSupport.render(code, phase, summary, context, helpMessages), cause);
        this.code = code;
        this.phase = phase;
        this.summary = summary;
        this.context = context;
        this.helpMessages = helpMessages;
        this.diagnosticText = getMessage();
    }

    public String code() {
        return code;
    }

    public GpuRuntimeFailurePhase phase() {
        return phase;
    }

    public String summary() {
        return summary;
    }

    public GpuRuntimeDiagnosticContext context() {
        return context;
    }

    public List<String> helpMessages() {
        return helpMessages;
    }

    public String diagnosticText() {
        return diagnosticText;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
