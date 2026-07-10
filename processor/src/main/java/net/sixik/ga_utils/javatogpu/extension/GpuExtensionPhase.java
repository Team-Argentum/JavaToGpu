package net.sixik.ga_utils.javatogpu.extension;

/**
 * Stable pipeline phases where third-party extensions may participate.
 */
public enum GpuExtensionPhase {
    IR_VALIDATION,
    DIAGNOSTICS,
    RUNTIME_IR_OPTIMIZATION,
    BACKEND_LOWERING,
    BACKEND_COMPILER_FEEDBACK,
    DEVICE_SELECTION,
    RUNTIME_EQUIVALENCE,
    PRODUCTION_PROMOTION,
    ARTIFACT_EMISSION
}
