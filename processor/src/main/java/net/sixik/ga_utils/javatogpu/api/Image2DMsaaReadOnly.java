package net.sixik.ga_utils.javatogpu.api;

import org.lwjgl.opencl.CL10;

/**
 * Java-side marker for an OpenCL {@code read_only image2d_msaa_t} kernel parameter.
 */
public final class Image2DMsaaReadOnly implements AutoCloseable {

    private final long handle;
    private final int width;
    private final int height;
    private final int sampleCount;
    private final boolean owned;
    private boolean closed;

    public Image2DMsaaReadOnly() {
        this(0L, 0, 0, 0, false);
    }

    public Image2DMsaaReadOnly(long handle, int width, int height, int sampleCount) {
        this(handle, width, height, sampleCount, false);
    }

    private Image2DMsaaReadOnly(long handle, int width, int height, int sampleCount, boolean owned) {
        this.handle = handle;
        this.width = width;
        this.height = height;
        this.sampleCount = sampleCount;
        this.owned = owned;
    }

    public static Image2DMsaaReadOnly borrowed(long handle, int width, int height, int sampleCount) {
        return new Image2DMsaaReadOnly(handle, width, height, sampleCount, false);
    }

    public static Image2DMsaaReadOnly owned(long handle, int width, int height, int sampleCount) {
        return new Image2DMsaaReadOnly(handle, width, height, sampleCount, true);
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

    public int sampleCount() {
        return sampleCount;
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
