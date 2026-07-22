package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Classpath lookup status for one {@code @GPUTest} fixture reference.
 */
public record GpuRuntimeMethodTestFixtureResourceStatus(
        String testId,
        String kind,
        int index,
        String resourceRef,
        boolean available,
        String location,
        long sizeBytes,
        String sha256,
        GpuRuntimeMethodTestFixturePayloadPreview payloadPreview,
        String blocker
) {

    public GpuRuntimeMethodTestFixtureResourceStatus {
        testId = normalize(testId, "unknown");
        kind = normalize(kind, "unknown");
        resourceRef = normalize(resourceRef, "");
        location = normalize(location, "none");
        sizeBytes = available ? Math.max(sizeBytes, 0L) : -1L;
        sha256 = normalize(sha256, available ? "unknown" : "none");
        payloadPreview = payloadPreview == null
                ? GpuRuntimeMethodTestFixturePayloadPreview.unavailable(available
                ? "fixture-payload-preview-missing"
                : "fixture-payload-unavailable")
                : payloadPreview;
        blocker = normalize(blocker, available ? "none" : "fixture-resource-not-found");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "fixtureResource" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".testId", testId);
        fields.put(normalizedPrefix + ".kind", kind);
        fields.put(normalizedPrefix + ".index", Integer.toString(index));
        fields.put(normalizedPrefix + ".resourceRef", resourceRef);
        fields.put(normalizedPrefix + ".available", Boolean.toString(available));
        fields.put(normalizedPrefix + ".location", location);
        fields.put(normalizedPrefix + ".sizeBytes", Long.toString(sizeBytes));
        fields.put(normalizedPrefix + ".sha256", sha256);
        fields.putAll(payloadPreview.artifactFields(normalizedPrefix + ".payload"));
        fields.put(normalizedPrefix + ".blocker", blocker);
        return Collections.unmodifiableMap(fields);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
