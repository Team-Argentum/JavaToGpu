package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only runtime plan for method-level {@code @GPUTest} probes.
 *
 * <p>This is intentionally not an executor yet. It makes generated test-vector metadata visible to examples, support
 * logs, lifecycle journals, and future backend/device selection code before fixture loading and GPU execution exist.</p>
 */
public record GpuRuntimeMethodTestProbePlan(
        String kernelName,
        String kernelResource,
        String irGpuResource,
        boolean artifactLoaded,
        List<GpuRuntimeMethodTestVectorPlan> testVectors,
        List<String> blockers,
        List<String> diagnostics
) {

    public GpuRuntimeMethodTestProbePlan {
        kernelName = normalize(kernelName, "unknown");
        kernelResource = normalize(kernelResource, "");
        irGpuResource = normalize(irGpuResource, "");
        testVectors = testVectors == null ? List.of() : List.copyOf(testVectors);
        blockers = normalizeList(blockers);
        diagnostics = normalizeList(diagnostics);
    }

    public boolean metadataReady() {
        return artifactLoaded && !testVectors.isEmpty();
    }

    public boolean hasSelectionProbes() {
        return !selectionProbeVectors().isEmpty();
    }

    public List<GpuRuntimeMethodTestVectorPlan> selectionProbeVectors() {
        return testVectors.stream()
                .filter(GpuRuntimeMethodTestVectorPlan::selectionProbe)
                .toList();
    }

    public String firstBlocker() {
        return blockers.isEmpty() ? "none" : blockers.get(0);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "methodTestProbePlan" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".kernelName", kernelName);
        fields.put(normalizedPrefix + ".kernelResource", kernelResource);
        fields.put(normalizedPrefix + ".irGpuResource", irGpuResource);
        fields.put(normalizedPrefix + ".artifactLoaded", Boolean.toString(artifactLoaded));
        fields.put(normalizedPrefix + ".metadataReady", Boolean.toString(metadataReady()));
        fields.put(normalizedPrefix + ".testVector.count", Integer.toString(testVectors.size()));
        fields.put(normalizedPrefix + ".selectionProbe.count", Integer.toString(selectionProbeVectors().size()));
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker());
        for (int index = 0; index < testVectors.size(); index++) {
            fields.putAll(testVectors.get(index).artifactFields(normalizedPrefix + ".testVector." + index));
        }
        writeList(fields, normalizedPrefix + ".blocker", blockers);
        writeList(fields, normalizedPrefix + ".diagnostic", diagnostics);
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Method test probe plan: ").append(metadataReady() ? "metadata-ready" : "blocked").append('\n');
        builder.append("Kernel: ").append(kernelName).append('\n');
        if (!irGpuResource.isBlank()) {
            builder.append("IrGpu resource: ").append(irGpuResource).append('\n');
        }
        builder.append("Test vectors: ").append(testVectors.size()).append('\n');
        builder.append("Selection probes: ").append(selectionProbeVectors().size()).append('\n');
        if (!"none".equals(firstBlocker())) {
            builder.append("First blocker: ").append(firstBlocker()).append('\n');
        }
        if (!testVectors.isEmpty()) {
            builder.append('\n').append("Vectors:").append('\n');
            for (GpuRuntimeMethodTestVectorPlan vector : testVectors) {
                builder.append("- ")
                        .append(vector.testId())
                        .append(" method=")
                        .append(vector.methodName())
                        .append(" emitted=")
                        .append(vector.emittedName())
                        .append(" selectionProbe=")
                        .append(vector.selectionProbe())
                        .append(" inputs=")
                        .append(vector.inputRefs().size())
                        .append(" expectedOutputs=")
                        .append(vector.expectedOutputRefs().size());
                if (!vector.tolerance().isBlank()) {
                    builder.append(" tolerance=").append(vector.tolerance());
                }
                if (!vector.tags().isEmpty()) {
                    builder.append(" tags=").append(String.join(",", vector.tags()));
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
                .toList();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
