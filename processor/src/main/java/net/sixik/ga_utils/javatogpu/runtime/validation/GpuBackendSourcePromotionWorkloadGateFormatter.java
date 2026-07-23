package net.sixik.ga_utils.javatogpu.runtime.validation;

import net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuBackendSourcePromotionBlockerClassifier;
import net.sixik.ga_utils.javatogpu.runtime.GpuProductionPromotionDecision;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeArtifactProperties;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Merges runtime snapshot source-promotion evidence into a workload-level gate artifact.
 *
 * <p>The formatter is backend-neutral: runtimes provide the latest kernel resource and gate properties, while this
 * class owns the stable {@code kernel.N.*} aggregation format shared by validation reports and future backend gates.</p>
 */
public final class GpuBackendSourcePromotionWorkloadGateFormatter {

    private static final String RUNTIME_OPTIMIZER_DRIFT_PREFIX = "runtimeOptimizerDrift.";
    private static final String RUNTIME_BACKEND_SOURCE_PORTABLE_PREFIX = "runtime.backend.source";
    private static final String RUNTIME_BACKEND_SOURCE_PREFIX = RUNTIME_BACKEND_SOURCE_PORTABLE_PREFIX + ".";
    private static final String RUNTIME_IR_PRODUCTION_MUTATION_PORTABLE_PREFIX = "runtime.ir.productionMutation";

    private static final DriftProperty[] RUNTIME_OPTIMIZER_DRIFT_PROPERTIES = {
            driftProperty("pass.count", "0", "unknown"),
            driftProperty("pass.applied.count", "0", "unknown"),
            driftProperty("pass.skipped.count", "0", "unknown"),
            driftProperty("pass.rolledBack.count", "0", "unknown"),
            driftProperty("pass.failed.count", "0", "unknown"),
            driftProperty("proofArtifact.count", "0", "unknown"),
            driftProperty("proofArtifact.accepted.count", "0", "unknown"),
            driftProperty("proofArtifact.blocking.count", "0", "unknown"),
            driftProperty("replacementPlan.complete.count", "0"),
            driftProperty("replacementPlan.partial.count", "0"),
            driftProperty("replacementPlan.firstBlocker", "none"),
            driftProperty("replacementPlan.validation.count", "0"),
            driftProperty("replacementPlan.validation.valid.count", "0"),
            driftProperty("replacementPlan.validation.invalid.count", "0"),
            driftProperty("replacementPlan.validation.firstBlocker", "none"),
            driftProperty("rewriteVisitor.count", "0"),
            driftProperty("rewriteVisitor.ready.count", "0"),
            driftProperty("rewriteVisitor.blocked.count", "0"),
            driftProperty("rewriteVisitor.firstBlocker", "none"),
            driftProperty("rewriteVisitor.visitorImplemented", "true"),
            driftProperty("rewriteVisitor.replacementBuilderImplemented", "false"),
            driftProperty("rewriteVisitor.transformedIrBuilt", "false"),
            driftProperty("rewriteVisitor.mutationAllowed", "false"),
            driftProperty("rewriteVisitor.selectedIrReplacement", "false"),
            driftProperty("replacementBlueprint.count", "0"),
            driftProperty("replacementBlueprint.ready.count", "0"),
            driftProperty("replacementBlueprint.blocked.count", "0"),
            driftProperty("replacementBlueprint.firstBlocker", "none"),
            driftProperty("replacementBlueprint.blueprintImplemented", "true"),
            driftProperty("replacementBlueprint.replacementBuilderImplemented", "false"),
            driftProperty("replacementBlueprint.transformedIrBuilt", "false"),
            driftProperty("replacementBlueprint.mutationAllowed", "false"),
            driftProperty("replacementBlueprint.selectedIrReplacement", "false"),
            driftProperty("rewriteTransaction.count", "0"),
            driftProperty("rewriteTransaction.ready.count", "0"),
            driftProperty("rewriteTransaction.blocked.count", "0"),
            driftProperty("rewriteTransaction.firstBlocker", "none"),
            driftProperty("rewriteTransaction.transactionPreflightImplemented", "true"),
            driftProperty("rewriteTransaction.nodeIdAllocatorImplemented", "false"),
            driftProperty("rewriteTransaction.graphRewriteImplemented", "false"),
            driftProperty("rewriteTransaction.transformedIrBuilt", "false"),
            driftProperty("rewriteTransaction.mutationAllowed", "false"),
            driftProperty("rewriteTransaction.selectedIrReplacement", "false"),
            driftProperty("nodeIdAllocation.count", "0"),
            driftProperty("nodeIdAllocation.ready.count", "0"),
            driftProperty("nodeIdAllocation.blocked.count", "0"),
            driftProperty("nodeIdAllocation.firstBlocker", "none"),
            driftProperty("nodeIdAllocation.allocationPreflightImplemented", "true"),
            driftProperty("nodeIdAllocation.nodeIdsReserved", "false"),
            driftProperty("nodeIdAllocation.nodeIdAllocatorApplied", "false"),
            driftProperty("nodeIdAllocation.graphRewriteImplemented", "false"),
            driftProperty("nodeIdAllocation.transformedIrBuilt", "false"),
            driftProperty("nodeIdAllocation.mutationAllowed", "false"),
            driftProperty("nodeIdAllocation.selectedIrReplacement", "false"),
            driftProperty("replacementNode.count", "0"),
            driftProperty("replacementNode.ready.count", "0"),
            driftProperty("replacementNode.blocked.count", "0"),
            driftProperty("replacementNode.firstBlocker", "none"),
            driftProperty("replacementNode.replacementNodePreflightImplemented", "true"),
            driftProperty("replacementNode.replacementNodeBuilt", "false"),
            driftProperty("replacementNode.replacementBuilderImplemented", "false"),
            driftProperty("replacementNode.graphRewriteImplemented", "false"),
            driftProperty("replacementNode.transformedIrBuilt", "false"),
            driftProperty("replacementNode.mutationAllowed", "false"),
            driftProperty("replacementNode.selectedIrReplacement", "false"),
            driftProperty("graphPatch.count", "0"),
            driftProperty("graphPatch.ready.count", "0"),
            driftProperty("graphPatch.blocked.count", "0"),
            driftProperty("graphPatch.firstBlocker", "none"),
            driftProperty("graphPatch.graphPatchPreflightImplemented", "true"),
            driftProperty("graphPatch.graphPatchApplied", "false"),
            driftProperty("graphPatch.graphRewriteImplemented", "false"),
            driftProperty("graphPatch.transformedIrBuilt", "false"),
            driftProperty("graphPatch.mutationAllowed", "false"),
            driftProperty("graphPatch.selectedIrReplacement", "false"),
            driftProperty("transformedGraph.count", "0"),
            driftProperty("transformedGraph.ready.count", "0"),
            driftProperty("transformedGraph.blocked.count", "0"),
            driftProperty("transformedGraph.firstBlocker", "none"),
            driftProperty("transformedGraph.materializationPreflightImplemented", "true"),
            driftProperty("transformedGraph.transformedGraphBuilt", "false"),
            driftProperty("transformedGraph.transformedIrBuilt", "false"),
            driftProperty("transformedGraph.graphPatchApplied", "false"),
            driftProperty("transformedGraph.graphRewriteImplemented", "false"),
            driftProperty("transformedGraph.mutationAllowed", "false"),
            driftProperty("transformedGraph.selectedIrReplacement", "false"),
            driftProperty("irArtifactEnvelope.count", "0"),
            driftProperty("irArtifactEnvelope.ready.count", "0"),
            driftProperty("irArtifactEnvelope.blocked.count", "0"),
            driftProperty("irArtifactEnvelope.firstBlocker", "none"),
            driftProperty("irArtifactEnvelope.artifactEnvelopePreflightImplemented", "true"),
            driftProperty("irArtifactEnvelope.artifactEnvelopeBuilt", "false"),
            driftProperty("irArtifactEnvelope.optimizedArtifactBuilt", "false"),
            driftProperty("irArtifactEnvelope.transformedGraphBuilt", "false"),
            driftProperty("irArtifactEnvelope.transformedIrBuilt", "false"),
            driftProperty("irArtifactEnvelope.graphPatchApplied", "false"),
            driftProperty("irArtifactEnvelope.graphRewriteImplemented", "false"),
            driftProperty("irArtifactEnvelope.mutationAllowed", "false"),
            driftProperty("irArtifactEnvelope.selectedIrReplacement", "false"),
            driftProperty("artifactProofBinding.count", "0"),
            driftProperty("artifactProofBinding.ready.count", "0"),
            driftProperty("artifactProofBinding.blocked.count", "0"),
            driftProperty("artifactProofBinding.firstBlocker", "none"),
            driftProperty("artifactProofBinding.bindingPreflightImplemented", "true"),
            driftProperty("artifactProofBinding.proofBound", "false"),
            driftProperty("artifactProofBinding.rollbackBound", "false"),
            driftProperty("artifactProofBinding.approvalBound", "false"),
            driftProperty("artifactProofBinding.optimizedArtifactBuilt", "false"),
            driftProperty("artifactProofBinding.transformedIrBuilt", "false"),
            driftProperty("artifactProofBinding.mutationAllowed", "false"),
            driftProperty("artifactProofBinding.selectedIrReplacement", "false"),
            driftProperty("artifactSelection.count", "0"),
            driftProperty("artifactSelection.ready.count", "0"),
            driftProperty("artifactSelection.blocked.count", "0"),
            driftProperty("artifactSelection.firstBlocker", "none"),
            driftProperty("artifactSelection.selectionPreflightImplemented", "true"),
            driftProperty("artifactSelection.productionGateRequired", "false"),
            driftProperty("artifactSelection.productionGateAccepted", "false"),
            driftProperty("artifactSelection.mutationPolicyAllowed", "false"),
            driftProperty("artifactSelection.selectionApplied", "false"),
            driftProperty("artifactSelection.optimizedArtifactSelected", "false"),
            driftProperty("artifactSelection.optimizedArtifactBuilt", "false"),
            driftProperty("artifactSelection.transformedIrBuilt", "false"),
            driftProperty("artifactSelection.mutationAllowed", "false"),
            driftProperty("artifactSelection.selectedIrReplacement", "false"),
            driftProperty("rewriteSketch.count", "0"),
            driftProperty("rewriteSketch.ready.count", "0"),
            driftProperty("rewriteSketch.blocked.count", "0"),
            driftProperty("rewriteSketch.firstBlocker", "none"),
            driftProperty("rewriteSketch.rewriteBuilderImplemented", "false"),
            driftProperty("rewriteSketch.mutationAllowed", "false"),
            driftProperty("rewriteSketch.selectedIrReplacement", "false"),
            driftProperty("rewriteSketch.conflict.count", "0"),
            driftProperty("rewriteSketch.conflict.firstBlocker", "none"),
            driftProperty("rewriteSketch.conflict.conflictResolutionImplemented", "false"),
            driftProperty("rewriteSketch.conflict.selectionApplied", "false"),
            driftProperty("rewriteSketch.conflict.mutationAllowed", "false"),
            driftProperty("rewriteSketch.conflict.selectedIrReplacement", "false"),
            driftProperty("rewriteSelection.sketch.count", "0"),
            driftProperty("rewriteSelection.sketch.ready.count", "0"),
            driftProperty("rewriteSelection.sketch.blocked.count", "0"),
            driftProperty("rewriteSelection.conflict.count", "0"),
            driftProperty("rewriteSelection.status", "not-required"),
            driftProperty("rewriteSelection.firstBlocker", "no-rewrite-sketches"),
            driftProperty("rewriteSelection.rewriteBuilderImplemented", "false"),
            driftProperty("rewriteSelection.conflictResolutionImplemented", "false"),
            driftProperty("rewriteSelection.runtimeEquivalenceRequired", "false"),
            driftProperty("rewriteSelection.runtimeEquivalenceProven", "false"),
            driftProperty("rewriteSelection.approvalRequired", "false"),
            driftProperty("rewriteSelection.approvalAccepted", "false"),
            driftProperty("rewriteSelection.mutationAllowed", "false"),
            driftProperty("rewriteSelection.selectionApplied", "false"),
            driftProperty("rewriteSelection.selectedIrReplacement", "false"),
            driftProperty("rewriteProof.status", "not-required"),
            driftProperty("rewriteProof.firstBlocker", "no-proof-candidates"),
            driftProperty("rewriteProof.proofAccepted", "false"),
            driftProperty("rewriteProof.runtimeEquivalencePayload.present", "false"),
            driftProperty("rewriteProof.runtimeEquivalencePayload.complete", "false"),
            driftProperty("rewriteProof.rollbackEvidence.present", "false"),
            driftProperty("rewriteProof.rollbackClean", "false"),
            driftProperty("rewriteProof.approvalAccepted", "false"),
            driftProperty("rewriteProof.mutationAllowed", "false"),
            driftProperty("rewriteProof.selectedIrReplacement", "false"),
            driftProperty("rewriteReviewPackage.status", "not-required"),
            driftProperty("rewriteReviewPackage.firstBlocker", "no-review-candidates"),
            driftProperty("rewriteReviewPackage.required", "false"),
            driftProperty("rewriteReviewPackage.complete", "false"),
            driftProperty("rewriteReviewPackage.conflict.count", "0"),
            driftProperty("rewriteReviewPackage.proofAccepted", "false"),
            driftProperty("rewriteReviewPackage.runtimeEquivalencePayload.present", "false"),
            driftProperty("rewriteReviewPackage.runtimeEquivalencePayload.complete", "false"),
            driftProperty("rewriteReviewPackage.rollbackEvidence.present", "false"),
            driftProperty("rewriteReviewPackage.rollbackClean", "false"),
            driftProperty("rewriteReviewPackage.approvalAccepted", "false"),
            driftProperty("rewriteReviewPackage.mutationAllowed", "false"),
            driftProperty("rewriteReviewPackage.selectionApplied", "false"),
            driftProperty("rewriteReviewPackage.selectedIrReplacement", "false"),
            driftProperty("rewriteReviewPackage.manualReviewOnly", "true"),
            driftProperty("optimizerRule.count", "0"),
            driftProperty("optimizerRule.summary", "none"),
    };

    private GpuBackendSourcePromotionWorkloadGateFormatter() {
    }

    public static String merge(Path path, String sourceKernelResource, String latestGateProperties) throws IOException {
        return merge(path, sourceKernelResource, latestGateProperties, "");
    }

    public static String merge(
            Path path,
            String sourceKernelResource,
            String latestGateProperties,
            String latestSourceSwitchingDecisionProperties
    ) throws IOException {
        return merge(path, sourceKernelResource, latestGateProperties, latestSourceSwitchingDecisionProperties, "");
    }

    public static String merge(
            Path path,
            String sourceKernelResource,
            String latestGateProperties,
            String latestSourceSwitchingDecisionProperties,
            String latestRuntimeIrHandoffProperties
    ) throws IOException {
        return merge(
                path,
                sourceKernelResource,
                latestGateProperties,
                latestSourceSwitchingDecisionProperties,
                latestRuntimeIrHandoffProperties,
                ""
        );
    }

    public static String merge(
            Path path,
            String sourceKernelResource,
            String latestGateProperties,
            String latestSourceSwitchingDecisionProperties,
            String latestRuntimeIrHandoffProperties,
            String latestRuntimeProductionMutationSafetyProperties
    ) throws IOException {
        return merge(
                path,
                sourceKernelResource,
                latestGateProperties,
                latestSourceSwitchingDecisionProperties,
                latestRuntimeIrHandoffProperties,
                latestRuntimeProductionMutationSafetyProperties,
                ""
        );
    }

    public static String merge(
            Path path,
            String sourceKernelResource,
            String latestGateProperties,
            String latestSourceSwitchingDecisionProperties,
            String latestRuntimeIrHandoffProperties,
            String latestRuntimeProductionMutationSafetyProperties,
            String latestI3ReadinessSummaryProperties
    ) throws IOException {
        return merge(
                path,
                sourceKernelResource,
                latestGateProperties,
                latestSourceSwitchingDecisionProperties,
                latestRuntimeIrHandoffProperties,
                latestRuntimeProductionMutationSafetyProperties,
                latestI3ReadinessSummaryProperties,
                ""
        );
    }

    public static String merge(
            Path path,
            String sourceKernelResource,
            String latestGateProperties,
            String latestSourceSwitchingDecisionProperties,
            String latestRuntimeIrHandoffProperties,
            String latestRuntimeProductionMutationSafetyProperties,
            String latestI3ReadinessSummaryProperties,
            String latestRuntimeOptimizerDriftProperties
    ) throws IOException {
        return merge(
                path,
                sourceKernelResource,
                latestGateProperties,
                latestSourceSwitchingDecisionProperties,
                latestRuntimeIrHandoffProperties,
                latestRuntimeProductionMutationSafetyProperties,
                latestI3ReadinessSummaryProperties,
                latestRuntimeOptimizerDriftProperties,
                ""
        );
    }

    public static String merge(
            Path path,
            String sourceKernelResource,
            String latestGateProperties,
            String latestSourceSwitchingDecisionProperties,
            String latestRuntimeIrHandoffProperties,
            String latestRuntimeProductionMutationSafetyProperties,
            String latestI3ReadinessSummaryProperties,
            String latestRuntimeOptimizerDriftProperties,
            String latestOptimizerFamilyEquivalencePayloadProperties
    ) throws IOException {
        return merge(
                path,
                sourceKernelResource,
                latestGateProperties,
                latestSourceSwitchingDecisionProperties,
                latestRuntimeIrHandoffProperties,
                latestRuntimeProductionMutationSafetyProperties,
                latestI3ReadinessSummaryProperties,
                latestRuntimeOptimizerDriftProperties,
                latestOptimizerFamilyEquivalencePayloadProperties,
                ""
        );
    }

    public static String merge(
            Path path,
            String sourceKernelResource,
            String latestGateProperties,
            String latestSourceSwitchingDecisionProperties,
            String latestRuntimeIrHandoffProperties,
            String latestRuntimeProductionMutationSafetyProperties,
            String latestI3ReadinessSummaryProperties,
            String latestRuntimeOptimizerDriftProperties,
            String latestOptimizerFamilyEquivalencePayloadProperties,
            String latestRuntimeExtensionParticipationProperties
    ) throws IOException {
        Properties latest = loadProperties(latestGateProperties);
        Properties latestSourceSwitchingDecision = loadProperties(latestSourceSwitchingDecisionProperties);
        Properties latestRuntimeIrHandoff = loadProperties(latestRuntimeIrHandoffProperties);
        Properties latestRuntimeProductionMutationSafety = loadProperties(latestRuntimeProductionMutationSafetyProperties);
        Properties latestI3ReadinessSummary = loadProperties(latestI3ReadinessSummaryProperties);
        Properties latestRuntimeOptimizerDrift = loadProperties(latestRuntimeOptimizerDriftProperties);
        Properties latestOptimizerFamilyEquivalencePayload = loadProperties(latestOptimizerFamilyEquivalencePayloadProperties);
        Properties latestRuntimeExtensionParticipation = loadProperties(latestRuntimeExtensionParticipationProperties);
        Properties existing = new Properties();
        if (Files.exists(path)) {
            try (InputStream inputStream = Files.newInputStream(path)) {
                existing.load(inputStream);
            }
        }

        LinkedHashMap<String, Properties> kernels = new LinkedHashMap<>();
        int existingKernelCount = parsePositiveInt(existing.getProperty("kernel.count", "0"));
        for (int index = 0; index < existingKernelCount; index++) {
            Properties entry = extractKernelEntry(existing, index);
            String resource = entry.getProperty("sourceKernelResource", "");
            if (!resource.isBlank()) {
                kernels.put(resource, entry);
            }
        }

        Properties latestEntry = new Properties();
        copyGateProperty(latest, latestEntry, "status");
        copyGateProperty(latest, latestEntry, "reviewReady");
        copyGateProperty(latest, latestEntry, "ready");
        copyGateProperty(latest, latestEntry, "reconstructed");
        copyGateProperty(latest, latestEntry, "sourceAvailable");
        copyGateProperty(latest, latestEntry, "sourceParityChecked");
        copyGateProperty(latest, latestEntry, "sourceParityMatched");
        copyGateProperty(latest, latestEntry, "runtimeEquivalencePassed");
        copyGateProperty(latest, latestEntry, "runtimeEquivalence.status");
        copyGateProperty(latest, latestEntry, "runtimeEquivalence.executed");
        copyGateProperty(latest, latestEntry, "runtimeEquivalence.equivalent");
        copyGateProperty(latest, latestEntry, "runtimeEquivalence.inputCase.count");
        copyGateProperty(latest, latestEntry, "runtimeEquivalence.comparedOutput.count");
        copyGateProperty(latest, latestEntry, "fallbackClean");
        copyGateProperty(latest, latestEntry, "selectedSource");
        copyGateProperty(latest, latestEntry, "payloadFormat");
        copyGateProperty(latest, latestEntry, "runtimeLoadMode");
        copySourceSwitchingDecisionProperties(latestSourceSwitchingDecision, latestEntry);
        copyRuntimeIrHandoffProperties(latestRuntimeIrHandoff, latestEntry);
        copyRuntimeProductionMutationSafetyProperties(latestRuntimeProductionMutationSafety, latestEntry);
        copyI3ReadinessSummaryProperties(latestI3ReadinessSummary, latestEntry);
        copyRuntimeOptimizerDriftProperties(latestRuntimeOptimizerDrift, latestEntry);
        copyOptimizerFamilyEquivalencePayloadProperties(latestOptimizerFamilyEquivalencePayload, latestEntry);
        copyRuntimeExtensionParticipationProperties(latestRuntimeExtensionParticipation, latestEntry);
        copyIndexedProperties(latest, latestEntry, "runtimeEquivalence.diagnostic");
        copyIndexedProperties(latest, latestEntry, "reconstruction.blocker");
        copyIndexedProperties(latest, latestEntry, "reconstruction.diagnostic");
        copyDiagnosticProperties(latest, latestEntry);
        latestEntry.setProperty("realWorkloadEvidence", "runtime-snapshot");
        latestEntry.setProperty("sourceKernelResource", normalizeResource(sourceKernelResource));
        latestEntry.setProperty("productionSourceSwitching", "false");
        kernels.put(latestEntry.getProperty("sourceKernelResource"), latestEntry);

        return format(kernels);
    }

    private static Properties loadProperties(String propertiesText) throws IOException {
        Properties properties = new Properties();
        try (StringReader reader = new StringReader(propertiesText == null ? "" : propertiesText)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Properties extractKernelEntry(Properties properties, int index) {
        Properties entry = new Properties();
        String prefix = "kernel." + index + ".";
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                entry.setProperty(key.substring(prefix.length()), properties.getProperty(key));
            }
        }
        return entry;
    }

    private static void copyGateProperty(Properties source, Properties target, String key) {
        target.setProperty(key, source.getProperty(key, "unknown"));
    }

    private static void copyDiagnosticProperties(Properties source, Properties target) {
        int diagnosticCount = parsePositiveInt(source.getProperty("diagnostic.count", "0"));
        target.setProperty("diagnostic.count", Integer.toString(diagnosticCount));
        LinkedHashMap<String, Integer> familyCounts = new LinkedHashMap<>();
        for (int index = 0; index < diagnosticCount; index++) {
            String diagnostic = source.getProperty("diagnostic." + index, "unknown");
            target.setProperty("diagnostic." + index, diagnostic);
            String family = GpuBackendSourcePromotionBlockerClassifier.classify(diagnostic);
            familyCounts.merge(family, 1, Integer::sum);
        }
        target.setProperty("blockerFamily.count", Integer.toString(familyCounts.size()));
        int familyIndex = 0;
        for (Map.Entry<String, Integer> family : familyCounts.entrySet()) {
            target.setProperty("blockerFamily." + familyIndex + ".name", family.getKey());
            target.setProperty("blockerFamily." + familyIndex + ".count", Integer.toString(family.getValue()));
            familyIndex++;
        }
    }

    private static void copyIndexedProperties(Properties source, Properties target, String keyPrefix) {
        int count = parsePositiveInt(source.getProperty(keyPrefix + ".count", "0"));
        target.setProperty(keyPrefix + ".count", Integer.toString(count));
        for (int index = 0; index < count; index++) {
            target.setProperty(keyPrefix + "." + index, source.getProperty(keyPrefix + "." + index, "unknown"));
        }
    }

    private static void copySourceSwitchingDecisionProperties(Properties source, Properties target) {
        if (source == null || source.isEmpty()) {
            target.setProperty("sourceSwitching.status", "not-recorded");
            target.setProperty("sourceSwitching.decision", "not-recorded");
            target.setProperty("sourceSwitching.sourcePromotionStatus", "not-recorded");
            target.setProperty("sourceSwitching.sourcePromotionReviewReady", "false");
            target.setProperty("sourceSwitching.productionProfileRequested", "unknown");
            target.setProperty("sourceSwitching.sourceSelection", "unknown");
            target.setProperty("sourceSwitching.irGpuSourceRequested", "unknown");
            target.setProperty("sourceSwitching.productionSourceSwitching", "false");
            target.setProperty("sourceSwitching.productionSourceSwitchingEnabled", "false");
            target.setProperty("sourceSwitching.productionPromotionDecisionMode", GpuProductionPromotionDecision.DIAGNOSTIC_ONLY);
            target.setProperty("sourceSwitching.productionPromotionOperatorAccepted", "false");
            target.setProperty("sourceSwitching.sourcePromotionFirstBlocker", "source-switching-decision-not-recorded");
            target.setProperty("sourceSwitching.diagnostic.count", "0");
            putRuntimeBackendSourceFallbackFields(target, false);
            return;
        }
        copySourceSwitchingProperty(source, target, "status");
        copySourceSwitchingProperty(source, target, "decision");
        copySourceSwitchingProperty(source, target, "optimizationProfile");
        copySourceSwitchingProperty(source, target, "productionProfileRequested");
        copySourceSwitchingProperty(source, target, "sourceSelection");
        copySourceSwitchingProperty(source, target, "irGpuSourceRequested");
        copySourceSwitchingProperty(source, target, "sourcePromotionStatus", target.getProperty("status", "unknown"));
        copySourceSwitchingProperty(source, target, "sourcePromotionReviewReady", target.getProperty("reviewReady", "unknown"));
        copySourceSwitchingProperty(source, target, "productionSourceSwitching");
        copySourceSwitchingProperty(source, target, "productionSourceSwitchingEnabled");
        copySourceSwitchingProperty(source, target, "productionPromotionDecisionMode");
        copySourceSwitchingProperty(source, target, "productionPromotionOperatorAccepted");
        copySourceSwitchingProperty(source, target, "sourcePromotionFirstBlocker");
        copyIndexedProperties(source, target, "sourceSwitching.diagnostic", "diagnostic");
        copyRuntimeBackendSourceDecisionProperties(source, target);
    }

    private static void copySourceSwitchingProperty(Properties source, Properties target, String key) {
        copySourceSwitchingProperty(source, target, key, "unknown");
    }

    private static void copySourceSwitchingProperty(
            Properties source,
            Properties target,
            String key,
            String fallback
    ) {
        target.setProperty("sourceSwitching." + key, source.getProperty(key, fallback));
    }

    private static void copyRuntimeBackendSourceDecisionProperties(Properties source, Properties target) {
        target.setProperty(
                RUNTIME_BACKEND_SOURCE_PREFIX + "selection.present",
                source.getProperty(RUNTIME_BACKEND_SOURCE_PREFIX + "selection.present", "true")
        );
        copyRuntimeBackendSourceProperty(source, target, "status", "sourceSwitching.status", "unknown");
        copyRuntimeBackendSourceProperty(source, target, "decision", "sourceSwitching.decision", "unknown");
        copyRuntimeBackendSourceProperty(source, target, "selection", "sourceSwitching.sourceSelection", "unknown");
        copyRuntimeBackendSourceProperty(source, target, "irgpuRequested", "sourceSwitching.irGpuSourceRequested", "unknown");
        copyRuntimeBackendSourceProperty(source, target, "ready", "ready", "unknown");
        copyRuntimeBackendSourceProperty(source, target, "reconstructed", "reconstructed", "unknown");
        copyRuntimeBackendSourceProperty(source, target, "available", "sourceAvailable", "unknown");
        copyRuntimeBackendSourceProperty(source, target, "parityChecked", "sourceParityChecked", "unknown");
        copyRuntimeBackendSourceProperty(source, target, "parityMatched", "sourceParityMatched", "unknown");
        copyRuntimeBackendSourceProperty(source, target, "promotionStatus", "sourceSwitching.sourcePromotionStatus", target.getProperty("status", "unknown"));
        copyRuntimeBackendSourceProperty(source, target, "promotionReviewReady", "sourceSwitching.sourcePromotionReviewReady", target.getProperty("reviewReady", "unknown"));
        copyRuntimeBackendSourceProperty(source, target, "promotionFirstBlocker", "sourceSwitching.sourcePromotionFirstBlocker", "unknown");
        copyRuntimeBackendSourceProperty(source, target, "productionProfileRequested", "sourceSwitching.productionProfileRequested", "unknown");
        copyRuntimeBackendSourceProperty(source, target, "productionSwitching", "sourceSwitching.productionSourceSwitching", "false");
        copyRuntimeBackendSourceProperty(source, target, "productionSwitchingEnabled", "sourceSwitching.productionSourceSwitchingEnabled", "false");
        copyRuntimeBackendSourceProperty(source, target, "productionPromotionDecisionMode", "sourceSwitching.productionPromotionDecisionMode", GpuProductionPromotionDecision.DIAGNOSTIC_ONLY);
        copyRuntimeBackendSourceProperty(source, target, "productionPromotionOperatorAccepted", "sourceSwitching.productionPromotionOperatorAccepted", "false");
        copyRuntimeBackendSourceProperty(source, target, "runtimeLoadMode", "runtimeLoadMode", "unknown");
        setRuntimeBackendSourceProperty(
                target,
                "diagnostic",
                source.getProperty(
                        RUNTIME_BACKEND_SOURCE_PREFIX + "diagnostic",
                        target.getProperty("sourceSwitching.diagnostic.0", source.getProperty("diagnostic.0", "unknown"))
                )
        );
        GpuRuntimeArtifactProperties.setPortable(
                target,
                "runtime",
                "status",
                source.getProperty("runtime.status", target.getProperty("sourceSwitching.status", "unknown"))
        );
    }

    private static void copyRuntimeBackendSourceProperty(
            Properties source,
            Properties target,
            String runtimeKey,
            String fallbackKey,
            String fallbackValue
    ) {
        GpuRuntimeArtifactProperties.setPortable(
                target,
                RUNTIME_BACKEND_SOURCE_PORTABLE_PREFIX,
                runtimeKey,
                source.getProperty(
                        RUNTIME_BACKEND_SOURCE_PREFIX + runtimeKey,
                        target.getProperty(fallbackKey, source.getProperty(fallbackKey, fallbackValue))
                )
        );
    }

    private static void putRuntimeBackendSourceFallbackFields(Properties target, boolean present) {
        setRuntimeBackendSourceProperty(target, "selection.present", present);
        setRuntimeBackendSourceProperty(target, "status", target.getProperty("sourceSwitching.status", "not-recorded"));
        setRuntimeBackendSourceProperty(target, "decision", target.getProperty("sourceSwitching.decision", "not-recorded"));
        setRuntimeBackendSourceProperty(target, "selection", target.getProperty("sourceSwitching.sourceSelection", "unknown"));
        setRuntimeBackendSourceProperty(target, "irgpuRequested", target.getProperty("sourceSwitching.irGpuSourceRequested", "unknown"));
        setRuntimeBackendSourceProperty(target, "ready", target.getProperty("ready", "unknown"));
        setRuntimeBackendSourceProperty(target, "reconstructed", target.getProperty("reconstructed", "unknown"));
        setRuntimeBackendSourceProperty(target, "available", target.getProperty("sourceAvailable", "unknown"));
        setRuntimeBackendSourceProperty(target, "parityChecked", target.getProperty("sourceParityChecked", "unknown"));
        setRuntimeBackendSourceProperty(target, "parityMatched", target.getProperty("sourceParityMatched", "unknown"));
        setRuntimeBackendSourceProperty(target, "promotionStatus", target.getProperty("sourceSwitching.sourcePromotionStatus", target.getProperty("status", "not-recorded")));
        setRuntimeBackendSourceProperty(target, "promotionReviewReady", target.getProperty("sourceSwitching.sourcePromotionReviewReady", target.getProperty("reviewReady", "unknown")));
        setRuntimeBackendSourceProperty(target, "promotionFirstBlocker", target.getProperty("sourceSwitching.sourcePromotionFirstBlocker", "unknown"));
        setRuntimeBackendSourceProperty(target, "productionProfileRequested", target.getProperty("sourceSwitching.productionProfileRequested", "unknown"));
        setRuntimeBackendSourceProperty(target, "productionSwitching", target.getProperty("sourceSwitching.productionSourceSwitching", "false"));
        setRuntimeBackendSourceProperty(target, "productionSwitchingEnabled", target.getProperty("sourceSwitching.productionSourceSwitchingEnabled", "false"));
        setRuntimeBackendSourceProperty(target, "productionPromotionDecisionMode", target.getProperty("sourceSwitching.productionPromotionDecisionMode", GpuProductionPromotionDecision.DIAGNOSTIC_ONLY));
        setRuntimeBackendSourceProperty(target, "productionPromotionOperatorAccepted", target.getProperty("sourceSwitching.productionPromotionOperatorAccepted", "false"));
        setRuntimeBackendSourceProperty(target, "runtimeLoadMode", target.getProperty("runtimeLoadMode", "unknown"));
        setRuntimeBackendSourceProperty(target, "diagnostic", target.getProperty("sourceSwitching.diagnostic.0", "unknown"));
        GpuRuntimeArtifactProperties.setPortable(
                target,
                "runtime",
                "status",
                target.getProperty("sourceSwitching.status", "not-recorded")
        );
    }

    private static void setRuntimeBackendSourceProperty(Properties target, String key, Object value) {
        GpuRuntimeArtifactProperties.setPortable(target, RUNTIME_BACKEND_SOURCE_PORTABLE_PREFIX, key, value);
    }

    private static void copyRuntimeIrHandoffProperties(Properties source, Properties target) {
        if (source == null || source.isEmpty()) {
            target.setProperty("runtimeIrHandoff.status", "not-recorded");
            target.setProperty("runtimeIrHandoff.selectedStage", "original");
            target.setProperty("runtimeIrHandoff.original.present", "unknown");
            target.setProperty("runtimeIrHandoff.optimized.present", "false");
            target.setProperty("runtimeIrHandoff.selected.present", "unknown");
            target.setProperty("runtimeIrHandoff.optimizedDiffersFromOriginal", "false");
            target.setProperty("runtimeIrHandoff.optimizationRequiresRollback", "false");
            target.setProperty("runtimeIrHandoff.fallbackDecision", "none");
            target.setProperty("runtimeIrHandoff.optimizedIrRejected", "false");
            target.setProperty("runtimeIrHandoff.backendTarget", "unknown");
            target.setProperty("runtimeIrHandoff.backendFormat", "unknown");
            target.setProperty("runtimeIrHandoff.backendResource", "unknown");
            target.setProperty("runtimeIrHandoff.runtimeLoadMode", target.getProperty("runtimeLoadMode", "unknown"));
            target.setProperty("runtimeIrHandoff.diagnostic.count", "1");
            target.setProperty(
                    "runtimeIrHandoff.diagnostic.0",
                    "runtime IR handoff artifact was not recorded; original runtime source remains selected"
            );
            return;
        }
        copyRuntimeIrHandoffProperty(source, target, "status");
        copyRuntimeIrHandoffProperty(source, target, "selectedStage");
        copyRuntimeIrHandoffProperty(source, target, "original.present");
        copyRuntimeIrHandoffProperty(source, target, "optimized.present");
        copyRuntimeIrHandoffProperty(source, target, "selected.present");
        copyRuntimeIrHandoffProperty(source, target, "original.identity");
        copyRuntimeIrHandoffProperty(source, target, "optimized.identity");
        copyRuntimeIrHandoffProperty(source, target, "selected.identity");
        copyRuntimeIrHandoffProperty(source, target, "optimizedDiffersFromOriginal");
        copyRuntimeIrHandoffProperty(source, target, "optimizationReportPresent");
        copyRuntimeIrHandoffProperty(source, target, "optimizationRequiresRollback");
        copyRuntimeIrHandoffProperty(source, target, "fallbackDecision");
        copyRuntimeIrHandoffProperty(source, target, "optimizedIrRejected");
        copyRuntimeIrHandoffProperty(source, target, "backendTarget");
        copyRuntimeIrHandoffProperty(source, target, "backendFormat");
        copyRuntimeIrHandoffProperty(source, target, "backendResource");
        copyRuntimeIrHandoffProperty(source, target, "runtimeLoadMode");
        copyIndexedProperties(source, target, "runtimeIrHandoff.diagnostic", "diagnostic");
    }

    private static void copyRuntimeIrHandoffProperty(Properties source, Properties target, String key) {
        target.setProperty("runtimeIrHandoff." + key, source.getProperty(key, "unknown"));
    }

    private static void copyRuntimeProductionMutationSafetyProperties(Properties source, Properties target) {
        if (source == null || source.isEmpty()) {
            target.setProperty("runtimeProductionMutationSafety.status", "not-recorded");
            target.setProperty("runtimeProductionMutationSafety.productionMutationEnabled", "false");
            target.setProperty("runtimeProductionMutationSafety.productionGateStatus", "not-recorded");
            target.setProperty("runtimeProductionMutationSafety.productionProfileRequested", "unknown");
            target.setProperty("runtimeProductionMutationSafety.selectedStage", "original");
            target.setProperty("runtimeProductionMutationSafety.optimizedSelected", "false");
            target.setProperty("runtimeProductionMutationSafety.optimizedDiffersFromOriginal", "false");
            target.setProperty("runtimeProductionMutationSafety.optimizedIrRejected", "false");
            target.setProperty("runtimeProductionMutationSafety.fallbackDecision", "none");
            target.setProperty("runtimeProductionMutationSafety.runtimeEquivalencePassed", target.getProperty("runtimeEquivalencePassed", "unknown"));
            target.setProperty("runtimeProductionMutationSafety.fallbackClean", target.getProperty("fallbackClean", "unknown"));
            target.setProperty("runtimeProductionMutationSafety.strategyEvidenceBacked", "false");
            target.setProperty("runtimeProductionMutationSafety.vendorPromotionEligible", "false");
            target.setProperty("runtimeProductionMutationSafety.rollbackClean", "unknown");
            target.setProperty("runtimeProductionMutationSafety.diagnostic.count", "1");
            target.setProperty(
                    "runtimeProductionMutationSafety.diagnostic.0",
                    "production mutation remains disabled because runtime production safety evidence was not recorded"
            );
            putRuntimeProductionMutationSafetyPortableFields(target);
            return;
        }
        copyRuntimeProductionMutationSafetyProperty(source, target, "status", "status");
        copyRuntimeProductionMutationSafetyProperty(source, target, "productionMutationEnabled", "enabled");
        copyRuntimeProductionMutationSafetyProperty(source, target, "productionGateStatus", "productionGateStatus");
        copyRuntimeProductionMutationSafetyProperty(source, target, "productionProfileRequested", "productionProfileRequested");
        copyRuntimeProductionMutationSafetyProperty(source, target, "selectedStage", "selectedStage");
        copyRuntimeProductionMutationSafetyProperty(source, target, "optimizedSelected", "optimizedSelected");
        copyRuntimeProductionMutationSafetyProperty(source, target, "optimizedDiffersFromOriginal", "optimizedDiffersFromOriginal");
        copyRuntimeProductionMutationSafetyProperty(source, target, "optimizedIrRejected", "optimizedIrRejected");
        copyRuntimeProductionMutationSafetyProperty(source, target, "fallbackDecision", "fallbackDecision");
        copyRuntimeProductionMutationSafetyProperty(source, target, "runtimeEquivalencePassed", "runtimeEquivalencePassed");
        copyRuntimeProductionMutationSafetyProperty(source, target, "fallbackClean", "fallbackClean");
        copyRuntimeProductionMutationSafetyProperty(source, target, "strategyEvidenceBacked", "strategyEvidenceBacked");
        copyRuntimeProductionMutationSafetyProperty(source, target, "vendorPromotionEligible", "vendorPromotionEligible");
        copyRuntimeProductionMutationSafetyProperty(source, target, "rollbackClean", "rollbackClean");
        copyIndexedProperties(source, target, "runtimeProductionMutationSafety.diagnostic", "diagnostic");
        if (source.getProperty("runtime.ir.productionMutation.diagnostic") != null) {
            target.setProperty("runtimeProductionMutationSafety.diagnostic.count", "1");
            target.setProperty(
                    "runtimeProductionMutationSafety.diagnostic.0",
                    source.getProperty("runtime.ir.productionMutation.diagnostic")
            );
        }
        putRuntimeProductionMutationSafetyPortableFields(target);
    }

    private static void copyRuntimeProductionMutationSafetyProperty(
            Properties source,
            Properties target,
            String legacyKey,
            String portableKey
    ) {
        target.setProperty(
                "runtimeProductionMutationSafety." + legacyKey,
                source.getProperty(legacyKey, source.getProperty("runtime.ir.productionMutation." + portableKey, "unknown"))
        );
    }

    private static void putRuntimeProductionMutationSafetyPortableFields(Properties target) {
        setRuntimeProductionMutationProperty(
                target,
                "status",
                target.getProperty("runtimeProductionMutationSafety.status", "not-recorded")
        );
        setRuntimeProductionMutationProperty(
                target,
                "enabled",
                target.getProperty("runtimeProductionMutationSafety.productionMutationEnabled", "false")
        );
        setRuntimeProductionMutationProperty(
                target,
                "productionGateStatus",
                target.getProperty("runtimeProductionMutationSafety.productionGateStatus", "not-recorded")
        );
        setRuntimeProductionMutationProperty(
                target,
                "productionProfileRequested",
                target.getProperty("runtimeProductionMutationSafety.productionProfileRequested", "unknown")
        );
        setRuntimeProductionMutationProperty(
                target,
                "selectedStage",
                target.getProperty("runtimeProductionMutationSafety.selectedStage", "original")
        );
        setRuntimeProductionMutationProperty(
                target,
                "optimizedSelected",
                target.getProperty("runtimeProductionMutationSafety.optimizedSelected", "false")
        );
        setRuntimeProductionMutationProperty(
                target,
                "optimizedDiffersFromOriginal",
                target.getProperty("runtimeProductionMutationSafety.optimizedDiffersFromOriginal", "false")
        );
        setRuntimeProductionMutationProperty(
                target,
                "optimizedIrRejected",
                target.getProperty("runtimeProductionMutationSafety.optimizedIrRejected", "false")
        );
        setRuntimeProductionMutationProperty(
                target,
                "fallbackDecision",
                target.getProperty("runtimeProductionMutationSafety.fallbackDecision", "none")
        );
        setRuntimeProductionMutationProperty(
                target,
                "runtimeEquivalencePassed",
                target.getProperty("runtimeProductionMutationSafety.runtimeEquivalencePassed", "unknown")
        );
        setRuntimeProductionMutationProperty(
                target,
                "fallbackClean",
                target.getProperty("runtimeProductionMutationSafety.fallbackClean", "unknown")
        );
        setRuntimeProductionMutationProperty(
                target,
                "strategyEvidenceBacked",
                target.getProperty("runtimeProductionMutationSafety.strategyEvidenceBacked", "false")
        );
        setRuntimeProductionMutationProperty(
                target,
                "vendorPromotionEligible",
                target.getProperty("runtimeProductionMutationSafety.vendorPromotionEligible", "false")
        );
        setRuntimeProductionMutationProperty(
                target,
                "rollbackClean",
                target.getProperty("runtimeProductionMutationSafety.rollbackClean", "unknown")
        );
        setRuntimeProductionMutationProperty(
                target,
                "diagnostic",
                target.getProperty("runtimeProductionMutationSafety.diagnostic.0", "unknown")
        );
    }

    private static void setRuntimeProductionMutationProperty(Properties target, String key, Object value) {
        GpuRuntimeArtifactProperties.setPortable(target, RUNTIME_IR_PRODUCTION_MUTATION_PORTABLE_PREFIX, key, value);
    }

    private static void copyI3ReadinessSummaryProperties(Properties source, Properties target) {
        if (source == null || source.isEmpty()) {
            target.setProperty("i3Readiness.status", "blocked");
            target.setProperty("i3Readiness.backendTarget", "unknown");
            target.setProperty("i3Readiness.backendFormat", "unknown");
            target.setProperty("i3Readiness.backendResource", "unknown");
            target.setProperty("i3Readiness.selectedRuntimeIrStage", target.getProperty("runtimeIrHandoff.selectedStage", "original"));
            target.setProperty("i3Readiness.selectedRuntimeIrIdentity", "unknown");
            target.setProperty("i3Readiness.optimizedIrRejected", target.getProperty("runtimeIrHandoff.optimizedIrRejected", "false"));
            target.setProperty("i3Readiness.fallbackDecision", target.getProperty("runtimeIrHandoff.fallbackDecision", "none"));
            target.setProperty("i3Readiness.sourceReconstructed", target.getProperty("reconstructed", "unknown"));
            target.setProperty("i3Readiness.sourceReady", target.getProperty("ready", "unknown"));
            target.setProperty("i3Readiness.sourceAvailable", target.getProperty("sourceAvailable", "unknown"));
            target.setProperty("i3Readiness.sourceParityChecked", target.getProperty("sourceParityChecked", "unknown"));
            target.setProperty("i3Readiness.sourceParityMatched", target.getProperty("sourceParityMatched", "unknown"));
            target.setProperty("i3Readiness.runtimeEquivalencePassed", target.getProperty("runtimeEquivalencePassed", "unknown"));
            target.setProperty("i3Readiness.sourcePromotionStatus", target.getProperty("status", "unknown"));
            target.setProperty("i3Readiness.sourcePromotionReviewReady", target.getProperty("reviewReady", "unknown"));
            target.setProperty("i3Readiness.optimizerProductionGateStatus", "not-recorded");
            target.setProperty("i3Readiness.productionProfileRequested", target.getProperty("sourceSwitching.productionProfileRequested", "unknown"));
            target.setProperty("i3Readiness.productionMutationEnabled", "false");
            target.setProperty("i3Readiness.blocker.count", "2");
            target.setProperty("i3Readiness.blocker.0", "runtime-i3-readiness-artifact-missing");
            target.setProperty("i3Readiness.blocker.1", "production-mutation-disabled");
            target.setProperty("i3Readiness.diagnostic.count", "1");
            target.setProperty(
                    "i3Readiness.diagnostic.0",
                    "I3 readiness evidence was not recorded for this workload kernel; treating it as blocked"
            );
            return;
        }
        copyI3ReadinessSummaryProperty(source, target, "status");
        copyI3ReadinessSummaryProperty(source, target, "backendTarget");
        copyI3ReadinessSummaryProperty(source, target, "backendFormat");
        copyI3ReadinessSummaryProperty(source, target, "backendResource");
        copyI3ReadinessSummaryProperty(source, target, "selectedRuntimeIrStage");
        copyI3ReadinessSummaryProperty(source, target, "selectedRuntimeIrIdentity");
        copyI3ReadinessSummaryProperty(source, target, "optimizedIrRejected");
        copyI3ReadinessSummaryProperty(source, target, "fallbackDecision");
        copyI3ReadinessSummaryProperty(source, target, "sourceReconstructed");
        copyI3ReadinessSummaryProperty(source, target, "sourceReady");
        copyI3ReadinessSummaryProperty(source, target, "sourceAvailable");
        copyI3ReadinessSummaryProperty(source, target, "sourceParityChecked");
        copyI3ReadinessSummaryProperty(source, target, "sourceParityMatched");
        copyI3ReadinessSummaryProperty(source, target, "runtimeEquivalencePassed");
        copyI3ReadinessSummaryProperty(source, target, "sourcePromotionStatus");
        copyI3ReadinessSummaryProperty(source, target, "sourcePromotionReviewReady");
        copyI3ReadinessSummaryProperty(source, target, "optimizerProductionGateStatus");
        copyI3ReadinessSummaryProperty(source, target, "productionProfileRequested");
        copyI3ReadinessSummaryProperty(source, target, "productionMutationEnabled");
        copyIndexedProperties(source, target, "i3Readiness.blocker", "blocker");
        copyIndexedProperties(source, target, "i3Readiness.diagnostic", "diagnostic");
    }

    private static void copyI3ReadinessSummaryProperty(Properties source, Properties target, String key) {
        target.setProperty("i3Readiness." + key, source.getProperty(key, "unknown"));
    }

    private static void copyRuntimeOptimizerDriftProperties(Properties source, Properties target) {
        if (source == null || source.isEmpty()) {
            target.setProperty("runtimeOptimizerDrift.status", "not-recorded");
            copyDefaultRuntimeOptimizerDriftProperties(target);
            target.setProperty("runtimeOptimizerDrift.fallbackDecision", target.getProperty("runtimeIrHandoff.fallbackDecision", "none"));
            target.setProperty("runtimeOptimizerDrift.selectedRuntimeIrStage", target.getProperty("runtimeIrHandoff.selectedStage", "original"));
            target.setProperty("runtimeOptimizerDrift.selectedRuntimeIrIdentity", target.getProperty("runtimeIrHandoff.selected.identity", "unknown"));
            target.setProperty("runtimeOptimizerDrift.optimizedIrRejected", target.getProperty("runtimeIrHandoff.optimizedIrRejected", "false"));
            target.setProperty("runtimeOptimizerDrift.strategyName", "unknown");
            target.setProperty("runtimeOptimizerDrift.selectedProfile", "unknown");
            target.setProperty("runtimeOptimizerDrift.baselineStatus", "unknown");
            target.setProperty("runtimeOptimizerDrift.promotionEligible", "false");
            target.setProperty("runtimeOptimizerDrift.productionGateStatus", target.getProperty("runtimeProductionMutationSafety.productionGateStatus", "not-recorded"));
            target.setProperty("runtimeOptimizerDrift.productionProfileRequested", target.getProperty("runtimeProductionMutationSafety.productionProfileRequested", "unknown"));
            return;
        }
        copyRuntimeOptimizerDriftPropertiesFromSource(source, target);
        copyIndexedPropertyGroup(source, target, "runtimeOptimizerDrift.optimizerRule", "optimizerRule");
        copyRuntimeOptimizerDriftProperty(source, target, "optimizerFamily.count");
        copyRuntimeOptimizerDriftProperty(source, target, "optimizerFamily.promotionReady.count");
        copyRuntimeOptimizerDriftProperty(source, target, "optimizerFamily.summary");
        copyRuntimeOptimizerDriftProperty(source, target, "fallbackDecision");
        copyRuntimeOptimizerDriftProperty(source, target, "selectedRuntimeIrStage");
        copyRuntimeOptimizerDriftProperty(source, target, "selectedRuntimeIrIdentity");
        copyRuntimeOptimizerDriftProperty(source, target, "optimizedIrRejected");
        copyRuntimeOptimizerDriftProperty(source, target, "strategyName");
        copyRuntimeOptimizerDriftProperty(source, target, "selectedProfile");
        copyRuntimeOptimizerDriftProperty(source, target, "baselineStatus");
        copyRuntimeOptimizerDriftProperty(source, target, "promotionEligible");
        copyRuntimeOptimizerDriftProperty(source, target, "productionGateStatus");
        copyRuntimeOptimizerDriftProperty(source, target, "productionProfileRequested");
        target.setProperty("runtimeOptimizerDrift.status", "recorded");
    }

    private static void copyDefaultRuntimeOptimizerDriftProperties(Properties target) {
        for (DriftProperty property : RUNTIME_OPTIMIZER_DRIFT_PROPERTIES) {
            target.setProperty(RUNTIME_OPTIMIZER_DRIFT_PREFIX + property.key(), property.emptySourceDefault());
        }
    }

    private static void copyRuntimeOptimizerDriftPropertiesFromSource(Properties source, Properties target) {
        for (DriftProperty property : RUNTIME_OPTIMIZER_DRIFT_PROPERTIES) {
            copyRuntimeOptimizerDriftProperty(source, target, property.key(), property.recordedSourceDefault());
        }
    }

    private static void copyRuntimeOptimizerDriftProperty(Properties source, Properties target, String key) {
        target.setProperty(RUNTIME_OPTIMIZER_DRIFT_PREFIX + key, source.getProperty(key, "unknown"));
    }

    private static void copyRuntimeOptimizerDriftProperty(
            Properties source,
            Properties target,
            String key,
            String defaultValue
    ) {
        target.setProperty(RUNTIME_OPTIMIZER_DRIFT_PREFIX + key, source.getProperty(key, defaultValue));
    }

    private static DriftProperty driftProperty(String key, String defaultValue) {
        return driftProperty(key, defaultValue, defaultValue);
    }

    private static DriftProperty driftProperty(String key, String emptySourceDefault, String recordedSourceDefault) {
        return new DriftProperty(key, emptySourceDefault, recordedSourceDefault);
    }

    private record DriftProperty(String key, String emptySourceDefault, String recordedSourceDefault) {
    }

    private static void copyIndexedPropertyGroup(
            Properties source,
            Properties target,
            String targetKeyPrefix,
            String sourceKeyPrefix
    ) {
        int count = parsePositiveInt(source.getProperty(sourceKeyPrefix + ".count", "0"));
        target.setProperty(targetKeyPrefix + ".count", Integer.toString(count));
        String sourceIndexedPrefix = sourceKeyPrefix + ".";
        String targetIndexedPrefix = targetKeyPrefix + ".";
        for (String key : source.stringPropertyNames().stream().sorted().toList()) {
            if (!key.startsWith(sourceIndexedPrefix)) {
                continue;
            }
            String suffix = key.substring(sourceIndexedPrefix.length());
            int index = indexedPropertyIndex(suffix);
            if (index < 0 || index >= count) {
                continue;
            }
            target.setProperty(targetIndexedPrefix + suffix, source.getProperty(key, "unknown"));
        }
    }

    private static void copyOptimizerFamilyEquivalencePayloadProperties(Properties source, Properties target) {
        if (source == null || source.isEmpty()) {
            target.setProperty("optimizerFamilyPayload.status", "not-recorded");
            target.setProperty("optimizerFamilyPayload.family.count", "0");
            target.setProperty("optimizerFamilyPayload.family.complete.count", "0");
            target.setProperty("optimizerFamilyPayload.family.complete.all", "false");
            return;
        }
        copyOptimizerFamilyEquivalencePayloadProperty(source, target, "status");
        copyOptimizerFamilyEquivalencePayloadProperty(source, target, "runtimeEquivalence.passed");
        copyOptimizerFamilyEquivalencePayloadProperty(source, target, "family.count");
        copyOptimizerFamilyEquivalencePayloadProperty(source, target, "family.complete.count");
        copyOptimizerFamilyEquivalencePayloadProperty(source, target, "family.complete.all");
    }

    private static void copyOptimizerFamilyEquivalencePayloadProperty(Properties source, Properties target, String key) {
        target.setProperty("optimizerFamilyPayload." + key, source.getProperty(key, "unknown"));
    }

    private static void copyIndexedProperties(
            Properties source,
            Properties target,
            String targetKeyPrefix,
            String sourceKeyPrefix
    ) {
        int count = parsePositiveInt(source.getProperty(sourceKeyPrefix + ".count", "0"));
        target.setProperty(targetKeyPrefix + ".count", Integer.toString(count));
        for (int index = 0; index < count; index++) {
            target.setProperty(targetKeyPrefix + "." + index, source.getProperty(sourceKeyPrefix + "." + index, "unknown"));
        }
    }

    private static String format(LinkedHashMap<String, Properties> kernels) {
        boolean anyReviewReady = kernels.values().stream()
                .anyMatch(entry -> "true".equals(entry.getProperty("reviewReady")));
        boolean allReviewReady = !kernels.isEmpty()
                && kernels.values().stream().allMatch(entry -> "true".equals(entry.getProperty("reviewReady")));
        boolean allSourceParityMatched = !kernels.isEmpty()
                && kernels.values().stream().allMatch(entry -> "true".equals(entry.getProperty("sourceParityMatched")));
        boolean allRuntimeEquivalencePassed = !kernels.isEmpty()
                && kernels.values().stream().allMatch(entry -> "true".equals(entry.getProperty("runtimeEquivalencePassed")));
        long productionSourceSwitchingEnabledCount = kernels.values().stream()
                .filter(GpuBackendSourcePromotionWorkloadGateFormatter::entryProductionSourceSwitchingEnabled)
                .count();
        long productionPromotionEnabledCount = kernels.values().stream()
                .filter(GpuBackendSourcePromotionWorkloadGateFormatter::entryProductionPromotionEnabled)
                .count();
        long productionPromotionOperatorAcceptedCount = kernels.values().stream()
                .filter(GpuBackendSourcePromotionWorkloadGateFormatter::entryProductionPromotionOperatorAccepted)
                .count();
        long productionSourceDecisionCount = kernels.values().stream()
                .filter(GpuBackendSourcePromotionWorkloadGateFormatter::entryProductionSourceDecision)
                .count();
        boolean allProductionSourceSwitchingEnabled = !kernels.isEmpty()
                && productionSourceSwitchingEnabledCount == kernels.size();
        boolean allProductionPromotionEnabled = !kernels.isEmpty()
                && productionPromotionEnabledCount == kernels.size();
        boolean allProductionPromotionOperatorAccepted = !kernels.isEmpty()
                && productionPromotionOperatorAcceptedCount == kernels.size();
        boolean allProductionSourceDecisions = !kernels.isEmpty()
                && productionSourceDecisionCount == kernels.size();
        boolean productionSourceSwitchingEnabled = allReviewReady
                && allSourceParityMatched
                && allRuntimeEquivalencePassed
                && allProductionSourceSwitchingEnabled
                && allProductionPromotionEnabled
                && allProductionPromotionOperatorAccepted
                && allProductionSourceDecisions;
        String status = productionSourceSwitchingEnabled ? "production-enabled" : allReviewReady ? "review-ready" : "blocked";
        LinkedHashMap<String, Integer> aggregateFamilies = aggregatePromotionBlockerFamilies(kernels);
        LinkedHashMap<String, Integer> aggregateSourceDecisions = aggregateSourceDecisions(kernels);
        LinkedHashMap<String, Integer> aggregateSourcePromotionFirstBlockers = aggregateSourcePromotionFirstBlockers(kernels);
        LinkedHashMap<String, Integer> aggregateSourcePromotionFirstBlockerFamilies = aggregateSourcePromotionFirstBlockerFamilies(
                aggregateSourcePromotionFirstBlockers
        );
        StringBuilder builder = new StringBuilder();
        builder.append("status=").append(status).append('\n');
        builder.append("reviewReady=").append(allReviewReady).append('\n');
        builder.append("sourceParityMatched=").append(allSourceParityMatched).append('\n');
        builder.append("runtimeEquivalencePassed=").append(allRuntimeEquivalencePassed).append('\n');
        builder.append("realWorkloadEvidence=runtime-snapshot\n");
        builder.append("scope=real-workload\n");
        builder.append("productionSourceSwitching=").append(productionSourceSwitchingEnabled ? "enabled" : "false").append('\n');
        builder.append("runtime.backend.source.productionSwitchingEnabled.count=").append(productionSourceSwitchingEnabledCount).append('\n');
        builder.append("runtime.backend.source.productionSwitchingEnabled.all=").append(allProductionSourceSwitchingEnabled).append('\n');
        builder.append("productionSourceSwitchingEnabled.count=").append(productionSourceSwitchingEnabledCount).append('\n');
        builder.append("productionSourceSwitchingEnabled.all=").append(allProductionSourceSwitchingEnabled).append('\n');
        builder.append("runtime.backend.source.productionPromotionDecisionMode.productionEnabled.count=").append(productionPromotionEnabledCount).append('\n');
        builder.append("runtime.backend.source.productionPromotionDecisionMode.productionEnabled.all=").append(allProductionPromotionEnabled).append('\n');
        builder.append("productionPromotionDecisionMode.productionEnabled.count=").append(productionPromotionEnabledCount).append('\n');
        builder.append("productionPromotionDecisionMode.productionEnabled.all=").append(allProductionPromotionEnabled).append('\n');
        builder.append("runtime.backend.source.productionPromotionOperatorAccepted.count=").append(productionPromotionOperatorAcceptedCount).append('\n');
        builder.append("runtime.backend.source.productionPromotionOperatorAccepted.all=").append(allProductionPromotionOperatorAccepted).append('\n');
        builder.append("productionPromotionOperatorAccepted.count=").append(productionPromotionOperatorAcceptedCount).append('\n');
        builder.append("productionPromotionOperatorAccepted.all=").append(allProductionPromotionOperatorAccepted).append('\n');
        builder.append("runtime.backend.source.productionDecision.count=").append(productionSourceDecisionCount).append('\n');
        builder.append("runtime.backend.source.productionDecision.all=").append(allProductionSourceDecisions).append('\n');
        builder.append("sourceSwitching.productionDecision.count=").append(productionSourceDecisionCount).append('\n');
        builder.append("sourceSwitching.productionDecision.all=").append(allProductionSourceDecisions).append('\n');
        appendAggregateRuntimeExtensionParticipation(builder, kernels);
        builder.append("sourceSwitching.count=").append(kernels.size()).append('\n');
        builder.append("runtime.backend.source.decision.count=").append(aggregateSourceDecisions.size()).append('\n');
        int sourceDecisionIndex = 0;
        for (Map.Entry<String, Integer> decision : aggregateSourceDecisions.entrySet()) {
            builder.append("runtime.backend.source.decision.").append(sourceDecisionIndex).append(".name=").append(decision.getKey()).append('\n');
            builder.append("runtime.backend.source.decision.").append(sourceDecisionIndex).append(".count=").append(decision.getValue()).append('\n');
            sourceDecisionIndex++;
        }
        builder.append("runtime.backend.source.promotionFirstBlocker.count=").append(aggregateSourcePromotionFirstBlockers.size()).append('\n');
        builder.append("sourceSwitching.sourcePromotionFirstBlocker.count=").append(aggregateSourcePromotionFirstBlockers.size()).append('\n');
        int sourcePromotionBlockerIndex = 0;
        for (Map.Entry<String, Integer> blocker : aggregateSourcePromotionFirstBlockers.entrySet()) {
            builder.append("runtime.backend.source.promotionFirstBlocker.").append(sourcePromotionBlockerIndex).append(".name=").append(blocker.getKey()).append('\n');
            builder.append("runtime.backend.source.promotionFirstBlocker.").append(sourcePromotionBlockerIndex).append(".count=").append(blocker.getValue()).append('\n');
            builder.append("sourceSwitching.sourcePromotionFirstBlocker.").append(sourcePromotionBlockerIndex).append(".name=").append(blocker.getKey()).append('\n');
            builder.append("sourceSwitching.sourcePromotionFirstBlocker.").append(sourcePromotionBlockerIndex).append(".count=").append(blocker.getValue()).append('\n');
            sourcePromotionBlockerIndex++;
        }
        builder.append("runtime.backend.source.promotionFirstBlockerFamily.count=").append(aggregateSourcePromotionFirstBlockerFamilies.size()).append('\n');
        builder.append("sourceSwitching.sourcePromotionFirstBlockerFamily.count=").append(aggregateSourcePromotionFirstBlockerFamilies.size()).append('\n');
        int sourcePromotionBlockerFamilyIndex = 0;
        for (Map.Entry<String, Integer> family : aggregateSourcePromotionFirstBlockerFamilies.entrySet()) {
            builder.append("runtime.backend.source.promotionFirstBlockerFamily.").append(sourcePromotionBlockerFamilyIndex).append(".name=").append(family.getKey()).append('\n');
            builder.append("runtime.backend.source.promotionFirstBlockerFamily.").append(sourcePromotionBlockerFamilyIndex).append(".count=").append(family.getValue()).append('\n');
            builder.append("sourceSwitching.sourcePromotionFirstBlockerFamily.").append(sourcePromotionBlockerFamilyIndex).append(".name=").append(family.getKey()).append('\n');
            builder.append("sourceSwitching.sourcePromotionFirstBlockerFamily.").append(sourcePromotionBlockerFamilyIndex).append(".count=").append(family.getValue()).append('\n');
            sourcePromotionBlockerFamilyIndex++;
        }
        builder.append("kernel.count=").append(kernels.size()).append('\n');
        builder.append("blockerFamily.count=").append(aggregateFamilies.size()).append('\n');
        int familyIndex = 0;
        for (Map.Entry<String, Integer> family : aggregateFamilies.entrySet()) {
            builder.append("blockerFamily.").append(familyIndex).append(".name=").append(family.getKey()).append('\n');
            builder.append("blockerFamily.").append(familyIndex).append(".count=").append(family.getValue()).append('\n');
            familyIndex++;
        }
        builder.append("reason=").append(productionSourceSwitchingEnabled
                ? "all workload kernels reached production IrGpu source switching with accepted promotion evidence"
                : anyReviewReady
                ? "one or more workload kernels reached review-ready, but production source switching is disabled"
                : "real workload runtime snapshots remain fail-closed until source promotion is explicitly enabled")
                .append('\n');
        int index = 0;
        for (Properties entry : kernels.values()) {
            appendKernelEntry(builder, index, entry);
            index++;
        }
        return builder.toString();
    }

    private static void appendKernelEntry(StringBuilder builder, int index, Properties entry) {
        String prefix = "kernel." + index + ".";
        builder.append(prefix).append("sourceKernelResource=").append(entry.getProperty("sourceKernelResource", "unknown")).append('\n');
        builder.append(prefix).append("status=").append(entry.getProperty("status", "unknown")).append('\n');
        builder.append(prefix).append("reviewReady=").append(entry.getProperty("reviewReady", "unknown")).append('\n');
        builder.append(prefix).append("ready=").append(entry.getProperty("ready", "unknown")).append('\n');
        builder.append(prefix).append("reconstructed=").append(entry.getProperty("reconstructed", "unknown")).append('\n');
        builder.append(prefix).append("sourceAvailable=").append(entry.getProperty("sourceAvailable", "unknown")).append('\n');
        builder.append(prefix).append("sourceParityChecked=").append(entry.getProperty("sourceParityChecked", "unknown")).append('\n');
        builder.append(prefix).append("sourceParityMatched=").append(entry.getProperty("sourceParityMatched", "unknown")).append('\n');
        builder.append(prefix).append("runtimeEquivalencePassed=").append(entry.getProperty("runtimeEquivalencePassed", "unknown")).append('\n');
        builder.append(prefix).append("runtimeEquivalence.status=").append(entry.getProperty("runtimeEquivalence.status", "unknown")).append('\n');
        builder.append(prefix).append("runtimeEquivalence.executed=").append(entry.getProperty("runtimeEquivalence.executed", "unknown")).append('\n');
        builder.append(prefix).append("runtimeEquivalence.equivalent=").append(entry.getProperty("runtimeEquivalence.equivalent", "unknown")).append('\n');
        builder.append(prefix).append("runtimeEquivalence.inputCase.count=").append(entry.getProperty("runtimeEquivalence.inputCase.count", "unknown")).append('\n');
        builder.append(prefix).append("runtimeEquivalence.comparedOutput.count=").append(entry.getProperty("runtimeEquivalence.comparedOutput.count", "unknown")).append('\n');
        builder.append(prefix).append("fallbackClean=").append(entry.getProperty("fallbackClean", "unknown")).append('\n');
        builder.append(prefix).append("selectedSource=").append(entry.getProperty("selectedSource", "unknown")).append('\n');
        builder.append(prefix).append("payloadFormat=").append(entry.getProperty("payloadFormat", "unknown")).append('\n');
        builder.append(prefix).append("runtimeLoadMode=").append(entry.getProperty("runtimeLoadMode", "unknown")).append('\n');
        builder.append(prefix).append("realWorkloadEvidence=").append(entry.getProperty("realWorkloadEvidence", "runtime-snapshot")).append('\n');
        builder.append(prefix).append("productionSourceSwitching=").append(entryProductionSourceSwitching(entry)).append('\n');
        appendSourceSwitchingDecision(builder, prefix, entry);
        appendRuntimeIrHandoff(builder, prefix, entry);
        appendRuntimeProductionMutationSafety(builder, prefix, entry);
        appendI3ReadinessSummary(builder, prefix, entry);
        appendRuntimeOptimizerDrift(builder, prefix, entry);
        appendRuntimeExtensionParticipation(builder, prefix, entry);
        appendIndexedProperties(builder, prefix, entry, "runtimeEquivalence.diagnostic");
        appendIndexedProperties(builder, prefix, entry, "reconstruction.blocker");
        appendIndexedProperties(builder, prefix, entry, "reconstruction.diagnostic");
        int diagnosticCount = parsePositiveInt(entry.getProperty("diagnostic.count", "0"));
        builder.append(prefix).append("diagnostic.count=").append(diagnosticCount).append('\n');
        for (int diagnosticIndex = 0; diagnosticIndex < diagnosticCount; diagnosticIndex++) {
            builder.append(prefix)
                    .append("diagnostic.")
                    .append(diagnosticIndex)
                    .append('=')
                    .append(entry.getProperty("diagnostic." + diagnosticIndex, "unknown"))
                    .append('\n');
        }
        int familyCount = parsePositiveInt(entry.getProperty("blockerFamily.count", "0"));
        builder.append(prefix).append("blockerFamily.count=").append(familyCount).append('\n');
        for (int familyIndex = 0; familyIndex < familyCount; familyIndex++) {
            builder.append(prefix)
                    .append("blockerFamily.")
                    .append(familyIndex)
                    .append(".name=")
                    .append(entry.getProperty("blockerFamily." + familyIndex + ".name", "unknown"))
                    .append('\n');
            builder.append(prefix)
                    .append("blockerFamily.")
                    .append(familyIndex)
                    .append(".count=")
                    .append(entry.getProperty("blockerFamily." + familyIndex + ".count", "0"))
                    .append('\n');
        }
    }

    private static void appendSourceSwitchingDecision(StringBuilder builder, String prefix, Properties entry) {
        builder.append(prefix).append("sourceSwitching.status=")
                .append(runtimeBackendSourceProperty(entry, "status", "sourceSwitching.status", "not-recorded"))
                .append('\n');
        builder.append(prefix).append("sourceSwitching.decision=")
                .append(runtimeBackendSourceProperty(entry, "decision", "sourceSwitching.decision", "not-recorded"))
                .append('\n');
        builder.append(prefix).append("sourceSwitching.optimizationProfile=").append(entry.getProperty("sourceSwitching.optimizationProfile", "unknown")).append('\n');
        builder.append(prefix).append("sourceSwitching.productionProfileRequested=")
                .append(runtimeBackendSourceProperty(entry, "productionProfileRequested", "sourceSwitching.productionProfileRequested", "unknown"))
                .append('\n');
        builder.append(prefix).append("sourceSwitching.sourceSelection=")
                .append(runtimeBackendSourceProperty(entry, "selection", "sourceSwitching.sourceSelection", "unknown"))
                .append('\n');
        builder.append(prefix).append("sourceSwitching.irGpuSourceRequested=")
                .append(runtimeBackendSourceProperty(entry, "irgpuRequested", "sourceSwitching.irGpuSourceRequested", "unknown"))
                .append('\n');
        builder.append(prefix).append("sourceSwitching.productionSourceSwitching=")
                .append(runtimeBackendSourceProperty(entry, "productionSwitching", "sourceSwitching.productionSourceSwitching", "false"))
                .append('\n');
        builder.append(prefix).append("sourceSwitching.productionSourceSwitchingEnabled=")
                .append(runtimeBackendSourceProperty(entry, "productionSwitchingEnabled", "sourceSwitching.productionSourceSwitchingEnabled", "false"))
                .append('\n');
        builder.append(prefix).append("sourceSwitching.productionPromotionDecisionMode=")
                .append(runtimeBackendSourceProperty(entry, "productionPromotionDecisionMode", "sourceSwitching.productionPromotionDecisionMode", GpuProductionPromotionDecision.DIAGNOSTIC_ONLY))
                .append('\n');
        builder.append(prefix).append("sourceSwitching.productionPromotionOperatorAccepted=")
                .append(runtimeBackendSourceProperty(entry, "productionPromotionOperatorAccepted", "sourceSwitching.productionPromotionOperatorAccepted", "false"))
                .append('\n');
        builder.append(prefix).append("sourceSwitching.sourcePromotionFirstBlocker=")
                .append(runtimeBackendSourceProperty(entry, "promotionFirstBlocker", "sourceSwitching.sourcePromotionFirstBlocker", "unknown"))
                .append('\n');
        appendIndexedProperties(builder, prefix, entry, "sourceSwitching.diagnostic");
        appendRuntimeBackendSourceDecision(builder, prefix, entry);
    }

    private static String runtimeBackendSourceProperty(
            Properties entry,
            String runtimeKey,
            String legacyKey,
            String fallback
    ) {
        return entry.getProperty(RUNTIME_BACKEND_SOURCE_PREFIX + runtimeKey, entry.getProperty(legacyKey, fallback));
    }

    private static void appendRuntimeBackendSourceDecision(StringBuilder builder, String prefix, Properties entry) {
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "selection.present", "false");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "status", "not-recorded");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "decision", "not-recorded");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "selection", "unknown");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "irgpuRequested", "unknown");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "ready", "unknown");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "reconstructed", "unknown");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "available", "unknown");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "parityChecked", "unknown");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "parityMatched", "unknown");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "promotionStatus", "unknown");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "promotionReviewReady", "unknown");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "promotionFirstBlocker", "unknown");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "productionProfileRequested", "unknown");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "productionSwitching", "false");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "productionSwitchingEnabled", "false");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "productionPromotionDecisionMode", GpuProductionPromotionDecision.DIAGNOSTIC_ONLY);
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "productionPromotionOperatorAccepted", "false");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "runtimeLoadMode", "unknown");
        appendRuntimeBackendSourceProperty(builder, prefix, entry, "diagnostic", "unknown");
        builder.append(prefix).append("runtime.status=").append(entry.getProperty("runtime.status", "not-recorded")).append('\n');
    }

    private static void appendRuntimeBackendSourceProperty(
            StringBuilder builder,
            String prefix,
            Properties entry,
            String key,
            String fallback
    ) {
        String propertyName = RUNTIME_BACKEND_SOURCE_PREFIX + key;
        builder.append(prefix).append(propertyName).append('=').append(entry.getProperty(propertyName, fallback)).append('\n');
    }

    private static void appendRuntimeIrHandoff(StringBuilder builder, String prefix, Properties entry) {
        builder.append(prefix).append("runtimeIrHandoff.status=").append(entry.getProperty("runtimeIrHandoff.status", "not-recorded")).append('\n');
        builder.append(prefix).append("runtimeIrHandoff.selectedStage=").append(entry.getProperty("runtimeIrHandoff.selectedStage", "unknown")).append('\n');
        builder.append(prefix).append("runtimeIrHandoff.original.present=").append(entry.getProperty("runtimeIrHandoff.original.present", "unknown")).append('\n');
        builder.append(prefix).append("runtimeIrHandoff.optimized.present=").append(entry.getProperty("runtimeIrHandoff.optimized.present", "unknown")).append('\n');
        builder.append(prefix).append("runtimeIrHandoff.selected.present=").append(entry.getProperty("runtimeIrHandoff.selected.present", "unknown")).append('\n');
        builder.append(prefix).append("runtimeIrHandoff.original.identity=").append(entry.getProperty("runtimeIrHandoff.original.identity", "unknown")).append('\n');
        builder.append(prefix).append("runtimeIrHandoff.optimized.identity=").append(entry.getProperty("runtimeIrHandoff.optimized.identity", "unknown")).append('\n');
        builder.append(prefix).append("runtimeIrHandoff.selected.identity=").append(entry.getProperty("runtimeIrHandoff.selected.identity", "unknown")).append('\n');
        builder.append(prefix).append("runtimeIrHandoff.optimizedDiffersFromOriginal=").append(entry.getProperty("runtimeIrHandoff.optimizedDiffersFromOriginal", "unknown")).append('\n');
        builder.append(prefix).append("runtimeIrHandoff.optimizationReportPresent=").append(entry.getProperty("runtimeIrHandoff.optimizationReportPresent", "unknown")).append('\n');
        builder.append(prefix).append("runtimeIrHandoff.optimizationRequiresRollback=").append(entry.getProperty("runtimeIrHandoff.optimizationRequiresRollback", "unknown")).append('\n');
        builder.append(prefix).append("runtimeIrHandoff.fallbackDecision=").append(entry.getProperty("runtimeIrHandoff.fallbackDecision", "unknown")).append('\n');
        builder.append(prefix).append("runtimeIrHandoff.optimizedIrRejected=").append(entry.getProperty("runtimeIrHandoff.optimizedIrRejected", "unknown")).append('\n');
        builder.append(prefix).append("runtimeIrHandoff.backendTarget=").append(entry.getProperty("runtimeIrHandoff.backendTarget", "unknown")).append('\n');
        builder.append(prefix).append("runtimeIrHandoff.backendFormat=").append(entry.getProperty("runtimeIrHandoff.backendFormat", "unknown")).append('\n');
        builder.append(prefix).append("runtimeIrHandoff.backendResource=").append(entry.getProperty("runtimeIrHandoff.backendResource", "unknown")).append('\n');
        builder.append(prefix).append("runtimeIrHandoff.runtimeLoadMode=").append(entry.getProperty("runtimeIrHandoff.runtimeLoadMode", "unknown")).append('\n');
        appendIndexedProperties(builder, prefix, entry, "runtimeIrHandoff.diagnostic");
    }

    private static void appendRuntimeProductionMutationSafety(StringBuilder builder, String prefix, Properties entry) {
        appendRuntimeProductionMutationProperty(builder, prefix, entry, "status", "not-recorded");
        appendRuntimeProductionMutationProperty(builder, prefix, entry, "enabled", "false");
        appendRuntimeProductionMutationProperty(builder, prefix, entry, "productionGateStatus", "not-recorded");
        appendRuntimeProductionMutationProperty(builder, prefix, entry, "productionProfileRequested", "unknown");
        appendRuntimeProductionMutationProperty(builder, prefix, entry, "selectedStage", "unknown");
        appendRuntimeProductionMutationProperty(builder, prefix, entry, "optimizedSelected", "unknown");
        appendRuntimeProductionMutationProperty(builder, prefix, entry, "optimizedDiffersFromOriginal", "unknown");
        appendRuntimeProductionMutationProperty(builder, prefix, entry, "optimizedIrRejected", "unknown");
        appendRuntimeProductionMutationProperty(builder, prefix, entry, "fallbackDecision", "unknown");
        appendRuntimeProductionMutationProperty(builder, prefix, entry, "runtimeEquivalencePassed", "unknown");
        appendRuntimeProductionMutationProperty(builder, prefix, entry, "fallbackClean", "unknown");
        appendRuntimeProductionMutationProperty(builder, prefix, entry, "strategyEvidenceBacked", "unknown");
        appendRuntimeProductionMutationProperty(builder, prefix, entry, "vendorPromotionEligible", "unknown");
        appendRuntimeProductionMutationProperty(builder, prefix, entry, "rollbackClean", "unknown");
        appendRuntimeProductionMutationProperty(builder, prefix, entry, "diagnostic", "unknown");
        builder.append(prefix).append("runtimeProductionMutationSafety.status=").append(entry.getProperty("runtimeProductionMutationSafety.status", "not-recorded")).append('\n');
        builder.append(prefix).append("runtimeProductionMutationSafety.productionMutationEnabled=").append(entry.getProperty("runtimeProductionMutationSafety.productionMutationEnabled", "unknown")).append('\n');
        builder.append(prefix).append("runtimeProductionMutationSafety.productionGateStatus=").append(entry.getProperty("runtimeProductionMutationSafety.productionGateStatus", "unknown")).append('\n');
        builder.append(prefix).append("runtimeProductionMutationSafety.productionProfileRequested=").append(entry.getProperty("runtimeProductionMutationSafety.productionProfileRequested", "unknown")).append('\n');
        builder.append(prefix).append("runtimeProductionMutationSafety.selectedStage=").append(entry.getProperty("runtimeProductionMutationSafety.selectedStage", "unknown")).append('\n');
        builder.append(prefix).append("runtimeProductionMutationSafety.optimizedSelected=").append(entry.getProperty("runtimeProductionMutationSafety.optimizedSelected", "unknown")).append('\n');
        builder.append(prefix).append("runtimeProductionMutationSafety.optimizedDiffersFromOriginal=").append(entry.getProperty("runtimeProductionMutationSafety.optimizedDiffersFromOriginal", "unknown")).append('\n');
        builder.append(prefix).append("runtimeProductionMutationSafety.optimizedIrRejected=").append(entry.getProperty("runtimeProductionMutationSafety.optimizedIrRejected", "unknown")).append('\n');
        builder.append(prefix).append("runtimeProductionMutationSafety.fallbackDecision=").append(entry.getProperty("runtimeProductionMutationSafety.fallbackDecision", "unknown")).append('\n');
        builder.append(prefix).append("runtimeProductionMutationSafety.runtimeEquivalencePassed=").append(entry.getProperty("runtimeProductionMutationSafety.runtimeEquivalencePassed", "unknown")).append('\n');
        builder.append(prefix).append("runtimeProductionMutationSafety.fallbackClean=").append(entry.getProperty("runtimeProductionMutationSafety.fallbackClean", "unknown")).append('\n');
        builder.append(prefix).append("runtimeProductionMutationSafety.strategyEvidenceBacked=").append(entry.getProperty("runtimeProductionMutationSafety.strategyEvidenceBacked", "unknown")).append('\n');
        builder.append(prefix).append("runtimeProductionMutationSafety.vendorPromotionEligible=").append(entry.getProperty("runtimeProductionMutationSafety.vendorPromotionEligible", "unknown")).append('\n');
        builder.append(prefix).append("runtimeProductionMutationSafety.rollbackClean=").append(entry.getProperty("runtimeProductionMutationSafety.rollbackClean", "unknown")).append('\n');
        appendIndexedProperties(builder, prefix, entry, "runtimeProductionMutationSafety.diagnostic");
    }

    private static void appendRuntimeProductionMutationProperty(
            StringBuilder builder,
            String prefix,
            Properties entry,
            String key,
            String fallback
    ) {
        String propertyName = "runtime.ir.productionMutation." + key;
        builder.append(prefix).append(propertyName).append('=').append(entry.getProperty(propertyName, fallback)).append('\n');
    }

    private static void appendI3ReadinessSummary(StringBuilder builder, String prefix, Properties entry) {
        builder.append(prefix).append("i3Readiness.status=").append(entry.getProperty("i3Readiness.status", "not-recorded")).append('\n');
        builder.append(prefix).append("i3Readiness.backendTarget=").append(entry.getProperty("i3Readiness.backendTarget", "unknown")).append('\n');
        builder.append(prefix).append("i3Readiness.backendFormat=").append(entry.getProperty("i3Readiness.backendFormat", "unknown")).append('\n');
        builder.append(prefix).append("i3Readiness.backendResource=").append(entry.getProperty("i3Readiness.backendResource", "unknown")).append('\n');
        builder.append(prefix).append("i3Readiness.selectedRuntimeIrStage=").append(entry.getProperty("i3Readiness.selectedRuntimeIrStage", "unknown")).append('\n');
        builder.append(prefix).append("i3Readiness.selectedRuntimeIrIdentity=").append(entry.getProperty("i3Readiness.selectedRuntimeIrIdentity", "unknown")).append('\n');
        builder.append(prefix).append("i3Readiness.optimizedIrRejected=").append(entry.getProperty("i3Readiness.optimizedIrRejected", "unknown")).append('\n');
        builder.append(prefix).append("i3Readiness.fallbackDecision=").append(entry.getProperty("i3Readiness.fallbackDecision", "unknown")).append('\n');
        builder.append(prefix).append("i3Readiness.sourceReconstructed=").append(entry.getProperty("i3Readiness.sourceReconstructed", "unknown")).append('\n');
        builder.append(prefix).append("i3Readiness.sourceReady=").append(entry.getProperty("i3Readiness.sourceReady", "unknown")).append('\n');
        builder.append(prefix).append("i3Readiness.sourceAvailable=").append(entry.getProperty("i3Readiness.sourceAvailable", "unknown")).append('\n');
        builder.append(prefix).append("i3Readiness.sourceParityChecked=").append(entry.getProperty("i3Readiness.sourceParityChecked", "unknown")).append('\n');
        builder.append(prefix).append("i3Readiness.sourceParityMatched=").append(entry.getProperty("i3Readiness.sourceParityMatched", "unknown")).append('\n');
        builder.append(prefix).append("i3Readiness.runtimeEquivalencePassed=").append(entry.getProperty("i3Readiness.runtimeEquivalencePassed", "unknown")).append('\n');
        builder.append(prefix).append("i3Readiness.sourcePromotionStatus=").append(entry.getProperty("i3Readiness.sourcePromotionStatus", "unknown")).append('\n');
        builder.append(prefix).append("i3Readiness.sourcePromotionReviewReady=").append(entry.getProperty("i3Readiness.sourcePromotionReviewReady", "unknown")).append('\n');
        builder.append(prefix).append("i3Readiness.optimizerProductionGateStatus=").append(entry.getProperty("i3Readiness.optimizerProductionGateStatus", "unknown")).append('\n');
        builder.append(prefix).append("i3Readiness.productionProfileRequested=").append(entry.getProperty("i3Readiness.productionProfileRequested", "unknown")).append('\n');
        builder.append(prefix).append("i3Readiness.productionMutationEnabled=").append(entry.getProperty("i3Readiness.productionMutationEnabled", "unknown")).append('\n');
        appendIndexedProperties(builder, prefix, entry, "i3Readiness.blocker");
        appendIndexedProperties(builder, prefix, entry, "i3Readiness.diagnostic");
    }

    private static void appendRuntimeOptimizerDrift(StringBuilder builder, String prefix, Properties entry) {
        builder.append(prefix).append("runtimeOptimizerDrift.status=").append(entry.getProperty("runtimeOptimizerDrift.status", "not-recorded")).append('\n');
        appendRuntimeOptimizerDriftProperties(builder, prefix, entry);
        appendIndexedPropertyGroup(builder, prefix, entry, "runtimeOptimizerDrift.optimizerRule");
        builder.append(prefix).append("runtimeOptimizerDrift.optimizerFamily.count=").append(entry.getProperty("runtimeOptimizerDrift.optimizerFamily.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.optimizerFamily.promotionReady.count=").append(entry.getProperty("runtimeOptimizerDrift.optimizerFamily.promotionReady.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.optimizerFamily.summary=").append(entry.getProperty("runtimeOptimizerDrift.optimizerFamily.summary", "none")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.fallbackDecision=").append(entry.getProperty("runtimeOptimizerDrift.fallbackDecision", "unknown")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.selectedRuntimeIrStage=").append(entry.getProperty("runtimeOptimizerDrift.selectedRuntimeIrStage", "unknown")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.selectedRuntimeIrIdentity=").append(entry.getProperty("runtimeOptimizerDrift.selectedRuntimeIrIdentity", "unknown")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.optimizedIrRejected=").append(entry.getProperty("runtimeOptimizerDrift.optimizedIrRejected", "unknown")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.strategyName=").append(entry.getProperty("runtimeOptimizerDrift.strategyName", "unknown")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.selectedProfile=").append(entry.getProperty("runtimeOptimizerDrift.selectedProfile", "unknown")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.baselineStatus=").append(entry.getProperty("runtimeOptimizerDrift.baselineStatus", "unknown")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.promotionEligible=").append(entry.getProperty("runtimeOptimizerDrift.promotionEligible", "unknown")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.productionGateStatus=").append(entry.getProperty("runtimeOptimizerDrift.productionGateStatus", "unknown")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.productionProfileRequested=").append(entry.getProperty("runtimeOptimizerDrift.productionProfileRequested", "unknown")).append('\n');
        builder.append(prefix).append("optimizerFamilyPayload.status=").append(entry.getProperty("optimizerFamilyPayload.status", "not-recorded")).append('\n');
        builder.append(prefix).append("optimizerFamilyPayload.runtimeEquivalence.passed=").append(entry.getProperty("optimizerFamilyPayload.runtimeEquivalence.passed", "unknown")).append('\n');
        builder.append(prefix).append("optimizerFamilyPayload.family.count=").append(entry.getProperty("optimizerFamilyPayload.family.count", "0")).append('\n');
        builder.append(prefix).append("optimizerFamilyPayload.family.complete.count=").append(entry.getProperty("optimizerFamilyPayload.family.complete.count", "0")).append('\n');
        builder.append(prefix).append("optimizerFamilyPayload.family.complete.all=").append(entry.getProperty("optimizerFamilyPayload.family.complete.all", "false")).append('\n');
    }

    private static void appendRuntimeOptimizerDriftProperties(StringBuilder builder, String prefix, Properties entry) {
        for (DriftProperty property : RUNTIME_OPTIMIZER_DRIFT_PROPERTIES) {
            String key = RUNTIME_OPTIMIZER_DRIFT_PREFIX + property.key();
            builder.append(prefix).append(key).append('=').append(entry.getProperty(key, property.emptySourceDefault())).append('\n');
        }
    }

    private static void copyRuntimeExtensionParticipationProperties(Properties source, Properties target) {
        if (source == null || source.isEmpty()) {
            target.setProperty("runtimeExtensionParticipation.status", "not-recorded");
            target.setProperty("runtimeExtensionParticipation.entry.count", "0");
            target.setProperty("runtimeExtensionParticipation.succeeded.count", "0");
            target.setProperty("runtimeExtensionParticipation.skipped.count", "0");
            target.setProperty("runtimeExtensionParticipation.failedContinued.count", "0");
            target.setProperty("runtimeExtensionParticipation.failedClosed.count", "0");
            target.setProperty("runtimeExtensionParticipation.pipelineContinued.all", "unknown");
            target.setProperty("runtimeExtensionParticipation.firstFailure", "none");
            target.setProperty("runtimeExtensionParticipation.source.count", "0");
            return;
        }
        target.setProperty("runtimeExtensionParticipation.status", source.getProperty("status", "unknown"));
        target.setProperty("runtimeExtensionParticipation.entry.count", source.getProperty("entry.count", "0"));
        target.setProperty("runtimeExtensionParticipation.succeeded.count", source.getProperty("succeeded.count", "0"));
        target.setProperty("runtimeExtensionParticipation.skipped.count", source.getProperty("skipped.count", "0"));
        target.setProperty("runtimeExtensionParticipation.failedContinued.count", source.getProperty("failedContinued.count", "0"));
        target.setProperty("runtimeExtensionParticipation.failedClosed.count", source.getProperty("failedClosed.count", "0"));
        target.setProperty("runtimeExtensionParticipation.pipelineContinued.all", source.getProperty("pipelineContinued.all", "unknown"));
        target.setProperty("runtimeExtensionParticipation.firstFailure", source.getProperty("firstFailure", "none"));
        LinkedHashMap<String, Integer> sourceCounts = runtimeExtensionParticipationSourceCounts(source);
        target.setProperty("runtimeExtensionParticipation.source.count", Integer.toString(sourceCounts.size()));
        int sourceIndex = 0;
        for (Map.Entry<String, Integer> sourceCount : sourceCounts.entrySet()) {
            target.setProperty("runtimeExtensionParticipation.source." + sourceIndex + ".name", sourceCount.getKey());
            target.setProperty("runtimeExtensionParticipation.source." + sourceIndex + ".count", Integer.toString(sourceCount.getValue()));
            sourceIndex++;
        }
    }

    private static LinkedHashMap<String, Integer> runtimeExtensionParticipationSourceCounts(Properties properties) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        int entryCount = parsePositiveInt(properties.getProperty("entry.count", "0"));
        for (int index = 0; index < entryCount; index++) {
            String source = properties.getProperty("entry." + index + ".source", "");
            if (!source.isBlank()) {
                counts.merge(source, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static void appendAggregateRuntimeExtensionParticipation(
            StringBuilder builder,
            LinkedHashMap<String, Properties> kernels
    ) {
        int recordedKernelCount = 0;
        int entryCount = 0;
        int failedContinuedCount = 0;
        int failedClosedCount = 0;
        LinkedHashMap<String, Integer> sourceCounts = new LinkedHashMap<>();
        for (Properties entry : kernels.values()) {
            if ("recorded".equals(entry.getProperty("runtimeExtensionParticipation.status"))) {
                recordedKernelCount++;
            }
            entryCount += parsePositiveInt(entry.getProperty("runtimeExtensionParticipation.entry.count", "0"));
            failedContinuedCount += parsePositiveInt(entry.getProperty("runtimeExtensionParticipation.failedContinued.count", "0"));
            failedClosedCount += parsePositiveInt(entry.getProperty("runtimeExtensionParticipation.failedClosed.count", "0"));
            int sourceCount = parsePositiveInt(entry.getProperty("runtimeExtensionParticipation.source.count", "0"));
            for (int index = 0; index < sourceCount; index++) {
                String source = entry.getProperty("runtimeExtensionParticipation.source." + index + ".name", "");
                int count = parsePositiveInt(entry.getProperty("runtimeExtensionParticipation.source." + index + ".count", "0"));
                if (!source.isBlank()) {
                    sourceCounts.merge(source, count, Integer::sum);
                }
            }
        }
        builder.append("runtimeExtensionParticipation.recordedKernel.count=").append(recordedKernelCount).append('\n');
        builder.append("runtimeExtensionParticipation.entry.count=").append(entryCount).append('\n');
        builder.append("runtimeExtensionParticipation.failedContinued.count=").append(failedContinuedCount).append('\n');
        builder.append("runtimeExtensionParticipation.failedClosed.count=").append(failedClosedCount).append('\n');
        builder.append("runtimeExtensionParticipation.source.count=").append(sourceCounts.size()).append('\n');
        int sourceIndex = 0;
        for (Map.Entry<String, Integer> source : sourceCounts.entrySet()) {
            builder.append("runtimeExtensionParticipation.source.").append(sourceIndex).append(".name=").append(source.getKey()).append('\n');
            builder.append("runtimeExtensionParticipation.source.").append(sourceIndex).append(".count=").append(source.getValue()).append('\n');
            sourceIndex++;
        }
    }

    private static void appendRuntimeExtensionParticipation(StringBuilder builder, String prefix, Properties entry) {
        builder.append(prefix).append("runtimeExtensionParticipation.status=").append(entry.getProperty("runtimeExtensionParticipation.status", "not-recorded")).append('\n');
        builder.append(prefix).append("runtimeExtensionParticipation.entry.count=").append(entry.getProperty("runtimeExtensionParticipation.entry.count", "0")).append('\n');
        builder.append(prefix).append("runtimeExtensionParticipation.succeeded.count=").append(entry.getProperty("runtimeExtensionParticipation.succeeded.count", "0")).append('\n');
        builder.append(prefix).append("runtimeExtensionParticipation.skipped.count=").append(entry.getProperty("runtimeExtensionParticipation.skipped.count", "0")).append('\n');
        builder.append(prefix).append("runtimeExtensionParticipation.failedContinued.count=").append(entry.getProperty("runtimeExtensionParticipation.failedContinued.count", "0")).append('\n');
        builder.append(prefix).append("runtimeExtensionParticipation.failedClosed.count=").append(entry.getProperty("runtimeExtensionParticipation.failedClosed.count", "0")).append('\n');
        builder.append(prefix).append("runtimeExtensionParticipation.pipelineContinued.all=").append(entry.getProperty("runtimeExtensionParticipation.pipelineContinued.all", "unknown")).append('\n');
        builder.append(prefix).append("runtimeExtensionParticipation.firstFailure=").append(entry.getProperty("runtimeExtensionParticipation.firstFailure", "none")).append('\n');
        int sourceCount = parsePositiveInt(entry.getProperty("runtimeExtensionParticipation.source.count", "0"));
        builder.append(prefix).append("runtimeExtensionParticipation.source.count=").append(sourceCount).append('\n');
        for (int sourceIndex = 0; sourceIndex < sourceCount; sourceIndex++) {
            builder.append(prefix).append("runtimeExtensionParticipation.source.").append(sourceIndex).append(".name=").append(entry.getProperty("runtimeExtensionParticipation.source." + sourceIndex + ".name", "unknown")).append('\n');
            builder.append(prefix).append("runtimeExtensionParticipation.source.").append(sourceIndex).append(".count=").append(entry.getProperty("runtimeExtensionParticipation.source." + sourceIndex + ".count", "0")).append('\n');
        }
    }

    private static void appendIndexedPropertyGroup(
            StringBuilder builder,
            String kernelPrefix,
            Properties entry,
            String keyPrefix
    ) {
        String indexedPrefix = keyPrefix + ".";
        int count = parsePositiveInt(entry.getProperty(keyPrefix + ".count", "0"));
        for (String key : entry.stringPropertyNames().stream().sorted().toList()) {
            if (!key.startsWith(indexedPrefix)) {
                continue;
            }
            String suffix = key.substring(indexedPrefix.length());
            int index = indexedPropertyIndex(suffix);
            if (index < 0 || index >= count) {
                continue;
            }
            builder.append(kernelPrefix).append(key).append('=').append(entry.getProperty(key, "unknown")).append('\n');
        }
    }

    private static int indexedPropertyIndex(String suffix) {
        if (suffix == null || suffix.isBlank() || !Character.isDigit(suffix.charAt(0))) {
            return -1;
        }
        int end = 0;
        while (end < suffix.length() && Character.isDigit(suffix.charAt(end))) {
            end++;
        }
        if (end == suffix.length() || suffix.charAt(end) != '.') {
            return -1;
        }
        return parsePositiveInt(suffix.substring(0, end));
    }

    private static void appendIndexedProperties(
            StringBuilder builder,
            String kernelPrefix,
            Properties entry,
            String keyPrefix
    ) {
        int count = parsePositiveInt(entry.getProperty(keyPrefix + ".count", "0"));
        builder.append(kernelPrefix).append(keyPrefix).append(".count=").append(count).append('\n');
        for (int index = 0; index < count; index++) {
            builder.append(kernelPrefix)
                    .append(keyPrefix)
                    .append('.')
                    .append(index)
                    .append('=')
                    .append(entry.getProperty(keyPrefix + "." + index, "unknown"))
                    .append('\n');
        }
    }

    private static LinkedHashMap<String, Integer> aggregatePromotionBlockerFamilies(LinkedHashMap<String, Properties> kernels) {
        LinkedHashMap<String, Integer> families = new LinkedHashMap<>();
        for (Properties entry : kernels.values()) {
            int familyCount = parsePositiveInt(entry.getProperty("blockerFamily.count", "0"));
            for (int familyIndex = 0; familyIndex < familyCount; familyIndex++) {
                String family = entry.getProperty("blockerFamily." + familyIndex + ".name", "unknown");
                int count = parsePositiveInt(entry.getProperty("blockerFamily." + familyIndex + ".count", "0"));
                families.merge(family, count, Integer::sum);
            }
        }
        return families;
    }

    private static LinkedHashMap<String, Integer> aggregateSourceDecisions(LinkedHashMap<String, Properties> kernels) {
        LinkedHashMap<String, Integer> decisions = new LinkedHashMap<>();
        for (Properties entry : kernels.values()) {
            String decision = runtimeBackendSourceProperty(
                    entry,
                    "decision",
                    "sourceSwitching.decision",
                    "not-recorded"
            );
            if (decision.isBlank() || "not-recorded".equals(decision)) {
                continue;
            }
            decisions.merge(decision, 1, Integer::sum);
        }
        return decisions;
    }

    private static LinkedHashMap<String, Integer> aggregateSourcePromotionFirstBlockers(LinkedHashMap<String, Properties> kernels) {
        LinkedHashMap<String, Integer> blockers = new LinkedHashMap<>();
        for (Properties entry : kernels.values()) {
            String blocker = runtimeBackendSourceProperty(
                    entry,
                    "promotionFirstBlocker",
                    "sourceSwitching.sourcePromotionFirstBlocker",
                    "unknown"
            );
            if (blocker.isBlank() || "none".equals(blocker) || "unknown".equals(blocker)) {
                continue;
            }
            blockers.merge(blocker, 1, Integer::sum);
        }
        return blockers;
    }

    private static LinkedHashMap<String, Integer> aggregateSourcePromotionFirstBlockerFamilies(
            LinkedHashMap<String, Integer> blockers
    ) {
        LinkedHashMap<String, Integer> families = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> blocker : blockers.entrySet()) {
            String family = GpuBackendSourcePromotionBlockerClassifier.classify(blocker.getKey());
            families.merge(family, blocker.getValue(), Integer::sum);
        }
        return families;
    }

    private static String entryProductionSourceSwitching(Properties entry) {
        return entryProductionSourceSwitchingEnabled(entry)
                && entryProductionPromotionEnabled(entry)
                && entryProductionSourceDecision(entry)
                ? "enabled"
                : "false";
    }

    private static boolean entryProductionSourceSwitchingEnabled(Properties entry) {
        String switchingEnabled = runtimeBackendSourceProperty(
                entry,
                "productionSwitchingEnabled",
                "sourceSwitching.productionSourceSwitchingEnabled",
                "false"
        );
        String switching = runtimeBackendSourceProperty(
                entry,
                "productionSwitching",
                "sourceSwitching.productionSourceSwitching",
                "false"
        );
        return "true".equals(switchingEnabled) || "enabled".equals(switching);
    }

    private static boolean entryProductionPromotionEnabled(Properties entry) {
        return GpuProductionPromotionDecision.PRODUCTION_ENABLED.equals(
                runtimeBackendSourceProperty(
                        entry,
                        "productionPromotionDecisionMode",
                        "sourceSwitching.productionPromotionDecisionMode",
                        GpuProductionPromotionDecision.DIAGNOSTIC_ONLY
                )
        );
    }

    private static boolean entryProductionPromotionOperatorAccepted(Properties entry) {
        return "true".equals(runtimeBackendSourceProperty(
                entry,
                "productionPromotionOperatorAccepted",
                "sourceSwitching.productionPromotionOperatorAccepted",
                "false"
        ));
    }

    private static boolean entryProductionSourceDecision(Properties entry) {
        return "compile-irgpu-source-production".equals(runtimeBackendSourceProperty(
                entry,
                "decision",
                "sourceSwitching.decision",
                "not-recorded"
        ));
    }

    private static int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String normalizeResource(String sourceKernelResource) {
        return sourceKernelResource == null || sourceKernelResource.isBlank() ? "unknown" : sourceKernelResource;
    }
}
