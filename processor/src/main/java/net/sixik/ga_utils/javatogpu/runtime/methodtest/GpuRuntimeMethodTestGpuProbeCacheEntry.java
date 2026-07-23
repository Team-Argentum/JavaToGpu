package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

/**
 * Process-local GPU probe cache lookup result.
 */
public record GpuRuntimeMethodTestGpuProbeCacheEntry(
        GpuRuntimeMethodTestGpuProbeExecution execution,
        boolean cacheHit,
        long createdEpochMillis
) {

    public GpuRuntimeMethodTestGpuProbeCacheEntry(
            GpuRuntimeMethodTestGpuProbeExecution execution,
            boolean cacheHit
    ) {
        this(execution, cacheHit, System.currentTimeMillis());
    }

    public GpuRuntimeMethodTestGpuProbeCacheEntry {
        execution = java.util.Objects.requireNonNull(execution, "execution").withCacheHit(cacheHit);
        createdEpochMillis = createdEpochMillis <= 0L ? System.currentTimeMillis() : createdEpochMillis;
    }

    public long ageMillis() {
        return Math.max(0L, System.currentTimeMillis() - createdEpochMillis);
    }
}
