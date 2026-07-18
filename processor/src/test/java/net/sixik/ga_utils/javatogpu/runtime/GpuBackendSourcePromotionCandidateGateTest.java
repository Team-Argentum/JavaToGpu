package net.sixik.ga_utils.javatogpu.runtime;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendSourcePromotionCandidateGateTest {

    @Test
    void joinsReviewReadyWorkloadsWithIdentityBoundAcceptance() {
        GpuBackendSourcePromotionCandidateGate gate = GpuBackendSourcePromotionCandidateGate.from(
                workloadGate("kernel-a.cl", "kernel-b.cl"),
                controlledSummary("kernel-a.cl", "kernel-b.cl")
        );

        assertEquals("review-ready", gate.status());
        assertTrue(gate.reviewReady());
        assertEquals(2, gate.candidateReadyCount());
        assertEquals(2, gate.operatorAcceptedCount());
        assertEquals(2, gate.operatorBoundCount());
        assertTrue(gate.blockers().isEmpty());
        assertTrue(gate.toPropertiesText().contains("defaultProductionSourceSwitching=disabled"));
        assertTrue(gate.toPropertiesText().contains("candidateProductionSourceSwitching=review-ready"));
        assertTrue(gate.toPropertiesText().contains("runtime.production.candidateGate.status=review-ready"));
        assertTrue(gate.toPropertiesText().contains("runtime.production.candidateGate.candidateReady.all=true"));
        assertTrue(gate.toPropertiesText().contains("kernel.0.runtime.production.candidateGate.resource=kernel-a.cl"));
    }

    @Test
    void blocksWhenOneWorkloadHasNoControlledAcceptanceEvidence() {
        GpuBackendSourcePromotionCandidateGate gate = GpuBackendSourcePromotionCandidateGate.from(
                workloadGate("kernel-a.cl", "kernel-b.cl"),
                controlledSummary("kernel-a.cl")
        );

        assertEquals("blocked", gate.status());
        assertFalse(gate.reviewReady());
        assertEquals(1, gate.candidateReadyCount());
        assertEquals("controlled-kernel-evidence-missing", gate.firstBlocker());
        assertEquals("controlled-kernel-evidence-missing", gate.kernels().get(1).blockers().get(0));
    }

    private static Properties workloadGate(String... resources) {
        Properties properties = new Properties();
        properties.setProperty("status", "review-ready");
        properties.setProperty("reviewReady", "true");
        properties.setProperty("kernel.count", Integer.toString(resources.length));
        for (int index = 0; index < resources.length; index++) {
            String prefix = "kernel." + index + ".";
            properties.setProperty(prefix + "sourceKernelResource", resources[index]);
            properties.setProperty(prefix + "reviewReady", "true");
            properties.setProperty(prefix + "sourceParityMatched", "true");
            properties.setProperty(prefix + "runtimeEquivalencePassed", "true");
        }
        return properties;
    }

    private static Properties controlledSummary(String... resources) {
        Properties properties = new Properties();
        properties.setProperty("status", "passed");
        properties.setProperty("reviewReady", "true");
        properties.setProperty("operatorAcceptance.mode", "identity-bound");
        properties.setProperty("operatorAcceptance.bound", "true");
        properties.setProperty("operatorAcceptance.deviceVendor", "NVIDIA Corporation");
        properties.setProperty("operatorAcceptance.deviceLabel", "NVIDIA CUDA / RTX 5070");
        properties.setProperty("operatorAcceptance.driverVersion", "595.97");
        properties.setProperty("kernel.count", Integer.toString(resources.length));
        for (int index = 0; index < resources.length; index++) {
            String prefix = "kernel." + index + ".";
            properties.setProperty(prefix + "resource", resources[index]);
            properties.setProperty(prefix + "status", "passed");
            properties.setProperty(prefix + "operatorAcceptance.id", "acceptance:" + resources[index]);
            properties.setProperty(prefix + "operatorAcceptance.status", "accepted");
            properties.setProperty(prefix + "operatorAcceptance.bound", "true");
        }
        return properties;
    }
}
