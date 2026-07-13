package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;

import java.util.Objects;

/**
 * Builds optimized-artifact candidate envelopes without applying runtime IR selection.
 */
public final class GpuIrOptimizedArtifactCandidateBuilder {

    private static final GpuIrOptimizedArtifactCandidateBuilder FAIL_CLOSED =
            new GpuIrOptimizedArtifactCandidateBuilder();

    private GpuIrOptimizedArtifactCandidateBuilder() {
    }

    public static GpuIrOptimizedArtifactCandidateBuilder failClosed() {
        return FAIL_CLOSED;
    }

    public GpuIrOptimizedArtifactCandidate build(
            GpuIrOptimizationProposalRequest request,
            GpuIrOptimizationProposal proposal,
            GpuIrOptimizationValidationResult optimizedValidation
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(proposal, "proposal");
        if (!proposal.hasOptimizedArtifact()) {
            throw new IllegalArgumentException("optimized artifact candidates require a proposed optimized artifact");
        }
        IrGpuArtifact originalArtifact = proposal.originalArtifact();
        IrGpuArtifact optimizedArtifact = proposal.optimizedArtifact().orElseThrow();
        GpuIrOptimizationValidationResult validation = optimizedValidation == null
                ? GpuIrOptimizationValidationResult.invalid(
                GpuIrOptimizationValidationStage.OPTIMIZED_AFTER,
                "optimized-validation-missing",
                "optimized validation result was not recorded"
        )
                : optimizedValidation;
        boolean validationPassed = validation.valid();
        boolean mutationAllowed = request.policy().mutationAllowed();
        String requestOriginalIdentity = IrGpuArtifactIdentity.stableIdentity(request.originalArtifact());
        String originalIdentity = IrGpuArtifactIdentity.stableIdentity(originalArtifact);
        if (!requestOriginalIdentity.equals(originalIdentity)) {
            throw new IllegalArgumentException("optimized artifact candidate proposal original does not match request original artifact");
        }
        String optimizedIdentity = IrGpuArtifactIdentity.stableIdentity(optimizedArtifact);
        return new GpuIrOptimizedArtifactCandidate(
                originalArtifact,
                optimizedArtifact,
                proposal,
                validation,
                originalIdentity,
                optimizedIdentity,
                "",
                true,
                validationPassed,
                proposal.proofArtifact() != null,
                request.policy().rollbackRequired(),
                mutationAllowed,
                false,
                false,
                false,
                validationPassed ? "candidate-ready" : "blocked",
                validationPassed ? "none" : validation.verdict(),
                selectionFirstBlocker(validationPassed, mutationAllowed)
        );
    }

    private static String selectionFirstBlocker(boolean validationPassed, boolean mutationAllowed) {
        if (!validationPassed) {
            return GpuIrOptimizedArtifactCandidate.BLOCKER_OPTIMIZED_VALIDATION_FAILED;
        }
        if (!mutationAllowed) {
            return GpuIrOptimizedArtifactCandidate.BLOCKER_MUTATION_DISABLED;
        }
        return GpuIrOptimizedArtifactCandidate.BLOCKER_SELECTION_GATE_NOT_BOUND;
    }
}
