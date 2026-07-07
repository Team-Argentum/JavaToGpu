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
                    parseMethodBodyDependencies(properties, prefix),
                    parseSourceLocation(properties, prefix)
            ));
        }
        return List.copyOf(methodBodies);
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
