package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import net.sixik.ga_utils.javatogpu.runtime.validation.GpuRuntimeObservabilityServiceHarness;
import net.sixik.ga_utils.javatogpu.runtime.validation.GpuRuntimeObservabilityServiceHarnessReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeObservabilityServiceHarnessTest {

    @Test
    void runsLifecycleAndLogServicesAgainstSyntheticReceipts() {
        AtomicReference<GpuRuntimeLifecycleEvent> lifecycleEvent = new AtomicReference<>();
        AtomicReference<GpuRuntimeLogRecord> logRecord = new AtomicReference<>();
        GpuRuntimeLifecycleService lifecycleService = new OrderedLifecycleService("test.observability.lifecycle", 10) {
            @Override
            public void onRuntimeLifecycleEvent(GpuRuntimeLifecycleEvent event) {
                lifecycleEvent.set(event);
            }
        };
        GpuRuntimeLogService logService = new OrderedLogService("test.observability.log", 20) {
            @Override
            public void log(GpuRuntimeLogRecord record) {
                logRecord.set(record);
            }
        };

        GpuRuntimeObservabilityServiceHarnessReport report = GpuRuntimeObservabilityServiceHarness
                .of(List.of(lifecycleService), List.of(logService))
                .runSynthetic(GpuBackendTarget.CUDA);
        Map<String, String> fields = report.artifactFields("observability");

        assertEquals(GpuBackendTarget.CUDA, report.backendTarget());
        assertEquals("succeeded", report.status());
        assertTrue(report.allSucceeded());
        assertEquals(1, report.lifecycleListenerCount());
        assertEquals(1, report.logServiceCount());
        assertEquals(GpuRuntimeLifecycleEventKind.BACKEND_DEVICE_PREFLIGHT_COMPLETED, lifecycleEvent.get().kind());
        assertEquals("CUDA", lifecycleEvent.get().fields().get("runtime.backend.target"));
        assertEquals(GpuRuntimeLogLevel.INFO, logRecord.get().level());
        assertEquals("CUDA", logRecord.get().fields().get("runtime.backend.target"));
        assertEquals("succeeded", fields.get("observability.status"));
        assertEquals("1", fields.get("observability.lifecycle.listener.count"));
        assertEquals("1", fields.get("observability.log.service.count"));
        assertEquals("BACKEND_DEVICE_PREFLIGHT_COMPLETED",
                fields.get("observability.lifecycle.dispatch.event.kind"));
        assertEquals("INFO", fields.get("observability.log.dispatch.record.level"));
        assertTrue(report.toMarkdown().contains("Runtime observability service harness: succeeded"));
        assertTrue(report.toMarkdown().contains("Backend: CUDA"));
    }

    @Test
    void isolatesServiceFailuresAndReportsFailedContinuedStatus() {
        GpuRuntimeLifecycleService failingLifecycleService = new OrderedLifecycleService(
                "test.observability.lifecycle-failing",
                0
        ) {
            @Override
            public void onRuntimeLifecycleEvent(GpuRuntimeLifecycleEvent event) {
                throw new IllegalStateException("lifecycle failure");
            }
        };
        GpuRuntimeLogService failingLogService = new OrderedLogService("test.observability.log-failing", 0) {
            @Override
            public void log(GpuRuntimeLogRecord record) {
                throw new IllegalStateException("log failure");
            }
        };

        GpuRuntimeObservabilityServiceHarnessReport report = GpuRuntimeObservabilityServiceHarness
                .of(List.of(failingLifecycleService), List.of(failingLogService))
                .runSyntheticOpenCl();

        assertFalse(report.allSucceeded());
        assertEquals("failed-continued", report.status());
        assertEquals(GpuExtensionExecutionOutcome.FAILED_CONTINUED,
                report.lifecycleReport().listenerReports().get(0).outcome());
        assertEquals(GpuExtensionExecutionOutcome.FAILED_CONTINUED,
                report.logReport().serviceReports().get(0).outcome());
        assertTrue(report.artifactFields("observability")
                .get("observability.lifecycle.dispatch.listener.0.message")
                .contains("lifecycle failure"));
        assertTrue(report.artifactFields("observability")
                .get("observability.log.dispatch.service.0.message")
                .contains("log failure"));
    }

    private static class OrderedLifecycleService implements GpuRuntimeLifecycleService {
        private final String id;
        private final int order;

        private OrderedLifecycleService(String id, int order) {
            this.id = id;
            this.order = order;
        }

        @Override
        public void onRuntimeLifecycleEvent(GpuRuntimeLifecycleEvent event) {
        }

        @Override
        public String extensionId() {
            return id;
        }

        @Override
        public int extensionOrder() {
            return order;
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
        public int extensionOrder() {
            return order;
        }
    }
}
