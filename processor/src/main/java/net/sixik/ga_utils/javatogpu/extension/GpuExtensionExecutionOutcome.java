package net.sixik.ga_utils.javatogpu.extension;

/**
 * Runtime outcome of one extension invocation.
 */
public enum GpuExtensionExecutionOutcome {
    SUCCEEDED,
    SKIPPED,
    FAILED_CONTINUED,
    FAILED_CLOSED
}
