package net.sixik.ga_utils.javatogpu.iroptimizer;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

import java.util.Map;
import java.util.Objects;

/**
 * Backend-neutral immutable input for an IR optimization proposal.
 */
public record GpuIrOptimizationProposalRequest(
        IrGpuArtifact originalArtifact,
        String optimizerProfile,
        boolean mutationAllowed,
        Map<String, String> contextFields
) {

    public GpuIrOptimizationProposalRequest(IrGpuArtifact originalArtifact) {
        this(originalArtifact, "off", false, Map.of());
    }

    public GpuIrOptimizationProposalRequest(
            IrGpuArtifact originalArtifact,
            GpuIrOptimizationPolicy policy,
            Map<String, String> contextFields
    ) {
        this(
                originalArtifact,
                policy == null ? "off" : policy.optimizerProfile(),
                policy != null && policy.mutationAllowed(),
                mergePolicyFields(policy == null ? GpuIrOptimizationPolicy.off() : policy, contextFields)
        );
    }

    public GpuIrOptimizationProposalRequest {
        originalArtifact = Objects.requireNonNull(originalArtifact, "originalArtifact");
        GpuIrOptimizationPolicy policy = GpuIrOptimizationPolicy.fromContext(
                optimizerProfile,
                mutationAllowed,
                contextFields
        );
        optimizerProfile = policy.optimizerProfile();
        mutationAllowed = policy.mutationAllowed();
        contextFields = mergePolicyFields(policy, contextFields);
    }

    public GpuIrOptimizationPolicy policy() {
        return GpuIrOptimizationPolicy.fromContext(optimizerProfile, mutationAllowed, contextFields);
    }

    private static Map<String, String> mergePolicyFields(
            GpuIrOptimizationPolicy policy,
            Map<String, String> contextFields
    ) {
        java.util.LinkedHashMap<String, String> merged = new java.util.LinkedHashMap<>();
        if (contextFields != null) {
            merged.putAll(contextFields);
        }
        merged.putAll(policy.asContextFields());
        return Map.copyOf(merged);
    }
}
