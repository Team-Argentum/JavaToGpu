package net.sixik.ga_utils.javatogpu.runtime.opencl;

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
 * Aggregates per-kernel compiler resource metrics into validation and history artifacts.
 */
record OpenClCompilerResourceSummary(String status, List<Entry> entries, String diagnostic) {

    private static final String ARTIFACT_FILE_NAME = "backend-compiler-feedback.properties";
    private static final String HISTORY_SNAPSHOT_MARKER = "; snapshot=v1:";

    OpenClCompilerResourceSummary {
        status = status == null || status.isBlank() ? "unknown" : status;
        entries = entries == null ? List.of() : List.copyOf(entries);
        diagnostic = diagnostic == null ? "" : diagnostic;
    }

    static OpenClCompilerResourceSummary notRecorded() {
        return new OpenClCompilerResourceSummary("not-recorded", List.of(), "");
    }

    static OpenClCompilerResourceSummary failed(Throwable failure) {
        return new OpenClCompilerResourceSummary(
                "failed",
                List.of(),
                failure == null ? "unknown failure" : failure.toString()
        );
    }

    static OpenClCompilerResourceSummary read(Path workloadGateFile) throws IOException {
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
        Map<String, Properties> feedbackByResource = loadCompilerFeedback(artifactRoot);
        ArrayList<Entry> entries = new ArrayList<>();
        for (int index = 0; index < kernelCount; index++) {
            String resource = gate.getProperty("kernel." + index + ".sourceKernelResource", "unknown");
            entries.add(Entry.from(resource, feedbackByResource.get(resource)));
        }
        return new OpenClCompilerResourceSummary("recorded", entries, "");
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

    int spillingCount() {
        int count = 0;
        for (Entry entry : entries) {
            if (entry.knownSpillBytes() > 0) {
                count++;
            }
        }
        return count;
    }

    int stackUsingCount() {
        int count = 0;
        for (Entry entry : entries) {
            if (entry.stackFrameBytes() > 0) {
                count++;
            }
        }
        return count;
    }

    int maxRegisters() {
        int maximum = -1;
        for (Entry entry : entries) {
            maximum = Math.max(maximum, entry.registerCount());
        }
        return maximum;
    }

    int totalSpillBytes() {
        int total = 0;
        boolean known = false;
        for (Entry entry : entries) {
            int spillBytes = entry.knownSpillBytes();
            if (spillBytes >= 0) {
                total += spillBytes;
                known = true;
            }
        }
        return known ? total : -1;
    }

    int maxStackBytes() {
        int maximum = -1;
        for (Entry entry : entries) {
            maximum = Math.max(maximum, entry.stackFrameBytes());
        }
        return maximum;
    }

    String toHistorySummary() {
        if (!"recorded".equals(status)) {
            return status;
        }
        String aggregate = "recorded (kernels=" + entries.size()
                + ", available=" + count("recorded")
                + ", unavailable=" + count("unavailable")
                + ", missing=" + count("missing")
                + ", spilling=" + spillingCount()
                + ", stackUsing=" + stackUsingCount()
                + ", maxRegisters=" + maxRegisters()
                + ", totalSpillBytes=" + totalSpillBytes()
                + ", maxStackBytes=" + maxStackBytes()
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
        markdown.append("## Compiler Resource Metrics\n\n");
        markdown.append("- Status: `").append(inline(status)).append("`\n");
        if (!diagnostic.isBlank()) {
            markdown.append("- Diagnostic: `").append(inline(diagnostic)).append("`\n\n");
            return markdown.toString();
        }
        markdown.append("- Kernel count: `").append(entries.size()).append("`\n");
        markdown.append("- Metrics available: `").append(count("recorded")).append("`\n");
        markdown.append("- Metrics unavailable: `").append(count("unavailable") + count("missing")).append("`\n");
        markdown.append("- Kernels with spills: `").append(spillingCount()).append("`\n");
        markdown.append("- Kernels with stack frames: `").append(stackUsingCount()).append("`\n");
        markdown.append("- Maximum register count: `").append(metric(maxRegisters())).append("`\n\n");
        if (entries.isEmpty()) {
            return markdown.toString();
        }
        markdown.append("| Kernel resource | Status | Provider | Tool | Registers | Spill stores | Spill loads | Stack frame |\n");
        markdown.append("| --- | --- | --- | --- | ---: | ---: | ---: | ---: |\n");
        for (Entry entry : entries) {
            markdown.append("| `").append(table(entry.kernelResource())).append("` | `")
                    .append(table(entry.status())).append("` | `")
                    .append(table(entry.provider())).append("` | `")
                    .append(table(entry.tool())).append("` | `")
                    .append(metric(entry.registerCount())).append("` | `")
                    .append(metric(entry.spillStoreBytes())).append("` | `")
                    .append(metric(entry.spillLoadBytes())).append("` | `")
                    .append(metric(entry.stackFrameBytes())).append("` |\n");
        }
        markdown.append('\n');
        return markdown.toString();
    }

    private static Map<String, Properties> loadCompilerFeedback(Path artifactRoot) throws IOException {
        LinkedHashMap<String, Properties> feedback = new LinkedHashMap<>();
        if (artifactRoot == null || !Files.isDirectory(artifactRoot)) {
            return feedback;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(artifactRoot)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(candidate -> ARTIFACT_FILE_NAME.equals(candidate.getFileName().toString()))
                    .toList()) {
                Properties properties = loadProperties(path);
                String resource = properties.getProperty("backendResource", "");
                if (!resource.isBlank()) {
                    feedback.put(resource, properties);
                }
            }
        }
        return feedback;
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

    private static int parseMetric(String value) {
        return parseInt(value, -1);
    }

    private static String metric(int value) {
        return value < 0 ? "unknown" : Integer.toString(value);
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
            String provider,
            String tool,
            int registerCount,
            int spillStoreBytes,
            int spillLoadBytes,
            int stackFrameBytes
    ) {

        private static final Base64.Encoder HISTORY_ENCODER = Base64.getUrlEncoder().withoutPadding();
        private static final Base64.Decoder HISTORY_DECODER = Base64.getUrlDecoder();

        Entry {
            kernelResource = normalize(kernelResource, "unknown");
            status = normalize(status, "unknown");
            provider = normalize(provider, "none");
            tool = normalize(tool, "none");
            registerCount = Math.max(-1, registerCount);
            spillStoreBytes = Math.max(-1, spillStoreBytes);
            spillLoadBytes = Math.max(-1, spillLoadBytes);
            stackFrameBytes = Math.max(-1, stackFrameBytes);
        }

        static Entry from(String kernelResource, Properties properties) {
            if (properties == null || properties.isEmpty()) {
                return new Entry(kernelResource, "missing", "none", "none", -1, -1, -1, -1);
            }
            int registers = parseMetric(properties.getProperty("selected.register.effective"));
            int spillStores = parseMetric(properties.getProperty("selected.spill.storeBytes"));
            int spillLoads = parseMetric(properties.getProperty("selected.spill.loadBytes"));
            int stackFrame = parseMetric(properties.getProperty("selected.stackFrameBytes"));
            boolean available = registers >= 0 || spillStores >= 0 || spillLoads >= 0 || stackFrame >= 0;
            return new Entry(
                    kernelResource,
                    available ? "recorded" : "unavailable",
                    properties.getProperty("selected.providerId", "none"),
                    properties.getProperty("diagnosticCompilation.binaryInspection.tool", "none"),
                    registers,
                    spillStores,
                    spillLoads,
                    stackFrame
            );
        }

        int knownSpillBytes() {
            if (spillStoreBytes < 0 && spillLoadBytes < 0) {
                return -1;
            }
            return Math.max(0, spillStoreBytes) + Math.max(0, spillLoadBytes);
        }

        String toHistoryToken() {
            return String.join(".",
                    encode(kernelResource),
                    encode(status),
                    encode(provider),
                    encode(tool),
                    encode(Integer.toString(registerCount)),
                    encode(Integer.toString(spillStoreBytes)),
                    encode(Integer.toString(spillLoadBytes)),
                    encode(Integer.toString(stackFrameBytes))
            );
        }

        static Optional<Entry> fromHistoryToken(String token) {
            String[] fields = token.split("\\.", -1);
            if (fields.length != 8) {
                return Optional.empty();
            }
            try {
                return Optional.of(new Entry(
                        decode(fields[0]),
                        decode(fields[1]),
                        decode(fields[2]),
                        decode(fields[3]),
                        Integer.parseInt(decode(fields[4])),
                        Integer.parseInt(decode(fields[5])),
                        Integer.parseInt(decode(fields[6])),
                        Integer.parseInt(decode(fields[7]))
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
    }
}
