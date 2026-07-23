package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Portable status for a backend pipeline stage.
 */
public enum GpuBackendStageStatus {
    NOT_STARTED(false, false),
    SKIPPED(true, false),
    UNSUPPORTED(true, false),
    SUCCEEDED(true, true),
    FAILED(true, false);

    private final boolean terminal;
    private final boolean successful;

    GpuBackendStageStatus(boolean terminal, boolean successful) {
        this.terminal = terminal;
        this.successful = successful;
    }

    /**
     * Returns whether the stage reached a final state.
     */
    public boolean terminal() {
        return terminal;
    }

    /**
     * Returns whether the final state allows the pipeline to continue normally.
     */
    public boolean successful() {
        return successful;
    }
}
