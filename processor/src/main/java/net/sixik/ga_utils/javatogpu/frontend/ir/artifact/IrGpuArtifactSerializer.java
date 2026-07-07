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
        properties.put("helper.count", Integer.toString(module.helperMethods().size()));
        for (int index = 0; index < module.helperMethods().size(); index++) {
            IrGpuModuleMethod helper = module.helperMethods().get(index);
            properties.put("helper." + index + ".name", helper.name());
            properties.put("helper." + index + ".emittedName", helper.emittedName());
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
