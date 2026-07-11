package net.sixik.ga_utils.javatogpu.extension;

/**
 * Failure handling applied to one extension invocation.
 */
public enum GpuExtensionFailurePolicy {
    CONTINUE,
    STOP_PIPELINE,
    THROW
}
