package net.sixik.ga_utils.javatogpu.api;

import org.lwjgl.opencl.CL10;

/**
 * Java-side marker for an OpenCL {@code write_only image2d_t} kernel parameter.
 */
public final class Image2DWriteOnly implements AutoCloseable {

    private final long handle;
    private final int width;
    private final int height;
    private final boolean owned;
    private boolean closed;

    public Image2DWriteOnly() {
        this(0L, 0, 0, false);
    }

    public Image2DWriteOnly(long handle, int width, int height) {
        this(handle, width, height, false);
    }

    private Image2DWriteOnly(long handle, int width, int height, boolean owned) {
        this.handle = handle;
        this.width = width;
        this.height = height;
        this.owned = owned;
    }

    public static Image2DWriteOnly borrowed(long handle, int width, int height) {
        return new Image2DWriteOnly(handle, width, height, false);
    }

    public static Image2DWriteOnly owned(long handle, int width, int height) {
        return new Image2DWriteOnly(handle, width, height, true);
    }

    public long handle() {
        return handle;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
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
