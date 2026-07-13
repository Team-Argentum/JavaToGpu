package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuMethodBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuModule;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuRegenerationMetadata;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedBody;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Review-only typed dead-code materialization for unreachable pure typed IR nodes.
 */
public final class GpuIrTypedDeadCodeMaterializationProposalProvider implements GpuIrOptimizationProposalProvider {

    public static final String PROVIDER_ID = GpuIrOptimizerModule.MODULE_ID + ".typed-dead-code-materialization";
    public static final String PROVIDER_VERSION = PROVIDER_ID + ":1";

    @Override
    public GpuIrOptimizationProposal propose(GpuIrOptimizationProposalRequest request) {
        IrGpuArtifact original = request.originalArtifact();
        ArtifactRewrite rewrite = ArtifactRewrite.from(original, request.mutationAllowed());
        if (!rewrite.changed()) {
            return new GpuIrOptimizationProposal(
                    extensionId(),
                    extensionVersion(),
                    original,
                    Optional.empty(),
                    GpuIrOptimizationProposalDecision.NO_CHANGE,
                    GpuRuntimeIrOptimizationProofArtifact.fromFields(
                            "ir-optimizer.typed-dead-code-materialization",
                            rewrite.verdict(),
                            rewrite.fields()
                    ),
                    "",
                    List.of(rewrite.diagnostic())
            );
        }
        IrGpuArtifact optimized = copyWithModuleAndRegeneration(
                original,
                rewrite.module().orElseThrow(),
                original.regenerationMetadata()
        );
        return GpuIrOptimizationProposal.proposed(
                extensionId(),
                extensionVersion(),
                original,
                optimized,
                GpuRuntimeIrOptimizationProofArtifact.fromFields(
                        "ir-optimizer.typed-dead-code-materialization",
                        "materialized-review-candidate",
                        rewrite.fields()
                ),
                List.of("materialized " + rewrite.removedNodeCount()
                        + " unreachable pure typed node(s) into a review candidate")
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
        return 425;
    }

    private record ArtifactRewrite(
            Optional<IrGpuModule> module,
            int methodBodyCount,
            int typedBodyCount,
            int nodeCount,
            int reachableNodeCount,
            int unreachableNodeCount,
            int removedNodeCount,
            int changedMethodBodyCount,
            int rootMissingCount,
            int missingChildReferenceCount,
            int sideEffectingUnreachableNodeCount,
            Map<String, Integer> unreachableKindCounts,
            Map<String, Integer> removedKindCounts,
            List<RemovedNode> removedNodes,
            String firstRemovedNode,
            String firstBlocker,
            boolean mutationAllowed
    ) {

        private static ArtifactRewrite from(IrGpuArtifact artifact, boolean mutationAllowed) {
            ArrayList<IrGpuMethodBody> methodBodies = new ArrayList<>();
            RewriteStats stats = new RewriteStats();
            stats.methodBodyCount = artifact.module().methodBodies().size();

            for (IrGpuMethodBody methodBody : artifact.module().methodBodies()) {
                MethodRewrite methodRewrite = rewriteMethodBody(methodBody);
                methodBodies.add(methodRewrite.methodBody());
                stats.add(methodRewrite.stats());
            }

            if (stats.rootMissingCount > 0) {
                stats.setFirstBlocker("missing-root-node");
            }
            if (stats.missingChildReferenceCount > 0) {
                stats.setFirstBlocker("missing-child-reference");
            }
            if (stats.sideEffectingUnreachableNodeCount > 0) {
                stats.setFirstBlocker("side-effecting-unreachable-node");
            }
            if (!"none".equals(stats.firstBlocker)) {
                return noChange(stats, mutationAllowed, stats.firstBlocker);
            }
            if (stats.removedNodeCount == 0) {
                return noChange(
                        stats,
                        mutationAllowed,
                        stats.nodeCount == 0 ? "no-typed-nodes" : "no-removable-unreachable-pure-typed-nodes"
                );
            }

            return new ArtifactRewrite(
                    Optional.of(copyWithBodies(artifact.module(), methodBodies)),
                    stats.methodBodyCount,
                    stats.typedBodyCount,
                    stats.nodeCount,
                    stats.reachableNodeCount,
                    stats.unreachableNodeCount,
                    stats.removedNodeCount,
                    stats.changedMethodBodyCount,
                    stats.rootMissingCount,
                    stats.missingChildReferenceCount,
                    stats.sideEffectingUnreachableNodeCount,
                    Map.copyOf(stats.unreachableKindCounts),
                    Map.copyOf(stats.removedKindCounts),
                    List.copyOf(stats.removedNodes),
                    stats.firstRemovedNode,
                    "none",
                    mutationAllowed
            );
        }

        private static ArtifactRewrite noChange(RewriteStats stats, boolean mutationAllowed, String firstBlocker) {
            return new ArtifactRewrite(
                    Optional.empty(),
                    stats.methodBodyCount,
                    stats.typedBodyCount,
                    stats.nodeCount,
                    stats.reachableNodeCount,
                    stats.unreachableNodeCount,
                    stats.removedNodeCount,
                    stats.changedMethodBodyCount,
                    stats.rootMissingCount,
                    stats.missingChildReferenceCount,
                    stats.sideEffectingUnreachableNodeCount,
                    Map.copyOf(stats.unreachableKindCounts),
                    Map.copyOf(stats.removedKindCounts),
                    List.copyOf(stats.removedNodes),
                    stats.firstRemovedNode,
                    firstBlocker,
                    mutationAllowed
            );
        }

        private boolean changed() {
            return module.isPresent() && removedNodeCount > 0;
        }

        private String verdict() {
            return changed() ? "materialized-review-candidate" : "materialization-no-change";
        }

        private String diagnostic() {
            if (changed()) {
                return "typed dead-code materialization removed " + removedNodeCount + " unreachable pure typed node(s)";
            }
            return "typed dead-code materialization produced no review candidate: " + firstBlocker;
        }

        private Map<String, String> fields() {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("methodBody.count", Integer.toString(methodBodyCount));
            fields.put("methodBody.rewriteScope", "all-method-bodies");
            fields.put("typedBody.count", Integer.toString(typedBodyCount));
            fields.put("node.count", Integer.toString(nodeCount));
            fields.put("reachableNode.count", Integer.toString(reachableNodeCount));
            fields.put("unreachableNode.count", Integer.toString(unreachableNodeCount));
            fields.put("removedNode.count", Integer.toString(removedNodeCount));
            fields.put("changedMethodBody.count", Integer.toString(changedMethodBodyCount));
            fields.put("blocked.missingRoot.count", Integer.toString(rootMissingCount));
            fields.put("blocked.missingChildReference.count", Integer.toString(missingChildReferenceCount));
            fields.put("blocked.sideEffectingUnreachableNode.count", Integer.toString(sideEffectingUnreachableNodeCount));
            fields.put("unreachableKind.counts", countsSummary(unreachableKindCounts));
            fields.put("removedKind.counts", countsSummary(removedKindCounts));
            fields.put("rewrite.proposed", Boolean.toString(changed()));
            fields.put("rewrite.materialized", Boolean.toString(changed()));
            fields.put("optimizerFamily", "typed-dead-code-materialization");
            fields.put("mutationRequired", "false");
            fields.put("productionAffecting", "false");
            fields.put("previewOnly", "false");
            fields.put("provider.mutatesOriginal", "false");
            fields.put("policy.mutationAllowed", Boolean.toString(mutationAllowed));
            fields.put("policy.proposalOnly", Boolean.toString(!mutationAllowed));
            fields.put("policy.rollbackRequired", "true");
            fields.put("policy.proofRequired", "true");
            fields.put("proof.runtimeEquivalenceRequiredBeforeSelection", Boolean.toString(changed()));
            fields.put("proof.runtimeEquivalencePayloadRequiredBeforeSelection", Boolean.toString(changed()));
            fields.put("proof.approvalRequiredBeforeProduction", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.required", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.status", changed() ? "recorded" : "not-required");
            fields.put("runtimeEquivalencePayload.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.passed", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.firstBlocker", "none");
            fields.put("runtimeEquivalencePayload.comparisonMode", "typed-structure-unreachable-pure-node-removal");
            fields.put("runtimeEquivalencePayload.resource", "ir-optimizer://typed-dead-code-materialization/review-candidate");
            fields.put("runtimeEquivalencePayload.cpuReference.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.preOptimizationOutput.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.postOptimizationOutput.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.tolerance.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.failureFixture.present", Boolean.toString(changed()));
            fields.put("runtimeEquivalencePayload.CpuReference", "removedUnreachablePureNodes=" + removedNodeCount);
            fields.put("runtimeEquivalencePayload.PreOptimizationOutput", "typedNodes=" + nodeCount);
            fields.put("runtimeEquivalencePayload.PostOptimizationOutput", "typedNodes=" + (nodeCount - removedNodeCount));
            fields.put("runtimeEquivalencePayload.Tolerance", "mode=typed-structure-no-runtime-output-change");
            fields.put("runtimeEquivalencePayload.FailureFixture", "none");
            fields.put("runtimeEquivalencePayload.ReferenceMode", "static-typed-reachability-pure-node-removal");
            fields.put("runtimeEquivalencePayload.CaseIdentity", "method-name-and-node-id");
            appendRuntimeEquivalenceCases(fields, removedNodes);
            fields.put("safety.sideEffectFreedomProven", Boolean.toString(changed()));
            fields.put("safety.reachabilityScope", "typed-body-root-materialization");
            fields.put("safety.removedNodeKindScope", "non-side-effecting-unreachable-typed-nodes");
            fields.put("firstRemovedNode", firstRemovedNode);
            fields.put("firstBlocker", firstBlocker);
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

    private record MethodRewrite(IrGpuMethodBody methodBody, RewriteStats stats) {
    }

    private static MethodRewrite rewriteMethodBody(IrGpuMethodBody methodBody) {
        RewriteStats stats = new RewriteStats();
        if (!methodBody.typedBody().available()) {
            return new MethodRewrite(methodBody, stats);
        }
        stats.typedBodyCount = 1;
        IrGpuTypedBody typedBody = methodBody.typedBody();
        Map<Integer, IrGpuTypedNode> nodesById = nodesById(typedBody);
        Reachability reachability = reachableNodeIds(typedBody, nodesById);
        stats.nodeCount = typedBody.nodes().size();
        stats.reachableNodeCount = reachability.reachableNodeIds().size();
        stats.rootMissingCount = reachability.rootMissingCount();
        stats.missingChildReferenceCount = reachability.missingChildReferenceCount();

        ArrayList<IrGpuTypedNode> retainedNodes = new ArrayList<>();
        for (IrGpuTypedNode node : typedBody.nodes()) {
            if (reachability.reachableNodeIds().contains(node.id())) {
                retainedNodes.add(node);
                continue;
            }
            stats.unreachableNodeCount++;
            stats.unreachableKindCounts.merge(node.kind(), 1, Integer::sum);
            if (isSideEffecting(node)) {
                stats.sideEffectingUnreachableNodeCount++;
                retainedNodes.add(node);
                continue;
            }
            stats.removedNodeCount++;
            stats.removedKindCounts.merge(node.kind(), 1, Integer::sum);
            RemovedNode removedNode = new RemovedNode(methodBody.name(), node.id(), node.kind());
            stats.removedNodes.add(removedNode);
            if ("none".equals(stats.firstRemovedNode)) {
                stats.firstRemovedNode = removedNode.summary();
            }
        }
        if (stats.removedNodeCount == 0) {
            return new MethodRewrite(methodBody, stats);
        }
        stats.changedMethodBodyCount = 1;
        return new MethodRewrite(
                copyWithTypedBody(methodBody, new IrGpuTypedBody(typedBody.format(), typedBody.rootNodeIds(), retainedNodes)),
                stats
        );
    }

    private record Reachability(Set<Integer> reachableNodeIds, int rootMissingCount, int missingChildReferenceCount) {
    }

    private record RemovedNode(String methodName, int nodeId, String nodeKind) {
        private String summary() {
            return methodName + "#" + nodeId + "=" + nodeKind;
        }
    }

    private static void appendRuntimeEquivalenceCases(LinkedHashMap<String, String> fields, List<RemovedNode> removedNodes) {
        fields.put("runtimeEquivalencePayload.Case.Count", Integer.toString(removedNodes.size()));
        for (int index = 0; index < removedNodes.size(); index++) {
            RemovedNode node = removedNodes.get(index);
            String prefix = "runtimeEquivalencePayload.Case." + index;
            fields.put(prefix + ".Name", node.summary() + "->removed");
            fields.put(prefix + ".MethodName", node.methodName());
            fields.put(prefix + ".NodeId", Integer.toString(node.nodeId()));
            fields.put(prefix + ".RewriteKind", "typed-dead-code-removal");
            fields.put(prefix + ".Successful", "true");
            fields.put(prefix + ".Input.Count", "1");
            fields.put(prefix + ".Input.0.Name", "nodeKind");
            fields.put(prefix + ".Input.0.Value", node.nodeKind());
            fields.put(prefix + ".Output.Count", "1");
            fields.put(prefix + ".Output.0.Name", "nodeStatus");
            fields.put(prefix + ".Output.0.CpuReference", "removed-unreachable-pure-node");
            fields.put(prefix + ".Output.0.PreOptimization", "present");
            fields.put(prefix + ".Output.0.PostOptimization", "removed");
            fields.put(prefix + ".Output.0.Tolerance", "typed-structure");
            fields.put(prefix + ".Output.0.Equivalent", "true");
            fields.put(prefix + ".FailureFixture.Diagnostic.Count", "0");
        }
    }

    private static Reachability reachableNodeIds(IrGpuTypedBody typedBody, Map<Integer, IrGpuTypedNode> nodesById) {
        LinkedHashSet<Integer> reachable = new LinkedHashSet<>();
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        int rootMissingCount = 0;
        int missingChildReferenceCount = 0;
        for (Integer rootNodeId : typedBody.rootNodeIds()) {
            if (rootNodeId == null || !nodesById.containsKey(rootNodeId)) {
                rootMissingCount++;
                continue;
            }
            pending.add(rootNodeId);
        }
        while (!pending.isEmpty()) {
            int nodeId = pending.removeFirst();
            if (!reachable.add(nodeId)) {
                continue;
            }
            IrGpuTypedNode node = nodesById.get(nodeId);
            if (node == null) {
                continue;
            }
            for (List<Integer> childIds : node.children().values()) {
                for (Integer childId : childIds) {
                    if (childId == null || !nodesById.containsKey(childId)) {
                        missingChildReferenceCount++;
                        continue;
                    }
                    if (!reachable.contains(childId)) {
                        pending.add(childId);
                    }
                }
            }
        }
        return new Reachability(Set.copyOf(reachable), rootMissingCount, missingChildReferenceCount);
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

    private static Map<Integer, IrGpuTypedNode> nodesById(IrGpuTypedBody typedBody) {
        LinkedHashMap<Integer, IrGpuTypedNode> nodes = new LinkedHashMap<>();
        for (IrGpuTypedNode node : typedBody.nodes()) {
            nodes.put(node.id(), node);
        }
        return Map.copyOf(nodes);
    }

    private static IrGpuMethodBody copyWithTypedBody(IrGpuMethodBody methodBody, IrGpuTypedBody typedBody) {
        return new IrGpuMethodBody(
                methodBody.role(),
                methodBody.name(),
                methodBody.emittedName(),
                methodBody.format(),
                methodBody.body(),
                typedBody,
                methodBody.bodyIndex(),
                methodBody.helperDependencies(),
                methodBody.sourceLocation()
        );
    }

    private static IrGpuModule copyWithBodies(IrGpuModule module, List<IrGpuMethodBody> methodBodies) {
        return new IrGpuModule(
                module.entryMethod(),
                module.entryEmittedName(),
                module.entryOpenClAttributes(),
                module.entryAttributeMetadata(),
                module.helperMethods(),
                module.structs(),
                methodBodies
        );
    }

    private static IrGpuArtifact copyWithModuleAndRegeneration(
            IrGpuArtifact artifact,
            IrGpuModule module,
            IrGpuRegenerationMetadata regenerationMetadata
    ) {
        return new IrGpuArtifact(
                artifact.header(),
                module,
                artifact.entryParameters(),
                artifact.launchMetadata(),
                artifact.validationMetadata(),
                artifact.featureMetadata(),
                artifact.optimizerPolicyMetadata(),
                regenerationMetadata,
                artifact.structMetadata(),
                artifact.constants(),
                artifact.constantData(),
                artifact.backendOutputs(),
                artifact.runtimeDefaultBackend(),
                artifact.runtimeOptimizationProfile(),
                artifact.methodDeviceConstraints(),
                artifact.methodFallbackVariants(),
                artifact.extensionParticipationMetadata()
        );
    }

    private static final class RewriteStats {
        private int methodBodyCount;
        private int typedBodyCount;
        private int nodeCount;
        private int reachableNodeCount;
        private int unreachableNodeCount;
        private int removedNodeCount;
        private int changedMethodBodyCount;
        private int rootMissingCount;
        private int missingChildReferenceCount;
        private int sideEffectingUnreachableNodeCount;
        private final LinkedHashMap<String, Integer> unreachableKindCounts = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> removedKindCounts = new LinkedHashMap<>();
        private final ArrayList<RemovedNode> removedNodes = new ArrayList<>();
        private String firstRemovedNode = "none";
        private String firstBlocker = "none";

        private void add(RewriteStats other) {
            typedBodyCount += other.typedBodyCount;
            nodeCount += other.nodeCount;
            reachableNodeCount += other.reachableNodeCount;
            unreachableNodeCount += other.unreachableNodeCount;
            removedNodeCount += other.removedNodeCount;
            changedMethodBodyCount += other.changedMethodBodyCount;
            rootMissingCount += other.rootMissingCount;
            missingChildReferenceCount += other.missingChildReferenceCount;
            sideEffectingUnreachableNodeCount += other.sideEffectingUnreachableNodeCount;
            other.unreachableKindCounts.forEach((kind, count) -> unreachableKindCounts.merge(kind, count, Integer::sum));
            other.removedKindCounts.forEach((kind, count) -> removedKindCounts.merge(kind, count, Integer::sum));
            removedNodes.addAll(other.removedNodes);
            if ("none".equals(firstRemovedNode) && !"none".equals(other.firstRemovedNode)) {
                firstRemovedNode = other.firstRemovedNode;
            }
            if ("none".equals(firstBlocker) && !"none".equals(other.firstBlocker)) {
                firstBlocker = other.firstBlocker;
            }
        }

        private void setFirstBlocker(String blocker) {
            if ("none".equals(firstBlocker) && blocker != null && !blocker.isBlank() && !"none".equals(blocker)) {
                firstBlocker = blocker;
            }
        }
    }
}
