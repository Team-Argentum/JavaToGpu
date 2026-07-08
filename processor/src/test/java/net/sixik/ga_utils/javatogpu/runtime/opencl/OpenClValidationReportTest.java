package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                        "passed (perlin=passed, packedBlob=passed)",
                        "passed (reviewReady=true, sourceSelection=irgpu)",
                        "blocked (reviewReady=false, sourceParityMatched=false, runtimeEquivalencePassed=false)",
                        "not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired)"
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
                        "not recorded",
                        "not recorded",
                        "not recorded",
                        "not-promoted"
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
                        "not recorded",
                        "not recorded",
                        "blocked (reviewReady=false, sourceParityMatched=false, runtimeEquivalencePassed=false)",
                        "not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired)"
                )
        );

        OpenClValidationHistoryIO.writeMarkdown(historyMarkdownFile, entries);
        String markdown = java.nio.file.Files.readString(historyMarkdownFile);

        assertTrue(markdown.contains("compileOnlyTest=passed, performanceStressTest=failed"));
        assertTrue(markdown.contains("blocked (reviewReady=false, sourceParityMatched=false, runtimeEquivalencePassed=false)"));
        assertTrue(markdown.contains("not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired)"));
        assertTrue(markdown.contains("| nvidia |"));
    }

    @Test
    void validationHistoryMarkdownKeepsRuntimeEquivalenceAndStressArtifactsVisible() throws Exception {
        java.nio.file.Path historyMarkdownFile = java.nio.file.Files.createTempFile("javatogpu-opencl-history-artifacts", ".md");
        String bucketSummary = "openClWorkloadValidationTest=passed, openClLongRunningStabilityTest=passed, benchmarkTest=passed";
        String workloadSummary = "passed (perlin=passed, packedBlob=passed, packedNumeric=passed, packedGrid3d=passed, image=passed)";
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
                        workloadSummary,
                        "not recorded",
                        "blocked (reviewReady=false, sourceParityMatched=false, runtimeEquivalencePassed=false)",
                        "not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired)"
                )
        );

        OpenClValidationHistoryIO.writeMarkdown(historyMarkdownFile, entries);
        String markdown = java.nio.file.Files.readString(historyMarkdownFile);

        assertTrue(markdown.contains(bucketSummary));
        assertTrue(markdown.contains("| passed | " + workloadSummary + " | not recorded | blocked (reviewReady=false, sourceParityMatched=false, runtimeEquivalencePassed=false) | not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired) |"));
        assertTrue(markdown.contains("openClLongRunningStabilityTest=passed"));
        assertTrue(markdown.contains("benchmarkTest=passed"));
    }

    @Test
    void validationHistoryMarkdownKeepsNvidiaOperationalEvidenceTogether() throws Exception {
        java.nio.file.Path historyMarkdownFile = java.nio.file.Files.createTempFile("javatogpu-opencl-nvidia-operational", ".md");
        String bucketSummary = String.join(", ",
                "openClVendorValidation=passed",
                "integrationOpenClSmokeTest=passed",
                "openClWorkloadValidationTest=passed",
                "openClLongRunningStabilityTest=passed",
                "benchmarkTest=passed"
        );
        String workloadSummary = "passed (perlin=passed, packedBlob=passed, packedNumeric=passed, packedGrid3d=passed, image=passed)";
        java.util.List<OpenClValidationHistoryEntry> entries = java.util.List.of(
                new OpenClValidationHistoryEntry(
                        Instant.parse("2026-07-03T10:00:00Z"),
                        "NVIDIA",
                        "OpenCL",
                        "Mock RTX",
                        "NVIDIA Corporation",
                        "595.97",
                        "OpenCL 3.0 CUDA",
                        bucketSummary,
                        "passed",
                        workloadSummary,
                        "not recorded",
                        "blocked (reviewReady=false, sourceParityMatched=false, runtimeEquivalencePassed=false)",
                        "not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired)"
                )
        );

        OpenClValidationHistoryIO.writeMarkdown(historyMarkdownFile, entries);
        String markdown = java.nio.file.Files.readString(historyMarkdownFile);

        assertTrue(markdown.contains("openClVendorValidation=passed"));
        assertTrue(markdown.contains("integrationOpenClSmokeTest=passed"));
        assertTrue(markdown.contains("openClWorkloadValidationTest=passed"));
        assertTrue(markdown.contains("openClLongRunningStabilityTest=passed"));
        assertTrue(markdown.contains("benchmarkTest=passed"));
        assertTrue(markdown.contains("| passed | " + workloadSummary + " | not recorded | blocked (reviewReady=false, sourceParityMatched=false, runtimeEquivalencePassed=false) | not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired) |"));
        assertTrue(!markdown.toLowerCase(java.util.Locale.ROOT).contains("c2" + "me"));
    }

    @Test
    void validationHistoryMarkdownKeepsPeriodicNvidiaEvidenceAsInterimOnly() throws Exception {
        java.nio.file.Path historyMarkdownFile = java.nio.file.Files.createTempFile(
                "javatogpu-opencl-periodic-nvidia", ".md");
        String bucketSummary = String.join(", ",
                "benchmarkTest=passed",
                "performanceStressTest=passed",
                "openClLongRunningStabilityTest=passed",
                "openClWorkloadValidationTest=passed",
                "openClValidationReport=passed"
        );
        String workloadSummary = "passed (perlin=passed, packedBlob=passed, packedNumeric=passed, packedGrid3d=passed, image=passed)";
        java.util.List<OpenClValidationHistoryEntry> entries = java.util.List.of(
                new OpenClValidationHistoryEntry(
                        Instant.parse("2026-07-06T08:26:18Z"),
                        "NVIDIA",
                        "OpenCL",
                        "NVIDIA CUDA / NVIDIA GeForce RTX 5070",
                        "NVIDIA Corporation",
                        "595.97",
                        "OpenCL 3.0 CUDA",
                        bucketSummary,
                        "passed",
                        workloadSummary,
                        "not recorded",
                        "blocked (reviewReady=false, sourceParityMatched=false, runtimeEquivalencePassed=false)",
                        "not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired)"
                )
        );

        OpenClValidationHistoryIO.writeMarkdown(historyMarkdownFile, entries);
        String markdown = java.nio.file.Files.readString(historyMarkdownFile);

        assertTrue(markdown.contains("2026-07-06T08:26:18Z"));
        assertTrue(markdown.contains("benchmarkTest=passed"));
        assertTrue(markdown.contains("performanceStressTest=passed"));
        assertTrue(markdown.contains("openClLongRunningStabilityTest=passed"));
        assertTrue(markdown.contains("openClWorkloadValidationTest=passed"));
        assertTrue(markdown.contains("NVIDIA CUDA / NVIDIA GeForce RTX 5070"));
        assertTrue(markdown.contains("| passed | " + workloadSummary + " | not recorded | blocked (reviewReady=false, sourceParityMatched=false, runtimeEquivalencePassed=false) | not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired) |"));
        assertTrue(!markdown.toLowerCase(java.util.Locale.ROOT).contains("cross-vendor proven"));
        assertTrue(!markdown.toLowerCase(java.util.Locale.ROOT).contains("amd=passed"));
        assertTrue(!markdown.toLowerCase(java.util.Locale.ROOT).contains("intel=passed"));
    }

    @Test
    void backendSourcePromotionGateFallbackArtifactIsVisibleToReporter() throws Exception {
        java.nio.file.Path gateFile = java.nio.file.Files.createTempFile("javatogpu-backend-source-promotion", ".properties");
        java.nio.file.Path workloadGateFile = java.nio.file.Files.createTempFile("javatogpu-backend-source-promotion-workload", ".properties");
        java.nio.file.Files.deleteIfExists(gateFile);
        java.nio.file.Files.deleteIfExists(workloadGateFile);
        String previousGateFile = System.getProperty("javatogpu.opencl.backendSourcePromotionGateFile");
        String previousWorkloadGateFile = System.getProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile");
        String previousReportFile = System.getProperty("javatogpu.opencl.validationReportFile");
        try {
            System.setProperty("javatogpu.opencl.backendSourcePromotionGateFile", gateFile.toString());
            System.setProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", workloadGateFile.toString());
            java.nio.file.Path reportFile = java.nio.file.Files.createTempFile("javatogpu-opencl-report", ".md");
            System.setProperty("javatogpu.opencl.validationReportFile", reportFile.toString());

            OpenClValidationReporter.main(new String[0]);

            String gateProperties = java.nio.file.Files.readString(gateFile);
            String workloadGateProperties = java.nio.file.Files.readString(workloadGateFile);
            String reportMarkdown = java.nio.file.Files.readString(reportFile);
            assertTrue(gateProperties.contains("status=blocked"));
            assertTrue(gateProperties.contains("reviewReady=false"));
            assertTrue(gateProperties.contains("sourceParityMatched=false"));
            assertTrue(gateProperties.contains("runtimeEquivalencePassed=false"));
            assertTrue(workloadGateProperties.contains("status=blocked"));
            assertTrue(workloadGateProperties.contains("reviewReady=false"));
            assertTrue(workloadGateProperties.contains("sourceParityMatched=false"));
            assertTrue(workloadGateProperties.contains("runtimeEquivalencePassed=false"));
            assertTrue(workloadGateProperties.contains("real workload source-promotion runtime-equivalence evidence is not wired yet"));
            assertTrue(reportMarkdown.contains("## Backend Source Promotion Contract Fixture"));
            assertTrue(reportMarkdown.contains("- Status: `blocked`"));
            assertTrue(reportMarkdown.contains("- Review ready: `false`"));
            assertTrue(reportMarkdown.contains("## Backend Source Promotion Workload Gate"));
            assertTrue(reportMarkdown.contains("- Status: `blocked`"));
            assertTrue(reportMarkdown.contains("- Scope: `real workload promotion gate; fail-closed until workload evidence is wired`"));
            assertTrue(reportMarkdown.contains("- Production source switching: `disabled`"));
        } finally {
            restoreProperty("javatogpu.opencl.backendSourcePromotionGateFile", previousGateFile);
            restoreProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", previousWorkloadGateFile);
            restoreProperty("javatogpu.opencl.validationReportFile", previousReportFile);
        }
    }

    @Test
    void validationHistorySummarizesRealWorkloadGateAsNotPromotedEvenWhenArtifactIsBlocked() throws Exception {
        java.nio.file.Path gateFile = java.nio.file.Files.createTempFile("javatogpu-backend-source-promotion-contract", ".properties");
        java.nio.file.Path workloadGateFile = java.nio.file.Files.createTempFile("javatogpu-backend-source-promotion-workload-history", ".properties");
        java.nio.file.Path historyFile = java.nio.file.Files.createTempFile("javatogpu-opencl-history-with-workload-gate", ".properties");
        java.nio.file.Files.deleteIfExists(gateFile);
        java.nio.file.Files.deleteIfExists(workloadGateFile);
        java.nio.file.Files.deleteIfExists(historyFile);
        String previousGateFile = System.getProperty("javatogpu.opencl.backendSourcePromotionGateFile");
        String previousWorkloadGateFile = System.getProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile");
        String previousHistoryFile = System.getProperty("javatogpu.opencl.validationHistoryFile");
        try {
            System.setProperty("javatogpu.opencl.backendSourcePromotionGateFile", gateFile.toString());
            System.setProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", workloadGateFile.toString());
            System.setProperty("javatogpu.opencl.validationHistoryFile", historyFile.toString());

            OpenClValidationReporter.main(new String[0]);

            java.util.List<OpenClValidationHistoryEntry> entries = OpenClValidationHistoryIO.readAll(historyFile);
            assertEquals(1, entries.size());
            assertTrue(entries.get(0).backendSourcePromotionContractStatus().contains("blocked"));
            assertTrue(entries.get(0).backendSourcePromotionWorkloadStatus().contains("not-promoted"));
            assertTrue(entries.get(0).backendSourcePromotionWorkloadStatus().contains("gateStatus=blocked"));
            assertTrue(entries.get(0).backendSourcePromotionWorkloadStatus().contains("productionSourceSwitching=disabled"));
        } finally {
            restoreProperty("javatogpu.opencl.backendSourcePromotionGateFile", previousGateFile);
            restoreProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", previousWorkloadGateFile);
            restoreProperty("javatogpu.opencl.validationHistoryFile", previousHistoryFile);
        }
    }

    @Test
    void validationReportAndHistoryExposeIrGpuSourceReviewArtifact() throws Exception {
        java.nio.file.Path reviewFile = java.nio.file.Files.createTempFile("javatogpu-irgpu-source-review", ".properties");
        java.nio.file.Path reportFile = java.nio.file.Files.createTempFile("javatogpu-opencl-report-irgpu-review", ".md");
        java.nio.file.Path historyFile = java.nio.file.Files.createTempFile("javatogpu-opencl-history-irgpu-review", ".properties");
        java.nio.file.Files.deleteIfExists(historyFile);
        java.nio.file.Files.writeString(reviewFile, String.join("\n",
                "status=passed",
                "reviewReady=true",
                "scope=opt-in-irgpu-source-review",
                "productionSourceSwitching=false",
                "sourceSelection=irgpu",
                "optimizationProfile=source-reconstruction-review",
                "kernel.count=1",
                "kernel.0.name=gpu_irgpu_entry",
                "kernel.0.resource=inline://integration/simple-irgpu-source-kernel.cl",
                "kernel.0.irGpuResource=javatogpu/runtime/opencl/integration/simple-irgpu-source-kernel.irgpu.properties",
                "kernel.0.status=passed",
                "diagnostic.0=opt-in IrGpu source review lane does not alter production workload gate",
                ""
        ));
        String previousReviewFile = System.getProperty("javatogpu.opencl.irGpuSourceReviewFile");
        String previousReportFile = System.getProperty("javatogpu.opencl.validationReportFile");
        String previousHistoryFile = System.getProperty("javatogpu.opencl.validationHistoryFile");
        try {
            System.setProperty("javatogpu.opencl.irGpuSourceReviewFile", reviewFile.toString());
            System.setProperty("javatogpu.opencl.validationReportFile", reportFile.toString());
            System.setProperty("javatogpu.opencl.validationHistoryFile", historyFile.toString());

            OpenClValidationReporter.main(new String[0]);

            String reportMarkdown = java.nio.file.Files.readString(reportFile);
            java.util.List<OpenClValidationHistoryEntry> entries = OpenClValidationHistoryIO.readAll(historyFile);
            assertTrue(reportMarkdown.contains("## IrGpu Source Review"));
            assertTrue(reportMarkdown.contains("- Status: `passed`"));
            assertTrue(reportMarkdown.contains("- Source selection: `irgpu`"));
            assertTrue(reportMarkdown.contains("- Optimization profile: `source-reconstruction-review`"));
            assertTrue(reportMarkdown.contains("- Production source switching: `false`"));
            assertTrue(reportMarkdown.contains("- Kernel `0`: name=`gpu_irgpu_entry`, status=`passed`, resource=`inline://integration/simple-irgpu-source-kernel.cl`, irGpuResource=`javatogpu/runtime/opencl/integration/simple-irgpu-source-kernel.irgpu.properties`"));
            assertEquals(1, entries.size());
            assertTrue(entries.get(0).irGpuSourceReviewStatus().contains("passed"));
            assertTrue(entries.get(0).irGpuSourceReviewStatus().contains("sourceSelection=irgpu"));
            assertTrue(entries.get(0).irGpuSourceReviewStatus().contains("kernelCount=1"));
            assertTrue(entries.get(0).irGpuSourceReviewStatus().contains("kernel.0=inline://integration/simple-irgpu-source-kernel.cl[status=passed"));
        } finally {
            restoreProperty("javatogpu.opencl.irGpuSourceReviewFile", previousReviewFile);
            restoreProperty("javatogpu.opencl.validationReportFile", previousReportFile);
            restoreProperty("javatogpu.opencl.validationHistoryFile", previousHistoryFile);
        }
    }

    @Test
    void validationReportKeepsUnexpectedWorkloadReviewReadyGateBlocked() throws Exception {
        java.nio.file.Path workloadGateFile = java.nio.file.Files.createTempFile(
                "javatogpu-backend-source-promotion-workload-review-ready", ".properties");
        java.nio.file.Path reportFile = java.nio.file.Files.createTempFile("javatogpu-opencl-report-workload-review-ready", ".md");
        java.nio.file.Files.writeString(workloadGateFile, String.join("\n",
                "status=review-ready",
                "reviewReady=true",
                "sourceParityMatched=true",
                "runtimeEquivalencePassed=true",
                "reason=synthetic accidental workload review-ready fixture",
                ""
        ));
        String previousWorkloadGateFile = System.getProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile");
        String previousReportFile = System.getProperty("javatogpu.opencl.validationReportFile");
        try {
            System.setProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", workloadGateFile.toString());
            System.setProperty("javatogpu.opencl.validationReportFile", reportFile.toString());

            OpenClValidationReporter.main(new String[0]);

            String reportMarkdown = java.nio.file.Files.readString(reportFile);
            assertTrue(reportMarkdown.contains("## Backend Source Promotion Workload Gate"));
            assertTrue(reportMarkdown.contains("- Status: `blocked`"));
            assertTrue(reportMarkdown.contains("- Review ready: `false`"));
            assertTrue(reportMarkdown.contains("- Gate status: `review-ready`"));
            assertTrue(reportMarkdown.contains("- Production source switching: `disabled`"));
            assertTrue(reportMarkdown.contains("synthetic accidental workload review-ready fixture"));
        } finally {
            restoreProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", previousWorkloadGateFile);
            restoreProperty("javatogpu.opencl.validationReportFile", previousReportFile);
        }
    }

    @Test
    void validationReportAndHistoryExposeRuntimeSnapshotWorkloadGateEvidence() throws Exception {
        java.nio.file.Path workloadGateFile = java.nio.file.Files.createTempFile(
                "javatogpu-backend-source-promotion-workload-runtime-snapshot", ".properties");
        java.nio.file.Path reportFile = java.nio.file.Files.createTempFile("javatogpu-opencl-report-workload-runtime-snapshot", ".md");
        java.nio.file.Path historyFile = java.nio.file.Files.createTempFile("javatogpu-opencl-history-workload-runtime-snapshot", ".properties");
        java.nio.file.Path i3SummaryFile = java.nio.file.Files.createTempFile("javatogpu-i3-readiness-workload-summary", ".properties");
        java.nio.file.Files.deleteIfExists(historyFile);
        java.nio.file.Files.deleteIfExists(i3SummaryFile);
        java.nio.file.Files.writeString(workloadGateFile, String.join("\n",
                "status=blocked",
                "reviewReady=false",
                "sourceParityMatched=false",
                "runtimeEquivalencePassed=false",
                "realWorkloadEvidence=runtime-snapshot",
                "sourceSwitching.count=2",
                "blockerFamily.count=3",
                "blockerFamily.0.name=reconstruction",
                "blockerFamily.0.count=1",
                "blockerFamily.1.name=runtime-equivalence",
                "blockerFamily.1.count=1",
                "blockerFamily.2.name=source-parity",
                "blockerFamily.2.count=1",
                "kernel.count=2",
                "kernel.0.sourceKernelResource=inline://integration/image-kernel.cl",
                "kernel.0.status=blocked",
                "kernel.0.sourceParityMatched=false",
                "kernel.0.runtimeEquivalencePassed=false",
                "kernel.0.sourceSwitching.status=review-ready",
                "kernel.0.sourceSwitching.decision=compile-irgpu-source-review",
                "kernel.0.sourceSwitching.optimizationProfile=source-reconstruction-review",
                "kernel.0.sourceSwitching.productionProfileRequested=false",
                "kernel.0.sourceSwitching.sourceSelection=irgpu",
                "kernel.0.sourceSwitching.irGpuSourceRequested=true",
                "kernel.0.sourceSwitching.productionSourceSwitching=disabled",
                "kernel.0.sourceSwitching.productionSourceSwitchingEnabled=false",
                "kernel.0.sourceSwitching.diagnostic.count=1",
                "kernel.0.sourceSwitching.diagnostic.0=IrGpu source was explicitly selected for review or smoke validation",
                "kernel.0.runtimeIrHandoff.status=selected",
                "kernel.0.runtimeIrHandoff.selectedStage=optimized",
                "kernel.0.runtimeIrHandoff.optimizedDiffersFromOriginal=true",
                "kernel.0.runtimeIrHandoff.optimizationRequiresRollback=false",
                "kernel.0.runtimeIrHandoff.fallbackDecision=none",
                "kernel.0.runtimeIrHandoff.optimizedIrRejected=false",
                "kernel.0.runtimeIrHandoff.diagnostic.count=1",
                "kernel.0.runtimeIrHandoff.diagnostic.0=optimized IrGpu is selected for backend lowering after runtime optimizer passes",
                "kernel.0.runtimeProductionMutationSafety.status=disabled",
                "kernel.0.runtimeProductionMutationSafety.productionMutationEnabled=false",
                "kernel.0.runtimeProductionMutationSafety.productionGateStatus=not-requested",
                "kernel.0.runtimeProductionMutationSafety.productionProfileRequested=false",
                "kernel.0.runtimeProductionMutationSafety.selectedStage=optimized",
                "kernel.0.runtimeProductionMutationSafety.diagnostic.count=1",
                "kernel.0.runtimeProductionMutationSafety.diagnostic.0=runtime IR participates in diagnostics, but production mutation is disabled because no production profile was requested",
                "kernel.0.i3Readiness.status=review-ready",
                "kernel.0.i3Readiness.selectedRuntimeIrStage=optimized",
                "kernel.0.i3Readiness.sourcePromotionStatus=review-ready",
                "kernel.0.i3Readiness.sourcePromotionReviewReady=true",
                "kernel.0.i3Readiness.optimizerProductionGateStatus=not-requested",
                "kernel.0.i3Readiness.productionMutationEnabled=false",
                "kernel.0.i3Readiness.diagnostic.count=1",
                "kernel.0.i3Readiness.diagnostic.0=I3 source pipeline is review-ready, but production mutation remains disabled until production gates are accepted",
                "kernel.0.reconstruction.blocker.count=1",
                "kernel.0.reconstruction.blocker.0=irgpu-artifact-missing",
                "kernel.0.reconstruction.diagnostic.count=1",
                "kernel.0.reconstruction.diagnostic.0=OpenCL reconstruction preview skipped because no IrGpu artifact was available",
                "kernel.0.diagnostic.count=2",
                "kernel.0.diagnostic.0=backend source must be reconstructed from IrGpu before promotion review",
                "kernel.0.diagnostic.1=runtime equivalence must execute and pass before backend source promotion",
                "kernel.0.blockerFamily.count=2",
                "kernel.0.blockerFamily.0.name=reconstruction",
                "kernel.0.blockerFamily.0.count=1",
                "kernel.0.blockerFamily.1.name=runtime-equivalence",
                "kernel.0.blockerFamily.1.count=1",
                "kernel.1.sourceKernelResource=inline://integration/perlin-kernel.cl",
                "kernel.1.status=blocked",
                "kernel.1.sourceParityMatched=false",
                "kernel.1.runtimeEquivalencePassed=false",
                "kernel.1.sourceSwitching.status=blocked",
                "kernel.1.sourceSwitching.decision=reject-production-irgpu-source",
                "kernel.1.sourceSwitching.optimizationProfile=vendor-tuned",
                "kernel.1.sourceSwitching.productionProfileRequested=true",
                "kernel.1.sourceSwitching.sourceSelection=irgpu",
                "kernel.1.sourceSwitching.irGpuSourceRequested=true",
                "kernel.1.sourceSwitching.productionSourceSwitching=disabled",
                "kernel.1.sourceSwitching.productionSourceSwitchingEnabled=false",
                "kernel.1.sourceSwitching.diagnostic.count=1",
                "kernel.1.sourceSwitching.diagnostic.0=production-like profile requested IrGpu source but opencl.productionSourceSwitching is disabled",
                "kernel.1.runtimeIrHandoff.status=selected",
                "kernel.1.runtimeIrHandoff.selectedStage=original",
                "kernel.1.runtimeIrHandoff.optimizedDiffersFromOriginal=false",
                "kernel.1.runtimeIrHandoff.optimizationRequiresRollback=true",
                "kernel.1.runtimeIrHandoff.fallbackDecision=optimizer-rollback",
                "kernel.1.runtimeIrHandoff.optimizedIrRejected=true",
                "kernel.1.runtimeIrHandoff.diagnostic.count=1",
                "kernel.1.runtimeIrHandoff.diagnostic.0=optimized IrGpu was rejected; original IrGpu remains selected for backend lowering",
                "kernel.1.runtimeProductionMutationSafety.status=disabled",
                "kernel.1.runtimeProductionMutationSafety.productionMutationEnabled=false",
                "kernel.1.runtimeProductionMutationSafety.productionGateStatus=blocked",
                "kernel.1.runtimeProductionMutationSafety.productionProfileRequested=true",
                "kernel.1.runtimeProductionMutationSafety.selectedStage=original",
                "kernel.1.runtimeProductionMutationSafety.diagnostic.count=1",
                "kernel.1.runtimeProductionMutationSafety.diagnostic.0=runtime IR participates in diagnostics, but production mutation remains fail-closed until production optimizer gates pass",
                "kernel.1.i3Readiness.status=blocked",
                "kernel.1.i3Readiness.selectedRuntimeIrStage=original",
                "kernel.1.i3Readiness.sourcePromotionStatus=blocked",
                "kernel.1.i3Readiness.sourcePromotionReviewReady=false",
                "kernel.1.i3Readiness.optimizerProductionGateStatus=blocked",
                "kernel.1.i3Readiness.productionMutationEnabled=false",
                "kernel.1.i3Readiness.diagnostic.count=1",
                "kernel.1.i3Readiness.diagnostic.0=I3 pipeline is active for diagnostics, but source promotion or production mutation is still blocked",
                "kernel.1.diagnostic.count=1",
                "kernel.1.diagnostic.0=reconstructed source must match descriptor source before promotion review",
                "kernel.1.blockerFamily.count=1",
                "kernel.1.blockerFamily.0.name=source-parity",
                "kernel.1.blockerFamily.0.count=1",
                "reason=real workload runtime snapshot captured before source promotion",
                ""
        ));
        String previousWorkloadGateFile = System.getProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile");
        String previousReportFile = System.getProperty("javatogpu.opencl.validationReportFile");
        String previousHistoryFile = System.getProperty("javatogpu.opencl.validationHistoryFile");
        String previousI3SummaryFile = System.getProperty("javatogpu.opencl.i3ReadinessWorkloadSummaryFile");
        try {
            System.setProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", workloadGateFile.toString());
            System.setProperty("javatogpu.opencl.validationReportFile", reportFile.toString());
            System.setProperty("javatogpu.opencl.validationHistoryFile", historyFile.toString());
            System.setProperty("javatogpu.opencl.i3ReadinessWorkloadSummaryFile", i3SummaryFile.toString());

            OpenClValidationReporter.main(new String[0]);

            String reportMarkdown = java.nio.file.Files.readString(reportFile);
            String i3Summary = java.nio.file.Files.readString(i3SummaryFile);
            java.util.List<OpenClValidationHistoryEntry> entries = OpenClValidationHistoryIO.readAll(historyFile);
            assertTrue(reportMarkdown.contains("- Real workload evidence: `runtime-snapshot`"));
            assertTrue(reportMarkdown.contains("- Source switching decisions: `compile-irgpu-source-review=1, reject-production-irgpu-source=1`"));
            assertTrue(reportMarkdown.contains("- Blocker families: `reconstruction=1, runtime-equivalence=1, source-parity=1`"));
            assertTrue(reportMarkdown.contains("- Kernel evidence count: `2`"));
            assertTrue(reportMarkdown.contains("- Kernel `0`: `inline://integration/image-kernel.cl`, status=`blocked`, parity=`false`, runtimeEquivalence=`false`, sourceSwitching=`compile-irgpu-source-review`, runtimeIr=`optimized`, productionMutation=`false`, i3=`review-ready`"));
            assertTrue(reportMarkdown.contains("- Kernel `0` source switching: status=`review-ready`, profile=`source-reconstruction-review`, first=`IrGpu source was explicitly selected for review or smoke validation`"));
            assertTrue(reportMarkdown.contains("- Kernel `0` runtime IR handoff: stage=`optimized`, transformed=`true`, rollback=`false`, rejected=`false`, first=`optimized IrGpu is selected for backend lowering after runtime optimizer passes`"));
            assertTrue(reportMarkdown.contains("- Kernel `0` production mutation safety: enabled=`false`, gate=`not-requested`, profileRequested=`false`, first=`runtime IR participates in diagnostics, but production mutation is disabled because no production profile was requested`"));
            assertTrue(reportMarkdown.contains("- Kernel `0` I3 readiness: status=`review-ready`, sourcePromotion=`review-ready`, optimizerGate=`not-requested`, productionMutation=`false`, first=`I3 source pipeline is review-ready, but production mutation remains disabled until production gates are accepted`"));
            assertTrue(reportMarkdown.contains("- Kernel `0` diagnostics: `2`; first=`backend source must be reconstructed from IrGpu before promotion review`"));
            assertTrue(reportMarkdown.contains("- Kernel `0` reconstruction blockers: `1`; first=`irgpu-artifact-missing`"));
            assertTrue(reportMarkdown.contains("- Kernel blocker families: `reconstruction=1, runtime-equivalence=1`"));
            assertTrue(reportMarkdown.contains("- Kernel `1`: `inline://integration/perlin-kernel.cl`, status=`blocked`, parity=`false`, runtimeEquivalence=`false`, sourceSwitching=`reject-production-irgpu-source`, runtimeIr=`original`, productionMutation=`false`, i3=`blocked`"));
            assertTrue(reportMarkdown.contains("- Kernel `1` source switching: status=`blocked`, profile=`vendor-tuned`, first=`production-like profile requested IrGpu source but opencl.productionSourceSwitching is disabled`"));
            assertTrue(reportMarkdown.contains("- Kernel `1` runtime IR handoff: stage=`original`, transformed=`false`, rollback=`true`, rejected=`true`, first=`optimized IrGpu was rejected; original IrGpu remains selected for backend lowering`"));
            assertTrue(reportMarkdown.contains("- Kernel `1` production mutation safety: enabled=`false`, gate=`blocked`, profileRequested=`true`, first=`runtime IR participates in diagnostics, but production mutation remains fail-closed until production optimizer gates pass`"));
            assertTrue(reportMarkdown.contains("- Kernel `1` I3 readiness: status=`blocked`, sourcePromotion=`blocked`, optimizerGate=`blocked`, productionMutation=`false`, first=`I3 pipeline is active for diagnostics, but source promotion or production mutation is still blocked`"));
            assertTrue(reportMarkdown.contains("- Kernel `1` diagnostics: `1`; first=`reconstructed source must match descriptor source before promotion review`"));
            assertTrue(reportMarkdown.contains("- Kernel blocker families: `source-parity=1`"));
            assertFalse(reportMarkdown.contains("runtimeIr=`unknown`"));
            assertFalse(reportMarkdown.contains("i3=`unknown`"));
            assertTrue(reportMarkdown.contains("real workload runtime snapshot captured before source promotion"));
            assertEquals(1, entries.size());
            assertTrue(entries.get(0).backendSourcePromotionWorkloadStatus().contains("realWorkloadEvidence=runtime-snapshot"));
            assertTrue(entries.get(0).backendSourcePromotionWorkloadStatus().contains("sourceSwitching=compile-irgpu-source-review=1, reject-production-irgpu-source=1"));
            assertTrue(entries.get(0).backendSourcePromotionWorkloadStatus().contains("kernelCount=2"));
            assertTrue(entries.get(0).backendSourcePromotionWorkloadStatus().contains("kernel.0=inline://integration/image-kernel.cl[diagnostics=2, sourceSwitching=compile-irgpu-source-review, runtimeIr=optimized, productionMutation=false, i3=review-ready, families=reconstruction=1, runtime-equivalence=1]"));
            assertTrue(entries.get(0).backendSourcePromotionWorkloadStatus().contains("kernel.1=inline://integration/perlin-kernel.cl[diagnostics=1, sourceSwitching=reject-production-irgpu-source, runtimeIr=original, productionMutation=false, i3=blocked, families=source-parity=1]"));
            assertTrue(entries.get(0).backendSourcePromotionWorkloadStatus().contains("productionSourceSwitching=disabled"));
            assertTrue(i3Summary.contains("status=blocked"));
            assertTrue(i3Summary.contains("kernel.count=2"));
            assertTrue(i3Summary.contains("reviewReady.count=1"));
            assertTrue(i3Summary.contains("blocked.count=1"));
            assertTrue(i3Summary.contains("productionMutationEnabled=false"));
            assertTrue(i3Summary.contains("status.0.name=review-ready"));
            assertTrue(i3Summary.contains("status.1.name=blocked"));
            assertTrue(i3Summary.contains("kernel.0.i3Status=review-ready"));
            assertTrue(i3Summary.contains("kernel.0.runtimeIr=optimized"));
            assertTrue(i3Summary.contains("kernel.1.i3Status=blocked"));
            assertTrue(i3Summary.contains("kernel.1.runtimeIr=original"));
            assertFalse(i3Summary.contains("i3Status=unknown"));
            assertFalse(i3Summary.contains("runtimeIr=unknown"));
            assertTrue(i3Summary.contains("diagnostic.0=I3 workload readiness remains blocked: reviewReady=1, blocked=1"));
        } finally {
            restoreProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", previousWorkloadGateFile);
            restoreProperty("javatogpu.opencl.validationReportFile", previousReportFile);
            restoreProperty("javatogpu.opencl.validationHistoryFile", previousHistoryFile);
            restoreProperty("javatogpu.opencl.i3ReadinessWorkloadSummaryFile", previousI3SummaryFile);
        }
    }

    @Test
    void backendSourcePromotionGateReporterWritesSyntheticDumpContractArtifact() throws Exception {
        java.nio.file.Path gateFile = java.nio.file.Files.createTempFile("javatogpu-backend-source-promotion-real", ".properties");
        java.nio.file.Files.deleteIfExists(gateFile);
        String previousGateFile = System.getProperty("javatogpu.opencl.backendSourcePromotionGateFile");
        try {
            System.setProperty("javatogpu.opencl.backendSourcePromotionGateFile", gateFile.toString());

            OpenClBackendSourcePromotionGateReporter.main(new String[0]);

            String gateProperties = java.nio.file.Files.readString(gateFile);
            assertTrue(gateProperties.contains("status=review-ready"));
            assertTrue(gateProperties.contains("reviewReady=true"));
            assertTrue(gateProperties.contains("reconstructed=true"));
            assertTrue(gateProperties.contains("sourceAvailable=true"));
            assertTrue(gateProperties.contains("sourceParityMatched=true"));
            assertTrue(gateProperties.contains("runtimeEquivalencePassed=true"));
            assertTrue(gateProperties.contains("diagnostic.0=backend source reconstruction is ready for promotion review"));
        } finally {
            restoreProperty("javatogpu.opencl.backendSourcePromotionGateFile", previousGateFile);
        }
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
                "passed",
                "passed"
        );

        OpenClWorkloadValidationSummaryIO.write(summaryFile, summary);
        OpenClWorkloadValidationSummary loaded = OpenClWorkloadValidationSummaryIO.readIfExists(summaryFile).orElseThrow();

        assertEquals(summary, loaded);
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    @Test
    void workloadSummaryPropertiesExposeSyntheticPackedGridArtifact() throws Exception {
        java.nio.file.Path summaryFile = java.nio.file.Files.createTempFile("javatogpu-opencl-workloads-packed-grid", ".properties");
        OpenClWorkloadValidationSummary summary = new OpenClWorkloadValidationSummary(
                Instant.parse("2026-07-01T12:30:00Z"),
                "passed",
                "passed",
                "passed",
                "passed",
                "passed",
                "passed"
        );

        OpenClWorkloadValidationSummaryIO.write(summaryFile, summary);
        String properties = java.nio.file.Files.readString(summaryFile);

        assertTrue(properties.contains("packedGrid3dStatus=passed"));
        assertTrue(!properties.toLowerCase(java.util.Locale.ROOT).contains("c2" + "me"));
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
