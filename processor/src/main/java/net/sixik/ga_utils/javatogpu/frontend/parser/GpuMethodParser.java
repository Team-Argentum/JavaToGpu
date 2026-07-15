package net.sixik.ga_utils.javatogpu.frontend.parser;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstant;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstantData;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAttributeMetadata;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;

import java.util.ArrayList;
import java.util.List;

public final class GpuMethodParser {

    static {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    }

    public ParsedGpuMethod parseMethod(String methodSource) {
        return parseMethod(methodSource, "", "", List.of(), List.of());
    }

    public ParsedGpuMethod parseMethod(String methodSource, String ownerSimpleName, String ownerQualifiedName) {
        return parseMethod(methodSource, ownerSimpleName, ownerQualifiedName, List.of(), List.of());
    }

    public ParsedGpuMethod parseMethod(
            String methodSource,
            String ownerSimpleName,
            String ownerQualifiedName,
            List<ParsedGpuConstant> constants
    ) {
        return parseMethod(methodSource, ownerSimpleName, ownerQualifiedName, constants, List.of());
    }

    public ParsedGpuMethod parseMethod(
            String methodSource,
            String ownerSimpleName,
            String ownerQualifiedName,
            List<ParsedGpuConstant> constants,
            List<ParsedGpuConstantData> constantData
    ) {
        MethodDeclaration declaration = StaticJavaParser.parseMethodDeclaration(methodSource);

        List<ParsedGpuParameter> parameters = declaration.getParameters().stream()
                .map(this::toParsedParameter)
                .toList();

        return new ParsedGpuMethod(
                ownerSimpleName,
                ownerQualifiedName,
                declaration.getNameAsString(),
                declaration.getTypeAsString(),
                parameters,
                List.copyOf(constants),
                List.copyOf(constantData),
                declaration,
                parseInlineFlag(declaration),
                parseOpenClAttributes(declaration),
                parseAttributeMetadata(declaration),
                parseNativeCode(declaration),
                parseAnnotationStringValue(declaration, "support"),
                parseAnnotationStringValue(declaration, "callback"),
                declaration.isNative()
        );
    }

    private ParsedGpuParameter toParsedParameter(Parameter parameter) {
        boolean isGlobal = parameter.getAnnotationByName("GPUGlobal").isPresent();
        boolean isConstantAddressSpace = parameter.getAnnotationByName("GPUConstant").isPresent();
        boolean isLocal = parameter.getAnnotationByName("GPULocal").isPresent();
        int addressSpaceAnnotations = (isGlobal ? 1 : 0) + (isConstantAddressSpace ? 1 : 0) + (isLocal ? 1 : 0);
        if (addressSpaceAnnotations > 1) {
            throw new IllegalArgumentException("GPU parameter cannot declare multiple address space annotations: " + parameter);
        }
        boolean constant = parameter.getAnnotationByName("GPUGlobal")
                .filter(annotation -> annotation.isNormalAnnotationExpr())
                .flatMap(annotation -> annotation.asNormalAnnotationExpr().getPairs().stream()
                        .filter(pair -> pair.getNameAsString().equals("constant"))
                        .findFirst()
                        .map(pair -> pair.getValue().toString()))
                .map(Boolean::parseBoolean)
                .orElse(false);

        return new ParsedGpuParameter(
                parameter.getNameAsString(),
                parameter.getTypeAsString(),
                resolveAddressSpace(isGlobal, isConstantAddressSpace, isLocal),
                constant,
                parseOpenClQualifiers(parameter)
        );
    }

    private List<String> parseOpenClQualifiers(Parameter parameter) {
        List<String> qualifiers = new ArrayList<>(GpuStructParser.parseStringListAnnotation(
                parameter.getAnnotations(),
                "OpenCLQualifiers",
                "OpenCLQualifiers"
        ));
        qualifiers.addAll(GpuStructParser.parseGpuAttributesForOpenCl(parameter.getAnnotations()));
        return List.copyOf(qualifiers);
    }

    private List<String> parseOpenClAttributes(MethodDeclaration declaration) {
        List<String> attributes = new ArrayList<>(GpuStructParser.parseOpenClAttributes(declaration.getAnnotations()));
        parseWorkGroupSizeAttribute(declaration).ifPresent(attributes::add);
        parseWorkGroupSizeHintAttribute(declaration).ifPresent(attributes::add);
        parseVectorTypeHintAttribute(declaration).ifPresent(attributes::add);
        if (declaration.getAnnotationByName("GPUAlwaysInline").isPresent()) {
            attributes.add("always_inline");
        }
        return List.copyOf(attributes);
    }

    private List<GpuAttributeMetadata> parseAttributeMetadata(MethodDeclaration declaration) {
        ArrayList<GpuAttributeMetadata> metadata = new ArrayList<>();
        parseWorkGroupSizeMetadata(declaration).ifPresent(metadata::add);
        parseWorkGroupSizeHintMetadata(declaration).ifPresent(metadata::add);
        parseVectorTypeHintMetadata(declaration).ifPresent(metadata::add);
        if (declaration.getAnnotationByName("GPUAlwaysInline").isPresent()) {
            metadata.add(GpuAttributeMetadata.alwaysInline("GPUAlwaysInline"));
        }
        return List.copyOf(metadata);
    }

    private java.util.Optional<String> parseWorkGroupSizeAttribute(MethodDeclaration declaration) {
        return declaration.getAnnotationByName("GPUWorkGroupSize")
                .map(annotation -> workGroupSizeAttribute(annotation, "GPUWorkGroupSize", "reqd_work_group_size"));
    }

    private java.util.Optional<GpuAttributeMetadata> parseWorkGroupSizeMetadata(MethodDeclaration declaration) {
        return declaration.getAnnotationByName("GPUWorkGroupSize")
                .map(annotation -> workGroupSizeMetadata(annotation, "GPUWorkGroupSize", "required"));
    }

    private java.util.Optional<String> parseWorkGroupSizeHintAttribute(MethodDeclaration declaration) {
        return declaration.getAnnotationByName("GPUWorkGroupSizeHint")
                .map(annotation -> workGroupSizeAttribute(annotation, "GPUWorkGroupSizeHint", "work_group_size_hint"));
    }

    private java.util.Optional<GpuAttributeMetadata> parseWorkGroupSizeHintMetadata(MethodDeclaration declaration) {
        return declaration.getAnnotationByName("GPUWorkGroupSizeHint")
                .map(annotation -> workGroupSizeMetadata(annotation, "GPUWorkGroupSizeHint", "hint"));
    }

    private java.util.Optional<String> parseVectorTypeHintAttribute(MethodDeclaration declaration) {
        return declaration.getAnnotationByName("GPUVectorTypeHint")
                .map(annotation -> "vec_type_hint(" + parseStringAnnotationValue(annotation, "value", "GPUVectorTypeHint.value") + ")");
    }

    private java.util.Optional<GpuAttributeMetadata> parseVectorTypeHintMetadata(MethodDeclaration declaration) {
        return declaration.getAnnotationByName("GPUVectorTypeHint")
                .map(annotation -> GpuAttributeMetadata.vectorTypeHint(
                        parseStringAnnotationValue(annotation, "value", "GPUVectorTypeHint.value"),
                        "GPUVectorTypeHint"
                ));
    }

    private String workGroupSizeAttribute(AnnotationExpr annotation, String annotationName, String attributeName) {
        int x = parseIntAnnotationValue(annotation, "x", 1, annotationName);
        int y = parseIntAnnotationValue(annotation, "y", 1, annotationName);
        int z = parseIntAnnotationValue(annotation, "z", 1, annotationName);
        if (x <= 0 || y <= 0 || z <= 0) {
            throw new IllegalArgumentException(annotationName + " dimensions must be positive integers");
        }
        return attributeName + "(" + x + ", " + y + ", " + z + ")";
    }

    private GpuAttributeMetadata workGroupSizeMetadata(AnnotationExpr annotation, String annotationName, String mode) {
        int x = parseIntAnnotationValue(annotation, "x", 1, annotationName);
        int y = parseIntAnnotationValue(annotation, "y", 1, annotationName);
        int z = parseIntAnnotationValue(annotation, "z", 1, annotationName);
        if (x <= 0 || y <= 0 || z <= 0) {
            throw new IllegalArgumentException(annotationName + " dimensions must be positive integers");
        }
        return "required".equals(mode)
                ? GpuAttributeMetadata.requiredWorkGroupSize(x, y, z, annotationName)
                : GpuAttributeMetadata.workGroupSizeHint(x, y, z, annotationName);
    }

    private int parseIntAnnotationValue(AnnotationExpr annotation, String propertyName, int defaultValue, String errorLabel) {
        if (!annotation.isNormalAnnotationExpr()) {
            return defaultValue;
        }
        return annotation.asNormalAnnotationExpr().getPairs().stream()
                .filter(pair -> pair.getNameAsString().equals(propertyName))
                .findFirst()
                .map(pair -> parsePositiveIntLiteral(pair.getValue(), errorLabel + "." + propertyName))
                .orElse(defaultValue);
    }

    private int parsePositiveIntLiteral(com.github.javaparser.ast.expr.Expression expression, String errorLabel) {
        if (expression.isIntegerLiteralExpr()) {
            return expression.asIntegerLiteralExpr().asInt();
        }
        throw new IllegalArgumentException(errorLabel + " must be an integer literal: " + expression);
    }

    private String parseStringAnnotationValue(AnnotationExpr annotation, String propertyName, String errorLabel) {
        com.github.javaparser.ast.expr.Expression value;
        if (annotation.isSingleMemberAnnotationExpr()) {
            value = annotation.asSingleMemberAnnotationExpr().getMemberValue();
        } else if (annotation.isNormalAnnotationExpr()) {
            value = annotation.asNormalAnnotationExpr().getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals(propertyName))
                    .findFirst()
                    .map(pair -> pair.getValue())
                    .orElseThrow(() -> new IllegalArgumentException(errorLabel + " must be declared"));
        } else {
            throw new IllegalArgumentException(errorLabel + " must be declared");
        }
        if (value instanceof StringLiteralExpr stringLiteralExpr) {
            return stringLiteralExpr.asString();
        }
        if (value instanceof TextBlockLiteralExpr textBlockLiteralExpr) {
            return textBlockLiteralExpr.asString();
        }
        if (value instanceof LiteralStringValueExpr literalStringValueExpr) {
            return literalStringValueExpr.getValue();
        }
        throw new IllegalArgumentException(errorLabel + " must be a string literal: " + value);
    }

    private GpuAddressSpace resolveAddressSpace(boolean isGlobal, boolean isConstantAddressSpace, boolean isLocal) {
        if (isGlobal) {
            return GpuAddressSpace.GLOBAL;
        }
        if (isConstantAddressSpace) {
            return GpuAddressSpace.CONSTANT;
        }
        if (isLocal) {
            return GpuAddressSpace.LOCAL;
        }
        return GpuAddressSpace.PRIVATE;
    }

    private boolean parseInlineFlag(MethodDeclaration declaration) {
        return declaration.getAnnotationByName("CCode")
                .filter(annotation -> annotation.isNormalAnnotationExpr())
                .flatMap(annotation -> annotation.asNormalAnnotationExpr().getPairs().stream()
                        .filter(pair -> pair.getNameAsString().equals("inline"))
                        .findFirst()
                        .map(pair -> pair.getValue().toString()))
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    private String parseAnnotationStringValue(MethodDeclaration declaration, String propertyName) {
        return declaration.getAnnotationByName("CCode")
                .filter(annotation -> annotation.isNormalAnnotationExpr())
                .flatMap(annotation -> annotation.asNormalAnnotationExpr().getPairs().stream()
                        .filter(pair -> pair.getNameAsString().equals(propertyName))
                        .findFirst()
                        .map(pair -> pair.getValue()))
                .filter(LiteralStringValueExpr.class::isInstance)
                .map(LiteralStringValueExpr.class::cast)
                .map(this::literalStringValue)
                .orElse("");
    }

    private String parseNativeCode(MethodDeclaration declaration) {
        return parseAnnotationStringValue(declaration, "code");
    }

    private String literalStringValue(LiteralStringValueExpr literal) {
        if (literal instanceof TextBlockLiteralExpr textBlockLiteral) {
            return textBlockLiteral.asString();
        }
        if (literal instanceof StringLiteralExpr stringLiteral) {
            return stringLiteral.asString();
        }
        return literal.getValue();
    }
}
