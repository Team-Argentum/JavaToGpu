package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.frontend.ir.artifact.IrGpuArtifact;

import java.util.Objects;
import java.util.Optional;

public record GpuRuntimeCompileRequest(
        GpuKernelDescriptor descriptor,
        GpuRuntimeCompileOptions options,
        GpuRuntimeDeviceProfile deviceProfile,
        Optional<IrGpuArtifact> irGpuArtifact
) {

    public GpuRuntimeCompileRequest(
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileOptions options,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        this(descriptor, options, deviceProfile, Optional.empty());
    }

    public GpuRuntimeCompileRequest {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        options = options == null
                ? GpuRuntimeCompileOptions.defaults(deviceProfile == null ? null : deviceProfile.backendTarget())
                : options;
        deviceProfile = deviceProfile == null
                ? GpuRuntimeDeviceProfile.generic(options.backendTarget(), "unknown")
                : deviceProfile;
        irGpuArtifact = irGpuArtifact == null ? Optional.empty() : irGpuArtifact;
    }

    public GpuRuntimeCompileRequest withIrGpuArtifact(Optional<IrGpuArtifact> artifact) {
        return new GpuRuntimeCompileRequest(descriptor, options, deviceProfile, artifact);
    }
}
