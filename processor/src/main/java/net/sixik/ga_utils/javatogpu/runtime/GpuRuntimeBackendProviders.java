package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.cuda.CudaRuntimeBackendProvider;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClRuntimeBackendProvider;
import net.sixik.ga_utils.javatogpu.runtime.spi.PlannedGpuRuntimeBackendProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Deterministic provider registry for built-in and ServiceLoader backend adapters.
 */
public final class GpuRuntimeBackendProviders {

    private GpuRuntimeBackendProviders() {
    }

    /**
     * Returns production-ready built-in providers only.
     */
    public static List<GpuRuntimeBackendProvider> standard() {
        return List.of(new OpenClRuntimeBackendProvider());
    }

    /**
     * Returns built-in providers, including planned non-executing backend families.
     */
    public static List<GpuRuntimeBackendProvider> builtInsWithPlannedBackends() {
        return List.of(
                new OpenClRuntimeBackendProvider(),
                new CudaRuntimeBackendProvider(),
                new PlannedGpuRuntimeBackendProvider(GpuBackendTarget.VULKAN, 200),
                new PlannedGpuRuntimeBackendProvider(GpuBackendTarget.METAL, 300)
        );
    }

    /**
     * Returns built-ins plus any external providers discovered through ServiceLoader.
     */
    public static List<GpuRuntimeBackendProvider> standardWithPlannedBackends() {
        return standardWithPlannedBackends(contextClassLoader());
    }

    /**
     * Returns built-ins plus ServiceLoader providers from the supplied class loader.
     */
    public static List<GpuRuntimeBackendProvider> standardWithPlannedBackends(ClassLoader classLoader) {
        ArrayList<GpuRuntimeBackendProvider> providers = new ArrayList<>(builtInsWithPlannedBackends());
        ServiceLoader.load(GpuRuntimeBackendProvider.class, classLoader == null ? contextClassLoader() : classLoader)
                .forEach(providers::add);
        return orderedUniqueProviders(providers);
    }

    /**
     * Applies the provider registry ordering and validation without creating backend adapters.
     */
    public static List<GpuRuntimeBackendProvider> orderedProviders(List<GpuRuntimeBackendProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        return orderedUniqueProviders(providers);
    }

    /**
     * Creates adapters from provider definitions after applying the same ordering and duplicate-id validation.
     */
    public static List<GpuRuntimeBackendAdapter> adapters(List<GpuRuntimeBackendProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        ArrayList<GpuRuntimeBackendAdapter> adapters = new ArrayList<>();
        for (GpuRuntimeBackendProvider provider : orderedUniqueProviders(providers)) {
            GpuRuntimeBackendAdapter adapter = Objects.requireNonNull(
                    provider.createAdapter(),
                    "provider " + provider.providerId() + " returned null adapter"
            );
            if (adapter.backendTarget() != provider.backendTarget()) {
                throw new IllegalStateException(
                        "GPU runtime backend provider "
                                + provider.providerId()
                                + " reports "
                                + provider.backendTarget()
                                + " but created adapter for "
                                + adapter.backendTarget()
                );
            }
            adapters.add(new ProviderBackedRuntimeBackendAdapter(adapter, provider.executionSupport()));
        }
        return List.copyOf(adapters);
    }

    public static Optional<GpuRuntimeBackendProvider> forTarget(GpuBackendTarget backendTarget) {
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        return standardWithPlannedBackends().stream()
                .filter(provider -> provider.backendTarget() == target)
                .findFirst();
    }

    private static List<GpuRuntimeBackendProvider> orderedUniqueProviders(List<GpuRuntimeBackendProvider> providers) {
        ArrayList<GpuRuntimeBackendProvider> ordered = new ArrayList<>();
        for (GpuRuntimeBackendProvider provider : providers) {
            ordered.add(validateProvider(provider));
        }
        ordered.sort(Comparator
                .comparingInt(GpuRuntimeBackendProvider::providerOrder)
                .thenComparing(GpuRuntimeBackendProvider::providerId));

        LinkedHashMap<String, GpuRuntimeBackendProvider> unique = new LinkedHashMap<>();
        for (GpuRuntimeBackendProvider provider : ordered) {
            GpuRuntimeBackendProvider previous = unique.putIfAbsent(provider.providerId(), provider);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate GPU runtime backend provider id '"
                                + provider.providerId()
                                + "' for "
                                + previous.getClass().getName()
                                + " and "
                                + provider.getClass().getName()
                );
            }
        }
        return List.copyOf(unique.values());
    }

    private static GpuRuntimeBackendProvider validateProvider(GpuRuntimeBackendProvider provider) {
        Objects.requireNonNull(provider, "provider");
        if (provider.providerId() == null || provider.providerId().isBlank()) {
            throw new IllegalStateException("GPU runtime backend provider id must not be blank: "
                    + provider.getClass().getName());
        }
        if (provider.providerVersion() == null || provider.providerVersion().isBlank()) {
            throw new IllegalStateException("GPU runtime backend provider version must not be blank: "
                    + provider.providerId());
        }
        GpuRuntimeBackendExecutionSupport executionSupport = Objects.requireNonNull(
                provider.executionSupport(),
                "provider " + provider.providerId() + " returned null execution support"
        );
        if (executionSupport.backendTarget() != provider.backendTarget()) {
            throw new IllegalStateException(
                    "GPU runtime backend provider "
                            + provider.providerId()
                            + " reports "
                            + provider.backendTarget()
                            + " but execution support reports "
                            + executionSupport.backendTarget()
            );
        }
        Optional<GpuBackendExecutionPipelineFactory<?, ?, ?>> executionPipelineFactory = Objects.requireNonNull(
                provider.executionPipelineFactory(),
                "provider " + provider.providerId() + " returned null execution pipeline factory optional"
        );
        if (executionPipelineFactory.isPresent()
                && executionPipelineFactory.orElseThrow().backendTarget() != provider.backendTarget()) {
            throw new IllegalStateException(
                    "GPU runtime backend provider "
                            + provider.providerId()
                            + " reports "
                            + provider.backendTarget()
                            + " but execution pipeline factory reports "
                            + executionPipelineFactory.orElseThrow().backendTarget()
            );
        }
        if (executionPipelineFactory.isPresent() && !executionSupport.executionPipelineAvailable()) {
            throw new IllegalStateException(
                    "GPU runtime backend provider "
                            + provider.providerId()
                            + " exposes an execution pipeline factory but did not declare compile/prepare/invoke support"
            );
        }
        return provider;
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader == null ? GpuRuntimeBackendProviders.class.getClassLoader() : classLoader;
    }

    private record ProviderBackedRuntimeBackendAdapter(
            GpuRuntimeBackendAdapter delegate,
            GpuRuntimeBackendExecutionSupport executionSupport
    ) implements GpuRuntimeBackendAdapter {

        private ProviderBackedRuntimeBackendAdapter {
            Objects.requireNonNull(delegate, "delegate");
            Objects.requireNonNull(executionSupport, "executionSupport");
        }

        @Override
        public GpuBackendTarget backendTarget() {
            return delegate.backendTarget();
        }

        @Override
        public String backendName() {
            return delegate.backendName();
        }

        @Override
        public GpuRuntimeBackendCatalogEntry catalogEntry() {
            return delegate.catalogEntry().withExecutionSupport(executionSupport);
        }

        @Override
        public GpuRuntimeDeviceDiscoveryResult discoverDevices(
                GpuRuntimeCompileOptions compileOptions,
                GpuRuntimeDevicePolicyRegistry devicePolicyRegistry
        ) {
            return delegate.discoverDevices(compileOptions, devicePolicyRegistry);
        }

        @Override
        public GpuBackendLowerer lowerer() {
            return delegate.lowerer();
        }
    }
}
