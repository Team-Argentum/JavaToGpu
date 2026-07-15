package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Process-local GPU probe cache lookup result.
 */
public record GpuRuntimeMethodTestGpuProbeCacheEntry(
        GpuRuntimeMethodTestGpuProbeExecution execution,
        boolean cacheHit
) {

    public GpuRuntimeMethodTestGpuProbeCacheEntry {
        execution = java.util.Objects.requireNonNull(execution, "execution").withCacheHit(cacheHit);
    }
}
