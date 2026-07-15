package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Executes the fail-closed validation sandwich around one immutable optimizer proposal.
 */
public final class GpuIrOptimizationSandwichRunner {

    private final GpuIrOptimizationValidationGate validationGate;
    private final GpuIrOptimizedArtifactCandidateBuilder candidateBuilder;

    public GpuIrOptimizationSandwichRunner(GpuIrOptimizationValidationGate validationGate) {
        this.validationGate = validationGate == null ? GpuIrOptimizationValidationGate.alwaysValid() : validationGate;
        this.candidateBuilder = GpuIrOptimizedArtifactCandidateBuilder.failClosed();
    }

    public static GpuIrOptimizationSandwichRunner alwaysValid() {
        return new GpuIrOptimizationSandwichRunner(GpuIrOptimizationValidationGate.alwaysValid());
    }

    public GpuIrOptimizationSandwichReport run(
            GpuIrOptimizationProposalRequest request,
            GpuIrOptimizationProposalProvider provider
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(provider, "provider");
        IrGpuArtifact originalArtifact = request.originalArtifact();
        ArrayList<String> diagnostics = new ArrayList<>();

        GpuIrOptimizationValidationResult originalValidation = validationGate.validate(
                new GpuIrOptimizationValidationRequest(
                        originalArtifact,
                        GpuIrOptimizationValidationStage.ORIGINAL_BEFORE,
                        Optional.empty()
                )
        );
        diagnostics.addAll(originalValidation.diagnostics());
        if (!originalValidation.valid()) {
            return report(
                    originalArtifact,
                    originalArtifact,
                    Optional.empty(),
                    originalValidation,
                    Optional.empty(),
                    GpuIrOptimizationSandwichStatus.ORIGINAL_INVALID,
                    diagnostics
            );
        }

        GpuIrOptimizationProposal proposal;
        try {
            proposal = Objects.requireNonNull(provider.propose(request), "proposal");
        } catch (RuntimeException exception) {
            diagnostics.add(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            return report(
                    originalArtifact,
                    originalArtifact,
                    Optional.empty(),
                    originalValidation,
                    Optional.empty(),
                    GpuIrOptimizationSandwichStatus.PROPOSAL_REJECTED,
                    diagnostics
            );
        }

        diagnostics.addAll(proposal.diagnostics());
        if (proposal.decision() == GpuIrOptimizationProposalDecision.REJECTED) {
            return report(
                    originalArtifact,
                    originalArtifact,
                    Optional.of(proposal),
                    originalValidation,
                    Optional.empty(),
                    GpuIrOptimizationSandwichStatus.PROPOSAL_REJECTED,
                    diagnostics
            );
        }
        if (!proposal.hasOptimizedArtifact()) {
            return report(
                    originalArtifact,
                    originalArtifact,
                    Optional.of(proposal),
                    originalValidation,
                    Optional.empty(),
                    GpuIrOptimizationSandwichStatus.NO_CHANGE,
                    diagnostics
            );
        }

        IrGpuArtifact optimizedArtifact = proposal.optimizedArtifact().orElseThrow();
        GpuIrOptimizationValidationResult optimizedValidation = validationGate.validate(
                new GpuIrOptimizationValidationRequest(
                        optimizedArtifact,
                        GpuIrOptimizationValidationStage.OPTIMIZED_AFTER,
                        Optional.of(proposal)
                )
        );
        GpuIrOptimizedArtifactCandidate candidate = candidateBuilder.build(request, proposal, optimizedValidation);
        diagnostics.addAll(optimizedValidation.diagnostics());
        if (!optimizedValidation.valid()) {
            return report(
                    originalArtifact,
                    originalArtifact,
                    Optional.of(proposal),
                    Optional.of(candidate),
                    originalValidation,
                    Optional.of(optimizedValidation),
                    GpuIrOptimizationSandwichStatus.OPTIMIZED_INVALID_ROLLED_BACK,
                    diagnostics
            );
        }
        if (!request.mutationAllowed()) {
            diagnostics.add("optimized artifact validated but mutation is disabled; original IR remains selected");
            return report(
                    originalArtifact,
                    originalArtifact,
                    Optional.of(proposal),
                    Optional.of(candidate),
                    originalValidation,
                    Optional.of(optimizedValidation),
                    GpuIrOptimizationSandwichStatus.PROPOSAL_ONLY,
                    diagnostics
            );
        }

        return report(
                originalArtifact,
                optimizedArtifact,
                Optional.of(proposal),
                Optional.of(candidate),
                originalValidation,
                Optional.of(optimizedValidation),
                GpuIrOptimizationSandwichStatus.OPTIMIZED_SELECTED,
                diagnostics
        );
    }

    private static GpuIrOptimizationSandwichReport report(
            IrGpuArtifact originalArtifact,
            IrGpuArtifact selectedArtifact,
            Optional<GpuIrOptimizationProposal> proposal,
            GpuIrOptimizationValidationResult originalValidation,
            Optional<GpuIrOptimizationValidationResult> optimizedValidation,
            GpuIrOptimizationSandwichStatus status,
            List<String> diagnostics
    ) {
        return report(
                originalArtifact,
                selectedArtifact,
                proposal,
                Optional.empty(),
                originalValidation,
                optimizedValidation,
                status,
                diagnostics
        );
    }

    private static GpuIrOptimizationSandwichReport report(
            IrGpuArtifact originalArtifact,
            IrGpuArtifact selectedArtifact,
            Optional<GpuIrOptimizationProposal> proposal,
            Optional<GpuIrOptimizedArtifactCandidate> candidate,
            GpuIrOptimizationValidationResult originalValidation,
            Optional<GpuIrOptimizationValidationResult> optimizedValidation,
            GpuIrOptimizationSandwichStatus status,
            List<String> diagnostics
    ) {
        return new GpuIrOptimizationSandwichReport(
                originalArtifact,
                selectedArtifact,
                proposal,
                candidate,
                originalValidation,
                optimizedValidation,
                status,
                diagnostics
        );
    }
}
