package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationApprovalTemplateFormatterTest {

    @Test
    void returnsNotApplicableForPreviewNoChangeProposal() {
        IrGpuArtifact original = artifact("body\n");
        GpuIrOptimizationProposal preview = new GpuIrConstantFoldingPreviewProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        GpuIrOptimizationApprovalTemplateResult result = GpuIrOptimizationApprovalTemplateFormatter.format(
                preview,
                request(original)
        );

        assertFalse(result.applicable());
        assertEquals("not-applicable", result.status());
        assertEquals("proposal-decision-not-proposed", result.firstBlocker());
        assertTrue(result.templateText().isBlank());
        assertEquals("NO_CHANGE", result.fields().get("decision"));
        assertTrue(result.toPropertiesText().contains("template.present=false"));
    }

    @Test
    void returnsPendingTemplateForRealProposedRewrite() {
        IrGpuArtifact original = artifact("body\n");
        GpuIrOptimizationProposal proposal = proposed(original, artifact("body optimized\n"));

        GpuIrOptimizationApprovalTemplateResult result = GpuIrOptimizationApprovalTemplateFormatter.format(
                proposal,
                request(original)
        );

        assertTrue(result.applicable());
        assertEquals("pending", result.status());
        assertEquals("none", result.firstBlocker());
        assertTrue(result.templateText().contains("status=pending"));
        assertTrue(result.templateText().contains("binding.optimizerId=optimizer:test"));
        assertTrue(result.templateText().contains("authorization.productionMutation=disabled"));
        assertTrue(result.toPropertiesText().contains("template.present=true"));
        assertTrue(result.toPropertiesText().contains("template.resourceDirectory=META-INF/javatogpu/ir-optimization-approvals/"));
    }

    @Test
    void returnsNotApplicableForMalformedProposedRewrite() {
        IrGpuArtifact original = artifact("body\n");
        GpuIrOptimizationProposal proposal = proposed(
                original,
                artifact("body optimized\n"),
                GpuRuntimeIrOptimizationProofArtifact.fromFields("ir-optimizer", "not-proven", Map.of())
        );

        GpuIrOptimizationApprovalTemplateResult result = GpuIrOptimizationApprovalTemplateFormatter.format(
                proposal,
                request(original)
        );

        assertFalse(result.applicable());
        assertEquals("proposal-proof-source-not-specific", result.firstBlocker());
        assertTrue(result.fields().get("diagnostic").contains("proposal-proof-source-not-specific"));
    }

    private static GpuIrOptimizationProposal proposed(IrGpuArtifact original, IrGpuArtifact optimized) {
        return proposed(
                original,
                optimized,
                GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "ir-optimizer.test-proof",
                        "runtime-equivalence-passed",
                        Map.of("case.count", "1")
                )
        );
    }

    private static GpuIrOptimizationProposal proposed(
            IrGpuArtifact original,
            IrGpuArtifact optimized,
            GpuRuntimeIrOptimizationProofArtifact proofArtifact
    ) {
        return GpuIrOptimizationProposal.proposed(
                "optimizer:test",
                "optimizer:test:1",
                original,
                optimized,
                proofArtifact,
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
