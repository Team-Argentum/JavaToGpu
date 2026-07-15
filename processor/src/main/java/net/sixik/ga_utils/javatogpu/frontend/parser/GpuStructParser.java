package net.sixik.ga_utils.javatogpu.frontend.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.api.GpuVendorTarget;
import net.sixik.ga_utils.javatogpu.backend.GpuBackendSupport;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstant;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstantData;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuConstantDataKind;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAttributeMetadata;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStructField;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.ArrayList;
import java.util.List;

public final class GpuStructParser {

    public ParsedGpuStruct parseStruct(String structSource) {
        return parseStruct(structSource, "", "");
    }

    public ParsedGpuStruct parseStruct(String structSource, String ownerSimpleName, String ownerQualifiedName) {
        TypeDeclaration<?> declaration = StaticJavaParser.parseBodyDeclaration(structSource)
                .toTypeDeclaration()
                .orElseThrow(() -> new IllegalArgumentException("GPU struct source must declare a type"));
        if (!(declaration instanceof ClassOrInterfaceDeclaration classDeclaration)) {
            throw new IllegalArgumentException("GPU struct source must declare a class or interface");
        }

        String resolvedSimpleName = ownerSimpleName == null || ownerSimpleName.isBlank()
                ? classDeclaration.getNameAsString()
                : ownerSimpleName;
        String resolvedQualifiedName = ownerQualifiedName == null || ownerQualifiedName.isBlank()
                ? resolvedSimpleName
                : ownerQualifiedName;

        List<ParsedGpuStructField> fields = new ArrayList<>();
        List<ParsedGpuConstant> constants = new ArrayList<>();
        List<ParsedGpuConstantData> constantData = new ArrayList<>();
        for (FieldDeclaration fieldDeclaration : classDeclaration.getFields()) {
            fieldDeclaration.getVariables().forEach(variable -> {
                if (fieldDeclaration.getAnnotationByName("GPUConstantData").isPresent()) {
                    if (variable.getInitializer().isPresent()
                            && GpuTypeSupport.isArrayType(variable.getTypeAsString())
                            && GpuTypeSupport.isSupportedScalarType(GpuTypeSupport.componentType(variable.getTypeAsString()))) {
                        constantData.add(new ParsedGpuConstantData(
                                resolvedSimpleName,
                                resolvedQualifiedName,
                                variable.getNameAsString(),
                                variable.getTypeAsString(),
                                variable.getInitializer().orElseThrow().toString(),
                                GpuConstantDataKind.EMBEDDED
                        ));
                    }
                    return;
                }
                if (fieldDeclaration.getAnnotationByName("GPUExternConstantData").isPresent()) {
                    if (GpuTypeSupport.isArrayType(variable.getTypeAsString())
                            && GpuTypeSupport.isSupportedScalarType(GpuTypeSupport.componentType(variable.getTypeAsString()))) {
                        constantData.add(new ParsedGpuConstantData(
                                resolvedSimpleName,
                                resolvedQualifiedName,
                                variable.getNameAsString(),
                                variable.getTypeAsString(),
                                variable.getInitializer().map(Expression::toString).orElse(""),
                                GpuConstantDataKind.EXTERN
                        ));
                    }
                    return;
                }
                if (fieldDeclaration.isStatic() && fieldDeclaration.isFinal() && variable.getInitializer().isPresent()) {
                    constants.add(new ParsedGpuConstant(
                            resolvedSimpleName,
                            resolvedQualifiedName,
                            variable.getNameAsString(),
                            variable.getTypeAsString(),
                            variable.getInitializer().orElseThrow().toString()
                    ));
                    return;
                }
                fields.add(new ParsedGpuStructField(
                        variable.getNameAsString(),
                        variable.getTypeAsString(),
                        parseOpenClAttributes(fieldDeclaration.getAnnotations()),
                        parseAttributeMetadata(fieldDeclaration.getAnnotations())
                ));
            });
        }

        return new ParsedGpuStruct(
                resolvedSimpleName,
                resolvedQualifiedName,
                List.copyOf(fields),
                List.copyOf(constants),
                List.copyOf(constantData),
                parseOpenClAttributes(classDeclaration.getAnnotations()),
                parseAttributeMetadata(classDeclaration.getAnnotations())
        );
    }

    static List<String> parseOpenClAttributes(NodeList<AnnotationExpr> annotations) {
        List<String> attributes = new ArrayList<>(parseStringListAnnotation(
                annotations,
                "OpenCLAttributes",
                "OpenCLAttributes"
        ));
        attributes.addAll(parseGpuAttributesForOpenCl(annotations));
        if (hasAnnotation(annotations, "GPUPacked")) {
            attributes.add("packed");
        }
        parseAlignedAttribute(annotations).ifPresent(attributes::add);
        return List.copyOf(attributes);
    }

    static List<GpuAttributeMetadata> parseAttributeMetadata(NodeList<AnnotationExpr> annotations) {
        ArrayList<GpuAttributeMetadata> metadata = new ArrayList<>();
        if (hasAnnotation(annotations, "GPUPacked")) {
            metadata.add(GpuAttributeMetadata.packed("GPUPacked"));
        }
        parseAlignedMetadata(annotations).ifPresent(metadata::add);
        return List.copyOf(metadata);
    }

    private static boolean hasAnnotation(NodeList<AnnotationExpr> annotations, String annotationName) {
        return annotations.stream().anyMatch(annotation -> annotation.getNameAsString().equals(annotationName));
    }

    private static java.util.Optional<String> parseAlignedAttribute(NodeList<AnnotationExpr> annotations) {
        return annotations.stream()
                .filter(annotation -> annotation.getNameAsString().equals("GPUAligned"))
                .findFirst()
                .map(annotation -> "aligned(" + parsePositiveIntAnnotationValue(annotation, "value", "GPUAligned.value") + ")");
    }

    private static java.util.Optional<GpuAttributeMetadata> parseAlignedMetadata(NodeList<AnnotationExpr> annotations) {
        return annotations.stream()
                .filter(annotation -> annotation.getNameAsString().equals("GPUAligned"))
                .findFirst()
                .map(annotation -> GpuAttributeMetadata.aligned(
                        parsePositiveIntAnnotationValue(annotation, "value", "GPUAligned.value"),
                        "GPUAligned"
                ));
    }

    static int parsePositiveIntAnnotationValue(AnnotationExpr annotation, String propertyName, String errorLabel) {
        Expression value;
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
        if (!value.isIntegerLiteralExpr()) {
            throw new IllegalArgumentException(errorLabel + " must be an integer literal: " + value);
        }
        int intValue = value.asIntegerLiteralExpr().asInt();
        if (intValue <= 0) {
            throw new IllegalArgumentException(errorLabel + " must be a positive integer");
        }
        return intValue;
    }

    static List<String> parseGpuAttributesForOpenCl(NodeList<AnnotationExpr> annotations) {
        ArrayList<String> attributes = new ArrayList<>();
        for (AnnotationExpr annotation : annotations) {
            if (annotation.getNameAsString().equals("GPUAttribute")) {
                if (supportsOpenClAttributeSelector(annotation)) {
                    attributes.addAll(attributeValues(annotation, "GPUAttribute"));
                }
                continue;
            }
            if (annotation.getNameAsString().equals("GPUAttributes")) {
                for (AnnotationExpr nestedAttribute : nestedGpuAttributes(annotation)) {
                    if (supportsOpenClAttributeSelector(nestedAttribute)) {
                        attributes.addAll(attributeValues(nestedAttribute, "GPUAttribute"));
                    }
                }
            }
        }
        return List.copyOf(attributes);
    }

    private static List<AnnotationExpr> nestedGpuAttributes(AnnotationExpr annotation) {
        Expression value = annotation.isSingleMemberAnnotationExpr()
                ? annotation.asSingleMemberAnnotationExpr().getMemberValue()
                : annotation.asNormalAnnotationExpr().getPairs().stream()
                .filter(pair -> pair.getNameAsString().equals("value"))
                .findFirst()
                .map(pair -> pair.getValue())
                .orElseThrow(() -> new IllegalArgumentException("GPUAttributes must declare a value"));
        if (value instanceof ArrayInitializerExpr arrayInitializerExpr) {
            return arrayInitializerExpr.getValues().stream()
                    .map(GpuStructParser::nestedGpuAttribute)
                    .toList();
        }
        return List.of(nestedGpuAttribute(value));
    }

    private static AnnotationExpr nestedGpuAttribute(Expression expression) {
        if (expression instanceof NormalAnnotationExpr normalAnnotationExpr
                && normalAnnotationExpr.getNameAsString().equals("GPUAttribute")) {
            return normalAnnotationExpr;
        }
        throw new IllegalArgumentException("GPUAttributes values must be GPUAttribute annotations: " + expression);
    }

    private static boolean supportsOpenClAttributeSelector(AnnotationExpr annotation) {
        if (!annotation.isNormalAnnotationExpr()) {
            throw new IllegalArgumentException("GPUAttribute must declare backend and value properties");
        }
        GpuBackendTarget backend = annotation.asNormalAnnotationExpr().getPairs().stream()
                .filter(pair -> pair.getNameAsString().equals("backend"))
                .findFirst()
                .map(pair -> GpuBackendSupport.parseBackendTarget(pair.getValue()))
                .orElseThrow(() -> new IllegalArgumentException("GPUAttribute must declare a backend"));
        GpuVendorTarget vendor = annotation.asNormalAnnotationExpr().getPairs().stream()
                .filter(pair -> pair.getNameAsString().equals("vendor"))
                .findFirst()
                .map(pair -> parseVendorTarget(pair.getValue()))
                .orElse(GpuVendorTarget.ANY);
        GpuDeviceClassTarget deviceClass = annotation.asNormalAnnotationExpr().getPairs().stream()
                .filter(pair -> pair.getNameAsString().equals("deviceClass"))
                .findFirst()
                .map(pair -> parseDeviceClassTarget(pair.getValue()))
                .orElse(GpuDeviceClassTarget.ANY);
        return backend == GpuBackendTarget.OPENCL
                && (vendor == GpuVendorTarget.ANY || vendor == GpuVendorTarget.UNKNOWN)
                && (deviceClass == GpuDeviceClassTarget.ANY || deviceClass == GpuDeviceClassTarget.UNKNOWN);
    }

    private static GpuVendorTarget parseVendorTarget(Expression expression) {
        return GpuVendorTarget.valueOf(enumName(expression, "GPU vendor target"));
    }

    private static GpuDeviceClassTarget parseDeviceClassTarget(Expression expression) {
        return GpuDeviceClassTarget.valueOf(enumName(expression, "GPU device class target"));
    }

    private static String enumName(Expression expression, String label) {
        if (expression.isFieldAccessExpr()) {
            return expression.asFieldAccessExpr().getNameAsString();
        }
        if (expression.isNameExpr()) {
            return expression.asNameExpr().getNameAsString();
        }
        throw new IllegalArgumentException("Unsupported " + label + " expression: " + expression);
    }

    static List<String> parseStringListAnnotation(
            NodeList<AnnotationExpr> annotations,
            String annotationName,
            String errorLabel
    ) {
        return annotations.stream()
                .filter(annotation -> annotation.getNameAsString().equals(annotationName))
                .findFirst()
                .map(annotation -> attributeValues(annotation, errorLabel))
                .orElse(List.of());
    }

    private static List<String> attributeValues(AnnotationExpr annotation, String errorLabel) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            return stringValues(annotation.asSingleMemberAnnotationExpr().getMemberValue(), errorLabel);
        }
        if (annotation.isNormalAnnotationExpr()) {
            return annotation.asNormalAnnotationExpr().getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals("value"))
                    .findFirst()
                    .map(pair -> stringValues(pair.getValue(), errorLabel))
                    .orElse(List.of());
        }
        return List.of();
    }

    private static List<String> stringValues(Expression expression, String errorLabel) {
        if (expression instanceof ArrayInitializerExpr arrayInitializerExpr) {
            return arrayInitializerExpr.getValues().stream()
                    .map(value -> stringValue(value, errorLabel))
                    .toList();
        }
        return List.of(stringValue(expression, errorLabel));
    }

    private static String stringValue(Expression expression, String errorLabel) {
        if (expression instanceof StringLiteralExpr stringLiteralExpr) {
            return stringLiteralExpr.asString();
        }
        if (expression instanceof TextBlockLiteralExpr textBlockLiteralExpr) {
            return textBlockLiteralExpr.asString();
        }
        if (expression instanceof LiteralStringValueExpr literalStringValueExpr) {
            return literalStringValueExpr.getValue();
        }
        throw new IllegalArgumentException(errorLabel + " values must be string literals: " + expression);
    }
}
