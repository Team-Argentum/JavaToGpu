package net.sixik.ga_utils.javatogpu.api;

import org.lwjgl.opencl.CL10;

/**
 * Java-side marker for an OpenCL {@code write_only image1d_array_t} kernel parameter.
 */
public final class Image1DArrayWriteOnly implements AutoCloseable {

    private final long handle;
    private final int width;
    private final int layers;
    private final boolean owned;
    private boolean closed;

    public Image1DArrayWriteOnly() {
        this(0L, 0, 0, false);
    }

    public Image1DArrayWriteOnly(long handle, int width, int layers) {
        this(handle, width, layers, false);
    }

    private Image1DArrayWriteOnly(long handle, int width, int layers, boolean owned) {
        this.handle = handle;
        this.width = width;
        this.layers = layers;
        this.owned = owned;
    }

    public static Image1DArrayWriteOnly borrowed(long handle, int width, int layers) {
        return new Image1DArrayWriteOnly(handle, width, layers, false);
    }

    public static Image1DArrayWriteOnly owned(long handle, int width, int layers) {
        return new Image1DArrayWriteOnly(handle, width, layers, true);
    }

    public long handle() {
        return handle;
    }

    public int width() {
        return width;
    }

    public int layers() {
        return layers;
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
