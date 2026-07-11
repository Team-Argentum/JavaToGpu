package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

/**
 * Backend-neutral input exposed to compiler feedback providers after backend compilation.
 */
public record GpuBackendCompilerFeedbackRequest(
        GpuBackendTarget backendTarget,
        String backendFormat,
        String backendResource,
        String compileLog
) {

    public GpuBackendCompilerFeedbackRequest {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        backendFormat = normalize(backendFormat, "unknown");
        backendResource = backendResource == null ? "" : backendResource;
        compileLog = compileLog == null ? "" : compileLog;
    }

    public static GpuBackendCompilerFeedbackRequest from(GpuRuntimeCompileArtifactSnapshot snapshot) {
        GpuRuntimeCompileArtifactSnapshot value = java.util.Objects.requireNonNull(snapshot, "snapshot");
        GpuBackendModuleArtifact module = value.backendModuleArtifact();
        return new GpuBackendCompilerFeedbackRequest(
                module.backendTarget(),
                module.format(),
                module.resource(),
                value.compileLog()
        );
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
