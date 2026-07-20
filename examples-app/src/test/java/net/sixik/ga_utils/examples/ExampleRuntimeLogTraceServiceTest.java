package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLogBus;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLogLevel;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLogRecord;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleRuntimeLogTraceServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void serviceIsNoOpWhenTraceFileIsNotConfigured() throws Exception {
        Path traceFile = temporaryDirectory.resolve("example-runtime-log.trace");

        withTraceFileProperty(null, () -> new ExampleRuntimeLogTraceService().log(sampleRecord()));

        assertFalse(Files.exists(traceFile));
    }

    @Test
    void writesHumanReadableRuntimeLogTraceWhenConfigured() throws Exception {
        Path traceFile = temporaryDirectory.resolve("example-runtime-log.trace");

        withTraceFileProperty(traceFile, () -> new ExampleRuntimeLogTraceService().log(sampleRecord()));

        String trace = Files.readString(traceFile, StandardCharsets.UTF_8);
        assertTrue(trace.contains("WARN"), trace);
        assertTrue(trace.contains("logger=net.sixik.example"), trace);
        assertTrue(trace.contains("message=example log"), trace);
        assertTrue(trace.contains("runtime.backend.target=OPENCL"), trace);
    }

    @Test
    void serviceLoaderBusCanWriteThroughExampleRuntimeLogTraceService() throws Exception {
        Path traceFile = temporaryDirectory.resolve("service-loader-runtime-log.trace");

        withTraceFileProperty(traceFile, () -> GpuRuntimeLogBus.loadFromServiceLoader().publish(sampleRecord()));

        String trace = Files.readString(traceFile, StandardCharsets.UTF_8);
        assertTrue(trace.contains("WARN"), trace);
        assertTrue(trace.contains("example log"), trace);
    }

    @Test
    void serviceDescriptorRegistersExampleRuntimeLogTraceService() throws Exception {
        String servicePath = "META-INF/services/" + GpuRuntimeLogService.class.getName();
        Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(servicePath);
        StringBuilder descriptors = new StringBuilder();
        boolean found = false;
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try (var input = resource.openStream()) {
                String descriptor = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                descriptors.append(resource).append(System.lineSeparator()).append(descriptor).append(System.lineSeparator());
                found |= descriptor.contains(ExampleRuntimeLogTraceService.class.getName());
            }
        }
        assertTrue(found, descriptors.toString());
    }

    private static GpuRuntimeLogRecord sampleRecord() {
        return GpuRuntimeLogRecord
                .of(GpuRuntimeLogLevel.WARN, "net.sixik.example", "example log")
                .withField("runtime.backend.target", "OPENCL")
                .withField("runtime.status", "synthetic");
    }

    private static void withTraceFileProperty(Path traceFile, ThrowingRunnable action) throws Exception {
        String previous = System.getProperty(ExampleRuntimeLogTraceService.TRACE_FILE_PROPERTY);
        try {
            if (traceFile == null) {
                System.clearProperty(ExampleRuntimeLogTraceService.TRACE_FILE_PROPERTY);
            } else {
                System.setProperty(ExampleRuntimeLogTraceService.TRACE_FILE_PROPERTY, traceFile.toString());
            }
            action.run();
        } finally {
            if (previous == null) {
                System.clearProperty(ExampleRuntimeLogTraceService.TRACE_FILE_PROPERTY);
            } else {
                System.setProperty(ExampleRuntimeLogTraceService.TRACE_FILE_PROPERTY, previous);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
