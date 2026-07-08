package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
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

class GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceRunnerTest {
    private final GpuIrAutoVectorizationCandidateScanner scanner = new GpuIrAutoVectorizationCandidateScanner();
    private final GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceRunner runner =
            new GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceRunner();

    @Test
    void packagesSuccessfulPrototypePrePostRuntimeEquivalenceArtifact() {
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

        GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceReport report = runner.run(
                method,
                preview,
                List.of(inputCase("case-a",
                        new int[]{7, -2, 13, 99},
                        new int[]{1, 4, -3, 11},
                        new int[]{0, 0, 0, 0}
                )),
                List.of("out")
        );

        Map<String, String> fields = report.artifactFields();

        assertTrue(report.successful());
        assertEquals(1, report.preOptimizationRewriteCandidates());
        assertEquals(1, report.postOptimizationAppliedRewrites());
        assertEquals(0, report.diagnosticCount());
        assertEquals("true", fields.get("autoVectorizationPrototypePrePostRuntimeEquivalenceSuccessful"));
        assertEquals("1", fields.get("autoVectorizationPrototypePrePostRuntimeEquivalencePreOptimizationRewriteCandidates"));
        assertEquals("1", fields.get("autoVectorizationPrototypePrePostRuntimeEquivalencePostOptimizationAppliedRewrites"));
        assertEquals("true", fields.get("autoVectorizationPrototypePrePostRuntimeEquivalenceRuntimeEquivalenceSuccessful"));
        assertEquals("{}", fields.get("autoVectorizationPrototypePrePostRuntimeEquivalenceRuntimeEquivalenceDiagnosticFamilyCounts"));
        assertEquals("true", fields.get("autoVectorizationPrototypePrePostRuntimeEquivalenceArtifact.RuntimeEquivalence.Successful"));
        assertTrue(report.summary().contains("diagnosticFamilyCounts={}"));
    }

    @Test
    void packagesFailedPrototypePrePostRuntimeEquivalenceArtifact() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method)).preview();

        GpuIrAutoVectorizationPrototypePrePostRuntimeEquivalenceReport report = runner.run(
                method,
                preview,
                List.of(inputCase("case-a",
                        new int[]{7, -2, 13, 99},
                        new int[]{1, 4, -3, 11},
                        new int[]{0, 0, 0, 0}
                )),
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
    void rejectsInvalidRunnerInputs() {
        GpuIrMethod method = new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ));
        GpuIrAutoVectorizationPreview preview = scanner.scan(compiledMethod(method)).preview();

        assertThrows(IllegalArgumentException.class, () -> runner.run(
                method,
                preview,
                List.of(inputCase("case-a", new int[]{1, 2, 3, 4}, new int[]{0, 0, 0, 0}, new int[]{0, 0, 0, 0})),
                List.of()
        ));
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
}
