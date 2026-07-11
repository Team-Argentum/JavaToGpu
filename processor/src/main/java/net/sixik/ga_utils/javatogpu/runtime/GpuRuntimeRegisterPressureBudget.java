package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;

import java.util.Locale;

/**
 * Conservative value-register budget used before backend-specific occupancy data is available.
 */
public record GpuRuntimeRegisterPressureBudget(
        String modelVersion,
        int valueRegisterBudget,
        String source
) {

    public static final String MODEL_VERSION = "register-pressure-budget:v1";

    public GpuRuntimeRegisterPressureBudget {
        modelVersion = normalize(modelVersion, MODEL_VERSION);
        valueRegisterBudget = Math.max(1, valueRegisterBudget);
        source = normalize(source, "device-class-conservative-default");
    }

    public static GpuRuntimeRegisterPressureBudget forDevice(GpuRuntimeDeviceProfile profile) {
        GpuRuntimeDeviceProfile device = profile == null
                ? GpuRuntimeDeviceProfile.generic(null, "unknown")
                : profile;
        GpuDeviceClassTarget deviceClass = device.deviceClass();
        String vendor = device.vendor().toLowerCase(Locale.ROOT);
        if (deviceClass == GpuDeviceClassTarget.IGPU) {
            return new GpuRuntimeRegisterPressureBudget(MODEL_VERSION, 24, "igpu-conservative-v1");
        }
        if (deviceClass == GpuDeviceClassTarget.CPU) {
            return new GpuRuntimeRegisterPressureBudget(MODEL_VERSION, 16, "cpu-opencl-conservative-v1");
        }
        if (deviceClass == GpuDeviceClassTarget.DGPU) {
            if (vendor.contains("nvidia")) {
                return new GpuRuntimeRegisterPressureBudget(MODEL_VERSION, 64, "nvidia-dgpu-advisory-v1");
            }
            if (vendor.contains("amd") || vendor.contains("advanced micro devices")) {
                return new GpuRuntimeRegisterPressureBudget(MODEL_VERSION, 64, "amd-dgpu-advisory-v1");
            }
            if (vendor.contains("intel")) {
                return new GpuRuntimeRegisterPressureBudget(MODEL_VERSION, 48, "intel-dgpu-advisory-v1");
            }
            return new GpuRuntimeRegisterPressureBudget(MODEL_VERSION, 48, "unknown-dgpu-advisory-v1");
        }
        return new GpuRuntimeRegisterPressureBudget(MODEL_VERSION, 32, "unknown-device-conservative-v1");
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
