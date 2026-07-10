package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

/**
 * Cached correctness evidence and ranking contribution for one device/runner/compiler identity.
 */
public record GpuRuntimeDeviceSelfTestResult(
        GpuRuntimeDeviceSelfTestIdentity identity,
        GpuRuntimeDeviceSelfTestOutcome outcome,
        long durationNanos,
        int scoreAdjustment,
        boolean rejectDevice,
        GpuRuntimeDeviceSelfTestPerformance performance,
        List<String> diagnostics
) {

    public GpuRuntimeDeviceSelfTestResult(
            GpuRuntimeDeviceSelfTestIdentity identity,
            GpuRuntimeDeviceSelfTestOutcome outcome,
            long durationNanos,
            int scoreAdjustment,
            boolean rejectDevice,
            List<String> diagnostics
    ) {
        this(
                identity,
                outcome,
                durationNanos,
                scoreAdjustment,
                rejectDevice,
                GpuRuntimeDeviceSelfTestPerformance.unavailable(),
                diagnostics
        );
    }

    public GpuRuntimeDeviceSelfTestResult {
        identity = java.util.Objects.requireNonNull(identity, "identity");
        outcome = outcome == null ? GpuRuntimeDeviceSelfTestOutcome.SKIPPED : outcome;
        durationNanos = Math.max(0L, durationNanos);
        performance = performance == null
                ? GpuRuntimeDeviceSelfTestPerformance.unavailable()
                : performance;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static GpuRuntimeDeviceSelfTestResult passed(
            GpuRuntimeDeviceSelfTestIdentity identity,
            long durationNanos,
            int scoreAdjustment,
            List<String> diagnostics
    ) {
        return new GpuRuntimeDeviceSelfTestResult(
                identity,
                GpuRuntimeDeviceSelfTestOutcome.PASSED,
                durationNanos,
                scoreAdjustment,
                false,
                GpuRuntimeDeviceSelfTestPerformance.unavailable(),
                diagnostics
        );
    }

    public static GpuRuntimeDeviceSelfTestResult passed(
            GpuRuntimeDeviceSelfTestIdentity identity,
            long durationNanos,
            int scoreAdjustment,
            GpuRuntimeDeviceSelfTestPerformance performance,
            List<String> diagnostics
    ) {
        return new GpuRuntimeDeviceSelfTestResult(
                identity,
                GpuRuntimeDeviceSelfTestOutcome.PASSED,
                durationNanos,
                scoreAdjustment,
                false,
                performance,
                diagnostics
        );
    }

    public static GpuRuntimeDeviceSelfTestResult failed(
            GpuRuntimeDeviceSelfTestIdentity identity,
            long durationNanos,
            List<String> diagnostics
    ) {
        return new GpuRuntimeDeviceSelfTestResult(
                identity,
                GpuRuntimeDeviceSelfTestOutcome.FAILED,
                durationNanos,
                0,
                true,
                GpuRuntimeDeviceSelfTestPerformance.unavailable(),
                diagnostics
        );
    }

    public static GpuRuntimeDeviceSelfTestResult skipped(
            GpuRuntimeDeviceSelfTestIdentity identity,
            List<String> diagnostics
    ) {
        return new GpuRuntimeDeviceSelfTestResult(
                identity,
                GpuRuntimeDeviceSelfTestOutcome.SKIPPED,
                0L,
                0,
                false,
                GpuRuntimeDeviceSelfTestPerformance.unavailable(),
                diagnostics
        );
    }
}
