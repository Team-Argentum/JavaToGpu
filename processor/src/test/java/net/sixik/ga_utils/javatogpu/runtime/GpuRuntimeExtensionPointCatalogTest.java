package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPass;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationProvider;
import net.sixik.ga_utils.javatogpu.runtime.validation.GpuRuntimeExtensionPointCatalog;
import net.sixik.ga_utils.javatogpu.runtime.validation.GpuRuntimePackageTaxonomy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GpuRuntimeExtensionPointCatalogTest {

    @Test
    void catalogContainsSupportedServiceLoaderExtensionPoints() {
        Set<String> serviceTypes = serviceTypes();

        assertTrue(serviceTypes.contains(GpuRuntimeLogService.class.getName()));
        assertTrue(serviceTypes.contains(GpuRuntimeLifecycleService.class.getName()));
        assertTrue(serviceTypes.contains(GpuRuntimeLifecycleEventListener.class.getName()));
        assertTrue(serviceTypes.contains(GpuIrValidationProvider.class.getName()));
        assertTrue(serviceTypes.contains(GpuRuntimeIrOptimizationPass.class.getName()));
        assertTrue(serviceTypes.contains(GpuRuntimeIrPeepholeRule.class.getName()));
        assertTrue(serviceTypes.contains(GpuRuntimeIrOptimizer.class.getName()));
        assertTrue(serviceTypes.contains(GpuBackendCompilerFeedbackProvider.class.getName()));
        assertTrue(serviceTypes.contains(GpuRuntimeBackendProvider.class.getName()));
        assertTrue(serviceTypes.contains(GpuRuntimeNativeMemoryService.class.getName()));
        assertTrue(serviceTypes.contains(GpuRuntimeDevicePolicy.class.getName()));
        assertTrue(serviceTypes.contains(GpuBackendHook.class.getName()));
        assertTrue(serviceTypes.contains(GpuBackendPolicyContributor.class.getName()));
        assertTrue(serviceTypes.contains(GpuRuntimeBackendScoreContributor.class.getName()));
        assertTrue(serviceTypes.contains(GpuBackendDiscoveryContributor.class.getName()));
        assertTrue(serviceTypes.contains(GpuBackendLoweringHook.class.getName()));
        assertTrue(serviceTypes.contains(GpuBackendCompilationHook.class.getName()));
        assertTrue(serviceTypes.contains(GpuBackendInvocationHook.class.getName()));
        assertTrue(serviceTypes.contains(GpuBackendArtifactHook.class.getName()));
        assertTrue(serviceTypes.contains(GpuRuntimeMethodVariantProvider.class.getName()));
        assertTrue(serviceTypes.contains(GpuIrPass.class.getName()));
    }

    @Test
    void serviceTypesAreUniqueAndHaveRegistrationFiles() {
        List<GpuRuntimeExtensionPointCatalog.ExtensionPoint> points = GpuRuntimeExtensionPointCatalog.all();
        HashSet<String> serviceTypes = new HashSet<>();
        for (GpuRuntimeExtensionPointCatalog.ExtensionPoint point : points) {
            assertTrue(serviceTypes.add(point.serviceTypeName()), point.serviceTypeName());
            assertEquals("META-INF/services/" + point.serviceTypeName(), point.registrationFile());
            assertFalse(point.domainPackage().isBlank(), point.serviceTypeName());
            assertFalse(point.permissionModel().isBlank(), point.serviceTypeName());
            assertFalse(point.summary().isBlank(), point.serviceTypeName());
        }
    }

    @Test
    void publicExtensionPointsExcludeCompilerInternalIrPass() {
        assertTrue(GpuRuntimeExtensionPointCatalog.findByServiceType(GpuIrPass.class.getName()).orElseThrow().audience()
                == GpuRuntimePackageTaxonomy.Audience.IMPLEMENTATION_DETAIL);
        assertTrue(GpuRuntimeExtensionPointCatalog.publicExtensionPoints().stream()
                .noneMatch(point -> point.serviceTypeName().equals(GpuIrPass.class.getName())));
    }

    @Test
    void extensionContractDocumentMentionsPublicServiceTypes() throws IOException {
        String document = Files.readString(projectRoot().resolve("docs/Public-API-And-Extension-Contract.md"));
        List<String> missing = GpuRuntimeExtensionPointCatalog.publicExtensionPoints().stream()
                .map(GpuRuntimeExtensionPointCatalog.ExtensionPoint::serviceTypeName)
                .filter(serviceTypeName -> !document.contains(serviceTypeName))
                .sorted()
                .toList();

        assertTrue(
                missing.isEmpty(),
                "Document public extension ServiceLoader types in Public-API-And-Extension-Contract.md: " + missing
        );
    }

    @Test
    void markdownRendersServiceLoaderTable() {
        String markdown = GpuRuntimeExtensionPointCatalog.toMarkdown();
        assertTrue(markdown.contains("| Service type | Audience | Domain | Permission | Harness | Summary |"));
        assertTrue(markdown.contains(GpuRuntimeLogService.class.getName()));
        assertTrue(markdown.contains(GpuRuntimeBackendProvider.class.getName()));
        assertFalse(markdown.contains(GpuIrPass.class.getName()));
    }

    private static Set<String> serviceTypes() {
        return GpuRuntimeExtensionPointCatalog.all().stream()
                .map(GpuRuntimeExtensionPointCatalog.ExtensionPoint::serviceTypeName)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate project root");
    }
}
