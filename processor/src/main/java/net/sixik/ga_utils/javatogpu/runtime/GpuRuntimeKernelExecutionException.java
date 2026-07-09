package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

/**
 * Raised when a compiled kernel fails during argument binding, enqueue, synchronization, or readback.
 */
public final class GpuRuntimeKernelExecutionException extends GpuRuntimeException {

    public GpuRuntimeKernelExecutionException(
            String summary,
            GpuRuntimeDiagnosticContext context,
            Throwable cause
    ) {
        super(
                "JTG-RUNTIME-EXECUTE-001",
                GpuRuntimeFailurePhase.KERNEL_EXECUTION,
                summary,
                context,
                List.of(
                        "re-run device capability validation for this kernel",
                        "reduce work-group size or resource pressure when the driver reports out-of-resources",
                        "switch to a fallback method, device, or backend when the failure is device-specific"
                ),
                cause
        );
    }
}
