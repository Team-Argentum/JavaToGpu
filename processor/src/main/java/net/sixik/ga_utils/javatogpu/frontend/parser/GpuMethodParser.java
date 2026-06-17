package net.sixik.ga_utils.javatogpu.frontend.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuConstant;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;

import java.util.List;

public final class GpuMethodParser {

    public ParsedGpuMethod parseMethod(String methodSource) {
        return parseMethod(methodSource, "", "", List.of());
    }

    public ParsedGpuMethod parseMethod(String methodSource, String ownerSimpleName, String ownerQualifiedName) {
        return parseMethod(methodSource, ownerSimpleName, ownerQualifiedName, List.of());
    }

    public ParsedGpuMethod parseMethod(
            String methodSource,
            String ownerSimpleName,
            String ownerQualifiedName,
            List<ParsedGpuConstant> constants
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
                declaration,
                parseInlineFlag(declaration)
        );
    }

    private ParsedGpuParameter toParsedParameter(Parameter parameter) {
        boolean isGlobal = parameter.getAnnotationByName("GPUGlobal").isPresent();
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
                isGlobal ? GpuAddressSpace.GLOBAL : GpuAddressSpace.PRIVATE,
                constant
        );
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
}
