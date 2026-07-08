package net.sixik.ga_utils.javatogpu.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void explainabilityFileBecomesRuntimeDecision() throws IOException {
        Path path = Files.createTempFile("javatogpu-production-promotion", ".properties");
        try {
            Properties properties = blockedArtifact();
            try (java.io.Writer writer = Files.newBufferedWriter(path)) {
                properties.store(writer, "test production promotion explainability");
            }

            GpuProductionPromotionDecision decision = GpuProductionPromotionDecision
                    .fromExplainabilityFileOrDiagnosticOnly(path);

            assertEquals(GpuProductionPromotionDecision.REVIEW_READY, decision.mode());
            assertTrue(decision.contractValid());
            assertEquals("production-source-switching-disabled", decision.firstBlocker());
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void missingExplainabilityFileStaysDiagnosticOnly() {
        GpuProductionPromotionDecision decision = GpuProductionPromotionDecision
                .fromExplainabilityFileOrDiagnosticOnly(Path.of("missing-production-promotion-explainability.properties"));

        assertEquals(GpuProductionPromotionDecision.DIAGNOSTIC_ONLY, decision.mode());
        assertFalse(decision.contractValid());
    }

    private static Properties blockedArtifact() {
        Properties properties = new Properties();
        properties.setProperty("status", "blocked");
        properties.setProperty("kernel.count", "2");
        properties.setProperty("i3ReviewReady.count", "2");
        properties.setProperty("i3Blocked.count", "0");
        properties.setProperty("i3SourceReady.count", "2");
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
        properties.setProperty("i3SourceReady.count", "2");
        properties.setProperty("productionSourceSwitchingAllowed", "true");
        properties.setProperty("productionSourceSwitchingEnabled", "true");
        properties.setProperty("productionSourceSwitchingEnabled.count", "2");
        properties.setProperty("productionSourceSwitchingEnabled.all", "true");
        properties.setProperty("productionPromotionDecisionMode.productionEnabled.count", "2");
        properties.setProperty("productionPromotionDecisionMode.productionEnabled.all", "true");
        properties.setProperty("sourceSwitching.productionDecision.count", "2");
        properties.setProperty("sourceSwitching.productionDecision.all", "true");
        properties.setProperty("productionMutationAllowed", "true");
        properties.setProperty("productionMutationEnabled", "true");
        properties.setProperty("blocker.count", "0");
        return properties;
    }
}
