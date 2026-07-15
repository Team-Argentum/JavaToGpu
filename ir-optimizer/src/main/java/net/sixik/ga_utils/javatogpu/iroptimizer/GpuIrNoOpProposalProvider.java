package net.sixik.ga_utils.javatogpu.iroptimizer;

/**
 * Backend-neutral no-op proposal provider used to pin the initial immutable optimizer contract.
 */
public final class GpuIrNoOpProposalProvider implements GpuIrOptimizationProposalProvider {

    @Override
    public GpuIrOptimizationProposal propose(GpuIrOptimizationProposalRequest request) {
        return GpuIrOptimizationProposal.noChange(
                extensionId(),
                extensionVersion(),
                request.originalArtifact(),
                "no-op proposal provider keeps original IR selected"
        );
    }

    @Override
    public String extensionId() {
        return GpuIrNoOpOptimizationPass.PASS_ID;
    }

    @Override
    public String extensionVersion() {
        return GpuIrNoOpOptimizationPass.PASS_ID + ":" + GpuIrNoOpOptimizationPass.PASS_VERSION;
    }
}
