package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only replacement-node construction preview for a future peephole rewrite.
 *
 * <p>The preview pairs a deterministic candidate node id with the target intrinsic-call blueprint. It never builds a
 * replacement node, mutates the graph, or selects transformed IR.</p>
 */
public record GpuRuntimeIrPeepholeReplacementNodePreflight(
        String ruleId,
        String methodName,
        int rootNodeId,
        String replacementKind,
        String targetNodeKind,
        String targetOperation,
        String candidateReplacementNodeId,
        List<Integer> argumentNodeIds,
        List<String> argumentRoles,
        boolean allocationReady,
        boolean blueprintReady,
        boolean replacementNodeReady,
        String status,
        String firstBlocker,
        boolean replacementNodePreflightImplemented,
        boolean replacementNodeBuilt,
        boolean replacementBuilderImplemented,
        boolean graphRewriteImplemented,
        boolean transformedIrBuilt,
        boolean mutationAllowed,
        boolean selectedIrReplacement
) {

    public GpuRuntimeIrPeepholeReplacementNodePreflight {
        ruleId = normalize(ruleId, "rule:unknown");
        methodName = normalize(methodName, "unknown");
        rootNodeId = Math.max(0, rootNodeId);
        replacementKind = normalize(replacementKind, "unknown");
        targetNodeKind = normalize(targetNodeKind, "GpuIrIntrinsicCall");
        targetOperation = normalize(targetOperation, replacementKind);
        candidateReplacementNodeId = normalize(candidateReplacementNodeId, "none");
        argumentNodeIds = normalizeNodeIds(argumentNodeIds);
        argumentRoles = argumentRoles == null || argumentRoles.isEmpty()
                ? defaultRoles(argumentNodeIds.size())
                : List.copyOf(argumentRoles);
        status = normalize(status, replacementNodeReady ? "replacement-node-ready" : "blocked");
        firstBlocker = normalize(firstBlocker, replacementNodeReady ? "none" : "replacement-node-blocked");
    }

    public static GpuRuntimeIrPeepholeReplacementNodePreflight from(
            GpuRuntimeIrPeepholeNodeIdAllocationPreflight allocation,
            GpuRuntimeIrPeepholeReplacementBlueprint blueprint
    ) {
        if (allocation == null) {
            return blocked(
                    "rule:unknown",
                    "unknown",
                    0,
                    "unknown",
                    "GpuIrIntrinsicCall",
                    "unknown",
                    List.of(),
                    List.of(),
                    false,
                    false,
                    "node-id-allocation-missing"
            );
        }
        if (blueprint == null) {
            return blocked(
                    allocation.ruleId(),
                    allocation.methodName(),
                    allocation.rootNodeId(),
                    allocation.replacementKind(),
                    "GpuIrIntrinsicCall",
                    allocation.replacementKind(),
                    List.of(),
                    List.of(),
                    allocation.allocationReady(),
                    false,
                    "replacement-blueprint-missing"
            );
        }
        if (!allocation.allocationReady()) {
            return blockedFrom(allocation, blueprint, allocation.firstBlocker());
        }
        if (!blueprint.blueprintReady()) {
            return blockedFrom(allocation, blueprint, blueprint.firstBlocker());
        }
        if (allocation.candidateNodeIds().isEmpty()) {
            return blockedFrom(allocation, blueprint, "candidate-replacement-node-id-missing");
        }
        if (blueprint.argumentNodeIds().isEmpty()) {
            return blockedFrom(allocation, blueprint, "replacement-node-arguments-missing");
        }
        return new GpuRuntimeIrPeepholeReplacementNodePreflight(
                allocation.ruleId(),
                allocation.methodName(),
                allocation.rootNodeId(),
                allocation.replacementKind(),
                blueprint.targetNodeKind(),
                blueprint.targetOperation(),
                Integer.toString(allocation.candidateNodeIds().get(0)),
                blueprint.argumentNodeIds(),
                blueprint.argumentRoles(),
                true,
                true,
                true,
                "replacement-node-ready",
                "none",
                true,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }

    public Map<String, String> fields(String prefix) {
        String normalizedPrefix = normalize(prefix, "replacementNode");
        return Map.ofEntries(
                Map.entry(normalizedPrefix + ".ruleId", ruleId),
                Map.entry(normalizedPrefix + ".methodName", methodName),
                Map.entry(normalizedPrefix + ".rootNodeId", Integer.toString(rootNodeId)),
                Map.entry(normalizedPrefix + ".replacementKind", replacementKind),
                Map.entry(normalizedPrefix + ".targetNodeKind", targetNodeKind),
                Map.entry(normalizedPrefix + ".targetOperation", targetOperation),
                Map.entry(normalizedPrefix + ".candidateReplacementNodeId", candidateReplacementNodeId),
                Map.entry(normalizedPrefix + ".argumentNodeIds", join(argumentNodeIds)),
                Map.entry(normalizedPrefix + ".argumentRoles", String.join(",", argumentRoles)),
                Map.entry(normalizedPrefix + ".allocationReady", Boolean.toString(allocationReady)),
                Map.entry(normalizedPrefix + ".blueprintReady", Boolean.toString(blueprintReady)),
                Map.entry(normalizedPrefix + ".replacementNodeReady", Boolean.toString(replacementNodeReady)),
                Map.entry(normalizedPrefix + ".status", status),
                Map.entry(normalizedPrefix + ".firstBlocker", firstBlocker),
                Map.entry(normalizedPrefix + ".replacementNodePreflightImplemented", Boolean.toString(replacementNodePreflightImplemented)),
                Map.entry(normalizedPrefix + ".replacementNodeBuilt", Boolean.toString(replacementNodeBuilt)),
                Map.entry(normalizedPrefix + ".replacementBuilderImplemented", Boolean.toString(replacementBuilderImplemented)),
                Map.entry(normalizedPrefix + ".graphRewriteImplemented", Boolean.toString(graphRewriteImplemented)),
                Map.entry(normalizedPrefix + ".transformedIrBuilt", Boolean.toString(transformedIrBuilt)),
                Map.entry(normalizedPrefix + ".mutationAllowed", Boolean.toString(mutationAllowed)),
                Map.entry(normalizedPrefix + ".selectedIrReplacement", Boolean.toString(selectedIrReplacement))
        );
    }

    private static GpuRuntimeIrPeepholeReplacementNodePreflight blockedFrom(
            GpuRuntimeIrPeepholeNodeIdAllocationPreflight allocation,
            GpuRuntimeIrPeepholeReplacementBlueprint blueprint,
            String firstBlocker
    ) {
        return blocked(
                allocation.ruleId(),
                allocation.methodName(),
                allocation.rootNodeId(),
                allocation.replacementKind(),
                blueprint.targetNodeKind(),
                blueprint.targetOperation(),
                blueprint.argumentNodeIds(),
                blueprint.argumentRoles(),
                allocation.allocationReady(),
                blueprint.blueprintReady(),
                firstBlocker
        );
    }

    private static GpuRuntimeIrPeepholeReplacementNodePreflight blocked(
            String ruleId,
            String methodName,
            int rootNodeId,
            String replacementKind,
            String targetNodeKind,
            String targetOperation,
            List<Integer> argumentNodeIds,
            List<String> argumentRoles,
            boolean allocationReady,
            boolean blueprintReady,
            String firstBlocker
    ) {
        return new GpuRuntimeIrPeepholeReplacementNodePreflight(
                ruleId,
                methodName,
                rootNodeId,
                replacementKind,
                targetNodeKind,
                targetOperation,
                "none",
                argumentNodeIds,
                argumentRoles,
                allocationReady,
                blueprintReady,
                false,
                "blocked",
                firstBlocker,
                true,
                false,
                false,
                false,
                false,
                false,
                false
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

    private static List<String> defaultRoles(int count) {
        if (count <= 0) {
            return List.of();
        }
        java.util.ArrayList<String> roles = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            roles.add("arg" + index);
        }
        return List.copyOf(roles);
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
