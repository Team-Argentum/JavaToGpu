package net.sixik.ga_utils.javatogpu.runtime;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Bounded execution options for opt-in {@code @GPUTest} GPU probes.
 */
public record GpuRuntimeMethodTestGpuProbeOptions(
        GpuExecutionConfig executionConfig,
        GpuRuntimeCompileOptions compileOptions,
        GpuRuntimeDeviceProfile deviceProfile,
        GpuRuntimeMethodTestGpuProbeCache cache,
        long maxGlobalWorkItems
) {

    public static final long DEFAULT_MAX_GLOBAL_WORK_ITEMS = 1_048_576L;

    public GpuRuntimeMethodTestGpuProbeOptions {
        maxGlobalWorkItems = maxGlobalWorkItems <= 0L ? DEFAULT_MAX_GLOBAL_WORK_ITEMS : maxGlobalWorkItems;
    }

    public static GpuRuntimeMethodTestGpuProbeOptions defaults() {
        return new GpuRuntimeMethodTestGpuProbeOptions(null, null, null, null, DEFAULT_MAX_GLOBAL_WORK_ITEMS);
    }

    public static GpuRuntimeMethodTestGpuProbeOptions cached() {
        return defaults().withCache(GpuRuntimeMethodTestGpuProbeCache.shared());
    }

    public static GpuRuntimeMethodTestGpuProbeOptions persistentCached(Path cacheDirectory) {
        return defaults().withPersistentCache(cacheDirectory);
    }

    public static GpuRuntimeMethodTestGpuProbeOptions persistentCached(Path cacheDirectory, Duration maxEntryAge) {
        return defaults().withPersistentCache(cacheDirectory, maxEntryAge);
    }

    public GpuRuntimeMethodTestGpuProbeOptions withExecutionConfig(GpuExecutionConfig executionConfig) {
        return new GpuRuntimeMethodTestGpuProbeOptions(executionConfig, compileOptions, deviceProfile, cache, maxGlobalWorkItems);
    }

    public GpuRuntimeMethodTestGpuProbeOptions withCompileOptions(GpuRuntimeCompileOptions compileOptions) {
        return new GpuRuntimeMethodTestGpuProbeOptions(executionConfig, compileOptions, deviceProfile, cache, maxGlobalWorkItems);
    }

    public GpuRuntimeMethodTestGpuProbeOptions withDeviceProfile(GpuRuntimeDeviceProfile deviceProfile) {
        return new GpuRuntimeMethodTestGpuProbeOptions(executionConfig, compileOptions, deviceProfile, cache, maxGlobalWorkItems);
    }

    public GpuRuntimeMethodTestGpuProbeOptions withCache(GpuRuntimeMethodTestGpuProbeCache cache) {
        return new GpuRuntimeMethodTestGpuProbeOptions(executionConfig, compileOptions, deviceProfile, cache, maxGlobalWorkItems);
    }

    public GpuRuntimeMethodTestGpuProbeOptions withPersistentCache(Path cacheDirectory) {
        return withCache(GpuRuntimeMethodTestGpuProbeCache.persistent(cacheDirectory));
    }

    public GpuRuntimeMethodTestGpuProbeOptions withPersistentCache(Path cacheDirectory, Duration maxEntryAge) {
        return withCache(GpuRuntimeMethodTestGpuProbeCache.persistent(cacheDirectory, maxEntryAge));
    }

    public GpuRuntimeMethodTestGpuProbeOptions withMaxGlobalWorkItems(long maxGlobalWorkItems) {
        return new GpuRuntimeMethodTestGpuProbeOptions(executionConfig, compileOptions, deviceProfile, cache, maxGlobalWorkItems);
    }
}
