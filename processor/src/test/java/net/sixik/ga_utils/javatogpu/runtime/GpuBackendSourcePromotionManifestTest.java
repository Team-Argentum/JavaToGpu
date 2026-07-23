package net.sixik.ga_utils.javatogpu.runtime;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendSourcePromotionManifestTest {

    static final String GIT_SHA = "1ec61b94d629c8f4f5346ed321bc63fd2d50442c";

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
        assertTrue(template.contains("runtime.production.manifest.status=pending"));
        assertTrue(template.contains("runtime.production.manifest.binding.gitSha=" + GIT_SHA));
        assertTrue(template.contains("runtime.production.manifest.authorization.productionMutation=disabled"));
    }

    @Test
    void templateAcceptsPortableOnlyCandidateArtifact() throws Exception {
        String candidate = portableOnlyCandidateText();
        byte[] candidateBytes = candidate.getBytes(StandardCharsets.UTF_8);

        String template = GpuBackendSourcePromotionManifest.template(
                properties(candidate),
                candidateBytes,
                GIT_SHA
        );

        assertTrue(template.contains("binding.deviceVendor=NVIDIA Corporation"));
        assertTrue(template.contains("binding.kernel.0.resource=kernel-a.cl"));
        assertTrue(template.contains("runtime.production.manifest.binding.kernel.1.resource=kernel-b.cl"));
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
        assertTrue(validation.toPropertiesText().contains("runtime.production.manifest.status=approved"));
        assertTrue(validation.toPropertiesText().contains("runtime.production.manifest.valid=true"));
        assertTrue(validation.toPropertiesText().contains("runtime.production.manifest.binding.gitShaMatched=true"));
    }

    @Test
    void validatesPortableOnlyManifestAgainstPortableOnlyCandidate() throws Exception {
        String candidate = portableOnlyCandidateText();
        byte[] candidateBytes = candidate.getBytes(StandardCharsets.UTF_8);
        String pending = GpuBackendSourcePromotionManifest.template(
                properties(candidate),
                candidateBytes,
                GIT_SHA
        );
        String manifest = portableOnly(approve(pending), GpuBackendSourcePromotionManifest.PORTABLE_PREFIX);

        GpuBackendSourcePromotionManifest.Validation validation =
                GpuBackendSourcePromotionManifest.validate(
                        properties(candidate),
                        candidateBytes,
                        properties(manifest),
                        GIT_SHA
                );

        assertTrue(validation.valid());
        assertEquals("approved", validation.status());
        assertTrue(validation.blockers().isEmpty());
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

    static String portableOnlyCandidateText() {
        return String.join("\n",
                "runtime.production.candidateGate.formatVersion=1",
                "runtime.production.candidateGate.status=review-ready",
                "runtime.production.candidateGate.reviewReady=true",
                "runtime.production.candidateGate.scope=real-workload-production-candidate",
                "runtime.production.candidateGate.defaultProductionSourceSwitching=disabled",
                "runtime.production.candidateGate.candidateProductionSourceSwitching=review-ready",
                "runtime.production.candidateGate.productionMutation=disabled",
                "runtime.production.candidateGate.kernel.count=2",
                "runtime.production.candidateGate.candidateReady.count=2",
                "runtime.production.candidateGate.candidateReady.all=true",
                "runtime.production.candidateGate.sourceParityMatched=true",
                "runtime.production.candidateGate.runtimeEquivalencePassed=true",
                "runtime.production.candidateGate.controlledSourceSwitching.status=passed",
                "runtime.production.candidateGate.operatorAcceptance.mode=identity-bound",
                "runtime.production.candidateGate.operatorAcceptance.accepted.count=2",
                "runtime.production.candidateGate.operatorAcceptance.accepted.all=true",
                "runtime.production.candidateGate.operatorAcceptance.bound.count=2",
                "runtime.production.candidateGate.operatorAcceptance.bound.all=true",
                "runtime.production.candidateGate.operatorAcceptance.deviceVendor=NVIDIA Corporation",
                "runtime.production.candidateGate.operatorAcceptance.deviceLabel=NVIDIA CUDA / NVIDIA GeForce RTX 5070",
                "runtime.production.candidateGate.operatorAcceptance.driverVersion=595.97",
                "kernel.0.runtime.production.candidateGate.resource=kernel-a.cl",
                "kernel.1.runtime.production.candidateGate.resource=kernel-b.cl",
                "runtime.production.candidateGate.blocker.count=0",
                ""
        );
    }

    static String approvedManifest(byte[] candidateBytes) throws Exception {
        return approve(GpuBackendSourcePromotionManifest.template(
                properties(candidateText()),
                candidateBytes,
                GIT_SHA
        ));
    }

    static String approve(String pending) {
        return pending.replace("status=pending", "status=approved")
                .replace("approval.id=REQUIRED", "approval.id=approval:release-2026-07-10")
                .replace("approval.approvedBy=REQUIRED", "approval.approvedBy=release-operator")
                .replace("approval.approvedAtUtc=REQUIRED", "approval.approvedAtUtc=2026-07-10T20:00:00Z");
    }

    static String portableOnly(String text, String portablePrefix) {
        StringBuilder builder = new StringBuilder();
        for (String line : text.split("\\R")) {
            if (line.startsWith(portablePrefix)) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    static Properties properties(String text) throws Exception {
        Properties properties = new Properties();
        properties.load(new StringReader(text));
        return properties;
    }
}
