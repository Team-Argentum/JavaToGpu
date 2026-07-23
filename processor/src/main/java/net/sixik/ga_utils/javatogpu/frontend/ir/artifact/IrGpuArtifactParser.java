package net.sixik.ga_utils.javatogpu.frontend.ir.artifact;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.api.GpuDeviceClassTarget;
import net.sixik.ga_utils.javatogpu.api.GpuVendorTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionFailurePolicy;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

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
                parseIndexedValues(properties, "entry.openClAttribute"),
                parseAttributeMetadata(properties, "entry.attributeMetadata"),
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
                parseOptimizerPolicyMetadata(properties),
                parseRegenerationMetadata(properties),
                parseStructMetadata(properties),
                parseConstants(properties),
                parseConstantData(properties),
                backendOutputs,
                properties.getProperty("runtime.defaultBackend", "opencl"),
                properties.getProperty("runtime.optimizationProfile", "off"),
                parseMethodDeviceConstraints(properties),
                parseMethodFallbackVariants(properties),
                parseExtensionParticipationMetadata(properties),
                parseMethodTestVectors(properties)
        );
    }

    private static List<IrGpuMethodTestVectorMetadata> parseMethodTestVectors(Properties properties) {
        int count = parseInt(properties, "methodTestVector.count", 0);
        ArrayList<IrGpuMethodTestVectorMetadata> testVectors = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = "methodTestVector." + index + ".";
            testVectors.add(new IrGpuMethodTestVectorMetadata(
                    require(properties, prefix + "methodName"),
                    require(properties, prefix + "emittedName"),
                    properties.getProperty(prefix + "testId", ""),
                    parseIndexedValues(properties, prefix + "inputRef"),
                    parseIndexedValues(properties, prefix + "expectedOutputRef"),
                    properties.getProperty(prefix + "tolerance", ""),
                    parseIndexedValues(properties, prefix + "tag"),
                    Boolean.parseBoolean(properties.getProperty(prefix + "selectionProbe", "true")),
                    properties.getProperty(prefix + "source", "GPUTest")
            ));
        }
        return List.copyOf(testVectors);
    }

    private static List<IrGpuExtensionParticipationMetadata> parseExtensionParticipationMetadata(Properties properties) {
        int count = parseInt(properties, "extensionParticipation.count", 0);
        ArrayList<IrGpuExtensionParticipationMetadata> metadata = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = "extensionParticipation." + index + ".";
            metadata.add(new IrGpuExtensionParticipationMetadata(
                    properties.getProperty(prefix + "source", "unknown"),
                    properties.getProperty(prefix + "extensionId", "extension:unknown"),
                    properties.getProperty(prefix + "extensionVersion", "unknown"),
                    GpuExtensionPhase.valueOf(properties.getProperty(prefix + "phase", "IR_VALIDATION")),
                    GpuExtensionPermission.valueOf(properties.getProperty(prefix + "permission", "READ_ONLY")),
                    properties.getProperty(prefix + "operation", "extension invocation"),
                    GpuExtensionExecutionOutcome.valueOf(properties.getProperty(prefix + "outcome", "SKIPPED")),
                    GpuExtensionFailurePolicy.valueOf(properties.getProperty(prefix + "failurePolicy", "CONTINUE")),
                    Boolean.parseBoolean(properties.getProperty(prefix + "pipelineContinued", "true")),
                    properties.getProperty(prefix + "failureType", "none"),
                    properties.getProperty(prefix + "message", ""),
                    parseIndexedValues(properties, prefix + "diagnostic")
            ));
        }
        return List.copyOf(metadata);
    }

    private static List<IrGpuMethodFallbackVariant> parseMethodFallbackVariants(Properties properties) {
        int count = parseInt(properties, "methodFallbackVariant.count", 0);
        ArrayList<IrGpuMethodFallbackVariant> variants = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = "methodFallbackVariant." + index + ".";
            variants.add(new IrGpuMethodFallbackVariant(
                    require(properties, prefix + "methodName"),
                    require(properties, prefix + "emittedName"),
                    require(properties, prefix + "groupId"),
                    properties.getProperty(prefix + "variantId", ""),
                    parseInt(properties, prefix + "priority", 0),
                    properties.getProperty(prefix + "compatibilityNote", ""),
                    properties.getProperty(prefix + "source", "GPUFallbackVariant")
            ));
        }
        return List.copyOf(variants);
    }

    private static List<IrGpuMethodDeviceConstraint> parseMethodDeviceConstraints(Properties properties) {
        int count = parseInt(properties, "methodDeviceConstraint.count", 0);
        ArrayList<IrGpuMethodDeviceConstraint> constraints = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = "methodDeviceConstraint." + index + ".";
            constraints.add(new IrGpuMethodDeviceConstraint(
                    require(properties, prefix + "methodName"),
                    require(properties, prefix + "emittedName"),
                    parseIndexedValues(properties, prefix + "backend").stream()
                            .map(GpuBackendTarget::valueOf)
                            .toList(),
                    parseIndexedValues(properties, prefix + "vendor").stream()
                            .map(GpuVendorTarget::valueOf)
                            .toList(),
                    parseIndexedValues(properties, prefix + "deviceClass").stream()
                            .map(GpuDeviceClassTarget::valueOf)
                            .toList(),
                    parseIndexedValues(properties, prefix + "requiredFeature"),
                    properties.getProperty(prefix + "source", "default-unconstrained")
            ));
        }
        return List.copyOf(constraints);
    }

    private static List<IrGpuModuleMethod> parseHelpers(Properties properties) {
        int count = parseInt(properties, "helper.count", 0);
        ArrayList<IrGpuModuleMethod> helpers = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            helpers.add(new IrGpuModuleMethod(
                    require(properties, "helper." + index + ".name"),
                    require(properties, "helper." + index + ".emittedName"),
                    properties.getProperty("helper." + index + ".returnType", "unknown"),
                    parseMethodParameters(properties, "helper." + index + ".parameter"),
                    parseIndexedValues(properties, "helper." + index + ".openClAttribute"),
                    parseAttributeMetadata(properties, "helper." + index + ".attributeMetadata"),
                    Boolean.parseBoolean(properties.getProperty("helper." + index + ".inline", "false"))
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
                    parseTypedBody(properties, prefix + "typed."),
                    parseBodyIndex(properties, prefix),
                    parseMethodBodyDependencies(properties, prefix),
                    parseSourceLocation(properties, prefix)
            ));
        }
        return List.copyOf(methodBodies);
    }

    private static IrGpuTypedBody parseTypedBody(Properties properties, String prefix) {
        String format = properties.getProperty(prefix + "format", "none");
        int rootCount = parseInt(properties, prefix + "root.count", 0);
        ArrayList<Integer> roots = new ArrayList<>();
        for (int index = 0; index < rootCount; index++) {
            roots.add(parseInt(properties, prefix + "root." + index));
        }
        int nodeCount = parseInt(properties, prefix + "node.count", 0);
        ArrayList<IrGpuTypedNode> nodes = new ArrayList<>();
        for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
            String nodePrefix = prefix + "node." + nodeIndex + ".";
            nodes.add(new IrGpuTypedNode(
                    parseInt(properties, nodePrefix + "id"),
                    require(properties, nodePrefix + "kind"),
                    parseNamedValues(properties, nodePrefix + "attribute"),
                    parseNamedChildLists(properties, nodePrefix + "child")
            ));
        }
        return new IrGpuTypedBody(format, roots, nodes);
    }

    private static java.util.Map<String, String> parseNamedValues(Properties properties, String prefix) {
        int count = parseInt(properties, prefix + ".count", 0);
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            values.put(
                    require(properties, prefix + "." + index + ".name"),
                    properties.getProperty(prefix + "." + index + ".value", "")
            );
        }
        return java.util.Map.copyOf(values);
    }

    private static java.util.Map<String, java.util.List<Integer>> parseNamedChildLists(
            Properties properties,
            String prefix
    ) {
        int count = parseInt(properties, prefix + ".count", 0);
        java.util.LinkedHashMap<String, java.util.List<Integer>> children = new java.util.LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String childPrefix = prefix + "." + index + ".";
            String name = require(properties, childPrefix + "name");
            int nodeCount = parseInt(properties, childPrefix + "node.count", 0);
            ArrayList<Integer> nodeIds = new ArrayList<>();
            for (int nodeIndex = 0; nodeIndex < nodeCount; nodeIndex++) {
                nodeIds.add(parseInt(properties, childPrefix + "node." + nodeIndex));
            }
            children.put(name, List.copyOf(nodeIds));
        }
        return java.util.Map.copyOf(children);
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
            parameters.add(parseParameter(properties, prefix));
        }
        return List.copyOf(parameters);
    }

    private static List<IrGpuEntryParameter> parseMethodParameters(Properties properties, String prefix) {
        int count = parseInt(properties, prefix + ".count", 0);
        ArrayList<IrGpuEntryParameter> parameters = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            parameters.add(parseParameter(properties, prefix + "." + index + "."));
        }
        return List.copyOf(parameters);
    }

    private static IrGpuEntryParameter parseParameter(Properties properties, String prefix) {
        return new IrGpuEntryParameter(
                require(properties, prefix + "name"),
                require(properties, prefix + "javaType"),
                properties.getProperty(prefix + "addressSpace", "PRIVATE"),
                Boolean.parseBoolean(properties.getProperty(prefix + "constant", "false")),
                parseEntryParameterQualifiers(properties, prefix)
        );
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

    private static IrGpuOptimizerPolicyMetadata parseOptimizerPolicyMetadata(Properties properties) {
        return new IrGpuOptimizerPolicyMetadata(
                Boolean.parseBoolean(properties.getProperty("optimizerPolicy.fastMath", "false")),
                Boolean.parseBoolean(properties.getProperty("optimizerPolicy.enabled", "false")),
                properties.getProperty("optimizerPolicy.profile", "off"),
                parseIndexedValues(properties, "optimizerPolicy.enabledFamily"),
                parseIndexedValues(properties, "optimizerPolicy.disabledFamily"),
                Boolean.parseBoolean(properties.getProperty("optimizerPolicy.journal", "false")),
                Boolean.parseBoolean(properties.getProperty("optimizerPolicy.dumpArtifacts", "false")),
                Boolean.parseBoolean(properties.getProperty("optimizerPolicy.productionIntent", "false")),
                Boolean.parseBoolean(properties.getProperty("optimizerPolicy.vendorAdaptation", "false")),
                properties.getProperty("optimizerPolicy.vectorization", "auto"),
                Boolean.parseBoolean(properties.getProperty("optimizerPolicy.resourceShaping", "false")),
                properties.getProperty("optimizerPolicy.source", "default-strict")
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
                    parseIndexedValues(properties, prefix + "openClAttribute"),
                    parseAttributeMetadata(properties, prefix + "attributeMetadata")
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
                    parseIndexedValues(properties, prefix + "openClAttribute"),
                    parseAttributeMetadata(properties, prefix + "attributeMetadata")
            ));
        }
        return List.copyOf(fields);
    }

    private static List<IrGpuAttributeMetadata> parseAttributeMetadata(Properties properties, String prefix) {
        int count = parseInt(properties, prefix + ".count", 0);
        ArrayList<IrGpuAttributeMetadata> metadata = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String itemPrefix = prefix + "." + index + ".";
            metadata.add(new IrGpuAttributeMetadata(
                    require(properties, itemPrefix + "kind"),
                    properties.getProperty(itemPrefix + "value", ""),
                    properties.getProperty(itemPrefix + "source", "unknown")
            ));
        }
        return List.copyOf(metadata);
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
