package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Locale;
import java.util.Optional;

/**
 * Backend-neutral capability vocabulary used by runtime reports, device profiles, and future backend policies.
 */
public enum GpuRuntimeCapability {
    FP64("fp64"),
    IMAGES("images"),
    IMAGE_3D_WRITES("image-3d-writes"),
    SUBGROUPS("subgroups"),
    SHARED_CACHE("shared-cache"),
    UNIFIED_MEMORY("unified-memory"),
    DEVICE_CLASS("device-class"),
    DRIVER_VERSION("driver-version"),
    RUNTIME_VERSION("runtime-version"),
    COMPILER_VERSION("compiler-version"),
    COMPUTE_CAPABILITY("compute-capability"),
    GLOBAL_MEMORY("global-memory"),
    LOCAL_MEMORY("local-memory"),
    MAX_WORK_GROUP_SIZE("max-work-group-size"),
    COMPUTE_UNITS("compute-units"),
    PREFERRED_FLOAT_VECTOR_WIDTH("preferred-float-vector-width"),
    ATOMICS("atomics"),
    VECTOR_TYPES("vector-types"),
    ADDRESS_SPACE_GLOBAL("address-space-global"),
    ADDRESS_SPACE_LOCAL("address-space-local"),
    ADDRESS_SPACE_CONSTANT("address-space-constant"),
    STRUCT_ABI("struct-abi"),
    IMAGE_ABI("image-abi");

    private final String key;

    GpuRuntimeCapability(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<GpuRuntimeCapability> fromKey(String key) {
        String normalized = normalizeKey(key);
        for (GpuRuntimeCapability capability : values()) {
            if (capability.key.equals(normalized)) {
                return Optional.of(capability);
            }
        }
        return Optional.empty();
    }

    public static Optional<GpuRuntimeCapability> fromFeature(GpuRuntimeFeature feature) {
        if (feature == null) {
            return Optional.empty();
        }
        return switch (feature) {
            case DOUBLE_PRECISION -> Optional.of(FP64);
            case IMAGES -> Optional.of(IMAGES);
            case IMAGE3D_WRITES -> Optional.of(IMAGE_3D_WRITES);
            case SHARED_CACHE -> Optional.of(SHARED_CACHE);
        };
    }

    public Optional<GpuRuntimeFeature> legacyFeature() {
        return switch (this) {
            case FP64 -> Optional.of(GpuRuntimeFeature.DOUBLE_PRECISION);
            case IMAGES -> Optional.of(GpuRuntimeFeature.IMAGES);
            case IMAGE_3D_WRITES -> Optional.of(GpuRuntimeFeature.IMAGE3D_WRITES);
            case SHARED_CACHE -> Optional.of(GpuRuntimeFeature.SHARED_CACHE);
            default -> Optional.empty();
        };
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return key.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
