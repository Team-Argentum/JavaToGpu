package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

/**
 * Signals that no implementation in a declared fallback group can execute on the available devices.
 */
public final class GpuRuntimeMethodVariantSelectionException extends GpuRuntimeException {

    private final List<GpuRuntimeMethodVariantEvaluation> evaluations;

    public GpuRuntimeMethodVariantSelectionException(
            String message,
            List<GpuRuntimeMethodVariantEvaluation> evaluations
    ) {
        this(message, evaluations, GpuRuntimeDiagnosticContext.unknown());
    }

    public GpuRuntimeMethodVariantSelectionException(
            String message,
            List<GpuRuntimeMethodVariantEvaluation> evaluations,
            GpuRuntimeDiagnosticContext context
    ) {
        super(
                "JTG-RUNTIME-VARIANT-001",
                GpuRuntimeFailurePhase.METHOD_VARIANT_SELECTION,
                message,
                context,
                List.of(
                        "inspect rejected variant diagnostics and device constraints",
                        "provide an ABI-compatible fallback for the active device or open a new runtime scope"
                ),
                null
        );
        this.evaluations = evaluations == null ? List.of() : List.copyOf(evaluations);
    }

    public List<GpuRuntimeMethodVariantEvaluation> evaluations() {
        return evaluations;
    }
}
