package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Map;

/**
 * Read-only preflight that explains why future peephole rewrite sketches are not selectable yet.
 */
public record GpuRuntimeIrPeepholeRewriteSelectionReadiness(
        int sketchCount,
        int readySketchCount,
        int blockedSketchCount,
        int conflictCount,
        String status,
        String firstBlocker,
        boolean rewriteBuilderImplemented,
        boolean conflictResolutionImplemented,
        boolean runtimeEquivalenceRequired,
        boolean runtimeEquivalenceProven,
        boolean approvalRequired,
        boolean approvalAccepted,
        boolean mutationAllowed,
        boolean selectionApplied,
        boolean selectedIrReplacement
) {

    public GpuRuntimeIrPeepholeRewriteSelectionReadiness {
        sketchCount = Math.max(0, sketchCount);
        readySketchCount = Math.max(0, readySketchCount);
        blockedSketchCount = Math.max(0, blockedSketchCount);
        conflictCount = Math.max(0, conflictCount);
        status = normalize(status, sketchCount == 0 ? "not-required" : "blocked");
        firstBlocker = normalize(firstBlocker, defaultBlocker(sketchCount, readySketchCount, blockedSketchCount, conflictCount));
    }

    public static GpuRuntimeIrPeepholeRewriteSelectionReadiness from(
            List<GpuRuntimeIrPeepholeRewriteSketch> sketches,
            List<GpuRuntimeIrPeepholeRewriteSketchConflict> conflicts
    ) {
        List<GpuRuntimeIrPeepholeRewriteSketch> normalizedSketches = sketches == null ? List.of() : sketches;
        List<GpuRuntimeIrPeepholeRewriteSketchConflict> normalizedConflicts = conflicts == null ? List.of() : conflicts;
        int readyCount = (int) normalizedSketches.stream()
                .filter(GpuRuntimeIrPeepholeRewriteSketch::sketchReady)
                .count();
        int blockedCount = normalizedSketches.size() - readyCount;
        String firstBlocker = firstBlocker(normalizedSketches, readyCount, blockedCount, normalizedConflicts.size());
        return new GpuRuntimeIrPeepholeRewriteSelectionReadiness(
                normalizedSketches.size(),
                readyCount,
                blockedCount,
                normalizedConflicts.size(),
                normalizedSketches.isEmpty() ? "not-required" : "blocked",
                firstBlocker,
                false,
                false,
                readyCount > 0,
                false,
                readyCount > 0,
                false,
                false,
                false,
                false
        );
    }

    public Map<String, String> fields(String prefix) {
        String normalizedPrefix = normalize(prefix, "rewriteSelection");
        return Map.ofEntries(
                Map.entry(normalizedPrefix + ".sketch.count", Integer.toString(sketchCount)),
                Map.entry(normalizedPrefix + ".sketch.ready.count", Integer.toString(readySketchCount)),
                Map.entry(normalizedPrefix + ".sketch.blocked.count", Integer.toString(blockedSketchCount)),
                Map.entry(normalizedPrefix + ".conflict.count", Integer.toString(conflictCount)),
                Map.entry(normalizedPrefix + ".status", status),
                Map.entry(normalizedPrefix + ".firstBlocker", firstBlocker),
                Map.entry(normalizedPrefix + ".rewriteBuilderImplemented", Boolean.toString(rewriteBuilderImplemented)),
                Map.entry(normalizedPrefix + ".conflictResolutionImplemented", Boolean.toString(conflictResolutionImplemented)),
                Map.entry(normalizedPrefix + ".runtimeEquivalenceRequired", Boolean.toString(runtimeEquivalenceRequired)),
                Map.entry(normalizedPrefix + ".runtimeEquivalenceProven", Boolean.toString(runtimeEquivalenceProven)),
                Map.entry(normalizedPrefix + ".approvalRequired", Boolean.toString(approvalRequired)),
                Map.entry(normalizedPrefix + ".approvalAccepted", Boolean.toString(approvalAccepted)),
                Map.entry(normalizedPrefix + ".mutationAllowed", Boolean.toString(mutationAllowed)),
                Map.entry(normalizedPrefix + ".selectionApplied", Boolean.toString(selectionApplied)),
                Map.entry(normalizedPrefix + ".selectedIrReplacement", Boolean.toString(selectedIrReplacement))
        );
    }

    private static String firstBlocker(
            List<GpuRuntimeIrPeepholeRewriteSketch> sketches,
            int readyCount,
            int blockedCount,
            int conflictCount
    ) {
        if (sketches.isEmpty()) {
            return "no-rewrite-sketches";
        }
        if (blockedCount > 0) {
            return sketches.stream()
                    .filter(sketch -> !sketch.sketchReady())
                    .map(GpuRuntimeIrPeepholeRewriteSketch::firstBlocker)
                    .filter(blocker -> blocker != null && !blocker.isBlank() && !"none".equals(blocker))
                    .findFirst()
                    .orElse("rewrite-sketch-blocked");
        }
        if (conflictCount > 0) {
            return "rewrite-sketch-conflict-resolution-required";
        }
        return readyCount > 0 ? "rewrite-builder-not-implemented" : "no-rewrite-sketches";
    }

    private static String defaultBlocker(int sketchCount, int readySketchCount, int blockedSketchCount, int conflictCount) {
        if (sketchCount == 0) {
            return "no-rewrite-sketches";
        }
        if (blockedSketchCount > 0) {
            return "rewrite-sketch-blocked";
        }
        if (conflictCount > 0) {
            return "rewrite-sketch-conflict-resolution-required";
        }
        return readySketchCount > 0 ? "rewrite-builder-not-implemented" : "no-rewrite-sketches";
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
