package net.sixik.ga_utils.javatogpu.runtime.validation;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hardware-free harness for lifecycle and logging ServiceLoader extensions.
 */
public final class GpuRuntimeObservabilityServiceHarness {

    private final GpuRuntimeLifecycleEventBus lifecycleEventBus;
    private final GpuRuntimeLogBus logBus;

    private GpuRuntimeObservabilityServiceHarness(
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            GpuRuntimeLogBus logBus
    ) {
        this.lifecycleEventBus = lifecycleEventBus == null ? GpuRuntimeLifecycleEventBus.empty() : lifecycleEventBus;
        this.logBus = logBus == null ? GpuRuntimeLogBus.empty() : logBus;
    }

    public static GpuRuntimeObservabilityServiceHarness empty() {
        return new GpuRuntimeObservabilityServiceHarness(
                GpuRuntimeLifecycleEventBus.empty(),
                GpuRuntimeLogBus.empty()
        );
    }

    public static GpuRuntimeObservabilityServiceHarness of(
            Collection<? extends GpuRuntimeLifecycleEventListener> lifecycleListeners,
            Collection<? extends GpuRuntimeLogService> logServices
    ) {
        return new GpuRuntimeObservabilityServiceHarness(
                GpuRuntimeLifecycleEventBus.of(lifecycleListeners),
                GpuRuntimeLogBus.of(logServices)
        );
    }

    public static GpuRuntimeObservabilityServiceHarness of(
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            GpuRuntimeLogBus logBus
    ) {
        return new GpuRuntimeObservabilityServiceHarness(lifecycleEventBus, logBus);
    }

    public static GpuRuntimeObservabilityServiceHarness loadFromServiceLoader() {
        return new GpuRuntimeObservabilityServiceHarness(
                GpuRuntimeLifecycleEventBus.loadFromServiceLoader(),
                GpuRuntimeLogBus.loadFromServiceLoader()
        );
    }

    public GpuRuntimeLifecycleEventBus lifecycleEventBus() {
        return lifecycleEventBus;
    }

    public GpuRuntimeLogBus logBus() {
        return logBus;
    }

    public GpuRuntimeObservabilityServiceHarnessReport runSyntheticOpenCl() {
        return runSynthetic(GpuBackendTarget.OPENCL);
    }

    public GpuRuntimeObservabilityServiceHarnessReport runSynthetic(GpuBackendTarget backendTarget) {
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        GpuRuntimeLifecycleEventReport lifecycleReport = lifecycleEventBus.publish(syntheticLifecycleEvent(target));
        GpuRuntimeLogDispatchReport logReport = logBus.publish(syntheticLogRecord(target));
        return new GpuRuntimeObservabilityServiceHarnessReport(
                target,
                lifecycleEventBus.extensionArtifactFields("runtime.observability.lifecycleExtension"),
                logBus.extensionArtifactFields("runtime.observability.logExtension"),
                lifecycleReport,
                logReport
        );
    }

    public static GpuRuntimeLifecycleEvent syntheticLifecycleEvent(GpuBackendTarget backendTarget) {
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("runtime.observability.harness.present", "true");
        fields.put("runtime.status", "synthetic");
        fields.put("runtime.backend.target", target.name());
        fields.put("runtime.backend.name", target.name());
        fields.put("runtime.backendDevicePreflight.status", "synthetic");
        return new GpuRuntimeLifecycleEvent(
                GpuRuntimeLifecycleEventKind.BACKEND_DEVICE_PREFLIGHT_COMPLETED,
                target,
                "synthetic-observability-harness",
                "diagnostic",
                "runtime observability service harness synthetic lifecycle event",
                fields
        );
    }

    public static GpuRuntimeLogRecord syntheticLogRecord(GpuBackendTarget backendTarget) {
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        return GpuRuntimeLogRecord
                .of(
                        GpuRuntimeLogLevel.INFO,
                        "net.sixik.ga_utils.javatogpu.runtime.observability",
                        "runtime observability service harness synthetic log record"
                )
                .withField("runtime.observability.harness.present", "true")
                .withField("runtime.backend.target", target.name())
                .withField("runtime.status", "synthetic");
    }
}
