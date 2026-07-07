package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModuleMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuStructFieldMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuStructMetadata;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Assembles the first reconstructable flat IrGpu slice into OpenCL-C source text.
 */
public final class OpenClIrGpuSourceAssembler {

    public static final OpenClIrGpuSourceAssembler INSTANCE = new OpenClIrGpuSourceAssembler();

    private OpenClIrGpuSourceAssembler() {
    }

    public OpenClIrGpuSourceAssemblyResult assemble(
            IrGpuArtifact artifact,
            IrGpuMethodBody entryBody,
            String emittedBody,
            Map<String, String> emittedHelperBodies
    ) {
        ArrayList<String> blockers = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        if (artifact == null) {
            blockers.add("irgpu-artifact-missing");
            return new OpenClIrGpuSourceAssemblyResult(false, "", blockers, diagnostics);
        }
        if (entryBody == null) {
            blockers.add("irgpu-entry-body-missing");
            return new OpenClIrGpuSourceAssemblyResult(false, "", blockers, diagnostics);
        }
        if (emittedBody == null || emittedBody.isBlank()) {
            blockers.add("irgpu-entry-opencl-body-missing");
        }

        Map<String, String> helperBodies = emittedHelperBodies == null ? Map.of() : emittedHelperBodies;
        List<IrGpuMethodBody> helpers = artifact.module().methodBodies().stream()
                .filter(methodBody -> "helper".equals(methodBody.role()))
                .toList();
        ArrayList<String> helperSources = new ArrayList<>();
        for (IrGpuMethodBody helperBody : helpers) {
            IrGpuModuleMethod helperMethod = findHelperMethod(artifact, helperBody.emittedName());
            if (helperMethod == null) {
                blockers.add("irgpu-helper-" + safeEmittedName(helperBody) + "-metadata-missing");
                continue;
            }
            String helperSource = emitHelper(helperMethod, helperBodies.get(helperBody.emittedName()), blockers);
            if (!helperSource.isBlank()) {
                helperSources.add(helperSource);
            }
        }
        if (artifact.entryParameters().isEmpty()) {
            blockers.add("irgpu-entry-parameter-metadata-missing");
        }

        ArrayList<String> parameters = new ArrayList<>();
        for (IrGpuEntryParameter parameter : artifact.entryParameters()) {
            String emittedParameter = emitEntryParameter(parameter, blockers);
            if (!emittedParameter.isBlank()) {
                parameters.add(emittedParameter);
            }
        }
        if (!blockers.isEmpty()) {
            return new OpenClIrGpuSourceAssemblyResult(false, "", blockers, diagnostics);
        }

        String emittedName = entryBody.emittedName().isBlank() ? artifact.module().entryEmittedName() : entryBody.emittedName();
        StringBuilder source = new StringBuilder();
        for (IrGpuStructMetadata struct : artifact.structMetadata()) {
            source.append(emitStruct(struct));
        }
        if (!artifact.structMetadata().isEmpty()) {
            source.append('\n');
        }
        for (IrGpuModuleMethod helperMethod : artifact.module().helperMethods()) {
            String prototype = emitHelperPrototype(helperMethod, blockers);
            if (!prototype.isBlank()) {
                source.append(prototype);
            }
        }
        if (!artifact.module().helperMethods().isEmpty()) {
            source.append('\n');
        }
        for (String helperSource : helperSources) {
            source.append(helperSource);
        }
        emitAttributes(source, artifact.module().entryOpenClAttributes(), false);
        source.append("__kernel void ")
                .append(emittedName)
                .append("(")
                .append(String.join(", ", parameters))
                .append(") {\n")
                .append(emittedBody)
                .append("}\n");
        diagnostics.add("OpenCL source assembler emitted entry kernel " + emittedName);
        diagnostics.add("OpenCL source assembler emitted " + parameters.size() + " entry parameter(s)");
        diagnostics.add("OpenCL source assembler emitted " + helperSources.size() + " helper function(s)");
        diagnostics.add("OpenCL source assembler emitted " + artifact.structMetadata().size() + " struct typedef(s)");
        return new OpenClIrGpuSourceAssemblyResult(true, source.toString(), blockers, diagnostics);
    }

    private static String emitStruct(IrGpuStructMetadata struct) {
        StringBuilder builder = new StringBuilder();
        builder.append("typedef struct");
        if (!struct.openClAttributes().isEmpty()) {
            builder.append(' ');
            emitAttributes(builder, struct.openClAttributes(), true);
        }
        builder.append("{\n");
        for (IrGpuStructFieldMetadata field : struct.fields()) {
            builder.append("    ")
                    .append(emitType(field.javaType()))
                    .append(' ')
                    .append(field.name());
            emitAttributes(builder, field.openClAttributes(), false);
            builder.append(";\n");
        }
        builder.append("} ")
                .append(GpuTypeSupport.simpleTypeName(struct.ownerSimpleName()))
                .append(";\n");
        return builder.toString();
    }

    private static void emitAttributes(StringBuilder builder, List<String> attributes, boolean structPrefix) {
        if (attributes.isEmpty()) {
            return;
        }
        if (structPrefix) {
            builder.append(attributes.stream().collect(Collectors.joining(" "))).append(' ');
            return;
        }
        builder.append("__attribute__((")
                .append(String.join(", ", attributes))
                .append(")) ");
    }

    private static String emitHelper(IrGpuModuleMethod helperMethod, String emittedBody, List<String> blockers) {
        String emittedName = helperMethod.emittedName().isBlank() ? helperMethod.name() : helperMethod.emittedName();
        if (helperMethod.returnType().isBlank() || "unknown".equals(helperMethod.returnType())) {
            blockers.add("irgpu-helper-" + sanitize(emittedName) + "-return-type-missing");
            return "";
        }
        if (emittedBody == null || emittedBody.isBlank()) {
            blockers.add("irgpu-helper-" + sanitize(emittedName) + "-opencl-body-missing");
            return "";
        }
        ArrayList<String> parameters = new ArrayList<>();
        for (IrGpuEntryParameter parameter : helperMethod.parameters()) {
            String emittedParameter = emitParameter(parameter, "irgpu-helper-" + sanitize(emittedName), blockers);
            if (!emittedParameter.isBlank()) {
                parameters.add(emittedParameter);
            }
        }
        if (blockers.stream().anyMatch(blocker -> blocker.startsWith("irgpu-helper-" + sanitize(emittedName)))) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendInlinePrefix(builder, helperMethod);
        emitAttributes(builder, helperMethod.openClAttributes(), false);
        builder.append(emitType(helperMethod.returnType()))
                .append(" ")
                .append(emittedName)
                .append("(")
                .append(String.join(", ", parameters))
                .append(") {\n")
                .append(emittedBody)
                .append("}\n");
        return builder.toString();
    }

    private static String emitHelperPrototype(IrGpuModuleMethod helperMethod, List<String> blockers) {
        String emittedName = helperMethod.emittedName().isBlank() ? helperMethod.name() : helperMethod.emittedName();
        if (helperMethod.returnType().isBlank() || "unknown".equals(helperMethod.returnType())) {
            blockers.add("irgpu-helper-" + sanitize(emittedName) + "-return-type-missing");
            return "";
        }
        ArrayList<String> parameters = new ArrayList<>();
        for (IrGpuEntryParameter parameter : helperMethod.parameters()) {
            String emittedParameter = emitParameter(parameter, "irgpu-helper-" + sanitize(emittedName), blockers);
            if (!emittedParameter.isBlank()) {
                parameters.add(emittedParameter);
            }
        }
        if (blockers.stream().anyMatch(blocker -> blocker.startsWith("irgpu-helper-" + sanitize(emittedName)))) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendInlinePrefix(builder, helperMethod);
        emitAttributes(builder, helperMethod.openClAttributes(), false);
        builder.append(emitType(helperMethod.returnType()))
                .append(" ")
                .append(emittedName)
                .append("(")
                .append(String.join(", ", parameters))
                .append(");\n");
        return builder.toString();
    }

    private static void appendInlinePrefix(StringBuilder builder, IrGpuModuleMethod helperMethod) {
        if (helperMethod.inline()) {
            builder.append("inline ");
        }
    }

    private static String emitEntryParameter(IrGpuEntryParameter parameter, List<String> blockers) {
        return emitParameter(parameter, "irgpu-entry-parameter", blockers);
    }

    private static String emitParameter(IrGpuEntryParameter parameter, String blockerPrefix, List<String> blockers) {
        if (parameter.name().isBlank()) {
            blockers.add(blockerPrefix + "-name-missing");
            return "";
        }
        String javaType = parameter.javaType();
        try {
            if (GpuTypeSupport.isSupportedImageOrSamplerType(javaType)) {
                return emitType(javaType) + " " + parameter.name();
            }
            if (GpuTypeSupport.isSupportedPointerType(javaType)) {
                return addressSpacePrefix(GpuTypeSupport.pointerAddressSpace(javaType))
                        + qualifiers(parameter.openClQualifiers(), "CONSTANT".equals(GpuTypeSupport.pointerAddressSpace(javaType)))
                        + emitType(GpuTypeSupport.pointerValueType(javaType))
                        + "*"
                        + restrictPostfix(parameter.openClQualifiers())
                        + " "
                        + parameter.name();
            }
            if (javaType.endsWith("[]")) {
                String elementType = GpuTypeSupport.componentType(javaType);
                return addressSpacePrefix(parameter.addressSpace())
                        + qualifiers(parameter.openClQualifiers(), parameter.constant())
                        + emitType(elementType)
                        + "*"
                        + restrictPostfix(parameter.openClQualifiers())
                        + " "
                        + parameter.name();
            }
            if ("GLOBAL".equals(parameter.addressSpace())) {
                return "__global " + emitType(javaType) + "* " + parameter.name();
            }
            return emitType(javaType) + " " + parameter.name();
        } catch (IllegalArgumentException exception) {
            blockers.add(blockerPrefix + "-unsupported-type-" + sanitize(javaType));
            return "";
        }
    }

    private static IrGpuModuleMethod findHelperMethod(IrGpuArtifact artifact, String emittedName) {
        return artifact.module().helperMethods().stream()
                .filter(helper -> helper.emittedName().equals(emittedName))
                .findFirst()
                .orElse(null);
    }

    private static String safeEmittedName(IrGpuMethodBody methodBody) {
        String emittedName = methodBody.emittedName().isBlank() ? methodBody.name() : methodBody.emittedName();
        return sanitize(emittedName);
    }

    private static String emitType(String javaType) {
        if (GpuTypeSupport.isSupportedPointerType(javaType)) {
            return emitType(GpuTypeSupport.pointerValueType(javaType));
        }
        if (GpuTypeSupport.isSupportedScalarAliasType(javaType)) {
            return GpuTypeSupport.openClScalarAliasTypeName(javaType);
        }
        if (GpuTypeSupport.isSupportedImageOrSamplerType(javaType)) {
            return GpuTypeSupport.openClImageOrSamplerTypeName(javaType);
        }
        if (GpuTypeSupport.isSupportedVectorType(javaType)) {
            return GpuTypeSupport.openClVectorTypeName(javaType);
        }
        return switch (javaType) {
            case "byte" -> "char";
            case "char" -> "ushort";
            case "boolean" -> "bool";
            default -> GpuTypeSupport.simpleTypeName(javaType);
        };
    }

    private static String addressSpacePrefix(String addressSpace) {
        return switch (addressSpace) {
            case "GLOBAL" -> "__global ";
            case "CONSTANT" -> "__constant ";
            case "LOCAL" -> "__local ";
            default -> "";
        };
    }

    private static String qualifiers(List<String> openClQualifiers, boolean implicitConst) {
        java.util.LinkedHashSet<String> qualifiers = new java.util.LinkedHashSet<>();
        if (implicitConst) {
            qualifiers.add("const");
        }
        openClQualifiers.stream()
                .filter(qualifier -> !"restrict".equals(qualifier))
                .forEach(qualifiers::add);
        if (qualifiers.isEmpty()) {
            return "";
        }
        return qualifiers.stream().collect(Collectors.joining(" ")) + " ";
    }

    private static String restrictPostfix(List<String> openClQualifiers) {
        return openClQualifiers.contains("restrict") ? " restrict" : "";
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9_-]", "-");
    }
}
