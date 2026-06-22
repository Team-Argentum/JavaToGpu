package net.sixik.ga_utils.javatogpu.api;

import org.lwjgl.opencl.CL10;

/**
 * Java-side marker for an OpenCL {@code write_only image3d_t} kernel parameter.
 */
public final class Image3DWriteOnly implements AutoCloseable {

    private final long handle;
    private final int width;
    private final int height;
    private final int depth;
    private final boolean owned;
    private boolean closed;

    public Image3DWriteOnly() {
        this(0L, 0, 0, 0, false);
    }

    public Image3DWriteOnly(long handle, int width, int height, int depth) {
        this(handle, width, height, depth, false);
    }

    private Image3DWriteOnly(long handle, int width, int height, int depth, boolean owned) {
        this.handle = handle;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.owned = owned;
    }

    public static Image3DWriteOnly borrowed(long handle, int width, int height, int depth) {
        return new Image3DWriteOnly(handle, width, height, depth, false);
    }

    public static Image3DWriteOnly owned(long handle, int width, int height, int depth) {
        return new Image3DWriteOnly(handle, width, height, depth, true);
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

    public int depth() {
        return depth;
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
