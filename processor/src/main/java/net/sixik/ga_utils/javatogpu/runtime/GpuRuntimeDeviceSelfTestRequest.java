package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Immutable input passed to a backend-specific runtime device self-test runner.
 */
public record GpuRuntimeDeviceSelfTestRequest(
        GpuRuntimeDeviceProfile deviceProfile,
        GpuRuntimeDeviceSelfTestIdentity identity
) {

    public GpuRuntimeDeviceSelfTestRequest {
        deviceProfile = java.util.Objects.requireNonNull(deviceProfile, "deviceProfile");
        identity = java.util.Objects.requireNonNull(identity, "identity");
    }
}
