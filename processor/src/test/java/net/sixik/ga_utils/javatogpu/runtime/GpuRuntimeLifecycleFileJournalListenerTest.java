package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeLifecycleFileJournalListenerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void listenerIsNoOpWhenJournalFileIsNotConfigured() throws Exception {
        withJournalProperties(null, null, () -> {
            new GpuRuntimeLifecycleFileJournalListener().onRuntimeLifecycleEvent(sampleEvent(
                    GpuRuntimeLifecycleEventKind.INVOCATION_STARTED
            ));
            assertFalse(Files.exists(temporaryDirectory.resolve("runtime-lifecycle.jsonl")));
        });
    }

    @Test
    void writesJsonlLifecycleEventsWhenConfigured() throws Exception {
        Path journal = temporaryDirectory.resolve("runtime-lifecycle.jsonl");

        withJournalProperties(journal, "jsonl", () -> new GpuRuntimeLifecycleFileJournalListener()
                .onRuntimeLifecycleEvent(sampleEvent(GpuRuntimeLifecycleEventKind.INVOCATION_STARTED)));

        String jsonl = Files.readString(journal, StandardCharsets.UTF_8);
        assertTrue(jsonl.contains("\"kind\":\"INVOCATION_STARTED\""), jsonl);
        assertTrue(jsonl.contains("\"backendTarget\":\"OPENCL\""), jsonl);
        assertTrue(jsonl.contains("\"kernelResource\":\"javatogpu/sample/Demo/kernel.cl\""), jsonl);
        assertTrue(jsonl.contains("\"status\":\"started\""), jsonl);
        assertTrue(jsonl.contains("\"work.globalX\":\"4\""), jsonl);
    }

    @Test
    void writesIndexedPropertiesLifecycleEventsWhenConfigured() throws Exception {
        Path journal = temporaryDirectory.resolve("runtime-lifecycle.properties");

        withJournalProperties(journal, "properties", () -> {
            GpuRuntimeLifecycleFileJournalListener listener = new GpuRuntimeLifecycleFileJournalListener();
            listener.onRuntimeLifecycleEvent(sampleEvent(GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_STARTED));
            listener.onRuntimeLifecycleEvent(sampleEvent(GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_COMPLETED));
        });

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(journal, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        assertEquals("properties", properties.getProperty("journal.format"));
        assertEquals("2", properties.getProperty("event.count"));
        assertEquals("BACKEND_COMPILATION_STARTED", properties.getProperty("event.0.kind"));
        assertEquals("BACKEND_COMPILATION_COMPLETED", properties.getProperty("event.1.kind"));
        assertEquals("OPENCL", properties.getProperty("event.0.backendTarget"));
        assertEquals("status", properties.getProperty("event.0.field.0.key"));
        assertEquals("started", properties.getProperty("event.0.field.0.value"));
    }

    @Test
    void serviceLoaderBusCanWriteThroughBuiltInFileJournalListener() throws Exception {
        Path journal = temporaryDirectory.resolve("service-loader-lifecycle.jsonl");

        withJournalProperties(journal, "jsonl", () -> GpuRuntimeLifecycleEventBus.loadFromServiceLoader()
                .publish(sampleEvent(GpuRuntimeLifecycleEventKind.ARTIFACT_DUMP_COMPLETED)));

        String jsonl = Files.readString(journal, StandardCharsets.UTF_8);
        assertTrue(jsonl.contains("\"kind\":\"ARTIFACT_DUMP_COMPLETED\""), jsonl);
    }

    @Test
    void builtInJournalIsRegisteredAsLifecycleService() throws Exception {
        String servicePath = "META-INF/services/" + GpuRuntimeLifecycleService.class.getName();
        try (var input = Thread.currentThread().getContextClassLoader().getResourceAsStream(servicePath)) {
            assertTrue(input != null, servicePath);
            String descriptor = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(descriptor.contains(GpuRuntimeLifecycleFileJournalListener.class.getName()), descriptor);
        }
        assertTrue(new GpuRuntimeLifecycleFileJournalListener() instanceof GpuRuntimeLifecycleService);
    }

    private static GpuRuntimeLifecycleEvent sampleEvent(GpuRuntimeLifecycleEventKind kind) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", "started");
        fields.put("work.globalX", "4");
        return new GpuRuntimeLifecycleEvent(
                kind,
                GpuBackendTarget.OPENCL,
                "javatogpu/sample/Demo/kernel.cl",
                "diagnostic",
                "sample lifecycle event",
                fields
        );
    }

    private static void withJournalProperties(
            Path journal,
            String format,
            ThrowingRunnable action
    ) throws Exception {
        String previousFile = System.getProperty(GpuRuntimeLifecycleFileJournalListener.JOURNAL_FILE_PROPERTY);
        String previousFormat = System.getProperty(GpuRuntimeLifecycleFileJournalListener.JOURNAL_FORMAT_PROPERTY);
        try {
            if (journal == null) {
                System.clearProperty(GpuRuntimeLifecycleFileJournalListener.JOURNAL_FILE_PROPERTY);
            } else {
                System.setProperty(GpuRuntimeLifecycleFileJournalListener.JOURNAL_FILE_PROPERTY, journal.toString());
            }
            if (format == null) {
                System.clearProperty(GpuRuntimeLifecycleFileJournalListener.JOURNAL_FORMAT_PROPERTY);
            } else {
                System.setProperty(GpuRuntimeLifecycleFileJournalListener.JOURNAL_FORMAT_PROPERTY, format);
            }
            action.run();
        } finally {
            restoreProperty(GpuRuntimeLifecycleFileJournalListener.JOURNAL_FILE_PROPERTY, previousFile);
            restoreProperty(GpuRuntimeLifecycleFileJournalListener.JOURNAL_FORMAT_PROPERTY, previousFormat);
        }
    }

    private static void restoreProperty(String property, String value) {
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
