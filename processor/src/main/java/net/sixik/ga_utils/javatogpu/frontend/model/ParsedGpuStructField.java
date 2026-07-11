package net.sixik.ga_utils.javatogpu.frontend.model;

import java.util.List;

public record ParsedGpuStructField(
        String name,
        String javaType,
        List<String> openClAttributes,
        List<GpuAttributeMetadata> attributeMetadata
) {

    public ParsedGpuStructField(String name, String javaType, List<String> openClAttributes) {
        this(name, javaType, openClAttributes, List.of());
    }

    public ParsedGpuStructField {
        openClAttributes = openClAttributes == null ? List.of() : List.copyOf(openClAttributes);
        attributeMetadata = attributeMetadata == null ? List.of() : List.copyOf(attributeMetadata);
    }
}
