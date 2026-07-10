package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;

/**
 * Backend-neutral startup workload budget selected from device class and memory topology.
 */
public record GpuRuntimeDeviceSelfTestProfile(
        String profileId,
        String transferModel,
        int computeWorkItems,
        int computeIterations,
        int transferBytesOneWay,
        int sampleCount,
        int maxNoisePermille,
        int computeScoreCap,
        int transferScoreCap
) {

    public GpuRuntimeDeviceSelfTestProfile {
        profileId = normalize(profileId, "unknown-conservative-v1");
        transferModel = normalize(transferModel, "unknown-memory");
        computeWorkItems = Math.max(1, computeWorkItems);
        computeIterations = Math.max(1, computeIterations);
        transferBytesOneWay = Math.max(Integer.BYTES, transferBytesOneWay);
        sampleCount = Math.max(3, sampleCount);
        maxNoisePermille = Math.max(0, maxNoisePermille);
        computeScoreCap = Math.max(0, Math.min(
                GpuRuntimeDeviceSelfTestPerformance.MAX_COMPUTE_SCORE_CAP,
                computeScoreCap
        ));
        transferScoreCap = Math.max(0, Math.min(
                GpuRuntimeDeviceSelfTestPerformance.MAX_TRANSFER_SCORE_CAP,
                transferScoreCap
        ));
    }

    public static GpuRuntimeDeviceSelfTestProfile forDevice(GpuRuntimeDeviceProfile profile) {
        GpuRuntimeDeviceProfile value = java.util.Objects.requireNonNull(profile, "profile");
        GpuDeviceClassTarget deviceClass = value.deviceClass();
        return switch (deviceClass) {
            case DGPU -> new GpuRuntimeDeviceSelfTestProfile(
                    "dgpu-balanced-v1",
                    "dedicated-memory-round-trip",
                    262_144,
                    128,
                    4 * 1024 * 1024,
                    5,
                    300,
                    3_000_000,
                    1_000_000
            );
            case IGPU -> new GpuRuntimeDeviceSelfTestProfile(
                    value.unifiedMemory() ? "igpu-unified-v1" : "igpu-conservative-v1",
                    value.unifiedMemory() ? "unified-memory-round-trip" : "integrated-memory-round-trip",
                    131_072,
                    64,
                    2 * 1024 * 1024,
                    5,
                    400,
                    2_000_000,
                    250_000
            );
            case CPU -> new GpuRuntimeDeviceSelfTestProfile(
                    "cpu-opencl-conservative-v1",
                    "host-memory-round-trip",
                    32_768,
                    32,
                    1024 * 1024,
                    5,
                    500,
                    500_000,
                    100_000
            );
            case UNKNOWN, ANY -> new GpuRuntimeDeviceSelfTestProfile(
                    "unknown-conservative-v1",
                    value.unifiedMemory() ? "unified-memory-round-trip" : "unknown-memory-round-trip",
                    65_536,
                    64,
                    2 * 1024 * 1024,
                    5,
                    400,
                    1_000_000,
                    250_000
            );
        };
    }

    public long computeOperations(int operationsPerIteration) {
        return (long) computeWorkItems * computeIterations * Math.max(1, operationsPerIteration);
    }

    public long transferBytesRoundTrip() {
        return (long) transferBytesOneWay * 2L;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
