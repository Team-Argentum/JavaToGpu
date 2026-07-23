package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable event passed to runtime lifecycle listeners.
 *
 * <p>Events are observational only. Listeners receive already-normalized context fields and must not rely on mutating
 * this object to influence compilation or execution.</p>
 */
public record GpuRuntimeLifecycleEvent(
        GpuRuntimeLifecycleEventKind kind,
        GpuBackendTarget backendTarget,
        String kernelResource,
        String optimizationProfile,
        String message,
        Map<String, String> fields
) {

    public GpuRuntimeLifecycleEvent {
        kind = Objects.requireNonNull(kind, "kind");
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        kernelResource = normalize(kernelResource, "unknown");
        optimizationProfile = normalize(optimizationProfile, "off");
        message = normalize(message, kind.name());
        fields = normalizeFields(fields);
    }

    public static GpuRuntimeLifecycleEvent of(GpuRuntimeLifecycleEventKind kind) {
        return new GpuRuntimeLifecycleEvent(kind, GpuBackendTarget.UNKNOWN, "unknown", "off", kind.name(), Map.of());
    }

    public GpuRuntimeLifecycleEvent withField(String key, String value) {
        LinkedHashMap<String, String> nextFields = new LinkedHashMap<>(fields);
        nextFields.put(requireFieldKey(key), normalize(value, ""));
        return new GpuRuntimeLifecycleEvent(kind, backendTarget, kernelResource, optimizationProfile, message, nextFields);
    }

    public GpuRuntimeLifecycleEvent withFields(Map<String, String> additionalFields) {
        if (additionalFields == null || additionalFields.isEmpty()) {
            return this;
        }
        LinkedHashMap<String, String> nextFields = new LinkedHashMap<>(fields);
        additionalFields.forEach((key, value) -> nextFields.put(requireFieldKey(key), normalize(value, "")));
        return new GpuRuntimeLifecycleEvent(kind, backendTarget, kernelResource, optimizationProfile, message, nextFields);
    }

    public Map<String, String> artifactFields(String prefix) {
        String safePrefix = prefix == null || prefix.isBlank() ? "runtimeLifecycleEvent" : prefix.trim();
        LinkedHashMap<String, String> artifactFields = new LinkedHashMap<>();
        artifactFields.put(safePrefix + ".kind", kind.name());
        artifactFields.put(safePrefix + ".backendTarget", backendTarget.name());
        artifactFields.put(safePrefix + ".kernelResource", kernelResource);
        artifactFields.put(safePrefix + ".optimizationProfile", optimizationProfile);
        artifactFields.put(safePrefix + ".message", message);
        artifactFields.put(safePrefix + ".field.count", Integer.toString(fields.size()));
        int index = 0;
        int runtimeFieldCount = 0;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String entryPrefix = safePrefix + ".field." + index;
            artifactFields.put(entryPrefix + ".key", entry.getKey());
            artifactFields.put(entryPrefix + ".value", entry.getValue());
            if (entry.getKey().startsWith("runtime.")) {
                artifactFields.put(safePrefix + "." + entry.getKey(), entry.getValue());
                runtimeFieldCount++;
            }
            index++;
        }
        artifactFields.put(safePrefix + ".runtimeField.count", Integer.toString(runtimeFieldCount));
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
            throw new IllegalArgumentException("lifecycle event field key must not be blank");
        }
        return key;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
