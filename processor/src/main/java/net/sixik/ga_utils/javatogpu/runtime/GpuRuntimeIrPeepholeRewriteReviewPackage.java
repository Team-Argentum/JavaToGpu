package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Map;

/**
 * Read-only review package preflight for future peephole rewrites.
 *
 * <p>The package intentionally remains incomplete until runtime-equivalence payload, rollback evidence, approval, and
 * proof acceptance exist. It never enables mutation or selects transformed IR.</p>
 */
public record GpuRuntimeIrPeepholeRewriteReviewPackage(
        String optimizerFamily,
        int readySketchCount,
        int conflictCount,
        String status,
        String firstBlocker,
        boolean required,
        boolean complete,
        boolean proofAccepted,
        boolean runtimeEquivalencePayloadPresent,
        boolean runtimeEquivalencePayloadComplete,
        boolean rollbackEvidencePresent,
        boolean rollbackClean,
        boolean approvalAccepted,
        boolean mutationAllowed,
        boolean selectionApplied,
        boolean selectedIrReplacement,
        boolean manualReviewOnly,
        String originalIrIdentity,
        String transformedIrIdentity
) {

    public GpuRuntimeIrPeepholeRewriteReviewPackage {
        optimizerFamily = normalize(optimizerFamily, "peephole");
        readySketchCount = Math.max(0, readySketchCount);
        conflictCount = Math.max(0, conflictCount);
        status = normalize(status, readySketchCount == 0 ? "not-required" : "blocked");
        firstBlocker = normalize(firstBlocker, readySketchCount == 0 ? "no-review-candidates" : "runtime-equivalence-payload-missing");
        originalIrIdentity = normalize(originalIrIdentity, "irgpu:missing");
        transformedIrIdentity = normalize(transformedIrIdentity, "not-built");
    }

    public static GpuRuntimeIrPeepholeRewriteReviewPackage from(
            GpuRuntimeIrPeepholeRewriteSelectionReadiness selectionReadiness,
            GpuRuntimeIrPeepholeRewriteProofReadiness proofReadiness
    ) {
        int readySketches = selectionReadiness == null ? 0 : selectionReadiness.readySketchCount();
        int conflicts = selectionReadiness == null ? 0 : selectionReadiness.conflictCount();
        boolean required = readySketches > 0;
        boolean complete = required
                && conflicts == 0
                && proofReadiness != null
                && proofReadiness.proofAccepted()
                && proofReadiness.runtimeEquivalencePayloadComplete()
                && proofReadiness.rollbackEvidencePresent()
                && proofReadiness.rollbackClean()
                && proofReadiness.approvalAccepted()
                && !proofReadiness.mutationAllowed()
                && !proofReadiness.selectedIrReplacement();
        return new GpuRuntimeIrPeepholeRewriteReviewPackage(
                proofReadiness == null ? "peephole" : proofReadiness.optimizerFamily(),
                readySketches,
                conflicts,
                !required ? "not-required" : complete ? "ready-for-manual-review" : "blocked",
                firstBlocker(selectionReadiness, proofReadiness, required, complete),
                required,
                complete,
                proofReadiness != null && proofReadiness.proofAccepted(),
                proofReadiness != null && proofReadiness.runtimeEquivalencePayloadPresent(),
                proofReadiness != null && proofReadiness.runtimeEquivalencePayloadComplete(),
                proofReadiness != null && proofReadiness.rollbackEvidencePresent(),
                proofReadiness != null && proofReadiness.rollbackClean(),
                proofReadiness != null && proofReadiness.approvalAccepted(),
                false,
                false,
                false,
                true,
                proofReadiness == null ? "irgpu:missing" : proofReadiness.originalIrIdentity(),
                proofReadiness == null ? "not-built" : proofReadiness.transformedIrIdentity()
        );
    }

    public Map<String, String> fields(String prefix) {
        String normalizedPrefix = normalize(prefix, "rewriteReviewPackage");
        return Map.ofEntries(
                Map.entry(normalizedPrefix + ".optimizerFamily", optimizerFamily),
                Map.entry(normalizedPrefix + ".sketch.ready.count", Integer.toString(readySketchCount)),
                Map.entry(normalizedPrefix + ".conflict.count", Integer.toString(conflictCount)),
                Map.entry(normalizedPrefix + ".status", status),
                Map.entry(normalizedPrefix + ".firstBlocker", firstBlocker),
                Map.entry(normalizedPrefix + ".required", Boolean.toString(required)),
                Map.entry(normalizedPrefix + ".complete", Boolean.toString(complete)),
                Map.entry(normalizedPrefix + ".proofAccepted", Boolean.toString(proofAccepted)),
                Map.entry(normalizedPrefix + ".runtimeEquivalencePayload.present", Boolean.toString(runtimeEquivalencePayloadPresent)),
                Map.entry(normalizedPrefix + ".runtimeEquivalencePayload.complete", Boolean.toString(runtimeEquivalencePayloadComplete)),
                Map.entry(normalizedPrefix + ".rollbackEvidence.present", Boolean.toString(rollbackEvidencePresent)),
                Map.entry(normalizedPrefix + ".rollbackClean", Boolean.toString(rollbackClean)),
                Map.entry(normalizedPrefix + ".approvalAccepted", Boolean.toString(approvalAccepted)),
                Map.entry(normalizedPrefix + ".mutationAllowed", Boolean.toString(mutationAllowed)),
                Map.entry(normalizedPrefix + ".selectionApplied", Boolean.toString(selectionApplied)),
                Map.entry(normalizedPrefix + ".selectedIrReplacement", Boolean.toString(selectedIrReplacement)),
                Map.entry(normalizedPrefix + ".manualReviewOnly", Boolean.toString(manualReviewOnly)),
                Map.entry(normalizedPrefix + ".originalIrIdentity", originalIrIdentity),
                Map.entry(normalizedPrefix + ".transformedIrIdentity", transformedIrIdentity)
        );
    }

    private static String firstBlocker(
            GpuRuntimeIrPeepholeRewriteSelectionReadiness selectionReadiness,
            GpuRuntimeIrPeepholeRewriteProofReadiness proofReadiness,
            boolean required,
            boolean complete
    ) {
        if (!required) {
            return selectionReadiness == null ? "no-review-candidates" : selectionReadiness.firstBlocker();
        }
        if (complete) {
            return "none";
        }
        if (selectionReadiness != null && selectionReadiness.conflictCount() > 0) {
            return "rewrite-sketch-conflict-resolution-required";
        }
        if (proofReadiness == null || !proofReadiness.runtimeEquivalencePayloadPresent()) {
            return "runtime-equivalence-payload-missing";
        }
        if (!proofReadiness.runtimeEquivalencePayloadComplete()) {
            return "runtime-equivalence-payload-incomplete";
        }
        if (!proofReadiness.rollbackEvidencePresent()) {
            return "rollback-evidence-missing";
        }
        if (!proofReadiness.rollbackClean()) {
            return "rollback-not-clean";
        }
        if (!proofReadiness.approvalAccepted()) {
            return "approval-not-accepted";
        }
        if (!proofReadiness.proofAccepted()) {
            return "proof-not-accepted";
        }
        return "manual-review-required";
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
