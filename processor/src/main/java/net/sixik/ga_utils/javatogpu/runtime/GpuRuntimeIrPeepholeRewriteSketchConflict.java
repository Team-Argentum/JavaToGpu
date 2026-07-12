package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only overlap evidence between two future peephole rewrite sketches.
 */
public record GpuRuntimeIrPeepholeRewriteSketchConflict(
        String methodName,
        String firstRuleId,
        int firstRootNodeId,
        String firstReplacementKind,
        String secondRuleId,
        int secondRootNodeId,
        String secondReplacementKind,
        List<Integer> overlappingNodeIds,
        String firstBlocker
) {

    public GpuRuntimeIrPeepholeRewriteSketchConflict {
        methodName = normalize(methodName, "unknown");
        firstRuleId = normalize(firstRuleId, "rule:unknown");
        firstRootNodeId = Math.max(0, firstRootNodeId);
        firstReplacementKind = normalize(firstReplacementKind, "unknown");
        secondRuleId = normalize(secondRuleId, "rule:unknown");
        secondRootNodeId = Math.max(0, secondRootNodeId);
        secondReplacementKind = normalize(secondReplacementKind, "unknown");
        overlappingNodeIds = overlappingNodeIds == null ? List.of() : List.copyOf(overlappingNodeIds);
        firstBlocker = normalize(firstBlocker, "rewrite-sketch-covered-node-overlap");
    }

    public static GpuRuntimeIrPeepholeRewriteSketchConflict between(
            GpuRuntimeIrPeepholeRewriteSketch first,
            GpuRuntimeIrPeepholeRewriteSketch second,
            List<Integer> overlappingNodeIds
    ) {
        return new GpuRuntimeIrPeepholeRewriteSketchConflict(
                first.methodName(),
                first.ruleId(),
                first.rootNodeId(),
                first.replacementKind(),
                second.ruleId(),
                second.rootNodeId(),
                second.replacementKind(),
                overlappingNodeIds,
                "rewrite-sketch-covered-node-overlap"
        );
    }

    public Map<String, String> fields(String prefix) {
        String normalizedPrefix = normalize(prefix, "rewriteSketch.conflict");
        return Map.of(
                normalizedPrefix + ".methodName", methodName,
                normalizedPrefix + ".firstRuleId", firstRuleId,
                normalizedPrefix + ".firstRootNodeId", Integer.toString(firstRootNodeId),
                normalizedPrefix + ".firstReplacementKind", firstReplacementKind,
                normalizedPrefix + ".secondRuleId", secondRuleId,
                normalizedPrefix + ".secondRootNodeId", Integer.toString(secondRootNodeId),
                normalizedPrefix + ".secondReplacementKind", secondReplacementKind,
                normalizedPrefix + ".overlappingNodeIds", join(overlappingNodeIds),
                normalizedPrefix + ".firstBlocker", firstBlocker
        );
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
