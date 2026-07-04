package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReportTest {
    @Test
    void exposesSuccessfulRuntimeEquivalenceArtifactFields() {
        GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport report =
                GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport.equivalent(
                        rewriteReport(),
                        3,
                        List.of("out", "mask")
                );

        Map<String, String> fields = report.artifactFields();

        assertTrue(report.successful());
        assertFalse(report.hasDiagnostics());
        assertEquals(2, report.comparedOutputCount());
        assertEquals(0, report.diagnosticCount());
        assertEquals("", report.firstDiagnostic());
        assertEquals("true", fields.get("autoVectorizationPrototypeRuntimeEquivalenceSuccessful"));
        assertEquals("true", fields.get("autoVectorizationPrototypeRuntimeEquivalenceEquivalent"));
        assertEquals("3", fields.get("autoVectorizationPrototypeRuntimeEquivalenceInputCases"));
        assertEquals("2", fields.get("autoVectorizationPrototypeRuntimeEquivalenceComparedOutputs"));
        assertEquals("out,mask", fields.get("autoVectorizationPrototypeRuntimeEquivalenceComparedOutputNames"));
        assertEquals("0", fields.get("autoVectorizationPrototypeRuntimeEquivalenceDiagnostics"));
        assertEquals("false", fields.get("autoVectorizationPrototypeRuntimeEquivalenceHasDiagnostics"));
        assertEquals("1", fields.get("autoVectorizationPrototypeRuntimeEquivalenceAppliedRewrites"));
        assertEquals(
                "{laneCopy=1,unaryLaneOp=0,binaryLaneOp=0,laneLiteralBinaryOp=0}",
                fields.get("autoVectorizationPrototypeRuntimeEquivalenceAppliedRewriteFamilies")
        );
        assertFalse(fields.containsKey("autoVectorizationPrototypeRuntimeEquivalenceFirstDiagnostic"));
        assertTrue(report.summary().contains("successful=true"));
        assertTrue(report.summary().contains("inputCases=3"));
        assertTrue(report.summary().contains("appliedRewrites=1"));
    }

    @Test
    void exposesFailedRuntimeEquivalenceArtifactFields() {
        GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport report =
                GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport.failed(
                        rewriteReport(),
                        2,
                        List.of("out"),
                        List.of("case 1 output out differs at lane 2")
                );

        Map<String, String> fields = report.artifactFields("prototypeEquivalence.");

        assertFalse(report.successful());
        assertFalse(report.equivalent());
        assertTrue(report.hasDiagnostics());
        assertEquals(1, report.diagnosticCount());
        assertEquals("case 1 output out differs at lane 2", report.firstDiagnostic());
        assertEquals("false", fields.get("prototypeEquivalence.Successful"));
        assertEquals("false", fields.get("prototypeEquivalence.Equivalent"));
        assertEquals("2", fields.get("prototypeEquivalence.InputCases"));
        assertEquals("1", fields.get("prototypeEquivalence.ComparedOutputs"));
        assertEquals("out", fields.get("prototypeEquivalence.ComparedOutputNames"));
        assertEquals("1", fields.get("prototypeEquivalence.Diagnostics"));
        assertEquals("true", fields.get("prototypeEquivalence.HasDiagnostics"));
        assertEquals("case 1 output out differs at lane 2", fields.get("prototypeEquivalence.FirstDiagnostic"));
        assertTrue(report.summary().contains("successful=false"));
        assertTrue(report.summary().contains("firstDiagnostic=case 1 output out differs at lane 2"));
    }

    @Test
    void treatsDiagnosticsAsUnsuccessfulEvenWhenEquivalentFlagIsTrue() {
        GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport report =
                new GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport(
                        rewriteReport(),
                        true,
                        1,
                        List.of("out"),
                        List.of("diagnostic emitted by external runner")
                );

        assertFalse(report.successful());
        assertTrue(report.equivalent());
        assertEquals("diagnostic emitted by external runner", report.firstDiagnostic());
    }

    @Test
    void returnsImmutableArtifactFieldsAndLists() {
        GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport report =
                GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport.equivalent(
                        rewriteReport(),
                        1,
                        List.of("out")
                );

        assertThrows(UnsupportedOperationException.class, () -> report.comparedOutputs().add("mask"));
        assertThrows(UnsupportedOperationException.class, () -> report.diagnostics().add("failure"));
        assertThrows(UnsupportedOperationException.class, () -> report.artifactFields().put("x", "y"));
    }

    @Test
    void rejectsInvalidRuntimeEquivalenceMetadata() {
        GpuIrAutoVectorizationPrototypeRewriteReport rewriteReport = rewriteReport();

        assertThrows(
                IllegalArgumentException.class,
                () -> GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport.equivalent(
                        rewriteReport,
                        -1,
                        List.of("out")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport.equivalent(
                        rewriteReport,
                        1,
                        List.of("")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport.failed(
                        rewriteReport,
                        1,
                        List.of("out"),
                        java.util.Arrays.asList((String) null)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport.equivalent(
                        rewriteReport,
                        1,
                        List.of("out")
                ).artifactFields("")
        );
    }

    private GpuIrAutoVectorizationPrototypeRewriteReport rewriteReport() {
        return new GpuIrAutoVectorizationPrototypeRewriteReport(
                new GpuIrMethod("kernel", List.of()),
                List.of(new GpuIrAutoVectorizationPrototypeAppliedRewrite(
                        "stmt[0]",
                        0,
                        "Int4",
                        0,
                        4,
                        List.of("out"),
                        List.of("left"),
                        GpuIrAutoVectorizationPrototypeExpressionKind.LANE_COPY,
                        "",
                        ""
                ))
        );
    }
}
