package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only graph patch preview for a future typed peephole rewrite.
 *
 * <p>The patch records the replace/remove/retain/insert sets that a graph rewrite would need. It never applies the
 * patch, rewrites the graph, builds transformed IR, or selects optimized IR.</p>
 */
public record GpuRuntimeIrPeepholeGraphPatchPreflight(
        String ruleId,
        String methodName,
        int rootNodeId,
        String replacementKind,
        String replacementNodeId,
        List<Integer> replacedNodeIds,
        List<Integer> removedNodeIds,
        List<Integer> retainedInputNodeIds,
        List<Integer> insertedNodeIds,
        boolean transactionReady,
        boolean replacementNodeReady,
        boolean graphPatchReady,
        String status,
        String firstBlocker,
        boolean graphPatchPreflightImplemented,
        boolean graphPatchApplied,
        boolean graphRewriteImplemented,
        boolean transformedIrBuilt,
        boolean mutationAllowed,
        boolean selectedIrReplacement
) {

    public GpuRuntimeIrPeepholeGraphPatchPreflight {
        ruleId = normalize(ruleId, "rule:unknown");
        methodName = normalize(methodName, "unknown");
        rootNodeId = Math.max(0, rootNodeId);
        replacementKind = normalize(replacementKind, "unknown");
        replacementNodeId = normalize(replacementNodeId, "none");
        replacedNodeIds = normalizeNodeIds(replacedNodeIds);
        removedNodeIds = normalizeNodeIds(removedNodeIds);
        retainedInputNodeIds = normalizeNodeIds(retainedInputNodeIds);
        insertedNodeIds = normalizeNodeIds(insertedNodeIds);
        status = normalize(status, graphPatchReady ? "graph-patch-ready" : "blocked");
        firstBlocker = normalize(firstBlocker, graphPatchReady ? "none" : "graph-patch-blocked");
    }

    public static GpuRuntimeIrPeepholeGraphPatchPreflight from(
            GpuRuntimeIrPeepholeRewriteTransactionPreflight transaction,
            GpuRuntimeIrPeepholeReplacementNodePreflight replacementNode
    ) {
        if (transaction == null) {
            return blocked(
                    "rule:unknown",
                    "unknown",
                    0,
                    "unknown",
                    "none",
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    false,
                    "rewrite-transaction-missing"
            );
        }
        if (replacementNode == null) {
            return blockedFrom(transaction, "none", List.of(), false, "replacement-node-missing");
        }
        if (!transaction.transactionReady()) {
            return blockedFrom(transaction, replacementNode.candidateReplacementNodeId(), List.of(), false, transaction.firstBlocker());
        }
        if (!replacementNode.replacementNodeReady()) {
            return blockedFrom(transaction, replacementNode.candidateReplacementNodeId(), List.of(), false, replacementNode.firstBlocker());
        }
        int replacementNodeId = parseNodeId(replacementNode.candidateReplacementNodeId());
        if (replacementNodeId < 0) {
            return blockedFrom(transaction, replacementNode.candidateReplacementNodeId(), List.of(), true, "candidate-replacement-node-id-missing");
        }
        return new GpuRuntimeIrPeepholeGraphPatchPreflight(
                transaction.ruleId(),
                transaction.methodName(),
                transaction.rootNodeId(),
                transaction.replacementKind(),
                Integer.toString(replacementNodeId),
                transaction.replacedNodeIds(),
                transaction.removedNodeIds(),
                transaction.retainedInputNodeIds(),
                List.of(replacementNodeId),
                true,
                true,
                true,
                "graph-patch-ready",
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
        String normalizedPrefix = normalize(prefix, "graphPatch");
        return Map.ofEntries(
                Map.entry(normalizedPrefix + ".ruleId", ruleId),
                Map.entry(normalizedPrefix + ".methodName", methodName),
                Map.entry(normalizedPrefix + ".rootNodeId", Integer.toString(rootNodeId)),
                Map.entry(normalizedPrefix + ".replacementKind", replacementKind),
                Map.entry(normalizedPrefix + ".replacementNodeId", replacementNodeId),
                Map.entry(normalizedPrefix + ".replacedNodeIds", join(replacedNodeIds)),
                Map.entry(normalizedPrefix + ".removedNodeIds", join(removedNodeIds)),
                Map.entry(normalizedPrefix + ".retainedInputNodeIds", join(retainedInputNodeIds)),
                Map.entry(normalizedPrefix + ".insertedNodeIds", join(insertedNodeIds)),
                Map.entry(normalizedPrefix + ".transactionReady", Boolean.toString(transactionReady)),
                Map.entry(normalizedPrefix + ".replacementNodeReady", Boolean.toString(replacementNodeReady)),
                Map.entry(normalizedPrefix + ".graphPatchReady", Boolean.toString(graphPatchReady)),
                Map.entry(normalizedPrefix + ".status", status),
                Map.entry(normalizedPrefix + ".firstBlocker", firstBlocker),
                Map.entry(normalizedPrefix + ".graphPatchPreflightImplemented", Boolean.toString(graphPatchPreflightImplemented)),
                Map.entry(normalizedPrefix + ".graphPatchApplied", Boolean.toString(graphPatchApplied)),
                Map.entry(normalizedPrefix + ".graphRewriteImplemented", Boolean.toString(graphRewriteImplemented)),
                Map.entry(normalizedPrefix + ".transformedIrBuilt", Boolean.toString(transformedIrBuilt)),
                Map.entry(normalizedPrefix + ".mutationAllowed", Boolean.toString(mutationAllowed)),
                Map.entry(normalizedPrefix + ".selectedIrReplacement", Boolean.toString(selectedIrReplacement))
        );
    }

    private static GpuRuntimeIrPeepholeGraphPatchPreflight blockedFrom(
            GpuRuntimeIrPeepholeRewriteTransactionPreflight transaction,
            String replacementNodeId,
            List<Integer> insertedNodeIds,
            boolean replacementNodeReady,
            String firstBlocker
    ) {
        return blocked(
                transaction.ruleId(),
                transaction.methodName(),
                transaction.rootNodeId(),
                transaction.replacementKind(),
                replacementNodeId,
                transaction.replacedNodeIds(),
                transaction.removedNodeIds(),
                transaction.retainedInputNodeIds(),
                transaction.transactionReady(),
                replacementNodeReady,
                firstBlocker
        ).withInsertedNodeIds(insertedNodeIds);
    }

    private GpuRuntimeIrPeepholeGraphPatchPreflight withInsertedNodeIds(List<Integer> insertedNodeIds) {
        return new GpuRuntimeIrPeepholeGraphPatchPreflight(
                ruleId,
                methodName,
                rootNodeId,
                replacementKind,
                replacementNodeId,
                replacedNodeIds,
                removedNodeIds,
                retainedInputNodeIds,
                insertedNodeIds,
                transactionReady,
                replacementNodeReady,
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

    private static GpuRuntimeIrPeepholeGraphPatchPreflight blocked(
            String ruleId,
            String methodName,
            int rootNodeId,
            String replacementKind,
            String replacementNodeId,
            List<Integer> replacedNodeIds,
            List<Integer> removedNodeIds,
            List<Integer> retainedInputNodeIds,
            boolean transactionReady,
            boolean replacementNodeReady,
            String firstBlocker
    ) {
        return new GpuRuntimeIrPeepholeGraphPatchPreflight(
                ruleId,
                methodName,
                rootNodeId,
                replacementKind,
                replacementNodeId,
                replacedNodeIds,
                removedNodeIds,
                retainedInputNodeIds,
                List.of(),
                transactionReady,
                replacementNodeReady,
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

    private static int parseNodeId(String value) {
        try {
            return Math.max(-1, Integer.parseInt(value == null ? "-1" : value));
        } catch (NumberFormatException ignored) {
            return -1;
        }
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
