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
        values.putAll(nestedArtifactFields);
    }
}
