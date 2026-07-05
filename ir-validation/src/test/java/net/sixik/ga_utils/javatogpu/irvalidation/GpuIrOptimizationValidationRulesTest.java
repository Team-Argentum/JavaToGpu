package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.fixedWidthLoop;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationRulesTest {
    @Test
    void defaultRegistryPassesForCleanMethodAndExportsStableMetadata() {
        GpuIrOptimizationValidationReport report = validate(new GpuIrMethod("cleanKernel", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1"))),
                new GpuIrReturn(null)
        )));

        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules.defaultRegistry();
        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(report));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals(3, results.size());
        assertEquals("safety.clean", results.get(0).ruleId());
        assertEquals("optimizer.noBlockingDiagnostics", results.get(1).ruleId());
        assertEquals("optimizer.advisoryDiagnostics", results.get(2).ruleId());
        assertTrue(results.get(0).passed());
        assertTrue(results.get(1).passed());
        assertTrue(results.get(2).passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.PASS, results.get(0).status());
        assertFalse(results.get(0).blocking());
        assertEquals("true", fields.get("rulesPassed"));
        assertEquals("pass", fields.get("rulesResult.0.Status"));
        assertEquals("false", fields.get("rulesResult.0.Blocking"));
        assertEquals("cleanKernel", fields.get("rulesResult.0.Metadata.method"));
        assertEquals("false", fields.get("rulesResult.0.Metadata.hasSafetyError"));
        assertEquals("false", fields.get("rulesResult.1.Metadata.hasOptimizerDiagnostics"));
        assertEquals("0", fields.get("rulesResult.1.Metadata.optimizerDiagnostics"));
        assertEquals("false", fields.get("rulesResult.1.Metadata.optimizerGateBlocked"));
        assertEquals("none", fields.get("rulesResult.1.Metadata.optimizerGateSource"));
        assertEquals("none", fields.get("rulesResult.1.Metadata.optimizerGateFamily"));
        assertEquals("pass", fields.get("rulesResult.2.Status"));
        assertEquals("false", fields.get("rulesResult.2.Blocking"));
        assertEquals("false", fields.get("rulesResult.2.Metadata.hasAdvisoryOptimizerSignals"));
    }

    @Test
    void safetyCleanFailsOnlyOnSafetyError() {
        GpuIrOptimizationValidationReport report = validate(new GpuIrMethod("brokenKernel", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrVariableRef("missing"))
        )));

        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules.defaultRegistry();
        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(report));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals("safety.clean", results.get(0).ruleId());
        assertFalse(results.get(0).passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.FAIL, results.get(0).status());
        assertTrue(results.get(0).blocking());
        assertEquals("optimizer.noBlockingDiagnostics", results.get(1).ruleId());
        assertTrue(results.get(1).passed());
        assertEquals("optimizer.advisoryDiagnostics", results.get(2).ruleId());
        assertTrue(results.get(2).passed());
        assertEquals("false", fields.get("rulesPassed"));
        assertEquals("true", fields.get("rulesResult.0.Metadata.hasSafetyError"));
        assertTrue(fields.get("rulesResult.0.Metadata.safetyError").contains("unknown variable reference: missing"));
        assertEquals("false", fields.get("rulesResult.1.Metadata.hasOptimizerDiagnostics"));
        assertEquals("safety", fields.get("rulesResult.1.Metadata.optimizerGateSource"));
        assertEquals("safety.error", fields.get("rulesResult.1.Metadata.optimizerGateFamily"));
        assertEquals("pass", fields.get("rulesResult.2.Status"));
        assertEquals("false", fields.get("rulesResult.2.Metadata.hasAdvisoryOptimizerSignals"));
    }

    @Test
    void optimizerNoBlockingDiagnosticsFailsOnOptimizerGateWithoutTreatingSafetyAsOptimizerFailure() {
        GpuIrOptimizationValidationReport report = validate(new GpuIrMethod("vectorBlockedKernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                ))),
                new GpuIrReturn(null)
        )));

        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules.defaultRegistry();
        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(report));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals("safety.clean", results.get(0).ruleId());
        assertTrue(results.get(0).passed());
        assertEquals("optimizer.noBlockingDiagnostics", results.get(1).ruleId());
        assertFalse(results.get(1).passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.FAIL, results.get(1).status());
        assertTrue(results.get(1).blocking());
        assertEquals("optimizer.advisoryDiagnostics", results.get(2).ruleId());
        assertTrue(results.get(2).passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.PASS, results.get(2).status());
        assertEquals("true", fields.get("rulesResult.1.Metadata.hasOptimizerDiagnostics"));
        assertEquals("fail", fields.get("rulesResult.1.Status"));
        assertEquals("true", fields.get("rulesResult.1.Blocking"));
        assertEquals("1", fields.get("rulesResult.1.Metadata.optimizerDiagnostics"));
        assertEquals("true", fields.get("rulesResult.1.Metadata.optimizerGateBlocked"));
        assertEquals("autoVectorization", fields.get("rulesResult.1.Metadata.optimizerGateSource"));
        assertEquals("guard.earlyExitBoundary", fields.get("rulesResult.1.Metadata.optimizerGateFamily"));
        assertEquals("{autoVectorization=1}", fields.get("rulesResult.1.Metadata.optimizerGateSourceCounts"));
        assertEquals("{guard.earlyExitBoundary=1}", fields.get("rulesResult.1.Metadata.optimizerGateFamilyCounts"));
        assertEquals("pass", fields.get("rulesResult.2.Status"));
        assertEquals("false", fields.get("rulesResult.2.Metadata.hasAdvisoryOptimizerSignals"));
    }

    @Test
    void optimizerAdvisoryDiagnosticsWarnsForNonBlockingOptimizerOpportunities() {
        GpuIrOptimizationValidationReport report = validate(new GpuIrMethod("cseOpportunityKernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("x"))),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("x"))),
                new GpuIrReturn(null)
        )));

        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules.defaultRegistry();
        List<GpuIrOptimizationValidationRuleResult> results = registry.evaluate(new GpuIrOptimizationValidationRuleContext(report));
        Map<String, String> fields = registry.artifactFields("rules", results);

        assertEquals("optimizer.advisoryDiagnostics", results.get(2).ruleId());
        assertTrue(results.get(2).passed());
        assertEquals(GpuIrOptimizationValidationRuleStatus.WARN, results.get(2).status());
        assertFalse(results.get(2).blocking());
        assertEquals("true", fields.get("rulesPassed"));
        assertEquals("warn", fields.get("rulesResult.2.Status"));
        assertEquals("false", fields.get("rulesResult.2.Blocking"));
        assertEquals("true", fields.get("rulesResult.2.Metadata.hasAdvisoryOptimizerSignals"));
        assertEquals("1", fields.get("rulesResult.2.Metadata.cseInsertions"));
        assertEquals("1", fields.get("rulesResult.2.Metadata.cseReplacements"));
    }
}
