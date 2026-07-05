package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
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

class GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceRunnerTest {
    private final GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceRunner runner =
            new GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceRunner();

    @Test
    void producesSuccessfulEvidenceForSafeLiteralCanonicalizationPreview() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+",
                        new GpuIrVariableRef("x"),
                        new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrLiteral("1"))
                )),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("*",
                        new GpuIrVariableRef("x"),
                        new GpuIrBinary("*", new GpuIrVariableRef("y"), new GpuIrLiteral("2"))
                )),
                new GpuIrAssignment(new GpuIrVariableRef("out"), new GpuIrBinary("+",
                        new GpuIrVariableRef("first"),
                        new GpuIrVariableRef("second")
                )),
                new GpuIrReturn(new GpuIrVariableRef("out"))
        ));

        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport report = runner.run(
                compiledMethod(method),
                List.of(
                        inputCase("positive", Map.of("x", 3, "y", 5, "out", 0)),
                        inputCase("mixed", Map.of("x", -4, "y", 7, "out", 100)),
                        inputCase("zero", Map.of("x", 0, "y", 0, "out", -1))
                ),
                List.of("out", "return")
        );
        Map<String, String> fields = report.artifactFields("literalRuntime");

        assertTrue(report.successful());
        assertTrue(report.equivalent());
        assertFalse(report.hasDiagnostics());
        assertEquals("proven", report.readiness());
        assertEquals(2, report.canonicalizationReport().candidateCount());
        assertTrue(report.numericSemanticsProofReport().fullyProven());
        assertEquals("3", fields.get("literalRuntimeInputCases"));
        assertEquals("2", fields.get("literalRuntimeComparedOutputs"));
        assertEquals("out,return", fields.get("literalRuntimeComparedOutputNames"));
        assertEquals("2", fields.get("literalRuntimePreviewCandidates"));
        assertEquals("2", fields.get("literalRuntimeUniqueCanonicalKeys"));
        assertTrue(fields.get("literalRuntimeCanonicalKeyCounts").contains("literal_assoc_preview(plus:int,int,int;literals=1)=1"));
        assertTrue(fields.get("literalRuntimeCanonicalKeyCounts").contains("literal_assoc_preview(times:int,int,int;literals=2)=1"));
    }

    @Test
    void successfulEvidenceLeavesOnlyFingerprintGateBlocker() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+",
                        new GpuIrVariableRef("x"),
                        new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrLiteral("1"))
                )),
                new GpuIrAssignment(new GpuIrVariableRef("out"), new GpuIrVariableRef("first")),
                new GpuIrReturn(new GpuIrVariableRef("out"))
        ));
        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport runtimeEquivalence = runner.run(
                compiledMethod(method),
                List.of(inputCase("case-a", Map.of("x", 2, "y", 9, "out", 0))),
                List.of("out", "return")
        );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate gate =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationGate.from(
                        runtimeEquivalence.canonicalizationReport(),
                        runtimeEquivalence.numericSemanticsProofReport(),
                        runtimeEquivalence
                );

        assertTrue(runtimeEquivalence.successful());
        assertEquals(List.of("fingerprintIntegrationDisabled"), gate.blockingReasons());
        assertEquals("fingerprintIntegrationDisabled", gate.firstBlockingReason().orElseThrow());
    }

    @Test
    void runArtifactPackagesSuccessfulLiteralEvidence() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+",
                        new GpuIrVariableRef("x"),
                        new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrLiteral("1"))
                )),
                new GpuIrAssignment(new GpuIrVariableRef("out"), new GpuIrVariableRef("first")),
                new GpuIrReturn(new GpuIrVariableRef("out"))
        ));

        GpuIrCommonSubexpressionSimpleArithmeticLiteralArtifactReport artifact = runner.runArtifact(
                compiledMethod(method),
                List.of(inputCase("case-a", Map.of("x", 2, "y", 9, "out", 0))),
                List.of("out", "return")
        );
        Map<String, String> fields = artifact.artifactFields("literalArtifact");

        assertTrue(artifact.successful());
        assertEquals("true", fields.get("literalArtifactSuccessful"));
        assertEquals("true", fields.get("literalArtifactRuntimeEquivalence.Successful"));
        assertEquals("[fingerprintIntegrationDisabled]", fields.get("literalArtifactGate.BlockingReasons"));
        assertEquals("consistent", fields.get("literalArtifactConsistency.Verdict"));
        assertEquals("literal artifact consistency check passed: 12 checks", fields.get("literalArtifactConsistency.CiSummaryLine"));
        assertTrue(fields.get("literalArtifactSummary").contains("runtimeEquivalenceSuccessful=true"));
    }

    @Test
    void reportsFailedEvidenceWhenComparedOutputIsMissing() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+",
                        new GpuIrVariableRef("x"),
                        new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrLiteral("1"))
                )),
                new GpuIrReturn(new GpuIrVariableRef("first"))
        ));

        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport report = runner.run(
                compiledMethod(method),
                List.of(inputCase("case-a", Map.of("x", 1, "y", 2, "out", 0))),
                List.of("missingOut")
        );

        assertFalse(report.successful());
        assertEquals("notProven", report.readiness());
        assertEquals(1, report.diagnosticCount());
        assertTrue(report.firstDiagnostic().orElseThrow().contains("output missingOut is missing"));
    }

    @Test
    void reportsFailedEvidenceWhenPreviewHasNoLiteralCandidates() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("out"), new GpuIrBinary("+",
                        new GpuIrVariableRef("x"),
                        new GpuIrVariableRef("y")
                )),
                new GpuIrReturn(new GpuIrVariableRef("out"))
        ));

        GpuIrCommonSubexpressionSimpleArithmeticLiteralRuntimeEquivalenceReport report = runner.run(
                compiledMethod(method),
                List.of(inputCase("case-a", Map.of("x", 1, "y", 2, "out", 0))),
                List.of("out", "return")
        );

        assertFalse(report.successful());
        assertEquals("none", report.readiness());
        assertEquals(0, report.canonicalizationReport().candidateCount());
        assertTrue(report.diagnostics().contains("literal canonicalization preview has no candidates"));
        assertTrue(report.diagnostics().contains("literal canonicalization numeric semantics proof is not fully proven"));
    }

    @Test
    void rejectsInvalidRunnerInputs() {
        GpuIrCompiledMethod method = compiledMethod(new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("out"), new GpuIrLiteral("1"))
        )));
        List<GpuIrCommonSubexpressionInputCase> cases = List.of(inputCase("case-a", Map.of("out", 0)));

        assertThrows(IllegalArgumentException.class, () -> runner.run(method, List.of(), List.of("out")));
        assertThrows(IllegalArgumentException.class, () -> runner.run(method, cases, List.of()));
        assertThrows(IllegalArgumentException.class, () -> runner.run(method, cases, List.of("out", "out")));
    }

    private GpuIrCommonSubexpressionInputCase inputCase(String name, Map<String, Integer> values) {
        return new GpuIrCommonSubexpressionInputCase(name, values);
    }

    private GpuIrCompiledMethod compiledMethod(GpuIrMethod method) {
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                method.name(),
                "void",
                List.of(parameter("x"), parameter("y"), parameter("out")),
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
