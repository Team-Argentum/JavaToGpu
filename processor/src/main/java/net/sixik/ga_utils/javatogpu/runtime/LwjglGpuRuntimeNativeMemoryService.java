package net.sixik.ga_utils.javatogpu.runtime;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Built-in native memory provider backed by LWJGL {@link MemoryUtil}.
 */
public final class LwjglGpuRuntimeNativeMemoryService implements GpuRuntimeNativeMemoryService {

    public static final String SERVICE_ID = "native-memory:lwjgl";

    @Override
    public String serviceId() {
        return SERVICE_ID;
    }

    @Override
    public String serviceVersion() {
        return "1";
    }

    @Override
    public int serviceOrder() {
        return 10_000;
    }

    @Override
    public GpuRuntimeNativeMemoryAllocation allocate(GpuRuntimeNativeMemoryAllocationRequest request) {
        Objects.requireNonNull(request, "request");
        ByteBuffer buffer = (request.zeroed()
                ? MemoryUtil.memCalloc(request.byteSize())
                : MemoryUtil.memAlloc(request.byteSize()))
                .order(ByteOrder.nativeOrder());
        return new GpuRuntimeNativeMemoryAllocation(
                serviceId(),
                serviceVersion(),
                request,
                buffer,
                MemoryUtil.memAddress(buffer),
                () -> MemoryUtil.memFree(buffer)
        );
    }
}
