package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class IrGpuArtifactParser {

    private IrGpuArtifactParser() {
    }

    public static IrGpuArtifact parse(String manifest) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(manifest));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to parse IrGpu artifact manifest", exception);
        }

        IrGpuArtifactHeader header = new IrGpuArtifactHeader(
                require(properties, "format"),
                parseInt(properties, "schemaVersion"),
                require(properties, "compilerArtifact"),
                require(properties, "sourceFrontend")
        );
        IrGpuModule module = new IrGpuModule(
                require(properties, "entryMethod"),
                require(properties, "entryEmittedName"),
                parseHelpers(properties),
                parseStructs(properties),
                parseMethodBodies(properties)
        );
        List<IrGpuBackendOutput> backendOutputs = parseBackendOutputs(properties);
        if (backendOutputs.isEmpty() && properties.getProperty("derived.opencl.resource") != null) {
            backendOutputs = List.of(IrGpuBackendOutput.openClSource(properties.getProperty("derived.opencl.resource")));
        }

        return new IrGpuArtifact(
                header,
                module,
                parseEntryParameters(properties),
                parseLaunchMetadata(properties),
                parseValidationMetadata(properties),
                parseFeatureMetadata(properties),
                parseRegenerationMetadata(properties),
                parseStructMetadata(properties),
                parseConstants(properties),
                parseConstantData(properties),
                backendOutputs,
                properties.getProperty("runtime.defaultBackend", "opencl"),
                properties.getProperty("runtime.optimizationProfile", "off")
        );
    }

    private static List<IrGpuModuleMethod> parseHelpers(Properties properties) {
        int count = parseInt(properties, "helper.count", 0);
        ArrayList<IrGpuModuleMethod> helpers = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            helpers.add(new IrGpuModuleMethod(
                    require(properties, "helper." + index + ".name"),
                    require(properties, "helper." + index + ".emittedName")
            ));
        }
        return List.copyOf(helpers);
    }

    private static List<String> parseStructs(Properties properties) {
        int count = parseInt(properties, "struct.count", 0);
        ArrayList<String> structs = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            structs.add(require(properties, "struct." + index + ".name"));
        }
        return List.copyOf(structs);
    }

    private static List<IrGpuMethodBody> parseMethodBodies(Properties properties) {
        int count = parseInt(properties, "methodBody.count", 0);
        ArrayList<IrGpuMethodBody> methodBodies = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = "methodBody." + index + ".";
            methodBodies.add(new IrGpuMethodBody(
                    require(properties, prefix + "role"),
                    require(properties, prefix + "name"),
                    require(properties, prefix + "emittedName"),
                    require(properties, prefix + "format"),
                    require(properties, prefix + "body"),
                    parseBodyIndex(properties, prefix),
                    parseMethodBodyDependencies(properties, prefix),
                    parseSourceLocation(properties, prefix)
            ));
        }
        return List.copyOf(methodBodies);
    }

    private static IrGpuBodyIndex parseBodyIndex(Properties properties, String prefix) {
        return new IrGpuBodyIndex(
                parseInt(properties, prefix + "bodyIndex.statement.count", 0),
                parseIndexedValues(properties, prefix + "bodyIndex.statementKind"),
                parseIndexedValues(properties, prefix + "bodyIndex.expressionKind"),
                parseIndexedValues(properties, prefix + "bodyIndex.intrinsicCall"),
                parseIndexedValues(properties, prefix + "bodyIndex.helperCall"),
                Boolean.parseBoolean(properties.getProperty(prefix + "bodyIndex.writesMemory", "false")),
                Boolean.parseBoolean(properties.getProperty(prefix + "bodyIndex.hasControlFlow", "false"))
        );
    }

    private static IrGpuSourceLocation parseSourceLocation(Properties properties, String prefix) {
        String sourceKind = properties.getProperty(prefix + "source.kind");
        if (sourceKind == null) {
            return IrGpuSourceLocation.unknown(properties.getProperty(prefix + "name", ""));
        }
        return new IrGpuSourceLocation(
                sourceKind,
                properties.getProperty(prefix + "source.ownerQualifiedName", ""),
                properties.getProperty(prefix + "source.methodName", properties.getProperty(prefix + "name", "")),
                parseInt(properties, prefix + "source.beginLine", -1),
                parseInt(properties, prefix + "source.beginColumn", -1),
                parseInt(properties, prefix + "source.endLine", -1),
                parseInt(properties, prefix + "source.endColumn", -1)
        );
    }

    private static List<String> parseMethodBodyDependencies(Properties properties, String prefix) {
        int count = parseInt(properties, prefix + "helperDependency.count", 0);
        ArrayList<String> dependencies = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            dependencies.add(require(properties, prefix + "helperDependency." + index));
        }
        return List.copyOf(dependencies);
    }

    private static List<IrGpuEntryParameter> parseEntryParameters(Properties properties) {
        int count = parseInt(properties, "entryParameter.count", 0);
        ArrayList<IrGpuEntryParameter> parameters = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = "entryParameter." + index + ".";
            parameters.add(new IrGpuEntryParameter(
                    require(properties, prefix + "name"),
                    require(properties, prefix + "javaType"),
                    properties.getProperty(prefix + "addressSpace", "PRIVATE"),
                    Boolean.parseBoolean(properties.getProperty(prefix + "constant", "false")),
                    parseEntryParameterQualifiers(properties, prefix)
            ));
        }
        return List.copyOf(parameters);
    }

    private static List<String> parseEntryParameterQualifiers(Properties properties, String prefix) {
        int count = parseInt(properties, prefix + "openClQualifier.count", 0);
        ArrayList<String> qualifiers = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            qualifiers.add(require(properties, prefix + "openClQualifier." + index));
        }
        return List.copyOf(qualifiers);
    }

    private static IrGpuLaunchMetadata parseLaunchMetadata(Properties properties) {
        return new IrGpuLaunchMetadata(
                parseInt(properties, "launch.requiredDimensions", 1),
                properties.getProperty("launch.globalWorkSizeSource", "first-buffer-parameter"),
                Boolean.parseBoolean(properties.getProperty("launch.explicitConfigSupported", "true"))
        );
    }

    private static IrGpuValidationMetadata parseValidationMetadata(Properties properties) {
        return new IrGpuValidationMetadata(
                properties.getProperty("validation.contractVersion", "ir-validation-v1"),
                properties.getProperty("validation.safetyMode", "frontend-subset"),
                Boolean.parseBoolean(properties.getProperty("validation.optimizerEvidenceRequired", "false"))
        );
    }

    private static IrGpuFeatureMetadata parseFeatureMetadata(Properties properties) {
        return new IrGpuFeatureMetadata(
                parseIndexedValues(properties, "feature.required"),
                parseIndexedValues(properties, "feature.optional")
        );
    }

    private static IrGpuRegenerationMetadata parseRegenerationMetadata(Properties properties) {
        return new IrGpuRegenerationMetadata(
                Boolean.parseBoolean(properties.getProperty("regeneration.backendNeutralSourceReady", "false")),
                properties.getProperty("regeneration.payloadFormat", "ir-text-v1"),
                properties.getProperty("regeneration.fallbackSource", "derived-opencl-source"),
                parseIndexedValues(properties, "regeneration.blocker")
        );
    }

    private static List<String> parseIndexedValues(Properties properties, String prefix) {
        int count = parseInt(properties, prefix + ".count", 0);
        ArrayList<String> values = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            values.add(require(properties, prefix + "." + index));
        }
        return List.copyOf(values);
    }

    private static List<IrGpuBackendOutput> parseBackendOutputs(Properties properties) {
        int count = parseInt(properties, "backendOutput.count", 0);
        ArrayList<IrGpuBackendOutput> outputs = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            outputs.add(new IrGpuBackendOutput(
                    require(properties, "backendOutput." + index + ".backend"),
                    require(properties, "backendOutput." + index + ".kind"),
                    require(properties, "backendOutput." + index + ".resource"),
                    require(properties, "backendOutput." + index + ".format")
            ));
        }
        return List.copyOf(outputs);
    }

    private static List<IrGpuStructMetadata> parseStructMetadata(Properties properties) {
        int count = parseInt(properties, "structMetadata.count", 0);
        ArrayList<IrGpuStructMetadata> structs = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = "structMetadata." + index + ".";
            structs.add(new IrGpuStructMetadata(
                    require(properties, prefix + "ownerQualifiedName"),
                    properties.getProperty(prefix + "ownerSimpleName", ""),
                    parseStructFields(properties, prefix),
                    parseIndexedValues(properties, prefix + "openClAttribute")
            ));
        }
        return List.copyOf(structs);
    }

    private static List<IrGpuStructFieldMetadata> parseStructFields(Properties properties, String structPrefix) {
        int count = parseInt(properties, structPrefix + "field.count", 0);
        ArrayList<IrGpuStructFieldMetadata> fields = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = structPrefix + "field." + index + ".";
            fields.add(new IrGpuStructFieldMetadata(
                    require(properties, prefix + "name"),
                    require(properties, prefix + "javaType"),
                    parseIndexedValues(properties, prefix + "openClAttribute")
            ));
        }
        return List.copyOf(fields);
    }

    private static List<IrGpuConstantMetadata> parseConstants(Properties properties) {
        int count = parseInt(properties, "constant.count", 0);
        ArrayList<IrGpuConstantMetadata> constants = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = "constant." + index + ".";
            constants.add(new IrGpuConstantMetadata(
                    require(properties, prefix + "ownerQualifiedName"),
                    properties.getProperty(prefix + "ownerSimpleName", ""),
                    require(properties, prefix + "name"),
                    require(properties, prefix + "javaType"),
                    properties.getProperty(prefix + "sourceText", "")
            ));
        }
        return List.copyOf(constants);
    }

    private static List<IrGpuConstantDataMetadata> parseConstantData(Properties properties) {
        int count = parseInt(properties, "constantData.count", 0);
        ArrayList<IrGpuConstantDataMetadata> constantData = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = "constantData." + index + ".";
            constantData.add(new IrGpuConstantDataMetadata(
                    require(properties, prefix + "ownerQualifiedName"),
                    properties.getProperty(prefix + "ownerSimpleName", ""),
                    require(properties, prefix + "name"),
                    require(properties, prefix + "javaType"),
                    properties.getProperty(prefix + "initializerSource", ""),
                    properties.getProperty(prefix + "kind", "EMBEDDED")
            ));
        }
        return List.copyOf(constantData);
    }

    private static String require(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing IrGpu artifact property: " + key);
        }
        return value;
    }

    private static int parseInt(Properties properties, String key) {
        return parseInt(properties, key, null);
    }

    private static int parseInt(Properties properties, String key, Integer defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            if (defaultValue != null) {
                return defaultValue;
            }
            throw new IllegalArgumentException("Missing IrGpu artifact property: " + key);
        }
        return Integer.parseInt(value);
    }
}
