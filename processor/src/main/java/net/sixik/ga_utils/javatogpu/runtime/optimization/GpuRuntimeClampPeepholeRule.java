package net.sixik.ga_utils.javatogpu.runtime.optimization;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;
import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detects typed {@code min(max(x, lo), hi)} clamp candidates without rewriting them.
 */
public final class GpuRuntimeClampPeepholeRule implements GpuRuntimeIrPeepholeRule {

    public static final String RULE_ID = "clamp";
    public static final String VERSION = "peephole-rule:clamp-v1";

    @Override
    public GpuRuntimeIrPeepholeRuleReport analyze(GpuRuntimeIrPeepholeRuleContext context) {
        int candidates = 0;
        ArrayList<GpuRuntimeIrPeepholeReplacementPlan> plans = new ArrayList<>();
        for (IrGpuTypedNode node : context.graph().nodes()) {
            if (!context.graph().isCall(node, "min")) {
                continue;
            }
            GpuRuntimeIrPeepholeReplacementPlan plan = replacementPlan(context, node);
            if (plan.complete()) {
                candidates++;
            }
            if (!"not-clamp-shape".equals(plan.firstBlocker())) {
                plans.add(plan);
            }
        }
        return GpuRuntimeIrPeepholeRuleReport.diagnosticCandidates(
                this,
                context.methodBody().name(),
                candidates,
                Map.of(
                        "pattern", "min(max(x,lo),hi)",
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
        return "javatogpu.peephole.clamp";
    }

    @Override
    public int extensionOrder() {
        return 100;
    }

    private GpuRuntimeIrPeepholeReplacementPlan replacementPlan(
            GpuRuntimeIrPeepholeRuleContext context,
            IrGpuTypedNode minNode
    ) {
        GpuRuntimeIrTypedNodeGraph graph = context.graph();
        List<Integer> minArgs = graph.callArguments(minNode);
        if (minArgs.size() != 2) {
            return GpuRuntimeIrPeepholeReplacementPlan.blocked(
                    RULE_ID,
                    context.methodBody().name(),
                    minNode.id(),
                    "clamp",
                    List.of(minNode.id()),
                    minArgs,
                    "min-arguments-incomplete"
            );
        }
        IrGpuTypedNode first = graph.node(minArgs.get(0));
        IrGpuTypedNode second = graph.node(minArgs.get(1));
        boolean firstMax = graph.isCall(first, "max");
        boolean secondMax = graph.isCall(second, "max");
        if (!firstMax && !secondMax) {
            return GpuRuntimeIrPeepholeReplacementPlan.blocked(
                    RULE_ID,
                    context.methodBody().name(),
                    minNode.id(),
                    "clamp",
                    List.of(minNode.id()),
                    minArgs,
                    "not-clamp-shape"
            );
        }
        IrGpuTypedNode maxNode = firstMax ? first : second;
        int hiId = firstMax ? minArgs.get(1) : minArgs.get(0);
        List<Integer> maxArgs = graph.callArguments(maxNode);
        if (maxArgs.size() != 2) {
            return GpuRuntimeIrPeepholeReplacementPlan.blocked(
                    RULE_ID,
                    context.methodBody().name(),
                    minNode.id(),
                    "clamp",
                    List.of(minNode.id(), maxNode.id()),
                    List.of(hiId),
                    "max-arguments-incomplete"
            );
        }
        return GpuRuntimeIrPeepholeReplacementPlan.complete(
                RULE_ID,
                context.methodBody().name(),
                minNode.id(),
                "clamp",
                List.of(minNode.id(), maxNode.id()),
                List.of(maxArgs.get(0), maxArgs.get(1), hiId)
        );
    }
}
