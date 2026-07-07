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
        Properties latest = loadProperties(latestGateProperties);
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
        copyGateProperty(latest, latestEntry, "fallbackClean");
        copyGateProperty(latest, latestEntry, "selectedSource");
        copyGateProperty(latest, latestEntry, "payloadFormat");
        copyGateProperty(latest, latestEntry, "runtimeLoadMode");
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
        builder.append(prefix).append("fallbackClean=").append(entry.getProperty("fallbackClean", "unknown")).append('\n');
        builder.append(prefix).append("selectedSource=").append(entry.getProperty("selectedSource", "unknown")).append('\n');
        builder.append(prefix).append("payloadFormat=").append(entry.getProperty("payloadFormat", "unknown")).append('\n');
        builder.append(prefix).append("runtimeLoadMode=").append(entry.getProperty("runtimeLoadMode", "unknown")).append('\n');
        builder.append(prefix).append("realWorkloadEvidence=").append(entry.getProperty("realWorkloadEvidence", "runtime-snapshot")).append('\n');
        builder.append(prefix).append("productionSourceSwitching=false\n");
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
