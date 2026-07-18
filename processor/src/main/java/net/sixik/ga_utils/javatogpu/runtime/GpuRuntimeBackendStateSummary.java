package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backend-neutral runtime-state counters for lifecycle events.
 *
 * <p>The public counter vocabulary remains split across {@code runtime.backend.cache.*},
 * {@code runtime.backend.compile.*}, {@code runtime.backend.invocation.*}, and resource namespaces. This summary keeps
 * that stable field shape while giving backend adapters one typed object to populate instead of assembling maps by hand.</p>
 *
 * @param cacheMode stable backend cache mode label, for example {@code INSTANCE}, {@code SHARED}, or {@code unknown}
 * @param compiledKernelCount number of compiled kernels currently tracked by the backend cache
 * @param nativeBufferCount number of native/device buffers currently tracked by the backend
 * @param invocationCount number of kernel invocations observed by the backend scope
 * @param compileCount number of backend compilation attempts observed by the backend scope
 * @param compileCacheHitCount number of backend compilation cache hits
 * @param sessionCreationCount number of native backend sessions created by the scope
 * @param deviceBufferCreationCount number of device-buffer creation operations observed by the backend
 */
public record GpuRuntimeBackendStateSummary(
        String cacheMode,
        long compiledKernelCount,
        long nativeBufferCount,
        long invocationCount,
        long compileCount,
        long compileCacheHitCount,
        long sessionCreationCount,
        long deviceBufferCreationCount
) {

    public GpuRuntimeBackendStateSummary {
        cacheMode = cacheMode == null || cacheMode.isBlank() ? "unknown" : cacheMode.trim();
        compiledKernelCount = normalizeCounter(compiledKernelCount);
        nativeBufferCount = normalizeCounter(nativeBufferCount);
        invocationCount = normalizeCounter(invocationCount);
        compileCount = normalizeCounter(compileCount);
        compileCacheHitCount = normalizeCounter(compileCacheHitCount);
        sessionCreationCount = normalizeCounter(sessionCreationCount);
        deviceBufferCreationCount = normalizeCounter(deviceBufferCreationCount);
    }

    public static GpuRuntimeBackendStateSummary empty() {
        return new GpuRuntimeBackendStateSummary("unknown", 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    /**
     * Renders stable lifecycle fields for logs, journals, and diagnostic artifacts.
     *
     * <p>The supplied prefix only controls the marker field such as {@code runtime.backend.state.present}; the counters
     * intentionally remain in their public namespaces like {@code runtime.backend.cache.*} and
     * {@code runtime.backend.compile.*} for compatibility with existing tooling.</p>
     *
     * @param statePrefix marker-field prefix, or {@code runtime.backend.state} when blank
     * @return immutable portable field map
     */
    public Map<String, String> artifactFields(String statePrefix) {
        String normalizedStatePrefix = statePrefix == null || statePrefix.isBlank()
                ? "runtime.backend.state"
                : statePrefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        GpuRuntimeArtifactProperties.putPortable(fields, normalizedStatePrefix, "present", true);
        GpuRuntimeArtifactProperties.putPortable(fields, "runtime.backend.cache", "mode", cacheMode);
        GpuRuntimeArtifactProperties.putPortable(
                fields,
                "runtime.backend.cache",
                "compiledKernel.count",
                compiledKernelCount
        );
        GpuRuntimeArtifactProperties.putPortable(
                fields,
                "runtime.backend.cache",
                "compileHit.count",
                compileCacheHitCount
        );
        GpuRuntimeArtifactProperties.putPortable(fields, "runtime.backend.compile", "count", compileCount);
        GpuRuntimeArtifactProperties.putPortable(fields, "runtime.backend.invocation", "count", invocationCount);
        GpuRuntimeArtifactProperties.putPortable(
                fields,
                "runtime.backend.session",
                "creation.count",
                sessionCreationCount
        );
        GpuRuntimeArtifactProperties.putPortable(fields, "runtime.backend.buffer", "native.count", nativeBufferCount);
        GpuRuntimeArtifactProperties.putPortable(
                fields,
                "runtime.backend.buffer",
                "device.creation.count",
                deviceBufferCreationCount
        );
        return Collections.unmodifiableMap(fields);
    }

    private static long normalizeCounter(long value) {
        return value < 0L ? 0L : value;
    }
}
