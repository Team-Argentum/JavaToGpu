package net.sixik.ga_utils.javatogpu.runtime.validation;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourcePromotionActivationGate;
import net.sixik.ga_utils.javatogpu.runtime.GpuProductionActivationToken;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Command-line entrypoint for the controlled opt-in source-promotion activation gate. */
public final class GpuBackendSourcePromotionActivationGateCli {

    private GpuBackendSourcePromotionActivationGateCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Expected candidate gate path, manifest validation path, controlled source-switching path, and output path"
            );
        }
        Path candidatePath = Path.of(args[0]);
        Path manifestValidationPath = Path.of(args[1]);
        Path controlledSourceSwitchingPath = Path.of(args[2]);
        Path outputPath = Path.of(args[3]);
        GpuBackendSourcePromotionActivationGate gate = GpuBackendSourcePromotionActivationGate.from(
                load(candidatePath, "production candidate gate"),
                load(manifestValidationPath, "manual promotion manifest validation"),
                load(controlledSourceSwitchingPath, "controlled source-switching validation")
        );
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        byte[] artifactBytes = gate.toPropertiesText().getBytes(StandardCharsets.UTF_8);
        Files.write(outputPath, artifactBytes);
        Files.writeString(
                outputPath.resolveSibling(outputPath.getFileName() + ".sha256"),
                GpuProductionActivationToken.sha256(artifactBytes) + "\n",
                StandardCharsets.UTF_8
        );
        if (!gate.activationReady()) {
            throw new IllegalStateException(
                    "Controlled activation gate is blocked for " + outputPath + ": " + gate.firstBlocker()
            );
        }
        System.out.println("Controlled activation gate OK: kernels="
                + gate.controlledCoverageCount()
                + "/"
                + gate.kernelCount()
                + ", approval="
                + gate.approvalId()
                + ", defaultRuntime=false");
    }

    private static Properties load(Path path, String label) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Missing " + label + ": " + path);
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }
}
