package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Final immutable optimizer selection report after validation sandwich execution.
 */
public record GpuIrOptimizationSandwichReport(
        IrGpuArtifact originalArtifact,
        IrGpuArtifact selectedArtifact,
        Optional<GpuIrOptimizationProposal> proposal,
        GpuIrOptimizationValidationResult originalValidation,
        Optional<GpuIrOptimizationValidationResult> optimizedValidation,
        GpuIrOptimizationSandwichStatus status,
        List<String> diagnostics
) {

    public GpuIrOptimizationSandwichReport {
        originalArtifact = Objects.requireNonNull(originalArtifact, "originalArtifact");
        selectedArtifact = Objects.requireNonNull(selectedArtifact, "selectedArtifact");
        proposal = proposal == null ? Optional.empty() : proposal;
        originalValidation = Objects.requireNonNull(originalValidation, "originalValidation");
        optimizedValidation = optimizedValidation == null ? Optional.empty() : optimizedValidation;
        status = status == null ? GpuIrOptimizationSandwichStatus.NO_CHANGE : status;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean selectedOriginal() {
        return IrGpuArtifactIdentity.stableIdentity(originalArtifact)
                .equals(IrGpuArtifactIdentity.stableIdentity(selectedArtifact));
    }

    public boolean selectedOptimized() {
        return status == GpuIrOptimizationSandwichStatus.OPTIMIZED_SELECTED && !selectedOriginal();
    }

    public boolean requiresRollback() {
        return status == GpuIrOptimizationSandwichStatus.OPTIMIZED_INVALID_ROLLED_BACK;
    }
}
