package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Aggregates optional ir-optimizer evidence artifacts into the OpenCL validation report.
 */
record OpenClRuntimeIrOptimizerEvidenceSummary(String status, List<Entry> entries, String diagnostic) {

    private static final String ARTIFACT_FILE_NAME =
            GpuRuntimeCompileArtifactDumper.RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT;

    OpenClRuntimeIrOptimizerEvidenceSummary {
        status = status == null || status.isBlank() ? "unknown" : status;
        entries = entries == null ? List.of() : List.copyOf(entries);
        diagnostic = diagnostic == null ? "" : diagnostic;
    }

    static OpenClRuntimeIrOptimizerEvidenceSummary notRecorded() {
        return new OpenClRuntimeIrOptimizerEvidenceSummary("not-recorded", List.of(), "");
    }

    static OpenClRuntimeIrOptimizerEvidenceSummary failed(Throwable failure) {
        return new OpenClRuntimeIrOptimizerEvidenceSummary(
                "failed",
                List.of(),
                failure == null ? "unknown failure" : failure.toString()
        );
    }

    static OpenClRuntimeIrOptimizerEvidenceSummary read(Path workloadGateFile) throws IOException {
        if (workloadGateFile == null || !Files.isRegularFile(workloadGateFile)) {
            return notRecorded();
        }
        Properties gate = loadProperties(workloadGateFile);
        int kernelCount = parseInt(gate.getProperty("kernel.count"), 0);
        if (kernelCount <= 0) {
            return notRecorded();
        }

        Path reportDirectory = workloadGateFile.getParent();
        Path artifactRoot = reportDirectory == null
                ? null
                : reportDirectory.resolve("runtime-compile-artifacts");
        Map<String, Properties> evidenceByResource = loadEvidenceArtifacts(artifactRoot);
        ArrayList<Entry> entries = new ArrayList<>();
        for (int index = 0; index < kernelCount; index++) {
            String resource = gate.getProperty("kernel." + index + ".sourceKernelResource", "unknown");
            entries.add(Entry.from(resource, evidenceByResource.get(resource)));
        }
        return new OpenClRuntimeIrOptimizerEvidenceSummary("recorded", entries, "");
    }

    int count(String expectedStatus) {
        int count = 0;
        for (Entry entry : entries) {
            if (expectedStatus.equals(entry.status())) {
                count++;
            }
        }
        return count;
    }

    int totalPassCount() {
        return entries.stream().mapToInt(Entry::passCount).sum();
    }

    int totalProposalOnlyCount() {
        return entries.stream().mapToInt(Entry::proposalOnlyCount).sum();
    }

    int totalSelectedOptimizedCount() {
        return entries.stream().mapToInt(Entry::selectedOptimizedCount).sum();
    }

    int totalRolledBackCount() {
        return entries.stream().mapToInt(Entry::rolledBackCount).sum();
    }

    int totalApprovalTemplatePendingCount() {
        return entries.stream().mapToInt(Entry::approvalTemplatePendingCount).sum();
    }

    int totalApprovalTemplateNotApplicableCount() {
        return entries.stream().mapToInt(Entry::approvalTemplateNotApplicableCount).sum();
    }

    String providerSummary() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (Entry entry : entries) {
            for (Map.Entry<String, Integer> provider : entry.providerCounts().entrySet()) {
                counts.merge(provider.getKey(), provider.getValue(), Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return "none";
        }
        StringBuilder summary = new StringBuilder();
        counts.forEach((provider, count) -> {
            if (!summary.isEmpty()) {
                summary.append(", ");
            }
            summary.append(provider).append('=').append(count);
        });
        return summary.toString();
    }

    String toMarkdown() {
        StringBuilder markdown = new StringBuilder();
        markdown.append("## Runtime IR Optimizer Evidence\n\n");
        markdown.append("- Status: `").append(inline(status)).append("`\n");
        if (!diagnostic.isBlank()) {
            markdown.append("- Diagnostic: `").append(inline(diagnostic)).append("`\n\n");
            return markdown.toString();
        }
        markdown.append("- Kernel count: `").append(entries.size()).append("`\n");
        markdown.append("- Recorded kernels: `").append(count("recorded")).append("`\n");
        markdown.append("- Missing artifacts: `").append(count("missing")).append("`\n");
        markdown.append("- Proposal pass count: `").append(totalPassCount()).append("`\n");
        markdown.append("- Proposal-only count: `").append(totalProposalOnlyCount()).append("`\n");
        markdown.append("- Selected optimized count: `").append(totalSelectedOptimizedCount()).append("`\n");
        markdown.append("- Rolled back count: `").append(totalRolledBackCount()).append("`\n");
        markdown.append("- Approval templates pending: `").append(totalApprovalTemplatePendingCount()).append("`\n");
        markdown.append("- Approval templates not applicable: `").append(totalApprovalTemplateNotApplicableCount()).append("`\n");
        markdown.append("- Providers: `").append(inline(providerSummary())).append("`\n\n");
        if (entries.isEmpty()) {
            return markdown.toString();
        }
        markdown.append("| Kernel resource | Status | Passes | Proposal-only | Selected | Rolled back | Approval pending | Approval N/A | Providers |\n");
        markdown.append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |\n");
        for (Entry entry : entries) {
            markdown.append("| `").append(table(entry.kernelResource())).append("` | `")
                    .append(table(entry.status())).append("` | `")
                    .append(entry.passCount()).append("` | `")
                    .append(entry.proposalOnlyCount()).append("` | `")
                    .append(entry.selectedOptimizedCount()).append("` | `")
                    .append(entry.rolledBackCount()).append("` | `")
                    .append(entry.approvalTemplatePendingCount()).append("` | `")
                    .append(entry.approvalTemplateNotApplicableCount()).append("` | `")
                    .append(table(formatProviderCounts(entry.providerCounts()))).append("` |\n");
        }
        markdown.append('\n');
        return markdown.toString();
    }

    private static Map<String, Properties> loadEvidenceArtifacts(Path artifactRoot) throws IOException {
        LinkedHashMap<String, Properties> artifacts = new LinkedHashMap<>();
        if (artifactRoot == null || !Files.isDirectory(artifactRoot)) {
            return artifacts;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(artifactRoot)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(candidate -> ARTIFACT_FILE_NAME.equals(candidate.getFileName().toString()))
                    .toList()) {
                Properties properties = loadProperties(path);
                String resource = firstNonBlank(
                        properties.getProperty("backendResource", ""),
                        properties.getProperty("kernelResource", "")
                );
                if (resource.isBlank()) {
                    resource = inferResourceFromSiblingArtifacts(path.getParent());
                }
                if (!resource.isBlank() && !"unknown".equals(resource)) {
                    artifacts.put(resource, properties);
                }
            }
        }
        return artifacts;
    }

    private static String inferResourceFromSiblingArtifacts(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            return "";
        }
        for (String sibling : List.of(
                GpuRuntimeCompileArtifactDumper.RUNTIME_EXTENSION_PARTICIPATION_ARTIFACT,
                "backend-module.properties",
                "backend-diagnostics.properties"
        )) {
            Path path = directory.resolve(sibling);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            Properties properties = loadProperties(path);
            String resource = firstNonBlank(
                    properties.getProperty("backendResource", ""),
                    properties.getProperty("resource", "")
            );
            if (!resource.isBlank() && !"unknown".equals(resource)) {
                return resource;
            }
        }
        return "";
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String inline(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replace('`', '\'');
    }

    private static String table(String value) {
        return inline(value).replace("|", "\\|");
    }

    private static String formatProviderCounts(Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return "none";
        }
        StringBuilder summary = new StringBuilder();
        counts.forEach((provider, count) -> {
            if (!summary.isEmpty()) {
                summary.append(", ");
            }
            summary.append(provider).append('=').append(count);
        });
        return summary.toString();
    }

    record Entry(
            String kernelResource,
            String status,
            int passCount,
            int proposalOnlyCount,
            int selectedOptimizedCount,
            int rolledBackCount,
            int approvalTemplatePendingCount,
            int approvalTemplateNotApplicableCount,
            Map<String, Integer> providerCounts
    ) {

        Entry {
            kernelResource = normalize(kernelResource, "unknown");
            status = normalize(status, "unknown");
            passCount = Math.max(0, passCount);
            proposalOnlyCount = Math.max(0, proposalOnlyCount);
            selectedOptimizedCount = Math.max(0, selectedOptimizedCount);
            rolledBackCount = Math.max(0, rolledBackCount);
            approvalTemplatePendingCount = Math.max(0, approvalTemplatePendingCount);
            approvalTemplateNotApplicableCount = Math.max(0, approvalTemplateNotApplicableCount);
            providerCounts = normalizeProviderCounts(providerCounts);
        }

        static Entry from(String kernelResource, Properties properties) {
            if (properties == null || properties.isEmpty()) {
                return new Entry(kernelResource, "missing", 0, 0, 0, 0, 0, 0, Map.of());
            }
            return new Entry(
                    kernelResource,
                    properties.getProperty("status", "unknown"),
                    parseInt(properties.getProperty("pass.count"), 0),
                    parseInt(properties.getProperty("proposalOnly.count"), 0),
                    parseInt(properties.getProperty("selectedOptimized.count"), 0),
                    parseInt(properties.getProperty("rolledBack.count"), 0),
                    parseInt(properties.getProperty("approvalTemplate.pending.count"), 0),
                    parseInt(properties.getProperty("approvalTemplate.notApplicable.count"), 0),
                    parseProviderCounts(properties)
            );
        }

        private static String normalize(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }

        private static Map<String, Integer> parseProviderCounts(Properties properties) {
            LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
            int count = parseInt(properties.getProperty("pass.count"), 0);
            for (int index = 0; index < count; index++) {
                String provider = properties.getProperty("pass." + index + ".passVersion", "");
                if (!provider.isBlank()) {
                    counts.merge(provider, 1, Integer::sum);
                }
            }
            return counts;
        }

        private static Map<String, Integer> normalizeProviderCounts(Map<String, Integer> values) {
            if (values == null || values.isEmpty()) {
                return Map.of();
            }
            LinkedHashMap<String, Integer> normalized = new LinkedHashMap<>();
            values.forEach((provider, count) -> {
                if (provider != null && !provider.isBlank() && count != null && count > 0) {
                    normalized.put(provider, count);
                }
            });
            return java.util.Collections.unmodifiableMap(normalized);
        }
    }
}
