package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeIrOptimizationProofArtifact;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable optimizer proposal: original IR plus an optional optimized replacement and proof metadata.
 */
public record GpuIrOptimizationProposal(
        String optimizerId,
        String optimizerVersion,
        IrGpuArtifact originalArtifact,
        Optional<IrGpuArtifact> optimizedArtifact,
        GpuIrOptimizationProposalDecision decision,
        GpuRuntimeIrOptimizationProofArtifact proofArtifact,
        String rollbackReason,
        List<String> diagnostics
) {

    public GpuIrOptimizationProposal {
        optimizerId = normalize(optimizerId, "javatogpu.ir-optimizer.unknown");
        optimizerVersion = normalize(optimizerVersion, optimizerId + ":unknown");
        originalArtifact = Objects.requireNonNull(originalArtifact, "originalArtifact");
        optimizedArtifact = optimizedArtifact == null ? Optional.empty() : optimizedArtifact;
        decision = decision == null ? GpuIrOptimizationProposalDecision.NO_CHANGE : decision;
        proofArtifact = proofArtifact == null
                ? GpuRuntimeIrOptimizationProofArtifact.fromFields("ir-optimizer", "not-proven", Map.of())
                : proofArtifact;
        rollbackReason = rollbackReason == null ? "" : rollbackReason;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        if (decision == GpuIrOptimizationProposalDecision.PROPOSED && optimizedArtifact.isEmpty()) {
            throw new IllegalArgumentException("PROPOSED optimizer decisions must include an optimized artifact");
        }
        if (decision == GpuIrOptimizationProposalDecision.PROPOSED) {
            IrGpuArtifact optimized = optimizedArtifact.orElseThrow();
            if (optimized == originalArtifact) {
                throw new IllegalArgumentException("PROPOSED optimizer decisions must return a distinct optimized artifact instance");
            }
            if (IrGpuArtifactIdentity.stableIdentity(originalArtifact)
                    .equals(IrGpuArtifactIdentity.stableIdentity(optimized))) {
                throw new IllegalArgumentException("PROPOSED optimizer decisions must change the optimized artifact identity");
            }
        }
    }

    public static GpuIrOptimizationProposal noChange(
            String optimizerId,
            String optimizerVersion,
            IrGpuArtifact originalArtifact,
            String diagnostic
    ) {
        return new GpuIrOptimizationProposal(
                optimizerId,
                optimizerVersion,
                originalArtifact,
                Optional.empty(),
                GpuIrOptimizationProposalDecision.NO_CHANGE,
                GpuRuntimeIrOptimizationProofArtifact.fromFields("ir-optimizer", "not-mutating", Map.of()),
                "",
                diagnostic == null || diagnostic.isBlank() ? List.of() : List.of(diagnostic)
        );
    }

    public static GpuIrOptimizationProposal proposed(
            String optimizerId,
            String optimizerVersion,
            IrGpuArtifact originalArtifact,
            IrGpuArtifact optimizedArtifact,
            GpuRuntimeIrOptimizationProofArtifact proofArtifact,
            List<String> diagnostics
    ) {
        return new GpuIrOptimizationProposal(
                optimizerId,
                optimizerVersion,
                originalArtifact,
                Optional.of(Objects.requireNonNull(optimizedArtifact, "optimizedArtifact")),
                GpuIrOptimizationProposalDecision.PROPOSED,
                proofArtifact,
                "",
                diagnostics
        );
    }

    public static GpuIrOptimizationProposal rejected(
            String optimizerId,
            String optimizerVersion,
            IrGpuArtifact originalArtifact,
            String reason,
            List<String> diagnostics
    ) {
        return new GpuIrOptimizationProposal(
                optimizerId,
                optimizerVersion,
                originalArtifact,
                Optional.empty(),
                GpuIrOptimizationProposalDecision.REJECTED,
                GpuRuntimeIrOptimizationProofArtifact.fromFields("ir-optimizer", "rejected", Map.of()),
                reason,
                diagnostics
        );
    }

    public String originalIdentity() {
        return IrGpuArtifactIdentity.stableIdentity(originalArtifact);
    }

    public String optimizedIdentity() {
        return IrGpuArtifactIdentity.stableIdentity(optimizedArtifact);
    }

    public boolean hasOptimizedArtifact() {
        return optimizedArtifact.isPresent();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
