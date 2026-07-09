package net.sixik.ga_utils.javatogpu.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GpuRuntimeMethodVariantRegistryTest {

    @Test
    void ordersProvidersAndFiltersRegistrationsByGroup() {
        GpuRuntimeMethodVariantRegistry registry = GpuRuntimeMethodVariantRegistry.of(List.of(
                provider("provider-b", registration("other", "b", "b.cl")),
                provider("provider-a", registration("noise", "a", "a.cl"))
        ));

        assertEquals(List.of("provider-a", "provider-b"), registry.providers().stream()
                .map(GpuRuntimeMethodVariantProvider::providerId)
                .toList());
        assertEquals(List.of("a"), registry.variants("noise").stream()
                .map(GpuRuntimeMethodVariantRegistration::variantId)
                .toList());
    }

    @Test
    void rejectsDuplicateProviderIds() {
        assertThrows(IllegalArgumentException.class, () -> GpuRuntimeMethodVariantRegistry.of(List.of(
                provider("duplicate", registration("noise", "a", "a.cl")),
                provider("duplicate", registration("noise", "b", "b.cl"))
        )));
    }

    @Test
    void rejectsConflictingCrossModuleVariantIds() {
        assertThrows(IllegalArgumentException.class, () -> GpuRuntimeMethodVariantRegistry.of(List.of(
                provider("provider-a", registration("noise", "same", "a.cl")),
                provider("provider-b", registration("noise", "same", "b.cl"))
        )));
    }

    private static GpuRuntimeMethodVariantProvider provider(
            String providerId,
            GpuRuntimeMethodVariantRegistration registration
    ) {
        return new GpuRuntimeMethodVariantProvider() {
            @Override
            public String providerId() {
                return providerId;
            }

            @Override
            public List<GpuRuntimeMethodVariantRegistration> variants() {
                return List.of(registration);
            }
        };
    }

    private static GpuRuntimeMethodVariantRegistration registration(
            String groupId,
            String variantId,
            String resource
    ) {
        return new GpuRuntimeMethodVariantRegistration(
                groupId,
                variantId,
                new GpuKernelDescriptor(variantId, resource, "kernel", List.of())
        );
    }
}
