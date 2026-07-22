package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.runtime.launch.GpuRuntimeCompileRequestSupport;

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
        return GpuRuntimeCompileRequestSupport.fromDescriptor(descriptor, compileOptions, deviceProfile);
    }

    public static GpuRuntimeCompileRequest fromDescriptor(
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeDeviceProfile deviceProfile,
            Optional<IrGpuArtifact> irGpuArtifact
    ) {
        return GpuRuntimeCompileRequestSupport.fromDescriptor(
                descriptor,
                compileOptions,
                deviceProfile,
                irGpuArtifact
        );
    }

    public static GpuRuntimeCompileRequest fromInvocation(
            GpuKernelInvocation invocation,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        return GpuRuntimeCompileRequestSupport.fromInvocation(invocation, deviceProfile);
    }

    public static GpuRuntimeCompileRequest fromInvocation(
            GpuKernelInvocation invocation,
            GpuRuntimeDeviceProfile deviceProfile,
            Optional<IrGpuArtifact> irGpuArtifact
    ) {
        return GpuRuntimeCompileRequestSupport.fromInvocation(invocation, deviceProfile, irGpuArtifact);
    }
}
