package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Small CLI entry point for generating a vendor-validation snapshot report.
 */
public final class OpenClValidationReporter {

    private static final String REPORT_FILE_PROPERTY = "javatogpu.opencl.validationReportFile";
    private static final String LONG_RUNNING_SUMMARY_FILE_PROPERTY = "javatogpu.opencl.longRunningSummaryFile";
    private static final String WORKLOAD_SUMMARY_FILE_PROPERTY = "javatogpu.opencl.workloadSummaryFile";
    private static final String BUCKET_STATUS_FILE_PROPERTY = "javatogpu.opencl.bucketStatusFile";
    private static final String HISTORY_PROPERTIES_FILE_PROPERTY = "javatogpu.opencl.validationHistoryFile";
    private static final String HISTORY_MARKDOWN_FILE_PROPERTY = "javatogpu.opencl.validationHistoryMarkdownFile";
    private static final int MAX_HISTORY_ENTRIES = 25;

    private OpenClValidationReporter() {
    }

    public static void main(String[] args) throws IOException {
        String markdown = buildReport();
        String outputPath = System.getProperty(REPORT_FILE_PROPERTY);
        if (outputPath != null && !outputPath.isBlank()) {
            Path path = Paths.get(outputPath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, markdown, StandardCharsets.UTF_8);
            System.out.println("Wrote OpenCL validation report to " + path.toAbsolutePath());
        }
        updateHistoryArtifacts(markdown);
        System.out.println(markdown);
    }

    private static String buildReport() {
        StringBuilder markdown = new StringBuilder();
        String requestedVendor = env("JTG_VALIDATION_VENDOR");
        String runnerName = env("RUNNER_NAME");
        String runnerOs = env("RUNNER_OS");
        String gitSha = env("GITHUB_SHA");
        String gitRef = env("GITHUB_REF_NAME");

        markdown.append("# OpenCL Vendor Validation Snapshot\n\n");
        if (!requestedVendor.isBlank()) {
            markdown.append("- Requested vendor lane: `").append(requestedVendor).append("`\n");
        }
        if (!runnerName.isBlank() || !runnerOs.isBlank()) {
            markdown.append("- Runner: `").append((runnerName + " " + runnerOs).trim()).append("`\n");
        }
        if (!gitRef.isBlank()) {
            markdown.append("- Git ref: `").append(gitRef).append("`\n");
        }
        if (!gitSha.isBlank()) {
            markdown.append("- Git SHA: `").append(gitSha).append("`\n");
        }
        if (markdown.charAt(markdown.length() - 1) != '\n') {
            markdown.append('\n');
        }
        markdown.append('\n');

        appendBucketStatusMatrix(markdown);
        appendWorkloadSummary(markdown);
        appendLongRunningSummary(markdown);

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            markdown.append(backend.validationReport().toMarkdown());
        } catch (Throwable failure) {
            markdown.append("## Report Failure\n\n");
            markdown.append("- Status: `failed to query OpenCL runtime`\n");
            markdown.append("- Error: `").append(sanitizeInline(failure.toString())).append("`\n");
        }

        return markdown.toString();
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    private static String sanitizeInline(String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }

    private static void appendLongRunningSummary(StringBuilder markdown) {
        String summaryPath = System.getProperty(LONG_RUNNING_SUMMARY_FILE_PROPERTY);
        if (summaryPath == null || summaryPath.isBlank()) {
            return;
        }

        markdown.append("## Long-Running Stability Bucket\n\n");
        try {
            java.util.Optional<OpenClLongRunningValidationSummary> summary =
                    OpenClLongRunningValidationSummaryIO.readIfExists(Paths.get(summaryPath));
            if (summary.isEmpty()) {
                markdown.append("- Status: `not recorded`\n");
                markdown.append("- Summary file: `").append(summaryPath).append("`\n\n");
                return;
            }

            OpenClLongRunningValidationSummary value = summary.get();
            markdown.append("- Status: `").append(sanitizeInline(value.status())).append("`\n");
            markdown.append("- Completed at (UTC): `").append(value.completedAtUtc()).append("`\n");
            markdown.append("- Iterations: `").append(value.iterations()).append("`\n");
            markdown.append("- Invocation count: `").append(value.statistics().invocationCount()).append("`\n");
            markdown.append("- Compile count: `").append(value.statistics().compileCount()).append("`\n");
            markdown.append("- Compile cache hits: `").append(value.statistics().compileCacheHitCount()).append("`\n");
            markdown.append("- Session creation count: `").append(value.statistics().sessionCreationCount()).append("`\n");
            markdown.append("- Device buffer creation count: `").append(value.statistics().deviceBufferCreationCount()).append("`\n");
            markdown.append("- Summary file: `").append(summaryPath).append("`\n\n");
        } catch (Throwable failure) {
            markdown.append("- Status: `failed to read`\n");
            markdown.append("- Summary file: `").append(summaryPath).append("`\n");
            markdown.append("- Error: `").append(sanitizeInline(failure.toString())).append("`\n\n");
        }
    }

    private static void appendWorkloadSummary(StringBuilder markdown) {
        String summaryPath = System.getProperty(WORKLOAD_SUMMARY_FILE_PROPERTY);
        if (summaryPath == null || summaryPath.isBlank()) {
            return;
        }

        markdown.append("## Serious Workloads\n\n");
        try {
            java.util.Optional<OpenClWorkloadValidationSummary> summary =
                    OpenClWorkloadValidationSummaryIO.readIfExists(Paths.get(summaryPath));
            if (summary.isEmpty()) {
                markdown.append("- Status: `not recorded`\n");
                markdown.append("- Summary file: `").append(summaryPath).append("`\n\n");
                return;
            }

            OpenClWorkloadValidationSummary value = summary.get();
            markdown.append("- Status: `").append(sanitizeInline(value.status())).append("`\n");
            markdown.append("- Completed at (UTC): `").append(value.completedAtUtc()).append("`\n");
            markdown.append("- Perlin workload: `").append(sanitizeInline(value.perlinStatus())).append("`\n");
            markdown.append("- Packed/blob workload: `").append(sanitizeInline(value.packedBlobStatus())).append("`\n");
            markdown.append("- Packed numeric workload: `").append(sanitizeInline(value.packedNumericStatus())).append("`\n");
            markdown.append("- Synthetic 3D packed-grid workload: `").append(sanitizeInline(value.packedGrid3dStatus())).append("`\n");
            markdown.append("- Image workload: `").append(sanitizeInline(value.imageStatus())).append("`\n");
            markdown.append("- Summary file: `").append(summaryPath).append("`\n\n");
        } catch (Throwable failure) {
            markdown.append("- Status: `failed to read`\n");
            markdown.append("- Summary file: `").append(summaryPath).append("`\n");
            markdown.append("- Error: `").append(sanitizeInline(failure.toString())).append("`\n\n");
        }
    }

    private static void appendBucketStatusMatrix(StringBuilder markdown) {
        String statusPath = System.getProperty(BUCKET_STATUS_FILE_PROPERTY);
        if (statusPath == null || statusPath.isBlank()) {
            return;
        }

        markdown.append("## Validation Buckets\n\n");
        try {
            java.util.Map<String, OpenClValidationBucketStatus> statuses =
                    OpenClValidationBucketStatusIO.readAll(Paths.get(statusPath));
            if (statuses.isEmpty()) {
                markdown.append("- Status: `not recorded`\n");
                markdown.append("- Registry file: `").append(statusPath).append("`\n\n");
                return;
            }

            for (OpenClValidationBucketStatus status : statuses.values()) {
                markdown.append("- `")
                        .append(status.taskName())
                        .append("`: `")
                        .append(sanitizeInline(status.status()))
                        .append("` at `")
                        .append(status.recordedAtUtc())
                        .append("`\n");
            }
            markdown.append("- Registry file: `").append(statusPath).append("`\n\n");
        } catch (Throwable failure) {
            markdown.append("- Status: `failed to read`\n");
            markdown.append("- Registry file: `").append(statusPath).append("`\n");
            markdown.append("- Error: `").append(sanitizeInline(failure.toString())).append("`\n\n");
        }
    }

    private static void updateHistoryArtifacts(String markdown) throws IOException {
        String historyPropertiesPath = System.getProperty(HISTORY_PROPERTIES_FILE_PROPERTY);
        String historyMarkdownPath = System.getProperty(HISTORY_MARKDOWN_FILE_PROPERTY);
        if ((historyPropertiesPath == null || historyPropertiesPath.isBlank())
                && (historyMarkdownPath == null || historyMarkdownPath.isBlank())) {
            return;
        }

        OpenClValidationHistoryEntry entry = buildHistoryEntry();
        java.util.List<OpenClValidationHistoryEntry> entries;
        Path propertiesPath = historyPropertiesPath == null || historyPropertiesPath.isBlank() ? null : Paths.get(historyPropertiesPath);
        if (propertiesPath != null) {
            entries = OpenClValidationHistoryIO.readAll(propertiesPath);
        } else {
            entries = new java.util.ArrayList<>();
        }

        entries.removeIf(existing -> sameRun(existing, entry));
        entries.add(0, entry);
        if (entries.size() > MAX_HISTORY_ENTRIES) {
            entries = new java.util.ArrayList<>(entries.subList(0, MAX_HISTORY_ENTRIES));
        }

        if (propertiesPath != null) {
            OpenClValidationHistoryIO.writeAll(propertiesPath, entries);
            System.out.println("Updated OpenCL validation history properties at " + propertiesPath.toAbsolutePath());
        }
        if (historyMarkdownPath != null && !historyMarkdownPath.isBlank()) {
            Path markdownPath = Paths.get(historyMarkdownPath);
            OpenClValidationHistoryIO.writeMarkdown(markdownPath, entries);
            System.out.println("Updated OpenCL validation history markdown at " + markdownPath.toAbsolutePath());
        }
    }

    private static OpenClValidationHistoryEntry buildHistoryEntry() {
        String requestedVendor = env("JTG_VALIDATION_VENDOR");
        String bucketSummary = summarizeBuckets();
        String longRunningStatus = summarizeLongRunningStatus();
        String workloadStatus = summarizeWorkloadStatus();

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            OpenClValidationReport report = backend.validationReport();
            return new OpenClValidationHistoryEntry(
                    report.generatedAtUtc(),
                    requestedVendor,
                    report.backendName(),
                    report.deviceLabel(),
                    report.vendor(),
                    report.driverVersion(),
                    report.deviceVersion(),
                    bucketSummary,
                    longRunningStatus,
                    workloadStatus
            );
        } catch (Throwable failure) {
            return new OpenClValidationHistoryEntry(
                    java.time.Instant.now(),
                    requestedVendor,
                    "report-failed",
                    sanitizeInline(failure.getClass().getSimpleName()),
                    "unknown",
                    "unknown",
                    sanitizeInline(failure.toString()),
                    bucketSummary,
                    longRunningStatus,
                    workloadStatus
            );
        }
    }

    private static String summarizeBuckets() {
        String statusPath = System.getProperty(BUCKET_STATUS_FILE_PROPERTY);
        if (statusPath == null || statusPath.isBlank()) {
            return "not recorded";
        }
        try {
            java.util.Map<String, OpenClValidationBucketStatus> statuses = OpenClValidationBucketStatusIO.readAll(Paths.get(statusPath));
            if (statuses.isEmpty()) {
                return "not recorded";
            }
            return statuses.values().stream()
                    .map(status -> status.taskName() + "=" + status.status())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("not recorded");
        } catch (Throwable failure) {
            return "failed to read";
        }
    }

    private static String summarizeLongRunningStatus() {
        String summaryPath = System.getProperty(LONG_RUNNING_SUMMARY_FILE_PROPERTY);
        if (summaryPath == null || summaryPath.isBlank()) {
            return "not recorded";
        }
        try {
            java.util.Optional<OpenClLongRunningValidationSummary> summary =
                    OpenClLongRunningValidationSummaryIO.readIfExists(Paths.get(summaryPath));
            return summary.map(OpenClLongRunningValidationSummary::status).orElse("not recorded");
        } catch (Throwable failure) {
            return "failed to read";
        }
    }

    private static String summarizeWorkloadStatus() {
        String summaryPath = System.getProperty(WORKLOAD_SUMMARY_FILE_PROPERTY);
        if (summaryPath == null || summaryPath.isBlank()) {
            return "not recorded";
        }
        try {
            java.util.Optional<OpenClWorkloadValidationSummary> summary =
                    OpenClWorkloadValidationSummaryIO.readIfExists(Paths.get(summaryPath));
            return summary.map(value -> value.status()
                            + " (perlin=" + value.perlinStatus()
                            + ", packedBlob=" + value.packedBlobStatus()
                            + ", packedNumeric=" + value.packedNumericStatus()
                            + ", packedGrid3d=" + value.packedGrid3dStatus()
                            + ", image=" + value.imageStatus()
                            + ")")
                    .orElse("not recorded");
        } catch (Throwable failure) {
            return "failed to read";
        }
    }

    private static boolean sameRun(OpenClValidationHistoryEntry left, OpenClValidationHistoryEntry right) {
        return left.generatedAtUtc().equals(right.generatedAtUtc())
                && left.deviceLabel().equals(right.deviceLabel())
                && left.backendName().equals(right.backendName());
    }
}
