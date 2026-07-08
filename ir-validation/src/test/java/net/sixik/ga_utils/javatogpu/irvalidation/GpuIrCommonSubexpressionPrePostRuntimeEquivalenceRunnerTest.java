package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
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

class GpuIrCommonSubexpressionPrePostRuntimeEquivalenceRunnerTest {
    private final GpuIrCommonSubexpressionPrePostRuntimeEquivalenceRunner runner =
            new GpuIrCommonSubexpressionPrePostRuntimeEquivalenceRunner();

    @Test
    void packagesSuccessfulPrePostRuntimeEquivalenceArtifact() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("*",
                        new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y")),
                        new GpuIrLiteral("2"))),
                new GpuIrAssignment(new GpuIrVariableRef("outB"), new GpuIrBinary("-",
                        new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x")),
                        new GpuIrLiteral("3"))),
                new GpuIrReturn(new GpuIrBinary("+", new GpuIrVariableRef("outA"), new GpuIrVariableRef("outB")))
        ));

        GpuIrCommonSubexpressionPrePostRuntimeEquivalenceReport report = runner.run(
                compiledMethod(method),
                List.of(
                        inputCase("case-a", Map.of("x", 7, "y", 11, "outA", 0, "outB", 0)),
                        inputCase("case-b", Map.of("x", -4, "y", 13, "outA", 3, "outB", -9))
                ),
                List.of("outA", "outB", "return")
        );

        Map<String, String> fields = report.artifactFields();

        assertTrue(report.successful());
        assertEquals(1, report.preOptimizationInsertionCandidates());
        assertEquals(1, report.postOptimizationReplacementChecks());
        assertEquals(0, report.diagnosticCount());
        assertEquals("true", fields.get("csePrePostRuntimeEquivalenceSuccessful"));
        assertEquals("1", fields.get("csePrePostRuntimeEquivalencePreOptimizationInsertionCandidates"));
        assertEquals("1", fields.get("csePrePostRuntimeEquivalencePostOptimizationReplacementChecks"));
        assertEquals("true", fields.get("csePrePostRuntimeEquivalenceRuntimeEquivalenceSuccessful"));
        assertEquals("{}", fields.get("csePrePostRuntimeEquivalenceRuntimeEquivalenceDiagnosticFamilyCounts"));
        assertEquals("true", fields.get("csePrePostRuntimeEquivalenceArtifact.RuntimeEquivalence.Successful"));
        assertTrue(report.summary().contains("diagnosticFamilyCounts={}"));
    }

    @Test
    void packagesFailedPrePostRuntimeEquivalenceArtifact() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrBinary("+",
                        new GpuIrVariableRef("x"),
                        new GpuIrVariableRef("y"))),
                new GpuIrReturn(new GpuIrVariableRef("outA"))
        ));

        GpuIrCommonSubexpressionPrePostRuntimeEquivalenceReport report = runner.run(
                compiledMethod(method),
                List.of(inputCase("case-a", Map.of("x", 1, "y", 2, "outA", 0))),
                List.of("missingOut")
        );

        Map<String, String> fields = report.artifactFields("prePost.");

        assertFalse(report.successful());
        assertEquals(1, report.diagnosticCount());
        assertEquals("false", fields.get("prePost.Successful"));
        assertEquals("1", fields.get("prePost.RuntimeEquivalenceDiagnostics"));
        assertEquals("{missingOutput=1}", fields.get("prePost.RuntimeEquivalenceDiagnosticFamilyCounts"));
        assertEquals("1", fields.get("prePost.RuntimeEquivalenceDiagnosticFamily.missingOutput"));
        assertTrue(fields.get("prePost.Summary").contains("diagnostics=1"));
        assertTrue(report.summary().contains("diagnosticFamilyCounts={missingOutput=1}"));
    }

    @Test
    void rejectsInvalidMetadata() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                new GpuIrAssignment(new GpuIrVariableRef("outA"), new GpuIrLiteral("1"))
        ));

        assertThrows(IllegalArgumentException.class, () -> runner.run(
                compiledMethod(method),
                List.of(inputCase("case-a", Map.of("outA", 0))),
                List.of()
        ));
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
