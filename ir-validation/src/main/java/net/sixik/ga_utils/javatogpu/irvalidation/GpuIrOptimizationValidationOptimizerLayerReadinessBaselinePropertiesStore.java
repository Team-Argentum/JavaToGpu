package net.sixik.ga_utils.javatogpu.irvalidation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Small `.properties` bridge for persisted optimizer-layer readiness baselines.
 *
 * <p>The store only reads and writes the compact baseline snapshot fields. It deliberately does
 * not validate current reports or enable optimizer mutation; callers decide whether to use strict
 * comparison APIs or the fail-closed CI helpers after loading the file.</p>
 */
public final class GpuIrOptimizationValidationOptimizerLayerReadinessBaselinePropertiesStore {
    private static final String STORE_COMMENT = "JavaToGpu optimizer-layer readiness baseline";

    private GpuIrOptimizationValidationOptimizerLayerReadinessBaselinePropertiesStore() {
    }

    public static void save(
            Path path,
            GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot snapshot
    ) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        saveFields(path, snapshot.artifactFields());
    }

    public static void saveFields(
            Path path,
            Map<String, String> fields
    ) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(fields, "fields");
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Properties properties = new Properties();
        fields.forEach((key, value) -> properties.setProperty(key, value == null ? "" : value));
        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, STORE_COMMENT);
        }
    }

    public static Map<String, String> loadFields(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        properties.stringPropertyNames().stream()
                .sorted()
                .forEach(key -> fields.put(key, properties.getProperty(key)));
        return Collections.unmodifiableMap(fields);
    }

    public static GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot load(Path path) throws IOException {
        return GpuIrOptimizationValidationOptimizerLayerReadinessBaselineSnapshot.fromArtifactFields(loadFields(path));
    }
}
