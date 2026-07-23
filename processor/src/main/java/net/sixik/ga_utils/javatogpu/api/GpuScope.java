package net.sixik.ga_utils.javatogpu.api;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope;

import java.util.Objects;

/**
 * User-facing GPU runtime scope returned by {@link JavaToGpu}.
 *
 * <p>Use this type with try-with-resources around generated {@code @GPU} launcher calls. Closing the scope restores the
 * previously active runtime backend and releases any scope-owned runtime resources.</p>
 *
 * <pre>{@code
 * try (GpuScope ignored = JavaToGpu.useOpenClSharedCache()) {
 *     DemoKernel.transform(input, output);
 * }
 * }</pre>
 *
 * <p>This is a small public wrapper over the lower-level runtime scope. It exists so ordinary application code can stay
 * in the {@code net.sixik.ga_utils.javatogpu.api} namespace for the common path.</p>
 */
public final class GpuScope implements AutoCloseable {

    private final GpuRuntimeScope delegate;

    private GpuScope(GpuRuntimeScope delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    static GpuScope wrap(GpuRuntimeScope delegate) {
        return new GpuScope(delegate);
    }

    /**
     * Returns whether this scope has already been closed.
     */
    public boolean closed() {
        return delegate.closed();
    }

    /**
     * Returns whether closing this scope also closes the installed runtime backend instance.
     */
    public boolean ownsRuntime() {
        return delegate.ownsInstalledBackend();
    }

    /**
     * Restores the previous runtime backend and releases scope-owned resources.
     *
     * <p>This method is idempotent when called on the active innermost scope. Like the lower-level runtime scope, nested
     * scopes must be closed in last-in-first-out order.</p>
     */
    @Override
    public void close() {
        delegate.close();
    }
}
