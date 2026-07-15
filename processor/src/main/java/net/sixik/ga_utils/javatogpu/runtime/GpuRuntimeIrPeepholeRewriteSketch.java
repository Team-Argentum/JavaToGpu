package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only skeleton for a future structural typed-IR peephole rewrite operation.
 *
 * <p>A sketch can prove that a replacement plan has enough graph information to describe a future rewrite, but it does
 * not build replacement nodes, mutate typed bodies, select optimized IR, or authorize production mutation.</p>
 */
public record GpuRuntimeIrPeepholeRewriteSketch(
        String ruleId,
        String methodName,
        int rootNodeId,
        String replacementKind,
        List<Integer> coveredNodeIds,
        List<Integer> inputNodeIds,
        boolean planComplete,
        boolean planValid,
        boolean sketchReady,
        String status,
        String firstBlocker,
        boolean rewriteBuilderImplemented,
        boolean mutationAllowed,
        boolean selectedIrReplacement,
        boolean runtimeEquivalenceRequired,
        boolean approvalRequired
) {

    public GpuRuntimeIrPeepholeRewriteSketch {
        ruleId = normalize(ruleId, "rule:unknown");
        methodName = normalize(methodName, "unknown");
        rootNodeId = Math.max(0, rootNodeId);
        replacementKind = normalize(replacementKind, "unknown");
        coveredNodeIds = normalizeNodeIds(coveredNodeIds);
        inputNodeIds = normalizeNodeIds(inputNodeIds);
        status = normalize(status, sketchReady ? "sketch-ready" : "blocked");
        firstBlocker = normalize(firstBlocker, sketchReady ? "none" : "rewrite-sketch-blocked");
    }

    public static GpuRuntimeIrPeepholeRewriteSketch from(
            GpuRuntimeIrPeepholeReplacementPlan plan,
            GpuRuntimeIrPeepholeReplacementPlanValidation validation
    ) {
        if (plan == null) {
            return blocked(
                    "rule:unknown",
                    "unknown",
                    0,
                    "unknown",
                    List.of(),
                    List.of(),
                    false,
                    false,
                    "replacement-plan-missing"
            );
        }
        boolean planValid = validation != null && validation.valid();
        if (!plan.complete()) {
            return blocked(
                    plan.ruleId(),
                    plan.methodName(),
                    plan.rootNodeId(),
                    plan.replacementKind(),
                    plan.coveredNodeIds(),
                    plan.inputNodeIds(),
                    false,
                    planValid,
                    plan.firstBlocker()
            );
        }
        if (validation == null) {
            return blocked(
                    plan.ruleId(),
                    plan.methodName(),
                    plan.rootNodeId(),
                    plan.replacementKind(),
                    plan.coveredNodeIds(),
                    plan.inputNodeIds(),
                    true,
                    false,
                    "replacement-plan-validation-missing"
            );
        }
        if (!validation.valid()) {
            return blocked(
                    plan.ruleId(),
                    plan.methodName(),
                    plan.rootNodeId(),
                    plan.replacementKind(),
                    plan.coveredNodeIds(),
                    plan.inputNodeIds(),
                    true,
                    false,
                    validation.firstBlocker()
            );
        }
        return new GpuRuntimeIrPeepholeRewriteSketch(
                plan.ruleId(),
                plan.methodName(),
                plan.rootNodeId(),
                plan.replacementKind(),
                plan.coveredNodeIds(),
                plan.inputNodeIds(),
                true,
                true,
                true,
                "sketch-ready",
                "none",
                false,
                false,
                false,
                true,
                true
        );
    }

    public Map<String, String> fields(String prefix) {
        String normalizedPrefix = normalize(prefix, "rewriteSketch");
        return Map.ofEntries(
                Map.entry(normalizedPrefix + ".ruleId", ruleId),
                Map.entry(normalizedPrefix + ".methodName", methodName),
                Map.entry(normalizedPrefix + ".rootNodeId", Integer.toString(rootNodeId)),
                Map.entry(normalizedPrefix + ".replacementKind", replacementKind),
                Map.entry(normalizedPrefix + ".coveredNodeIds", join(coveredNodeIds)),
                Map.entry(normalizedPrefix + ".inputNodeIds", join(inputNodeIds)),
                Map.entry(normalizedPrefix + ".planComplete", Boolean.toString(planComplete)),
                Map.entry(normalizedPrefix + ".planValid", Boolean.toString(planValid)),
                Map.entry(normalizedPrefix + ".sketchReady", Boolean.toString(sketchReady)),
                Map.entry(normalizedPrefix + ".status", status),
                Map.entry(normalizedPrefix + ".firstBlocker", firstBlocker),
                Map.entry(normalizedPrefix + ".rewriteBuilderImplemented", Boolean.toString(rewriteBuilderImplemented)),
                Map.entry(normalizedPrefix + ".mutationAllowed", Boolean.toString(mutationAllowed)),
                Map.entry(normalizedPrefix + ".selectedIrReplacement", Boolean.toString(selectedIrReplacement)),
                Map.entry(normalizedPrefix + ".runtimeEquivalenceRequired", Boolean.toString(runtimeEquivalenceRequired)),
                Map.entry(normalizedPrefix + ".approvalRequired", Boolean.toString(approvalRequired))
        );
    }

    private static GpuRuntimeIrPeepholeRewriteSketch blocked(
            String ruleId,
            String methodName,
            int rootNodeId,
            String replacementKind,
            List<Integer> coveredNodeIds,
            List<Integer> inputNodeIds,
            boolean planComplete,
            boolean planValid,
            String firstBlocker
    ) {
        return new GpuRuntimeIrPeepholeRewriteSketch(
                ruleId,
                methodName,
                rootNodeId,
                replacementKind,
                coveredNodeIds,
                inputNodeIds,
                planComplete,
                planValid,
                false,
                "blocked",
                firstBlocker,
                false,
                false,
                false,
                true,
                true
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
