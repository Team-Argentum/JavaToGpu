package net.sixik.ga_utils.javatogpu.frontend.ir.passes;

public final class GpuIrPassException extends RuntimeException {
    public GpuIrPassException(String message) {
        super(message);
    }

    public GpuIrPassException(String message, Throwable cause) {
        super(message, cause);
    }
}
