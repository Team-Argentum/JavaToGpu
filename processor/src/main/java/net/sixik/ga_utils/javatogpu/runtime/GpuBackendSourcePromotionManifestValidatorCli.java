package net.sixik.ga_utils.javatogpu.runtime;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Command-line entrypoint for fail-closed validation of a manual production-promotion manifest. */
public final class GpuBackendSourcePromotionManifestValidatorCli {

    private GpuBackendSourcePromotionManifestValidatorCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Expected candidate gate path, manifest path, validation output path, and expected Git SHA"
            );
        }
        Path candidatePath = Path.of(args[0]);
        Path manifestPath = Path.of(args[1]);
        Path outputPath = Path.of(args[2]);
        byte[] candidateBytes = readRequired(candidatePath, "production candidate gate");
        byte[] manifestBytes = readRequired(manifestPath, "manual production-promotion manifest");
        GpuBackendSourcePromotionManifest.Validation validation = GpuBackendSourcePromotionManifest.validate(
                load(candidateBytes),
                candidateBytes,
                load(manifestBytes),
                args[3]
        );
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputPath, validation.toPropertiesText(), StandardCharsets.UTF_8);
        if (!validation.valid()) {
            throw new IllegalStateException(
                    "Production-promotion manifest is blocked for " + manifestPath + ": " + validation.firstBlocker()
            );
        }
        System.out.println("Production-promotion manifest OK: approval="
                + validation.approvalId()
                + ", kernels="
                + validation.kernelCount()
                + ", production=disabled");
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
