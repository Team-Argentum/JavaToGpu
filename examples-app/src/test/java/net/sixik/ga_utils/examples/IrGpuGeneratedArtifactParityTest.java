package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactParser;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceReconstructionResult;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrGpuSourceReconstructor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrGpuGeneratedArtifactParityTest {

    @Test
    void generatedIrGpuArtifactsReconstructToGeneratedOpenClSources() throws Exception {
        List<Path> classpathRoots = javatogpuClasspathRoots();
        List<Path> manifests = generatedIrGpuManifests(classpathRoots);

        assertEquals(34, manifests.size(), "examples-app should keep all showcase IrGpu artifacts under parity coverage");

        ArrayList<String> failures = new ArrayList<>();
        for (Path manifestPath : manifests) {
            IrGpuArtifact artifact = IrGpuArtifactParser.parse(Files.readString(manifestPath, StandardCharsets.UTF_8));
            Path classpathRoot = classpathRootFor(classpathRoots, manifestPath);
            Path descriptorSourcePath = classpathRoot.resolve(artifact.derivedOpenClResource()).normalize();
            if (!Files.isRegularFile(descriptorSourcePath)) {
                failures.add(manifestPath.getFileName() + ": missing paired OpenCL source " + artifact.derivedOpenClResource());
                continue;
            }

            GpuBackendSourceReconstructionResult reconstruction = OpenClIrGpuSourceReconstructor.INSTANCE.reconstruct(
                    artifact,
                    artifact.derivedOpenClResource(),
                    Files.readString(descriptorSourcePath, StandardCharsets.UTF_8)
            );
            if (!reconstruction.blockers().isEmpty()
                    || !reconstruction.diagnostics().contains("sourceParity.matched=true")) {
                failures.add(manifestPath.getFileName()
                        + ": blockers=" + reconstruction.blockers()
                        + ", diagnostics=" + reconstruction.diagnostics());
            }
        }

        assertTrue(failures.isEmpty(), String.join(System.lineSeparator(), failures));
    }

    private static List<Path> javatogpuClasspathRoots() throws IOException, URISyntaxException {
        ArrayList<Path> roots = new ArrayList<>();
        Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources("javatogpu");
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            if ("file".equals(resource.getProtocol())) {
                roots.add(Path.of(resource.toURI()).getParent());
            }
        }
        return roots;
    }

    private static List<Path> generatedIrGpuManifests(List<Path> classpathRoots) throws IOException {
        ArrayList<Path> manifests = new ArrayList<>();
        for (Path root : classpathRoots) {
            Path artifactRoot = root.resolve("javatogpu");
            if (!Files.isDirectory(artifactRoot)) {
                continue;
            }
            try (var stream = Files.walk(artifactRoot)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".irgpu.properties"))
                        .sorted()
                        .forEach(manifests::add);
            }
        }
        return List.copyOf(manifests);
    }

    private static Path classpathRootFor(List<Path> classpathRoots, Path manifestPath) {
        return classpathRoots.stream()
                .filter(root -> manifestPath.normalize().startsWith(root.normalize()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No classpath root found for " + manifestPath));
    }
}
