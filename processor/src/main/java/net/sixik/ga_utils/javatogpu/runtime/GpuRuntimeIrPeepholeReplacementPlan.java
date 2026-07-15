package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;

/**
 * Read-only proof plan for a future structural typed-IR peephole replacement.
 *
 * <p>The plan records the nodes a rule would replace and the input nodes needed to build the replacement, but it does
 * not mutate or authorize mutation by itself.</p>
 */
public record GpuRuntimeIrPeepholeReplacementPlan(
        String ruleId,
        String methodName,
        int rootNodeId,
        String replacementKind,
        List<Integer> coveredNodeIds,
        List<Integer> inputNodeIds,
        boolean complete,
        String firstBlocker
) {

    public GpuRuntimeIrPeepholeReplacementPlan {
        ruleId = normalize(ruleId, "rule:unknown");
        methodName = normalize(methodName, "unknown");
        rootNodeId = Math.max(0, rootNodeId);
        replacementKind = normalize(replacementKind, "unknown");
        coveredNodeIds = normalizeNodeIds(coveredNodeIds);
        inputNodeIds = normalizeNodeIds(inputNodeIds);
        firstBlocker = normalize(firstBlocker, complete ? "none" : "replacement-plan-incomplete");
    }

    public static GpuRuntimeIrPeepholeReplacementPlan complete(
            String ruleId,
            String methodName,
            int rootNodeId,
            String replacementKind,
            List<Integer> coveredNodeIds,
            List<Integer> inputNodeIds
    ) {
        return new GpuRuntimeIrPeepholeReplacementPlan(
                ruleId,
                methodName,
                rootNodeId,
                replacementKind,
                coveredNodeIds,
                inputNodeIds,
                true,
                "none"
        );
    }

    public static GpuRuntimeIrPeepholeReplacementPlan blocked(
            String ruleId,
            String methodName,
            int rootNodeId,
            String replacementKind,
            List<Integer> coveredNodeIds,
            List<Integer> inputNodeIds,
            String firstBlocker
    ) {
        return new GpuRuntimeIrPeepholeReplacementPlan(
                ruleId,
                methodName,
                rootNodeId,
                replacementKind,
                coveredNodeIds,
                inputNodeIds,
                false,
                firstBlocker
        );
    }

    public Map<String, String> fields(String prefix) {
        String normalizedPrefix = normalize(prefix, "replacementPlan");
        return Map.of(
                normalizedPrefix + ".ruleId", ruleId,
                normalizedPrefix + ".methodName", methodName,
                normalizedPrefix + ".rootNodeId", Integer.toString(rootNodeId),
                normalizedPrefix + ".replacementKind", replacementKind,
                normalizedPrefix + ".coveredNodeIds", join(coveredNodeIds),
                normalizedPrefix + ".inputNodeIds", join(inputNodeIds),
                normalizedPrefix + ".complete", Boolean.toString(complete),
                normalizedPrefix + ".firstBlocker", firstBlocker
        );
    }

    private static List<Integer> normalizeNodeIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && id >= 0)
                .distinct()
                .toList();
    }

    private static String join(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return "none";
        }
        return ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
