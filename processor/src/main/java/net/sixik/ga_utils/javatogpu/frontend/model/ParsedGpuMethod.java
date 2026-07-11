package net.sixik.ga_utils.javatogpu.frontend.model;

import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.List;

public record ParsedGpuMethod(
        String ownerSimpleName,
        String ownerQualifiedName,
        String name,
        String returnType,
        List<ParsedGpuParameter> parameters,
        List<ParsedGpuConstant> constants,
        List<ParsedGpuConstantData> constantData,
        MethodDeclaration declaration,
        boolean inline,
        List<String> openClAttributes,
        List<GpuAttributeMetadata> attributeMetadata,
        String nativeCode,
        String supportCondition,
        String callbackMethodName,
        boolean nativeDeclaration
) {

    public ParsedGpuMethod(
            String ownerSimpleName,
            String ownerQualifiedName,
            String name,
            String returnType,
            List<ParsedGpuParameter> parameters,
            List<ParsedGpuConstant> constants,
            List<ParsedGpuConstantData> constantData,
            MethodDeclaration declaration,
            boolean inline,
            List<String> openClAttributes,
            String nativeCode,
            String supportCondition,
            String callbackMethodName,
            boolean nativeDeclaration
    ) {
        this(
                ownerSimpleName,
                ownerQualifiedName,
                name,
                returnType,
                parameters,
                constants,
                constantData,
                declaration,
                inline,
                openClAttributes,
                List.of(),
                nativeCode,
                supportCondition,
                callbackMethodName,
                nativeDeclaration
        );
    }

    public ParsedGpuMethod {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        constants = constants == null ? List.of() : List.copyOf(constants);
        constantData = constantData == null ? List.of() : List.copyOf(constantData);
        openClAttributes = openClAttributes == null ? List.of() : List.copyOf(openClAttributes);
        attributeMetadata = attributeMetadata == null ? List.of() : List.copyOf(attributeMetadata);
    }
}
