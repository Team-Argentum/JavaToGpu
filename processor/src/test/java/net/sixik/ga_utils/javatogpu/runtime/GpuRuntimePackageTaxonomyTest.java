package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.runtime.validation.GpuRuntimePackageTaxonomy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GpuRuntimePackageTaxonomyTest {

    @Test
    void classifiesRepresentativeRuntimeRootClasses() {
        assertEquals(
                GpuRuntimePackageTaxonomy.Domain.USER_API,
                GpuRuntimePackageTaxonomy.classifyRootRuntimeClass("GpuRuntime").orElseThrow()
        );
        assertEquals(
                GpuRuntimePackageTaxonomy.Domain.BACKEND_SPI,
                GpuRuntimePackageTaxonomy.classifyRootRuntimeClass("GpuBackendExecutionPipeline").orElseThrow()
        );
        assertEquals(
                GpuRuntimePackageTaxonomy.Domain.BACKEND_HOOKS,
                GpuRuntimePackageTaxonomy.classifyRootRuntimeClass("GpuBackendHookRegistry").orElseThrow()
        );
        assertEquals(
                GpuRuntimePackageTaxonomy.Domain.SELECTION_AND_DEVICE_POLICY,
                GpuRuntimePackageTaxonomy.classifyRootRuntimeClass("GpuRuntimeDeviceSelection").orElseThrow()
        );
        assertEquals(
                GpuRuntimePackageTaxonomy.Domain.METHOD_TESTS,
                GpuRuntimePackageTaxonomy.classifyRootRuntimeClass("GpuRuntimeMethodTestProbes").orElseThrow()
        );
        assertEquals(
                GpuRuntimePackageTaxonomy.Domain.IR_OPTIMIZATION,
                GpuRuntimePackageTaxonomy.classifyRootRuntimeClass("GpuRuntimeIrOptimizer").orElseThrow()
        );
        assertEquals(
                GpuRuntimePackageTaxonomy.Domain.OBSERVABILITY,
                GpuRuntimePackageTaxonomy.classifyRootRuntimeClass("GpuRuntimeLifecycleEventBus").orElseThrow()
        );
        assertEquals(
                GpuRuntimePackageTaxonomy.Domain.NATIVE_MEMORY,
                GpuRuntimePackageTaxonomy.classifyRootRuntimeClass("GpuRuntimeNativeMemoryService").orElseThrow()
        );
    }

    @Test
    void everyCurrentRootRuntimeClassHasATaxonomyDomain() throws IOException {
        Path runtimeDirectory = runtimeSourceDirectory();
        List<String> unclassified;
        try (Stream<Path> files = Files.list(runtimeDirectory)) {
            unclassified = files
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.java$", ""))
                    .filter(simpleName -> !"package-info".equals(simpleName))
                    .filter(simpleName -> GpuRuntimePackageTaxonomy.classifyRootRuntimeClass(simpleName).isEmpty())
                    .sorted()
                    .toList();
        }

        assertTrue(
                unclassified.isEmpty(),
                "New root runtime classes must be classified or moved to a domain package: " + unclassified
        );
    }

    @Test
    void everyDomainHasARecommendedPackage() {
        for (GpuRuntimePackageTaxonomy.Domain domain : GpuRuntimePackageTaxonomy.Domain.values()) {
            String recommendedPackage = GpuRuntimePackageTaxonomy.recommendedPackage(domain);
            assertFalse(recommendedPackage == null || recommendedPackage.isBlank(), domain.name());
        }
    }

    @Test
    void recommendedRuntimeDomainPackagesExist() {
        Path runtimeDirectory = runtimeSourceDirectory();
        for (GpuRuntimePackageTaxonomy.Domain domain : GpuRuntimePackageTaxonomy.Domain.values()) {
            String recommendedPackage = GpuRuntimePackageTaxonomy.recommendedPackage(domain);
            if (!recommendedPackage.startsWith("net.sixik.ga_utils.javatogpu.runtime.")) {
                continue;
            }

            String relativePackage = recommendedPackage.substring("net.sixik.ga_utils.javatogpu.runtime.".length());
            Path packageInfo = runtimeDirectory
                    .resolve(relativePackage.replace('.', '/'))
                    .resolve("package-info.java");
            assertTrue(
                    Files.isRegularFile(packageInfo),
                    domain.name() + " recommended package must exist: " + recommendedPackage
            );
        }
    }

    private static Path runtimeSourceDirectory() {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        Path processorLocal = workingDirectory.resolve("src/main/java/net/sixik/ga_utils/javatogpu/runtime");
        if (Files.isDirectory(processorLocal)) {
            return processorLocal;
        }
        Path rootLocal = workingDirectory.resolve("processor/src/main/java/net/sixik/ga_utils/javatogpu/runtime");
        if (Files.isDirectory(rootLocal)) {
            return rootLocal;
        }
        throw new IllegalStateException("Cannot locate runtime source directory from " + workingDirectory);
    }
}
