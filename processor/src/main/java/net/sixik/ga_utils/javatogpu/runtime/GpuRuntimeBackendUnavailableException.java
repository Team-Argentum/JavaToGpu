package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

/**
 * Raised when a runtime backend cannot initialize its native device/session state.
 */
public final class GpuRuntimeBackendUnavailableException extends GpuRuntimeException {

    public GpuRuntimeBackendUnavailableException(
            String summary,
            GpuRuntimeDiagnosticContext context,
            Throwable cause
    ) {
        super(
                "JTG-RUNTIME-BACKEND-001",
                GpuRuntimeFailurePhase.BACKEND_INITIALIZATION,
                summary,
                context,
                List.of(
                        "configure a fallback backend when GPU execution is optional",
                        "use GpuRuntime.trySelect(...) before opening a runtime scope"
                ),
                cause
        );
    }
}
