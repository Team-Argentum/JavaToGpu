package net.sixik.ga_utils.javatogpu.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Applies cached backend correctness evidence to deterministic device selection.
 */
public final class GpuRuntimeDeviceSelfTestPolicy implements GpuRuntimeDevicePolicy {

    public static final String POLICY_ID = "javatogpu.runtime.device-self-test";
    public static final String POLICY_VERSION = "3";
    private final GpuRuntimeDeviceSelfTestCache cache;

    public GpuRuntimeDeviceSelfTestPolicy(GpuRuntimeDeviceSelfTestCache cache) {
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    public GpuRuntimeDeviceSelfTestCache cache() {
        return cache;
    }

    @Override
    public GpuRuntimeDevicePolicyDecision evaluate(GpuRuntimeDevicePolicyContext context) {
        GpuRuntimeDeviceSelfTestMode mode = context.compileOptions().backendOptions().deviceSelfTestMode();
        if (mode == GpuRuntimeDeviceSelfTestMode.DISABLED) {
            return new GpuRuntimeDevicePolicyDecision(
                    policyId(),
                    policyVersion(),
                    Map.of(),
                    Set.of(),
                    deviceSelfTestModeFact(mode),
                    List.of(),
                    true,
                    List.of(),
                    List.of("runtime device self-tests are disabled by compile options")
            );
        }

        LinkedHashMap<String, Integer> scoreAdjustments = new LinkedHashMap<>();
        LinkedHashSet<String> rejected = new LinkedHashSet<>();
        LinkedHashMap<String, String> facts = new LinkedHashMap<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        putRuntimeDeviceSelfTestFact(facts, "mode", mode.optionValue());
        for (GpuRuntimeDeviceProfile candidate : context.candidates()) {
            String deviceKey = GpuRuntimeDevicePolicyContext.deviceKey(candidate);
            List<GpuRuntimeDeviceSelfTestResult> results = cache.resultsFor(candidate);
            facts.put(deviceKey + ".selfTest.count", Integer.toString(results.size()));
            if (results.isEmpty()) {
                facts.put(deviceKey + ".selfTest.status", "missing");
                if (mode == GpuRuntimeDeviceSelfTestMode.REQUIRED) {
                    rejected.add(deviceKey);
                    diagnostics.add("required runtime device self-test evidence is missing for " + deviceKey);
                }
                continue;
            }

            int adjustment = 0;
            boolean failed = false;
            long candidateComputeRate = 0L;
            long candidateTransferRate = 0L;
            String computeProfile = "unavailable";
            String transferModel = "unavailable";
            int computeScoreCap = 0;
            int transferScoreCap = 0;
            for (int index = 0; index < results.size(); index++) {
                GpuRuntimeDeviceSelfTestResult result = results.get(index);
                String prefix = deviceKey + ".selfTest." + index;
                facts.put(prefix + ".runnerId", result.identity().runnerId());
                facts.put(prefix + ".runnerVersion", result.identity().runnerVersion());
                facts.put(prefix + ".compilerIdentity", result.identity().compilerIdentity());
                facts.put(prefix + ".outcome", result.outcome().name().toLowerCase(java.util.Locale.ROOT));
                facts.put(prefix + ".durationNanos", Long.toString(result.durationNanos()));
                facts.put(prefix + ".scoreAdjustment", Integer.toString(result.scoreAdjustment()));
                adjustment = saturatingAdd(adjustment, result.scoreAdjustment());
                GpuRuntimeDeviceSelfTestPerformance performance = result.performance();
                facts.put(prefix + ".performance.available", Boolean.toString(performance.available()));
                facts.put(prefix + ".performance.stable", Boolean.toString(performance.stable()));
                facts.put(prefix + ".performance.computeStable", Boolean.toString(performance.computeStable()));
                facts.put(prefix + ".performance.transferStable", Boolean.toString(performance.transferStable()));
                facts.put(prefix + ".performance.workloadProfile", performance.workloadProfile());
                facts.put(prefix + ".performance.transferModel", performance.transferModel());
                facts.put(prefix + ".performance.unifiedMemory", Boolean.toString(performance.unifiedMemory()));
                facts.put(prefix + ".performance.computeScoreCap", Integer.toString(performance.computeScoreCap()));
                facts.put(prefix + ".performance.transferScoreCap", Integer.toString(performance.transferScoreCap()));
                facts.put(prefix + ".performance.computeOperationsPerSecond", Long.toString(
                        performance.computeOperationsPerSecond()
                ));
                facts.put(prefix + ".performance.computeMedianNanos", Long.toString(
                        performance.computeSamples().medianNanos()
                ));
                facts.put(prefix + ".performance.computeSampleCount", Integer.toString(
                        performance.computeSamples().sampleCount()
                ));
                facts.put(prefix + ".performance.computeAcceptedSampleCount", Integer.toString(
                        performance.computeSamples().acceptedSampleCount()
                ));
                facts.put(prefix + ".performance.computeDiscardedSampleCount", Integer.toString(
                        performance.computeSamples().discardedSampleCount()
                ));
                facts.put(prefix + ".performance.computeNoisePermille", Integer.toString(
                        performance.computeSamples().noisePermille()
                ));
                facts.put(prefix + ".performance.transferBytesPerSecond", Long.toString(
                        performance.transferBytesPerSecond()
                ));
                facts.put(prefix + ".performance.transferMedianNanos", Long.toString(
                        performance.transferSamples().medianNanos()
                ));
                facts.put(prefix + ".performance.transferSampleCount", Integer.toString(
                        performance.transferSamples().sampleCount()
                ));
                facts.put(prefix + ".performance.transferAcceptedSampleCount", Integer.toString(
                        performance.transferSamples().acceptedSampleCount()
                ));
                facts.put(prefix + ".performance.transferDiscardedSampleCount", Integer.toString(
                        performance.transferSamples().discardedSampleCount()
                ));
                facts.put(prefix + ".performance.transferNoisePermille", Integer.toString(
                        performance.transferSamples().noisePermille()
                ));
                if (performance.computeStable()) {
                    if (performance.computeOperationsPerSecond() > candidateComputeRate) {
                        candidateComputeRate = performance.computeOperationsPerSecond();
                        computeProfile = performance.workloadProfile();
                        computeScoreCap = performance.computeScoreCap();
                    }
                } else if (performance.computeAvailable()) {
                    diagnostics.add(deviceKey + ": compute performance evidence is too noisy and does not affect ranking");
                }
                if (performance.transferStable()) {
                    if (performance.transferBytesPerSecond() > candidateTransferRate) {
                        candidateTransferRate = performance.transferBytesPerSecond();
                        transferModel = performance.transferModel();
                        transferScoreCap = performance.transferScoreCap();
                    }
                } else if (performance.transferAvailable()) {
                    diagnostics.add(deviceKey + ": transfer performance evidence is too noisy and does not affect ranking");
                }
                if (result.rejectDevice() || result.outcome() == GpuRuntimeDeviceSelfTestOutcome.FAILED) {
                    failed = true;
                }
                if (mode == GpuRuntimeDeviceSelfTestMode.REQUIRED
                        && result.outcome() != GpuRuntimeDeviceSelfTestOutcome.PASSED) {
                    failed = true;
                }
                result.diagnostics().forEach(message -> diagnostics.add(deviceKey + ": " + message));
            }
            facts.put(deviceKey + ".selfTest.status", failed ? "failed" : "accepted");
            if (failed) {
                rejected.add(deviceKey);
            } else {
                int computeScore = relativeScore(
                        candidateComputeRate,
                        maxStableComputeRate(context, computeProfile),
                        computeScoreCap
                );
                int transferScore = relativeScore(
                        candidateTransferRate,
                        maxStableTransferRate(context, transferModel),
                        transferScoreCap
                );
                facts.put(deviceKey + ".selfTest.performance.computeProfile", computeProfile);
                facts.put(deviceKey + ".selfTest.performance.transferModel", transferModel);
                facts.put(deviceKey + ".selfTest.performance.computeScore", Integer.toString(computeScore));
                facts.put(deviceKey + ".selfTest.performance.transferScore", Integer.toString(transferScore));
                adjustment = saturatingAdd(
                        adjustment,
                        computeScore
                );
                adjustment = saturatingAdd(
                        adjustment,
                        transferScore
                );
                if (adjustment != 0) {
                    scoreAdjustments.put(deviceKey, adjustment);
                }
            }
        }
        return new GpuRuntimeDevicePolicyDecision(
                policyId(),
                policyVersion(),
                scoreAdjustments,
                rejected,
                facts,
                List.of(),
                true,
                List.of(),
                diagnostics
        );
    }

    @Override
    public String policyId() {
        return POLICY_ID;
    }

    @Override
    public String policyVersion() {
        return POLICY_VERSION;
    }

    private static Map<String, String> deviceSelfTestModeFact(GpuRuntimeDeviceSelfTestMode mode) {
        LinkedHashMap<String, String> facts = new LinkedHashMap<>();
        putRuntimeDeviceSelfTestFact(facts, "mode", mode.optionValue());
        return facts;
    }

    private static void putRuntimeDeviceSelfTestFact(
            Map<String, String> facts,
            String key,
            Object value
    ) {
        GpuRuntimeArtifactProperties.putPortable(facts, "runtime.deviceSelfTest", key, value);
    }

    private static int saturatingAdd(int left, int right) {
        long sum = (long) left + right;
        if (sum > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (sum < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) sum;
    }

    private long maxStableComputeRate(GpuRuntimeDevicePolicyContext context, String workloadProfile) {
        return context.candidates().stream()
                .flatMap(candidate -> cache.resultsFor(candidate).stream())
                .filter(result -> result.outcome() == GpuRuntimeDeviceSelfTestOutcome.PASSED)
                .filter(result -> !result.rejectDevice())
                .map(GpuRuntimeDeviceSelfTestResult::performance)
                .filter(GpuRuntimeDeviceSelfTestPerformance::computeStable)
                .filter(performance -> performance.workloadProfile().equals(workloadProfile))
                .mapToLong(GpuRuntimeDeviceSelfTestPerformance::computeOperationsPerSecond)
                .max()
                .orElse(0L);
    }

    private long maxStableTransferRate(GpuRuntimeDevicePolicyContext context, String transferModel) {
        return context.candidates().stream()
                .flatMap(candidate -> cache.resultsFor(candidate).stream())
                .filter(result -> result.outcome() == GpuRuntimeDeviceSelfTestOutcome.PASSED)
                .filter(result -> !result.rejectDevice())
                .map(GpuRuntimeDeviceSelfTestResult::performance)
                .filter(GpuRuntimeDeviceSelfTestPerformance::transferStable)
                .filter(performance -> performance.transferModel().equals(transferModel))
                .mapToLong(GpuRuntimeDeviceSelfTestPerformance::transferBytesPerSecond)
                .max()
                .orElse(0L);
    }

    private static int relativeScore(long value, long maximum, int cap) {
        if (value <= 0L || maximum <= 0L || cap <= 0) {
            return 0;
        }
        return (int) Math.min(cap, Math.round((double) value * cap / maximum));
    }
}
