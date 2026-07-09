package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

/**
 * Raised when the selected device cannot satisfy a kernel capability requirement.
 */
public final class GpuRuntimeCapabilityException extends GpuRuntimeException {

    public GpuRuntimeCapabilityException(
            String summary,
            GpuRuntimeDiagnosticContext context,
            Throwable cause
    ) {
        super(
                "JTG-RUNTIME-CAPABILITY-001",
                GpuRuntimeFailurePhase.CAPABILITY_VALIDATION,
                summary,
                context,
                List.of(
                        "declare the requirement through @GPUDeviceConstraint so selection can reject earlier",
                        "provide a compatible fallback variant or choose another device/backend"
                ),
                cause
        );
    }
}
