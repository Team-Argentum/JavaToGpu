package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Stable public phase taxonomy for catchable GPU runtime failures.
 */
public enum GpuRuntimeFailurePhase {
    DEVICE_SELECTION,
    METHOD_VARIANT_SELECTION,
    BACKEND_INITIALIZATION,
    COMPILE_OPTIONS,
    ARGUMENT_MARSHALLING,
    CAPABILITY_VALIDATION,
    KERNEL_COMPILATION,
    KERNEL_EXECUTION,
    RUNTIME_SETUP
}
