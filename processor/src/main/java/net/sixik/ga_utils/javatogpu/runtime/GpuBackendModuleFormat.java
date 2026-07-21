package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Locale;
import java.util.Set;

/**
 * Canonical backend module formats understood by the runtime SPI.
 */
public enum GpuBackendModuleFormat {
    UNKNOWN("unknown", false, false, Set.of(GpuBackendTarget.UNKNOWN)),
    OPENCL_C("opencl-c", true, false, Set.of(GpuBackendTarget.OPENCL)),
    CUDA_C("cuda-c", true, false, Set.of(GpuBackendTarget.CUDA)),
    PTX("ptx", true, true, Set.of(GpuBackendTarget.CUDA)),
    CUBIN("cubin", false, true, Set.of(GpuBackendTarget.CUDA)),
    FATBIN("fatbin", false, true, Set.of(GpuBackendTarget.CUDA)),
    SPIR_V("spir-v", false, true, Set.of(GpuBackendTarget.VULKAN, GpuBackendTarget.OPENCL)),
    METAL_SHADING_LANGUAGE("metal-shading-language", true, false, Set.of(GpuBackendTarget.METAL)),
    NATIVE_BINARY("native-binary", false, true, Set.of());

    private final String key;
    private final boolean sourceLike;
    private final boolean binaryLike;
    private final Set<GpuBackendTarget> defaultTargets;

    GpuBackendModuleFormat(
            String key,
            boolean sourceLike,
            boolean binaryLike,
            Set<GpuBackendTarget> defaultTargets
    ) {
        this.key = key;
        this.sourceLike = sourceLike;
        this.binaryLike = binaryLike;
        this.defaultTargets = Set.copyOf(defaultTargets);
    }

    public String key() {
        return key;
    }

    public boolean sourceLike() {
        return sourceLike;
    }

    public boolean binaryLike() {
        return binaryLike;
    }

    public Set<GpuBackendTarget> defaultTargets() {
        return defaultTargets;
    }

    public boolean hasDefaultTarget(GpuBackendTarget backendTarget) {
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        return defaultTargets.isEmpty() || defaultTargets.contains(target);
    }

    public static GpuBackendModuleFormat fromKey(String key) {
        String normalized = normalizeKey(key);
        for (GpuBackendModuleFormat format : values()) {
            if (format.key.equals(normalized)) {
                return format;
            }
        }
        return UNKNOWN;
    }

    public static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return UNKNOWN.key;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if ("spirv".equals(normalized) || "spv".equals(normalized)) {
            return SPIR_V.key;
        }
        if ("opencl".equals(normalized) || "openclc".equals(normalized) || "opencl-c-source".equals(normalized)) {
            return OPENCL_C.key;
        }
        if ("cuda".equals(normalized) || "cudac".equals(normalized) || "cuda-c-source".equals(normalized)) {
            return CUDA_C.key;
        }
        if ("cuda-cubin".equals(normalized) || "nvidia-cubin".equals(normalized)) {
            return CUBIN.key;
        }
        if ("cuda-fatbin".equals(normalized) || "nvidia-fatbin".equals(normalized)) {
            return FATBIN.key;
        }
        if ("metal".equals(normalized) || "metal-shading-language-source".equals(normalized)) {
            return METAL_SHADING_LANGUAGE.key;
        }
        if ("binary".equals(normalized) || "native".equals(normalized)) {
            return NATIVE_BINARY.key;
        }
        return normalized;
    }
}
