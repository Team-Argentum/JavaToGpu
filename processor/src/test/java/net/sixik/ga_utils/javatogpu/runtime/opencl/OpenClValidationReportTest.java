package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClValidationReportTest {

    @Test
    void markdownIncludesKeyRuntimeSections() {
        OpenClValidationReport report = new OpenClValidationReport(
                Instant.parse("2026-06-24T12:00:00Z"),
                "OpenCL",
                "INSTANCE",
                "Mock GPU",
                "Mock Vendor",
                "1.2.3",
                "OpenCL 3.0 Mock",
                "Mock Platform",
                "OpenCL 3.0 Platform",
                true,
                true,
                false,
                32768,
                256,
                new OpenClRuntimeStatistics(3, 1, 2, 1, 4)
        );

        String markdown = report.toMarkdown();

        assertTrue(markdown.contains("# OpenCL Validation Report"));
        assertTrue(markdown.contains("- Backend: `OpenCL`"));
        assertTrue(markdown.contains("- Device label: `Mock GPU`"));
        assertTrue(markdown.contains("- Vendor: `Mock Vendor`"));
        assertTrue(markdown.contains("- Double precision: `yes`"));
        assertTrue(markdown.contains("- 3D image writes: `no`"));
        assertTrue(markdown.contains("- Compile cache hits: `2`"));
    }

    @Test
    void longRunningSummaryRoundTripsThroughPropertiesFormat() throws Exception {
        java.nio.file.Path summaryFile = java.nio.file.Files.createTempFile("javatogpu-opencl-long-running", ".properties");
        OpenClLongRunningValidationSummary summary = new OpenClLongRunningValidationSummary(
                Instant.parse("2026-07-01T12:00:00Z"),
                "passed",
                150,
                new OpenClRuntimeStatistics(600, 4, 596, 1, 600)
        );

        OpenClLongRunningValidationSummaryIO.write(summaryFile, summary);
        OpenClLongRunningValidationSummary loaded = OpenClLongRunningValidationSummaryIO.readIfExists(summaryFile).orElseThrow();

        assertEquals(summary, loaded);
    }

    @Test
    void bucketStatusRegistryRoundTripsThroughPropertiesFormat() throws Exception {
        java.nio.file.Path registryFile = java.nio.file.Files.createTempFile("javatogpu-opencl-buckets", ".properties");
        java.util.Map<String, OpenClValidationBucketStatus> statuses = new java.util.LinkedHashMap<>();
        statuses.put(
                "compileOnlyTest",
                new OpenClValidationBucketStatus("compileOnlyTest", "passed", Instant.parse("2026-07-01T12:00:00Z"))
        );
        statuses.put(
                "openClLongRunningStabilityTest",
                new OpenClValidationBucketStatus("openClLongRunningStabilityTest", "passed", Instant.parse("2026-07-01T12:05:00Z"))
        );
        statuses.put(
                "performanceStressTest",
                new OpenClValidationBucketStatus("performanceStressTest", "failed", Instant.parse("2026-07-01T12:06:00Z"))
        );

        OpenClValidationBucketStatusIO.writeAll(registryFile, statuses);
        java.util.Map<String, OpenClValidationBucketStatus> loaded = OpenClValidationBucketStatusIO.readAll(registryFile);

        assertEquals(statuses, loaded);
    }

    @Test
    void validationHistoryRoundTripsThroughPropertiesFormat() throws Exception {
        java.nio.file.Path historyFile = java.nio.file.Files.createTempFile("javatogpu-opencl-history", ".properties");
        java.util.List<OpenClValidationHistoryEntry> entries = java.util.List.of(
                new OpenClValidationHistoryEntry(
                        Instant.parse("2026-07-01T12:10:00Z"),
                        "NVIDIA",
                        "OpenCL",
                        "Mock GPU A",
                        "Mock Vendor",
                        "1.2.3",
                        "OpenCL 3.0 Mock",
                        "compileOnlyTest=passed, performanceStressTest=passed",
                        "passed",
                        "passed (perlin=passed, packedBlob=passed)"
                ),
                new OpenClValidationHistoryEntry(
                        Instant.parse("2026-07-01T12:00:00Z"),
                        "Intel",
                        "OpenCL",
                        "Mock GPU B",
                        "Mock Vendor",
                        "1.2.2",
                        "OpenCL 3.0 Mock",
                        "compileOnlyTest=passed",
                        "not recorded",
                        "not recorded"
                )
        );

        OpenClValidationHistoryIO.writeAll(historyFile, entries);
        java.util.List<OpenClValidationHistoryEntry> loaded = OpenClValidationHistoryIO.readAll(historyFile);

        assertEquals(entries, loaded);
    }

    @Test
    void validationHistoryMarkdownKeepsFailedBucketStatusVisible() throws Exception {
        java.nio.file.Path historyMarkdownFile = java.nio.file.Files.createTempFile("javatogpu-opencl-history", ".md");
        java.util.List<OpenClValidationHistoryEntry> entries = java.util.List.of(
                new OpenClValidationHistoryEntry(
                        Instant.parse("2026-07-01T12:10:00Z"),
                        "nvidia",
                        "OpenCL",
                        "Mock GPU",
                        "Mock Vendor",
                        "1.2.3",
                        "OpenCL 3.0 Mock",
                        "compileOnlyTest=passed, performanceStressTest=failed",
                        "passed",
                        "not recorded"
                )
        );

        OpenClValidationHistoryIO.writeMarkdown(historyMarkdownFile, entries);
        String markdown = java.nio.file.Files.readString(historyMarkdownFile);

        assertTrue(markdown.contains("compileOnlyTest=passed, performanceStressTest=failed"));
        assertTrue(markdown.contains("| nvidia |"));
    }

    @Test
    void validationHistoryMarkdownKeepsRuntimeEquivalenceAndStressArtifactsVisible() throws Exception {
        java.nio.file.Path historyMarkdownFile = java.nio.file.Files.createTempFile("javatogpu-opencl-history-artifacts", ".md");
        String bucketSummary = "openClWorkloadValidationTest=passed, openClLongRunningStabilityTest=passed, benchmarkTest=passed";
        String workloadSummary = "passed (perlin=passed, packedBlob=passed, packedNumeric=passed, image=passed)";
        java.util.List<OpenClValidationHistoryEntry> entries = java.util.List.of(
                new OpenClValidationHistoryEntry(
                        Instant.parse("2026-07-01T12:20:00Z"),
                        "nvidia",
                        "OpenCL",
                        "Mock GPU",
                        "Mock Vendor",
                        "1.2.3",
                        "OpenCL 3.0 Mock",
                        bucketSummary,
                        "passed",
                        workloadSummary
                )
        );

        OpenClValidationHistoryIO.writeMarkdown(historyMarkdownFile, entries);
        String markdown = java.nio.file.Files.readString(historyMarkdownFile);

        assertTrue(markdown.contains(bucketSummary));
        assertTrue(markdown.contains("| passed | " + workloadSummary + " |"));
        assertTrue(markdown.contains("openClLongRunningStabilityTest=passed"));
        assertTrue(markdown.contains("benchmarkTest=passed"));
    }

    @Test
    void workloadSummaryRoundTripsThroughPropertiesFormat() throws Exception {
        java.nio.file.Path summaryFile = java.nio.file.Files.createTempFile("javatogpu-opencl-workloads", ".properties");
        OpenClWorkloadValidationSummary summary = new OpenClWorkloadValidationSummary(
                Instant.parse("2026-07-01T12:00:00Z"),
                "passed",
                "passed",
                "passed",
                "passed",
                "passed"
        );

        OpenClWorkloadValidationSummaryIO.write(summaryFile, summary);
        OpenClWorkloadValidationSummary loaded = OpenClWorkloadValidationSummaryIO.readIfExists(summaryFile).orElseThrow();

        assertEquals(summary, loaded);
    }

    @Test
    void backendValidationReportUsesRuntimeDeviceInfo() {
        OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend(OpenClGpuRuntimeBackend.CacheMode.SHARED) {
            @Override
            protected OpenClValidationDeviceInfo runtimeValidationDeviceInfo() {
                return new OpenClValidationDeviceInfo(
                        "Shared Mock GPU",
                        "Vendor X",
                        "Driver 99",
                        "OpenCL 2.1 Vendor X",
                        "Platform X",
                        "OpenCL 2.1 Platform X",
                        true,
                        false,
                        false,
                        65536,
                        512
                );
            }
        };

        OpenClValidationReport report = backend.validationReport();

        assertEquals("OpenCL (shared cache)", report.backendName());
        assertEquals("SHARED", report.cacheMode());
        assertEquals("Shared Mock GPU", report.deviceLabel());
        assertEquals("Vendor X", report.vendor());
        assertEquals(0L, report.statistics().invocationCount());
        assertEquals(0L, report.statistics().compileCount());
    }
}
