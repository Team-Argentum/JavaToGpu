package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

/**
 * Compatibility facade for native host-memory service discovery.
 */
public final class GpuRuntimeNativeMemoryServiceRegistry {

    private final net.sixik.ga_utils.javatogpu.runtime.memory.GpuRuntimeNativeMemoryServiceRegistry delegate;

    private GpuRuntimeNativeMemoryServiceRegistry(
            net.sixik.ga_utils.javatogpu.runtime.memory.GpuRuntimeNativeMemoryServiceRegistry delegate
    ) {
        this.delegate = delegate;
    }

    public static GpuRuntimeNativeMemoryServiceRegistry of(List<GpuRuntimeNativeMemoryService> services) {
        return new GpuRuntimeNativeMemoryServiceRegistry(
                net.sixik.ga_utils.javatogpu.runtime.memory.GpuRuntimeNativeMemoryServiceRegistry.of(services)
        );
    }

    public static GpuRuntimeNativeMemoryServiceRegistry loadWithBuiltIns() {
        return new GpuRuntimeNativeMemoryServiceRegistry(
                net.sixik.ga_utils.javatogpu.runtime.memory.GpuRuntimeNativeMemoryServiceRegistry.loadWithBuiltIns()
        );
    }

    public GpuRuntimeNativeMemoryAllocation allocate(GpuRuntimeNativeMemoryAllocationRequest request) {
        return delegate.allocate(request);
    }

    public List<GpuRuntimeNativeMemoryService> services() {
        return delegate.services();
    }
}
