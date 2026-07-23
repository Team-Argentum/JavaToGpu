package net.sixik.ga_utils.javatogpu.runtime;

import java.io.PrintStream;

/**
 * Compatibility facade for the built-in system stream log sink.
 *
 * @deprecated use {@link net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeSystemStreamLogService}.
 */
@Deprecated
public final class GpuRuntimeSystemStreamLogService implements GpuRuntimeLogService {

    private final net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeSystemStreamLogService delegate;

    public GpuRuntimeSystemStreamLogService(PrintStream output, String extensionId) {
        this.delegate = new net.sixik.ga_utils.javatogpu.runtime.observability.GpuRuntimeSystemStreamLogService(
                output,
                extensionId
        );
    }

    public static GpuRuntimeSystemStreamLogService systemOut() {
        return new GpuRuntimeSystemStreamLogService(System.out, "runtime.logging.system-out");
    }

    public static GpuRuntimeSystemStreamLogService systemErr() {
        return new GpuRuntimeSystemStreamLogService(System.err, "runtime.logging.system-err");
    }

    @Override
    public void log(GpuRuntimeLogRecord record) {
        delegate.log(record);
    }

    @Override
    public String extensionId() {
        return delegate.extensionId();
    }

    @Override
    public String extensionVersion() {
        return delegate.extensionVersion();
    }

    @Override
    public int extensionOrder() {
        return delegate.extensionOrder();
    }
}
