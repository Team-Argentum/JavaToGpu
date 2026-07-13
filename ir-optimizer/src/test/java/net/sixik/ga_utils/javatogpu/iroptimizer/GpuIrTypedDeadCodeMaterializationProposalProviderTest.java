package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactHeader;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrTypedDeadCodeMaterializationProposalProviderTest {

    @Test
    void materializesUnreachablePureTypedNodesIntoReviewCandidate() {
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

        GpuIrOptimizationProposal proposal = new GpuIrTypedDeadCodeMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.PROPOSED, proposal.decision());
        assertSame(original, proposal.originalArtifact());
        IrGpuArtifact optimized = proposal.optimizedArtifact().orElseThrow();
        assertNotEquals(IrGpuArtifactIdentity.stableIdentity(original), IrGpuArtifactIdentity.stableIdentity(optimized));
        assertEquals(original.module().methodBodies().get(0).body(), optimized.module().methodBodies().get(0).body());
        assertEquals(List.of(0, 1), optimized.module().methodBodies().get(0).typedBody().nodes().stream()
                .map(IrGpuTypedNode::id)
                .toList());
        assertEquals("materialized-review-candidate", proposal.proofArtifact().verdict());
        assertEquals("typed-dead-code-materialization", proposal.proofArtifact().fields().get("optimizerFamily"));
        assertEquals("5", proposal.proofArtifact().fields().get("node.count"));
        assertEquals("2", proposal.proofArtifact().fields().get("reachableNode.count"));
        assertEquals("3", proposal.proofArtifact().fields().get("unreachableNode.count"));
        assertEquals("3", proposal.proofArtifact().fields().get("removedNode.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("changedMethodBody.count"));
        assertEquals("{GpuIrBinary=1,GpuIrLiteral=2}", proposal.proofArtifact().fields().get("removedKind.counts"));
        assertEquals("true", proposal.proofArtifact().fields().get("rewrite.proposed"));
        assertEquals("true", proposal.proofArtifact().fields().get("rewrite.materialized"));
        assertEquals("false", proposal.proofArtifact().fields().get("provider.mutatesOriginal"));
        assertEquals("true", proposal.proofArtifact().fields().get("proof.runtimeEquivalenceRequiredBeforeSelection"));
        assertEquals("true", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.required"));
        assertEquals("true", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.passed"));
        assertEquals("3", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.Count"));
        assertEquals("method-name-and-node-id", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.CaseIdentity"));
        assertEquals("kernel", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.MethodName"));
        assertEquals("2", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.NodeId"));
        assertEquals("typed-dead-code-removal", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.Case.0.RewriteKind"));
        assertEquals("true", proposal.proofArtifact().fields().get("safety.sideEffectFreedomProven"));
        assertEquals("typed-body-root-materialization", proposal.proofArtifact().fields().get("safety.reachabilityScope"));
        assertEquals("non-side-effecting-unreachable-typed-nodes",
                proposal.proofArtifact().fields().get("safety.removedNodeKindScope"));
        assertEquals("none", proposal.proofArtifact().fields().get("firstBlocker"));
    }

    @Test
    void blocksMaterializationWhenUnreachableNodeMayHaveSideEffects() {
        IrGpuArtifact original = artifact(typedBody(
                List.of(0),
                new IrGpuTypedNode(0, "GpuIrReturn", Map.of(), Map.of("value", List.of(1))),
                new IrGpuTypedNode(1, "GpuIrLiteral", Map.of("sourceText", "1"), Map.of()),
                new IrGpuTypedNode(2, "GpuIrHelperCall", Map.of("helper", "sideEffect"), Map.of())
        ));

        GpuIrOptimizationProposal proposal = new GpuIrTypedDeadCodeMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertEquals("materialization-no-change", proposal.proofArtifact().verdict());
        assertEquals("1", proposal.proofArtifact().fields().get("blocked.sideEffectingUnreachableNode.count"));
        assertEquals("0", proposal.proofArtifact().fields().get("removedNode.count"));
        assertEquals("side-effecting-unreachable-node", proposal.proofArtifact().fields().get("firstBlocker"));
        assertEquals("false", proposal.proofArtifact().fields().get("rewrite.proposed"));
        assertEquals("false", proposal.proofArtifact().fields().get("proof.runtimeEquivalenceRequiredBeforeSelection"));
        assertEquals("false", proposal.proofArtifact().fields().get("runtimeEquivalencePayload.required"));
    }

    @Test
    void blocksMaterializationWhenTypedGraphHasMissingReferences() {
        IrGpuArtifact original = artifact(typedBody(
                List.of(99, 0),
                new IrGpuTypedNode(0, "GpuIrReturn", Map.of(), Map.of("value", List.of(1, 42))),
                new IrGpuTypedNode(1, "GpuIrLiteral", Map.of("sourceText", "1"), Map.of()),
                new IrGpuTypedNode(2, "GpuIrLiteral", Map.of("sourceText", "2"), Map.of())
        ));

        GpuIrOptimizationProposal proposal = new GpuIrTypedDeadCodeMaterializationProposalProvider()
                .propose(new GpuIrOptimizationProposalRequest(original));

        assertEquals(GpuIrOptimizationProposalDecision.NO_CHANGE, proposal.decision());
        assertFalse(proposal.hasOptimizedArtifact());
        assertEquals("1", proposal.proofArtifact().fields().get("blocked.missingRoot.count"));
        assertEquals("1", proposal.proofArtifact().fields().get("blocked.missingChildReference.count"));
        assertEquals("missing-root-node", proposal.proofArtifact().fields().get("firstBlocker"));
        assertEquals("false", proposal.proofArtifact().fields().get("safety.sideEffectFreedomProven"));
    }

    @Test
    void materializationProviderIsServiceLoaded() {
        List<GpuIrOptimizationProposalProvider> providers = ServiceLoader
                .load(GpuIrOptimizationProposalProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(GpuIrTypedDeadCodeMaterializationProposalProvider.class::isInstance)
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
