package net.sixik.ga_utils.javatogpu.runtime;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Command-line entrypoint for generating a pending manual promotion manifest template. */
public final class GpuBackendSourcePromotionManifestTemplateCli {

    private GpuBackendSourcePromotionManifestTemplateCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected candidate gate path, output path, and Git SHA");
        }
        Path candidatePath = Path.of(args[0]);
        Path outputPath = Path.of(args[1]);
        byte[] candidateBytes = readRequired(candidatePath, "production candidate gate");
        Properties candidate = load(candidateBytes);
        String template = GpuBackendSourcePromotionManifest.template(candidate, candidateBytes, args[2]);
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputPath, template, StandardCharsets.UTF_8);
        System.out.println("Wrote pending production-promotion manifest template to " + outputPath.toAbsolutePath());
    }

    private static byte[] readRequired(Path path, String label) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Missing " + label + ": " + path);
        }
        return Files.readAllBytes(path);
    }

    private static Properties load(byte[] bytes) throws IOException {
        Properties properties = new Properties();
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            properties.load(input);
        }
        return properties;
    }
}
