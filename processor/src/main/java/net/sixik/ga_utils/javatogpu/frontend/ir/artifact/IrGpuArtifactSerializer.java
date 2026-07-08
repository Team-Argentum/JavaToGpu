package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.util.Map;
import java.util.TreeMap;

public final class IrGpuArtifactSerializer {

    private IrGpuArtifactSerializer() {
    }

    public static String serialize(IrGpuArtifact artifact) {
        TreeMap<String, String> properties = new TreeMap<>();
        writeHeader(properties, artifact.header());
        writeModule(properties, artifact.module());
        writeEntryParameters(properties, artifact.entryParameters());
        writeLaunchMetadata(properties, artifact.launchMetadata());
        writeValidationMetadata(properties, artifact.validationMetadata());
        writeFeatureMetadata(properties, artifact.featureMetadata());
        writeRegenerationMetadata(properties, artifact.regenerationMetadata());
        writeStructMetadata(properties, artifact.structMetadata());
        writeConstants(properties, artifact.constants());
        writeConstantData(properties, artifact.constantData());
        writeBackendOutputs(properties, artifact.backendOutputs());
        properties.put("derived.opencl.resource", artifact.derivedOpenClResource());
        properties.put("runtime.defaultBackend", artifact.runtimeDefaultBackend());
        properties.put("runtime.optimizationProfile", artifact.runtimeOptimizationProfile());

        StringBuilder builder = new StringBuilder();
        builder.append("# JavaToGpu backend-neutral IR artifact manifest\n");
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            builder.append(entry.getKey())
                    .append('=')
                    .append(escape(entry.getValue()))
                    .append('\n');
        }
        return builder.toString();
    }

    private static void writeHeader(TreeMap<String, String> properties, IrGpuArtifactHeader header) {
        properties.put("format", header.format());
        properties.put("schemaVersion", Integer.toString(header.schemaVersion()));
        properties.put("compilerArtifact", header.compilerArtifact());
        properties.put("sourceFrontend", header.sourceFrontend());
    }

    private static void writeModule(TreeMap<String, String> properties, IrGpuModule module) {
        properties.put("entryMethod", module.entryMethod());
        properties.put("entryEmittedName", module.entryEmittedName());
        writeStringList(properties, "entry.openClAttribute", module.entryOpenClAttributes());
        properties.put("helper.count", Integer.toString(module.helperMethods().size()));
        for (int index = 0; index < module.helperMethods().size(); index++) {
            IrGpuModuleMethod helper = module.helperMethods().get(index);
            properties.put("helper." + index + ".name", helper.name());
            properties.put("helper." + index + ".emittedName", helper.emittedName());
            properties.put("helper." + index + ".returnType", helper.returnType());
            properties.put("helper." + index + ".inline", Boolean.toString(helper.inline()));
            writeStringList(properties, "helper." + index + ".openClAttribute", helper.openClAttributes());
            writeMethodParameters(properties, "helper." + index + ".parameter", helper.parameters());
        }
        properties.put("struct.count", Integer.toString(module.structs().size()));
        for (int index = 0; index < module.structs().size(); index++) {
            properties.put("struct." + index + ".name", module.structs().get(index));
        }
        properties.put("methodBody.count", Integer.toString(module.methodBodies().size()));
        for (int index = 0; index < module.methodBodies().size(); index++) {
            IrGpuMethodBody methodBody = module.methodBodies().get(index);
            String prefix = "methodBody." + index + ".";
            properties.put(prefix + "role", methodBody.role());
            properties.put(prefix + "name", methodBody.name());
            properties.put(prefix + "emittedName", methodBody.emittedName());
            properties.put(prefix + "format", methodBody.format());
            properties.put(prefix + "body", methodBody.body());
            writeBodyIndex(properties, prefix, methodBody.bodyIndex());
            writeSourceLocation(properties, prefix, methodBody.sourceLocation());
            properties.put(prefix + "helperDependency.count", Integer.toString(methodBody.helperDependencies().size()));
            for (int dependencyIndex = 0; dependencyIndex < methodBody.helperDependencies().size(); dependencyIndex++) {
                properties.put(
                        prefix + "helperDependency." + dependencyIndex,
                        methodBody.helperDependencies().get(dependencyIndex)
                );
            }
        }
    }

    private static void writeBodyIndex(
            TreeMap<String, String> properties,
            String prefix,
            IrGpuBodyIndex bodyIndex
    ) {
        IrGpuBodyIndex index = bodyIndex == null ? IrGpuBodyIndex.empty() : bodyIndex;
        properties.put(prefix + "bodyIndex.statement.count", Integer.toString(index.statementCount()));
        writeStringList(properties, prefix + "bodyIndex.statementKind", index.statementKinds());
        writeStringList(properties, prefix + "bodyIndex.expressionKind", index.expressionKinds());
        writeStringList(properties, prefix + "bodyIndex.intrinsicCall", index.intrinsicCalls());
        writeStringList(properties, prefix + "bodyIndex.helperCall", index.helperCalls());
        properties.put(prefix + "bodyIndex.writesMemory", Boolean.toString(index.writesMemory()));
        properties.put(prefix + "bodyIndex.hasControlFlow", Boolean.toString(index.hasControlFlow()));
    }

    private static void writeStringList(TreeMap<String, String> properties, String prefix, java.util.List<String> values) {
        java.util.List<String> safeValues = values == null ? java.util.List.of() : values;
        properties.put(prefix + ".count", Integer.toString(safeValues.size()));
        for (int index = 0; index < safeValues.size(); index++) {
            properties.put(prefix + "." + index, safeValues.get(index));
        }
    }

    private static void writeSourceLocation(
            TreeMap<String, String> properties,
            String prefix,
            IrGpuSourceLocation sourceLocation
    ) {
        IrGpuSourceLocation location = sourceLocation == null
                ? IrGpuSourceLocation.unknown("")
                : sourceLocation;
        properties.put(prefix + "source.kind", location.sourceKind());
        properties.put(prefix + "source.ownerQualifiedName", location.ownerQualifiedName());
        properties.put(prefix + "source.methodName", location.methodName());
        properties.put(prefix + "source.beginLine", Integer.toString(location.beginLine()));
        properties.put(prefix + "source.beginColumn", Integer.toString(location.beginColumn()));
        properties.put(prefix + "source.endLine", Integer.toString(location.endLine()));
        properties.put(prefix + "source.endColumn", Integer.toString(location.endColumn()));
    }

    private static void writeEntryParameters(
            TreeMap<String, String> properties,
            java.util.List<IrGpuEntryParameter> parameters
    ) {
        properties.put("entryParameter.count", Integer.toString(parameters.size()));
        for (int index = 0; index < parameters.size(); index++) {
            IrGpuEntryParameter parameter = parameters.get(index);
            String prefix = "entryParameter." + index + ".";
            writeParameter(properties, prefix, parameter);
        }
    }

    private static void writeMethodParameters(
            TreeMap<String, String> properties,
            String prefix,
            java.util.List<IrGpuEntryParameter> parameters
    ) {
        properties.put(prefix + ".count", Integer.toString(parameters.size()));
        for (int index = 0; index < parameters.size(); index++) {
            writeParameter(properties, prefix + "." + index + ".", parameters.get(index));
        }
    }

    private static void writeParameter(
            TreeMap<String, String> properties,
            String prefix,
            IrGpuEntryParameter parameter
    ) {
        properties.put(prefix + "name", parameter.name());
        properties.put(prefix + "javaType", parameter.javaType());
        properties.put(prefix + "addressSpace", parameter.addressSpace());
        properties.put(prefix + "constant", Boolean.toString(parameter.constant()));
        properties.put(prefix + "openClQualifier.count", Integer.toString(parameter.openClQualifiers().size()));
        for (int qualifierIndex = 0; qualifierIndex < parameter.openClQualifiers().size(); qualifierIndex++) {
            properties.put(
                    prefix + "openClQualifier." + qualifierIndex,
                    parameter.openClQualifiers().get(qualifierIndex)
            );
        }
    }

    private static void writeLaunchMetadata(TreeMap<String, String> properties, IrGpuLaunchMetadata launchMetadata) {
        IrGpuLaunchMetadata metadata = launchMetadata == null
                ? IrGpuLaunchMetadata.defaultOneDimensional()
                : launchMetadata;
        properties.put("launch.requiredDimensions", Integer.toString(metadata.requiredDimensions()));
        properties.put("launch.globalWorkSizeSource", metadata.globalWorkSizeSource());
        properties.put("launch.explicitConfigSupported", Boolean.toString(metadata.explicitConfigSupported()));
    }

    private static void writeValidationMetadata(
            TreeMap<String, String> properties,
            IrGpuValidationMetadata validationMetadata
    ) {
        IrGpuValidationMetadata metadata = validationMetadata == null
                ? IrGpuValidationMetadata.frontendSubset()
                : validationMetadata;
        properties.put("validation.contractVersion", metadata.contractVersion());
        properties.put("validation.safetyMode", metadata.safetyMode());
        properties.put("validation.optimizerEvidenceRequired", Boolean.toString(metadata.optimizerEvidenceRequired()));
    }

    private static void writeFeatureMetadata(TreeMap<String, String> properties, IrGpuFeatureMetadata featureMetadata) {
        IrGpuFeatureMetadata metadata = featureMetadata == null ? IrGpuFeatureMetadata.none() : featureMetadata;
        properties.put("feature.required.count", Integer.toString(metadata.requiredFeatures().size()));
        for (int index = 0; index < metadata.requiredFeatures().size(); index++) {
            properties.put("feature.required." + index, metadata.requiredFeatures().get(index));
        }
        properties.put("feature.optional.count", Integer.toString(metadata.optionalFeatures().size()));
        for (int index = 0; index < metadata.optionalFeatures().size(); index++) {
            properties.put("feature.optional." + index, metadata.optionalFeatures().get(index));
        }
    }

    private static void writeRegenerationMetadata(
            TreeMap<String, String> properties,
            IrGpuRegenerationMetadata regenerationMetadata
    ) {
        IrGpuRegenerationMetadata metadata = regenerationMetadata == null
                ? IrGpuRegenerationMetadata.transitionalIrText()
                : regenerationMetadata;
        properties.put("regeneration.backendNeutralSourceReady", Boolean.toString(metadata.backendNeutralSourceReady()));
        properties.put("regeneration.payloadFormat", metadata.payloadFormat());
        properties.put("regeneration.fallbackSource", metadata.fallbackSource());
        writeStringList(properties, "regeneration.blocker", metadata.blockers());
    }

    private static void writeBackendOutputs(TreeMap<String, String> properties, java.util.List<IrGpuBackendOutput> outputs) {
        properties.put("backendOutput.count", Integer.toString(outputs.size()));
        for (int index = 0; index < outputs.size(); index++) {
            IrGpuBackendOutput output = outputs.get(index);
            properties.put("backendOutput." + index + ".backend", output.backend());
            properties.put("backendOutput." + index + ".kind", output.kind());
            properties.put("backendOutput." + index + ".resource", output.resource());
            properties.put("backendOutput." + index + ".format", output.format());
        }
    }

    private static void writeStructMetadata(TreeMap<String, String> properties, java.util.List<IrGpuStructMetadata> structs) {
        properties.put("structMetadata.count", Integer.toString(structs.size()));
        for (int structIndex = 0; structIndex < structs.size(); structIndex++) {
            IrGpuStructMetadata struct = structs.get(structIndex);
            String prefix = "structMetadata." + structIndex + ".";
            properties.put(prefix + "ownerQualifiedName", struct.ownerQualifiedName());
            properties.put(prefix + "ownerSimpleName", struct.ownerSimpleName());
            writeStringList(properties, prefix + "openClAttribute", struct.openClAttributes());
            properties.put(prefix + "field.count", Integer.toString(struct.fields().size()));
            for (int fieldIndex = 0; fieldIndex < struct.fields().size(); fieldIndex++) {
                IrGpuStructFieldMetadata field = struct.fields().get(fieldIndex);
                String fieldPrefix = prefix + "field." + fieldIndex + ".";
                properties.put(fieldPrefix + "name", field.name());
                properties.put(fieldPrefix + "javaType", field.javaType());
                writeStringList(properties, fieldPrefix + "openClAttribute", field.openClAttributes());
            }
        }
    }

    private static void writeConstants(TreeMap<String, String> properties, java.util.List<IrGpuConstantMetadata> constants) {
        properties.put("constant.count", Integer.toString(constants.size()));
        for (int index = 0; index < constants.size(); index++) {
            IrGpuConstantMetadata constant = constants.get(index);
            String prefix = "constant." + index + ".";
            properties.put(prefix + "ownerQualifiedName", constant.ownerQualifiedName());
            properties.put(prefix + "ownerSimpleName", constant.ownerSimpleName());
            properties.put(prefix + "name", constant.name());
            properties.put(prefix + "javaType", constant.javaType());
            properties.put(prefix + "sourceText", constant.sourceText());
        }
    }

    private static void writeConstantData(
            TreeMap<String, String> properties,
            java.util.List<IrGpuConstantDataMetadata> constantData
    ) {
        properties.put("constantData.count", Integer.toString(constantData.size()));
        for (int index = 0; index < constantData.size(); index++) {
            IrGpuConstantDataMetadata constant = constantData.get(index);
            String prefix = "constantData." + index + ".";
            properties.put(prefix + "ownerQualifiedName", constant.ownerQualifiedName());
            properties.put(prefix + "ownerSimpleName", constant.ownerSimpleName());
            properties.put(prefix + "name", constant.name());
            properties.put(prefix + "javaType", constant.javaType());
            properties.put(prefix + "initializerSource", constant.initializerSource());
            properties.put(prefix + "kind", constant.kind());
        }
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                default -> builder.append(ch);
            }
        }
        return builder.toString();
    }
}
