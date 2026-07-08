package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.List;

/**
 * ABI metadata for one entry-kernel parameter stored in the backend-neutral IrGpu artifact.
 */
public record IrGpuEntryParameter(
        String name,
        String javaType,
        String addressSpace,
        boolean constant,
        List<String> openClQualifiers
) {

    public IrGpuEntryParameter {
        name = normalize(name, "");
        javaType = normalize(javaType, "unknown");
        addressSpace = normalize(addressSpace, "PRIVATE");
        openClQualifiers = openClQualifiers == null ? List.of() : List.copyOf(openClQualifiers);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
