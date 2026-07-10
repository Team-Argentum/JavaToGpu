package net.sixik.ga_utils.javatogpu.runtime.opencl;

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

record OpenClKernelLaunchAdvisorySummary(
        String status,
        List<Entry> entries,
        String diagnostic
) {

    OpenClKernelLaunchAdvisorySummary {
        status = status == null || status.isBlank() ? "unknown" : status;
        entries = entries == null ? List.of() : List.copyOf(entries);
        diagnostic = diagnostic == null ? "" : diagnostic;
    }

    static OpenClKernelLaunchAdvisorySummary notRecorded() {
        return new OpenClKernelLaunchAdvisorySummary("not-recorded", List.of(), "");
    }

    static OpenClKernelLaunchAdvisorySummary failed(Throwable failure) {
        return new OpenClKernelLaunchAdvisorySummary(
                "failed",
                List.of(),
                failure == null ? "unknown failure" : failure.toString()
        );
    }

    static OpenClKernelLaunchAdvisorySummary read(Path workloadGateFile) throws IOException {
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
        Map<String, Properties> advisories = loadAdvisories(artifactRoot);
        ArrayList<Entry> entries = new ArrayList<>();
        for (int index = 0; index < kernelCount; index++) {
            String resource = gate.getProperty("kernel." + index + ".sourceKernelResource", "unknown");
            entries.add(Entry.from(resource, advisories.get(resource)));
        }
        return new OpenClKernelLaunchAdvisorySummary("recorded", entries, "");
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

    int blockingCount() {
        int count = 0;
        for (Entry entry : entries) {
            if ("true".equals(entry.blocking())) {
                count++;
            }
        }
        return count;
    }

    String toHistorySummary() {
        if (!"recorded".equals(status)) {
            return status;
        }
        return "recorded (kernels=" + entries.size()
                + ", aligned=" + count("aligned")
                + ", nonPreferred=" + count("non-preferred-multiple")
                + ", driverSelected=" + count("driver-selected")
                + ", unavailable=" + count("unavailable")
                + ", missing=" + count("missing")
                + ", blocking=" + blockingCount()
                + ")";
    }

    String toMarkdown() {
        StringBuilder markdown = new StringBuilder();
        markdown.append("## Kernel Launch Advisories\n\n");
        markdown.append("- Status: `").append(inline(status)).append("`\n");
        if (!diagnostic.isBlank()) {
            markdown.append("- Diagnostic: `").append(inline(diagnostic)).append("`\n\n");
            return markdown.toString();
        }
        if (entries.isEmpty()) {
            markdown.append("- Kernel count: `0`\n\n");
            return markdown.toString();
        }

        markdown.append("- Kernel count: `").append(entries.size()).append("`\n");
        markdown.append("- Aligned: `").append(count("aligned")).append("`\n");
        markdown.append("- Non-preferred multiple: `").append(count("non-preferred-multiple")).append("`\n");
        markdown.append("- Driver-selected: `").append(count("driver-selected")).append("`\n");
        markdown.append("- Unavailable: `").append(count("unavailable")).append("`\n");
        markdown.append("- Missing: `").append(count("missing")).append("`\n");
        markdown.append("- Blocking: `").append(blockingCount()).append("`\n\n");
        markdown.append("| Kernel resource | Status | Local shape | Size | Kernel max | Preferred | Matched | Blocking |\n");
        markdown.append("| --- | --- | --- | ---: | ---: | ---: | --- | --- |\n");
        for (Entry entry : entries) {
            markdown.append("| `").append(table(entry.kernelResource())).append("` | `")
                    .append(table(entry.status())).append("` | `")
                    .append(table(entry.localShape())).append("` | `")
                    .append(table(entry.requestedSize())).append("` | `")
                    .append(table(entry.kernelMax())).append("` | `")
                    .append(table(entry.preferredMultiple())).append("` | `")
                    .append(table(entry.matched())).append("` | `")
                    .append(table(entry.blocking())).append("` |\n");
        }
        markdown.append('\n');
        return markdown.toString();
    }

    private static Map<String, Properties> loadAdvisories(Path artifactRoot) throws IOException {
        LinkedHashMap<String, Properties> advisories = new LinkedHashMap<>();
        if (artifactRoot == null || !Files.isDirectory(artifactRoot)) {
            return advisories;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(artifactRoot)) {
            for (Path path : paths
                    .filter(Files::isRegularFile)
                    .filter(candidate -> OpenClKernelLaunchAdvisory.ARTIFACT_FILE_NAME.equals(
                            candidate.getFileName().toString()
                    ))
                    .toList()) {
                Properties properties = loadProperties(path);
                String resource = properties.getProperty("kernelResource", "");
                if (!resource.isBlank()) {
                    advisories.put(resource, properties);
                }
            }
        }
        return advisories;
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
            String localShape,
            String requestedSize,
            String kernelMax,
            String preferredMultiple,
            String matched,
            String blocking
    ) {

        static Entry from(String kernelResource, Properties properties) {
            if (properties == null || properties.isEmpty()) {
                return new Entry(kernelResource, "missing", "-", "-", "-", "-", "-", "-");
            }
            return new Entry(
                    kernelResource,
                    properties.getProperty("status", "unknown"),
                    properties.getProperty("requestedLocalWorkGroupShape", "-"),
                    properties.getProperty("requestedLocalWorkGroupSize", "-"),
                    properties.getProperty("kernelMaxWorkGroupSize", "-"),
                    properties.getProperty("preferredWorkGroupSizeMultiple", "-"),
                    properties.getProperty("preferredMultipleMatched", "-"),
                    properties.getProperty("blocking", "-")
            );
        }
    }
}
