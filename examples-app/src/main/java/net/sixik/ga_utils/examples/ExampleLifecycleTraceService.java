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
 * classpath does not create files unless the application opts into this particular trace output. When runtime events
 * carry portable lifecycle summaries, the trace line prints a compact {@code summary=} segment that is safe to parse
 * across OpenCL now and future backends later.</p>
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
        String status = eventStatus(event);
        return event.kind().name()
                + " | backend=" + event.backendTarget().name()
                + " | kernel=" + event.kernelResource()
                + " | profile=" + event.optimizationProfile()
                + " | status=" + status
                + portableSummary(event)
                + " | message=" + event.message();
    }

    private static String portableSummary(GpuRuntimeLifecycleEvent event) {
        StringBuilder builder = new StringBuilder();
        appendBackendStateSummary(builder, event);
        appendCompilationSummary(builder, event);
        appendInvocationBindingSummary(builder, event);
        appendArtifactDumpSummary(builder, event);
        return builder.length() == 0 ? "" : " | summary=" + builder;
    }

    private static void appendBackendStateSummary(StringBuilder builder, GpuRuntimeLifecycleEvent event) {
        if (!"true".equals(event.fields().get("runtime.backend.state.present"))) {
            return;
        }
        appendSummaryPart(
                builder,
                "state cache=" + firstPresentField(event, "runtime.backend.cache.mode")
                        + " compiled=" + firstPresentField(event, "runtime.backend.cache.compiledKernel.count")
                        + " compile=" + firstPresentField(event, "runtime.backend.compile.count")
                        + " invoke=" + firstPresentField(event, "runtime.backend.invocation.count")
                        + " buffers=" + firstPresentField(event, "runtime.backend.buffer.native.count")
        );
    }

    private static void appendCompilationSummary(StringBuilder builder, GpuRuntimeLifecycleEvent event) {
        if (!"true".equals(event.fields().get("runtime.compilation.present"))) {
            return;
        }
        appendSummaryPart(
                builder,
                "compilation module=" + firstPresentField(event, "runtime.compilation.module.format")
                        + " cacheKey=" + firstPresentField(event, "runtime.compilation.cacheKey.present")
                        + " log=" + firstPresentField(event, "runtime.compilation.compileLog.present")
                        + " binaries=" + firstPresentField(event, "runtime.compilation.binaryArtifact.count")
        );
    }

    private static void appendInvocationBindingSummary(StringBuilder builder, GpuRuntimeLifecycleEvent event) {
        if (!"true".equals(event.fields().get("runtime.invocation.binding.present"))) {
            return;
        }
        appendSummaryPart(
                builder,
                "bindings args=" + firstPresentField(event, "runtime.invocation.binding.argument.count")
                        + " buffers=" + firstPresentField(event, "runtime.invocation.binding.buffer.count")
                        + " locals=" + firstPresentField(event, "runtime.invocation.binding.local.count")
                        + " scalars=" + firstPresentField(event, "runtime.invocation.binding.scalar.count")
        );
    }

    private static void appendArtifactDumpSummary(StringBuilder builder, GpuRuntimeLifecycleEvent event) {
        if (!"true".equals(event.fields().get("runtime.artifactDump.present"))) {
            return;
        }
        appendSummaryPart(
                builder,
                "dump dirs=" + firstPresentField(event, "runtime.artifactDump.directory.count")
                        + " artifacts=" + firstPresentField(event, "runtime.artifactDump.artifact.count")
                        + " binaries=" + firstPresentField(event, "runtime.artifactDump.binaryArtifact.count")
        );
    }

    private static void appendSummaryPart(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("; ");
        }
        builder.append(value);
    }

    private static String eventStatus(GpuRuntimeLifecycleEvent event) {
        String explicitStatus = firstPresentField(
                event,
                "runtime.status",
                "status",
                "selection.status",
                "warmup.status"
        );
        if (!explicitStatus.isBlank()) {
            return explicitStatus;
        }
        if (event.fields().containsKey("discovery.available")) {
            return Boolean.parseBoolean(event.fields().get("discovery.available")) ? "available" : "unavailable";
        }
        if (event.fields().containsKey("candidate.count")) {
            return "candidates=" + event.fields().get("candidate.count");
        }
        return "unknown";
    }

    private static String firstPresentField(GpuRuntimeLifecycleEvent event, String... keys) {
        for (String key : keys) {
            String value = event.fields().get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static Path parentOrCurrent(Path path) {
        Path parent = path.toAbsolutePath().normalize().getParent();
        return parent == null ? Path.of(".") : parent;
    }
}
