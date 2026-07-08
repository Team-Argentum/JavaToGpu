package net.sixik.ga_utils.javatogpu.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class GpuProductionPromotionDecisionTest {

    @Test
    void invalidContractStaysDiagnosticOnly() {
        Properties properties = blockedArtifact();
        properties.setProperty("blocker.count", "0");

        GpuProductionPromotionDecision decision = GpuProductionPromotionDecision.fromExplainability(properties);

        assertEquals(GpuProductionPromotionDecision.DIAGNOSTIC_ONLY, decision.mode());
        assertFalse(decision.contractValid());
        assertTrue(decision.firstViolation().contains("at least one blocker"));
    }

    @Test
    void blockedButFullyReviewReadyArtifactBecomesReviewReady() {
        GpuProductionPromotionDecision decision = GpuProductionPromotionDecision.fromExplainability(blockedArtifact());

        assertEquals(GpuProductionPromotionDecision.REVIEW_READY, decision.mode());
        assertTrue(decision.contractValid());
        assertEquals("production-source-switching-disabled", decision.firstBlocker());
    }

    @Test
    void productionReadyArtifactBecomesProductionEnabled() {
        GpuProductionPromotionDecision decision = GpuProductionPromotionDecision.fromExplainability(productionReadyArtifact());

        assertEquals(GpuProductionPromotionDecision.PRODUCTION_ENABLED, decision.mode());
        assertTrue(decision.productionSourceSwitchingAllowed());
        assertTrue(decision.productionMutationAllowed());
    }

    private static Properties blockedArtifact() {
        Properties properties = new Properties();
        properties.setProperty("status", "blocked");
        properties.setProperty("kernel.count", "2");
        properties.setProperty("i3ReviewReady.count", "2");
        properties.setProperty("i3Blocked.count", "0");
        properties.setProperty("productionSourceSwitchingAllowed", "false");
        properties.setProperty("productionSourceSwitchingEnabled", "false");
        properties.setProperty("productionMutationAllowed", "false");
        properties.setProperty("productionMutationEnabled", "false");
        properties.setProperty("blocker.count", "2");
        properties.setProperty("blocker.0", "production-source-switching-disabled");
        properties.setProperty("blocker.1", "production-mutation-disabled");
        return properties;
    }

    private static Properties productionReadyArtifact() {
        Properties properties = new Properties();
        properties.setProperty("status", "production-ready");
        properties.setProperty("kernel.count", "2");
        properties.setProperty("i3ReviewReady.count", "2");
        properties.setProperty("i3Blocked.count", "0");
        properties.setProperty("productionSourceSwitchingAllowed", "true");
        properties.setProperty("productionSourceSwitchingEnabled", "true");
        properties.setProperty("productionMutationAllowed", "true");
        properties.setProperty("productionMutationEnabled", "true");
        properties.setProperty("blocker.count", "0");
        return properties;
    }
}
