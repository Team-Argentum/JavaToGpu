package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceDiscoveryResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDevicePolicyRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceSelection;

import java.util.List;
import java.util.Objects;

/**
 * OpenCL adapter for public device discovery snapshots.
 */
public final class OpenClRuntimeDeviceDiscovery {

    private OpenClRuntimeDeviceDiscovery() {
    }

    /**
     * Discovers OpenCL devices and runs the existing deterministic runtime device policy over the candidates.
     */
    public static GpuRuntimeDeviceDiscoveryResult discover(
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry
    ) {
        GpuRuntimeCompileOptions resolvedOptions = compileOptions == null
                ? GpuRuntimeCompileOptions.defaults(GpuBackendTarget.OPENCL)
                : compileOptions;
        GpuRuntimeDevicePolicyRegistry resolvedRegistry = Objects.requireNonNullElseGet(
                devicePolicyRegistry,
                GpuRuntimeDevicePolicyRegistry::loadWithBuiltIns
        );
        try {
            List<GpuRuntimeDeviceProfile> profiles = OpenClRuntimeSession.discoverDeviceProfiles(
                    resolvedRegistry,
                    resolvedOptions
            );
            GpuRuntimeDeviceSelection selection = OpenClRuntimeSession.selectDevice(
                    resolvedRegistry,
                    profiles,
                    null,
                    resolvedOptions
            );
            return GpuRuntimeDeviceDiscoveryResult.available(
                    GpuBackendTarget.OPENCL,
                    "OpenCL",
                    profiles,
                    selection
            );
        } catch (RuntimeException | LinkageError exception) {
            return GpuRuntimeDeviceDiscoveryResult.unavailable(
                    GpuBackendTarget.OPENCL,
                    "OpenCL",
                    "opencl-device-discovery-failed",
                    exception
            );
        }
    }
}
