package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEvent;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEventBus;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEventKind;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleLifecycleTraceServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void serviceIsNoOpWhenTraceFileIsNotConfigured() throws Exception {
        Path traceFile = temporaryDirectory.resolve("example-lifecycle-service.trace");

        withTraceFileProperty(null, () -> new ExampleLifecycleTraceService().onRuntimeLifecycleEvent(sampleEvent()));

        assertFalse(Files.exists(traceFile));
    }

    @Test
    void writesHumanReadableLifecycleTraceWhenConfigured() throws Exception {
        Path traceFile = temporaryDirectory.resolve("example-lifecycle-service.trace");

        withTraceFileProperty(traceFile, () -> new ExampleLifecycleTraceService().onRuntimeLifecycleEvent(sampleEvent()));

        String trace = Files.readString(traceFile, StandardCharsets.UTF_8);
        assertTrue(trace.contains("BACKEND_COMPILATION_STARTED"), trace);
        assertTrue(trace.contains("backend=OPENCL"), trace);
        assertTrue(trace.contains("kernel=javatogpu/sample/Kernel.cl"), trace);
        assertTrue(trace.contains("profile=diagnostic"), trace);
        assertTrue(trace.contains("status=started"), trace);
    }

    @Test
    void serviceLoaderBusCanWriteThroughExampleLifecycleTraceService() throws Exception {
        Path traceFile = temporaryDirectory.resolve("service-loader-example-lifecycle.trace");

        withTraceFileProperty(traceFile, () -> GpuRuntimeLifecycleEventBus.loadFromServiceLoader()
                .publish(sampleEvent()));

        String trace = Files.readString(traceFile, StandardCharsets.UTF_8);
        assertTrue(trace.contains("BACKEND_COMPILATION_STARTED"), trace);
        assertTrue(trace.contains("backend=OPENCL"), trace);
    }

    @Test
    void serviceDescriptorRegistersExampleLifecycleTraceService() throws Exception {
        String servicePath = "META-INF/services/" + GpuRuntimeLifecycleService.class.getName();
        Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(servicePath);
        StringBuilder descriptors = new StringBuilder();
        boolean found = false;
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try (var input = resource.openStream()) {
                String descriptor = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                descriptors.append(resource).append(System.lineSeparator()).append(descriptor).append(System.lineSeparator());
                found |= descriptor.contains(ExampleLifecycleTraceService.class.getName());
            }
        }
        assertTrue(found, descriptors.toString());
    }

    private static GpuRuntimeLifecycleEvent sampleEvent() {
        return new GpuRuntimeLifecycleEvent(
                GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_STARTED,
                GpuBackendTarget.OPENCL,
                "javatogpu/sample/Kernel.cl",
                "diagnostic",
                "backend compile started",
                Map.of("status", "started")
        );
    }

    private static void withTraceFileProperty(Path traceFile, ThrowingRunnable action) throws Exception {
        String previous = System.getProperty(ExampleLifecycleTraceService.TRACE_FILE_PROPERTY);
        try {
            if (traceFile == null) {
                System.clearProperty(ExampleLifecycleTraceService.TRACE_FILE_PROPERTY);
            } else {
                System.setProperty(ExampleLifecycleTraceService.TRACE_FILE_PROPERTY, traceFile.toString());
            }
            action.run();
        } finally {
            if (previous == null) {
                System.clearProperty(ExampleLifecycleTraceService.TRACE_FILE_PROPERTY);
            } else {
                System.setProperty(ExampleLifecycleTraceService.TRACE_FILE_PROPERTY, previous);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
