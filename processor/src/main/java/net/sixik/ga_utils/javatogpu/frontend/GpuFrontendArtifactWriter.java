package net.sixik.ga_utils.javatogpu.frontend;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionFailurePolicy;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactSerializer;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuExtensionParticipationMetadata;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Writes the paired frontend artifacts that Java and ASM frontends hand to runtime packaging.
 */
public final class GpuFrontendArtifactWriter {

    static final String ARTIFACT_WRITER_EXTENSION_ID = "artifact-writer:frontend";
    static final String ARTIFACT_WRITER_EXTENSION_VERSION = "frontend-artifact-writer-v1";

    private GpuFrontendArtifactWriter() {
    }

    public static void write(
            GpuFrontendCompilationResult result,
            ArtifactSink sink
    ) throws IOException {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(sink, "sink");
        writeText(result.openClResource(), result.openClSource(), sink);
        writeText(
                result.irGpuResource(),
                IrGpuArtifactSerializer.serialize(withArtifactWriterParticipation(result)),
                sink
        );
    }

    private static net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact withArtifactWriterParticipation(
            GpuFrontendCompilationResult result
    ) {
        ArrayList<IrGpuExtensionParticipationMetadata> metadata = new ArrayList<>(
                result.irGpuArtifact().extensionParticipationMetadata()
        );
        metadata.add(new IrGpuExtensionParticipationMetadata(
                "artifact-writer",
                ARTIFACT_WRITER_EXTENSION_ID,
                ARTIFACT_WRITER_EXTENSION_VERSION,
                GpuExtensionPhase.ARTIFACT_EMISSION,
                GpuExtensionPermission.READ_ONLY,
                "frontend artifact emission",
                GpuExtensionExecutionOutcome.SUCCEEDED,
                GpuExtensionFailurePolicy.CONTINUE,
                true,
                "none",
                "frontend artifact writer emitted paired OpenCL and IrGpu resources",
                List.of(
                        "openClResource=" + result.openClResource(),
                        "irGpuResource=" + result.irGpuResource(),
                        "openClSource.bytes=" + byteLength(result.openClSource()),
                        "irGpuManifest.format=javatogpu.irgpu.v1"
                )
        ));
        return result.irGpuArtifact().withExtensionParticipationMetadata(metadata);
    }

    private static int byteLength(String value) {
        return value == null ? 0 : value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
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
