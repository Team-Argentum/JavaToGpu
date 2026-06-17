package net.sixik.ga_utils.javatogpu.frontend.model;

import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.List;

public record ParsedGpuMethod(
        String ownerSimpleName,
        String ownerQualifiedName,
        String name,
        String returnType,
        List<ParsedGpuParameter> parameters,
        MethodDeclaration declaration,
        boolean inline
) {
}
