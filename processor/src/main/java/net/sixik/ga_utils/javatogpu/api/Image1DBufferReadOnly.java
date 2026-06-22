package net.sixik.ga_utils.javatogpu.api;

import org.lwjgl.opencl.CL10;

/**
 * Java-side marker for an OpenCL {@code read_only image1d_buffer_t} kernel parameter.
 */
public final class Image1DBufferReadOnly implements AutoCloseable {

    private final long handle;
    private final int width;
    private final long backingBufferHandle;
    private final boolean owned;
    private boolean closed;

    public Image1DBufferReadOnly() {
        this(0L, 0, 0L, false);
    }

    public Image1DBufferReadOnly(long handle, int width) {
        this(handle, width, 0L, false);
    }

    public Image1DBufferReadOnly(long handle, int width, long backingBufferHandle) {
        this(handle, width, backingBufferHandle, false);
    }

    private Image1DBufferReadOnly(long handle, int width, long backingBufferHandle, boolean owned) {
        this.handle = handle;
        this.width = width;
        this.backingBufferHandle = backingBufferHandle;
        this.owned = owned;
    }

    public static Image1DBufferReadOnly borrowed(long handle, int width) {
        return new Image1DBufferReadOnly(handle, width, 0L, false);
    }

    public static Image1DBufferReadOnly owned(long handle, int width, long backingBufferHandle) {
        return new Image1DBufferReadOnly(handle, width, backingBufferHandle, true);
    }

    public long handle() {
        return handle;
    }

    public int width() {
        return width;
    }

    public long backingBufferHandle() {
        return backingBufferHandle;
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
        int imageError = CL10.clReleaseMemObject(handle);
        int bufferError = backingBufferHandle != 0L ? CL10.clReleaseMemObject(backingBufferHandle) : CL10.CL_SUCCESS;
        if (imageError != CL10.CL_SUCCESS) {
            throw new IllegalStateException("Failed to release OpenCL image handle: " + imageError);
        }
        if (bufferError != CL10.CL_SUCCESS) {
            throw new IllegalStateException("Failed to release OpenCL backing buffer handle: " + bufferError);
        }
    }
}
