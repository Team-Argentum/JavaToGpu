package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.api.GpuVendorTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;

import java.util.List;
import java.util.Locale;

/**
 * Normalized backend-neutral compatibility metadata for one IrGpu method.
 */
public record IrGpuMethodDeviceConstraint(
        String methodName,
        String emittedName,
        List<GpuBackendTarget> supportedBackends,
        List<GpuVendorTarget> supportedVendors,
        List<GpuDeviceClassTarget> supportedDeviceClasses,
        List<String> requiredFeatures,
        String source
) {

    public IrGpuMethodDeviceConstraint {
        methodName = normalize(methodName, "unknown");
        emittedName = normalize(emittedName, methodName);
        supportedBackends = normalizeBackends(supportedBackends);
        supportedVendors = normalizeVendors(supportedVendors);
        supportedDeviceClasses = normalizeDeviceClasses(supportedDeviceClasses);
        requiredFeatures = requiredFeatures == null
                ? List.of()
                : requiredFeatures.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
        source = normalize(source, "default-unconstrained");
    }

    public boolean active() {
        return !supportedBackends.isEmpty()
                || !supportedVendors.isEmpty()
                || !supportedDeviceClasses.isEmpty()
                || !requiredFeatures.isEmpty();
    }

    public boolean supports(GpuRuntimeDeviceProfile profile) {
        return incompatibility(profile).isBlank();
    }

    public String incompatibility(GpuRuntimeDeviceProfile profile) {
        if (profile == null) {
            return "device profile is missing";
        }
        if (!supportedBackends.isEmpty() && !supportedBackends.contains(profile.backendTarget())) {
            return "backend " + profile.backendTarget() + " is not supported";
        }
        if (!supportedVendors.isEmpty() && supportedVendors.stream().noneMatch(vendor -> vendorMatches(vendor, profile.vendor()))) {
            return "vendor " + profile.vendor() + " is not supported";
        }
        if (!supportedDeviceClasses.isEmpty() && !supportedDeviceClasses.contains(profile.deviceClass())) {
            return "device class " + profile.deviceClass() + " is not supported";
        }
        for (String feature : requiredFeatures) {
            if (!supportsFeature(profile, feature)) {
                return "required feature " + feature + " is unavailable";
            }
        }
        return "";
    }

    private static boolean supportsFeature(GpuRuntimeDeviceProfile profile, String feature) {
        return switch (feature) {
            case "fp64", "double", "double-precision" -> profile.supportsDoublePrecision();
            case "images", "image" -> profile.supportsImages();
            case "image-3d-writes", "image3d-writes", "image3d-write" -> profile.supportsImage3dWrites();
            case "atomics", "atomic", "int32-atomics", "global-int32-atomics" -> profile.supportsAtomics();
            case "subgroups", "subgroup" -> profile.supportsSubgroups();
            default -> false;
        };
    }

    private static boolean vendorMatches(GpuVendorTarget vendor, String candidate) {
        if (vendor == GpuVendorTarget.ANY) {
            return true;
        }
        String normalized = candidate == null ? "" : candidate.toLowerCase(Locale.ROOT);
        return switch (vendor) {
            case NVIDIA -> normalized.contains("nvidia");
            case AMD -> normalized.contains("amd") || normalized.contains("advanced micro devices");
            case INTEL -> normalized.contains("intel");
            case APPLE -> normalized.contains("apple");
            case UNKNOWN -> normalized.isBlank() || normalized.equals("unknown");
            case ANY -> true;
        };
    }

    private static List<GpuBackendTarget> normalizeBackends(List<GpuBackendTarget> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && value != GpuBackendTarget.UNKNOWN)
                .distinct()
                .sorted()
                .toList();
    }

    private static List<GpuVendorTarget> normalizeVendors(List<GpuVendorTarget> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && value != GpuVendorTarget.ANY && value != GpuVendorTarget.UNKNOWN)
                .distinct()
                .sorted()
                .toList();
    }

    private static List<GpuDeviceClassTarget> normalizeDeviceClasses(List<GpuDeviceClassTarget> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && value != GpuDeviceClassTarget.ANY && value != GpuDeviceClassTarget.UNKNOWN)
                .distinct()
                .sorted()
                .toList();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
