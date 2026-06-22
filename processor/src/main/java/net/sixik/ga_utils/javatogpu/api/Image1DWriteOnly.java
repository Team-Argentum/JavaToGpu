package net.sixik.ga_utils.javatogpu.api;

import org.lwjgl.opencl.CL10;

/**
 * Java-side marker for an OpenCL {@code write_only image1d_t} kernel parameter.
 */
public final class Image1DWriteOnly implements AutoCloseable {

    private final long handle;
    private final int width;
    private final boolean owned;
    private boolean closed;

    public Image1DWriteOnly() {
        this(0L, 0, false);
    }

    public Image1DWriteOnly(long handle, int width) {
        this(handle, width, false);
    }

    private Image1DWriteOnly(long handle, int width, boolean owned) {
        this.handle = handle;
        this.width = width;
        this.owned = owned;
    }

    public static Image1DWriteOnly borrowed(long handle, int width) {
        return new Image1DWriteOnly(handle, width, false);
    }

    public static Image1DWriteOnly owned(long handle, int width) {
        return new Image1DWriteOnly(handle, width, true);
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

    public boolean isValid() {
        return handle != 0L && !closed;
    }

    @Override
    public void close() {
        if (!owned || handle == 0L || closed) {
            return;
        }
        closed = true;
        int errorCode = CL10.clReleaseMemObject(handle);
        if (errorCode != CL10.CL_SUCCESS) {
            throw new IllegalStateException("Failed to release OpenCL image handle: " + errorCode);
        }
    }
}
