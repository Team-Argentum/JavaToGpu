package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.List;

/**
 * Struct metadata needed before IrGpu can become the source of truth for backend lowering.
 */
public record IrGpuStructMetadata(
        String ownerQualifiedName,
        String ownerSimpleName,
        List<IrGpuStructFieldMetadata> fields,
        List<String> openClAttributes,
        List<IrGpuAttributeMetadata> attributeMetadata
) {

    public IrGpuStructMetadata(
            String ownerQualifiedName,
            String ownerSimpleName,
            List<IrGpuStructFieldMetadata> fields,
            List<String> openClAttributes
    ) {
        this(ownerQualifiedName, ownerSimpleName, fields, openClAttributes, List.of());
    }

    public IrGpuStructMetadata {
        ownerQualifiedName = normalize(ownerQualifiedName, "");
        ownerSimpleName = normalize(ownerSimpleName, ownerQualifiedName);
        fields = fields == null ? List.of() : List.copyOf(fields);
        openClAttributes = openClAttributes == null ? List.of() : List.copyOf(openClAttributes);
        attributeMetadata = attributeMetadata == null ? List.of() : List.copyOf(attributeMetadata);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
