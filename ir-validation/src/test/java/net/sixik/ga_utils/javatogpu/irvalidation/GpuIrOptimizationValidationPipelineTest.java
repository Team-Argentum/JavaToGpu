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
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrStatement;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import net.sixik.ga_utils.javatogpu.frontend.model.GpuAddressSpace;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuMethod;
import net.sixik.ga_utils.javatogpu.frontend.model.ParsedGpuParameter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationPipelineTest {
    private final GpuIrOptimizationValidationPipeline pipeline = new GpuIrOptimizationValidationPipeline();

    @Test
    void combinesSafetyCseAndAutoVectorizationDiagnosticsWithoutMutatingIr() {
        GpuIrMethod irMethod = new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x"))),
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                ))),
                new GpuIrReturn(null)
        ));
        GpuIrPassContext context = context(method(irMethod));

        GpuIrOptimizationValidationReport report = pipeline.validate(context);

        assertFalse(report.hasSafetyError());
        assertTrue(report.commonSubexpressionPreview().hasRewriteWork());
        assertTrue(report.autoVectorizationPreview().hasRewriteCandidates());
        assertTrue(report.hasBlockingDiagnostics());
        assertTrue(report.hasOptimizerDiagnostics());
        assertFalse(report.hasCommonSubexpressionDiagnostics());
        assertTrue(report.hasAutoVectorizationDiagnostics());
        assertEquals(1, report.commonSubexpressionInsertionCount());
        assertEquals(1, report.commonSubexpressionReplacementCount());
        assertEquals(0, report.commonSubexpressionSkippedCount());
        assertEquals(1, report.autoVectorizationRewriteCandidateCount());
        assertEquals(0, report.autoVectorizationWarningCount());
        assertEquals(0, report.autoVectorizationRejectionCount());
        assertEquals(1, report.optimizerDiagnosticCount());
        assertTrue(report.summary().contains("safety=ok"));
        assertTrue(report.summary().contains("optimizerDiagnostics=1"));
        assertTrue(report.summary().contains("cse rewrite preview"));
        assertTrue(report.summary().contains("auto-vectorization preview"));
        assertTrue(report.summary().contains("rewriteCandidates=1"));
        assertTrue(report.summary().contains("insertions=1"));
        assertTrue(report.summary().contains("replacements=1"));
        assertTrue(report.summary().contains("skipped=0"));
        assertTrue(report.summary().contains("warnings=0"));
        assertTrue(report.summary().contains("rejections=0"));
        assertTrue(report.summary().contains("rewritePlanGuards=1"));
        assertTrue(report.summary().contains("earlyExitBoundary=1"));
        assertTrue(report.compactSummary().contains("ir optimization validation method=kernel"));
        assertTrue(report.compactSummary().contains("cseInsertions=1"));
        assertTrue(report.compactSummary().contains("autoVectorizationCandidates=1"));
        assertTrue(report.compactSummary().contains("autoVectorizationRewriteReadiness=blockedByGuard"));
        assertTrue(report.compactSummary().contains("autoVectorizationRejections=0"));
        assertFalse(report.compactSummary().contains("cse rewrite preview"));
        assertTrue(irMethod.statements().get(0) instanceof GpuIrVariableDeclaration);
    }

    @Test
    void capturesSafetyFailuresAndSkipsCsePlanningForMalformedIr() {
        GpuIrPassContext context = context(method(new GpuIrMethod("broken", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrVariableRef("missing"))
        ))));

        GpuIrOptimizationValidationReport report = pipeline.validate(context);

        assertTrue(report.hasSafetyError());
        assertTrue(report.safetyError().orElseThrow().contains("unknown variable reference: missing"));
        assertFalse(report.commonSubexpressionPreview().hasRewriteWork());
        assertEquals(0, report.optimizerDiagnosticCount());
        assertTrue(report.summary().contains("safety=failed"));
        assertTrue(report.summary().contains("unknown variable reference: missing"));
    }

    @Test
    void strictSafetyModeFailsOnlyOnSafetyErrors() {
        GpuIrOptimizationValidationPipeline strictPipeline = new GpuIrOptimizationValidationPipeline(
                GpuIrOptimizationValidationMode.STRICT_FAIL_ON_SAFETY_ERROR
        );
        GpuIrPassContext validContext = context(method(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("y"))),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("y"), new GpuIrVariableRef("x"))),
                new GpuIrReturn(null)
        ))));
        GpuIrPassContext brokenContext = context(method(new GpuIrMethod("broken", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrVariableRef("missing"))
        ))));

        assertDoesNotThrow(() -> strictPipeline.validate(validContext));
        assertTrue(assertThrows(GpuIrPassException.class, () -> strictPipeline.validate(brokenContext))
                .getMessage().contains("unknown variable reference: missing"));
    }

    @Test
    void strictOptimizerModeFailsOnOptimizerDiagnostics() {
        GpuIrOptimizationValidationPipeline strictPipeline = new GpuIrOptimizationValidationPipeline(
                GpuIrOptimizationValidationMode.STRICT_FAIL_ON_OPTIMIZER_DIAGNOSTICS
        );
        GpuIrPassContext context = context(method(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("z"), new GpuIrLiteral("1"))),
                new GpuIrAssignment(new GpuIrVariableRef("z"), new GpuIrLiteral("7")),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("z"), new GpuIrLiteral("1"))),
                fixedWidthLoop(5, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                ))),
                new GpuIrReturn(null)
        ))));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> strictPipeline.validate(context));

        assertTrue(exception.getMessage().contains("IR optimization validation failed for kernel"));
        assertTrue(exception.getMessage().contains("optimizerDiagnostics=2"));
        assertTrue(exception.getMessage().contains("MUTATED_BETWEEN_OCCURRENCES"));
        assertTrue(exception.getMessage().contains("UNSUPPORTED_LANE_COUNT"));
        assertTrue(exception.getMessage().contains("ir optimization validation"));
    }

    @Test
    void strictOptimizerModeIncludesFirstRewriteGuardDiagnostic() {
        GpuIrOptimizationValidationPipeline strictPipeline = new GpuIrOptimizationValidationPipeline(
                GpuIrOptimizationValidationMode.STRICT_FAIL_ON_OPTIMIZER_DIAGNOSTICS
        );
        GpuIrPassContext context = context(method(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                ))),
                new GpuIrReturn(null)
        ))));

        GpuIrPassException exception = assertThrows(GpuIrPassException.class, () -> strictPipeline.validate(context));

        assertTrue(exception.getMessage().contains("IR optimization validation failed for kernel"));
        assertTrue(exception.getMessage().contains("optimizerDiagnostics=1"));
        assertTrue(exception.getMessage().contains("rewritePlanGuardFamilies={earlyExitBoundary=1}"));
        assertTrue(exception.getMessage().contains("next statement stmt[1] is an early-exit boundary"));
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
                        new ParsedGpuParameter("x", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("y", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("z", "int", GpuAddressSpace.PRIVATE, false, List.of()),
                        new ParsedGpuParameter("left", "int[]", GpuAddressSpace.GLOBAL, false, List.of()),
                        new ParsedGpuParameter("out", "int[]", GpuAddressSpace.GLOBAL, false, List.of())
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
