package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Report produced by explicit {@code @GPUTest} probe-evidence warm-up.
 */
public record GpuRuntimeMethodTestProbeEvidenceWarmupPlan(
        String kernelName,
        String irGpuResource,
        List<GpuRuntimeMethodTestProbeEvidenceWarmupCandidateResult> candidateResults,
        List<String> blockers,
        List<String> diagnostics
) {

    public GpuRuntimeMethodTestProbeEvidenceWarmupPlan {
        kernelName = normalize(kernelName, "unknown");
        irGpuResource = normalize(irGpuResource, "");
        candidateResults = candidateResults == null ? List.of() : List.copyOf(candidateResults);
        blockers = normalizeList(blockers);
        diagnostics = normalizeList(diagnostics);
    }

    public boolean warmupReady() {
        return !candidateResults.isEmpty() && blockers.isEmpty()
                && candidateResults.stream().allMatch(GpuRuntimeMethodTestProbeEvidenceWarmupCandidateResult::warmupReady);
    }

    public boolean warmupPassed() {
        return warmupReady()
                && candidateResults.stream().allMatch(GpuRuntimeMethodTestProbeEvidenceWarmupCandidateResult::warmupPassed);
    }

    public int candidateReadyCount() {
        return (int) candidateResults.stream()
                .filter(GpuRuntimeMethodTestProbeEvidenceWarmupCandidateResult::warmupReady)
                .count();
    }

    public int candidatePassedCount() {
        return (int) candidateResults.stream()
                .filter(GpuRuntimeMethodTestProbeEvidenceWarmupCandidateResult::warmupPassed)
                .count();
    }

    public int candidateFailedCount() {
        return (int) candidateResults.stream()
                .filter(candidate -> candidate.warmupReady() && !candidate.warmupPassed())
                .count();
    }

    public String status() {
        if (!blockers.isEmpty() || candidateResults.stream().anyMatch(candidate -> !candidate.warmupReady())) {
            return "blocked";
        }
        if (candidateFailedCount() > 0) {
            return "failed";
        }
        return warmupPassed() ? "passed" : "blocked";
    }

    public String firstBlocker() {
        if (!blockers.isEmpty()) {
            return blockers.get(0);
        }
        return candidateResults.stream()
                .map(GpuRuntimeMethodTestProbeEvidenceWarmupCandidateResult::firstBlocker)
                .filter(blocker -> !"none".equals(blocker))
                .findFirst()
                .orElse("none");
    }

    public String firstFailure() {
        return candidateResults.stream()
                .map(GpuRuntimeMethodTestProbeEvidenceWarmupCandidateResult::firstFailure)
                .filter(failure -> !"none".equals(failure))
                .findFirst()
                .orElse("none");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = normalize(prefix, "methodTestProbeEvidenceWarmup");
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".kernelName", kernelName);
        fields.put(normalizedPrefix + ".irGpuResource", irGpuResource);
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".warmupReady", Boolean.toString(warmupReady()));
        fields.put(normalizedPrefix + ".warmupPassed", Boolean.toString(warmupPassed()));
        fields.put(normalizedPrefix + ".candidate.count", Integer.toString(candidateResults.size()));
        fields.put(normalizedPrefix + ".candidate.ready.count", Integer.toString(candidateReadyCount()));
        fields.put(normalizedPrefix + ".candidate.passed.count", Integer.toString(candidatePassedCount()));
        fields.put(normalizedPrefix + ".candidate.failed.count", Integer.toString(candidateFailedCount()));
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        fields.put(normalizedPrefix + ".firstFailure", firstFailure());
        for (int index = 0; index < candidateResults.size(); index++) {
            fields.putAll(candidateResults.get(index).artifactFields(normalizedPrefix + ".candidate." + index));
        }
        writeList(fields, normalizedPrefix + ".blocker", blockers);
        writeList(fields, normalizedPrefix + ".diagnostic", diagnostics);
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Method test probe evidence warm-up: ")
                .append(status())
                .append('\n');
        builder.append("Candidates: ")
                .append(candidatePassedCount())
                .append('/')
                .append(candidateResults.size())
                .append(" passed")
                .append('\n');
        if (!"none".equals(firstBlocker())) {
            builder.append("First blocker: ").append(firstBlocker()).append('\n');
        }
        if (!"none".equals(firstFailure())) {
            builder.append("First failure: ").append(firstFailure()).append('\n');
        }
        if (!candidateResults.isEmpty()) {
            builder.append('\n');
            for (GpuRuntimeMethodTestProbeEvidenceWarmupCandidateResult candidate : candidateResults) {
                builder.append(candidate.toMarkdown());
            }
        }
        if (!diagnostics.isEmpty()) {
            builder.append('\n').append("Diagnostics:").append('\n');
            for (String diagnostic : diagnostics) {
                builder.append("- ").append(diagnostic).append('\n');
            }
        }
        return builder.toString();
    }

    private static void writeList(LinkedHashMap<String, String> fields, String prefix, List<String> values) {
        fields.put(prefix + ".count", Integer.toString(values.size()));
        for (int index = 0; index < values.size(); index++) {
            fields.put(prefix + "." + index, values.get(index));
        }
    }

    private static List<String> normalizeList(List<String> values) {
        return values == null
                ? List.of()
                : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
