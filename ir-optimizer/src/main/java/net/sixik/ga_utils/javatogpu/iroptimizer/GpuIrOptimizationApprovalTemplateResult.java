package net.sixik.ga_utils.javatogpu.iroptimizer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of formatting a manual approval template for one optimizer proposal.
 */
public record GpuIrOptimizationApprovalTemplateResult(
        String status,
        boolean applicable,
        String firstBlocker,
        String templateText,
        Map<String, String> fields
) {

    public GpuIrOptimizationApprovalTemplateResult {
        status = normalize(status, applicable ? "pending" : "not-applicable");
        firstBlocker = normalize(firstBlocker, applicable ? "none" : "unknown");
        templateText = templateText == null ? "" : templateText;
        fields = fields == null ? Map.of() : Map.copyOf(fields);
        if (applicable && templateText.isBlank()) {
            throw new IllegalArgumentException("Applicable optimizer approval template result must include template text");
        }
    }

    static GpuIrOptimizationApprovalTemplateResult pending(
            String templateText,
            Map<String, String> fields
    ) {
        return new GpuIrOptimizationApprovalTemplateResult(
                "pending",
                true,
                "none",
                templateText,
                fields
        );
    }

    static GpuIrOptimizationApprovalTemplateResult notApplicable(
            String firstBlocker,
            Map<String, String> fields
    ) {
        return new GpuIrOptimizationApprovalTemplateResult(
                "not-applicable",
                false,
                firstBlocker,
                "",
                fields
        );
    }

    public String toPropertiesText() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("formatVersion", "1");
        values.put("status", status);
        values.put("applicable", Boolean.toString(applicable));
        values.put("firstBlocker", firstBlocker);
        values.putAll(fields);
        if (applicable) {
            values.put("template.present", "true");
            values.put("template.scope", GpuIrOptimizationApprovalManifest.SCOPE);
            values.put("template.resourceDirectory", GpuIrOptimizationApprovalManifest.RESOURCE_DIRECTORY);
        } else {
            values.put("template.present", "false");
        }
        StringBuilder builder = new StringBuilder();
        values.forEach((key, value) -> builder.append(key)
                .append('=')
                .append(propertyValue(value))
                .append('\n'));
        return builder.toString();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String propertyValue(String value) {
        return normalize(value, "unknown").replace('\\', '/').replace('\r', ' ').replace('\n', ' ');
    }
}
