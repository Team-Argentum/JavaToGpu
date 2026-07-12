package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBackendOutput;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuBodyIndex;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuSourceLocation;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrTypedDeadCodePreviewProposalProviderTest {

    @Test
    void recordsUnreachableTypedNodesWithoutProposingRewrite() {
        IrGpuArtifact original = artifact(typedBody(
                List.of(0),
                new IrGpuTypedNode(0, "GpuIrReturn", Map.of(), Map.of("value", List.of(1))),
                new IrGpuTypedNode(1, "GpuIrVariableRef", Map.of("name", "live"), Map.of()),
                new IrGpuTypedNode(2, "GpuIrBinary", Map.of("operator", "+"), Map.of(
                        "left", List.of(3),
                        "right", List.of(4)
                )),
                new IrGpuTypedNode(3, "GpuIrLiteral", Map.of("sourceText", "1"), Map.of()),
                new IrGpuTypedNode(4, "GpuIrLiteral", Map.of("sourceText", "2"), Map.of())
        ));

        GpuIrOptimizationProposal proposal = new GpuIrTypedDeadCodePreviewProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertSame(original, proposal.originalArtifact());
        assertEquals("preview-candidates-recorded", proposal.proofArtifact().verdict());
        assertEquals("5", proposal.proofArtifact().fields().get("node.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("reachableNode.count"));
        assertEquals("3", proposal.proofArtifact().fields().get("unreachableNode.count"));
        assertEquals("{GpuIrBinary=1,GpuIrLiteral=2}", proposal.proofArtifact().fields().get("unreachableKind.counts"));
        assertEquals("false", proposal.proofArtifact().fields().get("rewrite.proposed"));
        assertEquals("true", proposal.proofArtifact().fields().get("previewOnly"));
        assertEquals("true", proposal.proofArtifact().fields().get("proof.runtimeEquivalenceRequiredBeforeRewrite"));
        assertEquals("true", proposal.proofArtifact().fields().get("proof.approvalRequiredBeforeRewrite"));
        assertEquals("false", proposal.proofArtifact().fields().get("safety.sideEffectFreedomProven"));
        assertEquals("typed-body-root-preview", proposal.proofArtifact().fields().get("safety.reachabilityScope"));
        assertEquals("preview-only-no-rewrite", proposal.proofArtifact().fields().get("firstBlocker"));
        assertTrue(proposal.proofArtifact().fields().get("firstUnreachableNode").contains("GpuIrBinary"));
        assertTrue(proposal.diagnostics().get(0).contains("no rewrite was proposed"));
    }

    @Test
    void recordsMissingReferencesAndSideEffectingUnreachableBlockers() {
        IrGpuArtifact original = artifact(typedBody(
                List.of(99, 0),
                new IrGpuTypedNode(0, "GpuIrReturn", Map.of(), Map.of("value", List.of(1, 42))),
                new IrGpuTypedNode(1, "GpuIrLiteral", Map.of("sourceText", "1"), Map.of()),
                new IrGpuTypedNode(2, "GpuIrHelperCall", Map.of("helper", "sideEffect"), Map.of())
        ));

        GpuIrOptimizationProposal proposal = new GpuIrTypedDeadCodePreviewProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertEquals("preview-candidates-recorded", proposal.proofArtifact().verdict());
        assertEquals("3", proposal.proofArtifact().fields().get("node.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("reachableNode.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("unreachableNode.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("blocked.missingRoot.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("blocked.missingChildReference.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("blocked.sideEffectingUnreachableNode.count"));
        assertEquals("{GpuIrHelperCall=1}", proposal.proofArtifact().fields().get("unreachableKind.counts"));
    }

    @Test
    void previewStaysNoChangeEvenWhenMutationAllowed() {
        IrGpuArtifact original = artifact(typedBody(
                List.of(0),
                new IrGpuTypedNode(0, "GpuIrReturn", Map.of(), Map.of("value", List.of(1))),
                new IrGpuTypedNode(1, "GpuIrLiteral", Map.of("sourceText", "1"), Map.of()),
                new IrGpuTypedNode(2, "GpuIrLiteral", Map.of("sourceText", "2"), Map.of())
        ));

        GpuIrOptimizationSandwichReport report = GpuIrOptimizationSandwichRunner.alwaysValid()
                .run(new GpuIrOptimizationProposalRequest(original, "diagnostic", true, Map.of()),
                        new GpuIrTypedDeadCodePreviewProposalProvider());

        assertEquals(GpuIrOptimizationSandwichStatus.NO_CHANGE, report.status());
        assertSame(original, report.selectedArtifact());
        assertTrue(report.selectedOriginal());
        assertFalse(report.selectedOptimized());
        assertEquals("1", report.proposal().orElseThrow().proofArtifact().fields().get("unreachableNode.count"));
    }

    @Test
    void previewProviderIsServiceLoaded() {
        List<GpuIrOptimizationProposalProvider> providers = ServiceLoader
                .load(GpuIrOptimizationProposalProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(GpuIrTypedDeadCodePreviewProposalProvider.class::isInstance)
                .toList();

        assertEquals(1, providers.size());
    }

    private static IrGpuTypedBody typedBody(List<Integer> rootNodeIds, IrGpuTypedNode... nodes) {
        return new IrGpuTypedBody(IrGpuTypedBody.FORMAT, rootNodeIds, List.of(nodes));
    }

    private static IrGpuArtifact artifact(IrGpuTypedBody typedBody) {
        IrGpuMethodBody methodBody = new IrGpuMethodBody(
                "entry",
                "kernel",
                "jtg_kernel",
                "ir-text-v1",
                "body\n  return 1\n",
                typedBody,
                IrGpuBodyIndex.empty(),
                List.of(),
                IrGpuSourceLocation.unknown("kernel")
        );
        return new IrGpuArtifact(
                IrGpuArtifactHeader.javaSourceV1(),
                new IrGpuModule("kernel", "jtg_kernel", List.of(), List.of(), List.of(methodBody)),
                List.of(IrGpuBackendOutput.openClSource("test.cl")),
                "opencl",
                "off"
        );
    }
}
