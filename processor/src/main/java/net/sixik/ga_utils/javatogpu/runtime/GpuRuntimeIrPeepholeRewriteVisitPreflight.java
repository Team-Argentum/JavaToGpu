package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only traversal preflight for a future typed peephole rewrite.
 *
 * <p>This proves only that the original graph nodes needed by a replacement plan can be visited deterministically. It
 * does not create replacement nodes, mutate typed bodies, or select optimized IR.</p>
 */
public record GpuRuntimeIrPeepholeRewriteVisitPreflight(
        String ruleId,
        String methodName,
        int rootNodeId,
        String replacementKind,
        List<Integer> coveredNodeIds,
        List<Integer> inputNodeIds,
        List<Integer> visitOrderNodeIds,
        int graphNodeMaxId,
        boolean planComplete,
        boolean planValid,
        boolean rootVisitable,
        boolean coveredNodesVisitable,
        boolean inputNodesVisitable,
        boolean visitorReady,
        String status,
        String firstBlocker,
        boolean visitorImplemented,
        boolean replacementBuilderImplemented,
        boolean transformedIrBuilt,
        boolean mutationAllowed,
        boolean selectedIrReplacement
) {

    public GpuRuntimeIrPeepholeRewriteVisitPreflight {
        ruleId = normalize(ruleId, "rule:unknown");
        methodName = normalize(methodName, "unknown");
        rootNodeId = Math.max(0, rootNodeId);
        replacementKind = normalize(replacementKind, "unknown");
        coveredNodeIds = normalizeNodeIds(coveredNodeIds);
        inputNodeIds = normalizeNodeIds(inputNodeIds);
        visitOrderNodeIds = normalizeNodeIds(visitOrderNodeIds);
        graphNodeMaxId = Math.max(0, graphNodeMaxId);
        status = normalize(status, visitorReady ? "visitor-ready" : "blocked");
        firstBlocker = normalize(firstBlocker, visitorReady ? "none" : "rewrite-visitor-blocked");
    }

    public static GpuRuntimeIrPeepholeRewriteVisitPreflight blocked(
            String ruleId,
            String methodName,
            int rootNodeId,
            String replacementKind,
            List<Integer> coveredNodeIds,
            List<Integer> inputNodeIds,
            List<Integer> visitOrderNodeIds,
            int graphNodeMaxId,
            boolean planComplete,
            boolean planValid,
            boolean rootVisitable,
            boolean coveredNodesVisitable,
            boolean inputNodesVisitable,
            String firstBlocker
    ) {
        return new GpuRuntimeIrPeepholeRewriteVisitPreflight(
                ruleId,
                methodName,
                rootNodeId,
                replacementKind,
                coveredNodeIds,
                inputNodeIds,
                visitOrderNodeIds,
                graphNodeMaxId,
                planComplete,
                planValid,
                rootVisitable,
                coveredNodesVisitable,
                inputNodesVisitable,
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

    public static GpuRuntimeIrPeepholeRewriteVisitPreflight ready(
            GpuRuntimeIrPeepholeReplacementPlan plan,
            List<Integer> visitOrderNodeIds,
            int graphNodeMaxId
    ) {
        return new GpuRuntimeIrPeepholeRewriteVisitPreflight(
                plan.ruleId(),
                plan.methodName(),
                plan.rootNodeId(),
                plan.replacementKind(),
                plan.coveredNodeIds(),
                plan.inputNodeIds(),
                visitOrderNodeIds,
                graphNodeMaxId,
                true,
                true,
                true,
                true,
                true,
                true,
                "visitor-ready",
                "none",
                true,
                false,
                false,
                false,
                false
        );
    }

    public Map<String, String> fields(String prefix) {
        String normalizedPrefix = normalize(prefix, "rewriteVisitor");
        return Map.ofEntries(
                Map.entry(normalizedPrefix + ".ruleId", ruleId),
                Map.entry(normalizedPrefix + ".methodName", methodName),
                Map.entry(normalizedPrefix + ".rootNodeId", Integer.toString(rootNodeId)),
                Map.entry(normalizedPrefix + ".replacementKind", replacementKind),
                Map.entry(normalizedPrefix + ".coveredNodeIds", join(coveredNodeIds)),
                Map.entry(normalizedPrefix + ".inputNodeIds", join(inputNodeIds)),
                Map.entry(normalizedPrefix + ".visitOrderNodeIds", join(visitOrderNodeIds)),
                Map.entry(normalizedPrefix + ".graphNodeMaxId", Integer.toString(graphNodeMaxId)),
                Map.entry(normalizedPrefix + ".planComplete", Boolean.toString(planComplete)),
                Map.entry(normalizedPrefix + ".planValid", Boolean.toString(planValid)),
                Map.entry(normalizedPrefix + ".rootVisitable", Boolean.toString(rootVisitable)),
                Map.entry(normalizedPrefix + ".coveredNodesVisitable", Boolean.toString(coveredNodesVisitable)),
                Map.entry(normalizedPrefix + ".inputNodesVisitable", Boolean.toString(inputNodesVisitable)),
                Map.entry(normalizedPrefix + ".visitorReady", Boolean.toString(visitorReady)),
                Map.entry(normalizedPrefix + ".status", status),
                Map.entry(normalizedPrefix + ".firstBlocker", firstBlocker),
                Map.entry(normalizedPrefix + ".visitorImplemented", Boolean.toString(visitorImplemented)),
                Map.entry(normalizedPrefix + ".replacementBuilderImplemented", Boolean.toString(replacementBuilderImplemented)),
                Map.entry(normalizedPrefix + ".transformedIrBuilt", Boolean.toString(transformedIrBuilt)),
                Map.entry(normalizedPrefix + ".mutationAllowed", Boolean.toString(mutationAllowed)),
                Map.entry(normalizedPrefix + ".selectedIrReplacement", Boolean.toString(selectedIrReplacement))
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
