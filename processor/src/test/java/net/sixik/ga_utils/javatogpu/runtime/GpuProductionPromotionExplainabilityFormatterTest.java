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
        assertTrue(formatted.contains("readinessChecklist.ready.count=7"));
        assertTrue(formatted.contains("readinessChecklist.blocked.count=1"));
        assertTrue(formatted.contains("readinessChecklist.firstBlocked=controlled-source-switching-covered"));
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

    @Test
    void recordsControlledProductionSourceSwitchingEvidenceWithoutEnablingProduction() {
        String formatted = GpuProductionPromotionExplainabilityFormatter.format(
                blockedWorkloadGate(),
                blockedReadiness(),
                completePromotionArtifactSupport(),
                controlledProductionSourceSwitchingValidation()
        );

        assertTrue(formatted.contains("status=blocked"));
        assertTrue(formatted.contains("productionSourceSwitchingAllowed=false"));
        assertTrue(formatted.contains("controlledProductionSourceSwitching.status=passed"));
        assertTrue(formatted.contains("controlledProductionSourceSwitching.kernel.count=7"));
        assertTrue(formatted.contains("controlledProductionSourceSwitching.productionSourceSwitching=enabled"));
        assertTrue(formatted.contains("controlledProductionSourceSwitching.productionPromotionDecisionMode=production-enabled"));
        assertTrue(formatted.contains("controlledProductionSourceSwitching.realWorkload.covered.count=1"));
        assertTrue(formatted.contains("controlledProductionSourceSwitching.realWorkload.total.count=2"));
        assertTrue(formatted.contains("controlledProductionSourceSwitching.realWorkload.uncovered.count=1"));
        assertTrue(formatted.contains("controlledProductionSourceSwitching.realWorkload.covered.all=false"));
        assertTrue(formatted.contains("controlledProductionSourceSwitching.realWorkload.covered.0.resource=kernel-a.cl"));
        assertTrue(formatted.contains("controlledProductionSourceSwitching.realWorkload.uncovered.0.resource=kernel-b.cl"));
        assertTrue(formatted.contains("readinessChecklist.item.count=8"));
        assertTrue(formatted.contains("readinessChecklist.ready.count=3"));
        assertTrue(formatted.contains("readinessChecklist.blocked.count=5"));
        assertTrue(formatted.contains("readinessChecklist.ready.all=false"));
        assertTrue(formatted.contains("readinessChecklist.firstBlocked=workload-gate-review-ready"));
        assertTrue(formatted.contains("readinessChecklist.item.0.name=workload-gate-review-ready"));
        assertTrue(formatted.contains("readinessChecklist.item.0.ready=false"));
        assertTrue(formatted.contains("blocker.0=workload-source-promotion-gate-not-review-ready"));
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
        properties.setProperty("kernel.0.sourceKernelResource", "kernel-a.cl");
        properties.setProperty("kernel.1.sourceKernelResource", "kernel-b.cl");
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

    private static Properties completePromotionArtifactSupport() {
        Properties properties = new Properties();
        properties.setProperty("complete", "true");
        properties.setProperty("supported.count", "11");
        properties.setProperty("missing.count", "0");
        return properties;
    }

    private static Properties controlledProductionSourceSwitchingValidation() {
        Properties properties = new Properties();
        properties.setProperty("status", "passed");
        properties.setProperty("reviewReady", "true");
        properties.setProperty("kernel.count", "7");
        properties.setProperty("kernel.0.resource", "inline://integration/simple-irgpu-source-kernel.cl");
        properties.setProperty("kernel.1.resource", "kernel-a.cl");
        properties.setProperty("kernel.2.resource", "inline://integration/dual-buffer-int-kernel.cl");
        properties.setProperty("productionSourceSwitching", "enabled");
        properties.setProperty("productionPromotionDecisionMode", GpuProductionPromotionDecision.PRODUCTION_ENABLED);
        return properties;
    }
}
