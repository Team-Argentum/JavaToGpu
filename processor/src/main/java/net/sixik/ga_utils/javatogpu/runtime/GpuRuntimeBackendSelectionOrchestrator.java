package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;
import net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeBackendDeviceSelectionSupport;
import net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeBackendSelectionSupport;

import java.util.List;
import java.util.Optional;

/**
 * Compatibility facade for backend-neutral runtime selection.
 *
 * <p>New selection-focused code should prefer
 * {@link net.sixik.ga_utils.javatogpu.runtime.selection.GpuRuntimeSelection}. The implementation lives under the
 * {@code runtime.selection} domain package; this class preserves source/binary compatibility for older callers that
 * imported the original root-runtime orchestrator.</p>
 */
public final class GpuRuntimeBackendSelectionOrchestrator {

    private GpuRuntimeBackendSelectionOrchestrator() {
    }

    /**
     * Selects a backend using an immutable policy and returns candidate-level audit evidence.
     */
    public static GpuRuntimeSelectionResult select(GpuRuntimeBackendPolicy policy) {
        return GpuRuntimeBackendSelectionSupport.select(policy);
    }

    /**
     * Selects a backend and attaches a backend-neutral native device discovery catalog.
     */
    public static GpuRuntimeBackendDeviceSelection selectWithDeviceDiscovery(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeDeviceDiscoveryCatalog deviceDiscoveryCatalog
    ) {
        return GpuRuntimeBackendDeviceSelectionSupport.selectWithDeviceDiscovery(policy, deviceDiscoveryCatalog);
    }

    /**
     * Selects a backend, attaches a precomputed device discovery catalog, and publishes optional lifecycle events.
     */
    public static GpuRuntimeBackendDeviceSelection selectWithDeviceDiscovery(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeDeviceDiscoveryCatalog deviceDiscoveryCatalog,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        return GpuRuntimeBackendDeviceSelectionSupport.selectWithDeviceDiscovery(
                policy,
                deviceDiscoveryCatalog,
                lifecycleEventBus
        );
    }

    /**
     * Selects from a backend policy and discovers the standard backend device inventory shape.
     */
    public static GpuRuntimeBackendDeviceSelection selectStandardBackendAndDevice(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeCompileOptions openClDiscoveryOptions
    ) {
        return GpuRuntimeBackendDeviceSelectionSupport.selectStandardBackendAndDevice(policy, openClDiscoveryOptions);
    }

    /**
     * Selects from a backend policy, discovers standard backend devices, and publishes optional lifecycle events.
     */
    public static GpuRuntimeBackendDeviceSelection selectStandardBackendAndDevice(
            GpuRuntimeBackendPolicy policy,
            GpuRuntimeCompileOptions openClDiscoveryOptions,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        return GpuRuntimeBackendDeviceSelectionSupport.selectStandardBackendAndDevice(
                policy,
                openClDiscoveryOptions,
                lifecycleEventBus
        );
    }

    /**
     * Selects a borrowed backend from already-created backend candidates.
     */
    public static GpuRuntimeSelectionResult selectBorrowed(
            List<GpuRuntimeRequirement> requirements,
            GpuRuntimeBackend... candidates
    ) {
        return GpuRuntimeBackendSelectionSupport.selectBorrowed(requirements, candidates);
    }

    /**
     * Selects a backend from factories and records every creation, rejection, closure, and selected candidate.
     */
    public static GpuRuntimeSelectionResult select(
            List<GpuRuntimeRequirement> requirements,
            List<GpuRuntimeBackendFactory> candidateFactories,
            List<GpuRuntimeBackendOwnership> candidateOwnerships
    ) {
        return GpuRuntimeBackendSelectionSupport.select(requirements, candidateFactories, candidateOwnerships);
    }

    /**
     * Selects a backend from factories and records catalog/provider metadata for every candidate when available.
     */
    public static GpuRuntimeSelectionResult select(
            List<GpuRuntimeRequirement> requirements,
            List<GpuRuntimeBackendFactory> candidateFactories,
            List<GpuRuntimeBackendOwnership> candidateOwnerships,
            List<GpuRuntimeBackendCandidateMetadata> candidateMetadata
    ) {
        return GpuRuntimeBackendSelectionSupport.select(
                requirements,
                candidateFactories,
                candidateOwnerships,
                candidateMetadata
        );
    }

    /**
     * Selects a backend from factories and records catalog/provider metadata for every candidate when available.
     */
    public static GpuRuntimeSelectionResult select(
            List<GpuRuntimeRequirement> requirements,
            List<GpuRuntimeBackendRequirement> backendRequirements,
            List<GpuRuntimeBackendFactory> candidateFactories,
            List<GpuRuntimeBackendOwnership> candidateOwnerships,
            List<GpuRuntimeBackendCandidateMetadata> candidateMetadata
    ) {
        return GpuRuntimeBackendSelectionSupport.select(
                requirements,
                backendRequirements,
                candidateFactories,
                candidateOwnerships,
                candidateMetadata
        );
    }

    /**
     * Selects a backend from factories and records catalog/provider metadata for every candidate when available.
     */
    public static GpuRuntimeSelectionResult select(
            List<GpuRuntimeRequirement> requirements,
            List<GpuRuntimeBackendRequirement> backendRequirements,
            List<GpuRuntimeBackendFactory> candidateFactories,
            List<GpuRuntimeBackendOwnership> candidateOwnerships,
            List<GpuRuntimeBackendCandidateMetadata> candidateMetadata,
            GpuRuntimeBackendCandidateOrdering candidateOrdering
    ) {
        return GpuRuntimeBackendSelectionSupport.select(
                requirements,
                backendRequirements,
                candidateFactories,
                candidateOwnerships,
                candidateMetadata,
                candidateOrdering
        );
    }

    /**
     * Selects a backend from factories and records catalog/provider metadata for every candidate when available.
     */
    public static GpuRuntimeSelectionResult select(
            List<GpuRuntimeRequirement> requirements,
            List<GpuRuntimeBackendRequirement> backendRequirements,
            List<GpuRuntimeBackendFactory> candidateFactories,
            List<GpuRuntimeBackendOwnership> candidateOwnerships,
            List<GpuRuntimeBackendCandidateMetadata> candidateMetadata,
            GpuRuntimeBackendCandidateOrdering candidateOrdering,
            List<GpuRuntimeBackendScoreContributor> scoreContributors,
            GpuRuntimeCompileOptions scoreCompileOptions
    ) {
        return GpuRuntimeBackendSelectionSupport.select(
                requirements,
                backendRequirements,
                candidateFactories,
                candidateOwnerships,
                candidateMetadata,
                candidateOrdering,
                scoreContributors,
                scoreCompileOptions
        );
    }

    /**
     * Selects a backend from factories and records catalog/provider metadata for every candidate when available.
     */
    public static GpuRuntimeSelectionResult select(
            List<GpuRuntimeRequirement> requirements,
            List<GpuRuntimeBackendRequirement> backendRequirements,
            List<GpuRuntimeBackendFactory> candidateFactories,
            List<GpuRuntimeBackendOwnership> candidateOwnerships,
            List<GpuRuntimeBackendCandidateMetadata> candidateMetadata,
            GpuRuntimeBackendCandidateOrdering candidateOrdering,
            List<GpuRuntimeBackendScoreContributor> scoreContributors,
            GpuRuntimeCompileOptions scoreCompileOptions,
            Optional<GpuKernelDescriptor> scoreDescriptor,
            Optional<IrGpuArtifact> scoreIrGpuArtifact,
            Optional<GpuRuntimeDeviceProfile> scoreDeviceProfile,
            Optional<GpuBackendCompilerFeedbackReport> scoreCompilerFeedbackReport,
            Optional<GpuRuntimeWorkloadHints> scoreWorkloadHints
    ) {
        return GpuRuntimeBackendSelectionSupport.select(
                requirements,
                backendRequirements,
                candidateFactories,
                candidateOwnerships,
                candidateMetadata,
                candidateOrdering,
                scoreContributors,
                scoreCompileOptions,
                scoreDescriptor,
                scoreIrGpuArtifact,
                scoreDeviceProfile,
                scoreCompilerFeedbackReport,
                scoreWorkloadHints
        );
    }
}
