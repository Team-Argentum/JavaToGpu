package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Backend-neutral runtime selection orchestrator.
 *
 * <p>The first concrete production adapter is OpenCL, but this selector deliberately works over generic backend
 * factories, requirements, ownership, and capability reports so future CUDA, Vulkan/SPIR-V, and Metal adapters can use
 * the same explanation surface.</p>
 */
public final class GpuRuntimeBackendSelectionOrchestrator {

    private GpuRuntimeBackendSelectionOrchestrator() {
    }

    /**
     * Selects a backend using an immutable policy and returns candidate-level audit evidence.
     */
    public static GpuRuntimeSelectionResult select(GpuRuntimeBackendPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        return select(policy.requirements(), policy.candidateFactories(), policy.candidateOwnerships());
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
        GpuRuntimeSelectionResult backendSelection = select(policy);
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
     *
     * <p>OpenCL is queried through the native adapter today. CUDA, Vulkan/SPIR-V, and Metal are represented as explicit
     * planned/unavailable discovery states until real discovery adapters are implemented.</p>
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
        GpuRuntimeSelectionResult backendSelection = select(policy);
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
        GpuRuntimeDeviceDiscoveryCatalog catalog = GpuRuntimeDeviceDiscovery.discoverStandardBackends(openClDiscoveryOptions);
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

    /**
     * Selects a borrowed backend from already-created backend candidates.
     */
    public static GpuRuntimeSelectionResult selectBorrowed(
            List<GpuRuntimeRequirement> requirements,
            GpuRuntimeBackend... candidates
    ) {
        Objects.requireNonNull(candidates, "candidates");
        ArrayList<GpuRuntimeBackendFactory> factories = new ArrayList<>(candidates.length);
        ArrayList<GpuRuntimeBackendOwnership> ownerships = new ArrayList<>(candidates.length);
        for (int index = 0; index < candidates.length; index++) {
            GpuRuntimeBackend candidate = Objects.requireNonNull(candidates[index], "candidates[" + index + "]");
            factories.add(() -> candidate);
            ownerships.add(GpuRuntimeBackendOwnership.BORROWED);
        }
        return select(requirements, factories, ownerships);
    }

    /**
     * Selects a backend from factories and records every creation, rejection, closure, and selected candidate.
     */
    public static GpuRuntimeSelectionResult select(
            List<GpuRuntimeRequirement> requirements,
            List<GpuRuntimeBackendFactory> candidateFactories,
            List<GpuRuntimeBackendOwnership> candidateOwnerships
    ) {
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(candidateFactories, "candidateFactories");
        Objects.requireNonNull(candidateOwnerships, "candidateOwnerships");
        if (candidateFactories.size() != candidateOwnerships.size()) {
            throw new IllegalArgumentException("candidate factory and ownership counts must match");
        }
        if (candidateFactories.isEmpty()) {
            return new GpuRuntimeSelectionResult(
                    null,
                    List.of("no backend candidates were provided"),
                    List.of()
            );
        }

        ArrayList<String> failures = new ArrayList<>();
        ArrayList<GpuRuntimeBackendCandidateDecision> decisions = new ArrayList<>();
        for (int index = 0; index < candidateFactories.size(); index++) {
            GpuRuntimeBackendFactory factory = Objects.requireNonNull(
                    candidateFactories.get(index),
                    "candidateFactories[" + index + "]"
            );
            GpuRuntimeBackendOwnership ownership = candidateOwnerships.get(index) == null
                    ? GpuRuntimeBackendOwnership.BORROWED
                    : candidateOwnerships.get(index);
            GpuRuntimeBackend candidate;
            try {
                candidate = factory.create();
            } catch (RuntimeException exception) {
                GpuRuntimeBackendCandidateDecision decision = GpuRuntimeBackendCandidateDecision.creationFailed(
                        index,
                        ownership,
                        exception
                );
                decisions.add(decision);
                failures.add(String.join("; ", decision.diagnostics()));
                continue;
            }

            GpuRuntimeBackendReport report = GpuRuntime.describeBackend(candidate);
            List<String> reasons = GpuRuntimeRequirements.failureReasons(report, requirements);
            if (reasons.isEmpty()) {
                decisions.add(GpuRuntimeBackendCandidateDecision.selected(index, report, ownership));
                return new GpuRuntimeSelectionResult(
                        new GpuRuntimeBackendSelection(candidate, report, ownership),
                        failures,
                        decisions
                );
            }

            boolean closed = false;
            if (ownership == GpuRuntimeBackendOwnership.OWNED) {
                closed = closeCandidateQuietly(candidate);
            }
            GpuRuntimeBackendCandidateDecision decision = GpuRuntimeBackendCandidateDecision.rejected(
                    index,
                    report,
                    ownership,
                    reasons,
                    closed
            );
            decisions.add(decision);
            failures.add(report.backendName() + ": " + String.join("; ", reasons));
        }

        return new GpuRuntimeSelectionResult(null, failures, decisions);
    }

    private static boolean closeCandidateQuietly(GpuRuntimeBackend candidate) {
        if (candidate instanceof AutoCloseable closeable) {
            try {
                closeable.close();
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
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
