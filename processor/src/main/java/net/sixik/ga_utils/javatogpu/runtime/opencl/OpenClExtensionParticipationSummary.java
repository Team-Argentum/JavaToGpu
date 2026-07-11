package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Aggregates runtime extension participation artifacts into validation and history summaries.
 */
record OpenClExtensionParticipationSummary(String status, List<Entry> entries, String diagnostic) {

    private static final String ARTIFACT_FILE_NAME =
            GpuRuntimeCompileArtifactDumper.RUNTIME_EXTENSION_PARTICIPATION_ARTIFACT;
    private static final String HISTORY_SNAPSHOT_MARKER = "; snapshot=v1:";

    OpenClExtensionParticipationSummary {
        status = status == null || status.isBlank() ? "unknown" : status;
        entries = entries == null ? List.of() : List.copyOf(entries);
        diagnostic = diagnostic == null ? "" : diagnostic;
    }

    static OpenClExtensionParticipationSummary notRecorded() {
        return new OpenClExtensionParticipationSummary("not-recorded", List.of(), "");
    }

    static OpenClExtensionParticipationSummary failed(Throwable failure) {
        return new OpenClExtensionParticipationSummary(
                "failed",
                List.of(),
                failure == null ? "unknown failure" : failure.toString()
        );
    }

    static OpenClExtensionParticipationSummary read(Path workloadGateFile) throws IOException {
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
        Map<String, Properties> participationByResource = loadParticipationArtifacts(artifactRoot);
        ArrayList<Entry> entries = new ArrayList<>();
        for (int index = 0; index < kernelCount; index++) {
            String resource = gate.getProperty("kernel." + index + ".sourceKernelResource", "unknown");
            entries.add(Entry.from(resource, participationByResource.get(resource)));
        }
        return new OpenClExtensionParticipationSummary("recorded", entries, "");
    }

    int totalExecutions() {
        return entries.stream().mapToInt(Entry::executionCount).sum();
    }

    int totalFailedContinued() {
        return entries.stream().mapToInt(Entry::failedContinuedCount).sum();
    }

    int totalFailedClosed() {
        return entries.stream().mapToInt(Entry::failedClosedCount).sum();
    }

    int missingCount() {
        int count = 0;
        for (Entry entry : entries) {
            if ("missing".equals(entry.status())) {
                count++;
            }
        }
        return count;
    }

    String toHistorySummary() {
        if (!"recorded".equals(status)) {
            return status;
        }
        String aggregate = "recorded (kernels=" + entries.size()
                + ", executions=" + totalExecutions()
                + ", failedContinued=" + totalFailedContinued()
                + ", failedClosed=" + totalFailedClosed()
                + ", missing=" + missingCount()
                + ")";
        StringBuilder snapshot = new StringBuilder(aggregate).append(HISTORY_SNAPSHOT_MARKER);
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) {
                snapshot.append(';');
            }
            snapshot.append(entries.get(index).toHistoryToken());
        }
        return snapshot.toString();
    }

    static String aggregateHistorySummary(String summary) {
        if (summary == null) {
            return "unknown";
        }
        int markerIndex = summary.indexOf(HISTORY_SNAPSHOT_MARKER);
        return markerIndex < 0 ? summary : summary.substring(0, markerIndex);
    }

    static Optional<List<Entry>> parseHistoryEntries(String summary) {
        if (summary == null) {
            return Optional.empty();
        }
        int markerIndex = summary.indexOf(HISTORY_SNAPSHOT_MARKER);
        if (markerIndex < 0) {
            return Optional.empty();
        }
        String payload = summary.substring(markerIndex + HISTORY_SNAPSHOT_MARKER.length());
        if (payload.isBlank()) {
            return Optional.of(List.of());
        }
        ArrayList<Entry> parsed = new ArrayList<>();
        for (String token : payload.split(";", -1)) {
            Optional<Entry> entry = Entry.fromHistoryToken(token);
            if (entry.isEmpty()) {
                return Optional.empty();
            }
            parsed.add(entry.get());
        }
        return Optional.of(List.copyOf(parsed));
    }

    String toMarkdown() {
        StringBuilder markdown = new StringBuilder();
        markdown.append("## Runtime Extension Participation\n\n");
        markdown.append("- Status: `").append(inline(status)).append("`\n");
        if (!diagnostic.isBlank()) {
            markdown.append("- Diagnostic: `").append(inline(diagnostic)).append("`\n\n");
            return markdown.toString();
        }
        markdown.append("- Kernel count: `").append(entries.size()).append("`\n");
        markdown.append("- Execution count: `").append(totalExecutions()).append("`\n");
        markdown.append("- Failed continued: `").append(totalFailedContinued()).append("`\n");
        markdown.append("- Failed closed: `").append(totalFailedClosed()).append("`\n");
        markdown.append("- Missing artifacts: `").append(missingCount()).append("`\n\n");
        markdown.append("- Participation sources: `").append(inline(sourceSummary())).append("`\n\n");
        if (entries.isEmpty()) {
            return markdown.toString();
        }
        markdown.append("| Kernel resource | Status | Executions | Failed continued | Failed closed | Pipeline continued | First failure |\n");
        markdown.append("| --- | --- | ---: | ---: | ---: | --- | --- |\n");
        for (Entry entry : entries) {
            markdown.append("| `").append(table(entry.kernelResource())).append("` | `")
                    .append(table(entry.status())).append("` | `")
                    .append(entry.executionCount()).append("` | `")
                    .append(entry.failedContinuedCount()).append("` | `")
                    .append(entry.failedClosedCount()).append("` | `")
                    .append(table(entry.pipelineContinuedAll())).append("` | `")
                    .append(table(entry.firstFailure())).append("` |\n");
        }
        markdown.append('\n');
        return markdown.toString();
    }

    String sourceSummary() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (Entry entry : entries) {
            for (Map.Entry<String, Integer> source : entry.sourceCounts().entrySet()) {
                counts.merge(source.getKey(), source.getValue(), Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return "none";
        }
        StringBuilder summary = new StringBuilder();
        counts.forEach((source, count) -> {
            if (!summary.isEmpty()) {
                summary.append(", ");
            }
            summary.append(source).append('=').append(count);
        });
        return summary.toString();
    }

    private static Map<String, Properties> loadParticipationArtifacts(Path artifactRoot) throws IOException {
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
            return Integer.parseInt(value);
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
            int executionCount,
            int failedContinuedCount,
            int failedClosedCount,
            String pipelineContinuedAll,
            String firstFailure,
            Map<String, Integer> sourceCounts
    ) {

        private static final Base64.Encoder HISTORY_ENCODER = Base64.getUrlEncoder().withoutPadding();
        private static final Base64.Decoder HISTORY_DECODER = Base64.getUrlDecoder();

        Entry {
            kernelResource = normalize(kernelResource, "unknown");
            status = normalize(status, "unknown");
            executionCount = Math.max(0, executionCount);
            failedContinuedCount = Math.max(0, failedContinuedCount);
            failedClosedCount = Math.max(0, failedClosedCount);
            pipelineContinuedAll = normalize(pipelineContinuedAll, "unknown");
            firstFailure = normalize(firstFailure, "none");
            sourceCounts = normalizeSourceCounts(sourceCounts);
        }

        Entry(
                String kernelResource,
                String status,
                int executionCount,
                int failedContinuedCount,
                int failedClosedCount,
                String pipelineContinuedAll,
                String firstFailure
        ) {
            this(
                    kernelResource,
                    status,
                    executionCount,
                    failedContinuedCount,
                    failedClosedCount,
                    pipelineContinuedAll,
                    firstFailure,
                    Map.of()
            );
        }

        static Entry from(String kernelResource, Properties properties) {
            if (properties == null || properties.isEmpty()) {
                return new Entry(kernelResource, "missing", 0, 0, 0, "unknown", "none");
            }
            return new Entry(
                    kernelResource,
                    properties.getProperty("status", "unknown"),
                    parseInt(properties.getProperty("entry.count"), 0),
                    parseInt(properties.getProperty("failedContinued.count"), 0),
                    parseInt(properties.getProperty("failedClosed.count"), 0),
                    properties.getProperty("pipelineContinued.all", "unknown"),
                    properties.getProperty("firstFailure", "none"),
                    parseSourceCounts(properties)
            );
        }

        String toHistoryToken() {
            return String.join(".",
                    encode(kernelResource),
                    encode(status),
                    encode(Integer.toString(executionCount)),
                    encode(Integer.toString(failedContinuedCount)),
                    encode(Integer.toString(failedClosedCount)),
                    encode(pipelineContinuedAll),
                    encode(firstFailure),
                    encode(formatSourceCounts(sourceCounts))
            );
        }

        static Optional<Entry> fromHistoryToken(String token) {
            String[] fields = token.split("\\.", -1);
            if (fields.length != 7 && fields.length != 8) {
                return Optional.empty();
            }
            try {
                return Optional.of(new Entry(
                        decode(fields[0]),
                        decode(fields[1]),
                        Integer.parseInt(decode(fields[2])),
                        Integer.parseInt(decode(fields[3])),
                        Integer.parseInt(decode(fields[4])),
                        decode(fields[5]),
                        decode(fields[6]),
                        fields.length == 8 ? parseSourceCounts(decode(fields[7])) : Map.of()
                ));
            } catch (IllegalArgumentException failure) {
                return Optional.empty();
            }
        }

        private static String normalize(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }

        private static String encode(String value) {
            return HISTORY_ENCODER.encodeToString(inline(value).getBytes(StandardCharsets.UTF_8));
        }

        private static String decode(String value) {
            return new String(HISTORY_DECODER.decode(value), StandardCharsets.UTF_8);
        }

        private static Map<String, Integer> parseSourceCounts(Properties properties) {
            LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
            int count = parseInt(properties.getProperty("entry.count"), 0);
            for (int index = 0; index < count; index++) {
                String source = properties.getProperty("entry." + index + ".source", "");
                if (!source.isBlank()) {
                    counts.merge(source, 1, Integer::sum);
                }
            }
            return counts;
        }

        private static Map<String, Integer> parseSourceCounts(String summary) {
            LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
            if (summary == null || summary.isBlank() || "none".equals(summary)) {
                return counts;
            }
            for (String token : summary.split(",", -1)) {
                String trimmed = token.trim();
                int separator = trimmed.lastIndexOf('=');
                if (separator <= 0 || separator == trimmed.length() - 1) {
                    return Map.of();
                }
                counts.put(trimmed.substring(0, separator), parseInt(trimmed.substring(separator + 1), 0));
            }
            return counts;
        }

        private static Map<String, Integer> normalizeSourceCounts(Map<String, Integer> values) {
            if (values == null || values.isEmpty()) {
                return Map.of();
            }
            LinkedHashMap<String, Integer> normalized = new LinkedHashMap<>();
            values.forEach((source, count) -> {
                if (source != null && !source.isBlank() && count != null && count > 0) {
                    normalized.put(source, count);
                }
            });
            return java.util.Collections.unmodifiableMap(normalized);
        }

        private static String formatSourceCounts(Map<String, Integer> counts) {
            if (counts == null || counts.isEmpty()) {
                return "none";
            }
            StringBuilder summary = new StringBuilder();
            counts.forEach((source, count) -> {
                if (!summary.isEmpty()) {
                    summary.append(", ");
                }
                summary.append(source).append('=').append(count);
            });
            return summary.toString();
        }
    }
}
