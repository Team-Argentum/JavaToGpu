package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

final class OpenClWorkloadValidationSummaryIO {

    private OpenClWorkloadValidationSummaryIO() {
    }

    static void write(Path path, OpenClWorkloadValidationSummary summary) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("completedAtUtc", summary.completedAtUtc().toString());
        properties.setProperty("status", summary.status());
        properties.setProperty("perlinStatus", summary.perlinStatus());
        properties.setProperty("packedBlobStatus", summary.packedBlobStatus());
        properties.setProperty("packedNumericStatus", summary.packedNumericStatus());
        properties.setProperty("packedGrid3dStatus", summary.packedGrid3dStatus());
        properties.setProperty("imageStatus", summary.imageStatus());
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            properties.store(outputStream, "JavaToGpu OpenCL workload validation summary");
        }
    }

    static Optional<OpenClWorkloadValidationSummary> readIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        }
        return Optional.of(new OpenClWorkloadValidationSummary(
                Instant.parse(properties.getProperty("completedAtUtc")),
                properties.getProperty("status", "unknown"),
                properties.getProperty("perlinStatus", "unknown"),
                properties.getProperty("packedBlobStatus", "unknown"),
                properties.getProperty("packedNumericStatus", "unknown"),
                properties.getProperty("packedGrid3dStatus", "unknown"),
                properties.getProperty("imageStatus", "unknown")
        ));
    }
}
