package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

/**
 * Raised when Java launch arguments or execution configuration cannot be converted into a backend invocation.
 */
public final class GpuRuntimeInvocationException extends GpuRuntimeException {

    public GpuRuntimeInvocationException(
            String summary,
            GpuRuntimeDiagnosticContext context,
            Throwable cause
    ) {
        super(
                "JTG-RUNTIME-INVOKE-001",
                GpuRuntimeFailurePhase.ARGUMENT_MARSHALLING,
                summary,
                context,
                List.of(
                        "compare Java arguments with the generated kernel descriptor ABI",
                        "verify explicit global/local work sizes against the selected dimensionality"
                ),
                cause
        );
    }
}
