package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Scoped runtime backend installation handle returned by {@link GpuRuntime} helper methods.
 *
 * <p>A scope remembers the previously active backend, installs a new one, and restores the previous backend when the
 * scope is closed. Depending on how the scope was created, it may also close the installed backend instance.
 *
 * <p>This makes runtime configuration safe for nested try-with-resources usage:
 *
 * <pre>{@code
 * try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
 *     DemoKernel.invoke(input, output);
 * }
 * }</pre>
 */
public final class GpuRuntimeScope implements AutoCloseable {

    private final GpuRuntimeBackend previousBackend;
    private final GpuRuntimeBackend installedBackend;
    private final boolean closeInstalledBackend;
    private boolean closed;

    GpuRuntimeScope(
            GpuRuntimeBackend previousBackend,
            GpuRuntimeBackend installedBackend,
            boolean closeInstalledBackend
    ) {
        this.previousBackend = previousBackend;
        this.installedBackend = installedBackend;
        this.closeInstalledBackend = closeInstalledBackend;
    }

    /**
     * Returns the backend installed for this scope.
     */
    public GpuRuntimeBackend installedBackend() {
        return installedBackend;
    }

    /**
     * Returns the backend that was active before this scope installed its backend.
     */
    public GpuRuntimeBackend previousBackend() {
        return previousBackend;
    }

    /**
     * Returns whether closing this scope will also close the installed backend instance.
     */
    public boolean ownsInstalledBackend() {
        return closeInstalledBackend;
    }

    /**
     * Returns whether this scope has already been closed successfully.
     */
    public boolean closed() {
        return closed;
    }

    /**
     * Restores the previous backend and optionally closes the installed backend.
     *
     * <p>This method is idempotent.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }

        GpuRuntimeBackend currentBackend = GpuRuntime.backend();
        if (currentBackend != installedBackend) {
            throw new IllegalStateException(
                    "GpuRuntimeScope cannot close out of order: the active backend changed before this scope was closed"
            );
        }

        closed = true;

        Throwable failure = null;
        GpuRuntime.setBackend(previousBackend);

        if (closeInstalledBackend && installedBackend instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Throwable throwable) {
                failure = throwable;
            }
        }

        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure != null) {
            throw new RuntimeException("Failed to close scoped GPU runtime backend", failure);
        }
    }
}
