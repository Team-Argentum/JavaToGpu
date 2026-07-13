package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable optimized-artifact candidate envelope produced before runtime IR selection.
 *
 * <p>The candidate records the optimized artifact and validation/proof metadata, but intentionally does not apply
 * selection. Runtime-side selection remains a separate fail-closed gate.</p>
 */
public record GpuIrOptimizedArtifactCandidate(
        IrGpuArtifact originalArtifact,
        IrGpuArtifact optimizedArtifact,
        GpuIrOptimizationProposal proposal,
        GpuIrOptimizationValidationResult optimizedValidation,
        String originalIrIdentity,
        String optimizedIrIdentity,
        String candidateEnvelopeKey,
        boolean candidateBuilt,
        boolean optimizedValidationPassed,
        boolean proofPresent,
        boolean rollbackRequired,
        boolean mutationAllowed,
        boolean selectionReady,
        boolean selectionApplied,
        boolean selectedIrReplacement,
        String status,
        String firstBlocker,
        String selectionFirstBlocker
) {

    public static final String DEFAULT_FIELD_PREFIX = "optimizedArtifactCandidate";
    public static final String STATUS_CANDIDATE_READY = "candidate-ready";
    public static final String STATUS_BLOCKED = "blocked";
    public static final String BLOCKER_NONE = "none";
    public static final String BLOCKER_MUTATION_DISABLED = "mutation-disabled";
    public static final String BLOCKER_SELECTION_GATE_NOT_BOUND = "selection-gate-not-bound";
    public static final String BLOCKER_OPTIMIZED_VALIDATION_FAILED = "optimized-validation-failed";

    public GpuIrOptimizedArtifactCandidate {
        originalArtifact = Objects.requireNonNull(originalArtifact, "originalArtifact");
        optimizedArtifact = Objects.requireNonNull(optimizedArtifact, "optimizedArtifact");
        proposal = Objects.requireNonNull(proposal, "proposal");
        optimizedValidation = optimizedValidation == null
                ? GpuIrOptimizationValidationResult.invalid(
                GpuIrOptimizationValidationStage.OPTIMIZED_AFTER,
                "optimized-validation-missing",
                "optimized validation result was not recorded"
        )
                : optimizedValidation;
        originalIrIdentity = IrGpuArtifactIdentity.stableIdentity(originalArtifact);
        optimizedIrIdentity = IrGpuArtifactIdentity.stableIdentity(optimizedArtifact);
        candidateEnvelopeKey = normalize(candidateEnvelopeKey, envelopeKey(
                proposal.optimizerId(),
                proposal.optimizerVersion(),
                originalIrIdentity,
                optimizedIrIdentity,
                optimizedValidation.verdict()
        ));
        candidateBuilt = proposal.hasOptimizedArtifact();
        optimizedValidationPassed = optimizedValidation.valid();
        proofPresent = proposal.proofArtifact() != null;
        status = optimizedValidationPassed ? STATUS_CANDIDATE_READY : STATUS_BLOCKED;
        firstBlocker = optimizedValidationPassed
                ? BLOCKER_NONE
                : normalize(optimizedValidation.verdict(), BLOCKER_OPTIMIZED_VALIDATION_FAILED);
        selectionFirstBlocker = defaultSelectionFirstBlocker(optimizedValidationPassed, mutationAllowed);
        selectionReady = false;
        selectionApplied = false;
        selectedIrReplacement = false;
    }

    public Map<String, String> fields(String prefix) {
        String normalizedPrefix = normalize(prefix, DEFAULT_FIELD_PREFIX);
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".optimizerId", proposal.optimizerId());
        fields.put(normalizedPrefix + ".optimizerVersion", proposal.optimizerVersion());
        fields.put(normalizedPrefix + ".originalIrIdentity", originalIrIdentity);
        fields.put(normalizedPrefix + ".optimizedIrIdentity", optimizedIrIdentity);
        fields.put(normalizedPrefix + ".candidateEnvelopeKey", candidateEnvelopeKey);
        fields.put(normalizedPrefix + ".candidateBuilt", Boolean.toString(candidateBuilt));
        fields.put(normalizedPrefix + ".optimizedValidationPassed", Boolean.toString(optimizedValidationPassed));
        fields.put(normalizedPrefix + ".optimizedValidationVerdict", optimizedValidation.verdict());
        fields.put(normalizedPrefix + ".proofPresent", Boolean.toString(proofPresent));
        fields.put(normalizedPrefix + ".rollbackRequired", Boolean.toString(rollbackRequired));
        fields.put(normalizedPrefix + ".mutationAllowed", Boolean.toString(mutationAllowed));
        fields.put(normalizedPrefix + ".selectionReady", Boolean.toString(selectionReady));
        fields.put(normalizedPrefix + ".selectionApplied", Boolean.toString(selectionApplied));
        fields.put(normalizedPrefix + ".selectedIrReplacement", Boolean.toString(selectedIrReplacement));
        fields.put(normalizedPrefix + ".status", status);
        fields.put(normalizedPrefix + ".firstBlocker", firstBlocker);
        fields.put(normalizedPrefix + ".selectionFirstBlocker", selectionFirstBlocker);
        return Collections.unmodifiableMap(fields);
    }

    private static String envelopeKey(
            String optimizerId,
            String optimizerVersion,
            String originalIrIdentity,
            String optimizedIrIdentity,
            String validationVerdict
    ) {
        return String.join(
                "|",
                normalize(optimizerId, "optimizer:unknown"),
                normalize(optimizerVersion, "optimizer:unknown:version"),
                "original=" + normalize(originalIrIdentity, "irgpu:unknown"),
                "optimized=" + normalize(optimizedIrIdentity, "irgpu:unknown"),
                "validation=" + normalize(validationVerdict, "unknown")
        );
    }

    private static String defaultSelectionFirstBlocker(boolean optimizedValidationPassed, boolean mutationAllowed) {
        if (!optimizedValidationPassed) {
            return BLOCKER_OPTIMIZED_VALIDATION_FAILED;
        }
        if (!mutationAllowed) {
            return BLOCKER_MUTATION_DISABLED;
        }
        return BLOCKER_SELECTION_GATE_NOT_BOUND;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
