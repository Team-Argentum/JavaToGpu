package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationNoCandidateBucketSummaryReportTest {
    @Test
    void bucketsPureNoCandidateScannerState() {
        GpuIrAutoVectorizationNoCandidateBucketSummaryReport report =
                GpuIrAutoVectorizationNoCandidateBucketSummaryReport.from(noCandidateReadiness());
        Map<String, String> fields = report.artifactFields();

        assertTrue(report.noCandidates());
        assertEquals("bucketed", report.readiness());
        assertEquals(List.of("scannerFoundNoVectorShape"), report.buckets());
        assertEquals("scannerFoundNoVectorShape", report.firstBucket().orElseThrow());
        assertEquals("classifyScalarOrLoopShape", report.firstRemainingWork());
        assertEquals("scannerFoundNoVectorShape", report.firstExample().orElseThrow().bucket());
        assertEquals("method", report.firstExample().orElseThrow().location());
        assertEquals("bucketed", fields.get("autoVectorizationNoCandidateReadiness"));
        assertEquals("true", fields.get("autoVectorizationNoCandidateNoCandidates"));
        assertEquals("0", fields.get("autoVectorizationNoCandidateCandidateCount"));
        assertEquals("[scannerFoundNoVectorShape]", fields.get("autoVectorizationNoCandidateBuckets"));
        assertEquals("{scannerFoundNoVectorShape=1}", fields.get("autoVectorizationNoCandidateBucketCounts"));
        assertEquals("1", fields.get("autoVectorizationNoCandidateBucket.scannerFoundNoVectorShape"));
        assertEquals("scannerFoundNoVectorShape", fields.get("autoVectorizationNoCandidateFirstExampleBucket"));
        assertEquals("method", fields.get("autoVectorizationNoCandidateFirstExampleLocation"));
        assertTrue(fields.get("autoVectorizationNoCandidateFirstExample").contains("kernel@method"));
        assertTrue(fields.get("autoVectorizationNoCandidateExample.scannerFoundNoVectorShape").contains("kernel@method"));
        assertEquals("JTG-IR-AV-001", fields.get("autoVectorizationNoCandidateDiagnosticCode"));
        assertEquals("inspect the first IR example and add a more specific scanner shape bucket if needed",
                fields.get("autoVectorizationNoCandidateFirstHelp"));
        assertTrue(fields.get("autoVectorizationNoCandidateFirstDiagnostic").contains("note[JTG-IR-AV-001]"));
        assertTrue(fields.get("autoVectorizationNoCandidateCiSummaryLine").contains("firstBucket=scannerFoundNoVectorShape"));
        assertTrue(fields.get("autoVectorizationNoCandidateCiSummaryLine").contains("firstExample=kernel@method"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void prefersScannerProvidedShapeBuckets() {
        GpuIrAutoVectorizationPreview preview = new GpuIrAutoVectorizationPreview(
                "kernel",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("scalarOnlyMethod"),
                List.of(new GpuIrAutoVectorizationNoCandidateExample(
                        "scalarOnlyMethod",
                        "kernel",
                        "stmt[0]",
                        "method only contains scalar statements and no array lane work"
                ))
        );
        GpuIrAutoVectorizationReadinessSummaryReport readiness = GpuIrAutoVectorizationReadinessSummaryReport.from(
                preview,
                GpuIrAutoVectorizationRewriteDryRunReport.skipped("kernel", 0, 0, 0, List.of("skipped")),
                GpuIrAutoVectorizationResolvedRewriteOperations.empty("kernel")
        );

        GpuIrAutoVectorizationNoCandidateBucketSummaryReport report =
                GpuIrAutoVectorizationNoCandidateBucketSummaryReport.from(readiness);

        assertEquals(List.of("scalarOnlyMethod"), report.buckets());
        assertEquals("scalarOnlyMethod", report.firstBucket().orElseThrow());
        assertEquals("skipOrDocumentScalarOnlyMethod", report.firstRemainingWork());
        assertEquals("stmt[0]", report.firstExample().orElseThrow().location());
        assertEquals("kernel@stmt[0]: method only contains scalar statements and no array lane work",
                report.artifactFields().get("autoVectorizationNoCandidateFirstExample"));
        assertEquals("JTG-IR-AV-001", report.artifactFields().get("autoVectorizationNoCandidateDiagnosticCode"));
        assertEquals("keep the method scalar-only or introduce lane-wise array work before expecting vectorization",
                report.artifactFields().get("autoVectorizationNoCandidateFirstHelp"));
    }

    @Test
    void reportsNotApplicableWhenCandidatesExist() {
        GpuIrAutoVectorizationNoCandidateBucketSummaryReport report =
                GpuIrAutoVectorizationNoCandidateBucketSummaryReport.from(readyReadiness());

        assertEquals("notApplicable", report.readiness());
        assertEquals(1, report.candidateCount());
        assertEquals(List.of(), report.buckets());
        assertEquals("none", report.firstRemainingWork());
        assertTrue(report.firstExample().isEmpty());
        assertEquals("[]", report.artifactFields().get("autoVectorizationNoCandidateBuckets"));
        assertEquals("none", report.artifactFields().get("autoVectorizationNoCandidateFirstExample"));
        assertEquals("none", report.artifactFields().get("autoVectorizationNoCandidateDiagnosticCode"));
        assertEquals("none", report.artifactFields().get("autoVectorizationNoCandidateFirstHelp"));
        assertEquals("none", report.artifactFields().get("autoVectorizationNoCandidateFirstDiagnostic"));
    }

    @Test
    void rejectsInvalidMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationNoCandidateBucketSummaryReport(
                "",
                0,
                0,
                0,
                0,
                "none",
                "allow",
                "none",
                "skipped",
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationNoCandidateBucketSummaryReport(
                "kernel",
                -1,
                0,
                0,
                0,
                "none",
                "allow",
                "none",
                "skipped",
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> noCandidateReadiness()
                .noCandidateBucketSummaryReport()
                .artifactFields(""));
    }

    private GpuIrAutoVectorizationReadinessSummaryReport noCandidateReadiness() {
        GpuIrAutoVectorizationPreview preview = new GpuIrAutoVectorizationPreview(
                "kernel",
                List.of(),
                List.of(),
                List.of()
        );
        return GpuIrAutoVectorizationReadinessSummaryReport.from(
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
    }

    private GpuIrAutoVectorizationReadinessSummaryReport readyReadiness() {
        GpuIrAutoVectorizationPreview preview = new GpuIrAutoVectorizationPreview(
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
        return GpuIrAutoVectorizationReadinessSummaryReport.from(
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
    }
}
