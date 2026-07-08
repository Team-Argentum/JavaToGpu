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
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.rule;
import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationRuleArtifactReportTest {
    @Test
    void exportsDefaultRuleArtifactForCleanValidationReport() {
        GpuIrOptimizationValidationReport validationReport = validate(new GpuIrMethod("cleanKernel", List.of(
                new GpuIrVariableDeclaration("int", "value", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrLiteral("1"))),
                new GpuIrReturn(null)
        )));

        GpuIrOptimizationValidationRuleArtifactReport report = GpuIrOptimizationValidationRuleArtifactReport.evaluateDefault(validationReport);
        Map<String, String> fields = report.artifactFields();

        assertTrue(report.passed());
        assertEquals("pass", report.verdict());
        assertEquals(3, report.ruleCount());
        assertEquals(3, report.resultCount());
        assertEquals(0, report.failedCount());
        assertEquals(0, report.warningCount());
        assertEquals(0, report.blockingCount());
        assertFalse(report.hasBlockingResults());
        assertEquals("", report.failedRuleIds());
        assertEquals("", report.blockingRuleIds());
        assertTrue(report.firstWarningResult().isEmpty());
        assertTrue(report.firstBlockingResult().isEmpty());
        assertEquals(Map.of(GpuIrOptimizationValidationRuleStatus.PASS, 3L), report.statusCounts());
        assertEquals("{pass=3}", report.statusCountsSummary());
        assertEquals(Map.of("safety", 1L, "optimizer", 2L), report.ruleFamilyCounts());
        assertEquals(Map.of(), report.failedRuleFamilyCounts());
        assertEquals("{safety=1,optimizer=2}", report.ruleFamilyCountsSummary());
        assertEquals("{}", report.failedRuleFamilyCountsSummary());
        assertEquals("cleanKernel", fields.get("validationRulesMethod"));
        assertEquals("pass", fields.get("validationRulesVerdict"));
        assertEquals("true", fields.get("validationRulesPassed"));
        assertEquals("3", fields.get("validationRulesRules"));
        assertEquals("3", fields.get("validationRulesResults"));
        assertEquals("0", fields.get("validationRulesFailed"));
        assertEquals("false", fields.get("validationRulesHasFailures"));
        assertEquals("0", fields.get("validationRulesWarnings"));
        assertEquals("false", fields.get("validationRulesHasWarnings"));
        assertEquals("0", fields.get("validationRulesBlocking"));
        assertEquals("false", fields.get("validationRulesHasBlockingResults"));
        assertEquals("", fields.get("validationRulesFailedRuleIds"));
        assertEquals("", fields.get("validationRulesWarningRuleIds"));
        assertEquals("", fields.get("validationRulesBlockingRuleIds"));
        assertEquals(
                "[safety.clean=pass,optimizer.noBlockingDiagnostics=pass,optimizer.advisoryDiagnostics=pass]",
                fields.get("validationRulesRuleIndex")
        );
        assertEquals("[]", fields.get("validationRulesWarningRuleIndex"));
        assertEquals("[]", fields.get("validationRulesBlockingRuleIndex"));
        assertFalse(fields.containsKey("validationRulesFirstFailedRuleId"));
        assertFalse(fields.containsKey("validationRulesFirstWarningRuleId"));
        assertFalse(fields.containsKey("validationRulesFirstBlockingRuleId"));
        assertEquals("{pass=3}", fields.get("validationRulesStatusCounts"));
        assertEquals("3", fields.get("validationRulesStatus.pass"));
        assertEquals("{safety=1,optimizer=2}", fields.get("validationRulesRuleFamilyCounts"));
        assertEquals("1", fields.get("validationRulesRuleFamily.safety"));
        assertEquals("2", fields.get("validationRulesRuleFamily.optimizer"));
        assertEquals("{}", fields.get("validationRulesWarningRuleFamilyCounts"));
        assertEquals("{}", fields.get("validationRulesBlockingRuleFamilyCounts"));
        assertEquals("{}", fields.get("validationRulesFailedRuleFamilyCounts"));
        assertTrue(fields.get("validationRulesSummary").contains("method=cleanKernel"));
        assertTrue(fields.get("validationRulesSummary").contains("statusCounts={pass=3}"));
        assertTrue(fields.get("validationRulesSummary").contains("ruleFamilyCounts={safety=1,optimizer=2}"));
        assertTrue(fields.get("validationRulesSummary").contains("failedRuleFamilyCounts={}"));
        assertEquals("true", fields.get("validationRulesRegistryPassed"));
        assertEquals(
                "[safety.clean=pass,optimizer.noBlockingDiagnostics=pass,optimizer.advisoryDiagnostics=pass]",
                fields.get("validationRulesRegistryRuleIndex")
        );
        assertEquals("[]", fields.get("validationRulesRegistryWarningRuleIndex"));
        assertEquals("[]", fields.get("validationRulesRegistryBlockingRuleIndex"));
        assertEquals("safety.clean", fields.get("validationRulesRegistryResult.0.RuleId"));
        assertEquals("true", fields.get("validationRulesRegistryResult.0.Passed"));
        assertEquals("optimizer.noBlockingDiagnostics", fields.get("validationRulesRegistryResult.1.RuleId"));
        assertEquals("false", fields.get("validationRulesRegistryResult.1.Metadata.hasOptimizerDiagnostics"));
        assertEquals("optimizer.advisoryDiagnostics", fields.get("validationRulesRegistryResult.2.RuleId"));
        assertEquals("pass", fields.get("validationRulesRegistryResult.2.Status"));
        assertEquals("false", fields.get("validationRulesRegistryResult.2.Metadata.hasAdvisoryOptimizerSignals"));
        assertEquals(report.ciSummaryLine(), fields.get("validationRulesCiSummaryLine"));
        assertEquals(report.summary(), fields.get("validationRulesSummary"));
        assertTrue(report.consistencyReport().consistent());
        assertEquals("consistent", report.consistencyReport().verdict());
        assertEquals("true", fields.get("validationRulesConsistency.Consistent"));
        assertEquals("14", fields.get("validationRulesConsistency.Checks"));
        assertEquals("0", fields.get("validationRulesConsistency.FailedChecks"));
        assertEquals("[]", fields.get("validationRulesConsistency.FailedCheckList"));
        assertTrue(fields.get("validationRulesConsistency.CiSummaryLine").contains("passed"));

        GpuIrOptimizationValidationRuleArtifactSummary summary = report.artifactSummary();
        assertEquals("cleanKernel", summary.methodName());
        assertEquals("pass", summary.verdict());
        assertTrue(summary.passed());
        assertEquals(3, summary.ruleCount());
        assertEquals(3, summary.resultCount());
        assertEquals(0, summary.failedCount());
        assertFalse(summary.hasFailures());
        assertEquals(0, summary.warningCount());
        assertFalse(summary.hasWarnings());
        assertEquals(0, summary.blockingCount());
        assertFalse(summary.hasBlockingResults());
        assertEquals("", summary.warningRuleIds());
        assertEquals(
                "[safety.clean=pass,optimizer.noBlockingDiagnostics=pass,optimizer.advisoryDiagnostics=pass]",
                summary.ruleIndex()
        );
        assertEquals("[]", summary.warningRuleIndex());
        assertEquals("[]", summary.blockingRuleIndex());
        assertEquals("{pass=3}", summary.statusCounts());
        assertEquals("{safety=1,optimizer=2}", summary.ruleFamilyCounts());
        assertEquals("{}", summary.warningRuleFamilyCounts());
        assertEquals("{}", summary.blockingRuleFamilyCounts());
        assertEquals("{}", summary.failedRuleFamilyCounts());
        assertFalse(summary.summaryLine().contains("failedRuleIds="));
        assertFalse(summary.summaryLine().contains("warningRuleIds="));
        assertFalse(summary.summaryLine().contains("firstFailedRuleId="));
        assertFalse(summary.summaryLine().contains("firstWarningRuleId="));
        assertFalse(summary.summaryLine().contains("firstBlockingRuleId="));
        assertTrue(summary.summaryLine().contains("hasFailures=false"));
        assertTrue(summary.summaryLine().contains("hasWarnings=false"));
        assertTrue(summary.summaryLine().contains("hasBlockingResults=false"));
        assertTrue(summary.summaryLine().contains("ruleIndex=[safety.clean=pass,optimizer.noBlockingDiagnostics=pass,optimizer.advisoryDiagnostics=pass]"));
        assertTrue(summary.summaryLine().contains("verdict=pass"));
        assertTrue(summary.summaryLine().contains("warningRuleIndex=[]"));
        assertTrue(summary.summaryLine().contains("blockingRuleIndex=[]"));
    }

    @Test
    void exportsFailedRuleIdsForOptimizerDiagnostics() {
        GpuIrOptimizationValidationReport validationReport = validate(new GpuIrMethod("vectorBlockedKernel", List.of(
                fixedWidthLoop(4, List.of(new GpuIrAssignment(
                        new GpuIrArrayAccess("out", new GpuIrVariableRef("i")),
                        new GpuIrArrayAccess("left", new GpuIrVariableRef("i"))
                ))),
                new GpuIrReturn(null)
        )));

        GpuIrOptimizationValidationRuleArtifactReport report = GpuIrOptimizationValidationRuleArtifactReport.evaluateDefault(validationReport);
        Map<String, String> fields = report.artifactFields("rulesArtifact.");

        assertFalse(report.passed());
        assertEquals("fail", report.verdict());
        assertEquals(1, report.failedCount());
        assertEquals(0, report.warningCount());
        assertEquals(1, report.blockingCount());
        assertTrue(report.hasBlockingResults());
        assertEquals("optimizer.noBlockingDiagnostics", report.failedRuleIds());
        assertEquals("optimizer.noBlockingDiagnostics", report.blockingRuleIds());
        assertTrue(report.firstWarningResult().isEmpty());
        assertEquals("optimizer.noBlockingDiagnostics", report.firstBlockingResult().orElseThrow().ruleId());
        assertEquals(Map.of(
                GpuIrOptimizationValidationRuleStatus.PASS, 2L,
                GpuIrOptimizationValidationRuleStatus.FAIL, 1L
        ), report.statusCounts());
        assertEquals(Map.of("safety", 1L, "optimizer", 2L), report.ruleFamilyCounts());
        assertEquals(Map.of("optimizer", 1L), report.failedRuleFamilyCounts());
        assertEquals("false", fields.get("rulesArtifact.Passed"));
        assertEquals("fail", fields.get("rulesArtifact.Verdict"));
        assertEquals("1", fields.get("rulesArtifact.Failed"));
        assertEquals("true", fields.get("rulesArtifact.HasFailures"));
        assertEquals("0", fields.get("rulesArtifact.Warnings"));
        assertEquals("false", fields.get("rulesArtifact.HasWarnings"));
        assertEquals("1", fields.get("rulesArtifact.Blocking"));
        assertEquals("true", fields.get("rulesArtifact.HasBlockingResults"));
        assertEquals("optimizer.noBlockingDiagnostics", fields.get("rulesArtifact.FailedRuleIds"));
        assertEquals("", fields.get("rulesArtifact.WarningRuleIds"));
        assertEquals("optimizer.noBlockingDiagnostics", fields.get("rulesArtifact.BlockingRuleIds"));
        assertEquals(
                "[safety.clean=pass,optimizer.noBlockingDiagnostics=fail,optimizer.advisoryDiagnostics=pass]",
                fields.get("rulesArtifact.RuleIndex")
        );
        assertEquals("[]", fields.get("rulesArtifact.WarningRuleIndex"));
        assertEquals("[optimizer.noBlockingDiagnostics=fail]", fields.get("rulesArtifact.BlockingRuleIndex"));
        assertFalse(fields.containsKey("rulesArtifact.FirstWarningRuleId"));
        assertEquals("optimizer.noBlockingDiagnostics", fields.get("rulesArtifact.FirstFailedRuleId"));
        assertEquals("fail", fields.get("rulesArtifact.FirstFailedStatus"));
        assertEquals("true", fields.get("rulesArtifact.FirstFailedBlocking"));
        assertEquals("method has blocking optimizer diagnostics", fields.get("rulesArtifact.FirstFailedMessage"));
        assertEquals("optimizer.noBlockingDiagnostics", fields.get("rulesArtifact.FirstBlockingRuleId"));
        assertEquals("fail", fields.get("rulesArtifact.FirstBlockingStatus"));
        assertEquals("true", fields.get("rulesArtifact.FirstBlockingBlocking"));
        assertEquals("method has blocking optimizer diagnostics", fields.get("rulesArtifact.FirstBlockingMessage"));
        assertEquals("{pass=2,fail=1}", fields.get("rulesArtifact.StatusCounts"));
        assertEquals("2", fields.get("rulesArtifact.Status.pass"));
        assertEquals("1", fields.get("rulesArtifact.Status.fail"));
        assertEquals("{safety=1,optimizer=2}", fields.get("rulesArtifact.RuleFamilyCounts"));
        assertEquals("1", fields.get("rulesArtifact.RuleFamily.safety"));
        assertEquals("2", fields.get("rulesArtifact.RuleFamily.optimizer"));
        assertEquals("{}", fields.get("rulesArtifact.WarningRuleFamilyCounts"));
        assertEquals("{optimizer=1}", fields.get("rulesArtifact.BlockingRuleFamilyCounts"));
        assertEquals("1", fields.get("rulesArtifact.BlockingRuleFamily.optimizer"));
        assertEquals("{optimizer=1}", fields.get("rulesArtifact.FailedRuleFamilyCounts"));
        assertEquals("1", fields.get("rulesArtifact.FailedRuleFamily.optimizer"));
        assertEquals("false", fields.get("rulesArtifact.RegistryPassed"));
        assertEquals(
                "[safety.clean=pass,optimizer.noBlockingDiagnostics=fail,optimizer.advisoryDiagnostics=pass]",
                fields.get("rulesArtifact.RegistryRuleIndex")
        );
        assertEquals("[]", fields.get("rulesArtifact.RegistryWarningRuleIndex"));
        assertEquals("[optimizer.noBlockingDiagnostics=fail]", fields.get("rulesArtifact.RegistryBlockingRuleIndex"));
        assertEquals("false", fields.get("rulesArtifact.RegistryResult.1.Passed"));
        assertEquals("fail", fields.get("rulesArtifact.RegistryResult.1.Status"));
        assertEquals("true", fields.get("rulesArtifact.RegistryResult.1.Blocking"));
        assertEquals("autoVectorization", fields.get("rulesArtifact.RegistryResult.1.Metadata.optimizerGateSource"));
        assertEquals("guard.earlyExitBoundary", fields.get("rulesArtifact.RegistryResult.1.Metadata.optimizerGateFamily"));
        assertTrue(report.summary().contains("failedRuleIds=optimizer.noBlockingDiagnostics"));
        assertTrue(report.summary().contains("verdict=fail"));
        assertTrue(report.summary().contains("blockingRuleIds=optimizer.noBlockingDiagnostics"));
        assertTrue(report.summary().contains("ruleIndex=[safety.clean=pass,optimizer.noBlockingDiagnostics=fail,optimizer.advisoryDiagnostics=pass]"));
        assertTrue(report.summary().contains("blockingRuleIndex=[optimizer.noBlockingDiagnostics=fail]"));
        assertTrue(report.summary().contains("firstFailedRuleId=optimizer.noBlockingDiagnostics"));
        assertTrue(report.summary().contains("firstBlockingRuleId=optimizer.noBlockingDiagnostics"));
        assertTrue(report.summary().contains("statusCounts={pass=2,fail=1}"));
        assertTrue(report.summary().contains("warningRuleFamilyCounts={}"));
        assertTrue(report.summary().contains("blockingRuleFamilyCounts={optimizer=1}"));
        assertTrue(report.summary().contains("failedRuleFamilyCounts={optimizer=1}"));
        assertEquals(report.ciSummaryLine(), fields.get("rulesArtifact.CiSummaryLine"));
        assertTrue(report.consistencyReport().consistent());
        assertEquals("true", fields.get("rulesArtifact.Consistency.Consistent"));
        assertEquals("14", fields.get("rulesArtifact.Consistency.Checks"));
        assertEquals("0", fields.get("rulesArtifact.Consistency.FailedChecks"));
        assertTrue(report.artifactSummary().hasFailures());
        assertFalse(report.artifactSummary().hasWarnings());
        assertTrue(report.artifactSummary().hasBlockingResults());
        assertTrue(report.summary().contains("hasFailures=true"));
        assertTrue(report.summary().contains("hasWarnings=false"));
        assertTrue(report.summary().contains("hasBlockingResults=true"));
        assertEquals("optimizer.noBlockingDiagnostics", report.firstFailedResult().orElseThrow().ruleId());
        assertEquals("optimizer.noBlockingDiagnostics", report.artifactSummary().firstFailedRuleId());
        assertEquals("optimizer.noBlockingDiagnostics", report.artifactSummary().firstBlockingRuleId());
    }

    @Test
    void defaultArtifactWarnsForNonBlockingOptimizerOpportunities() {
        GpuIrOptimizationValidationReport validationReport = validate(new GpuIrMethod("cseOpportunityKernel", List.of(
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("x"))),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("x"), new GpuIrVariableRef("x"))),
                new GpuIrReturn(null)
        )));

        GpuIrOptimizationValidationRuleArtifactReport report = GpuIrOptimizationValidationRuleArtifactReport.evaluateDefault(validationReport);
        Map<String, String> fields = report.artifactFields("rulesArtifact.");

        assertTrue(report.passed());
        assertEquals("warn", report.verdict());
        assertEquals(1, report.warningCount());
        assertEquals(0, report.failedCount());
        assertEquals(0, report.blockingCount());
        assertEquals("optimizer.advisoryDiagnostics", report.firstWarningResult().orElseThrow().ruleId());
        assertTrue(report.firstBlockingResult().isEmpty());
        assertEquals("{pass=2,warn=1}", report.statusCountsSummary());
        assertEquals("true", fields.get("rulesArtifact.Passed"));
        assertEquals("warn", fields.get("rulesArtifact.Verdict"));
        assertEquals("1", fields.get("rulesArtifact.Warnings"));
        assertEquals("false", fields.get("rulesArtifact.HasFailures"));
        assertEquals("true", fields.get("rulesArtifact.HasWarnings"));
        assertEquals("0", fields.get("rulesArtifact.Blocking"));
        assertEquals("optimizer.advisoryDiagnostics", fields.get("rulesArtifact.WarningRuleIds"));
        assertEquals(
                "[safety.clean=pass,optimizer.noBlockingDiagnostics=pass,optimizer.advisoryDiagnostics=warn]",
                fields.get("rulesArtifact.RuleIndex")
        );
        assertEquals("[optimizer.advisoryDiagnostics=warn]", fields.get("rulesArtifact.WarningRuleIndex"));
        assertEquals("[]", fields.get("rulesArtifact.BlockingRuleIndex"));
        assertEquals("optimizer.advisoryDiagnostics", fields.get("rulesArtifact.FirstWarningRuleId"));
        assertEquals("warn", fields.get("rulesArtifact.FirstWarningStatus"));
        assertEquals("false", fields.get("rulesArtifact.FirstWarningBlocking"));
        assertEquals("method has non-blocking optimizer advisory signals", fields.get("rulesArtifact.FirstWarningMessage"));
        assertFalse(fields.containsKey("rulesArtifact.FirstBlockingRuleId"));
        assertEquals("{pass=2,warn=1}", fields.get("rulesArtifact.StatusCounts"));
        assertEquals("{optimizer=1}", fields.get("rulesArtifact.WarningRuleFamilyCounts"));
        assertEquals("1", fields.get("rulesArtifact.WarningRuleFamily.optimizer"));
        assertEquals("{}", fields.get("rulesArtifact.BlockingRuleFamilyCounts"));
        assertEquals("warn", fields.get("rulesArtifact.RegistryResult.2.Status"));
        assertEquals("[optimizer.advisoryDiagnostics=warn]", fields.get("rulesArtifact.RegistryWarningRuleIndex"));
        assertEquals("[]", fields.get("rulesArtifact.RegistryBlockingRuleIndex"));
        assertEquals("false", fields.get("rulesArtifact.RegistryResult.2.Blocking"));
        assertEquals("true", fields.get("rulesArtifact.RegistryResult.2.Metadata.hasAdvisoryOptimizerSignals"));
        assertEquals("1", fields.get("rulesArtifact.RegistryResult.2.Metadata.cseInsertions"));
        assertEquals("1", fields.get("rulesArtifact.RegistryResult.2.Metadata.cseReplacements"));
        assertEquals(report.ciSummaryLine(), fields.get("rulesArtifact.CiSummaryLine"));
        assertEquals("optimizer.advisoryDiagnostics", report.warningRuleIds());
        assertEquals("[optimizer.advisoryDiagnostics=warn]", report.warningRuleIndex());
        assertEquals("optimizer.advisoryDiagnostics", report.artifactSummary().firstWarningRuleId());
        assertEquals("warn", report.artifactSummary().verdict());
        assertFalse(report.artifactSummary().hasFailures());
        assertTrue(report.artifactSummary().hasWarnings());
        assertFalse(report.artifactSummary().hasBlockingResults());
        assertEquals("{optimizer=1}", report.artifactSummary().warningRuleFamilyCounts());
        assertEquals("{}", report.artifactSummary().blockingRuleFamilyCounts());
        assertEquals("optimizer.advisoryDiagnostics", report.artifactSummary().warningRuleIds());
        assertEquals("[optimizer.advisoryDiagnostics=warn]", report.artifactSummary().warningRuleIndex());
        assertTrue(report.summary().contains("warningRuleIds=optimizer.advisoryDiagnostics"));
        assertTrue(report.summary().contains("verdict=warn"));
        assertTrue(report.summary().contains("hasWarnings=true"));
        assertTrue(report.summary().contains("warningRuleFamilyCounts={optimizer=1}"));
        assertTrue(report.summary().contains("blockingRuleFamilyCounts={}"));
        assertTrue(report.summary().contains("warningRuleIndex=[optimizer.advisoryDiagnostics=warn]"));
        assertTrue(report.summary().contains("firstWarningRuleId=optimizer.advisoryDiagnostics"));
    }

    @Test
    void advisoryWarningsRemainPassingAndNonBlocking() {
        GpuIrOptimizationValidationReport validationReport = validate(new GpuIrMethod("cleanKernel", List.of(new GpuIrReturn(null))));
        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRuleRegistry.of(List.of(
                rule("optimizer.advisory", context -> GpuIrOptimizationValidationRuleResult.warned(
                        "optimizer.advisory",
                        "advisory optimizer note"
                ))
        ));

        GpuIrOptimizationValidationRuleArtifactReport report = GpuIrOptimizationValidationRuleArtifactReport.evaluate(validationReport, registry);
        Map<String, String> fields = report.artifactFields("advisory.");

        assertTrue(report.passed());
        assertEquals(1, report.warningCount());
        assertEquals(0, report.failedCount());
        assertEquals(0, report.blockingCount());
        assertFalse(report.hasBlockingResults());
        assertEquals("optimizer.advisory", report.firstWarningResult().orElseThrow().ruleId());
        assertTrue(report.firstBlockingResult().isEmpty());
        assertEquals("{warn=1}", report.statusCountsSummary());
        assertEquals("true", fields.get("advisory.Passed"));
        assertEquals("1", fields.get("advisory.Warnings"));
        assertEquals("false", fields.get("advisory.HasFailures"));
        assertEquals("true", fields.get("advisory.HasWarnings"));
        assertEquals("0", fields.get("advisory.Blocking"));
        assertEquals("optimizer.advisory", fields.get("advisory.WarningRuleIds"));
        assertEquals("[optimizer.advisory=warn]", fields.get("advisory.WarningRuleIndex"));
        assertEquals("[]", fields.get("advisory.BlockingRuleIndex"));
        assertEquals("false", fields.get("advisory.HasBlockingResults"));
        assertEquals("optimizer.advisory", fields.get("advisory.FirstWarningRuleId"));
        assertEquals("warn", fields.get("advisory.FirstWarningStatus"));
        assertEquals("false", fields.get("advisory.FirstWarningBlocking"));
        assertEquals("advisory optimizer note", fields.get("advisory.FirstWarningMessage"));
        assertFalse(fields.containsKey("advisory.FirstBlockingRuleId"));
        assertEquals("{warn=1}", fields.get("advisory.StatusCounts"));
        assertEquals("1", fields.get("advisory.Status.warn"));
        assertEquals("warn", fields.get("advisory.RegistryResult.0.Status"));
        assertEquals("[optimizer.advisory=warn]", fields.get("advisory.RegistryWarningRuleIndex"));
        assertEquals("[]", fields.get("advisory.RegistryBlockingRuleIndex"));
        assertEquals("false", fields.get("advisory.RegistryResult.0.Blocking"));
        assertEquals(report.ciSummaryLine(), fields.get("advisory.CiSummaryLine"));
    }

    @Test
    void artifactSummaryRejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationRuleArtifactSummary(
                "",
                "pass",
                true,
                1,
                1,
                0,
                0,
                0,
                "",
                "",
                "",
                "[]",
                "[]",
                "[]",
                "",
                "",
                "",
                "{pass=1}",
                "{safety=1}",
                "{}",
                "{}",
                "{}"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationRuleArtifactSummary(
                "kernel",
                "pass",
                true,
                -1,
                1,
                0,
                0,
                0,
                "",
                "",
                "",
                "[]",
                "[]",
                "[]",
                "",
                "",
                "",
                "{pass=1}",
                "{safety=1}",
                "{}",
                "{}",
                "{}"
        ));
        assertThrows(NullPointerException.class, () -> new GpuIrOptimizationValidationRuleArtifactSummary(
                "kernel",
                "pass",
                true,
                1,
                1,
                0,
                0,
                0,
                null,
                "",
                "",
                "[]",
                "[]",
                "[]",
                "",
                "",
                "",
                "{pass=1}",
                "{safety=1}",
                "{}",
                "{}",
                "{}"
        ));
    }

    @Test
    void rejectsInvalidArtifactInputsAndReturnsImmutableFields() {
        GpuIrOptimizationValidationReport validationReport = validate(new GpuIrMethod("cleanKernel", List.of(new GpuIrReturn(null))));
        GpuIrOptimizationValidationRuleRegistry registry = GpuIrOptimizationValidationRules.defaultRegistry();
        GpuIrOptimizationValidationRuleArtifactReport report = GpuIrOptimizationValidationRuleArtifactReport.evaluate(validationReport, registry);

        assertThrows(UnsupportedOperationException.class, () -> report.artifactFields().put("x", "y"));
        assertThrows(IllegalArgumentException.class, () -> report.artifactFields(""));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactReport.evaluate(null, registry));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactReport.evaluate(validationReport, null));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationRuleArtifactConsistencyReport.from(null));
        GpuIrOptimizationValidationRuleArtifactConsistencyReport failedConsistencyReport = new GpuIrOptimizationValidationRuleArtifactConsistencyReport(
                "kernel",
                "inconsistent",
                false,
                12,
                1,
                List.of("summaryVerdict")
        );
        Map<String, String> failedConsistencyFields = failedConsistencyReport.artifactFields();

        assertTrue(failedConsistencyReport.hasFailures());
        assertEquals("summaryVerdict", failedConsistencyReport.firstFailedCheck().orElseThrow());
        assertEquals("artifact summary and report disagree on verdict", failedConsistencyReport.firstFailureExplanation().orElseThrow());
        assertEquals(Map.of("summaryVerdict", 1L), failedConsistencyReport.failedCheckCounts());
        assertEquals("inconsistent", failedConsistencyFields.get("validationRulesConsistencyVerdict"));
        assertEquals("false", failedConsistencyFields.get("validationRulesConsistencyConsistent"));
        assertEquals("12", failedConsistencyFields.get("validationRulesConsistencyChecks"));
        assertEquals("1", failedConsistencyFields.get("validationRulesConsistencyFailedChecks"));
        assertEquals("[summaryVerdict]", failedConsistencyFields.get("validationRulesConsistencyFailedCheckList"));
        assertEquals("{summaryVerdict=1}", failedConsistencyFields.get("validationRulesConsistencyFailedCheckCounts"));
        assertEquals("summaryVerdict", failedConsistencyFields.get("validationRulesConsistencyFirstFailedCheck"));
        assertEquals("artifact summary and report disagree on verdict", failedConsistencyFields.get("validationRulesConsistencyFirstFailureExplanation"));
        assertTrue(failedConsistencyFields.get("validationRulesConsistencyCiSummaryLine").contains("failed"));
        assertTrue(failedConsistencyFields.get("validationRulesConsistencySummary").contains("consistent=false"));
        assertEquals("false", failedConsistencyReport.artifactFields("custom.").get("custom.Consistent"));
        assertThrows(UnsupportedOperationException.class, () -> failedConsistencyFields.put("x", "y"));
        assertThrows(IllegalArgumentException.class, () -> failedConsistencyReport.artifactFields(""));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationRuleArtifactConsistencyReport(
                "",
                "consistent",
                true,
                1,
                0,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationRuleArtifactConsistencyReport(
                "kernel",
                "consistent",
                true,
                1,
                1,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationRuleArtifactReport(
                validationReport,
                registry,
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationRuleArtifactReport(
                validationReport,
                GpuIrOptimizationValidationRuleRegistry.of(List.of(rule(
                        "expected.rule",
                        context -> GpuIrOptimizationValidationRuleResult.passed("expected.rule", "ok")
                ))),
                List.of(GpuIrOptimizationValidationRuleResult.passed("different.rule", "wrong rule id"))
        ));
        assertThrows(UnsupportedOperationException.class, () -> report.results().add(GpuIrOptimizationValidationRuleResult.passed("x", "y")));
    }
}
