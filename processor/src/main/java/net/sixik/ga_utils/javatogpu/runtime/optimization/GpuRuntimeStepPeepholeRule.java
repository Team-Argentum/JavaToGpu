package net.sixik.ga_utils.javatogpu.runtime.optimization;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;
import net.sixik.ga_utils.javatogpu.runtime.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detects simple typed ternary forms that can become {@code step(edge, x)}.
 */
public final class GpuRuntimeStepPeepholeRule implements GpuRuntimeIrPeepholeRule {

    public static final String RULE_ID = "step";
    public static final String VERSION = "peephole-rule:step-v1";

    @Override
    public GpuRuntimeIrPeepholeRuleReport analyze(GpuRuntimeIrPeepholeRuleContext context) {
        int candidates = 0;
        ArrayList<GpuRuntimeIrPeepholeReplacementPlan> plans = new ArrayList<>();
        for (IrGpuTypedNode node : context.graph().nodes()) {
            if (!context.graph().isConditional(node)) {
                continue;
            }
            GpuRuntimeIrPeepholeReplacementPlan plan = replacementPlan(context, node);
            if (plan.complete()) {
                candidates++;
            }
            if (!"not-step-shape".equals(plan.firstBlocker())) {
                plans.add(plan);
            }
        }
        return GpuRuntimeIrPeepholeRuleReport.diagnosticCandidates(
                this,
                context.methodBody().name(),
                candidates,
                Map.of(
                        "pattern", "x<edge?0:1|x>=edge?1:0",
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
        return "javatogpu.peephole.step";
    }

    @Override
    public int extensionOrder() {
        return 100;
    }

    private GpuRuntimeIrPeepholeReplacementPlan replacementPlan(
            GpuRuntimeIrPeepholeRuleContext context,
            IrGpuTypedNode conditional
    ) {
        GpuRuntimeIrTypedNodeGraph graph = context.graph();
        Integer conditionId = graph.singleChild(conditional, "condition", "cond");
        Integer thenId = graph.singleChild(conditional, "then", "true", "ifTrue");
        Integer elseId = graph.singleChild(conditional, "else", "false", "ifFalse");
        if (conditionId == null || thenId == null || elseId == null) {
            return GpuRuntimeIrPeepholeReplacementPlan.blocked(
                    RULE_ID,
                    context.methodBody().name(),
                    conditional.id(),
                    "step",
                    List.of(conditional.id()),
                    GpuRuntimeIrTypedNodeGraph.presentIds(conditionId, thenId, elseId),
                    "ternary-operands-incomplete"
            );
        }
        IrGpuTypedNode condition = graph.node(conditionId);
        if (condition == null || !"GpuIrBinary".equals(condition.kind())) {
            return blockedNotStep(context, conditional, conditionId, thenId, elseId);
        }
        Integer left = graph.singleChild(condition, "left");
        Integer right = graph.singleChild(condition, "right");
        if (left == null || right == null) {
            return GpuRuntimeIrPeepholeReplacementPlan.blocked(
                    RULE_ID,
                    context.methodBody().name(),
                    conditional.id(),
                    "step",
                    List.of(conditional.id(), condition.id()),
                    List.of(thenId, elseId),
                    "comparison-operands-incomplete"
            );
        }
        IrGpuTypedNode thenNode = graph.node(thenId);
        IrGpuTypedNode elseNode = graph.node(elseId);
        String operator = graph.attribute(condition, "operator");
        if ("<".equals(operator) && isZero(graph, thenNode) && isOne(graph, elseNode)) {
            return complete(context, conditional, condition, right, left);
        }
        if (">=".equals(operator) && isOne(graph, thenNode) && isZero(graph, elseNode)) {
            return complete(context, conditional, condition, right, left);
        }
        if (("<".equals(operator) || ">=".equals(operator))
                && (isZero(graph, thenNode) || isOne(graph, thenNode)
                || isZero(graph, elseNode) || isOne(graph, elseNode))) {
            return GpuRuntimeIrPeepholeReplacementPlan.blocked(
                    RULE_ID,
                    context.methodBody().name(),
                    conditional.id(),
                    "step",
                    List.of(conditional.id(), condition.id()),
                    List.of(left, right, thenId, elseId),
                    "step-branch-values-unsupported"
            );
        }
        return blockedNotStep(context, conditional, conditionId, thenId, elseId);
    }

    private static GpuRuntimeIrPeepholeReplacementPlan complete(
            GpuRuntimeIrPeepholeRuleContext context,
            IrGpuTypedNode conditional,
            IrGpuTypedNode condition,
            int edgeId,
            int valueId
    ) {
        return GpuRuntimeIrPeepholeReplacementPlan.complete(
                RULE_ID,
                context.methodBody().name(),
                conditional.id(),
                "step",
                List.of(conditional.id(), condition.id()),
                List.of(edgeId, valueId)
        );
    }

    private static GpuRuntimeIrPeepholeReplacementPlan blockedNotStep(
            GpuRuntimeIrPeepholeRuleContext context,
            IrGpuTypedNode conditional,
            int conditionId,
            int thenId,
            int elseId
    ) {
        return GpuRuntimeIrPeepholeReplacementPlan.blocked(
                RULE_ID,
                context.methodBody().name(),
                conditional.id(),
                "step",
                List.of(conditional.id()),
                List.of(conditionId, thenId, elseId),
                "not-step-shape"
        );
    }

    private static boolean isZero(GpuRuntimeIrTypedNodeGraph graph, IrGpuTypedNode node) {
        return graph.literalText(node).matches("[+]?0(?:\\.0+)?[fFdD]?");
    }

    private static boolean isOne(GpuRuntimeIrTypedNodeGraph graph, IrGpuTypedNode node) {
        return graph.literalText(node).matches("[+]?1(?:\\.0+)?[fFdD]?");
    }
}
