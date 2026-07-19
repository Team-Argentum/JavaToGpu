package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable input passed to backend score contributors for one candidate.
 */
public record GpuRuntimeBackendScoreContext(
        int candidateIndex,
        GpuRuntimeBackendReport report,
        GpuRuntimeBackendCandidateMetadata metadata,
        GpuRuntimeCompileOptions compileOptions,
        Optional<GpuKernelDescriptor> descriptor,
        Optional<IrGpuArtifact> irGpuArtifact,
        Optional<GpuRuntimeDeviceProfile> deviceProfile,
        Optional<GpuBackendCompilerFeedbackReport> compilerFeedbackReport
) {

    public GpuRuntimeBackendScoreContext(
            int candidateIndex,
            GpuRuntimeBackendReport report,
            GpuRuntimeBackendCandidateMetadata metadata,
            GpuRuntimeCompileOptions compileOptions
    ) {
        this(
                candidateIndex,
                report,
                metadata,
                compileOptions,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public GpuRuntimeBackendScoreContext {
        report = Objects.requireNonNull(report, "report");
        metadata = metadata == null ? GpuRuntimeBackendCandidateMetadata.unknown() : metadata;
        compileOptions = compileOptions == null
                ? GpuRuntimeCompileOptions.defaults(report.backendTarget())
                : compileOptions;
        descriptor = descriptor == null ? Optional.empty() : descriptor;
        irGpuArtifact = irGpuArtifact == null ? Optional.empty() : irGpuArtifact;
        deviceProfile = deviceProfile == null ? Optional.empty() : deviceProfile;
        compilerFeedbackReport = compilerFeedbackReport == null ? Optional.empty() : compilerFeedbackReport;
    }
}
