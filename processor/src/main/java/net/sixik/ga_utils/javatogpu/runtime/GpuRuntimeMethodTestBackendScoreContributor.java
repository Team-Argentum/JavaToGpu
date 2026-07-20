package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cache-only backend score bridge for warmed {@code @GPUTest} selection-probe evidence.
 */
public final class GpuRuntimeMethodTestBackendScoreContributor implements GpuRuntimeBackendScoreContributor {

    public static final String CONTRIBUTOR_ID = "javatogpu.backend.method-test-gpu-probe-evidence";
    public static final String CONTRIBUTOR_VERSION = "1";

    private static final int PASSED_PROBE_SCORE = 1_600_000_000;
    private static final int FAILED_PROBE_SCORE = -1_600_000_000;

    private GpuRuntimeMethodTestBackendScoreContributor() {
    }

    public static GpuRuntimeMethodTestBackendScoreContributor cacheOnly() {
        return new GpuRuntimeMethodTestBackendScoreContributor();
    }

    @Override
    public GpuRuntimeBackendScoreContribution scoreCandidate(GpuRuntimeBackendScoreContext context) {
        Objects.requireNonNull(context, "context");
        Optional<String> modeBlocker = context.compileOptions().backendOptions().methodTestProbeModeBlocker();
        if (modeBlocker.isPresent()) {
            return GpuRuntimeBackendScoreContribution.of(
                    0,
                    "method-test backend score skipped: invalid probe mode " + modeBlocker.orElseThrow()
            );
        }
        if (!context.compileOptions().backendOptions().requestsMethodTestProbeEvidenceRanking()) {
            return GpuRuntimeBackendScoreContribution.none();
        }
        if (context.descriptor().isEmpty()) {
            return GpuRuntimeBackendScoreContribution.of(
                    0,
                    "method-test backend score skipped: descriptor missing"
            );
        }

        GpuRuntimeMethodTestGpuProbeCache cache;
        try {
            cache = cache(context.compileOptions().backendOptions());
        } catch (RuntimeException exception) {
            return GpuRuntimeBackendScoreContribution.of(
                    0,
                    "method-test backend score skipped: cache path invalid " + exceptionMessage(exception)
            );
        }

        GpuKernelDescriptor descriptor = context.descriptor().orElseThrow();
        GpuRuntimeMethodTestProbePlan probePlan = probePlan(descriptor, context.irGpuArtifact());
        Set<String> selectionTestIds = probePlan.selectionProbeVectors().stream()
                .map(GpuRuntimeMethodTestVectorPlan::testId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (selectionTestIds.isEmpty()) {
            return GpuRuntimeBackendScoreContribution.of(
                    0,
                    "method-test backend score skipped: " + probePlan.firstBlocker()
            );
        }

        GpuRuntimeMethodTestFixtureValueBindingPlan bindings = GpuRuntimeMethodTestProbes.fixtureValueBindings(
                descriptor,
                probePlan,
                Thread.currentThread().getContextClassLoader(),
                GpuRuntimeLifecycleEventBus.empty()
        );
        GpuRuntimeMethodTestInvocationMaterializationPlan materialization =
                GpuRuntimeMethodTestProbes.fixtureInvocationMaterialization(
                        descriptor,
                        bindings,
                        GpuRuntimeLifecycleEventBus.empty()
                );
        if (!materialization.materializationReady()) {
            return GpuRuntimeBackendScoreContribution.of(
                    0,
                    "method-test backend score skipped: " + materialization.firstBlocker()
            );
        }

        List<GpuRuntimeMethodTestInvocationMaterialization> selectionInvocations = materialization.invocations().stream()
                .filter(invocation -> selectionTestIds.contains(invocation.testId()))
                .toList();
        EvidenceCounts counts = evidenceCounts(
                descriptor,
                context,
                cache,
                selectionInvocations
        );
        if (counts.failedCount > 0) {
            int failedScore = scaledScore(FAILED_PROBE_SCORE, counts.freshnessPermille);
            return GpuRuntimeBackendScoreContribution.of(
                    failedScore,
                    counts.diagnostic("failed cached method-test backend evidence " + signed(failedScore))
            );
        }
        if (counts.passedCount > 0 && counts.missingCount == 0 && counts.blockedCount == 0) {
            int passedScore = scaledScore(PASSED_PROBE_SCORE, counts.freshnessPermille);
            return GpuRuntimeBackendScoreContribution.of(
                    passedScore,
                    counts.diagnostic("passed cached method-test backend evidence " + signed(passedScore))
            );
        }
        if (counts.blockedCount > 0) {
            return GpuRuntimeBackendScoreContribution.of(
                    0,
                    counts.diagnostic("method-test backend evidence blocked +0")
            );
        }
        return GpuRuntimeBackendScoreContribution.of(
                0,
                counts.diagnostic("method-test backend evidence missing +0")
        );
    }

    @Override
    public String extensionId() {
        return CONTRIBUTOR_ID;
    }

    @Override
    public String extensionVersion() {
        return CONTRIBUTOR_VERSION;
    }

    @Override
    public int extensionOrder() {
        return 20;
    }

    private static EvidenceCounts evidenceCounts(
            GpuKernelDescriptor descriptor,
            GpuRuntimeBackendScoreContext context,
            GpuRuntimeMethodTestGpuProbeCache cache,
            List<GpuRuntimeMethodTestInvocationMaterialization> invocations
    ) {
        int passedCount = 0;
        int failedCount = 0;
        int missingCount = 0;
        int blockedCount = 0;
        int freshnessPermille = 1_000;
        int ageLimitedCount = 0;
        long oldestAgeMillis = -1L;
        Duration maxEntryAge = context.compileOptions().backendOptions().methodTestProbeEvidenceMaxAge().orElse(null);
        ArrayList<String> hashes = new ArrayList<>();
        GpuRuntimeDeviceProfile deviceProfile = deviceProfile(context);
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
                    context.compileOptions(),
                    context.report(),
                    deviceProfile
            );
            hashes.add(evidenceKey.stableHash());
            GpuRuntimeMethodTestGpuProbeCacheEntry entry = cache.get(evidenceKey);
            if (entry == null) {
                missingCount++;
            } else if (entry.execution().executionPassed()) {
                passedCount++;
                int entryFreshness = freshnessPermille(entry, maxEntryAge);
                freshnessPermille = Math.min(freshnessPermille, entryFreshness);
                ageLimitedCount += entryFreshness < 1_000 ? 1 : 0;
                oldestAgeMillis = Math.max(oldestAgeMillis, entry.ageMillis());
            } else {
                failedCount++;
                int entryFreshness = freshnessPermille(entry, maxEntryAge);
                freshnessPermille = Math.min(freshnessPermille, entryFreshness);
                ageLimitedCount += entryFreshness < 1_000 ? 1 : 0;
                oldestAgeMillis = Math.max(oldestAgeMillis, entry.ageMillis());
            }
        }
        return new EvidenceCounts(
                invocations.size(),
                passedCount,
                failedCount,
                missingCount,
                blockedCount,
                hashes,
                freshnessPermille,
                ageLimitedCount,
                oldestAgeMillis,
                maxEntryAge == null ? -1L : maxEntryAge.toMillis()
        );
    }

    private static int freshnessPermille(
            GpuRuntimeMethodTestGpuProbeCacheEntry entry,
            Duration maxEntryAge
    ) {
        if (entry == null || maxEntryAge == null || maxEntryAge.isZero() || maxEntryAge.isNegative()) {
            return 1_000;
        }
        long maxAgeMillis = maxEntryAge.toMillis();
        if (maxAgeMillis <= 0L) {
            return 1_000;
        }
        long ageMillis = entry.ageMillis();
        long fullWeightWindow = maxAgeMillis / 2L;
        if (ageMillis <= fullWeightWindow) {
            return 1_000;
        }
        if (ageMillis >= maxAgeMillis) {
            return 250;
        }
        long decayWindow = Math.max(1L, maxAgeMillis - fullWeightWindow);
        long remainingMillis = maxAgeMillis - ageMillis;
        long decayed = 250L + remainingMillis * 750L / decayWindow;
        return (int) Math.max(250L, Math.min(1_000L, decayed));
    }

    private static GpuRuntimeDeviceProfile deviceProfile(GpuRuntimeBackendScoreContext context) {
        if (context.deviceProfile().isPresent()
                && context.deviceProfile().orElseThrow().backendTarget() == context.report().backendTarget()) {
            return context.deviceProfile().orElseThrow();
        }
        return new GpuRuntimeDeviceProfile(
                context.report().backendTarget(),
                context.report().backendName(),
                context.report().deviceLabel(),
                "unknown",
                "unknown",
                context.report().apiVersionText(),
                -1L,
                context.report().localMemoryBytes() == null ? -1L : context.report().localMemoryBytes(),
                context.report().maxWorkGroupSize() == null ? -1L : context.report().maxWorkGroupSize(),
                -1L,
                context.report().supports(GpuRuntimeFeature.DOUBLE_PRECISION),
                context.report().supports(GpuRuntimeFeature.IMAGES),
                false
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

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    private static int scaledScore(int score, int freshnessPermille) {
        int boundedFreshness = Math.max(0, Math.min(1_000, freshnessPermille));
        return (int) ((long) score * boundedFreshness / 1_000L);
    }

    private static String exceptionMessage(RuntimeException exception) {
        return exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                ? "unknown"
                : exception.getMessage();
    }

    private record EvidenceCounts(
            int totalCount,
            int passedCount,
            int failedCount,
            int missingCount,
            int blockedCount,
            List<String> hashes,
            int freshnessPermille,
            int ageLimitedCount,
            long oldestAgeMillis,
            long maxAgeMillis
    ) {

        private String diagnostic(String status) {
            return status
                    + "; total=" + totalCount
                    + ", passed=" + passedCount
                    + ", failed=" + failedCount
                    + ", missing=" + missingCount
                    + ", blocked=" + blockedCount
                    + ", freshnessPermille=" + freshnessPermille
                    + ", ageLimited=" + ageLimitedCount
                    + ", oldestAgeMillis=" + metricText(oldestAgeMillis)
                    + ", maxAgeMillis=" + metricText(maxAgeMillis)
                    + ", hashes=" + String.join(",", hashes);
        }

        private static String metricText(long value) {
            return value < 0L ? "unknown" : Long.toString(value);
        }
    }
}
