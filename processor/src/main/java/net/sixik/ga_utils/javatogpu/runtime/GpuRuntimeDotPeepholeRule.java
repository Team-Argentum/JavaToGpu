package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects fixed-width additive multiply trees that can become {@code dot(...)}.
 */
public final class GpuRuntimeDotPeepholeRule implements GpuRuntimeIrPeepholeRule {

    public static final String RULE_ID = "dot";
    public static final String VERSION = "peephole-rule:dot-v1";

    @Override
    public GpuRuntimeIrPeepholeRuleReport analyze(GpuRuntimeIrPeepholeRuleContext context) {
        GpuRuntimeIrTypedNodeGraph graph = context.graph();
        Set<Integer> nestedAddNodes = nestedAddNodes(graph);
        int candidates = 0;
        ArrayList<GpuRuntimeIrPeepholeReplacementPlan> plans = new ArrayList<>();
        for (IrGpuTypedNode node : graph.nodes()) {
            if (!graph.isBinary(node, "+") || nestedAddNodes.contains(node.id())) {
                continue;
            }
            GpuRuntimeIrPeepholeReplacementPlan plan = replacementPlan(context, node);
            if (plan.complete()) {
                candidates++;
            }
            if (!"not-dot-shape".equals(plan.firstBlocker())) {
                plans.add(plan);
            }
        }
        return GpuRuntimeIrPeepholeRuleReport.diagnosticCandidates(
                this,
                context.methodBody().name(),
                candidates,
                Map.of(
                        "pattern", "sum(aN*bN)",
                        "replacementPlan.count", Integer.toString(plans.size()),
                        "replacementPlan.complete.count", Long.toString(plans.stream().filter(GpuRuntimeIrPeepholeReplacementPlan::complete).count()),
                        "replacementPlan.partial.count", Long.toString(plans.stream().filter(plan -> !plan.complete()).count()),
                        "replacementPlan.firstBlocker", plans.stream()
                                .filter(plan -> !plan.complete())
                                .map(GpuRuntimeIrPeepholeReplacementPlan::firstBlocker)
                                .findFirst()
                                .orElse("none")
                ),
                plans
        );
    }

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public String ruleVersion() {
        return VERSION;
    }

    @Override
    public String extensionId() {
        return "javatogpu.peephole.dot";
    }

    @Override
    public int extensionOrder() {
        return 100;
    }

    private GpuRuntimeIrPeepholeReplacementPlan replacementPlan(
            GpuRuntimeIrPeepholeRuleContext context,
            IrGpuTypedNode addNode
    ) {
        DotPlan plan = collect(context.graph(), addNode);
        if (!"none".equals(plan.firstBlocker())) {
            return GpuRuntimeIrPeepholeReplacementPlan.blocked(
                    RULE_ID,
                    context.methodBody().name(),
                    addNode.id(),
                    "dot",
                    plan.coveredNodeIds(),
                    plan.inputNodeIds(),
                    plan.firstBlocker()
            );
        }
        if (plan.productCount() < 2) {
            return GpuRuntimeIrPeepholeReplacementPlan.blocked(
                    RULE_ID,
                    context.methodBody().name(),
                    addNode.id(),
                    "dot",
                    plan.coveredNodeIds(),
                    plan.inputNodeIds(),
                    "not-dot-shape"
            );
        }
        return GpuRuntimeIrPeepholeReplacementPlan.complete(
                RULE_ID,
                context.methodBody().name(),
                addNode.id(),
                "dot",
                plan.coveredNodeIds(),
                plan.inputNodeIds()
        );
    }

    private static DotPlan collect(GpuRuntimeIrTypedNodeGraph graph, IrGpuTypedNode node) {
        if (node == null) {
            return DotPlan.blocked(List.of(), List.of(), "dot-node-missing");
        }
        if (graph.isBinary(node, "+")) {
            Integer left = graph.singleChild(node, "left");
            Integer right = graph.singleChild(node, "right");
            if (left == null || right == null) {
                return DotPlan.blocked(
                        List.of(node.id()),
                        GpuRuntimeIrTypedNodeGraph.presentIds(left, right),
                        "dot-add-operands-incomplete"
                );
            }
            DotPlan leftPlan = collect(graph, graph.node(left));
            DotPlan rightPlan = collect(graph, graph.node(right));
            return DotPlan.merge(node.id(), leftPlan, rightPlan);
        }
        if (graph.isBinary(node, "*")) {
            Integer left = graph.singleChild(node, "left");
            Integer right = graph.singleChild(node, "right");
            if (left == null || right == null) {
                return DotPlan.blocked(
                        List.of(node.id()),
                        GpuRuntimeIrTypedNodeGraph.presentIds(left, right),
                        "dot-multiply-operands-incomplete"
                );
            }
            return new DotPlan(List.of(node.id()), List.of(left, right), 1, "none");
        }
        return DotPlan.blocked(List.of(), List.of(node.id()), "not-dot-shape");
    }

    private static Set<Integer> nestedAddNodes(GpuRuntimeIrTypedNodeGraph graph) {
        HashSet<Integer> nested = new HashSet<>();
        for (IrGpuTypedNode node : graph.nodes()) {
            if (!graph.isBinary(node, "+")) {
                continue;
            }
            for (Integer childId : graph.childIds(node, "left", "right")) {
                IrGpuTypedNode child = graph.node(childId);
                if (graph.isBinary(child, "+")) {
                    nested.add(child.id());
                }
            }
        }
        return nested;
    }

    private record DotPlan(
            List<Integer> coveredNodeIds,
            List<Integer> inputNodeIds,
            int productCount,
            String firstBlocker
    ) {

        private DotPlan {
            coveredNodeIds = List.copyOf(coveredNodeIds);
            inputNodeIds = List.copyOf(inputNodeIds);
            firstBlocker = firstBlocker == null || firstBlocker.isBlank() ? "none" : firstBlocker;
        }

        private static DotPlan blocked(List<Integer> coveredNodeIds, List<Integer> inputNodeIds, String blocker) {
            return new DotPlan(coveredNodeIds, inputNodeIds, 0, blocker);
        }

        private static DotPlan merge(int addNodeId, DotPlan left, DotPlan right) {
            ArrayList<Integer> covered = new ArrayList<>();
            covered.add(addNodeId);
            covered.addAll(left.coveredNodeIds());
            covered.addAll(right.coveredNodeIds());
            ArrayList<Integer> inputs = new ArrayList<>();
            inputs.addAll(left.inputNodeIds());
            inputs.addAll(right.inputNodeIds());
            String blocker = !"none".equals(left.firstBlocker()) ? left.firstBlocker() : right.firstBlocker();
            return new DotPlan(covered, inputs, left.productCount() + right.productCount(), blocker);
        }
    }
}
