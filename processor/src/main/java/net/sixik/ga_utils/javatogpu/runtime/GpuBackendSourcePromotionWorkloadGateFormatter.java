package net.sixik.ga_utils.javatogpu.runtime;

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
            target.setProperty("sourceSwitching.productionProfileRequested", "unknown");
            target.setProperty("sourceSwitching.sourceSelection", "unknown");
            target.setProperty("sourceSwitching.irGpuSourceRequested", "unknown");
            target.setProperty("sourceSwitching.productionSourceSwitching", "false");
            target.setProperty("sourceSwitching.productionSourceSwitchingEnabled", "false");
            target.setProperty("sourceSwitching.productionPromotionDecisionMode", GpuProductionPromotionDecision.DIAGNOSTIC_ONLY);
            target.setProperty("sourceSwitching.productionPromotionOperatorAccepted", "false");
            target.setProperty("sourceSwitching.sourcePromotionFirstBlocker", "source-switching-decision-not-recorded");
            target.setProperty("sourceSwitching.diagnostic.count", "0");
            return;
        }
        copySourceSwitchingProperty(source, target, "status");
        copySourceSwitchingProperty(source, target, "decision");
        copySourceSwitchingProperty(source, target, "optimizationProfile");
        copySourceSwitchingProperty(source, target, "productionProfileRequested");
        copySourceSwitchingProperty(source, target, "sourceSelection");
        copySourceSwitchingProperty(source, target, "irGpuSourceRequested");
        copySourceSwitchingProperty(source, target, "productionSourceSwitching");
        copySourceSwitchingProperty(source, target, "productionSourceSwitchingEnabled");
        copySourceSwitchingProperty(source, target, "productionPromotionDecisionMode");
        copySourceSwitchingProperty(source, target, "productionPromotionOperatorAccepted");
        copySourceSwitchingProperty(source, target, "sourcePromotionFirstBlocker");
        copyIndexedProperties(source, target, "sourceSwitching.diagnostic", "diagnostic");
    }

    private static void copySourceSwitchingProperty(Properties source, Properties target, String key) {
        target.setProperty("sourceSwitching." + key, source.getProperty(key, "unknown"));
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
            return;
        }
        copyRuntimeProductionMutationSafetyProperty(source, target, "status");
        copyRuntimeProductionMutationSafetyProperty(source, target, "productionMutationEnabled");
        copyRuntimeProductionMutationSafetyProperty(source, target, "productionGateStatus");
        copyRuntimeProductionMutationSafetyProperty(source, target, "productionProfileRequested");
        copyRuntimeProductionMutationSafetyProperty(source, target, "selectedStage");
        copyRuntimeProductionMutationSafetyProperty(source, target, "optimizedSelected");
        copyRuntimeProductionMutationSafetyProperty(source, target, "optimizedDiffersFromOriginal");
        copyRuntimeProductionMutationSafetyProperty(source, target, "optimizedIrRejected");
        copyRuntimeProductionMutationSafetyProperty(source, target, "fallbackDecision");
        copyRuntimeProductionMutationSafetyProperty(source, target, "runtimeEquivalencePassed");
        copyRuntimeProductionMutationSafetyProperty(source, target, "fallbackClean");
        copyRuntimeProductionMutationSafetyProperty(source, target, "strategyEvidenceBacked");
        copyRuntimeProductionMutationSafetyProperty(source, target, "vendorPromotionEligible");
        copyRuntimeProductionMutationSafetyProperty(source, target, "rollbackClean");
        copyIndexedProperties(source, target, "runtimeProductionMutationSafety.diagnostic", "diagnostic");
    }

    private static void copyRuntimeProductionMutationSafetyProperty(Properties source, Properties target, String key) {
        target.setProperty("runtimeProductionMutationSafety." + key, source.getProperty(key, "unknown"));
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
            target.setProperty("runtimeOptimizerDrift.pass.count", "0");
            target.setProperty("runtimeOptimizerDrift.pass.applied.count", "0");
            target.setProperty("runtimeOptimizerDrift.pass.skipped.count", "0");
            target.setProperty("runtimeOptimizerDrift.pass.rolledBack.count", "0");
            target.setProperty("runtimeOptimizerDrift.pass.failed.count", "0");
            target.setProperty("runtimeOptimizerDrift.proofArtifact.count", "0");
            target.setProperty("runtimeOptimizerDrift.proofArtifact.accepted.count", "0");
            target.setProperty("runtimeOptimizerDrift.proofArtifact.blocking.count", "0");
            target.setProperty("runtimeOptimizerDrift.replacementPlan.complete.count", "0");
            target.setProperty("runtimeOptimizerDrift.replacementPlan.partial.count", "0");
            target.setProperty("runtimeOptimizerDrift.replacementPlan.firstBlocker", "none");
            target.setProperty("runtimeOptimizerDrift.replacementPlan.validation.count", "0");
            target.setProperty("runtimeOptimizerDrift.replacementPlan.validation.valid.count", "0");
            target.setProperty("runtimeOptimizerDrift.replacementPlan.validation.invalid.count", "0");
            target.setProperty("runtimeOptimizerDrift.replacementPlan.validation.firstBlocker", "none");
            target.setProperty("runtimeOptimizerDrift.rewriteSketch.count", "0");
            target.setProperty("runtimeOptimizerDrift.rewriteSketch.ready.count", "0");
            target.setProperty("runtimeOptimizerDrift.rewriteSketch.blocked.count", "0");
            target.setProperty("runtimeOptimizerDrift.rewriteSketch.firstBlocker", "none");
            target.setProperty("runtimeOptimizerDrift.rewriteSketch.rewriteBuilderImplemented", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteSketch.mutationAllowed", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteSketch.selectedIrReplacement", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteSketch.conflict.count", "0");
            target.setProperty("runtimeOptimizerDrift.rewriteSketch.conflict.firstBlocker", "none");
            target.setProperty("runtimeOptimizerDrift.rewriteSketch.conflict.conflictResolutionImplemented", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteSketch.conflict.selectionApplied", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteSketch.conflict.mutationAllowed", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteSketch.conflict.selectedIrReplacement", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteSelection.sketch.count", "0");
            target.setProperty("runtimeOptimizerDrift.rewriteSelection.sketch.ready.count", "0");
            target.setProperty("runtimeOptimizerDrift.rewriteSelection.sketch.blocked.count", "0");
            target.setProperty("runtimeOptimizerDrift.rewriteSelection.conflict.count", "0");
            target.setProperty("runtimeOptimizerDrift.rewriteSelection.status", "not-required");
            target.setProperty("runtimeOptimizerDrift.rewriteSelection.firstBlocker", "no-rewrite-sketches");
            target.setProperty("runtimeOptimizerDrift.rewriteSelection.rewriteBuilderImplemented", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteSelection.conflictResolutionImplemented", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteSelection.runtimeEquivalenceRequired", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteSelection.runtimeEquivalenceProven", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteSelection.approvalRequired", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteSelection.approvalAccepted", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteSelection.mutationAllowed", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteSelection.selectionApplied", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteSelection.selectedIrReplacement", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteProof.status", "not-required");
            target.setProperty("runtimeOptimizerDrift.rewriteProof.firstBlocker", "no-proof-candidates");
            target.setProperty("runtimeOptimizerDrift.rewriteProof.proofAccepted", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteProof.runtimeEquivalencePayload.present", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteProof.runtimeEquivalencePayload.complete", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteProof.rollbackEvidence.present", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteProof.rollbackClean", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteProof.approvalAccepted", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteProof.mutationAllowed", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteProof.selectedIrReplacement", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteReviewPackage.status", "not-required");
            target.setProperty("runtimeOptimizerDrift.rewriteReviewPackage.firstBlocker", "no-review-candidates");
            target.setProperty("runtimeOptimizerDrift.rewriteReviewPackage.required", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteReviewPackage.complete", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteReviewPackage.conflict.count", "0");
            target.setProperty("runtimeOptimizerDrift.rewriteReviewPackage.proofAccepted", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteReviewPackage.runtimeEquivalencePayload.present", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteReviewPackage.runtimeEquivalencePayload.complete", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteReviewPackage.rollbackEvidence.present", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteReviewPackage.rollbackClean", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteReviewPackage.approvalAccepted", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteReviewPackage.mutationAllowed", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteReviewPackage.selectionApplied", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteReviewPackage.selectedIrReplacement", "false");
            target.setProperty("runtimeOptimizerDrift.rewriteReviewPackage.manualReviewOnly", "true");
            target.setProperty("runtimeOptimizerDrift.optimizerRule.count", "0");
            target.setProperty("runtimeOptimizerDrift.optimizerRule.summary", "none");
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
        copyRuntimeOptimizerDriftProperty(source, target, "pass.count");
        copyRuntimeOptimizerDriftProperty(source, target, "pass.applied.count");
        copyRuntimeOptimizerDriftProperty(source, target, "pass.skipped.count");
        copyRuntimeOptimizerDriftProperty(source, target, "pass.rolledBack.count");
        copyRuntimeOptimizerDriftProperty(source, target, "pass.failed.count");
        copyRuntimeOptimizerDriftProperty(source, target, "proofArtifact.count");
        copyRuntimeOptimizerDriftProperty(source, target, "proofArtifact.accepted.count");
        copyRuntimeOptimizerDriftProperty(source, target, "proofArtifact.blocking.count");
        copyRuntimeOptimizerDriftProperty(source, target, "replacementPlan.complete.count", "0");
        copyRuntimeOptimizerDriftProperty(source, target, "replacementPlan.partial.count", "0");
        copyRuntimeOptimizerDriftProperty(source, target, "replacementPlan.firstBlocker", "none");
        copyRuntimeOptimizerDriftProperty(source, target, "replacementPlan.validation.count", "0");
        copyRuntimeOptimizerDriftProperty(source, target, "replacementPlan.validation.valid.count", "0");
        copyRuntimeOptimizerDriftProperty(source, target, "replacementPlan.validation.invalid.count", "0");
        copyRuntimeOptimizerDriftProperty(source, target, "replacementPlan.validation.firstBlocker", "none");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSketch.count", "0");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSketch.ready.count", "0");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSketch.blocked.count", "0");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSketch.firstBlocker", "none");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSketch.rewriteBuilderImplemented", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSketch.mutationAllowed", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSketch.selectedIrReplacement", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSketch.conflict.count", "0");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSketch.conflict.firstBlocker", "none");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSketch.conflict.conflictResolutionImplemented", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSketch.conflict.selectionApplied", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSketch.conflict.mutationAllowed", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSketch.conflict.selectedIrReplacement", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSelection.sketch.count", "0");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSelection.sketch.ready.count", "0");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSelection.sketch.blocked.count", "0");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSelection.conflict.count", "0");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSelection.status", "not-required");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSelection.firstBlocker", "no-rewrite-sketches");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSelection.rewriteBuilderImplemented", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSelection.conflictResolutionImplemented", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSelection.runtimeEquivalenceRequired", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSelection.runtimeEquivalenceProven", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSelection.approvalRequired", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSelection.approvalAccepted", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSelection.mutationAllowed", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSelection.selectionApplied", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteSelection.selectedIrReplacement", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteProof.status", "not-required");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteProof.firstBlocker", "no-proof-candidates");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteProof.proofAccepted", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteProof.runtimeEquivalencePayload.present", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteProof.runtimeEquivalencePayload.complete", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteProof.rollbackEvidence.present", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteProof.rollbackClean", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteProof.approvalAccepted", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteProof.mutationAllowed", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteProof.selectedIrReplacement", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteReviewPackage.status", "not-required");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteReviewPackage.firstBlocker", "no-review-candidates");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteReviewPackage.required", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteReviewPackage.complete", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteReviewPackage.conflict.count", "0");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteReviewPackage.proofAccepted", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteReviewPackage.runtimeEquivalencePayload.present", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteReviewPackage.runtimeEquivalencePayload.complete", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteReviewPackage.rollbackEvidence.present", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteReviewPackage.rollbackClean", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteReviewPackage.approvalAccepted", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteReviewPackage.mutationAllowed", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteReviewPackage.selectionApplied", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteReviewPackage.selectedIrReplacement", "false");
        copyRuntimeOptimizerDriftProperty(source, target, "rewriteReviewPackage.manualReviewOnly", "true");
        copyRuntimeOptimizerDriftProperty(source, target, "optimizerRule.count", "0");
        copyRuntimeOptimizerDriftProperty(source, target, "optimizerRule.summary", "none");
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

    private static void copyRuntimeOptimizerDriftProperty(Properties source, Properties target, String key) {
        target.setProperty("runtimeOptimizerDrift." + key, source.getProperty(key, "unknown"));
    }

    private static void copyRuntimeOptimizerDriftProperty(
            Properties source,
            Properties target,
            String key,
            String defaultValue
    ) {
        target.setProperty("runtimeOptimizerDrift." + key, source.getProperty(key, defaultValue));
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
        builder.append("productionSourceSwitchingEnabled.count=").append(productionSourceSwitchingEnabledCount).append('\n');
        builder.append("productionSourceSwitchingEnabled.all=").append(allProductionSourceSwitchingEnabled).append('\n');
        builder.append("productionPromotionDecisionMode.productionEnabled.count=").append(productionPromotionEnabledCount).append('\n');
        builder.append("productionPromotionDecisionMode.productionEnabled.all=").append(allProductionPromotionEnabled).append('\n');
        builder.append("productionPromotionOperatorAccepted.count=").append(productionPromotionOperatorAcceptedCount).append('\n');
        builder.append("productionPromotionOperatorAccepted.all=").append(allProductionPromotionOperatorAccepted).append('\n');
        builder.append("sourceSwitching.productionDecision.count=").append(productionSourceDecisionCount).append('\n');
        builder.append("sourceSwitching.productionDecision.all=").append(allProductionSourceDecisions).append('\n');
        appendAggregateRuntimeExtensionParticipation(builder, kernels);
        builder.append("sourceSwitching.count=").append(kernels.size()).append('\n');
        builder.append("sourceSwitching.sourcePromotionFirstBlocker.count=").append(aggregateSourcePromotionFirstBlockers.size()).append('\n');
        int sourcePromotionBlockerIndex = 0;
        for (Map.Entry<String, Integer> blocker : aggregateSourcePromotionFirstBlockers.entrySet()) {
            builder.append("sourceSwitching.sourcePromotionFirstBlocker.").append(sourcePromotionBlockerIndex).append(".name=").append(blocker.getKey()).append('\n');
            builder.append("sourceSwitching.sourcePromotionFirstBlocker.").append(sourcePromotionBlockerIndex).append(".count=").append(blocker.getValue()).append('\n');
            sourcePromotionBlockerIndex++;
        }
        builder.append("sourceSwitching.sourcePromotionFirstBlockerFamily.count=").append(aggregateSourcePromotionFirstBlockerFamilies.size()).append('\n');
        int sourcePromotionBlockerFamilyIndex = 0;
        for (Map.Entry<String, Integer> family : aggregateSourcePromotionFirstBlockerFamilies.entrySet()) {
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
        builder.append(prefix).append("sourceSwitching.status=").append(entry.getProperty("sourceSwitching.status", "not-recorded")).append('\n');
        builder.append(prefix).append("sourceSwitching.decision=").append(entry.getProperty("sourceSwitching.decision", "not-recorded")).append('\n');
        builder.append(prefix).append("sourceSwitching.optimizationProfile=").append(entry.getProperty("sourceSwitching.optimizationProfile", "unknown")).append('\n');
        builder.append(prefix).append("sourceSwitching.productionProfileRequested=").append(entry.getProperty("sourceSwitching.productionProfileRequested", "unknown")).append('\n');
        builder.append(prefix).append("sourceSwitching.sourceSelection=").append(entry.getProperty("sourceSwitching.sourceSelection", "unknown")).append('\n');
        builder.append(prefix).append("sourceSwitching.irGpuSourceRequested=").append(entry.getProperty("sourceSwitching.irGpuSourceRequested", "unknown")).append('\n');
        builder.append(prefix).append("sourceSwitching.productionSourceSwitching=").append(entry.getProperty("sourceSwitching.productionSourceSwitching", "false")).append('\n');
        builder.append(prefix).append("sourceSwitching.productionSourceSwitchingEnabled=").append(entry.getProperty("sourceSwitching.productionSourceSwitchingEnabled", "false")).append('\n');
        builder.append(prefix).append("sourceSwitching.productionPromotionDecisionMode=").append(entry.getProperty("sourceSwitching.productionPromotionDecisionMode", GpuProductionPromotionDecision.DIAGNOSTIC_ONLY)).append('\n');
        builder.append(prefix).append("sourceSwitching.productionPromotionOperatorAccepted=").append(entry.getProperty("sourceSwitching.productionPromotionOperatorAccepted", "false")).append('\n');
        builder.append(prefix).append("sourceSwitching.sourcePromotionFirstBlocker=").append(entry.getProperty("sourceSwitching.sourcePromotionFirstBlocker", "unknown")).append('\n');
        appendIndexedProperties(builder, prefix, entry, "sourceSwitching.diagnostic");
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
        builder.append(prefix).append("runtimeOptimizerDrift.pass.count=").append(entry.getProperty("runtimeOptimizerDrift.pass.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.pass.applied.count=").append(entry.getProperty("runtimeOptimizerDrift.pass.applied.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.pass.skipped.count=").append(entry.getProperty("runtimeOptimizerDrift.pass.skipped.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.pass.rolledBack.count=").append(entry.getProperty("runtimeOptimizerDrift.pass.rolledBack.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.pass.failed.count=").append(entry.getProperty("runtimeOptimizerDrift.pass.failed.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.proofArtifact.count=").append(entry.getProperty("runtimeOptimizerDrift.proofArtifact.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.proofArtifact.accepted.count=").append(entry.getProperty("runtimeOptimizerDrift.proofArtifact.accepted.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.proofArtifact.blocking.count=").append(entry.getProperty("runtimeOptimizerDrift.proofArtifact.blocking.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.replacementPlan.complete.count=").append(entry.getProperty("runtimeOptimizerDrift.replacementPlan.complete.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.replacementPlan.partial.count=").append(entry.getProperty("runtimeOptimizerDrift.replacementPlan.partial.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.replacementPlan.firstBlocker=").append(entry.getProperty("runtimeOptimizerDrift.replacementPlan.firstBlocker", "none")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.replacementPlan.validation.count=").append(entry.getProperty("runtimeOptimizerDrift.replacementPlan.validation.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.replacementPlan.validation.valid.count=").append(entry.getProperty("runtimeOptimizerDrift.replacementPlan.validation.valid.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.replacementPlan.validation.invalid.count=").append(entry.getProperty("runtimeOptimizerDrift.replacementPlan.validation.invalid.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.replacementPlan.validation.firstBlocker=").append(entry.getProperty("runtimeOptimizerDrift.replacementPlan.validation.firstBlocker", "none")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSketch.count=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSketch.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSketch.ready.count=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSketch.ready.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSketch.blocked.count=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSketch.blocked.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSketch.firstBlocker=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSketch.firstBlocker", "none")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSketch.rewriteBuilderImplemented=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSketch.rewriteBuilderImplemented", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSketch.mutationAllowed=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSketch.mutationAllowed", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSketch.selectedIrReplacement=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSketch.selectedIrReplacement", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSketch.conflict.count=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSketch.conflict.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSketch.conflict.firstBlocker=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSketch.conflict.firstBlocker", "none")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSketch.conflict.conflictResolutionImplemented=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSketch.conflict.conflictResolutionImplemented", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSketch.conflict.selectionApplied=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSketch.conflict.selectionApplied", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSketch.conflict.mutationAllowed=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSketch.conflict.mutationAllowed", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSketch.conflict.selectedIrReplacement=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSketch.conflict.selectedIrReplacement", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSelection.sketch.count=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSelection.sketch.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSelection.sketch.ready.count=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSelection.sketch.ready.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSelection.sketch.blocked.count=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSelection.sketch.blocked.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSelection.conflict.count=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSelection.conflict.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSelection.status=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSelection.status", "not-required")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSelection.firstBlocker=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSelection.firstBlocker", "no-rewrite-sketches")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSelection.rewriteBuilderImplemented=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSelection.rewriteBuilderImplemented", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSelection.conflictResolutionImplemented=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSelection.conflictResolutionImplemented", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSelection.runtimeEquivalenceRequired=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSelection.runtimeEquivalenceRequired", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSelection.runtimeEquivalenceProven=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSelection.runtimeEquivalenceProven", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSelection.approvalRequired=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSelection.approvalRequired", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSelection.approvalAccepted=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSelection.approvalAccepted", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSelection.mutationAllowed=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSelection.mutationAllowed", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSelection.selectionApplied=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSelection.selectionApplied", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteSelection.selectedIrReplacement=").append(entry.getProperty("runtimeOptimizerDrift.rewriteSelection.selectedIrReplacement", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteProof.status=").append(entry.getProperty("runtimeOptimizerDrift.rewriteProof.status", "not-required")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteProof.firstBlocker=").append(entry.getProperty("runtimeOptimizerDrift.rewriteProof.firstBlocker", "no-proof-candidates")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteProof.proofAccepted=").append(entry.getProperty("runtimeOptimizerDrift.rewriteProof.proofAccepted", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteProof.runtimeEquivalencePayload.present=").append(entry.getProperty("runtimeOptimizerDrift.rewriteProof.runtimeEquivalencePayload.present", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteProof.runtimeEquivalencePayload.complete=").append(entry.getProperty("runtimeOptimizerDrift.rewriteProof.runtimeEquivalencePayload.complete", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteProof.rollbackEvidence.present=").append(entry.getProperty("runtimeOptimizerDrift.rewriteProof.rollbackEvidence.present", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteProof.rollbackClean=").append(entry.getProperty("runtimeOptimizerDrift.rewriteProof.rollbackClean", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteProof.approvalAccepted=").append(entry.getProperty("runtimeOptimizerDrift.rewriteProof.approvalAccepted", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteProof.mutationAllowed=").append(entry.getProperty("runtimeOptimizerDrift.rewriteProof.mutationAllowed", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteProof.selectedIrReplacement=").append(entry.getProperty("runtimeOptimizerDrift.rewriteProof.selectedIrReplacement", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteReviewPackage.status=").append(entry.getProperty("runtimeOptimizerDrift.rewriteReviewPackage.status", "not-required")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteReviewPackage.firstBlocker=").append(entry.getProperty("runtimeOptimizerDrift.rewriteReviewPackage.firstBlocker", "no-review-candidates")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteReviewPackage.required=").append(entry.getProperty("runtimeOptimizerDrift.rewriteReviewPackage.required", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteReviewPackage.complete=").append(entry.getProperty("runtimeOptimizerDrift.rewriteReviewPackage.complete", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteReviewPackage.conflict.count=").append(entry.getProperty("runtimeOptimizerDrift.rewriteReviewPackage.conflict.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteReviewPackage.proofAccepted=").append(entry.getProperty("runtimeOptimizerDrift.rewriteReviewPackage.proofAccepted", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteReviewPackage.runtimeEquivalencePayload.present=").append(entry.getProperty("runtimeOptimizerDrift.rewriteReviewPackage.runtimeEquivalencePayload.present", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteReviewPackage.runtimeEquivalencePayload.complete=").append(entry.getProperty("runtimeOptimizerDrift.rewriteReviewPackage.runtimeEquivalencePayload.complete", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteReviewPackage.rollbackEvidence.present=").append(entry.getProperty("runtimeOptimizerDrift.rewriteReviewPackage.rollbackEvidence.present", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteReviewPackage.rollbackClean=").append(entry.getProperty("runtimeOptimizerDrift.rewriteReviewPackage.rollbackClean", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteReviewPackage.approvalAccepted=").append(entry.getProperty("runtimeOptimizerDrift.rewriteReviewPackage.approvalAccepted", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteReviewPackage.mutationAllowed=").append(entry.getProperty("runtimeOptimizerDrift.rewriteReviewPackage.mutationAllowed", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteReviewPackage.selectionApplied=").append(entry.getProperty("runtimeOptimizerDrift.rewriteReviewPackage.selectionApplied", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteReviewPackage.selectedIrReplacement=").append(entry.getProperty("runtimeOptimizerDrift.rewriteReviewPackage.selectedIrReplacement", "false")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.rewriteReviewPackage.manualReviewOnly=").append(entry.getProperty("runtimeOptimizerDrift.rewriteReviewPackage.manualReviewOnly", "true")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.optimizerRule.count=").append(entry.getProperty("runtimeOptimizerDrift.optimizerRule.count", "0")).append('\n');
        builder.append(prefix).append("runtimeOptimizerDrift.optimizerRule.summary=").append(entry.getProperty("runtimeOptimizerDrift.optimizerRule.summary", "none")).append('\n');
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

    private static LinkedHashMap<String, Integer> aggregateSourcePromotionFirstBlockers(LinkedHashMap<String, Properties> kernels) {
        LinkedHashMap<String, Integer> blockers = new LinkedHashMap<>();
        for (Properties entry : kernels.values()) {
            String blocker = entry.getProperty("sourceSwitching.sourcePromotionFirstBlocker", "unknown");
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
        return "true".equals(entry.getProperty("sourceSwitching.productionSourceSwitchingEnabled"))
                || "enabled".equals(entry.getProperty("sourceSwitching.productionSourceSwitching"));
    }

    private static boolean entryProductionPromotionEnabled(Properties entry) {
        return GpuProductionPromotionDecision.PRODUCTION_ENABLED.equals(
                entry.getProperty("sourceSwitching.productionPromotionDecisionMode")
        );
    }

    private static boolean entryProductionPromotionOperatorAccepted(Properties entry) {
        return "true".equals(entry.getProperty("sourceSwitching.productionPromotionOperatorAccepted"));
    }

    private static boolean entryProductionSourceDecision(Properties entry) {
        return "compile-irgpu-source-production".equals(entry.getProperty("sourceSwitching.decision"));
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
