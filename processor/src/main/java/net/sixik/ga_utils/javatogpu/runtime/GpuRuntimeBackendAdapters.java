package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Built-in backend adapter registry.
 *
 * <p>The registry is deliberately small and deterministic. It gives applications, examples, and future backend modules
 * one place to enumerate backend families without duplicating catalog, discovery, and lowerer wiring.</p>
 */
public final class GpuRuntimeBackendAdapters {

    private static final GpuRuntimeBackendAdapter OPENCL = new OpenClRuntimeBackendAdapter();
    private static final GpuRuntimeBackendAdapter CUDA = new CudaRuntimeBackendAdapter();
    private static final GpuRuntimeBackendAdapter VULKAN = new PlannedGpuRuntimeBackendAdapter(GpuBackendTarget.VULKAN);
    private static final GpuRuntimeBackendAdapter METAL = new PlannedGpuRuntimeBackendAdapter(GpuBackendTarget.METAL);

    private GpuRuntimeBackendAdapters() {
    }

    public static List<GpuRuntimeBackendAdapter> standard() {
        return List.of(OPENCL);
    }

    public static List<GpuRuntimeBackendAdapter> standardWithPlannedBackends() {
        return List.of(OPENCL, CUDA, VULKAN, METAL);
    }

    public static Optional<GpuRuntimeBackendAdapter> forTarget(GpuBackendTarget backendTarget) {
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        return standardWithPlannedBackends().stream()
                .filter(adapter -> adapter.backendTarget() == target)
                .findFirst();
    }

    public static GpuRuntimeBackendAdapter requireTarget(GpuBackendTarget backendTarget) {
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        return forTarget(target).orElseThrow(() -> new IllegalArgumentException(
                "No GPU runtime backend adapter is registered for " + target
        ));
    }

    public static List<GpuRuntimeBackendCatalogEntry> catalogEntries(List<GpuRuntimeBackendAdapter> adapters) {
        Objects.requireNonNull(adapters, "adapters");
        ArrayList<GpuRuntimeBackendCatalogEntry> entries = new ArrayList<>(adapters.size());
        for (GpuRuntimeBackendAdapter adapter : adapters) {
            entries.add(Objects.requireNonNull(adapter, "adapter").catalogEntry());
        }
        return List.copyOf(entries);
    }

    public static GpuRuntimeDeviceDiscoveryCatalog discoverDevices(
            List<GpuRuntimeBackendAdapter> adapters,
            GpuRuntimeCompileOptions compileOptions
    ) {
        return discoverDevices(adapters, compileOptions, GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns());
    }

    public static GpuRuntimeDeviceDiscoveryCatalog discoverDevices(
            List<GpuRuntimeBackendAdapter> adapters,
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry
    ) {
        Objects.requireNonNull(adapters, "adapters");
        GpuRuntimeDevicePolicyRegistry resolvedRegistry = devicePolicyRegistry == null
                ? GpuRuntimeDevicePolicyRegistry.loadWithBuiltIns()
                : devicePolicyRegistry;
        ArrayList<GpuRuntimeDeviceDiscoveryResult> discoveries = new ArrayList<>(adapters.size());
        for (GpuRuntimeBackendAdapter adapter : adapters) {
            discoveries.add(Objects.requireNonNull(adapter, "adapter").discoverDevices(compileOptions, resolvedRegistry));
        }
        return GpuRuntimeDeviceDiscoveryCatalog.of(discoveries);
    }
}
