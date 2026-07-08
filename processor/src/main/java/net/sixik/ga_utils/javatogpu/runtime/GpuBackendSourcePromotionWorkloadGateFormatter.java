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
        Properties latest = loadProperties(latestGateProperties);
        Properties latestSourceSwitchingDecision = loadProperties(latestSourceSwitchingDecisionProperties);
        Properties latestRuntimeIrHandoff = loadProperties(latestRuntimeIrHandoffProperties);
        Properties latestRuntimeProductionMutationSafety = loadProperties(latestRuntimeProductionMutationSafetyProperties);
        Properties latestI3ReadinessSummary = loadProperties(latestI3ReadinessSummaryProperties);
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
        boolean allSourceParityMatched = !kernels.isEmpty()
                && kernels.values().stream().allMatch(entry -> "true".equals(entry.getProperty("sourceParityMatched")));
        boolean allRuntimeEquivalencePassed = !kernels.isEmpty()
                && kernels.values().stream().allMatch(entry -> "true".equals(entry.getProperty("runtimeEquivalencePassed")));
        LinkedHashMap<String, Integer> aggregateFamilies = aggregatePromotionBlockerFamilies(kernels);
        StringBuilder builder = new StringBuilder();
        builder.append("status=blocked\n");
        builder.append("reviewReady=false\n");
        builder.append("sourceParityMatched=").append(allSourceParityMatched).append('\n');
        builder.append("runtimeEquivalencePassed=").append(allRuntimeEquivalencePassed).append('\n');
        builder.append("realWorkloadEvidence=runtime-snapshot\n");
        builder.append("scope=real-workload\n");
        builder.append("productionSourceSwitching=false\n");
        builder.append("sourceSwitching.count=").append(kernels.size()).append('\n');
        builder.append("kernel.count=").append(kernels.size()).append('\n');
        builder.append("blockerFamily.count=").append(aggregateFamilies.size()).append('\n');
        int familyIndex = 0;
        for (Map.Entry<String, Integer> family : aggregateFamilies.entrySet()) {
            builder.append("blockerFamily.").append(familyIndex).append(".name=").append(family.getKey()).append('\n');
            builder.append("blockerFamily.").append(familyIndex).append(".count=").append(family.getValue()).append('\n');
            familyIndex++;
        }
        builder.append("reason=").append(anyReviewReady
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
        builder.append(prefix).append("productionSourceSwitching=false\n");
        appendSourceSwitchingDecision(builder, prefix, entry);
        appendRuntimeIrHandoff(builder, prefix, entry);
        appendRuntimeProductionMutationSafety(builder, prefix, entry);
        appendI3ReadinessSummary(builder, prefix, entry);
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
