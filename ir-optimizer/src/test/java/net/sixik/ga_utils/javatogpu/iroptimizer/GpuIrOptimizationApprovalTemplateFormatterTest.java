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
        String resourcePath = GpuIrOptimizationApprovalManifest.resourcePath(proposal, request(original));

        assertTrue(result.applicable());
        assertEquals("pending", result.status());
        assertEquals("none", result.firstBlocker());
        assertTrue(result.templateText().contains("status=pending"));
        assertTrue(result.templateText().contains("manifest.resourcePath=" + resourcePath));
        assertTrue(result.templateText().contains("binding.optimizerId=optimizer:test"));
        assertTrue(result.templateText().contains("authorization.productionMutation=disabled"));
        assertEquals("false", result.fields().get("runtimeEquivalencePayload.required"));
        assertEquals(resourcePath, result.fields().get("resourcePath"));
        assertTrue(result.toPropertiesText().contains("template.present=true"));
        assertTrue(result.toPropertiesText().contains("template.resourceDirectory=META-INF/javatogpu/ir-optimization-approvals/"));
        assertTrue(result.toPropertiesText().contains("template.resourcePath=" + resourcePath));
    }

    @Test
    void exposesRuntimeEquivalencePayloadBindingForPendingTemplate() {
        IrGpuArtifact original = artifact("body\n");
        GpuIrOptimizationProposal proposal = proposed(
                original,
                artifact("body optimized\n"),
                GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "ir-optimizer.test-proof",
                        "runtime-equivalence-passed",
                        Map.ofEntries(
                                Map.entry("proof.runtimeEquivalencePayloadRequiredBeforeSelection", "true"),
                                Map.entry("runtimeEquivalencePayload.required", "true"),
                                Map.entry("runtimeEquivalencePayload.present", "true"),
                                Map.entry("runtimeEquivalencePayload.passed", "true"),
                                Map.entry("runtimeEquivalencePayload.cpuReference.present", "true"),
                                Map.entry("runtimeEquivalencePayload.preOptimizationOutput.present", "true"),
                                Map.entry("runtimeEquivalencePayload.postOptimizationOutput.present", "true"),
                                Map.entry("runtimeEquivalencePayload.tolerance.present", "true"),
                                Map.entry("runtimeEquivalencePayload.failureFixture.present", "true"),
                                Map.entry("runtimeEquivalencePayload.Case.Count", "1"),
                                Map.entry("runtimeEquivalencePayload.resource", "artifact://payload/cf"),
                                Map.entry(
                                        "runtimeEquivalencePayload.comparisonMode",
                                        "optimizer-family:constant-folding-materialization:review-candidate"
                                )
                        )
                )
        );

        GpuIrOptimizationApprovalTemplateResult result = GpuIrOptimizationApprovalTemplateFormatter.format(
                proposal,
                request(original)
        );

        assertTrue(result.applicable());
        assertEquals("true", result.fields().get("runtimeEquivalencePayload.required"));
        assertEquals("true", result.fields().get("runtimeEquivalencePayload.present"));
        assertEquals("true", result.fields().get("runtimeEquivalencePayload.passed"));
        assertEquals("true", result.fields().get("runtimeEquivalencePayload.componentsComplete"));
        assertEquals("1", result.fields().get("runtimeEquivalencePayload.caseCount"));
        assertEquals("artifact://payload/cf", result.fields().get("runtimeEquivalencePayload.resource"));
        assertEquals(
                "optimizer-family:constant-folding-materialization:review-candidate",
                result.fields().get("runtimeEquivalencePayload.comparisonMode")
        );
        assertTrue(result.fields().get("resourcePath").startsWith(
                GpuIrOptimizationApprovalManifest.RESOURCE_DIRECTORY + "approval-"
        ));
        assertTrue(result.templateText().contains("binding.runtimeEquivalencePayload.resource=artifact://payload/cf"));
        assertTrue(result.templateText().contains(
                "binding.runtimeEquivalencePayload.comparisonMode=optimizer-family:constant-folding-materialization:review-candidate"
        ));
        assertTrue(result.templateText().contains("manifest.resourcePath=" + result.fields().get("resourcePath")));
        assertTrue(result.toPropertiesText().contains("runtimeEquivalencePayload.componentsComplete=true"));
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
