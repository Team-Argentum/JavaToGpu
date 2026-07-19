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

    public boolean terminal() {
        return terminal;
    }

    public boolean successful() {
        return successful;
    }
}
