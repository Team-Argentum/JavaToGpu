package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLogRecord;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLogService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * Example runtime log hook discovered through ServiceLoader.
 */
public final class ExampleRuntimeLogTraceService implements GpuRuntimeLogService {

    public static final String TRACE_FILE_PROPERTY = "javatogpu.examples.runtimeLogTraceFile";

    private static final Object WRITE_LOCK = new Object();

    @Override
    public void log(GpuRuntimeLogRecord record) {
        String configuredFile = System.getProperty(TRACE_FILE_PROPERTY);
        if (configuredFile == null || configuredFile.isBlank()) {
            return;
        }
        Path traceFile = Path.of(configuredFile.trim());
        synchronized (WRITE_LOCK) {
            try {
                Files.createDirectories(parentOrCurrent(traceFile));
                Files.writeString(
                        traceFile,
                        toTraceLine(record) + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND
                );
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to write example runtime log trace: " + traceFile, exception);
            }
        }
    }

    @Override
    public String extensionId() {
        return "examples.runtime-log.trace-service";
    }

    @Override
    public String extensionVersion() {
        return "1";
    }

    @Override
    public int extensionOrder() {
        return 20_100;
    }

    static String toTraceLine(GpuRuntimeLogRecord record) {
        StringBuilder builder = new StringBuilder();
        builder.append(record.level().name())
                .append(" | logger=").append(record.loggerName())
                .append(" | message=").append(record.message());
        if (!record.fields().isEmpty()) {
            builder.append(" | fields=");
            boolean first = true;
            for (Map.Entry<String, String> entry : record.fields().entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
        }
        if (record.throwable() != null) {
            builder.append(" | throwable=")
                    .append(record.throwable().getClass().getName())
                    .append(':')
                    .append(record.throwable().getMessage());
        }
        return builder.toString();
    }

    private static Path parentOrCurrent(Path path) {
        Path parent = path.toAbsolutePath().normalize().getParent();
        return parent == null ? Path.of(".") : parent;
    }
}
