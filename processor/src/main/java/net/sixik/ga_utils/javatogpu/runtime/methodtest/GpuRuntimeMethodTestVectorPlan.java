package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodTestVectorMetadata;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime-facing view of one method-level {@code @GPUTest} fixture declaration.
 */
public record GpuRuntimeMethodTestVectorPlan(
        String methodName,
        String emittedName,
        String testId,
        List<String> inputRefs,
        List<String> expectedOutputRefs,
        String tolerance,
        List<String> tags,
        boolean selectionProbe,
        String source
) {

    public GpuRuntimeMethodTestVectorPlan {
        methodName = normalize(methodName, "unknown");
        emittedName = normalize(emittedName, methodName);
        testId = normalize(testId, methodName);
        inputRefs = normalizeList(inputRefs);
        expectedOutputRefs = normalizeList(expectedOutputRefs);
        tolerance = normalize(tolerance, "");
        tags = normalizeList(tags);
        source = normalize(source, "GPUTest");
    }

    public static GpuRuntimeMethodTestVectorPlan from(IrGpuMethodTestVectorMetadata metadata) {
        return new GpuRuntimeMethodTestVectorPlan(
                metadata.methodName(),
                metadata.emittedName(),
                metadata.testId(),
                metadata.inputRefs(),
                metadata.expectedOutputRefs(),
                metadata.tolerance(),
                metadata.tags(),
                metadata.selectionProbe(),
                metadata.source()
        );
    }

    public boolean hasExpectedOutputs() {
        return !expectedOutputRefs.isEmpty();
    }

    public boolean hasInputs() {
        return !inputRefs.isEmpty();
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "methodTestVector" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".methodName", methodName);
        fields.put(normalizedPrefix + ".emittedName", emittedName);
        fields.put(normalizedPrefix + ".testId", testId);
        fields.put(normalizedPrefix + ".selectionProbe", Boolean.toString(selectionProbe));
        fields.put(normalizedPrefix + ".tolerance", tolerance);
        fields.put(normalizedPrefix + ".source", source);
        writeList(fields, normalizedPrefix + ".inputRef", inputRefs);
        writeList(fields, normalizedPrefix + ".expectedOutputRef", expectedOutputRefs);
        writeList(fields, normalizedPrefix + ".tag", tags);
        return Collections.unmodifiableMap(fields);
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
