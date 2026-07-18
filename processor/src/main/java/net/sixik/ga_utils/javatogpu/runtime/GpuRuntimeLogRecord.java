package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable runtime log payload passed to pluggable logging services.
 */
public record GpuRuntimeLogRecord(
        GpuRuntimeLogLevel level,
        String loggerName,
        String message,
        Map<String, String> fields,
        Throwable throwable
) {

    public GpuRuntimeLogRecord {
        level = level == null ? GpuRuntimeLogLevel.INFO : level;
        loggerName = normalize(loggerName, "net.sixik.ga_utils.javatogpu");
        message = normalize(message, level.name());
        fields = normalizeFields(fields);
    }

    public static GpuRuntimeLogRecord of(GpuRuntimeLogLevel level, String loggerName, String message) {
        return new GpuRuntimeLogRecord(level, loggerName, message, Map.of(), null);
    }

    public GpuRuntimeLogRecord withField(String key, String value) {
        LinkedHashMap<String, String> nextFields = new LinkedHashMap<>(fields);
        nextFields.put(requireFieldKey(key), normalize(value, ""));
        return new GpuRuntimeLogRecord(level, loggerName, message, nextFields, throwable);
    }

    public GpuRuntimeLogRecord withThrowable(Throwable value) {
        return new GpuRuntimeLogRecord(level, loggerName, message, fields, value);
    }

    public Map<String, String> artifactFields(String prefix) {
        String safePrefix = prefix == null || prefix.isBlank() ? "runtimeLog" : prefix.trim();
        LinkedHashMap<String, String> artifactFields = new LinkedHashMap<>();
        artifactFields.put(safePrefix + ".level", level.name());
        artifactFields.put(safePrefix + ".loggerName", loggerName);
        artifactFields.put(safePrefix + ".message", message);
        artifactFields.put(safePrefix + ".throwable.present", Boolean.toString(throwable != null));
        if (throwable != null) {
            artifactFields.put(safePrefix + ".throwable.class", throwable.getClass().getName());
            artifactFields.put(safePrefix + ".throwable.message", normalize(throwable.getMessage(), ""));
        }
        artifactFields.put(safePrefix + ".field.count", Integer.toString(fields.size()));
        int index = 0;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String entryPrefix = safePrefix + ".field." + index;
            artifactFields.put(entryPrefix + ".key", entry.getKey());
            artifactFields.put(entryPrefix + ".value", entry.getValue());
            index++;
        }
        return Collections.unmodifiableMap(artifactFields);
    }

    private static Map<String, String> normalizeFields(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> normalizedFields = new LinkedHashMap<>();
        fields.forEach((key, value) -> normalizedFields.put(requireFieldKey(key), normalize(value, "")));
        return Collections.unmodifiableMap(normalizedFields);
    }

    private static String requireFieldKey(String value) {
        String key = normalize(value, "");
        if (key.isBlank()) {
            throw new IllegalArgumentException("runtime log field key must not be blank");
        }
        return key;
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return Objects.requireNonNullElse(fallback, "");
        }
        return value.trim();
    }
}
