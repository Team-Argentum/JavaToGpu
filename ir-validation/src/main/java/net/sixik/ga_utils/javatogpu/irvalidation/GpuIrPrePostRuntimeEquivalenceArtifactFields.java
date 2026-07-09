package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.Map;

/**
 * Shared field writer for opt-in pre/post runtime-equivalence artifact wrappers.
 */
final class GpuIrPrePostRuntimeEquivalenceArtifactFields {
    private GpuIrPrePostRuntimeEquivalenceArtifactFields() {
    }

    static void putCommonFields(
            Map<String, String> values,
            String prefix,
            boolean successful,
            boolean runtimeEquivalenceSuccessful,
            int diagnosticCount,
            java.util.List<String> diagnostics,
            String summary,
            Map<String, String> nestedArtifactFields
    ) {
        values.put(prefix + "Successful", Boolean.toString(successful));
        values.put(prefix + "RuntimeEquivalenceSuccessful", Boolean.toString(runtimeEquivalenceSuccessful));
        values.put(prefix + "RuntimeEquivalenceDiagnostics", Integer.toString(diagnosticCount));
        GpuIrRuntimeEquivalenceDiagnosticFamilies.putArtifactFields(
                values,
                prefix + "RuntimeEquivalence",
                diagnostics
        );
        values.put(prefix + "Summary", summary);
        putRuntimeEquivalencePayloadFields(
                values,
                prefix,
                successful,
                runtimeEquivalenceSuccessful,
                diagnosticCount,
                nestedArtifactFields
        );
        values.putAll(nestedArtifactFields);
    }

    private static void putRuntimeEquivalencePayloadFields(
            Map<String, String> values,
            String prefix,
            boolean successful,
            boolean runtimeEquivalenceSuccessful,
            int diagnosticCount,
            Map<String, String> nestedArtifactFields
    ) {
        boolean payloadPresent = !nestedArtifactFields.isEmpty();
        boolean failureFixturePresent = payloadPresent;
        String resourceId = sanitizeResourceId(prefix);
        values.put("runtimeEquivalencePayload.present", Boolean.toString(payloadPresent));
        values.put("runtimeEquivalencePayload.cpuReference.present", Boolean.toString(runtimeEquivalenceSuccessful));
        values.put("runtimeEquivalencePayload.preOptimizationOutput.present", Boolean.toString(payloadPresent));
        values.put("runtimeEquivalencePayload.postOptimizationOutput.present", Boolean.toString(payloadPresent));
        values.put("runtimeEquivalencePayload.tolerance.present", Boolean.toString(runtimeEquivalenceSuccessful));
        values.put("runtimeEquivalencePayload.failureFixture.present", Boolean.toString(failureFixturePresent));
        values.put("runtimeEquivalencePayload.resource", "i2://pre-post-runtime-equivalence/" + resourceId);
        values.put("runtimeEquivalencePayload.cpuReference.resource", "i2://pre-post-runtime-equivalence/" + resourceId + "/cpu-reference");
        values.put("runtimeEquivalencePayload.preOptimizationOutput.resource", "i2://pre-post-runtime-equivalence/" + resourceId + "/pre-output");
        values.put("runtimeEquivalencePayload.postOptimizationOutput.resource", "i2://pre-post-runtime-equivalence/" + resourceId + "/post-output");
        values.put("runtimeEquivalencePayload.tolerance.resource", "i2://pre-post-runtime-equivalence/" + resourceId + "/tolerance");
        values.put("runtimeEquivalencePayload.failureFixture.resource",
                "i2://pre-post-runtime-equivalence/" + resourceId + "/failure-fixture");
    }

    private static String sanitizeResourceId(String prefix) {
        String normalized = prefix == null ? "artifact" : prefix.replace('.', '-');
        normalized = normalized.replaceAll("[^A-Za-z0-9_-]", "-");
        normalized = normalized.replaceAll("-+", "-");
        if (normalized.startsWith("-")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("-")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "artifact" : normalized;
    }
}
