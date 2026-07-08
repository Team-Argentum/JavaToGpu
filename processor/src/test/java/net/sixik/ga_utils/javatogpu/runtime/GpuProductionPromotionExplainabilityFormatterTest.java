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
        assertTrue(formatted.contains("blocker.1=production-source-switching-disabled"));
        assertTrue(formatted.contains("contract.status=valid"));
        assertTrue(formatted.contains("contract.valid=true"));
        assertTrue(formatted.contains("contract.violation.count=0"));
        assertTrue(formatted.contains("decision.mode=review-ready"));
        assertTrue(formatted.contains("decision.contractValid=true"));
    }

    @Test
    void emitsValidProductionReadyArtifactWithContractFields() {
        String formatted = GpuProductionPromotionExplainabilityFormatter.format(productionReadyGate(), productionReadyReadiness());

        assertTrue(formatted.contains("status=production-ready"));
        assertTrue(formatted.contains("productionSourceSwitchingAllowed=true"));
        assertTrue(formatted.contains("productionMutationAllowed=true"));
        assertTrue(formatted.contains("contract.status=valid"));
        assertTrue(formatted.contains("contract.violation.count=0"));
        assertTrue(formatted.contains("decision.mode=production-enabled"));
        assertTrue(formatted.contains("decision.productionMutationAllowed=true"));
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
        properties.setProperty("realWorkloadEvidence", "runtime-snapshot");
        properties.setProperty("kernel.count", "2");
        return properties;
    }

    private static Properties productionReadyReadiness() {
        Properties properties = new Properties();
        properties.setProperty("reviewReady.count", "2");
        properties.setProperty("blocked.count", "0");
        properties.setProperty("productionMutationEnabled", "true");
        return properties;
    }
}
