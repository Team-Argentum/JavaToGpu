package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Objects;

/**
 * Selected backend together with the capability report and ownership semantics that made it eligible.
 */
public record GpuRuntimeBackendSelection(
        GpuRuntimeBackend backend,
        GpuRuntimeBackendReport report,
        GpuRuntimeBackendOwnership ownership
) {

    public GpuRuntimeBackendSelection {
        backend = Objects.requireNonNull(backend, "backend");
        report = Objects.requireNonNull(report, "report");
        ownership = Objects.requireNonNull(ownership, "ownership");
    }

    public GpuRuntimeBackendSelection(GpuRuntimeBackend backend, GpuRuntimeBackendReport report) {
        this(backend, report, GpuRuntimeBackendOwnership.BORROWED);
    }

    /**
     * Installs the selected backend using the ownership semantics that came with the selection.
     */
    public GpuRuntimeScope install() {
        return ownership == GpuRuntimeBackendOwnership.OWNED
                ? GpuRuntime.useOwnedBackend(backend)
                : GpuRuntime.useBackend(backend);
    }

    /**
     * Returns whether the selected backend should be auto-closed when installed through {@link #install()}.
     */
    public boolean ownsBackend() {
        return ownership == GpuRuntimeBackendOwnership.OWNED;
    }
}
