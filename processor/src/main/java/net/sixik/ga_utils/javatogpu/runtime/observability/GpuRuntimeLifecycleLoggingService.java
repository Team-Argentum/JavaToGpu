package net.sixik.ga_utils.javatogpu.runtime.observability;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEvent;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEventKind;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleService;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLogLevel;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLogRecord;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLogger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridges runtime lifecycle events into the pluggable runtime logging bus.
 */
public final class GpuRuntimeLifecycleLoggingService implements GpuRuntimeLifecycleService {

    private static final String LOGGER_NAME = "net.sixik.ga_utils.javatogpu.runtime.lifecycle";

    @Override
    public void onRuntimeLifecycleEvent(GpuRuntimeLifecycleEvent event) {
        GpuRuntimeLifecycleEvent value = event == null
                ? GpuRuntimeLifecycleEvent.of(GpuRuntimeLifecycleEventKind.RUNTIME_SHUTDOWN_COMPLETED)
                : event;
        GpuRuntimeLogger.log(new GpuRuntimeLogRecord(
                levelFor(value),
                LOGGER_NAME,
                value.message(),
                logFields(value),
                null
        ));
    }

    @Override
    public String extensionId() {
        return "runtime.lifecycle.logging-bridge";
    }

    @Override
    public String extensionVersion() {
        return "1";
    }

    @Override
    public int extensionOrder() {
        return 10_500;
    }

    private static GpuRuntimeLogLevel levelFor(GpuRuntimeLifecycleEvent event) {
        String status = firstPresentField(event, "status", "selection.status", "warmup.status");
        if (isFailureStatus(status)) {
            return GpuRuntimeLogLevel.WARN;
        }
        return switch (event.kind()) {
            case VALIDATION_STARTED,
                    DESCRIPTOR_DISCOVERY_STARTED,
                    IRGPU_LOAD_STARTED,
                    BACKEND_LOWERER_SELECTION_STARTED,
                    OPTIMIZER_DISCOVERY_STARTED,
                    OPTIMIZER_PASS_STARTED,
                    BACKEND_COMPILATION_STARTED,
                    COMPILER_FEEDBACK_PARSING_STARTED,
                    MODULE_LOAD_STARTED,
                    ARTIFACT_DUMP_STARTED,
                    INVOCATION_STARTED,
                    RUNTIME_SHUTDOWN_STARTED,
                    METHOD_TEST_METADATA_STARTED,
                    METHOD_TEST_FIXTURE_READINESS_STARTED,
                    METHOD_TEST_VALUE_BINDING_STARTED,
                    METHOD_TEST_INVOCATION_MATERIALIZATION_STARTED,
                    METHOD_TEST_REFERENCE_COMPARISON_STARTED,
                    METHOD_TEST_GPU_PROBE_STARTED,
                    METHOD_TEST_GPU_PROBE_CACHE_LOOKUP_STARTED,
                    METHOD_TEST_GPU_PROBE_EVIDENCE_WARMUP_STARTED,
                    METHOD_TEST_GPU_PROBE_EVIDENCE_SELECTION_STARTED,
                    METHOD_TEST_GPU_PROBE_EVIDENCE_SELECTION_DISCOVERY_STARTED -> GpuRuntimeLogLevel.DEBUG;
            default -> GpuRuntimeLogLevel.INFO;
        };
    }

    private static Map<String, String> logFields(GpuRuntimeLifecycleEvent event) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("kind", event.kind().name());
        fields.put("backendTarget", event.backendTarget().name());
        fields.put("kernelResource", event.kernelResource());
        fields.put("optimizationProfile", event.optimizationProfile());
        event.fields().forEach((key, value) -> fields.put("event." + key, value));
        return fields;
    }

    private static boolean isFailureStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String normalized = status.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("fail")
                || normalized.contains("blocked")
                || normalized.contains("error")
                || normalized.contains("unavailable")
                || normalized.contains("rejected");
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
}
