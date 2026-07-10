package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourcePromotionWorkloadSummary;
import net.sixik.ga_utils.javatogpu.runtime.GpuPromotionArtifactRegistry;
import net.sixik.ga_utils.javatogpu.runtime.GpuProductionPromotionExplainabilityValidation;
import net.sixik.ga_utils.javatogpu.runtime.GpuProductionPromotionExplainabilityFormatter;
import net.sixik.ga_utils.javatogpu.runtime.GpuProductionPromotionExplainabilitySummary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Small CLI entry point for generating a vendor-validation snapshot report.
 */
public final class OpenClValidationReporter {

    private static final String REPORT_FILE_PROPERTY = "javatogpu.opencl.validationReportFile";
    private static final String LONG_RUNNING_SUMMARY_FILE_PROPERTY = "javatogpu.opencl.longRunningSummaryFile";
    private static final String WORKLOAD_SUMMARY_FILE_PROPERTY = "javatogpu.opencl.workloadSummaryFile";
    private static final String BUCKET_STATUS_FILE_PROPERTY = "javatogpu.opencl.bucketStatusFile";
    private static final String IRGPU_SOURCE_REVIEW_FILE_PROPERTY = "javatogpu.opencl.irGpuSourceReviewFile";
    private static final String PRODUCTION_SOURCE_SWITCHING_VALIDATION_FILE_PROPERTY =
            "javatogpu.opencl.productionSourceSwitchingValidationFile";
    private static final String BACKEND_SOURCE_PROMOTION_GATE_FILE_PROPERTY = "javatogpu.opencl.backendSourcePromotionGateFile";
    private static final String BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE_FILE_PROPERTY = "javatogpu.opencl.backendSourcePromotionWorkloadGateFile";
    private static final String BACKEND_SOURCE_PROMOTION_CANDIDATE_GATE_FILE_PROPERTY =
            "javatogpu.opencl.backendSourcePromotionCandidateGateFile";
    private static final String I3_READINESS_WORKLOAD_SUMMARY_FILE_PROPERTY = "javatogpu.opencl.i3ReadinessWorkloadSummaryFile";
    private static final String PRODUCTION_PROMOTION_EXPLAINABILITY_FILE_PROPERTY = "javatogpu.opencl.productionPromotionExplainabilityFile";
    private static final String BACKEND_PROMOTION_ARTIFACT_SUPPORT_FILE_PROPERTY = "javatogpu.opencl.backendPromotionArtifactSupportFile";
    private static final String KERNEL_LAUNCH_ADVISORY_SUMMARY_FILE_PROPERTY =
            "javatogpu.opencl.kernelLaunchAdvisorySummaryFile";
    private static final String KERNEL_LAUNCH_ADVISORY_DRIFT_FILE_PROPERTY =
            "javatogpu.opencl.kernelLaunchAdvisoryDriftFile";
    private static final String COMPILER_RESOURCE_DRIFT_FILE_PROPERTY =
            "javatogpu.opencl.compilerResourceDriftFile";
    private static final String HISTORY_PROPERTIES_FILE_PROPERTY = "javatogpu.opencl.validationHistoryFile";
    private static final String HISTORY_MARKDOWN_FILE_PROPERTY = "javatogpu.opencl.validationHistoryMarkdownFile";
    private static final String HISTORY_BASELINE_FILE_PROPERTY = "javatogpu.opencl.validationHistoryBaselineFile";
    private static final int MAX_HISTORY_ENTRIES = 25;

    private OpenClValidationReporter() {
    }

    public static void main(String[] args) throws IOException {
        ensureBackendSourcePromotionGateArtifact();
        ensureBackendSourcePromotionWorkloadGateArtifact();
        ensureI3ReadinessWorkloadSummaryArtifact();
        ensureBackendPromotionArtifactSupportArtifact();
        ensureProductionPromotionExplainabilityArtifact();
        seedValidationHistoryFromBaseline();
        OpenClKernelLaunchAdvisorySummary launchAdvisorySummary = loadKernelLaunchAdvisorySummary();
        OpenClCompilerResourceSummary compilerResourceSummary = loadCompilerResourceSummary();
        OpenClValidationHistoryEntry currentHistoryEntry = buildHistoryEntry(
                launchAdvisorySummary,
                compilerResourceSummary
        );
        OpenClKernelLaunchAdvisoryDrift launchAdvisoryDrift = loadKernelLaunchAdvisoryDrift(currentHistoryEntry);
        OpenClCompilerResourceDrift compilerResourceDrift = loadCompilerResourceDrift(currentHistoryEntry);
        String markdown = buildReport(
                launchAdvisorySummary,
                launchAdvisoryDrift,
                compilerResourceSummary,
                compilerResourceDrift
        );
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
        writeKernelLaunchAdvisorySummary(launchAdvisorySummary, launchAdvisoryDrift);
        writeKernelLaunchAdvisoryDrift(launchAdvisoryDrift);
        writeCompilerResourceDrift(compilerResourceDrift);
        updateHistoryArtifacts(markdown, currentHistoryEntry);
        System.out.println(markdown);
    }

    private static String buildReport(
            OpenClKernelLaunchAdvisorySummary launchAdvisorySummary,
            OpenClKernelLaunchAdvisoryDrift launchAdvisoryDrift,
            OpenClCompilerResourceSummary compilerResourceSummary,
            OpenClCompilerResourceDrift compilerResourceDrift
    ) {
        StringBuilder markdown = new StringBuilder();
        String requestedVendor = env("JTG_VALIDATION_VENDOR");
        String runnerName = env("RUNNER_NAME");
        String runnerOs = env("RUNNER_OS");
        String gitSha = env("GITHUB_SHA");
        String gitRef = env("GITHUB_REF_NAME");
        ensureBackendSourcePromotionGateArtifact();
        ensureBackendSourcePromotionWorkloadGateArtifact();
        ensureI3ReadinessWorkloadSummaryArtifact();
        ensureBackendPromotionArtifactSupportArtifact();
        ensureProductionPromotionExplainabilityArtifact();

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
        markdown.append(launchAdvisorySummary.toMarkdown());
        markdown.append(launchAdvisoryDrift.toMarkdown());
        markdown.append(compilerResourceSummary.toMarkdown());
        markdown.append(compilerResourceDrift.toMarkdown());
        appendIrGpuSourceReviewSummary(markdown);
        appendProductionSourceSwitchingValidationSummary(markdown);
        appendLongRunningSummary(markdown);
        appendBackendSourcePromotionContractSummary(markdown);
        appendBackendSourcePromotionWorkloadSummary(markdown);
        appendBackendSourcePromotionCandidateSummary(markdown);
        appendProductionPromotionExplainabilitySummary(markdown);

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            markdown.append(backend.validationReport().toMarkdown());
        } catch (Throwable failure) {
            markdown.append("## Report Failure\n\n");
            markdown.append("- Status: `failed to query OpenCL runtime`\n");
            markdown.append("- Error: `").append(sanitizeInline(failure.toString())).append("`\n");
        }

        return markdown.toString();
    }

    private static OpenClKernelLaunchAdvisorySummary loadKernelLaunchAdvisorySummary() {
        String gatePath = System.getProperty(BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE_FILE_PROPERTY);
        if (gatePath == null || gatePath.isBlank()) {
            return OpenClKernelLaunchAdvisorySummary.notRecorded();
        }
        try {
            return OpenClKernelLaunchAdvisorySummary.read(Paths.get(gatePath));
        } catch (Throwable failure) {
            return OpenClKernelLaunchAdvisorySummary.failed(failure);
        }
    }

    private static OpenClCompilerResourceSummary loadCompilerResourceSummary() {
        String gatePath = System.getProperty(BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE_FILE_PROPERTY);
        if (gatePath == null || gatePath.isBlank()) {
            return OpenClCompilerResourceSummary.notRecorded();
        }
        try {
            return OpenClCompilerResourceSummary.read(Paths.get(gatePath));
        } catch (Throwable failure) {
            return OpenClCompilerResourceSummary.failed(failure);
        }
    }

    private static OpenClKernelLaunchAdvisoryDrift loadKernelLaunchAdvisoryDrift(
            OpenClValidationHistoryEntry currentEntry
    ) {
        String baselinePath = System.getProperty(HISTORY_BASELINE_FILE_PROPERTY);
        String historyPath = System.getProperty(HISTORY_PROPERTIES_FILE_PROPERTY);
        Optional<OpenClKernelLaunchAdvisoryDrift.Counts> currentCounts =
                OpenClKernelLaunchAdvisoryDrift.Counts.parse(currentEntry.kernelLaunchAdvisoryStatus());
        if (baselinePath != null && !baselinePath.isBlank()) {
            Path path = Paths.get(baselinePath);
            if (!Files.isRegularFile(path)) {
                return currentCounts
                        .map(OpenClKernelLaunchAdvisoryDrift::noBaseline)
                        .orElseGet(() -> OpenClKernelLaunchAdvisoryDrift.compare(currentEntry, java.util.List.of()));
            }
            try {
                return OpenClKernelLaunchAdvisoryDrift.compare(
                        currentEntry,
                        OpenClValidationHistoryIO.readAll(path)
                );
            } catch (Throwable failure) {
                return currentCounts
                        .map(OpenClKernelLaunchAdvisoryDrift::noBaseline)
                        .orElseGet(() -> OpenClKernelLaunchAdvisoryDrift.compare(currentEntry, java.util.List.of()));
            }
        }
        if (historyPath == null || historyPath.isBlank()) {
            return currentCounts
                    .map(OpenClKernelLaunchAdvisoryDrift::noBaseline)
                    .orElseGet(() -> OpenClKernelLaunchAdvisoryDrift.compare(currentEntry, java.util.List.of()));
        }
        try {
            return OpenClKernelLaunchAdvisoryDrift.compare(
                    currentEntry,
                    OpenClValidationHistoryIO.readAll(Paths.get(historyPath))
            );
        } catch (Throwable failure) {
            return currentCounts
                    .map(OpenClKernelLaunchAdvisoryDrift::noBaseline)
                    .orElseGet(() -> OpenClKernelLaunchAdvisoryDrift.compare(currentEntry, java.util.List.of()));
        }
    }

    private static OpenClCompilerResourceDrift loadCompilerResourceDrift(
            OpenClValidationHistoryEntry currentEntry
    ) {
        String baselinePath = System.getProperty(HISTORY_BASELINE_FILE_PROPERTY);
        String historyPath = System.getProperty(HISTORY_PROPERTIES_FILE_PROPERTY);
        Optional<OpenClCompilerResourceDrift.Counts> currentCounts =
                OpenClCompilerResourceDrift.Counts.parse(currentEntry.compilerResourceStatus());
        if (baselinePath != null && !baselinePath.isBlank()) {
            Path path = Paths.get(baselinePath);
            if (!Files.isRegularFile(path)) {
                return currentCounts
                        .map(OpenClCompilerResourceDrift::noBaseline)
                        .orElseGet(() -> OpenClCompilerResourceDrift.compare(currentEntry, java.util.List.of()));
            }
            try {
                return OpenClCompilerResourceDrift.compare(
                        currentEntry,
                        OpenClValidationHistoryIO.readAll(path)
                );
            } catch (Throwable failure) {
                return currentCounts
                        .map(OpenClCompilerResourceDrift::noBaseline)
                        .orElseGet(() -> OpenClCompilerResourceDrift.compare(currentEntry, java.util.List.of()));
            }
        }
        if (historyPath == null || historyPath.isBlank()) {
            return currentCounts
                    .map(OpenClCompilerResourceDrift::noBaseline)
                    .orElseGet(() -> OpenClCompilerResourceDrift.compare(currentEntry, java.util.List.of()));
        }
        try {
            return OpenClCompilerResourceDrift.compare(
                    currentEntry,
                    OpenClValidationHistoryIO.readAll(Paths.get(historyPath))
            );
        } catch (Throwable failure) {
            return currentCounts
                    .map(OpenClCompilerResourceDrift::noBaseline)
                    .orElseGet(() -> OpenClCompilerResourceDrift.compare(currentEntry, java.util.List.of()));
        }
    }

    private static void seedValidationHistoryFromBaseline() {
        String baselinePath = System.getProperty(HISTORY_BASELINE_FILE_PROPERTY);
        String historyPath = System.getProperty(HISTORY_PROPERTIES_FILE_PROPERTY);
        if (baselinePath == null || baselinePath.isBlank() || historyPath == null || historyPath.isBlank()) {
            return;
        }
        Path baseline = Paths.get(baselinePath);
        Path history = Paths.get(historyPath);
        if (!Files.isRegularFile(baseline) || Files.exists(history)) {
            return;
        }
        try {
            Path parent = history.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(baseline, history);
        } catch (Throwable failure) {
            // Baseline seeding is advisory here; the drift validator handles missing or invalid evidence.
        }
    }

    private static void writeKernelLaunchAdvisorySummary(
            OpenClKernelLaunchAdvisorySummary summary,
            OpenClKernelLaunchAdvisoryDrift drift
    ) {
        String outputPath = System.getProperty(KERNEL_LAUNCH_ADVISORY_SUMMARY_FILE_PROPERTY);
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        try {
            Path path = Paths.get(outputPath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, summary.toMarkdown() + drift.toMarkdown(), StandardCharsets.UTF_8);
        } catch (Throwable failure) {
            // The primary validation report must remain available if the optional CI fragment cannot be written.
        }
    }

    private static void writeKernelLaunchAdvisoryDrift(OpenClKernelLaunchAdvisoryDrift drift) {
        String outputPath = System.getProperty(KERNEL_LAUNCH_ADVISORY_DRIFT_FILE_PROPERTY);
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        try {
            Path path = Paths.get(outputPath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, drift.toPropertiesText(), StandardCharsets.UTF_8);
        } catch (Throwable failure) {
            // The primary validation report must remain available; the dedicated validator fails on a missing artifact.
        }
    }

    private static void writeCompilerResourceDrift(OpenClCompilerResourceDrift drift) {
        String outputPath = System.getProperty(COMPILER_RESOURCE_DRIFT_FILE_PROPERTY);
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        try {
            Path path = Paths.get(outputPath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, drift.toPropertiesText(), StandardCharsets.UTF_8);
        } catch (Throwable failure) {
            // The primary validation report must remain available; the dedicated validator fails on a missing artifact.
        }
    }

    private static void ensureBackendPromotionArtifactSupportArtifact() {
        String supportPath = System.getProperty(BACKEND_PROMOTION_ARTIFACT_SUPPORT_FILE_PROPERTY);
        if (supportPath == null || supportPath.isBlank()) {
            return;
        }
        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            OpenClValidationReport report = backend.validationReport();
            Path path = Paths.get(supportPath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, report.promotionArtifactSupport().toPropertiesText(), StandardCharsets.UTF_8);
        } catch (Throwable failure) {
            // Keep the validation report printable even if this optional support artifact cannot be written.
        }
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

    private static void appendIrGpuSourceReviewSummary(StringBuilder markdown) {
        String reviewPath = System.getProperty(IRGPU_SOURCE_REVIEW_FILE_PROPERTY);
        if (reviewPath == null || reviewPath.isBlank()) {
            return;
        }

        markdown.append("## IrGpu Source Review\n\n");
        try {
            java.util.Properties properties = loadPropertiesIfExists(Paths.get(reviewPath));
            if (properties.isEmpty()) {
                markdown.append("- Status: `not recorded`\n");
                markdown.append("- Review file: `").append(reviewPath).append("`\n\n");
                return;
            }
            markdown.append("- Status: `").append(sanitizeInline(properties.getProperty("status", "unknown"))).append("`\n");
            markdown.append("- Review ready: `").append(sanitizeInline(properties.getProperty("reviewReady", "unknown"))).append("`\n");
            markdown.append("- Source selection: `").append(sanitizeInline(properties.getProperty("sourceSelection", "unknown"))).append("`\n");
            markdown.append("- Optimization profile: `").append(sanitizeInline(properties.getProperty("optimizationProfile", "unknown"))).append("`\n");
            markdown.append("- Production source switching: `").append(sanitizeInline(properties.getProperty("productionSourceSwitching", "unknown"))).append("`\n");
            markdown.append("- Scope: `").append(sanitizeInline(properties.getProperty("scope", "opt-in-irgpu-source-review"))).append("`\n");
            int kernelCount = parsePositiveInt(properties.getProperty("kernel.count", "0"));
            markdown.append("- Kernel count: `").append(kernelCount).append("`\n");
            for (int index = 0; index < kernelCount; index++) {
                appendIrGpuSourceReviewKernelSummary(markdown, properties, index);
            }
            String diagnostic = properties.getProperty("diagnostic.0", "");
            if (!diagnostic.isBlank()) {
                markdown.append("- Diagnostic: `").append(sanitizeInline(diagnostic)).append("`\n");
            }
            markdown.append("- Review file: `").append(reviewPath).append("`\n\n");
        } catch (Throwable failure) {
            markdown.append("- Status: `failed to read`\n");
            markdown.append("- Review file: `").append(reviewPath).append("`\n");
            markdown.append("- Error: `").append(sanitizeInline(failure.toString())).append("`\n\n");
        }
    }

    private static void appendIrGpuSourceReviewKernelSummary(
            StringBuilder markdown,
            java.util.Properties properties,
            int index
    ) {
        String prefix = "kernel." + index + ".";
        markdown.append("- Kernel `")
                .append(index)
                .append("`: name=`")
                .append(sanitizeInline(properties.getProperty(prefix + "name", "unknown")))
                .append("`, status=`")
                .append(sanitizeInline(properties.getProperty(prefix + "status", "unknown")))
                .append("`, resource=`")
                .append(sanitizeInline(properties.getProperty(prefix + "resource", "unknown")))
                .append("`, irGpuResource=`")
                .append(sanitizeInline(properties.getProperty(prefix + "irGpuResource", "unknown")))
                .append("`\n");
    }

    private static void appendProductionSourceSwitchingValidationSummary(StringBuilder markdown) {
        String validationPath = System.getProperty(PRODUCTION_SOURCE_SWITCHING_VALIDATION_FILE_PROPERTY);
        if (validationPath == null || validationPath.isBlank()) {
            return;
        }

        markdown.append("## Controlled Production Source Switching\n\n");
        try {
            java.util.Properties properties = loadPropertiesIfExists(Paths.get(validationPath));
            if (properties.isEmpty()) {
                markdown.append("- Status: `not recorded`\n");
                markdown.append("- Validation file: `").append(validationPath).append("`\n\n");
                return;
            }
            markdown.append("- Status: `").append(sanitizeInline(properties.getProperty("status", "unknown"))).append("`\n");
            markdown.append("- Review ready: `").append(sanitizeInline(properties.getProperty("reviewReady", "unknown"))).append("`\n");
            markdown.append("- Source selection: `").append(sanitizeInline(properties.getProperty("sourceSelection", "unknown"))).append("`\n");
            markdown.append("- Optimization profile: `").append(sanitizeInline(properties.getProperty("optimizationProfile", "unknown"))).append("`\n");
            markdown.append("- Production source switching: `").append(sanitizeInline(properties.getProperty("productionSourceSwitching", "unknown"))).append("`\n");
            markdown.append("- Production decision mode: `")
                    .append(sanitizeInline(properties.getProperty("productionPromotionDecisionMode", "unknown")))
                    .append("`\n");
            markdown.append("- Scope: `")
                    .append(sanitizeInline(properties.getProperty("scope", "controlled-production-source-switching-smoke")))
                    .append("`\n");
            int kernelCount = parsePositiveInt(properties.getProperty("kernel.count", "0"));
            markdown.append("- Kernel count: `").append(kernelCount).append("`\n");
            for (int index = 0; index < kernelCount; index++) {
                appendIrGpuSourceReviewKernelSummary(markdown, properties, index);
            }
            String diagnostic = properties.getProperty("diagnostic.0", "");
            if (!diagnostic.isBlank()) {
                markdown.append("- Diagnostic: `").append(sanitizeInline(diagnostic)).append("`\n");
            }
            markdown.append("- Validation file: `").append(validationPath).append("`\n\n");
        } catch (Throwable failure) {
            markdown.append("- Status: `failed to read`\n");
            markdown.append("- Validation file: `").append(validationPath).append("`\n");
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
            GpuBackendSourcePromotionWorkloadSummary workloadSummary =
                    GpuBackendSourcePromotionWorkloadSummary.fromProperties(properties);
            markdown.append("- Production promotion operator accepted: `")
                    .append(workloadSummary.productionPromotionOperatorAcceptedCount())
                    .append("/")
                    .append(workloadSummary.kernelCount())
                    .append("`, all=`")
                    .append(sanitizeInline(workloadSummary.productionPromotionOperatorAcceptedAll()))
                    .append("`\n");
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

    private static void appendBackendSourcePromotionCandidateSummary(StringBuilder markdown) {
        String gatePath = System.getProperty(BACKEND_SOURCE_PROMOTION_CANDIDATE_GATE_FILE_PROPERTY);
        if (gatePath == null || gatePath.isBlank()) {
            return;
        }

        markdown.append("## Backend Source Promotion Candidate Gate\n\n");
        try {
            java.util.Properties properties = loadPropertiesIfExists(Paths.get(gatePath));
            if (properties.isEmpty()) {
                markdown.append("- Status: `not recorded`\n");
                markdown.append("- Gate file: `").append(gatePath).append("`\n\n");
                return;
            }
            int kernelCount = parsePositiveInt(properties.getProperty("kernel.count", "0"));
            int candidateReadyCount = parsePositiveInt(properties.getProperty("candidateReady.count", "0"));
            int acceptedCount = parsePositiveInt(properties.getProperty("operatorAcceptance.accepted.count", "0"));
            int boundCount = parsePositiveInt(properties.getProperty("operatorAcceptance.bound.count", "0"));
            int blockerCount = parsePositiveInt(properties.getProperty("blocker.count", "0"));
            markdown.append("- Status: `")
                    .append(sanitizeInline(properties.getProperty("status", "unknown")))
                    .append("`\n");
            markdown.append("- Review ready: `")
                    .append(sanitizeInline(properties.getProperty("reviewReady", "unknown")))
                    .append("`\n");
            markdown.append("- Candidate ready: `")
                    .append(candidateReadyCount)
                    .append("/")
                    .append(kernelCount)
                    .append("`, all=`")
                    .append(sanitizeInline(properties.getProperty("candidateReady.all", "false")))
                    .append("`\n");
            markdown.append("- Source parity matched: `")
                    .append(sanitizeInline(properties.getProperty("sourceParityMatched", "unknown")))
                    .append("`\n");
            markdown.append("- Runtime equivalence passed: `")
                    .append(sanitizeInline(properties.getProperty("runtimeEquivalencePassed", "unknown")))
                    .append("`\n");
            markdown.append("- Controlled source switching: `")
                    .append(sanitizeInline(properties.getProperty("controlledSourceSwitching.status", "not-recorded")))
                    .append("`\n");
            markdown.append("- Operator acceptance mode: `")
                    .append(sanitizeInline(properties.getProperty("operatorAcceptance.mode", "not-recorded")))
                    .append("`\n");
            markdown.append("- Operator accepted: `")
                    .append(acceptedCount)
                    .append("/")
                    .append(kernelCount)
                    .append("`, all=`")
                    .append(sanitizeInline(properties.getProperty("operatorAcceptance.accepted.all", "false")))
                    .append("`\n");
            markdown.append("- Operator bound: `")
                    .append(boundCount)
                    .append("/")
                    .append(kernelCount)
                    .append("`, all=`")
                    .append(sanitizeInline(properties.getProperty("operatorAcceptance.bound.all", "false")))
                    .append("`\n");
            markdown.append("- Device vendor: `")
                    .append(sanitizeInline(properties.getProperty("operatorAcceptance.deviceVendor", "unknown")))
                    .append("`\n");
            markdown.append("- Device label: `")
                    .append(sanitizeInline(properties.getProperty("operatorAcceptance.deviceLabel", "unknown")))
                    .append("`\n");
            markdown.append("- Driver version: `")
                    .append(sanitizeInline(properties.getProperty("operatorAcceptance.driverVersion", "unknown")))
                    .append("`\n");
            markdown.append("- Default production source switching: `")
                    .append(sanitizeInline(properties.getProperty("defaultProductionSourceSwitching", "disabled")))
                    .append("`\n");
            markdown.append("- Candidate production source switching: `")
                    .append(sanitizeInline(properties.getProperty("candidateProductionSourceSwitching", "blocked")))
                    .append("`\n");
            markdown.append("- Production mutation: `")
                    .append(sanitizeInline(properties.getProperty("productionMutation", "disabled")))
                    .append("`\n");
            markdown.append("- Blocker count: `").append(blockerCount).append("`\n");
            if (blockerCount > 0) {
                markdown.append("- First blocker: `")
                        .append(sanitizeInline(properties.getProperty("blocker.0", "unknown")))
                        .append("`\n");
            }
            String diagnostic = properties.getProperty("diagnostic", "");
            if (!diagnostic.isBlank()) {
                markdown.append("- Diagnostic: `").append(sanitizeInline(diagnostic)).append("`\n");
            }
            markdown.append("- Gate file: `").append(gatePath).append("`\n\n");
        } catch (Throwable failure) {
            markdown.append("- Status: `failed to read`\n");
            markdown.append("- Gate file: `").append(gatePath).append("`\n");
            markdown.append("- Error: `").append(sanitizeInline(failure.toString())).append("`\n\n");
        }
    }

    private static void appendProductionPromotionExplainabilitySummary(StringBuilder markdown) {
        String explainabilityPath = System.getProperty(PRODUCTION_PROMOTION_EXPLAINABILITY_FILE_PROPERTY);
        if (explainabilityPath == null || explainabilityPath.isBlank()) {
            return;
        }

        markdown.append("## Production Promotion Explainability\n\n");
        try {
            java.util.Properties properties = loadPropertiesIfExists(Paths.get(explainabilityPath));
            if (properties.isEmpty()) {
                markdown.append("- Status: `not recorded`\n");
                markdown.append("- Explainability file: `").append(explainabilityPath).append("`\n\n");
                return;
            }
            GpuProductionPromotionExplainabilitySummary summary =
                    GpuProductionPromotionExplainabilitySummary.fromProperties(properties);
            markdown.append("- Status: `").append(sanitizeInline(summary.status())).append("`\n");
            markdown.append("- Contract: `").append(summary.contractStatus()).append("`\n");
            markdown.append("- Decision mode: `")
                    .append(sanitizeInline(summary.decisionMode()))
                    .append("`\n");
            if (!summary.contractValid()) {
                markdown.append("- Contract violation: `").append(sanitizeInline(summary.firstViolation())).append("`\n");
            }
            markdown.append("- Production source switching allowed: `")
                    .append(sanitizeInline(summary.productionSourceSwitchingAllowed()))
                    .append("`\n");
            markdown.append("- Production source switching enabled: `")
                    .append(sanitizeInline(summary.productionSourceSwitchingEnabled()))
                    .append("`\n");
            markdown.append("- Production mutation allowed: `")
                    .append(sanitizeInline(summary.productionMutationAllowed()))
                    .append("`\n");
            markdown.append("- Production mutation enabled: `")
                    .append(sanitizeInline(summary.productionMutationEnabled()))
                    .append("`\n");
            markdown.append("- Controlled source switching smoke: `")
                    .append(sanitizeInline(summary.controlledProductionSourceSwitchingStatus()))
                    .append("`\n");
            markdown.append("- Controlled source switching kernels: `")
                    .append(summary.controlledProductionSourceSwitchingKernelCount())
                    .append("`\n");
            markdown.append("- Controlled real workload coverage: `")
                    .append(summary.controlledProductionSourceSwitchingRealWorkloadCoveredCount())
                    .append("/")
                    .append(summary.controlledProductionSourceSwitchingRealWorkloadTotalCount())
                    .append("`\n");
            markdown.append("- Controlled real workload coverage all: `")
                    .append(sanitizeInline(summary.controlledProductionSourceSwitchingRealWorkloadCoveredAll()))
                    .append("`\n");
            markdown.append("- Production readiness checklist: `")
                    .append(summary.readinessChecklistReadyCount())
                    .append(" ready / ")
                    .append(summary.readinessChecklistBlockedCount())
                    .append(" blocked`\n");
            markdown.append("- Production readiness checklist all: `")
                    .append(sanitizeInline(summary.readinessChecklistReadyAll()))
                    .append("`\n");
            if (summary.readinessChecklistBlockedCount() > 0) {
                markdown.append("- First readiness blocker: `")
                        .append(sanitizeInline(summary.readinessChecklistFirstBlocked()))
                        .append("`\n");
            }
            markdown.append("- Kernel count: `").append(summary.kernelCount()).append("`\n");
            markdown.append("- I3 review-ready kernels: `").append(summary.i3ReviewReadyCount()).append("`\n");
            markdown.append("- I3 blocked kernels: `").append(summary.i3BlockedCount()).append("`\n");
            markdown.append("- Optimizer families: `")
                    .append(summary.optimizerFamilyCount())
                    .append("`\n");
            markdown.append("- Optimizer promotion-ready families: `")
                    .append(summary.optimizerFamilyPromotionReadyCount())
                    .append("`\n");
            markdown.append("- Optimizer payload-complete families: `")
                    .append(summary.optimizerFamilyPayloadCompleteCount())
                    .append("`\n");
            markdown.append("- Optimizer payload complete all: `")
                    .append(sanitizeInline(summary.optimizerFamilyPayloadCompleteAll()))
                    .append("`\n");
            if (!"none".equals(summary.optimizerFamilySummary())) {
                markdown.append("- Optimizer family summary: `")
                        .append(sanitizeInline(summary.optimizerFamilySummary()))
                        .append("`\n");
            }
            markdown.append("- Blocker count: `").append(summary.blockerCount()).append("`\n");
            if (summary.blockerCount() > 0) {
                markdown.append("- First blocker: `")
                        .append(sanitizeInline(summary.firstBlocker()))
                        .append("`\n");
            }
            String diagnostic = properties.getProperty("diagnostic.0", "");
            if (!diagnostic.isBlank()) {
                markdown.append("- Diagnostic: `").append(sanitizeInline(diagnostic)).append("`\n");
            }
            markdown.append("- Explainability file: `").append(explainabilityPath).append("`\n\n");
        } catch (Throwable failure) {
            markdown.append("- Status: `failed to read`\n");
            markdown.append("- Explainability file: `").append(explainabilityPath).append("`\n");
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
                .append("`, operatorAccepted=`")
                .append(sanitizeInline(properties.getProperty(
                        prefix + "sourceSwitching.productionPromotionOperatorAccepted",
                        "false"
                )))
                .append("`, runtimeIr=`")
                .append(sanitizeInline(properties.getProperty(prefix + "runtimeIrHandoff.selectedStage", "unknown")))
                .append("`, productionMutation=`")
                .append(sanitizeInline(properties.getProperty(prefix + "runtimeProductionMutationSafety.productionMutationEnabled", "unknown")))
                .append("`, sourceReady=`")
                .append(sanitizeInline(properties.getProperty(prefix + "i3Readiness.sourceReady", "unknown")))
                .append("`, i3=`")
                .append(sanitizeInline(properties.getProperty(prefix + "i3Readiness.status", "unknown")))
                .append("`\n");
        String sourceSwitchingDiagnostic = properties.getProperty(prefix + "sourceSwitching.diagnostic.0", "");
        if (!sourceSwitchingDiagnostic.isBlank()) {
            markdown.append("- Kernel `")
                    .append(index)
                    .append("` source switching: status=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "sourceSwitching.status", "not-recorded")))
                    .append("`, profile=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "sourceSwitching.optimizationProfile", "unknown")))
                    .append("`, sourcePromotionFirstBlocker=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "sourceSwitching.sourcePromotionFirstBlocker", "unknown")))
                    .append("`, operatorAccepted=`")
                    .append(sanitizeInline(properties.getProperty(
                            prefix + "sourceSwitching.productionPromotionOperatorAccepted",
                            "false"
                    )))
                    .append("`, first=`")
                    .append(sanitizeInline(sourceSwitchingDiagnostic))
                    .append("`\n");
        }
        String runtimeIrHandoffDiagnostic = properties.getProperty(prefix + "runtimeIrHandoff.diagnostic.0", "");
        if (!runtimeIrHandoffDiagnostic.isBlank()) {
            markdown.append("- Kernel `")
                    .append(index)
                    .append("` runtime IR handoff: stage=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeIrHandoff.selectedStage", "unknown")))
                    .append("`, transformed=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeIrHandoff.optimizedDiffersFromOriginal", "unknown")))
                    .append("`, rollback=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeIrHandoff.optimizationRequiresRollback", "unknown")))
                    .append("`, rejected=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeIrHandoff.optimizedIrRejected", "unknown")))
                    .append("`, fallback=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeIrHandoff.fallbackDecision", "unknown")))
                    .append("`, first=`")
                    .append(sanitizeInline(runtimeIrHandoffDiagnostic))
                    .append("`\n");
        }
        String productionMutationDiagnostic = properties.getProperty(prefix + "runtimeProductionMutationSafety.diagnostic.0", "");
        if (!productionMutationDiagnostic.isBlank()) {
            markdown.append("- Kernel `")
                    .append(index)
                    .append("` production mutation safety: enabled=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeProductionMutationSafety.productionMutationEnabled", "unknown")))
                    .append("`, gate=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeProductionMutationSafety.productionGateStatus", "unknown")))
                    .append("`, profileRequested=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeProductionMutationSafety.productionProfileRequested", "unknown")))
                    .append("`, first=`")
                    .append(sanitizeInline(productionMutationDiagnostic))
                    .append("`\n");
        }
        String i3ReadinessDiagnostic = properties.getProperty(prefix + "i3Readiness.diagnostic.0", "");
        if (!i3ReadinessDiagnostic.isBlank()) {
            markdown.append("- Kernel `")
                    .append(index)
                    .append("` I3 readiness: status=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "i3Readiness.status", "unknown")))
                    .append("`, sourcePromotion=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "i3Readiness.sourcePromotionStatus", "unknown")))
                    .append("`, sourceReady=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "i3Readiness.sourceReady", "unknown")))
                    .append("`, optimizerGate=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "i3Readiness.optimizerProductionGateStatus", "unknown")))
                    .append("`, productionMutation=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "i3Readiness.productionMutationEnabled", "unknown")))
                    .append("`, first=`")
                    .append(sanitizeInline(i3ReadinessDiagnostic))
                    .append("`\n");
        }
        String optimizerDriftStatus = properties.getProperty(prefix + "runtimeOptimizerDrift.status", "not-recorded");
        if (!"not-recorded".equals(optimizerDriftStatus)) {
            markdown.append("- Kernel `")
                    .append(index)
                    .append("` runtime optimizer drift: passes=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeOptimizerDrift.pass.count", "0")))
                    .append("`, applied=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeOptimizerDrift.pass.applied.count", "0")))
                    .append("`, rolledBack=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeOptimizerDrift.pass.rolledBack.count", "0")))
                    .append("`, failed=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeOptimizerDrift.pass.failed.count", "0")))
                    .append("`, proof=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeOptimizerDrift.proofArtifact.count", "0")))
                    .append("`, acceptedProof=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeOptimizerDrift.proofArtifact.accepted.count", "0")))
                    .append("`, blockingProof=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeOptimizerDrift.proofArtifact.blocking.count", "0")))
                    .append("`, optimizerFamilies=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeOptimizerDrift.optimizerFamily.count", "0")))
                    .append("`, promotionReadyFamilies=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeOptimizerDrift.optimizerFamily.promotionReady.count", "0")))
                    .append("`, selected=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeOptimizerDrift.selectedRuntimeIrStage", "unknown")))
                    .append("`, fallback=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeOptimizerDrift.fallbackDecision", "unknown")))
                    .append("`, gate=`")
                    .append(sanitizeInline(properties.getProperty(prefix + "runtimeOptimizerDrift.productionGateStatus", "unknown")))
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
        GpuBackendSourcePromotionWorkloadSummary summary =
                GpuBackendSourcePromotionWorkloadSummary.fromProperties(properties);
        if (summary.sourceSwitchingDecisions().isBlank()) {
            return;
        }
        markdown.append("- Source switching decisions: `")
                .append(sanitizeInline(summary.sourceSwitchingDecisions()))
                .append("`\n");
        if (!summary.sourcePromotionFirstBlockers().isBlank()) {
            markdown.append("- Source switching first blockers: `")
                    .append(sanitizeInline(summary.sourcePromotionFirstBlockers()))
                    .append("`\n");
        }
        if (!summary.sourcePromotionFirstBlockerFamilies().isBlank()) {
            markdown.append("- Source switching first blocker families: `")
                    .append(sanitizeInline(summary.sourcePromotionFirstBlockerFamilies()))
                    .append("`\n");
        }
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

    private static void ensureI3ReadinessWorkloadSummaryArtifact() {
        String summaryPath = System.getProperty(I3_READINESS_WORKLOAD_SUMMARY_FILE_PROPERTY);
        String gatePath = System.getProperty(BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE_FILE_PROPERTY);
        if (summaryPath == null || summaryPath.isBlank() || gatePath == null || gatePath.isBlank()) {
            return;
        }
        try {
            Path path = Paths.get(summaryPath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            java.util.Properties gate = loadPropertiesIfExists(Paths.get(gatePath));
            Files.writeString(path, formatI3ReadinessWorkloadSummary(gate), StandardCharsets.UTF_8);
        } catch (Throwable failure) {
            // Validation reports should remain printable even if the optional compact CI summary cannot be written.
        }
    }

    private static void ensureProductionPromotionExplainabilityArtifact() {
        String outputPath = System.getProperty(PRODUCTION_PROMOTION_EXPLAINABILITY_FILE_PROPERTY);
        String gatePath = System.getProperty(BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE_FILE_PROPERTY);
        String i3SummaryPath = System.getProperty(I3_READINESS_WORKLOAD_SUMMARY_FILE_PROPERTY);
        String supportPath = System.getProperty(BACKEND_PROMOTION_ARTIFACT_SUPPORT_FILE_PROPERTY);
        String controlledProductionSourceSwitchingPath = System.getProperty(PRODUCTION_SOURCE_SWITCHING_VALIDATION_FILE_PROPERTY);
        if (outputPath == null || outputPath.isBlank() || gatePath == null || gatePath.isBlank()) {
            return;
        }
        try {
            Path path = Paths.get(outputPath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            java.util.Properties gate = loadPropertiesIfExists(Paths.get(gatePath));
            java.util.Properties i3Summary = i3SummaryPath == null || i3SummaryPath.isBlank()
                    ? new java.util.Properties()
                    : loadPropertiesIfExists(Paths.get(i3SummaryPath));
            java.util.Properties backendPromotionArtifactSupport = supportPath == null || supportPath.isBlank()
                    ? openClBackendPromotionArtifactSupportProperties()
                    : loadPropertiesIfExists(Paths.get(supportPath));
            java.util.Properties controlledProductionSourceSwitchingValidation = controlledProductionSourceSwitchingPath == null
                    || controlledProductionSourceSwitchingPath.isBlank()
                    ? new java.util.Properties()
                    : loadPropertiesIfExists(Paths.get(controlledProductionSourceSwitchingPath));
            applyOptimizerFamilyRuntimeEquivalenceHistoryBaseline(gate);
            Files.writeString(
                    path,
                    GpuProductionPromotionExplainabilityFormatter.format(
                            gate,
                            i3Summary,
                            backendPromotionArtifactSupport,
                            controlledProductionSourceSwitchingValidation
                    ),
                    StandardCharsets.UTF_8
            );
        } catch (Throwable failure) {
            // Keep the validation report printable even if this optional explainability artifact cannot be written.
        }
    }

    private static void applyOptimizerFamilyRuntimeEquivalenceHistoryBaseline(java.util.Properties gate) {
        int promotionReadyFamilies = parsePositiveInt(gate.getProperty("optimizerFamily.promotionReady.count", "0"));
        if (promotionReadyFamilies == 0 || gate.containsKey("optimizerFamily.runtimeEquivalenceHistoryBaselineReady")) {
            return;
        }
        gate.setProperty(
                "optimizerFamily.runtimeEquivalenceHistoryBaselineReady",
                Boolean.toString(hasOptimizerFamilyRuntimeEquivalenceHistoryBaseline())
        );
    }

    private static boolean hasOptimizerFamilyRuntimeEquivalenceHistoryBaseline() {
        String historyPropertiesPath = System.getProperty(HISTORY_PROPERTIES_FILE_PROPERTY);
        if (historyPropertiesPath == null || historyPropertiesPath.isBlank()) {
            return false;
        }
        try {
            return OpenClValidationHistoryIO.readAll(Paths.get(historyPropertiesPath)).stream()
                    .anyMatch(OpenClValidationReporter::hasOptimizerFamilyRuntimeEquivalenceHistoryBaseline);
        } catch (Throwable failure) {
            return false;
        }
    }

    private static boolean hasOptimizerFamilyRuntimeEquivalenceHistoryBaseline(OpenClValidationHistoryEntry entry) {
        String workloadStatus = entry.backendSourcePromotionWorkloadStatus();
        String explainabilityStatus = entry.productionPromotionExplainabilityStatus();
        return containsPositiveOptimizerFamilies(workloadStatus)
                && containsRuntimeEquivalencePassed(workloadStatus)
                && containsRuntimeEquivalencePassed(explainabilityStatus);
    }

    private static boolean containsPositiveOptimizerFamilies(String status) {
        return containsPositiveMetric(status, "optimizerPromotionReadyFamilies=")
                || containsPositiveMetric(status, "promotionReadyFamilies=");
    }

    private static boolean containsRuntimeEquivalencePassed(String status) {
        return status != null && status.contains("runtimeEquivalencePassed=true");
    }

    private static boolean containsPositiveMetric(String status, String token) {
        if (status == null) {
            return false;
        }
        int start = status.indexOf(token);
        if (start < 0) {
            return false;
        }
        int valueStart = start + token.length();
        int valueEnd = valueStart;
        while (valueEnd < status.length() && Character.isDigit(status.charAt(valueEnd))) {
            valueEnd++;
        }
        if (valueEnd == valueStart) {
            return false;
        }
        return parsePositiveInt(status.substring(valueStart, valueEnd)) > 0;
    }

    private static java.util.Properties openClBackendPromotionArtifactSupportProperties() throws IOException {
        java.util.Properties properties = new java.util.Properties();
        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            properties.load(new java.io.StringReader(backend.promotionArtifactSupport().toPropertiesText()));
        }
        return properties;
    }

    private static String formatI3ReadinessWorkloadSummary(java.util.Properties gate) {
        if (gate == null || gate.isEmpty()) {
            return "status=not-recorded\n"
                    + "kernel.count=0\n"
                    + "reviewReady.count=0\n"
                    + "blocked.count=0\n"
                    + "productionEnabled.count=0\n"
                    + "sourceReady.count=0\n"
                    + "sourceReady.all=false\n"
                    + "productionMutationEnabled=false\n"
                    + "diagnostic.0=backend source promotion workload gate was not recorded\n";
        }
        int kernelCount = parsePositiveInt(gate.getProperty("kernel.count", "0"));
        java.util.LinkedHashMap<String, Integer> statusCounts = new java.util.LinkedHashMap<>();
        boolean productionMutationEnabled = false;
        int sourceReadyCount = 0;
        for (int index = 0; index < kernelCount; index++) {
            String prefix = "kernel." + index + ".";
            String status = normalizeI3ReadinessStatus(gate.getProperty(prefix + "i3Readiness.status", "unknown"));
            statusCounts.merge(status, 1, Integer::sum);
            if ("true".equals(gate.getProperty(prefix + "i3Readiness.sourceReady", "false"))) {
                sourceReadyCount++;
            }
            productionMutationEnabled |= "true".equals(gate.getProperty(prefix + "i3Readiness.productionMutationEnabled", "false"));
        }
        int reviewReadyCount = statusCounts.getOrDefault("review-ready", 0);
        int blockedCount = statusCounts.getOrDefault("blocked", 0);
        int productionEnabledCount = statusCounts.getOrDefault("production-enabled", 0);
        String status = productionEnabledCount > 0
                ? "production-enabled"
                : reviewReadyCount > 0 && blockedCount == 0 && kernelCount > 0 ? "review-ready" : "blocked";
        StringBuilder builder = new StringBuilder();
        builder.append("status=").append(status).append('\n');
        builder.append("gateStatus=").append(gate.getProperty("status", "unknown")).append('\n');
        builder.append("reviewReady=").append(gate.getProperty("reviewReady", "unknown")).append('\n');
        builder.append("sourceParityMatched=").append(gate.getProperty("sourceParityMatched", "unknown")).append('\n');
        builder.append("runtimeEquivalencePassed=").append(gate.getProperty("runtimeEquivalencePassed", "unknown")).append('\n');
        builder.append("realWorkloadEvidence=").append(gate.getProperty("realWorkloadEvidence", "not-wired")).append('\n');
        builder.append("kernel.count=").append(kernelCount).append('\n');
        builder.append("reviewReady.count=").append(reviewReadyCount).append('\n');
        builder.append("blocked.count=").append(blockedCount).append('\n');
        builder.append("productionEnabled.count=").append(productionEnabledCount).append('\n');
        builder.append("sourceReady.count=").append(sourceReadyCount).append('\n');
        builder.append("sourceReady.all=").append(kernelCount > 0 && sourceReadyCount == kernelCount).append('\n');
        builder.append("productionMutationEnabled=").append(productionMutationEnabled).append('\n');
        builder.append("status.count=").append(statusCounts.size()).append('\n');
        int statusIndex = 0;
        for (java.util.Map.Entry<String, Integer> entry : statusCounts.entrySet()) {
            builder.append("status.").append(statusIndex).append(".name=").append(entry.getKey()).append('\n');
            builder.append("status.").append(statusIndex).append(".count=").append(entry.getValue()).append('\n');
            statusIndex++;
        }
        for (int index = 0; index < kernelCount; index++) {
            String prefix = "kernel." + index + ".";
            builder.append(prefix).append("resource=").append(gate.getProperty(prefix + "sourceKernelResource", "unknown")).append('\n');
            builder.append(prefix).append("i3Status=").append(normalizeI3ReadinessStatus(
                    gate.getProperty(prefix + "i3Readiness.status", "unknown"))).append('\n');
            builder.append(prefix).append("runtimeIr=").append(gate.getProperty(prefix + "runtimeIrHandoff.selectedStage", "unknown")).append('\n');
            builder.append(prefix).append("optimizerDriftStatus=").append(gate.getProperty(prefix + "runtimeOptimizerDrift.status", "not-recorded")).append('\n');
            builder.append(prefix).append("optimizerDriftPassCount=").append(gate.getProperty(prefix + "runtimeOptimizerDrift.pass.count", "0")).append('\n');
            builder.append(prefix).append("optimizerDriftRolledBackCount=").append(gate.getProperty(prefix + "runtimeOptimizerDrift.pass.rolledBack.count", "0")).append('\n');
            builder.append(prefix).append("optimizerDriftFallbackDecision=").append(gate.getProperty(prefix + "runtimeOptimizerDrift.fallbackDecision", "unknown")).append('\n');
            builder.append(prefix).append("sourceReady=").append(gate.getProperty(prefix + "i3Readiness.sourceReady", "unknown")).append('\n');
            builder.append(prefix).append("sourcePromotionStatus=").append(gate.getProperty(prefix + "i3Readiness.sourcePromotionStatus", "unknown")).append('\n');
            builder.append(prefix).append("optimizerProductionGateStatus=").append(gate.getProperty(prefix + "i3Readiness.optimizerProductionGateStatus", "unknown")).append('\n');
            builder.append(prefix).append("productionMutationEnabled=").append(gate.getProperty(prefix + "i3Readiness.productionMutationEnabled", "unknown")).append('\n');
            builder.append(prefix).append("diagnostic=").append(gate.getProperty(
                    prefix + "i3Readiness.diagnostic.0",
                    "I3 readiness evidence is missing for this workload kernel"
            )).append('\n');
        }
        builder.append("diagnostic.0=").append(i3ReadinessWorkloadSummaryDiagnostic(status, kernelCount, reviewReadyCount, blockedCount)).append('\n');
        return builder.toString();
    }

    private static String normalizeI3ReadinessStatus(String status) {
        return switch (status) {
            case "production-enabled", "review-ready", "blocked" -> status;
            default -> "blocked";
        };
    }

    private static String i3ReadinessWorkloadSummaryDiagnostic(
            String status,
            int kernelCount,
            int reviewReadyCount,
            int blockedCount
    ) {
        if (kernelCount == 0) {
            return "I3 readiness workload gate has no runtime kernel evidence yet";
        }
        if ("production-enabled".equals(status)) {
            return "I3 production mutation is enabled for at least one workload kernel";
        }
        if ("review-ready".equals(status)) {
            return "all workload kernels are I3 review-ready while production mutation remains disabled";
        }
        return "I3 workload readiness remains blocked: reviewReady=" + reviewReadyCount + ", blocked=" + blockedCount;
    }

    private static void updateHistoryArtifacts(
            String markdown,
            OpenClValidationHistoryEntry entry
    ) throws IOException {
        String historyPropertiesPath = System.getProperty(HISTORY_PROPERTIES_FILE_PROPERTY);
        String historyMarkdownPath = System.getProperty(HISTORY_MARKDOWN_FILE_PROPERTY);
        if ((historyPropertiesPath == null || historyPropertiesPath.isBlank())
                && (historyMarkdownPath == null || historyMarkdownPath.isBlank())) {
            return;
        }

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

    private static OpenClValidationHistoryEntry buildHistoryEntry(
            OpenClKernelLaunchAdvisorySummary launchAdvisorySummary,
            OpenClCompilerResourceSummary compilerResourceSummary
    ) {
        String requestedVendor = env("JTG_VALIDATION_VENDOR");
        String bucketSummary = summarizeBuckets();
        String longRunningStatus = summarizeLongRunningStatus();
        String workloadStatus = summarizeWorkloadStatus();
        String irGpuSourceReviewStatus = summarizeIrGpuSourceReviewStatus();
        String productionSourceSwitchingValidationStatus = summarizeProductionSourceSwitchingValidationStatus();
        String backendSourcePromotionContractStatus = summarizeBackendSourcePromotionContractStatus();
        String backendSourcePromotionWorkloadStatus = summarizeBackendSourcePromotionWorkloadStatus();
        String productionPromotionExplainabilityStatus = summarizeProductionPromotionExplainabilityStatus();
        String kernelLaunchAdvisoryStatus = launchAdvisorySummary.toHistorySummary();
        String compilerResourceStatus = compilerResourceSummary.toHistorySummary();

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
                    irGpuSourceReviewStatus,
                    productionSourceSwitchingValidationStatus,
                    backendSourcePromotionContractStatus,
                    backendSourcePromotionWorkloadStatus,
                    productionPromotionExplainabilityStatus,
                    kernelLaunchAdvisoryStatus,
                    compilerResourceStatus
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
                    irGpuSourceReviewStatus,
                    productionSourceSwitchingValidationStatus,
                    backendSourcePromotionContractStatus,
                    backendSourcePromotionWorkloadStatus,
                    productionPromotionExplainabilityStatus,
                    kernelLaunchAdvisoryStatus,
                    compilerResourceStatus
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

    private static String summarizeIrGpuSourceReviewStatus() {
        String reviewPath = System.getProperty(IRGPU_SOURCE_REVIEW_FILE_PROPERTY);
        if (reviewPath == null || reviewPath.isBlank()) {
            return "not recorded";
        }
        try {
            java.util.Properties properties = loadPropertiesIfExists(Paths.get(reviewPath));
            if (properties.isEmpty()) {
                return "not recorded";
            }
            return properties.getProperty("status", "unknown")
                    + " (reviewReady=" + properties.getProperty("reviewReady", "unknown")
                    + ", sourceSelection=" + properties.getProperty("sourceSelection", "unknown")
                    + ", optimizationProfile=" + properties.getProperty("optimizationProfile", "unknown")
                    + ", productionSourceSwitching=" + properties.getProperty("productionSourceSwitching", "unknown")
                    + summarizeIrGpuSourceReviewKernelEvidence(properties)
                    + ")";
        } catch (Throwable failure) {
            return "failed to read";
        }
    }

    private static String summarizeIrGpuSourceReviewKernelEvidence(java.util.Properties properties) {
        int kernelCount = parsePositiveInt(properties.getProperty("kernel.count", "0"));
        if (kernelCount == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(", kernelCount=").append(kernelCount);
        for (int index = 0; index < kernelCount; index++) {
            String prefix = "kernel." + index + ".";
            builder.append(", kernel.")
                    .append(index)
                    .append('=')
                    .append(properties.getProperty(prefix + "resource", "unknown"))
                    .append("[status=")
                    .append(properties.getProperty(prefix + "status", "unknown"))
                    .append(", irGpuResource=")
                    .append(properties.getProperty(prefix + "irGpuResource", "unknown"))
                    .append(']');
        }
        return builder.toString();
    }

    private static String summarizeProductionSourceSwitchingValidationStatus() {
        String validationPath = System.getProperty(PRODUCTION_SOURCE_SWITCHING_VALIDATION_FILE_PROPERTY);
        if (validationPath == null || validationPath.isBlank()) {
            return "not recorded";
        }
        try {
            java.util.Properties properties = loadPropertiesIfExists(Paths.get(validationPath));
            if (properties.isEmpty()) {
                return "not recorded";
            }
            return properties.getProperty("status", "unknown")
                    + " (reviewReady=" + properties.getProperty("reviewReady", "unknown")
                    + ", sourceSelection=" + properties.getProperty("sourceSelection", "unknown")
                    + ", productionSourceSwitching=" + properties.getProperty("productionSourceSwitching", "unknown")
                    + ", productionDecision=" + properties.getProperty("productionPromotionDecisionMode", "unknown")
                    + summarizeIrGpuSourceReviewKernelEvidence(properties)
                    + ")";
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
            return GpuBackendSourcePromotionWorkloadSummary.fromProperties(properties).historyStatus();
        } catch (Throwable failure) {
            return "failed to read";
        }
    }

    private static String summarizeProductionPromotionExplainabilityStatus() {
        String explainabilityPath = System.getProperty(PRODUCTION_PROMOTION_EXPLAINABILITY_FILE_PROPERTY);
        if (explainabilityPath == null || explainabilityPath.isBlank()) {
            return "not recorded";
        }
        try {
            java.util.Properties properties = loadPropertiesIfExists(Paths.get(explainabilityPath));
            if (properties.isEmpty()) {
                return "not recorded";
            }
            return GpuProductionPromotionExplainabilitySummary.fromProperties(properties).historyStatus();
        } catch (Throwable failure) {
            return "failed to read";
        }
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
