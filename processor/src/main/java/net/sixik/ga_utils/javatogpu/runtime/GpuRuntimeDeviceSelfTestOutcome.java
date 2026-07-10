package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Stable correctness outcome produced by one backend-specific device self-test runner.
 */
public enum GpuRuntimeDeviceSelfTestOutcome {
    PASSED,
    FAILED,
    SKIPPED
}
