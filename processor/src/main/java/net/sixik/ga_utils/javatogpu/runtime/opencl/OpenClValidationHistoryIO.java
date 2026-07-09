package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;

final class OpenClValidationHistoryIO {

    private OpenClValidationHistoryIO() {
    }

    static List<OpenClValidationHistoryEntry> readAll(Path path) throws IOException {
        List<OpenClValidationHistoryEntry> entries = new ArrayList<>();
        if (!Files.exists(path)) {
            return entries;
        }
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        }
        TreeSet<String> prefixes = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            int separator = key.indexOf('.');
            if (separator > 0) {
                prefixes.add(key.substring(0, separator));
            }
        }
        for (String prefix : prefixes) {
            entries.add(new OpenClValidationHistoryEntry(
                    Instant.parse(properties.getProperty(prefix + ".generatedAtUtc")),
                    properties.getProperty(prefix + ".requestedVendorLane", "unknown"),
                    properties.getProperty(prefix + ".backendName", "unknown"),
                    properties.getProperty(prefix + ".deviceLabel", "unknown"),
                    properties.getProperty(prefix + ".vendor", "unknown"),
                    properties.getProperty(prefix + ".driverVersion", "unknown"),
                    properties.getProperty(prefix + ".deviceVersion", "unknown"),
                    properties.getProperty(prefix + ".bucketSummary", "unknown"),
                    properties.getProperty(prefix + ".longRunningStatus", "unknown"),
                    properties.getProperty(prefix + ".workloadStatus", "unknown"),
                    properties.getProperty(prefix + ".irGpuSourceReviewStatus", "not recorded"),
                    properties.getProperty(prefix + ".productionSourceSwitchingValidationStatus", "not recorded"),
                    properties.getProperty(prefix + ".backendSourcePromotionContractStatus",
                            properties.getProperty(prefix + ".backendSourcePromotionStatus", "not recorded")),
                    properties.getProperty(prefix + ".backendSourcePromotionWorkloadStatus", "not-promoted"),
                    properties.getProperty(prefix + ".productionPromotionExplainabilityStatus", "not recorded")
            ));
        }
        entries.sort(Comparator.comparing(OpenClValidationHistoryEntry::generatedAtUtc).reversed());
        return entries;
    }

    static void writeAll(Path path, List<OpenClValidationHistoryEntry> entries) throws IOException {
        Properties properties = new Properties();
        for (int index = 0; index < entries.size(); index++) {
            String prefix = "entry" + index;
            OpenClValidationHistoryEntry entry = entries.get(index);
            properties.setProperty(prefix + ".generatedAtUtc", entry.generatedAtUtc().toString());
            properties.setProperty(prefix + ".requestedVendorLane", entry.requestedVendorLane());
            properties.setProperty(prefix + ".backendName", entry.backendName());
            properties.setProperty(prefix + ".deviceLabel", entry.deviceLabel());
            properties.setProperty(prefix + ".vendor", entry.vendor());
            properties.setProperty(prefix + ".driverVersion", entry.driverVersion());
            properties.setProperty(prefix + ".deviceVersion", entry.deviceVersion());
            properties.setProperty(prefix + ".bucketSummary", entry.bucketSummary());
            properties.setProperty(prefix + ".longRunningStatus", entry.longRunningStatus());
            properties.setProperty(prefix + ".workloadStatus", entry.workloadStatus());
            properties.setProperty(prefix + ".irGpuSourceReviewStatus", entry.irGpuSourceReviewStatus());
            properties.setProperty(prefix + ".productionSourceSwitchingValidationStatus", entry.productionSourceSwitchingValidationStatus());
            properties.setProperty(prefix + ".backendSourcePromotionContractStatus", entry.backendSourcePromotionContractStatus());
            properties.setProperty(prefix + ".backendSourcePromotionWorkloadStatus", entry.backendSourcePromotionWorkloadStatus());
            properties.setProperty(prefix + ".productionPromotionExplainabilityStatus", entry.productionPromotionExplainabilityStatus());
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            properties.store(outputStream, "JavaToGpu OpenCL validation history");
        }
    }

    static void writeMarkdown(Path path, List<OpenClValidationHistoryEntry> entries) throws IOException {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# OpenCL Validation History\n\n");
        markdown.append("| Generated (UTC) | Lane | Backend | Device | Vendor | Driver | Device Version | Buckets | Long-Running | Workloads | IrGpu Source Review | Controlled Production Source Switching | Backend Source Contract Fixture | Backend Source Workload Gate | Production Promotion Explainability |\n");
        markdown.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (OpenClValidationHistoryEntry entry : entries) {
            markdown.append("| ")
                    .append(entry.generatedAtUtc())
                    .append(" | ")
                    .append(escapeTable(entry.requestedVendorLane()))
                    .append(" | ")
                    .append(escapeTable(entry.backendName()))
                    .append(" | ")
                    .append(escapeTable(entry.deviceLabel()))
                    .append(" | ")
                    .append(escapeTable(entry.vendor()))
                    .append(" | ")
                    .append(escapeTable(entry.driverVersion()))
                    .append(" | ")
                    .append(escapeTable(entry.deviceVersion()))
                    .append(" | ")
                    .append(escapeTable(entry.bucketSummary()))
                    .append(" | ")
                    .append(escapeTable(entry.longRunningStatus()))
                    .append(" | ")
                    .append(escapeTable(entry.workloadStatus()))
                    .append(" | ")
                    .append(escapeTable(entry.irGpuSourceReviewStatus()))
                    .append(" | ")
                    .append(escapeTable(entry.productionSourceSwitchingValidationStatus()))
                    .append(" | ")
                    .append(escapeTable(entry.backendSourcePromotionContractStatus()))
                    .append(" | ")
                    .append(escapeTable(entry.backendSourcePromotionWorkloadStatus()))
                    .append(" | ")
                    .append(escapeTable(entry.productionPromotionExplainabilityStatus()))
                    .append(" |\n");
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, markdown.toString(), StandardCharsets.UTF_8);
    }

    private static String escapeTable(String value) {
        return value.replace("|", "\\|").replace('\r', ' ').replace('\n', ' ');
    }
}
