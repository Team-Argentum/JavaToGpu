package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class GpuRuntimeIrArtifactLoader {

    private GpuRuntimeIrArtifactLoader() {
    }

    public static Optional<IrGpuArtifact> load(GpuKernelDescriptor descriptor) {
        if (descriptor == null || descriptor.irGpuResource() == null || descriptor.irGpuResource().isBlank()) {
            return Optional.empty();
        }
        return load(descriptor.irGpuResource(), Thread.currentThread().getContextClassLoader())
                .or(() -> load(descriptor.irGpuResource(), GpuRuntimeIrArtifactLoader.class.getClassLoader()));
    }

    public static Optional<IrGpuArtifact> load(String resourcePath, ClassLoader classLoader) {
        if (resourcePath == null || resourcePath.isBlank() || classLoader == null) {
            return Optional.empty();
        }
        try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return Optional.empty();
            }
            String manifest = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return Optional.of(IrGpuArtifactParser.parse(manifest));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load IrGpu artifact resource: " + resourcePath, exception);
        }
    }
}
