package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Read-only node-id allocation preview for a future peephole rewrite transaction.
 *
 * <p>The candidate ids are deterministic diagnostics only. They are not reserved, inserted, or selected.</p>
 */
public record GpuRuntimeIrPeepholeNodeIdAllocationPreflight(
        String ruleId,
        String methodName,
        int rootNodeId,
        String replacementKind,
        int graphNodeMaxId,
        int plannedAddedNodeCount,
        List<Integer> candidateNodeIds,
        boolean transactionReady,
        boolean allocationReady,
        String status,
        String firstBlocker,
        boolean allocationPreflightImplemented,
        boolean nodeIdsReserved,
        boolean nodeIdAllocatorApplied,
        boolean graphRewriteImplemented,
        boolean transformedIrBuilt,
        boolean mutationAllowed,
        boolean selectedIrReplacement
) {

    public GpuRuntimeIrPeepholeNodeIdAllocationPreflight {
        ruleId = normalize(ruleId, "rule:unknown");
        methodName = normalize(methodName, "unknown");
        rootNodeId = Math.max(0, rootNodeId);
        replacementKind = normalize(replacementKind, "unknown");
        graphNodeMaxId = Math.max(0, graphNodeMaxId);
        plannedAddedNodeCount = Math.max(0, plannedAddedNodeCount);
        candidateNodeIds = normalizeNodeIds(candidateNodeIds);
        status = normalize(status, allocationReady ? "allocation-ready" : "blocked");
        firstBlocker = normalize(firstBlocker, allocationReady ? "none" : "node-id-allocation-blocked");
    }

    public static GpuRuntimeIrPeepholeNodeIdAllocationPreflight from(
            GpuRuntimeIrPeepholeRewriteTransactionPreflight transaction,
            GpuRuntimeIrPeepholeRewriteVisitPreflight visitPreflight
    ) {
        if (transaction == null) {
            return blocked(
                    "rule:unknown",
                    "unknown",
                    0,
                    "unknown",
                    0,
                    0,
                    false,
                    "rewrite-transaction-missing"
            );
        }
        int graphMax = visitPreflight == null ? 0 : visitPreflight.graphNodeMaxId();
        if (!transaction.transactionReady()) {
            return blocked(
                    transaction.ruleId(),
                    transaction.methodName(),
                    transaction.rootNodeId(),
                    transaction.replacementKind(),
                    graphMax,
                    transaction.plannedAddedNodeCount(),
                    false,
                    transaction.firstBlocker()
            );
        }
        if (transaction.plannedAddedNodeCount() <= 0) {
            return blocked(
                    transaction.ruleId(),
                    transaction.methodName(),
                    transaction.rootNodeId(),
                    transaction.replacementKind(),
                    graphMax,
                    0,
                    true,
                    "no-added-nodes-planned"
            );
        }
        return new GpuRuntimeIrPeepholeNodeIdAllocationPreflight(
                transaction.ruleId(),
                transaction.methodName(),
                transaction.rootNodeId(),
                transaction.replacementKind(),
                graphMax,
                transaction.plannedAddedNodeCount(),
                candidateIds(graphMax, transaction.plannedAddedNodeCount()),
                true,
                true,
                "allocation-ready",
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
        String normalizedPrefix = normalize(prefix, "nodeIdAllocation");
        return Map.ofEntries(
                Map.entry(normalizedPrefix + ".ruleId", ruleId),
                Map.entry(normalizedPrefix + ".methodName", methodName),
                Map.entry(normalizedPrefix + ".rootNodeId", Integer.toString(rootNodeId)),
                Map.entry(normalizedPrefix + ".replacementKind", replacementKind),
                Map.entry(normalizedPrefix + ".graphNodeMaxId", Integer.toString(graphNodeMaxId)),
                Map.entry(normalizedPrefix + ".plannedAddedNode.count", Integer.toString(plannedAddedNodeCount)),
                Map.entry(normalizedPrefix + ".candidateNodeIds", join(candidateNodeIds)),
                Map.entry(normalizedPrefix + ".transactionReady", Boolean.toString(transactionReady)),
                Map.entry(normalizedPrefix + ".allocationReady", Boolean.toString(allocationReady)),
                Map.entry(normalizedPrefix + ".status", status),
                Map.entry(normalizedPrefix + ".firstBlocker", firstBlocker),
                Map.entry(normalizedPrefix + ".allocationPreflightImplemented", Boolean.toString(allocationPreflightImplemented)),
                Map.entry(normalizedPrefix + ".nodeIdsReserved", Boolean.toString(nodeIdsReserved)),
                Map.entry(normalizedPrefix + ".nodeIdAllocatorApplied", Boolean.toString(nodeIdAllocatorApplied)),
                Map.entry(normalizedPrefix + ".graphRewriteImplemented", Boolean.toString(graphRewriteImplemented)),
                Map.entry(normalizedPrefix + ".transformedIrBuilt", Boolean.toString(transformedIrBuilt)),
                Map.entry(normalizedPrefix + ".mutationAllowed", Boolean.toString(mutationAllowed)),
                Map.entry(normalizedPrefix + ".selectedIrReplacement", Boolean.toString(selectedIrReplacement))
        );
    }

    private static GpuRuntimeIrPeepholeNodeIdAllocationPreflight blocked(
            String ruleId,
            String methodName,
            int rootNodeId,
            String replacementKind,
            int graphNodeMaxId,
            int plannedAddedNodeCount,
            boolean transactionReady,
            String firstBlocker
    ) {
        return new GpuRuntimeIrPeepholeNodeIdAllocationPreflight(
                ruleId,
                methodName,
                rootNodeId,
                replacementKind,
                graphNodeMaxId,
                plannedAddedNodeCount,
                List.of(),
                transactionReady,
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

    private static List<Integer> candidateIds(int graphNodeMaxId, int count) {
        if (count <= 0) {
            return List.of();
        }
        return IntStream.rangeClosed(graphNodeMaxId + 1, graphNodeMaxId + count)
                .boxed()
                .toList();
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
