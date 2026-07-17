package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

import java.util.ArrayList;
import java.util.List;
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
}
