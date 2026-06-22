package net.sixik.ga_utils.javatogpu.api;

import org.lwjgl.opencl.CL10;

/**
 * Java-side marker for an OpenCL {@code read_only image2d_array_t} kernel parameter.
 */
public final class Image2DArrayReadOnly implements AutoCloseable {

    private final long handle;
    private final int width;
    private final int height;
    private final int layers;
    private final boolean owned;
    private boolean closed;

    public Image2DArrayReadOnly() {
        this(0L, 0, 0, 0, false);
    }

    public Image2DArrayReadOnly(long handle, int width, int height, int layers) {
        this(handle, width, height, layers, false);
    }

    private Image2DArrayReadOnly(long handle, int width, int height, int layers, boolean owned) {
        this.handle = handle;
        this.width = width;
        this.height = height;
        this.layers = layers;
        this.owned = owned;
    }

    public static Image2DArrayReadOnly borrowed(long handle, int width, int height, int layers) {
        return new Image2DArrayReadOnly(handle, width, height, layers, false);
    }

    public static Image2DArrayReadOnly owned(long handle, int width, int height, int layers) {
        return new Image2DArrayReadOnly(handle, width, height, layers, true);
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
