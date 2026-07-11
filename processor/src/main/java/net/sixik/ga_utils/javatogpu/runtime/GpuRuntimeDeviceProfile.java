package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;

public record GpuRuntimeDeviceProfile(
        GpuBackendTarget backendTarget,
        String backendName,
        String deviceId,
        String deviceLabel,
        String vendor,
        String driverVersion,
        String apiVersionText,
        GpuDeviceClassTarget deviceClass,
        long computeUnits,
        long globalMemoryBytes,
        long localMemoryBytes,
        long maxWorkGroupSize,
        long preferredVectorWidthFloat,
        boolean unifiedMemory,
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
                "unknown",
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                GpuDeviceClassTarget.UNKNOWN,
                -1L,
                -1L,
                -1L,
                -1L,
                -1L,
                false,
                false,
                false,
                false
        );
    }

    public GpuRuntimeDeviceProfile(
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
        this(
                backendTarget,
                backendName,
                "unknown",
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                GpuDeviceClassTarget.UNKNOWN,
                computeUnits,
                -1L,
                localMemoryBytes,
                maxWorkGroupSize,
                preferredVectorWidthFloat,
                false,
                supportsDoublePrecision,
                supportsImages,
                supportsSubgroups
        );
    }

    public GpuRuntimeDeviceProfile {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        backendName = normalize(backendName);
        deviceId = normalize(deviceId);
        deviceLabel = normalize(deviceLabel);
        vendor = normalize(vendor);
        driverVersion = normalize(driverVersion);
        apiVersionText = normalize(apiVersionText);
        deviceClass = normalizeDeviceClass(deviceClass);
        computeUnits = normalizeLong(computeUnits);
        globalMemoryBytes = normalizeLong(globalMemoryBytes);
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
                "unknown",
                GpuDeviceClassTarget.UNKNOWN,
                -1L,
                -1L,
                -1L,
                -1L,
                -1L,
                false,
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
                "unknown",
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                GpuDeviceClassTarget.UNKNOWN,
                computeUnits,
                -1L,
                localMemoryBytes,
                maxWorkGroupSize,
                preferredVectorWidthFloat,
                false,
                supportsDoublePrecision,
                supportsImages,
                supportsSubgroups
        );
    }

    public static GpuRuntimeDeviceProfile openCl(
            String backendName,
            String deviceId,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String apiVersionText,
            GpuDeviceClassTarget deviceClass,
            long computeUnits,
            long globalMemoryBytes,
            long localMemoryBytes,
            long maxWorkGroupSize,
            long preferredVectorWidthFloat,
            boolean unifiedMemory,
            boolean supportsDoublePrecision,
            boolean supportsImages,
            boolean supportsSubgroups
    ) {
        return new GpuRuntimeDeviceProfile(
                GpuBackendTarget.OPENCL,
                backendName,
                deviceId,
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                deviceClass,
                computeUnits,
                globalMemoryBytes,
                localMemoryBytes,
                maxWorkGroupSize,
                preferredVectorWidthFloat,
                unifiedMemory,
                supportsDoublePrecision,
                supportsImages,
                supportsSubgroups
        );
    }

    public GpuRuntimeDeviceProfile withBackendName(String value) {
        return new GpuRuntimeDeviceProfile(
                backendTarget,
                value,
                deviceId,
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                deviceClass,
                computeUnits,
                globalMemoryBytes,
                localMemoryBytes,
                maxWorkGroupSize,
                preferredVectorWidthFloat,
                unifiedMemory,
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

    private static GpuDeviceClassTarget normalizeDeviceClass(GpuDeviceClassTarget value) {
        return value == null || value == GpuDeviceClassTarget.ANY
                ? GpuDeviceClassTarget.UNKNOWN
                : value;
    }
}
