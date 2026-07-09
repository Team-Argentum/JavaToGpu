package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuTypedNode;

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
        for (IrGpuTypedNode node : context.typedBody().nodes()) {
            if (!isBinary(node, "+")) {
                continue;
            }
            if (childIsBinary(node, "left", "*", context.nodesById())
                    || childIsBinary(node, "right", "*", context.nodesById())) {
                candidates++;
            }
        }
        return GpuRuntimeIrPeepholeRuleReport.diagnosticCandidates(
                this,
                context.methodBody().name(),
                candidates,
                Map.of("pattern", "a*b+c|c+a*b")
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

    private static boolean childIsBinary(
            IrGpuTypedNode parent,
            String childName,
            String operator,
            Map<Integer, IrGpuTypedNode> nodesById
    ) {
        List<Integer> childIds = parent.children().getOrDefault(childName, List.of());
        if (childIds.size() != 1) {
            return false;
        }
        return isBinary(nodesById.get(childIds.get(0)), operator);
    }

    private static boolean isBinary(IrGpuTypedNode node, String operator) {
        return node != null
                && "GpuIrBinary".equals(node.kind())
                && operator.equals(node.attributes().get("operator"));
    }
}
