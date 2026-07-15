package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Map;

/**
 * Read-only proof gate for future peephole rewrites.
 *
 * <p>This records the runtime-equivalence, rollback, and approval evidence that would be required before a structural
 * rewrite could ever be selected. It does not build transformed IR or mark proof as accepted.</p>
 */
public record GpuRuntimeIrPeepholeRewriteProofReadiness(
        String optimizerFamily,
        int readySketchCount,
        String status,
        String firstBlocker,
        boolean proofRequired,
        boolean proofAccepted,
        boolean runtimeEquivalenceRequired,
        boolean runtimeEquivalencePayloadPresent,
        boolean runtimeEquivalencePayloadComplete,
        boolean rollbackRequired,
        boolean rollbackEvidencePresent,
        boolean rollbackClean,
        boolean approvalRequired,
        boolean approvalAccepted,
        boolean mutationAllowed,
        boolean selectedIrReplacement,
        String originalIrIdentity,
        String transformedIrIdentity
) {

    public GpuRuntimeIrPeepholeRewriteProofReadiness {
        optimizerFamily = normalize(optimizerFamily, "peephole");
        readySketchCount = Math.max(0, readySketchCount);
        status = normalize(status, readySketchCount == 0 ? "not-required" : "blocked");
        firstBlocker = normalize(firstBlocker, readySketchCount == 0 ? "no-proof-candidates" : "runtime-equivalence-payload-missing");
        originalIrIdentity = normalize(originalIrIdentity, "irgpu:missing");
        transformedIrIdentity = normalize(transformedIrIdentity, "not-built");
    }

    public static GpuRuntimeIrPeepholeRewriteProofReadiness from(
            GpuRuntimeIrPeepholeRewriteSelectionReadiness selectionReadiness,
            String originalIrIdentity
    ) {
        if (selectionReadiness == null || selectionReadiness.readySketchCount() == 0) {
            return new GpuRuntimeIrPeepholeRewriteProofReadiness(
                    "peephole",
                    0,
                    "not-required",
                    selectionReadiness == null ? "no-proof-candidates" : selectionReadiness.firstBlocker(),
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    originalIrIdentity,
                    "not-built"
            );
        }
        return new GpuRuntimeIrPeepholeRewriteProofReadiness(
                "peephole",
                selectionReadiness.readySketchCount(),
                "blocked",
                selectionReadiness.firstBlocker(),
                true,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                originalIrIdentity,
                "not-built"
        );
    }

    public Map<String, String> fields(String prefix) {
        String normalizedPrefix = normalize(prefix, "rewriteProof");
        return Map.ofEntries(
                Map.entry(normalizedPrefix + ".optimizerFamily", optimizerFamily),
                Map.entry(normalizedPrefix + ".sketch.ready.count", Integer.toString(readySketchCount)),
                Map.entry(normalizedPrefix + ".status", status),
                Map.entry(normalizedPrefix + ".firstBlocker", firstBlocker),
                Map.entry(normalizedPrefix + ".proofRequired", Boolean.toString(proofRequired)),
                Map.entry(normalizedPrefix + ".proofAccepted", Boolean.toString(proofAccepted)),
                Map.entry(normalizedPrefix + ".runtimeEquivalenceRequired", Boolean.toString(runtimeEquivalenceRequired)),
                Map.entry(normalizedPrefix + ".runtimeEquivalencePayload.present", Boolean.toString(runtimeEquivalencePayloadPresent)),
                Map.entry(normalizedPrefix + ".runtimeEquivalencePayload.complete", Boolean.toString(runtimeEquivalencePayloadComplete)),
                Map.entry(normalizedPrefix + ".rollbackRequired", Boolean.toString(rollbackRequired)),
                Map.entry(normalizedPrefix + ".rollbackEvidence.present", Boolean.toString(rollbackEvidencePresent)),
                Map.entry(normalizedPrefix + ".rollbackClean", Boolean.toString(rollbackClean)),
                Map.entry(normalizedPrefix + ".approvalRequired", Boolean.toString(approvalRequired)),
                Map.entry(normalizedPrefix + ".approvalAccepted", Boolean.toString(approvalAccepted)),
                Map.entry(normalizedPrefix + ".mutationAllowed", Boolean.toString(mutationAllowed)),
                Map.entry(normalizedPrefix + ".selectedIrReplacement", Boolean.toString(selectedIrReplacement)),
                Map.entry(normalizedPrefix + ".originalIrIdentity", originalIrIdentity),
                Map.entry(normalizedPrefix + ".transformedIrIdentity", transformedIrIdentity)
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
