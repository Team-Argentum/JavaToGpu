package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stable identity for one materialized {@code @GPUTest} GPU probe evidence entry.
 */
public record GpuRuntimeMethodTestGpuProbeEvidenceKey(
        String formatVersion,
        String testId,
        String kernelName,
        String kernelResource,
        String irGpuResource,
        String kernelSourceSha256,
        String fixtureInputFingerprint,
        String expectedOutputFingerprint,
        String executionConfigFingerprint,
        GpuBackendTarget backendTarget,
        String backendName,
        String deviceId,
        String deviceLabel,
        String vendor,
        String driverVersion,
        String apiVersionText,
        String optimizationProfile,
        String compileOptionsFingerprint,
        String compilerIdentity
) {

    public static final String FORMAT_VERSION = "javatogpu.method-test-gpu-probe-evidence-key.v1";

    public GpuRuntimeMethodTestGpuProbeEvidenceKey {
        formatVersion = normalize(formatVersion, FORMAT_VERSION);
        testId = normalize(testId, "unknown");
        kernelName = normalize(kernelName, "unknown");
        kernelResource = normalize(kernelResource, "");
        irGpuResource = normalize(irGpuResource, "");
        kernelSourceSha256 = normalize(kernelSourceSha256, "none");
        fixtureInputFingerprint = normalize(fixtureInputFingerprint, "none");
        expectedOutputFingerprint = normalize(expectedOutputFingerprint, "none");
        executionConfigFingerprint = normalize(executionConfigFingerprint, "none");
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        backendName = normalize(backendName, "unknown");
        deviceId = normalize(deviceId, "unknown");
        deviceLabel = normalize(deviceLabel, "unknown");
        vendor = normalize(vendor, "unknown");
        driverVersion = normalize(driverVersion, "unknown");
        apiVersionText = normalize(apiVersionText, "unknown");
        optimizationProfile = normalize(optimizationProfile, "off");
        compileOptionsFingerprint = normalize(compileOptionsFingerprint, "none");
        compilerIdentity = normalize(compilerIdentity, defaultCompilerIdentity());
    }

    public static GpuRuntimeMethodTestGpuProbeEvidenceKey from(
            GpuKernelDescriptor descriptor,
            GpuRuntimeMethodTestInvocationMaterialization invocation,
            GpuExecutionConfig executionConfig,
            GpuRuntimeCompileOptions compileOptions,
            GpuRuntimeBackendReport backendReport,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        GpuRuntimeCompileOptions resolvedCompileOptions = compileOptions == null
                ? GpuRuntimeCompileOptions.defaults(backendTarget(backendReport, deviceProfile))
                : compileOptions;
        GpuRuntimeBackendReport resolvedReport = backendReport == null
                ? GpuRuntimeBackendReport.unavailable(
                resolvedCompileOptions.backendTarget(),
                "unknown",
                "backend report not available"
        )
                : backendReport;
        GpuRuntimeDeviceProfile resolvedDevice = deviceProfile == null
                ? deviceProfileFromReport(resolvedReport)
                : deviceProfile;

        return new GpuRuntimeMethodTestGpuProbeEvidenceKey(
                FORMAT_VERSION,
                invocation == null ? "unknown" : invocation.testId(),
                descriptor == null ? "unknown" : descriptor.kernelName(),
                descriptor == null ? "" : descriptor.kernelResource(),
                descriptor == null ? "" : descriptor.irGpuResource(),
                sha256(descriptor == null ? "" : descriptor.kernelSource()),
                fixtureInputFingerprint(invocation),
                expectedOutputFingerprint(invocation),
                executionConfigFingerprint(executionConfig),
                resolvedDevice.backendTarget(),
                resolvedDevice.backendName(),
                resolvedDevice.deviceId(),
                resolvedDevice.deviceLabel(),
                resolvedDevice.vendor(),
                resolvedDevice.driverVersion(),
                resolvedDevice.apiVersionText(),
                resolvedCompileOptions.optimizationProfile(),
                compileOptionsFingerprint(resolvedCompileOptions),
                defaultCompilerIdentity()
        );
    }

    public String stableKey() {
        return String.join("|",
                formatVersion,
                "test=" + testId,
                "kernel=" + kernelName,
                "resource=" + kernelResource,
                "irgpu=" + irGpuResource,
                "kernelSha256=" + kernelSourceSha256,
                "inputs=" + fixtureInputFingerprint,
                "expected=" + expectedOutputFingerprint,
                "launch=" + executionConfigFingerprint,
                "backend=" + backendTarget.name(),
                "backendName=" + backendName,
                "deviceId=" + deviceId,
                "device=" + deviceLabel,
                "vendor=" + vendor,
                "driver=" + driverVersion,
                "api=" + apiVersionText,
                "profile=" + optimizationProfile,
                "options=" + compileOptionsFingerprint,
                "compiler=" + compilerIdentity
        );
    }

    public String stableHash() {
        return sha256(stableKey());
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "methodTestGpuProbeEvidenceKey" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".formatVersion", formatVersion);
        fields.put(normalizedPrefix + ".stableHash", stableHash());
        fields.put(normalizedPrefix + ".testId", testId);
        fields.put(normalizedPrefix + ".kernelName", kernelName);
        fields.put(normalizedPrefix + ".kernelResource", kernelResource);
        fields.put(normalizedPrefix + ".irGpuResource", irGpuResource);
        fields.put(normalizedPrefix + ".kernelSource.sha256", kernelSourceSha256);
        fields.put(normalizedPrefix + ".fixtureInput.fingerprint", fixtureInputFingerprint);
        fields.put(normalizedPrefix + ".expectedOutput.fingerprint", expectedOutputFingerprint);
        fields.put(normalizedPrefix + ".executionConfig.fingerprint", executionConfigFingerprint);
        fields.put(normalizedPrefix + ".backendTarget", backendTarget.name());
        fields.put(normalizedPrefix + ".backendName", backendName);
        fields.put(normalizedPrefix + ".deviceId", deviceId);
        fields.put(normalizedPrefix + ".deviceLabel", deviceLabel);
        fields.put(normalizedPrefix + ".vendor", vendor);
        fields.put(normalizedPrefix + ".driverVersion", driverVersion);
        fields.put(normalizedPrefix + ".apiVersionText", apiVersionText);
        fields.put(normalizedPrefix + ".optimizationProfile", optimizationProfile);
        fields.put(normalizedPrefix + ".compileOptions.fingerprint", compileOptionsFingerprint);
        fields.put(normalizedPrefix + ".compilerIdentity", compilerIdentity);
        return Collections.unmodifiableMap(fields);
    }

    private static GpuBackendTarget backendTarget(
            GpuRuntimeBackendReport backendReport,
            GpuRuntimeDeviceProfile deviceProfile
    ) {
        if (deviceProfile != null) {
            return deviceProfile.backendTarget();
        }
        return backendReport == null ? GpuBackendTarget.UNKNOWN : backendReport.backendTarget();
    }

    private static GpuRuntimeDeviceProfile deviceProfileFromReport(GpuRuntimeBackendReport report) {
        return new GpuRuntimeDeviceProfile(
                report.backendTarget(),
                report.backendName(),
                normalize(report.deviceLabel(), "unknown"),
                "unknown",
                "unknown",
                normalize(report.apiVersionText(), "unknown"),
                -1L,
                report.localMemoryBytes() == null ? -1L : report.localMemoryBytes(),
                report.maxWorkGroupSize() == null ? -1L : report.maxWorkGroupSize(),
                -1L,
                report.supports(GpuRuntimeFeature.DOUBLE_PRECISION),
                report.supports(GpuRuntimeFeature.IMAGES),
                false
        );
    }

    private static String fixtureInputFingerprint(GpuRuntimeMethodTestInvocationMaterialization invocation) {
        if (invocation == null) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (GpuRuntimeMethodTestInvocationArgument argument : invocation.arguments()) {
            if (argument.argumentReady()) {
                builder.append(argument.parameterIndex())
                        .append(':')
                        .append(argument.parameterName())
                        .append(':')
                        .append(argument.argumentKind())
                        .append(':')
                        .append(argument.itemCount())
                        .append(':')
                        .append(String.join(",", argument.argumentNumericValues()))
                        .append('\n');
            }
        }
        return sha256(builder.toString());
    }

    private static String expectedOutputFingerprint(GpuRuntimeMethodTestInvocationMaterialization invocation) {
        if (invocation == null) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (GpuRuntimeMethodTestInvocationArgument argument : invocation.arguments()) {
            if (argument.expectedOutputReady()) {
                builder.append(argument.parameterIndex())
                        .append(':')
                        .append(argument.parameterName())
                        .append(':')
                        .append(argument.expectedOutputKind())
                        .append(':')
                        .append(argument.expectedOutputItemCount())
                        .append(':')
                        .append(String.join(",", argument.expectedOutputNumericValues()))
                        .append('\n');
            }
        }
        return sha256(builder.toString());
    }

    private static String executionConfigFingerprint(GpuExecutionConfig executionConfig) {
        if (executionConfig == null) {
            return "none";
        }
        return sha256(executionConfig.dimensions()
                + ":" + executionConfig.globalX()
                + ":" + executionConfig.globalY()
                + ":" + executionConfig.globalZ()
                + ":" + executionConfig.localX()
                + ":" + executionConfig.localY()
                + ":" + executionConfig.localZ());
    }

    private static String compileOptionsFingerprint(GpuRuntimeCompileOptions compileOptions) {
        if (compileOptions == null) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(compileOptions.backendTarget().name()).append('\n');
        builder.append(compileOptions.optimizationProfile()).append('\n');
        for (String flag : compileOptions.compileArgs()) {
            builder.append("flag=").append(flag).append('\n');
        }
        builder.append("backendOptions=").append(compileOptions.backendOptions().backendTarget().name()).append('\n');
        for (String flag : compileOptions.backendOptions().flags()) {
            builder.append("backendFlag=").append(flag).append('\n');
        }
        compileOptions.backendOptions().properties().entrySet().stream()
                .filter(entry -> !methodTestProbeEvidenceRankingProperty(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> builder
                        .append("property=")
                        .append(entry.getKey())
                        .append('=')
                        .append(entry.getValue())
                        .append('\n'));
        return sha256(builder.toString());
    }

    private static boolean methodTestProbeEvidenceRankingProperty(String propertyName) {
        return GpuBackendCompileOptions.RUNTIME_METHOD_TEST_PROBE_EVIDENCE_RANKING_PROPERTY.equals(propertyName)
                || GpuBackendCompileOptions.RUNTIME_METHOD_TEST_PROBE_EVIDENCE_CACHE_PATH_PROPERTY.equals(propertyName)
                || GpuBackendCompileOptions.RUNTIME_METHOD_TEST_PROBE_EVIDENCE_MAX_AGE_MILLIS_PROPERTY.equals(propertyName);
    }

    private static String defaultCompilerIdentity() {
        String explicit = System.getProperty(GpuRuntimeDeviceSelfTestIdentity.COMPILER_IDENTITY_PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        Package runtimePackage = GpuRuntimeMethodTestGpuProbeEvidenceKey.class.getPackage();
        String implementationVersion = runtimePackage == null ? null : runtimePackage.getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank()
                ? "JavaToGpu-development"
                : "JavaToGpu-" + implementationVersion.trim();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalize(value, "").getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
