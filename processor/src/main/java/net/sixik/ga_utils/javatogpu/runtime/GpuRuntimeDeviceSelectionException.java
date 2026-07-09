package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Stable runtime failure raised when device policy cannot satisfy a compile request.
 */
public final class GpuRuntimeDeviceSelectionException extends GpuRuntimeException {

    private final GpuRuntimeDeviceSelection selection;

    public GpuRuntimeDeviceSelectionException(String message, GpuRuntimeDeviceSelection selection) {
        this(message, selection, GpuRuntimeDiagnosticContext.fromSelection(selection));
    }

    public GpuRuntimeDeviceSelectionException(
            String message,
            GpuRuntimeDeviceSelection selection,
            GpuRuntimeDiagnosticContext context
    ) {
        super(
                "JTG-RUNTIME-DEVICE-001",
                GpuRuntimeFailurePhase.DEVICE_SELECTION,
                message == null || message.isBlank() ? "GPU device selection failed" : message,
                context,
                java.util.List.of(
                        "inspect runtime-device-selection.properties for ranked candidates and policy decisions",
                        "adjust GpuRuntimeDeviceOverride or @GPUDeviceConstraint when the request is intentionally narrow"
                ),
                null
        );
        this.selection = selection;
    }

    public GpuRuntimeDeviceSelection selection() {
        return selection;
    }
}
