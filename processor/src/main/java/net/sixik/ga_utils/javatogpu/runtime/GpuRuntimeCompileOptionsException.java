package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

/**
 * Raised when runtime compile arguments are invalid for the selected backend.
 */
public final class GpuRuntimeCompileOptionsException extends GpuRuntimeException {

    public GpuRuntimeCompileOptionsException(
            String summary,
            GpuRuntimeDiagnosticContext context,
            Throwable cause
    ) {
        super(
                "JTG-RUNTIME-OPTIONS-001",
                GpuRuntimeFailurePhase.COMPILE_OPTIONS,
                summary,
                context,
                List.of(
                        "pass one backend option per compileArgs entry",
                        "remove unsupported or malformed backend flags before retrying"
                ),
                cause
        );
    }
}
