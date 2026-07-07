package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.types.GpuTypeSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Assembles the first reconstructable entry-only IrGpu slice into OpenCL-C source text.
 */
public final class OpenClIrGpuSourceAssembler {

    public static final OpenClIrGpuSourceAssembler INSTANCE = new OpenClIrGpuSourceAssembler();

    private OpenClIrGpuSourceAssembler() {
    }

    public OpenClIrGpuSourceAssemblyResult assemble(IrGpuArtifact artifact, IrGpuMethodBody entryBody, String emittedBody) {
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

        List<IrGpuMethodBody> helpers = artifact.module().methodBodies().stream()
                .filter(methodBody -> "helper".equals(methodBody.role()))
                .toList();
        if (!helpers.isEmpty()) {
            blockers.add("irgpu-helper-source-assembly-not-yet-implemented");
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
        String source = "__kernel void "
                + emittedName
                + "("
                + String.join(", ", parameters)
                + ") {\n"
                + emittedBody
                + "}\n";
        diagnostics.add("OpenCL source assembler emitted entry kernel " + emittedName);
        diagnostics.add("OpenCL source assembler emitted " + parameters.size() + " entry parameter(s)");
        return new OpenClIrGpuSourceAssemblyResult(true, source, blockers, diagnostics);
    }

    private static String emitEntryParameter(IrGpuEntryParameter parameter, List<String> blockers) {
        if (parameter.name().isBlank()) {
            blockers.add("irgpu-entry-parameter-name-missing");
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
            blockers.add("irgpu-entry-parameter-unsupported-type-" + sanitize(javaType));
            return "";
        }
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
