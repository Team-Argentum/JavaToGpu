package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeCompileArtifactDumper;

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
 * Aggregates {@code @GPUTest} runtime evidence artifacts into the OpenCL validation report.
 */
record OpenClMethodTestEvidenceSummary(String status, List<Entry> entries, String diagnostic) {

    private static final String ARTIFACT_FILE_NAME =
            GpuRuntimeCompileArtifactDumper.RUNTIME_METHOD_TEST_EVIDENCE_ARTIFACT;

    OpenClMethodTestEvidenceSummary {
        status = status == null || status.isBlank() ? "unknown" : status;
        entries = entries == null ? List.of() : List.copyOf(entries);
        diagnostic = diagnostic == null ? "" : diagnostic;
    }

    static OpenClMethodTestEvidenceSummary notRecorded() {
        return new OpenClMethodTestEvidenceSummary("not-recorded", List.of(), "");
    }

    static OpenClMethodTestEvidenceSummary failed(Throwable failure) {
        return new OpenClMethodTestEvidenceSummary(
                "failed",
                List.of(),
                failure == null ? "unknown failure" : failure.toString()
        );
    }

    static OpenClMethodTestEvidenceSummary read(Path workloadGateFile) throws IOException {
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
        return new OpenClMethodTestEvidenceSummary("recorded", entries, "");
    }

    int metadataRecordedCount() {
        return (int) entries.stream().filter(entry -> "recorded".equals(entry.metadataStatus())).count();
    }

    int cacheEvidenceRecordedCount() {
        return (int) entries.stream().filter(entry -> !"not-recorded".equals(entry.cacheEvidenceStatus())).count();
    }

    int missingCount() {
        return (int) entries.stream().filter(entry -> "missing".equals(entry.status())).count();
    }

    int totalTestVectors() {
        return entries.stream().mapToInt(Entry::testVectorCount).sum();
    }

    int totalSelectionProbes() {
        return entries.stream().mapToInt(Entry::selectionProbeCount).sum();
    }

    int totalPassedEvidence() {
        return entries.stream().mapToInt(Entry::cachePassedCount).sum();
    }

    int totalFailedEvidence() {
        return entries.stream().mapToInt(Entry::cacheFailedCount).sum();
    }

    int totalMissingEvidence() {
        return entries.stream().mapToInt(Entry::cacheMissingCount).sum();
    }

    String toMarkdown() {
        StringBuilder markdown = new StringBuilder();
        markdown.append("## Method Test Evidence\n\n");
        markdown.append("- Status: `").append(inline(status)).append("`\n");
        if (!diagnostic.isBlank()) {
            markdown.append("- Diagnostic: `").append(inline(diagnostic)).append("`\n\n");
            return markdown.toString();
        }
        markdown.append("- Kernel count: `").append(entries.size()).append("`\n");
        markdown.append("- Metadata recorded kernels: `").append(metadataRecordedCount()).append("`\n");
        markdown.append("- Cache evidence recorded kernels: `").append(cacheEvidenceRecordedCount()).append("`\n");
        markdown.append("- Missing artifacts: `").append(missingCount()).append("`\n");
        markdown.append("- Test vectors: `").append(totalTestVectors()).append("`\n");
        markdown.append("- Selection probes: `").append(totalSelectionProbes()).append("`\n");
        markdown.append("- Cached evidence passed/failed/missing: `")
                .append(totalPassedEvidence())
                .append('/')
                .append(totalFailedEvidence())
                .append('/')
                .append(totalMissingEvidence())
                .append("`\n\n");
        if (entries.isEmpty()) {
            return markdown.toString();
        }
        markdown.append("| Kernel resource | Status | Metadata | Vectors | Selection probes | Cache evidence | Passed | Failed | Missing | First blocker |\n");
        markdown.append("| --- | --- | --- | ---: | ---: | --- | ---: | ---: | ---: | --- |\n");
        for (Entry entry : entries) {
            markdown.append("| `").append(table(entry.kernelResource())).append("` | `")
                    .append(table(entry.status())).append("` | `")
                    .append(table(entry.metadataStatus())).append("` | `")
                    .append(entry.testVectorCount()).append("` | `")
                    .append(entry.selectionProbeCount()).append("` | `")
                    .append(table(entry.cacheEvidenceStatus())).append("` | `")
                    .append(entry.cachePassedCount()).append("` | `")
                    .append(entry.cacheFailedCount()).append("` | `")
                    .append(entry.cacheMissingCount()).append("` | `")
                    .append(table(entry.firstBlocker())).append("` |\n");
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
                String resource = properties.getProperty("backendResource", "");
                if (!resource.isBlank() && !"unknown".equals(resource)) {
                    artifacts.put(resource, properties);
                }
            }
        }
        return artifacts;
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
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String inline(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replace('`', '\'');
    }

    private static String table(String value) {
        return inline(value).replace("|", "\\|");
    }

    record Entry(
            String kernelResource,
            String status,
            String metadataStatus,
            int testVectorCount,
            int selectionProbeCount,
            String cacheEvidenceStatus,
            int cachePassedCount,
            int cacheFailedCount,
            int cacheMissingCount,
            int cacheBlockedCount,
            String firstBlocker
    ) {

        Entry {
            kernelResource = normalize(kernelResource, "unknown");
            status = normalize(status, "unknown");
            metadataStatus = normalize(metadataStatus, "unknown");
            testVectorCount = Math.max(0, testVectorCount);
            selectionProbeCount = Math.max(0, selectionProbeCount);
            cacheEvidenceStatus = normalize(cacheEvidenceStatus, "not-recorded");
            cachePassedCount = Math.max(0, cachePassedCount);
            cacheFailedCount = Math.max(0, cacheFailedCount);
            cacheMissingCount = Math.max(0, cacheMissingCount);
            cacheBlockedCount = Math.max(0, cacheBlockedCount);
            firstBlocker = normalize(firstBlocker, "none");
        }

        static Entry from(String kernelResource, Properties properties) {
            if (properties == null || properties.isEmpty()) {
                return new Entry(kernelResource, "missing", "missing", 0, 0, "not-recorded", 0, 0, 0, 0, "artifact-missing");
            }
            return new Entry(
                    kernelResource,
                    properties.getProperty("status", "unknown"),
                    properties.getProperty("metadata.status", "unknown"),
                    parseInt(properties.getProperty("metadata.entryTestVector.count"), 0),
                    parseInt(properties.getProperty("metadata.selectionProbe.count"), 0),
                    properties.getProperty("cacheEvidence.status", "not-recorded"),
                    parseInt(properties.getProperty("cacheEvidence.passed.count"), 0),
                    parseInt(properties.getProperty("cacheEvidence.failed.count"), 0),
                    parseInt(properties.getProperty("cacheEvidence.missing.count"), 0),
                    parseInt(properties.getProperty("cacheEvidence.blocked.count"), 0),
                    properties.getProperty("firstBlocker", "none")
            );
        }

        private static String normalize(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
