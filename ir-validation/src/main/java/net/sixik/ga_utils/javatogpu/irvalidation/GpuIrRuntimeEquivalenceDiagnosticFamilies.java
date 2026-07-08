package net.sixik.ga_utils.javatogpu.irvalidation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared diagnostic grouping for opt-in runtime-equivalence artifact reports.
 */
final class GpuIrRuntimeEquivalenceDiagnosticFamilies {
    private GpuIrRuntimeEquivalenceDiagnosticFamilies() {
    }

    static void putRollupArtifactFields(Map<String, String> values, String prefix, List<String> diagnostics) {
        values.put(prefix + "Diagnostics", Integer.toString(diagnostics.size()));
        values.put("has" + Character.toUpperCase(prefix.charAt(0)) + prefix.substring(1) + "Diagnostics",
                Boolean.toString(!diagnostics.isEmpty()));
        putArtifactFields(values, prefix, diagnostics);
    }

    static void putArtifactFields(Map<String, String> values, String prefix, List<String> diagnostics) {
        Map<String, Long> counts = counts(diagnostics);
        values.put(prefix + "DiagnosticFamilyCounts", countsSummary(counts));
        counts.forEach((family, count) -> values.put(
                prefix + "DiagnosticFamily." + family,
                Long.toString(count)
        ));
    }

    static Map<String, Long> counts(List<String> diagnostics) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String diagnostic : diagnostics) {
            counts.merge(family(diagnostic), 1L, Long::sum);
        }
        return counts;
    }

    static String countsSummary(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    static String countsSummary(List<String> diagnostics) {
        return countsSummary(counts(diagnostics));
    }

    static String family(String diagnostic) {
        if (diagnostic.contains(" execution failed: ")) {
            return "executionFailed";
        }
        if (diagnostic.contains(" output ") && diagnostic.contains(" is missing")) {
            return "missingOutput";
        }
        if (diagnostic.contains(" output ") && diagnostic.contains(" differs")) {
            return "outputDiffers";
        }
        return "other";
    }
}
