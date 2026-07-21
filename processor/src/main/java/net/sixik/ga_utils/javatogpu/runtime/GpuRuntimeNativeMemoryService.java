package net.sixik.ga_utils.javatogpu.runtime;

/**
 * ServiceLoader-facing native host-memory provider for backend runtime binders.
 *
 * <p>The current built-in implementation is LWJGL-backed. Future Panama-based modules can provide the same contract by
 * returning an address plus a {@link java.nio.ByteBuffer} view over their allocation.</p>
 */
public interface GpuRuntimeNativeMemoryService {

    default String serviceId() {
        return getClass().getName();
    }

    default String serviceVersion() {
        return "1";
    }

    default int serviceOrder() {
        return 0;
    }

    default boolean supports(GpuRuntimeNativeMemoryAllocationRequest request) {
        return request != null;
    }

    GpuRuntimeNativeMemoryAllocation allocate(GpuRuntimeNativeMemoryAllocationRequest request);
}
