package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Coarse advisory severity for backend-neutral register-pressure estimates.
 */
public enum GpuRuntimeRegisterPressureLevel {
    UNAVAILABLE,
    LOW,
    MODERATE,
    HIGH,
    CRITICAL;

    public static GpuRuntimeRegisterPressureLevel fromUtilization(boolean available, int utilizationPermille) {
        if (!available) {
            return UNAVAILABLE;
        }
        if (utilizationPermille <= 500) {
            return LOW;
        }
        if (utilizationPermille <= 750) {
            return MODERATE;
        }
        if (utilizationPermille <= 1_000) {
            return HIGH;
        }
        return CRITICAL;
    }
}
