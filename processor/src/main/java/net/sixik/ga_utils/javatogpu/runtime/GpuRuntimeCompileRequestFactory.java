package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.runtime.launch.GpuRuntimeCompileRequestSupport;

import java.util.Optional;

/**
 * Compatibility facade for runtime compile-request construction.
 *
 * <p>New launch/descriptor code should prefer
 * {@link net.sixik.ga_utils.javatogpu.runtime.launch.GpuRuntimeCompileRequestSupport}. This class keeps the original
 * root runtime API stable for Java, ASM, generated launchers, tests, and existing callers.</p>
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
