package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Read-only description of the future replacement node for one peephole rewrite.
 *
 * <p>The blueprint records the intended intrinsic call shape and argument source nodes. It is not a builder and never
 * creates transformed IR.</p>
 */
public record GpuRuntimeIrPeepholeReplacementBlueprint(
        String ruleId,
        String methodName,
        int rootNodeId,
        String replacementKind,
        String targetNodeKind,
        String targetOperation,
        List<Integer> argumentNodeIds,
        List<String> argumentRoles,
        boolean visitorReady,
        boolean blueprintReady,
        String status,
        String firstBlocker,
        boolean blueprintImplemented,
        boolean replacementBuilderImplemented,
        boolean transformedIrBuilt,
        boolean mutationAllowed,
        boolean selectedIrReplacement
) {

    public GpuRuntimeIrPeepholeReplacementBlueprint {
        ruleId = normalize(ruleId, "rule:unknown");
        methodName = normalize(methodName, "unknown");
        rootNodeId = Math.max(0, rootNodeId);
        replacementKind = normalize(replacementKind, "unknown");
        targetNodeKind = normalize(targetNodeKind, "GpuIrIntrinsicCall");
        targetOperation = normalize(targetOperation, replacementKind);
        argumentNodeIds = normalizeNodeIds(argumentNodeIds);
        argumentRoles = argumentRoles == null || argumentRoles.isEmpty()
                ? defaultRoles(argumentNodeIds.size())
                : List.copyOf(argumentRoles);
        status = normalize(status, blueprintReady ? "blueprint-ready" : "blocked");
        firstBlocker = normalize(firstBlocker, blueprintReady ? "none" : "replacement-blueprint-blocked");
    }

    public static GpuRuntimeIrPeepholeReplacementBlueprint from(
            GpuRuntimeIrPeepholeRewriteVisitPreflight preflight
    ) {
        if (preflight == null) {
            return blocked(
                    "rule:unknown",
                    "unknown",
                    0,
                    "unknown",
                    List.of(),
                    false,
                    "rewrite-visitor-preflight-missing"
            );
        }
        if (!preflight.visitorReady()) {
            return blocked(
                    preflight.ruleId(),
                    preflight.methodName(),
                    preflight.rootNodeId(),
                    preflight.replacementKind(),
                    preflight.inputNodeIds(),
                    false,
                    preflight.firstBlocker()
            );
        }
        if (preflight.inputNodeIds().isEmpty()) {
            return blocked(
                    preflight.ruleId(),
                    preflight.methodName(),
                    preflight.rootNodeId(),
                    preflight.replacementKind(),
                    preflight.inputNodeIds(),
                    true,
                    "replacement-blueprint-inputs-missing"
            );
        }
        return new GpuRuntimeIrPeepholeReplacementBlueprint(
                preflight.ruleId(),
                preflight.methodName(),
                preflight.rootNodeId(),
                preflight.replacementKind(),
                "GpuIrIntrinsicCall",
                preflight.replacementKind(),
                preflight.inputNodeIds(),
                defaultRoles(preflight.inputNodeIds().size()),
                true,
                true,
                "blueprint-ready",
                "none",
                true,
                false,
                false,
                false,
                false
        );
    }

    public Map<String, String> fields(String prefix) {
        String normalizedPrefix = normalize(prefix, "replacementBlueprint");
        return Map.ofEntries(
                Map.entry(normalizedPrefix + ".ruleId", ruleId),
                Map.entry(normalizedPrefix + ".methodName", methodName),
                Map.entry(normalizedPrefix + ".rootNodeId", Integer.toString(rootNodeId)),
                Map.entry(normalizedPrefix + ".replacementKind", replacementKind),
                Map.entry(normalizedPrefix + ".targetNodeKind", targetNodeKind),
                Map.entry(normalizedPrefix + ".targetOperation", targetOperation),
                Map.entry(normalizedPrefix + ".argumentNodeIds", join(argumentNodeIds)),
                Map.entry(normalizedPrefix + ".argumentRoles", String.join(",", argumentRoles)),
                Map.entry(normalizedPrefix + ".visitorReady", Boolean.toString(visitorReady)),
                Map.entry(normalizedPrefix + ".blueprintReady", Boolean.toString(blueprintReady)),
                Map.entry(normalizedPrefix + ".status", status),
                Map.entry(normalizedPrefix + ".firstBlocker", firstBlocker),
                Map.entry(normalizedPrefix + ".blueprintImplemented", Boolean.toString(blueprintImplemented)),
                Map.entry(normalizedPrefix + ".replacementBuilderImplemented", Boolean.toString(replacementBuilderImplemented)),
                Map.entry(normalizedPrefix + ".transformedIrBuilt", Boolean.toString(transformedIrBuilt)),
                Map.entry(normalizedPrefix + ".mutationAllowed", Boolean.toString(mutationAllowed)),
                Map.entry(normalizedPrefix + ".selectedIrReplacement", Boolean.toString(selectedIrReplacement))
        );
    }

    private static GpuRuntimeIrPeepholeReplacementBlueprint blocked(
            String ruleId,
            String methodName,
            int rootNodeId,
            String replacementKind,
            List<Integer> argumentNodeIds,
            boolean visitorReady,
            String firstBlocker
    ) {
        return new GpuRuntimeIrPeepholeReplacementBlueprint(
                ruleId,
                methodName,
                rootNodeId,
                replacementKind,
                "GpuIrIntrinsicCall",
                replacementKind,
                argumentNodeIds,
                defaultRoles(argumentNodeIds == null ? 0 : argumentNodeIds.size()),
                visitorReady,
                false,
                "blocked",
                firstBlocker,
                true,
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
        return IntStream.range(0, count)
                .mapToObj(index -> "arg" + index)
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
