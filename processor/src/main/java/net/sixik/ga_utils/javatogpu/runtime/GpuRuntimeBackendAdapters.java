package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Backend adapter registry backed by {@link GpuRuntimeBackendProvider} definitions.
 *
 * <p>The registry keeps the existing adapter-oriented API while routing built-in and ServiceLoader providers through
 * the same provider boundary. This lets applications enumerate backend families without duplicating catalog,
 * discovery, and lowerer wiring.</p>
 */
public final class GpuRuntimeBackendAdapters {

    private GpuRuntimeBackendAdapters() {
    }

    public static List<GpuRuntimeBackendAdapter> standard() {
        return GpuRuntimeBackendProviders.adapters(GpuRuntimeBackendProviders.standard());
    }

    public static List<GpuRuntimeBackendAdapter> standardWithPlannedBackends() {
        return GpuRuntimeBackendProviders.adapters(GpuRuntimeBackendProviders.standardWithPlannedBackends());
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
        java.util.ArrayList<GpuRuntimeBackendCatalogEntry> entries = new java.util.ArrayList<>(adapters.size());
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
        java.util.ArrayList<GpuRuntimeDeviceDiscoveryResult> discoveries = new java.util.ArrayList<>(adapters.size());
        for (GpuRuntimeBackendAdapter adapter : adapters) {
            discoveries.add(Objects.requireNonNull(adapter, "adapter").discoverDevices(compileOptions, resolvedRegistry));
        }
        return GpuRuntimeDeviceDiscoveryCatalog.of(discoveries);
    }
}
