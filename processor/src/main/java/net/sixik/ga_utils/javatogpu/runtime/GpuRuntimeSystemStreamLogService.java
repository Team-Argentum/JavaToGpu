package net.sixik.ga_utils.javatogpu.runtime;

import java.io.PrintStream;
import java.util.Map;
import java.util.Objects;

/**
 * Small built-in logging sink for examples and local diagnostics.
 */
public final class GpuRuntimeSystemStreamLogService implements GpuRuntimeLogService {

    private final PrintStream output;
    private final String extensionId;

    public GpuRuntimeSystemStreamLogService(PrintStream output, String extensionId) {
        this.output = Objects.requireNonNull(output, "output");
        this.extensionId = extensionId == null || extensionId.isBlank()
                ? "runtime.logging.system-stream"
                : extensionId.trim();
    }

    public static GpuRuntimeSystemStreamLogService systemOut() {
        return new GpuRuntimeSystemStreamLogService(System.out, "runtime.logging.system-out");
    }

    public static GpuRuntimeSystemStreamLogService systemErr() {
        return new GpuRuntimeSystemStreamLogService(System.err, "runtime.logging.system-err");
    }

    @Override
    public void log(GpuRuntimeLogRecord record) {
        GpuRuntimeLogRecord value = Objects.requireNonNull(record, "record");
        synchronized (output) {
            output.println(format(value));
            if (value.throwable() != null) {
                value.throwable().printStackTrace(output);
            }
        }
    }

    @Override
    public String extensionId() {
        return extensionId;
    }

    @Override
    public String extensionVersion() {
        return "1";
    }

    @Override
    public int extensionOrder() {
        return 100_000;
    }

    private static String format(GpuRuntimeLogRecord record) {
        StringBuilder builder = new StringBuilder();
        builder.append("[JavaToGpu]")
                .append('[')
                .append(record.level().name())
                .append(']')
                .append('[')
                .append(record.loggerName())
                .append("] ")
                .append(record.message());
        for (Map.Entry<String, String> entry : record.fields().entrySet()) {
            builder.append(' ')
                    .append(entry.getKey())
                    .append('=')
                    .append(entry.getValue());
        }
        return builder.toString();
    }
}
