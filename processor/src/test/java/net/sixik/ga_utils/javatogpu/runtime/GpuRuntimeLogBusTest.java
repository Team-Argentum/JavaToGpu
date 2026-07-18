package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionCapability;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeLogBusTest {

    @Test
    void dispatchesServicesInExtensionOrder() {
        ArrayList<String> records = new ArrayList<>();
        GpuRuntimeLogService later = service("test.logging.zeta", 20, records);
        GpuRuntimeLogService earlier = service("test.logging.alpha", 10, records);

        GpuRuntimeLogDispatchReport report = GpuRuntimeLogBus.of(List.of(later, earlier))
                .publish(GpuRuntimeLogRecord.of(GpuRuntimeLogLevel.INFO, "test.logger", "hello"));

        assertEquals(List.of("test.logging.alpha:INFO:hello", "test.logging.zeta:INFO:hello"), records);
        assertEquals(2, report.serviceReports().size());
        assertTrue(report.allServicesSucceeded());
        assertEquals("INFO", report.artifactFields("logging").get("logging.record.level"));
        assertEquals("test.logger", report.artifactFields("logging").get("logging.record.loggerName"));
    }

    @Test
    void serviceFailureIsIsolatedAndFollowingServicesStillRun() {
        AtomicBoolean followingInvoked = new AtomicBoolean();
        GpuRuntimeLogService failing = new OrderedLogService("test.logging.failing", 0) {
            @Override
            public void log(GpuRuntimeLogRecord record) {
                throw new IllegalStateException("logger exploded");
            }
        };
        GpuRuntimeLogService following = new OrderedLogService("test.logging.following", 10) {
            @Override
            public void log(GpuRuntimeLogRecord record) {
                followingInvoked.set(true);
            }
        };

        GpuRuntimeLogDispatchReport report = GpuRuntimeLogBus.of(List.of(failing, following))
                .publish(GpuRuntimeLogRecord.of(GpuRuntimeLogLevel.WARN, "test.logger", "careful"));

        assertTrue(followingInvoked.get());
        assertFalse(report.allServicesSucceeded());
        assertEquals(GpuExtensionExecutionOutcome.FAILED_CONTINUED, report.serviceReports().get(0).outcome());
        assertEquals(GpuExtensionExecutionOutcome.SUCCEEDED, report.serviceReports().get(1).outcome());
        assertTrue(report.serviceReports().get(0).pipelineContinued());
    }

    @Test
    void busRejectsWrongExtensionPhaseCapabilityOrPermission() {
        GpuRuntimeLogService wrongPhase = new OrderedLogService("test.logging.wrong-phase", 0) {
            @Override
            public GpuExtensionPhase extensionPhase() {
                return GpuExtensionPhase.RUNTIME_LIFECYCLE;
            }
        };
        GpuRuntimeLogService wrongCapability = new OrderedLogService("test.logging.wrong-capability", 0) {
            @Override
            public Set<GpuExtensionCapability> extensionCapabilities() {
                return Set.of(GpuExtensionCapability.DIAGNOSTIC_REPORTING);
            }
        };
        GpuRuntimeLogService wrongPermission = new OrderedLogService("test.logging.wrong-permission", 0) {
            @Override
            public GpuExtensionPermission extensionPermission() {
                return GpuExtensionPermission.MUTATION_PROPOSAL;
            }
        };

        assertThrows(IllegalArgumentException.class, () -> GpuRuntimeLogBus.of(List.of(wrongPhase)));
        assertThrows(IllegalArgumentException.class, () -> GpuRuntimeLogBus.of(List.of(wrongCapability)));
        assertThrows(IllegalArgumentException.class, () -> GpuRuntimeLogBus.of(List.of(wrongPermission)));
    }

    @Test
    void systemStreamServiceFormatsRecord() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        GpuRuntimeSystemStreamLogService service = new GpuRuntimeSystemStreamLogService(
                new PrintStream(bytes, true, StandardCharsets.UTF_8),
                "test.logging.system"
        );

        service.log(GpuRuntimeLogRecord.of(GpuRuntimeLogLevel.INFO, "test.logger", "hello")
                .withField("kernel", "demo.cl"));

        String text = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("[JavaToGpu][INFO][test.logger] hello"), text);
        assertTrue(text.contains("kernel=demo.cl"), text);
    }

    @Test
    void defaultBusCanBeDisabledByProperty() {
        String previous = System.getProperty(GpuRuntimeLogger.LOG_PROPERTY);
        System.setProperty(GpuRuntimeLogger.LOG_PROPERTY, "off");
        try {
            assertEquals(0, GpuRuntimeLogBus.loadDefault().serviceCount());
        } finally {
            restoreProperty(GpuRuntimeLogger.LOG_PROPERTY, previous);
        }
    }

    @Test
    void lifecycleLoggingBridgePublishesLifecycleEventsToConfiguredLogger() {
        AtomicReference<GpuRuntimeLogRecord> captured = new AtomicReference<>();
        GpuRuntimeLogService captureService = new OrderedLogService("test.logging.capture", 0) {
            @Override
            public void log(GpuRuntimeLogRecord record) {
                captured.set(record);
            }
        };
        GpuRuntimeLogger.use(GpuRuntimeLogBus.of(List.of(captureService)));
        try {
            new GpuRuntimeLifecycleLoggingService().onRuntimeLifecycleEvent(new GpuRuntimeLifecycleEvent(
                    GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_COMPLETED,
                    GpuBackendTarget.OPENCL,
                    "kernels/demo.cl",
                    "diagnostic",
                    "backend compile completed",
                    Map.of("status", "failed")
            ));
        } finally {
            GpuRuntimeLogger.reset();
        }

        GpuRuntimeLogRecord record = captured.get();
        assertEquals(GpuRuntimeLogLevel.WARN, record.level());
        assertEquals("net.sixik.ga_utils.javatogpu.runtime.lifecycle", record.loggerName());
        assertEquals("BACKEND_COMPILATION_COMPLETED", record.fields().get("kind"));
        assertEquals("failed", record.fields().get("event.status"));
    }

    private static GpuRuntimeLogService service(
            String id,
            int order,
            List<String> records
    ) {
        return new OrderedLogService(id, order) {
            @Override
            public void log(GpuRuntimeLogRecord record) {
                records.add(extensionId() + ":" + record.level().name() + ":" + record.message());
            }
        };
    }

    private static void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }

    private static class OrderedLogService implements GpuRuntimeLogService {
        private final String id;
        private final int order;

        private OrderedLogService(String id, int order) {
            this.id = id;
            this.order = order;
        }

        @Override
        public void log(GpuRuntimeLogRecord record) {
        }

        @Override
        public String extensionId() {
            return id;
        }

        @Override
        public String extensionVersion() {
            return "1";
        }

        @Override
        public int extensionOrder() {
            return order;
        }
    }
}
