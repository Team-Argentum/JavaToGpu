package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

/**
 * Raised when the selected backend cannot compile or load a generated kernel.
 */
public final class GpuRuntimeKernelCompilationException extends GpuRuntimeException {

    public GpuRuntimeKernelCompilationException(
            String summary,
            GpuRuntimeDiagnosticContext context,
            Throwable cause
    ) {
        super(
                "JTG-RUNTIME-COMPILE-001",
                GpuRuntimeFailurePhase.KERNEL_COMPILATION,
                summary,
                context,
                List.of(
                        "check the generated kernel source and compile arguments",
                        "enable ABI debug for layout-sensitive failures",
                        "compare repeated driver-specific failures against docs/Device-Quirks.md"
                ),
                cause
        );
    }
}
