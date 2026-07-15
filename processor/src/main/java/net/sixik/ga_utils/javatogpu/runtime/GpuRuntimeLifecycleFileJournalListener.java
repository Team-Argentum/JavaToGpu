package net.sixik.ga_utils.javatogpu.runtime;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Optional file-backed lifecycle journal listener for local diagnostics and examples.
 *
 * <p>The listener is safe to register by default because it is a no-op until
 * {@value #JOURNAL_FILE_PROPERTY} is set. When enabled, it records every runtime lifecycle event as either JSONL or an
 * indexed {@code .properties} file. Listener failures are isolated by {@link GpuRuntimeLifecycleEventBus}, so a broken
 * journal path cannot control compilation or execution flow.</p>
 */
public final class GpuRuntimeLifecycleFileJournalListener implements GpuRuntimeLifecycleService {

    public static final String JOURNAL_FILE_PROPERTY = "javatogpu.runtime.lifecycleJournalFile";
    public static final String JOURNAL_FORMAT_PROPERTY = "javatogpu.runtime.lifecycleJournalFormat";

    private static final Object WRITE_LOCK = new Object();

    @Override
    public void onRuntimeLifecycleEvent(GpuRuntimeLifecycleEvent event) {
        Optional<Path> journalFile = configuredJournalFile();
        if (journalFile.isEmpty()) {
            return;
        }
        Path path = journalFile.orElseThrow();
        JournalFormat format = JournalFormat.from(path, System.getProperty(JOURNAL_FORMAT_PROPERTY));
        synchronized (WRITE_LOCK) {
            try {
                Files.createDirectories(parentOrCurrent(path));
                if (format == JournalFormat.PROPERTIES) {
                    writeProperties(path, event);
                } else {
                    writeJsonl(path, event);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to write runtime lifecycle journal: " + path, exception);
            }
        }
    }

    @Override
    public String extensionId() {
        return "runtime.lifecycle.file-journal";
    }

    @Override
    public String extensionVersion() {
        return "1";
    }

    @Override
    public int extensionOrder() {
        return 10_000;
    }

    private static Optional<Path> configuredJournalFile() {
        String value = System.getProperty(JOURNAL_FILE_PROPERTY);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(value.trim()));
    }

    private static void writeJsonl(Path path, GpuRuntimeLifecycleEvent event) throws IOException {
        Files.writeString(
                path,
                toJson(event) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
        );
    }

    private static void writeProperties(Path path, GpuRuntimeLifecycleEvent event) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        int index = parseInt(properties.getProperty("event.count"), 0);
        properties.setProperty("journal.format", "properties");
        properties.setProperty("journal.version", "1");
        properties.setProperty("event.count", Integer.toString(index + 1));
        event.artifactFields("event." + index).forEach(properties::setProperty);
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            properties.store(writer, "JavaToGpu runtime lifecycle journal");
        }
    }

    private static String toJson(GpuRuntimeLifecycleEvent event) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("kind", event.kind().name());
        fields.put("backendTarget", event.backendTarget().name());
        fields.put("kernelResource", event.kernelResource());
        fields.put("optimizationProfile", event.optimizationProfile());
        fields.put("message", event.message());
        fields.put("timestampEpochMillis", Long.toString(System.currentTimeMillis()));

        StringBuilder builder = new StringBuilder();
        builder.append('{');
        appendJsonStringFields(builder, fields);
        builder.append(",\"fields\":{");
        appendJsonStringFields(builder, event.fields());
        builder.append("}}");
        return builder.toString();
    }

    private static void appendJsonStringFields(StringBuilder builder, Map<String, String> fields) {
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append('"')
                    .append(jsonEscape(entry.getKey()))
                    .append("\":\"")
                    .append(jsonEscape(entry.getValue()))
                    .append('"');
            first = false;
        }
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (current < 0x20) {
                        builder.append(String.format("\\u%04x", (int) current));
                    } else {
                        builder.append(current);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static Path parentOrCurrent(Path path) {
        Path parent = path.toAbsolutePath().normalize().getParent();
        return parent == null ? Path.of(".") : parent;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private enum JournalFormat {
        JSONL,
        PROPERTIES;

        private static JournalFormat from(Path path, String configuredFormat) {
            String normalized = configuredFormat == null ? "" : configuredFormat.trim().toLowerCase(java.util.Locale.ROOT);
            if (normalized.isBlank() || "auto".equals(normalized)) {
                String fileName = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                return fileName.endsWith(".properties") ? PROPERTIES : JSONL;
            }
            return switch (normalized) {
                case "jsonl", "json" -> JSONL;
                case "properties", "props" -> PROPERTIES;
                default -> throw new IllegalArgumentException(
                        "Unsupported runtime lifecycle journal format '" + configuredFormat + "'; use jsonl or properties"
                );
            };
        }
    }
}
