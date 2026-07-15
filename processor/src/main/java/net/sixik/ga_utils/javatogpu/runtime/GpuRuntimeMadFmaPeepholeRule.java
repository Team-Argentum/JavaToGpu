package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detects typed {@code a * b + c} and {@code c + a * b} candidates.
 */
public final class GpuRuntimeMadFmaPeepholeRule implements GpuRuntimeIrPeepholeRule {

    public static final String RULE_ID = "madFma";
    public static final String VERSION = "peephole-rule:mad-fma-v1";

    @Override
    public GpuRuntimeIrPeepholeRuleReport analyze(GpuRuntimeIrPeepholeRuleContext context) {
        int candidates = 0;
        ArrayList<GpuRuntimeIrPeepholeReplacementPlan> plans = new ArrayList<>();
        for (IrGpuTypedNode node : context.graph().nodes()) {
            if (!context.graph().isBinary(node, "+")) {
                continue;
            }
            GpuRuntimeIrPeepholeReplacementPlan plan = replacementPlan(context, node);
            if (plan.complete()) {
                candidates++;
            }
            if (!"not-mad-fma-shape".equals(plan.firstBlocker())) {
                plans.add(plan);
            }
        }
        return GpuRuntimeIrPeepholeRuleReport.diagnosticCandidates(
                this,
                context.methodBody().name(),
                candidates,
                Map.of(
                        "pattern", "a*b+c|c+a*b",
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
        return "javatogpu.peephole.mad-fma";
    }

    private GpuRuntimeIrPeepholeReplacementPlan replacementPlan(
            GpuRuntimeIrPeepholeRuleContext context,
            IrGpuTypedNode addNode
    ) {
        GpuRuntimeIrTypedNodeGraph graph = context.graph();
        Integer left = graph.singleChild(addNode, "left");
        Integer right = graph.singleChild(addNode, "right");
        if (left == null || right == null) {
            return GpuRuntimeIrPeepholeReplacementPlan.blocked(
                    RULE_ID,
                    context.methodBody().name(),
                    addNode.id(),
                    "mad-fma",
                    List.of(addNode.id()),
                    List.of(),
                    "add-operands-incomplete"
            );
        }
        IrGpuTypedNode leftNode = graph.node(left);
        IrGpuTypedNode rightNode = graph.node(right);
        boolean leftMultiply = graph.isBinary(leftNode, "*");
        boolean rightMultiply = graph.isBinary(rightNode, "*");
        if (!leftMultiply && !rightMultiply) {
            return GpuRuntimeIrPeepholeReplacementPlan.blocked(
                    RULE_ID,
                    context.methodBody().name(),
                    addNode.id(),
                    "mad-fma",
                    List.of(addNode.id()),
                    List.of(left, right),
                    "not-mad-fma-shape"
            );
        }
        IrGpuTypedNode multiply = leftMultiply ? leftNode : rightNode;
        int addendId = leftMultiply ? right : left;
        Integer multiplyLeft = graph.singleChild(multiply, "left");
        Integer multiplyRight = graph.singleChild(multiply, "right");
        if (multiplyLeft == null || multiplyRight == null) {
            return GpuRuntimeIrPeepholeReplacementPlan.blocked(
                    RULE_ID,
                    context.methodBody().name(),
                    addNode.id(),
                    "mad-fma",
                    List.of(addNode.id(), multiply.id()),
                    List.of(addendId),
                    "multiply-operands-incomplete"
            );
        }
        return GpuRuntimeIrPeepholeReplacementPlan.complete(
                RULE_ID,
                context.methodBody().name(),
                addNode.id(),
                "mad-fma",
                List.of(addNode.id(), multiply.id()),
                List.of(multiplyLeft, multiplyRight, addendId)
        );
    }
}
