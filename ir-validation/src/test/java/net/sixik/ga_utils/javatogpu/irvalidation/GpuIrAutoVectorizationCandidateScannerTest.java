package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrIntrinsicCall;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationCandidateScannerTest {
    private final GpuIrAutoVectorizationCandidateScanner scanner = new GpuIrAutoVectorizationCandidateScanner();

    @Test
    void reportsFixedWidthLaneWiseArrayAssignmentsWithoutMutatingIr() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                                new GpuIrBinary("+",
                                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                                        new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
                                )
                        ),
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("mask", new GpuIrVariableRef("i")),
                                new GpuIrBinary("^",
                                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                                        new GpuIrLiteral("7")
                                )
                        )
                ))
        ));

        GpuIrAutoVectorizationReport report = scanner.scan(method);

        assertTrue(report.hasCandidates());
        assertEquals("kernel", report.methodName());
        assertEquals(1, report.candidateCount());
        assertEquals(2, report.totalAssignmentCount());
        GpuIrAutoVectorizationCandidate candidate = report.candidates().getFirst();
        assertEquals("stmt[0]", candidate.loopLocation());
        assertEquals("i", candidate.inductionVariable());
        assertEquals(0, candidate.startInclusive());
        assertEquals(4, candidate.endExclusive());
        assertEquals(4, candidate.laneCount());
        assertEquals(List.of("out", "mask"), candidate.targetArrays());
        assertEquals(List.of("left", "right", "out"), candidate.sourceArrays());
        assertEquals(List.of("target array `out` is also read in the loop body"), candidate.aliasWarnings());
        assertEquals(List.of(), candidate.crossLaneReadWarnings());
        assertTrue(candidate.hasAliasWarnings());
        assertFalse(candidate.hasCrossLaneReadWarnings());
        assertTrue(candidate.hasWarnings());
        assertTrue(report.hasAliasWarnings());
        assertFalse(report.hasCrossLaneReadWarnings());
        assertTrue(report.hasCandidateWarnings());
        assertEquals(List.of(candidate), report.candidatesWithAliasWarnings());
        assertEquals(List.of(candidate), report.candidatesWithWarnings());
        assertEquals(List.of(GpuIrAutoVectorizationWarningDiagnostic.from(candidate)), report.previewWarningDiagnostics());
        assertEquals(1, report.previewWarningDiagnosticsByLocation().get("stmt[0]").size());
        assertEquals(0, candidate.priorityScore());
        assertFalse(candidate.isRewritePriorityCandidate());
        assertEquals(List.of(), report.rewritePriorityCandidates());
        assertEquals(2, candidate.assignmentCount());
        assertTrue(candidate.summary().contains("lanes=4"));
        assertTrue(candidate.summary().contains("sources=[left, right, out]"));
        assertTrue(candidate.summary().contains("priorityScore=0"));
        assertTrue(report.summary().contains("candidates=1"));
        assertTrue(report.summary().contains("warnings=1"));
    }

    @Test
    void reportsCrossLaneReadWarningsForOffsetArrayIndexes() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                                new GpuIrBinary("+",
                                        new GpuIrArrayAccess("left", new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))),
                                        new GpuIrArrayAccess("right", new GpuIrBinary("-", new GpuIrVariableRef("i"), new GpuIrLiteral("1")))
                                )
                        )
                ))
        ));

        GpuIrAutoVectorizationReport report = scanner.scan(method);
        GpuIrAutoVectorizationCandidate candidate = report.candidates().getFirst();

        assertTrue(report.hasCandidates());
        assertTrue(report.hasCrossLaneReadWarnings());
        assertTrue(report.hasCandidateWarnings());
        assertEquals(List.of(candidate), report.candidatesWithCrossLaneReadWarnings());
        assertEquals(List.of(candidate), report.candidatesWithWarnings());
        assertEquals("stmt[0]", report.previewWarningDiagnostics().getFirst().loopLocation());
        assertTrue(report.previewWarningDiagnostics().getFirst().summary().contains("crossLaneReadWarnings"));
        assertEquals(List.of("left", "right"), candidate.sourceArrays());
        assertEquals(List.of(
                "array `left` is read at cross-lane offset +1 from `i`",
                "array `right` is read at cross-lane offset -1 from `i`"
        ), candidate.crossLaneReadWarnings());
        assertEquals(0, candidate.priorityScore());
        assertFalse(candidate.isRewritePriorityCandidate());
        assertTrue(candidate.hasCrossLaneReadWarnings());
        assertTrue(candidate.summary().contains("crossLaneReadWarnings"));
    }

    @Test
    void ranksWarningFreeCandidatesByDiagnosticPriorityScore() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("outA", new GpuIrVariableRef("i")),
                                new GpuIrBinary("+",
                                        new GpuIrArrayAccess("leftA", new GpuIrVariableRef("i")),
                                        new GpuIrArrayAccess("rightA", new GpuIrVariableRef("i"))
                                )
                        )
                )),
                fixedWidthLoop(8, List.of(
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("outB", new GpuIrVariableRef("i")),
                                new GpuIrBinary("+",
                                        new GpuIrArrayAccess("leftB", new GpuIrVariableRef("i")),
                                        new GpuIrArrayAccess("rightB", new GpuIrVariableRef("i"))
                                )
                        ),
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("maskB", new GpuIrVariableRef("i")),
                                new GpuIrBinary("^",
                                        new GpuIrArrayAccess("bitsB", new GpuIrVariableRef("i")),
                                        new GpuIrLiteral("3")
                                )
                        )
                ))
        ));

        GpuIrAutoVectorizationReport report = scanner.scan(method);
        List<GpuIrAutoVectorizationCandidate> ranked = report.rewritePriorityCandidates();

        assertEquals(2, ranked.size());
        assertEquals("stmt[1]", ranked.get(0).loopLocation());
        assertEquals(16, ranked.get(0).priorityScore());
        assertEquals("stmt[0]", ranked.get(1).loopLocation());
        assertEquals(4, ranked.get(1).priorityScore());
        assertTrue(ranked.stream().allMatch(GpuIrAutoVectorizationCandidate::isRewritePriorityCandidate));
        assertEquals(ranked, report.topCandidates());
    }

    @Test
    void ignoresUnsupportedLaneCountsAndNonZeroStarts() {
        GpuIrMethod unsupportedWidth = new GpuIrMethod("kernel", List.of(fixedWidthLoop(5, List.of(
                new GpuIrAssignment(new GpuIrArrayAccess("out", new GpuIrVariableRef("i")), new GpuIrVariableRef("i"))
        ))));
        GpuIrMethod nonZeroStart = new GpuIrMethod("kernel", List.of(new GpuIrForLoop(
                new GpuIrVariableDeclaration("int", "i", new GpuIrLiteral("1")),
                new GpuIrBinary("<", new GpuIrVariableRef("i"), new GpuIrLiteral("4")),
                new GpuIrAssignment(new GpuIrVariableRef("i"), new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))),
                List.of(new GpuIrAssignment(new GpuIrArrayAccess("out", new GpuIrVariableRef("i")), new GpuIrVariableRef("i")))
        )));

        assertFalse(scanner.scan(unsupportedWidth).hasCandidates());
        assertFalse(scanner.scan(nonZeroStart).hasCandidates());

        GpuIrAutoVectorizationReport unsupportedWidthReport = scanner.scan(unsupportedWidth);
        assertTrue(unsupportedWidthReport.hasRejections());
        assertEquals(1, unsupportedWidthReport.rejectionCount());
        assertEquals(GpuIrAutoVectorizationRejectionReason.UNSUPPORTED_LANE_COUNT, unsupportedWidthReport.rejections().getFirst().reason());
        assertTrue(unsupportedWidthReport.rejections().getFirst().summary().contains("laneCount=5"));

        GpuIrAutoVectorizationReport nonZeroStartReport = scanner.scan(nonZeroStart);
        assertEquals(GpuIrAutoVectorizationRejectionReason.UNSUPPORTED_LOOP_SHAPE, nonZeroStartReport.rejections().getFirst().reason());
    }

    @Test
    void rejectsBodiesWithSideEffectsOrNonLaneTargets() {
        GpuIrMethod sideEffecting = new GpuIrMethod("kernel", List.of(fixedWidthLoop(4, List.of(
                new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrIntrinsicCall(null, "barrier", "barrier(1)", "void", List.of())
                )
        ))));
        GpuIrMethod nonLaneTarget = new GpuIrMethod("kernel", List.of(fixedWidthLoop(4, List.of(
                new GpuIrAssignment(new GpuIrArrayAccess("out", new GpuIrLiteral("0")), new GpuIrVariableRef("i"))
        ))));

        assertFalse(scanner.scan(sideEffecting).hasCandidates());
        assertFalse(scanner.scan(nonLaneTarget).hasCandidates());

        GpuIrAutoVectorizationReport sideEffectingReport = scanner.scan(sideEffecting);
        assertEquals(GpuIrAutoVectorizationRejectionReason.SIDE_EFFECTING_VALUE, sideEffectingReport.rejections().getFirst().reason());

        GpuIrAutoVectorizationReport nonLaneTargetReport = scanner.scan(nonLaneTarget);
        assertEquals(GpuIrAutoVectorizationRejectionReason.NON_LANE_TARGET, nonLaneTargetReport.rejections().getFirst().reason());
        assertEquals(1L, nonLaneTargetReport.rejectionReasonCounts().get(GpuIrAutoVectorizationRejectionReason.NON_LANE_TARGET));
        assertEquals(1, nonLaneTargetReport.rejectionsByReason().get(GpuIrAutoVectorizationRejectionReason.NON_LANE_TARGET).size());
    }

    @Test
    void groupsMultipleRejectedLoopsByReason() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(5, List.of(
                        new GpuIrAssignment(new GpuIrArrayAccess("outA", new GpuIrVariableRef("i")), new GpuIrVariableRef("i"))
                )),
                fixedWidthLoop(7, List.of(
                        new GpuIrAssignment(new GpuIrArrayAccess("outB", new GpuIrVariableRef("i")), new GpuIrVariableRef("i"))
                )),
                fixedWidthLoop(4, List.of(
                        new GpuIrAssignment(new GpuIrArrayAccess("outC", new GpuIrLiteral("0")), new GpuIrVariableRef("i"))
                ))
        ));

        GpuIrAutoVectorizationReport report = scanner.scan(method);

        assertFalse(report.hasCandidates());
        assertTrue(report.hasRejections());
        assertEquals(3, report.rejectionCount());
        assertEquals(2L, report.rejectionReasonCounts().get(GpuIrAutoVectorizationRejectionReason.UNSUPPORTED_LANE_COUNT));
        assertEquals(1L, report.rejectionReasonCounts().get(GpuIrAutoVectorizationRejectionReason.NON_LANE_TARGET));
        assertEquals(2, report.rejectionsByReason().get(GpuIrAutoVectorizationRejectionReason.UNSUPPORTED_LANE_COUNT).size());
        assertTrue(report.summary().contains("rejections=3"));
        assertTrue(report.summary().contains("UNSUPPORTED_LANE_COUNT=2"));
    }

    @Test
    void reportAndCandidateValidateRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationReport("", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationCandidate(
                "stmt[0]",
                "i",
                0,
                4,
                3,
                List.of("out"),
                List.of(),
                List.of(),
                List.of(),
                1
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationRejectionDiagnostic(
                "",
                GpuIrAutoVectorizationRejectionReason.EMPTY_BODY,
                "empty"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationWarningDiagnostic(
                "stmt[0]",
                1,
                List.of(),
                List.of()
        ));
    }

    private GpuIrForLoop fixedWidthLoop(int endExclusive, List<net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement> body) {
        return new GpuIrForLoop(
                new GpuIrVariableDeclaration("int", "i", new GpuIrLiteral("0")),
                new GpuIrBinary("<", new GpuIrVariableRef("i"), new GpuIrLiteral(Integer.toString(endExclusive))),
                new GpuIrAssignment(new GpuIrVariableRef("i"), new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))),
                body
        );
    }
}
