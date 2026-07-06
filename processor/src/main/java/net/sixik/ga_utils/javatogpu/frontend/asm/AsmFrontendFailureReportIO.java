package net.sixik.ga_utils.javatogpu.frontend.asm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Writes ASM preflight reports as stable .properties artifacts for CI and build tooling.
 */
public final class AsmFrontendFailureReportIO {

    private static final String DEFAULT_PREFIX = "asmReport";
    private static final String COMMENT = "JavaToGpu ASM frontend preflight report";

    private AsmFrontendFailureReportIO() {
    }

    public static void write(Path path, AsmFrontendFailureReport report) throws IOException {
        write(path, report, DEFAULT_PREFIX);
    }

    public static void write(Path path, AsmFrontendFailureReport report, String prefix) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(report, "report");

        Properties properties = toProperties(report, prefix);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            properties.store(outputStream, COMMENT);
        }
    }

    public static Optional<Properties> readIfExists(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        }
        return Optional.of(properties);
    }

    public static Properties toProperties(AsmFrontendFailureReport report) {
        return toProperties(report, DEFAULT_PREFIX);
    }

    public static Properties toProperties(AsmFrontendFailureReport report, String prefix) {
        Objects.requireNonNull(report, "report");

        Properties properties = new Properties();
        for (Map.Entry<String, String> entry : report.artifactFields(prefix).entrySet()) {
            properties.setProperty(entry.getKey(), entry.getValue());
        }
        return properties;
    }
}
