package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

/**
 * Compatibility facade for runtime method-variant selection.
 *
 * @deprecated use {@link net.sixik.ga_utils.javatogpu.runtime.variants.GpuRuntimeMethodVariantSelector}.
 */
@Deprecated
public final class GpuRuntimeMethodVariantSelector {

    private GpuRuntimeMethodVariantSelector() {
    }

    public static GpuRuntimeMethodVariantSelection select(
            GpuKernelDescriptor primaryDescriptor,
            List<GpuKernelDescriptor> fallbackDescriptors,
            ClassLoader artifactClassLoader,
            GpuRuntimeCompileOptions compileOptions,
            List<GpuRuntimeDeviceProfile> deviceProfiles,
            GpuRuntimeDevicePolicyRegistry devicePolicyRegistry
    ) {
        return net.sixik.ga_utils.javatogpu.runtime.variants.GpuRuntimeMethodVariantSelector.select(
                primaryDescriptor,
                fallbackDescriptors,
                artifactClassLoader,
                compileOptions,
                deviceProfiles,
                devicePolicyRegistry
        );
    }
}
