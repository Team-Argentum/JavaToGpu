package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

public record GpuRuntimeDeviceProfile(
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
        boolean supportsSubgroups
) {

    public GpuRuntimeDeviceProfile(
            GpuBackendTarget backendTarget,
            String backendName,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String apiVersionText
    ) {
        this(
                backendTarget,
                backendName,
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                -1L,
                -1L,
                -1L,
                -1L,
                false,
                false,
                false
        );
    }

    public GpuRuntimeDeviceProfile {
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
    }

    public static GpuRuntimeDeviceProfile generic(GpuBackendTarget backendTarget, String backendName) {
        return new GpuRuntimeDeviceProfile(
                backendTarget,
                backendName,
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
                false
        );
    }

    public static GpuRuntimeDeviceProfile openCl(
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
            boolean supportsSubgroups
    ) {
        return new GpuRuntimeDeviceProfile(
                GpuBackendTarget.OPENCL,
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
                supportsSubgroups
        );
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static long normalizeLong(long value) {
        return value < 0L ? -1L : value;
    }
}
