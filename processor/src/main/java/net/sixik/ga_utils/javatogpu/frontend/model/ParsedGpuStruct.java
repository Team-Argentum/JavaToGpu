package net.sixik.ga_utils.javatogpu.frontend.model;

import java.util.List;

public record ParsedGpuStruct(
        String ownerSimpleName,
        String ownerQualifiedName,
        List<ParsedGpuStructField> fields,
        List<ParsedGpuConstant> constants,
        List<ParsedGpuConstantData> constantData,
        List<String> openClAttributes,
        List<GpuAttributeMetadata> attributeMetadata
) {

    public ParsedGpuStruct(
            String ownerSimpleName,
            String ownerQualifiedName,
            List<ParsedGpuStructField> fields,
            List<ParsedGpuConstant> constants,
            List<ParsedGpuConstantData> constantData,
            List<String> openClAttributes
    ) {
        this(ownerSimpleName, ownerQualifiedName, fields, constants, constantData, openClAttributes, List.of());
    }

    public ParsedGpuStruct {
        fields = fields == null ? List.of() : List.copyOf(fields);
        constants = constants == null ? List.of() : List.copyOf(constants);
        constantData = constantData == null ? List.of() : List.copyOf(constantData);
        openClAttributes = openClAttributes == null ? List.of() : List.copyOf(openClAttributes);
        attributeMetadata = attributeMetadata == null ? List.of() : List.copyOf(attributeMetadata);
    }
}
