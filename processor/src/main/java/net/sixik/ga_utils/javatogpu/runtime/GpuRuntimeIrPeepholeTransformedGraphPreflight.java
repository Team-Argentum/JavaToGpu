package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only transformed-graph materialization preview for a future peephole rewrite.
 *
 * <p>The preview records the deterministic materialization inputs and original IR identity. It never materializes a
 * transformed graph, builds transformed IR, applies graph patches, or selects optimized IR.</p>
 */
public record GpuRuntimeIrPeepholeTransformedGraphPreflight(
        String ruleId,
        String methodName,
        int rootNodeId,
        String replacementKind,
        String originalIrIdentity,
        String transformedGraphIdentity,
        String materializationKey,
        String replacementNodeId,
        List<Integer> replacedNodeIds,
        List<Integer> removedNodeIds,
        List<Integer> retainedInputNodeIds,
        List<Integer> insertedNodeIds,
        boolean graphPatchReady,
        boolean materializationReady,
        String status,
        String firstBlocker,
        boolean materializationPreflightImplemented,
        boolean transformedGraphBuilt,
        boolean transformedIrBuilt,
        boolean graphPatchApplied,
        boolean graphRewriteImplemented,
        boolean mutationAllowed,
        boolean selectedIrReplacement
) {

    public GpuRuntimeIrPeepholeTransformedGraphPreflight {
        ruleId = normalize(ruleId, "rule:unknown");
        methodName = normalize(methodName, "unknown");
        rootNodeId = Math.max(0, rootNodeId);
        replacementKind = normalize(replacementKind, "unknown");
        originalIrIdentity = normalize(originalIrIdentity, "irgpu:unknown");
        transformedGraphIdentity = normalize(transformedGraphIdentity, "not-built");
        replacementNodeId = normalize(replacementNodeId, "none");
        replacedNodeIds = normalizeNodeIds(replacedNodeIds);
        removedNodeIds = normalizeNodeIds(removedNodeIds);
        retainedInputNodeIds = normalizeNodeIds(retainedInputNodeIds);
        insertedNodeIds = normalizeNodeIds(insertedNodeIds);
        materializationKey = normalize(materializationKey, materializationKey(
                ruleId,
                methodName,
                rootNodeId,
                replacementKind,
                replacementNodeId,
                replacedNodeIds,
                removedNodeIds,
                retainedInputNodeIds,
                insertedNodeIds
        ));
        status = normalize(status, materializationReady ? "materialization-ready" : "blocked");
        firstBlocker = normalize(firstBlocker, materializationReady ? "none" : "transformed-graph-materialization-blocked");
    }

    public static GpuRuntimeIrPeepholeTransformedGraphPreflight from(
            GpuRuntimeIrPeepholeGraphPatchPreflight graphPatch,
            String originalIrIdentity
    ) {
        if (graphPatch == null) {
            return blocked(
                    "rule:unknown",
                    "unknown",
                    0,
                    "unknown",
                    originalIrIdentity,
                    "none",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    "graph-patch-missing"
            );
        }
        if (!graphPatch.graphPatchReady()) {
            return blocked(
                    graphPatch.ruleId(),
                    graphPatch.methodName(),
                    graphPatch.rootNodeId(),
                    graphPatch.replacementKind(),
                    originalIrIdentity,
                    graphPatch.replacementNodeId(),
                    graphPatch.replacedNodeIds(),
                    graphPatch.removedNodeIds(),
                    graphPatch.retainedInputNodeIds(),
                    graphPatch.insertedNodeIds(),
                    false,
                    graphPatch.firstBlocker()
            );
        }
        return new GpuRuntimeIrPeepholeTransformedGraphPreflight(
                graphPatch.ruleId(),
                graphPatch.methodName(),
                graphPatch.rootNodeId(),
                graphPatch.replacementKind(),
                originalIrIdentity,
                "not-built",
                materializationKey(
                        graphPatch.ruleId(),
                        graphPatch.methodName(),
                        graphPatch.rootNodeId(),
                        graphPatch.replacementKind(),
                        graphPatch.replacementNodeId(),
                        graphPatch.replacedNodeIds(),
                        graphPatch.removedNodeIds(),
                        graphPatch.retainedInputNodeIds(),
                        graphPatch.insertedNodeIds()
                ),
                graphPatch.replacementNodeId(),
                graphPatch.replacedNodeIds(),
                graphPatch.removedNodeIds(),
                graphPatch.retainedInputNodeIds(),
                graphPatch.insertedNodeIds(),
                true,
                true,
                "materialization-ready",
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
        String normalizedPrefix = normalize(prefix, "transformedGraph");
        return Map.ofEntries(
                Map.entry(normalizedPrefix + ".ruleId", ruleId),
                Map.entry(normalizedPrefix + ".methodName", methodName),
                Map.entry(normalizedPrefix + ".rootNodeId", Integer.toString(rootNodeId)),
                Map.entry(normalizedPrefix + ".replacementKind", replacementKind),
                Map.entry(normalizedPrefix + ".originalIrIdentity", originalIrIdentity),
                Map.entry(normalizedPrefix + ".transformedGraphIdentity", transformedGraphIdentity),
                Map.entry(normalizedPrefix + ".materializationKey", materializationKey),
                Map.entry(normalizedPrefix + ".replacementNodeId", replacementNodeId),
                Map.entry(normalizedPrefix + ".replacedNodeIds", join(replacedNodeIds)),
                Map.entry(normalizedPrefix + ".removedNodeIds", join(removedNodeIds)),
                Map.entry(normalizedPrefix + ".retainedInputNodeIds", join(retainedInputNodeIds)),
                Map.entry(normalizedPrefix + ".insertedNodeIds", join(insertedNodeIds)),
                Map.entry(normalizedPrefix + ".graphPatchReady", Boolean.toString(graphPatchReady)),
                Map.entry(normalizedPrefix + ".materializationReady", Boolean.toString(materializationReady)),
                Map.entry(normalizedPrefix + ".status", status),
                Map.entry(normalizedPrefix + ".firstBlocker", firstBlocker),
                Map.entry(normalizedPrefix + ".materializationPreflightImplemented", Boolean.toString(materializationPreflightImplemented)),
                Map.entry(normalizedPrefix + ".transformedGraphBuilt", Boolean.toString(transformedGraphBuilt)),
                Map.entry(normalizedPrefix + ".transformedIrBuilt", Boolean.toString(transformedIrBuilt)),
                Map.entry(normalizedPrefix + ".graphPatchApplied", Boolean.toString(graphPatchApplied)),
                Map.entry(normalizedPrefix + ".graphRewriteImplemented", Boolean.toString(graphRewriteImplemented)),
                Map.entry(normalizedPrefix + ".mutationAllowed", Boolean.toString(mutationAllowed)),
                Map.entry(normalizedPrefix + ".selectedIrReplacement", Boolean.toString(selectedIrReplacement))
        );
    }

    private static GpuRuntimeIrPeepholeTransformedGraphPreflight blocked(
            String ruleId,
            String methodName,
            int rootNodeId,
            String replacementKind,
            String originalIrIdentity,
            String replacementNodeId,
            List<Integer> replacedNodeIds,
            List<Integer> removedNodeIds,
            List<Integer> retainedInputNodeIds,
            List<Integer> insertedNodeIds,
            boolean graphPatchReady,
            String firstBlocker
    ) {
        return new GpuRuntimeIrPeepholeTransformedGraphPreflight(
                ruleId,
                methodName,
                rootNodeId,
                replacementKind,
                originalIrIdentity,
                "not-built",
                materializationKey(
                        ruleId,
                        methodName,
                        rootNodeId,
                        replacementKind,
                        replacementNodeId,
                        replacedNodeIds,
                        removedNodeIds,
                        retainedInputNodeIds,
                        insertedNodeIds
                ),
                replacementNodeId,
                replacedNodeIds,
                removedNodeIds,
                retainedInputNodeIds,
                insertedNodeIds,
                graphPatchReady,
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

    private static String materializationKey(
            String ruleId,
            String methodName,
            int rootNodeId,
            String replacementKind,
            String replacementNodeId,
            List<Integer> replacedNodeIds,
            List<Integer> removedNodeIds,
            List<Integer> retainedInputNodeIds,
            List<Integer> insertedNodeIds
    ) {
        return String.join(
                "|",
                normalize(ruleId, "rule:unknown"),
                normalize(methodName, "unknown"),
                Integer.toString(Math.max(0, rootNodeId)),
                normalize(replacementKind, "unknown"),
                "replacement=" + normalize(replacementNodeId, "none"),
                "replace=" + join(normalizeNodeIds(replacedNodeIds)),
                "remove=" + join(normalizeNodeIds(removedNodeIds)),
                "retain=" + join(normalizeNodeIds(retainedInputNodeIds)),
                "insert=" + join(normalizeNodeIds(insertedNodeIds))
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
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
