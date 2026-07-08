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
    private static final String BACKEND_SOURCE_PROMOTION_GATE_FILE_PROPERTY = "javatogpu.opencl.backendSourcePromotionGateFile";
    private static final String BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE_FILE_PROPERTY = "javatogpu.opencl.backendSourcePromotionWorkloadGateFile";
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
        ensureBackendSourcePromotionGateArtifact();
        ensureBackendSourcePromotionWorkloadGateArtifact();

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
        appendBackendSourcePromotionContractSummary(markdown);
        appendBackendSourcePromotionWorkloadSummary(markdown);

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

    private static void appendBackendSourcePromotionContractSummary(StringBuilder markdown) {
        String gatePath = System.getProperty(BACKEND_SOURCE_PROMOTION_GATE_FILE_PROPERTY);
        if (gatePath == null || gatePath.isBlank()) {
            return;
        }

        markdown.append("## Backend Source Promotion Contract Fixture\n\n");
        try {
            java.util.Properties properties = loadPropertiesIfExists(Paths.get(gatePath));
            if (properties.isEmpty()) {
                markdown.append("- Status: `not recorded`\n");
                markdown.append("- Gate file: `").append(gatePath).append("`\n\n");
                return;
            }
            markdown.append("- Status: `").append(sanitizeInline(properties.getProperty("status", "unknown"))).append("`\n");
            markdown.append("- Review ready: `").append(sanitizeInline(properties.getProperty("reviewReady", "unknown"))).append("`\n");
            markdown.append("- Source parity matched: `").append(sanitizeInline(properties.getProperty("sourceParityMatched", "unknown"))).append("`\n");
            markdown.append("- Runtime equivalence passed: `").append(sanitizeInline(properties.getProperty("runtimeEquivalencePassed", "unknown"))).append("`\n");
            markdown.append("- Scope: `synthetic contract fixture only; not production workload promotion`\n");
            markdown.append("- Gate file: `").append(gatePath).append("`\n\n");
        } catch (Throwable failure) {
            markdown.append("- Status: `failed to read`\n");
            markdown.append("- Gate file: `").append(gatePath).append("`\n");
            markdown.append("- Error: `").append(sanitizeInline(failure.toString())).append("`\n\n");
        }
    }

    private static void appendBackendSourcePromotionWorkloadSummary(StringBuilder markdown) {
        markdown.append("## Backend Source Promotion Workload Gate\n\n");
        String gatePath = System.getProperty(BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE_FILE_PROPERTY);
        if (gatePath == null || gatePath.isBlank()) {
            markdown.append("- Status: `not-promoted`\n");
            markdown.append("- Reason: `real workload source-promotion evidence is not wired yet`\n");
            markdown.append("- Production source switching: `disabled`\n\n");
            return;
        }

        try {
            java.util.Properties properties = loadPropertiesIfExists(Paths.get(gatePath));
            if (properties.isEmpty()) {
                markdown.append("- Status: `not recorded`\n");
                markdown.append("- Gate file: `").append(gatePath).append("`\n\n");
                return;
            }
            String rawStatus = properties.getProperty("status", "unknown");
            boolean unexpectedReviewReady = "review-ready".equals(rawStatus);
            String effectiveStatus = unexpectedReviewReady ? "blocked" : rawStatus;
            String effectiveReviewReady = unexpectedReviewReady ? "false" : properties.getProperty("reviewReady", "unknown");
            markdown.append("- Status: `").append(sanitizeInline(effectiveStatus)).append("`\n");
            markdown.append("- Review ready: `").append(sanitizeInline(effectiveReviewReady)).append("`\n");
            if (unexpectedReviewReady) {
                markdown.append("- Gate status: `review-ready`\n");
            }
            markdown.append("- Source parity matched: `").append(sanitizeInline(properties.getProperty("sourceParityMatched", "unknown"))).append("`\n");
            markdown.append("- Runtime equivalence passed: `").append(sanitizeInline(properties.getProperty("runtimeEquivalencePassed", "unknown"))).append("`\n");
            markdown.append("- Real workload evidence: `").append(sanitizeInline(properties.getProperty("realWorkloadEvidence", "not-wired"))).append("`\n");
            appendBackendSourceSwitchingSummary(markdown, properties);
            appendBackendSourcePromotionWorkloadFamilySummary(markdown, properties, "");
            int kernelCount = parsePositiveInt(properties.getProperty("kernel.count", "0"));
            if (kernelCount > 0) {
                markdown.append("- Kernel evidence count: `").append(kernelCount).append("`\n");
                for (int index = 0; index < kernelCount; index++) {
                    appendBackendSourcePromotionWorkloadKernelSummary(markdown, properties, index);
                }
            } else {
                String sourceKernelResource = properties.getProperty("sourceKernelResource", "");
                if (!sourceKernelResource.isBlank()) {
                    markdown.append("- Source kernel resource: `").append(sanitizeInline(sourceKernelResource)).append("`\n");
                }
            }
            markdown.append("- Reason: `").append(sanitizeInline(properties.getProperty(
                    "reason",
                    unexpectedReviewReady
                            ? "unexpected workload review-ready gate cannot promote production source while source switching is disabled"
                            : "real workload source-promotion evidence is not wired yet"
            ))).append("`\n");
            markdown.append("- Production source switching: `disabled`\n");
            markdown.append("- Scope: `real workload promotion gate; fail-closed until workload evidence is wired`\n");
            markdown.append("- Gate file: `").append(gatePath).append("`\n\n");
        } catch (Throwable failure) {
            markdown.append("- Status: `failed to read`\n");
            markdown.append("- Gate file: `").append(gatePath).append("`\n");
            markdown.append("- Error: `").append(sanitizeInline(failure.toString())).append("`\n\n");
        }
    }

    private static void appendBackendSourcePromotionWorkloadKernelSummary(
            StringBuilder markdown,
            java.util.Properties properties,
            int index
    ) {
        String prefix = "kernel." + index + ".";
        markdown.append("- Kernel `")
                .append(index)
                .append("`: `")
                .append(sanitizeInline(properties.getProperty(prefix + "sourceKernelResource", "unknown")))
                .append("`, status=`")
                .append(sanitizeInline(properties.getProperty(prefix + "status", "unknown")))
                .append("`, parity=`")
                .append(sanitizeInline(properties.getProperty(prefix + "sourceParityMatched", "unknown")))
                .append("`, runtimeEquivalence=`")
                .append(sanitizeInline(properties.getProperty(prefix + "runtimeEquivalencePassed", "unknown")))
                .append("`, sourceSwitching=`")
                .append(sanitizeInline(properties.getProperty(prefix + "sourceSwitching.decision", "not-recorded")))
                .append("`\n");
        String sourceSwitchingDiagnostic = properties.getProperty(prefix + "sourceSwitching.diagnostic.0", "");
        if (!sourceSwitchingDiagnostic.isBlank()) {
            markdown.append("- Kernel `")
                    .append(index)
                    .append("` source switching: status=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "sourceSwitching.status", "not-recorded")))
                    .append("`, profile=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "sourceSwitching.optimizationProfile", "unknown")))
                    .append("`, first=`")
                    .append(sanitizeInline(sourceSwitchingDiagnostic))
                    .append("`\n");
        }
        int diagnosticCount = parsePositiveInt(properties.getProperty(prefix + "diagnostic.count", "0"));
        if (diagnosticCount > 0) {
            markdown.append("- Kernel `")
                    .append(index)
                    .append("` diagnostics: `")
                    .append(diagnosticCount)
                    .append("`; first=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "diagnostic.0", "unknown")))
                    .append("`\n");
        }
        int reconstructionBlockerCount = parsePositiveInt(properties.getProperty(prefix + "reconstruction.blocker.count", "0"));
        if (reconstructionBlockerCount > 0) {
            markdown.append("- Kernel `")
                    .append(index)
                    .append("` reconstruction blockers: `")
                    .append(reconstructionBlockerCount)
                    .append("`; first=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "reconstruction.blocker.0", "unknown")))
                    .append("`\n");
        }
        appendBackendSourcePromotionWorkloadFamilySummary(markdown, properties, prefix);
    }

    private static void appendBackendSourceSwitchingSummary(
            StringBuilder markdown,
            java.util.Properties properties
    ) {
        String summary = summarizeSourceSwitchingDecisions(properties);
        if (summary.isBlank()) {
            return;
        }
        markdown.append("- Source switching decisions: `").append(sanitizeInline(summary)).append("`\n");
    }

    private static void appendBackendSourcePromotionWorkloadFamilySummary(
            StringBuilder markdown,
            java.util.Properties properties,
            String prefix
    ) {
        int familyCount = parsePositiveInt(properties.getProperty(prefix + "blockerFamily.count", "0"));
        if (familyCount == 0) {
            return;
        }
        markdown.append("- ")
                .append(prefix.isBlank() ? "Blocker families" : "Kernel blocker families")
                .append(": `")
                .append(formatBlockerFamilies(properties, prefix, familyCount))
                .append("`\n");
    }

    private static void ensureBackendSourcePromotionGateArtifact() {
        String gatePath = System.getProperty(BACKEND_SOURCE_PROMOTION_GATE_FILE_PROPERTY);
        if (gatePath == null || gatePath.isBlank()) {
            return;
        }
        Path path = Paths.get(gatePath);
        try {
            if (Files.exists(path) && !loadPropertiesIfExists(path).isEmpty()) {
                return;
            }
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String properties = net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourcePromotionGate.evaluate(
                    null,
                    net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeEquivalenceEvidence.notRun(
                            null,
                            "operational validation has not produced backend source runtime-equivalence evidence yet"
                    ),
                    net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeFallbackEvidence.none()
            ).toPropertiesText();
            Files.writeString(path, properties, StandardCharsets.UTF_8);
        } catch (Throwable failure) {
            // The validation report must still be printable even if this optional diagnostic artifact cannot be written.
        }
    }

    private static void ensureBackendSourcePromotionWorkloadGateArtifact() {
        String gatePath = System.getProperty(BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE_FILE_PROPERTY);
        if (gatePath == null || gatePath.isBlank()) {
            return;
        }
        Path path = Paths.get(gatePath);
        try {
            if (Files.exists(path) && !loadPropertiesIfExists(path).isEmpty()) {
                return;
            }
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String properties = net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourcePromotionGate.evaluate(
                    null,
                    net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeEquivalenceEvidence.notRun(
                            null,
                            "real workload source-promotion runtime-equivalence evidence is not wired yet"
                    ),
                    net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeFallbackEvidence.none()
            ).toPropertiesText()
                    + "scope=real-workload\n"
                    + "productionSourceSwitching=false\n"
                    + "realWorkloadEvidence=not-wired\n"
                    + "reason=real workload source-promotion runtime-equivalence evidence is not wired yet\n";
            Files.writeString(path, properties, StandardCharsets.UTF_8);
        } catch (Throwable failure) {
            // The validation report must still be printable even if this optional diagnostic artifact cannot be written.
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
        String backendSourcePromotionContractStatus = summarizeBackendSourcePromotionContractStatus();
        String backendSourcePromotionWorkloadStatus = summarizeBackendSourcePromotionWorkloadStatus();

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
                    workloadStatus,
                    backendSourcePromotionContractStatus,
                    backendSourcePromotionWorkloadStatus
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
                    workloadStatus,
                    backendSourcePromotionContractStatus,
                    backendSourcePromotionWorkloadStatus
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

    private static String summarizeBackendSourcePromotionContractStatus() {
        String gatePath = System.getProperty(BACKEND_SOURCE_PROMOTION_GATE_FILE_PROPERTY);
        if (gatePath == null || gatePath.isBlank()) {
            return "not recorded";
        }
        try {
            java.util.Properties properties = loadPropertiesIfExists(Paths.get(gatePath));
            if (properties.isEmpty()) {
                return "not recorded";
            }
            return properties.getProperty("status", "unknown")
                    + " (reviewReady=" + properties.getProperty("reviewReady", "unknown")
                    + ", sourceParityMatched=" + properties.getProperty("sourceParityMatched", "unknown")
                    + ", runtimeEquivalencePassed=" + properties.getProperty("runtimeEquivalencePassed", "unknown")
                    + ")";
        } catch (Throwable failure) {
            return "failed to read";
        }
    }

    private static String summarizeBackendSourcePromotionWorkloadStatus() {
        String gatePath = System.getProperty(BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE_FILE_PROPERTY);
        if (gatePath == null || gatePath.isBlank()) {
            return "not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired)";
        }
        try {
            java.util.Properties properties = loadPropertiesIfExists(Paths.get(gatePath));
            if (properties.isEmpty()) {
                return "not recorded";
            }
            String status = properties.getProperty("status", "unknown");
            if ("review-ready".equals(status)) {
                return "blocked (productionSourceSwitching=disabled, unexpectedWorkloadReviewReady=true)";
            }
            return "not-promoted (gateStatus=" + status
                    + ", reviewReady=" + properties.getProperty("reviewReady", "unknown")
                    + ", sourceParityMatched=" + properties.getProperty("sourceParityMatched", "unknown")
                    + ", runtimeEquivalencePassed=" + properties.getProperty("runtimeEquivalencePassed", "unknown")
                    + ", realWorkloadEvidence=" + properties.getProperty("realWorkloadEvidence", "not-wired")
                    + summarizeSourceSwitchingEvidence(properties)
                    + summarizeKernelEvidence(properties)
                    + summarizeSourceKernelResource(properties)
                    + ", productionSourceSwitching=disabled)";
        } catch (Throwable failure) {
            return "failed to read";
        }
    }

    private static String summarizeSourceKernelResource(java.util.Properties properties) {
        String sourceKernelResource = properties.getProperty("sourceKernelResource", "");
        return sourceKernelResource.isBlank() ? "" : ", sourceKernelResource=" + sourceKernelResource;
    }

    private static String summarizeSourceSwitchingEvidence(java.util.Properties properties) {
        String summary = summarizeSourceSwitchingDecisions(properties);
        return summary.isBlank() ? "" : ", sourceSwitching=" + summary;
    }

    private static String summarizeSourceSwitchingDecisions(java.util.Properties properties) {
        int kernelCount = parsePositiveInt(properties.getProperty("kernel.count", "0"));
        if (kernelCount == 0) {
            return "";
        }
        java.util.LinkedHashMap<String, Integer> decisionCounts = new java.util.LinkedHashMap<>();
        for (int index = 0; index < kernelCount; index++) {
            String decision = properties.getProperty("kernel." + index + ".sourceSwitching.decision", "");
            if (decision.isBlank() || "not-recorded".equals(decision)) {
                continue;
            }
            decisionCounts.merge(decision, 1, Integer::sum);
        }
        if (decisionCounts.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> entry : decisionCounts.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static String summarizeKernelEvidence(java.util.Properties properties) {
        int kernelCount = parsePositiveInt(properties.getProperty("kernel.count", "0"));
        if (kernelCount == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(", kernelCount=").append(kernelCount);
        for (int index = 0; index < kernelCount; index++) {
            builder.append(", kernel.")
                    .append(index)
                    .append("=")
                    .append(properties.getProperty("kernel." + index + ".sourceKernelResource", "unknown"))
                    .append("[diagnostics=")
                    .append(properties.getProperty("kernel." + index + ".diagnostic.count", "0"))
                    .append(", sourceSwitching=")
                    .append(properties.getProperty("kernel." + index + ".sourceSwitching.decision", "not-recorded"))
                    .append(", families=")
                    .append(formatBlockerFamilies(
                            properties,
                            "kernel." + index + ".",
                            parsePositiveInt(properties.getProperty("kernel." + index + ".blockerFamily.count", "0"))
                    ))
                    .append("]");
        }
        return builder.toString();
    }

    private static String formatBlockerFamilies(java.util.Properties properties, String prefix, int familyCount) {
        if (familyCount == 0) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < familyCount; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(properties.getProperty(prefix + "blockerFamily." + index + ".name", "unknown"))
                    .append('=')
                    .append(properties.getProperty(prefix + "blockerFamily." + index + ".count", "0"));
        }
        return builder.toString();
    }

    private static int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static java.util.Properties loadPropertiesIfExists(Path path) throws IOException {
        java.util.Properties properties = new java.util.Properties();
        if (path == null || !Files.exists(path)) {
            return properties;
        }
        try (java.io.InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        }
        return properties;
    }

    private static boolean sameRun(OpenClValidationHistoryEntry left, OpenClValidationHistoryEntry right) {
        return left.generatedAtUtc().equals(right.generatedAtUtc())
                && left.deviceLabel().equals(right.deviceLabel())
                && left.backendName().equals(right.backendName());
    }
}
