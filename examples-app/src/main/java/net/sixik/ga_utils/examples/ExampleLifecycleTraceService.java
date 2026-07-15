package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEvent;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Example runtime lifecycle hook discovered through ServiceLoader.
 *
 * <p>The service is intentionally a no-op until {@value #TRACE_FILE_PROPERTY} is set, so adding the example module to a
 * classpath does not create files unless the application opts into this particular trace output.</p>
 */
public final class ExampleLifecycleTraceService implements GpuRuntimeLifecycleService {

    public static final String TRACE_FILE_PROPERTY = "javatogpu.examples.lifecycleTraceFile";

    private static final Object WRITE_LOCK = new Object();

    @Override
    public void onRuntimeLifecycleEvent(GpuRuntimeLifecycleEvent event) {
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
                        toTraceLine(event) + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND
                );
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to write example lifecycle trace: " + traceFile, exception);
            }
        }
    }

    @Override
    public String extensionId() {
        return "examples.lifecycle.trace-service";
    }

    @Override
    public String extensionVersion() {
        return "1";
    }

    @Override
    public int extensionOrder() {
        return 20_000;
    }

    static String toTraceLine(GpuRuntimeLifecycleEvent event) {
        String status = event.fields().getOrDefault("status", "unknown");
        return event.kind().name()
                + " | backend=" + event.backendTarget().name()
                + " | kernel=" + event.kernelResource()
                + " | profile=" + event.optimizationProfile()
                + " | status=" + status
                + " | message=" + event.message();
    }

    private static Path parentOrCurrent(Path path) {
        Path parent = path.toAbsolutePath().normalize().getParent();
        return parent == null ? Path.of(".") : parent;
    }
}
