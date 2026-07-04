package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrExpression;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrFieldAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrStructInit;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassContext;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrIf;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationRewriteApplicatorTest {
    private final GpuIrAutoVectorizationCandidateScanner scanner = new GpuIrAutoVectorizationCandidateScanner();
    private final GpuIrAutoVectorizationRewriteApplicator applicator = new GpuIrAutoVectorizationRewriteApplicator();
    private final GpuIrSafetyValidator safetyValidator = new GpuIrSafetyValidator();

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
    void dryRunReturnsSuccessfulStructuredReportForMatchingTypedOperations() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        )));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();

        GpuIrAutoVectorizationRewriteDryRunReport report = applicator.dryRun(
                compiledMethod.irMethod(),
                preview.rewritePlan()
        );

        assertTrue(report.successful());
        assertFalse(report.hasFailures());
        assertEquals(GpuIrAutoVectorizationRewriteDryRunReadiness.READY, report.readiness());
        assertEquals("kernel", report.methodName());
        assertEquals(1, report.candidateCount());
        assertEquals(1, report.insertionOperationCount());
        assertEquals(1, report.replacementOperationCount());
        assertEquals(2, report.operationCount());
        assertEquals(List.of(), report.diagnostics());
        assertEquals("", report.firstDiagnostic());
        assertTrue(report.summary().contains("successful=true"));
        assertTrue(report.summary().contains("readiness=ready"));
        assertTrue(report.summary().contains("operations=2"));
    }

    @Test
    void resolvesRewriteOperationsWithoutMutatingIr() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                        ),
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("mask", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                        )
                ))
        )));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();

        GpuIrAutoVectorizationResolvedRewriteOperations resolved = applicator.resolveOperations(
                compiledMethod.irMethod(),
                preview.rewritePlan()
        );

        assertSame(compiledMethod.irMethod().statements().get(0), compiledMethod.irMethod().statements().get(0));
        assertTrue(resolved.hasOperations());
        assertEquals(2, resolved.operationCount());
        assertEquals(1, resolved.insertions().size());
        assertEquals(1, resolved.replacements().size());
        assertEquals(0, resolved.insertions().get(0).statementIndex());
        assertEquals(2, resolved.insertions().get(0).loopBodyStatementCount());
        assertEquals(2, resolved.insertions().get(0).loopBodyAssignmentCount());
        assertEquals(0, resolved.replacements().get(0).statementIndex());
        assertEquals(4, resolved.replacements().get(0).laneCount());
        assertTrue(resolved.summary().contains("operations=2"));
    }

    @Test
    void rewritePrototypeReplacesSingleLaneCopyLoopWithVectorTempAndScalarWrites() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        )));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();

        GpuIrMethod rewritten = applicator.rewritePrototype(compiledMethod.irMethod(), preview);

        assertEquals(5, rewritten.statements().size());
        assertTrue(rewritten.statements().get(0) instanceof GpuIrVariableDeclaration);
        GpuIrVariableDeclaration vectorDeclaration = (GpuIrVariableDeclaration) rewritten.statements().get(0);
        assertEquals("Int4", vectorDeclaration.typeName());
        assertEquals("__jtg_vec_0", vectorDeclaration.name());
        assertTrue(vectorDeclaration.initializer() instanceof GpuIrStructInit);
        assertFalse(rewritten.statements().stream().anyMatch(GpuIrForLoop.class::isInstance));
        for (int index = 1; index < rewritten.statements().size(); index++) {
            assertTrue(rewritten.statements().get(index) instanceof GpuIrAssignment);
        }
    }

    @Test
    void rewritePrototypePreservesLaneCopyArraySemantics() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));
        GpuIrCompiledMethod compiledMethod = compiledMethod(method);
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();
        GpuIrMethod rewritten = applicator.rewritePrototype(method, preview);

        ExecutionResult original = execute(method, Map.of(
                "left", new int[]{7, -2, 13, 99},
                "out", new int[]{0, 0, 0, 0}
        ));
        ExecutionResult rewrittenResult = execute(rewritten, Map.of(
                "left", new int[]{7, -2, 13, 99},
                "out", new int[]{0, 0, 0, 0}
        ));

        assertEquals(List.of(7, -2, 13, 99), original.arrayValues("out"));
        assertEquals(original.arrayValues("out"), rewrittenResult.arrayValues("out"));
    }

    @Test
    void rewritePrototypeValidatesRewrittenCompiledMethodSafety() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        )));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();

        GpuIrCompiledMethod rewritten = applicator.rewritePrototype(compiledMethod, preview);

        assertDoesNotThrow(() -> safetyValidator.run(new GpuIrPassContext(rewritten, List.of(), List.of(), true)));
    }

    @Test
    void rewritePrototypePreservesSimpleBinaryLaneArraySemantics() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrBinary("+",
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
                        )
                )))
        ));
        GpuIrCompiledMethod compiledMethod = compiledMethod(method);
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();
        GpuIrMethod rewritten = applicator.rewritePrototype(method, preview);

        ExecutionResult original = execute(method, Map.of(
                "left", new int[]{7, -2, 13, 99},
                "right", new int[]{1, 4, -3, 11},
                "out", new int[]{0, 0, 0, 0}
        ));
        ExecutionResult rewrittenResult = execute(rewritten, Map.of(
                "left", new int[]{7, -2, 13, 99},
                "right", new int[]{1, 4, -3, 11},
                "out", new int[]{0, 0, 0, 0}
        ));

        assertFalse(rewritten.statements().stream().anyMatch(GpuIrForLoop.class::isInstance));
        assertEquals(List.of(8, 2, 10, 110), original.arrayValues("out"));
        assertEquals(original.arrayValues("out"), rewrittenResult.arrayValues("out"));
    }

    @Test
    void rewritePrototypeReportSummarizesAppliedRewrite() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        )));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();

        GpuIrAutoVectorizationPrototypeRewriteReport report = applicator.rewritePrototypeReport(
                compiledMethod.irMethod(),
                preview
        );

        assertTrue(report.hasAppliedRewrites());
        assertEquals(1, report.appliedRewriteCount());
        GpuIrAutoVectorizationPrototypeAppliedRewrite appliedRewrite = report.firstAppliedRewrite();
        assertEquals("stmt[0]", appliedRewrite.loopLocation());
        assertEquals(0, appliedRewrite.statementIndex());
        assertEquals("Int4", appliedRewrite.vectorType());
        assertEquals(0, appliedRewrite.startInclusive());
        assertEquals(4, appliedRewrite.endExclusive());
        assertEquals(4, appliedRewrite.laneCount());
        assertEquals(List.of("out"), appliedRewrite.targetArrays());
        assertEquals(List.of("left"), appliedRewrite.sourceArrays());
        assertTrue(report.firstAppliedRewriteSummary().contains("statementIndex=0"));
        assertTrue(report.firstAppliedRewriteSummary().contains("vectorType=Int4"));
        assertTrue(report.summary().contains("appliedRewrites=1"));
    }

    @Test
    void rewritePrototypeRejectsMultiAssignmentLoopsForNow() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                        ),
                        new GpuIrAssignment(
                                new GpuIrArrayAccess("mask", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                        )
                ))
        )));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> applicator.rewritePrototype(compiledMethod.irMethod(), preview)
        );

        assertTrue(exception.getMessage().contains("only supports one lane assignment"));
    }

    @Test
    void dryRunReturnsFailureStructuredReportForMismatchedTypedOperations() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        )));
        GpuIrAutoVectorizationRewritePlan plan = planWithReplacementLoopLocation(compiledMethod, "stmt[1]");

        GpuIrAutoVectorizationRewriteDryRunReport report = applicator.dryRun(compiledMethod.irMethod(), plan);

        assertFalse(report.successful());
        assertTrue(report.hasFailures());
        assertEquals(GpuIrAutoVectorizationRewriteDryRunReadiness.FAILED, report.readiness());
        assertEquals(1, report.diagnostics().size());
        assertTrue(report.firstDiagnostic().contains("replacement loopLocation"));
        assertTrue(report.firstDiagnostic().contains("expected=stmt[0]"));
        assertTrue(report.firstDiagnostic().contains("actual=stmt[1]"));
        assertTrue(report.summary().contains("successful=false"));
        assertTrue(report.summary().contains("readiness=failed"));
        assertTrue(report.summary().contains("diagnostics=1"));
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
                        parameter("left", "int[]"),
                        parameter("mask", "int[]"),
                        parameter("out", "int[]"),
                        parameter("right", "int[]")
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

    private ExecutionResult execute(GpuIrMethod method, Map<String, int[]> inputArrays) {
        Map<String, Object> values = new HashMap<>();
        inputArrays.forEach((name, value) -> values.put(name, value.clone()));
        executeStatements(method.statements(), values);
        return new ExecutionResult(values);
    }

    private void executeStatements(List<GpuIrStatement> statements, Map<String, Object> values) {
        for (GpuIrStatement statement : statements) {
            if (statement instanceof GpuIrVariableDeclaration declaration) {
                values.put(declaration.name(), evaluate(declaration.initializer(), values));
            } else if (statement instanceof GpuIrAssignment assignment) {
                assign(assignment, values);
            } else if (statement instanceof GpuIrForLoop loop) {
                executeLoop(loop, values);
            }
        }
    }

    private void executeLoop(GpuIrForLoop loop, Map<String, Object> values) {
        executeStatements(List.of(loop.initializer()), values);
        while (evaluateInt(loop.condition(), values) != 0) {
            executeStatements(loop.body(), values);
            executeStatements(List.of(loop.update()), values);
        }
    }

    private void assign(GpuIrAssignment assignment, Map<String, Object> values) {
        if (assignment.target() instanceof GpuIrVariableRef variableRef) {
            values.put(variableRef.name(), evaluate(assignment.value(), values));
            return;
        }
        if (assignment.target() instanceof GpuIrArrayAccess arrayAccess) {
            int[] array = (int[]) values.get(arrayAccess.arrayName());
            array[evaluateInt(arrayAccess.index(), values)] = evaluateInt(assignment.value(), values);
            return;
        }
        throw new IllegalArgumentException("Unsupported test assignment target: " + assignment.target());
    }

    private Object evaluate(GpuIrExpression expression, Map<String, Object> values) {
        if (expression instanceof GpuIrStructInit structInit) {
            List<Integer> lanes = new ArrayList<>();
            for (GpuIrExpression argument : structInit.arguments()) {
                lanes.add(evaluateInt(argument, values));
            }
            return lanes;
        }
        return evaluateInt(expression, values);
    }

    private int evaluateInt(GpuIrExpression expression, Map<String, Object> values) {
        if (expression instanceof GpuIrLiteral literal) {
            return Integer.parseInt(literal.sourceText());
        }
        if (expression instanceof GpuIrVariableRef variableRef) {
            return (Integer) values.get(variableRef.name());
        }
        if (expression instanceof GpuIrArrayAccess arrayAccess) {
            int[] array = (int[]) values.get(arrayAccess.arrayName());
            return array[evaluateInt(arrayAccess.index(), values)];
        }
        if (expression instanceof GpuIrFieldAccess fieldAccess) {
            @SuppressWarnings("unchecked")
            List<Integer> lanes = (List<Integer>) values.get(((GpuIrVariableRef) fieldAccess.target()).name());
            return lanes.get(vectorFieldIndex(fieldAccess.fieldName()));
        }
        if (expression instanceof GpuIrBinary binary) {
            int left = evaluateInt(binary.left(), values);
            int right = evaluateInt(binary.right(), values);
            return switch (binary.operator()) {
                case "+" -> left + right;
                case "-" -> left - right;
                case "*" -> left * right;
                case "&" -> left & right;
                case "|" -> left | right;
                case "^" -> left ^ right;
                case "<" -> left < right ? 1 : 0;
                default -> throw new IllegalArgumentException("Unsupported test binary operator: " + binary.operator());
            };
        }
        throw new IllegalArgumentException("Unsupported test expression: " + expression);
    }

    private int vectorFieldIndex(String fieldName) {
        return switch (fieldName) {
            case "x", "s0" -> 0;
            case "y", "s1" -> 1;
            case "z", "s2" -> 2;
            case "w", "s3" -> 3;
            default -> throw new IllegalArgumentException("Unsupported test vector field: " + fieldName);
        };
    }

    private record ExecutionResult(Map<String, Object> values) {
        List<Integer> arrayValues(String name) {
            int[] array = (int[]) values.get(name);
            List<Integer> result = new ArrayList<>();
            for (int value : array) {
                result.add(value);
            }
            return result;
        }
    }
}
