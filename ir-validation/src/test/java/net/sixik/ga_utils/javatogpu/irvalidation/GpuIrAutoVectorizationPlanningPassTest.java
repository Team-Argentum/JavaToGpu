package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassContext;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassException;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationPlanningPassTest {
    private final GpuIrAutoVectorizationPlanningPass pass = new GpuIrAutoVectorizationPlanningPass();

    @Test
    void runScansAndRanksCandidatesWithoutMutatingIr() {
        GpuIrMethod irMethod = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrBinary("+",
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
                        )
                )))
        ));
        GpuIrPassContext context = context(method(irMethod));

        GpuIrAutoVectorizationReport report = pass.scan(context);

        assertEquals(1, report.candidateCount());
        assertEquals(List.of(report.candidates().get(0)), report.rewritePriorityCandidates());
        assertEquals(irMethod.statements(), context.method().irMethod().statements());
        assertDoesNotThrow(() -> pass.run(context));
    }

    @Test
    void previewReturnsUnifiedReadOnlyAggregateWithoutMutatingIr() {
        GpuIrMethod irMethod = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));
        GpuIrPassContext context = context(method(irMethod));

        GpuIrAutoVectorizationPreview preview = pass.preview(context);

        assertTrue(preview.hasRewriteCandidates());
        assertFalse(preview.hasBlockingDiagnostics());
        assertEquals(1, preview.rewriteCandidateCount());
        assertEquals("x4", preview.rewriteCandidates().get(0).vectorWidth());
        assertEquals("int", preview.rewriteCandidates().get(0).scalarElementType());
        assertEquals("int4", preview.rewriteCandidates().get(0).vectorType());
        assertEquals(java.util.Map.of("int4", 1L), preview.vectorTypeCounts());
        assertEquals(List.of("write out[i=0..3]"), preview.rewriteCandidates().get(0).plannedVectorWrites());
        assertEquals(List.of("read left[i=0..3]"), preview.rewriteCandidates().get(0).plannedVectorReads());
        assertEquals(2, preview.rewritePlan().operationCount());
        assertFalse(preview.rewritePlan().hasGuardDiagnostics());
        assertTrue(preview.rewritePlan().insertionPreviews().get(0).contains("type=int4"));
        assertTrue(preview.rewritePlan().replacementPreviews().get(0).contains("write out[i=0..3]"));
        assertEquals(0, preview.warningCount());
        assertEquals(0, preview.rejectionCount());
        assertEquals(1, preview.totalDiagnosticCount());
        assertEquals(java.util.Map.of(), preview.warningFamilyCounts());
        assertTrue(preview.firstBlockingDiagnosticSummary().isEmpty());
        assertEquals(irMethod.statements(), context.method().irMethod().statements());
        assertTrue(preview.summary().contains("rewriteCandidates=1"));
    }

    @Test
    void diagnosticModeDoesNotFailOnWarnedCandidates() {
        GpuIrPassContext context = context(method(methodWithCrossLaneWarning()));

        assertDoesNotThrow(() -> pass.run(context));
    }

    @Test
    void strictModeFailsOnWarnedCandidates() {
        GpuIrAutoVectorizationPlanningPass strictPass = new GpuIrAutoVectorizationPlanningPass(
                new GpuIrAutoVectorizationCandidateScanner(),
                GpuIrAutoVectorizationPlanningMode.STRICT_FAIL_ON_WARNED_CANDIDATES
        );
        GpuIrPassContext context = context(method(methodWithCrossLaneWarning()));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> strictPass.run(context));

        assertTrue(exception.getMessage().contains("IR auto-vectorization planning failed"));
        assertTrue(exception.getMessage().contains("auto-vectorization preview"));
        assertTrue(exception.getMessage().contains("warnings=1"));
        assertTrue(exception.getMessage().contains("warningFamilies"));
        assertTrue(exception.getMessage().contains("first warning"));
        assertTrue(exception.getMessage().contains("auto-vectorization warning"));
        assertTrue(exception.getMessage().contains("crossLaneReadWarnings"));
        assertTrue(exception.getMessage().contains("stmt[0]"));
    }

    @Test
    void warnedCandidateStrictModeIgnoresRejectedLoops() {
        GpuIrAutoVectorizationPlanningPass strictPass = new GpuIrAutoVectorizationPlanningPass(
                new GpuIrAutoVectorizationCandidateScanner(),
                GpuIrAutoVectorizationPlanningMode.STRICT_FAIL_ON_WARNED_CANDIDATES
        );
        GpuIrMethod irMethod = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(5, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));
        GpuIrPassContext context = context(method(irMethod));

        assertDoesNotThrow(() -> strictPass.run(context));
    }

    @Test
    void anyDiagnosticStrictModeFailsOnWarnedCandidates() {
        GpuIrAutoVectorizationPlanningPass strictPass = new GpuIrAutoVectorizationPlanningPass(
                new GpuIrAutoVectorizationCandidateScanner(),
                GpuIrAutoVectorizationPlanningMode.STRICT_FAIL_ON_ANY_DIAGNOSTIC
        );
        GpuIrPassContext context = context(method(methodWithCrossLaneWarning()));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> strictPass.run(context));

        assertTrue(exception.getMessage().contains("auto-vectorization preview"));
        assertTrue(exception.getMessage().contains("warnings=1"));
        assertTrue(exception.getMessage().contains("first blocking diagnostic"));
        assertTrue(exception.getMessage().contains("auto-vectorization warning"));
        assertTrue(exception.getMessage().contains("crossLaneReadWarnings"));
        assertTrue(exception.getMessage().contains("stmt[0]"));
    }

    @Test
    void anyDiagnosticStrictModeFailsOnRejectedLoops() {
        GpuIrAutoVectorizationPlanningPass strictPass = new GpuIrAutoVectorizationPlanningPass(
                new GpuIrAutoVectorizationCandidateScanner(),
                GpuIrAutoVectorizationPlanningMode.STRICT_FAIL_ON_ANY_DIAGNOSTIC
        );
        GpuIrMethod irMethod = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(5, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));
        GpuIrPassContext context = context(method(irMethod));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> strictPass.run(context));

        assertTrue(exception.getMessage().contains("auto-vectorization preview"));
        assertTrue(exception.getMessage().contains("rejections=1"));
        assertTrue(exception.getMessage().contains("rejectionReasons"));
        assertTrue(exception.getMessage().contains("first blocking diagnostic"));
        assertTrue(exception.getMessage().contains("UNSUPPORTED_LANE_COUNT"));
        assertTrue(exception.getMessage().contains("laneCount=5"));
        assertTrue(exception.getMessage().contains("stmt[0]"));
    }

    @Test
    void anyDiagnosticStrictModeReportsIncompleteContextWithoutThrowingNullPointerExceptions() {
        GpuIrAutoVectorizationPlanningPass strictPass = new GpuIrAutoVectorizationPlanningPass(
                new GpuIrAutoVectorizationCandidateScanner(),
                GpuIrAutoVectorizationPlanningMode.STRICT_FAIL_ON_ANY_DIAGNOSTIC
        );

        GpuIrPassException nullContext = assertThrows(GpuIrPassException.class, () -> strictPass.run(null));
        GpuIrPassException missingMethod = assertThrows(
                GpuIrPassException.class,
                () -> strictPass.run(new GpuIrPassContext(null, List.of(), List.of(), true))
        );

        assertTrue(nullContext.getMessage().contains("IR auto-vectorization planning failed for <missing>"));
        assertTrue(nullContext.getMessage().contains("INCOMPLETE_IR"));
        assertTrue(nullContext.getMessage().contains("missing method"));
        assertTrue(missingMethod.getMessage().contains("IR auto-vectorization planning failed for <missing>"));
        assertTrue(missingMethod.getMessage().contains("INCOMPLETE_IR"));
        assertTrue(missingMethod.getMessage().contains("missing method"));
    }

    @Test
    void strictModeReportsWarnedCandidateEvenWhenCleanCandidateRanksHigher() {
        GpuIrAutoVectorizationPlanningPass strictPass = new GpuIrAutoVectorizationPlanningPass(
                new GpuIrAutoVectorizationCandidateScanner(),
                GpuIrAutoVectorizationPlanningMode.STRICT_FAIL_ON_WARNED_CANDIDATES
        );
        GpuIrMethod irMethod = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(8, List.of(
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("cleanOutA", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                        ),
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("cleanOutB", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
                        )
                )),
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("warnedOut", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1")))
                )))
        ));
        GpuIrPassContext context = context(method(irMethod));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> strictPass.run(context));

        assertTrue(exception.getMessage().contains("stmt[1]"));
        assertTrue(exception.getMessage().contains("crossLaneReadWarnings"));
        assertTrue(exception.getMessage().contains("priorityScore=0"));
    }

    private GpuIrMethod methodWithCrossLaneWarning() {
        return new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1")))
                )))
        ));
    }

    private GpuIrForLoop fixedWidthLoop(int endExclusive, List<GpuIrStatement> body) {
        return new GpuIrForLoop(
                new GpuIrVariableDeclaration("int", "i", new GpuIrLiteral("0")),
                new GpuIrBinary("<", new GpuIrVariableRef("i"), new GpuIrLiteral(Integer.toString(endExclusive))),
                new GpuIrAssignment(new GpuIrVariableRef("i"), new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))),
                body
        );
    }

    private GpuIrPassContext context(GpuIrCompiledMethod method) {
        return new GpuIrPassContext(method, List.of(), List.of(), true);
    }

    private GpuIrCompiledMethod method(GpuIrMethod irMethod) {
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                irMethod.name(),
                "void",
                List.of(
                        new ParsedGpuParameter("left", "int[]", GpuAddressSpace.GLOBAL, false, List.of()),
                        new ParsedGpuParameter("right", "int[]", GpuAddressSpace.GLOBAL, false, List.of()),
                        new ParsedGpuParameter("out", "int[]", GpuAddressSpace.GLOBAL, false, List.of()),
                        new ParsedGpuParameter("cleanOutA", "int[]", GpuAddressSpace.GLOBAL, false, List.of()),
                        new ParsedGpuParameter("cleanOutB", "int[]", GpuAddressSpace.GLOBAL, false, List.of()),
                        new ParsedGpuParameter("warnedOut", "int[]", GpuAddressSpace.GLOBAL, false, List.of())
                ),
                List.of(),
                List.of(),
                null,
                false,
                List.of(),
                null,
                "",
                null,
                false
        );
        return new GpuIrCompiledMethod(parsedMethod, irMethod, "jtg_kernel", List.of());
    }
}
