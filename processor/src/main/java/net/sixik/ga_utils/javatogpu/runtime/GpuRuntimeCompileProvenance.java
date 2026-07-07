package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.List;

/**
 * Records where a runtime compile artifact came from and which options shaped it.
 */
public record GpuRuntimeCompileProvenance(
        GpuBackendTarget backendTarget,
        String backendName,
        String deviceLabel,
        String vendor,
        String driverVersion,
        String apiVersionText,
        long computeUnits,
        long localMemoryBytes,
        long maxWorkGroupSize,
        long preferredVectorWidthFloat,
        boolean supportsDoublePrecision,
        boolean supportsImages,
        boolean supportsSubgroups,
        List<String> compileArgs,
        String optimizationProfile,
        String fallbackDecision
) {

    public static final String NO_FALLBACK = "none";

    public GpuRuntimeCompileProvenance {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        backendName = normalize(backendName);
        deviceLabel = normalize(deviceLabel);
        vendor = normalize(vendor);
        driverVersion = normalize(driverVersion);
        apiVersionText = normalize(apiVersionText);
        computeUnits = normalizeLong(computeUnits);
        localMemoryBytes = normalizeLong(localMemoryBytes);
        maxWorkGroupSize = normalizeLong(maxWorkGroupSize);
        preferredVectorWidthFloat = normalizeLong(preferredVectorWidthFloat);
        compileArgs = compileArgs == null ? List.of() : List.copyOf(compileArgs);
        optimizationProfile = optimizationProfile == null || optimizationProfile.isBlank()
                ? "off"
                : optimizationProfile;
        fallbackDecision = fallbackDecision == null || fallbackDecision.isBlank()
                ? NO_FALLBACK
                : fallbackDecision;
    }

    public static GpuRuntimeCompileProvenance from(GpuRuntimeCompileRequest compileRequest) {
        if (compileRequest == null) {
            return unknown();
        }
        GpuRuntimeCompileOptions options = compileRequest.options();
        GpuRuntimeDeviceProfile deviceProfile = compileRequest.deviceProfile();
        return new GpuRuntimeCompileProvenance(
                options.backendTarget(),
                deviceProfile.backendName(),
                deviceProfile.deviceLabel(),
                deviceProfile.vendor(),
                deviceProfile.driverVersion(),
                deviceProfile.apiVersionText(),
                deviceProfile.computeUnits(),
                deviceProfile.localMemoryBytes(),
                deviceProfile.maxWorkGroupSize(),
                deviceProfile.preferredVectorWidthFloat(),
                deviceProfile.supportsDoublePrecision(),
                deviceProfile.supportsImages(),
                deviceProfile.supportsSubgroups(),
                options.compileArgs(),
                options.optimizationProfile(),
                NO_FALLBACK
        );
    }

    public static GpuRuntimeCompileProvenance unknown() {
        return new GpuRuntimeCompileProvenance(
                GpuBackendTarget.UNKNOWN,
                "unknown",
                "unknown",
                "unknown",
                "unknown",
                "unknown",
                -1L,
                -1L,
                -1L,
                -1L,
                false,
                false,
                false,
                List.of(),
                "off",
                NO_FALLBACK
        );
    }

    public GpuRuntimeCompileProvenance withFallbackDecision(String fallbackDecision) {
        return new GpuRuntimeCompileProvenance(
                backendTarget,
                backendName,
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                computeUnits,
                localMemoryBytes,
                maxWorkGroupSize,
                preferredVectorWidthFloat,
                supportsDoublePrecision,
                supportsImages,
                supportsSubgroups,
                compileArgs,
                optimizationProfile,
                fallbackDecision
        );
    }

    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("backendTarget=").append(backendTarget).append('\n');
        builder.append("backendName=").append(backendName).append('\n');
        builder.append("deviceLabel=").append(deviceLabel).append('\n');
        builder.append("vendor=").append(vendor).append('\n');
        builder.append("driverVersion=").append(driverVersion).append('\n');
        builder.append("apiVersionText=").append(apiVersionText).append('\n');
        builder.append("computeUnits=").append(computeUnits).append('\n');
        builder.append("localMemoryBytes=").append(localMemoryBytes).append('\n');
        builder.append("maxWorkGroupSize=").append(maxWorkGroupSize).append('\n');
        builder.append("preferredVectorWidthFloat=").append(preferredVectorWidthFloat).append('\n');
        builder.append("supportsDoublePrecision=").append(supportsDoublePrecision).append('\n');
        builder.append("supportsImages=").append(supportsImages).append('\n');
        builder.append("supportsSubgroups=").append(supportsSubgroups).append('\n');
        builder.append("compileArg.count=").append(compileArgs.size()).append('\n');
        for (int index = 0; index < compileArgs.size(); index++) {
            builder.append("compileArg.").append(index).append('=').append(compileArgs.get(index)).append('\n');
        }
        builder.append("optimizationProfile=").append(optimizationProfile).append('\n');
        builder.append("fallbackDecision=").append(fallbackDecision).append('\n');
        return builder.toString();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static long normalizeLong(long value) {
        return value < 0L ? -1L : value;
    }
}
