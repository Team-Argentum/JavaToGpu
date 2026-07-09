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
        assertTrue(summary.historyStatus().contains("backendPromotionArtifactSupportComplete=unknown"));
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
        properties.setProperty("blocker.count", "1");
        properties.setProperty("blocker.0", "production-mutation-disabled");

        GpuProductionPromotionExplainabilitySummary summary =
                GpuProductionPromotionExplainabilitySummary.fromProperties(properties);
        String formatted = GpuProductionPromotionExplainabilitySummaryCli.format(summary);

        assertTrue(formatted.contains("status=blocked\n"));
        assertTrue(formatted.contains("contract.status=valid\n"));
        assertTrue(formatted.contains("contract.valid=true\n"));
        assertTrue(formatted.contains("decision.mode=diagnostic-only\n"));
        assertTrue(formatted.contains("productionSourceSwitchingAllowed=false\n"));
        assertTrue(formatted.contains("productionMutationEnabled=false\n"));
        assertTrue(formatted.contains("backendPromotionArtifactSupport.complete=unknown\n"));
        assertTrue(formatted.contains("backendPromotionArtifactSupport.missing.count=0\n"));
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
