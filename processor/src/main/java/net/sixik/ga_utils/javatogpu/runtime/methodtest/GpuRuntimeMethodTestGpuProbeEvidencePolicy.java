package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies already-recorded {@code @GPUTest} GPU probe evidence to device ranking.
 *
 * <p>This policy never compiles or executes kernels. It only computes the stable evidence key that would correspond to
 * each candidate device and reads an opt-in cache. Missing evidence is neutral, passed evidence boosts ranking, and
 * failed cached evidence rejects the candidate.</p>
 */
public final class GpuRuntimeMethodTestGpuProbeEvidencePolicy implements GpuRuntimeDevicePolicy {

    public static final String POLICY_ID = "javatogpu.device.method-test-gpu-probe-evidence";
    public static final String POLICY_VERSION = "1";

    private static final int PASSED_PROBE_SCORE = 1_600_000_000;

    @Override
    public GpuRuntimeDevicePolicyDecision evaluate(GpuRuntimeDevicePolicyContext context) {
        Objects.requireNonNull(context, "context");
        Optional<String> modeBlocker = context.compileOptions().backendOptions().methodTestProbeModeBlocker();
        if (modeBlocker.isPresent()) {
            return new GpuRuntimeDevicePolicyDecision(
                    policyId(),
                    policyVersion(),
                    Map.of(),
                    Set.of(),
                    Map.of(
                            "methodTestProbeEvidence.status", "compile-options-invalid",
                            "methodTestProbeEvidence.firstBlocker", modeBlocker.orElseThrow(),
                            "methodTestProbeEvidence.mode", "invalid"
                    ),
                    List.of(),
                    false,
                    List.of("method-test probe mode is invalid: " + modeBlocker.orElseThrow()),
                    List.of()
            );
        }
        if (!context.compileOptions().backendOptions().requestsMethodTestProbeEvidenceRanking()) {
            return GpuRuntimeDevicePolicyDecision.noChange(this);
        }

        GpuRuntimeMethodTestProbeMode mode = context.compileOptions().backendOptions().methodTestProbeMode();

        GpuRuntimeMethodTestGpuProbeCache cache;
        try {
            cache = cache(context.compileOptions().backendOptions());
        } catch (RuntimeException exception) {
            return new GpuRuntimeDevicePolicyDecision(
                    policyId(),
                    policyVersion(),
                    Map.of(),
                    Set.of(),
                    Map.of("methodTestProbeEvidence.status", "cache-path-invalid"),
                    List.of(),
                    false,
                    List.of("method-test probe evidence cache path is invalid: " + exception.getMessage()),
                    List.of()
            );
        }

        LinkedHashMap<String, String> facts = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> scoreAdjustments = new LinkedHashMap<>();
        LinkedHashSet<String> rejected = new LinkedHashSet<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        facts.put("methodTestProbeEvidence.mode", mode.optionValue());
        facts.put("methodTestProbeEvidence.execution", "cache-only");
        facts.put("methodTestProbeEvidence.cache.persistent", Boolean.toString(cache.persistent()));
        facts.put("methodTestProbeEvidence.cache.path", cache.persistentDirectory() == null
                ? "process-local"
                : cache.persistentDirectory().toString());

        Optional<GpuKernelDescriptor> descriptor = context.descriptor();
        if (descriptor.isEmpty()) {
            facts.put("methodTestProbeEvidence.status", "descriptor-missing");
            diagnostics.add("method-test probe evidence ranking skipped because no kernel descriptor is available");
            return decision(scoreAdjustments, rejected, facts, diagnostics);
        }

        GpuRuntimeMethodTestProbePlan probePlan = probePlan(descriptor.orElseThrow(), context.irGpuArtifact());
        Set<String> selectionTestIds = probePlan.selectionProbeVectors().stream()
                .map(GpuRuntimeMethodTestVectorPlan::testId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (selectionTestIds.isEmpty()) {
            facts.put("methodTestProbeEvidence.status", "selection-probe-missing");
            facts.put("methodTestProbeEvidence.firstBlocker", probePlan.firstBlocker());
            diagnostics.add("method-test probe evidence ranking skipped: " + probePlan.firstBlocker());
            return decision(scoreAdjustments, rejected, facts, diagnostics);
        }

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        GpuRuntimeMethodTestFixtureValueBindingPlan bindings = GpuRuntimeMethodTestProbes.fixtureValueBindings(
                descriptor.orElseThrow(),
                probePlan,
                classLoader,
                GpuRuntimeLifecycleEventBus.empty()
        );
        GpuRuntimeMethodTestInvocationMaterializationPlan materialization =
                GpuRuntimeMethodTestProbes.fixtureInvocationMaterialization(
                        descriptor.orElseThrow(),
                        bindings,
                        GpuRuntimeLifecycleEventBus.empty()
                );
        if (!materialization.materializationReady()) {
            facts.put("methodTestProbeEvidence.status", "materialization-blocked");
            facts.put("methodTestProbeEvidence.firstBlocker", materialization.firstBlocker());
            diagnostics.add("method-test probe evidence ranking skipped: " + materialization.firstBlocker());
            return decision(scoreAdjustments, rejected, facts, diagnostics);
        }

        List<GpuRuntimeMethodTestInvocationMaterialization> selectionInvocations = materialization.invocations().stream()
                .filter(invocation -> selectionTestIds.contains(invocation.testId()))
                .toList();
        facts.put("methodTestProbeEvidence.status", "active");
        facts.put("methodTestProbeEvidence.selectionProbe.count", Integer.toString(selectionInvocations.size()));

        for (GpuRuntimeDeviceProfile candidate : context.candidates()) {
            evaluateCandidate(
                    descriptor.orElseThrow(),
                    context.compileOptions(),
                    cache,
                    selectionInvocations,
                    candidate,
                    facts,
                    scoreAdjustments,
                    rejected,
                    diagnostics
            );
        }
        return decision(scoreAdjustments, rejected, facts, diagnostics);
    }

    @Override
    public String policyId() {
        return POLICY_ID;
    }

    @Override
    public String policyVersion() {
        return POLICY_VERSION;
    }

    @Override
    public int extensionOrder() {
        return 10;
    }

    private void evaluateCandidate(
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeMethodTestGpuProbeCache cache,
            List<GpuRuntimeMethodTestInvocationMaterialization> invocations,
            GpuRuntimeDeviceProfile candidate,
            LinkedHashMap<String, String> facts,
            LinkedHashMap<String, Integer> scoreAdjustments,
            LinkedHashSet<String> rejected,
            ArrayList<String> diagnostics
    ) {
        String deviceKey = GpuRuntimeDevicePolicyContext.deviceKey(candidate);
        int passedCount = 0;
        int failedCount = 0;
        int missingCount = 0;
        int blockedCount = 0;
        ArrayList<String> evidenceHashes = new ArrayList<>();
        for (GpuRuntimeMethodTestInvocationMaterialization invocation : invocations) {
            GpuExecutionConfig executionConfig = inferExecutionConfig(invocation);
            if (executionConfig == null) {
                blockedCount++;
                continue;
            }
            GpuRuntimeMethodTestGpuProbeEvidenceKey evidenceKey = GpuRuntimeMethodTestGpuProbeEvidenceKey.from(
                    descriptor,
                    invocation,
                    executionConfig,
                    compileOptions,
                    backendReport(candidate),
                    candidate
            );
            evidenceHashes.add(evidenceKey.stableHash());
            GpuRuntimeMethodTestGpuProbeCacheEntry entry = cache.get(evidenceKey);
            if (entry == null) {
                missingCount++;
            } else if (entry.execution().executionPassed()) {
                passedCount++;
            } else {
                failedCount++;
            }
        }

        facts.put(deviceKey + ".methodTestProbeEvidence.count", Integer.toString(invocations.size()));
        facts.put(deviceKey + ".methodTestProbeEvidence.passed.count", Integer.toString(passedCount));
        facts.put(deviceKey + ".methodTestProbeEvidence.failed.count", Integer.toString(failedCount));
        facts.put(deviceKey + ".methodTestProbeEvidence.missing.count", Integer.toString(missingCount));
        facts.put(deviceKey + ".methodTestProbeEvidence.blocked.count", Integer.toString(blockedCount));
        for (int index = 0; index < evidenceHashes.size(); index++) {
            facts.put(deviceKey + ".methodTestProbeEvidence." + index + ".stableHash", evidenceHashes.get(index));
        }
        if (failedCount > 0) {
            facts.put(deviceKey + ".methodTestProbeEvidence.status", "failed");
            rejected.add(deviceKey);
            diagnostics.add(deviceKey + ": cached method-test GPU probe evidence failed");
        } else if (passedCount > 0 && missingCount == 0 && blockedCount == 0) {
            facts.put(deviceKey + ".methodTestProbeEvidence.status", "passed");
            scoreAdjustments.put(deviceKey, PASSED_PROBE_SCORE);
            diagnostics.add(deviceKey + ": cached method-test GPU probe evidence passed");
        } else if (blockedCount > 0) {
            facts.put(deviceKey + ".methodTestProbeEvidence.status", "blocked");
        } else {
            facts.put(deviceKey + ".methodTestProbeEvidence.status", "missing");
        }
    }

    private static GpuRuntimeDevicePolicyDecision decision(
            Map<String, Integer> scoreAdjustments,
            Set<String> rejected,
            Map<String, String> facts,
            List<String> diagnostics
    ) {
        return new GpuRuntimeDevicePolicyDecision(
                POLICY_ID,
                POLICY_VERSION,
                scoreAdjustments,
                rejected,
                facts,
                List.of(),
                true,
                List.of(),
                diagnostics
        );
    }

    private static GpuRuntimeMethodTestProbePlan probePlan(
            GpuKernelDescriptor descriptor,
            Optional<IrGpuArtifact> artifact
    ) {
        return artifact
                .map(value -> GpuRuntimeMethodTestProbes.plan(descriptor, value, GpuRuntimeLifecycleEventBus.empty()))
                .orElseGet(() -> GpuRuntimeMethodTestProbes.plan(
                        descriptor,
                        Thread.currentThread().getContextClassLoader(),
                        GpuRuntimeLifecycleEventBus.empty()
                ));
    }

    private static GpuRuntimeMethodTestGpuProbeCache cache(GpuBackendCompileOptions options) {
        Optional<String> cachePath = options.methodTestProbeEvidenceCachePath();
        if (cachePath.isPresent()) {
            return GpuRuntimeMethodTestGpuProbeCache.persistent(
                    Path.of(cachePath.orElseThrow()),
                    options.methodTestProbeEvidenceMaxAge().orElse(null)
            );
        }
        return GpuRuntimeMethodTestGpuProbeCache.shared();
    }

    private static GpuExecutionConfig inferExecutionConfig(GpuRuntimeMethodTestInvocationMaterialization invocation) {
        int inferredItemCount = -1;
        for (GpuRuntimeMethodTestInvocationArgument argument : invocation.arguments()) {
            if (!argument.expectedOutputReady()) {
                continue;
            }
            int itemCount = argument.expectedOutputItemCount();
            if (itemCount <= 0) {
                return null;
            }
            if (inferredItemCount < 0) {
                inferredItemCount = itemCount;
            } else if (inferredItemCount != itemCount) {
                return null;
            }
        }
        return inferredItemCount <= 0 ? null : GpuExecutionConfig.oneDimensional(inferredItemCount);
    }

    private static GpuRuntimeBackendReport backendReport(GpuRuntimeDeviceProfile profile) {
        return GpuRuntimeBackendReport.available(
                profile.backendTarget(),
                profile.backendName(),
                profile.deviceLabel(),
                null,
                profile.apiVersionText(),
                Set.of(),
                profile.localMemoryBytes() < 0L ? null : profile.localMemoryBytes(),
                profile.maxWorkGroupSize() < 0L ? null : profile.maxWorkGroupSize(),
                "method-test GPU probe evidence ranking candidate"
        );
    }
}
