package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only optimized IR artifact envelope preview for a future peephole rewrite.
 *
 * <p>The preview records the metadata, proof, and rollback anchors a future optimized artifact must carry. It never
 * builds an optimized artifact, builds transformed IR, applies graph patches, or selects optimized IR.</p>
 */
public record GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight(
        String ruleId,
        String methodName,
        int rootNodeId,
        String replacementKind,
        String originalIrIdentity,
        String transformedGraphIdentity,
        String optimizedArtifactIdentity,
        String materializationKey,
        String envelopeKey,
        String proofAnchor,
        String rollbackAnchor,
        String replacementNodeId,
        List<Integer> replacedNodeIds,
        List<Integer> removedNodeIds,
        List<Integer> retainedInputNodeIds,
        List<Integer> insertedNodeIds,
        boolean materializationReady,
        boolean metadataReady,
        boolean proofSlotReady,
        boolean rollbackPlanReady,
        boolean artifactEnvelopeReady,
        String status,
        String firstBlocker,
        boolean artifactEnvelopePreflightImplemented,
        boolean artifactEnvelopeBuilt,
        boolean optimizedArtifactBuilt,
        boolean transformedGraphBuilt,
        boolean transformedIrBuilt,
        boolean graphPatchApplied,
        boolean graphRewriteImplemented,
        boolean mutationAllowed,
        boolean selectedIrReplacement
) {

    public GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight {
        ruleId = normalize(ruleId, "rule:unknown");
        methodName = normalize(methodName, "unknown");
        rootNodeId = Math.max(0, rootNodeId);
        replacementKind = normalize(replacementKind, "unknown");
        originalIrIdentity = normalize(originalIrIdentity, "irgpu:unknown");
        transformedGraphIdentity = normalize(transformedGraphIdentity, "not-built");
        optimizedArtifactIdentity = normalize(optimizedArtifactIdentity, "not-built");
        materializationKey = normalize(materializationKey, "not-built");
        proofAnchor = normalize(proofAnchor, "runtime-equivalence-required");
        rollbackAnchor = normalize(rollbackAnchor, "original-ir");
        replacementNodeId = normalize(replacementNodeId, "none");
        replacedNodeIds = normalizeNodeIds(replacedNodeIds);
        removedNodeIds = normalizeNodeIds(removedNodeIds);
        retainedInputNodeIds = normalizeNodeIds(retainedInputNodeIds);
        insertedNodeIds = normalizeNodeIds(insertedNodeIds);
        envelopeKey = normalize(envelopeKey, envelopeKey(
                ruleId,
                methodName,
                rootNodeId,
                replacementKind,
                originalIrIdentity,
                transformedGraphIdentity,
                materializationKey,
                proofAnchor,
                rollbackAnchor
        ));
        status = normalize(status, artifactEnvelopeReady ? "artifact-envelope-ready" : "blocked");
        firstBlocker = normalize(firstBlocker, artifactEnvelopeReady ? "none" : "ir-artifact-envelope-blocked");
    }

    public static GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight from(
            GpuRuntimeIrPeepholeTransformedGraphPreflight transformedGraph
    ) {
        if (transformedGraph == null) {
            return blocked(
                    "rule:unknown",
                    "unknown",
                    0,
                    "unknown",
                    "irgpu:unknown",
                    "not-built",
                    "not-built",
                    "none",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    "transformed-graph-missing"
            );
        }
        if (!transformedGraph.materializationReady()) {
            return blocked(
                    transformedGraph.ruleId(),
                    transformedGraph.methodName(),
                    transformedGraph.rootNodeId(),
                    transformedGraph.replacementKind(),
                    transformedGraph.originalIrIdentity(),
                    transformedGraph.transformedGraphIdentity(),
                    transformedGraph.materializationKey(),
                    transformedGraph.replacementNodeId(),
                    transformedGraph.replacedNodeIds(),
                    transformedGraph.removedNodeIds(),
                    transformedGraph.retainedInputNodeIds(),
                    transformedGraph.insertedNodeIds(),
                    false,
                    transformedGraph.firstBlocker()
            );
        }
        String proofAnchor = "runtime-equivalence-required";
        String rollbackAnchor = "original-ir";
        return new GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight(
                transformedGraph.ruleId(),
                transformedGraph.methodName(),
                transformedGraph.rootNodeId(),
                transformedGraph.replacementKind(),
                transformedGraph.originalIrIdentity(),
                transformedGraph.transformedGraphIdentity(),
                "not-built",
                transformedGraph.materializationKey(),
                envelopeKey(
                        transformedGraph.ruleId(),
                        transformedGraph.methodName(),
                        transformedGraph.rootNodeId(),
                        transformedGraph.replacementKind(),
                        transformedGraph.originalIrIdentity(),
                        transformedGraph.transformedGraphIdentity(),
                        transformedGraph.materializationKey(),
                        proofAnchor,
                        rollbackAnchor
                ),
                proofAnchor,
                rollbackAnchor,
                transformedGraph.replacementNodeId(),
                transformedGraph.replacedNodeIds(),
                transformedGraph.removedNodeIds(),
                transformedGraph.retainedInputNodeIds(),
                transformedGraph.insertedNodeIds(),
                true,
                true,
                true,
                true,
                true,
                "artifact-envelope-ready",
                "none",
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }

    public Map<String, String> fields(String prefix) {
        String normalizedPrefix = normalize(prefix, "irArtifactEnvelope");
        return Map.ofEntries(
                Map.entry(normalizedPrefix + ".ruleId", ruleId),
                Map.entry(normalizedPrefix + ".methodName", methodName),
                Map.entry(normalizedPrefix + ".rootNodeId", Integer.toString(rootNodeId)),
                Map.entry(normalizedPrefix + ".replacementKind", replacementKind),
                Map.entry(normalizedPrefix + ".originalIrIdentity", originalIrIdentity),
                Map.entry(normalizedPrefix + ".transformedGraphIdentity", transformedGraphIdentity),
                Map.entry(normalizedPrefix + ".optimizedArtifactIdentity", optimizedArtifactIdentity),
                Map.entry(normalizedPrefix + ".materializationKey", materializationKey),
                Map.entry(normalizedPrefix + ".envelopeKey", envelopeKey),
                Map.entry(normalizedPrefix + ".proofAnchor", proofAnchor),
                Map.entry(normalizedPrefix + ".rollbackAnchor", rollbackAnchor),
                Map.entry(normalizedPrefix + ".replacementNodeId", replacementNodeId),
                Map.entry(normalizedPrefix + ".replacedNodeIds", join(replacedNodeIds)),
                Map.entry(normalizedPrefix + ".removedNodeIds", join(removedNodeIds)),
                Map.entry(normalizedPrefix + ".retainedInputNodeIds", join(retainedInputNodeIds)),
                Map.entry(normalizedPrefix + ".insertedNodeIds", join(insertedNodeIds)),
                Map.entry(normalizedPrefix + ".materializationReady", Boolean.toString(materializationReady)),
                Map.entry(normalizedPrefix + ".metadataReady", Boolean.toString(metadataReady)),
                Map.entry(normalizedPrefix + ".proofSlotReady", Boolean.toString(proofSlotReady)),
                Map.entry(normalizedPrefix + ".rollbackPlanReady", Boolean.toString(rollbackPlanReady)),
                Map.entry(normalizedPrefix + ".artifactEnvelopeReady", Boolean.toString(artifactEnvelopeReady)),
                Map.entry(normalizedPrefix + ".status", status),
                Map.entry(normalizedPrefix + ".firstBlocker", firstBlocker),
                Map.entry(normalizedPrefix + ".artifactEnvelopePreflightImplemented", Boolean.toString(artifactEnvelopePreflightImplemented)),
                Map.entry(normalizedPrefix + ".artifactEnvelopeBuilt", Boolean.toString(artifactEnvelopeBuilt)),
                Map.entry(normalizedPrefix + ".optimizedArtifactBuilt", Boolean.toString(optimizedArtifactBuilt)),
                Map.entry(normalizedPrefix + ".transformedGraphBuilt", Boolean.toString(transformedGraphBuilt)),
                Map.entry(normalizedPrefix + ".transformedIrBuilt", Boolean.toString(transformedIrBuilt)),
                Map.entry(normalizedPrefix + ".graphPatchApplied", Boolean.toString(graphPatchApplied)),
                Map.entry(normalizedPrefix + ".graphRewriteImplemented", Boolean.toString(graphRewriteImplemented)),
                Map.entry(normalizedPrefix + ".mutationAllowed", Boolean.toString(mutationAllowed)),
                Map.entry(normalizedPrefix + ".selectedIrReplacement", Boolean.toString(selectedIrReplacement))
        );
    }

    private static GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight blocked(
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
            boolean materializationReady,
            String firstBlocker
    ) {
        String proofAnchor = "runtime-equivalence-required";
        String rollbackAnchor = "original-ir";
        return new GpuRuntimeIrPeepholeIrArtifactEnvelopePreflight(
                ruleId,
                methodName,
                rootNodeId,
                replacementKind,
                originalIrIdentity,
                transformedGraphIdentity,
                "not-built",
                materializationKey,
                envelopeKey(
                        ruleId,
                        methodName,
                        rootNodeId,
                        replacementKind,
                        originalIrIdentity,
                        transformedGraphIdentity,
                        materializationKey,
                        proofAnchor,
                        rollbackAnchor
                ),
                proofAnchor,
                rollbackAnchor,
                replacementNodeId,
                replacedNodeIds,
                removedNodeIds,
                retainedInputNodeIds,
                insertedNodeIds,
                materializationReady,
                false,
                false,
                false,
                false,
                "blocked",
                firstBlocker,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }

    private static String envelopeKey(
            String ruleId,
            String methodName,
            int rootNodeId,
            String replacementKind,
            String originalIrIdentity,
            String transformedGraphIdentity,
            String materializationKey,
            String proofAnchor,
            String rollbackAnchor
    ) {
        return String.join(
                "|",
                normalize(ruleId, "rule:unknown"),
                normalize(methodName, "unknown"),
                Integer.toString(Math.max(0, rootNodeId)),
                normalize(replacementKind, "unknown"),
                "original=" + normalize(originalIrIdentity, "irgpu:unknown"),
                "graph=" + normalize(transformedGraphIdentity, "not-built"),
                "materialization=" + normalize(materializationKey, "not-built"),
                "proof=" + normalize(proofAnchor, "runtime-equivalence-required"),
                "rollback=" + normalize(rollbackAnchor, "original-ir")
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
