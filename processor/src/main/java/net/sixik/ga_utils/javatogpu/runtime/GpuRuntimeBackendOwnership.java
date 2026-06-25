package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Describes whether a selected runtime backend should be treated as owned by the selection/install flow or borrowed
 * from caller-managed state.
 */
public enum GpuRuntimeBackendOwnership {
    /**
     * The backend instance is caller-managed and should not be auto-closed by selection/install helpers.
     */
    BORROWED,

    /**
     * The backend instance is owned by the selection/install helper and may be auto-closed when the scope ends.
     */
    OWNED
}
