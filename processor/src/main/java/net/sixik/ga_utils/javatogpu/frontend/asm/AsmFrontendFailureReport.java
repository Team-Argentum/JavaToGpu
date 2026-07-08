package net.sixik.ga_utils.javatogpu.frontend.asm;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Read-only summary of ASM frontend failures collected without changing validation rules.
 */
public record AsmFrontendFailureReport(
        List<AsmFrontendFailureMetadata> failures
) {
    public AsmFrontendFailureReport {
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        if (failures.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("failures must not contain null entries");
        }
    }

    public boolean successful() {
        return failures.isEmpty();
    }

    public int failureCount() {
        return failures.size();
    }

    public Map<String, Long> familyCounts() {
        return failures.stream()
                .collect(Collectors.groupingBy(
                        AsmFrontendFailureMetadata::family,
                        java.util.LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    public List<String> summaries() {
        return failures.stream()
                .map(AsmFrontendFailureMetadata::summary)
                .toList();
    }

    public AsmFrontendReadinessReport readinessReport() {
        return new AsmFrontendReadinessReport(this);
    }

    public String summaryLine() {
        if (successful()) {
            return "asmFailureReport successful failures=0";
        }
        return "asmFailureReport failed failures="
                + failureCount()
                + " families="
                + familyCounts()
                + " first="
                + failures.get(0).summary();
    }

    public AsmFrontendFailureReport requireSuccessful() {
        if (successful()) {
            return this;
        }
        AsmFrontendFailureMetadata firstFailure = failures.get(0);
        throw new AsmFrontendException(
                summaryLine() + "; summaries=" + String.join(" | ", summaries()),
                firstFailure
        );
    }

    public Map<String, String> artifactFields(String prefix) {
        String safePrefix = prefix == null || prefix.isBlank() ? "asmFailureReport" : prefix;
        java.util.LinkedHashMap<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put(safePrefix + ".successful", Boolean.toString(successful()));
        fields.put(safePrefix + ".failureCount", Integer.toString(failureCount()));
        fields.put(safePrefix + ".summary", summaryLine());
        fields.put(safePrefix + ".families", familyCounts().toString());
        fields.putAll(readinessReport().artifactFields(safePrefix + ".readiness"));
        List<String> summaries = summaries();
        if (!summaries.isEmpty()) {
            fields.put(safePrefix + ".firstFailure", summaries.get(0));
            fields.put(safePrefix + ".summaries", String.join(" | ", summaries));
        }
        for (int index = 0; index < failures.size(); index++) {
            AsmFrontendFailureMetadata failure = failures.get(index);
            fields.putAll(failure.artifactFields(safePrefix + ".failure." + index));
        }
        return Map.copyOf(fields);
    }
}
