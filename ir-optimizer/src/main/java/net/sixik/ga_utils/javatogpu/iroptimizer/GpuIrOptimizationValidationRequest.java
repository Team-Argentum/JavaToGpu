package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

import java.util.Objects;
import java.util.Optional;

/**
 * Validation input for one stage of the optimizer validation sandwich.
 */
public record GpuIrOptimizationValidationRequest(
        IrGpuArtifact artifact,
        GpuIrOptimizationValidationStage stage,
        Optional<GpuIrOptimizationProposal> proposal
) {

    public GpuIrOptimizationValidationRequest {
        artifact = Objects.requireNonNull(artifact, "artifact");
        stage = stage == null ? GpuIrOptimizationValidationStage.ORIGINAL_BEFORE : stage;
        proposal = proposal == null ? Optional.empty() : proposal;
    }
}
