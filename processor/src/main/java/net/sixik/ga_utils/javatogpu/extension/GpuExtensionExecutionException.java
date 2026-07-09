package net.sixik.ga_utils.javatogpu.extension;

/**
 * Fail-closed exception raised when an extension fails in a strict pipeline.
 */
public final class GpuExtensionExecutionException extends RuntimeException {

    private final GpuExtensionExecutionReport report;

    public GpuExtensionExecutionException(GpuExtensionExecutionReport report, RuntimeException cause) {
        super(report == null ? "GPU extension execution failed" : report.toLine(), cause);
        this.report = report;
    }

    public GpuExtensionExecutionReport report() {
        return report;
    }
}
