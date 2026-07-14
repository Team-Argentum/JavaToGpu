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
import java.util.Map;

/**
 * Converts runtime compile snapshots into comparable text artifacts for diagnostics and tests.
 */
public final class GpuRuntimeCompileArtifactDumper {

    public static final String RUNTIME_DEVICE_SELECTION_ARTIFACT = "runtime-device-selection.properties";
    public static final String BACKEND_COMPILER_FEEDBACK_ARTIFACT = "backend-compiler-feedback.properties";
    public static final String RUNTIME_EXTENSION_PARTICIPATION_ARTIFACT =
            "runtime-extension-participation.properties";
    public static final String RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT =
            "runtime-ir-optimizer-evidence.properties";
    public static final String RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD_DIRECTORY =
            "runtime-optimizer-family-equivalence-payload";

    private GpuRuntimeCompileArtifactDumper() {
    }

    public static GpuRuntimeCompileArtifactDump dump(GpuRuntimeCompileArtifactSnapshot snapshot) {
        return dump(snapshot, GpuBackendCompilerFeedbackRegistry.loadWithBuiltIns());
    }

    public static GpuRuntimeCompileArtifactDump dump(
            GpuRuntimeCompileArtifactSnapshot snapshot,
            GpuBackendCompilerFeedbackRegistry compilerFeedbackRegistry
    ) {
        if (snapshot == null) {
            return new GpuRuntimeCompileArtifactDump(
                    java.util.Map.of(),
                    java.util.List.of(),
                    GpuRuntimeCompileInvalidationStamp.from(null, GpuBackendModuleArtifact.unknown(), null)
            );
        }
        java.util.Objects.requireNonNull(compilerFeedbackRegistry, "compilerFeedbackRegistry");

        LinkedHashMap<String, String> artifacts = new LinkedHashMap<>();
        LinkedHashMap<String, GpuRuntimeBinaryArtifact> binaryArtifacts = new LinkedHashMap<>();
        for (GpuRuntimeBinaryArtifact artifact : snapshot.binaryArtifacts()) {
            if (artifact != null && artifact.size() > 0) {
                binaryArtifacts.putIfAbsent(artifact.name(), artifact);
            }
        }
        GpuBackendCompilerFeedbackReport compilerFeedbackReport = compilerFeedbackRegistry.inspect(snapshot);
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
        snapshot.originalBackendModuleArtifact().ifPresent(artifact -> putBackendStageArtifact(
                artifacts,
                "original",
                artifact
        ));
        snapshot.optimizedBackendModuleArtifact().ifPresent(artifact -> putBackendStageArtifact(
                artifacts,
                "optimized",
                artifact
        ));
        artifacts.put("backend." + snapshot.backendModuleArtifact().format(), snapshot.backendModuleArtifact().source());
        artifacts.put("compile-provenance.properties", snapshot.compileProvenance().toPropertiesText());
        snapshot.deviceSelection().ifPresent(selection -> artifacts.put(
                RUNTIME_DEVICE_SELECTION_ARTIFACT,
                formatProperties(selection.artifactFields("deviceSelection"))
        ));
        artifacts.put("runtime-equivalence.properties", snapshot.runtimeEquivalenceEvidence().toPropertiesText());
        artifacts.put("fallback.properties", snapshot.fallbackEvidence().toPropertiesText());
        artifacts.put("production-optimizer-gate.properties", snapshot.productionOptimizerGate().toPropertiesText());
        artifacts.put(GpuPromotionArtifactRegistry.RUNTIME_IR_HANDOFF, formatRuntimeIrHandoff(snapshot));
        artifacts.put(GpuPromotionArtifactRegistry.RUNTIME_PRODUCTION_MUTATION_SAFETY, formatRuntimeProductionMutationSafety(snapshot));
        artifacts.put(GpuPromotionArtifactRegistry.I3_READINESS_SUMMARY, formatI3ReadinessSummary(snapshot));
        artifacts.put("backend-source-selection.properties", formatBackendSourceSelection(snapshot));
        artifacts.put("backend-module.properties", formatBackendModule(snapshot.backendModuleArtifact()));
        artifacts.put("backend-diagnostics.properties", formatBackendDiagnostics(snapshot));
        artifacts.put(BACKEND_COMPILER_FEEDBACK_ARTIFACT, compilerFeedbackReport.toPropertiesText());
        artifacts.put(
                RUNTIME_EXTENSION_PARTICIPATION_ARTIFACT,
                GpuRuntimeExtensionParticipationArtifact.from(snapshot, compilerFeedbackReport).toPropertiesText()
        );
        artifacts.put("opencl-irgpu-reconstruction-preview.properties", formatOpenClIrGpuReconstructionPreview(snapshot));
        artifacts.put("backend-source-reconstruction.properties", formatBackendSourceReconstruction(snapshot));
        artifacts.put(GpuPromotionArtifactRegistry.BACKEND_SOURCE_PROMOTION_GATE, formatBackendSourcePromotionGate(snapshot));
        artifacts.put(GpuPromotionArtifactRegistry.BACKEND_SOURCE_SWITCHING_DECISION, formatBackendSourceSwitchingDecision(snapshot));
        artifacts.put("backend-source-map.properties", formatBackendSourceMap(snapshot));
        artifacts.put(GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_DRIFT, GpuRuntimeOptimizerDriftArtifact.from(snapshot).toPropertiesText());
        OptimizerFamilyEquivalenceArtifacts optimizerFamilyEquivalenceArtifacts =
                optimizerFamilyEquivalenceArtifacts(snapshot);
        artifacts.put(
                GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD,
                optimizerFamilyEquivalenceArtifacts.index()
        );
        artifacts.putAll(optimizerFamilyEquivalenceArtifacts.files());
        if (snapshot.optimizationReport().hasReports() || snapshot.productionOptimizerGate().productionProfileRequested()) {
            artifacts.put("optimizer-report.txt", snapshot.optimizationReport().toText());
        }
        String irOptimizerEvidence = formatRuntimeIrOptimizerEvidence(snapshot.optimizationReport());
        if (!irOptimizerEvidence.isBlank()) {
            artifacts.put(RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT, irOptimizerEvidence);
        }
        String runtimeIrAnalysis = formatRuntimeIrAnalysis(snapshot.optimizationReport(), compilerFeedbackReport);
        if (!runtimeIrAnalysis.isBlank()) {
            artifacts.put("runtime-ir-analysis.properties", runtimeIrAnalysis);
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
                snapshot.invalidationStamp(),
                binaryArtifacts
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

    private static void putBackendStageArtifact(
            LinkedHashMap<String, String> artifacts,
            String stage,
            GpuBackendModuleArtifact artifact
    ) {
        if (artifact == null || !artifact.sourceAvailable()) {
            return;
        }
        artifacts.put(stage + ".backend." + artifact.format(), artifact.source());
    }

    private static String formatProperties(Map<String, String> fields) {
        StringBuilder builder = new StringBuilder();
        fields.forEach((key, value) -> builder
                .append(key)
                .append('=')
                .append(safePropertyValue(value))
                .append('\n'));
        return builder.toString();
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

    private static OptimizerFamilyEquivalenceArtifacts optimizerFamilyEquivalenceArtifacts(
            GpuRuntimeCompileArtifactSnapshot snapshot
    ) {
        LinkedHashMap<String, OptimizerFamilyPayload> families = new LinkedHashMap<>();
        for (GpuRuntimeIrOptimizationPassReport passReport : snapshot.optimizationReport().passReports()) {
            if (passReport.analysisOnly()) {
                continue;
            }
            String familyName = optimizerFamilyName(passReport);
            OptimizerFamilyPayload existing = families.getOrDefault(familyName, OptimizerFamilyPayload.empty(familyName));
            families.put(familyName, existing.add(passReport));
        }

        boolean runtimeEquivalencePassed = snapshot.runtimeEquivalenceEvidence().executed()
                && snapshot.runtimeEquivalenceEvidence().equivalent();
        OptimizerFamilyBindingDecision bindingDecision = optimizerFamilyBindingDecision(
                families,
                snapshot.runtimeEquivalenceEvidence(),
                runtimeEquivalencePassed
        );
        int completeFamilyCount = 0;
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        StringBuilder builder = new StringBuilder();
        builder.append("status=").append(families.isEmpty() ? "not-recorded" : "recorded").append('\n');
        builder.append("runtimeEquivalence.status=").append(snapshot.runtimeEquivalenceEvidence().status()).append('\n');
        builder.append("runtimeEquivalence.executed=").append(snapshot.runtimeEquivalenceEvidence().executed()).append('\n');
        builder.append("runtimeEquivalence.equivalent=").append(snapshot.runtimeEquivalenceEvidence().equivalent()).append('\n');
        builder.append("runtimeEquivalence.passed=").append(runtimeEquivalencePassed).append('\n');
        builder.append("runtimeEquivalence.comparisonCase.count=")
                .append(snapshot.runtimeEquivalenceEvidence().comparisonCases().size()).append('\n');
        builder.append("runtimeEquivalence.comparisonMode.summary=")
                .append(runtimeEquivalenceComparisonModeSummary(snapshot.runtimeEquivalenceEvidence())).append('\n');
        builder.append("familyBinding.status=").append(bindingDecision.status()).append('\n');
        builder.append("familyBinding.eligible=").append(bindingDecision.eligible()).append('\n');
        builder.append("familyBinding.family=").append(bindingDecision.family()).append('\n');
        builder.append("familyBinding.firstBlocker=").append(bindingDecision.firstBlocker()).append('\n');
        builder.append("family.count=").append(families.size()).append('\n');
        int index = 0;
        for (OptimizerFamilyPayload family : families.values()) {
            boolean complete = family.complete(runtimeEquivalencePassed);
            if (complete) {
                completeFamilyCount++;
            }
            String prefix = "family." + index + ".";
            builder.append(prefix).append("name=").append(family.name()).append('\n');
            builder.append(prefix).append("pass.count=").append(family.passCount()).append('\n');
            builder.append(prefix).append("runtimePayload.present=").append(family.runtimePayloadPresent()).append('\n');
            builder.append(prefix).append("cpuReference.present=").append(family.cpuReferencePresent()).append('\n');
            builder.append(prefix).append("preOptimizationOutput.present=").append(family.preOptimizationOutputPresent()).append('\n');
            builder.append(prefix).append("postOptimizationOutput.present=").append(family.postOptimizationOutputPresent()).append('\n');
            builder.append(prefix).append("tolerance.present=").append(family.tolerancePresent()).append('\n');
            builder.append(prefix).append("failureFixture.present=").append(family.failureFixturePresent()).append('\n');
            builder.append(prefix).append("complete=").append(complete).append('\n');
            builder.append(prefix).append("firstMissing=").append(family.firstMissing(runtimeEquivalencePassed)).append('\n');
            builder.append(prefix).append("proof.source.summary=").append(family.proofSourceSummary()).append('\n');
            builder.append(prefix).append("proof.verdict.summary=").append(family.proofVerdictSummary()).append('\n');
            builder.append(prefix).append("payload.resource.summary=").append(family.payloadResourceSummary()).append('\n');
            for (int passIndex = 0; passIndex < family.passTraces().size(); passIndex++) {
                OptimizerFamilyPassTrace passTrace = family.passTraces().get(passIndex);
                String passPrefix = prefix + "pass." + passIndex + ".";
                OptimizerFamilyPayloadPaths paths = materializeOptimizerFamilyPassPayload(
                        files,
                        index,
                        family.name(),
                        passIndex,
                        passTrace
                );
                builder.append(passPrefix).append("optimizerVersion=").append(passTrace.optimizerVersion()).append('\n');
                builder.append(passPrefix).append("outcome=").append(passTrace.outcome()).append('\n');
                builder.append(passPrefix).append("proofStatus=").append(passTrace.proofStatus()).append('\n');
                builder.append(passPrefix).append("proof.source=").append(passTrace.proofSource()).append('\n');
                builder.append(passPrefix).append("proof.verdict=").append(passTrace.proofVerdict()).append('\n');
                builder.append(passPrefix).append("payload.resource=").append(passTrace.payloadResource()).append('\n');
                builder.append(passPrefix).append("cpuReference.resource=").append(passTrace.cpuReferenceResource()).append('\n');
                builder.append(passPrefix).append("preOptimizationOutput.resource=").append(passTrace.preOptimizationOutputResource()).append('\n');
                builder.append(passPrefix).append("postOptimizationOutput.resource=").append(passTrace.postOptimizationOutputResource()).append('\n');
                builder.append(passPrefix).append("tolerance.resource=").append(passTrace.toleranceResource()).append('\n');
                builder.append(passPrefix).append("failureFixture.resource=").append(passTrace.failureFixtureResource()).append('\n');
                builder.append(passPrefix).append("cpuReference.payload=").append(passTrace.cpuReferencePayload()).append('\n');
                builder.append(passPrefix).append("preOptimizationOutput.payload=").append(passTrace.preOptimizationOutputPayload()).append('\n');
                builder.append(passPrefix).append("postOptimizationOutput.payload=").append(passTrace.postOptimizationOutputPayload()).append('\n');
                builder.append(passPrefix).append("tolerance.payload=").append(passTrace.tolerancePayload()).append('\n');
                builder.append(passPrefix).append("failureFixture.payload=").append(passTrace.failureFixturePayload()).append('\n');
                builder.append(passPrefix).append("firstDiagnostic=").append(passTrace.firstDiagnostic()).append('\n');
                builder.append(passPrefix).append("durable.directory=").append(paths.directory()).append('\n');
                builder.append(passPrefix).append("durable.manifest.path=").append(paths.manifest()).append('\n');
                builder.append(passPrefix).append("durable.cpuReference.path=").append(paths.cpuReference()).append('\n');
                builder.append(passPrefix).append("durable.preOptimizationOutput.path=")
                        .append(paths.preOptimizationOutput()).append('\n');
                builder.append(passPrefix).append("durable.postOptimizationOutput.path=")
                        .append(paths.postOptimizationOutput()).append('\n');
                builder.append(passPrefix).append("durable.tolerance.path=").append(paths.tolerance()).append('\n');
                builder.append(passPrefix).append("durable.failureFixture.path=")
                        .append(paths.failureFixture()).append('\n');
                builder.append(passPrefix).append("durable.diagnostics.path=").append(paths.diagnostics()).append('\n');
            }
            index++;
        }
        builder.append("family.complete.count=").append(completeFamilyCount).append('\n');
        builder.append("family.complete.all=").append(!families.isEmpty() && completeFamilyCount == families.size()).append('\n');
        return new OptimizerFamilyEquivalenceArtifacts(
                builder.toString(),
                java.util.Collections.unmodifiableMap(files)
        );
    }

    private static OptimizerFamilyBindingDecision optimizerFamilyBindingDecision(
            Map<String, OptimizerFamilyPayload> families,
            GpuRuntimeEquivalenceEvidence evidence,
            boolean runtimeEquivalencePassed
    ) {
        if (evidence.comparisonCases().isEmpty()) {
            return OptimizerFamilyBindingDecision.blocked("runtime-comparison-cases-missing");
        }
        if (families.isEmpty()) {
            return OptimizerFamilyBindingDecision.blocked("optimizer-family-missing");
        }
        if (families.size() != 1) {
            return OptimizerFamilyBindingDecision.blocked("optimizer-family-count-not-one");
        }
        String family = families.keySet().iterator().next();
        if (!runtimeEquivalencePassed) {
            return OptimizerFamilyBindingDecision.blocked(family, "runtime-equivalence-not-passed");
        }
        String requiredModePrefix = "optimizer-family:" + family + ":";
        boolean familySpecific = evidence.comparisonCases().stream()
                .allMatch(caseEvidence -> caseEvidence.comparisonMode().startsWith(requiredModePrefix));
        if (!familySpecific) {
            return OptimizerFamilyBindingDecision.blocked(family, "comparison-mode-not-family-specific");
        }
        return new OptimizerFamilyBindingDecision("bound", true, family, "none");
    }

    private static String runtimeEquivalenceComparisonModeSummary(GpuRuntimeEquivalenceEvidence evidence) {
        if (evidence.comparisonCases().isEmpty()) {
            return "none";
        }
        return evidence.comparisonCases().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        GpuRuntimeEquivalenceCaseEvidence::comparisonMode,
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()
                ))
                .entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private static OptimizerFamilyPayloadPaths materializeOptimizerFamilyPassPayload(
            Map<String, String> files,
            int familyIndex,
            String familyName,
            int passIndex,
            OptimizerFamilyPassTrace passTrace
    ) {
        String directory = RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD_DIRECTORY
                + "/family-"
                + familyIndex
                + "-"
                + artifactPathSegment(familyName)
                + "/pass-"
                + passIndex;
        OptimizerFamilyPayloadPaths paths = new OptimizerFamilyPayloadPaths(
                directory,
                directory + "/manifest.properties",
                directory + "/cpu-reference.properties",
                directory + "/pre-optimization-output.properties",
                directory + "/post-optimization-output.properties",
                directory + "/tolerance.properties",
                directory + "/failure-fixture.properties",
                directory + "/diagnostics.properties"
        );
        files.put(paths.manifest(), formatOptimizerFamilyPassManifest(familyName, passIndex, passTrace, paths));
        files.put(paths.cpuReference(), formatOptimizerFamilyPayloadComponent(
                familyName,
                passIndex,
                "cpu-reference",
                passTrace.cpuReferenceResource(),
                passTrace.cpuReferencePayload(),
                payloadComponentEvidenceFields(passTrace.evidenceFields(), "cpu-reference")
        ));
        files.put(paths.preOptimizationOutput(), formatOptimizerFamilyPayloadComponent(
                familyName,
                passIndex,
                "pre-optimization-output",
                passTrace.preOptimizationOutputResource(),
                passTrace.preOptimizationOutputPayload(),
                payloadComponentEvidenceFields(passTrace.evidenceFields(), "pre-optimization-output")
        ));
        files.put(paths.postOptimizationOutput(), formatOptimizerFamilyPayloadComponent(
                familyName,
                passIndex,
                "post-optimization-output",
                passTrace.postOptimizationOutputResource(),
                passTrace.postOptimizationOutputPayload(),
                payloadComponentEvidenceFields(passTrace.evidenceFields(), "post-optimization-output")
        ));
        files.put(paths.tolerance(), formatOptimizerFamilyPayloadComponent(
                familyName,
                passIndex,
                "tolerance",
                passTrace.toleranceResource(),
                passTrace.tolerancePayload(),
                payloadComponentEvidenceFields(passTrace.evidenceFields(), "tolerance")
        ));
        files.put(paths.failureFixture(), formatOptimizerFamilyPayloadComponent(
                familyName,
                passIndex,
                "failure-fixture",
                passTrace.failureFixtureResource(),
                passTrace.failureFixturePayload(),
                payloadComponentEvidenceFields(passTrace.evidenceFields(), "failure-fixture")
        ));
        files.put(paths.diagnostics(), formatOptimizerFamilyPayloadDiagnostics(familyName, passIndex, passTrace));
        return paths;
    }

    private static String formatOptimizerFamilyPassManifest(
            String familyName,
            int passIndex,
            OptimizerFamilyPassTrace passTrace,
            OptimizerFamilyPayloadPaths paths
    ) {
        return "formatVersion=1\n"
                + "status=recorded\n"
                + "scope=runtime-optimizer-family-equivalence-payload\n"
                + "optimizerFamily=" + safePropertyValue(familyName) + "\n"
                + "pass.index=" + passIndex + "\n"
                + "optimizerVersion=" + passTrace.optimizerVersion() + "\n"
                + "outcome=" + passTrace.outcome() + "\n"
                + "proofStatus=" + passTrace.proofStatus() + "\n"
                + "proof.source=" + passTrace.proofSource() + "\n"
                + "proof.verdict=" + passTrace.proofVerdict() + "\n"
                + "payload.resource=" + passTrace.payloadResource() + "\n"
                + "component.cpuReference.path=" + paths.cpuReference() + "\n"
                + "component.preOptimizationOutput.path=" + paths.preOptimizationOutput() + "\n"
                + "component.postOptimizationOutput.path=" + paths.postOptimizationOutput() + "\n"
                + "component.tolerance.path=" + paths.tolerance() + "\n"
                + "component.failureFixture.path=" + paths.failureFixture() + "\n"
                + "diagnostics.path=" + paths.diagnostics() + "\n";
    }

    private static String formatOptimizerFamilyPayloadComponent(
            String familyName,
            int passIndex,
            String component,
            String sourceResource,
            String payload,
            Map<String, String> structuredFields
    ) {
        boolean payloadPresent = payload != null
                && !payload.isBlank()
                && !"not-recorded".equals(payload);
        StringBuilder builder = new StringBuilder()
                .append("formatVersion=1\n")
                .append("status=").append(payloadPresent ? "recorded" : "not-recorded").append('\n')
                .append("scope=runtime-optimizer-family-equivalence-payload-component\n")
                .append("optimizerFamily=").append(safePropertyValue(familyName)).append('\n')
                .append("pass.index=").append(passIndex).append('\n')
                .append("component=").append(component).append('\n')
                .append("source.resource=").append(safePropertyValue(sourceResource)).append('\n')
                .append("payload.present=").append(payloadPresent).append('\n')
                .append("payload=").append(safePropertyValue(payload)).append('\n')
                .append("structured.field.count=").append(structuredFields.size()).append('\n');
        int fieldIndex = 0;
        for (Map.Entry<String, String> entry : structuredFields.entrySet()) {
            builder.append("structured.field.").append(fieldIndex).append(".name=")
                    .append(safePropertyValue(entry.getKey())).append('\n');
            builder.append("structured.field.").append(fieldIndex).append(".value=")
                    .append(safePropertyValue(entry.getValue())).append('\n');
            fieldIndex++;
        }
        return builder.toString();
    }

    private static String formatOptimizerFamilyPayloadDiagnostics(
            String familyName,
            int passIndex,
            OptimizerFamilyPassTrace passTrace
    ) {
        StringBuilder builder = new StringBuilder()
                .append("formatVersion=1\n")
                .append("status=recorded\n")
                .append("scope=runtime-optimizer-family-equivalence-payload-diagnostics\n")
                .append("optimizerFamily=").append(safePropertyValue(familyName)).append('\n')
                .append("pass.index=").append(passIndex).append('\n')
                .append("firstDiagnostic=").append(passTrace.firstDiagnostic()).append('\n')
                .append("field.count=").append(passTrace.evidenceFields().size()).append('\n');
        int fieldIndex = 0;
        for (Map.Entry<String, String> entry : passTrace.evidenceFields().entrySet()) {
            builder.append("field.").append(fieldIndex).append(".name=")
                    .append(safePropertyValue(entry.getKey())).append('\n');
            builder.append("field.").append(fieldIndex).append(".value=")
                    .append(safePropertyValue(entry.getValue())).append('\n');
            fieldIndex++;
        }
        return builder.toString();
    }

    private static Map<String, String> payloadComponentEvidenceFields(
            Map<String, String> fields,
            String component
    ) {
        LinkedHashMap<String, String> evidence = new LinkedHashMap<>();
        fields.entrySet().stream()
                .filter(entry -> isPayloadComponentEvidenceField(entry.getKey(), component))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> evidence.put(entry.getKey(), entry.getValue()));
        return java.util.Collections.unmodifiableMap(evidence);
    }

    private static boolean isPayloadComponentEvidenceField(String key, String component) {
        String normalizedKey = key == null ? "" : key.toLowerCase(java.util.Locale.ROOT);
        if ("cpu-reference".equals(component) && normalizedKey.endsWith("payload.referencemode")) {
            return true;
        }
        String relativeKey = payloadCaseRelativeKey(key);
        if (relativeKey == null) {
            return false;
        }
        String normalizedRelativeKey = relativeKey.toLowerCase(java.util.Locale.ROOT);
        boolean caseIdentity = normalizedRelativeKey.equals("count")
                || normalizedRelativeKey.matches("\\d+\\.(name|successful)");
        boolean outputIdentity = normalizedRelativeKey.matches("\\d+\\.output\\.count")
                || normalizedRelativeKey.matches("\\d+\\.output\\.\\d+\\.name");
        return switch (component) {
            case "cpu-reference" -> caseIdentity
                    || outputIdentity
                    || normalizedRelativeKey.contains(".input.")
                    || normalizedRelativeKey.endsWith(".cpureference");
            case "pre-optimization-output" -> caseIdentity
                    || outputIdentity
                    || normalizedRelativeKey.endsWith(".preoptimization");
            case "post-optimization-output" -> caseIdentity
                    || outputIdentity
                    || normalizedRelativeKey.endsWith(".postoptimization")
                    || normalizedRelativeKey.endsWith(".equivalent");
            case "tolerance" -> caseIdentity
                    || outputIdentity
                    || normalizedRelativeKey.endsWith(".tolerance");
            case "failure-fixture" -> caseIdentity
                    || normalizedRelativeKey.contains(".failurefixture.");
            default -> false;
        };
    }

    private static String payloadCaseRelativeKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        String marker = "payload.case.";
        int markerIndex = normalized.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        return key.substring(markerIndex + marker.length());
    }

    private static String artifactPathSegment(String value) {
        String normalized = value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "-");
        normalized = normalized.replaceAll("-+", "-");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private static String formatRuntimeIrAnalysis(
            GpuRuntimeIrOptimizationReport report,
            GpuBackendCompilerFeedbackReport compilerFeedbackReport
    ) {
        List<GpuRuntimeIrOptimizationPassReport> analysisReports = report.passReports().stream()
                .filter(GpuRuntimeIrOptimizationPassReport::analysisOnly)
                .toList();
        if (analysisReports.isEmpty() && !compilerFeedbackReport.available()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("status=recorded\n");
        builder.append("analysis.count=").append(analysisReports.size()).append('\n');
        for (int index = 0; index < analysisReports.size(); index++) {
            GpuRuntimeIrOptimizationPassReport passReport = analysisReports.get(index);
            String prefix = "analysis." + index + ".";
            builder.append(prefix).append("passVersion=")
                    .append(safePropertyValue(passReport.optimizerVersion())).append('\n');
            builder.append(prefix).append("stage=").append(passReport.stage()).append('\n');
            builder.append(prefix).append("outcome=").append(passReport.outcome()).append('\n');
            builder.append(prefix).append("proofStatus=")
                    .append(safePropertyValue(passReport.proofStatus())).append('\n');
            builder.append(prefix).append("diagnostic.count=").append(passReport.diagnostics().size()).append('\n');
            for (int diagnosticIndex = 0; diagnosticIndex < passReport.diagnostics().size(); diagnosticIndex++) {
                builder.append(prefix).append("diagnostic.").append(diagnosticIndex).append('=')
                        .append(safePropertyValue(passReport.diagnostics().get(diagnosticIndex))).append('\n');
            }
            passReport.proofArtifact().fields().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> builder.append(prefix).append("field.")
                            .append(safePropertyValue(entry.getKey())).append('=')
                            .append(safePropertyValue(entry.getValue())).append('\n'));
        }
        appendCompilerFeedbackAnalysis(builder, analysisReports, compilerFeedbackReport);
        return builder.toString();
    }

    private static String formatRuntimeIrOptimizerEvidence(GpuRuntimeIrOptimizationReport report) {
        List<GpuRuntimeIrOptimizationPassReport> irOptimizerReports = report.passReports().stream()
                .filter(GpuRuntimeCompileArtifactDumper::isRuntimeIrOptimizerEvidence)
                .toList();
        if (irOptimizerReports.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("status=recorded\n");
        builder.append("source=runtime-ir-optimizer\n");
        builder.append("providerPrefix=javatogpu.ir-optimizer\n");
        builder.append("pass.count=").append(irOptimizerReports.size()).append('\n');
        builder.append("applied.count=").append(countOutcome(irOptimizerReports, GpuRuntimeIrOptimizationOutcome.APPLIED)).append('\n');
        builder.append("skipped.count=").append(countOutcome(irOptimizerReports, GpuRuntimeIrOptimizationOutcome.SKIPPED)).append('\n');
        builder.append("rolledBack.count=").append(countOutcome(irOptimizerReports, GpuRuntimeIrOptimizationOutcome.ROLLED_BACK)).append('\n');
        builder.append("failed.count=").append(countOutcome(irOptimizerReports, GpuRuntimeIrOptimizationOutcome.FAILED)).append('\n');
        builder.append("proposalOnly.count=").append(countProofStatus(irOptimizerReports, "proposal-only")).append('\n');
        builder.append("selectedOptimized.count=").append(countProofStatus(irOptimizerReports, "optimized-selected")).append('\n');
        builder.append("approvalTemplate.pending.count=")
                .append(countApprovalTemplateStatus(irOptimizerReports, "pending"))
                .append('\n');
        builder.append("approvalTemplate.notApplicable.count=")
                .append(countApprovalTemplateStatus(irOptimizerReports, "not-applicable"))
                .append('\n');
        ApprovalTemplatePayloadEvidence approvalTemplatePayload = approvalTemplatePayloadEvidence(irOptimizerReports);
        builder.append("approvalTemplate.runtimeEquivalencePayloadRequired.count=")
                .append(approvalTemplatePayload.requiredCount()).append('\n');
        builder.append("approvalTemplate.runtimeEquivalencePayloadPresent.count=")
                .append(approvalTemplatePayload.presentCount()).append('\n');
        builder.append("approvalTemplate.runtimeEquivalencePayloadPassed.count=")
                .append(approvalTemplatePayload.passedCount()).append('\n');
        builder.append("approvalTemplate.runtimeEquivalencePayloadComplete.count=")
                .append(approvalTemplatePayload.completeCount()).append('\n');
        PolicyGateEvidence policyGate = policyGateEvidence(irOptimizerReports);
        builder.append("policyGate.skipped.count=").append(policyGate.skippedCount()).append('\n');
        builder.append("policyGate.optimizerPolicyDisabled.count=")
                .append(policyGate.optimizerPolicyDisabledCount()).append('\n');
        builder.append("policyGate.familyDisabled.count=")
                .append(policyGate.familyDisabledCount()).append('\n');
        builder.append("policyGate.familyNotEnabled.count=")
                .append(policyGate.familyNotEnabledCount()).append('\n');
        builder.append("policyGate.providerInvoked.count=")
                .append(policyGate.providerInvokedCount()).append('\n');
        builder.append("policyGate.firstBlocker=")
                .append(safePropertyValue(policyGate.firstBlocker())).append('\n');
        builder.append("policyGate.family.summary=")
                .append(safePropertyValue(policyGate.familySummary())).append('\n');
        builder.append("policyGate.mutationAllowed=false\n");
        builder.append("policyGate.selectionApplied=false\n");
        builder.append("policyGate.optimizedArtifactSelected=false\n");
        builder.append("policyGate.selectedIrReplacement=false\n");
        OptimizedArtifactCandidateEvidence optimizedArtifactCandidate = optimizedArtifactCandidateEvidence(irOptimizerReports);
        builder.append("optimizedArtifactCandidate.status=").append(optimizedArtifactCandidate.status()).append('\n');
        builder.append("optimizedArtifactCandidate.count=").append(optimizedArtifactCandidate.count()).append('\n');
        builder.append("optimizedArtifactCandidate.ready.count=")
                .append(optimizedArtifactCandidate.readyCount()).append('\n');
        builder.append("optimizedArtifactCandidate.blocked.count=")
                .append(optimizedArtifactCandidate.blockedCount()).append('\n');
        builder.append("optimizedArtifactCandidate.selectionReady.count=")
                .append(optimizedArtifactCandidate.selectionReadyCount()).append('\n');
        builder.append("optimizedArtifactCandidate.selectionApplied.count=")
                .append(optimizedArtifactCandidate.selectionAppliedCount()).append('\n');
        builder.append("optimizedArtifactCandidate.selectedIrReplacement.count=")
                .append(optimizedArtifactCandidate.selectedIrReplacementCount()).append('\n');
        builder.append("optimizedArtifactCandidate.mutationAllowed.count=")
                .append(optimizedArtifactCandidate.mutationAllowedCount()).append('\n');
        builder.append("optimizedArtifactCandidate.firstBlocker=")
                .append(safePropertyValue(optimizedArtifactCandidate.firstBlocker())).append('\n');
        builder.append("optimizedArtifactCandidate.selectionFirstBlocker=")
                .append(safePropertyValue(optimizedArtifactCandidate.selectionFirstBlocker())).append('\n');
        builder.append("optimizedArtifactCandidate.selectionApplied=")
                .append(optimizedArtifactCandidate.selectionApplied()).append('\n');
        builder.append("optimizedArtifactCandidate.selectedIrReplacement=")
                .append(optimizedArtifactCandidate.selectedIrReplacement()).append('\n');
        BackendNeutralSourceMaterializationEvidence backendNeutralSourceMaterialization =
                backendNeutralSourceMaterializationEvidence(irOptimizerReports);
        builder.append("backendNeutralSourceMaterialization.pass.count=")
                .append(backendNeutralSourceMaterialization.passCount()).append('\n');
        builder.append("backendNeutralSourceMaterialization.candidate.count=")
                .append(backendNeutralSourceMaterialization.candidateCount()).append('\n');
        builder.append("backendNeutralSourceMaterialization.sourceReady.count=")
                .append(backendNeutralSourceMaterialization.sourceReadyCount()).append('\n');
        builder.append("backendNeutralSourceMaterialization.sourceLength.total=")
                .append(backendNeutralSourceMaterialization.sourceLengthTotal()).append('\n');
        builder.append("backendNeutralSourceMaterialization.materializationOnly.count=")
                .append(backendNeutralSourceMaterialization.materializationOnlyCount()).append('\n');
        builder.append("backendNeutralSourceMaterialization.status=")
                .append(backendNeutralSourceMaterialization.status()).append('\n');
        builder.append("backendNeutralSourceMaterialization.firstBlocker=")
                .append(safePropertyValue(backendNeutralSourceMaterialization.firstBlocker())).append('\n');
        ConstantFoldingPreviewEvidence constantFoldingPreview = constantFoldingPreviewEvidence(irOptimizerReports);
        builder.append("constantFoldingPreview.pass.count=").append(constantFoldingPreview.passCount()).append('\n');
        builder.append("constantFoldingPreview.candidate.count=").append(constantFoldingPreview.candidateCount()).append('\n');
        builder.append("constantFoldingPreview.skipped.nonPlainLiteral.count=")
                .append(constantFoldingPreview.skippedNonPlainLiteralCount()).append('\n');
        builder.append("constantFoldingPreview.skipped.divideByZero.count=")
                .append(constantFoldingPreview.skippedDivideByZeroCount()).append('\n');
        builder.append("constantFoldingPreview.skipped.nonEvenDivision.count=")
                .append(constantFoldingPreview.skippedNonEvenDivisionCount()).append('\n');
        builder.append("constantFoldingPreview.skipped.unsupportedOperator.count=")
                .append(constantFoldingPreview.skippedUnsupportedOperatorCount()).append('\n');
        builder.append("constantFoldingPreview.skipped.nonLiteralOperand.count=")
                .append(constantFoldingPreview.skippedNonLiteralOperandCount()).append('\n');
        builder.append("constantFoldingPreview.runtimeEquivalenceRequiredBeforeRewrite=")
                .append(constantFoldingPreview.runtimeEquivalenceRequiredBeforeRewrite()).append('\n');
        builder.append("constantFoldingPreview.approvalRequiredBeforeRewrite=")
                .append(constantFoldingPreview.approvalRequiredBeforeRewrite()).append('\n');
        builder.append("constantFoldingPreview.integerOverflowProven=")
                .append(constantFoldingPreview.integerOverflowProven()).append('\n');
        builder.append("constantFoldingPreview.floatingPointRoundingProven=")
                .append(constantFoldingPreview.floatingPointRoundingProven()).append('\n');
        ConstantFoldingMaterializationEvidence constantFoldingMaterialization =
                constantFoldingMaterializationEvidence(irOptimizerReports);
        builder.append("constantFoldingMaterialization.pass.count=")
                .append(constantFoldingMaterialization.passCount()).append('\n');
        builder.append("constantFoldingMaterialization.candidate.count=")
                .append(constantFoldingMaterialization.candidateCount()).append('\n');
        builder.append("constantFoldingMaterialization.transformedNode.count=")
                .append(constantFoldingMaterialization.transformedNodeCount()).append('\n');
        builder.append("constantFoldingMaterialization.literalRewrite.count=")
                .append(constantFoldingMaterialization.literalRewriteCount()).append('\n');
        builder.append("constantFoldingMaterialization.identityRewrite.count=")
                .append(constantFoldingMaterialization.identityRewriteCount()).append('\n');
        builder.append("constantFoldingMaterialization.fixedPointPass.count=")
                .append(constantFoldingMaterialization.fixedPointPassCount()).append('\n');
        builder.append("constantFoldingMaterialization.changedMethodBody.count=")
                .append(constantFoldingMaterialization.changedMethodBodyCount()).append('\n');
        builder.append("constantFoldingMaterialization.bodyTextReplacement.count=")
                .append(constantFoldingMaterialization.bodyTextReplacementCount()).append('\n');
        builder.append("constantFoldingMaterialization.skipped.divideByZero.count=")
                .append(constantFoldingMaterialization.skippedDivideByZeroCount()).append('\n');
        builder.append("constantFoldingMaterialization.skipped.nonEvenDivision.count=")
                .append(constantFoldingMaterialization.skippedNonEvenDivisionCount()).append('\n');
        builder.append("constantFoldingMaterialization.runtimeEquivalenceRequiredBeforeSelection=")
                .append(constantFoldingMaterialization.runtimeEquivalenceRequiredBeforeSelection()).append('\n');
        builder.append("constantFoldingMaterialization.runtimeEquivalencePayloadRequired=")
                .append(constantFoldingMaterialization.runtimeEquivalencePayloadRequired()).append('\n');
        builder.append("constantFoldingMaterialization.runtimeEquivalencePayloadPresent.count=")
                .append(constantFoldingMaterialization.runtimeEquivalencePayloadPresentCount()).append('\n');
        builder.append("constantFoldingMaterialization.runtimeEquivalencePassed.count=")
                .append(constantFoldingMaterialization.runtimeEquivalencePassedCount()).append('\n');
        builder.append("constantFoldingMaterialization.approvalRequiredBeforeProduction=")
                .append(constantFoldingMaterialization.approvalRequiredBeforeProduction()).append('\n');
        builder.append("constantFoldingMaterialization.status=")
                .append(constantFoldingMaterialization.status()).append('\n');
        builder.append("constantFoldingMaterialization.firstBlocker=")
                .append(safePropertyValue(constantFoldingMaterialization.firstBlocker())).append('\n');
        SafeLocalCsePreviewEvidence safeLocalCsePreview = safeLocalCsePreviewEvidence(irOptimizerReports);
        builder.append("safeLocalCsePreview.pass.count=").append(safeLocalCsePreview.passCount()).append('\n');
        builder.append("safeLocalCsePreview.expression.count=")
                .append(safeLocalCsePreview.expressionCount()).append('\n');
        builder.append("safeLocalCsePreview.candidateExpression.count=")
                .append(safeLocalCsePreview.candidateExpressionCount()).append('\n');
        builder.append("safeLocalCsePreview.duplicateExpression.count=")
                .append(safeLocalCsePreview.duplicateExpressionCount()).append('\n');
        builder.append("safeLocalCsePreview.equivalenceClass.count=")
                .append(safeLocalCsePreview.equivalenceClassCount()).append('\n');
        builder.append("safeLocalCsePreview.blocked.unsupportedOperator.count=")
                .append(safeLocalCsePreview.blockedUnsupportedOperatorCount()).append('\n');
        builder.append("safeLocalCsePreview.blocked.impureOperand.count=")
                .append(safeLocalCsePreview.blockedImpureOperandCount()).append('\n');
        builder.append("safeLocalCsePreview.blocked.controlFlowBoundary.count=")
                .append(safeLocalCsePreview.blockedControlFlowBoundaryCount()).append('\n');
        builder.append("safeLocalCsePreview.runtimeEquivalenceRequiredBeforeRewrite=")
                .append(safeLocalCsePreview.runtimeEquivalenceRequiredBeforeRewrite()).append('\n');
        builder.append("safeLocalCsePreview.approvalRequiredBeforeRewrite=")
                .append(safeLocalCsePreview.approvalRequiredBeforeRewrite()).append('\n');
        builder.append("safeLocalCsePreview.dominanceProven=")
                .append(safeLocalCsePreview.dominanceProven()).append('\n');
        builder.append("safeLocalCsePreview.sideEffectFreedomProven=")
                .append(safeLocalCsePreview.sideEffectFreedomProven()).append('\n');
        SafeLocalCseMaterializationEvidence safeLocalCseMaterialization =
                safeLocalCseMaterializationEvidence(irOptimizerReports);
        builder.append("safeLocalCseMaterialization.pass.count=")
                .append(safeLocalCseMaterialization.passCount()).append('\n');
        builder.append("safeLocalCseMaterialization.localBinding.count=")
                .append(safeLocalCseMaterialization.localBindingCount()).append('\n');
        builder.append("safeLocalCseMaterialization.candidate.count=")
                .append(safeLocalCseMaterialization.candidateCount()).append('\n');
        builder.append("safeLocalCseMaterialization.transformedNode.count=")
                .append(safeLocalCseMaterialization.transformedNodeCount()).append('\n');
        builder.append("safeLocalCseMaterialization.changedMethodBody.count=")
                .append(safeLocalCseMaterialization.changedMethodBodyCount()).append('\n');
        builder.append("safeLocalCseMaterialization.bodyTextReplacement.count=")
                .append(safeLocalCseMaterialization.bodyTextReplacementCount()).append('\n');
        builder.append("safeLocalCseMaterialization.fixedPoint.pass.count=")
                .append(safeLocalCseMaterialization.fixedPointPassCount()).append('\n');
        builder.append("safeLocalCseMaterialization.skipped.controlFlowBoundary.count=")
                .append(safeLocalCseMaterialization.skippedControlFlowBoundaryCount()).append('\n');
        builder.append("safeLocalCseMaterialization.skipped.unsupportedOperator.count=")
                .append(safeLocalCseMaterialization.skippedUnsupportedOperatorCount()).append('\n');
        builder.append("safeLocalCseMaterialization.skipped.impureOperand.count=")
                .append(safeLocalCseMaterialization.skippedImpureOperandCount()).append('\n');
        builder.append("safeLocalCseMaterialization.skipped.bodyTextPatternMissing.count=")
                .append(safeLocalCseMaterialization.skippedBodyTextPatternMissingCount()).append('\n');
        builder.append("safeLocalCseMaterialization.runtimeEquivalenceRequiredBeforeSelection=")
                .append(safeLocalCseMaterialization.runtimeEquivalenceRequiredBeforeSelection()).append('\n');
        builder.append("safeLocalCseMaterialization.runtimeEquivalencePayloadRequired=")
                .append(safeLocalCseMaterialization.runtimeEquivalencePayloadRequired()).append('\n');
        builder.append("safeLocalCseMaterialization.runtimeEquivalencePayloadPresent.count=")
                .append(safeLocalCseMaterialization.runtimeEquivalencePayloadPresentCount()).append('\n');
        builder.append("safeLocalCseMaterialization.runtimeEquivalencePassed.count=")
                .append(safeLocalCseMaterialization.runtimeEquivalencePassedCount()).append('\n');
        builder.append("safeLocalCseMaterialization.approvalRequiredBeforeProduction=")
                .append(safeLocalCseMaterialization.approvalRequiredBeforeProduction()).append('\n');
        builder.append("safeLocalCseMaterialization.dominanceProven=")
                .append(safeLocalCseMaterialization.dominanceProven()).append('\n');
        builder.append("safeLocalCseMaterialization.sideEffectFreedomProven=")
                .append(safeLocalCseMaterialization.sideEffectFreedomProven()).append('\n');
        builder.append("safeLocalCseMaterialization.status=")
                .append(safeLocalCseMaterialization.status()).append('\n');
        builder.append("safeLocalCseMaterialization.firstBlocker=")
                .append(safePropertyValue(safeLocalCseMaterialization.firstBlocker())).append('\n');
        MadFmaMaterializationEvidence madFmaMaterialization = madFmaMaterializationEvidence(irOptimizerReports);
        builder.append("madFmaMaterialization.pass.count=")
                .append(madFmaMaterialization.passCount()).append('\n');
        builder.append("madFmaMaterialization.candidate.count=")
                .append(madFmaMaterialization.candidateCount()).append('\n');
        builder.append("madFmaMaterialization.transformedNode.count=")
                .append(madFmaMaterialization.transformedNodeCount()).append('\n');
        builder.append("madFmaMaterialization.changedMethodBody.count=")
                .append(madFmaMaterialization.changedMethodBodyCount()).append('\n');
        builder.append("madFmaMaterialization.bodyTextReplacement.count=")
                .append(madFmaMaterialization.bodyTextReplacementCount()).append('\n');
        builder.append("madFmaMaterialization.fixedPoint.pass.count=")
                .append(madFmaMaterialization.fixedPointPassCount()).append('\n');
        builder.append("madFmaMaterialization.skipped.fastMathPolicy.count=")
                .append(madFmaMaterialization.skippedFastMathPolicyCount()).append('\n');
        builder.append("madFmaMaterialization.skipped.bodyTextPatternMissing.count=")
                .append(madFmaMaterialization.skippedBodyTextPatternMissingCount()).append('\n');
        builder.append("madFmaMaterialization.runtimeEquivalenceRequiredBeforeSelection=")
                .append(madFmaMaterialization.runtimeEquivalenceRequiredBeforeSelection()).append('\n');
        builder.append("madFmaMaterialization.runtimeEquivalencePayloadRequired=")
                .append(madFmaMaterialization.runtimeEquivalencePayloadRequired()).append('\n');
        builder.append("madFmaMaterialization.runtimeEquivalencePayloadPresent.count=")
                .append(madFmaMaterialization.runtimeEquivalencePayloadPresentCount()).append('\n');
        builder.append("madFmaMaterialization.runtimeEquivalencePassed.count=")
                .append(madFmaMaterialization.runtimeEquivalencePassedCount()).append('\n');
        builder.append("madFmaMaterialization.approvalRequiredBeforeProduction=")
                .append(madFmaMaterialization.approvalRequiredBeforeProduction()).append('\n');
        builder.append("madFmaMaterialization.fastMathAllowed=")
                .append(madFmaMaterialization.fastMathAllowed()).append('\n');
        builder.append("madFmaMaterialization.status=")
                .append(madFmaMaterialization.status()).append('\n');
        builder.append("madFmaMaterialization.firstBlocker=")
                .append(safePropertyValue(madFmaMaterialization.firstBlocker())).append('\n');
        IntrinsicMaterializationEvidence clampMaterialization = intrinsicMaterializationEvidence(
                irOptimizerReports,
                "clamp-materialization"
        );
        appendIntrinsicMaterializationEvidence(builder, "clampMaterialization", clampMaterialization);
        builder.append("clampMaterialization.strictFloatPreserved=")
                .append(clampMaterialization.strictFloatPreserved()).append('\n');
        builder.append("clampMaterialization.argumentOrderPreserved=")
                .append(clampMaterialization.argumentOrderPreserved()).append('\n');
        builder.append("clampMaterialization.fastMathRequired=")
                .append(clampMaterialization.fastMathRequired()).append('\n');
        IntrinsicMaterializationEvidence stepMaterialization = intrinsicMaterializationEvidence(
                irOptimizerReports,
                "step-materialization"
        );
        appendIntrinsicMaterializationEvidence(builder, "stepMaterialization", stepMaterialization);
        builder.append("stepMaterialization.directStep.count=")
                .append(stepMaterialization.directStepCount()).append('\n');
        builder.append("stepMaterialization.invertedStep.count=")
                .append(stepMaterialization.invertedStepCount()).append('\n');
        builder.append("stepMaterialization.strictFloatPreserved=")
                .append(stepMaterialization.strictFloatPreserved()).append('\n');
        builder.append("stepMaterialization.strictComparisonPreserved=")
                .append(stepMaterialization.strictComparisonPreserved()).append('\n');
        builder.append("stepMaterialization.equalityBehaviorPreserved=")
                .append(stepMaterialization.equalityBehaviorPreserved()).append('\n');
        builder.append("stepMaterialization.nanComparisonPreserved=")
                .append(stepMaterialization.nanComparisonPreserved()).append('\n');
        builder.append("stepMaterialization.fastMathRequired=")
                .append(stepMaterialization.fastMathRequired()).append('\n');
        IntrinsicMaterializationEvidence mixMaterialization = intrinsicMaterializationEvidence(
                irOptimizerReports,
                "mix-materialization"
        );
        appendIntrinsicMaterializationEvidence(builder, "mixMaterialization", mixMaterialization);
        builder.append("mixMaterialization.canonicalMix.count=")
                .append(mixMaterialization.canonicalMixCount()).append('\n');
        builder.append("mixMaterialization.expandedMix.count=")
                .append(mixMaterialization.expandedMixCount()).append('\n');
        builder.append("mixMaterialization.madExpandedMix.count=")
                .append(mixMaterialization.madExpandedMixCount()).append('\n');
        builder.append("mixMaterialization.fastMathAllowed=")
                .append(mixMaterialization.fastMathAllowed()).append('\n');
        builder.append("mixMaterialization.fastMathRequired=")
                .append(mixMaterialization.fastMathRequired()).append('\n');
        builder.append("mixMaterialization.strictFloatPreserved=")
                .append(mixMaterialization.strictFloatPreserved()).append('\n');
        builder.append("mixMaterialization.algebraicReassociationRequired=")
                .append(mixMaterialization.algebraicReassociationRequired()).append('\n');
        builder.append("mixMaterialization.mixArgumentOrderPreserved=")
                .append(mixMaterialization.mixArgumentOrderPreserved()).append('\n');
        LoopVectorizationMaterializationEvidence loopVectorizationMaterialization =
                loopVectorizationMaterializationEvidence(irOptimizerReports);
        builder.append("loopVectorizationMaterialization.pass.count=")
                .append(loopVectorizationMaterialization.passCount()).append('\n');
        builder.append("loopVectorizationMaterialization.candidate.count=")
                .append(loopVectorizationMaterialization.candidateCount()).append('\n');
        builder.append("loopVectorizationMaterialization.transformedLoop.count=")
                .append(loopVectorizationMaterialization.transformedLoopCount()).append('\n');
        builder.append("loopVectorizationMaterialization.changedMethodBody.count=")
                .append(loopVectorizationMaterialization.changedMethodBodyCount()).append('\n');
        builder.append("loopVectorizationMaterialization.bodyTextReplacement.count=")
                .append(loopVectorizationMaterialization.bodyTextReplacementCount()).append('\n');
        builder.append("loopVectorizationMaterialization.typedBody.materialized.count=")
                .append(loopVectorizationMaterialization.typedBodyMaterializedCount()).append('\n');
        builder.append("loopVectorizationMaterialization.typedBody.invalidated.count=")
                .append(loopVectorizationMaterialization.typedBodyInvalidatedCount()).append('\n');
        builder.append("loopVectorizationMaterialization.skipped.loopShape.count=")
                .append(loopVectorizationMaterialization.skippedLoopShapeCount()).append('\n');
        builder.append("loopVectorizationMaterialization.skipped.unsupportedWidth.count=")
                .append(loopVectorizationMaterialization.skippedUnsupportedWidthCount()).append('\n');
        builder.append("loopVectorizationMaterialization.skipped.unsafeLoadPattern.count=")
                .append(loopVectorizationMaterialization.skippedUnsafeLoadPatternCount()).append('\n');
        builder.append("loopVectorizationMaterialization.runtimeEquivalenceRequiredBeforeSelection=")
                .append(loopVectorizationMaterialization.runtimeEquivalenceRequiredBeforeSelection()).append('\n');
        builder.append("loopVectorizationMaterialization.runtimeEquivalencePayloadRequired=")
                .append(loopVectorizationMaterialization.runtimeEquivalencePayloadRequired()).append('\n');
        builder.append("loopVectorizationMaterialization.runtimeEquivalencePayloadPresent.count=")
                .append(loopVectorizationMaterialization.runtimeEquivalencePayloadPresentCount()).append('\n');
        builder.append("loopVectorizationMaterialization.runtimeEquivalencePassed.count=")
                .append(loopVectorizationMaterialization.runtimeEquivalencePassedCount()).append('\n');
        builder.append("loopVectorizationMaterialization.approvalRequiredBeforeProduction=")
                .append(loopVectorizationMaterialization.approvalRequiredBeforeProduction()).append('\n');
        builder.append("loopVectorizationMaterialization.loopTripCountProven=")
                .append(loopVectorizationMaterialization.loopTripCountProven()).append('\n');
        builder.append("loopVectorizationMaterialization.contiguousLoadProven=")
                .append(loopVectorizationMaterialization.contiguousLoadProven()).append('\n');
        builder.append("loopVectorizationMaterialization.orderedReductionPreserved=")
                .append(loopVectorizationMaterialization.orderedReductionPreserved()).append('\n');
        builder.append("loopVectorizationMaterialization.status=")
                .append(loopVectorizationMaterialization.status()).append('\n');
        builder.append("loopVectorizationMaterialization.firstBlocker=")
                .append(safePropertyValue(loopVectorizationMaterialization.firstBlocker())).append('\n');
        TypedDeadCodePreviewEvidence typedDeadCodePreview = typedDeadCodePreviewEvidence(irOptimizerReports);
        builder.append("typedDeadCodePreview.pass.count=").append(typedDeadCodePreview.passCount()).append('\n');
        builder.append("typedDeadCodePreview.node.count=").append(typedDeadCodePreview.nodeCount()).append('\n');
        builder.append("typedDeadCodePreview.reachableNode.count=")
                .append(typedDeadCodePreview.reachableNodeCount()).append('\n');
        builder.append("typedDeadCodePreview.unreachableNode.count=")
                .append(typedDeadCodePreview.unreachableNodeCount()).append('\n');
        builder.append("typedDeadCodePreview.blocked.missingRoot.count=")
                .append(typedDeadCodePreview.blockedMissingRootCount()).append('\n');
        builder.append("typedDeadCodePreview.blocked.missingChildReference.count=")
                .append(typedDeadCodePreview.blockedMissingChildReferenceCount()).append('\n');
        builder.append("typedDeadCodePreview.blocked.sideEffectingUnreachableNode.count=")
                .append(typedDeadCodePreview.blockedSideEffectingUnreachableNodeCount()).append('\n');
        builder.append("typedDeadCodePreview.runtimeEquivalenceRequiredBeforeRewrite=")
                .append(typedDeadCodePreview.runtimeEquivalenceRequiredBeforeRewrite()).append('\n');
        builder.append("typedDeadCodePreview.approvalRequiredBeforeRewrite=")
                .append(typedDeadCodePreview.approvalRequiredBeforeRewrite()).append('\n');
        builder.append("typedDeadCodePreview.sideEffectFreedomProven=")
                .append(typedDeadCodePreview.sideEffectFreedomProven()).append('\n');
        TypedDeadCodeMaterializationEvidence typedDeadCodeMaterialization =
                typedDeadCodeMaterializationEvidence(irOptimizerReports);
        builder.append("typedDeadCodeMaterialization.pass.count=")
                .append(typedDeadCodeMaterialization.passCount()).append('\n');
        builder.append("typedDeadCodeMaterialization.node.count=")
                .append(typedDeadCodeMaterialization.nodeCount()).append('\n');
        builder.append("typedDeadCodeMaterialization.unreachableNode.count=")
                .append(typedDeadCodeMaterialization.unreachableNodeCount()).append('\n');
        builder.append("typedDeadCodeMaterialization.removedNode.count=")
                .append(typedDeadCodeMaterialization.removedNodeCount()).append('\n');
        builder.append("typedDeadCodeMaterialization.changedMethodBody.count=")
                .append(typedDeadCodeMaterialization.changedMethodBodyCount()).append('\n');
        builder.append("typedDeadCodeMaterialization.blocked.missingRoot.count=")
                .append(typedDeadCodeMaterialization.blockedMissingRootCount()).append('\n');
        builder.append("typedDeadCodeMaterialization.blocked.missingChildReference.count=")
                .append(typedDeadCodeMaterialization.blockedMissingChildReferenceCount()).append('\n');
        builder.append("typedDeadCodeMaterialization.blocked.sideEffectingUnreachableNode.count=")
                .append(typedDeadCodeMaterialization.blockedSideEffectingUnreachableNodeCount()).append('\n');
        builder.append("typedDeadCodeMaterialization.runtimeEquivalenceRequiredBeforeSelection=")
                .append(typedDeadCodeMaterialization.runtimeEquivalenceRequiredBeforeSelection()).append('\n');
        builder.append("typedDeadCodeMaterialization.runtimeEquivalencePayloadRequired=")
                .append(typedDeadCodeMaterialization.runtimeEquivalencePayloadRequired()).append('\n');
        builder.append("typedDeadCodeMaterialization.runtimeEquivalencePayloadPresent.count=")
                .append(typedDeadCodeMaterialization.runtimeEquivalencePayloadPresentCount()).append('\n');
        builder.append("typedDeadCodeMaterialization.runtimeEquivalencePassed.count=")
                .append(typedDeadCodeMaterialization.runtimeEquivalencePassedCount()).append('\n');
        builder.append("typedDeadCodeMaterialization.approvalRequiredBeforeProduction=")
                .append(typedDeadCodeMaterialization.approvalRequiredBeforeProduction()).append('\n');
        builder.append("typedDeadCodeMaterialization.sideEffectFreedomProven=")
                .append(typedDeadCodeMaterialization.sideEffectFreedomProven()).append('\n');
        builder.append("typedDeadCodeMaterialization.status=")
                .append(typedDeadCodeMaterialization.status()).append('\n');
        builder.append("typedDeadCodeMaterialization.firstBlocker=")
                .append(safePropertyValue(typedDeadCodeMaterialization.firstBlocker())).append('\n');
        PreviewReadinessEvidence previewReadiness = previewReadinessEvidence(
                constantFoldingPreview,
                safeLocalCsePreview,
                typedDeadCodePreview
        );
        builder.append("previewReadiness.status=").append(previewReadiness.status()).append('\n');
        builder.append("previewReadiness.family.count=").append(previewReadiness.familyCount()).append('\n');
        builder.append("previewReadiness.candidateFamily.count=")
                .append(previewReadiness.candidateFamilyCount()).append('\n');
        builder.append("previewReadiness.blockedFamily.count=")
                .append(previewReadiness.blockedFamilyCount()).append('\n');
        builder.append("previewReadiness.familySummary=")
                .append(safePropertyValue(previewReadiness.familySummary())).append('\n');
        RuntimeEquivalenceReviewEvidence runtimeEquivalenceReview = runtimeEquivalenceReviewEvidence(
                previewReadiness,
                constantFoldingMaterialization,
                safeLocalCseMaterialization,
                madFmaMaterialization,
                clampMaterialization,
                stepMaterialization,
                mixMaterialization,
                loopVectorizationMaterialization,
                typedDeadCodeMaterialization
        );
        builder.append("runtimeEquivalenceReview.status=").append(runtimeEquivalenceReview.status()).append('\n');
        builder.append("runtimeEquivalenceReview.eligible=").append(runtimeEquivalenceReview.eligible()).append('\n');
        builder.append("runtimeEquivalenceReview.required=").append(runtimeEquivalenceReview.required()).append('\n');
        builder.append("runtimeEquivalenceReview.firstBlocker=")
                .append(safePropertyValue(runtimeEquivalenceReview.firstBlocker())).append('\n');
        builder.append("runtimeEquivalenceReview.familySummary=")
                .append(safePropertyValue(runtimeEquivalenceReview.familySummary())).append('\n');
        builder.append("runtimeEquivalenceReview.productionMutation=disabled\n");
        builder.append("runtimeEquivalenceReview.selectedIrReplacement=disabled\n");
        builder.append("runtimeEquivalenceReview.manualReviewOnly=true\n");
        ReviewPackageEvidence reviewPackage = reviewPackageEvidence(irOptimizerReports, runtimeEquivalenceReview);
        builder.append("reviewPackage.status=").append(reviewPackage.status()).append('\n');
        builder.append("reviewPackage.required=").append(reviewPackage.required()).append('\n');
        builder.append("reviewPackage.complete=").append(reviewPackage.complete()).append('\n');
        builder.append("reviewPackage.firstBlocker=")
                .append(safePropertyValue(reviewPackage.firstBlocker())).append('\n');
        builder.append("reviewPackage.proposalPass.count=").append(reviewPackage.proposalPassCount()).append('\n');
        builder.append("reviewPackage.pendingApproval.count=").append(reviewPackage.pendingApprovalCount()).append('\n');
        builder.append("reviewPackage.runtimeEquivalence.status=")
                .append(safePropertyValue(reviewPackage.runtimeEquivalenceStatus())).append('\n');
        ApprovalManifestPackageEvidence approvalManifestPackage = approvalManifestPackageEvidence(
                irOptimizerReports,
                reviewPackage
        );
        builder.append("reviewPackage.approvalManifest.status=")
                .append(approvalManifestPackage.status()).append('\n');
        builder.append("reviewPackage.approvalManifest.required=")
                .append(approvalManifestPackage.required()).append('\n');
        builder.append("reviewPackage.approvalManifest.present.count=")
                .append(approvalManifestPackage.presentCount()).append('\n');
        builder.append("reviewPackage.approvalManifest.accepted.count=")
                .append(approvalManifestPackage.acceptedCount()).append('\n');
        builder.append("reviewPackage.approvalManifest.resourcePath.summary=")
                .append(safePropertyValue(approvalManifestPackage.resourcePathSummary())).append('\n');
        builder.append("reviewPackage.approvalManifest.firstBlocker=")
                .append(safePropertyValue(approvalManifestPackage.firstBlocker())).append('\n');
        builder.append("reviewPackage.approvalManifest.manualReviewOnly=true\n");
        builder.append("reviewPackage.approvalManifest.productionMutation=disabled\n");
        builder.append("reviewPackage.approvalManifest.selectedIrReplacement=disabled\n");
        builder.append("reviewPackage.originalIrRequired=true\n");
        builder.append("reviewPackage.optimizedIrRequired=true\n");
        builder.append("reviewPackage.proofSummaryRequired=true\n");
        builder.append("reviewPackage.manualReviewOnly=true\n");
        builder.append("reviewPackage.productionMutation=disabled\n");
        builder.append("reviewPackage.selectedIrReplacement=disabled\n");
        for (int index = 0; index < irOptimizerReports.size(); index++) {
            GpuRuntimeIrOptimizationPassReport passReport = irOptimizerReports.get(index);
            String prefix = "pass." + index + ".";
            ApprovalTemplateEvidence approvalTemplate = approvalTemplateEvidence(passReport);
            builder.append(prefix).append("passVersion=")
                    .append(safePropertyValue(passReport.optimizerVersion())).append('\n');
            builder.append(prefix).append("stage=").append(passReport.stage()).append('\n');
            builder.append(prefix).append("outcome=").append(passReport.outcome()).append('\n');
            builder.append(prefix).append("proofStatus=")
                    .append(safePropertyValue(passReport.proofStatus())).append('\n');
            builder.append(prefix).append("originalIrIdentity=")
                    .append(safePropertyValue(passReport.originalIrIdentity())).append('\n');
            builder.append(prefix).append("transformedIrIdentity=")
                    .append(safePropertyValue(passReport.transformedIrIdentity())).append('\n');
            builder.append(prefix).append("rollbackReason=")
                    .append(safePropertyValue(passReport.rollbackReason())).append('\n');
            builder.append(prefix).append("proofArtifact.source=")
                    .append(safePropertyValue(passReport.proofArtifact().source())).append('\n');
            builder.append(prefix).append("proofArtifact.verdict=")
                    .append(safePropertyValue(passReport.proofArtifact().verdict())).append('\n');
            builder.append(prefix).append("proofArtifact.field.count=")
                    .append(passReport.proofArtifact().fields().size()).append('\n');
            passReport.proofArtifact().fields().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> builder.append(prefix).append("proofArtifact.field.")
                            .append(safePropertyValue(entry.getKey())).append('=')
                            .append(safePropertyValue(entry.getValue())).append('\n'));
            builder.append(prefix).append("approvalTemplate.status=")
                    .append(approvalTemplate.status()).append('\n');
            builder.append(prefix).append("approvalTemplate.applicable=")
                    .append(approvalTemplate.applicable()).append('\n');
            builder.append(prefix).append("approvalTemplate.firstBlocker=")
                    .append(safePropertyValue(approvalTemplate.firstBlocker())).append('\n');
            builder.append(prefix).append("approvalTemplate.productionMutation=disabled\n");
            builder.append(prefix).append("approvalTemplate.manualReviewOnly=true\n");
            if (approvalTemplate.applicable()) {
                builder.append(prefix)
                        .append("approvalTemplate.resourceDirectory=META-INF/javatogpu/ir-optimization-approvals/\n");
            }
            approvalTemplate.fields().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> builder.append(prefix).append("approvalTemplate.field.")
                            .append(safePropertyValue(entry.getKey())).append('=')
                            .append(safePropertyValue(entry.getValue())).append('\n'));
            builder.append(prefix).append("diagnostic.count=").append(passReport.diagnostics().size()).append('\n');
            for (int diagnosticIndex = 0; diagnosticIndex < passReport.diagnostics().size(); diagnosticIndex++) {
                builder.append(prefix).append("diagnostic.").append(diagnosticIndex).append('=')
                        .append(safePropertyValue(passReport.diagnostics().get(diagnosticIndex))).append('\n');
            }
        }
        return builder.toString();
    }

    private static boolean isRuntimeIrOptimizerEvidence(GpuRuntimeIrOptimizationPassReport passReport) {
        if (passReport == null) {
            return false;
        }
        String optimizerVersion = passReport.optimizerVersion() == null ? "" : passReport.optimizerVersion();
        String proofSource = passReport.proofArtifact().source() == null ? "" : passReport.proofArtifact().source();
        return optimizerVersion.startsWith("javatogpu.ir-optimizer")
                || proofSource.startsWith("ir-optimizer");
    }

    private static long countOutcome(
            List<GpuRuntimeIrOptimizationPassReport> passReports,
            GpuRuntimeIrOptimizationOutcome outcome
    ) {
        return passReports.stream().filter(passReport -> passReport.outcome() == outcome).count();
    }

    private static long countProofStatus(List<GpuRuntimeIrOptimizationPassReport> passReports, String proofStatus) {
        return passReports.stream()
                .filter(passReport -> proofStatus.equals(passReport.proofStatus()))
                .count();
    }

    private static long countApprovalTemplateStatus(
            List<GpuRuntimeIrOptimizationPassReport> passReports,
            String status
    ) {
        return passReports.stream()
                .map(GpuRuntimeCompileArtifactDumper::approvalTemplateEvidence)
                .filter(evidence -> status.equals(evidence.status()))
                .count();
    }

    private static ApprovalTemplatePayloadEvidence approvalTemplatePayloadEvidence(
            List<GpuRuntimeIrOptimizationPassReport> passReports
    ) {
        int requiredCount = 0;
        int presentCount = 0;
        int passedCount = 0;
        int completeCount = 0;
        for (GpuRuntimeIrOptimizationPassReport passReport : passReports) {
            ApprovalTemplateEvidence approvalTemplate = approvalTemplateEvidence(passReport);
            if (!approvalTemplate.applicable()) {
                continue;
            }
            Map<String, String> fields = approvalTemplate.fields();
            if (parseBoolean(fields.get("runtimeEquivalencePayload.required"))) {
                requiredCount++;
            }
            if (parseBoolean(fields.get("runtimeEquivalencePayload.present"))) {
                presentCount++;
            }
            if (parseBoolean(fields.get("runtimeEquivalencePayload.passed"))) {
                passedCount++;
            }
            if (approvalTemplatePayloadComplete(fields)) {
                completeCount++;
            }
        }
        return new ApprovalTemplatePayloadEvidence(requiredCount, presentCount, passedCount, completeCount);
    }

    private static boolean approvalTemplatePayloadComplete(Map<String, String> fields) {
        return parseBoolean(fields.get("runtimeEquivalencePayload.present"))
                && parseBoolean(fields.get("runtimeEquivalencePayload.passed"))
                && parseBoolean(fields.get("runtimeEquivalencePayload.componentsComplete"))
                && parseNonNegativeInt(fields.get("runtimeEquivalencePayload.caseCount")) > 0;
    }

    private record ApprovalTemplatePayloadEvidence(
            int requiredCount,
            int presentCount,
            int passedCount,
            int completeCount
    ) {
    }

    private static PolicyGateEvidence policyGateEvidence(
            List<GpuRuntimeIrOptimizationPassReport> passReports
    ) {
        int skippedCount = 0;
        int optimizerPolicyDisabledCount = 0;
        int familyDisabledCount = 0;
        int familyNotEnabledCount = 0;
        int providerInvokedCount = 0;
        String firstBlocker = "none";
        LinkedHashMap<String, Integer> familyCounts = new LinkedHashMap<>();
        for (GpuRuntimeIrOptimizationPassReport passReport : passReports) {
            if (passReport == null || passReport.proofArtifact() == null) {
                continue;
            }
            Map<String, String> fields = passReport.proofArtifact().fields();
            if (!"skipped".equals(fields.get("policyGate.status"))) {
                continue;
            }
            skippedCount++;
            String reason = fields.getOrDefault("policyGate.reason", "unknown");
            String family = fields.getOrDefault("policyGate.family", fields.getOrDefault("optimizerFamily", "unknown"));
            if (!family.isBlank()) {
                familyCounts.merge(family, 1, Integer::sum);
            }
            if (parseBoolean(fields.get("policyGate.providerInvoked"))) {
                providerInvokedCount++;
            }
            switch (reason) {
                case "optimizer-policy-disabled" -> optimizerPolicyDisabledCount++;
                case "optimizer-family-disabled" -> familyDisabledCount++;
                case "optimizer-family-not-enabled" -> familyNotEnabledCount++;
                default -> { }
            }
            if ("none".equals(firstBlocker) && !reason.isBlank() && !"none".equals(reason)) {
                firstBlocker = reason;
            }
        }
        return new PolicyGateEvidence(
                skippedCount,
                optimizerPolicyDisabledCount,
                familyDisabledCount,
                familyNotEnabledCount,
                providerInvokedCount,
                skippedCount == 0 ? "none" : firstBlocker,
                formatCountSummary(familyCounts)
        );
    }

    private static String formatCountSummary(Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return "none";
        }
        StringBuilder summary = new StringBuilder();
        counts.forEach((key, count) -> {
            if (!summary.isEmpty()) {
                summary.append(", ");
            }
            summary.append(key).append('=').append(Math.max(0, count));
        });
        return summary.toString();
    }

    private static OptimizedArtifactCandidateEvidence optimizedArtifactCandidateEvidence(
            List<GpuRuntimeIrOptimizationPassReport> passReports
    ) {
        int count = 0;
        int readyCount = 0;
        int blockedCount = 0;
        int selectionReadyCount = 0;
        int selectionAppliedCount = 0;
        int selectedIrReplacementCount = 0;
        int mutationAllowedCount = 0;
        String firstBlocker = "no-candidates";
        String selectionFirstBlocker = "no-candidates";
        for (GpuRuntimeIrOptimizationPassReport passReport : passReports) {
            if (passReport == null || passReport.proofArtifact() == null) {
                continue;
            }
            Map<String, String> fields = passReport.proofArtifact().fields();
            if (!hasOptimizedArtifactCandidate(fields)) {
                continue;
            }
            count++;
            String status = fields.getOrDefault("optimizedArtifactCandidate.status", "unknown");
            if ("candidate-ready".equals(status)) {
                readyCount++;
            } else {
                blockedCount++;
            }
            if (parseBoolean(fields.get("optimizedArtifactCandidate.selectionReady"))) {
                selectionReadyCount++;
            }
            if (parseBoolean(fields.get("optimizedArtifactCandidate.selectionApplied"))) {
                selectionAppliedCount++;
            }
            if (parseBoolean(fields.get("optimizedArtifactCandidate.selectedIrReplacement"))) {
                selectedIrReplacementCount++;
            }
            if (parseBoolean(fields.get("optimizedArtifactCandidate.mutationAllowed"))) {
                mutationAllowedCount++;
            }
            String candidateBlocker = fields.getOrDefault("optimizedArtifactCandidate.firstBlocker", "none");
            if (isPreferredCandidateBlocker(firstBlocker, candidateBlocker)) {
                firstBlocker = candidateBlocker;
            }
            String candidateSelectionBlocker = fields.getOrDefault(
                    "optimizedArtifactCandidate.selectionFirstBlocker",
                    "selection-gate-not-bound"
            );
            if (isPreferredCandidateBlocker(selectionFirstBlocker, candidateSelectionBlocker)) {
                selectionFirstBlocker = candidateSelectionBlocker;
            }
        }
        String status = optimizedArtifactCandidateStatus(count, readyCount, blockedCount);
        if (count > 0 && "no-candidates".equals(firstBlocker)) {
            firstBlocker = "none";
        }
        if (count > 0 && "no-candidates".equals(selectionFirstBlocker)) {
            selectionFirstBlocker = "selection-gate-not-bound";
        }
        return new OptimizedArtifactCandidateEvidence(
                status,
                count,
                readyCount,
                blockedCount,
                selectionReadyCount,
                selectionAppliedCount,
                selectedIrReplacementCount,
                mutationAllowedCount,
                firstBlocker,
                selectionFirstBlocker
        );
    }

    private static boolean hasOptimizedArtifactCandidate(Map<String, String> fields) {
        return fields != null && fields.keySet().stream()
                .anyMatch(key -> key.startsWith("optimizedArtifactCandidate."));
    }

    private static boolean isPreferredCandidateBlocker(String currentBlocker, String candidateBlocker) {
        if (candidateBlocker == null || candidateBlocker.isBlank()) {
            return false;
        }
        if ("no-candidates".equals(currentBlocker)) {
            return true;
        }
        return "none".equals(currentBlocker) && !"none".equals(candidateBlocker);
    }

    private static boolean isPreferredMaterializationBlocker(String currentBlocker, String candidateBlocker) {
        if (candidateBlocker == null || candidateBlocker.isBlank()) {
            return false;
        }
        if (currentBlocker == null || currentBlocker.isBlank() || "not-recorded".equals(currentBlocker)) {
            return true;
        }
        return "none".equals(currentBlocker) && !"none".equals(candidateBlocker);
    }

    private static BackendNeutralSourceMaterializationEvidence backendNeutralSourceMaterializationEvidence(
            List<GpuRuntimeIrOptimizationPassReport> passReports
    ) {
        int passCount = 0;
        int candidateCount = 0;
        int sourceReadyCount = 0;
        int sourceLengthTotal = 0;
        int materializationOnlyCount = 0;
        for (GpuRuntimeIrOptimizationPassReport passReport : passReports) {
            if (passReport == null || passReport.proofArtifact() == null) {
                continue;
            }
            Map<String, String> fields = passReport.proofArtifact().fields();
            String source = passReport.proofArtifact().source() == null ? "" : passReport.proofArtifact().source();
            String optimizerVersion = passReport.optimizerVersion() == null ? "" : passReport.optimizerVersion();
            if (!source.contains("backend-neutral-source-materialization")
                    && !optimizerVersion.contains("backend-neutral-source-materialization")) {
                continue;
            }
            passCount++;
            if (parseBoolean(fields.get("materializationOnly"))) {
                materializationOnlyCount++;
                candidateCount++;
            }
            if (parseBoolean(fields.get("sourceGenerated")) || parseBoolean(fields.get("sourceReady"))) {
                sourceReadyCount++;
            }
            sourceLengthTotal += parseNonNegativeInt(fields.get("sourceLength"));
        }
        String status;
        String firstBlocker;
        if (passCount <= 0) {
            status = "not-recorded";
            firstBlocker = "not-recorded";
        } else if (candidateCount <= 0) {
            status = "no-candidates";
            firstBlocker = "backend-neutral-source-materialization-not-needed";
        } else if (sourceReadyCount >= candidateCount) {
            status = "review-ready";
            firstBlocker = "none";
        } else if (sourceReadyCount > 0) {
            status = "mixed";
            firstBlocker = "backend-neutral-source-partially-materialized";
        } else {
            status = "blocked";
            firstBlocker = "backend-neutral-source-not-materialized";
        }
        return new BackendNeutralSourceMaterializationEvidence(
                passCount,
                candidateCount,
                sourceReadyCount,
                sourceLengthTotal,
                materializationOnlyCount,
                status,
                firstBlocker
        );
    }

    private static String optimizedArtifactCandidateStatus(int count, int readyCount, int blockedCount) {
        if (count <= 0) {
            return "not-recorded";
        }
        if (readyCount > 0 && blockedCount > 0) {
            return "mixed";
        }
        return blockedCount > 0 ? "blocked" : "candidate-ready";
    }

    private static ConstantFoldingPreviewEvidence constantFoldingPreviewEvidence(
            List<GpuRuntimeIrOptimizationPassReport> passReports
    ) {
        int passCount = 0;
        int candidateCount = 0;
        int skippedNonPlainLiteralCount = 0;
        int skippedDivideByZeroCount = 0;
        int skippedNonEvenDivisionCount = 0;
        int skippedUnsupportedOperatorCount = 0;
        int skippedNonLiteralOperandCount = 0;
        boolean runtimeEquivalenceRequiredBeforeRewrite = false;
        boolean approvalRequiredBeforeRewrite = false;
        boolean integerOverflowProven = false;
        boolean floatingPointRoundingProven = false;
        for (GpuRuntimeIrOptimizationPassReport passReport : passReports) {
            if (passReport == null || passReport.proofArtifact() == null) {
                continue;
            }
            Map<String, String> fields = passReport.proofArtifact().fields();
            String source = passReport.proofArtifact().source() == null ? "" : passReport.proofArtifact().source();
            if (!source.contains("constant-folding-preview")
                    && !passReport.optimizerVersion().contains("constant-folding-preview")) {
                continue;
            }
            passCount++;
            candidateCount += parseNonNegativeInt(fields.get("candidate.count"));
            skippedNonPlainLiteralCount += parseNonNegativeInt(fields.get("skipped.nonPlainLiteral.count"));
            skippedDivideByZeroCount += parseNonNegativeInt(fields.get("skipped.divideByZero.count"));
            skippedNonEvenDivisionCount += parseNonNegativeInt(fields.get("skipped.nonEvenDivision.count"));
            skippedUnsupportedOperatorCount += parseNonNegativeInt(fields.get("skipped.unsupportedOperator.count"));
            skippedNonLiteralOperandCount += parseNonNegativeInt(fields.get("skipped.nonLiteralOperand.count"));
            runtimeEquivalenceRequiredBeforeRewrite |= parseBoolean(fields.get("proof.runtimeEquivalenceRequiredBeforeRewrite"));
            approvalRequiredBeforeRewrite |= parseBoolean(fields.get("proof.approvalRequiredBeforeRewrite"));
            integerOverflowProven |= parseBoolean(fields.get("safety.integerOverflowProven"));
            floatingPointRoundingProven |= parseBoolean(fields.get("safety.floatingPointRoundingProven"));
        }
        return new ConstantFoldingPreviewEvidence(
                passCount,
                candidateCount,
                skippedNonPlainLiteralCount,
                skippedDivideByZeroCount,
                skippedNonEvenDivisionCount,
                skippedUnsupportedOperatorCount,
                skippedNonLiteralOperandCount,
                runtimeEquivalenceRequiredBeforeRewrite,
                approvalRequiredBeforeRewrite,
                integerOverflowProven,
                floatingPointRoundingProven
        );
    }

    private static ConstantFoldingMaterializationEvidence constantFoldingMaterializationEvidence(
            List<GpuRuntimeIrOptimizationPassReport> passReports
    ) {
        int passCount = 0;
        int candidateCount = 0;
        int transformedNodeCount = 0;
        int literalRewriteCount = 0;
        int identityRewriteCount = 0;
        int fixedPointPassCount = 0;
        int changedMethodBodyCount = 0;
        int bodyTextReplacementCount = 0;
        int skippedDivideByZeroCount = 0;
        int skippedNonEvenDivisionCount = 0;
        int runtimeEquivalencePayloadPresentCount = 0;
        int runtimeEquivalencePassedCount = 0;
        boolean runtimeEquivalenceRequiredBeforeSelection = false;
        boolean runtimeEquivalencePayloadRequired = false;
        boolean approvalRequiredBeforeProduction = false;
        String firstBlocker = "not-recorded";
        for (GpuRuntimeIrOptimizationPassReport passReport : passReports) {
            if (passReport == null || passReport.proofArtifact() == null) {
                continue;
            }
            Map<String, String> fields = passReport.proofArtifact().fields();
            String source = passReport.proofArtifact().source() == null ? "" : passReport.proofArtifact().source();
            String optimizerVersion = passReport.optimizerVersion() == null ? "" : passReport.optimizerVersion();
            if (!source.contains("constant-folding-materialization")
                    && !optimizerVersion.contains("constant-folding-materialization")) {
                continue;
            }
            passCount++;
            candidateCount += parseNonNegativeInt(fields.get("candidate.count"));
            transformedNodeCount += parseNonNegativeInt(fields.get("transformedNode.count"));
            literalRewriteCount += parseNonNegativeInt(fields.get("literalRewrite.count"));
            identityRewriteCount += parseNonNegativeInt(fields.get("identityRewrite.count"));
            fixedPointPassCount += parseNonNegativeInt(fields.get("fixedPoint.pass.count"));
            changedMethodBodyCount += parseNonNegativeInt(fields.get("changedMethodBody.count"));
            bodyTextReplacementCount += parseNonNegativeInt(fields.get("bodyTextReplacement.count"));
            skippedDivideByZeroCount += parseNonNegativeInt(fields.get("skipped.divideByZero.count"));
            skippedNonEvenDivisionCount += parseNonNegativeInt(fields.get("skipped.nonEvenDivision.count"));
            runtimeEquivalenceRequiredBeforeSelection |= parseBoolean(
                    fields.get("proof.runtimeEquivalenceRequiredBeforeSelection")
            );
            runtimeEquivalencePayloadRequired |= parseBoolean(
                    fields.get("proof.runtimeEquivalencePayloadRequiredBeforeSelection")
            ) || parseBoolean(fields.get("runtimeEquivalencePayload.required"));
            approvalRequiredBeforeProduction |= parseBoolean(fields.get("proof.approvalRequiredBeforeProduction"));
            if (parseBoolean(fields.get("runtimeEquivalencePayload.present"))) {
                runtimeEquivalencePayloadPresentCount++;
            }
            if (parseBoolean(fields.get("runtimeEquivalencePayload.passed"))) {
                runtimeEquivalencePassedCount++;
            }
            String blocker = fields.getOrDefault("runtimeEquivalencePayload.firstBlocker", "");
            if (isPreferredMaterializationBlocker(firstBlocker, blocker)) {
                firstBlocker = blocker;
            }
        }
        String status;
        if (passCount <= 0) {
            status = "not-recorded";
            firstBlocker = "not-recorded";
        } else if (transformedNodeCount <= 0) {
            status = "no-candidates";
            firstBlocker = "no-materialized-candidates";
        } else if (runtimeEquivalencePayloadPresentCount <= 0) {
            status = "pending-runtime-equivalence";
            firstBlocker = "runtime-equivalence-payload-not-recorded";
        } else if (runtimeEquivalencePassedCount < runtimeEquivalencePayloadPresentCount) {
            status = "runtime-equivalence-not-passed";
            firstBlocker = "runtime-equivalence-not-passed";
        } else {
            status = "review-ready";
            firstBlocker = "none";
        }
        return new ConstantFoldingMaterializationEvidence(
                passCount,
                candidateCount,
                transformedNodeCount,
                literalRewriteCount,
                identityRewriteCount,
                fixedPointPassCount,
                changedMethodBodyCount,
                bodyTextReplacementCount,
                skippedDivideByZeroCount,
                skippedNonEvenDivisionCount,
                runtimeEquivalenceRequiredBeforeSelection,
                runtimeEquivalencePayloadRequired,
                runtimeEquivalencePayloadPresentCount,
                runtimeEquivalencePassedCount,
                approvalRequiredBeforeProduction,
                status,
                firstBlocker
        );
    }

    private static TypedDeadCodeMaterializationEvidence typedDeadCodeMaterializationEvidence(
            List<GpuRuntimeIrOptimizationPassReport> passReports
    ) {
        int passCount = 0;
        int nodeCount = 0;
        int unreachableNodeCount = 0;
        int removedNodeCount = 0;
        int changedMethodBodyCount = 0;
        int blockedMissingRootCount = 0;
        int blockedMissingChildReferenceCount = 0;
        int blockedSideEffectingUnreachableNodeCount = 0;
        int runtimeEquivalencePayloadPresentCount = 0;
        int runtimeEquivalencePassedCount = 0;
        boolean runtimeEquivalenceRequiredBeforeSelection = false;
        boolean runtimeEquivalencePayloadRequired = false;
        boolean approvalRequiredBeforeProduction = false;
        boolean sideEffectFreedomProven = false;
        String firstBlocker = "not-recorded";
        for (GpuRuntimeIrOptimizationPassReport passReport : passReports) {
            if (passReport == null || passReport.proofArtifact() == null) {
                continue;
            }
            Map<String, String> fields = passReport.proofArtifact().fields();
            String source = passReport.proofArtifact().source() == null ? "" : passReport.proofArtifact().source();
            String optimizerVersion = passReport.optimizerVersion() == null ? "" : passReport.optimizerVersion();
            if (!source.contains("typed-dead-code-materialization")
                    && !optimizerVersion.contains("typed-dead-code-materialization")) {
                continue;
            }
            passCount++;
            nodeCount += parseNonNegativeInt(fields.get("node.count"));
            unreachableNodeCount += parseNonNegativeInt(fields.get("unreachableNode.count"));
            removedNodeCount += parseNonNegativeInt(fields.get("removedNode.count"));
            changedMethodBodyCount += parseNonNegativeInt(fields.get("changedMethodBody.count"));
            blockedMissingRootCount += parseNonNegativeInt(fields.get("blocked.missingRoot.count"));
            blockedMissingChildReferenceCount += parseNonNegativeInt(fields.get("blocked.missingChildReference.count"));
            blockedSideEffectingUnreachableNodeCount += parseNonNegativeInt(
                    fields.get("blocked.sideEffectingUnreachableNode.count")
            );
            runtimeEquivalenceRequiredBeforeSelection |= parseBoolean(
                    fields.get("proof.runtimeEquivalenceRequiredBeforeSelection")
            ) || parseBoolean(fields.get("proof.runtimeEquivalencePayloadRequiredBeforeSelection"));
            runtimeEquivalencePayloadRequired |= parseBoolean(
                    fields.get("proof.runtimeEquivalencePayloadRequiredBeforeSelection")
            ) || parseBoolean(fields.get("runtimeEquivalencePayload.required"));
            approvalRequiredBeforeProduction |= parseBoolean(fields.get("proof.approvalRequiredBeforeProduction"));
            sideEffectFreedomProven |= parseBoolean(fields.get("safety.sideEffectFreedomProven"));
            if (parseBoolean(fields.get("runtimeEquivalencePayload.present"))) {
                runtimeEquivalencePayloadPresentCount++;
            }
            if (parseBoolean(fields.get("runtimeEquivalencePayload.passed"))) {
                runtimeEquivalencePassedCount++;
            }
            String blocker = fields.getOrDefault("firstBlocker", "");
            if (isPreferredMaterializationBlocker(firstBlocker, blocker)) {
                firstBlocker = blocker;
            }
            blocker = fields.getOrDefault("runtimeEquivalencePayload.firstBlocker", "");
            if (isPreferredMaterializationBlocker(firstBlocker, blocker)) {
                firstBlocker = blocker;
            }
        }

        int blockerCount = blockedMissingRootCount
                + blockedMissingChildReferenceCount
                + blockedSideEffectingUnreachableNodeCount;
        String status;
        if (passCount <= 0) {
            status = "not-recorded";
            firstBlocker = "not-recorded";
        } else if (removedNodeCount <= 0 && blockerCount > 0) {
            status = "blocked";
            if ("not-recorded".equals(firstBlocker) || "none".equals(firstBlocker)) {
                firstBlocker = typedDeadCodeMaterializationFirstBlocker(
                        blockedMissingRootCount,
                        blockedMissingChildReferenceCount,
                        blockedSideEffectingUnreachableNodeCount
                );
            }
        } else if (removedNodeCount <= 0) {
            status = "no-candidates";
            firstBlocker = "no-materialized-candidates";
        } else if (!sideEffectFreedomProven) {
            status = "blocked";
            firstBlocker = "side-effect-freedom-not-proven";
        } else if (runtimeEquivalencePayloadPresentCount <= 0) {
            status = "pending-runtime-equivalence";
            firstBlocker = "runtime-equivalence-payload-not-recorded";
        } else if (runtimeEquivalencePassedCount < runtimeEquivalencePayloadPresentCount) {
            status = "runtime-equivalence-not-passed";
            firstBlocker = "runtime-equivalence-not-passed";
        } else {
            status = "review-ready";
            firstBlocker = "none";
        }
        return new TypedDeadCodeMaterializationEvidence(
                passCount,
                nodeCount,
                unreachableNodeCount,
                removedNodeCount,
                changedMethodBodyCount,
                blockedMissingRootCount,
                blockedMissingChildReferenceCount,
                blockedSideEffectingUnreachableNodeCount,
                runtimeEquivalenceRequiredBeforeSelection,
                runtimeEquivalencePayloadRequired,
                runtimeEquivalencePayloadPresentCount,
                runtimeEquivalencePassedCount,
                approvalRequiredBeforeProduction,
                sideEffectFreedomProven,
                status,
                firstBlocker
        );
    }

    private static String typedDeadCodeMaterializationFirstBlocker(
            int blockedMissingRootCount,
            int blockedMissingChildReferenceCount,
            int blockedSideEffectingUnreachableNodeCount
    ) {
        if (blockedMissingRootCount > 0) {
            return "missing-root-node";
        }
        if (blockedMissingChildReferenceCount > 0) {
            return "missing-child-reference";
        }
        if (blockedSideEffectingUnreachableNodeCount > 0) {
            return "side-effecting-unreachable-node";
        }
        return "typed-dead-code-materialization-blocked";
    }

    private static SafeLocalCseMaterializationEvidence safeLocalCseMaterializationEvidence(
            List<GpuRuntimeIrOptimizationPassReport> passReports
    ) {
        int passCount = 0;
        int localBindingCount = 0;
        int candidateCount = 0;
        int transformedNodeCount = 0;
        int changedMethodBodyCount = 0;
        int bodyTextReplacementCount = 0;
        int fixedPointPassCount = 0;
        int skippedControlFlowBoundaryCount = 0;
        int skippedUnsupportedOperatorCount = 0;
        int skippedImpureOperandCount = 0;
        int skippedBodyTextPatternMissingCount = 0;
        int runtimeEquivalencePayloadPresentCount = 0;
        int runtimeEquivalencePassedCount = 0;
        boolean runtimeEquivalenceRequiredBeforeSelection = false;
        boolean runtimeEquivalencePayloadRequired = false;
        boolean approvalRequiredBeforeProduction = false;
        boolean dominanceProven = false;
        boolean sideEffectFreedomProven = false;
        String firstBlocker = "not-recorded";
        for (GpuRuntimeIrOptimizationPassReport passReport : passReports) {
            if (passReport == null || passReport.proofArtifact() == null) {
                continue;
            }
            Map<String, String> fields = passReport.proofArtifact().fields();
            String source = passReport.proofArtifact().source() == null ? "" : passReport.proofArtifact().source();
            String optimizerVersion = passReport.optimizerVersion() == null ? "" : passReport.optimizerVersion();
            if (!source.contains("safe-local-cse-materialization")
                    && !optimizerVersion.contains("safe-local-cse-materialization")) {
                continue;
            }
            passCount++;
            localBindingCount += parseNonNegativeInt(fields.get("localBinding.count"));
            candidateCount += parseNonNegativeInt(fields.get("candidate.count"));
            transformedNodeCount += parseNonNegativeInt(fields.get("transformedNode.count"));
            changedMethodBodyCount += parseNonNegativeInt(fields.get("changedMethodBody.count"));
            bodyTextReplacementCount += parseNonNegativeInt(fields.get("bodyTextReplacement.count"));
            fixedPointPassCount += parseNonNegativeInt(fields.get("fixedPoint.pass.count"));
            skippedControlFlowBoundaryCount += parseNonNegativeInt(fields.get("skipped.controlFlowBoundary.count"));
            skippedUnsupportedOperatorCount += parseNonNegativeInt(fields.get("skipped.unsupportedOperator.count"));
            skippedImpureOperandCount += parseNonNegativeInt(fields.get("skipped.impureOperand.count"));
            skippedBodyTextPatternMissingCount += parseNonNegativeInt(fields.get("skipped.bodyTextPatternMissing.count"));
            runtimeEquivalenceRequiredBeforeSelection |= parseBoolean(
                    fields.get("proof.runtimeEquivalenceRequiredBeforeSelection")
            ) || parseBoolean(fields.get("proof.runtimeEquivalencePayloadRequiredBeforeSelection"));
            runtimeEquivalencePayloadRequired |= parseBoolean(
                    fields.get("proof.runtimeEquivalencePayloadRequiredBeforeSelection")
            ) || parseBoolean(fields.get("runtimeEquivalencePayload.required"));
            approvalRequiredBeforeProduction |= parseBoolean(fields.get("proof.approvalRequiredBeforeProduction"));
            dominanceProven |= parseBoolean(fields.get("safety.dominanceProven"));
            sideEffectFreedomProven |= parseBoolean(fields.get("safety.sideEffectFreedomProven"));
            if (parseBoolean(fields.get("runtimeEquivalencePayload.present"))) {
                runtimeEquivalencePayloadPresentCount++;
            }
            if (parseBoolean(fields.get("runtimeEquivalencePayload.passed"))) {
                runtimeEquivalencePassedCount++;
            }
            String blocker = fields.getOrDefault("firstBlocker", "");
            if (isPreferredMaterializationBlocker(firstBlocker, blocker)) {
                firstBlocker = blocker;
            }
            blocker = fields.getOrDefault("runtimeEquivalencePayload.firstBlocker", "");
            if (isPreferredMaterializationBlocker(firstBlocker, blocker)) {
                firstBlocker = blocker;
            }
        }

        int blockerCount = skippedControlFlowBoundaryCount
                + skippedUnsupportedOperatorCount
                + skippedImpureOperandCount
                + skippedBodyTextPatternMissingCount;
        String status;
        if (passCount <= 0) {
            status = "not-recorded";
            firstBlocker = "not-recorded";
        } else if (transformedNodeCount <= 0 && blockerCount > 0) {
            status = "blocked";
            if ("not-recorded".equals(firstBlocker) || "none".equals(firstBlocker)) {
                firstBlocker = "safe-local-cse-materialization-blocked";
            }
        } else if (transformedNodeCount <= 0) {
            status = "no-candidates";
            firstBlocker = "no-materialized-candidates";
        } else if (!dominanceProven) {
            status = "blocked";
            firstBlocker = "dominance-not-proven";
        } else if (!sideEffectFreedomProven) {
            status = "blocked";
            firstBlocker = "side-effect-freedom-not-proven";
        } else if (runtimeEquivalencePayloadPresentCount <= 0) {
            status = "pending-runtime-equivalence";
            firstBlocker = "runtime-equivalence-payload-not-recorded";
        } else if (runtimeEquivalencePassedCount < runtimeEquivalencePayloadPresentCount) {
            status = "runtime-equivalence-not-passed";
            firstBlocker = "runtime-equivalence-not-passed";
        } else {
            status = "review-ready";
            firstBlocker = "none";
        }
        return new SafeLocalCseMaterializationEvidence(
                passCount,
                localBindingCount,
                candidateCount,
                transformedNodeCount,
                changedMethodBodyCount,
                bodyTextReplacementCount,
                fixedPointPassCount,
                skippedControlFlowBoundaryCount,
                skippedUnsupportedOperatorCount,
                skippedImpureOperandCount,
                skippedBodyTextPatternMissingCount,
                runtimeEquivalenceRequiredBeforeSelection,
                runtimeEquivalencePayloadRequired,
                runtimeEquivalencePayloadPresentCount,
                runtimeEquivalencePassedCount,
                approvalRequiredBeforeProduction,
                dominanceProven,
                sideEffectFreedomProven,
                status,
                firstBlocker
        );
    }

    private static MadFmaMaterializationEvidence madFmaMaterializationEvidence(
            List<GpuRuntimeIrOptimizationPassReport> passReports
    ) {
        int passCount = 0;
        int candidateCount = 0;
        int transformedNodeCount = 0;
        int changedMethodBodyCount = 0;
        int bodyTextReplacementCount = 0;
        int fixedPointPassCount = 0;
        int skippedFastMathPolicyCount = 0;
        int skippedBodyTextPatternMissingCount = 0;
        int runtimeEquivalencePayloadPresentCount = 0;
        int runtimeEquivalencePassedCount = 0;
        boolean runtimeEquivalenceRequiredBeforeSelection = false;
        boolean runtimeEquivalencePayloadRequired = false;
        boolean approvalRequiredBeforeProduction = false;
        boolean fastMathAllowed = false;
        String firstBlocker = "not-recorded";
        for (GpuRuntimeIrOptimizationPassReport passReport : passReports) {
            if (passReport == null || passReport.proofArtifact() == null) {
                continue;
            }
            Map<String, String> fields = passReport.proofArtifact().fields();
            String source = passReport.proofArtifact().source() == null ? "" : passReport.proofArtifact().source();
            String optimizerVersion = passReport.optimizerVersion() == null ? "" : passReport.optimizerVersion();
            if (!source.contains("mad-fma-materialization")
                    && !optimizerVersion.contains("mad-fma-materialization")) {
                continue;
            }
            passCount++;
            candidateCount += parseNonNegativeInt(fields.get("candidate.count"));
            transformedNodeCount += parseNonNegativeInt(fields.get("transformedNode.count"));
            changedMethodBodyCount += parseNonNegativeInt(fields.get("changedMethodBody.count"));
            bodyTextReplacementCount += parseNonNegativeInt(fields.get("bodyTextReplacement.count"));
            fixedPointPassCount += parseNonNegativeInt(fields.get("fixedPoint.pass.count"));
            skippedFastMathPolicyCount += parseNonNegativeInt(fields.get("skipped.fastMathPolicy.count"));
            skippedBodyTextPatternMissingCount += parseNonNegativeInt(fields.get("skipped.bodyTextPatternMissing.count"));
            runtimeEquivalenceRequiredBeforeSelection |= parseBoolean(
                    fields.get("proof.runtimeEquivalenceRequiredBeforeSelection")
            ) || parseBoolean(fields.get("proof.runtimeEquivalencePayloadRequiredBeforeSelection"));
            runtimeEquivalencePayloadRequired |= parseBoolean(
                    fields.get("proof.runtimeEquivalencePayloadRequiredBeforeSelection")
            ) || parseBoolean(fields.get("runtimeEquivalencePayload.required"));
            approvalRequiredBeforeProduction |= parseBoolean(fields.get("proof.approvalRequiredBeforeProduction"));
            fastMathAllowed |= parseBoolean(fields.get("policy.fastMathAllowed"))
                    || parseBoolean(fields.get("safety.fastMathAllowed"));
            if (parseBoolean(fields.get("runtimeEquivalencePayload.present"))) {
                runtimeEquivalencePayloadPresentCount++;
            }
            if (parseBoolean(fields.get("runtimeEquivalencePayload.passed"))) {
                runtimeEquivalencePassedCount++;
            }
            String blocker = fields.getOrDefault("firstBlocker", "");
            if (isPreferredMaterializationBlocker(firstBlocker, blocker)) {
                firstBlocker = blocker;
            }
            blocker = fields.getOrDefault("runtimeEquivalencePayload.firstBlocker", "");
            if (isPreferredMaterializationBlocker(firstBlocker, blocker)) {
                firstBlocker = blocker;
            }
        }

        int blockerCount = skippedFastMathPolicyCount + skippedBodyTextPatternMissingCount;
        String status;
        if (passCount <= 0) {
            status = "not-recorded";
            firstBlocker = "not-recorded";
        } else if (transformedNodeCount <= 0 && blockerCount > 0) {
            status = "blocked";
            if ("not-recorded".equals(firstBlocker) || "none".equals(firstBlocker)) {
                firstBlocker = madFmaMaterializationFirstBlocker(
                        skippedFastMathPolicyCount,
                        skippedBodyTextPatternMissingCount
                );
            }
        } else if (transformedNodeCount <= 0) {
            status = "no-candidates";
            firstBlocker = "no-materialized-candidates";
        } else if (!fastMathAllowed) {
            status = "blocked";
            firstBlocker = "fast-math-policy-not-enabled";
        } else if (runtimeEquivalencePayloadPresentCount <= 0) {
            status = "pending-runtime-equivalence";
            firstBlocker = "runtime-equivalence-payload-not-recorded";
        } else if (runtimeEquivalencePassedCount < runtimeEquivalencePayloadPresentCount) {
            status = "runtime-equivalence-not-passed";
            firstBlocker = "runtime-equivalence-not-passed";
        } else {
            status = "review-ready";
            firstBlocker = "none";
        }
        return new MadFmaMaterializationEvidence(
                passCount,
                candidateCount,
                transformedNodeCount,
                changedMethodBodyCount,
                bodyTextReplacementCount,
                fixedPointPassCount,
                skippedFastMathPolicyCount,
                skippedBodyTextPatternMissingCount,
                runtimeEquivalenceRequiredBeforeSelection,
                runtimeEquivalencePayloadRequired,
                runtimeEquivalencePayloadPresentCount,
                runtimeEquivalencePassedCount,
                approvalRequiredBeforeProduction,
                fastMathAllowed,
                status,
                firstBlocker
        );
    }

    private static String madFmaMaterializationFirstBlocker(
            int skippedFastMathPolicyCount,
            int skippedBodyTextPatternMissingCount
    ) {
        if (skippedFastMathPolicyCount > 0) {
            return "fast-math-policy-not-enabled";
        }
        if (skippedBodyTextPatternMissingCount > 0) {
            return "body-text-pattern-missing";
        }
        return "mad-fma-materialization-blocked";
    }

    private static void appendIntrinsicMaterializationEvidence(
            StringBuilder builder,
            String prefix,
            IntrinsicMaterializationEvidence evidence
    ) {
        builder.append(prefix).append(".pass.count=").append(evidence.passCount()).append('\n');
        builder.append(prefix).append(".candidate.count=").append(evidence.candidateCount()).append('\n');
        builder.append(prefix).append(".transformedNode.count=").append(evidence.transformedNodeCount()).append('\n');
        builder.append(prefix).append(".changedMethodBody.count=")
                .append(evidence.changedMethodBodyCount()).append('\n');
        builder.append(prefix).append(".bodyTextReplacement.count=")
                .append(evidence.bodyTextReplacementCount()).append('\n');
        builder.append(prefix).append(".fixedPoint.pass.count=")
                .append(evidence.fixedPointPassCount()).append('\n');
        builder.append(prefix).append(".skipped.typedBodyMissing.count=")
                .append(evidence.skippedTypedBodyMissingCount()).append('\n');
        builder.append(prefix).append(".skipped.unsupportedFormat.count=")
                .append(evidence.skippedUnsupportedFormatCount()).append('\n');
        builder.append(prefix).append(".skipped.fastMathPolicy.count=")
                .append(evidence.skippedFastMathPolicyCount()).append('\n');
        builder.append(prefix).append(".skipped.missingChildReference.count=")
                .append(evidence.skippedMissingChildReferenceCount()).append('\n');
        builder.append(prefix).append(".skipped.unsupportedShape.count=")
                .append(evidence.skippedUnsupportedShapeCount()).append('\n');
        builder.append(prefix).append(".skipped.bodyTextPatternMissing.count=")
                .append(evidence.skippedBodyTextPatternMissingCount()).append('\n');
        builder.append(prefix).append(".runtimeEquivalenceRequiredBeforeSelection=")
                .append(evidence.runtimeEquivalenceRequiredBeforeSelection()).append('\n');
        builder.append(prefix).append(".runtimeEquivalencePayloadRequired=")
                .append(evidence.runtimeEquivalencePayloadRequired()).append('\n');
        builder.append(prefix).append(".runtimeEquivalencePayloadPresent.count=")
                .append(evidence.runtimeEquivalencePayloadPresentCount()).append('\n');
        builder.append(prefix).append(".runtimeEquivalencePassed.count=")
                .append(evidence.runtimeEquivalencePassedCount()).append('\n');
        builder.append(prefix).append(".approvalRequiredBeforeProduction=")
                .append(evidence.approvalRequiredBeforeProduction()).append('\n');
        builder.append(prefix).append(".status=").append(evidence.status()).append('\n');
        builder.append(prefix).append(".firstBlocker=")
                .append(safePropertyValue(evidence.firstBlocker())).append('\n');
    }

    private static IntrinsicMaterializationEvidence intrinsicMaterializationEvidence(
            List<GpuRuntimeIrOptimizationPassReport> passReports,
            String familyToken
    ) {
        int passCount = 0;
        int candidateCount = 0;
        int transformedNodeCount = 0;
        int changedMethodBodyCount = 0;
        int bodyTextReplacementCount = 0;
        int fixedPointPassCount = 0;
        int skippedTypedBodyMissingCount = 0;
        int skippedUnsupportedFormatCount = 0;
        int skippedFastMathPolicyCount = 0;
        int skippedMissingChildReferenceCount = 0;
        int skippedUnsupportedShapeCount = 0;
        int skippedBodyTextPatternMissingCount = 0;
        int runtimeEquivalencePayloadPresentCount = 0;
        int runtimeEquivalencePassedCount = 0;
        int directStepCount = 0;
        int invertedStepCount = 0;
        int canonicalMixCount = 0;
        int expandedMixCount = 0;
        int madExpandedMixCount = 0;
        boolean runtimeEquivalenceRequiredBeforeSelection = false;
        boolean runtimeEquivalencePayloadRequired = false;
        boolean approvalRequiredBeforeProduction = false;
        boolean fastMathAllowed = false;
        boolean fastMathRequired = false;
        boolean strictFloatPreserved = false;
        boolean argumentOrderPreserved = false;
        boolean strictComparisonPreserved = false;
        boolean equalityBehaviorPreserved = false;
        boolean nanComparisonPreserved = false;
        boolean algebraicReassociationRequired = false;
        boolean mixArgumentOrderPreserved = false;
        String firstBlocker = "not-recorded";
        for (GpuRuntimeIrOptimizationPassReport passReport : passReports) {
            if (passReport == null || passReport.proofArtifact() == null) {
                continue;
            }
            Map<String, String> fields = passReport.proofArtifact().fields();
            String source = passReport.proofArtifact().source() == null ? "" : passReport.proofArtifact().source();
            String optimizerVersion = passReport.optimizerVersion() == null ? "" : passReport.optimizerVersion();
            String optimizerFamily = fields.getOrDefault("optimizerFamily", "");
            if (!familyToken.equals(optimizerFamily)
                    && !source.contains(familyToken)
                    && !optimizerVersion.contains(familyToken)) {
                continue;
            }
            passCount++;
            candidateCount += parseNonNegativeInt(fields.get("candidate.count"));
            transformedNodeCount += parseNonNegativeInt(fields.get("transformedNode.count"));
            changedMethodBodyCount += parseNonNegativeInt(fields.get("changedMethodBody.count"));
            bodyTextReplacementCount += parseNonNegativeInt(fields.get("bodyTextReplacement.count"));
            fixedPointPassCount += parseNonNegativeInt(fields.get("fixedPoint.pass.count"));
            skippedTypedBodyMissingCount += parseNonNegativeInt(fields.get("skipped.typedBodyMissing.count"));
            skippedUnsupportedFormatCount += parseNonNegativeInt(fields.get("skipped.unsupportedFormat.count"));
            skippedFastMathPolicyCount += parseNonNegativeInt(fields.get("skipped.fastMathPolicy.count"));
            skippedMissingChildReferenceCount += parseNonNegativeInt(fields.get("skipped.missingChildReference.count"));
            skippedUnsupportedShapeCount += parseNonNegativeInt(fields.get("skipped.unsupportedShape.count"));
            skippedBodyTextPatternMissingCount += parseNonNegativeInt(fields.get("skipped.bodyTextPatternMissing.count"));
            directStepCount += parseNonNegativeInt(fields.get("directStep.count"));
            invertedStepCount += parseNonNegativeInt(fields.get("invertedStep.count"));
            canonicalMixCount += parseNonNegativeInt(fields.get("canonicalMix.count"));
            expandedMixCount += parseNonNegativeInt(fields.get("expandedMix.count"));
            madExpandedMixCount += parseNonNegativeInt(fields.get("madExpandedMix.count"));
            runtimeEquivalenceRequiredBeforeSelection |= parseBoolean(
                    fields.get("proof.runtimeEquivalenceRequiredBeforeSelection")
            ) || parseBoolean(fields.get("proof.runtimeEquivalencePayloadRequiredBeforeSelection"));
            runtimeEquivalencePayloadRequired |= parseBoolean(
                    fields.get("proof.runtimeEquivalencePayloadRequiredBeforeSelection")
            ) || parseBoolean(fields.get("runtimeEquivalencePayload.required"));
            approvalRequiredBeforeProduction |= parseBoolean(fields.get("proof.approvalRequiredBeforeProduction"));
            fastMathAllowed |= parseBoolean(fields.get("policy.fastMathAllowed"))
                    || parseBoolean(fields.get("safety.fastMathAllowed"));
            fastMathRequired |= parseBoolean(fields.get("safety.fastMathRequired"));
            strictFloatPreserved |= parseBoolean(fields.get("safety.strictFloatPreserved"));
            argumentOrderPreserved |= parseBoolean(fields.get("safety.argumentOrderPreserved"));
            strictComparisonPreserved |= parseBoolean(fields.get("safety.strictComparisonPreserved"));
            equalityBehaviorPreserved |= parseBoolean(fields.get("safety.equalityBehaviorPreserved"));
            nanComparisonPreserved |= parseBoolean(fields.get("safety.nanComparisonPreserved"));
            algebraicReassociationRequired |= parseBoolean(fields.get("safety.algebraicReassociationRequired"));
            mixArgumentOrderPreserved |= parseBoolean(fields.get("safety.mixArgumentOrderPreserved"));
            if (parseBoolean(fields.get("runtimeEquivalencePayload.present"))) {
                runtimeEquivalencePayloadPresentCount++;
            }
            if (parseBoolean(fields.get("runtimeEquivalencePayload.passed"))) {
                runtimeEquivalencePassedCount++;
            }
            String blocker = fields.getOrDefault("firstBlocker", "");
            if (isPreferredMaterializationBlocker(firstBlocker, blocker)) {
                firstBlocker = blocker;
            }
            blocker = fields.getOrDefault("runtimeEquivalencePayload.firstBlocker", "");
            if (isPreferredMaterializationBlocker(firstBlocker, blocker)) {
                firstBlocker = blocker;
            }
        }

        int blockerCount = skippedTypedBodyMissingCount
                + skippedUnsupportedFormatCount
                + skippedFastMathPolicyCount
                + skippedMissingChildReferenceCount
                + skippedUnsupportedShapeCount
                + skippedBodyTextPatternMissingCount;
        String status;
        if (passCount <= 0) {
            status = "not-recorded";
            firstBlocker = "not-recorded";
        } else if (transformedNodeCount <= 0 && blockerCount > 0) {
            status = "blocked";
            if ("not-recorded".equals(firstBlocker) || "none".equals(firstBlocker)) {
                firstBlocker = intrinsicMaterializationFirstBlocker(
                        familyToken,
                        skippedTypedBodyMissingCount,
                        skippedUnsupportedFormatCount,
                        skippedFastMathPolicyCount,
                        skippedMissingChildReferenceCount,
                        skippedUnsupportedShapeCount,
                        skippedBodyTextPatternMissingCount
                );
            }
        } else if (transformedNodeCount <= 0) {
            status = "no-candidates";
            firstBlocker = "no-materialized-candidates";
        } else {
            String safetyBlocker = intrinsicMaterializationSafetyBlocker(
                    familyToken,
                    fastMathAllowed,
                    fastMathRequired,
                    strictFloatPreserved,
                    argumentOrderPreserved,
                    strictComparisonPreserved,
                    equalityBehaviorPreserved,
                    nanComparisonPreserved,
                    algebraicReassociationRequired,
                    mixArgumentOrderPreserved,
                    expandedMixCount,
                    madExpandedMixCount
            );
            if (!"none".equals(safetyBlocker)) {
                status = "blocked";
                firstBlocker = safetyBlocker;
            } else if (runtimeEquivalencePayloadPresentCount <= 0) {
                status = "pending-runtime-equivalence";
                firstBlocker = "runtime-equivalence-payload-not-recorded";
            } else if (runtimeEquivalencePassedCount < runtimeEquivalencePayloadPresentCount) {
                status = "runtime-equivalence-not-passed";
                firstBlocker = "runtime-equivalence-not-passed";
            } else {
                status = "review-ready";
                firstBlocker = "none";
            }
        }
        return new IntrinsicMaterializationEvidence(
                passCount,
                candidateCount,
                transformedNodeCount,
                changedMethodBodyCount,
                bodyTextReplacementCount,
                fixedPointPassCount,
                skippedTypedBodyMissingCount,
                skippedUnsupportedFormatCount,
                skippedFastMathPolicyCount,
                skippedMissingChildReferenceCount,
                skippedUnsupportedShapeCount,
                skippedBodyTextPatternMissingCount,
                runtimeEquivalenceRequiredBeforeSelection,
                runtimeEquivalencePayloadRequired,
                runtimeEquivalencePayloadPresentCount,
                runtimeEquivalencePassedCount,
                approvalRequiredBeforeProduction,
                directStepCount,
                invertedStepCount,
                canonicalMixCount,
                expandedMixCount,
                madExpandedMixCount,
                fastMathAllowed,
                fastMathRequired,
                strictFloatPreserved,
                argumentOrderPreserved,
                strictComparisonPreserved,
                equalityBehaviorPreserved,
                nanComparisonPreserved,
                algebraicReassociationRequired,
                mixArgumentOrderPreserved,
                status,
                firstBlocker
        );
    }

    private static String intrinsicMaterializationFirstBlocker(
            String familyToken,
            int skippedTypedBodyMissingCount,
            int skippedUnsupportedFormatCount,
            int skippedFastMathPolicyCount,
            int skippedMissingChildReferenceCount,
            int skippedUnsupportedShapeCount,
            int skippedBodyTextPatternMissingCount
    ) {
        if (skippedTypedBodyMissingCount > 0) {
            return "typed-body-missing";
        }
        if (skippedUnsupportedFormatCount > 0) {
            return "unsupported-format";
        }
        if (skippedFastMathPolicyCount > 0) {
            return "fast-math-policy-not-enabled";
        }
        if (skippedMissingChildReferenceCount > 0) {
            return "missing-child-reference";
        }
        if (skippedUnsupportedShapeCount > 0) {
            return "unsupported-shape";
        }
        if (skippedBodyTextPatternMissingCount > 0) {
            return "body-text-pattern-missing";
        }
        return familyToken + "-blocked";
    }

    private static String intrinsicMaterializationSafetyBlocker(
            String familyToken,
            boolean fastMathAllowed,
            boolean fastMathRequired,
            boolean strictFloatPreserved,
            boolean argumentOrderPreserved,
            boolean strictComparisonPreserved,
            boolean equalityBehaviorPreserved,
            boolean nanComparisonPreserved,
            boolean algebraicReassociationRequired,
            boolean mixArgumentOrderPreserved,
            int expandedMixCount,
            int madExpandedMixCount
    ) {
        return switch (familyToken) {
            case "clamp-materialization" -> clampMaterializationSafetyBlocker(
                    fastMathRequired,
                    strictFloatPreserved,
                    argumentOrderPreserved
            );
            case "step-materialization" -> stepMaterializationSafetyBlocker(
                    fastMathRequired,
                    strictFloatPreserved,
                    strictComparisonPreserved,
                    equalityBehaviorPreserved,
                    nanComparisonPreserved
            );
            case "mix-materialization" -> mixMaterializationSafetyBlocker(
                    fastMathAllowed,
                    fastMathRequired,
                    strictFloatPreserved,
                    algebraicReassociationRequired,
                    mixArgumentOrderPreserved,
                    expandedMixCount,
                    madExpandedMixCount
            );
            default -> "none";
        };
    }

    private static String clampMaterializationSafetyBlocker(
            boolean fastMathRequired,
            boolean strictFloatPreserved,
            boolean argumentOrderPreserved
    ) {
        if (fastMathRequired) {
            return "unexpected-fast-math-requirement";
        }
        if (!strictFloatPreserved) {
            return "strict-float-proof-not-recorded";
        }
        if (!argumentOrderPreserved) {
            return "argument-order-not-preserved";
        }
        return "none";
    }

    private static String stepMaterializationSafetyBlocker(
            boolean fastMathRequired,
            boolean strictFloatPreserved,
            boolean strictComparisonPreserved,
            boolean equalityBehaviorPreserved,
            boolean nanComparisonPreserved
    ) {
        if (fastMathRequired) {
            return "unexpected-fast-math-requirement";
        }
        if (!strictFloatPreserved) {
            return "strict-float-proof-not-recorded";
        }
        if (!strictComparisonPreserved) {
            return "strict-comparison-proof-not-recorded";
        }
        if (!equalityBehaviorPreserved) {
            return "equality-behavior-proof-not-recorded";
        }
        if (!nanComparisonPreserved) {
            return "nan-comparison-proof-not-recorded";
        }
        return "none";
    }

    private static String mixMaterializationSafetyBlocker(
            boolean fastMathAllowed,
            boolean fastMathRequired,
            boolean strictFloatPreserved,
            boolean algebraicReassociationRequired,
            boolean mixArgumentOrderPreserved,
            int expandedMixCount,
            int madExpandedMixCount
    ) {
        boolean expandedRewrite = fastMathRequired
                || algebraicReassociationRequired
                || expandedMixCount > 0
                || madExpandedMixCount > 0;
        if (!mixArgumentOrderPreserved) {
            return "mix-argument-order-not-preserved";
        }
        if (expandedRewrite && !fastMathAllowed) {
            return "fast-math-policy-not-enabled";
        }
        if (!expandedRewrite && !strictFloatPreserved) {
            return "strict-float-proof-not-recorded";
        }
        return "none";
    }

    private static LoopVectorizationMaterializationEvidence loopVectorizationMaterializationEvidence(
            List<GpuRuntimeIrOptimizationPassReport> passReports
    ) {
        int passCount = 0;
        int candidateCount = 0;
        int transformedLoopCount = 0;
        int changedMethodBodyCount = 0;
        int bodyTextReplacementCount = 0;
        int typedBodyMaterializedCount = 0;
        int typedBodyInvalidatedCount = 0;
        int skippedLoopShapeCount = 0;
        int skippedUnsupportedWidthCount = 0;
        int skippedUnsafeLoadPatternCount = 0;
        int runtimeEquivalencePayloadPresentCount = 0;
        int runtimeEquivalencePassedCount = 0;
        boolean runtimeEquivalenceRequiredBeforeSelection = false;
        boolean runtimeEquivalencePayloadRequired = false;
        boolean approvalRequiredBeforeProduction = false;
        boolean loopTripCountProven = false;
        boolean contiguousLoadProven = false;
        boolean orderedReductionPreserved = false;
        String firstBlocker = "not-recorded";
        for (GpuRuntimeIrOptimizationPassReport passReport : passReports) {
            if (passReport == null || passReport.proofArtifact() == null) {
                continue;
            }
            Map<String, String> fields = passReport.proofArtifact().fields();
            String source = passReport.proofArtifact().source() == null ? "" : passReport.proofArtifact().source();
            String optimizerVersion = passReport.optimizerVersion() == null ? "" : passReport.optimizerVersion();
            if (!source.contains("loop-vectorization-materialization")
                    && !optimizerVersion.contains("loop-vectorization-materialization")) {
                continue;
            }
            passCount++;
            candidateCount += parseNonNegativeInt(fields.get("candidate.count"));
            transformedLoopCount += parseNonNegativeInt(fields.get("transformedLoop.count"));
            changedMethodBodyCount += parseNonNegativeInt(fields.get("changedMethodBody.count"));
            bodyTextReplacementCount += parseNonNegativeInt(fields.get("bodyTextReplacement.count"));
            typedBodyMaterializedCount += parseNonNegativeInt(fields.get("typedBody.materialized.count"));
            typedBodyInvalidatedCount += parseNonNegativeInt(fields.get("typedBody.invalidated.count"));
            skippedLoopShapeCount += parseNonNegativeInt(fields.get("skipped.loopShape.count"));
            skippedUnsupportedWidthCount += parseNonNegativeInt(fields.get("skipped.unsupportedWidth.count"));
            skippedUnsafeLoadPatternCount += parseNonNegativeInt(fields.get("skipped.unsafeLoadPattern.count"));
            runtimeEquivalenceRequiredBeforeSelection |= parseBoolean(
                    fields.get("proof.runtimeEquivalenceRequiredBeforeSelection")
            ) || parseBoolean(fields.get("proof.runtimeEquivalencePayloadRequiredBeforeSelection"));
            runtimeEquivalencePayloadRequired |= parseBoolean(
                    fields.get("proof.runtimeEquivalencePayloadRequiredBeforeSelection")
            ) || parseBoolean(fields.get("runtimeEquivalencePayload.required"));
            approvalRequiredBeforeProduction |= parseBoolean(fields.get("proof.approvalRequiredBeforeProduction"));
            loopTripCountProven |= parseBoolean(fields.get("safety.loopTripCountProven"));
            contiguousLoadProven |= parseBoolean(fields.get("safety.contiguousLoadProven"));
            orderedReductionPreserved |= parseBoolean(fields.get("safety.orderedReductionPreserved"));
            if (parseBoolean(fields.get("runtimeEquivalencePayload.present"))) {
                runtimeEquivalencePayloadPresentCount++;
            }
            if (parseBoolean(fields.get("runtimeEquivalencePayload.passed"))) {
                runtimeEquivalencePassedCount++;
            }
            String blocker = fields.getOrDefault("firstBlocker", "");
            if (isPreferredMaterializationBlocker(firstBlocker, blocker)) {
                firstBlocker = blocker;
            }
            blocker = fields.getOrDefault("runtimeEquivalencePayload.firstBlocker", "");
            if (isPreferredMaterializationBlocker(firstBlocker, blocker)) {
                firstBlocker = blocker;
            }
        }

        int blockerCount = skippedLoopShapeCount + skippedUnsupportedWidthCount + skippedUnsafeLoadPatternCount;
        String status;
        if (passCount <= 0) {
            status = "not-recorded";
            firstBlocker = "not-recorded";
        } else if (transformedLoopCount <= 0 && blockerCount > 0) {
            status = "blocked";
            if ("not-recorded".equals(firstBlocker) || "none".equals(firstBlocker)) {
                firstBlocker = loopVectorizationMaterializationFirstBlocker(
                        skippedLoopShapeCount,
                        skippedUnsupportedWidthCount,
                        skippedUnsafeLoadPatternCount
                );
            }
        } else if (transformedLoopCount <= 0) {
            status = "no-candidates";
            firstBlocker = "no-materialized-candidates";
        } else if (!loopTripCountProven || !contiguousLoadProven || !orderedReductionPreserved) {
            status = "blocked";
            firstBlocker = "loop-vectorization-proof-incomplete";
        } else if (runtimeEquivalencePayloadPresentCount <= 0) {
            status = "pending-runtime-equivalence";
            firstBlocker = "runtime-equivalence-payload-not-recorded";
        } else if (runtimeEquivalencePassedCount < runtimeEquivalencePayloadPresentCount) {
            status = "runtime-equivalence-not-passed";
            firstBlocker = "runtime-equivalence-not-passed";
        } else {
            status = "review-ready";
            firstBlocker = "none";
        }
        return new LoopVectorizationMaterializationEvidence(
                passCount,
                candidateCount,
                transformedLoopCount,
                changedMethodBodyCount,
                bodyTextReplacementCount,
                typedBodyMaterializedCount,
                typedBodyInvalidatedCount,
                skippedLoopShapeCount,
                skippedUnsupportedWidthCount,
                skippedUnsafeLoadPatternCount,
                runtimeEquivalenceRequiredBeforeSelection,
                runtimeEquivalencePayloadRequired,
                runtimeEquivalencePayloadPresentCount,
                runtimeEquivalencePassedCount,
                approvalRequiredBeforeProduction,
                loopTripCountProven,
                contiguousLoadProven,
                orderedReductionPreserved,
                status,
                firstBlocker
        );
    }

    private static String loopVectorizationMaterializationFirstBlocker(
            int skippedLoopShapeCount,
            int skippedUnsupportedWidthCount,
            int skippedUnsafeLoadPatternCount
    ) {
        if (skippedUnsupportedWidthCount > 0) {
            return "loop-width-unsupported";
        }
        if (skippedUnsafeLoadPatternCount > 0) {
            return "load-pattern-not-contiguous-float-reduction";
        }
        if (skippedLoopShapeCount > 0) {
            return "loop-shape-unsupported";
        }
        return "loop-vectorization-materialization-blocked";
    }

    private static SafeLocalCsePreviewEvidence safeLocalCsePreviewEvidence(
            List<GpuRuntimeIrOptimizationPassReport> passReports
    ) {
        int passCount = 0;
        int expressionCount = 0;
        int candidateExpressionCount = 0;
        int duplicateExpressionCount = 0;
        int equivalenceClassCount = 0;
        int blockedUnsupportedOperatorCount = 0;
        int blockedImpureOperandCount = 0;
        int blockedControlFlowBoundaryCount = 0;
        boolean runtimeEquivalenceRequiredBeforeRewrite = false;
        boolean approvalRequiredBeforeRewrite = false;
        boolean dominanceProven = false;
        boolean sideEffectFreedomProven = false;
        for (GpuRuntimeIrOptimizationPassReport passReport : passReports) {
            if (passReport == null || passReport.proofArtifact() == null) {
                continue;
            }
            Map<String, String> fields = passReport.proofArtifact().fields();
            String source = passReport.proofArtifact().source() == null ? "" : passReport.proofArtifact().source();
            String optimizerVersion = passReport.optimizerVersion() == null ? "" : passReport.optimizerVersion();
            if (!source.contains("safe-local-cse-preview")
                    && !optimizerVersion.contains("safe-local-cse-preview")) {
                continue;
            }
            passCount++;
            expressionCount += parseNonNegativeInt(fields.get("expression.count"));
            candidateExpressionCount += parseNonNegativeInt(fields.get("candidateExpression.count"));
            duplicateExpressionCount += parseNonNegativeInt(fields.get("duplicateExpression.count"));
            equivalenceClassCount += parseNonNegativeInt(fields.get("equivalenceClass.count"));
            blockedUnsupportedOperatorCount += parseNonNegativeInt(fields.get("blocked.unsupportedOperator.count"));
            blockedImpureOperandCount += parseNonNegativeInt(fields.get("blocked.impureOperand.count"));
            blockedControlFlowBoundaryCount += parseNonNegativeInt(fields.get("blocked.controlFlowBoundary.count"));
            runtimeEquivalenceRequiredBeforeRewrite |= parseBoolean(fields.get("proof.runtimeEquivalenceRequiredBeforeRewrite"));
            approvalRequiredBeforeRewrite |= parseBoolean(fields.get("proof.approvalRequiredBeforeRewrite"));
            dominanceProven |= parseBoolean(fields.get("safety.dominanceProven"));
            sideEffectFreedomProven |= parseBoolean(fields.get("safety.sideEffectFreedomProven"));
        }
        return new SafeLocalCsePreviewEvidence(
                passCount,
                expressionCount,
                candidateExpressionCount,
                duplicateExpressionCount,
                equivalenceClassCount,
                blockedUnsupportedOperatorCount,
                blockedImpureOperandCount,
                blockedControlFlowBoundaryCount,
                runtimeEquivalenceRequiredBeforeRewrite,
                approvalRequiredBeforeRewrite,
                dominanceProven,
                sideEffectFreedomProven
        );
    }

    private static TypedDeadCodePreviewEvidence typedDeadCodePreviewEvidence(
            List<GpuRuntimeIrOptimizationPassReport> passReports
    ) {
        int passCount = 0;
        int nodeCount = 0;
        int reachableNodeCount = 0;
        int unreachableNodeCount = 0;
        int blockedMissingRootCount = 0;
        int blockedMissingChildReferenceCount = 0;
        int blockedSideEffectingUnreachableNodeCount = 0;
        boolean runtimeEquivalenceRequiredBeforeRewrite = false;
        boolean approvalRequiredBeforeRewrite = false;
        boolean sideEffectFreedomProven = false;
        for (GpuRuntimeIrOptimizationPassReport passReport : passReports) {
            if (passReport == null || passReport.proofArtifact() == null) {
                continue;
            }
            Map<String, String> fields = passReport.proofArtifact().fields();
            String source = passReport.proofArtifact().source() == null ? "" : passReport.proofArtifact().source();
            String optimizerVersion = passReport.optimizerVersion() == null ? "" : passReport.optimizerVersion();
            if (!source.contains("typed-dead-code-preview")
                    && !optimizerVersion.contains("typed-dead-code-preview")) {
                continue;
            }
            passCount++;
            nodeCount += parseNonNegativeInt(fields.get("node.count"));
            reachableNodeCount += parseNonNegativeInt(fields.get("reachableNode.count"));
            unreachableNodeCount += parseNonNegativeInt(fields.get("unreachableNode.count"));
            blockedMissingRootCount += parseNonNegativeInt(fields.get("blocked.missingRoot.count"));
            blockedMissingChildReferenceCount += parseNonNegativeInt(fields.get("blocked.missingChildReference.count"));
            blockedSideEffectingUnreachableNodeCount += parseNonNegativeInt(fields.get("blocked.sideEffectingUnreachableNode.count"));
            runtimeEquivalenceRequiredBeforeRewrite |= parseBoolean(fields.get("proof.runtimeEquivalenceRequiredBeforeRewrite"));
            approvalRequiredBeforeRewrite |= parseBoolean(fields.get("proof.approvalRequiredBeforeRewrite"));
            sideEffectFreedomProven |= parseBoolean(fields.get("safety.sideEffectFreedomProven"));
        }
        return new TypedDeadCodePreviewEvidence(
                passCount,
                nodeCount,
                reachableNodeCount,
                unreachableNodeCount,
                blockedMissingRootCount,
                blockedMissingChildReferenceCount,
                blockedSideEffectingUnreachableNodeCount,
                runtimeEquivalenceRequiredBeforeRewrite,
                approvalRequiredBeforeRewrite,
                sideEffectFreedomProven
        );
    }

    private static PreviewReadinessEvidence previewReadinessEvidence(
            ConstantFoldingPreviewEvidence constantFoldingPreview,
            SafeLocalCsePreviewEvidence safeLocalCsePreview,
            TypedDeadCodePreviewEvidence typedDeadCodePreview
    ) {
        PreviewFamilyReadiness constantFolding = new PreviewFamilyReadiness(
                "constant-folding",
                previewFamilyStatus(
                        constantFoldingPreview.passCount(),
                        constantFoldingPreview.candidateCount(),
                        constantFoldingPreview.skippedCount() + constantFoldingPreview.proofBlockerCount()
                ),
                constantFoldingPreview.candidateCount(),
                constantFoldingPreview.skippedCount() + constantFoldingPreview.proofBlockerCount()
        );
        PreviewFamilyReadiness safeLocalCse = new PreviewFamilyReadiness(
                "safe-local-cse",
                previewFamilyStatus(
                        safeLocalCsePreview.passCount(),
                        safeLocalCsePreview.duplicateExpressionCount(),
                        safeLocalCsePreview.blockedCount() + safeLocalCsePreview.proofBlockerCount()
                ),
                safeLocalCsePreview.duplicateExpressionCount(),
                safeLocalCsePreview.blockedCount() + safeLocalCsePreview.proofBlockerCount()
        );
        PreviewFamilyReadiness typedDeadCode = new PreviewFamilyReadiness(
                "typed-dead-code",
                previewFamilyStatus(
                        typedDeadCodePreview.passCount(),
                        typedDeadCodePreview.unreachableNodeCount(),
                        typedDeadCodePreview.blockedCount() + typedDeadCodePreview.proofBlockerCount()
                ),
                typedDeadCodePreview.unreachableNodeCount(),
                typedDeadCodePreview.blockedCount() + typedDeadCodePreview.proofBlockerCount()
        );
        List<PreviewFamilyReadiness> families = List.of(constantFolding, safeLocalCse, typedDeadCode);
        int familyCount = (int) families.stream()
                .filter(family -> !"not-recorded".equals(family.status()))
                .count();
        int candidateFamilyCount = (int) families.stream()
                .filter(family -> family.candidateCount() > 0)
                .count();
        int blockedFamilyCount = (int) families.stream()
                .filter(family -> "blocked-by-proof".equals(family.status()))
                .count();
        String status = previewReadinessStatus(families);
        String familySummary = families.stream()
                .map(family -> family.family() + "=" + family.status())
                .collect(java.util.stream.Collectors.joining(", "));
        return new PreviewReadinessEvidence(status, familyCount, candidateFamilyCount, blockedFamilyCount, familySummary);
    }

    private static RuntimeEquivalenceReviewEvidence runtimeEquivalenceReviewEvidence(
            PreviewReadinessEvidence previewReadiness,
            ConstantFoldingMaterializationEvidence constantFoldingMaterialization,
            SafeLocalCseMaterializationEvidence safeLocalCseMaterialization,
            MadFmaMaterializationEvidence madFmaMaterialization,
            IntrinsicMaterializationEvidence clampMaterialization,
            IntrinsicMaterializationEvidence stepMaterialization,
            IntrinsicMaterializationEvidence mixMaterialization,
            LoopVectorizationMaterializationEvidence loopVectorizationMaterialization,
            TypedDeadCodeMaterializationEvidence typedDeadCodeMaterialization
    ) {
        boolean constantFoldingMaterializedCandidate = constantFoldingMaterialization.transformedNodeCount() > 0;
        boolean safeLocalCseMaterializedCandidate = safeLocalCseMaterialization.transformedNodeCount() > 0;
        boolean madFmaMaterializedCandidate = madFmaMaterialization.transformedNodeCount() > 0;
        boolean clampMaterializedCandidate = clampMaterialization.transformedNodeCount() > 0;
        boolean stepMaterializedCandidate = stepMaterialization.transformedNodeCount() > 0;
        boolean mixMaterializedCandidate = mixMaterialization.transformedNodeCount() > 0;
        boolean loopVectorizationMaterializedCandidate = loopVectorizationMaterialization.transformedLoopCount() > 0;
        boolean typedDeadCodeMaterializedCandidate = typedDeadCodeMaterialization.removedNodeCount() > 0;
        boolean constantFoldingMaterializationReady = !constantFoldingMaterializedCandidate
                || "review-ready".equals(constantFoldingMaterialization.status());
        boolean safeLocalCseMaterializationReady = !safeLocalCseMaterializedCandidate
                || "review-ready".equals(safeLocalCseMaterialization.status());
        boolean madFmaMaterializationReady = !madFmaMaterializedCandidate
                || "review-ready".equals(madFmaMaterialization.status());
        boolean clampMaterializationReady = !clampMaterializedCandidate
                || "review-ready".equals(clampMaterialization.status());
        boolean stepMaterializationReady = !stepMaterializedCandidate
                || "review-ready".equals(stepMaterialization.status());
        boolean mixMaterializationReady = !mixMaterializedCandidate
                || "review-ready".equals(mixMaterialization.status());
        boolean loopVectorizationMaterializationReady = !loopVectorizationMaterializedCandidate
                || "review-ready".equals(loopVectorizationMaterialization.status());
        boolean typedDeadCodeMaterializationReady = !typedDeadCodeMaterializedCandidate
                || "review-ready".equals(typedDeadCodeMaterialization.status());
        boolean previewRequired = previewReadiness.candidateFamilyCount() > 0;
        boolean previewEligible = !previewRequired
                || "ready-for-runtime-equivalence-review".equals(previewReadiness.status());
        boolean required = previewRequired
                || constantFoldingMaterializedCandidate
                || safeLocalCseMaterializedCandidate
                || madFmaMaterializedCandidate
                || clampMaterializedCandidate
                || stepMaterializedCandidate
                || mixMaterializedCandidate
                || loopVectorizationMaterializedCandidate
                || typedDeadCodeMaterializedCandidate;
        boolean eligible = required
                && constantFoldingMaterializationReady
                && safeLocalCseMaterializationReady
                && madFmaMaterializationReady
                && clampMaterializationReady
                && stepMaterializationReady
                && mixMaterializationReady
                && loopVectorizationMaterializationReady
                && typedDeadCodeMaterializationReady
                && previewEligible;
        String status = eligible ? "review-ready" : "blocked";
        String previewBlocker = switch (previewReadiness.status()) {
            case "ready-for-runtime-equivalence-review" -> "none";
            case "not-recorded" -> "preview-readiness-not-recorded";
            case "no-candidates" -> "preview-readiness-no-candidates";
            case "blocked-by-proof" -> "preview-readiness-blocked-by-proof";
            case "candidates-recorded" -> "preview-readiness-candidates-not-proof-clean";
            default -> "preview-readiness-unknown";
        };
        String firstBlocker;
        if (constantFoldingMaterializedCandidate && !constantFoldingMaterializationReady) {
            firstBlocker = constantFoldingMaterialization.firstBlocker();
        } else if (safeLocalCseMaterializedCandidate && !safeLocalCseMaterializationReady) {
            firstBlocker = safeLocalCseMaterialization.firstBlocker();
        } else if (madFmaMaterializedCandidate && !madFmaMaterializationReady) {
            firstBlocker = madFmaMaterialization.firstBlocker();
        } else if (clampMaterializedCandidate && !clampMaterializationReady) {
            firstBlocker = clampMaterialization.firstBlocker();
        } else if (stepMaterializedCandidate && !stepMaterializationReady) {
            firstBlocker = stepMaterialization.firstBlocker();
        } else if (mixMaterializedCandidate && !mixMaterializationReady) {
            firstBlocker = mixMaterialization.firstBlocker();
        } else if (loopVectorizationMaterializedCandidate && !loopVectorizationMaterializationReady) {
            firstBlocker = loopVectorizationMaterialization.firstBlocker();
        } else if (typedDeadCodeMaterializedCandidate && !typedDeadCodeMaterializationReady) {
            firstBlocker = typedDeadCodeMaterialization.firstBlocker();
        } else if (previewRequired && !previewEligible) {
            firstBlocker = previewBlocker;
        } else {
            firstBlocker = required ? "none" : "review-not-required";
        }
        String familySummary = previewReadiness.familySummary()
                + ", constant-folding-materialization="
                + constantFoldingMaterialization.status()
                + ", safe-local-cse-materialization="
                + safeLocalCseMaterialization.status()
                + ", mad-fma-materialization="
                + madFmaMaterialization.status()
                + ", clamp-materialization="
                + clampMaterialization.status()
                + ", step-materialization="
                + stepMaterialization.status()
                + ", mix-materialization="
                + mixMaterialization.status()
                + ", loop-vectorization-materialization="
                + loopVectorizationMaterialization.status()
                + ", typed-dead-code-materialization="
                + typedDeadCodeMaterialization.status();
        return new RuntimeEquivalenceReviewEvidence(
                status,
                eligible,
                required,
                firstBlocker,
                familySummary
        );
    }

    private static ReviewPackageEvidence reviewPackageEvidence(
            List<GpuRuntimeIrOptimizationPassReport> passReports,
            RuntimeEquivalenceReviewEvidence runtimeEquivalenceReview
    ) {
        int proposalPassCount = 0;
        int pendingApprovalCount = 0;
        for (GpuRuntimeIrOptimizationPassReport passReport : passReports) {
            if (passReport == null) {
                continue;
            }
            ApprovalTemplateEvidence approvalTemplate = approvalTemplateEvidence(passReport);
            if (approvalTemplate.applicable()) {
                proposalPassCount++;
            }
            if ("pending".equals(approvalTemplate.status())) {
                pendingApprovalCount++;
            }
        }
        boolean required = runtimeEquivalenceReview.required() || proposalPassCount > 0;
        boolean complete = false;
        String firstBlocker;
        if (!required) {
            firstBlocker = "review-package-not-required";
        } else if (runtimeEquivalenceReview.required() && !runtimeEquivalenceReview.eligible()) {
            firstBlocker = runtimeEquivalenceReview.firstBlocker();
        } else if (pendingApprovalCount > 0) {
            firstBlocker = "approval-template-pending";
        } else if (proposalPassCount <= 0) {
            firstBlocker = "optimized-ir-proposal-missing";
        } else {
            firstBlocker = "manual-review-required";
        }
        String status = required ? "pending-manual-review" : "not-required";
        return new ReviewPackageEvidence(
                status,
                required,
                complete,
                firstBlocker,
                proposalPassCount,
                pendingApprovalCount,
                runtimeEquivalenceReview.status()
        );
    }

    private static ApprovalManifestPackageEvidence approvalManifestPackageEvidence(
            List<GpuRuntimeIrOptimizationPassReport> passReports,
            ReviewPackageEvidence reviewPackage
    ) {
        LinkedHashMap<String, Integer> resourcePaths = new LinkedHashMap<>();
        int applicableTemplateCount = 0;
        int presentCount = 0;
        int acceptedCount = 0;
        String firstManifestBlocker = "none";
        for (GpuRuntimeIrOptimizationPassReport passReport : passReports) {
            ApprovalTemplateEvidence approvalTemplate = approvalTemplateEvidence(passReport);
            if (!approvalTemplate.applicable()) {
                continue;
            }
            applicableTemplateCount++;
            String resourcePath = approvalTemplate.fields().getOrDefault("resourcePath", "missing");
            if (!resourcePath.isBlank() && !"missing".equals(resourcePath)) {
                resourcePaths.merge(resourcePath, 1, Integer::sum);
            }
            if (parseBoolean(approvalTemplate.fields().get("approvalManifest.present"))) {
                presentCount++;
            }
            if (parseBoolean(approvalTemplate.fields().get("approvalManifest.accepted"))) {
                acceptedCount++;
            }
            String blocker = approvalTemplate.fields().getOrDefault("approvalManifest.firstBlocker", "none");
            if ("none".equals(firstManifestBlocker) && !"none".equals(blocker)) {
                firstManifestBlocker = blocker;
            }
        }
        boolean required = reviewPackage.required() && applicableTemplateCount > 0;
        String resourcePathSummary = compactCountSummary(resourcePaths);
        String status;
        String firstBlocker;
        if (!required) {
            status = "not-required";
            firstBlocker = "approval-manifest-not-required";
        } else if (resourcePaths.isEmpty()) {
            status = "pending-resource-path";
            firstBlocker = "approval-manifest-resource-path-missing";
        } else if (acceptedCount >= applicableTemplateCount) {
            status = "accepted";
            firstBlocker = "none";
        } else if (presentCount > 0) {
            status = "blocked";
            firstBlocker = "none".equals(firstManifestBlocker)
                    ? "approval-manifest-validation-blocked"
                    : firstManifestBlocker;
        } else {
            status = "pending-manifest-validation";
            firstBlocker = "approval-manifest-not-loaded";
        }
        return new ApprovalManifestPackageEvidence(
                status,
                required,
                presentCount,
                acceptedCount,
                resourcePathSummary,
                firstBlocker
        );
    }

    private static String compactCountSummary(Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        counts.forEach((key, count) -> {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(key).append('=').append(count);
        });
        return builder.toString();
    }

    private static String previewReadinessStatus(List<PreviewFamilyReadiness> families) {
        if (families.stream().allMatch(family -> "not-recorded".equals(family.status()))) {
            return "not-recorded";
        }
        if (families.stream().anyMatch(family -> "blocked-by-proof".equals(family.status()))) {
            return "blocked-by-proof";
        }
        if (families.stream().anyMatch(family -> "ready-for-runtime-equivalence-review".equals(family.status()))) {
            return "ready-for-runtime-equivalence-review";
        }
        if (families.stream().anyMatch(family -> "candidates-recorded".equals(family.status()))) {
            return "candidates-recorded";
        }
        return "no-candidates";
    }

    private static String previewFamilyStatus(int passCount, int candidateCount, int blockerCount) {
        if (passCount <= 0) {
            return "not-recorded";
        }
        if (candidateCount <= 0 && blockerCount <= 0) {
            return "no-candidates";
        }
        if (blockerCount > 0) {
            return "blocked-by-proof";
        }
        if (candidateCount > 0) {
            return "ready-for-runtime-equivalence-review";
        }
        return "candidates-recorded";
    }

    private static int parseNonNegativeInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value)
                || "enabled".equalsIgnoreCase(value)
                || "required".equalsIgnoreCase(value);
    }

    private record ConstantFoldingPreviewEvidence(
            int passCount,
            int candidateCount,
            int skippedNonPlainLiteralCount,
            int skippedDivideByZeroCount,
            int skippedNonEvenDivisionCount,
            int skippedUnsupportedOperatorCount,
            int skippedNonLiteralOperandCount,
            boolean runtimeEquivalenceRequiredBeforeRewrite,
            boolean approvalRequiredBeforeRewrite,
            boolean integerOverflowProven,
            boolean floatingPointRoundingProven
    ) {
        private int skippedCount() {
            return skippedNonPlainLiteralCount
                    + skippedDivideByZeroCount
                    + skippedNonEvenDivisionCount
                    + skippedUnsupportedOperatorCount
                    + skippedNonLiteralOperandCount;
        }

        private int proofBlockerCount() {
            if (candidateCount <= 0) {
                return 0;
            }
            int blockers = 0;
            if (!integerOverflowProven) {
                blockers++;
            }
            if (!floatingPointRoundingProven) {
                blockers++;
            }
            return blockers;
        }
    }

    private record ConstantFoldingMaterializationEvidence(
            int passCount,
            int candidateCount,
            int transformedNodeCount,
            int literalRewriteCount,
            int identityRewriteCount,
            int fixedPointPassCount,
            int changedMethodBodyCount,
            int bodyTextReplacementCount,
            int skippedDivideByZeroCount,
            int skippedNonEvenDivisionCount,
            boolean runtimeEquivalenceRequiredBeforeSelection,
            boolean runtimeEquivalencePayloadRequired,
            int runtimeEquivalencePayloadPresentCount,
            int runtimeEquivalencePassedCount,
            boolean approvalRequiredBeforeProduction,
            String status,
            String firstBlocker
    ) {
    }

    private record TypedDeadCodeMaterializationEvidence(
            int passCount,
            int nodeCount,
            int unreachableNodeCount,
            int removedNodeCount,
            int changedMethodBodyCount,
            int blockedMissingRootCount,
            int blockedMissingChildReferenceCount,
            int blockedSideEffectingUnreachableNodeCount,
            boolean runtimeEquivalenceRequiredBeforeSelection,
            boolean runtimeEquivalencePayloadRequired,
            int runtimeEquivalencePayloadPresentCount,
            int runtimeEquivalencePassedCount,
            boolean approvalRequiredBeforeProduction,
            boolean sideEffectFreedomProven,
            String status,
            String firstBlocker
    ) {
    }

    private record SafeLocalCseMaterializationEvidence(
            int passCount,
            int localBindingCount,
            int candidateCount,
            int transformedNodeCount,
            int changedMethodBodyCount,
            int bodyTextReplacementCount,
            int fixedPointPassCount,
            int skippedControlFlowBoundaryCount,
            int skippedUnsupportedOperatorCount,
            int skippedImpureOperandCount,
            int skippedBodyTextPatternMissingCount,
            boolean runtimeEquivalenceRequiredBeforeSelection,
            boolean runtimeEquivalencePayloadRequired,
            int runtimeEquivalencePayloadPresentCount,
            int runtimeEquivalencePassedCount,
            boolean approvalRequiredBeforeProduction,
            boolean dominanceProven,
            boolean sideEffectFreedomProven,
            String status,
            String firstBlocker
    ) {
    }

    private record MadFmaMaterializationEvidence(
            int passCount,
            int candidateCount,
            int transformedNodeCount,
            int changedMethodBodyCount,
            int bodyTextReplacementCount,
            int fixedPointPassCount,
            int skippedFastMathPolicyCount,
            int skippedBodyTextPatternMissingCount,
            boolean runtimeEquivalenceRequiredBeforeSelection,
            boolean runtimeEquivalencePayloadRequired,
            int runtimeEquivalencePayloadPresentCount,
            int runtimeEquivalencePassedCount,
            boolean approvalRequiredBeforeProduction,
            boolean fastMathAllowed,
            String status,
            String firstBlocker
    ) {
    }

    private record IntrinsicMaterializationEvidence(
            int passCount,
            int candidateCount,
            int transformedNodeCount,
            int changedMethodBodyCount,
            int bodyTextReplacementCount,
            int fixedPointPassCount,
            int skippedTypedBodyMissingCount,
            int skippedUnsupportedFormatCount,
            int skippedFastMathPolicyCount,
            int skippedMissingChildReferenceCount,
            int skippedUnsupportedShapeCount,
            int skippedBodyTextPatternMissingCount,
            boolean runtimeEquivalenceRequiredBeforeSelection,
            boolean runtimeEquivalencePayloadRequired,
            int runtimeEquivalencePayloadPresentCount,
            int runtimeEquivalencePassedCount,
            boolean approvalRequiredBeforeProduction,
            int directStepCount,
            int invertedStepCount,
            int canonicalMixCount,
            int expandedMixCount,
            int madExpandedMixCount,
            boolean fastMathAllowed,
            boolean fastMathRequired,
            boolean strictFloatPreserved,
            boolean argumentOrderPreserved,
            boolean strictComparisonPreserved,
            boolean equalityBehaviorPreserved,
            boolean nanComparisonPreserved,
            boolean algebraicReassociationRequired,
            boolean mixArgumentOrderPreserved,
            String status,
            String firstBlocker
    ) {
    }

    private record LoopVectorizationMaterializationEvidence(
            int passCount,
            int candidateCount,
            int transformedLoopCount,
            int changedMethodBodyCount,
            int bodyTextReplacementCount,
            int typedBodyMaterializedCount,
            int typedBodyInvalidatedCount,
            int skippedLoopShapeCount,
            int skippedUnsupportedWidthCount,
            int skippedUnsafeLoadPatternCount,
            boolean runtimeEquivalenceRequiredBeforeSelection,
            boolean runtimeEquivalencePayloadRequired,
            int runtimeEquivalencePayloadPresentCount,
            int runtimeEquivalencePassedCount,
            boolean approvalRequiredBeforeProduction,
            boolean loopTripCountProven,
            boolean contiguousLoadProven,
            boolean orderedReductionPreserved,
            String status,
            String firstBlocker
    ) {
    }

    private record BackendNeutralSourceMaterializationEvidence(
            int passCount,
            int candidateCount,
            int sourceReadyCount,
            int sourceLengthTotal,
            int materializationOnlyCount,
            String status,
            String firstBlocker
    ) {
    }

    private record SafeLocalCsePreviewEvidence(
            int passCount,
            int expressionCount,
            int candidateExpressionCount,
            int duplicateExpressionCount,
            int equivalenceClassCount,
            int blockedUnsupportedOperatorCount,
            int blockedImpureOperandCount,
            int blockedControlFlowBoundaryCount,
            boolean runtimeEquivalenceRequiredBeforeRewrite,
            boolean approvalRequiredBeforeRewrite,
            boolean dominanceProven,
            boolean sideEffectFreedomProven
    ) {
        private int blockedCount() {
            return blockedUnsupportedOperatorCount
                    + blockedImpureOperandCount
                    + blockedControlFlowBoundaryCount;
        }

        private int proofBlockerCount() {
            if (duplicateExpressionCount <= 0) {
                return 0;
            }
            int blockers = 0;
            if (!dominanceProven) {
                blockers++;
            }
            if (!sideEffectFreedomProven) {
                blockers++;
            }
            return blockers;
        }
    }

    private record TypedDeadCodePreviewEvidence(
            int passCount,
            int nodeCount,
            int reachableNodeCount,
            int unreachableNodeCount,
            int blockedMissingRootCount,
            int blockedMissingChildReferenceCount,
            int blockedSideEffectingUnreachableNodeCount,
            boolean runtimeEquivalenceRequiredBeforeRewrite,
            boolean approvalRequiredBeforeRewrite,
            boolean sideEffectFreedomProven
    ) {
        private int blockedCount() {
            return blockedMissingRootCount
                    + blockedMissingChildReferenceCount
                    + blockedSideEffectingUnreachableNodeCount;
        }

        private int proofBlockerCount() {
            if (unreachableNodeCount <= 0) {
                return 0;
            }
            return sideEffectFreedomProven ? 0 : 1;
        }
    }

    private record PreviewFamilyReadiness(String family, String status, int candidateCount, int blockerCount) {
    }

    private record PreviewReadinessEvidence(
            String status,
            int familyCount,
            int candidateFamilyCount,
            int blockedFamilyCount,
            String familySummary
    ) {
    }

    private record RuntimeEquivalenceReviewEvidence(
            String status,
            boolean eligible,
            boolean required,
            String firstBlocker,
            String familySummary
    ) {
    }

    private record ReviewPackageEvidence(
            String status,
            boolean required,
            boolean complete,
            String firstBlocker,
            int proposalPassCount,
            int pendingApprovalCount,
            String runtimeEquivalenceStatus
    ) {
    }

    private record ApprovalManifestPackageEvidence(
            String status,
            boolean required,
            int presentCount,
            int acceptedCount,
            String resourcePathSummary,
            String firstBlocker
    ) {
    }

    private record PolicyGateEvidence(
            int skippedCount,
            int optimizerPolicyDisabledCount,
            int familyDisabledCount,
            int familyNotEnabledCount,
            int providerInvokedCount,
            String firstBlocker,
            String familySummary
    ) {
    }

    private record OptimizedArtifactCandidateEvidence(
            String status,
            int count,
            int readyCount,
            int blockedCount,
            int selectionReadyCount,
            int selectionAppliedCount,
            int selectedIrReplacementCount,
            int mutationAllowedCount,
            String firstBlocker,
            String selectionFirstBlocker
    ) {
        private boolean selectionApplied() {
            return selectionAppliedCount > 0;
        }

        private boolean selectedIrReplacement() {
            return selectedIrReplacementCount > 0;
        }
    }

    private static ApprovalTemplateEvidence approvalTemplateEvidence(GpuRuntimeIrOptimizationPassReport passReport) {
        if (passReport == null) {
            return ApprovalTemplateEvidence.notApplicable("pass-report-missing", Map.of());
        }
        Map<String, String> proofFields = passReport.proofArtifact().fields();
        if ("false".equals(proofFields.get("rewrite.proposed"))
                || "true".equals(proofFields.get("previewOnly"))) {
            return ApprovalTemplateEvidence.notApplicable("proposal-decision-not-proposed", Map.of());
        }
        if (!"proposal-only".equals(passReport.proofStatus())
                && !"optimized-selected".equals(passReport.proofStatus())) {
            return ApprovalTemplateEvidence.notApplicable("proposal-decision-not-proposed", Map.of());
        }
        if (!distinctIrIdentities(passReport.originalIrIdentity(), passReport.transformedIrIdentity())) {
            return ApprovalTemplateEvidence.notApplicable("proposal-identities-not-distinct", Map.of());
        }
        String proofSource = passReport.proofArtifact().source();
        if (proofSource == null
                || proofSource.isBlank()
                || "none".equals(proofSource)
                || "ir-optimizer".equals(proofSource)) {
            return ApprovalTemplateEvidence.notApplicable("proposal-proof-source-not-specific", Map.of());
        }
        String proofVerdict = passReport.proofArtifact().verdict() == null
                ? ""
                : passReport.proofArtifact().verdict().toLowerCase(java.util.Locale.ROOT);
        if (proofVerdict.isBlank()
                || proofVerdict.contains("not-proven")
                || proofVerdict.contains("rejected")
                || proofVerdict.contains("failed")) {
            return ApprovalTemplateEvidence.notApplicable("proposal-proof-verdict-not-accepted", Map.of());
        }
        return ApprovalTemplateEvidence.pending(approvalTemplateFields(passReport));
    }

    private static Map<String, String> approvalTemplateFields(GpuRuntimeIrOptimizationPassReport passReport) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        Map<String, String> proofFields = passReport.proofArtifact().fields();
        boolean runtimeEquivalencePayloadRequired = parseBoolean(
                proofFields.get("proof.runtimeEquivalencePayloadRequiredBeforeSelection")
        ) || parseBoolean(proofFields.get("runtimeEquivalencePayload.required"));
        boolean runtimeEquivalencePayloadComponentsComplete = parseBoolean(
                proofFields.get("runtimeEquivalencePayload.cpuReference.present")
        ) && parseBoolean(proofFields.get("runtimeEquivalencePayload.preOptimizationOutput.present"))
                && parseBoolean(proofFields.get("runtimeEquivalencePayload.postOptimizationOutput.present"))
                && parseBoolean(proofFields.get("runtimeEquivalencePayload.tolerance.present"))
                && parseBoolean(proofFields.get("runtimeEquivalencePayload.failureFixture.present"));
        fields.put("runtimeEquivalencePayload.required", Boolean.toString(runtimeEquivalencePayloadRequired));
        fields.put(
                "runtimeEquivalencePayload.present",
                Boolean.toString(parseBoolean(proofFields.get("runtimeEquivalencePayload.present")))
        );
        fields.put(
                "runtimeEquivalencePayload.passed",
                Boolean.toString(parseBoolean(proofFields.get("runtimeEquivalencePayload.passed")))
        );
        fields.put(
                "runtimeEquivalencePayload.componentsComplete",
                Boolean.toString(runtimeEquivalencePayloadComponentsComplete)
        );
        fields.put(
                "runtimeEquivalencePayload.caseCount",
                proofFields.getOrDefault("runtimeEquivalencePayload.Case.Count", "0")
        );
        fields.put(
                "runtimeEquivalencePayload.resource",
                runtimeEquivalencePayloadRequired
                        ? proofFields.getOrDefault("runtimeEquivalencePayload.resource", "missing")
                        : "not-required"
        );
        fields.put(
                "runtimeEquivalencePayload.comparisonMode",
                runtimeEquivalencePayloadRequired
                        ? proofFields.getOrDefault("runtimeEquivalencePayload.comparisonMode", "missing")
                        : "not-required"
        );
        fields.put("resourceDirectory", "META-INF/javatogpu/ir-optimization-approvals/");
        fields.put("resourcePath", proofFields.getOrDefault("approvalTemplate.resourcePath", "missing"));
        fields.put("approvalManifest.status", proofFields.getOrDefault("approvalManifest.status", "pending-manifest-validation"));
        fields.put("approvalManifest.required", proofFields.getOrDefault("approvalManifest.required", "true"));
        fields.put("approvalManifest.present", proofFields.getOrDefault("approvalManifest.present", "false"));
        fields.put("approvalManifest.accepted", proofFields.getOrDefault("approvalManifest.accepted", "false"));
        fields.put("approvalManifest.resourcePath", proofFields.getOrDefault("approvalManifest.resourcePath", "missing"));
        fields.put("approvalManifest.resource.count", proofFields.getOrDefault("approvalManifest.resource.count", "0"));
        fields.put("approvalManifest.firstBlocker", proofFields.getOrDefault(
                "approvalManifest.firstBlocker",
                "approval-manifest-not-loaded"
        ));
        return Map.copyOf(fields);
    }

    private static boolean distinctIrIdentities(String original, String transformed) {
        String normalizedOriginal = original == null ? "" : original;
        String normalizedTransformed = transformed == null ? "" : transformed;
        return !normalizedOriginal.isBlank()
                && !normalizedTransformed.isBlank()
                && !"irgpu:missing".equals(normalizedOriginal)
                && !"irgpu:missing".equals(normalizedTransformed)
                && !normalizedOriginal.equals(normalizedTransformed);
    }

    private record ApprovalTemplateEvidence(
            String status,
            boolean applicable,
            String firstBlocker,
            Map<String, String> fields
    ) {
        private ApprovalTemplateEvidence {
            fields = fields == null ? Map.of() : Map.copyOf(fields);
        }

        private static ApprovalTemplateEvidence pending(Map<String, String> fields) {
            return new ApprovalTemplateEvidence("pending", true, "none", fields);
        }

        private static ApprovalTemplateEvidence notApplicable(String firstBlocker, Map<String, String> fields) {
            return new ApprovalTemplateEvidence("not-applicable", false, firstBlocker, fields);
        }
    }

    private static void appendCompilerFeedbackAnalysis(
            StringBuilder builder,
            List<GpuRuntimeIrOptimizationPassReport> analysisReports,
            GpuBackendCompilerFeedbackReport compilerFeedbackReport
    ) {
        GpuBackendCompilerFeedback feedback = compilerFeedbackReport.selected().orElse(null);
        if (feedback == null) {
            return;
        }

        feedback.artifactFields("compilerFeedback.selected").forEach((key, value) -> builder
                .append(key)
                .append('=')
                .append(safePropertyValue(value))
                .append('\n'));
        int heuristicRegisters = heuristicRegisterEstimate(analysisReports);
        int compilerRegisters = feedback.effectiveRegisterCount();
        builder.append("compilerFeedback.registerPressure.heuristicAvailable=")
                .append(heuristicRegisters >= 0).append('\n');
        builder.append("compilerFeedback.registerPressure.heuristicEstimatedValueRegisters=")
                .append(heuristicRegisters >= 0 ? heuristicRegisters : "unknown").append('\n');
        builder.append("compilerFeedback.registerPressure.compilerEffectiveRegisters=")
                .append(compilerRegisters >= 0 ? compilerRegisters : "unknown").append('\n');
        if (heuristicRegisters >= 0 && compilerRegisters >= 0) {
            int delta = compilerRegisters - heuristicRegisters;
            builder.append("compilerFeedback.registerPressure.delta=").append(delta).append('\n');
            builder.append("compilerFeedback.registerPressure.comparisonStatus=")
                    .append(registerPressureComparisonStatus(delta)).append('\n');
        } else {
            builder.append("compilerFeedback.registerPressure.delta=unknown\n");
            builder.append("compilerFeedback.registerPressure.comparisonStatus=")
                    .append(compilerRegisters >= 0 ? "compiler-only" : "register-count-unavailable")
                    .append('\n');
        }
    }

    private static int heuristicRegisterEstimate(List<GpuRuntimeIrOptimizationPassReport> analysisReports) {
        for (GpuRuntimeIrOptimizationPassReport passReport : analysisReports) {
            String value = passReport.proofArtifact().fields().get("registerPressure.estimatedValueRegisters");
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return GpuBackendCompilerFeedback.UNKNOWN;
            }
        }
        return GpuBackendCompilerFeedback.UNKNOWN;
    }

    private static String registerPressureComparisonStatus(int delta) {
        if (delta == 0) {
            return "matched";
        }
        return delta > 0 ? "heuristic-underestimated" : "heuristic-overestimated";
    }

    private static String optimizerFamilyName(GpuRuntimeIrOptimizationPassReport passReport) {
        GpuRuntimeIrOptimizationProofArtifact proofArtifact = passReport.proofArtifact();
        String explicitFamily = proofArtifact.fields().getOrDefault("optimizerFamily", "");
        if (!explicitFamily.isBlank()) {
            return explicitFamily;
        }
        String optimizerVersion = passReport.optimizerVersion();
        int separator = optimizerVersion.indexOf(':');
        return separator >= 0 && separator < optimizerVersion.length() - 1
                ? optimizerVersion.substring(separator + 1)
                : optimizerVersion;
    }

    private record OptimizerFamilyEquivalenceArtifacts(
            String index,
            Map<String, String> files
    ) {
    }

    private record OptimizerFamilyPayloadPaths(
            String directory,
            String manifest,
            String cpuReference,
            String preOptimizationOutput,
            String postOptimizationOutput,
            String tolerance,
            String failureFixture,
            String diagnostics
    ) {
    }

    private record OptimizerFamilyPayload(
            String name,
            int passCount,
            boolean runtimePayloadPresent,
            boolean cpuReferencePresent,
            boolean preOptimizationOutputPresent,
            boolean postOptimizationOutputPresent,
            boolean tolerancePresent,
            boolean failureFixturePresent,
            List<OptimizerFamilyPassTrace> passTraces
    ) {

        private static OptimizerFamilyPayload empty(String name) {
            return new OptimizerFamilyPayload(name, 0, false, false, false, false, false, false, List.of());
        }

        private OptimizerFamilyPayload add(GpuRuntimeIrOptimizationPassReport passReport) {
            Map<String, String> fields = passReport.proofArtifact().fields();
            List<OptimizerFamilyPassTrace> updatedPassTraces = new java.util.ArrayList<>(passTraces);
            updatedPassTraces.add(OptimizerFamilyPassTrace.from(passReport));
            return new OptimizerFamilyPayload(
                    name,
                    passCount + 1,
                    runtimePayloadPresent || fieldIsTrue(fields, "runtimeEquivalencePayload.present"),
                    cpuReferencePresent || fieldIsTrue(fields, "runtimeEquivalencePayload.cpuReference.present"),
                    preOptimizationOutputPresent || fieldIsTrue(fields, "runtimeEquivalencePayload.preOptimizationOutput.present"),
                    postOptimizationOutputPresent || fieldIsTrue(fields, "runtimeEquivalencePayload.postOptimizationOutput.present"),
                    tolerancePresent || fieldIsTrue(fields, "runtimeEquivalencePayload.tolerance.present"),
                    failureFixturePresent || fieldIsTrue(fields, "runtimeEquivalencePayload.failureFixture.present"),
                    List.copyOf(updatedPassTraces)
            );
        }

        private boolean complete(boolean runtimeEquivalencePassed) {
            return runtimeEquivalencePassed
                    && runtimePayloadPresent
                    && cpuReferencePresent
                    && preOptimizationOutputPresent
                    && postOptimizationOutputPresent
                    && tolerancePresent
                    && failureFixturePresent;
        }

        private String firstMissing(boolean runtimeEquivalencePassed) {
            if (!runtimeEquivalencePassed) {
                return "runtime-equivalence-not-passed";
            }
            if (!runtimePayloadPresent) {
                return "runtime-equivalence-payload";
            }
            if (!cpuReferencePresent) {
                return "cpu-reference";
            }
            if (!preOptimizationOutputPresent) {
                return "pre-optimization-output";
            }
            if (!postOptimizationOutputPresent) {
                return "post-optimization-output";
            }
            if (!tolerancePresent) {
                return "tolerance-metadata";
            }
            if (!failureFixturePresent) {
                return "failure-fixture";
            }
            return "none";
        }

        private String proofSourceSummary() {
            return compactSummary(passTraces.stream()
                    .map(OptimizerFamilyPassTrace::proofSource)
                    .toList());
        }

        private String proofVerdictSummary() {
            return compactSummary(passTraces.stream()
                    .map(OptimizerFamilyPassTrace::proofVerdict)
                    .toList());
        }

        private String payloadResourceSummary() {
            return compactSummary(passTraces.stream()
                    .map(OptimizerFamilyPassTrace::payloadResource)
                    .toList());
        }
    }

    private record OptimizerFamilyPassTrace(
            String optimizerVersion,
            String outcome,
            String proofStatus,
            String proofSource,
            String proofVerdict,
            String payloadResource,
            String cpuReferenceResource,
            String preOptimizationOutputResource,
            String postOptimizationOutputResource,
            String toleranceResource,
            String failureFixtureResource,
            String cpuReferencePayload,
            String preOptimizationOutputPayload,
            String postOptimizationOutputPayload,
            String tolerancePayload,
            String failureFixturePayload,
            String firstDiagnostic,
            Map<String, String> evidenceFields
    ) {

        private static OptimizerFamilyPassTrace from(GpuRuntimeIrOptimizationPassReport passReport) {
            GpuRuntimeIrOptimizationProofArtifact proofArtifact = passReport.proofArtifact();
            Map<String, String> fields = proofArtifact.fields();
            return new OptimizerFamilyPassTrace(
                    safePropertyValue(passReport.optimizerVersion()),
                    safePropertyValue(passReport.outcome().name()),
                    safePropertyValue(passReport.proofStatus()),
                    safePropertyValue(proofArtifact.source()),
                    safePropertyValue(proofArtifact.verdict()),
                    firstField(fields,
                            "runtimeEquivalencePayload.resource",
                            "runtimeEquivalencePayload.path",
                            "runtimeEquivalencePayload.id"),
                    firstField(fields,
                            "runtimeEquivalencePayload.cpuReference.resource",
                            "runtimeEquivalencePayload.cpuReference.path",
                            "runtimeEquivalencePayload.cpuReference.id"),
                    firstField(fields,
                            "runtimeEquivalencePayload.preOptimizationOutput.resource",
                            "runtimeEquivalencePayload.preOptimizationOutput.path",
                            "runtimeEquivalencePayload.preOptimizationOutput.id"),
                    firstField(fields,
                            "runtimeEquivalencePayload.postOptimizationOutput.resource",
                            "runtimeEquivalencePayload.postOptimizationOutput.path",
                            "runtimeEquivalencePayload.postOptimizationOutput.id"),
                    firstField(fields,
                            "runtimeEquivalencePayload.tolerance.resource",
                            "runtimeEquivalencePayload.tolerance.path",
                            "runtimeEquivalencePayload.tolerance.id"),
                    firstField(fields,
                            "runtimeEquivalencePayload.failureFixture.resource",
                            "runtimeEquivalencePayload.failureFixture.path",
                            "runtimeEquivalencePayload.failureFixture.id"),
                    payloadField(fields, "Payload.CpuReference"),
                    payloadField(fields, "Payload.PreOptimizationOutput"),
                    payloadField(fields, "Payload.PostOptimizationOutput"),
                    payloadField(fields, "Payload.Tolerance"),
                    payloadField(fields, "Payload.FailureFixture"),
                    passReport.diagnostics().isEmpty()
                            ? "none"
                            : safePropertyValue(passReport.diagnostics().get(0)),
                    payloadEvidenceFields(fields)
            );
        }
    }

    private record OptimizerFamilyBindingDecision(
            String status,
            boolean eligible,
            String family,
            String firstBlocker
    ) {
        private static OptimizerFamilyBindingDecision blocked(String firstBlocker) {
            return blocked("none", firstBlocker);
        }

        private static OptimizerFamilyBindingDecision blocked(String family, String firstBlocker) {
            return new OptimizerFamilyBindingDecision("not-bound", false, family, firstBlocker);
        }
    }

    private static Map<String, String> payloadEvidenceFields(Map<String, String> fields) {
        LinkedHashMap<String, String> evidence = new LinkedHashMap<>();
        fields.entrySet().stream()
                .filter(entry -> isPayloadEvidenceField(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> evidence.put(entry.getKey(), entry.getValue()));
        return java.util.Collections.unmodifiableMap(evidence);
    }

    private static boolean isPayloadEvidenceField(String key) {
        String normalized = key == null ? "" : key.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("runtimeequivalence")
                || normalized.contains("equivalencepayload")
                || normalized.contains(".payload.");
    }

    private static boolean fieldIsTrue(Map<String, String> fields, String key) {
        return "true".equalsIgnoreCase(fields.getOrDefault(key, "false"));
    }

    private static String firstField(Map<String, String> fields, String... keys) {
        for (String key : keys) {
            String value = fields.getOrDefault(key, "");
            if (!value.isBlank()) {
                return safePropertyValue(value);
            }
        }
        return "not-recorded";
    }

    private static String payloadField(Map<String, String> fields, String suffix) {
        String directKey = "runtimeEquivalencePayload." + suffix;
        String directValue = fields.getOrDefault(directKey, "");
        if (!directValue.isBlank()) {
            return safePropertyValue(directValue);
        }
        String bestKey = "";
        String bestValue = "";
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (entry.getKey().endsWith(suffix) && !entry.getValue().isBlank()) {
                if (bestKey.isBlank() || entry.getKey().length() < bestKey.length()) {
                    bestKey = entry.getKey();
                    bestValue = entry.getValue();
                }
            }
        }
        return bestValue.isBlank() ? "not-recorded" : safePropertyValue(bestValue);
    }

    private static String compactSummary(List<String> values) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (String value : values) {
            String normalized = safePropertyValue(value);
            counts.put(normalized, counts.getOrDefault(normalized, 0) + 1);
        }
        if (counts.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static String safePropertyValue(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
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
