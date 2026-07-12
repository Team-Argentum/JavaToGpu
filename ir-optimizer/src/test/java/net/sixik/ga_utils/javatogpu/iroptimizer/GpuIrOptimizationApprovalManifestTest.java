package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationApprovalManifestTest {

    @Test
    void writesPendingTemplateBoundToProposalAndRuntimeContext() throws Exception {
        GpuIrOptimizationProposal proposal = proposal();
        GpuIrOptimizationProposalRequest request = request(proposal.originalArtifact());

        String template = GpuIrOptimizationApprovalManifest.template(proposal, request);

        assertTrue(template.contains("status=pending"));
        assertTrue(template.contains("scope=" + GpuIrOptimizationApprovalManifest.SCOPE));
        assertTrue(template.contains("approval.id=REQUIRED"));
        assertTrue(template.contains("binding.optimizerId=optimizer:test"));
        assertTrue(template.contains("binding.originalIrIdentity=" + proposal.originalIdentity()));
        assertTrue(template.contains("binding.optimizedIrIdentity=" + proposal.optimizedIdentity()));
        assertTrue(template.contains("binding.backendTarget=OPENCL"));
        assertTrue(template.contains("binding.deviceVendor=NVIDIA"));
        assertTrue(template.contains("binding.proof.verdict=runtime-equivalence-passed"));
    }

    @Test
    void validatesApprovedManifestAgainstExactProposalAndContext() throws Exception {
        GpuIrOptimizationProposal proposal = proposal();
        GpuIrOptimizationProposalRequest request = request(proposal.originalArtifact());
        Properties manifest = approvedManifest(proposal, request);

        GpuIrOptimizationApprovalManifest.Validation validation =
                GpuIrOptimizationApprovalManifest.validate(proposal, request, manifest);

        assertTrue(validation.valid());
        assertEquals("approved", validation.status());
        assertEquals("approval:optimizer-test", validation.approvalId());
        assertEquals(proposal.originalIdentity(), validation.originalIrIdentity());
        assertEquals(proposal.optimizedIdentity(), validation.optimizedIrIdentity());
        assertTrue(validation.toPropertiesText().contains("valid=true"));
        assertTrue(validation.toPropertiesText().contains("blocker.count=0"));
    }

    @Test
    void blocksManifestForDifferentOptimizedIdentity() throws Exception {
        GpuIrOptimizationProposal proposal = proposal();
        GpuIrOptimizationProposalRequest request = request(proposal.originalArtifact());
        Properties manifest = approvedManifest(proposal, request);
        manifest.setProperty("binding.optimizedIrIdentity", "irgpu:sha256:other");

        GpuIrOptimizationApprovalManifest.Validation validation =
                GpuIrOptimizationApprovalManifest.validate(proposal, request, manifest);

        assertFalse(validation.valid());
        assertTrue(validation.blockers().contains("manifest-optimized-ir-identity-mismatch"));
    }

    @Test
    void blocksManifestForDifferentDeviceBinding() throws Exception {
        GpuIrOptimizationProposal proposal = proposal();
        GpuIrOptimizationProposalRequest request = request(proposal.originalArtifact());
        Properties manifest = approvedManifest(proposal, request);
        manifest.setProperty("binding.deviceVendor", "AMD");

        GpuIrOptimizationApprovalManifest.Validation validation =
                GpuIrOptimizationApprovalManifest.validate(proposal, request, manifest);

        assertFalse(validation.valid());
        assertTrue(validation.blockers().contains("manifest-device-vendor-mismatch"));
    }

    @Test
    void templateRequiresRealProposedDistinctArtifactAndAcceptedProof() {
        IrGpuArtifact artifact = artifact("body\n");
        GpuIrOptimizationProposal noChange = GpuIrOptimizationProposal.noChange(
                "optimizer:test",
                "optimizer:test:1",
                artifact,
                "no change"
        );
        GpuIrOptimizationProposal missingProof = GpuIrOptimizationProposal.proposed(
                "optimizer:test",
                "optimizer:test:1",
                artifact,
                artifact("body changed\n"),
                GpuRuntimeIrOptimizationProofArtifact.fromFields("ir-optimizer", "not-proven", Map.of()),
                List.of()
        );

        IllegalStateException noChangeFailure = assertThrows(
                IllegalStateException.class,
                () -> GpuIrOptimizationApprovalManifest.template(noChange, new GpuIrOptimizationProposalRequest(artifact))
        );
        IllegalStateException proofFailure = assertThrows(
                IllegalStateException.class,
                () -> GpuIrOptimizationApprovalManifest.template(missingProof, request(artifact))
        );

        assertTrue(noChangeFailure.getMessage().contains("proposal-decision-not-proposed"));
        assertTrue(proofFailure.getMessage().contains("proposal-proof-source-not-specific"));
    }

    private static GpuIrOptimizationProposal proposal() {
        IrGpuArtifact original = artifact("body\n");
        IrGpuArtifact optimized = artifact("body optimized\n");
        return GpuIrOptimizationProposal.proposed(
                "optimizer:test",
                "optimizer:test:1",
                original,
                optimized,
                GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "ir-optimizer.test-proof",
                        "runtime-equivalence-passed",
                        Map.of("case.count", "1")
                ),
                List.of("test proposal")
        );
    }

    private static GpuIrOptimizationProposalRequest request(IrGpuArtifact artifact) {
        return new GpuIrOptimizationProposalRequest(
                artifact,
                "diagnostic",
                false,
                Map.of(
                        "backendTarget", "OPENCL",
                        "deviceProfile.vendor", "NVIDIA",
                        "deviceProfile.label", "RTX"
                )
        );
    }

    private static Properties approvedManifest(
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationProposalRequest request
    ) throws Exception {
        String approved = GpuIrOptimizationApprovalManifest.template(proposal, request)
                .replace("status=pending", "status=approved")
                .replace("approval.id=REQUIRED", "approval.id=approval:optimizer-test")
                .replace("approval.approvedBy=REQUIRED", "approval.approvedBy=CI")
                .replace("approval.approvedAtUtc=REQUIRED", "approval.approvedAtUtc=2026-07-12T00:00:00Z");
        Properties properties = new Properties();
        properties.load(new StringReader(approved));
        return properties;
    }

    private static IrGpuArtifact artifact(String body) {
        IrGpuMethodBody methodBody = IrGpuMethodBody.entry(
                "test.Kernel.run",
                "run",
                body,
                List.of()
        );
        IrGpuModule module = new IrGpuModule("test.Kernel.run", "run", List.of(), List.of(), List.of(methodBody));
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                module,
                List.of(IrGpuBackendOutput.openClSource("test.cl")),
                "opencl",
                "off"
        );
    }
}
