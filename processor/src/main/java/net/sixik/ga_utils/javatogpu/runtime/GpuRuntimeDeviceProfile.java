package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        boolean supportsSubgroups,
        String platformName,
        String platformVersion
) {

    private static final Pattern CUDA_RUNTIME_VERSION = Pattern.compile(
            "\\bCUDA\\s+([0-9]+(?:\\.[0-9]+)*|unknown)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CUDA_COMPUTE_CAPABILITY = Pattern.compile(
            "\\bcompute\\s+capability\\s+([0-9]+(?:\\.[0-9]+)*|unknown)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public GpuRuntimeDeviceProfile(
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
        this(
                backendTarget,
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
                supportsSubgroups,
                "unknown",
                "unknown"
        );
    }

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
        platformName = normalize(platformName);
        platformVersion = normalize(platformVersion);
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
                false,
                "unknown",
                "unknown"
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
        return openCl(
                backendName,
                deviceId,
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                "unknown",
                "unknown",
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

    public static GpuRuntimeDeviceProfile openCl(
            String backendName,
            String deviceId,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String apiVersionText,
            String platformName,
            String platformVersion,
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
                supportsSubgroups,
                platformName,
                platformVersion
        );
    }

    public static GpuRuntimeDeviceProfile cuda(
            String deviceId,
            String deviceLabel,
            String vendor,
            String driverVersion,
            String apiVersionText,
            GpuDeviceClassTarget deviceClass,
            long globalMemoryBytes,
            String platformName,
            String platformVersion
    ) {
        return new GpuRuntimeDeviceProfile(
                GpuBackendTarget.CUDA,
                "CUDA",
                deviceId,
                deviceLabel,
                vendor,
                driverVersion,
                apiVersionText,
                deviceClass,
                -1L,
                globalMemoryBytes,
                -1L,
                -1L,
                -1L,
                false,
                false,
                false,
                false,
                platformName,
                platformVersion
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
                supportsSubgroups,
                platformName,
                platformVersion
        );
    }

    public String cudaRuntimeVersion() {
        if (backendTarget != GpuBackendTarget.CUDA) {
            return "not-cuda";
        }
        String fromApiText = firstMatch(CUDA_RUNTIME_VERSION, apiVersionText);
        return "unknown".equals(fromApiText)
                ? firstMatch(CUDA_RUNTIME_VERSION, platformVersion)
                : fromApiText;
    }

    public String cudaComputeCapability() {
        if (backendTarget != GpuBackendTarget.CUDA) {
            return "not-cuda";
        }
        return firstMatch(CUDA_COMPUTE_CAPABILITY, apiVersionText);
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

    private static String firstMatch(Pattern pattern, String value) {
        String text = normalize(value);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : "unknown";
    }
}
