package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.List;

/**
 * Field-level metadata for a struct captured in an IrGpu artifact.
 */
public record IrGpuStructFieldMetadata(
        String name,
        String javaType,
        List<String> openClAttributes,
        List<IrGpuAttributeMetadata> attributeMetadata
) {

    public IrGpuStructFieldMetadata(String name, String javaType, List<String> openClAttributes) {
        this(name, javaType, openClAttributes, List.of());
    }

    public IrGpuStructFieldMetadata {
        name = normalize(name, "");
        javaType = normalize(javaType, "unknown");
        openClAttributes = openClAttributes == null ? List.of() : List.copyOf(openClAttributes);
        attributeMetadata = attributeMetadata == null ? List.of() : List.copyOf(attributeMetadata);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
