package net.sixik.ga_utils.javatogpu.runtime.methodtest;

import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only binding of one fixture JSON field to one kernel parameter.
 */
public record GpuRuntimeMethodTestFixtureValueBinding(
        String testId,
        String kind,
        int fixtureIndex,
        String resourceRef,
        String parameterName,
        String javaType,
        GpuKernelParameterAccess access,
        boolean bindingReady,
        String valueKind,
        int itemCount,
        List<String> numericValues,
        Object fixtureValue,
        String blocker
) {

    public GpuRuntimeMethodTestFixtureValueBinding(
            String testId,
            String kind,
            int fixtureIndex,
            String resourceRef,
            String parameterName,
            String javaType,
            GpuKernelParameterAccess access,
            boolean bindingReady,
            String valueKind,
            int itemCount,
            List<String> numericValues,
            String blocker
    ) {
        this(
                testId,
                kind,
                fixtureIndex,
                resourceRef,
                parameterName,
                javaType,
                access,
                bindingReady,
                valueKind,
                itemCount,
                numericValues,
                null,
                blocker
        );
    }

    public GpuRuntimeMethodTestFixtureValueBinding {
        testId = normalize(testId, "unknown");
        kind = normalize(kind, "unknown");
        resourceRef = normalize(resourceRef, "");
        parameterName = normalize(parameterName, "none");
        javaType = normalize(javaType, "none");
        access = access == null ? GpuKernelParameterAccess.VALUE : access;
        valueKind = normalize(valueKind, bindingReady ? "numeric" : "none");
        numericValues = bindingReady && numericValues != null ? List.copyOf(numericValues) : List.of();
        fixtureValue = bindingReady ? fixtureValue : null;
        itemCount = bindingReady ? Math.max(itemCount, 0) : -1;
        blocker = normalize(blocker, bindingReady ? "none" : "fixture-value-binding-not-ready");
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "fixtureValueBinding" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".testId", testId);
        fields.put(normalizedPrefix + ".kind", kind);
        fields.put(normalizedPrefix + ".fixtureIndex", Integer.toString(fixtureIndex));
        fields.put(normalizedPrefix + ".resourceRef", resourceRef);
        fields.put(normalizedPrefix + ".parameterName", parameterName);
        fields.put(normalizedPrefix + ".javaType", javaType);
        fields.put(normalizedPrefix + ".access", access.name());
        fields.put(normalizedPrefix + ".bindingReady", Boolean.toString(bindingReady));
        fields.put(normalizedPrefix + ".value.kind", valueKind);
        fields.put(normalizedPrefix + ".item.count", Integer.toString(itemCount));
        fields.put(normalizedPrefix + ".numericValue.count", Integer.toString(numericValues.size()));
        int previewCount = Math.min(numericValues.size(), 4);
        fields.put(normalizedPrefix + ".numericValue.preview.count", Integer.toString(previewCount));
        for (int index = 0; index < previewCount; index++) {
            fields.put(normalizedPrefix + ".numericValue.preview." + index, numericValues.get(index));
        }
        fields.put(normalizedPrefix + ".blocker", blocker);
        return Collections.unmodifiableMap(fields);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
