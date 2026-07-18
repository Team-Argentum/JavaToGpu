package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Explicit opt-in helper that warms {@code @GPUTest} probe evidence and then runs cache-only device selection.
 *
 * <p>This helper may execute probes during the warm-up phase because the caller asked for it. The follow-up device
 * selection remains read-only: it consumes shared or persistent cache evidence through
 * {@link GpuRuntimeMethodTestProbeMode#CACHE_ONLY} and never runs probes from inside a device policy.</p>
 */
public final class GpuRuntimeMethodTestProbeEvidenceSelection {

    private GpuRuntimeMethodTestProbeEvidenceSelection() {
    }

    public static GpuRuntimeMethodTestProbeEvidenceSelectionPlan warmAndSelect(
            GpuKernelDescriptor descriptor,
            List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> candidates,
            GpuRuntimeMethodTestGpuProbeOptions probeOptions
    ) {
        return warmAndSelect(
                descriptor,
                null,
                candidates,
                probeOptions,
                null,
                Optional.empty(),
                GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns(),
                GpuRuntimeLifecycleEventBus.loadFromServiceLoader()
        );
    }

    public static GpuRuntimeMethodTestProbeEvidenceSelectionPlan warmAndSelect(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader,
            List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> candidates,
            GpuRuntimeMethodTestGpuProbeOptions probeOptions,
            GpuRuntimeCompileOptions baseCompileOptions
    ) {
        return warmAndSelect(
                descriptor,
                preferredClassLoader,
                candidates,
                probeOptions,
                baseCompileOptions,
                Optional.empty(),
                GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns(),
                GpuRuntimeLifecycleEventBus.loadFromServiceLoader()
        );
    }

    /**
     * Discovers OpenCL devices, warms ranked GPU candidates, and runs cache-only device selection.
     */
    public static GpuRuntimeMethodTestProbeEvidenceSelectionPlan warmAndSelectOpenCl(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader,
            GpuRuntimeMethodTestGpuProbeOptions probeOptions,
            GpuRuntimeCompileOptions baseCompileOptions
    ) {
        return warmAndSelectOpenCl(
                descriptor,
                preferredClassLoader,
                probeOptions,
                baseCompileOptions,
                GpuRuntimeMethodTestProbeEvidenceWarmupCandidates.DEFAULT_OPENCL_GPU_WARMUP_LIMIT
        );
    }

    /**
     * Discovers OpenCL devices, caps how many GPU candidates may run warm-up probes, then selects cache-only.
     */
    public static GpuRuntimeMethodTestProbeEvidenceSelectionPlan warmAndSelectOpenCl(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader,
            GpuRuntimeMethodTestGpuProbeOptions probeOptions,
            GpuRuntimeCompileOptions baseCompileOptions,
            int maxWarmupCandidates
    ) {
        GpuRuntimeCompileOptions resolvedBaseOptions = baseCompileOptions == null
                ? GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                : baseCompileOptions;
        GpuRuntimeLifecycleEventBus eventBus = GpuRuntimeLifecycleEventBus.loadFromServiceLoader();
        GpuRuntimeDevicePolicyRegistry registry = GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns();
        publishOpenClSelectionEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_EVIDENCE_SELECTION_STARTED,
                descriptor,
                resolvedBaseOptions,
                "OpenCL method-test probe evidence selection started",
                Map.of(
                        "status", "started",
                        "maxWarmupCandidates", Integer.toString(maxWarmupCandidates)
                )
        );
        publishOpenClSelectionEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_EVIDENCE_SELECTION_DISCOVERY_STARTED,
                descriptor,
                resolvedBaseOptions,
                "OpenCL method-test probe evidence selection discovery started",
                Map.of(
                        "status", "started",
                        "backend", "OpenCL"
                )
        );
        GpuRuntimeDeviceDiscoveryResult discoveryResult = GpuRuntimeDeviceDiscovery.discoverOpenCl(
                resolvedBaseOptions,
                registry
        );
        publishOpenClSelectionEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_EVIDENCE_SELECTION_DISCOVERY_COMPLETED,
                descriptor,
                resolvedBaseOptions,
                "OpenCL method-test probe evidence selection discovery completed",
                discoveryFields(discoveryResult)
        );
        return warmAndSelectOpenCl(
                descriptor,
                preferredClassLoader,
                discoveryResult,
                probeOptions,
                resolvedBaseOptions,
                Optional.empty(),
                registry,
                eventBus,
                maxWarmupCandidates
        );
    }

    /**
     * OpenCL helper overload for tests, custom discovery, and callers that want explicit policy/event plumbing.
     */
    public static GpuRuntimeMethodTestProbeEvidenceSelectionPlan warmAndSelectOpenCl(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader,
            GpuRuntimeDeviceDiscoveryResult discoveryResult,
            GpuRuntimeMethodTestGpuProbeOptions probeOptions,
            GpuRuntimeCompileOptions baseCompileOptions,
            Optional<IrGpuArtifact> irGpuArtifact,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry,
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            int maxWarmupCandidates
    ) {
        GpuRuntimeCompileOptions resolvedBaseOptions = baseCompileOptions == null
                ? GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                : baseCompileOptions;
        GpuRuntimeLifecycleEventBus eventBus = lifecycleEventBus == null
                ? GpuRuntimeLifecycleEventBus.empty()
                : lifecycleEventBus;
        List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> warmupCandidates =
                GpuRuntimeMethodTestProbeEvidenceWarmupCandidates.openClGpuDevices(
                        discoveryResult,
                        maxWarmupCandidates
                );
        publishOpenClSelectionEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_EVIDENCE_SELECTION_CANDIDATES_SELECTED,
                descriptor,
                resolvedBaseOptions,
                "OpenCL method-test probe evidence selection candidates selected",
                candidateFields(warmupCandidates, maxWarmupCandidates)
        );
        GpuRuntimeMethodTestProbeEvidenceSelectionPlan selectionPlan = warmAndSelect(
                descriptor,
                preferredClassLoader,
                warmupCandidates,
                discoveryResult,
                probeOptions,
                resolvedBaseOptions,
                irGpuArtifact,
                devicePolicyRegistry,
                eventBus
        );
        publishOpenClSelectionEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_EVIDENCE_SELECTION_COMPLETED,
                descriptor,
                selectionPlan.selectionCompileOptions() == null
                        ? resolvedBaseOptions
                        : selectionPlan.selectionCompileOptions(),
                "OpenCL method-test probe evidence selection completed",
                selectionFields(selectionPlan)
        );
        return selectionPlan;
    }

    public static GpuRuntimeMethodTestProbeEvidenceSelectionPlan warmAndSelect(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader,
            List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> warmupCandidates,
            List<GpuRuntimeDeviceProfile> selectionCandidates,
            GpuRuntimeMethodTestGpuProbeOptions probeOptions,
            GpuRuntimeCompileOptions baseCompileOptions,
            Optional<IrGpuArtifact> irGpuArtifact,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        return warmAndSelectInternal(
                descriptor,
                preferredClassLoader,
                warmupCandidates,
                selectionCandidates,
                probeOptions,
                baseCompileOptions,
                irGpuArtifact,
                devicePolicyRegistry,
                lifecycleEventBus
        );
    }

    public static GpuRuntimeMethodTestProbeEvidenceSelectionPlan warmAndSelect(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader,
            List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> warmupCandidates,
            GpuRuntimeDeviceDiscoveryResult discoveryResult,
            GpuRuntimeMethodTestGpuProbeOptions probeOptions,
            GpuRuntimeCompileOptions baseCompileOptions,
            Optional<IrGpuArtifact> irGpuArtifact,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        if (discoveryResult == null || !discoveryResult.discoveryAvailable()) {
            String blocker = discoveryResult == null
                    ? "device-discovery-result-missing"
                    : discoveryResult.firstBlocker();
            ArrayList<String> diagnostics = new ArrayList<>();
            if (discoveryResult == null) {
                diagnostics.add("method-test probe evidence selection requires a device discovery result");
            } else {
                diagnostics.addAll(discoveryResult.diagnostics());
            }
            return blocked(
                    descriptor,
                    resolveBaseCompileOptions(baseCompileOptions, probeOptions, warmupCandidates),
                    blocker,
                    diagnostics
            );
        }
        return warmAndSelectInternal(
                descriptor,
                preferredClassLoader,
                warmupCandidates,
                discoveryResult.discoveredDevices(),
                probeOptions,
                baseCompileOptions,
                irGpuArtifact,
                devicePolicyRegistry,
                lifecycleEventBus
        );
    }

    public static GpuRuntimeMethodTestProbeEvidenceSelectionPlan warmAndSelect(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader,
            List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> candidates,
            GpuRuntimeMethodTestGpuProbeOptions probeOptions,
            GpuRuntimeCompileOptions baseCompileOptions,
            Optional<IrGpuArtifact> irGpuArtifact,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        return warmAndSelectInternal(
                descriptor,
                preferredClassLoader,
                candidates,
                null,
                probeOptions,
                baseCompileOptions,
                irGpuArtifact,
                devicePolicyRegistry,
                lifecycleEventBus
        );
    }

    private static GpuRuntimeMethodTestProbeEvidenceSelectionPlan warmAndSelectInternal(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader,
            List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> candidates,
            List<GpuRuntimeDeviceProfile> selectionCandidates,
            GpuRuntimeMethodTestGpuProbeOptions probeOptions,
            GpuRuntimeCompileOptions baseCompileOptions,
            Optional<IrGpuArtifact> irGpuArtifact,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> resolvedCandidates = candidates == null
                ? List.of()
                : candidates.stream().filter(Objects::nonNull).toList();
        List<GpuRuntimeDeviceProfile> resolvedSelectionCandidates = resolveSelectionCandidates(
                selectionCandidates,
                resolvedCandidates
        );
        GpuRuntimeCompileOptions resolvedBaseOptions = resolveBaseCompileOptions(baseCompileOptions, probeOptions, resolvedCandidates);
        GpuRuntimeMethodTestGpuProbeOptions resolvedProbeOptions = resolveProbeOptions(probeOptions, resolvedBaseOptions);
        GpuRuntimeLifecycleEventBus eventBus = lifecycleEventBus == null
                ? GpuRuntimeLifecycleEventBus.empty()
                : lifecycleEventBus;
        GpuRuntimeDevicePolicyRegistry registry = devicePolicyRegistry == null
                ? GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns()
                : devicePolicyRegistry;

        GpuRuntimeMethodTestProbeEvidenceWarmupPlan warmup = GpuRuntimeMethodTestProbeEvidenceWarmup.warmSelectionProbeEvidence(
                descriptor,
                preferredClassLoader,
                resolvedCandidates,
                resolvedProbeOptions,
                eventBus
        );
        GpuRuntimeCompileOptions selectionOptions = cacheOnlySelectionOptions(resolvedBaseOptions, resolvedProbeOptions.cache());
        GpuRuntimeDeviceSelection selection = registry.select(new GpuRuntimeDevicePolicyContext(
                descriptor,
                selectionOptions,
                resolvedSelectionCandidates,
                irGpuArtifact
        ));
        ArrayList<String> diagnostics = new ArrayList<>();
        diagnostics.add("method-test probe evidence was warmed explicitly before cache-only device selection");
        diagnostics.add("device selection consumed warmed evidence in "
                + selectionOptions.backendOptions().methodTestProbeMode().optionValue()
                + " mode");
        return new GpuRuntimeMethodTestProbeEvidenceSelectionPlan(
                descriptor == null ? "unknown" : descriptor.kernelName(),
                descriptor == null ? "" : descriptor.irGpuResource(),
                warmup,
                selectionOptions,
                selection,
                List.of(),
                diagnostics
        );
    }

    private static List<GpuRuntimeDeviceProfile> resolveSelectionCandidates(
            List<GpuRuntimeDeviceProfile> selectionCandidates,
            List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> warmupCandidates
    ) {
        if (selectionCandidates != null && !selectionCandidates.isEmpty()) {
            return selectionCandidates.stream().filter(Objects::nonNull).toList();
        }
        return warmupCandidates.stream()
                .map(GpuRuntimeMethodTestProbeEvidenceWarmupCandidate::deviceProfile)
                .filter(Objects::nonNull)
                .toList();
    }

    private static GpuRuntimeMethodTestGpuProbeOptions resolveProbeOptions(
            GpuRuntimeMethodTestGpuProbeOptions probeOptions,
            GpuRuntimeCompileOptions baseCompileOptions
    ) {
        GpuRuntimeMethodTestGpuProbeOptions resolved = probeOptions == null
                ? GpuRuntimeMethodTestGpuProbeOptions.cached()
                : probeOptions;
        if (resolved.cache() == null) {
            resolved = resolved.withCache(GpuRuntimeMethodTestGpuProbeCache.shared());
        }
        if (resolved.compileOptions() == null) {
            resolved = resolved.withCompileOptions(baseCompileOptions);
        }
        return resolved;
    }

    private static GpuRuntimeCompileOptions resolveBaseCompileOptions(
            GpuRuntimeCompileOptions baseCompileOptions,
            GpuRuntimeMethodTestGpuProbeOptions probeOptions,
            List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> candidates
    ) {
        if (baseCompileOptions != null) {
            return baseCompileOptions;
        }
        if (probeOptions != null && probeOptions.compileOptions() != null) {
            return probeOptions.compileOptions();
        }
        List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> resolvedCandidates = candidates == null
                ? List.of()
                : candidates;
        GpuBackendTarget target = resolvedCandidates.stream()
                .filter(Objects::nonNull)
                .map(GpuRuntimeMethodTestProbeEvidenceWarmupCandidate::deviceProfile)
                .filter(Objects::nonNull)
                .map(GpuRuntimeDeviceProfile::backendTarget)
                .findFirst()
                .orElse(GpuBackendTarget.UNKNOWN);
        return GpuRuntimeCompileOptions.defaults(target);
    }

    private static GpuRuntimeCompileOptions cacheOnlySelectionOptions(
            GpuRuntimeCompileOptions baseCompileOptions,
            GpuRuntimeMethodTestGpuProbeCache cache
    ) {
        GpuRuntimeCompileOptions base = baseCompileOptions == null
                ? GpuRuntimeCompileOptions.defaults(GpuBackendTarget.UNKNOWN)
                : baseCompileOptions;
        if (cache != null && cache.persistent()) {
            return base.withPersistentMethodTestProbeEvidenceRanking(cache.persistentDirectory(), cache.maxEntryAge());
        }
        return base.withMethodTestProbeMode(GpuRuntimeMethodTestProbeMode.CACHE_ONLY);
    }

    private static GpuRuntimeMethodTestProbeEvidenceSelectionPlan blocked(
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileOptions baseCompileOptions,
            String blocker,
            List<String> diagnostics
    ) {
        String resolvedBlocker = blocker == null || blocker.isBlank()
                ? "method-test-probe-evidence-selection-blocked"
                : blocker.trim();
        GpuRuntimeCompileOptions selectionOptions = cacheOnlySelectionOptions(baseCompileOptions, null);
        GpuRuntimeDeviceSelection selection = new GpuRuntimeDeviceSelection(
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                true,
                false,
                resolvedBlocker,
                diagnostics == null ? List.of() : diagnostics
        );
        return new GpuRuntimeMethodTestProbeEvidenceSelectionPlan(
                descriptor == null ? "unknown" : descriptor.kernelName(),
                descriptor == null ? "" : descriptor.irGpuResource(),
                null,
                selectionOptions,
                selection,
                List.of(resolvedBlocker),
                diagnostics
        );
    }

    private static void publishOpenClSelectionEvent(
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            GpuRuntimeLifecycleEventKind kind,
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileOptions compileOptions,
            String message,
            Map<String, String> fields
    ) {
        GpuRuntimeLifecycleEventBus eventBus = lifecycleEventBus == null
                ? GpuRuntimeLifecycleEventBus.empty()
                : lifecycleEventBus;
        LinkedHashMap<String, String> eventFields = new LinkedHashMap<>();
        eventFields.put("pipeline", "method-test");
        eventFields.put("stage", "probe-evidence-selection");
        eventFields.put("helper", "warmAndSelectOpenCl");
        eventFields.put("kernelName", descriptor == null ? "unknown" : normalize(descriptor.kernelName(), "unknown"));
        eventFields.put("irGpuResource", descriptor == null ? "" : normalize(descriptor.irGpuResource(), ""));
        if (fields != null) {
            fields.forEach((key, value) -> eventFields.put(key, normalize(value, "")));
        }
        eventBus.publish(new GpuRuntimeLifecycleEvent(
                kind,
                compileOptions == null ? GpuBackendTarget.OPENCL : compileOptions.backendTarget(),
                descriptor == null
                        ? "unknown"
                        : normalize(descriptor.kernelResource(), normalize(descriptor.irGpuResource(), "unknown")),
                compileOptions == null ? "off" : compileOptions.optimizationProfile(),
                message,
                eventFields
        ));
    }

    private static Map<String, String> discoveryFields(GpuRuntimeDeviceDiscoveryResult discoveryResult) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("discovery.available", Boolean.toString(discoveryResult != null && discoveryResult.discoveryAvailable()));
        fields.put("discovery.device.count", discoveryResult == null
                ? "0"
                : Integer.toString(discoveryResult.discoveredDevices().size()));
        fields.put("discovery.selectedDeviceKey", discoveryResult == null
                ? "none"
                : discoveryResult.selectedDevice().map(GpuRuntimeDevicePolicyContext::deviceKey).orElse("none"));
        fields.put("discovery.firstBlocker", discoveryResult == null
                ? "device-discovery-result-missing"
                : discoveryResult.firstBlocker());
        return fields;
    }

    private static Map<String, String> candidateFields(
            List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> candidates,
            int maxWarmupCandidates
    ) {
        List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> resolvedCandidates = candidates == null
                ? List.of()
                : candidates;
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("candidate.count", Integer.toString(resolvedCandidates.size()));
        fields.put("candidate.max", Integer.toString(maxWarmupCandidates));
        for (int index = 0; index < resolvedCandidates.size(); index++) {
            GpuRuntimeDeviceProfile profile = resolvedCandidates.get(index).deviceProfile();
            String prefix = "candidate." + index;
            fields.put(prefix + ".deviceKey", GpuRuntimeDevicePolicyContext.deviceKey(profile));
            fields.put(prefix + ".deviceId", profile.deviceId());
            fields.put(prefix + ".deviceLabel", profile.deviceLabel());
            fields.put(prefix + ".deviceClass", profile.deviceClass().name().toLowerCase(java.util.Locale.ROOT));
            fields.put(prefix + ".ownership", resolvedCandidates.get(index).ownership().name().toLowerCase(java.util.Locale.ROOT));
        }
        return fields;
    }

    private static Map<String, String> selectionFields(GpuRuntimeMethodTestProbeEvidenceSelectionPlan selectionPlan) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("selection.status", selectionPlan == null ? "blocked" : selectionPlan.status());
        fields.put("selection.ready", Boolean.toString(selectionPlan != null && selectionPlan.selectionReady()));
        fields.put("selection.selectedDeviceKey", selectionPlan == null
                ? "none"
                : selectionPlan.selectedDevice().map(GpuRuntimeDevicePolicyContext::deviceKey).orElse("none"));
        fields.put("selection.firstBlocker", selectionPlan == null ? "selection-plan-missing" : selectionPlan.firstBlocker());
        fields.put("warmup.status", selectionPlan == null || selectionPlan.warmupPlan() == null
                ? "not-run"
                : selectionPlan.warmupPlan().status());
        fields.put("warmup.candidate.count", selectionPlan == null || selectionPlan.warmupPlan() == null
                ? "0"
                : Integer.toString(selectionPlan.warmupPlan().candidateResults().size()));
        return fields;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
