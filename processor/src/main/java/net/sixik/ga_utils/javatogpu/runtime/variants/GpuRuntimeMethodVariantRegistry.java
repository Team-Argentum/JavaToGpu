package net.sixik.ga_utils.javatogpu.runtime.variants;

import net.sixik.ga_utils.javatogpu.runtime.GpuKernelDescriptor;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodVariantProvider;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeMethodVariantRegistration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Deterministic registry of cross-module method fallback providers.
 */
public final class GpuRuntimeMethodVariantRegistry {

    private final List<GpuRuntimeMethodVariantProvider> providers;
    private final List<GpuRuntimeMethodVariantRegistration> registrations;

    private GpuRuntimeMethodVariantRegistry(List<GpuRuntimeMethodVariantProvider> providers) {
        ArrayList<GpuRuntimeMethodVariantProvider> orderedProviders = new ArrayList<>(providers);
        orderedProviders.sort(Comparator
                .comparing(GpuRuntimeMethodVariantProvider::providerId)
                .thenComparing(GpuRuntimeMethodVariantProvider::providerVersion)
                .thenComparing(provider -> provider.getClass().getName()));
        validateProviderIds(orderedProviders);
        this.providers = List.copyOf(orderedProviders);
        this.registrations = collectRegistrations(orderedProviders);
    }

    public static GpuRuntimeMethodVariantRegistry load(ClassLoader preferredClassLoader) {
        ClassLoader classLoader = preferredClassLoader == null
                ? Thread.currentThread().getContextClassLoader()
                : preferredClassLoader;
        if (classLoader == null) {
            classLoader = GpuRuntimeMethodVariantRegistry.class.getClassLoader();
        }
        ArrayList<GpuRuntimeMethodVariantProvider> providers = new ArrayList<>();
        ServiceLoader.load(GpuRuntimeMethodVariantProvider.class, classLoader).forEach(providers::add);
        return new GpuRuntimeMethodVariantRegistry(providers);
    }

    public static GpuRuntimeMethodVariantRegistry of(List<GpuRuntimeMethodVariantProvider> providers) {
        return new GpuRuntimeMethodVariantRegistry(providers == null ? List.of() : providers);
    }

    public List<GpuRuntimeMethodVariantRegistration> variants(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return List.of();
        }
        String normalizedGroupId = groupId.trim();
        return registrations.stream()
                .filter(registration -> registration.groupId().equals(normalizedGroupId))
                .toList();
    }

    public List<GpuRuntimeMethodVariantProvider> providers() {
        return providers;
    }

    public List<GpuRuntimeMethodVariantRegistration> registrations() {
        return registrations;
    }

    private static void validateProviderIds(List<GpuRuntimeMethodVariantProvider> providers) {
        LinkedHashMap<String, GpuRuntimeMethodVariantProvider> byId = new LinkedHashMap<>();
        for (GpuRuntimeMethodVariantProvider provider : providers) {
            if (provider == null) {
                throw new IllegalArgumentException("Method variant provider must not be null");
            }
            String providerId = requireText(provider.providerId(), "provider id");
            requireText(provider.providerVersion(), "provider version");
            GpuRuntimeMethodVariantProvider previous = byId.putIfAbsent(providerId, provider);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate method variant provider id '" + providerId + "'");
            }
        }
    }

    private static List<GpuRuntimeMethodVariantRegistration> collectRegistrations(
            List<GpuRuntimeMethodVariantProvider> providers
    ) {
        ArrayList<GpuRuntimeMethodVariantRegistration> registrations = new ArrayList<>();
        LinkedHashMap<String, GpuRuntimeMethodVariantRegistration> byIdentity = new LinkedHashMap<>();
        for (GpuRuntimeMethodVariantProvider provider : providers) {
            List<GpuRuntimeMethodVariantRegistration> provided = provider.variants();
            if (provided == null) {
                throw new IllegalArgumentException(
                        "Method variant provider '" + provider.providerId() + "' returned null variants"
                );
            }
            for (GpuRuntimeMethodVariantRegistration registration : provided) {
                if (registration == null) {
                    throw new IllegalArgumentException(
                            "Method variant provider '" + provider.providerId() + "' returned a null registration"
                    );
                }
                String identity = registration.groupId() + '|' + registration.variantId();
                GpuRuntimeMethodVariantRegistration previous = byIdentity.putIfAbsent(identity, registration);
                if (previous != null && !sameDescriptor(previous.descriptor(), registration.descriptor())) {
                    throw new IllegalArgumentException(
                            "Duplicate method variant '" + registration.variantId()
                                    + "' in group '" + registration.groupId() + "'"
                    );
                }
            }
        }
        registrations.addAll(byIdentity.values());
        registrations.sort(Comparator
                .comparing(GpuRuntimeMethodVariantRegistration::groupId)
                .thenComparing(GpuRuntimeMethodVariantRegistration::variantId)
                .thenComparing(registration -> registration.descriptor().kernelResource())
                .thenComparing(registration -> registration.descriptor().kernelName()));
        return List.copyOf(registrations);
    }

    private static boolean sameDescriptor(GpuKernelDescriptor left, GpuKernelDescriptor right) {
        return left.kernelName().equals(right.kernelName())
                && left.kernelResource().equals(right.kernelResource())
                && left.irGpuResource().equals(right.irGpuResource())
                && left.parameterDescriptors().equals(right.parameterDescriptors());
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Method variant " + label + " must not be blank");
        }
        return value.trim();
    }
}
