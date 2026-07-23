package net.sixik.ga_utils.javatogpu.runtime.selection;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendDeviceSelection;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendPolicy;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeBackendSelection;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryCatalog;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeLifecycleEventBus;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeSelectionResult;

import java.util.Objects;

/**
 * Domain entry point for backend and device selection workflows.
 *
 * <p>Normal application code should usually start with
 * {@link net.sixik.ga_utils.javatogpu.api.JavaToGpu}. This class is for advanced users, examples, and runtime code that
 * need explicit discovery, policy selection, or backend/device preflight without browsing the root runtime package.</p>
 *
 * <p>The older root-runtime entry points remain source/binary compatible. New selection-focused code can import this
 * class as the stable package home while the large root package is split gradually.</p>
 */
public final class GpuRuntimeSelection {

    private GpuRuntimeSelection() {
    }

    /**
     * Selects a backend using the supplied immutable policy and throws when no candidate matches.
     */
    public static GpuRuntimeBackendSelection select(GpuRuntimeBackendPolicy policy) {
        return GpuRuntimeBackendSelectionSupport.select(Objects.requireNonNull(policy, "policy")).requireSelection();
    }

    /**
     * Selects a backend using the supplied immutable policy without throwing on a miss.
     */
    public static GpuRuntimeSelectionResult trySelect(GpuRuntimeBackendPolicy policy) {
        return GpuRuntimeBackendSelectionSupport.select(Objects.requireNonNull(policy, "policy"));
    }

    /**
     * Attaches a precomputed backend-neutral device discovery catalog to a backend selection result.
     */
    public static GpuRuntimeBackendDeviceSelection trySelectWithDeviceDiscovery(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeDeviceDiscoveryCatalog deviceDiscoveryCatalog
    ) {
        return GpuRuntimeBackendDeviceSelectionSupport.selectWithDeviceDiscovery(
                Objects.requireNonNull(policy, "policy"),
                deviceDiscoveryCatalog
        );
    }

    /**
     * Attaches a precomputed device discovery catalog and publishes lifecycle events around selection/discovery.
     */
    public static GpuRuntimeBackendDeviceSelection trySelectWithDeviceDiscovery(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeDeviceDiscoveryCatalog deviceDiscoveryCatalog,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        return GpuRuntimeBackendDeviceSelectionSupport.selectWithDeviceDiscovery(
                Objects.requireNonNull(policy, "policy"),
                deviceDiscoveryCatalog,
                lifecycleEventBus
        );
    }

    /**
     * Runs standard backend selection plus standard backend device discovery using default OpenCL controls.
     */
    public static GpuRuntimeBackendDeviceSelection trySelectStandardBackendAndDevice() {
        return trySelectStandardBackendAndDevice(GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL));
    }

    /**
     * Runs standard backend selection plus standard backend device discovery using caller-provided OpenCL controls.
     */
    public static GpuRuntimeBackendDeviceSelection trySelectStandardBackendAndDevice(
            GpuRuntimeCompileOptions openClDiscoveryOptions
    ) {
        GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
                .preferStandardBackendsWithPlannedDiagnostics()
                .build();
        return trySelectStandardBackendAndDevice(policy, openClDiscoveryOptions);
    }

    /**
     * Runs caller-supplied backend selection plus standard backend device discovery.
     */
    public static GpuRuntimeBackendDeviceSelection trySelectStandardBackendAndDevice(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeCompileOptions openClDiscoveryOptions
    ) {
        return GpuRuntimeBackendDeviceSelectionSupport.selectStandardBackendAndDevice(
                Objects.requireNonNull(policy, "policy"),
                openClDiscoveryOptions
        );
    }

    /**
     * Runs caller-supplied backend selection plus standard backend device discovery with lifecycle events.
     */
    public static GpuRuntimeBackendDeviceSelection trySelectStandardBackendAndDevice(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeCompileOptions openClDiscoveryOptions,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        return GpuRuntimeBackendDeviceSelectionSupport.selectStandardBackendAndDevice(
                Objects.requireNonNull(policy, "policy"),
                openClDiscoveryOptions,
                lifecycleEventBus
        );
    }

    /**
     * Installs a precomputed backend/device selection as the active runtime scope.
     */
    public static GpuRuntimeScope use(GpuRuntimeBackendDeviceSelection selection) {
        return GpuRuntime.use(Objects.requireNonNull(selection, "selection"));
    }

    /**
     * Selects and installs the standard backend/device pair using default OpenCL controls.
     */
    public static GpuRuntimeScope useStandardBackendAndDevice() {
        return GpuRuntime.useStandardBackendAndDevice();
    }

    /**
     * Selects and installs the standard backend/device pair using caller-provided OpenCL controls.
     */
    public static GpuRuntimeScope useStandardBackendAndDevice(GpuRuntimeCompileOptions compileOptions) {
        return GpuRuntime.useStandardBackendAndDevice(compileOptions);
    }

    /**
     * Discovers OpenCL devices and previews deterministic device selection with default controls.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverOpenCl() {
        return GpuRuntimeDeviceDiscoverySupport.discoverOpenCl();
    }

    /**
     * Discovers OpenCL devices and previews deterministic device selection with caller-provided controls.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverOpenCl(GpuRuntimeCompileOptions compileOptions) {
        return GpuRuntimeDeviceDiscoverySupport.discoverOpenCl(compileOptions);
    }

    /**
     * Discovers CUDA-visible NVIDIA devices without enabling CUDA production execution.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverCuda() {
        return GpuRuntimeDeviceDiscoverySupport.discoverCuda();
    }

    /**
     * Discovers CUDA-visible NVIDIA devices with caller-provided controls.
     */
    public static GpuRuntimeDeviceDiscoveryResult discoverCuda(GpuRuntimeCompileOptions compileOptions) {
        return GpuRuntimeDeviceDiscoverySupport.discoverCuda(compileOptions);
    }

    /**
     * Discovers the standard backend inventory shape: OpenCL, staged CUDA inventory, and planned placeholders.
     */
    public static GpuRuntimeDeviceDiscoveryCatalog discoverStandardBackends() {
        return GpuRuntimeDeviceDiscoverySupport.discoverStandardBackends();
    }

    /**
     * Discovers the standard backend inventory shape using caller-provided OpenCL discovery controls.
     */
    public static GpuRuntimeDeviceDiscoveryCatalog discoverStandardBackends(GpuRuntimeCompileOptions compileOptions) {
        return GpuRuntimeDeviceDiscoverySupport.discoverStandardBackends(compileOptions);
    }

    /**
     * Returns an explicit planned/unavailable discovery result for a backend without a native discovery adapter yet.
     */
    public static GpuRuntimeDeviceDiscoveryResult plannedUnavailable(GpuBackendTarget backendTarget) {
        return GpuRuntimeDeviceDiscoverySupport.plannedUnavailable(backendTarget);
    }
}
