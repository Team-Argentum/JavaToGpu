package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrCompiledMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.passes.GpuIrPassContext;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizerGatePolicyDecisionTest {
    @Test
    void diagnosticModeRecordsGateWithoutBlocking() {
        GpuIrOptimizationValidationReport report = optimizerBlockedReport();

        GpuIrOptimizerGatePolicyDecision decision = report.optimizerGatePolicyDecision(
                GpuIrOptimizationValidationMode.DIAGNOSTIC_ONLY
        );

        assertEquals(GpuIrOptimizationValidationMode.DIAGNOSTIC_ONLY, decision.mode());
        assertEquals(false, decision.blocked());
        assertEquals("none", decision.source());
        assertEquals("none", decision.family());
        assertTrue(decision.summary().contains("diagnostic mode"));
    }

    @Test
    void strictSafetyBlocksOnlySafetyErrors() {
        GpuIrOptimizationValidationReport optimizerBlockedReport = optimizerBlockedReport();
        GpuIrOptimizationValidationReport safetyBlockedReport = safetyBlockedReport();

        GpuIrOptimizerGatePolicyDecision optimizerDecision = optimizerBlockedReport.optimizerGatePolicyDecision(
                GpuIrOptimizationValidationMode.STRICT_FAIL_ON_SAFETY_ERROR
        );
        GpuIrOptimizerGatePolicyDecision safetyDecision = safetyBlockedReport.optimizerGatePolicyDecision(
                GpuIrOptimizationValidationMode.STRICT_FAIL_ON_SAFETY_ERROR
        );

        assertEquals(false, optimizerDecision.blocked());
        assertEquals("none", optimizerDecision.source());
        assertEquals(true, safetyDecision.blocked());
        assertEquals("safety", safetyDecision.source());
        assertEquals("safety.error", safetyDecision.family());
    }

    @Test
    void strictOptimizerBlocksOnUnifiedGateSnapshot() {
        GpuIrOptimizationValidationReport report = optimizerBlockedReport();

        GpuIrOptimizerGatePolicyDecision decision = report.optimizerGatePolicyDecision(
                GpuIrOptimizationValidationMode.STRICT_FAIL_ON_OPTIMIZER_DIAGNOSTICS
        );
        Map<String, String> fields = decision.artifactFields();

        assertEquals(true, decision.blocked());
        assertEquals("autoVectorization", decision.source());
        assertEquals("rejection.UNSUPPORTED_LANE_COUNT", decision.family());
        assertTrue(decision.summary().contains("sourceCounts={autoVectorization=1}"));
        assertEquals("STRICT_FAIL_ON_OPTIMIZER_DIAGNOSTICS", fields.get("optimizerGatePolicyMode"));
        assertEquals("true", fields.get("optimizerGatePolicyBlocked"));
        assertEquals("autoVectorization", fields.get("optimizerGatePolicySource"));
        assertEquals("rejection.UNSUPPORTED_LANE_COUNT", fields.get("optimizerGatePolicyFamily"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void constructorRejectsInvalidAllowedSourceAndBlankFields() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizerGatePolicyDecision(
                GpuIrOptimizationValidationMode.DIAGNOSTIC_ONLY,
                false,
                "autoVectorization",
                "warning.alias",
                "summary"
        ));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizerGatePolicyDecision.allowed(
                GpuIrOptimizationValidationMode.DIAGNOSTIC_ONLY,
                ""
        ));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizerGatePolicyDecision.from(null, optimizerBlockedReport()));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizerGatePolicyDecision.from(
                GpuIrOptimizationValidationMode.DIAGNOSTIC_ONLY,
                null
        ));
    }

    private GpuIrOptimizationValidationReport optimizerBlockedReport() {
        return new GpuIrOptimizationValidationPipeline().validate(context(new GpuIrMethod("kernel", List.of(
                fixedWidthLoop(5, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                )))
        ))));
    }

    private GpuIrOptimizationValidationReport safetyBlockedReport() {
        return new GpuIrOptimizationValidationPipeline().validate(context(new GpuIrMethod("broken", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrVariableRef("missing"))
        ))));
    }

    private GpuIrForLoop fixedWidthLoop(int endExclusive, List<GpuIrStatement> body) {
        return new GpuIrForLoop(
                new GpuIrVariableDeclaration("int", "i", new GpuIrLiteral("0")),
                new GpuIrBinary("<", new GpuIrVariableRef("i"), new GpuIrLiteral(Integer.toString(endExclusive))),
                new GpuIrAssignment(new GpuIrVariableRef("i"), new GpuIrBinary("+", new GpuIrVariableRef("i"), new GpuIrLiteral("1"))),
                body
        );
    }

    private GpuIrPassContext context(GpuIrMethod irMethod) {
        ParsedGpuMethod parsedMethod = new ParsedGpuMethod(
                "KernelOwner",
                "test.KernelOwner",
                irMethod.name(),
                "void",
                List.of(
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
        return new GpuIrPassContext(new GpuIrCompiledMethod(parsedMethod, irMethod, "jtg_" + irMethod.name(), List.of()), List.of(), List.of(), true);
    }
}
