package net.sixik.ga_utils.javatogpu.runtime;

/**
 * ServiceLoader-facing native host-memory provider for backend runtime binders.
 *
 * <p>The current built-in implementation is LWJGL-backed. Future Panama-based modules can provide the same contract by
 * returning an address plus a {@link java.nio.ByteBuffer} view over their allocation.</p>
 */
public interface GpuRuntimeNativeMemoryService {

    /**
     * Stable provider id used for deterministic ordering diagnostics.
     */
    default String serviceId() {
        return getClass().getName();
    }

    /**
     * Provider contract version, not the JVM or native allocator version.
     */
    default String serviceVersion() {
        return "1";
    }

    /**
     * Lower values are preferred when several services support the same allocation request.
     */
    default int serviceOrder() {
        return 0;
    }

    /**
     * Returns whether this service can satisfy the requested allocation shape.
     */
    default boolean supports(GpuRuntimeNativeMemoryAllocationRequest request) {
        return request != null;
    }

    /**
     * Allocates host-visible native memory for a backend binder.
     *
     * <p>The returned allocation must remain valid until closed by the caller. Implementations should fail with a
     * clear runtime exception rather than returning partially initialized memory.</p>
     */
    GpuRuntimeNativeMemoryAllocation allocate(GpuRuntimeNativeMemoryAllocationRequest request);
}
