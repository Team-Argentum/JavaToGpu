package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable input for device policy evaluation before runtime context creation.
 */
public record GpuRuntimeDevicePolicyContext(
        Optional<GpuKernelDescriptor> descriptor,
        GpuRuntimeCompileOptions compileOptions,
        List<GpuRuntimeDeviceProfile> candidates
) {

    public GpuRuntimeDevicePolicyContext {
        descriptor = descriptor == null ? Optional.empty() : descriptor;
        compileOptions = compileOptions == null
                ? GpuRuntimeCompileOptions.defaults(null)
                : compileOptions;
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public GpuRuntimeDevicePolicyContext(
            GpuKernelDescriptor descriptor,
            GpuRuntimeCompileOptions compileOptions,
            List<GpuRuntimeDeviceProfile> candidates
    ) {
        this(Optional.ofNullable(descriptor), compileOptions, candidates);
    }

    public static GpuRuntimeDevicePolicyContext fromRequest(GpuRuntimeCompileRequest request) {
        GpuRuntimeCompileRequest value = Objects.requireNonNull(request, "request");
        return new GpuRuntimeDevicePolicyContext(
                Optional.of(value.descriptor()),
                value.options(),
                List.of(value.deviceProfile())
        );
    }

    public static GpuRuntimeDevicePolicyContext forBackendDiscovery(
            GpuRuntimeCompileOptions compileOptions,
            List<GpuRuntimeDeviceProfile> candidates
    ) {
        return new GpuRuntimeDevicePolicyContext(Optional.empty(), compileOptions, candidates);
    }

    public static String deviceKey(GpuRuntimeDeviceProfile profile) {
        GpuRuntimeDeviceProfile value = Objects.requireNonNull(profile, "profile");
        if (!"unknown".equals(value.deviceId())) {
            return value.backendTarget().name() + ':' + sanitize(value.deviceId());
        }
        return value.backendTarget().name()
                + ':' + sanitize(value.vendor())
                + ':' + sanitize(value.deviceLabel())
                + ':' + sanitize(value.driverVersion());
    }

    private static String sanitize(String value) {
        return value == null || value.isBlank()
                ? "unknown"
                : value.trim().replaceAll("\\s+", "_");
    }
}
