package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Locale;

/**
 * Controls whether runtime device self-tests are skipped, opportunistic, or mandatory.
 */
public enum GpuRuntimeDeviceSelfTestMode {
    AUTO("auto"),
    DISABLED("disabled"),
    REQUIRED("required");

    private final String optionValue;

    GpuRuntimeDeviceSelfTestMode(String optionValue) {
        this.optionValue = optionValue;
    }

    public String optionValue() {
        return optionValue;
    }

    public static GpuRuntimeDeviceSelfTestMode parse(String value) {
        String normalized = value == null || value.isBlank()
                ? AUTO.optionValue
                : value.trim().toLowerCase(Locale.ROOT);
        for (GpuRuntimeDeviceSelfTestMode mode : values()) {
            if (mode.optionValue.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException(
                "Unsupported runtime device self-test mode '"
                        + value
                        + "'; expected auto, disabled, or required"
        );
    }
}
