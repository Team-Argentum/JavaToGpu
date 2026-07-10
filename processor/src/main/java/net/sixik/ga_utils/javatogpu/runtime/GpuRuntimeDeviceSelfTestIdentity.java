package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Objects;

/**
 * Cache identity that invalidates device evidence when hardware, driver, runtime, runner, or compiler changes.
 */
public record GpuRuntimeDeviceSelfTestIdentity(
        String deviceFingerprint,
        GpuBackendTarget backendTarget,
        String deviceId,
        String vendor,
        String deviceLabel,
        String driverVersion,
        String apiVersionText,
        String runnerId,
        String runnerVersion,
        String compilerIdentity
) {

    public static final String COMPILER_IDENTITY_PROPERTY = "javatogpu.compilerIdentity";

    public GpuRuntimeDeviceSelfTestIdentity {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        deviceId = normalize(deviceId, "unknown");
        vendor = normalize(vendor, "unknown");
        deviceLabel = normalize(deviceLabel, "unknown");
        driverVersion = normalize(driverVersion, "unknown");
        apiVersionText = normalize(apiVersionText, "unknown");
        runnerId = normalize(runnerId, "runner:unknown");
        runnerVersion = normalize(runnerVersion, "unknown");
        compilerIdentity = normalize(compilerIdentity, defaultCompilerIdentity());
        deviceFingerprint = normalize(
                deviceFingerprint,
                profileFingerprint(
                        backendTarget,
                        deviceId,
                        vendor,
                        deviceLabel,
                        driverVersion,
                        apiVersionText
                )
        );
    }

    public static GpuRuntimeDeviceSelfTestIdentity from(
            GpuRuntimeDeviceProfile profile,
            GpuRuntimeDeviceSelfTestRunner runner
    ) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(runner, "runner");
        return new GpuRuntimeDeviceSelfTestIdentity(
                profileFingerprint(profile),
                profile.backendTarget(),
                profile.deviceId(),
                profile.vendor(),
                profile.deviceLabel(),
                profile.driverVersion(),
                profile.apiVersionText(),
                runner.runnerId(),
                runner.runnerVersion(),
                defaultCompilerIdentity()
        );
    }

    public boolean matches(GpuRuntimeDeviceProfile profile) {
        return profile != null && deviceFingerprint.equals(profileFingerprint(profile));
    }

    public String stableKey() {
        return deviceFingerprint
                + "|runner=" + runnerId
                + '@' + runnerVersion
                + "|compiler=" + compilerIdentity;
    }

    public static String profileFingerprint(GpuRuntimeDeviceProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return profileFingerprint(
                profile.backendTarget(),
                profile.deviceId(),
                profile.vendor(),
                profile.deviceLabel(),
                profile.driverVersion(),
                profile.apiVersionText()
        )
                + ':' + profile.deviceClass().name()
                + ':' + profile.unifiedMemory();
    }

    private static String profileFingerprint(
            GpuBackendTarget backendTarget,
            String deviceId,
            String vendor,
            String deviceLabel,
            String driverVersion,
            String apiVersionText
    ) {
        return (backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget).name()
                + ':' + normalize(deviceId, "unknown")
                + ':' + normalize(vendor, "unknown")
                + ':' + normalize(deviceLabel, "unknown")
                + ':' + normalize(driverVersion, "unknown")
                + ':' + normalize(apiVersionText, "unknown");
    }

    private static String defaultCompilerIdentity() {
        String explicit = System.getProperty(COMPILER_IDENTITY_PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        Package runtimePackage = GpuRuntimeDeviceSelfTestIdentity.class.getPackage();
        String implementationVersion = runtimePackage == null ? null : runtimePackage.getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank()
                ? "JavaToGpu-development"
                : "JavaToGpu-" + implementationVersion.trim();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
