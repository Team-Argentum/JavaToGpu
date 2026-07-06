package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationReadinessSummaryReportTest {
    @Test
    void reportsNoCandidatesAsNotReady() {
        GpuIrAutoVectorizationPreview preview = new GpuIrAutoVectorizationPreview(
                "kernel",
                List.of(),
                List.of(),
                List.of()
        );
        GpuIrAutoVectorizationReadinessSummaryReport report = GpuIrAutoVectorizationReadinessSummaryReport.from(
                preview,
                GpuIrAutoVectorizationRewriteDryRunReport.skipped(
                        "kernel",
                        0,
                        0,
                        0,
                        List.of("Auto-vectorization rewrite dry-run skipped: no rewrite candidates")
                ),
                GpuIrAutoVectorizationResolvedRewriteOperations.empty("kernel")
        );
        Map<String, String> fields = report.artifactFields("autoReadiness");

        assertEquals("notReady/noCandidates", report.verdict());
        assertFalse(report.readyForPrototypeRewrite());
        assertEquals(List.of(
                "noRewriteCandidates",
                "rewritePolicyBlocksRewrite",
                "dryRunNotReady"
        ), report.blockingReasons());
        assertEquals("noRewriteCandidates", report.firstBlockingReason().orElseThrow());
        assertEquals("collectRewriteCandidates", report.firstRemainingWork().orElseThrow());
        assertEquals("notReady/noCandidates", fields.get("autoReadinessVerdict"));
        assertEquals("false", fields.get("autoReadinessReadyForPrototypeRewrite"));
        assertEquals("3", fields.get("autoReadinessBlockingReasonCount"));
        assertEquals("{noRewriteCandidates=1,rewritePolicyBlocksRewrite=1,dryRunNotReady=1}", fields.get("autoReadinessBlockingReasonCounts"));
        assertEquals("blocked", fields.get("autoReadinessBlockerVerdict"));
        assertEquals("candidateDiscovery.noRewriteCandidates", fields.get("autoReadinessBlockerFirstFamily"));
        assertEquals("collectRewriteCandidates", fields.get("autoReadinessBlockerFirstRemainingWork"));
        assertEquals("auto-vectorization readiness notReady/noCandidates blockers=3 first=noRewriteCandidates", fields.get("autoReadinessCiSummaryLine"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void reportsWarningsBeforeRewritePlanningCanProceed() {
        GpuIrAutoVectorizationPreview preview = new GpuIrAutoVectorizationPreview(
                "kernel",
                List.of(),
                List.of(new GpuIrAutoVectorizationWarningDiagnostic(
                        "stmt[0]",
                        1,
                        List.of("target array `out` is read inside the same loop body"),
                        List.of(),
                        List.of(),
                        List.of()
                )),
                List.of()
        );
        GpuIrAutoVectorizationReadinessSummaryReport report = GpuIrAutoVectorizationReadinessSummaryReport.from(
                preview,
                GpuIrAutoVectorizationRewriteDryRunReport.skipped(
                        "kernel",
                        0,
                        0,
                        0,
                        List.of("Auto-vectorization rewrite dry-run skipped: warnings are present")
                ),
                GpuIrAutoVectorizationResolvedRewriteOperations.empty("kernel")
        );

        assertEquals("notReady/noCandidates", report.verdict());
        assertEquals(1, report.warningCount());
        assertEquals(1, report.proofBundleDiagnosticCount());
        assertEquals(1, report.unsafeProofCount());
        assertEquals(List.of(
                "noRewriteCandidates",
                "warningsPresent",
                "proofBundleNotRewriteSafe",
                "proofDecisionBlocksRewrite",
                "rewritePolicyBlocksRewrite",
                "dryRunNotReady"
        ), report.blockingReasons());
        assertTrue(report.remainingWork().contains("clearAutoVectorizationWarnings"));
        assertTrue(report.remainingWork().contains("clearProofBundleBlockers"));
    }

    @Test
    void reportsDryRunBlockedWhenPreviewProofAndPolicyAreReady() {
        GpuIrAutoVectorizationPreview preview = readyPreview();
        GpuIrAutoVectorizationReadinessSummaryReport report = GpuIrAutoVectorizationReadinessSummaryReport.from(
                preview,
                GpuIrAutoVectorizationRewriteDryRunReport.failed(
                        "kernel",
                        1,
                        1,
                        1,
                        List.of("replacement loopLocation expected=stmt[0] actual=stmt[1]")
                ),
                GpuIrAutoVectorizationResolvedRewriteOperations.empty("kernel")
        );

        assertEquals("notReady/dryRunBlocked", report.verdict());
        assertTrue(report.previewCanApplyRewrite());
        assertTrue(report.proofBundleRewriteSafe());
        assertTrue(report.proofDecisionAllowsRewrite());
        assertTrue(report.rewritePolicyCanRewrite());
        assertFalse(report.dryRunSuccessful());
        assertEquals(List.of("dryRunNotReady"), report.blockingReasons());
        assertEquals(List.of("fixRewriteDryRun"), report.remainingWork());
    }

    @Test
    void reportsReadyWhenPreviewProofDryRunAndResolvedOperationsAreClear() {
        GpuIrAutoVectorizationPreview preview = readyPreview();
        GpuIrAutoVectorizationReadinessSummaryReport report = GpuIrAutoVectorizationReadinessSummaryReport.from(
                preview,
                GpuIrAutoVectorizationRewriteDryRunReport.ready("kernel", 1, 1, 1),
                new GpuIrAutoVectorizationResolvedRewriteOperations(
                        "kernel",
                        List.of(new GpuIrAutoVectorizationResolvedInsertionOperation(
                                "stmt[0]",
                                0,
                                "int4",
                                0,
                                4,
                                1,
                                1,
                                List.of("read input[i=0..3]")
                        )),
                        List.of(new GpuIrAutoVectorizationResolvedReplacementOperation(
                                "stmt[0]",
                                0,
                                "i",
                                0,
                                4,
                                1,
                                1,
                                List.of("write out[i=0..3]")
                        ))
                )
        );

        assertEquals("readyForPrototypeRewrite", report.verdict());
        assertTrue(report.readyForPrototypeRewrite());
        assertEquals(0, report.blockingReasonCount());
        assertEquals(0, report.remainingWorkCount());
        assertEquals("ready", report.blockerExplanation().verdict());
        assertEquals("none", report.blockerExplanation().firstBlockerFamily());
        assertEquals("auto-vectorization readiness ready", report.ciSummaryLine());
    }

    @Test
    void rejectsInvalidMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationReadinessSummaryReport(
                "",
                "notReady/noCandidates",
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                true,
                true,
                false,
                false,
                false,
                "none",
                "allow",
                "none",
                "skipped",
                List.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationReadinessSummaryReport(
                "kernel",
                "",
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                true,
                true,
                false,
                false,
                false,
                "none",
                "allow",
                "none",
                "skipped",
                List.of(),
                List.of()
        ));
    }

    private GpuIrAutoVectorizationPreview readyPreview() {
        return new GpuIrAutoVectorizationPreview(
                "kernel",
                List.of(new GpuIrAutoVectorizationRewriteCandidatePreview(
                        "stmt[0]",
                        "i",
                        0,
                        4,
                        4,
                        1,
                        1,
                        "x4",
                        "int",
                        "int4",
                        List.of("write out[i=0..3]"),
                        List.of("read input[i=0..3]"),
                        List.of(),
                        List.of("out"),
                        List.of("input")
                )),
                List.of(),
                List.of()
        );
    }
}
