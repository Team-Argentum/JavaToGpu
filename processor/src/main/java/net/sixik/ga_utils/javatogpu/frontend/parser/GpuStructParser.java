package net.sixik.ga_utils.javatogpu.frontend.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstant;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStruct;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuStructField;

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
        for (FieldDeclaration fieldDeclaration : classDeclaration.getFields()) {
            fieldDeclaration.getVariables().forEach(variable -> {
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
                        parseOpenClAttributes(fieldDeclaration.getAnnotations())
                ));
            });
        }

        return new ParsedGpuStruct(
                resolvedSimpleName,
                resolvedQualifiedName,
                List.copyOf(fields),
                List.copyOf(constants),
                parseOpenClAttributes(classDeclaration.getAnnotations())
        );
    }

    static List<String> parseOpenClAttributes(NodeList<AnnotationExpr> annotations) {
        return parseStringListAnnotation(annotations, "OpenCLAttributes", "OpenCLAttributes");
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
