package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrCast;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrTernary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrUnary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
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

class GpuIrCommonSubexpressionArtifactRunnerTest {
    private final GpuIrCommonSubexpressionArtifactRunner runner = new GpuIrCommonSubexpressionArtifactRunner();

    @Test
    void runsCseRewriteAndPackagesSuccessfulEquivalenceArtifact() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("*",
                        new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y")),
                        new GpuIrLiteral("2"))),
                new GpuIrAssignment(new GpuIrVariableRef("outB"), new GpuIrBinary("-",
                        new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")),
                        new GpuIrLiteral("3"))),
                new GpuIrReturn(new GpuIrBinary("+", new GpuIrVariableRef("outA"), new GpuIrVariableRef("outB")))
        ));

        GpuIrCommonSubexpressionArtifactReport report = runner.run(
                compiledMethod(method),
                List.of(
                        inputCase("case-a", Map.of("x", 7, "y", 11, "outA", 0, "outB", 0)),
                        inputCase("case-b", Map.of("x", -4, "y", 13, "outA", 3, "outB", -9))
                ),
                List.of("outA", "outB", "return")
        );

        Map<String, String> fields = report.artifactFields();

        assertTrue(report.successful());
        assertEquals(1, report.insertionCount());
        assertEquals(1, report.replacementCount());
        assertEquals(0, report.diagnosticCount());
        assertEquals("true", fields.get("cseArtifactSuccessful"));
        assertEquals("2", fields.get("cseArtifactRuntimeEquivalence.InputCases"));
        assertEquals("outA,outB,return", fields.get("cseArtifactRuntimeEquivalence.ComparedOutputNames"));
        assertEquals("2", fields.get("cseArtifactRuntimeEquivalence.Payload.Case.Count"));
        assertEquals("original-ir-interpreter", fields.get("cseArtifactRuntimeEquivalence.Payload.ReferenceMode"));
        assertEquals("1", fields.get("cseArtifactSnapshot.Insertions"));
        assertTrue(fields.get("cseArtifactSummary").contains("runtimeEquivalenceSuccessful=true"));

        GpuIrRuntimeEquivalenceCaseEvidence caseEvidence = report.runtimeEquivalenceReport().caseEvidence().get(0);
        assertEquals("case-a", caseEvidence.caseName());
        assertEquals("7", caseEvidence.inputs().get("x"));
        assertEquals("36", caseEvidence.cpuReferenceOutputs().get("outA"));
        assertEquals("36", caseEvidence.preOptimizationOutputs().get("outA"));
        assertEquals("36", caseEvidence.postOptimizationOutputs().get("outA"));
        assertEquals("51", caseEvidence.postOptimizationOutputs().get("return"));
        assertEquals("exact-int", caseEvidence.tolerances().get("outA"));
        assertTrue(caseEvidence.outputEquivalence().get("outA"));
        assertEquals(
                "36",
                fields.get("cseArtifactRuntimeEquivalence.Payload.Case.0.Output.0.CpuReference")
        );
    }

    @Test
    void runsCseRewriteAcrossUnaryCastTernaryExpressions() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrUnary("-",
                        new GpuIrCast("int", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))))),
                new GpuIrAssignment(new GpuIrVariableRef("outB"), new GpuIrTernary(
                        new GpuIrVariableRef("x"),
                        new GpuIrCast("int", new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x"))),
                        new GpuIrUnary("-", new GpuIrLiteral("1"))
                )),
                new GpuIrReturn(new GpuIrBinary("+", new GpuIrVariableRef("outA"), new GpuIrVariableRef("outB")))
        ));

        GpuIrCommonSubexpressionArtifactReport report = runner.run(
                compiledMethod(method),
                List.of(
                        inputCase("case-true", Map.of("x", 5, "y", 9, "outA", 0, "outB", 0)),
                        inputCase("case-false", Map.of("x", 0, "y", 7, "outA", 100, "outB", 200))
                ),
                List.of("outA", "outB", "return")
        );

        assertTrue(report.successful());
        assertTrue(report.insertionCount() > 0);
        assertEquals(0, report.diagnosticCount());
    }

    @Test
    void runsCseRewriteAcrossNestedSimpleArithmeticCanonicalExpressions() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+",
                        new GpuIrVariableRef("x"),
                        new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("z"))
                )),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+",
                        new GpuIrBinary("+", new GpuIrVariableRef("z"), new GpuIrVariableRef("x")),
                        new GpuIrVariableRef("y")
                )),
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("+",
                        new GpuIrVariableRef("first"),
                        new GpuIrVariableRef("second")
                )),
                new GpuIrReturn(new GpuIrVariableRef("outA"))
        ));
        GpuIrCommonSubexpressionArtifactRunner canonicalRunner = new GpuIrCommonSubexpressionArtifactRunner(
                GpuIrCommonSubexpressionScanner.optimizerFocused(),
                new GpuIrCommonSubexpressionRewritePlanner(),
                new GpuIrCommonSubexpressionRewriteApplicator()
        );

        GpuIrCommonSubexpressionArtifactReport report = canonicalRunner.run(
                compiledMethod(method, List.of(parameter("x"), parameter("y"), parameter("z"), parameter("outA"))),
                List.of(
                        inputCase("positive", Map.of("x", 2, "y", 3, "z", 5, "outA", 0)),
                        inputCase("mixed", Map.of("x", -7, "y", 11, "z", 4, "outA", 100)),
                        inputCase("zero", Map.of("x", 0, "y", 0, "z", 0, "outA", -1))
                ),
                List.of("outA", "return")
        );

        Map<String, String> fields = report.artifactFields();

        assertTrue(report.successful());
        assertEquals(1, report.insertionCount());
        assertEquals(1, report.replacementCount());
        assertEquals(0, report.skippedCount());
        assertEquals(0, report.diagnosticCount());
        assertTrue(report.snapshot().preview().insertions().get(0).fingerprint().startsWith("binary_assoc_simple(+"));
        assertEquals("true", fields.get("cseArtifactSuccessful"));
        assertEquals("3", fields.get("cseArtifactRuntimeEquivalence.InputCases"));
        assertEquals("outA,return", fields.get("cseArtifactRuntimeEquivalence.ComparedOutputNames"));
        assertEquals("1", fields.get("cseArtifactRuntimeEquivalence.Plans"));
        assertEquals("1", fields.get("cseArtifactRuntimeEquivalence.Replacements"));
        assertEquals("ready", fields.get("cseArtifactSnapshot.RewritePolicyReadiness"));
        assertEquals("true", fields.get("cseArtifactSnapshot.RewritePolicyCanRewrite"));
    }

    @Test
    void runsCseRewriteAcrossNestedSimpleMultiplicationCanonicalExpressions() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("*",
                        new GpuIrVariableRef("x"),
                        new GpuIrBinary("*", new GpuIrVariableRef("y"), new GpuIrVariableRef("z"))
                )),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("*",
                        new GpuIrBinary("*", new GpuIrVariableRef("z"), new GpuIrVariableRef("x")),
                        new GpuIrVariableRef("y")
                )),
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("+",
                        new GpuIrVariableRef("first"),
                        new GpuIrVariableRef("second")
                )),
                new GpuIrReturn(new GpuIrVariableRef("outA"))
        ));
        GpuIrCommonSubexpressionArtifactRunner canonicalRunner = new GpuIrCommonSubexpressionArtifactRunner(
                GpuIrCommonSubexpressionScanner.optimizerFocused(),
                new GpuIrCommonSubexpressionRewritePlanner(),
                new GpuIrCommonSubexpressionRewriteApplicator()
        );

        GpuIrCommonSubexpressionArtifactReport report = canonicalRunner.run(
                compiledMethod(method, List.of(parameter("x"), parameter("y"), parameter("z"), parameter("outA"))),
                List.of(
                        inputCase("positive", Map.of("x", 2, "y", 3, "z", 5, "outA", 0)),
                        inputCase("mixed", Map.of("x", -7, "y", 11, "z", 4, "outA", 100)),
                        inputCase("zero", Map.of("x", 0, "y", 9, "z", 3, "outA", -1))
                ),
                List.of("outA", "return")
        );

        Map<String, String> fields = report.artifactFields();

        assertTrue(report.successful());
        assertEquals(1, report.insertionCount());
        assertEquals(1, report.replacementCount());
        assertEquals(0, report.skippedCount());
        assertEquals(0, report.diagnosticCount());
        assertTrue(report.snapshot().preview().insertions().get(0).fingerprint().startsWith("binary_assoc_simple(*"));
        assertEquals("true", fields.get("cseArtifactSuccessful"));
        assertEquals("3", fields.get("cseArtifactRuntimeEquivalence.InputCases"));
        assertEquals("outA,return", fields.get("cseArtifactRuntimeEquivalence.ComparedOutputNames"));
        assertEquals("1", fields.get("cseArtifactRuntimeEquivalence.Plans"));
        assertEquals("1", fields.get("cseArtifactRuntimeEquivalence.Replacements"));
        assertEquals("ready", fields.get("cseArtifactSnapshot.RewritePolicyReadiness"));
        assertEquals("true", fields.get("cseArtifactSnapshot.RewritePolicyCanRewrite"));
    }

    @Test
    void reportsFailedEquivalenceWhenComparedOutputIsMissing() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("+",
                        new GpuIrVariableRef("x"),
                        new GpuIrVariableRef("y"))),
                new GpuIrReturn(new GpuIrVariableRef("outA"))
        ));

        GpuIrCommonSubexpressionArtifactReport report = runner.run(
                compiledMethod(method),
                List.of(inputCase("case-a", Map.of("x", 1, "y", 2, "outA", 0))),
                List.of("missingOut")
        );

        assertFalse(report.successful());
        assertEquals(1, report.diagnosticCount());
        assertTrue(report.runtimeEquivalenceReport().firstDiagnostic().contains("output missingOut is missing"));
    }

    @Test
    void reportsFailedEquivalenceWhenPrototypeOutputDiffers() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("+",
                        new GpuIrVariableRef("x"),
                        new GpuIrVariableRef("y"))),
                new GpuIrReturn(new GpuIrVariableRef("outA"))
        ));
        GpuIrMethod rewrittenWithWrongOutput = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrLiteral("123")),
                new GpuIrReturn(new GpuIrVariableRef("outA"))
        ));
        GpuIrCommonSubexpressionArtifactRunner failingRunner = new GpuIrCommonSubexpressionArtifactRunner(
                GpuIrCommonSubexpressionScanner.optimizerFocused(),
                new GpuIrCommonSubexpressionRewritePlanner(),
                (compiledMethod, planReport) -> rewrittenWithWrongOutput
        );

        GpuIrCommonSubexpressionArtifactReport report = failingRunner.run(
                compiledMethod(method),
                List.of(inputCase("case-differs", Map.of("x", 1, "y", 2, "outA", 0))),
                List.of("outA", "return")
        );

        assertFalse(report.successful());
        assertEquals(2, report.diagnosticCount());
        assertTrue(report.runtimeEquivalenceReport().firstDiagnostic().contains("case case-differs output outA differs"));
        assertTrue(report.runtimeEquivalenceReport().firstDiagnostic().contains("expected=3"));
        assertTrue(report.runtimeEquivalenceReport().firstDiagnostic().contains("actual=123"));
    }

    @Test
    void reportsFailedEquivalenceWhenRunnerHitsUnsupportedExpression() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("%",
                        new GpuIrVariableRef("x"),
                        new GpuIrVariableRef("y"))),
                new GpuIrReturn(new GpuIrVariableRef("outA"))
        ));

        GpuIrCommonSubexpressionArtifactReport report = runner.run(
                compiledMethod(method),
                List.of(inputCase("case-unsupported", Map.of("x", 8, "y", 2, "outA", 0))),
                List.of("outA", "return")
        );
        Map<String, String> fields = report.artifactFields("cseRun.");

        assertFalse(report.successful());
        assertEquals(1, report.diagnosticCount());
        assertTrue(report.runtimeEquivalenceReport().firstDiagnostic().contains("case case-unsupported execution failed"));
        assertTrue(report.runtimeEquivalenceReport().firstDiagnostic().contains("Unsupported CSE equivalence binary operator: %"));
        assertEquals(report.runtimeEquivalenceReport().firstDiagnostic(), fields.get("cseRun.RuntimeEquivalence.FirstDiagnostic"));
        assertEquals(report.runtimeEquivalenceReport().firstDiagnostic(), fields.get("cseRun.RuntimeEquivalence.Diagnostic.0"));
    }

    @Test
    void rejectsInvalidRunnerInputs() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrLiteral("1"))
        ));
        GpuIrCompiledMethod compiledMethod = compiledMethod(method);
        List<GpuIrCommonSubexpressionInputCase> cases = List.of(inputCase("case-a", Map.of("outA", 0)));

        assertThrows(IllegalArgumentException.class, () -> runner.run(compiledMethod, List.of(), List.of("outA")));
        assertThrows(IllegalArgumentException.class, () -> runner.run(compiledMethod, cases, List.of()));
        assertThrows(IllegalArgumentException.class, () -> runner.run(compiledMethod, cases, List.of("outA", "outA")));
    }

    @Test
    void inputCaseDefensivelyCopiesValues() {
        Map<String, Integer> values = new java.util.LinkedHashMap<>();
        values.put("x", 1);
        GpuIrCommonSubexpressionInputCase inputCase = inputCase("case-a", values);
        values.put("x", 99);

        assertEquals(1, inputCase.values().get("x"));
        assertThrows(UnsupportedOperationException.class, () -> inputCase.values().put("y", 2));
        assertThrows(IllegalArgumentException.class, () -> inputCase("", Map.of("x", 1)));
        assertThrows(IllegalArgumentException.class, () -> inputCase("case-b", Map.of()));
    }

    private GpuIrCommonSubexpressionInputCase inputCase(String name, Map<String, Integer> values) {
        return new GpuIrCommonSubexpressionInputCase(name, values);
    }

    private GpuIrCompiledMethod compiledMethod(GpuIrMethod method) {
        return compiledMethod(method, List.of(
                parameter("x"),
                parameter("y"),
                parameter("outA"),
                parameter("outB")
        ));
    }

    private GpuIrCompiledMethod compiledMethod(GpuIrMethod method, List<ParsedGpuParameter> parameters) {
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                method.name(),
                "void",
                parameters,
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
        return new GpuIrCompiledMethod(parsedMethod, method, "jtg_kernel", List.of());
    }

    private ParsedGpuParameter parameter(String name) {
        return new ParsedGpuParameter(name, "int", GpuAddressSpace.PRIVATE, false, List.of());
    }
}
