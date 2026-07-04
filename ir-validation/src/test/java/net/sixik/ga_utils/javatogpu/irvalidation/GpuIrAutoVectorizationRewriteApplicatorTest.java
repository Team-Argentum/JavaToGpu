package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationRewriteApplicatorTest {
    private final GpuIrAutoVectorizationCandidateScanner scanner = new GpuIrAutoVectorizationCandidateScanner();
    private final GpuIrAutoVectorizationRewriteApplicator applicator = new GpuIrAutoVectorizationRewriteApplicator();

    @Test
    void returnsOriginalMethodForReadyPreviewWithoutMutatingIr() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        )));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();

        GpuIrMethod rewritten = applicator.apply(compiledMethod, preview);

        assertSame(compiledMethod.irMethod(), rewritten);
        assertEquals(compiledMethod.irMethod().statements(), rewritten.statements());
        assertTrue(preview.canApplyRewrite());
    }

    @Test
    void dryRunValidationAcceptsMatchingTypedOperations() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        )));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();

        applicator.dryRunValidate(compiledMethod.irMethod(), preview.rewritePlan());
    }

    @Test
    void dryRunValidationRejectsMissingLoopLocation() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        )));
        GpuIrAutoVectorizationRewritePlan plan = planWithReplacementLoopLocation(compiledMethod, "stmt[1]");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> applicator.dryRunValidate(compiledMethod.irMethod(), plan)
        );

        assertTrue(exception.getMessage().contains("replacement loopLocation"));
        assertTrue(exception.getMessage().contains("expected=stmt[0]"));
        assertTrue(exception.getMessage().contains("actual=stmt[1]"));
    }

    @Test
    void dryRunValidationRejectsNonLoopStatementLocation() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "seed", new GpuIrLiteral("1")),
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        )));
        GpuIrAutoVectorizationRewritePlan plan = planWithInsertionLoopLocation(compiledMethod, "stmt[0]");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> applicator.dryRunValidate(compiledMethod.irMethod(), plan)
        );

        assertTrue(exception.getMessage().contains("insertion loopLocation"));
        assertTrue(exception.getMessage().contains("expected=stmt[1]"));
        assertTrue(exception.getMessage().contains("actual=stmt[0]"));
    }

    @Test
    void dryRunValidationRejectsMismatchedVectorReads() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        )));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();
        GpuIrAutoVectorizationRewriteCandidatePreview candidate = preview.rewriteCandidates().get(0);
        GpuIrAutoVectorizationRewritePlan plan = new GpuIrAutoVectorizationRewritePlan(
                preview.methodName(),
                preview.rewriteCandidates(),
                List.of(new GpuIrAutoVectorizationRewriteInsertionOperation(
                        candidate.loopLocation(),
                        candidate.vectorType(),
                        candidate.startInclusive(),
                        candidate.endExclusive(),
                        List.of("read other[i=0..3]")
                )),
                preview.rewritePlan().replacementOperations(),
                List.of(),
                List.of()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> applicator.dryRunValidate(compiledMethod.irMethod(), plan)
        );

        assertTrue(exception.getMessage().contains("insertion plannedVectorReads"));
        assertTrue(exception.getMessage().contains("read left[i=0..3]"));
        assertTrue(exception.getMessage().contains("read other[i=0..3]"));
    }

    @Test
    void rejectsGuardedPreviewBeforeAnyRewriteAttempt() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                new GpuIrIf(new GpuIrVariableRef("flag"), List.of(), List.of()),
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        )));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> applicator.apply(compiledMethod, preview)
        );

        assertTrue(exception.getMessage().contains("readiness=blockedByGuard"));
        assertTrue(exception.getMessage().contains("policyCanRewrite=false"));
        assertTrue(exception.getMessage().contains("control-flow boundary"));
    }

    @Test
    void rejectsWarnedPreviewBeforeAnyRewriteAttempt() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1")))
                )))
        )));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> applicator.apply(compiledMethod, preview)
        );

        assertTrue(exception.getMessage().contains("readiness=blockedByWarning"));
        assertTrue(exception.getMessage().contains("canApplyRewrite=false"));
        assertTrue(exception.getMessage().contains("crossLaneReadWarnings"));
    }

    @Test
    void rejectsRejectedPreviewBeforeAnyRewriteAttempt() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(5, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        )));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> applicator.apply(compiledMethod, preview)
        );

        assertTrue(exception.getMessage().contains("readiness=rejected"));
        assertTrue(exception.getMessage().contains("UNSUPPORTED_LANE_COUNT"));
    }

    private GpuIrForLoop fixedWidthLoop(int endExclusive, List<GpuIrStatement> body) {
        return new GpuIrForLoop(
                new GpuIrVariableDeclaration("int", "i", new GpuIrLiteral("0")),
                new GpuIrBinary("<", new GpuIrVariableRef("i"), new GpuIrLiteral(Integer.toString(endExclusive))),
                new GpuIrAssignment(new GpuIrVariableRef("i"), new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))),
                body
        );
    }

    private GpuIrCompiledMethod compiledMethod(GpuIrMethod irMethod) {
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                irMethod.name(),
                "void",
                List.of(
                        parameter("flag", "boolean"),
                        parameter("left", "int[]"),
                        parameter("out", "int[]")
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

    private ParsedGpuParameter parameter(String name, String type) {
        return new ParsedGpuParameter(name, type, GpuAddressSpace.GLOBAL, false, List.of());
    }

    private GpuIrAutoVectorizationRewritePlan planWithInsertionLoopLocation(
            GpuIrCompiledMethod compiledMethod,
            String loopLocation
    ) {
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();
        GpuIrAutoVectorizationRewritePlan plan = preview.rewritePlan();
        GpuIrAutoVectorizationRewriteInsertionOperation insertion = plan.insertionOperations().get(0);
        return new GpuIrAutoVectorizationRewritePlan(
                plan.methodName(),
                plan.candidates(),
                List.of(new GpuIrAutoVectorizationRewriteInsertionOperation(
                        loopLocation,
                        insertion.vectorType(),
                        insertion.startInclusive(),
                        insertion.endExclusive(),
                        insertion.plannedVectorReads()
                )),
                plan.replacementOperations(),
                plan.guardDiagnostics(),
                plan.typedGuardDiagnostics()
        );
    }

    private GpuIrAutoVectorizationRewritePlan planWithReplacementLoopLocation(
            GpuIrCompiledMethod compiledMethod,
            String loopLocation
    ) {
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();
        GpuIrAutoVectorizationRewritePlan plan = preview.rewritePlan();
        GpuIrAutoVectorizationRewriteReplacementOperation replacement = plan.replacementOperations().get(0);
        return new GpuIrAutoVectorizationRewritePlan(
                plan.methodName(),
                plan.candidates(),
                plan.insertionOperations(),
                List.of(new GpuIrAutoVectorizationRewriteReplacementOperation(
                        loopLocation,
                        replacement.inductionVariable(),
                        replacement.startInclusive(),
                        replacement.endExclusive(),
                        replacement.plannedVectorWrites()
                )),
                plan.guardDiagnostics(),
                plan.typedGuardDiagnostics()
        );
    }
}
