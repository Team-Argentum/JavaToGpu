package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Runtime opt-in token loaded from one exact controlled-activation gate artifact.
 */
public final class GpuProductionActivationToken {

    public static final String PROPERTY_PREFIX = "productionActivation.token.";
    private static final String TOKEN_ID_PROPERTY = PROPERTY_PREFIX + "id";
    private static final String ARTIFACT_SHA256_PROPERTY = PROPERTY_PREFIX + "artifactSha256";
    private static final String APPROVAL_ID_PROPERTY = PROPERTY_PREFIX + "approvalId";
    private static final String CANDIDATE_GIT_SHA_PROPERTY = PROPERTY_PREFIX + "candidateGitSha";
    private static final String BACKEND_TARGET_PROPERTY = PROPERTY_PREFIX + "backendTarget";
    private static final String DEVICE_VENDOR_PROPERTY = PROPERTY_PREFIX + "deviceVendor";
    private static final String DEVICE_LABEL_PROPERTY = PROPERTY_PREFIX + "deviceLabel";
    private static final String DRIVER_VERSION_PROPERTY = PROPERTY_PREFIX + "driverVersion";
    private static final String ACTIVATION_SCOPE_PROPERTY = PROPERTY_PREFIX + "activationScope";
    private static final String KERNEL_COUNT_PROPERTY = PROPERTY_PREFIX + "kernel.count";

    private final String tokenId;
    private final String artifactSha256;
    private final String approvalId;
    private final String candidateGitSha;
    private final GpuBackendTarget backendTarget;
    private final String deviceVendor;
    private final String deviceLabel;
    private final String driverVersion;
    private final String activationScope;
    private final List<String> kernelResources;

    private GpuProductionActivationToken(
            String tokenId,
            String artifactSha256,
            String approvalId,
            String candidateGitSha,
            GpuBackendTarget backendTarget,
            String deviceVendor,
            String deviceLabel,
            String driverVersion,
            String activationScope,
            List<String> kernelResources
    ) {
        this.tokenId = normalize(tokenId, "activation:missing");
        this.artifactSha256 = normalize(artifactSha256, "missing");
        this.approvalId = normalize(approvalId, "approval:missing");
        this.candidateGitSha = normalize(candidateGitSha, "unknown");
        this.backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        this.deviceVendor = normalize(deviceVendor, "unknown");
        this.deviceLabel = normalize(deviceLabel, "unknown");
        this.driverVersion = normalize(driverVersion, "unknown");
        this.activationScope = normalize(activationScope, "unknown");
        this.kernelResources = kernelResources == null ? List.of() : List.copyOf(kernelResources);
    }

    public static GpuProductionActivationToken fromArtifact(Path artifactPath, String expectedSha256) throws IOException {
        if (artifactPath == null || !Files.isRegularFile(artifactPath)) {
            throw new IllegalStateException("Missing controlled activation gate artifact: " + artifactPath);
        }
        byte[] bytes = Files.readAllBytes(artifactPath);
        String actualSha256 = sha256(bytes);
        String normalizedExpectedSha256 = normalize(expectedSha256, "missing").toLowerCase(java.util.Locale.ROOT);
        if (!actualSha256.equals(normalizedExpectedSha256)) {
            throw new IllegalStateException(
                    "Controlled activation gate SHA-256 mismatch: expected="
                            + normalizedExpectedSha256
                            + ", actual="
                            + actualSha256
            );
        }
        Properties properties = new Properties();
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            properties.load(input);
        }
        ArrayList<String> blockers = activationArtifactBlockers(properties);
        if (!blockers.isEmpty()) {
            throw new IllegalStateException("Controlled activation gate cannot issue a token: " + blockers.get(0));
        }
        int kernelCount = parseInt(properties.getProperty("kernel.count"), 0);
        ArrayList<String> kernelResources = new ArrayList<>();
        for (int index = 0; index < kernelCount; index++) {
            kernelResources.add(properties.getProperty("kernel." + index + ".resource"));
        }
        return new GpuProductionActivationToken(
                "activation:" + actualSha256.substring(0, 16),
                actualSha256,
                properties.getProperty("manifest.approval.id"),
                properties.getProperty("manifest.candidateGitSha"),
                parseBackendTarget(properties.getProperty("backendTarget")),
                properties.getProperty("deviceVendor"),
                properties.getProperty("deviceLabel"),
                properties.getProperty("driverVersion"),
                properties.getProperty("activationScope"),
                kernelResources
        );
    }

    public static Optional<GpuProductionActivationToken> from(GpuBackendCompileOptions options) {
        if (options == null) {
            return Optional.empty();
        }
        Map<String, String> properties = options.properties();
        String tokenId = properties.get(TOKEN_ID_PROPERTY);
        if (tokenId == null || tokenId.isBlank()) {
            return Optional.empty();
        }
        int kernelCount = parseInt(properties.get(KERNEL_COUNT_PROPERTY), 0);
        ArrayList<String> resources = new ArrayList<>();
        for (int index = 0; index < kernelCount; index++) {
            String resource = properties.get(PROPERTY_PREFIX + "kernel." + index + ".resource");
            if (resource != null && !resource.isBlank()) {
                resources.add(resource);
            }
        }
        return Optional.of(new GpuProductionActivationToken(
                tokenId,
                properties.get(ARTIFACT_SHA256_PROPERTY),
                properties.get(APPROVAL_ID_PROPERTY),
                properties.get(CANDIDATE_GIT_SHA_PROPERTY),
                parseBackendTarget(properties.get(BACKEND_TARGET_PROPERTY)),
                properties.get(DEVICE_VENDOR_PROPERTY),
                properties.get(DEVICE_LABEL_PROPERTY),
                properties.get(DRIVER_VERSION_PROPERTY),
                properties.get(ACTIVATION_SCOPE_PROPERTY),
                resources
        ));
    }

    public static Result evaluate(GpuRuntimeCompileRequest request) {
        if (request == null) {
            return Result.blocked("activation:missing", "production-activation-request-missing");
        }
        return from(request.options().backendOptions())
                .map(token -> token.evaluateAgainst(request))
                .orElseGet(() -> Result.blocked(
                        "activation:missing",
                        "production-activation-token-missing"
                ));
    }

    public Result evaluateAgainst(GpuRuntimeCompileRequest request) {
        if (request == null) {
            return Result.blocked(tokenId, "production-activation-request-missing");
        }
        ArrayList<String> blockers = new ArrayList<>();
        if (!GpuBackendSourcePromotionActivationGate.ACTIVATION_SCOPE.equals(activationScope)) {
            blockers.add("production-activation-scope-mismatch");
        }
        if (backendTarget != request.options().backendTarget()) {
            blockers.add("production-activation-backend-target-mismatch");
        }
        GpuRuntimeDeviceProfile device = request.deviceProfile();
        if (!deviceVendor.equals(device.vendor())) {
            blockers.add("production-activation-device-vendor-mismatch");
        }
        if (!deviceLabel.equals(device.deviceLabel())) {
            blockers.add("production-activation-device-label-mismatch");
        }
        if (!driverVersion.equals(device.driverVersion())) {
            blockers.add("production-activation-driver-version-mismatch");
        }
        if (!kernelResources.contains(request.descriptor().kernelResource())) {
            blockers.add("production-activation-kernel-resource-not-approved");
        }
        return blockers.isEmpty()
                ? Result.accepted(tokenId, artifactSha256, approvalId, candidateGitSha)
                : new Result(false, "blocked", tokenId, artifactSha256, approvalId, candidateGitSha, blockers);
    }

    public Map<String, String> properties() {
        LinkedHashMap<String, String> properties = new LinkedHashMap<>();
        properties.put(TOKEN_ID_PROPERTY, tokenId);
        properties.put(ARTIFACT_SHA256_PROPERTY, artifactSha256);
        properties.put(APPROVAL_ID_PROPERTY, approvalId);
        properties.put(CANDIDATE_GIT_SHA_PROPERTY, candidateGitSha);
        properties.put(BACKEND_TARGET_PROPERTY, backendTarget.name());
        properties.put(DEVICE_VENDOR_PROPERTY, deviceVendor);
        properties.put(DEVICE_LABEL_PROPERTY, deviceLabel);
        properties.put(DRIVER_VERSION_PROPERTY, driverVersion);
        properties.put(ACTIVATION_SCOPE_PROPERTY, activationScope);
        properties.put(KERNEL_COUNT_PROPERTY, Integer.toString(kernelResources.size()));
        for (int index = 0; index < kernelResources.size(); index++) {
            properties.put(PROPERTY_PREFIX + "kernel." + index + ".resource", kernelResources.get(index));
        }
        return Map.copyOf(properties);
    }

    public String tokenId() {
        return tokenId;
    }

    public String artifactSha256() {
        return artifactSha256;
    }

    public String approvalId() {
        return approvalId;
    }

    public String candidateGitSha() {
        return candidateGitSha;
    }

    public GpuBackendTarget backendTarget() {
        return backendTarget;
    }

    public String deviceVendor() {
        return deviceVendor;
    }

    public String deviceLabel() {
        return deviceLabel;
    }

    public String driverVersion() {
        return driverVersion;
    }

    public String activationScope() {
        return activationScope;
    }

    public List<String> kernelResources() {
        return kernelResources;
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    private static ArrayList<String> activationArtifactBlockers(Properties properties) {
        ArrayList<String> blockers = new ArrayList<>();
        requireEquals(blockers, properties, "status", "controlled-activation-ready", "activation-artifact-not-ready");
        requireTrue(blockers, properties, "activationReady", "activation-artifact-ready-false");
        requireEquals(
                blockers,
                properties,
                "activationScope",
                GpuBackendSourcePromotionActivationGate.ACTIVATION_SCOPE,
                "activation-artifact-scope-mismatch"
        );
        requireEquals(blockers, properties, "backendTarget", "OPENCL", "activation-artifact-backend-mismatch");
        requireEquals(blockers, properties, "defaultRuntimeActivation", "false", "activation-artifact-default-runtime-enabled");
        requireEquals(
                blockers,
                properties,
                "defaultProductionSourceSwitching",
                "disabled",
                "activation-artifact-default-source-switching-enabled"
        );
        requireEquals(blockers, properties, "productionMutation", "disabled", "activation-artifact-production-mutation-enabled");
        requireTrue(blockers, properties, "controlledCoverage.all", "activation-artifact-controlled-coverage-incomplete");
        requireTrue(blockers, properties, "operatorAcceptance.accepted.all", "activation-artifact-operator-acceptance-incomplete");
        requireTrue(blockers, properties, "operatorAcceptance.bound.all", "activation-artifact-operator-binding-incomplete");
        requireNonBlank(blockers, properties, "manifest.approval.id", "activation-artifact-approval-id-missing");
        requireNonBlank(blockers, properties, "deviceVendor", "activation-artifact-device-vendor-missing");
        requireNonBlank(blockers, properties, "deviceLabel", "activation-artifact-device-label-missing");
        requireNonBlank(blockers, properties, "driverVersion", "activation-artifact-driver-version-missing");
        if (!normalize(properties.getProperty("manifest.candidateGitSha"), "unknown")
                .matches("[0-9a-fA-F]{40,64}")) {
            blockers.add("activation-artifact-candidate-git-sha-invalid");
        }
        if (parseInt(properties.getProperty("blocker.count"), -1) != 0) {
            blockers.add("activation-artifact-has-blockers");
        }
        int kernelCount = parseInt(properties.getProperty("kernel.count"), 0);
        if (kernelCount <= 0) {
            blockers.add("activation-artifact-kernels-missing");
        }
        LinkedHashSet<String> resources = new LinkedHashSet<>();
        for (int index = 0; index < kernelCount; index++) {
            String prefix = "kernel." + index + ".";
            String resource = properties.getProperty(prefix + "resource", "");
            if (resource.isBlank()) {
                blockers.add("activation-artifact-kernel-resource-missing-" + index);
            } else if (!resources.add(resource)) {
                blockers.add("activation-artifact-kernel-resource-duplicate-" + index);
            }
            if (!Boolean.parseBoolean(properties.getProperty(prefix + "activationReady"))) {
                blockers.add("activation-artifact-kernel-not-ready-" + index);
            }
        }
        return blockers;
    }

    private static void requireEquals(
            List<String> blockers,
            Properties properties,
            String key,
            String expected,
            String blocker
    ) {
        if (!expected.equals(properties.getProperty(key))) {
            blockers.add(blocker);
        }
    }

    private static void requireTrue(List<String> blockers, Properties properties, String key, String blocker) {
        if (!Boolean.parseBoolean(properties.getProperty(key))) {
            blockers.add(blocker);
        }
    }

    private static void requireNonBlank(List<String> blockers, Properties properties, String key, String blocker) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank() || "unknown".equals(value) || "missing".equals(value)) {
            blockers.add(blocker);
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static GpuBackendTarget parseBackendTarget(String value) {
        try {
            return GpuBackendTarget.valueOf(normalize(value, "UNKNOWN").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return GpuBackendTarget.UNKNOWN;
        }
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record Result(
            boolean accepted,
            String status,
            String tokenId,
            String artifactSha256,
            String approvalId,
            String candidateGitSha,
            List<String> blockers
    ) {
        public Result {
            status = normalize(status, accepted ? "accepted" : "blocked");
            tokenId = normalize(tokenId, "activation:missing");
            artifactSha256 = normalize(artifactSha256, "missing");
            approvalId = normalize(approvalId, "approval:missing");
            candidateGitSha = normalize(candidateGitSha, "unknown");
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            if (accepted && !blockers.isEmpty()) {
                throw new IllegalArgumentException("Accepted production activation token must not contain blockers");
            }
        }

        public static Result accepted(
                String tokenId,
                String artifactSha256,
                String approvalId,
                String candidateGitSha
        ) {
            return new Result(
                    true,
                    "accepted",
                    tokenId,
                    artifactSha256,
                    approvalId,
                    candidateGitSha,
                    List.of()
            );
        }

        public static Result blocked(String tokenId, String blocker) {
            return new Result(
                    false,
                    "blocked",
                    tokenId,
                    "missing",
                    "approval:missing",
                    "unknown",
                    List.of(normalize(blocker, "production-activation-token-blocked"))
            );
        }

        public String diagnostic() {
            return accepted
                    ? "production activation token " + tokenId + " matches the runtime compile context"
                    : "production activation token is blocked by "
                    + (blockers.isEmpty() ? "production-activation-token-blocked" : blockers.get(0));
        }
    }
}
