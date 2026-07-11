package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Process-local self-test evidence cache keyed by hardware, driver, runtime, runner, and compiler identity.
 */
public final class GpuRuntimeDeviceSelfTestCache {

    private static final GpuRuntimeDeviceSelfTestCache SHARED = new GpuRuntimeDeviceSelfTestCache();

    private final ConcurrentHashMap<GpuRuntimeDeviceSelfTestIdentity, GpuRuntimeDeviceSelfTestResult> results =
            new ConcurrentHashMap<>();

    public static GpuRuntimeDeviceSelfTestCache shared() {
        return SHARED;
    }

    public GpuRuntimeDeviceSelfTestCacheEntry getOrRun(
            GpuRuntimeDeviceSelfTestIdentity identity,
            Supplier<GpuRuntimeDeviceSelfTestResult> supplier
    ) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(supplier, "supplier");
        removeStaleRunnerEvidence(identity);
        AtomicBoolean produced = new AtomicBoolean(false);
        GpuRuntimeDeviceSelfTestResult result = results.computeIfAbsent(identity, ignored -> {
            GpuRuntimeDeviceSelfTestResult value = Objects.requireNonNull(supplier.get(), "self-test result");
            if (!identity.equals(value.identity())) {
                throw new IllegalArgumentException("Self-test result identity does not match the requested cache identity");
            }
            produced.set(true);
            return value;
        });
        return new GpuRuntimeDeviceSelfTestCacheEntry(result, !produced.get());
    }

    public void record(GpuRuntimeDeviceSelfTestResult result) {
        GpuRuntimeDeviceSelfTestResult value = Objects.requireNonNull(result, "result");
        removeStaleRunnerEvidence(value.identity());
        results.put(value.identity(), value);
    }

    public List<GpuRuntimeDeviceSelfTestResult> resultsFor(GpuRuntimeDeviceProfile profile) {
        if (profile == null) {
            return List.of();
        }
        return results.values().stream()
                .filter(result -> result.identity().matches(profile))
                .sorted(Comparator
                        .comparing((GpuRuntimeDeviceSelfTestResult result) -> result.identity().runnerId())
                        .thenComparing(result -> result.identity().runnerVersion())
                        .thenComparing(result -> result.identity().compilerIdentity()))
                .toList();
    }

    public int size() {
        return results.size();
    }

    public void clear() {
        results.clear();
    }

    private void removeStaleRunnerEvidence(GpuRuntimeDeviceSelfTestIdentity identity) {
        results.keySet().removeIf(existing -> existing.deviceFingerprint().equals(identity.deviceFingerprint())
                && existing.runnerId().equals(identity.runnerId())
                && !existing.equals(identity));
    }
}
