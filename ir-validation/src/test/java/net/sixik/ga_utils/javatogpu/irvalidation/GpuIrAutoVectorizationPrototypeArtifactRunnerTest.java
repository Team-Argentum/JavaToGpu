package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrForLoop;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationPrototypeArtifactRunnerTest {
    private final GpuIrAutoVectorizationCandidateScanner scanner = new GpuIrAutoVectorizationCandidateScanner();
    private final GpuIrAutoVectorizationPrototypeArtifactRunner runner = new GpuIrAutoVectorizationPrototypeArtifactRunner();

    @Test
    void runsPrototypeRewriteAndPackagesSuccessfulEquivalenceArtifact() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrBinary("+",
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
                        )
                )))
        ));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method)).preview();

        GpuIrAutoVectorizationPrototypeArtifactReport report = runner.run(
                method,
                preview,
                List.of(
                        inputCase("case-a",
                                new int[]{7, -2, 13, 99},
                                new int[]{1, 4, -3, 11},
                                new int[]{0, 0, 0, 0}
                        ),
                        inputCase("case-b",
                                new int[]{0, 8, -5, 2},
                                new int[]{42, -8, 5, 3},
                                new int[]{9, 9, 9, 9}
                        )
                ),
                List.of("out")
        );

        Map<String, String> fields = report.artifactFields();

        assertTrue(report.successful());
        assertEquals(1, report.appliedRewriteCount());
        assertEquals(0, report.diagnosticCount());
        assertArtifactField(fields, "Successful", "true");
        assertArtifactField(fields, "RuntimeEquivalence.InputCases", "2");
        assertArtifactField(fields, "RuntimeEquivalence.ComparedOutputNames", "out");
        assertArtifactField(fields, "RuntimeEquivalence.Payload.ReferenceMode", "original-ir-array-interpreter");
        assertArtifactField(fields, "RuntimeEquivalence.Payload.Case.Count", "2");
        assertArtifactField(fields, "Rewrite.FirstAppliedRewriteExpressionKind", "binaryLaneOp");
        assertArtifactField(fields, "Rewrite.FirstAppliedRewriteBinaryOperator", "+");

        GpuIrRuntimeEquivalenceCaseEvidence caseEvidence = report.runtimeEquivalenceReport().caseEvidence().get(0);
        assertEquals("case-a", caseEvidence.caseName());
        assertEquals("[7, -2, 13, 99]", caseEvidence.inputs().get("left"));
        assertEquals("[8, 2, 10, 110]", caseEvidence.cpuReferenceOutputs().get("out"));
        assertEquals("[8, 2, 10, 110]", caseEvidence.preOptimizationOutputs().get("out"));
        assertEquals("[8, 2, 10, 110]", caseEvidence.postOptimizationOutputs().get("out"));
        assertEquals("exact-int-lane", caseEvidence.tolerances().get("out"));
        assertTrue(caseEvidence.outputEquivalence().get("out"));
        assertArtifactField(
                fields,
                "RuntimeEquivalence.Payload.Case.0.Output.0.PostOptimization",
                "[8, 2, 10, 110]"
        );
    }

    @Test
    void readinessReadyPreviewProducesSuccessfulPrototypeEquivalenceArtifact() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrBinary("+",
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
                        )
                )))
        ));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method)).preview();
        GpuIrAutoVectorizationRewriteApplicator applicator = new GpuIrAutoVectorizationRewriteApplicator();
        GpuIrAutoVectorizationArtifactSnapshot snapshot = new GpuIrAutoVectorizationArtifactSnapshot(
                preview,
                applicator.dryRun(method, preview.rewritePlan()),
                applicator.resolveOperations(method, preview.rewritePlan())
        );
        GpuIrAutoVectorizationReadinessSummaryReport readiness = snapshot.readinessSummaryReport();

        assertEquals("readyForPrototypeRewrite", readiness.verdict());
        assertTrue(readiness.readyForPrototypeRewrite());
        assertEquals(0, readiness.blockingReasonCount());
        assertEquals(0, readiness.remainingWorkCount());

        GpuIrAutoVectorizationPrototypeArtifactReport report = runner.run(
                method,
                preview,
                List.of(
                        inputCase("case-a",
                                new int[]{7, -2, 13, 99},
                                new int[]{1, 4, -3, 11},
                                new int[]{0, 0, 0, 0}
                        ),
                        inputCase("case-b",
                                new int[]{0, 8, -5, 2},
                                new int[]{42, -8, 5, 3},
                                new int[]{9, 9, 9, 9}
                        )
                ),
                List.of("out")
        );
        Map<String, String> fields = report.artifactFields();

        assertTrue(report.successful());
        assertEquals(0, report.diagnosticCount());
        assertArtifactField(fields, "Successful", "true");
        assertArtifactField(fields, "RuntimeEquivalence.Successful", "true");
        assertArtifactField(fields, "RuntimeEquivalence.InputCases", "2");
        assertArtifactField(fields, "RuntimeEquivalence.ComparedOutputNames", "out");
    }

    @Test
    void readinessReadyPreviewProducesSuccessfulMultiOutputPrototypeEquivalenceArtifact() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrBinary("+",
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
                        )
                )))
        ));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method)).preview();
        GpuIrAutoVectorizationRewriteApplicator applicator = new GpuIrAutoVectorizationRewriteApplicator();
        GpuIrAutoVectorizationArtifactSnapshot snapshot = new GpuIrAutoVectorizationArtifactSnapshot(
                preview,
                applicator.dryRun(method, preview.rewritePlan()),
                applicator.resolveOperations(method, preview.rewritePlan())
        );

        assertEquals("readyForPrototypeRewrite", snapshot.readinessSummaryReport().verdict());

        GpuIrAutoVectorizationPrototypeArtifactReport report = runner.run(
                method,
                preview,
                List.of(
                        inputCase("case-a",
                                new int[]{7, -2, 13, 99},
                                new int[]{1, 4, -3, 11},
                                new int[]{0, 0, 0, 0}
                        ),
                        inputCase("case-b",
                                new int[]{0, 8, -5, 2},
                                new int[]{42, -8, 5, 3},
                                new int[]{9, 9, 9, 9}
                        )
                ),
                List.of("out", "right")
        );
        Map<String, String> fields = report.artifactFields();

        assertTrue(report.successful());
        assertEquals(0, report.diagnosticCount());
        assertArtifactField(fields, "RuntimeEquivalence.Successful", "true");
        assertArtifactField(fields, "RuntimeEquivalence.ComparedOutputs", "2");
        assertArtifactField(fields, "RuntimeEquivalence.ComparedOutputNames", "out,right");
        assertArtifactField(fields, "RuntimeEquivalence.InputCases", "2");
    }

    @Test
    void multiOutputPrototypeEquivalenceArtifactReportsMissingComparedOutput() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrBinary("+",
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
                        )
                )))
        ));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method)).preview();

        GpuIrAutoVectorizationPrototypeArtifactReport report = runner.run(
                method,
                preview,
                List.of(inputCase("case-missing-output",
                        new int[]{7, -2, 13, 99},
                        new int[]{1, 4, -3, 11},
                        new int[]{0, 0, 0, 0}
                )),
                List.of("out", "missingOut")
        );
        Map<String, String> fields = report.artifactFields();

        assertFalse(report.successful());
        assertEquals(1, report.diagnosticCount());
        assertArtifactField(fields, "RuntimeEquivalence.Successful", "false");
        assertArtifactField(fields, "RuntimeEquivalence.ComparedOutputs", "2");
        assertArtifactField(fields, "RuntimeEquivalence.ComparedOutputNames", "out,missingOut");
        assertTrue(report.runtimeEquivalenceReport().firstDiagnostic().contains("case case-missing-output output missingOut is missing"));
    }

    @Test
    void multiCasePrototypeEquivalenceArtifactAggregatesComparedOutputDiagnostics() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrBinary("+",
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                                new GpuIrArrayAccess("right", new GpuIrVariableRef("i"))
                        )
                )))
        ));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method)).preview();

        GpuIrAutoVectorizationPrototypeArtifactReport report = runner.run(
                method,
                preview,
                List.of(
                        inputCase("case-a",
                                new int[]{7, -2, 13, 99},
                                new int[]{1, 4, -3, 11},
                                new int[]{0, 0, 0, 0}
                        ),
                        inputCase("case-b",
                                new int[]{0, 8, -5, 2},
                                new int[]{42, -8, 5, 3},
                                new int[]{9, 9, 9, 9}
                        )
                ),
                List.of("missingA", "missingB")
        );
        Map<String, String> fields = report.artifactFields();

        assertFalse(report.successful());
        assertEquals(4, report.diagnosticCount());
        assertArtifactField(fields, "RuntimeEquivalence.Successful", "false");
        assertArtifactField(fields, "RuntimeEquivalence.InputCases", "2");
        assertArtifactField(fields, "RuntimeEquivalence.ComparedOutputs", "2");
        assertArtifactField(fields, "RuntimeEquivalence.Diagnostics", "4");
        assertArtifactField(fields, "RuntimeEquivalence.FirstDiagnostic", "case case-a output missingA is missing");
        assertArtifactField(fields, "RuntimeEquivalence.AllDiagnostics", "case case-a output missingA is missing | case case-a output missingB is missing | case case-b output missingA is missing | case case-b output missingB is missing");
        assertArtifactField(fields, "RuntimeEquivalence.Diagnostic.0", "case case-a output missingA is missing");
        assertArtifactField(fields, "RuntimeEquivalence.Diagnostic.3", "case case-b output missingB is missing");
        assertEquals("case case-b output missingB is missing", report.runtimeEquivalenceReport().diagnostics().get(3));
    }

    @Test
    void warningBlockedPreviewDoesNotReportReadyForPrototypeEquivalenceBridge() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i"))
                )))
        ));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method)).preview();
        GpuIrAutoVectorizationRewriteApplicator applicator = new GpuIrAutoVectorizationRewriteApplicator();
        GpuIrAutoVectorizationArtifactSnapshot snapshot = new GpuIrAutoVectorizationArtifactSnapshot(
                preview,
                applicator.dryRun(method, preview.rewritePlan()),
                applicator.resolveOperations(method, preview.rewritePlan())
        );
        GpuIrAutoVectorizationReadinessSummaryReport readiness = snapshot.readinessSummaryReport();

        assertEquals("notReady/noCandidates", readiness.verdict());
        assertFalse(readiness.readyForPrototypeRewrite());
        assertTrue(readiness.blockingReasons().contains("warningsPresent"));
        assertTrue(readiness.blockingReasons().contains("proofBundleNotRewriteSafe"));
        assertTrue(readiness.remainingWork().contains("clearAutoVectorizationWarnings"));
        assertTrue(readiness.remainingWork().contains("clearProofBundleBlockers"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> runner.run(
                method,
                preview,
                List.of(inputCase("case-warning",
                        new int[]{7, -2, 13, 99},
                        new int[]{1, 4, -3, 11},
                        new int[]{0, 0, 0, 0}
                )),
                List.of("out")
        ));

        assertTrue(exception.getMessage().contains("Auto-vectorization rewrite cannot be applied"));
    }

    @Test
    void guardBlockedPreviewDoesNotReportReadyForPrototypeEquivalenceBridge() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(3, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method)).preview();
        GpuIrAutoVectorizationRewriteApplicator applicator = new GpuIrAutoVectorizationRewriteApplicator();
        GpuIrAutoVectorizationArtifactSnapshot snapshot = new GpuIrAutoVectorizationArtifactSnapshot(
                preview,
                applicator.dryRun(method, preview.rewritePlan()),
                applicator.resolveOperations(method, preview.rewritePlan())
        );
        GpuIrAutoVectorizationReadinessSummaryReport readiness = snapshot.readinessSummaryReport();

        assertEquals("notReady/rewriteGuards", readiness.verdict());
        assertFalse(readiness.readyForPrototypeRewrite());
        assertTrue(readiness.blockingReasons().contains("rewritePlanGuardsPresent"));
        assertTrue(readiness.blockingReasons().contains("proofBundleNotRewriteSafe"));
        assertTrue(readiness.remainingWork().contains("clearRewritePlanGuards"));
        assertTrue(readiness.remainingWork().contains("clearProofBundleBlockers"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> runner.run(
                method,
                preview,
                List.of(inputCase("case-guard",
                        new int[]{7, -2, 13},
                        new int[]{1, 4, -3},
                        new int[]{0, 0, 0}
                )),
                List.of("out")
        ));

        assertTrue(exception.getMessage().contains("Auto-vectorization rewrite cannot be applied"));
    }

    @Test
    void runsPrototypeRewriteAcrossEightLaneVectorFields() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(8, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method)).preview();

        GpuIrAutoVectorizationPrototypeArtifactReport report = runner.run(
                method,
                preview,
                List.of(inputCase("case-eight",
                        new int[]{7, -2, 13, 99, 4, 5, -8, 21},
                        new int[]{0, 0, 0, 0, 0, 0, 0, 0},
                        new int[]{0, 0, 0, 0, 0, 0, 0, 0}
                )),
                List.of("out")
        );

        Map<String, String> fields = report.artifactFields();

        assertTrue(report.successful());
        assertArtifactFieldContains(fields, "Rewrite.FirstAppliedRewrite", "vectorType=Int8");
        assertArtifactField(fields, "Rewrite.FirstAppliedRewriteExpressionKind", "laneCopy");
        assertArtifactField(fields, "RuntimeEquivalence.Successful", "true");
    }

    @Test
    void runsPrototypeRewriteAcrossSixteenLaneVectorFields() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(16, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrBinary("+",
                                new GpuIrArrayAccess("left", new GpuIrVariableRef("i")),
                                new GpuIrLiteral("3")
                        )
                )))
        ));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method)).preview();

        GpuIrAutoVectorizationPrototypeArtifactReport report = runner.run(
                method,
                preview,
                List.of(inputCase("case-sixteen",
                        new int[]{7, -2, 13, 99, 4, 5, -8, 21, 0, 1, 2, 3, 100, -100, 42, -42},
                        new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                        new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}
                )),
                List.of("out")
        );

        Map<String, String> fields = report.artifactFields();

        assertTrue(report.successful());
        assertArtifactFieldContains(fields, "Rewrite.FirstAppliedRewrite", "vectorType=Int16");
        assertArtifactField(fields, "Rewrite.FirstAppliedRewriteExpressionKind", "laneLiteralBinaryOp");
        assertArtifactField(fields, "Rewrite.FirstAppliedRewriteBinaryOperator", "+");
        assertArtifactField(fields, "RuntimeEquivalence.Successful", "true");
    }

    @Test
    void reportsFailedEquivalenceWhenComparedOutputIsMissing() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method)).preview();

        GpuIrAutoVectorizationPrototypeArtifactReport report = runner.run(
                method,
                preview,
                List.of(inputCase("case-a",
                        new int[]{7, -2, 13, 99},
                        new int[]{1, 4, -3, 11},
                        new int[]{0, 0, 0, 0}
                )),
                List.of("missingOut")
        );

        assertFalse(report.successful());
        assertEquals(1, report.diagnosticCount());
        assertTrue(report.runtimeEquivalenceReport().firstDiagnostic().contains("output missingOut is missing"));
    }

    @Test
    void reportsFailedEquivalenceWhenPrototypeExecutionHitsUnsupportedExpression() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method)).preview();
        GpuIrMethod rewrittenWithUnsupportedExpression = new GpuIrMethod("kernel", List.of(new GpuIrAssignment(
                new GpuIrArrayAccess("out", new GpuIrLiteral("0")),
                new GpuIrCast("int", new GpuIrLiteral("1"))
        )));
        GpuIrAutoVectorizationPrototypeArtifactRunner failingRunner =
                new GpuIrAutoVectorizationPrototypeArtifactRunner(rewriterReturning(rewrittenWithUnsupportedExpression));

        GpuIrAutoVectorizationPrototypeArtifactReport report = failingRunner.run(
                method,
                preview,
                List.of(inputCase("case-unsupported",
                        new int[]{7, -2, 13, 99},
                        new int[]{1, 4, -3, 11},
                        new int[]{0, 0, 0, 0}
                )),
                List.of("out")
        );

        assertFalse(report.successful());
        assertEquals(1, report.diagnosticCount());
        assertTrue(report.runtimeEquivalenceReport().firstDiagnostic().contains("case case-unsupported execution failed"));
        assertTrue(report.runtimeEquivalenceReport().firstDiagnostic().contains("Unsupported prototype equivalence expression"));
    }

    @Test
    void reportsFailedEquivalenceWhenPrototypeOutputDiffers() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method)).preview();
        GpuIrMethod rewrittenWithWrongOutput = new GpuIrMethod("kernel", List.of(new GpuIrAssignment(
                new GpuIrArrayAccess("out", new GpuIrLiteral("0")),
                new GpuIrLiteral("123")
        )));
        GpuIrAutoVectorizationPrototypeArtifactRunner failingRunner =
                new GpuIrAutoVectorizationPrototypeArtifactRunner(rewriterReturning(rewrittenWithWrongOutput));

        GpuIrAutoVectorizationPrototypeArtifactReport report = failingRunner.run(
                method,
                preview,
                List.of(inputCase("case-differs",
                        new int[]{7, -2, 13, 99},
                        new int[]{1, 4, -3, 11},
                        new int[]{0, 0, 0, 0}
                )),
                List.of("out")
        );

        assertFalse(report.successful());
        assertEquals(1, report.diagnosticCount());
        assertTrue(report.runtimeEquivalenceReport().firstDiagnostic().contains("case case-differs output out differs"));
        assertTrue(report.runtimeEquivalenceReport().firstDiagnostic().contains("expected=[7, -2, 13, 99]"));
        assertTrue(report.runtimeEquivalenceReport().firstDiagnostic().contains("actual=[123, 0, 0, 0]"));
    }

    @Test
    void rejectsInvalidRunnerInputs() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method)).preview();
        List<GpuIrAutoVectorizationPrototypeInputCase> cases = List.of(inputCase(
                "case-a",
                new int[]{7, -2, 13, 99},
                new int[]{1, 4, -3, 11},
                new int[]{0, 0, 0, 0}
        ));

        assertThrows(IllegalArgumentException.class, () -> runner.run(method, preview, List.of(), List.of("out")));
        assertThrows(IllegalArgumentException.class, () -> runner.run(method, preview, cases, List.of()));
        assertThrows(IllegalArgumentException.class, () -> runner.run(method, preview, cases, List.of("out", "out")));
    }

    private GpuIrAutoVectorizationPrototypeInputCase inputCase(
            String name,
            int[] left,
            int[] right,
            int[] out
    ) {
        return new GpuIrAutoVectorizationPrototypeInputCase(name, Map.of(
                "left", left,
                "right", right,
                "out", out
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

    private GpuIrCompiledMethod compiledMethod(GpuIrMethod irMethod) {
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                irMethod.name(),
                "void",
                List.of(
                        parameter("left", "int[]"),
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

    private void assertArtifactField(Map<String, String> fields, String name, String expected) {
        assertEquals(expected, artifactField(fields, name));
    }

    private void assertArtifactFieldContains(Map<String, String> fields, String name, String expectedSubstring) {
        assertTrue(artifactField(fields, name).contains(expectedSubstring));
    }

    private String artifactField(Map<String, String> fields, String name) {
        return fields.get("autoVectorizationPrototypeArtifact" + name);
    }

    private java.util.function.BiFunction<
            GpuIrMethod,
            GpuIrAutoVectorizationPreview,
            GpuIrAutoVectorizationPrototypeRewriteReport
            > rewriterReturning(GpuIrMethod rewrittenMethod) {
        return (method, preview) -> new GpuIrAutoVectorizationPrototypeRewriteReport(
                rewrittenMethod,
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
