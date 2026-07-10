package net.sixik.ga_utils.javatogpu.runtime;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuProductionPromotionExplainabilitySummaryTest {

    @Test
    void summarizesBlockedExplainabilityForReportsAndCi() {
        Properties properties = new Properties();
        properties.setProperty("status", "blocked");
        properties.setProperty("contract.status", "valid");
        properties.setProperty("contract.valid", "true");
        properties.setProperty("decision.mode", GpuProductionPromotionDecision.DIAGNOSTIC_ONLY);
        properties.setProperty("productionSourceSwitchingAllowed", "false");
        properties.setProperty("productionSourceSwitchingEnabled", "false");
        properties.setProperty("productionMutationAllowed", "false");
        properties.setProperty("productionMutationEnabled", "false");
        properties.setProperty("kernel.count", "2");
        properties.setProperty("i3ReviewReady.count", "1");
        properties.setProperty("i3Blocked.count", "1");
        properties.setProperty("i3SourceReady.count", "1");
        properties.setProperty("i3SourceReady.all", "false");
        properties.setProperty("optimizerFamily.count", "2");
        properties.setProperty("optimizerFamily.promotionReady.count", "1");
        properties.setProperty(
                "optimizerFamily.summary",
                "cse[passes=1, acceptedProof=1, blockingProof=0, rolledBack=0, failed=0, promotionReady=true], vector[passes=1, acceptedProof=0, blockingProof=1, rolledBack=0, failed=0, promotionReady=false]"
        );
        properties.setProperty("blocker.count", "2");
        properties.setProperty("blocker.0", "workload-source-promotion-gate-not-review-ready");
        properties.setProperty("blocker.1", "production-mutation-disabled");

        GpuProductionPromotionExplainabilitySummary summary =
                GpuProductionPromotionExplainabilitySummary.fromProperties(properties);

        assertEquals("blocked", summary.status());
        assertEquals("valid", summary.contractStatus());
        assertEquals(GpuProductionPromotionDecision.DIAGNOSTIC_ONLY, summary.decisionMode());
        assertEquals("workload-source-promotion-gate-not-review-ready", summary.firstBlocker());
        assertTrue(summary.historyStatus().contains("contract=valid"));
        assertTrue(summary.historyStatus().contains("blockers=2"));
        assertTrue(summary.historyStatus().contains("first=workload-source-promotion-gate-not-review-ready"));
        assertTrue(summary.historyStatus().contains("i3SourceReadyAll=false"));
        assertTrue(summary.historyStatus().contains("optimizerFamilies=2"));
        assertTrue(summary.historyStatus().contains("optimizerPromotionReadyFamilies=1"));
        assertTrue(summary.historyStatus().contains("optimizerRuntimeEquivalenceHistoryBaselineReady=false"));
        assertTrue(summary.historyStatus().contains("optimizerPromotionPreflightReady=true"));
        assertTrue(summary.historyStatus().contains("optimizerFamilySummary=cse[passes=1"));
        assertTrue(summary.historyStatus().contains("backendPromotionArtifactSupportComplete=unknown"));
        assertTrue(summary.historyStatus().contains("controlledSourceSwitching=not-recorded"));
        assertTrue(summary.historyStatus().contains("controlledRealWorkloadCoverage=0/0"));
        assertTrue(summary.historyStatus().contains("controlledActivationTokenSmoke=not-recorded"));
        assertTrue(summary.historyStatus().contains("controlledActivationTokenSmokePassed=false"));
        assertTrue(summary.historyStatus().contains("readinessChecklistReady=0"));
        assertTrue(summary.historyStatus().contains("readinessChecklistBlocked=0"));
    }

    @Test
    void marksInvalidContractInHistoryStatus() {
        Properties properties = new Properties();
        properties.setProperty("status", "production-ready");
        properties.setProperty("kernel.count", "1");
        properties.setProperty("i3ReviewReady.count", "0");
        properties.setProperty("i3Blocked.count", "1");
        properties.setProperty("i3SourceReady.count", "0");
        properties.setProperty("blocker.count", "1");
        properties.setProperty("blocker.0", "still-blocked");
        properties.setProperty("productionSourceSwitchingAllowed", "false");
        properties.setProperty("productionSourceSwitchingEnabled", "false");
        properties.setProperty("productionMutationAllowed", "false");
        properties.setProperty("productionMutationEnabled", "false");

        GpuProductionPromotionExplainabilitySummary summary =
                GpuProductionPromotionExplainabilitySummary.fromProperties(properties);

        assertEquals("invalid", summary.contractStatus());
        assertTrue(summary.historyStatus().contains("contract=invalid"));
        assertTrue(summary.historyStatus().contains("violation=production-ready explainability still has blockers"));
    }

    @Test
    void cliFormatKeepsCiFriendlyScalarFields() {
        Properties properties = new Properties();
        properties.setProperty("status", "blocked");
        properties.setProperty("decision.mode", GpuProductionPromotionDecision.DIAGNOSTIC_ONLY);
        properties.setProperty("productionSourceSwitchingAllowed", "false");
        properties.setProperty("productionSourceSwitchingEnabled", "false");
        properties.setProperty("productionMutationAllowed", "false");
        properties.setProperty("productionMutationEnabled", "false");
        properties.setProperty("kernel.count", "1");
        properties.setProperty("i3ReviewReady.count", "1");
        properties.setProperty("i3Blocked.count", "0");
        properties.setProperty("i3SourceReady.count", "0");
        properties.setProperty("i3SourceReady.all", "false");
        properties.setProperty("optimizerFamily.count", "2");
        properties.setProperty("optimizerFamily.promotionReady.count", "1");
        properties.setProperty(
                "optimizerFamily.summary",
                "cse[passes=1, acceptedProof=1, blockingProof=0, rolledBack=0, failed=0, promotionReady=true]"
        );
        properties.setProperty("blocker.count", "1");
        properties.setProperty("blocker.0", "production-mutation-disabled");
        properties.setProperty("controlledProductionSourceSwitching.status", "passed");
        properties.setProperty("controlledProductionSourceSwitching.kernel.count", "7");
        properties.setProperty("controlledProductionSourceSwitching.realWorkload.covered.count", "1");
        properties.setProperty("controlledProductionSourceSwitching.realWorkload.total.count", "2");
        properties.setProperty("controlledProductionSourceSwitching.realWorkload.uncovered.count", "1");
        properties.setProperty("controlledProductionSourceSwitching.realWorkload.covered.all", "false");
        properties.setProperty("controlledProductionActivationTokenSmoke.status", "passed");
        properties.setProperty("controlledProductionActivationTokenSmoke.tokenLoaded", "true");
        properties.setProperty("controlledProductionActivationTokenSmoke.approvedKernelExecuted", "true");
        properties.setProperty("controlledProductionActivationTokenSmoke.realWorkload.covered.count", "2");
        properties.setProperty("controlledProductionActivationTokenSmoke.realWorkload.total.count", "2");
        properties.setProperty("controlledProductionActivationTokenSmoke.realWorkload.uncovered.count", "0");
        properties.setProperty("controlledProductionActivationTokenSmoke.realWorkload.covered.all", "true");
        properties.setProperty("controlledProductionActivationTokenSmoke.safeDefaults", "true");
        properties.setProperty("controlledProductionActivationTokenSmoke.passed", "true");
        properties.setProperty("controlledProductionActivationTokenNegative.status", "passed");
        properties.setProperty("controlledProductionActivationTokenNegative.digestMismatchRejected", "true");
        properties.setProperty("controlledProductionActivationTokenNegative.unapprovedKernelRejected", "true");
        properties.setProperty("controlledProductionActivationTokenNegative.outputUnchanged", "true");
        properties.setProperty("controlledProductionActivationTokenNegative.safeDefaults", "true");
        properties.setProperty("controlledProductionActivationTokenNegative.passed", "true");
        properties.setProperty("readinessChecklist.ready.count", "4");
        properties.setProperty("readinessChecklist.blocked.count", "4");
        properties.setProperty("readinessChecklist.ready.all", "false");
        properties.setProperty("readinessChecklist.firstBlocked", "production-source-switching-enabled");

        GpuProductionPromotionExplainabilitySummary summary =
                GpuProductionPromotionExplainabilitySummary.fromProperties(properties);
        String formatted = GpuProductionPromotionExplainabilitySummaryCli.format(summary);

        assertTrue(formatted.contains("status=blocked\n"));
        assertTrue(formatted.contains("contract.status=valid\n"));
        assertTrue(formatted.contains("contract.valid=true\n"));
        assertTrue(formatted.contains("decision.mode=diagnostic-only\n"));
        assertTrue(formatted.contains("productionSourceSwitchingAllowed=false\n"));
        assertTrue(formatted.contains("productionMutationEnabled=false\n"));
        assertTrue(formatted.contains("optimizerFamily.count=2\n"));
        assertTrue(formatted.contains("optimizerFamily.promotionReady.count=1\n"));
        assertTrue(formatted.contains("optimizerFamily.summary=cse[passes=1"));
        assertTrue(formatted.contains("optimizerFamily.runtimeEquivalenceHistoryBaselineReady=false\n"));
        assertTrue(formatted.contains("optimizerFamily.promotionPreflightReady=true\n"));
        assertTrue(formatted.contains("backendPromotionArtifactSupport.complete=unknown\n"));
        assertTrue(formatted.contains("backendPromotionArtifactSupport.missing.count=0\n"));
        assertTrue(formatted.contains("controlledProductionSourceSwitching.status=passed\n"));
        assertTrue(formatted.contains("controlledProductionSourceSwitching.kernel.count=7\n"));
        assertTrue(formatted.contains("controlledProductionSourceSwitching.realWorkload.covered.count=1\n"));
        assertTrue(formatted.contains("controlledProductionSourceSwitching.realWorkload.total.count=2\n"));
        assertTrue(formatted.contains("controlledProductionSourceSwitching.realWorkload.uncovered.count=1\n"));
        assertTrue(formatted.contains("controlledProductionSourceSwitching.realWorkload.covered.all=false\n"));
        assertTrue(formatted.contains("controlledProductionActivationTokenSmoke.status=passed\n"));
        assertTrue(formatted.contains("controlledProductionActivationTokenSmoke.tokenLoaded=true\n"));
        assertTrue(formatted.contains("controlledProductionActivationTokenSmoke.approvedKernelExecuted=true\n"));
        assertTrue(formatted.contains("controlledProductionActivationTokenSmoke.realWorkload.covered.count=2\n"));
        assertTrue(formatted.contains("controlledProductionActivationTokenSmoke.realWorkload.total.count=2\n"));
        assertTrue(formatted.contains("controlledProductionActivationTokenSmoke.realWorkload.uncovered.count=0\n"));
        assertTrue(formatted.contains("controlledProductionActivationTokenSmoke.realWorkload.covered.all=true\n"));
        assertTrue(formatted.contains("controlledProductionActivationTokenSmoke.safeDefaults=true\n"));
        assertTrue(formatted.contains("controlledProductionActivationTokenSmoke.passed=true\n"));
        assertTrue(formatted.contains("controlledProductionActivationTokenNegative.status=passed\n"));
        assertTrue(formatted.contains("controlledProductionActivationTokenNegative.digestMismatchRejected=true\n"));
        assertTrue(formatted.contains("controlledProductionActivationTokenNegative.unapprovedKernelRejected=true\n"));
        assertTrue(formatted.contains("controlledProductionActivationTokenNegative.outputUnchanged=true\n"));
        assertTrue(formatted.contains("controlledProductionActivationTokenNegative.safeDefaults=true\n"));
        assertTrue(formatted.contains("controlledProductionActivationTokenNegative.passed=true\n"));
        assertTrue(formatted.contains("readinessChecklist.ready.count=4\n"));
        assertTrue(formatted.contains("readinessChecklist.blocked.count=4\n"));
        assertTrue(formatted.contains("readinessChecklist.ready.all=false\n"));
        assertTrue(formatted.contains("readinessChecklist.firstBlocked=production-source-switching-enabled\n"));
        assertTrue(formatted.contains("blocker.0=production-mutation-disabled\n"));
        assertTrue(formatted.contains("historyStatus=blocked"));
    }

    @Test
    void emptyPropertiesRemainNotRecorded() {
        GpuProductionPromotionExplainabilitySummary summary =
                GpuProductionPromotionExplainabilitySummary.fromProperties(new Properties());

        assertEquals("not-recorded", summary.status());
        assertEquals("invalid", summary.contractStatus());
        assertEquals("not recorded", summary.historyStatus());
    }
}
