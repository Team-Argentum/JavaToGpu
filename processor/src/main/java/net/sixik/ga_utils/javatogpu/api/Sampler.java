package net.sixik.ga_utils.javatogpu.api;

import org.lwjgl.opencl.CL10;

/**
 * Java-side marker for an OpenCL {@code sampler_t} kernel parameter.
 */
public final class Sampler implements AutoCloseable {

    private final long handle;
    private final boolean owned;
    private boolean closed;

    public Sampler() {
        this(0L, false);
    }

    public Sampler(long handle) {
        this(handle, false);
    }

    private Sampler(long handle, boolean owned) {
        this.handle = handle;
        this.owned = owned;
    }

    public static Sampler borrowed(long handle) {
        return new Sampler(handle, false);
    }

    public static Sampler owned(long handle) {
        return new Sampler(handle, true);
    }

    public long handle() {
        return handle;
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
        int errorCode = CL10.clReleaseSampler(handle);
        if (errorCode != CL10.CL_SUCCESS) {
            throw new IllegalStateException("Failed to release OpenCL sampler handle: " + errorCode);
        }
    }
}
