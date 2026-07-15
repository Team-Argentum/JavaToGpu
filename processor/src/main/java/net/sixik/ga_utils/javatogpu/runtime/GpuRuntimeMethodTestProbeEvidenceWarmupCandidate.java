package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Objects;

/**
 * One explicit backend/device pair to use while warming {@code @GPUTest} GPU-probe evidence.
 */
public record GpuRuntimeMethodTestProbeEvidenceWarmupCandidate(
        GpuRuntimeDeviceProfile deviceProfile,
        GpuRuntimeBackendFactory backendFactory,
        GpuRuntimeBackendOwnership ownership
) {

    public GpuRuntimeMethodTestProbeEvidenceWarmupCandidate {
        deviceProfile = Objects.requireNonNull(deviceProfile, "deviceProfile");
        backendFactory = Objects.requireNonNull(backendFactory, "backendFactory");
        ownership = ownership == null ? GpuRuntimeBackendOwnership.BORROWED : ownership;
    }

    public static GpuRuntimeMethodTestProbeEvidenceWarmupCandidate borrowed(
            GpuRuntimeDeviceProfile deviceProfile,
            GpuRuntimeBackend backend
    ) {
        Objects.requireNonNull(backend, "backend");
        return new GpuRuntimeMethodTestProbeEvidenceWarmupCandidate(
                deviceProfile,
                () -> backend,
                GpuRuntimeBackendOwnership.BORROWED
        );
    }

    public static GpuRuntimeMethodTestProbeEvidenceWarmupCandidate borrowed(
            GpuRuntimeDeviceProfile deviceProfile,
            GpuRuntimeBackendFactory backendFactory
    ) {
        return new GpuRuntimeMethodTestProbeEvidenceWarmupCandidate(
                deviceProfile,
                backendFactory,
                GpuRuntimeBackendOwnership.BORROWED
        );
    }

    public static GpuRuntimeMethodTestProbeEvidenceWarmupCandidate owned(
            GpuRuntimeDeviceProfile deviceProfile,
            GpuRuntimeBackendFactory backendFactory
    ) {
        return new GpuRuntimeMethodTestProbeEvidenceWarmupCandidate(
                deviceProfile,
                backendFactory,
                GpuRuntimeBackendOwnership.OWNED
        );
    }
}
