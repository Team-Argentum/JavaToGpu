package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.runtime.validation.GpuRuntimeCompatibilityFacadeCatalog;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
final class GpuRuntimeCompatibilityFacadeCatalogTest {

    @Test
    void catalogContainsTrackedCompatibilityFacades() {
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuRuntimeDeviceDiscovery.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuRuntimeBackendSelectionOrchestrator.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuRuntimeWorkloadHintInference.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuRuntimeWorkloadHintBackendScoreContributor.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuRuntimeInferredWorkloadHintBackendScoreContributor.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuBackendCompilerFeedbackScoreContributor.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuLauncherNaming.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuRuntimeCompileRequestFactory.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuBackendCompilerFeedbackRegistry.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuRuntimeCompileArtifactDumper.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuRuntimeIrPeepholePass.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuRuntimeRegisterPressureAnalyzer.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuRuntimeClampPeepholeRule.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuRuntimeNativeMemoryServiceRegistry.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuRuntimeLifecycleEventBus.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuRuntimeLogBus.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuRuntimeLifecycleFields.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuRuntimeSystemStreamLogService.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuRuntimeMethodVariantRegistry.class.getName()).isPresent());
        assertTrue(GpuRuntimeCompatibilityFacadeCatalog.findByRootClass(GpuRuntimeMethodVariantSelector.class.getName()).isPresent());
    }

    @Test
    void rootAndPreferredClassesAreUniqueAndDomainScoped() {
        HashSet<String> rootClasses = new HashSet<>();
        for (GpuRuntimeCompatibilityFacadeCatalog.CompatibilityFacade facade : GpuRuntimeCompatibilityFacadeCatalog.all()) {
            assertTrue(rootClasses.add(facade.rootClassName()), facade.rootClassName());
            assertTrue(allowedFacadeDomain(facade.domainPackage()), facade.rootClassName());
            assertTrue(facade.preferredClassName().contains("." + facade.domainPackage() + "."), facade.preferredClassName());
            assertFalse(facade.purpose().isBlank(), facade.rootClassName());
        }
    }

    @Test
    void rootFacadeSourcesMentionCompatibilityAndDomainPackage() throws IOException {
        Path runtimeRoot = runtimeSourceDirectory();
        List<String> missing = GpuRuntimeCompatibilityFacadeCatalog.all().stream()
                .filter(facade -> !sourceMentionsCompatibilityAndDomain(runtimeRoot, facade))
                .map(GpuRuntimeCompatibilityFacadeCatalog.CompatibilityFacade::rootClassName)
                .sorted()
                .toList();

        assertTrue(
                missing.isEmpty(),
                "Root compatibility facades must document/delegate to their domain package: " + missing
        );
    }

    @Test
    void apiOverviewMentionsTrackedCompatibilityFacades() throws IOException {
        String overview = Files.readString(projectRoot().resolve("docs/API-Overview.md"));
        List<String> missing = GpuRuntimeCompatibilityFacadeCatalog.all().stream()
                .flatMap(facade -> java.util.stream.Stream.of(facade.rootClassName(), facade.preferredClassName()))
                .distinct()
                .filter(className -> !overview.contains(className))
                .sorted()
                .toList();

        assertTrue(
                missing.isEmpty(),
                "Document runtime compatibility facade navigation in API-Overview.md: " + missing
        );
    }

    @Test
    void markdownRendersCompatibilityFacadeTable() {
        String markdown = GpuRuntimeCompatibilityFacadeCatalog.toMarkdown();
        assertTrue(markdown.contains("| Root facade | Prefer for new code | Domain | Audience | Purpose |"));
        assertTrue(markdown.contains(GpuRuntimeDeviceDiscovery.class.getName()));
        assertTrue(markdown.contains("runtime.selection"));
        assertTrue(markdown.contains("runtime.launch"));
        assertTrue(markdown.contains("runtime.diagnostics"));
        assertTrue(markdown.contains("runtime.optimization"));
        assertTrue(markdown.contains("runtime.memory"));
        assertTrue(markdown.contains("runtime.observability"));
        assertTrue(markdown.contains("runtime.variants"));
    }

    private static boolean allowedFacadeDomain(String domainPackage) {
        return domainPackage.equals("runtime.selection")
                || domainPackage.equals("runtime.launch")
                || domainPackage.equals("runtime.diagnostics")
                || domainPackage.equals("runtime.optimization")
                || domainPackage.equals("runtime.memory")
                || domainPackage.equals("runtime.observability")
                || domainPackage.equals("runtime.variants");
    }

    private static boolean sourceMentionsCompatibilityAndDomain(
            Path runtimeRoot,
            GpuRuntimeCompatibilityFacadeCatalog.CompatibilityFacade facade
    ) {
        String simpleName = facade.rootClassName().substring(facade.rootClassName().lastIndexOf('.') + 1);
        Path source = runtimeRoot.resolve(simpleName + ".java");
        if (!Files.isRegularFile(source)) {
            return false;
        }
        try {
            String text = Files.readString(source);
            return text.contains("Compatibility facade")
                    && text.contains("net.sixik.ga_utils.javatogpu." + facade.domainPackage());
        } catch (IOException exception) {
            return false;
        }
    }

    private static Path runtimeSourceDirectory() {
        return projectRoot().resolve("processor/src/main/java/net/sixik/ga_utils/javatogpu/runtime");
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
