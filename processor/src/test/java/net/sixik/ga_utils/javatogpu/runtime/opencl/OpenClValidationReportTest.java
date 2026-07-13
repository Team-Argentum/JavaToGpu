package net.sixik.ga_utils.javatogpu.runtime.opencl;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertTrue(markdown.contains("## Production Promotion Artifacts"));
        assertTrue(markdown.contains("- Complete support: `yes`"));
        assertTrue(markdown.contains("- Supported artifact count: `12`"));
        assertTrue(markdown.contains("- Missing artifacts: `none`"));
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
    void optimizerFamilyHistoryBaselineAcceptsWorkloadRuntimeEquivalenceEvidence() {
        OpenClValidationHistoryEntry entry = new OpenClValidationHistoryEntry(
                Instant.parse("2026-07-01T12:00:00Z"),
                "nvidia",
                "OpenCL",
                "NVIDIA CUDA / Mock GPU",
                "NVIDIA Corporation",
                "595.97",
                "OpenCL 3.0 CUDA",
                "openClWorkloadValidationTest=passed",
                "passed",
                "passed",
                "passed",
                "passed",
                "review-ready (reviewReady=true, sourceParityMatched=true, runtimeEquivalencePassed=true)",
                "blocked (gateStatus=review-ready, reviewReady=true, sourceParityMatched=true, runtimeEquivalencePassed=true, optimizerPromotionReadyFamilies=4)",
                "blocked (contract=valid, decisionMode=review-ready, optimizerPromotionReadyFamilies=4, optimizerPayloadCompleteAll=true)",
                "recorded",
                "recorded"
        );

        assertTrue(OpenClValidationReporter.hasOptimizerFamilyRuntimeEquivalenceHistoryBaseline(entry));
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
                        "passed (reviewReady=true, sourceSelection=irgpu, productionSourceSwitching=enabled, productionDecision=production-enabled, kernelCount=2)",
                        "blocked (reviewReady=false, sourceParityMatched=false, runtimeEquivalencePassed=false)",
                        "not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired)",
                        "blocked (sourceSwitchingAllowed=false, mutationAllowed=false, blockers=3)",
                        "recorded (kernels=5, aligned=1, nonPreferred=1, driverSelected=3, unavailable=0, missing=0, blocking=0)"
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
                        "not recorded",
                        "not-promoted",
                        "not recorded"
                )
        );

        OpenClValidationHistoryIO.writeAll(historyFile, entries);
        java.util.List<OpenClValidationHistoryEntry> loaded = OpenClValidationHistoryIO.readAll(historyFile);

        assertEquals(entries, loaded);
        assertTrue(loaded.get(0).kernelLaunchAdvisoryStatus().contains("nonPreferred=1"));
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
                        "not recorded",
                        "blocked (reviewReady=false, sourceParityMatched=false, runtimeEquivalencePassed=false)",
                        "not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired)",
                        "not recorded"
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
                        "not recorded",
                        "blocked (reviewReady=false, sourceParityMatched=false, runtimeEquivalencePassed=false)",
                        "not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired)",
                        "not recorded"
                )
        );

        OpenClValidationHistoryIO.writeMarkdown(historyMarkdownFile, entries);
        String markdown = java.nio.file.Files.readString(historyMarkdownFile);

        assertTrue(markdown.contains(bucketSummary));
        assertTrue(markdown.contains("| passed | " + workloadSummary + " | not recorded | not recorded | blocked (reviewReady=false, sourceParityMatched=false, runtimeEquivalencePassed=false) | not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired) | not recorded |"));
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
                        "not recorded",
                        "blocked (reviewReady=false, sourceParityMatched=false, runtimeEquivalencePassed=false)",
                        "not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired)",
                        "not recorded"
                )
        );

        OpenClValidationHistoryIO.writeMarkdown(historyMarkdownFile, entries);
        String markdown = java.nio.file.Files.readString(historyMarkdownFile);

        assertTrue(markdown.contains("openClVendorValidation=passed"));
        assertTrue(markdown.contains("integrationOpenClSmokeTest=passed"));
        assertTrue(markdown.contains("openClWorkloadValidationTest=passed"));
        assertTrue(markdown.contains("openClLongRunningStabilityTest=passed"));
        assertTrue(markdown.contains("benchmarkTest=passed"));
        assertTrue(markdown.contains("| passed | " + workloadSummary + " | not recorded | not recorded | blocked (reviewReady=false, sourceParityMatched=false, runtimeEquivalencePassed=false) | not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired) | not recorded |"));
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
                        "not recorded",
                        "blocked (reviewReady=false, sourceParityMatched=false, runtimeEquivalencePassed=false)",
                        "not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired)",
                        "not recorded"
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
        assertTrue(markdown.contains("| passed | " + workloadSummary + " | not recorded | not recorded | blocked (reviewReady=false, sourceParityMatched=false, runtimeEquivalencePassed=false) | not-promoted (productionSourceSwitching=disabled, realWorkloadEvidence=not-wired) | not recorded |"));
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
                "optimizationProfile=" + net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE,
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
            assertTrue(reportMarkdown.contains(
                    "- Optimization profile: `" + net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE + "`"
            ));
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
    void validationReportExposesControlledProductionSourceSwitchingArtifact() throws Exception {
        java.nio.file.Path validationFile = java.nio.file.Files.createTempFile(
                "javatogpu-production-source-switching", ".properties");
        java.nio.file.Path reportFile = java.nio.file.Files.createTempFile(
                "javatogpu-opencl-report-production-source-switching", ".md");
        java.nio.file.Files.writeString(validationFile, String.join("\n",
                "status=passed",
                "reviewReady=true",
                "scope=controlled-production-source-switching-smoke",
                "productionSourceSwitching=enabled",
                "sourceSelection=irgpu",
                "optimizationProfile=vendor-tuned",
                "productionPromotionDecisionMode=production-enabled",
                "kernel.count=7",
                "kernel.0.name=gpu_irgpu_entry",
                "kernel.0.resource=inline://integration/simple-irgpu-source-kernel.cl",
                "kernel.0.irGpuResource=javatogpu/runtime/opencl/integration/simple-irgpu-source-kernel.irgpu.properties",
                "kernel.0.status=passed",
                "kernel.1.name=gpu_image_entry",
                "kernel.1.resource=inline://integration/image-kernel.cl",
                "kernel.1.irGpuResource=javatogpu/runtime/opencl/integration/image-kernel.irgpu.properties",
                "kernel.1.status=passed",
                "kernel.2.name=gpu_dual_buffer_int_entry",
                "kernel.2.resource=inline://integration/dual-buffer-int-kernel.cl",
                "kernel.2.irGpuResource=javatogpu/runtime/opencl/integration/dual-buffer-int-kernel.irgpu.properties",
                "kernel.2.status=passed",
                "kernel.3.name=gpu_kernel",
                "kernel.3.resource=javatogpu/sample/PerlinWorkload/kernel.cl",
                "kernel.3.irGpuResource=javatogpu/sample/PerlinWorkload/kernel.irgpu.properties",
                "kernel.3.status=passed",
                "kernel.4.name=gpu_kernel",
                "kernel.4.resource=javatogpu/sample/PackedBlobWorkload/kernel.cl",
                "kernel.4.irGpuResource=javatogpu/sample/PackedBlobWorkload/kernel.irgpu.properties",
                "kernel.4.status=passed",
                "kernel.5.name=gpu_kernel",
                "kernel.5.resource=javatogpu/sample/PackedNumericWorkload/kernel.cl",
                "kernel.5.irGpuResource=javatogpu/sample/PackedNumericWorkload/kernel.irgpu.properties",
                "kernel.5.status=passed",
                "kernel.6.name=gpu_kernel",
                "kernel.6.resource=javatogpu/sample/Synthetic3DPackedGridWorkload/kernel.cl",
                "kernel.6.irGpuResource=javatogpu/sample/Synthetic3DPackedGridWorkload/kernel.irgpu.properties",
                "kernel.6.status=passed",
                "diagnostic.0=controlled production source-switching lane uses explicit production-enabled evidence only",
                ""
        ));
        String previousValidationFile = System.getProperty("javatogpu.opencl.productionSourceSwitchingValidationFile");
        String previousReportFile = System.getProperty("javatogpu.opencl.validationReportFile");
        try {
            System.setProperty("javatogpu.opencl.productionSourceSwitchingValidationFile", validationFile.toString());
            System.setProperty("javatogpu.opencl.validationReportFile", reportFile.toString());

            OpenClValidationReporter.main(new String[0]);

            String reportMarkdown = java.nio.file.Files.readString(reportFile);
            assertTrue(reportMarkdown.contains("## Controlled Production Source Switching"));
            assertTrue(reportMarkdown.contains("- Status: `passed`"));
            assertTrue(reportMarkdown.contains("- Production source switching: `enabled`"));
            assertTrue(reportMarkdown.contains("- Production decision mode: `production-enabled`"));
            assertTrue(reportMarkdown.contains("- Scope: `controlled-production-source-switching-smoke`"));
            assertTrue(reportMarkdown.contains("- Kernel count: `7`"));
            assertTrue(reportMarkdown.contains("- Kernel `0`: name=`gpu_irgpu_entry`, status=`passed`, resource=`inline://integration/simple-irgpu-source-kernel.cl`, irGpuResource=`javatogpu/runtime/opencl/integration/simple-irgpu-source-kernel.irgpu.properties`"));
            assertTrue(reportMarkdown.contains("- Kernel `1`: name=`gpu_image_entry`, status=`passed`, resource=`inline://integration/image-kernel.cl`, irGpuResource=`javatogpu/runtime/opencl/integration/image-kernel.irgpu.properties`"));
            assertTrue(reportMarkdown.contains("- Kernel `2`: name=`gpu_dual_buffer_int_entry`, status=`passed`, resource=`inline://integration/dual-buffer-int-kernel.cl`, irGpuResource=`javatogpu/runtime/opencl/integration/dual-buffer-int-kernel.irgpu.properties`"));
            assertTrue(reportMarkdown.contains("- Kernel `3`: name=`gpu_kernel`, status=`passed`, resource=`javatogpu/sample/PerlinWorkload/kernel.cl`, irGpuResource=`javatogpu/sample/PerlinWorkload/kernel.irgpu.properties`"));
            assertTrue(reportMarkdown.contains("- Kernel `4`: name=`gpu_kernel`, status=`passed`, resource=`javatogpu/sample/PackedBlobWorkload/kernel.cl`, irGpuResource=`javatogpu/sample/PackedBlobWorkload/kernel.irgpu.properties`"));
            assertTrue(reportMarkdown.contains("- Kernel `5`: name=`gpu_kernel`, status=`passed`, resource=`javatogpu/sample/PackedNumericWorkload/kernel.cl`, irGpuResource=`javatogpu/sample/PackedNumericWorkload/kernel.irgpu.properties`"));
            assertTrue(reportMarkdown.contains("- Kernel `6`: name=`gpu_kernel`, status=`passed`, resource=`javatogpu/sample/Synthetic3DPackedGridWorkload/kernel.cl`, irGpuResource=`javatogpu/sample/Synthetic3DPackedGridWorkload/kernel.irgpu.properties`"));
        } finally {
            restoreProperty("javatogpu.opencl.productionSourceSwitchingValidationFile", previousValidationFile);
            restoreProperty("javatogpu.opencl.validationReportFile", previousReportFile);
        }
    }

    @Test
    void productionExplainabilityRecordsControlledSourceSwitchingSmokeEvidence() throws Exception {
        java.nio.file.Path validationFile = java.nio.file.Files.createTempFile(
                "javatogpu-production-source-switching", ".properties");
        java.nio.file.Path workloadGateFile = java.nio.file.Files.createTempFile(
                "javatogpu-backend-source-promotion-workload", ".properties");
        java.nio.file.Path i3SummaryFile = java.nio.file.Files.createTempFile(
                "javatogpu-i3-readiness-workload-summary", ".properties");
        java.nio.file.Path activationTokenSmokeFile = java.nio.file.Files.createTempFile(
                "javatogpu-production-activation-token-smoke", ".properties");
        java.nio.file.Path activationTokenNegativeFile = java.nio.file.Files.createTempFile(
                "javatogpu-production-activation-token-negative", ".properties");
        java.nio.file.Path explainabilityFile = java.nio.file.Files.createTempFile(
                "javatogpu-production-promotion-explainability", ".properties");
        java.nio.file.Path reportFile = java.nio.file.Files.createTempFile(
                "javatogpu-opencl-report-production-source-switching-explainability", ".md");
        java.nio.file.Files.writeString(validationFile, String.join("\n",
                "status=passed",
                "reviewReady=true",
                "productionSourceSwitching=enabled",
                "productionPromotionDecisionMode=production-enabled",
                "kernel.count=7",
                "kernel.0.resource=inline://integration/image-kernel.cl",
                ""
        ));
        java.nio.file.Files.writeString(workloadGateFile, String.join("\n",
                "status=blocked",
                "reviewReady=false",
                "sourceParityMatched=true",
                "runtimeEquivalencePassed=true",
                "productionSourceSwitching=false",
                "productionPromotionOperatorAccepted.count=0",
                "productionPromotionOperatorAccepted.all=false",
                "kernel.count=1",
                "kernel.0.sourceKernelResource=inline://integration/image-kernel.cl",
                "kernel.0.sourceSwitching.productionPromotionOperatorAccepted=false",
                "realWorkloadEvidence=runtime-snapshot",
                ""
        ));
        java.nio.file.Files.writeString(i3SummaryFile, String.join("\n",
                "reviewReady.count=1",
                "blocked.count=0",
                "sourceReady.count=1",
                "productionMutationEnabled=false",
                ""
        ));
        java.nio.file.Files.writeString(activationTokenSmokeFile, String.join("\n",
                "status=passed",
                "scope=controlled-production-activation-token-smoke",
                "token.loaded=true",
                "token.artifactSha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "token.approvalId=approval:test",
                "token.candidateGitSha=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "token.backendTarget=OPENCL",
                "token.deviceVendor=NVIDIA Corporation",
                "token.deviceLabel=NVIDIA CUDA / Mock GPU",
                "token.driverVersion=1.0",
                "token.activationScope=controlled-opt-in-only",
                "defaultRuntimeActivation=false",
                "defaultProductionSourceSwitching=disabled",
                "productionMutation=disabled",
                "kernel.count=1",
                "kernel.0.resource=inline://integration/image-kernel.cl",
                "kernel.0.status=passed",
                ""
        ));
        java.nio.file.Files.writeString(activationTokenNegativeFile, String.join("\n",
                "status=passed",
                "scope=controlled-production-activation-token-negative",
                "digestMismatchRejected=true",
                "unapprovedKernelRejected=true",
                "outputUnchanged=true",
                "defaultRuntimeActivation=false",
                "defaultProductionSourceSwitching=disabled",
                "productionMutation=disabled",
                "passed=true",
                ""
        ));
        String previousValidationFile = System.getProperty("javatogpu.opencl.productionSourceSwitchingValidationFile");
        String previousActivationTokenSmokeFile = System.getProperty(
                "javatogpu.opencl.productionActivationTokenSmokeFile"
        );
        String previousActivationTokenNegativeFile = System.getProperty(
                "javatogpu.opencl.productionActivationTokenNegativeFile"
        );
        String previousWorkloadGateFile = System.getProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile");
        String previousI3SummaryFile = System.getProperty("javatogpu.opencl.i3ReadinessWorkloadSummaryFile");
        String previousExplainabilityFile = System.getProperty("javatogpu.opencl.productionPromotionExplainabilityFile");
        String previousReportFile = System.getProperty("javatogpu.opencl.validationReportFile");
        try {
            System.setProperty("javatogpu.opencl.productionSourceSwitchingValidationFile", validationFile.toString());
            System.setProperty(
                    "javatogpu.opencl.productionActivationTokenSmokeFile",
                    activationTokenSmokeFile.toString()
            );
            System.setProperty(
                    "javatogpu.opencl.productionActivationTokenNegativeFile",
                    activationTokenNegativeFile.toString()
            );
            System.setProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", workloadGateFile.toString());
            System.setProperty("javatogpu.opencl.i3ReadinessWorkloadSummaryFile", i3SummaryFile.toString());
            System.setProperty("javatogpu.opencl.productionPromotionExplainabilityFile", explainabilityFile.toString());
            System.setProperty("javatogpu.opencl.validationReportFile", reportFile.toString());

            OpenClValidationReporter.main(new String[0]);

            String explainability = java.nio.file.Files.readString(explainabilityFile);
            String reportMarkdown = java.nio.file.Files.readString(reportFile);
            assertTrue(explainability.contains("status=blocked"));
            assertTrue(explainability.contains("controlledProductionSourceSwitching.status=passed"));
            assertTrue(explainability.contains("controlledProductionSourceSwitching.kernel.count=7"));
            assertTrue(explainability.contains("controlledProductionSourceSwitching.realWorkload.covered.count=1"));
            assertTrue(explainability.contains("controlledProductionSourceSwitching.realWorkload.total.count=1"));
            assertTrue(explainability.contains("controlledProductionSourceSwitching.realWorkload.uncovered.count=0"));
            assertTrue(explainability.contains("controlledProductionSourceSwitching.realWorkload.covered.all=true"));
            assertTrue(explainability.contains("controlledProductionActivationTokenSmoke.status=passed"));
            assertTrue(explainability.contains("controlledProductionActivationTokenSmoke.tokenLoaded=true"));
            assertTrue(explainability.contains("controlledProductionActivationTokenSmoke.approvedKernelExecuted=true"));
            assertTrue(explainability.contains("controlledProductionActivationTokenSmoke.realWorkload.covered.count=1"));
            assertTrue(explainability.contains("controlledProductionActivationTokenSmoke.realWorkload.total.count=1"));
            assertTrue(explainability.contains("controlledProductionActivationTokenSmoke.realWorkload.uncovered.count=0"));
            assertTrue(explainability.contains("controlledProductionActivationTokenSmoke.realWorkload.covered.all=true"));
            assertTrue(explainability.contains("controlledProductionActivationTokenSmoke.safeDefaults=true"));
            assertTrue(explainability.contains("controlledProductionActivationTokenSmoke.passed=true"));
            assertTrue(explainability.contains("controlledProductionActivationTokenNegative.status=passed"));
            assertTrue(explainability.contains("controlledProductionActivationTokenNegative.digestMismatchRejected=true"));
            assertTrue(explainability.contains("controlledProductionActivationTokenNegative.unapprovedKernelRejected=true"));
            assertTrue(explainability.contains("controlledProductionActivationTokenNegative.outputUnchanged=true"));
            assertTrue(explainability.contains("controlledProductionActivationTokenNegative.safeDefaults=true"));
            assertTrue(explainability.contains("controlledProductionActivationTokenNegative.passed=true"));
            assertTrue(explainability.contains("readinessChecklist.ready.count=7"));
            assertTrue(explainability.contains("readinessChecklist.blocked.count=4"));
            assertTrue(explainability.contains("readinessChecklist.ready.all=false"));
            assertTrue(explainability.contains("readinessChecklist.firstBlocked=workload-gate-review-ready"));
            assertTrue(explainability.contains("productionSourceSwitchingAllowed=false"));
            assertTrue(reportMarkdown.contains("- Controlled source switching smoke: `passed`"));
            assertTrue(reportMarkdown.contains("- Controlled source switching kernels: `7`"));
            assertTrue(reportMarkdown.contains("- Controlled real workload coverage: `1/1`"));
            assertTrue(reportMarkdown.contains("- Controlled real workload coverage all: `true`"));
            assertTrue(reportMarkdown.contains("- Controlled activation-token smoke: `passed`"));
            assertTrue(reportMarkdown.contains("- Activation token loaded: `true`"));
            assertTrue(reportMarkdown.contains("- Approved activation-token kernel executed: `true`"));
            assertTrue(reportMarkdown.contains("- Activation-token real workload coverage: `1/1`"));
            assertTrue(reportMarkdown.contains("- Activation-token real workload coverage all: `true`"));
            assertTrue(reportMarkdown.contains("- Activation-token safe defaults: `true`"));
            assertTrue(reportMarkdown.contains("- Activation-token negative controls: `passed`"));
            assertTrue(reportMarkdown.contains("- Activation-token digest mismatch rejected: `true`"));
            assertTrue(reportMarkdown.contains("- Activation-token unapproved kernel rejected: `true`"));
            assertTrue(reportMarkdown.contains("- Activation-token rejected output unchanged: `true`"));
            assertTrue(reportMarkdown.contains("- Production promotion operator accepted: `0/1`, all=`false`"));
            assertTrue(reportMarkdown.contains("- Production readiness checklist: `7 ready / 4 blocked`"));
            assertTrue(reportMarkdown.contains("- Production readiness checklist all: `false`"));
            assertTrue(reportMarkdown.contains("- First readiness blocker: `workload-gate-review-ready`"));
            assertTrue(reportMarkdown.contains("- Optimizer families: `0`"));
            assertTrue(reportMarkdown.contains("- Optimizer promotion-ready families: `0`"));
        } finally {
            restoreProperty("javatogpu.opencl.productionSourceSwitchingValidationFile", previousValidationFile);
            restoreProperty(
                    "javatogpu.opencl.productionActivationTokenSmokeFile",
                    previousActivationTokenSmokeFile
            );
            restoreProperty(
                    "javatogpu.opencl.productionActivationTokenNegativeFile",
                    previousActivationTokenNegativeFile
            );
            restoreProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", previousWorkloadGateFile);
            restoreProperty("javatogpu.opencl.i3ReadinessWorkloadSummaryFile", previousI3SummaryFile);
            restoreProperty("javatogpu.opencl.productionPromotionExplainabilityFile", previousExplainabilityFile);
            restoreProperty("javatogpu.opencl.validationReportFile", previousReportFile);
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
    void validationReportIncludesReviewReadyProductionCandidateGate() throws Exception {
        java.nio.file.Path candidateGateFile = java.nio.file.Files.createTempFile(
                "javatogpu-backend-source-promotion-candidate", ".properties");
        java.nio.file.Path reportFile = java.nio.file.Files.createTempFile(
                "javatogpu-opencl-report-production-candidate", ".md");
        java.nio.file.Files.writeString(candidateGateFile, String.join("\n",
                "status=review-ready",
                "reviewReady=true",
                "defaultProductionSourceSwitching=disabled",
                "candidateProductionSourceSwitching=review-ready",
                "productionMutation=disabled",
                "kernel.count=5",
                "candidateReady.count=5",
                "candidateReady.all=true",
                "sourceParityMatched=true",
                "runtimeEquivalencePassed=true",
                "controlledSourceSwitching.status=passed",
                "operatorAcceptance.mode=identity-bound",
                "operatorAcceptance.accepted.count=5",
                "operatorAcceptance.accepted.all=true",
                "operatorAcceptance.bound.count=5",
                "operatorAcceptance.bound.all=true",
                "operatorAcceptance.deviceVendor=NVIDIA Corporation",
                "operatorAcceptance.deviceLabel=NVIDIA CUDA / NVIDIA GeForce RTX 5070",
                "operatorAcceptance.driverVersion=595.97",
                "blocker.count=0",
                "diagnostic=real workload production candidate is review-ready; default production source switching remains disabled",
                ""
        ));
        String previousCandidateGateFile = System.getProperty(
                "javatogpu.opencl.backendSourcePromotionCandidateGateFile");
        String previousReportFile = System.getProperty("javatogpu.opencl.validationReportFile");
        try {
            System.setProperty(
                    "javatogpu.opencl.backendSourcePromotionCandidateGateFile",
                    candidateGateFile.toString()
            );
            System.setProperty("javatogpu.opencl.validationReportFile", reportFile.toString());

            OpenClValidationReporter.main(new String[0]);

            String reportMarkdown = java.nio.file.Files.readString(reportFile);
            assertTrue(reportMarkdown.contains("## Backend Source Promotion Candidate Gate"));
            assertTrue(reportMarkdown.contains("- Status: `review-ready`"));
            assertTrue(reportMarkdown.contains("- Candidate ready: `5/5`, all=`true`"));
            assertTrue(reportMarkdown.contains("- Operator accepted: `5/5`, all=`true`"));
            assertTrue(reportMarkdown.contains("- Operator bound: `5/5`, all=`true`"));
            assertTrue(reportMarkdown.contains("- Device vendor: `NVIDIA Corporation`"));
            assertTrue(reportMarkdown.contains("- Device label: `NVIDIA CUDA / NVIDIA GeForce RTX 5070`"));
            assertTrue(reportMarkdown.contains("- Driver version: `595.97`"));
            assertTrue(reportMarkdown.contains("- Default production source switching: `disabled`"));
            assertTrue(reportMarkdown.contains("- Production mutation: `disabled`"));
        } finally {
            restoreProperty(
                    "javatogpu.opencl.backendSourcePromotionCandidateGateFile",
                    previousCandidateGateFile
            );
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
        java.nio.file.Path productionExplainabilityFile = java.nio.file.Files.createTempFile(
                "javatogpu-production-promotion-explainability", ".properties");
        java.nio.file.Files.deleteIfExists(historyFile);
        java.nio.file.Files.deleteIfExists(i3SummaryFile);
        java.nio.file.Files.deleteIfExists(productionExplainabilityFile);
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
                "kernel.0.status=review-ready",
                "kernel.0.sourceParityMatched=true",
                "kernel.0.runtimeEquivalencePassed=true",
                "kernel.0.sourceSwitching.status=review-ready",
                "kernel.0.sourceSwitching.decision=compile-irgpu-source-review",
                "kernel.0.sourceSwitching.optimizationProfile="
                        + net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE,
                "kernel.0.sourceSwitching.productionProfileRequested=false",
                "kernel.0.sourceSwitching.sourceSelection=irgpu",
                "kernel.0.sourceSwitching.irGpuSourceRequested=true",
                "kernel.0.sourceSwitching.productionSourceSwitching=disabled",
                "kernel.0.sourceSwitching.productionSourceSwitchingEnabled=false",
                "kernel.0.sourceSwitching.sourcePromotionFirstBlocker=none",
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
                "kernel.0.i3Readiness.sourceReady=true",
                "kernel.0.i3Readiness.sourcePromotionStatus=review-ready",
                "kernel.0.i3Readiness.sourcePromotionReviewReady=true",
                "kernel.0.i3Readiness.optimizerProductionGateStatus=not-requested",
                "kernel.0.i3Readiness.productionMutationEnabled=false",
                "kernel.0.i3Readiness.diagnostic.count=1",
                "kernel.0.i3Readiness.diagnostic.0=I3 source pipeline is review-ready, but production mutation remains disabled until production gates are accepted",
                "kernel.0.runtimeOptimizerDrift.status=recorded",
                "kernel.0.runtimeOptimizerDrift.pass.count=2",
                "kernel.0.runtimeOptimizerDrift.pass.applied.count=2",
                "kernel.0.runtimeOptimizerDrift.pass.rolledBack.count=0",
                "kernel.0.runtimeOptimizerDrift.pass.failed.count=0",
                "kernel.0.runtimeOptimizerDrift.fallbackDecision=none",
                "kernel.0.runtimeOptimizerDrift.selectedRuntimeIrStage=optimized",
                "kernel.0.runtimeOptimizerDrift.optimizedIrRejected=false",
                "kernel.0.runtimeOptimizerDrift.productionGateStatus=not-requested",
                "kernel.0.reconstruction.blocker.count=0",
                "kernel.0.reconstruction.diagnostic.count=2",
                "kernel.0.reconstruction.diagnostic.0=OpenCL source assembler emitted entry kernel gpu_image_entry",
                "kernel.0.reconstruction.diagnostic.1=sourceParity.matched=true",
                "kernel.0.diagnostic.count=1",
                "kernel.0.diagnostic.0=packaged IrGpu source reconstructed from runtime artifact loader with descriptor parity",
                "kernel.0.blockerFamily.count=0",
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
                "kernel.1.sourceSwitching.sourcePromotionFirstBlocker=runtime equivalence must execute and pass before backend source promotion",
                "kernel.1.sourceSwitching.diagnostic.count=1",
                "kernel.1.sourceSwitching.diagnostic.0=production-like profile requested IrGpu source but opencl.productionSourceSwitching is disabled",
                "kernel.1.runtimeIrHandoff.status=selected",
                "kernel.1.runtimeIrHandoff.selectedStage=original",
                "kernel.1.runtimeIrHandoff.optimizedDiffersFromOriginal=false",
                "kernel.1.runtimeIrHandoff.optimizationRequiresRollback=false",
                "kernel.1.runtimeIrHandoff.fallbackDecision=production-ir-gate-blocked",
                "kernel.1.runtimeIrHandoff.optimizedIrRejected=true",
                "kernel.1.runtimeIrHandoff.diagnostic.count=1",
                "kernel.1.runtimeIrHandoff.diagnostic.0=optimized IrGpu was rejected by the production IR acceptance gate; original IrGpu remains selected",
                "kernel.1.runtimeProductionMutationSafety.status=disabled",
                "kernel.1.runtimeProductionMutationSafety.productionMutationEnabled=false",
                "kernel.1.runtimeProductionMutationSafety.productionGateStatus=blocked",
                "kernel.1.runtimeProductionMutationSafety.productionProfileRequested=true",
                "kernel.1.runtimeProductionMutationSafety.selectedStage=original",
                "kernel.1.runtimeProductionMutationSafety.diagnostic.count=1",
                "kernel.1.runtimeProductionMutationSafety.diagnostic.0=runtime IR participates in diagnostics, but production mutation remains fail-closed until production optimizer gates pass",
                "kernel.1.i3Readiness.status=blocked",
                "kernel.1.i3Readiness.selectedRuntimeIrStage=original",
                "kernel.1.i3Readiness.sourceReady=false",
                "kernel.1.i3Readiness.sourcePromotionStatus=blocked",
                "kernel.1.i3Readiness.sourcePromotionReviewReady=false",
                "kernel.1.i3Readiness.optimizerProductionGateStatus=blocked",
                "kernel.1.i3Readiness.productionMutationEnabled=false",
                "kernel.1.i3Readiness.diagnostic.count=1",
                "kernel.1.i3Readiness.diagnostic.0=I3 pipeline is active for diagnostics, but source promotion or production mutation is still blocked",
                "kernel.1.runtimeOptimizerDrift.status=recorded",
                "kernel.1.runtimeOptimizerDrift.pass.count=3",
                "kernel.1.runtimeOptimizerDrift.pass.applied.count=1",
                "kernel.1.runtimeOptimizerDrift.pass.rolledBack.count=0",
                "kernel.1.runtimeOptimizerDrift.pass.failed.count=1",
                "kernel.1.runtimeOptimizerDrift.fallbackDecision=production-ir-gate-blocked",
                "kernel.1.runtimeOptimizerDrift.selectedRuntimeIrStage=original",
                "kernel.1.runtimeOptimizerDrift.optimizedIrRejected=true",
                "kernel.1.runtimeOptimizerDrift.productionGateStatus=blocked",
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
        String previousProductionExplainabilityFile = System.getProperty(
                "javatogpu.opencl.productionPromotionExplainabilityFile");
        try {
            System.setProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", workloadGateFile.toString());
            System.setProperty("javatogpu.opencl.validationReportFile", reportFile.toString());
            System.setProperty("javatogpu.opencl.validationHistoryFile", historyFile.toString());
            System.setProperty("javatogpu.opencl.i3ReadinessWorkloadSummaryFile", i3SummaryFile.toString());
            System.setProperty("javatogpu.opencl.productionPromotionExplainabilityFile", productionExplainabilityFile.toString());

            OpenClValidationReporter.main(new String[0]);

            String reportMarkdown = java.nio.file.Files.readString(reportFile);
            String i3Summary = java.nio.file.Files.readString(i3SummaryFile);
            String productionExplainability = java.nio.file.Files.readString(productionExplainabilityFile);
            java.util.List<OpenClValidationHistoryEntry> entries = OpenClValidationHistoryIO.readAll(historyFile);
            assertTrue(reportMarkdown.contains("- Real workload evidence: `runtime-snapshot`"));
            assertTrue(reportMarkdown.contains("- Source switching decisions: `compile-irgpu-source-review=1, reject-production-irgpu-source=1`"));
            assertTrue(reportMarkdown.contains("- Source switching first blockers: `runtime equivalence must execute and pass before backend source promotion=1`"));
            assertTrue(reportMarkdown.contains("- Source switching first blocker families: `runtime-equivalence=1`"));
            assertTrue(reportMarkdown.contains("- Blocker families: `reconstruction=1, runtime-equivalence=1, source-parity=1`"));
            assertTrue(reportMarkdown.contains("- Kernel evidence count: `2`"));
            assertTrue(reportMarkdown.contains("- Kernel `0`: `inline://integration/image-kernel.cl`, status=`review-ready`, parity=`true`, runtimeEquivalence=`true`, sourceSwitching=`compile-irgpu-source-review`, operatorAccepted=`false`, runtimeIr=`optimized`, productionMutation=`false`, sourceReady=`true`, i3=`review-ready`"));
            assertTrue(reportMarkdown.contains(
                    "- Kernel `0` source switching: status=`review-ready`, profile=`"
                            + net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE
                            + "`, sourcePromotionFirstBlocker=`none`, operatorAccepted=`false`, first=`IrGpu source was explicitly selected for review or smoke validation`"
            ));
            assertTrue(reportMarkdown.contains("- Kernel `0` runtime IR handoff: stage=`optimized`, transformed=`true`, rollback=`false`, rejected=`false`, fallback=`none`, first=`optimized IrGpu is selected for backend lowering after runtime optimizer passes`"));
            assertTrue(reportMarkdown.contains("- Kernel `0` production mutation safety: enabled=`false`, gate=`not-requested`, profileRequested=`false`, first=`runtime IR participates in diagnostics, but production mutation is disabled because no production profile was requested`"));
            assertTrue(reportMarkdown.contains("- Kernel `0` I3 readiness: status=`review-ready`, sourcePromotion=`review-ready`, sourceReady=`true`, optimizerGate=`not-requested`, productionMutation=`false`, first=`I3 source pipeline is review-ready, but production mutation remains disabled until production gates are accepted`"));
            assertTrue(reportMarkdown.contains("- Kernel `0` runtime optimizer drift: passes=`2`, applied=`2`, rolledBack=`0`, failed=`0`, proof=`0`, acceptedProof=`0`, blockingProof=`0`, replacementPlanComplete=`0`, replacementPlanPartial=`0`, replacementPlanFirstBlocker=`none`, replacementPlanValidation=`0/0 valid, invalid=0`, replacementPlanValidationFirstBlocker=`none`, rewriteSketch=`0/0 ready, blocked=0`, rewriteSketchFirstBlocker=`none`, rewriteSketchConflicts=`0`, rewriteSketchConflictFirstBlocker=`none`, rewriteSelection=`not-required`, rewriteSelectionFirstBlocker=`no-rewrite-sketches`, rewriteProof=`not-required`, rewriteProofFirstBlocker=`no-proof-candidates`, rewriteReviewPackage=`not-required`, rewriteReviewPackageFirstBlocker=`no-review-candidates`, optimizerRules=`0`, optimizerRuleDetails=`none`, optimizerFamilies=`0`, promotionReadyFamilies=`0`, selected=`optimized`, fallback=`none`, gate=`not-requested`"));
            assertTrue(reportMarkdown.contains("- Kernel `0` diagnostics: `1`; first=`packaged IrGpu source reconstructed from runtime artifact loader with descriptor parity`"));
            assertFalse(reportMarkdown.contains("- Kernel `0` reconstruction blockers:"));
            assertTrue(reportMarkdown.contains("- Kernel `1`: `inline://integration/perlin-kernel.cl`, status=`blocked`, parity=`false`, runtimeEquivalence=`false`, sourceSwitching=`reject-production-irgpu-source`, operatorAccepted=`false`, runtimeIr=`original`, productionMutation=`false`, sourceReady=`false`, i3=`blocked`"));
            assertTrue(reportMarkdown.contains("- Kernel `1` source switching: status=`blocked`, profile=`vendor-tuned`, sourcePromotionFirstBlocker=`runtime equivalence must execute and pass before backend source promotion`, operatorAccepted=`false`, first=`production-like profile requested IrGpu source but opencl.productionSourceSwitching is disabled`"));
            assertTrue(reportMarkdown.contains("- Kernel `1` runtime IR handoff: stage=`original`, transformed=`false`, rollback=`false`, rejected=`true`, fallback=`production-ir-gate-blocked`, first=`optimized IrGpu was rejected by the production IR acceptance gate; original IrGpu remains selected`"));
            assertTrue(reportMarkdown.contains("- Kernel `1` production mutation safety: enabled=`false`, gate=`blocked`, profileRequested=`true`, first=`runtime IR participates in diagnostics, but production mutation remains fail-closed until production optimizer gates pass`"));
            assertTrue(reportMarkdown.contains("- Kernel `1` I3 readiness: status=`blocked`, sourcePromotion=`blocked`, sourceReady=`false`, optimizerGate=`blocked`, productionMutation=`false`, first=`I3 pipeline is active for diagnostics, but source promotion or production mutation is still blocked`"));
            assertTrue(reportMarkdown.contains("- Kernel `1` runtime optimizer drift: passes=`3`, applied=`1`, rolledBack=`0`, failed=`1`, proof=`0`, acceptedProof=`0`, blockingProof=`0`, replacementPlanComplete=`0`, replacementPlanPartial=`0`, replacementPlanFirstBlocker=`none`, replacementPlanValidation=`0/0 valid, invalid=0`, replacementPlanValidationFirstBlocker=`none`, rewriteSketch=`0/0 ready, blocked=0`, rewriteSketchFirstBlocker=`none`, rewriteSketchConflicts=`0`, rewriteSketchConflictFirstBlocker=`none`, rewriteSelection=`not-required`, rewriteSelectionFirstBlocker=`no-rewrite-sketches`, rewriteProof=`not-required`, rewriteProofFirstBlocker=`no-proof-candidates`, rewriteReviewPackage=`not-required`, rewriteReviewPackageFirstBlocker=`no-review-candidates`, optimizerRules=`0`, optimizerRuleDetails=`none`, optimizerFamilies=`0`, promotionReadyFamilies=`0`, selected=`original`, fallback=`production-ir-gate-blocked`, gate=`blocked`"));
            assertTrue(reportMarkdown.contains("- Kernel `1` diagnostics: `1`; first=`reconstructed source must match descriptor source before promotion review`"));
            assertTrue(reportMarkdown.contains("- Kernel blocker families: `source-parity=1`"));
            assertFalse(reportMarkdown.contains("runtimeIr=`unknown`"));
            assertFalse(reportMarkdown.contains("i3=`unknown`"));
            assertTrue(reportMarkdown.contains("real workload runtime snapshot captured before source promotion"));
            assertTrue(reportMarkdown.contains("## Production Promotion Explainability"));
            assertTrue(reportMarkdown.contains("- Contract: `valid`"));
            assertTrue(reportMarkdown.contains("- Decision mode: `diagnostic-only`"));
            assertTrue(reportMarkdown.contains("- Optimizer payload-complete families: `0`"));
            assertTrue(reportMarkdown.contains("- Optimizer payload complete all: `false`"));
            assertEquals(1, entries.size());
            assertTrue(entries.get(0).backendSourcePromotionWorkloadStatus().contains("realWorkloadEvidence=runtime-snapshot"));
            assertTrue(entries.get(0).backendSourcePromotionWorkloadStatus().contains("sourceSwitching=compile-irgpu-source-review=1, reject-production-irgpu-source=1"));
            assertTrue(entries.get(0).backendSourcePromotionWorkloadStatus().contains("sourcePromotionFirstBlockers=runtime equivalence must execute and pass before backend source promotion=1"));
            assertTrue(entries.get(0).backendSourcePromotionWorkloadStatus().contains("sourcePromotionFirstBlockerFamilies=runtime-equivalence=1"));
            assertTrue(entries.get(0).backendSourcePromotionWorkloadStatus().contains("kernelCount=2"));
            assertTrue(entries.get(0).backendSourcePromotionWorkloadStatus().contains("kernel.0=inline://integration/image-kernel.cl[diagnostics=1, sourceSwitching=compile-irgpu-source-review/operatorAccepted=false/sourcePromotionFirstBlocker=none, runtimeIr=optimized, optimizerDrift=recorded/2passes/rollback=0/proof=0/acceptedProof=0/blockingProof=0/replacementPlanComplete=0/replacementPlanPartial=0/replacementPlanFirstBlocker=none/replacementPlanValidationValid=0/replacementPlanValidationTotal=0/replacementPlanValidationInvalid=0/replacementPlanValidationFirstBlocker=none/rewriteSketchReady=0/rewriteSketchTotal=0/rewriteSketchBlocked=0/rewriteSketchFirstBlocker=none/rewriteSketchConflicts=0/rewriteSketchConflictFirstBlocker=none/rewriteSketchConflictResolutionImplemented=false/rewriteSketchSelectionApplied=false/rewriteSelectionStatus=not-required/rewriteSelectionFirstBlocker=no-rewrite-sketches/rewriteSelectionApplied=false/rewriteProofStatus=not-required/rewriteProofFirstBlocker=no-proof-candidates/rewriteProofAccepted=false/rewriteBuilderImplemented=false/selectedIrReplacement=false/rewriteReviewPackageStatus=not-required/rewriteReviewPackageFirstBlocker=no-review-candidates/rewriteReviewPackageComplete=false/optimizerRules=0/optimizerRuleDetails=none/optimizerFamilies=0/promotionReadyFamilies=0/payloadCompleteFamilies=0/payloadCompleteAll=false, extensionParticipation=not-recorded/0executions/failedContinued=0/failedClosed=0/fallback=none, productionMutation=false, sourceReady=true, i3=review-ready, families=none]"));
            assertTrue(entries.get(0).backendSourcePromotionWorkloadStatus().contains("kernel.1=inline://integration/perlin-kernel.cl[diagnostics=1, sourceSwitching=reject-production-irgpu-source/operatorAccepted=false/sourcePromotionFirstBlocker=runtime equivalence must execute and pass before backend source promotion, runtimeIr=original, optimizerDrift=recorded/3passes/rollback=0/proof=0/acceptedProof=0/blockingProof=0/replacementPlanComplete=0/replacementPlanPartial=0/replacementPlanFirstBlocker=none/replacementPlanValidationValid=0/replacementPlanValidationTotal=0/replacementPlanValidationInvalid=0/replacementPlanValidationFirstBlocker=none/rewriteSketchReady=0/rewriteSketchTotal=0/rewriteSketchBlocked=0/rewriteSketchFirstBlocker=none/rewriteSketchConflicts=0/rewriteSketchConflictFirstBlocker=none/rewriteSketchConflictResolutionImplemented=false/rewriteSketchSelectionApplied=false/rewriteSelectionStatus=not-required/rewriteSelectionFirstBlocker=no-rewrite-sketches/rewriteSelectionApplied=false/rewriteProofStatus=not-required/rewriteProofFirstBlocker=no-proof-candidates/rewriteProofAccepted=false/rewriteBuilderImplemented=false/selectedIrReplacement=false/rewriteReviewPackageStatus=not-required/rewriteReviewPackageFirstBlocker=no-review-candidates/rewriteReviewPackageComplete=false/optimizerRules=0/optimizerRuleDetails=none/optimizerFamilies=0/promotionReadyFamilies=0/payloadCompleteFamilies=0/payloadCompleteAll=false, extensionParticipation=not-recorded/0executions/failedContinued=0/failedClosed=0/fallback=production-ir-gate-blocked, productionMutation=false, sourceReady=false, i3=blocked, families=source-parity=1]"));
            assertTrue(entries.get(0).backendSourcePromotionWorkloadStatus().contains("productionSourceSwitching=disabled"));
            assertTrue(entries.get(0).productionPromotionExplainabilityStatus().contains("contract=valid"));
            assertTrue(entries.get(0).productionPromotionExplainabilityStatus().contains("decisionMode=diagnostic-only"));
            assertTrue(entries.get(0).productionPromotionExplainabilityStatus().contains("sourceSwitchingAllowed=false"));
            assertTrue(entries.get(0).productionPromotionExplainabilityStatus().contains("i3SourceReady=1"));
            assertTrue(entries.get(0).productionPromotionExplainabilityStatus().contains("i3SourceReadyAll=false"));
            assertTrue(entries.get(0).productionPromotionExplainabilityStatus().contains("optimizerFamilies=0"));
            assertTrue(entries.get(0).productionPromotionExplainabilityStatus().contains("optimizerPromotionReadyFamilies=0"));
            assertTrue(i3Summary.contains("status=blocked"));
            assertTrue(i3Summary.contains("kernel.count=2"));
            assertTrue(i3Summary.contains("reviewReady.count=1"));
            assertTrue(i3Summary.contains("blocked.count=1"));
            assertTrue(i3Summary.contains("sourceReady.count=1"));
            assertTrue(i3Summary.contains("sourceReady.all=false"));
            assertTrue(i3Summary.contains("productionMutationEnabled=false"));
            assertTrue(i3Summary.contains("status.0.name=review-ready"));
            assertTrue(i3Summary.contains("status.1.name=blocked"));
            assertTrue(i3Summary.contains("kernel.0.i3Status=review-ready"));
            assertTrue(i3Summary.contains("kernel.0.sourceReady=true"));
            assertTrue(i3Summary.contains("kernel.0.runtimeIr=optimized"));
            assertTrue(i3Summary.contains("kernel.0.optimizerDriftStatus=recorded"));
            assertTrue(i3Summary.contains("kernel.0.optimizerDriftPassCount=2"));
            assertTrue(i3Summary.contains("kernel.0.optimizerDriftRolledBackCount=0"));
            assertTrue(i3Summary.contains("kernel.0.optimizerDriftFallbackDecision=none"));
            assertTrue(i3Summary.contains("kernel.1.i3Status=blocked"));
            assertTrue(i3Summary.contains("kernel.1.sourceReady=false"));
            assertTrue(i3Summary.contains("kernel.1.runtimeIr=original"));
            assertTrue(i3Summary.contains("kernel.1.optimizerDriftStatus=recorded"));
            assertTrue(i3Summary.contains("kernel.1.optimizerDriftPassCount=3"));
            assertTrue(i3Summary.contains("kernel.1.optimizerDriftRolledBackCount=0"));
            assertTrue(i3Summary.contains("kernel.1.optimizerDriftFallbackDecision=production-ir-gate-blocked"));
            assertFalse(i3Summary.contains("i3Status=unknown"));
            assertFalse(i3Summary.contains("runtimeIr=unknown"));
            assertTrue(i3Summary.contains("diagnostic.0=I3 workload readiness remains blocked: reviewReady=1, blocked=1"));
            assertTrue(productionExplainability.contains("status=blocked"));
            assertTrue(productionExplainability.contains("contract.status=valid"));
            assertTrue(productionExplainability.contains("contract.valid=true"));
            assertTrue(productionExplainability.contains("contract.violation.count=0"));
            assertTrue(productionExplainability.contains("decision.mode=diagnostic-only"));
            assertTrue(productionExplainability.contains("gateReviewReady=false"));
            assertTrue(productionExplainability.contains("sourceParityMatched=false"));
            assertTrue(productionExplainability.contains("runtimeEquivalencePassed=false"));
            assertTrue(productionExplainability.contains("kernel.count=2"));
            assertTrue(productionExplainability.contains("i3ReviewReady.count=1"));
            assertTrue(productionExplainability.contains("i3Blocked.count=1"));
            assertTrue(productionExplainability.contains("i3SourceReady.count=1"));
            assertTrue(productionExplainability.contains("i3SourceReady.all=false"));
            assertTrue(productionExplainability.contains("backendPromotionArtifactSupport.complete=true"));
            assertTrue(productionExplainability.contains("backendPromotionArtifactSupport.missing.count=0"));
            assertTrue(productionExplainability.contains("productionSourceSwitchingAllowed=false"));
            assertTrue(productionExplainability.contains("productionMutationAllowed=false"));
            assertTrue(productionExplainability.contains("blocker.0=workload-source-promotion-gate-not-review-ready"));
            assertTrue(productionExplainability.contains("blocker.1=source-parity-not-matched"));
            assertTrue(productionExplainability.contains("blocker.2=runtime-equivalence-not-passed"));
            assertTrue(productionExplainability.contains("blocker.3=i3-workload-readiness-not-review-ready"));
            assertTrue(productionExplainability.contains("blocker.4=i3-source-readiness-not-complete"));
            assertTrue(productionExplainability.contains("blocker.5=production-source-switching-disabled"));
            assertTrue(productionExplainability.contains("blocker.6=production-source-switching-not-enabled-for-all-kernels"));
            assertTrue(productionExplainability.contains("blocker.7=production-promotion-decision-not-enabled-for-all-kernels"));
            assertTrue(productionExplainability.contains("blocker.8=production-source-decision-not-compiled-for-all-kernels"));
            assertTrue(productionExplainability.contains("blocker.9=production-mutation-disabled"));
            assertTrue(productionExplainability.contains(
                    "diagnostic.0=production promotion remains blocked: first=workload-source-promotion-gate-not-review-ready, i3ReviewReady=1, i3Blocked=1"));
        } finally {
            restoreProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", previousWorkloadGateFile);
            restoreProperty("javatogpu.opencl.validationReportFile", previousReportFile);
            restoreProperty("javatogpu.opencl.validationHistoryFile", previousHistoryFile);
            restoreProperty("javatogpu.opencl.i3ReadinessWorkloadSummaryFile", previousI3SummaryFile);
            restoreProperty("javatogpu.opencl.productionPromotionExplainabilityFile", previousProductionExplainabilityFile);
        }
    }

    @Test
    void productionExplainabilityDerivesOptimizerFamilyBaselineFromValidationHistory() throws Exception {
        java.nio.file.Path workloadGateFile = java.nio.file.Files.createTempFile(
                "javatogpu-optimizer-family-baseline-workload", ".properties");
        java.nio.file.Path reportFile = java.nio.file.Files.createTempFile(
                "javatogpu-optimizer-family-baseline-report", ".md");
        java.nio.file.Path historyFile = java.nio.file.Files.createTempFile(
                "javatogpu-optimizer-family-baseline-history", ".properties");
        java.nio.file.Path i3SummaryFile = java.nio.file.Files.createTempFile(
                "javatogpu-optimizer-family-baseline-i3", ".properties");
        java.nio.file.Path productionExplainabilityFile = java.nio.file.Files.createTempFile(
                "javatogpu-optimizer-family-baseline-explainability", ".properties");
        OpenClValidationHistoryIO.writeAll(historyFile, java.util.List.of(new OpenClValidationHistoryEntry(
                Instant.parse("2026-07-01T12:00:00Z"),
                "nvidia",
                "OpenCL",
                "NVIDIA CUDA / Test GPU",
                "NVIDIA Corporation",
                "test-driver",
                "OpenCL 3.0 CUDA",
                "openClWorkloadValidationTest=passed",
                "passed",
                "passed",
                "passed",
                "passed",
                "review-ready (runtimeEquivalencePassed=true)",
                "blocked, runtimeEquivalencePassed=true, optimizerPromotionReadyFamilies=1, kernel.0=kernel.cl[optimizerDrift=recorded/1passes/promotionReadyFamilies=1]",
                "blocked, contract=valid, runtimeEquivalencePassed=true, optimizerPromotionReadyFamilies=1"
        )));
        java.nio.file.Files.writeString(workloadGateFile, String.join("\n",
                "status=review-ready",
                "reviewReady=true",
                "sourceParityMatched=true",
                "runtimeEquivalencePassed=true",
                "productionSourceSwitching=false",
                "realWorkloadEvidence=runtime-snapshot",
                "kernel.count=1",
                "kernel.0.sourceKernelResource=kernel.cl",
                "kernel.0.runtimeOptimizerDrift.optimizerFamily.count=1",
                "kernel.0.runtimeOptimizerDrift.optimizerFamily.promotionReady.count=1",
                "kernel.0.runtimeOptimizerDrift.optimizerFamily.summary=cse[passes=1, acceptedProof=1, blockingProof=0, rolledBack=0, failed=0, promotionReady=true]",
                ""
        ));
        String previousWorkloadGateFile = System.getProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile");
        String previousReportFile = System.getProperty("javatogpu.opencl.validationReportFile");
        String previousHistoryFile = System.getProperty("javatogpu.opencl.validationHistoryFile");
        String previousI3SummaryFile = System.getProperty("javatogpu.opencl.i3ReadinessWorkloadSummaryFile");
        String previousProductionExplainabilityFile = System.getProperty(
                "javatogpu.opencl.productionPromotionExplainabilityFile");
        try {
            System.setProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", workloadGateFile.toString());
            System.setProperty("javatogpu.opencl.validationReportFile", reportFile.toString());
            System.setProperty("javatogpu.opencl.validationHistoryFile", historyFile.toString());
            System.setProperty("javatogpu.opencl.i3ReadinessWorkloadSummaryFile", i3SummaryFile.toString());
            System.setProperty("javatogpu.opencl.productionPromotionExplainabilityFile", productionExplainabilityFile.toString());

            OpenClValidationReporter.main(new String[0]);

            String productionExplainability = java.nio.file.Files.readString(productionExplainabilityFile);
            assertTrue(productionExplainability.contains("optimizerFamily.count=1"));
            assertTrue(productionExplainability.contains("optimizerFamily.promotionReady.count=1"));
            assertTrue(productionExplainability.contains("optimizerFamily.runtimeEquivalenceHistoryBaselineReady=true"));
            assertTrue(productionExplainability.contains("optimizerFamily.promotionPreflightReady=true"));
            assertFalse(productionExplainability.contains("optimizer-family-runtime-equivalence-history-baseline-missing"));
            assertTrue(productionExplainability.contains("productionSourceSwitchingAllowed=false"));
        } finally {
            restoreProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", previousWorkloadGateFile);
            restoreProperty("javatogpu.opencl.validationReportFile", previousReportFile);
            restoreProperty("javatogpu.opencl.validationHistoryFile", previousHistoryFile);
            restoreProperty("javatogpu.opencl.i3ReadinessWorkloadSummaryFile", previousI3SummaryFile);
            restoreProperty("javatogpu.opencl.productionPromotionExplainabilityFile", previousProductionExplainabilityFile);
        }
    }

    @Test
    void validationReportAndHistoryExposeInvalidProductionPromotionExplainabilityContract() throws Exception {
        java.nio.file.Path productionExplainabilityFile = java.nio.file.Files.createTempFile(
                "javatogpu-invalid-production-promotion-explainability", ".properties");
        java.nio.file.Path reportFile = java.nio.file.Files.createTempFile(
                "javatogpu-opencl-report-invalid-production-explainability", ".md");
        java.nio.file.Path historyFile = java.nio.file.Files.createTempFile(
                "javatogpu-opencl-history-invalid-production-explainability", ".properties");
        java.nio.file.Files.deleteIfExists(historyFile);
        java.nio.file.Files.writeString(productionExplainabilityFile, String.join("\n",
                "status=blocked",
                "kernel.count=1",
                "i3ReviewReady.count=1",
                "i3Blocked.count=0",
                "productionSourceSwitchingAllowed=false",
                "productionSourceSwitchingEnabled=false",
                "productionMutationAllowed=false",
                "productionMutationEnabled=false",
                "blocker.count=0",
                "diagnostic.0=synthetic invalid fixture intentionally omits blockers",
                ""
        ));
        String previousWorkloadGateFile = System.getProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile");
        String previousReportFile = System.getProperty("javatogpu.opencl.validationReportFile");
        String previousHistoryFile = System.getProperty("javatogpu.opencl.validationHistoryFile");
        String previousProductionExplainabilityFile = System.getProperty(
                "javatogpu.opencl.productionPromotionExplainabilityFile");
        try {
            System.clearProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile");
            System.setProperty("javatogpu.opencl.validationReportFile", reportFile.toString());
            System.setProperty("javatogpu.opencl.validationHistoryFile", historyFile.toString());
            System.setProperty("javatogpu.opencl.productionPromotionExplainabilityFile", productionExplainabilityFile.toString());

            OpenClValidationReporter.main(new String[0]);

            String reportMarkdown = java.nio.file.Files.readString(reportFile);
            java.util.List<OpenClValidationHistoryEntry> entries = OpenClValidationHistoryIO.readAll(historyFile);
            assertTrue(reportMarkdown.contains("## Production Promotion Explainability"));
            assertTrue(reportMarkdown.contains("- Contract: `invalid`"));
            assertTrue(reportMarkdown.contains("- Decision mode: `unknown`"));
            assertTrue(reportMarkdown.contains("- Contract violation: `blocked explainability must include at least one blocker`"));
            assertEquals(1, entries.size());
            assertTrue(entries.get(0).productionPromotionExplainabilityStatus().contains("contract=invalid"));
            assertTrue(entries.get(0).productionPromotionExplainabilityStatus().contains("decisionMode=unknown"));
            assertTrue(entries.get(0).productionPromotionExplainabilityStatus().contains(
                    "violation=blocked explainability must include at least one blocker"));
        } finally {
            restoreProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", previousWorkloadGateFile);
            restoreProperty("javatogpu.opencl.validationReportFile", previousReportFile);
            restoreProperty("javatogpu.opencl.validationHistoryFile", previousHistoryFile);
            restoreProperty("javatogpu.opencl.productionPromotionExplainabilityFile", previousProductionExplainabilityFile);
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

    private static java.nio.file.Path findRepositoryFile(String relativePath) {
        java.nio.file.Path directory = java.nio.file.Path.of("").toAbsolutePath();
        while (directory != null) {
            java.nio.file.Path candidate = directory.resolve(relativePath);
            if (java.nio.file.Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Repository file not found: " + relativePath);
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
        assertTrue(report.promotionArtifactSupport().complete());
        assertTrue(report.promotionArtifactSupport().supports(
                net.sixik.ga_utils.javatogpu.runtime.GpuPromotionArtifactRegistry.BACKEND_PROMOTION_ARTIFACT_SUPPORT
        ));
    }

    @Test
    void validationReporterWritesBackendPromotionArtifactSupportProperties() throws Exception {
        java.nio.file.Path supportFile = java.nio.file.Files.createTempFile(
                "javatogpu-backend-promotion-artifact-support", ".properties");
        java.nio.file.Path reportFile = java.nio.file.Files.createTempFile("javatogpu-opencl-report-support", ".md");
        java.nio.file.Files.deleteIfExists(supportFile);
        String previousSupportFile = System.getProperty("javatogpu.opencl.backendPromotionArtifactSupportFile");
        String previousReportFile = System.getProperty("javatogpu.opencl.validationReportFile");
        try {
            System.setProperty("javatogpu.opencl.backendPromotionArtifactSupportFile", supportFile.toString());
            System.setProperty("javatogpu.opencl.validationReportFile", reportFile.toString());

            OpenClValidationReporter.main(new String[0]);

            String supportProperties = java.nio.file.Files.readString(supportFile);
            String reportMarkdown = java.nio.file.Files.readString(reportFile);
            assertTrue(supportProperties.contains("backendTarget=OPENCL"));
            assertTrue(supportProperties.contains("complete=true"));
            assertTrue(supportProperties.contains("supported.count=12"));
            assertTrue(supportProperties.contains("missing.count=0"));
            assertTrue(supportProperties.contains("supported.0=i3-readiness-summary.properties"));
            assertTrue(supportProperties.contains("backend-promotion-artifact-support.properties"));
            assertTrue(reportMarkdown.contains("## Production Promotion Artifacts"));
        } finally {
            restoreProperty("javatogpu.opencl.backendPromotionArtifactSupportFile", previousSupportFile);
            restoreProperty("javatogpu.opencl.validationReportFile", previousReportFile);
        }
    }

    @Test
    void validationReporterWritesKernelLaunchAdvisoryReportAndWorkflowFragment() throws Exception {
        java.nio.file.Path reportDirectory = java.nio.file.Files.createTempDirectory(
                "javatogpu-opencl-launch-advisory-reporter");
        java.nio.file.Path workloadGateFile = reportDirectory.resolve(
                "backend-source-promotion-workload-gate.properties");
        java.nio.file.Path reportFile = reportDirectory.resolve("validation-report.md");
        java.nio.file.Path summaryFile = reportDirectory.resolve("runtime-launch-advisory-summary.md");
        java.nio.file.Path historyFile = reportDirectory.resolve("validation-history.properties");
        java.nio.file.Path driftFile = reportDirectory.resolve("runtime-launch-advisory-drift.properties");
        java.nio.file.Path baselineFile = reportDirectory.resolve("validation-history-baseline.properties");
        java.nio.file.Files.writeString(workloadGateFile, String.join("\n",
                "kernel.count=1",
                "kernel.0.sourceKernelResource=workload/perlin.cl",
                ""
        ));
        java.nio.file.Path artifactDirectory = reportDirectory
                .resolve("runtime-compile-artifacts")
                .resolve("perlin");
        java.nio.file.Files.createDirectories(artifactDirectory);
        java.nio.file.Files.writeString(
                artifactDirectory.resolve(OpenClKernelLaunchAdvisory.ARTIFACT_FILE_NAME),
                String.join("\n",
                        "status=aligned",
                        "blocking=false",
                        "kernelResource=workload/perlin.cl",
                        "requestedLocalWorkGroupShape=32",
                        "requestedLocalWorkGroupSize=32",
                        "kernelMaxWorkGroupSize=256",
                        "preferredWorkGroupSizeMultiple=32",
                        "preferredMultipleMatched=true",
                        ""
                )
        );
        java.nio.file.Files.writeString(
                artifactDirectory.resolve("backend-module.properties"),
                String.join("\n",
                        "resource=workload/perlin.cl",
                        "backendTarget=OPENCL",
                        ""
                )
        );
        java.nio.file.Files.writeString(
                artifactDirectory.resolve(
                        net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileArtifactDumper
                                .RUNTIME_IR_OPTIMIZER_EVIDENCE_ARTIFACT),
                String.join("\n",
                        "status=recorded",
                        "pass.count=1",
                        "proposalOnly.count=1",
                        "selectedOptimized.count=0",
                        "rolledBack.count=0",
                        "constantFoldingPreview.pass.count=1",
                        "constantFoldingPreview.candidate.count=2",
                        "constantFoldingPreview.skipped.nonPlainLiteral.count=1",
                        "constantFoldingPreview.skipped.divideByZero.count=1",
                        "constantFoldingPreview.skipped.nonEvenDivision.count=0",
                        "constantFoldingPreview.skipped.unsupportedOperator.count=0",
                        "constantFoldingPreview.skipped.nonLiteralOperand.count=0",
                        "constantFoldingPreview.runtimeEquivalenceRequiredBeforeRewrite=true",
                        "constantFoldingPreview.approvalRequiredBeforeRewrite=true",
                        "constantFoldingPreview.integerOverflowProven=false",
                        "constantFoldingPreview.floatingPointRoundingProven=false",
                        "safeLocalCsePreview.pass.count=1",
                        "safeLocalCsePreview.expression.count=5",
                        "safeLocalCsePreview.candidateExpression.count=3",
                        "safeLocalCsePreview.duplicateExpression.count=1",
                        "safeLocalCsePreview.equivalenceClass.count=1",
                        "safeLocalCsePreview.blocked.unsupportedOperator.count=1",
                        "safeLocalCsePreview.blocked.impureOperand.count=0",
                        "safeLocalCsePreview.blocked.controlFlowBoundary.count=1",
                        "safeLocalCsePreview.runtimeEquivalenceRequiredBeforeRewrite=true",
                        "safeLocalCsePreview.approvalRequiredBeforeRewrite=true",
                        "safeLocalCsePreview.dominanceProven=false",
                        "safeLocalCsePreview.sideEffectFreedomProven=false",
                        "typedDeadCodePreview.pass.count=1",
                        "typedDeadCodePreview.node.count=7",
                        "typedDeadCodePreview.reachableNode.count=5",
                        "typedDeadCodePreview.unreachableNode.count=2",
                        "typedDeadCodePreview.blocked.missingRoot.count=0",
                        "typedDeadCodePreview.blocked.missingChildReference.count=1",
                        "typedDeadCodePreview.blocked.sideEffectingUnreachableNode.count=1",
                        "typedDeadCodePreview.runtimeEquivalenceRequiredBeforeRewrite=true",
                        "typedDeadCodePreview.approvalRequiredBeforeRewrite=true",
                        "typedDeadCodePreview.sideEffectFreedomProven=false",
                        "pass.0.passVersion=ir-optimizer:text-canonicalization:1",
                        ""
                )
        );
        String previousGateFile = System.getProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile");
        String previousReportFile = System.getProperty("javatogpu.opencl.validationReportFile");
        String previousSummaryFile = System.getProperty("javatogpu.opencl.kernelLaunchAdvisorySummaryFile");
        String previousHistoryFile = System.getProperty("javatogpu.opencl.validationHistoryFile");
        String previousDriftFile = System.getProperty("javatogpu.opencl.kernelLaunchAdvisoryDriftFile");
        String previousBaselineFile = System.getProperty("javatogpu.opencl.validationHistoryBaselineFile");
        try {
            System.setProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", workloadGateFile.toString());
            System.setProperty("javatogpu.opencl.validationReportFile", reportFile.toString());
            System.setProperty("javatogpu.opencl.kernelLaunchAdvisorySummaryFile", summaryFile.toString());
            System.setProperty("javatogpu.opencl.validationHistoryFile", historyFile.toString());
            System.setProperty("javatogpu.opencl.kernelLaunchAdvisoryDriftFile", driftFile.toString());

            OpenClValidationReporter.main(new String[0]);

            String baselineReport = java.nio.file.Files.readString(reportFile);
            assertTrue(baselineReport.contains("## Kernel Launch Advisory Drift"));
            assertTrue(baselineReport.contains("- Status: `no-baseline`"));
            java.nio.file.Files.copy(historyFile, baselineFile);
            java.nio.file.Files.delete(historyFile);
            System.setProperty("javatogpu.opencl.validationHistoryBaselineFile", baselineFile.toString());

            java.nio.file.Files.writeString(
                    artifactDirectory.resolve(OpenClKernelLaunchAdvisory.ARTIFACT_FILE_NAME),
                    String.join("\n",
                            "status=non-preferred-multiple",
                            "blocking=false",
                            "kernelResource=workload/perlin.cl",
                            "requestedLocalWorkGroupShape=48",
                            "requestedLocalWorkGroupSize=48",
                            "kernelMaxWorkGroupSize=256",
                            "preferredWorkGroupSizeMultiple=32",
                            "preferredMultipleMatched=false",
                            ""
                    )
            );
            OpenClValidationReporter.main(new String[0]);
            OpenClValidationReporter.main(new String[0]);

            String reportMarkdown = java.nio.file.Files.readString(reportFile);
            String summaryMarkdown = java.nio.file.Files.readString(summaryFile);
            assertTrue(reportMarkdown.contains("## Kernel Launch Advisories"));
            assertTrue(reportMarkdown.contains("- Non-preferred multiple: `1`"));
            assertTrue(reportMarkdown.contains("`workload/perlin.cl` | `non-preferred-multiple`"));
            assertTrue(reportMarkdown.contains("## Runtime IR Optimizer Evidence"));
            assertTrue(reportMarkdown.contains("- Proposal-only count: `1`"));
            assertTrue(reportMarkdown.contains("- Optimized artifact candidate status: `not-recorded`"));
            assertTrue(reportMarkdown.contains("- Optimized artifact candidates: `0`"));
            assertTrue(reportMarkdown.contains("- Optimized artifact candidate first blocker: `no-candidates`"));
            assertTrue(reportMarkdown.contains("- Optimized artifact candidate selection first blocker: `no-candidates`"));
            assertTrue(reportMarkdown.contains("- Optimized artifact candidate selection applied: `false`"));
            assertTrue(reportMarkdown.contains("- Optimized artifact candidate selected IR replacement: `false`"));
            assertTrue(reportMarkdown.contains("- Constant folding preview passes: `1`"));
            assertTrue(reportMarkdown.contains("- Constant folding preview candidates: `2`"));
            assertTrue(reportMarkdown.contains("- Constant folding preview skipped blockers: `2`"));
            assertTrue(reportMarkdown.contains("- Safe local CSE preview passes: `1`"));
            assertTrue(reportMarkdown.contains("- Safe local CSE preview candidate expressions: `3`"));
            assertTrue(reportMarkdown.contains("- Safe local CSE preview duplicate expressions: `1`"));
            assertTrue(reportMarkdown.contains("- Safe local CSE preview blockers: `2`"));
            assertTrue(reportMarkdown.contains("- Typed dead-code preview passes: `1`"));
            assertTrue(reportMarkdown.contains("- Typed dead-code preview unreachable nodes: `2`"));
            assertTrue(reportMarkdown.contains("- Typed dead-code preview blockers: `2`"));
            assertTrue(reportMarkdown.contains("- Preview readiness status: `blocked-by-proof`"));
            assertTrue(reportMarkdown.contains("- Preview readiness families: `constant-folding=blocked-by-proof, safe-local-cse=blocked-by-proof, typed-dead-code=blocked-by-proof`"));
            assertTrue(reportMarkdown.contains("- Preview readiness recorded families: `3`"));
            assertTrue(reportMarkdown.contains("- Preview readiness candidate families: `3`"));
            assertTrue(reportMarkdown.contains("- Preview readiness blocked families: `3`"));
            assertTrue(reportMarkdown.contains("- Runtime-equivalence review status: `blocked`"));
            assertTrue(reportMarkdown.contains("- Runtime-equivalence review eligible: `false`"));
            assertTrue(reportMarkdown.contains("- Runtime-equivalence review required: `true`"));
            assertTrue(reportMarkdown.contains("- Runtime-equivalence review first blocker: `preview-readiness-blocked-by-proof`"));
            assertTrue(reportMarkdown.contains("- Runtime-equivalence review production mutation: `disabled`"));
            assertTrue(reportMarkdown.contains("- Runtime-equivalence review selected IR replacement: `disabled`"));
            assertTrue(reportMarkdown.contains("- Review package status: `not-required`"));
            assertTrue(reportMarkdown.contains("- Review package required kernels: `0`"));
            assertTrue(reportMarkdown.contains("- Review package first blocker: `none`"));
            assertTrue(reportMarkdown.contains("- Review package manual review only: `true`"));
            assertTrue(reportMarkdown.contains("- Review package production mutation: `disabled`"));
            assertTrue(reportMarkdown.contains("- Review package selected IR replacement: `disabled`"));
            assertTrue(reportMarkdown.contains("| `workload/perlin.cl` | `recorded` | `1` | `1` | `0` | `0` | `0` | `0` | `not-recorded` | `no-candidates` | `no-candidates` | `2` | `2` | `3` | `1` | `2` | `2` | `2` | `not-recorded` | `none` | `ir-optimizer:text-canonicalization:1=1` |"));
            assertTrue(reportMarkdown.contains("ir-optimizer:text-canonicalization:1=1"));
            assertTrue(summaryMarkdown.contains("## Kernel Launch Advisories"));
            assertTrue(summaryMarkdown.contains("- Blocking: `0`"));
            assertTrue(summaryMarkdown.contains("## Kernel Launch Advisory Drift"));
            assertTrue(summaryMarkdown.contains("- Status: `regressed`"));
            assertTrue(summaryMarkdown.contains("nonPreferred=+1"));
            String driftProperties = java.nio.file.Files.readString(driftFile);
            assertTrue(driftProperties.contains("status=regressed"));
            assertTrue(driftProperties.contains("regression=true"));
            assertTrue(driftProperties.contains("delta.nonPreferred=1"));
            assertThrows(
                    IllegalStateException.class,
                    () -> OpenClKernelLaunchAdvisoryDriftValidatorCli.main(
                            new String[]{driftFile.toString()}
                    )
            );
            java.util.List<OpenClValidationHistoryEntry> baseline =
                    OpenClValidationHistoryIO.readAll(baselineFile);
            assertEquals(1, baseline.size());
            String baselineAdvisoryStatus = baseline.get(0).kernelLaunchAdvisoryStatus();
            assertEquals(
                    "recorded (kernels=1, aligned=1, nonPreferred=0, driverSelected=0, unavailable=0, missing=0, blocking=0)",
                    OpenClKernelLaunchAdvisorySummary.aggregateHistorySummary(baselineAdvisoryStatus)
            );
            assertEquals(
                    "aligned",
                    OpenClKernelLaunchAdvisorySummary.parseHistoryEntries(baselineAdvisoryStatus)
                            .orElseThrow()
                            .get(0)
                            .status()
            );
            java.util.List<OpenClValidationHistoryEntry> history = OpenClValidationHistoryIO.readAll(historyFile);
            assertEquals(3, history.size());
            String currentAdvisoryStatus = history.get(0).kernelLaunchAdvisoryStatus();
            assertEquals(
                    "recorded (kernels=1, aligned=0, nonPreferred=1, driverSelected=0, unavailable=0, missing=0, blocking=0)",
                    OpenClKernelLaunchAdvisorySummary.aggregateHistorySummary(currentAdvisoryStatus)
            );
            assertEquals(
                    "non-preferred-multiple",
                    OpenClKernelLaunchAdvisorySummary.parseHistoryEntries(currentAdvisoryStatus)
                            .orElseThrow()
                            .get(0)
                            .status()
            );
            String workflow = java.nio.file.Files.readString(findRepositoryFile(
                    ".github/workflows/opencl-vendor-matrix.yaml"
            ));
            assertTrue(workflow.contains("steps.launch_advisory_drift.outcome == 'success'"));
            assertTrue(workflow.contains("if: steps.validation_history_stage.outcome == 'success'"));
            assertTrue(workflow.contains("uses: actions/cache/save@v4"));
            assertTrue(workflow.contains("launch_advisory_negative_fixture:"));
            assertTrue(workflow.contains("validation_lane:"));
            assertTrue(workflow.contains("production_promotion_manifest_mode:"));
            assertTrue(workflow.contains("production_promotion_manifest_file:"));
            assertTrue(workflow.contains("production_promotion_candidate_git_sha:"));
            assertTrue(workflow.contains("github.event.inputs.validation_lane == 'nvidia'"));
            assertTrue(workflow.contains("github.event.inputs.validation_lane == 'amd'"));
            assertTrue(workflow.contains(":processor:writeOpenClBackendSourcePromotionManifestTemplate"));
            assertTrue(workflow.contains(":processor:validateOpenClBackendSourcePromotionManifest"));
            assertTrue(workflow.contains(":processor:validateOpenClBackendSourcePromotionActivationGate"));
            assertTrue(workflow.contains("JTG_PRODUCTION_PROMOTION_CANDIDATE_GIT_SHA"));
            assertTrue(workflow.contains("steps.production_promotion_manifest_validation.outcome != 'success'"));
            assertTrue(workflow.contains("steps.production_promotion_activation_gate.outcome != 'success'"));
            assertTrue(workflow.contains(":processor:prepareOpenClKernelLaunchAdvisoryNegativeFixture"));
            assertTrue(workflow.contains("env.JTG_LAUNCH_ADVISORY_NEGATIVE_FIXTURE != 'true'"));
            assertTrue(workflow.contains("LAUNCH_ADVISORY_DRIFT_OUTCOME%\"==\"failure"));
            assertTrue(workflow.contains("VALIDATION_HISTORY_STAGE_OUTCOME%\"==\"skipped"));
            assertTrue(workflow.contains("VALIDATION_HISTORY_SAVE_OUTCOME%\"==\"skipped"));
            assertTrue(workflow.contains("steps.launch_advisory_negative_fixture_check.outcome != 'success'"));
            String buildScript = java.nio.file.Files.readString(findRepositoryFile("processor/build.gradle"));
            String sourceSwitchingDependency = "dependsOn 'openClProductionSourceSwitchingValidationTest'";
            assertEquals(
                    4,
                    buildScript.split(java.util.regex.Pattern.quote(sourceSwitchingDependency), -1).length - 1
            );
            assertTrue(buildScript.contains("openClBackendSourcePromotionCandidateGate"));
            assertTrue(buildScript.contains("openClProductionMutationValidationTest"));
            assertTrue(buildScript.contains("prepareOpenClKernelLaunchAdvisoryNegativeFixture"));
            assertTrue(buildScript.contains("OpenClKernelLaunchAdvisoryNegativeFixtureCli"));
        } finally {
            restoreProperty("javatogpu.opencl.backendSourcePromotionWorkloadGateFile", previousGateFile);
            restoreProperty("javatogpu.opencl.validationReportFile", previousReportFile);
            restoreProperty("javatogpu.opencl.kernelLaunchAdvisorySummaryFile", previousSummaryFile);
            restoreProperty("javatogpu.opencl.validationHistoryFile", previousHistoryFile);
            restoreProperty("javatogpu.opencl.kernelLaunchAdvisoryDriftFile", previousDriftFile);
            restoreProperty("javatogpu.opencl.validationHistoryBaselineFile", previousBaselineFile);
        }
    }
}
