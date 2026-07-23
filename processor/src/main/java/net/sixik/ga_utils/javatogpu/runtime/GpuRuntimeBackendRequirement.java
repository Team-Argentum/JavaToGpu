package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Requirement that can inspect both the runtime report and catalog/provider metadata for one backend candidate.
 */
@FunctionalInterface
public interface GpuRuntimeBackendRequirement {

    /**
     * Returns {@code null} when the requirement is satisfied or a human-readable failure reason otherwise.
     */
    String failureReason(GpuRuntimeBackendReport report, GpuRuntimeBackendCandidateMetadata metadata);
}
