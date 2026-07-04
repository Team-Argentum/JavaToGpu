package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationPrototypeArtifactReportTest {
    @Test
    void exposesCombinedPrototypeArtifactFieldsForSuccessfulEquivalenceRun() {
        GpuIrAutoVectorizationPrototypeArtifactReport report = new GpuIrAutoVectorizationPrototypeArtifactReport(
                GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport.equivalent(
                        rewriteReport(),
                        2,
                        List.of("out")
                )
        );

        Map<String, String> fields = report.artifactFields();

        assertTrue(report.successful());
        assertTrue(report.hasAppliedRewrites());
        assertEquals(1, report.appliedRewriteCount());
        assertEquals(0, report.diagnosticCount());
        assertEquals("true", fields.get("autoVectorizationPrototypeArtifactSuccessful"));
        assertEquals("kernel", fields.get("autoVectorizationPrototypeArtifactMethod"));
        assertEquals("1", fields.get("autoVectorizationPrototypeArtifactAppliedRewrites"));
        assertEquals("true", fields.get("autoVectorizationPrototypeArtifactHasAppliedRewrites"));
        assertEquals("true", fields.get("autoVectorizationPrototypeArtifactRuntimeEquivalenceSuccessful"));
        assertEquals("0", fields.get("autoVectorizationPrototypeArtifactRuntimeEquivalenceDiagnostics"));
        assertEquals("1", fields.get("autoVectorizationPrototypeArtifactRewrite.AppliedRewrites"));
        assertEquals("true", fields.get("autoVectorizationPrototypeArtifactRewrite.HasAppliedRewrites"));
        assertEquals("laneCopy", fields.get("autoVectorizationPrototypeArtifactRewrite.FirstAppliedRewriteExpressionKind"));
        assertEquals("true", fields.get("autoVectorizationPrototypeArtifactRuntimeEquivalence.Successful"));
        assertEquals("2", fields.get("autoVectorizationPrototypeArtifactRuntimeEquivalence.InputCases"));
        assertEquals("out", fields.get("autoVectorizationPrototypeArtifactRuntimeEquivalence.ComparedOutputNames"));
        assertEquals(
                "{laneCopy=1,unaryLaneOp=0,binaryLaneOp=0,laneLiteralBinaryOp=0}",
                fields.get("autoVectorizationPrototypeArtifactRuntimeEquivalence.AppliedRewriteFamilies")
        );
        assertTrue(report.summary().contains("successful=true"));
        assertTrue(report.summary().contains("runtimeEquivalenceSuccessful=true"));
    }

    @Test
    void exposesCombinedPrototypeArtifactFieldsForFailedEquivalenceRun() {
        GpuIrAutoVectorizationPrototypeArtifactReport report = new GpuIrAutoVectorizationPrototypeArtifactReport(
                GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport.failed(
                        rewriteReport(),
                        1,
                        List.of("out"),
                        List.of("out differs at lane 3")
                )
        );

        Map<String, String> fields = report.artifactFields("prototypeArtifact.");

        assertFalse(report.successful());
        assertEquals(1, report.diagnosticCount());
        assertEquals("false", fields.get("prototypeArtifact.Successful"));
        assertEquals("false", fields.get("prototypeArtifact.RuntimeEquivalenceSuccessful"));
        assertEquals("1", fields.get("prototypeArtifact.RuntimeEquivalenceDiagnostics"));
        assertEquals("false", fields.get("prototypeArtifact.RuntimeEquivalence.Successful"));
        assertEquals("out differs at lane 3", fields.get("prototypeArtifact.RuntimeEquivalence.FirstDiagnostic"));
        assertTrue(report.summary().contains("successful=false"));
        assertTrue(report.summary().contains("diagnostics=1"));
    }

    @Test
    void returnsImmutableArtifactFieldsAndRejectsInvalidMetadata() {
        GpuIrAutoVectorizationPrototypeArtifactReport report = new GpuIrAutoVectorizationPrototypeArtifactReport(
                GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport.equivalent(
                        rewriteReport(),
                        1,
                        List.of("out")
                )
        );

        assertThrows(UnsupportedOperationException.class, () -> report.artifactFields().put("x", "y"));
        assertThrows(IllegalArgumentException.class, () -> report.artifactFields(""));
        assertThrows(NullPointerException.class, () -> new GpuIrAutoVectorizationPrototypeArtifactReport(null));
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
