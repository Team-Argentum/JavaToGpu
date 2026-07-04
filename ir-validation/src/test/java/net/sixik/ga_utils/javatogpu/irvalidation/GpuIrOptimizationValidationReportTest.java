package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationReportTest {
    @Test
    void summariesIncludeDryRunFailureDiagnostics() {
        GpuIrOptimizationValidationReport report = new GpuIrOptimizationValidationReport(
                "kernel",
                Optional.empty(),
                new GpuIrCommonSubexpressionRewritePreview(List.of(), List.of(), List.of()),
                new GpuIrAutoVectorizationPreview("kernel", List.of(), List.of(), List.of()),
                GpuIrAutoVectorizationRewriteDryRunReport.failed(
                        "kernel",
                        1,
                        1,
                        1,
                        List.of("Auto-vectorization rewrite dry-run failed for kernel: replacement loopLocation expected=stmt[0] actual=stmt[1]")
                ),
                GpuIrAutoVectorizationResolvedRewriteOperations.empty("kernel")
        );

        assertFalse(report.autoVectorizationRewriteDryRunSuccessful());
        assertTrue(report.compactSummary().contains("autoVectorizationRewriteDryRunReadiness=failed"));
        assertTrue(report.compactSummary().contains("autoVectorizationRewriteDryRunSuccessful=false"));
        assertTrue(report.compactSummary().contains("autoVectorizationRewriteDryRunDiagnostics=1"));
        assertTrue(report.compactSummary().contains("autoVectorizationResolvedRewriteOperations=0"));
        assertTrue(report.detailedSummary().contains("autoVectorizationRewriteDryRun={"));
        assertTrue(report.detailedSummary().contains("autoVectorizationResolvedRewriteOperations={"));
        assertTrue(report.detailedSummary().contains("successful=false"));
        assertTrue(report.detailedSummary().contains("replacement loopLocation expected=stmt[0] actual=stmt[1]"));
    }

    @Test
    void dryRunReportFactoriesKeepReadinessContractExplicit() {
        GpuIrAutoVectorizationRewriteDryRunReport ready = GpuIrAutoVectorizationRewriteDryRunReport.ready(
                "kernel",
                1,
                1,
                1
        );
        GpuIrAutoVectorizationRewriteDryRunReport failed = GpuIrAutoVectorizationRewriteDryRunReport.failed(
                "kernel",
                1,
                1,
                1,
                List.of("dry-run failed")
        );
        GpuIrAutoVectorizationRewriteDryRunReport skipped = GpuIrAutoVectorizationRewriteDryRunReport.skipped(
                "kernel",
                0,
                0,
                0,
                List.of("dry-run skipped")
        );

        assertEquals(GpuIrAutoVectorizationRewriteDryRunReadiness.READY, ready.readiness());
        assertTrue(ready.successful());
        assertEquals(GpuIrAutoVectorizationRewriteDryRunReadiness.FAILED, failed.readiness());
        assertTrue(failed.hasFailures());
        assertEquals(GpuIrAutoVectorizationRewriteDryRunReadiness.SKIPPED, skipped.readiness());
        assertTrue(skipped.hasFailures());
    }

    @Test
    void readyReportsRejectDiagnostics() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationRewriteDryRunReport(
                "kernel",
                GpuIrAutoVectorizationRewriteDryRunReadiness.READY,
                1,
                1,
                1,
                List.of("unexpected diagnostic")
        ));

        assertTrue(exception.getMessage().contains("ready dry-runs must not have diagnostics"));
    }
}
