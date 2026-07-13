package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuEntryParameter;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrBackendNeutralSourceMaterializationProposalProviderTest {

    @Test
    void materializesReconstructableOpenClIrAsReviewCandidate() {
        IrGpuArtifact original = artifact();

        GpuIrOptimizationProposal proposal = new GpuIrBackendNeutralSourceMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "OPENCL")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        assertFalse(original.regenerationMetadata().backendNeutralSourceReady());
        assertTrue(proposal.optimizedArtifact().orElseThrow().regenerationMetadata().backendNeutralSourceReady());
        assertEquals("backend-neutral-source-materialization", proposal.proofArtifact().fields().get("optimizerFamily"));
        assertEquals("true", proposal.proofArtifact().fields().get("rewrite.proposed"));
        assertEquals("true", proposal.proofArtifact().fields().get("rewrite.materialized"));
        assertEquals("true", proposal.proofArtifact().fields().get("sourceGenerated"));
        assertEquals("true", proposal.proofArtifact().fields().get("sourceReady"));
        assertEquals("true", proposal.proofArtifact().fields().get("materializationOnly"));
        assertEquals("false", proposal.proofArtifact().fields().get("productionAffecting"));
        assertEquals("false", proposal.proofArtifact().fields().get("provider.mutatesOriginal"));
        assertEquals("true", proposal.proofArtifact().fields().get("proof.approvalRequiredBeforeProduction"));
    }

    @Test
    void skipsNonOpenClRuntimeReview() {
        IrGpuArtifact original = artifact();

        GpuIrOptimizationProposal proposal = new GpuIrBackendNeutralSourceMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(
                        original,
                        "diagnostic",
                        false,
                        Map.of("backendTarget", "CUDA")
                ));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertTrue(proposal.diagnostics().get(0).contains("currently runs only for OpenCL"));
    }

    private static IrGpuArtifact artifact() {
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule(
                        "kernel",
                        "kernel",
                        List.of(),
                        List.of(),
                        List.of(IrGpuMethodBody.entry(
                                "kernel",
                                "kernel",
                                "body\n  set output[0] = 1\n",
                                List.of()
                        ))
                ),
                List.of(new IrGpuEntryParameter("output", "int[]", "GLOBAL", false, List.of())),
                List.of(IrGpuBackendOutput.openClSource("javatogpu/sample/Demo/kernel.cl")),
                "opencl",
                "off"
        );
    }
}
