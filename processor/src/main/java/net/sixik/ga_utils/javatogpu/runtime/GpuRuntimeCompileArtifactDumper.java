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
        artifacts.put("runtime-ir-handoff.properties", formatRuntimeIrHandoff(snapshot));
        artifacts.put("runtime-production-mutation-safety.properties", formatRuntimeProductionMutationSafety(snapshot));
        artifacts.put("i3-readiness-summary.properties", formatI3ReadinessSummary(snapshot));
        artifacts.put("backend-source-selection.properties", formatBackendSourceSelection(snapshot));
        artifacts.put("backend-module.properties", formatBackendModule(snapshot.backendModuleArtifact()));
        artifacts.put("backend-diagnostics.properties", formatBackendDiagnostics(snapshot));
        artifacts.put("opencl-irgpu-reconstruction-preview.properties", formatOpenClIrGpuReconstructionPreview(snapshot));
        artifacts.put("backend-source-reconstruction.properties", formatBackendSourceReconstruction(snapshot));
        artifacts.put("backend-source-promotion-gate.properties", formatBackendSourcePromotionGate(snapshot));
        artifacts.put("backend-source-switching-decision.properties", formatBackendSourceSwitchingDecision(snapshot));
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

    private static String formatRuntimeIrHandoff(GpuRuntimeCompileArtifactSnapshot snapshot) {
        GpuRuntimeIrSelection selection = snapshot.runtimeIrSelection();
        GpuProductionIrAcceptanceGate.Result productionIrGate = selection.productionIrGate();

        StringBuilder builder = new StringBuilder();
        builder.append("status=").append(selection.selectedArtifact().isPresent() ? "selected" : "missing").append('\n');
        builder.append("selectedStage=").append(selection.selectedStage()).append('\n');
        builder.append("original.present=").append(selection.originalArtifact().isPresent()).append('\n');
        builder.append("optimized.present=").append(selection.optimizedArtifact().isPresent()).append('\n');
        builder.append("selected.present=").append(selection.selectedArtifact().isPresent()).append('\n');
        builder.append("original.identity=").append(selection.originalIdentity()).append('\n');
        builder.append("optimized.identity=").append(selection.optimizedIdentity()).append('\n');
        builder.append("selected.identity=").append(selection.selectedIdentity()).append('\n');
        builder.append("optimizedDiffersFromOriginal=").append(selection.transformed()).append('\n');
        builder.append("optimizationReportPresent=").append(snapshot.optimizationReport().hasReports()).append('\n');
        builder.append("optimizationRequiresRollback=").append(snapshot.optimizationReport().requiresRollback()).append('\n');
        builder.append("fallbackDecision=").append(selection.fallbackDecision()).append('\n');
        builder.append("optimizedIrRejected=").append(selection.optimizedRejected()).append('\n');
        builder.append("productionIrGate.status=").append(productionIrGate.status()).append('\n');
        builder.append("productionIrGate.accepted=").append(productionIrGate.accepted()).append('\n');
        builder.append("productionIrGate.decisionMode=").append(productionIrGate.decisionMode()).append('\n');
        builder.append("productionIrGate.diagnostic=").append(productionIrGate.diagnostic()).append('\n');
        builder.append("backendTarget=").append(snapshot.backendModuleArtifact().backendTarget()).append('\n');
        builder.append("backendFormat=").append(snapshot.backendModuleArtifact().format()).append('\n');
        builder.append("backendResource=").append(snapshot.backendModuleArtifact().resource()).append('\n');
        builder.append("runtimeLoadMode=").append(snapshot.backendModuleArtifact().runtimeLoadMode()).append('\n');
        builder.append("diagnostic.count=1\n");
        builder.append("diagnostic.0=").append(selection.diagnostic()).append('\n');
        return builder.toString();
    }

    private static String formatRuntimeProductionMutationSafety(GpuRuntimeCompileArtifactSnapshot snapshot) {
        GpuRuntimeIrSelection selection = snapshot.runtimeIrSelection();
        GpuRuntimeProductionOptimizerGate gate = snapshot.productionOptimizerGate();
        boolean optimizedSelected = "optimized".equals(selection.selectedStage());
        boolean transformed = selection.transformed();
        boolean productionMutationEnabled = gate.accepted() && optimizedSelected && transformed;

        StringBuilder builder = new StringBuilder();
        builder.append("status=").append(productionMutationEnabled ? "enabled" : "disabled").append('\n');
        builder.append("productionMutationEnabled=").append(productionMutationEnabled).append('\n');
        builder.append("productionGateStatus=").append(gate.status()).append('\n');
        builder.append("productionProfileRequested=").append(gate.productionProfileRequested()).append('\n');
        builder.append("selectedStage=").append(selection.selectedStage()).append('\n');
        builder.append("optimizedSelected=").append(optimizedSelected).append('\n');
        builder.append("optimizedDiffersFromOriginal=").append(transformed).append('\n');
        builder.append("optimizedIrRejected=").append(selection.optimizedRejected()).append('\n');
        builder.append("fallbackDecision=").append(selection.fallbackDecision()).append('\n');
        builder.append("runtimeEquivalencePassed=").append(snapshot.runtimeEquivalenceEvidence().executed()
                && snapshot.runtimeEquivalenceEvidence().equivalent()).append('\n');
        builder.append("fallbackClean=").append(GpuRuntimeFallbackEvidence.NONE.equals(snapshot.fallbackEvidence().decision())).append('\n');
        builder.append("strategyEvidenceBacked=").append(gate.strategyEvidenceBacked()).append('\n');
        builder.append("vendorPromotionEligible=").append(gate.vendorPromotionEligible()).append('\n');
        builder.append("rollbackClean=").append(gate.rollbackClean()).append('\n');
        builder.append("diagnostic.count=1\n");
        builder.append("diagnostic.0=").append(productionMutationSafetyDiagnostic(
                productionMutationEnabled,
                gate,
                optimizedSelected,
                transformed
        )).append('\n');
        return builder.toString();
    }

    private static String productionMutationSafetyDiagnostic(
            boolean productionMutationEnabled,
            GpuRuntimeProductionOptimizerGate gate,
            boolean optimizedSelected,
            boolean transformed
    ) {
        if (productionMutationEnabled) {
            return "production runtime IR mutation is enabled because all production optimizer gates passed";
        }
        if (!gate.productionProfileRequested()) {
            return "runtime IR participates in diagnostics, but production mutation is disabled because no production profile was requested";
        }
        if (!gate.accepted()) {
            return "runtime IR participates in diagnostics, but production mutation remains fail-closed until production optimizer gates pass";
        }
        if (!optimizedSelected || !transformed) {
            return "production optimizer gates passed, but no transformed optimized IR was selected for production mutation";
        }
        return "runtime IR participates in diagnostics, but production mutation remains disabled";
    }

    private static String formatI3ReadinessSummary(GpuRuntimeCompileArtifactSnapshot snapshot) {
        GpuRuntimeIrSelection selection = snapshot.runtimeIrSelection();
        GpuBackendSourcePromotionGate sourcePromotionGate = sourcePromotionGate(snapshot);
        GpuRuntimeProductionOptimizerGate optimizerGate = snapshot.productionOptimizerGate();
        boolean runtimeEquivalencePassed = snapshot.runtimeEquivalenceEvidence().executed()
                && snapshot.runtimeEquivalenceEvidence().equivalent();
        boolean productionMutationEnabled = optimizerGate.accepted()
                && "optimized".equals(selection.selectedStage())
                && selection.transformed();

        StringBuilder builder = new StringBuilder();
        builder.append("status=").append(i3ReadinessStatus(sourcePromotionGate, productionMutationEnabled)).append('\n');
        builder.append("backendTarget=").append(snapshot.backendModuleArtifact().backendTarget()).append('\n');
        builder.append("backendFormat=").append(snapshot.backendModuleArtifact().format()).append('\n');
        builder.append("backendResource=").append(snapshot.backendModuleArtifact().resource()).append('\n');
        builder.append("selectedRuntimeIrStage=").append(selection.selectedStage()).append('\n');
        builder.append("selectedRuntimeIrIdentity=").append(selection.selectedIdentity()).append('\n');
        builder.append("optimizedIrRejected=").append(selection.optimizedRejected()).append('\n');
        builder.append("fallbackDecision=").append(selection.fallbackDecision()).append('\n');
        builder.append("sourceReconstructed=").append(sourcePromotionGate.reconstructed()).append('\n');
        builder.append("sourceReady=").append(sourcePromotionGate.ready()).append('\n');
        builder.append("sourceAvailable=").append(sourcePromotionGate.sourceAvailable()).append('\n');
        builder.append("sourceParityChecked=").append(sourcePromotionGate.sourceParityChecked()).append('\n');
        builder.append("sourceParityMatched=").append(sourcePromotionGate.sourceParityMatched()).append('\n');
        builder.append("runtimeEquivalencePassed=").append(runtimeEquivalencePassed).append('\n');
        builder.append("sourcePromotionStatus=").append(sourcePromotionGate.status()).append('\n');
        builder.append("sourcePromotionReviewReady=").append(sourcePromotionGate.reviewReady()).append('\n');
        builder.append("optimizerProductionGateStatus=").append(optimizerGate.status()).append('\n');
        builder.append("productionProfileRequested=").append(optimizerGate.productionProfileRequested()).append('\n');
        builder.append("productionMutationEnabled=").append(productionMutationEnabled).append('\n');
        java.util.List<String> blockers = i3ReadinessBlockers(sourcePromotionGate, optimizerGate, productionMutationEnabled);
        builder.append("blocker.count=").append(blockers.size()).append('\n');
        for (int index = 0; index < blockers.size(); index++) {
            builder.append("blocker.").append(index).append('=').append(blockers.get(index)).append('\n');
        }
        builder.append("diagnostic.count=1\n");
        builder.append("diagnostic.0=").append(i3ReadinessDiagnostic(sourcePromotionGate, productionMutationEnabled)).append('\n');
        return builder.toString();
    }

    private static GpuBackendSourceReconstructionResult reconstructBackendSource(GpuRuntimeCompileArtifactSnapshot snapshot) {
        IrGpuArtifact artifact = snapshot.optimizedIrGpuArtifact()
                .or(() -> snapshot.originalIrGpuArtifact())
                .orElse(null);
        return OpenClIrGpuSourceReconstructor.INSTANCE.reconstruct(
                artifact,
                snapshot.backendModuleArtifact().resource(),
                snapshot.backendModuleArtifact().source()
        );
    }

    private static GpuBackendSourcePromotionGate sourcePromotionGate(
            GpuRuntimeCompileArtifactSnapshot snapshot,
            GpuBackendSourceReconstructionResult reconstruction
    ) {
        return GpuBackendSourcePromotionGate.evaluate(
                reconstruction,
                snapshot.runtimeEquivalenceEvidence(),
                snapshot.fallbackEvidence()
        );
    }

    private static GpuBackendSourcePromotionGate sourcePromotionGate(GpuRuntimeCompileArtifactSnapshot snapshot) {
        if (snapshot.backendSourcePromotionGate().isPresent()) {
            return snapshot.backendSourcePromotionGate().orElseThrow();
        }
        return sourcePromotionGate(snapshot, reconstructBackendSource(snapshot));
    }

    private static String i3ReadinessStatus(
            GpuBackendSourcePromotionGate sourcePromotionGate,
            boolean productionMutationEnabled
    ) {
        if (productionMutationEnabled) {
            return "production-enabled";
        }
        if (sourcePromotionGate.reviewReady()) {
            return "review-ready";
        }
        return "blocked";
    }

    private static java.util.List<String> i3ReadinessBlockers(
            GpuBackendSourcePromotionGate sourcePromotionGate,
            GpuRuntimeProductionOptimizerGate optimizerGate,
            boolean productionMutationEnabled
    ) {
        java.util.ArrayList<String> blockers = new java.util.ArrayList<>();
        if (!sourcePromotionGate.reviewReady()) {
            blockers.add("backend-source-promotion-not-review-ready");
        }
        if (!optimizerGate.accepted()) {
            blockers.add("production-optimizer-gate-not-accepted");
        }
        if (!productionMutationEnabled) {
            blockers.add("production-mutation-disabled");
        }
        return java.util.List.copyOf(blockers);
    }

    private static String i3ReadinessDiagnostic(
            GpuBackendSourcePromotionGate sourcePromotionGate,
            boolean productionMutationEnabled
    ) {
        if (productionMutationEnabled) {
            return "I3 pipeline is production-enabled for this runtime compile snapshot";
        }
        if (sourcePromotionGate.reviewReady()) {
            return "I3 source pipeline is review-ready, but production mutation remains disabled until production gates are accepted";
        }
        return "I3 pipeline is active for diagnostics, but source promotion or production mutation is still blocked";
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
        return reconstructBackendSource(snapshot).toPropertiesText();
    }

    private static String formatBackendSourcePromotionGate(GpuRuntimeCompileArtifactSnapshot snapshot) {
        return sourcePromotionGate(snapshot).toPropertiesText();
    }

    private static String formatBackendSourceSwitchingDecision(GpuRuntimeCompileArtifactSnapshot snapshot) {
        if (snapshot.backendSourceSwitchingDecision().isPresent()) {
            return snapshot.backendSourceSwitchingDecision().orElseThrow().toPropertiesText();
        }
        GpuRuntimeCompileProvenance provenance = snapshot.compileProvenance();
        GpuBackendModuleArtifact module = snapshot.backendModuleArtifact();
        GpuBackendSourceReconstructionResult reconstruction = reconstructBackendSource(snapshot);
        GpuBackendSourcePromotionGate sourcePromotionGate = sourcePromotionGate(snapshot, reconstruction);
        return GpuBackendSourceSwitchingDecision.evaluate(
                provenance,
                module,
                reconstruction,
                sourcePromotionGate
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
