package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

import java.util.Optional;

/**
 * Shared construction point for runtime compile requests produced by Java, ASM, and future frontends.
 */
public final class GpuRuntimeCompileRequestFactory {

    private GpuRuntimeCompileRequestFactory() {
    }

    public static GpuRuntimeCompileRequest fromDescriptor(
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        return fromDescriptor(descriptor, compileOptions, deviceProfile, Optional.empty());
    }

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

    public static GpuRuntimeCompileRequest fromInvocation(
            GpuKernelInvocation invocation,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        return fromInvocation(invocation, deviceProfile, Optional.empty());
    }

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
