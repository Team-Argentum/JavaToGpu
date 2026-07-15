package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fail-closed structural validation for a read-only peephole replacement plan.
 */
public record GpuRuntimeIrPeepholeReplacementPlanValidation(
        String ruleId,
        String methodName,
        int rootNodeId,
        String replacementKind,
        boolean valid,
        String firstBlocker,
        boolean rootExists,
        boolean coveredIncludesRoot,
        List<Integer> missingCoveredNodeIds,
        List<Integer> missingInputNodeIds
) {

    public GpuRuntimeIrPeepholeReplacementPlanValidation {
        ruleId = normalize(ruleId, "rule:unknown");
        methodName = normalize(methodName, "unknown");
        rootNodeId = Math.max(0, rootNodeId);
        replacementKind = normalize(replacementKind, "unknown");
        firstBlocker = normalize(firstBlocker, valid ? "none" : "replacement-plan-validation-failed");
        missingCoveredNodeIds = missingCoveredNodeIds == null ? List.of() : List.copyOf(missingCoveredNodeIds);
        missingInputNodeIds = missingInputNodeIds == null ? List.of() : List.copyOf(missingInputNodeIds);
    }

    public static GpuRuntimeIrPeepholeReplacementPlanValidation validate(
            GpuRuntimeIrPeepholeReplacementPlan plan,
            GpuRuntimeIrTypedNodeGraph graph
    ) {
        if (plan == null) {
            return new GpuRuntimeIrPeepholeReplacementPlanValidation(
                    "rule:unknown",
                    "unknown",
                    0,
                    "unknown",
                    false,
                    "replacement-plan-missing",
                    false,
                    false,
                    List.of(),
                    List.of()
            );
        }
        boolean rootExists = graph != null && graph.containsNode(plan.rootNodeId());
        boolean coveredIncludesRoot = plan.coveredNodeIds().contains(plan.rootNodeId());
        List<Integer> missingCovered = graph == null ? plan.coveredNodeIds() : graph.missingNodeIds(plan.coveredNodeIds());
        List<Integer> missingInput = graph == null ? plan.inputNodeIds() : graph.missingNodeIds(plan.inputNodeIds());
        String blocker = firstBlocker(rootExists, coveredIncludesRoot, missingCovered, missingInput);
        return new GpuRuntimeIrPeepholeReplacementPlanValidation(
                plan.ruleId(),
                plan.methodName(),
                plan.rootNodeId(),
                plan.replacementKind(),
                "none".equals(blocker),
                blocker,
                rootExists,
                coveredIncludesRoot,
                missingCovered,
                missingInput
        );
    }

    public Map<String, String> fields(String prefix) {
        String normalizedPrefix = normalize(prefix, "replacementPlan.validation");
        return Map.of(
                normalizedPrefix + ".ruleId", ruleId,
                normalizedPrefix + ".methodName", methodName,
                normalizedPrefix + ".rootNodeId", Integer.toString(rootNodeId),
                normalizedPrefix + ".replacementKind", replacementKind,
                normalizedPrefix + ".valid", Boolean.toString(valid),
                normalizedPrefix + ".firstBlocker", firstBlocker,
                normalizedPrefix + ".rootExists", Boolean.toString(rootExists),
                normalizedPrefix + ".coveredIncludesRoot", Boolean.toString(coveredIncludesRoot),
                normalizedPrefix + ".missingCoveredNodeIds", join(missingCoveredNodeIds),
                normalizedPrefix + ".missingInputNodeIds", join(missingInputNodeIds)
        );
    }

    private static String firstBlocker(
            boolean rootExists,
            boolean coveredIncludesRoot,
            List<Integer> missingCovered,
            List<Integer> missingInput
    ) {
        if (!rootExists) {
            return "replacement-plan-root-missing";
        }
        if (!coveredIncludesRoot) {
            return "replacement-plan-root-not-covered";
        }
        if (missingCovered != null && !missingCovered.isEmpty()) {
            return "replacement-plan-covered-node-missing";
        }
        if (missingInput != null && !missingInput.isEmpty()) {
            return "replacement-plan-input-node-missing";
        }
        return "none";
    }

    private static String join(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return "none";
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
