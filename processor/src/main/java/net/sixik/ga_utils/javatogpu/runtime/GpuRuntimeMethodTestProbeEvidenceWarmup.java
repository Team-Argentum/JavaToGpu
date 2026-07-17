package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Explicit opt-in orchestration for warming cache-backed {@code @GPUTest} GPU-probe evidence.
 *
 * <p>This helper intentionally stays outside device ranking. It may execute kernels through caller-provided backend
 * candidates, write probe evidence into the configured cache, and return an auditable report. Device selection can then
 * consume that evidence through {@link GpuRuntimeCompileOptions#withMethodTestProbeEvidenceRankingCached()} or
 * {@link GpuRuntimeCompileOptions#withPersistentMethodTestProbeEvidenceRanking(java.nio.file.Path)} without running
 * probes during selection.</p>
 */
public final class GpuRuntimeMethodTestProbeEvidenceWarmup {

    private GpuRuntimeMethodTestProbeEvidenceWarmup() {
    }

    public static GpuRuntimeMethodTestProbeEvidenceWarmupPlan warmSelectionProbeEvidence(
            GpuKernelDescriptor descriptor,
            List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> candidates,
            GpuRuntimeMethodTestGpuProbeOptions options
    ) {
        return warmSelectionProbeEvidence(
                descriptor,
                null,
                candidates,
                options,
                GpuRuntimeLifecycleEventBus.loadFromServiceLoader()
        );
    }

    public static GpuRuntimeMethodTestProbeEvidenceWarmupPlan warmSelectionProbeEvidence(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader,
            List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> candidates,
            GpuRuntimeMethodTestGpuProbeOptions options
    ) {
        return warmSelectionProbeEvidence(
                descriptor,
                preferredClassLoader,
                candidates,
                options,
                GpuRuntimeLifecycleEventBus.loadFromServiceLoader()
        );
    }

    public static GpuRuntimeMethodTestProbeEvidenceWarmupPlan warmSelectionProbeEvidence(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader,
            List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> candidates,
            GpuRuntimeMethodTestGpuProbeOptions options,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        GpuRuntimeMethodTestGpuProbeOptions resolvedOptions = options == null
                ? GpuRuntimeMethodTestGpuProbeOptions.defaults()
                : options;
        GpuRuntimeLifecycleEventBus eventBus = lifecycleEventBus == null
                ? GpuRuntimeLifecycleEventBus.empty()
                : lifecycleEventBus;
        List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> resolvedCandidates = candidates == null
                ? List.of()
                : candidates.stream().filter(Objects::nonNull).toList();

        publishWarmupEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_EVIDENCE_WARMUP_STARTED,
                descriptor,
                resolvedOptions,
                "@GPUTest probe evidence warm-up started",
                warmupStartedFields(resolvedCandidates, resolvedOptions)
        );
        GpuRuntimeMethodTestProbeEvidenceWarmupPlan result = warmSelectionProbeEvidenceInternal(
                descriptor,
                preferredClassLoader,
                resolvedCandidates,
                resolvedOptions,
                eventBus
        );
        publishWarmupEvent(
                eventBus,
                GpuRuntimeLifecycleEventKind.METHOD_TEST_GPU_PROBE_EVIDENCE_WARMUP_COMPLETED,
                descriptor,
                resolvedOptions,
                "@GPUTest probe evidence warm-up completed",
                warmupFields(result, resolvedOptions)
        );
        return result;
    }

    private static GpuRuntimeMethodTestProbeEvidenceWarmupPlan warmSelectionProbeEvidenceInternal(
            GpuKernelDescriptor descriptor,
            ClassLoader preferredClassLoader,
            List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> candidates,
            GpuRuntimeMethodTestGpuProbeOptions options,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        if (descriptor == null) {
            return blocked("unknown", "", "kernel-descriptor-missing", "Kernel descriptor is required for probe evidence warm-up");
        }
        if (candidates.isEmpty()) {
            return blocked(
                    descriptor.kernelName(),
                    descriptor.irGpuResource(),
                    "method-test-probe-warmup-candidates-missing",
                    "No backend/device candidates were provided for probe evidence warm-up"
            );
        }

        GpuRuntimeMethodTestProbePlan probePlan = GpuRuntimeMethodTestProbes.plan(
                descriptor,
                preferredClassLoader,
                lifecycleEventBus
        );
        GpuRuntimeMethodTestFixtureValueBindingPlan bindings = GpuRuntimeMethodTestProbes.fixtureValueBindings(
                descriptor,
                probePlan,
                preferredClassLoader,
                lifecycleEventBus
        );
        GpuRuntimeMethodTestInvocationMaterializationPlan materialization =
                GpuRuntimeMethodTestProbes.fixtureInvocationMaterialization(descriptor, bindings, lifecycleEventBus);
        GpuRuntimeMethodTestInvocationMaterializationPlan selectionMaterialization = selectionProbeMaterialization(
                materialization,
                probePlan
        );
        if (!selectionMaterialization.materializationReady()) {
            return new GpuRuntimeMethodTestProbeEvidenceWarmupPlan(
                    selectionMaterialization.kernelName(),
                    selectionMaterialization.irGpuResource(),
                    List.of(),
                    List.of(selectionMaterialization.firstBlocker()),
                    selectionMaterialization.diagnostics()
            );
        }

        ArrayList<GpuRuntimeMethodTestProbeEvidenceWarmupCandidateResult> results = new ArrayList<>();
        for (GpuRuntimeMethodTestProbeEvidenceWarmupCandidate candidate : candidates) {
            results.add(warmCandidate(descriptor, probePlan, selectionMaterialization, options, candidate, lifecycleEventBus));
        }
        return new GpuRuntimeMethodTestProbeEvidenceWarmupPlan(
                selectionMaterialization.kernelName(),
                selectionMaterialization.irGpuResource(),
                results,
                List.of(),
                List.of("method-test probe evidence warm-up completed for " + results.size() + " candidate(s)")
        );
    }

    private static GpuRuntimeMethodTestProbeEvidenceWarmupCandidateResult warmCandidate(
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestProbePlan probePlan,
            GpuRuntimeMethodTestInvocationMaterializationPlan selectionMaterialization,
            GpuRuntimeMethodTestGpuProbeOptions options,
            GpuRuntimeMethodTestProbeEvidenceWarmupCandidate candidate,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        GpuRuntimeBackend backend;
        try {
            backend = Objects.requireNonNull(candidate.backendFactory().create(), "warm-up backend");
        } catch (RuntimeException exception) {
            return GpuRuntimeMethodTestProbeEvidenceWarmupCandidateResult.blocked(
                    candidate.deviceProfile(),
                    "method-test-probe-warmup-backend-create-failed",
                    "Backend factory failed for " + GpuRuntimeDevicePolicyContext.deviceKey(candidate.deviceProfile())
                            + ": " + exception.getClass().getSimpleName() + ": " + normalize(exception.getMessage(), "backend create failed")
            );
        }

        GpuRuntimeMethodTestGpuProbeOptions candidateOptions = options
                .withCompileOptions(compileOptionsForCandidate(options, candidate.deviceProfile()))
                .withDeviceProfile(candidate.deviceProfile());
        try (GpuRuntimeScope ignored = candidate.ownership() == GpuRuntimeBackendOwnership.OWNED
                ? GpuRuntime.useOwnedBackend(backend)
                : GpuRuntime.useBackend(backend)) {
            GpuRuntimeMethodTestGpuProbePlan gpuProbePlan = GpuRuntimeMethodTestProbes.executeGpuProbe(
                    descriptor,
                    selectionMaterialization,
                    probePlan,
                    candidateOptions,
                    lifecycleEventBus
            );
            return new GpuRuntimeMethodTestProbeEvidenceWarmupCandidateResult(
                    GpuRuntimeDevicePolicyContext.deviceKey(candidate.deviceProfile()),
                    candidate.deviceProfile(),
                    gpuProbePlan,
                    List.of(),
                    List.of("method-test probe evidence warm-up ran for "
                            + GpuRuntimeDevicePolicyContext.deviceKey(candidate.deviceProfile()))
            );
        } catch (RuntimeException exception) {
            return GpuRuntimeMethodTestProbeEvidenceWarmupCandidateResult.blocked(
                    candidate.deviceProfile(),
                    "method-test-probe-warmup-execution-failed",
                    "Probe evidence warm-up failed for " + GpuRuntimeDevicePolicyContext.deviceKey(candidate.deviceProfile())
                            + ": " + exception.getClass().getSimpleName() + ": " + normalize(exception.getMessage(), "warm-up failed")
            );
        }
    }

    private static GpuRuntimeMethodTestInvocationMaterializationPlan selectionProbeMaterialization(
            GpuRuntimeMethodTestInvocationMaterializationPlan materialization,
            GpuRuntimeMethodTestProbePlan probePlan
    ) {
        if (materialization == null) {
            return new GpuRuntimeMethodTestInvocationMaterializationPlan(
                    "unknown",
                    "",
                    List.of(),
                    List.of("fixture-invocation-materialization-plan-missing"),
                    List.of("Fixture invocation materialization is required for probe evidence warm-up")
            );
        }
        Set<String> selectionTestIds = probePlan == null
                ? Set.of()
                : probePlan.selectionProbeVectors().stream()
                .map(GpuRuntimeMethodTestVectorPlan::testId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        ArrayList<GpuRuntimeMethodTestInvocationMaterialization> invocations = materialization.invocations().stream()
                .filter(invocation -> selectionTestIds.contains(invocation.testId()))
                .collect(Collectors.toCollection(ArrayList::new));
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        if (selectionTestIds.isEmpty()) {
            blockers.add("selection-probe-vectors-missing");
            diagnostics.add("No @GPUTest selection-probe vectors were available for evidence warm-up");
        }
        if (invocations.isEmpty()) {
            blockers.add("method-test-selection-probe-invocation-missing");
            diagnostics.add("No materialized selection-probe invocations were available for evidence warm-up");
        }
        for (GpuRuntimeMethodTestInvocationMaterialization invocation : invocations) {
            if (!invocation.invocationReady()) {
                blockers.add(invocation.firstBlocker());
                diagnostics.addAll(invocation.diagnostics());
            }
        }
        return new GpuRuntimeMethodTestInvocationMaterializationPlan(
                materialization.kernelName(),
                materialization.irGpuResource(),
                invocations,
                List.copyOf(blockers),
                diagnostics
        );
    }

    private static GpuRuntimeCompileOptions compileOptionsForCandidate(
            GpuRuntimeMethodTestGpuProbeOptions options,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        GpuBackendTarget backendTarget = deviceProfile == null ? GpuBackendTarget.UNKNOWN : deviceProfile.backendTarget();
        GpuRuntimeCompileOptions baseOptions = options.compileOptions() == null
                ? GpuRuntimeCompileOptions.defaults(backendTarget)
                : options.compileOptions();
        return compileOptionsPinnedToCandidate(baseOptions, deviceProfile);
    }

    private static GpuRuntimeCompileOptions compileOptionsPinnedToCandidate(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        String deviceId = deviceProfile == null ? "" : deviceProfile.deviceId();
        if (compileOptions == null || deviceId.isBlank() || "unknown".equalsIgnoreCase(deviceId)) {
            return compileOptions;
        }
        return compileOptions.withDeviceOverride(GpuRuntimeDeviceOverride.byDeviceId(deviceId));
    }

    private static GpuRuntimeMethodTestProbeEvidenceWarmupPlan blocked(
            String kernelName,
            String irGpuResource,
            String blocker,
            String diagnostic
    ) {
        return new GpuRuntimeMethodTestProbeEvidenceWarmupPlan(
                kernelName,
                irGpuResource,
                List.of(),
                List.of(blocker),
                List.of(diagnostic)
        );
    }

    private static void publishWarmupEvent(
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            GpuRuntimeLifecycleEventKind kind,
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestGpuProbeOptions options,
            String message,
            Map<String, String> fields
    ) {
        LinkedHashMap<String, String> eventFields = new LinkedHashMap<>();
        eventFields.put("pipeline", "method-test");
        eventFields.put("stage", "probe-evidence-warmup");
        eventFields.put("kernelName", descriptor == null ? "unknown" : normalize(descriptor.kernelName(), "unknown"));
        eventFields.put("irGpuResource", descriptor == null ? "" : normalize(descriptor.irGpuResource(), ""));
        if (fields != null) {
            fields.forEach((key, value) -> eventFields.put(key, normalize(value, "")));
        }
        GpuRuntimeCompileOptions compileOptions = options == null ? null : options.compileOptions();
        lifecycleEventBus.publish(new GpuRuntimeLifecycleEvent(
                kind,
                compileOptions == null ? GpuBackendTarget.UNKNOWN : compileOptions.backendTarget(),
                descriptor == null ? "unknown" : normalize(descriptor.kernelResource(), normalize(descriptor.irGpuResource(), "unknown")),
                compileOptions == null ? "off" : compileOptions.optimizationProfile(),
                message,
                eventFields
        ));
    }

    private static Map<String, String> warmupStartedFields(
            List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidate> candidates,
            GpuRuntimeMethodTestGpuProbeOptions options
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", "started");
        fields.put("candidate.count", Integer.toString(candidates.size()));
        fields.put("cache.enabled", Boolean.toString(options.cache() != null));
        fields.put("cache.persistent", Boolean.toString(options.cache() != null && options.cache().persistent()));
        fields.put("maxGlobalWorkItems", Long.toString(options.maxGlobalWorkItems()));
        return fields;
    }

    private static Map<String, String> warmupFields(
            GpuRuntimeMethodTestProbeEvidenceWarmupPlan plan,
            GpuRuntimeMethodTestGpuProbeOptions options
    ) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("status", plan.status());
        fields.put("warmupReady", Boolean.toString(plan.warmupReady()));
        fields.put("warmupPassed", Boolean.toString(plan.warmupPassed()));
        fields.put("candidate.count", Integer.toString(plan.candidateResults().size()));
        fields.put("candidate.ready.count", Integer.toString(plan.candidateReadyCount()));
        fields.put("candidate.passed.count", Integer.toString(plan.candidatePassedCount()));
        fields.put("candidate.failed.count", Integer.toString(plan.candidateFailedCount()));
        fields.put("firstBlocker", plan.firstBlocker());
        fields.put("firstFailure", plan.firstFailure());
        fields.put("cache.enabled", Boolean.toString(options.cache() != null));
        fields.put("cache.persistent", Boolean.toString(options.cache() != null && options.cache().persistent()));
        return fields;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
