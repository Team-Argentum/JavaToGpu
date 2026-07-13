package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only transaction shape for a future typed peephole graph rewrite.
 *
 * <p>The transaction records which existing nodes would be replaced/removed/retained and whether a synthetic
 * replacement node would be needed. It does not allocate ids, rewrite the graph, or build transformed IR.</p>
 */
public record GpuRuntimeIrPeepholeRewriteTransactionPreflight(
        String ruleId,
        String methodName,
        int rootNodeId,
        String replacementKind,
        List<Integer> replacedNodeIds,
        List<Integer> removedNodeIds,
        List<Integer> retainedInputNodeIds,
        int plannedAddedNodeCount,
        String plannedAddedNodeIds,
        boolean visitorReady,
        boolean blueprintReady,
        boolean transactionReady,
        String status,
        String firstBlocker,
        boolean transactionPreflightImplemented,
        boolean nodeIdAllocatorImplemented,
        boolean graphRewriteImplemented,
        boolean transformedIrBuilt,
        boolean mutationAllowed,
        boolean selectedIrReplacement
) {

    public GpuRuntimeIrPeepholeRewriteTransactionPreflight {
        ruleId = normalize(ruleId, "rule:unknown");
        methodName = normalize(methodName, "unknown");
        rootNodeId = Math.max(0, rootNodeId);
        replacementKind = normalize(replacementKind, "unknown");
        replacedNodeIds = normalizeNodeIds(replacedNodeIds);
        removedNodeIds = normalizeNodeIds(removedNodeIds);
        retainedInputNodeIds = normalizeNodeIds(retainedInputNodeIds);
        plannedAddedNodeCount = Math.max(0, plannedAddedNodeCount);
        plannedAddedNodeIds = normalize(plannedAddedNodeIds, plannedAddedNodeCount == 0 ? "none" : "not-allocated");
        status = normalize(status, transactionReady ? "transaction-ready" : "blocked");
        firstBlocker = normalize(firstBlocker, transactionReady ? "none" : "rewrite-transaction-blocked");
    }

    public static GpuRuntimeIrPeepholeRewriteTransactionPreflight from(
            GpuRuntimeIrPeepholeReplacementBlueprint blueprint,
            GpuRuntimeIrPeepholeRewriteVisitPreflight visitPreflight
    ) {
        if (blueprint == null) {
            return blocked(
                    "rule:unknown",
                    "unknown",
                    0,
                    "unknown",
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    false,
                    "replacement-blueprint-missing"
            );
        }
        List<Integer> coveredNodeIds = visitPreflight == null ? List.of() : visitPreflight.coveredNodeIds();
        List<Integer> replacedNodeIds = rootOnly(blueprint.rootNodeId());
        if (!blueprint.blueprintReady()) {
            return blocked(
                    blueprint.ruleId(),
                    blueprint.methodName(),
                    blueprint.rootNodeId(),
                    blueprint.replacementKind(),
                    replacedNodeIds,
                    coveredNodeIds,
                    blueprint.argumentNodeIds(),
                    visitPreflight != null && visitPreflight.visitorReady(),
                    false,
                    blueprint.firstBlocker()
            );
        }
        return new GpuRuntimeIrPeepholeRewriteTransactionPreflight(
                blueprint.ruleId(),
                blueprint.methodName(),
                blueprint.rootNodeId(),
                blueprint.replacementKind(),
                replacedNodeIds,
                coveredNodeIds,
                blueprint.argumentNodeIds(),
                1,
                "not-allocated",
                true,
                true,
                true,
                "transaction-ready",
                "none",
                true,
                false,
                false,
                false,
                false,
                false
        );
    }

    public Map<String, String> fields(String prefix) {
        String normalizedPrefix = normalize(prefix, "rewriteTransaction");
        return Map.ofEntries(
                Map.entry(normalizedPrefix + ".ruleId", ruleId),
                Map.entry(normalizedPrefix + ".methodName", methodName),
                Map.entry(normalizedPrefix + ".rootNodeId", Integer.toString(rootNodeId)),
                Map.entry(normalizedPrefix + ".replacementKind", replacementKind),
                Map.entry(normalizedPrefix + ".replacedNodeIds", join(replacedNodeIds)),
                Map.entry(normalizedPrefix + ".removedNodeIds", join(removedNodeIds)),
                Map.entry(normalizedPrefix + ".retainedInputNodeIds", join(retainedInputNodeIds)),
                Map.entry(normalizedPrefix + ".plannedAddedNode.count", Integer.toString(plannedAddedNodeCount)),
                Map.entry(normalizedPrefix + ".plannedAddedNodeIds", plannedAddedNodeIds),
                Map.entry(normalizedPrefix + ".visitorReady", Boolean.toString(visitorReady)),
                Map.entry(normalizedPrefix + ".blueprintReady", Boolean.toString(blueprintReady)),
                Map.entry(normalizedPrefix + ".transactionReady", Boolean.toString(transactionReady)),
                Map.entry(normalizedPrefix + ".status", status),
                Map.entry(normalizedPrefix + ".firstBlocker", firstBlocker),
                Map.entry(normalizedPrefix + ".transactionPreflightImplemented", Boolean.toString(transactionPreflightImplemented)),
                Map.entry(normalizedPrefix + ".nodeIdAllocatorImplemented", Boolean.toString(nodeIdAllocatorImplemented)),
                Map.entry(normalizedPrefix + ".graphRewriteImplemented", Boolean.toString(graphRewriteImplemented)),
                Map.entry(normalizedPrefix + ".transformedIrBuilt", Boolean.toString(transformedIrBuilt)),
                Map.entry(normalizedPrefix + ".mutationAllowed", Boolean.toString(mutationAllowed)),
                Map.entry(normalizedPrefix + ".selectedIrReplacement", Boolean.toString(selectedIrReplacement))
        );
    }

    private static GpuRuntimeIrPeepholeRewriteTransactionPreflight blocked(
            String ruleId,
            String methodName,
            int rootNodeId,
            String replacementKind,
            List<Integer> replacedNodeIds,
            List<Integer> removedNodeIds,
            List<Integer> retainedInputNodeIds,
            boolean visitorReady,
            boolean blueprintReady,
            String firstBlocker
    ) {
        return new GpuRuntimeIrPeepholeRewriteTransactionPreflight(
                ruleId,
                methodName,
                rootNodeId,
                replacementKind,
                replacedNodeIds,
                removedNodeIds,
                retainedInputNodeIds,
                0,
                "none",
                visitorReady,
                blueprintReady,
                false,
                "blocked",
                firstBlocker,
                true,
                false,
                false,
                false,
                false,
                false
        );
    }

    private static List<Integer> rootOnly(int rootNodeId) {
        return rootNodeId < 0 ? List.of() : List.of(rootNodeId);
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
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
