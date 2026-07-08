package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrArrayAccess;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.fixedWidthLoop;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationRuleArtifactRunnerTest {
    @Test
    void defaultRunnerExportsValidationRulesArtifactForExistingReport() {
        GpuIrOptimizationValidationReport validationReport = validate(new GpuIrMethod("vectorBlockedKernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                ))),
                new GpuIrReturn(null)
        )));

        GpuIrOptimizationValidationRuleArtifactRunner runner = new GpuIrOptimizationValidationRuleArtifactRunner();
        GpuIrOptimizationValidationRuleArtifactReport artifact = runner.run(validationReport);
        Map<String, String> fields = artifact.artifactFields();

        assertFalse(artifact.passed());
        assertEquals(3, runner.registry().rules().size());
        assertEquals("optimizer.noBlockingDiagnostics", artifact.failedRuleIds());
        assertEquals("vectorBlockedKernel", fields.get("validationRulesMethod"));
        assertEquals("false", fields.get("validationRulesPassed"));
        assertEquals("1", fields.get("validationRulesFailed"));
        assertEquals("true", fields.get("validationRulesHasFailures"));
        assertEquals("0", fields.get("validationRulesWarnings"));
        assertEquals("false", fields.get("validationRulesHasWarnings"));
        assertEquals("1", fields.get("validationRulesBlocking"));
        assertEquals("true", fields.get("validationRulesHasBlockingResults"));
        assertEquals("optimizer.noBlockingDiagnostics", fields.get("validationRulesFailedRuleIds"));
        assertEquals("optimizer.noBlockingDiagnostics", fields.get("validationRulesBlockingRuleIds"));
        assertEquals("{pass=2,fail=1}", fields.get("validationRulesStatusCounts"));
        assertEquals("{safety=1,optimizer=2}", fields.get("validationRulesRuleFamilyCounts"));
        assertEquals("{}", fields.get("validationRulesWarningRuleFamilyCounts"));
        assertEquals("{optimizer=1}", fields.get("validationRulesBlockingRuleFamilyCounts"));
        assertEquals("{optimizer=1}", fields.get("validationRulesFailedRuleFamilyCounts"));
        assertEquals("true", fields.get("validationRulesConsistency.Consistent"));
        assertEquals("0", fields.get("validationRulesConsistency.FailedChecks"));
        assertEquals("[]", fields.get("validationRulesConsistency.FailedCheckList"));
        assertEquals("autoVectorization", fields.get("validationRulesRegistryResult.1.Metadata.optimizerGateSource"));
    }

    @Test
    void customRunnerUsesProvidedRegistryWithoutChangingValidationReport() {
        GpuIrOptimizationValidationReport validationReport = validate(new GpuIrMethod("cleanKernel", List.of(new GpuIrReturn(null))));
        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRuleRegistry.of(List.of(
                GpuIrOptimizationValidationRules.safetyClean()
        ));

        GpuIrOptimizationValidationRuleArtifactRunner runner = new GpuIrOptimizationValidationRuleArtifactRunner(registry);
        GpuIrOptimizationValidationRuleArtifactReport artifact = runner.run(validationReport);
        Map<String, String> fields = artifact.artifactFields("customRules.");

        assertSame(registry, runner.registry());
        assertTrue(artifact.passed());
        assertEquals(1, artifact.ruleCount());
        assertEquals(1, artifact.resultCount());
        assertEquals("true", fields.get("customRules.Passed"));
        assertEquals("1", fields.get("customRules.Rules"));
        assertEquals("0", fields.get("customRules.Blocking"));
        assertEquals("{pass=1}", fields.get("customRules.StatusCounts"));
        assertEquals("{safety=1}", fields.get("customRules.RuleFamilyCounts"));
        assertEquals("{}", fields.get("customRules.WarningRuleFamilyCounts"));
        assertEquals("{}", fields.get("customRules.BlockingRuleFamilyCounts"));
        assertEquals("{}", fields.get("customRules.FailedRuleFamilyCounts"));
        assertEquals("true", fields.get("customRules.Consistency.Consistent"));
        assertEquals("0", fields.get("customRules.Consistency.FailedChecks"));
        assertEquals("safety.clean", fields.get("customRules.RegistryResult.0.RuleId"));
        assertEquals("false", fields.get("customRules.RegistryResult.0.Metadata.hasSafetyError"));
    }

    @Test
    void rejectsNullRegistryAndNullReport() {
        GpuIrOptimizationValidationRuleArtifactRunner runner = new GpuIrOptimizationValidationRuleArtifactRunner();

        assertThrows(NullPointerException.class, () -> new GpuIrOptimizationValidationRuleArtifactRunner(null));
        assertThrows(NullPointerException.class, () -> runner.run(null));
    }
}
