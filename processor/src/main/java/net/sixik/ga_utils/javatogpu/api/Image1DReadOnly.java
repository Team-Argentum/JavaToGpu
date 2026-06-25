package net.sixik.ga_utils.javatogpu.api;

import org.lwjgl.opencl.CL10;

/**
 * Java-side marker for an OpenCL {@code read_only image1d_t} kernel parameter.
 */
public final class Image1DReadOnly implements AutoCloseable {

    private final long handle;
    private final int width;
    private final boolean owned;
    private boolean closed;

    public Image1DReadOnly() {
        this(0L, 0, false);
    }

    public Image1DReadOnly(long handle, int width) {
        this(handle, width, false);
    }

    private Image1DReadOnly(long handle, int width, boolean owned) {
        this.handle = handle;
        this.width = width;
        this.owned = owned;
    }

    public static Image1DReadOnly borrowed(long handle, int width) {
        return new Image1DReadOnly(handle, width, false);
    }

    public static Image1DReadOnly owned(long handle, int width) {
        return new Image1DReadOnly(handle, width, true);
    }

    public long handle() {
        return handle;
    }

    public int width() {
        return width;
    }

    public boolean owned() {
        return owned;
    }

    public boolean closed() {
        return closed;
    }

    public boolean isValid() {
        return handle != 0L && !closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (!owned || handle == 0L) {
            return;
        }
        int errorCode = CL10.clReleaseMemObject(handle);
        if (errorCode != CL10.CL_SUCCESS) {
            throw new IllegalStateException("Failed to release OpenCL image handle: " + errorCode);
        }
    }
}
