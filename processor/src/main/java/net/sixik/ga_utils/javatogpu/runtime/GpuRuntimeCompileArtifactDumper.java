package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactSerializer;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrGpuReconstructionPreview;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClIrGpuSourceReconstructor;

import java.util.List;
import java.util.LinkedHashMap;

/**
 * Converts runtime compile snapshots into comparable text artifacts for diagnostics and tests.
 */
public final class GpuRuntimeCompileArtifactDumper {

    private GpuRuntimeCompileArtifactDumper() {
    }

    public static GpuRuntimeCompileArtifactDump dump(GpuRuntimeCompileArtifactSnapshot snapshot) {
        if (snapshot == null) {
            return new GpuRuntimeCompileArtifactDump(
                    java.util.Map.of(),
                    java.util.List.of(),
                    GpuRuntimeCompileInvalidationStamp.from(null, GpuBackendModuleArtifact.unknown(), null)
            );
        }

        LinkedHashMap<String, String> artifacts = new LinkedHashMap<>();
        snapshot.originalIrGpuArtifact().ifPresent(artifact -> artifacts.put(
                "original.irgpu.properties",
                IrGpuArtifactSerializer.serialize(artifact)
        ));
        snapshot.optimizedIrGpuArtifact().ifPresent(artifact -> artifacts.put(
                "optimized.irgpu.properties",
                IrGpuArtifactSerializer.serialize(artifact)
        ));
        if (snapshot.originalIrGpuArtifact().isPresent() || snapshot.optimizedIrGpuArtifact().isPresent()) {
            artifacts.put("irgpu-regeneration.properties", formatRegenerationMetadata(snapshot));
        }
        artifacts.put("backend." + snapshot.backendModuleArtifact().format(), snapshot.backendModuleArtifact().source());
        artifacts.put("compile-provenance.properties", snapshot.compileProvenance().toPropertiesText());
        artifacts.put("runtime-equivalence.properties", snapshot.runtimeEquivalenceEvidence().toPropertiesText());
        artifacts.put("fallback.properties", snapshot.fallbackEvidence().toPropertiesText());
        artifacts.put("production-optimizer-gate.properties", snapshot.productionOptimizerGate().toPropertiesText());
        artifacts.put("backend-source-selection.properties", formatBackendSourceSelection(snapshot));
        artifacts.put("backend-module.properties", formatBackendModule(snapshot.backendModuleArtifact()));
        artifacts.put("backend-diagnostics.properties", formatBackendDiagnostics(snapshot));
        artifacts.put("opencl-irgpu-reconstruction-preview.properties", formatOpenClIrGpuReconstructionPreview(snapshot));
        artifacts.put("backend-source-reconstruction.properties", formatBackendSourceReconstruction(snapshot));
        artifacts.put("backend-source-map.properties", formatBackendSourceMap(snapshot));
        artifacts.put("runtime-optimizer-drift.properties", GpuRuntimeOptimizerDriftArtifact.from(snapshot).toPropertiesText());
        if (snapshot.optimizationReport().hasReports() || snapshot.productionOptimizerGate().productionProfileRequested()) {
            artifacts.put("optimizer-report.txt", snapshot.optimizationReport().toText());
        }
        if (!snapshot.compileLog().isBlank()) {
            artifacts.put("compile.log", snapshot.compileLog());
        }
        if (!snapshot.runtimeValidationEvidence().isEmpty()) {
            artifacts.put("runtime-validation.txt", String.join(System.lineSeparator(), snapshot.runtimeValidationEvidence()));
        }

        return new GpuRuntimeCompileArtifactDump(
                artifacts,
                snapshot.sourceLocations().stream()
                        .map(GpuRuntimeCompileArtifactDumper::formatSourceLocation)
                        .toList(),
                snapshot.invalidationStamp()
        );
    }

    private static String formatSourceLocation(IrGpuSourceLocation location) {
        String owner = location.ownerQualifiedName().isBlank() ? "<unknown>" : location.ownerQualifiedName();
        String method = location.methodName().isBlank() ? "<unknown>" : location.methodName();
        return location.sourceKind()
                + ":"
                + owner
                + "#"
                + method
                + ":"
                + location.beginLine()
                + ":"
                + location.beginColumn()
                + "-"
                + location.endLine()
                + ":"
                + location.endColumn();
    }

    private static String formatRegenerationMetadata(GpuRuntimeCompileArtifactSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        appendRegenerationMetadata(builder, "original", snapshot.originalIrGpuArtifact().orElse(null));
        appendRegenerationMetadata(builder, "optimized", snapshot.optimizedIrGpuArtifact().orElse(null));
        return builder.toString();
    }

    private static void appendRegenerationMetadata(StringBuilder builder, String prefix, IrGpuArtifact artifact) {
        if (artifact == null) {
            builder.append(prefix).append(".present=false\n");
            return;
        }

        IrGpuRegenerationMetadata metadata = artifact.regenerationMetadata();
        builder.append(prefix).append(".present=true\n");
        builder.append(prefix).append(".backendNeutralSourceReady=").append(metadata.backendNeutralSourceReady()).append('\n');
        builder.append(prefix).append(".payloadFormat=").append(metadata.payloadFormat()).append('\n');
        builder.append(prefix).append(".fallbackSource=").append(metadata.fallbackSource()).append('\n');
        builder.append(prefix).append(".blocker.count=").append(metadata.blockers().size()).append('\n');
        for (int index = 0; index < metadata.blockers().size(); index++) {
            builder.append(prefix).append(".blocker.").append(index).append('=').append(metadata.blockers().get(index)).append('\n');
        }
    }

    private static String formatBackendSourceSelection(GpuRuntimeCompileArtifactSnapshot snapshot) {
        IrGpuArtifact artifact = snapshot.optimizedIrGpuArtifact()
                .or(() -> snapshot.originalIrGpuArtifact())
                .orElse(null);
        GpuBackendModuleArtifact backendArtifact = snapshot.backendModuleArtifact();
        StringBuilder builder = new StringBuilder();
        builder.append("backendTarget=").append(backendArtifact.backendTarget()).append('\n');
        builder.append("backendFormat=").append(backendArtifact.format()).append('\n');
        builder.append("backendResource=").append(backendArtifact.resource()).append('\n');
        appendBackendSourceSelectionSummary(builder, artifact, backendArtifact);
        return builder.toString();
    }

    private static String formatBackendModule(GpuBackendModuleArtifact artifact) {
        GpuBackendModuleArtifact module = artifact == null ? GpuBackendModuleArtifact.unknown() : artifact;
        StringBuilder builder = new StringBuilder();
        builder.append("backendTarget=").append(module.backendTarget()).append('\n');
        builder.append("kind=").append(module.kind()).append('\n');
        builder.append("format=").append(module.format()).append('\n');
        builder.append("resource=").append(module.resource()).append('\n');
        builder.append("artifactVersion=").append(module.artifactVersion()).append('\n');
        builder.append("lowererVersion=").append(module.lowererVersion()).append('\n');
        builder.append("sourceOrigin=").append(module.sourceOrigin()).append('\n');
        builder.append("sourceAvailable=").append(module.sourceAvailable()).append('\n');
        builder.append("binaryAvailable=").append(module.binaryAvailable()).append('\n');
        builder.append("compileLogResource=").append(module.compileLogResource()).append('\n');
        builder.append("sourceMapResource=").append(module.sourceMapResource()).append('\n');
        builder.append("runtimeLoadMode=").append(module.runtimeLoadMode()).append('\n');
        return builder.toString();
    }

    private static String formatBackendDiagnostics(GpuRuntimeCompileArtifactSnapshot snapshot) {
        IrGpuArtifact artifact = snapshot.optimizedIrGpuArtifact()
                .or(() -> snapshot.originalIrGpuArtifact())
                .orElse(null);
        GpuBackendModuleArtifact module = snapshot.backendModuleArtifact();
        List<IrGpuMethodBody> methodBodies = collectMethodBodies(snapshot);
        StringBuilder builder = new StringBuilder();
        builder.append("backendTarget=").append(module.backendTarget()).append('\n');
        builder.append("backendFormat=").append(module.format()).append('\n');
        builder.append("backendResource=").append(module.resource()).append('\n');
        builder.append("sourceOrigin=").append(module.sourceOrigin()).append('\n');
        builder.append("runtimeLoadMode=").append(module.runtimeLoadMode()).append('\n');
        builder.append("sourceAvailable=").append(module.sourceAvailable()).append('\n');
        builder.append("binaryAvailable=").append(module.binaryAvailable()).append('\n');
        builder.append("compileLogAvailable=").append(!module.compileLogResource().isBlank()).append('\n');
        builder.append("sourceMapAvailable=").append(!module.sourceMapResource().isBlank()).append('\n');
        builder.append("compileLogResource=").append(module.compileLogResource()).append('\n');
        builder.append("sourceMapResource=").append(module.sourceMapResource()).append('\n');
        builder.append("sourceLocation.count=").append(snapshot.sourceLocations().size()).append('\n');
        builder.append("methodBody.count=").append(methodBodies.size()).append('\n');
        appendBackendSourceSelectionSummary(builder, artifact, module);
        return builder.toString();
    }

    private static String formatOpenClIrGpuReconstructionPreview(GpuRuntimeCompileArtifactSnapshot snapshot) {
        IrGpuArtifact artifact = snapshot.optimizedIrGpuArtifact()
                .or(() -> snapshot.originalIrGpuArtifact())
                .orElse(null);
        return OpenClIrGpuReconstructionPreview.inspect(
                artifact,
                snapshot.backendModuleArtifact().resource()
        ).toPropertiesText();
    }

    private static String formatBackendSourceReconstruction(GpuRuntimeCompileArtifactSnapshot snapshot) {
        IrGpuArtifact artifact = snapshot.optimizedIrGpuArtifact()
                .or(() -> snapshot.originalIrGpuArtifact())
                .orElse(null);
        return OpenClIrGpuSourceReconstructor.INSTANCE.reconstruct(
                artifact,
                snapshot.backendModuleArtifact().resource()
        ).toPropertiesText();
    }

    private static void appendBackendSourceSelectionSummary(
            StringBuilder builder,
            IrGpuArtifact artifact,
            GpuBackendModuleArtifact module
    ) {
        builder.append(GpuBackendIrGpuSourceSummary.from(artifact, module).toPropertiesText());
    }

    private static String formatBackendSourceMap(GpuRuntimeCompileArtifactSnapshot snapshot) {
        GpuBackendModuleArtifact module = snapshot.backendModuleArtifact();
        StringBuilder builder = new StringBuilder();
        builder.append("backendTarget=").append(module.backendTarget()).append('\n');
        builder.append("backendFormat=").append(module.format()).append('\n');
        builder.append("backendResource=").append(module.resource()).append('\n');
        builder.append("sourceMapResource=").append(module.sourceMapResource()).append('\n');
        builder.append("sourceLocation.count=").append(snapshot.sourceLocations().size()).append('\n');
        for (int index = 0; index < snapshot.sourceLocations().size(); index++) {
            IrGpuSourceLocation location = snapshot.sourceLocations().get(index);
            String prefix = "sourceLocation." + index + ".";
            builder.append(prefix).append("sourceKind=").append(location.sourceKind()).append('\n');
            builder.append(prefix).append("ownerQualifiedName=").append(location.ownerQualifiedName()).append('\n');
            builder.append(prefix).append("methodName=").append(location.methodName()).append('\n');
            builder.append(prefix).append("beginLine=").append(location.beginLine()).append('\n');
            builder.append(prefix).append("beginColumn=").append(location.beginColumn()).append('\n');
            builder.append(prefix).append("endLine=").append(location.endLine()).append('\n');
            builder.append(prefix).append("endColumn=").append(location.endColumn()).append('\n');
        }
        List<IrGpuMethodBody> methodBodies = collectMethodBodies(snapshot);
        builder.append("methodBody.count=").append(methodBodies.size()).append('\n');
        for (int index = 0; index < methodBodies.size(); index++) {
            IrGpuMethodBody methodBody = methodBodies.get(index);
            IrGpuSourceLocation location = methodBody.sourceLocation();
            String prefix = "methodBody." + index + ".";
            builder.append(prefix).append("role=").append(methodBody.role()).append('\n');
            builder.append(prefix).append("name=").append(methodBody.name()).append('\n');
            builder.append(prefix).append("emittedName=").append(methodBody.emittedName()).append('\n');
            builder.append(prefix).append("format=").append(methodBody.format()).append('\n');
            builder.append(prefix).append("sourceKind=").append(location.sourceKind()).append('\n');
            builder.append(prefix).append("ownerQualifiedName=").append(location.ownerQualifiedName()).append('\n');
            builder.append(prefix).append("sourceMethodName=").append(location.methodName()).append('\n');
            builder.append(prefix).append("beginLine=").append(location.beginLine()).append('\n');
            builder.append(prefix).append("beginColumn=").append(location.beginColumn()).append('\n');
            builder.append(prefix).append("endLine=").append(location.endLine()).append('\n');
            builder.append(prefix).append("endColumn=").append(location.endColumn()).append('\n');
        }
        return builder.toString();
    }

    private static List<IrGpuMethodBody> collectMethodBodies(GpuRuntimeCompileArtifactSnapshot snapshot) {
        LinkedHashMap<String, IrGpuMethodBody> methodBodies = new LinkedHashMap<>();
        addMethodBodies(methodBodies, snapshot.originalIrGpuArtifact().orElse(null));
        addMethodBodies(methodBodies, snapshot.optimizedIrGpuArtifact().orElse(null));
        return List.copyOf(methodBodies.values());
    }

    private static void addMethodBodies(LinkedHashMap<String, IrGpuMethodBody> methodBodies, IrGpuArtifact artifact) {
        if (artifact == null) {
            return;
        }
        artifact.module().methodBodies().forEach(methodBody -> methodBodies.putIfAbsent(
                methodBody.role() + "|" + methodBody.name() + "|" + methodBody.emittedName(),
                methodBody
        ));
    }
}
