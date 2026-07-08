package net.sixik.ga_utils.javatogpu.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class GpuProductionPromotionExplainabilityValidationTest {

    @Test
    void acceptsBlockedFailClosedArtifactWithBlockers() {
        Properties properties = blockedArtifact();

        GpuProductionPromotionExplainabilityValidation.Result result =
                GpuProductionPromotionExplainabilityValidation.validate(properties);

        assertTrue(result.valid());
        assertTrue(result.summary().contains("status=blocked"));
    }

    @Test
    void acceptsExplicitProductionReadyArtifact() {
        Properties properties = productionReadyArtifact();

        GpuProductionPromotionExplainabilityValidation.Result result =
                GpuProductionPromotionExplainabilityValidation.validate(properties);

        assertTrue(result.valid());
        assertTrue(result.summary().contains("sourceReady=2"));
        assertTrue(result.summary().contains("sourceSwitchingAllowed=true"));
    }

    @Test
    void rejectsBlockedArtifactWithProductionMutationEnabled() {
        Properties properties = blockedArtifact();
        properties.setProperty("productionMutationEnabled", "true");

        GpuProductionPromotionExplainabilityValidation.Result result =
                GpuProductionPromotionExplainabilityValidation.validate(properties);

        assertFalse(result.valid());
        assertTrue(result.firstViolation().contains("blocked explainability cannot enable"));
    }

    @Test
    void rejectsBlockedArtifactWithoutBlockers() {
        Properties properties = blockedArtifact();
        properties.setProperty("blocker.count", "0");

        GpuProductionPromotionExplainabilityValidation.Result result =
                GpuProductionPromotionExplainabilityValidation.validate(properties);

        assertFalse(result.valid());
        assertTrue(result.firstViolation().contains("at least one blocker"));
    }

    @Test
    void rejectsProductionReadyArtifactWithRemainingBlockers() {
        Properties properties = productionReadyArtifact();
        properties.setProperty("blocker.count", "1");
        properties.setProperty("blocker.0", "unexpected-blocker");

        GpuProductionPromotionExplainabilityValidation.Result result =
                GpuProductionPromotionExplainabilityValidation.validate(properties);

        assertFalse(result.valid());
        assertTrue(result.firstViolation().contains("still has blockers"));
    }

    @Test
    void rejectsProductionReadyArtifactWithoutAllKernelsReviewReady() {
        Properties properties = productionReadyArtifact();
        properties.setProperty("i3ReviewReady.count", "1");
        properties.setProperty("i3Blocked.count", "1");

        GpuProductionPromotionExplainabilityValidation.Result result =
                GpuProductionPromotionExplainabilityValidation.validate(properties);

        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(value -> value.contains("I3 review-ready")));
    }

    @Test
    void rejectsProductionReadyArtifactWithoutAllKernelsSourceReady() {
        Properties properties = productionReadyArtifact();
        properties.setProperty("i3SourceReady.count", "1");

        GpuProductionPromotionExplainabilityValidation.Result result =
                GpuProductionPromotionExplainabilityValidation.validate(properties);

        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(value -> value.contains("source-ready")));
    }

    private static Properties blockedArtifact() {
        Properties properties = new Properties();
        properties.setProperty("status", "blocked");
        properties.setProperty("kernel.count", "2");
        properties.setProperty("i3ReviewReady.count", "1");
        properties.setProperty("i3Blocked.count", "1");
        properties.setProperty("i3SourceReady.count", "1");
        properties.setProperty("productionSourceSwitchingAllowed", "false");
        properties.setProperty("productionSourceSwitchingEnabled", "false");
        properties.setProperty("productionMutationAllowed", "false");
        properties.setProperty("productionMutationEnabled", "false");
        properties.setProperty("blocker.count", "2");
        properties.setProperty("blocker.0", "workload-source-promotion-gate-not-review-ready");
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
