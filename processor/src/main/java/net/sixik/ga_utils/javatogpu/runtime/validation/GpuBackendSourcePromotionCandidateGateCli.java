package net.sixik.ga_utils.javatogpu.runtime.validation;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourcePromotionCandidateGate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Command-line entrypoint for generating and validating the real-workload production-candidate gate. */
public final class GpuBackendSourcePromotionCandidateGateCli {

    private GpuBackendSourcePromotionCandidateGateCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Expected workload gate path, controlled source-switching summary path, and output path"
            );
        }
        Path workloadGatePath = Path.of(args[0]);
        Path controlledSummaryPath = Path.of(args[1]);
        Path outputPath = Path.of(args[2]);
        Properties workloadGate = load(workloadGatePath, "backend source-promotion workload gate");
        Properties controlledSummary = load(controlledSummaryPath, "controlled source-switching summary");
        GpuBackendSourcePromotionCandidateGate candidate =
                GpuBackendSourcePromotionCandidateGate.from(workloadGate, controlledSummary);
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputPath, candidate.toPropertiesText(), StandardCharsets.UTF_8);
        if (!candidate.reviewReady()) {
            throw new IllegalStateException(
                    "Production-candidate gate is blocked for " + outputPath + ": " + candidate.firstBlocker()
            );
        }
        System.out.println("Production-candidate gate OK: kernels="
                + candidate.candidateReadyCount()
                + "/"
                + candidate.kernelCount()
                + ", operatorAccepted="
                + candidate.operatorAcceptedCount()
                + "/"
                + candidate.kernelCount());
    }

    private static Properties load(Path path, String label) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Missing " + label + ": " + path);
        }
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        }
        return properties;
    }
}
