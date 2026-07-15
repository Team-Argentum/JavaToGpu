package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.List;

/**
 * Backend-neutral validation fixture metadata for one IrGpu method.
 */
public record IrGpuMethodTestVectorMetadata(
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

    public IrGpuMethodTestVectorMetadata {
        methodName = normalize(methodName, "unknown");
        emittedName = normalize(emittedName, methodName);
        testId = normalize(testId, methodName);
        inputRefs = normalizeList(inputRefs);
        expectedOutputRefs = normalizeList(expectedOutputRefs);
        tolerance = normalize(tolerance, "");
        tags = normalizeList(tags);
        source = normalize(source, "GPUTest");
    }

    public boolean active() {
        return !inputRefs.isEmpty() || !expectedOutputRefs.isEmpty() || !tags.isEmpty() || !tolerance.isBlank();
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
