package net.sixik.ga_utils.javatogpu.frontend.ir.validation;

import java.util.Map;
import java.util.Objects;

/**
 * Machine-readable validation report entry emitted by optional validation providers.
 */
public record GpuIrValidationReportEntry(
        String provider,
        String extensionId,
        String extensionVersion,
        String ruleId,
        GpuIrValidationSeverity severity,
        String sourceAnchor,
        String methodName,
        boolean entryPoint,
        Map<String, String> values
) {
    public GpuIrValidationReportEntry(
            String provider,
            String methodName,
            boolean entryPoint,
            Map<String, String> values
    ) {
        this(
                provider,
                provider,
                "unknown",
                provider + ".summary",
                GpuIrValidationSeverity.INFO,
                methodName,
                methodName,
                entryPoint,
                values
        );
    }

    public GpuIrValidationReportEntry {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        extensionId = normalize(extensionId, provider);
        extensionVersion = normalize(extensionVersion, "unknown");
        ruleId = normalize(ruleId, provider + ".summary");
        severity = severity == null ? GpuIrValidationSeverity.INFO : severity;
        sourceAnchor = normalize(sourceAnchor, methodName);
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        values = Map.copyOf(Objects.requireNonNull(values, "values"));
    }

    public GpuIrValidationReportEntry withExtensionContext(
            String extensionId,
            String extensionVersion,
            String sourceAnchor,
            String methodName,
            boolean entryPoint
    ) {
        return new GpuIrValidationReportEntry(
                provider,
                extensionId,
                extensionVersion,
                ruleId,
                severity,
                sourceAnchor,
                methodName,
                entryPoint,
                values
        );
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "validationEntry" : prefix.trim();
        java.util.LinkedHashMap<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put(normalizedPrefix + ".provider", provider);
        fields.put(normalizedPrefix + ".extensionId", extensionId);
        fields.put(normalizedPrefix + ".extensionVersion", extensionVersion);
        fields.put(normalizedPrefix + ".ruleId", ruleId);
        fields.put(normalizedPrefix + ".severity", severity.name());
        fields.put(normalizedPrefix + ".sourceAnchor", sourceAnchor);
        fields.put(normalizedPrefix + ".methodName", methodName);
        fields.put(normalizedPrefix + ".entryPoint", Boolean.toString(entryPoint));
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> fields.put(normalizedPrefix + "." + entry.getKey(), entry.getValue()));
        return java.util.Collections.unmodifiableMap(fields);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
