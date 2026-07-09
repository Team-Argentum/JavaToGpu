package net.sixik.ga_utils.javatogpu.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class GpuProductionPromotionExplainabilityFormatterTest {

    @Test
    void emitsValidBlockedArtifactWithContractFields() {
        String formatted = GpuProductionPromotionExplainabilityFormatter.format(blockedWorkloadGate(), blockedReadiness());

        assertTrue(formatted.contains("status=blocked"));
        assertTrue(formatted.contains("blocker.0=workload-source-promotion-gate-not-review-ready"));
        assertTrue(formatted.contains("blocker.1=i3-source-readiness-not-complete"));
        assertTrue(formatted.contains("blocker.2=production-source-switching-disabled"));
        assertTrue(formatted.contains("i3SourceReady.count=1"));
        assertTrue(formatted.contains("i3SourceReady.all=false"));
        assertTrue(formatted.contains("contract.status=valid"));
        assertTrue(formatted.contains("contract.valid=true"));
        assertTrue(formatted.contains("contract.violation.count=0"));
        assertTrue(formatted.contains("decision.mode=diagnostic-only"));
        assertTrue(formatted.contains("decision.contractValid=true"));
    }

    @Test
    void emitsValidProductionReadyArtifactWithContractFields() {
        String formatted = GpuProductionPromotionExplainabilityFormatter.format(productionReadyGate(), productionReadyReadiness());

        assertTrue(formatted.contains("status=production-ready"));
        assertTrue(formatted.contains("productionSourceSwitchingAllowed=true"));
        assertTrue(formatted.contains("productionMutationAllowed=true"));
        assertTrue(formatted.contains("productionSourceSwitchingEnabled.count=2"));
        assertTrue(formatted.contains("productionSourceSwitchingEnabled.all=true"));
        assertTrue(formatted.contains("productionPromotionDecisionMode.productionEnabled.count=2"));
        assertTrue(formatted.contains("productionPromotionDecisionMode.productionEnabled.all=true"));
        assertTrue(formatted.contains("sourceSwitching.productionDecision.count=2"));
        assertTrue(formatted.contains("sourceSwitching.productionDecision.all=true"));
        assertTrue(formatted.contains("i3SourceReady.count=2"));
        assertTrue(formatted.contains("i3SourceReady.all=true"));
        assertTrue(formatted.contains("contract.status=valid"));
        assertTrue(formatted.contains("contract.violation.count=0"));
        assertTrue(formatted.contains("decision.mode=production-enabled"));
        assertTrue(formatted.contains("decision.productionMutationAllowed=true"));
    }

    @Test
    void blocksProductionReadyWhenBackendPromotionArtifactSupportIsIncomplete() {
        String formatted = GpuProductionPromotionExplainabilityFormatter.format(
                productionReadyGate(),
                productionReadyReadiness(),
                incompletePromotionArtifactSupport()
        );

        assertTrue(formatted.contains("status=blocked"));
        assertTrue(formatted.contains("backendPromotionArtifactSupport.complete=false"));
        assertTrue(formatted.contains("backendPromotionArtifactSupport.missing.count=1"));
        assertTrue(formatted.contains("blocker.0=backend-promotion-artifact-support-incomplete"));
        assertTrue(formatted.contains("productionSourceSwitchingAllowed=false"));
        assertTrue(formatted.contains("productionMutationAllowed=false"));
        assertTrue(formatted.contains("contract.status=valid"));
    }

    private static Properties blockedWorkloadGate() {
        Properties properties = new Properties();
        properties.setProperty("status", "blocked");
        properties.setProperty("reviewReady", "false");
        properties.setProperty("sourceParityMatched", "true");
        properties.setProperty("runtimeEquivalencePassed", "true");
        properties.setProperty("productionSourceSwitching", "false");
        properties.setProperty("realWorkloadEvidence", "runtime-snapshot");
        properties.setProperty("kernel.count", "2");
        return properties;
    }

    private static Properties blockedReadiness() {
        Properties properties = new Properties();
        properties.setProperty("reviewReady.count", "2");
        properties.setProperty("blocked.count", "0");
        properties.setProperty("sourceReady.count", "1");
        properties.setProperty("productionMutationEnabled", "false");
        return properties;
    }

    private static Properties productionReadyGate() {
        Properties properties = new Properties();
        properties.setProperty("status", "review-ready");
        properties.setProperty("reviewReady", "true");
        properties.setProperty("sourceParityMatched", "true");
        properties.setProperty("runtimeEquivalencePassed", "true");
        properties.setProperty("productionSourceSwitching", "enabled");
        properties.setProperty("productionSourceSwitchingEnabled.count", "2");
        properties.setProperty("productionSourceSwitchingEnabled.all", "true");
        properties.setProperty("productionPromotionDecisionMode.productionEnabled.count", "2");
        properties.setProperty("productionPromotionDecisionMode.productionEnabled.all", "true");
        properties.setProperty("sourceSwitching.productionDecision.count", "2");
        properties.setProperty("sourceSwitching.productionDecision.all", "true");
        properties.setProperty("realWorkloadEvidence", "runtime-snapshot");
        properties.setProperty("kernel.count", "2");
        return properties;
    }

    private static Properties productionReadyReadiness() {
        Properties properties = new Properties();
        properties.setProperty("reviewReady.count", "2");
        properties.setProperty("blocked.count", "0");
        properties.setProperty("sourceReady.count", "2");
        properties.setProperty("productionMutationEnabled", "true");
        return properties;
    }

    private static Properties incompletePromotionArtifactSupport() {
        Properties properties = new Properties();
        properties.setProperty("complete", "false");
        properties.setProperty("supported.count", "10");
        properties.setProperty("missing.count", "1");
        properties.setProperty("missing.0", GpuPromotionArtifactRegistry.BACKEND_PROMOTION_ARTIFACT_SUPPORT);
        return properties;
    }
}
