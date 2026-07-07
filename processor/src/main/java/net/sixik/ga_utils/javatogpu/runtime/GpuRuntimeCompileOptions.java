package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.List;

public record GpuRuntimeCompileOptions(
        GpuBackendTarget backendTarget,
        List<String> compileArgs,
        String optimizationProfile
) {

    public GpuRuntimeCompileOptions {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        compileArgs = compileArgs == null ? List.of() : List.copyOf(compileArgs);
        optimizationProfile = optimizationProfile == null || optimizationProfile.isBlank()
                ? "off"
                : optimizationProfile;
    }

    public static GpuRuntimeCompileOptions defaults(GpuBackendTarget backendTarget) {
        return new GpuRuntimeCompileOptions(backendTarget, List.of(), "off");
    }
}
