package net.sixik.ga_utils.javatogpu.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only preflight report for {@code @GPUTest} fixture references.
 */
public record GpuRuntimeMethodTestFixtureReadiness(
        GpuRuntimeMethodTestProbePlan probePlan,
        List<GpuRuntimeMethodTestFixtureResourceStatus> resources,
        List<String> blockers,
        List<String> diagnostics
) {

    public GpuRuntimeMethodTestFixtureReadiness {
        probePlan = probePlan == null
                ? new GpuRuntimeMethodTestProbePlan(
                "unknown",
                "",
                "",
                false,
                List.of(),
                List.of("method-test-probe-plan-missing"),
                List.of()
        )
                : probePlan;
        resources = resources == null ? List.of() : List.copyOf(resources);
        blockers = normalizeList(blockers);
        diagnostics = normalizeList(diagnostics);
    }

    public boolean fixtureResourcesReady() {
        return blockers.isEmpty();
    }

    public boolean selectionProbeResourcesReady() {
        List<GpuRuntimeMethodTestVectorPlan> selectionVectors = probePlan.selectionProbeVectors();
        return !selectionVectors.isEmpty() && selectionVectors.stream().allMatch(this::vectorReady);
    }

    public boolean fixturePayloadPreviewsReady() {
        return !resources.isEmpty() && resources.stream()
                .allMatch(resource -> resource.available() && resource.payloadPreview().schemaReady());
    }

    public int missingResourceCount() {
        return (int) resources.stream().filter(resource -> !resource.available()).count();
    }

    public int availableResourceCount() {
        return (int) resources.stream().filter(GpuRuntimeMethodTestFixtureResourceStatus::available).count();
    }

    public int payloadPreviewReadyCount() {
        return (int) resources.stream()
                .filter(GpuRuntimeMethodTestFixtureResourceStatus::available)
                .filter(resource -> resource.payloadPreview().schemaReady())
                .count();
    }

    public int payloadPreviewBlockedCount() {
        return resources.size() - payloadPreviewReadyCount();
    }

    public String firstBlocker() {
        return blockers.isEmpty() ? "none" : blockers.get(0);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "methodTestFixtureReadiness" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".fixtureResourcesReady", Boolean.toString(fixtureResourcesReady()));
        fields.put(normalizedPrefix + ".selectionProbeResourcesReady", Boolean.toString(selectionProbeResourcesReady()));
        fields.put(normalizedPrefix + ".fixturePayloadPreviewsReady", Boolean.toString(fixturePayloadPreviewsReady()));
        fields.put(normalizedPrefix + ".resource.count", Integer.toString(resources.size()));
        fields.put(normalizedPrefix + ".resource.available.count", Integer.toString(availableResourceCount()));
        fields.put(normalizedPrefix + ".resource.missing.count", Integer.toString(missingResourceCount()));
        fields.put(normalizedPrefix + ".payloadPreview.ready.count", Integer.toString(payloadPreviewReadyCount()));
        fields.put(normalizedPrefix + ".payloadPreview.blocked.count", Integer.toString(payloadPreviewBlockedCount()));
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        fields.putAll(probePlan.artifactFields(normalizedPrefix + ".probePlan"));
        for (int index = 0; index < resources.size(); index++) {
            fields.putAll(resources.get(index).artifactFields(normalizedPrefix + ".resource." + index));
        }
        writeList(fields, normalizedPrefix + ".blocker", blockers);
        writeList(fields, normalizedPrefix + ".diagnostic", diagnostics);
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Method test fixture readiness: ")
                .append(fixtureResourcesReady() ? "ready" : "blocked")
                .append('\n');
        builder.append("Selection probe resources ready: ")
                .append(selectionProbeResourcesReady())
                .append('\n');
        builder.append("Fixture resources: ")
                .append(availableResourceCount())
                .append('/')
                .append(resources.size())
                .append(" available")
                .append('\n');
        builder.append("Fixture payload previews ready: ")
                .append(payloadPreviewReadyCount())
                .append('/')
                .append(resources.size())
                .append('\n');
        if (!"none".equals(firstBlocker())) {
            builder.append("First blocker: ").append(firstBlocker()).append('\n');
        }
        if (!resources.isEmpty()) {
            builder.append('\n').append("Resources:").append('\n');
            for (GpuRuntimeMethodTestFixtureResourceStatus resource : resources) {
                builder.append("- ")
                        .append(resource.testId())
                        .append(' ')
                        .append(resource.kind())
                        .append('[')
                        .append(resource.index())
                        .append("] `")
                        .append(resource.resourceRef())
                        .append("` available=")
                        .append(resource.available());
                if (resource.available()) {
                    builder.append(" sizeBytes=")
                            .append(resource.sizeBytes())
                            .append(" sha256=")
                            .append(resource.sha256())
                            .append(" payload=")
                            .append(resource.payloadPreview().format())
                            .append(' ')
                            .append(resource.payloadPreview().primaryField())
                            .append('[')
                            .append(resource.payloadPreview().primaryItemCount())
                            .append(']');
                    if (!resource.payloadPreview().schemaReady()) {
                        builder.append(" payloadBlocker=")
                                .append(resource.payloadPreview().blocker());
                    }
                }
                if (!"none".equals(resource.location())) {
                    builder.append(" location=").append(resource.location());
                }
                if (!"none".equals(resource.blocker())) {
                    builder.append(" blocker=").append(resource.blocker());
                }
                builder.append('\n');
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

    private boolean vectorReady(GpuRuntimeMethodTestVectorPlan vector) {
        if (!vector.hasExpectedOutputs()) {
            return false;
        }
        Set<String> expectedRefs = new LinkedHashSet<>();
        expectedRefs.addAll(vector.inputRefs());
        expectedRefs.addAll(vector.expectedOutputRefs());
        return resources.stream()
                .filter(resource -> resource.testId().equals(vector.testId()))
                .filter(resource -> expectedRefs.contains(resource.resourceRef()))
                .allMatch(resource -> resource.available() && resource.payloadPreview().schemaReady())
                && resources.stream()
                .filter(resource -> resource.testId().equals(vector.testId()))
                .map(GpuRuntimeMethodTestFixtureResourceStatus::resourceRef)
                .collect(java.util.stream.Collectors.toSet())
                .containsAll(expectedRefs);
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
}
