package net.sixik.ga_utils.javatogpu.frontend.model;

public record ParsedGpuConstant(
        String ownerSimpleName,
        String ownerQualifiedName,
        String name,
        String javaType,
        String sourceText
) {
}
