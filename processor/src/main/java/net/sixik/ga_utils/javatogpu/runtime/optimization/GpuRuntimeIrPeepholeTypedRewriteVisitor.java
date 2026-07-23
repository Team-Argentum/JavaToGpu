package net.sixik.ga_utils.javatogpu.runtime.optimization;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrPeepholeReplacementPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrPeepholeReplacementPlanValidation;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrPeepholeRewriteVisitPreflight;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrTypedNodeGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shared read-only visitor preflight for future typed peephole rewrites.
 *
 * <p>The visitor only verifies deterministic access to original nodes referenced by a replacement plan. Replacement
 * node construction and artifact mutation intentionally stay outside this class.</p>
 */
public final class GpuRuntimeIrPeepholeTypedRewriteVisitor {

    private final GpuRuntimeIrTypedNodeGraph graph;

    private GpuRuntimeIrPeepholeTypedRewriteVisitor(GpuRuntimeIrTypedNodeGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    public static GpuRuntimeIrPeepholeTypedRewriteVisitor forGraph(GpuRuntimeIrTypedNodeGraph graph) {
        return new GpuRuntimeIrPeepholeTypedRewriteVisitor(graph);
    }

    public GpuRuntimeIrPeepholeRewriteVisitPreflight preflight(
            GpuRuntimeIrPeepholeReplacementPlan plan,
            GpuRuntimeIrPeepholeReplacementPlanValidation validation
    ) {
        if (plan == null) {
            return GpuRuntimeIrPeepholeRewriteVisitPreflight.blocked(
                    "rule:unknown",
                    "unknown",
                    0,
                    "unknown",
                    List.of(),
                    List.of(),
                    List.of(),
                    graph.maxNodeId(),
                    false,
                    false,
                    false,
                    false,
                    false,
                    "replacement-plan-missing"
            );
        }

        boolean rootVisitable = graph.containsNode(plan.rootNodeId());
        boolean coveredNodesVisitable = graph.missingNodeIds(plan.coveredNodeIds()).isEmpty();
        boolean inputNodesVisitable = graph.missingNodeIds(plan.inputNodeIds()).isEmpty();
        boolean planValid = validation != null && validation.valid();
        List<Integer> visitOrder = visitOrder(plan);
        String firstBlocker = firstBlocker(
                plan,
                validation,
                rootVisitable,
                coveredNodesVisitable,
                inputNodesVisitable,
                planValid
        );
        if (!"none".equals(firstBlocker)) {
            return GpuRuntimeIrPeepholeRewriteVisitPreflight.blocked(
                    plan.ruleId(),
                    plan.methodName(),
                    plan.rootNodeId(),
                    plan.replacementKind(),
                    plan.coveredNodeIds(),
                    plan.inputNodeIds(),
                    visitOrder,
                    graph.maxNodeId(),
                    plan.complete(),
                    planValid,
                    rootVisitable,
                    coveredNodesVisitable,
                    inputNodesVisitable,
                    firstBlocker
            );
        }
        return GpuRuntimeIrPeepholeRewriteVisitPreflight.ready(plan, visitOrder, graph.maxNodeId());
    }

    private List<Integer> visitOrder(GpuRuntimeIrPeepholeReplacementPlan plan) {
        ArrayList<Integer> ids = new ArrayList<>();
        addIfPresent(ids, plan.rootNodeId());
        for (Integer id : plan.coveredNodeIds()) {
            addIfPresent(ids, id);
        }
        for (Integer id : plan.inputNodeIds()) {
            addIfPresent(ids, id);
        }
        return List.copyOf(ids);
    }

    private void addIfPresent(List<Integer> ids, Integer id) {
        if (id != null && graph.containsNode(id) && !ids.contains(id)) {
            ids.add(id);
        }
    }

    private static String firstBlocker(
            GpuRuntimeIrPeepholeReplacementPlan plan,
            GpuRuntimeIrPeepholeReplacementPlanValidation validation,
            boolean rootVisitable,
            boolean coveredNodesVisitable,
            boolean inputNodesVisitable,
            boolean planValid
    ) {
        if (!plan.complete()) {
            return plan.firstBlocker();
        }
        if (validation == null) {
            return "replacement-plan-validation-missing";
        }
        if (!planValid) {
            return validation.firstBlocker();
        }
        if (!rootVisitable) {
            return "rewrite-visitor-root-not-visitable";
        }
        if (!coveredNodesVisitable) {
            return "rewrite-visitor-covered-node-not-visitable";
        }
        if (!inputNodesVisitable) {
            return "rewrite-visitor-input-node-not-visitable";
        }
        return "none";
    }
}
