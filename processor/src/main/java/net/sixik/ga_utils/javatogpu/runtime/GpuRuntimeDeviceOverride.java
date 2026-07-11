package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;

import java.util.Locale;

/**
 * Explicit backend-neutral device selector applied before native runtime context creation.
 */
public record GpuRuntimeDeviceOverride(
        String deviceId,
        String vendor,
        String deviceLabel,
        GpuDeviceClassTarget deviceClass
) {

    public GpuRuntimeDeviceOverride {
        deviceId = normalize(deviceId);
        vendor = normalize(vendor);
        deviceLabel = normalize(deviceLabel);
        deviceClass = deviceClass == null ? GpuDeviceClassTarget.ANY : deviceClass;
    }

    public static GpuRuntimeDeviceOverride automatic() {
        return new GpuRuntimeDeviceOverride("", "", "", GpuDeviceClassTarget.ANY);
    }

    public static GpuRuntimeDeviceOverride byDeviceId(String deviceId) {
        return new GpuRuntimeDeviceOverride(deviceId, "", "", GpuDeviceClassTarget.ANY);
    }

    public static GpuRuntimeDeviceOverride byVendor(String vendor) {
        return new GpuRuntimeDeviceOverride("", vendor, "", GpuDeviceClassTarget.ANY);
    }

    public static GpuRuntimeDeviceOverride byDeviceLabel(String deviceLabel) {
        return new GpuRuntimeDeviceOverride("", "", deviceLabel, GpuDeviceClassTarget.ANY);
    }

    public static GpuRuntimeDeviceOverride byDeviceClass(GpuDeviceClassTarget deviceClass) {
        return new GpuRuntimeDeviceOverride("", "", "", deviceClass);
    }

    public boolean active() {
        return !deviceId.isBlank()
                || !vendor.isBlank()
                || !deviceLabel.isBlank()
                || deviceClass != GpuDeviceClassTarget.ANY;
    }

    public boolean matches(GpuRuntimeDeviceProfile profile) {
        if (profile == null) {
            return false;
        }
        return matchesExact(deviceId, profile.deviceId())
                && matchesContains(vendor, profile.vendor())
                && matchesContains(deviceLabel, profile.deviceLabel())
                && (deviceClass == GpuDeviceClassTarget.ANY || deviceClass == profile.deviceClass());
    }

    public String describe() {
        if (!active()) {
            return "automatic";
        }
        return "deviceId=" + valueOrAny(deviceId)
                + ", vendor=" + valueOrAny(vendor)
                + ", deviceLabel=" + valueOrAny(deviceLabel)
                + ", deviceClass=" + deviceClass.name().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesExact(String selector, String candidate) {
        return selector.isBlank() || selector.equalsIgnoreCase(candidate == null ? "" : candidate.trim());
    }

    private static boolean matchesContains(String selector, String candidate) {
        return selector.isBlank()
                || (candidate != null && candidate.toLowerCase(Locale.ROOT).contains(selector.toLowerCase(Locale.ROOT)));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String valueOrAny(String value) {
        return value.isBlank() ? "any" : value;
    }
}
