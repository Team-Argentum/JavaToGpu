package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Bounded compute and host/device transfer measurements attached to correctness evidence.
 */
public record GpuRuntimeDeviceSelfTestPerformance(
        String workloadProfile,
        String transferModel,
        boolean unifiedMemory,
        int computeScoreCap,
        int transferScoreCap,
        long computeOperations,
        GpuRuntimeDeviceSelfTestSampleSummary computeSamples,
        long transferBytes,
        GpuRuntimeDeviceSelfTestSampleSummary transferSamples
) {

    public static final int MAX_COMPUTE_SCORE_CAP = 3_000_000;
    public static final int MAX_TRANSFER_SCORE_CAP = 1_000_000;

    public GpuRuntimeDeviceSelfTestPerformance(
            long computeOperations,
            GpuRuntimeDeviceSelfTestSampleSummary computeSamples,
            long transferBytes,
            GpuRuntimeDeviceSelfTestSampleSummary transferSamples
    ) {
        this(
                "generic-v1",
                "generic-round-trip",
                false,
                MAX_COMPUTE_SCORE_CAP,
                MAX_TRANSFER_SCORE_CAP,
                computeOperations,
                computeSamples,
                transferBytes,
                transferSamples
        );
    }

    public GpuRuntimeDeviceSelfTestPerformance {
        workloadProfile = normalize(workloadProfile, "generic-v1");
        transferModel = normalize(transferModel, "generic-round-trip");
        computeScoreCap = Math.max(0, Math.min(MAX_COMPUTE_SCORE_CAP, computeScoreCap));
        transferScoreCap = Math.max(0, Math.min(MAX_TRANSFER_SCORE_CAP, transferScoreCap));
        computeOperations = Math.max(0L, computeOperations);
        computeSamples = computeSamples == null
                ? GpuRuntimeDeviceSelfTestSampleSummary.unavailable()
                : computeSamples;
        transferBytes = Math.max(0L, transferBytes);
        transferSamples = transferSamples == null
                ? GpuRuntimeDeviceSelfTestSampleSummary.unavailable()
                : transferSamples;
    }

    public static GpuRuntimeDeviceSelfTestPerformance unavailable() {
        return new GpuRuntimeDeviceSelfTestPerformance(
                "unavailable",
                "unavailable",
                false,
                0,
                0,
                0L,
                GpuRuntimeDeviceSelfTestSampleSummary.unavailable(),
                0L,
                GpuRuntimeDeviceSelfTestSampleSummary.unavailable()
        );
    }

    public boolean available() {
        return computeAvailable() && transferAvailable();
    }

    public boolean stable() {
        return computeStable() && transferStable();
    }

    public boolean computeAvailable() {
        return computeOperations > 0L && computeSamples.available();
    }

    public boolean transferAvailable() {
        return transferBytes > 0L && transferSamples.available();
    }

    public boolean computeStable() {
        return computeAvailable() && computeSamples.stable();
    }

    public boolean transferStable() {
        return transferAvailable() && transferSamples.stable();
    }

    public long computeOperationsPerSecond() {
        return ratePerSecond(computeOperations, computeSamples.medianNanos());
    }

    public long transferBytesPerSecond() {
        return ratePerSecond(transferBytes, transferSamples.medianNanos());
    }

    private static long ratePerSecond(long units, long nanos) {
        if (units <= 0L || nanos <= 0L) {
            return 0L;
        }
        if (units > Long.MAX_VALUE / 1_000_000_000L) {
            return Long.MAX_VALUE;
        }
        return units * 1_000_000_000L / nanos;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
