package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrStructInit;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
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
    private final GpuIrAutoVectorizationPrototypeArtifactRunner prototypeArtifactRunner = new GpuIrAutoVectorizationPrototypeArtifactRunner();
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
        GpuIrAutoVectorizationPrototypeArtifactReport artifactReport = prototypeArtifactRunner.run(
                method,
                preview,
                List.of(inputCase("case-a", new int[]{7, -2, 13, 99}, null, new int[]{0, 0, 0, 0})),
                List.of("out")
        );
        Map<String, String> artifactFields = artifactReport.artifactFields();

        assertTrue(artifactReport.successful());
        assertEquals("true", artifactFields.get("autoVectorizationPrototypeArtifactRuntimeEquivalence.Successful"));
        assertEquals("1", artifactFields.get("autoVectorizationPrototypeArtifactRuntimeEquivalence.InputCases"));
        assertEquals("out", artifactFields.get("autoVectorizationPrototypeArtifactRuntimeEquivalence.ComparedOutputNames"));
        assertEquals("1", artifactFields.get("autoVectorizationPrototypeArtifactRuntimeEquivalence.AppliedRewrites"));
        assertEquals(
                "{laneCopy=1,unaryLaneOp=0,binaryLaneOp=0,laneLiteralBinaryOp=0}",
                artifactFields.get("autoVectorizationPrototypeArtifactRuntimeEquivalence.AppliedRewriteFamilies")
        );
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

        GpuIrAutoVectorizationPrototypeArtifactReport artifactReport = prototypeArtifactRunner.run(
                method,
                preview,
                List.of(inputCase(
                        "case-a",
                        new int[]{7, -2, 13, 99},
                        new int[]{1, 4, -3, 11},
                        new int[]{0, 0, 0, 0}
                )),
                List.of("out")
        );

        assertTrue(artifactReport.successful());
        assertFalse(artifactReport.rewriteReport().method().statements().stream().anyMatch(GpuIrForLoop.class::isInstance));
        assertEquals("binaryLaneOp", artifactReport.artifactFields().get("autoVectorizationPrototypeArtifactRewrite.FirstAppliedRewriteExpressionKind"));
        assertEquals("+", artifactReport.artifactFields().get("autoVectorizationPrototypeArtifactRewrite.FirstAppliedRewriteBinaryOperator"));
    }

    @Test
    void rewritePrototypePreservesLaneLiteralBinaryArraySemantics() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrBinary("+",
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                                new GpuIrLiteral("5")
                        )
                )))
        ));
        GpuIrCompiledMethod compiledMethod = compiledMethod(method);
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();

        GpuIrAutoVectorizationPrototypeArtifactReport artifactReport = prototypeArtifactRunner.run(
                method,
                preview,
                List.of(inputCase("case-a", new int[]{7, -2, 13, 99}, null, new int[]{0, 0, 0, 0})),
                List.of("out")
        );

        assertTrue(artifactReport.successful());
        assertFalse(artifactReport.rewriteReport().method().statements().stream().anyMatch(GpuIrForLoop.class::isInstance));
        assertEquals("laneLiteralBinaryOp", artifactReport.artifactFields().get("autoVectorizationPrototypeArtifactRewrite.FirstAppliedRewriteExpressionKind"));
        assertEquals("+", artifactReport.artifactFields().get("autoVectorizationPrototypeArtifactRewrite.FirstAppliedRewriteBinaryOperator"));
    }

    @Test
    void rewritePrototypePreservesLiteralLaneBinaryArraySemantics() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrBinary("-",
                                new GpuIrLiteral("20"),
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                        )
                )))
        ));
        GpuIrCompiledMethod compiledMethod = compiledMethod(method);
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();

        GpuIrAutoVectorizationPrototypeArtifactReport artifactReport = prototypeArtifactRunner.run(
                method,
                preview,
                List.of(inputCase("case-a", new int[]{7, -2, 13, 99}, null, new int[]{0, 0, 0, 0})),
                List.of("out")
        );

        assertTrue(artifactReport.successful());
        assertFalse(artifactReport.rewriteReport().method().statements().stream().anyMatch(GpuIrForLoop.class::isInstance));
        assertEquals("laneLiteralBinaryOp", artifactReport.artifactFields().get("autoVectorizationPrototypeArtifactRewrite.FirstAppliedRewriteExpressionKind"));
        assertEquals("-", artifactReport.artifactFields().get("autoVectorizationPrototypeArtifactRewrite.FirstAppliedRewriteBinaryOperator"));
    }

    @Test
    void rewritePrototypePreservesUnaryLaneArraySemantics() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrUnary("-", new GpuIrArrayAccess("left", new GpuIrVariableRef("i")))
                )))
        ));
        GpuIrCompiledMethod compiledMethod = compiledMethod(method);
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();

        GpuIrAutoVectorizationPrototypeArtifactReport artifactReport = prototypeArtifactRunner.run(
                method,
                preview,
                List.of(inputCase("case-a", new int[]{7, -2, 13, 99}, null, new int[]{0, 0, 0, 0})),
                List.of("out")
        );

        assertTrue(artifactReport.successful());
        assertFalse(artifactReport.rewriteReport().method().statements().stream().anyMatch(GpuIrForLoop.class::isInstance));
        assertEquals("unaryLaneOp", artifactReport.artifactFields().get("autoVectorizationPrototypeArtifactRewrite.FirstAppliedRewriteExpressionKind"));
        assertEquals("-", artifactReport.artifactFields().get("autoVectorizationPrototypeArtifactRewrite.FirstAppliedRewriteUnaryOperator"));
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
        assertEquals(GpuIrAutoVectorizationPrototypeExpressionKind.LANE_COPY, appliedRewrite.expressionKind());
        assertEquals("laneCopy", appliedRewrite.expressionKindArtifactValue());
        assertEquals("", appliedRewrite.binaryOperator());
        assertEquals(1, report.appliedRewriteCount(GpuIrAutoVectorizationPrototypeExpressionKind.LANE_COPY));
        assertEquals(1, report.appliedRewriteCount("laneCopy"));
        assertEquals(0, report.appliedRewriteCount(GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP));
        assertEquals(1, report.appliedRewriteCountsByKind().get(GpuIrAutoVectorizationPrototypeExpressionKind.LANE_COPY));
        assertEquals(1, report.appliedRewriteCountsByArtifactValue().get("laneCopy"));
        assertTrue(report.firstAppliedRewriteSummary().contains("statementIndex=0"));
        assertTrue(report.firstAppliedRewriteSummary().contains("vectorType=Int4"));
        assertTrue(report.firstAppliedRewriteSummary().contains("expressionKind=laneCopy"));
        assertTrue(report.appliedRewriteFamilyCountersSummary().contains("laneCopy=1"));
        assertTrue(report.appliedRewriteFamilyCountersSummary().contains("unaryLaneOp=0"));
        assertTrue(report.summary().contains("appliedRewrites=1"));
        assertTrue(report.summary().contains("appliedRewriteFamilies="));
    }

    @Test
    void rewritePrototypeReportSummarizesBinaryAppliedRewriteShape() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrBinary("^",
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
                        )
                )))
        )));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();

        GpuIrAutoVectorizationPrototypeRewriteReport report = applicator.rewritePrototypeReport(
                compiledMethod.irMethod(),
                preview
        );

        GpuIrAutoVectorizationPrototypeAppliedRewrite appliedRewrite = report.firstAppliedRewrite();
        assertEquals(GpuIrAutoVectorizationPrototypeExpressionKind.BINARY_LANE_OP, appliedRewrite.expressionKind());
        assertEquals("binaryLaneOp", appliedRewrite.expressionKindArtifactValue());
        assertEquals("^", appliedRewrite.binaryOperator());
        assertTrue(report.firstAppliedRewriteSummary().contains("expressionKind=binaryLaneOp"));
        assertTrue(report.firstAppliedRewriteSummary().contains("binaryOperator=^"));
    }

    @Test
    void rewritePrototypeReportSummarizesLaneLiteralAppliedRewriteShape() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrBinary("+",
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                                new GpuIrLiteral("5")
                        )
                )))
        )));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();

        GpuIrAutoVectorizationPrototypeRewriteReport report = applicator.rewritePrototypeReport(
                compiledMethod.irMethod(),
                preview
        );

        GpuIrAutoVectorizationPrototypeAppliedRewrite appliedRewrite = report.firstAppliedRewrite();
        assertEquals(GpuIrAutoVectorizationPrototypeExpressionKind.LANE_LITERAL_BINARY_OP, appliedRewrite.expressionKind());
        assertEquals("laneLiteralBinaryOp", appliedRewrite.expressionKindArtifactValue());
        assertEquals("laneLiteralBinaryOp", appliedRewrite.expressionKindSummary());
        assertEquals("+", appliedRewrite.binaryOperator());
        assertTrue(report.firstAppliedRewriteSummary().contains("expressionKind=laneLiteralBinaryOp"));
        assertTrue(report.firstAppliedRewriteSummary().contains("binaryOperator=+"));
    }

    @Test
    void rewritePrototypeReportSummarizesUnaryAppliedRewriteShape() {
        GpuIrCompiledMethod compiledMethod = compiledMethod(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrUnary("~", new GpuIrArrayAccess("left", new GpuIrVariableRef("i")))
                )))
        )));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod).preview();

        GpuIrAutoVectorizationPrototypeRewriteReport report = applicator.rewritePrototypeReport(
                compiledMethod.irMethod(),
                preview
        );

        GpuIrAutoVectorizationPrototypeAppliedRewrite appliedRewrite = report.firstAppliedRewrite();
        assertEquals(GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP, appliedRewrite.expressionKind());
        assertEquals("unaryLaneOp", appliedRewrite.expressionKindArtifactValue());
        assertEquals("", appliedRewrite.binaryOperator());
        assertEquals("~", appliedRewrite.unaryOperator());
        assertEquals(1, report.appliedRewriteCount(GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP));
        assertEquals(1, report.appliedRewriteCount("unaryLaneOp"));
        assertTrue(report.firstAppliedRewriteSummary().contains("expressionKind=unaryLaneOp"));
        assertTrue(report.firstAppliedRewriteSummary().contains("unaryOperator=~"));
        assertTrue(report.appliedRewriteFamilyCountersSummary().contains("unaryLaneOp=1"));
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

    private GpuIrAutoVectorizationPrototypeInputCase inputCase(
            String name,
            int[] left,
            int[] right,
            int[] out
    ) {
        Map<String, int[]> arrays = new java.util.LinkedHashMap<>();
        arrays.put("left", left);
        if (right != null) {
            arrays.put("right", right);
        }
        arrays.put("out", out);
        return new GpuIrAutoVectorizationPrototypeInputCase(name, arrays);
    }
}
