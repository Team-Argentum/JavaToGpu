package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Map;

/**
 * Read-only optimized-artifact selection preview for future peephole rewrites.
 *
 * <p>The preview records why a future optimized artifact cannot become the selected runtime IR yet. It never builds,
 * binds, selects, or mutates IR.</p>
 */
public record GpuRuntimeIrPeepholeOptimizedArtifactSelectionPreflight(
        String ruleId,
        String methodName,
        int rootNodeId,
        String replacementKind,
        String originalIrIdentity,
        String optimizedArtifactIdentity,
        String envelopeKey,
        String proofBindingStatus,
        String proofBindingFirstBlocker,
        boolean proofBindingReady,
        boolean proofBound,
        boolean rollbackBound,
        boolean approvalBound,
        boolean optimizedArtifactBuilt,
        boolean transformedIrBuilt,
        boolean productionGateRequired,
        boolean productionGateAccepted,
        boolean mutationPolicyAllowed,
        boolean selectionReady,
        String status,
        String firstBlocker,
        boolean selectionPreflightImplemented,
        boolean selectionApplied,
        boolean optimizedArtifactSelected,
        boolean mutationAllowed,
        boolean selectedIrReplacement
) {

    private static final String RULE_UNKNOWN = "rule:unknown";
    private static final String UNKNOWN = "unknown";
    private static final String ORIGINAL_IR_UNKNOWN = "irgpu:unknown";
    private static final String NOT_BUILT = "not-built";
    private static final String STATUS_BLOCKED = "blocked";
    private static final String STATUS_SELECTION_READY = "selection-ready";
    private static final String BLOCKER_NONE = "none";
    private static final String BLOCKER_ARTIFACT_PROOF_BINDING_BLOCKED = "artifact-proof-binding-blocked";
    private static final String BLOCKER_ARTIFACT_PROOF_BINDING_MISSING = "artifact-proof-binding-missing";
    private static final String BLOCKER_OPTIMIZED_ARTIFACT_SELECTION_BLOCKED = "optimized-artifact-selection-blocked";
    private static final String BLOCKER_PROOF_NOT_BOUND = "proof-not-bound";
    private static final String BLOCKER_ROLLBACK_NOT_BOUND = "rollback-not-bound";
    private static final String BLOCKER_APPROVAL_NOT_BOUND = "approval-not-bound";
    private static final String BLOCKER_OPTIMIZED_ARTIFACT_NOT_BUILT = "optimized-artifact-not-built";
    private static final String BLOCKER_TRANSFORMED_IR_NOT_BUILT = "transformed-ir-not-built";
    private static final String BLOCKER_PRODUCTION_GATE_NOT_ACCEPTED = "production-gate-not-accepted";

    public GpuRuntimeIrPeepholeOptimizedArtifactSelectionPreflight {
        ruleId = normalize(ruleId, RULE_UNKNOWN);
        methodName = normalize(methodName, UNKNOWN);
        rootNodeId = Math.max(0, rootNodeId);
        replacementKind = normalize(replacementKind, UNKNOWN);
        originalIrIdentity = normalize(originalIrIdentity, ORIGINAL_IR_UNKNOWN);
        optimizedArtifactIdentity = normalize(optimizedArtifactIdentity, NOT_BUILT);
        envelopeKey = normalize(envelopeKey, NOT_BUILT);
        proofBindingStatus = normalize(proofBindingStatus, STATUS_BLOCKED);
        proofBindingFirstBlocker = normalize(proofBindingFirstBlocker, BLOCKER_ARTIFACT_PROOF_BINDING_BLOCKED);
        status = normalize(status, selectionReady ? STATUS_SELECTION_READY : STATUS_BLOCKED);
        firstBlocker = normalize(firstBlocker, selectionReady ? BLOCKER_NONE : BLOCKER_OPTIMIZED_ARTIFACT_SELECTION_BLOCKED);
    }

    public static GpuRuntimeIrPeepholeOptimizedArtifactSelectionPreflight from(
            GpuRuntimeIrPeepholeArtifactProofBindingPreflight binding
    ) {
        if (binding == null) {
            return blocked(
                    RULE_UNKNOWN,
                    UNKNOWN,
                    0,
                    UNKNOWN,
                    ORIGINAL_IR_UNKNOWN,
                    NOT_BUILT,
                    NOT_BUILT,
                    STATUS_BLOCKED,
                    BLOCKER_ARTIFACT_PROOF_BINDING_MISSING,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    BLOCKER_ARTIFACT_PROOF_BINDING_MISSING
            );
        }
        if (!binding.bindingReady()) {
            return blocked(
                    binding.ruleId(),
                    binding.methodName(),
                    binding.rootNodeId(),
                    binding.replacementKind(),
                    binding.originalIrIdentity(),
                    binding.optimizedArtifactIdentity(),
                    binding.envelopeKey(),
                    binding.status(),
                    binding.firstBlocker(),
                    false,
                    binding.proofBound(),
                    binding.rollbackBound(),
                    binding.approvalBound(),
                    binding.optimizedArtifactBuilt(),
                    binding.transformedIrBuilt(),
                    binding.firstBlocker()
            );
        }
        return blocked(
                binding.ruleId(),
                binding.methodName(),
                binding.rootNodeId(),
                binding.replacementKind(),
                binding.originalIrIdentity(),
                binding.optimizedArtifactIdentity(),
                binding.envelopeKey(),
                binding.status(),
                binding.firstBlocker(),
                true,
                binding.proofBound(),
                binding.rollbackBound(),
                binding.approvalBound(),
                binding.optimizedArtifactBuilt(),
                binding.transformedIrBuilt(),
                firstBlocker(binding)
        );
    }

    public Map<String, String> fields(String prefix) {
        String normalizedPrefix = normalize(prefix, "artifactSelection");
        return Map.ofEntries(
                Map.entry(normalizedPrefix + ".ruleId", ruleId),
                Map.entry(normalizedPrefix + ".methodName", methodName),
                Map.entry(normalizedPrefix + ".rootNodeId", Integer.toString(rootNodeId)),
                Map.entry(normalizedPrefix + ".replacementKind", replacementKind),
                Map.entry(normalizedPrefix + ".originalIrIdentity", originalIrIdentity),
                Map.entry(normalizedPrefix + ".optimizedArtifactIdentity", optimizedArtifactIdentity),
                Map.entry(normalizedPrefix + ".envelopeKey", envelopeKey),
                Map.entry(normalizedPrefix + ".proofBindingStatus", proofBindingStatus),
                Map.entry(normalizedPrefix + ".proofBindingFirstBlocker", proofBindingFirstBlocker),
                Map.entry(normalizedPrefix + ".proofBindingReady", Boolean.toString(proofBindingReady)),
                Map.entry(normalizedPrefix + ".proofBound", Boolean.toString(proofBound)),
                Map.entry(normalizedPrefix + ".rollbackBound", Boolean.toString(rollbackBound)),
                Map.entry(normalizedPrefix + ".approvalBound", Boolean.toString(approvalBound)),
                Map.entry(normalizedPrefix + ".optimizedArtifactBuilt", Boolean.toString(optimizedArtifactBuilt)),
                Map.entry(normalizedPrefix + ".transformedIrBuilt", Boolean.toString(transformedIrBuilt)),
                Map.entry(normalizedPrefix + ".productionGateRequired", Boolean.toString(productionGateRequired)),
                Map.entry(normalizedPrefix + ".productionGateAccepted", Boolean.toString(productionGateAccepted)),
                Map.entry(normalizedPrefix + ".mutationPolicyAllowed", Boolean.toString(mutationPolicyAllowed)),
                Map.entry(normalizedPrefix + ".selectionReady", Boolean.toString(selectionReady)),
                Map.entry(normalizedPrefix + ".status", status),
                Map.entry(normalizedPrefix + ".firstBlocker", firstBlocker),
                Map.entry(normalizedPrefix + ".selectionPreflightImplemented", Boolean.toString(selectionPreflightImplemented)),
                Map.entry(normalizedPrefix + ".selectionApplied", Boolean.toString(selectionApplied)),
                Map.entry(normalizedPrefix + ".optimizedArtifactSelected", Boolean.toString(optimizedArtifactSelected)),
                Map.entry(normalizedPrefix + ".mutationAllowed", Boolean.toString(mutationAllowed)),
                Map.entry(normalizedPrefix + ".selectedIrReplacement", Boolean.toString(selectedIrReplacement))
        );
    }

    private static GpuRuntimeIrPeepholeOptimizedArtifactSelectionPreflight blocked(
            String ruleId,
            String methodName,
            int rootNodeId,
            String replacementKind,
            String originalIrIdentity,
            String optimizedArtifactIdentity,
            String envelopeKey,
            String proofBindingStatus,
            String proofBindingFirstBlocker,
            boolean proofBindingReady,
            boolean proofBound,
            boolean rollbackBound,
            boolean approvalBound,
            boolean optimizedArtifactBuilt,
            boolean transformedIrBuilt,
            String firstBlocker
    ) {
        return new GpuRuntimeIrPeepholeOptimizedArtifactSelectionPreflight(
                ruleId,
                methodName,
                rootNodeId,
                replacementKind,
                originalIrIdentity,
                optimizedArtifactIdentity,
                envelopeKey,
                proofBindingStatus,
                proofBindingFirstBlocker,
                proofBindingReady,
                proofBound,
                rollbackBound,
                approvalBound,
                optimizedArtifactBuilt,
                transformedIrBuilt,
                true,
                false,
                false,
                false,
                STATUS_BLOCKED,
                firstBlocker,
                true,
                false,
                false,
                false,
                false
        );
    }

    private static String firstBlocker(GpuRuntimeIrPeepholeArtifactProofBindingPreflight binding) {
        if (!binding.proofBound()) {
            return BLOCKER_PROOF_NOT_BOUND;
        }
        if (!binding.rollbackBound()) {
            return BLOCKER_ROLLBACK_NOT_BOUND;
        }
        if (!binding.approvalBound()) {
            return BLOCKER_APPROVAL_NOT_BOUND;
        }
        if (!binding.optimizedArtifactBuilt()) {
            return BLOCKER_OPTIMIZED_ARTIFACT_NOT_BUILT;
        }
        if (!binding.transformedIrBuilt()) {
            return BLOCKER_TRANSFORMED_IR_NOT_BUILT;
        }
        return BLOCKER_PRODUCTION_GATE_NOT_ACCEPTED;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
