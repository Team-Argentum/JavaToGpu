package net.sixik.ga_utils.javatogpu.runtime;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendSourcePromotionActivationGateTest {

    @Test
    void acceptsApprovedManifestForControlledOptInActivation() throws Exception {
        GpuBackendSourcePromotionActivationGate gate = GpuBackendSourcePromotionActivationGate.from(
                properties(GpuBackendSourcePromotionManifestTest.candidateText()),
                properties(manifestValidationText()),
                properties(controlledSourceSwitchingText("kernel-a.cl", "kernel-b.cl"))
        );

        assertTrue(gate.activationReady());
        assertEquals("controlled-activation-ready", gate.status());
        assertEquals("controlled-opt-in-only", gate.activationScope());
        assertEquals(2, gate.controlledCoverageCount());
        assertEquals(2, gate.operatorAcceptedCount());
        assertEquals(2, gate.operatorBoundCount());
        assertTrue(gate.blockers().isEmpty());
        assertTrue(gate.toPropertiesText().contains("defaultRuntimeActivation=false"));
        assertTrue(gate.toPropertiesText().contains("defaultProductionSourceSwitching=disabled"));
        assertTrue(gate.toPropertiesText().contains("productionMutation=disabled"));
    }

    @Test
    void blocksWhenManifestValidationIsNotApproved() throws Exception {
        String blockedManifest = manifestValidationText()
                .replace("status=approved", "status=blocked")
                .replace("valid=true", "valid=false")
                .replace("blocker.count=0", "blocker.count=1");

        GpuBackendSourcePromotionActivationGate gate = GpuBackendSourcePromotionActivationGate.from(
                properties(GpuBackendSourcePromotionManifestTest.candidateText()),
                properties(blockedManifest),
                properties(controlledSourceSwitchingText("kernel-a.cl", "kernel-b.cl"))
        );

        assertFalse(gate.activationReady());
        assertEquals("manifest-validation-not-approved", gate.firstBlocker());
    }

    @Test
    void blocksWhenControlledKernelEvidenceIsMissing() throws Exception {
        GpuBackendSourcePromotionActivationGate gate = GpuBackendSourcePromotionActivationGate.from(
                properties(GpuBackendSourcePromotionManifestTest.candidateText()),
                properties(manifestValidationText()),
                properties(controlledSourceSwitchingText("kernel-a.cl"))
        );

        assertFalse(gate.activationReady());
        assertEquals(1, gate.controlledCoverageCount());
        assertEquals("controlled-kernel-evidence-missing", gate.firstBlocker());
        assertEquals("controlled-kernel-evidence-missing", gate.kernels().get(1).blockers().get(0));
    }

    static String manifestValidationText() {
        return String.join("\n",
                "formatVersion=1",
                "status=approved",
                "valid=true",
                "scope=real-workload-production-candidate-manual-approval-validation",
                "approval.id=approval:test",
                "approval.approvedBy=test-operator",
                "approval.approvedAtUtc=2026-07-10T20:01:49Z",
                "binding.expectedGitSha=ac3ba1f681666fc215f4113e5e2a4c66ef63999f",
                "binding.manifestGitSha=ac3ba1f681666fc215f4113e5e2a4c66ef63999f",
                "binding.gitShaMatched=true",
                "binding.actualCandidateArtifact.sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "binding.manifestCandidateArtifact.sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "binding.candidateArtifactSha256Matched=true",
                "binding.backendTarget=OPENCL",
                "binding.deviceVendor=NVIDIA Corporation",
                "binding.deviceLabel=NVIDIA CUDA / NVIDIA GeForce RTX 5070",
                "binding.driverVersion=595.97",
                "binding.kernel.count=2",
                "authorization.defaultProductionSourceSwitching=disabled",
                "authorization.productionMutation=disabled",
                "authorization.scope=manual-review-only",
                "blocker.count=0",
                ""
        );
    }

    static String controlledSourceSwitchingText(String... resources) {
        StringBuilder builder = new StringBuilder()
                .append("status=passed\n")
                .append("reviewReady=true\n")
                .append("operatorAcceptance.mode=identity-bound\n")
                .append("operatorAcceptance.bound=true\n")
                .append("operatorAcceptance.deviceVendor=NVIDIA Corporation\n")
                .append("operatorAcceptance.deviceLabel=NVIDIA CUDA / NVIDIA GeForce RTX 5070\n")
                .append("operatorAcceptance.driverVersion=595.97\n")
                .append("kernel.count=").append(resources.length).append('\n');
        for (int index = 0; index < resources.length; index++) {
            String prefix = "kernel." + index + ".";
            builder.append(prefix).append("resource=").append(resources[index]).append('\n');
            builder.append(prefix).append("status=passed\n");
            builder.append(prefix).append("operatorAcceptance.status=accepted\n");
            builder.append(prefix).append("operatorAcceptance.bound=true\n");
        }
        return builder.toString();
    }

    static Properties properties(String text) throws Exception {
        Properties properties = new Properties();
        properties.load(new StringReader(text));
        return properties;
    }
}
