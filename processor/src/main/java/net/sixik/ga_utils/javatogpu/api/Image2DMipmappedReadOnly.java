package net.sixik.ga_utils.javatogpu.api;

import org.lwjgl.opencl.CL10;

/**
 * Java-side marker for an OpenCL {@code read_only image2d_t} with mip levels allocated.
 */
public final class Image2DMipmappedReadOnly implements AutoCloseable {

    private final long handle;
    private final int width;
    private final int height;
    private final int mipLevels;
    private final boolean owned;
    private boolean closed;

    public Image2DMipmappedReadOnly() {
        this(0L, 0, 0, 0, false);
    }

    public Image2DMipmappedReadOnly(long handle, int width, int height, int mipLevels) {
        this(handle, width, height, mipLevels, false);
    }

    private Image2DMipmappedReadOnly(long handle, int width, int height, int mipLevels, boolean owned) {
        this.handle = handle;
        this.width = width;
        this.height = height;
        this.mipLevels = mipLevels;
        this.owned = owned;
    }

    public static Image2DMipmappedReadOnly borrowed(long handle, int width, int height, int mipLevels) {
        return new Image2DMipmappedReadOnly(handle, width, height, mipLevels, false);
    }

    public static Image2DMipmappedReadOnly owned(long handle, int width, int height, int mipLevels) {
        return new Image2DMipmappedReadOnly(handle, width, height, mipLevels, true);
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

    public int mipLevels() {
        return mipLevels;
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
