package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detects typed {@code a + t * (b - a)} and {@code t * (b - a) + a} candidates.
 */
public final class GpuRuntimeMixPeepholeRule implements GpuRuntimeIrPeepholeRule {

    public static final String RULE_ID = "mix";
    public static final String VERSION = "peephole-rule:mix-v1";

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
            if (!"not-mix-shape".equals(plan.firstBlocker())) {
                plans.add(plan);
            }
        }
        return GpuRuntimeIrPeepholeRuleReport.diagnosticCandidates(
                this,
                context.methodBody().name(),
                candidates,
                Map.of(
                        "pattern", "a+t*(b-a)|t*(b-a)+a",
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
        return "javatogpu.peephole.mix";
    }

    @Override
    public int extensionOrder() {
        return 100;
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
                    "mix",
                    List.of(addNode.id()),
                    GpuRuntimeIrTypedNodeGraph.presentIds(left, right),
                    "mix-add-operands-incomplete"
            );
        }
        GpuRuntimeIrPeepholeReplacementPlan leftPlan = planWithBaseAndProduct(context, addNode, left, right);
        if (leftPlan.complete() || !"not-mix-shape".equals(leftPlan.firstBlocker())) {
            return leftPlan;
        }
        return planWithBaseAndProduct(context, addNode, right, left);
    }

    private static GpuRuntimeIrPeepholeReplacementPlan planWithBaseAndProduct(
            GpuRuntimeIrPeepholeRuleContext context,
            IrGpuTypedNode addNode,
            int baseId,
            int productId
    ) {
        GpuRuntimeIrTypedNodeGraph graph = context.graph();
        IrGpuTypedNode product = graph.node(productId);
        if (!graph.isBinary(product, "*")) {
            return blockedNotMix(context, addNode, baseId, productId);
        }
        Integer productLeft = graph.singleChild(product, "left");
        Integer productRight = graph.singleChild(product, "right");
        if (productLeft == null || productRight == null) {
            return GpuRuntimeIrPeepholeReplacementPlan.blocked(
                    RULE_ID,
                    context.methodBody().name(),
                    addNode.id(),
                    "mix",
                    List.of(addNode.id(), product.id()),
                    List.of(baseId),
                    "mix-multiply-operands-incomplete"
            );
        }
        GpuRuntimeIrPeepholeReplacementPlan leftDelta = planWithDelta(context, addNode, product, baseId, productLeft, productRight);
        if (leftDelta.complete() || !"not-mix-shape".equals(leftDelta.firstBlocker())) {
            return leftDelta;
        }
        return planWithDelta(context, addNode, product, baseId, productRight, productLeft);
    }

    private static GpuRuntimeIrPeepholeReplacementPlan planWithDelta(
            GpuRuntimeIrPeepholeRuleContext context,
            IrGpuTypedNode addNode,
            IrGpuTypedNode product,
            int baseId,
            int deltaId,
            int tId
    ) {
        GpuRuntimeIrTypedNodeGraph graph = context.graph();
        IrGpuTypedNode delta = graph.node(deltaId);
        if (!graph.isBinary(delta, "-")) {
            return blockedNotMix(context, addNode, baseId, product.id());
        }
        Integer bId = graph.singleChild(delta, "left");
        Integer deltaBaseId = graph.singleChild(delta, "right");
        if (bId == null || deltaBaseId == null) {
            return GpuRuntimeIrPeepholeReplacementPlan.blocked(
                    RULE_ID,
                    context.methodBody().name(),
                    addNode.id(),
                    "mix",
                    List.of(addNode.id(), product.id(), delta.id()),
                    List.of(baseId, tId),
                    "mix-delta-operands-incomplete"
            );
        }
        if (deltaBaseId != baseId) {
            return GpuRuntimeIrPeepholeReplacementPlan.blocked(
                    RULE_ID,
                    context.methodBody().name(),
                    addNode.id(),
                    "mix",
                    List.of(addNode.id(), product.id(), delta.id()),
                    List.of(baseId, bId, deltaBaseId, tId),
                    "mix-base-mismatch"
            );
        }
        return GpuRuntimeIrPeepholeReplacementPlan.complete(
                RULE_ID,
                context.methodBody().name(),
                addNode.id(),
                "mix",
                List.of(addNode.id(), product.id(), delta.id()),
                List.of(baseId, bId, tId)
        );
    }

    private static GpuRuntimeIrPeepholeReplacementPlan blockedNotMix(
            GpuRuntimeIrPeepholeRuleContext context,
            IrGpuTypedNode addNode,
            int left,
            int right
    ) {
        return GpuRuntimeIrPeepholeReplacementPlan.blocked(
                RULE_ID,
                context.methodBody().name(),
                addNode.id(),
                "mix",
                List.of(addNode.id()),
                List.of(left, right),
                "not-mix-shape"
        );
    }
}
