package net.sixik.ga_utils.javatogpu.runtime.launch;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeDeviceProfile;

import java.util.Optional;

/**
 * Domain implementation support for constructing runtime compile requests from launch descriptors and invocations.
 */
public final class GpuRuntimeCompileRequestSupport {

    private GpuRuntimeCompileRequestSupport() {
    }

    /**
     * Builds a compile request from a kernel descriptor and optional device profile.
     */
    public static GpuRuntimeCompileRequest fromDescriptor(
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        return fromDescriptor(descriptor, compileOptions, deviceProfile, Optional.empty());
    }

    /**
     * Builds a compile request from a kernel descriptor plus an optional canonical IR artifact.
     */
    public static GpuRuntimeCompileRequest fromDescriptor(
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDeviceProfile deviceProfile,
            Optional<IrGpuArtifact> irGpuArtifact
    ) {
        GpuRuntimeCompileOptions resolvedOptions = compileOptions == null
                ? GpuRuntimeCompileOptions.defaults(deviceProfile == null ? null : deviceProfile.backendTarget())
                : compileOptions;
        return new GpuRuntimeCompileRequest(
                descriptor,
                resolvedOptions,
                deviceProfile,
                irGpuArtifact
        );
    }

    /**
     * Builds a compile request from a launcher invocation and optional device profile.
     */
    public static GpuRuntimeCompileRequest fromInvocation(
            GpuKernelInvocation invocation,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        return fromInvocation(invocation, deviceProfile, Optional.empty());
    }

    /**
     * Builds a compile request from a launcher invocation plus an optional canonical IR artifact.
     */
    public static GpuRuntimeCompileRequest fromInvocation(
            GpuKernelInvocation invocation,
            GpuRuntimeDeviceProfile deviceProfile,
            Optional<IrGpuArtifact> irGpuArtifact
    ) {
        if (invocation == null) {
            throw new NullPointerException("invocation");
        }
        GpuRuntimeCompileOptions compileOptions = invocation.compileOptions() == null
                ? GpuRuntimeCompileOptions.defaults(deviceProfile == null ? null : deviceProfile.backendTarget())
                : invocation.compileOptions();
        return fromDescriptor(
                invocation.descriptor(),
                compileOptions,
                deviceProfile,
                irGpuArtifact
        );
    }
}
