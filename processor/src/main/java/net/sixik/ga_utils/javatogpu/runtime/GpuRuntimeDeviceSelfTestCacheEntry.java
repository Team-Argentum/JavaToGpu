package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Indicates whether self-test evidence was reused or produced during the current request.
 */
public record GpuRuntimeDeviceSelfTestCacheEntry(
        GpuRuntimeDeviceSelfTestResult result,
        boolean cacheHit
) {

    public GpuRuntimeDeviceSelfTestCacheEntry {
        result = java.util.Objects.requireNonNull(result, "result");
    }
}
