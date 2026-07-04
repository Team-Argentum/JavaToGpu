package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrBreak;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationControlFlowBoundaryAnalyzerTest {
    @Test
    void classifiesControlFlowBoundariesWithStableGuardDiagnostics() {
        GpuIrAutoVectorizationControlFlowBoundaryAnalyzer analyzer = new GpuIrAutoVectorizationControlFlowBoundaryAnalyzer();

        GpuIrAutoVectorizationControlFlowBoundaryReport report = analyzer.analyze(
                "stmt[1]",
                "stmt[0]",
                "previous",
                new GpuIrIf(new GpuIrVariableRef("flag"), List.of(), List.of())
        );

        assertTrue(report.blocksRewrite());
        assertEquals(GpuIrAutoVectorizationControlFlowBoundaryKind.CONTROL_FLOW_BOUNDARY, report.kind());
        assertEquals("previous statement stmt[0] is a control-flow boundary before vector rewrite safety is proven", report.guardMessage());
        assertEquals(GpuIrAutoVectorizationRewriteGuardFamily.CONTROL_FLOW_BOUNDARY, report.guardDiagnostic().family());
        assertEquals("controlFlowBoundary", report.proofSummary().proofKind());
        assertEquals(1, report.proofSummary().diagnosticCount());
        assertEquals("controlFlowBoundary", report.artifactFields().get("autoVectorizationProofControlFlowBoundaryKind"));
        assertEquals("false", report.artifactFields().get("autoVectorizationProofControlFlowBoundaryRewriteSafe"));
        assertEquals("1", report.artifactFields().get("autoVectorizationProofControlFlowBoundaryDiagnostics"));
        assertEquals("1", report.artifactFields().get("autoVectorizationProofControlFlowBoundaryGuardFamily.controlFlowBoundary"));
        assertTrue(report.summary().contains("kind=CONTROL_FLOW_BOUNDARY"));
    }

    @Test
    void classifiesEarlyExitBoundariesWithStableGuardDiagnostics() {
        GpuIrAutoVectorizationControlFlowBoundaryAnalyzer analyzer = new GpuIrAutoVectorizationControlFlowBoundaryAnalyzer();

        GpuIrAutoVectorizationControlFlowBoundaryReport returnReport = analyzer.analyze(
                "stmt[1]",
                "stmt[0]",
                "previous",
                new GpuIrReturn(null)
        );
        GpuIrAutoVectorizationControlFlowBoundaryReport breakReport = analyzer.analyze(
                "stmt[1]",
                "stmt[0]",
                "previous",
                new GpuIrBreak()
        );

        assertTrue(returnReport.blocksRewrite());
        assertEquals(GpuIrAutoVectorizationControlFlowBoundaryKind.EARLY_EXIT_BOUNDARY, returnReport.kind());
        assertEquals("previous statement stmt[0] is an early-exit boundary before vector rewrite safety is proven", returnReport.guardMessage());
        assertEquals(GpuIrAutoVectorizationRewriteGuardFamily.EARLY_EXIT_BOUNDARY, returnReport.guardDiagnostic().family());
        assertEquals(GpuIrAutoVectorizationControlFlowBoundaryKind.EARLY_EXIT_BOUNDARY, breakReport.kind());
    }

    @Test
    void allowsVectorShapedSiblingLoopsWhenPredicateApprovesThem() {
        GpuIrForLoop vectorLoop = fixedWidthLoop(List.of());
        GpuIrAutoVectorizationControlFlowBoundaryAnalyzer analyzer = new GpuIrAutoVectorizationControlFlowBoundaryAnalyzer(loop -> loop == vectorLoop);

        GpuIrAutoVectorizationControlFlowBoundaryReport report = analyzer.analyze(
                "stmt[1]",
                "stmt[0]",
                "previous",
                vectorLoop
        );

        assertFalse(report.blocksRewrite());
        assertEquals(GpuIrAutoVectorizationControlFlowBoundaryKind.NONE, report.kind());
        assertTrue(report.proofSummary().rewriteSafe());
        assertEquals("true", report.artifactFields().get("autoVectorizationProofControlFlowBoundaryRewriteSafe"));
        assertEquals("0", report.artifactFields().get("autoVectorizationProofControlFlowBoundaryDiagnostics"));
        assertThrows(IllegalStateException.class, report::guardDiagnostic);
    }

    @Test
    void treatsUnapprovedSiblingLoopsAsControlFlowBoundaries() {
        GpuIrForLoop loop = fixedWidthLoop(List.of());
        GpuIrAutoVectorizationControlFlowBoundaryAnalyzer analyzer = new GpuIrAutoVectorizationControlFlowBoundaryAnalyzer(loopCandidate -> false);

        GpuIrAutoVectorizationControlFlowBoundaryReport report = analyzer.analyze(
                "stmt[1]",
                "stmt[0]",
                "previous",
                loop
        );

        assertTrue(report.blocksRewrite());
        assertEquals(GpuIrAutoVectorizationControlFlowBoundaryKind.CONTROL_FLOW_BOUNDARY, report.kind());
        assertEquals(GpuIrAutoVectorizationRewriteGuardFamily.CONTROL_FLOW_BOUNDARY, report.guardDiagnostic().family());
    }

    @Test
    void rejectsInvalidBoundaryReportMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationControlFlowBoundaryReport(
                "",
                "stmt[0]",
                "previous",
                GpuIrAutoVectorizationControlFlowBoundaryKind.NONE
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationControlFlowBoundaryReport(
                "stmt[1]",
                "",
                "previous",
                GpuIrAutoVectorizationControlFlowBoundaryKind.NONE
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationControlFlowBoundaryReport(
                "stmt[1]",
                "stmt[0]",
                "",
                GpuIrAutoVectorizationControlFlowBoundaryKind.NONE
        ));
    }

    private GpuIrForLoop fixedWidthLoop(List<GpuIrStatement> body) {
        return new GpuIrForLoop(
                new GpuIrVariableDeclaration("int", "i", new GpuIrLiteral("0")),
                new GpuIrBinary("<", new GpuIrVariableRef("i"), new GpuIrLiteral("4")),
                new GpuIrAssignment(new GpuIrVariableRef("i"), new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))),
                body
        );
    }
}
