package net.sixik.ga_utils.javatogpu.runtime.selection;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendAdapters;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendDeviceSelection;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendPolicy;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEvent;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEventBus;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEventKind;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleFields;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeSelectionResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Domain implementation support for combined backend/device selection preflight.
 *
 * <p>This class owns the selection-domain glue around backend selection: attaching device discovery catalogs, running
 * standard backend/device discovery, and publishing lifecycle events for those preflight steps.</p>
 */
public final class GpuRuntimeBackendDeviceSelectionSupport {

    private GpuRuntimeBackendDeviceSelectionSupport() {
    }

    /**
     * Selects a backend and attaches a backend-neutral native device discovery catalog.
     */
    public static GpuRuntimeBackendDeviceSelection selectWithDeviceDiscovery(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeDeviceDiscoveryCatalog deviceDiscoveryCatalog
    ) {
        return selectWithDeviceDiscovery(policy, deviceDiscoveryCatalog, GpuRuntimeLifecycleEventBus.empty());
    }

    /**
     * Selects a backend, attaches a precomputed device discovery catalog, and publishes optional lifecycle events.
     */
    public static GpuRuntimeBackendDeviceSelection selectWithDeviceDiscovery(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeDeviceDiscoveryCatalog deviceDiscoveryCatalog,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        Objects.requireNonNull(policy, "policy");
        GpuRuntimeLifecycleEventBus eventBus = lifecycleEventBus(lifecycleEventBus);
        publishBackendSelectionEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.BACKEND_SELECTION_STARTED,
                null,
                "runtime backend selection started",
                Map.of(
                        "requirement.count", Integer.toString(policy.requirements().size()),
                        "candidate.count", Integer.toString(policy.candidateFactories().size())
                )
        );
        GpuRuntimeSelectionResult backendSelection = GpuRuntimeBackendSelectionSupport.select(policy);
        publishBackendSelectionEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.BACKEND_SELECTION_COMPLETED,
                backendSelection,
                "runtime backend selection completed",
                Map.of()
        );
        GpuRuntimeBackendDeviceSelection result = backendSelection.withDeviceDiscovery(deviceDiscoveryCatalog);
        publishDeviceDiscoveryEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.DEVICE_DISCOVERY_COMPLETED,
                result,
                "runtime device discovery catalog attached",
                Map.of("deviceDiscovery.precomputed", "true")
        );
        return result;
    }

    /**
     * Selects from a backend policy and discovers the standard backend device inventory shape.
     */
    public static GpuRuntimeBackendDeviceSelection selectStandardBackendAndDevice(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeCompileOptions openClDiscoveryOptions
    ) {
        return selectStandardBackendAndDevice(policy, openClDiscoveryOptions, GpuRuntimeLifecycleEventBus.empty());
    }

    /**
     * Selects from a backend policy, discovers standard backend devices, and publishes optional lifecycle events.
     */
    public static GpuRuntimeBackendDeviceSelection selectStandardBackendAndDevice(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeCompileOptions openClDiscoveryOptions,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        Objects.requireNonNull(policy, "policy");
        GpuRuntimeLifecycleEventBus eventBus = lifecycleEventBus(lifecycleEventBus);
        publishBackendSelectionEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.BACKEND_SELECTION_STARTED,
                null,
                "runtime backend selection started",
                Map.of(
                        "requirement.count", Integer.toString(policy.requirements().size()),
                        "candidate.count", Integer.toString(policy.candidateFactories().size())
                )
        );
        GpuRuntimeSelectionResult backendSelection = GpuRuntimeBackendSelectionSupport.select(policy);
        publishBackendSelectionEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.BACKEND_SELECTION_COMPLETED,
                backendSelection,
                "runtime backend selection completed",
                Map.of()
        );
        publishDeviceDiscoveryEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.DEVICE_DISCOVERY_STARTED,
                backendSelection.withDeviceDiscovery(GpuRuntimeDeviceDiscoveryCatalog.empty()),
                "runtime device discovery started",
                Map.of("adapter.count", Integer.toString(GpuRuntimeBackendAdapters.standardWithPlannedBackends().size()))
        );
        GpuRuntimeDeviceDiscoveryCatalog catalog = GpuRuntimeDeviceDiscoverySupport.discoverStandardBackends(
                openClDiscoveryOptions
        );
        GpuRuntimeBackendDeviceSelection result = backendSelection.withDeviceDiscovery(catalog);
        publishDeviceDiscoveryEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.DEVICE_DISCOVERY_COMPLETED,
                result,
                "runtime device discovery completed",
                Map.of("deviceDiscovery.precomputed", "false")
        );
        return result;
    }

    private static GpuRuntimeLifecycleEventBus lifecycleEventBus(GpuRuntimeLifecycleEventBus lifecycleEventBus) {
        return lifecycleEventBus == null ? GpuRuntimeLifecycleEventBus.empty() : lifecycleEventBus;
    }

    private static void publishBackendSelectionEvent(
            GpuRuntimeLifecycleEventBus eventBus,
            GpuRuntimeLifecycleEventKind kind,
            GpuRuntimeSelectionResult selectionResult,
            String message,
            Map<String, String> fields
    ) {
        LinkedHashMap<String, String> eventFields = new LinkedHashMap<>();
        eventFields.put("pipeline", "backend-device-selection");
        if (fields != null) {
            eventFields.putAll(fields);
        }
        GpuBackendTarget backendTarget = GpuBackendTarget.UNKNOWN;
        if (selectionResult != null) {
            eventFields.putAll(GpuRuntimeLifecycleFields.backendSelectionFields(selectionResult.explanation()));
            eventFields.put("backendSelection.matched", Boolean.toString(selectionResult.matched()));
            eventFields.put("backendSelection.summary", selectionResult.explanationSummary());
            eventFields.putAll(selectionResult.artifactFields("backendSelection"));
            backendTarget = selectionResult.explanation().selectedBackendTarget();
        } else {
            eventFields.putAll(GpuRuntimeLifecycleFields.backendSelectionFields(null));
            GpuRuntimeLifecycleFields.putStatus(eventFields, "started");
        }
        eventBus.publish(new GpuRuntimeLifecycleEvent(
                kind,
                backendTarget,
                "runtime-backend-selection",
                "selection",
                message,
                eventFields
        ));
    }

    private static void publishDeviceDiscoveryEvent(
            GpuRuntimeLifecycleEventBus eventBus,
            GpuRuntimeLifecycleEventKind kind,
            GpuRuntimeBackendDeviceSelection selection,
            String message,
            Map<String, String> fields
    ) {
        LinkedHashMap<String, String> eventFields = new LinkedHashMap<>();
        eventFields.put("pipeline", "backend-device-selection");
        if (fields != null) {
            eventFields.putAll(fields);
        }
        if (selection != null) {
            eventFields.putAll(GpuRuntimeLifecycleFields.backendDeviceSelectionFields(selection.explanation()));
            eventFields.put("runtimeSelection.status", selection.status());
            eventFields.put("runtimeSelection.summary", selection.summary());
            eventFields.putAll(selection.artifactFields("runtimeSelection"));
        } else {
            eventFields.putAll(GpuRuntimeLifecycleFields.backendDeviceSelectionFields(null));
            GpuRuntimeLifecycleFields.putStatus(eventFields, "started");
        }
        GpuBackendTarget backendTarget = selection == null
                ? GpuBackendTarget.UNKNOWN
                : selection.explanation().backendSelection().selectedBackendTarget();
        eventBus.publish(new GpuRuntimeLifecycleEvent(
                kind,
                backendTarget,
                "runtime-device-discovery",
                "selection",
                message,
                eventFields
        ));
    }
}
