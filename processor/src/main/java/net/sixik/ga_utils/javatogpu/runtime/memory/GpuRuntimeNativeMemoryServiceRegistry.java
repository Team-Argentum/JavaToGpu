package net.sixik.ga_utils.javatogpu.runtime.memory;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeNativeMemoryAllocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeNativeMemoryAllocationRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeNativeMemoryService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Deterministic registry for native host-memory services.
 */
public final class GpuRuntimeNativeMemoryServiceRegistry {

    private final List<GpuRuntimeNativeMemoryService> services;

    private GpuRuntimeNativeMemoryServiceRegistry(List<GpuRuntimeNativeMemoryService> services) {
        ArrayList<GpuRuntimeNativeMemoryService> sorted = new ArrayList<>(services == null ? List.of() : services);
        sorted.sort(Comparator
                .comparingInt(GpuRuntimeNativeMemoryService::serviceOrder)
                .thenComparing(GpuRuntimeNativeMemoryService::serviceId)
                .thenComparing(GpuRuntimeNativeMemoryService::serviceVersion));
        validateServiceIds(sorted);
        this.services = List.copyOf(sorted);
    }

    public static GpuRuntimeNativeMemoryServiceRegistry of(List<GpuRuntimeNativeMemoryService> services) {
        return new GpuRuntimeNativeMemoryServiceRegistry(services);
    }

    public static GpuRuntimeNativeMemoryServiceRegistry loadWithBuiltIns() {
        ArrayList<GpuRuntimeNativeMemoryService> loaded = new ArrayList<>();
        ServiceLoader.load(
                GpuRuntimeNativeMemoryService.class,
                GpuRuntimeNativeMemoryService.class.getClassLoader()
        ).forEach(loaded::add);
        loaded.add(new LwjglGpuRuntimeNativeMemoryService());
        return of(loaded);
    }

    public GpuRuntimeNativeMemoryAllocation allocate(GpuRuntimeNativeMemoryAllocationRequest request) {
        Objects.requireNonNull(request, "request");
        for (GpuRuntimeNativeMemoryService service : services) {
            if (!service.supports(request)) {
                continue;
            }
            return Objects.requireNonNull(service.allocate(request), "native memory allocation");
        }
        throw new IllegalStateException("No native memory service supports " + request.backendTarget()
                + " / " + request.purpose());
    }

    public List<GpuRuntimeNativeMemoryService> services() {
        return services;
    }

    private static void validateServiceIds(List<GpuRuntimeNativeMemoryService> services) {
        LinkedHashMap<String, GpuRuntimeNativeMemoryService> byId = new LinkedHashMap<>();
        for (GpuRuntimeNativeMemoryService service : services) {
            String serviceId = service.serviceId();
            GpuRuntimeNativeMemoryService previous = byId.putIfAbsent(serviceId, service);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate native memory service id: " + serviceId);
            }
        }
    }
}
