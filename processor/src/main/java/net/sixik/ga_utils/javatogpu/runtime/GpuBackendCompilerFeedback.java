package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resource usage reported by a backend compiler for one generated kernel or method.
 *
 * <p>General, vector, and scalar register counts remain separate because backend register files are not
 * interchangeable. In particular, AMD SGPR and VGPR counts must not be added together.</p>
 */
public record GpuBackendCompilerFeedback(
        String providerId,
        String providerVersion,
        String kernelName,
        int generalRegisterCount,
        int vectorRegisterCount,
        int scalarRegisterCount,
        int spillStoreBytes,
        int spillLoadBytes,
        int stackFrameBytes,
        int localMemoryBytes,
        int occupancyPermille,
        Map<String, String> rawFields,
        List<String> diagnostics
) {

    public static final int UNKNOWN = -1;

    public GpuBackendCompilerFeedback {
        providerId = normalize(providerId, "compiler-feedback:unknown");
        providerVersion = normalize(providerVersion, "unknown");
        kernelName = kernelName == null ? "" : kernelName;
        generalRegisterCount = metric(generalRegisterCount);
        vectorRegisterCount = metric(vectorRegisterCount);
        scalarRegisterCount = metric(scalarRegisterCount);
        spillStoreBytes = metric(spillStoreBytes);
        spillLoadBytes = metric(spillLoadBytes);
        stackFrameBytes = metric(stackFrameBytes);
        localMemoryBytes = metric(localMemoryBytes);
        occupancyPermille = occupancyPermille < 0 ? UNKNOWN : Math.min(1_000, occupancyPermille);
        rawFields = rawFields == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(rawFields));
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean available() {
        return metricCount() > 0 || !rawFields.isEmpty();
    }

    public int metricCount() {
        int count = 0;
        count += known(generalRegisterCount) ? 1 : 0;
        count += known(vectorRegisterCount) ? 1 : 0;
        count += known(scalarRegisterCount) ? 1 : 0;
        count += known(spillStoreBytes) ? 1 : 0;
        count += known(spillLoadBytes) ? 1 : 0;
        count += known(stackFrameBytes) ? 1 : 0;
        count += known(localMemoryBytes) ? 1 : 0;
        count += known(occupancyPermille) ? 1 : 0;
        return count;
    }

    /**
     * Returns the most comparable single register count without combining distinct physical register files.
     */
    public int effectiveRegisterCount() {
        if (known(generalRegisterCount)) {
            return generalRegisterCount;
        }
        if (known(vectorRegisterCount)) {
            return vectorRegisterCount;
        }
        return scalarRegisterCount;
    }

    public int knownSpillBytes() {
        if (!known(spillStoreBytes) && !known(spillLoadBytes)) {
            return UNKNOWN;
        }
        return Math.max(0, spillStoreBytes) + Math.max(0, spillLoadBytes);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "compilerFeedback" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".providerId", providerId);
        fields.put(normalizedPrefix + ".providerVersion", providerVersion);
        fields.put(normalizedPrefix + ".kernelName", kernelName);
        fields.put(normalizedPrefix + ".available", Boolean.toString(available()));
        fields.put(normalizedPrefix + ".metric.count", Integer.toString(metricCount()));
        fields.put(normalizedPrefix + ".register.general", metricText(generalRegisterCount));
        fields.put(normalizedPrefix + ".register.vector", metricText(vectorRegisterCount));
        fields.put(normalizedPrefix + ".register.scalar", metricText(scalarRegisterCount));
        fields.put(normalizedPrefix + ".register.effective", metricText(effectiveRegisterCount()));
        fields.put(normalizedPrefix + ".spill.storeBytes", metricText(spillStoreBytes));
        fields.put(normalizedPrefix + ".spill.loadBytes", metricText(spillLoadBytes));
        fields.put(normalizedPrefix + ".spill.knownBytes", metricText(knownSpillBytes()));
        fields.put(normalizedPrefix + ".stackFrameBytes", metricText(stackFrameBytes));
        fields.put(normalizedPrefix + ".localMemoryBytes", metricText(localMemoryBytes));
        fields.put(normalizedPrefix + ".occupancyPermille", metricText(occupancyPermille));
        fields.put(normalizedPrefix + ".raw.count", Integer.toString(rawFields.size()));
        int rawIndex = 0;
        for (Map.Entry<String, String> entry : rawFields.entrySet()) {
            fields.put(normalizedPrefix + ".raw." + rawIndex + ".key", entry.getKey());
            fields.put(normalizedPrefix + ".raw." + rawIndex + ".value", entry.getValue());
            rawIndex++;
        }
        fields.put(normalizedPrefix + ".diagnostic.count", Integer.toString(diagnostics.size()));
        for (int index = 0; index < diagnostics.size(); index++) {
            fields.put(normalizedPrefix + ".diagnostic." + index, diagnostics.get(index));
        }
        return Collections.unmodifiableMap(fields);
    }

    private static boolean known(int value) {
        return value >= 0;
    }

    private static int metric(int value) {
        return value < 0 ? UNKNOWN : value;
    }

    private static String metricText(int value) {
        return known(value) ? Integer.toString(value) : "unknown";
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
