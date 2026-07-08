package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactSerializer;

import java.io.IOException;
import java.io.Writer;
import java.util.Objects;

/**
 * Writes the paired frontend artifacts that Java and ASM frontends hand to runtime packaging.
 */
public final class GpuFrontendArtifactWriter {

    private GpuFrontendArtifactWriter() {
    }

    public static void write(
            GpuFrontendCompilationResult result,
            ArtifactSink sink
    ) throws IOException {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(sink, "sink");
        writeText(result.openClResource(), result.openClSource(), sink);
        writeText(result.irGpuResource(), IrGpuArtifactSerializer.serialize(result.irGpuArtifact()), sink);
    }

    private static void writeText(String resourcePath, String text, ArtifactSink sink) throws IOException {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("Frontend artifact resource path must not be blank");
        }
        try (Writer writer = sink.open(resourcePath)) {
            writer.write(text == null ? "" : text);
        }
    }

    @FunctionalInterface
    public interface ArtifactSink {
        Writer open(String resourcePath) throws IOException;
    }
}
