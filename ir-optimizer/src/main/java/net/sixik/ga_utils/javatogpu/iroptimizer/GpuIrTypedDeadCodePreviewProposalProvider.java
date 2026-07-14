package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Preview-only typed dead-code readiness analysis for unreachable typed IR nodes.
 */
public final class GpuIrTypedDeadCodePreviewProposalProvider implements GpuIrOptimizationProposalProvider {

    public static final String PROVIDER_ID = GpuIrOptimizerModule.MODULE_ID + ".typed-dead-code-preview";
    public static final String PROVIDER_VERSION = PROVIDER_ID + ":1";

    @Override
    public GpuIrOptimizationProposal propose(GpuIrOptimizationProposalRequest request) {
        IrGpuArtifact original = request.originalArtifact();
        Preview preview = Preview.from(original);
        return new GpuIrOptimizationProposal(
                extensionId(),
                extensionVersion(),
                original,
                Optional.empty(),
                GpuIrOptimizationProposalDecision.NO_CHANGE,
                GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "ir-optimizer.typed-dead-code-preview",
                        preview.verdict(),
                        preview.fields()
                ),
                "",
                List.of(preview.diagnostic())
        );
    }

    @Override
    public String extensionId() {
        return PROVIDER_ID;
    }

    @Override
    public String extensionVersion() {
        return PROVIDER_VERSION;
    }

    @Override
    public int extensionOrder() {
        return 420;
    }

    private record Preview(
            int methodBodyCount,
            int typedBodyCount,
            int nodeCount,
            int reachableNodeCount,
            int unreachableNodeCount,
            int rootMissingCount,
            int missingChildReferenceCount,
            int sideEffectingUnreachableNodeCount,
            Map<String, Integer> unreachableKindCounts,
            String firstUnreachableNode,
            String firstBlocker
    ) {

        private static Preview from(IrGpuArtifact artifact) {
            int methodBodyCount = artifact.module().methodBodies().size();
            int typedBodyCount = 0;
            int nodeCount = 0;
            int reachableNodeCount = 0;
            int unreachableNodeCount = 0;
            int rootMissingCount = 0;
            int missingChildReferenceCount = 0;
            int sideEffectingUnreachableNodeCount = 0;
            LinkedHashMap<String, Integer> unreachableKindCounts = new LinkedHashMap<>();
            String firstUnreachableNode = "none";
            String firstBlocker = "none";

            for (IrGpuMethodBody methodBody : artifact.module().methodBodies()) {
                if (!methodBody.typedBody().available()) {
                    continue;
                }
                typedBodyCount++;
                IrGpuTypedBody typedBody = methodBody.typedBody();
                Map<Integer, IrGpuTypedNode> nodesById = GpuIrTypedBodyGraphPatch.nodesById(typedBody);
                GpuIrTypedBodyGraphPatch.Reachability reachability = GpuIrTypedBodyGraphPatch
                        .reachability(typedBody, nodesById);
                nodeCount += typedBody.nodes().size();
                reachableNodeCount += reachability.reachableNodeIds().size();
                rootMissingCount += reachability.rootMissingCount();
                missingChildReferenceCount += reachability.missingChildReferenceCount();
                for (IrGpuTypedNode node : typedBody.nodes()) {
                    if (reachability.reachableNodeIds().contains(node.id())) {
                        continue;
                    }
                    unreachableNodeCount++;
                    unreachableKindCounts.merge(node.kind(), 1, Integer::sum);
                    if (isSideEffecting(node)) {
                        sideEffectingUnreachableNodeCount++;
                        if ("none".equals(firstBlocker)) {
                            firstBlocker = "side-effecting-unreachable-node";
                        }
                    }
                    if ("none".equals(firstUnreachableNode)) {
                        firstUnreachableNode = methodBody.name() + "#" + node.id() + "=" + node.kind();
                    }
                }
                if (reachability.rootMissingCount() > 0 && "none".equals(firstBlocker)) {
                    firstBlocker = "missing-root-node";
                }
                if (reachability.missingChildReferenceCount() > 0 && "none".equals(firstBlocker)) {
                    firstBlocker = "missing-child-reference";
                }
            }
            if (unreachableNodeCount == 0 && "none".equals(firstBlocker)) {
                firstBlocker = nodeCount == 0 ? "no-typed-nodes" : "no-unreachable-typed-nodes";
            }

            return new Preview(
                    methodBodyCount,
                    typedBodyCount,
                    nodeCount,
                    reachableNodeCount,
                    unreachableNodeCount,
                    rootMissingCount,
                    missingChildReferenceCount,
                    sideEffectingUnreachableNodeCount,
                    Map.copyOf(unreachableKindCounts),
                    firstUnreachableNode,
                    firstBlocker
            );
        }

        private String verdict() {
            return unreachableNodeCount == 0 ? "preview-no-candidates" : "preview-candidates-recorded";
        }

        private String diagnostic() {
            return unreachableNodeCount == 0
                    ? "typed dead-code preview found no unreachable typed nodes"
                    : "typed dead-code preview recorded " + unreachableNodeCount + " unreachable typed node(s); no rewrite was proposed";
        }

        private Map<String, String> fields() {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("methodBody.count", Integer.toString(methodBodyCount));
            fields.put("typedBody.count", Integer.toString(typedBodyCount));
            fields.put("node.count", Integer.toString(nodeCount));
            fields.put("reachableNode.count", Integer.toString(reachableNodeCount));
            fields.put("unreachableNode.count", Integer.toString(unreachableNodeCount));
            fields.put("blocked.missingRoot.count", Integer.toString(rootMissingCount));
            fields.put("blocked.missingChildReference.count", Integer.toString(missingChildReferenceCount));
            fields.put("blocked.sideEffectingUnreachableNode.count", Integer.toString(sideEffectingUnreachableNodeCount));
            fields.put("rewrite.proposed", "false");
            fields.put("mutationRequired", "false");
            fields.put("productionAffecting", "false");
            fields.put("previewOnly", "true");
            fields.put("policy.proposalOnly", "true");
            fields.put("policy.mutationAllowed", "false");
            fields.put("policy.rollbackRequired", "true");
            fields.put("policy.proofRequired", "true");
            fields.put("proof.runtimeEquivalenceRequiredBeforeRewrite", "true");
            fields.put("proof.approvalRequiredBeforeRewrite", "true");
            fields.put("safety.sideEffectFreedomProven", "false");
            fields.put("safety.reachabilityScope", "typed-body-root-preview");
            fields.put("unreachableKind.counts", countsSummary(unreachableKindCounts));
            fields.put("firstUnreachableNode", firstUnreachableNode);
            fields.put("firstBlocker", unreachableNodeCount == 0 ? firstBlocker : "preview-only-no-rewrite");
            return Map.copyOf(fields);
        }

        private static String countsSummary(Map<String, Integer> counts) {
            if (counts == null || counts.isEmpty()) {
                return "{}";
            }
            return counts.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(",", "{", "}"));
        }
    }

    private static boolean isSideEffecting(IrGpuTypedNode node) {
        return List.of(
                "GpuIrAssignment",
                "GpuIrHelperCall",
                "GpuIrIntrinsicCall",
                "GpuIrReturn",
                "GpuIrBreak",
                "GpuIrContinue",
                "GpuIrLoopBreak"
        ).contains(node.kind());
    }

}
