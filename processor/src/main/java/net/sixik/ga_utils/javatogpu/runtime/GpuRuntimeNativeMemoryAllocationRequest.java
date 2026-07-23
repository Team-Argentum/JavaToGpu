package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backend-neutral request for native host memory used by runtime binders and descriptor encoders.
 */
public record GpuRuntimeNativeMemoryAllocationRequest(
        GpuBackendTarget backendTarget,
        String purpose,
        int byteSize,
        int alignmentBytes,
        boolean zeroed
) {

    public GpuRuntimeNativeMemoryAllocationRequest {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        purpose = normalize(purpose, "runtime-native-memory");
        byteSize = Math.max(1, byteSize);
        alignmentBytes = Math.max(1, alignmentBytes);
    }

    public static GpuRuntimeNativeMemoryAllocationRequest cudaDescriptor(
            String purpose,
            int byteSize,
            boolean zeroed
    ) {
        return new GpuRuntimeNativeMemoryAllocationRequest(
                GpuBackendTarget.CUDA,
                purpose,
                byteSize,
                Long.BYTES,
                zeroed
        );
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.nativeMemory.request"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".backend", backendTarget.name());
        fields.put(normalizedPrefix + ".purpose", purpose);
        fields.put(normalizedPrefix + ".byteSize", Integer.toString(byteSize));
        fields.put(normalizedPrefix + ".alignmentBytes", Integer.toString(alignmentBytes));
        fields.put(normalizedPrefix + ".zeroed", Boolean.toString(zeroed));
        return Collections.unmodifiableMap(fields);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
