package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CPU/reference comparison result for one materialized {@code @GPUTest} output argument.
 */
public record GpuRuntimeMethodTestReferenceComparison(
        String testId,
        int parameterIndex,
        String parameterName,
        String javaType,
        GpuKernelParameterAccess access,
        boolean comparisonReady,
        boolean passed,
        String actualKind,
        int actualItemCount,
        List<String> actualNumericValues,
        String expectedKind,
        int expectedItemCount,
        List<String> expectedNumericValues,
        double absoluteTolerance,
        double relativeTolerance,
        String blocker,
        String failure,
        String diagnostic
) {

    public GpuRuntimeMethodTestReferenceComparison {
        testId = normalize(testId, "unknown");
        parameterName = normalize(parameterName, "none");
        javaType = normalize(javaType, "none");
        access = access == null ? GpuKernelParameterAccess.VALUE : access;
        actualKind = normalize(actualKind, "none");
        actualItemCount = actualItemCount < 0 ? -1 : actualItemCount;
        actualNumericValues = actualNumericValues == null ? List.of() : List.copyOf(actualNumericValues);
        expectedKind = normalize(expectedKind, "none");
        expectedItemCount = expectedItemCount < 0 ? -1 : expectedItemCount;
        expectedNumericValues = expectedNumericValues == null ? List.of() : List.copyOf(expectedNumericValues);
        absoluteTolerance = sanitizeTolerance(absoluteTolerance);
        relativeTolerance = sanitizeTolerance(relativeTolerance);
        blocker = normalize(blocker, comparisonReady ? "none" : "fixture-reference-comparison-not-ready");
        failure = normalize(failure, passed ? "none" : "fixture-reference-output-mismatch");
        diagnostic = normalize(diagnostic, "none");
        passed = comparisonReady && passed && "none".equals(blocker) && "none".equals(failure);
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "methodTestReferenceComparison" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".testId", testId);
        fields.put(normalizedPrefix + ".parameter.index", Integer.toString(parameterIndex));
        fields.put(normalizedPrefix + ".parameter.name", parameterName);
        fields.put(normalizedPrefix + ".javaType", javaType);
        fields.put(normalizedPrefix + ".access", access.name());
        fields.put(normalizedPrefix + ".comparisonReady", Boolean.toString(comparisonReady));
        fields.put(normalizedPrefix + ".passed", Boolean.toString(passed));
        fields.put(normalizedPrefix + ".actual.kind", actualKind);
        fields.put(normalizedPrefix + ".actual.item.count", Integer.toString(actualItemCount));
        writePreview(fields, normalizedPrefix + ".actual.numericValue", actualNumericValues);
        fields.put(normalizedPrefix + ".expected.kind", expectedKind);
        fields.put(normalizedPrefix + ".expected.item.count", Integer.toString(expectedItemCount));
        writePreview(fields, normalizedPrefix + ".expected.numericValue", expectedNumericValues);
        fields.put(normalizedPrefix + ".tolerance.absolute", Double.toString(absoluteTolerance));
        fields.put(normalizedPrefix + ".tolerance.relative", Double.toString(relativeTolerance));
        fields.put(normalizedPrefix + ".blocker", blocker);
        fields.put(normalizedPrefix + ".failure", failure);
        fields.put(normalizedPrefix + ".diagnostic", diagnostic);
        return Collections.unmodifiableMap(fields);
    }

    private static void writePreview(LinkedHashMap<String, String> fields, String prefix, List<String> values) {
        fields.put(prefix + ".count", Integer.toString(values.size()));
        int previewCount = Math.min(values.size(), 4);
        fields.put(prefix + ".preview.count", Integer.toString(previewCount));
        for (int index = 0; index < previewCount; index++) {
            fields.put(prefix + ".preview." + index, values.get(index));
        }
    }

    private static double sanitizeTolerance(double value) {
        return Double.isFinite(value) && value > 0.0d ? value : 0.0d;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
