package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifactIdentity;
import net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuRuntimeCompileInvalidationStampSupport;

import java.util.Objects;

public record GpuRuntimeCompileCacheKey(
        GpuKernelDescriptor descriptor,
        GpuBackendModuleArtifact moduleArtifact,
        String irGpuArtifactIdentity,
        String backendArtifactVersion,
        String backendLowererVersion,
        GpuRuntimeCompileInvalidationStamp invalidationStamp,
        GpuRuntimeCompileOptions options,
        GpuRuntimeDeviceProfile deviceProfile
) {

    public GpuRuntimeCompileCacheKey {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        moduleArtifact = moduleArtifact == null
                ? GpuBackendModuleArtifact.openClSource(descriptor.kernelSource(), descriptor.kernelResource(), "legacy-opencl-source")
                : moduleArtifact;
        irGpuArtifactIdentity = irGpuArtifactIdentity == null || irGpuArtifactIdentity.isBlank()
                ? IrGpuArtifactIdentity.MISSING_ARTIFACT_IDENTITY
                : irGpuArtifactIdentity;
        backendArtifactVersion = backendArtifactVersion == null || backendArtifactVersion.isBlank()
                ? moduleArtifact.artifactVersion()
                : backendArtifactVersion;
        backendLowererVersion = backendLowererVersion == null || backendLowererVersion.isBlank()
                ? moduleArtifact.lowererVersion()
                : backendLowererVersion;
        invalidationStamp = invalidationStamp == null
                ? GpuRuntimeCompileInvalidationStampSupport.from(null, moduleArtifact, null)
                : invalidationStamp;
        options = options == null
                ? GpuRuntimeCompileOptions.defaults(deviceProfile == null ? null : deviceProfile.backendTarget())
                : options;
        deviceProfile = deviceProfile == null
                ? GpuRuntimeDeviceProfile.generic(options.backendTarget(), "unknown")
                : deviceProfile;
    }

    public static GpuRuntimeCompileCacheKey from(
            GpuRuntimeCompileRequest request,
            GpuBackendModuleArtifact moduleArtifact
    ) {
        return from(request, moduleArtifact, GpuRuntimeCompileInvalidationStampSupport.from(request, moduleArtifact, null));
    }

    public static GpuRuntimeCompileCacheKey from(
            GpuRuntimeCompileRequest request,
            GpuBackendModuleArtifact moduleArtifact,
            GpuRuntimeCompileInvalidationStamp invalidationStamp
    ) {
        return new GpuRuntimeCompileCacheKey(
                request.descriptor(),
                moduleArtifact,
                IrGpuArtifactIdentity.stableIdentity(request.irGpuArtifact()),
                moduleArtifact == null ? null : moduleArtifact.artifactVersion(),
                moduleArtifact == null ? null : moduleArtifact.lowererVersion(),
                invalidationStamp,
                request.options(),
                request.deviceProfile()
        );
    }
}
