package net.sixik.ga_utils.javatogpu.runtime;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendSourcePromotionManifestTest {

    private static final String GIT_SHA = "1ec61b94d629c8f4f5346ed321bc63fd2d50442c";

    @Test
    void templateBindsCandidateArtifactAndRemainsPending() throws Exception {
        byte[] candidateBytes = candidateText().getBytes(StandardCharsets.UTF_8);

        String template = GpuBackendSourcePromotionManifest.template(
                properties(candidateText()),
                candidateBytes,
                GIT_SHA
        );

        assertTrue(template.contains("status=pending"));
        assertTrue(template.contains("approval.id=REQUIRED"));
        assertTrue(template.contains("binding.gitSha=" + GIT_SHA));
        assertTrue(template.contains("binding.candidateArtifact.sha256="
                + GpuBackendSourcePromotionManifest.sha256(candidateBytes)));
        assertTrue(template.contains("binding.kernel.0.resource=kernel-a.cl"));
        assertTrue(template.contains("binding.kernel.1.resource=kernel-b.cl"));
        assertTrue(template.contains("authorization.defaultProductionSourceSwitching=disabled"));
        assertTrue(template.contains("authorization.productionMutation=disabled"));
    }

    @Test
    void validatesApprovedManifestAgainstCandidateAndGitSha() throws Exception {
        byte[] candidateBytes = candidateText().getBytes(StandardCharsets.UTF_8);
        String manifest = approvedManifest(candidateBytes);

        GpuBackendSourcePromotionManifest.Validation validation =
                GpuBackendSourcePromotionManifest.validate(
                        properties(candidateText()),
                        candidateBytes,
                        properties(manifest),
                        GIT_SHA
                );

        assertTrue(validation.valid());
        assertEquals("approved", validation.status());
        assertEquals("approval:release-2026-07-10", validation.approvalId());
        assertEquals(2, validation.kernelCount());
        assertTrue(validation.blockers().isEmpty());
        assertTrue(validation.toPropertiesText().contains("binding.gitShaMatched=true"));
        assertTrue(validation.toPropertiesText().contains("binding.candidateArtifactSha256Matched=true"));
        assertTrue(validation.toPropertiesText().contains("authorization.productionMutation=disabled"));
    }

    @Test
    void blocksManifestForDifferentGitSha() throws Exception {
        byte[] candidateBytes = candidateText().getBytes(StandardCharsets.UTF_8);

        GpuBackendSourcePromotionManifest.Validation validation =
                GpuBackendSourcePromotionManifest.validate(
                        properties(candidateText()),
                        candidateBytes,
                        properties(approvedManifest(candidateBytes)),
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                );

        assertFalse(validation.valid());
        assertEquals("manifest-git-sha-mismatch", validation.firstBlocker());
        assertTrue(validation.toPropertiesText().contains("status=blocked"));
    }

    static String candidateText() {
        return String.join("\n",
                "formatVersion=1",
                "status=review-ready",
                "reviewReady=true",
                "scope=real-workload-production-candidate",
                "defaultProductionSourceSwitching=disabled",
                "candidateProductionSourceSwitching=review-ready",
                "productionMutation=disabled",
                "kernel.count=2",
                "candidateReady.count=2",
                "candidateReady.all=true",
                "sourceParityMatched=true",
                "runtimeEquivalencePassed=true",
                "controlledSourceSwitching.status=passed",
                "operatorAcceptance.mode=identity-bound",
                "operatorAcceptance.accepted.count=2",
                "operatorAcceptance.accepted.all=true",
                "operatorAcceptance.bound.count=2",
                "operatorAcceptance.bound.all=true",
                "operatorAcceptance.deviceVendor=NVIDIA Corporation",
                "operatorAcceptance.deviceLabel=NVIDIA CUDA / NVIDIA GeForce RTX 5070",
                "operatorAcceptance.driverVersion=595.97",
                "kernel.0.resource=kernel-a.cl",
                "kernel.1.resource=kernel-b.cl",
                "blocker.count=0",
                ""
        );
    }

    static String approvedManifest(byte[] candidateBytes) throws Exception {
        return GpuBackendSourcePromotionManifest.template(
                properties(candidateText()),
                candidateBytes,
                GIT_SHA
        ).replace("status=pending", "status=approved")
                .replace("approval.id=REQUIRED", "approval.id=approval:release-2026-07-10")
                .replace("approval.approvedBy=REQUIRED", "approval.approvedBy=release-operator")
                .replace("approval.approvedAtUtc=REQUIRED", "approval.approvedAtUtc=2026-07-10T20:00:00Z");
    }

    static Properties properties(String text) throws Exception {
        Properties properties = new Properties();
        properties.load(new StringReader(text));
        return properties;
    }
}
