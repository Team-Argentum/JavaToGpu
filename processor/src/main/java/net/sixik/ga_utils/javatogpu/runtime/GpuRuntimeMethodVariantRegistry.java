package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

/**
 * Compatibility facade for runtime method-variant discovery.
 *
 * @deprecated use {@link net.sixik.ga_utils.javatogpu.runtime.variants.GpuRuntimeMethodVariantRegistry}.
 */
@Deprecated
public final class GpuRuntimeMethodVariantRegistry {

    private final net.sixik.ga_utils.javatogpu.runtime.variants.GpuRuntimeMethodVariantRegistry delegate;

    private GpuRuntimeMethodVariantRegistry(
            net.sixik.ga_utils.javatogpu.runtime.variants.GpuRuntimeMethodVariantRegistry delegate
    ) {
        this.delegate = delegate;
    }

    public static GpuRuntimeMethodVariantRegistry load(ClassLoader preferredClassLoader) {
        return new GpuRuntimeMethodVariantRegistry(
                net.sixik.ga_utils.javatogpu.runtime.variants.GpuRuntimeMethodVariantRegistry.load(
                        preferredClassLoader
                )
        );
    }

    public static GpuRuntimeMethodVariantRegistry of(List<GpuRuntimeMethodVariantProvider> providers) {
        return new GpuRuntimeMethodVariantRegistry(
                net.sixik.ga_utils.javatogpu.runtime.variants.GpuRuntimeMethodVariantRegistry.of(providers)
        );
    }

    public List<GpuRuntimeMethodVariantRegistration> variants(String groupId) {
        return delegate.variants(groupId);
    }

    public List<GpuRuntimeMethodVariantProvider> providers() {
        return delegate.providers();
    }

    public List<GpuRuntimeMethodVariantRegistration> registrations() {
        return delegate.registrations();
    }
}
