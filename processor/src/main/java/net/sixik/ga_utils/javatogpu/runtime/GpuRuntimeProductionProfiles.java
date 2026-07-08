package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Locale;

/**
 * Shared classifier for runtime profiles that can affect production execution.
 */
public final class GpuRuntimeProductionProfiles {

    private GpuRuntimeProductionProfiles() {
    }

    public static boolean isProductionProfile(String optimizationProfile) {
        String normalizedProfile = optimizationProfile == null ? "off" : optimizationProfile.toLowerCase(Locale.ROOT);
        return normalizedProfile.equals("production")
                || normalizedProfile.equals("vendor-tuned")
                || normalizedProfile.equals("runtime-tuned")
                || normalizedProfile.equals("prod");
    }
}
