package net.sixik.ga_utils.javatogpu.runtime;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

/**
 * Manual approval artifact bound to one reviewed production-candidate gate and Git commit.
 */
public final class GpuBackendSourcePromotionManifest {

    public static final String SCOPE = "real-workload-production-candidate-manual-approval";
    public static final String PORTABLE_PREFIX = "runtime.production.manifest.";
    private static final String REQUIRED = "REQUIRED";

    private GpuBackendSourcePromotionManifest() {
    }

    public static String template(Properties candidate, byte[] candidateBytes, String gitSha) {
        Properties normalizedCandidate = candidate == null ? new Properties() : candidate;
        byte[] normalizedBytes = candidateBytes == null ? new byte[0] : candidateBytes.clone();
        List<String> candidateBlockers = candidateBlockers(normalizedCandidate);
        if (!candidateBlockers.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot create promotion manifest template: " + candidateBlockers.get(0)
            );
        }
        String normalizedGitSha = normalize(gitSha, "unknown");
        if (!validGitSha(normalizedGitSha)) {
            throw new IllegalArgumentException("Promotion manifest requires a full hexadecimal Git SHA");
        }
        int kernelCount = parseInt(candidateProperty(normalizedCandidate, "kernel.count", "0"), 0);
        String deviceVendor = propertyValue(candidateProperty(normalizedCandidate, "operatorAcceptance.deviceVendor", null));
        String deviceLabel = propertyValue(candidateProperty(normalizedCandidate, "operatorAcceptance.deviceLabel", null));
        String driverVersion = propertyValue(candidateProperty(normalizedCandidate, "operatorAcceptance.driverVersion", null));
        String candidateSha256 = sha256(normalizedBytes);
        StringBuilder builder = new StringBuilder();
        builder.append("formatVersion=1\n");
        builder.append("status=pending\n");
        builder.append("scope=").append(SCOPE).append('\n');
        builder.append("approval.id=").append(REQUIRED).append('\n');
        builder.append("approval.approvedBy=").append(REQUIRED).append('\n');
        builder.append("approval.approvedAtUtc=").append(REQUIRED).append('\n');
        builder.append("binding.gitSha=").append(normalizedGitSha).append('\n');
        builder.append("binding.candidateArtifact.sha256=").append(candidateSha256).append('\n');
        builder.append("binding.backendTarget=OPENCL\n");
        builder.append("binding.deviceVendor=").append(deviceVendor).append('\n');
        builder.append("binding.deviceLabel=").append(deviceLabel).append('\n');
        builder.append("binding.driverVersion=").append(driverVersion).append('\n');
        builder.append("binding.kernel.count=").append(kernelCount).append('\n');
        for (int index = 0; index < kernelCount; index++) {
            builder.append("binding.kernel.").append(index).append(".resource=")
                    .append(propertyValue(candidateKernelProperty(normalizedCandidate, index, "resource", null)))
                    .append('\n');
        }
        builder.append("authorization.defaultProductionSourceSwitching=disabled\n");
        builder.append("authorization.productionMutation=disabled\n");
        builder.append("authorization.scope=manual-review-only\n");
        String diagnostic = "complete approval fields and set status=approved; validation does not enable production";
        appendPortable(builder, "formatVersion", "1");
        appendPortable(builder, "status", "pending");
        appendPortable(builder, "scope", SCOPE);
        appendPortable(builder, "approval.id", REQUIRED);
        appendPortable(builder, "approval.approvedBy", REQUIRED);
        appendPortable(builder, "approval.approvedAtUtc", REQUIRED);
        appendPortable(builder, "binding.gitSha", normalizedGitSha);
        appendPortable(builder, "binding.candidateArtifact.sha256", candidateSha256);
        appendPortable(builder, "binding.backendTarget", "OPENCL");
        appendPortable(builder, "binding.deviceVendor", deviceVendor);
        appendPortable(builder, "binding.deviceLabel", deviceLabel);
        appendPortable(builder, "binding.driverVersion", driverVersion);
        appendPortable(builder, "binding.kernel.count", kernelCount);
        for (int index = 0; index < kernelCount; index++) {
            appendPortable(
                    builder,
                    "binding.kernel." + index + ".resource",
                    propertyValue(candidateKernelProperty(normalizedCandidate, index, "resource", null))
            );
        }
        appendPortable(builder, "authorization.defaultProductionSourceSwitching", "disabled");
        appendPortable(builder, "authorization.productionMutation", "disabled");
        appendPortable(builder, "authorization.scope", "manual-review-only");
        builder.append("diagnostic=").append(diagnostic).append('\n');
        appendPortable(builder, "diagnostic", diagnostic);
        return builder.toString();
    }

    public static Validation validate(
            Properties candidate,
            byte[] candidateBytes,
            Properties manifest,
            String expectedGitSha
    ) {
        Properties normalizedCandidate = candidate == null ? new Properties() : candidate;
        Properties normalizedManifest = manifest == null ? new Properties() : manifest;
        byte[] normalizedBytes = candidateBytes == null ? new byte[0] : candidateBytes.clone();
        LinkedHashSet<String> blockers = new LinkedHashSet<>(candidateBlockers(normalizedCandidate));
        String actualCandidateSha256 = sha256(normalizedBytes);
        String manifestCandidateSha256 = normalize(
                manifestProperty(normalizedManifest, "binding.candidateArtifact.sha256", null),
                "missing"
        );
        String normalizedExpectedGitSha = normalize(expectedGitSha, "unknown");
        String manifestGitSha = normalize(manifestProperty(normalizedManifest, "binding.gitSha", null), "missing");
        String approvalId = normalize(manifestProperty(normalizedManifest, "approval.id", null), "approval:missing");
        String approvedBy = normalize(manifestProperty(normalizedManifest, "approval.approvedBy", null), "missing");
        String approvedAtUtc = normalize(manifestProperty(normalizedManifest, "approval.approvedAtUtc", null), "missing");

        requireManifestEquals(blockers, normalizedManifest, "formatVersion", "1", "manifest-format-version-mismatch");
        requireManifestEquals(blockers, normalizedManifest, "status", "approved", "manifest-not-approved");
        requireManifestEquals(blockers, normalizedManifest, "scope", SCOPE, "manifest-scope-mismatch");
        requireApprovalValue(blockers, approvalId, "manifest-approval-id-missing");
        requireApprovalValue(blockers, approvedBy, "manifest-approved-by-missing");
        requireApprovalValue(blockers, approvedAtUtc, "manifest-approved-at-missing");
        if (!REQUIRED.equals(approvedAtUtc) && !"missing".equals(approvedAtUtc)) {
            try {
                Instant.parse(approvedAtUtc);
            } catch (DateTimeParseException exception) {
                blockers.add("manifest-approved-at-invalid");
            }
        }
        if (!validGitSha(normalizedExpectedGitSha)) {
            blockers.add("expected-git-sha-invalid");
        }
        if (!normalizedExpectedGitSha.equals(manifestGitSha)) {
            blockers.add("manifest-git-sha-mismatch");
        }
        if (!actualCandidateSha256.equals(manifestCandidateSha256)) {
            blockers.add("manifest-candidate-artifact-sha256-mismatch");
        }
        requireManifestEquals(blockers, normalizedManifest, "binding.backendTarget", "OPENCL", "manifest-backend-target-mismatch");
        requireManifestBindingEquals(
                blockers,
                normalizedManifest,
                "binding.deviceVendor",
                candidateProperty(normalizedCandidate, "operatorAcceptance.deviceVendor", null),
                "manifest-device-vendor-mismatch"
        );
        requireManifestBindingEquals(
                blockers,
                normalizedManifest,
                "binding.deviceLabel",
                candidateProperty(normalizedCandidate, "operatorAcceptance.deviceLabel", null),
                "manifest-device-label-mismatch"
        );
        requireManifestBindingEquals(
                blockers,
                normalizedManifest,
                "binding.driverVersion",
                candidateProperty(normalizedCandidate, "operatorAcceptance.driverVersion", null),
                "manifest-driver-version-mismatch"
        );
        int candidateKernelCount = parseInt(candidateProperty(normalizedCandidate, "kernel.count", "0"), 0);
        int manifestKernelCount = parseInt(manifestProperty(normalizedManifest, "binding.kernel.count", "-1"), -1);
        if (candidateKernelCount != manifestKernelCount) {
            blockers.add("manifest-kernel-count-mismatch");
        }
        for (int index = 0; index < candidateKernelCount; index++) {
            requireManifestBindingEquals(
                    blockers,
                    normalizedManifest,
                    "binding.kernel." + index + ".resource",
                    candidateKernelProperty(normalizedCandidate, index, "resource", null),
                    "manifest-kernel-resource-mismatch-" + index
            );
        }
        requireManifestEquals(
                blockers,
                normalizedManifest,
                "authorization.defaultProductionSourceSwitching",
                "disabled",
                "manifest-default-production-source-switching-not-disabled"
        );
        requireManifestEquals(
                blockers,
                normalizedManifest,
                "authorization.productionMutation",
                "disabled",
                "manifest-production-mutation-not-disabled"
        );
        requireManifestEquals(
                blockers,
                normalizedManifest,
                "authorization.scope",
                "manual-review-only",
                "manifest-authorization-scope-mismatch"
        );
        boolean valid = blockers.isEmpty();
        return new Validation(
                valid,
                valid ? "approved" : "blocked",
                approvalId,
                approvedBy,
                approvedAtUtc,
                normalizedExpectedGitSha,
                manifestGitSha,
                actualCandidateSha256,
                manifestCandidateSha256,
                candidateProperty(normalizedCandidate, "operatorAcceptance.deviceVendor", "unknown"),
                candidateProperty(normalizedCandidate, "operatorAcceptance.deviceLabel", "unknown"),
                candidateProperty(normalizedCandidate, "operatorAcceptance.driverVersion", "unknown"),
                candidateKernelCount,
                List.copyOf(blockers)
        );
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }

    private static List<String> candidateBlockers(Properties candidate) {
        ArrayList<String> blockers = new ArrayList<>();
        if (!"review-ready".equals(candidateProperty(candidate, "status", null))) {
            blockers.add("candidate-status-not-review-ready");
        }
        if (!Boolean.parseBoolean(candidateProperty(candidate, "reviewReady", null))) {
            blockers.add("candidate-review-ready-false");
        }
        if (!"real-workload-production-candidate".equals(candidateProperty(candidate, "scope", null))) {
            blockers.add("candidate-scope-mismatch");
        }
        if (!Boolean.parseBoolean(candidateProperty(candidate, "candidateReady.all", null))) {
            blockers.add("candidate-kernels-not-all-ready");
        }
        if (!Boolean.parseBoolean(candidateProperty(candidate, "sourceParityMatched", null))) {
            blockers.add("candidate-source-parity-not-matched");
        }
        if (!Boolean.parseBoolean(candidateProperty(candidate, "runtimeEquivalencePassed", null))) {
            blockers.add("candidate-runtime-equivalence-not-passed");
        }
        if (!"passed".equals(candidateProperty(candidate, "controlledSourceSwitching.status", null))) {
            blockers.add("candidate-controlled-source-switching-not-passed");
        }
        if (!"identity-bound".equals(candidateProperty(candidate, "operatorAcceptance.mode", null))) {
            blockers.add("candidate-operator-acceptance-mode-not-identity-bound");
        }
        if (!Boolean.parseBoolean(candidateProperty(candidate, "operatorAcceptance.accepted.all", null))) {
            blockers.add("candidate-operator-acceptance-not-all-accepted");
        }
        if (!Boolean.parseBoolean(candidateProperty(candidate, "operatorAcceptance.bound.all", null))) {
            blockers.add("candidate-operator-acceptance-not-all-bound");
        }
        if (!"disabled".equals(candidateProperty(candidate, "defaultProductionSourceSwitching", null))) {
            blockers.add("candidate-default-production-source-switching-not-disabled");
        }
        if (!"disabled".equals(candidateProperty(candidate, "productionMutation", null))) {
            blockers.add("candidate-production-mutation-not-disabled");
        }
        int kernelCount = parseInt(candidateProperty(candidate, "kernel.count", "0"), 0);
        if (kernelCount <= 0) {
            blockers.add("candidate-kernels-missing");
        }
        for (int index = 0; index < kernelCount; index++) {
            if (normalize(candidateKernelProperty(candidate, index, "resource", null), "unknown").equals("unknown")) {
                blockers.add("candidate-kernel-resource-missing-" + index);
            }
        }
        return List.copyOf(blockers);
    }

    private static void requireEquals(
            LinkedHashSet<String> blockers,
            Properties properties,
            String key,
            String expected,
            String blocker
    ) {
        if (!expected.equals(properties.getProperty(key))) {
            blockers.add(blocker);
        }
    }

    private static void requireManifestEquals(
            LinkedHashSet<String> blockers,
            Properties properties,
            String key,
            String expected,
            String blocker
    ) {
        if (!expected.equals(manifestProperty(properties, key, null))) {
            blockers.add(blocker);
        }
    }

    private static void requireManifestBindingEquals(
            LinkedHashSet<String> blockers,
            Properties manifest,
            String key,
            String expected,
            String blocker
    ) {
        if (!normalize(expected, "unknown").equals(normalize(manifestProperty(manifest, key, null), "missing"))) {
            blockers.add(blocker);
        }
    }

    private static void requireApprovalValue(LinkedHashSet<String> blockers, String value, String blocker) {
        if (value.isBlank() || REQUIRED.equals(value) || "missing".equals(value) || "approval:missing".equals(value)) {
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

    private static boolean validGitSha(String value) {
        return value != null && value.matches("[0-9a-fA-F]{40,64}");
    }

    private static String propertyValue(String value) {
        return normalize(value, "unknown").replace('\\', '/').replace('\r', ' ').replace('\n', ' ');
    }

    private static String candidateProperty(Properties candidate, String key, String fallback) {
        return GpuRuntimeArtifactProperties.portable(
                candidate,
                GpuBackendSourcePromotionCandidateGate.PORTABLE_PREFIX,
                key,
                fallback
        );
    }

    private static String candidateKernelProperty(Properties candidate, int index, String key, String fallback) {
        return GpuRuntimeArtifactProperties.prefixedPortable(
                candidate,
                "kernel." + index + ".",
                GpuBackendSourcePromotionCandidateGate.PORTABLE_PREFIX,
                key,
                fallback
        );
    }

    private static String manifestProperty(Properties manifest, String key, String fallback) {
        return GpuRuntimeArtifactProperties.portable(manifest, PORTABLE_PREFIX, key, fallback);
    }

    private static void appendPortable(StringBuilder builder, String key, Object value) {
        GpuRuntimeArtifactProperties.appendPortable(builder, PORTABLE_PREFIX, key, value);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record Validation(
            boolean valid,
            String status,
            String approvalId,
            String approvedBy,
            String approvedAtUtc,
            String expectedGitSha,
            String manifestGitSha,
            String actualCandidateSha256,
            String manifestCandidateSha256,
            String deviceVendor,
            String deviceLabel,
            String driverVersion,
            int kernelCount,
            List<String> blockers
    ) {
        public Validation {
            status = normalize(status, valid ? "approved" : "blocked");
            approvalId = normalize(approvalId, "approval:missing");
            approvedBy = normalize(approvedBy, "missing");
            approvedAtUtc = normalize(approvedAtUtc, "missing");
            expectedGitSha = normalize(expectedGitSha, "unknown");
            manifestGitSha = normalize(manifestGitSha, "missing");
            actualCandidateSha256 = normalize(actualCandidateSha256, "missing");
            manifestCandidateSha256 = normalize(manifestCandidateSha256, "missing");
            deviceVendor = normalize(deviceVendor, "unknown");
            deviceLabel = normalize(deviceLabel, "unknown");
            driverVersion = normalize(driverVersion, "unknown");
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            if (valid && !blockers.isEmpty()) {
                throw new IllegalArgumentException("Valid promotion manifest must not contain blockers");
            }
        }

        public String firstBlocker() {
            return blockers.isEmpty() ? "none" : blockers.get(0);
        }

        public String toPropertiesText() {
            StringBuilder builder = new StringBuilder();
            boolean gitShaMatched = expectedGitSha.equals(manifestGitSha);
            boolean artifactShaMatched = actualCandidateSha256.equals(manifestCandidateSha256);
            builder.append("formatVersion=1\n");
            builder.append("status=").append(status).append('\n');
            builder.append("valid=").append(valid).append('\n');
            builder.append("scope=").append(SCOPE).append("-validation\n");
            builder.append("approval.id=").append(propertyValue(approvalId)).append('\n');
            builder.append("approval.approvedBy=").append(propertyValue(approvedBy)).append('\n');
            builder.append("approval.approvedAtUtc=").append(propertyValue(approvedAtUtc)).append('\n');
            builder.append("binding.expectedGitSha=").append(expectedGitSha).append('\n');
            builder.append("binding.manifestGitSha=").append(manifestGitSha).append('\n');
            builder.append("binding.gitShaMatched=").append(gitShaMatched).append('\n');
            builder.append("binding.actualCandidateArtifact.sha256=").append(actualCandidateSha256).append('\n');
            builder.append("binding.manifestCandidateArtifact.sha256=").append(manifestCandidateSha256).append('\n');
            builder.append("binding.candidateArtifactSha256Matched=").append(artifactShaMatched).append('\n');
            builder.append("binding.backendTarget=OPENCL\n");
            builder.append("binding.deviceVendor=").append(propertyValue(deviceVendor)).append('\n');
            builder.append("binding.deviceLabel=").append(propertyValue(deviceLabel)).append('\n');
            builder.append("binding.driverVersion=").append(propertyValue(driverVersion)).append('\n');
            builder.append("binding.kernel.count=").append(kernelCount).append('\n');
            builder.append("authorization.defaultProductionSourceSwitching=disabled\n");
            builder.append("authorization.productionMutation=disabled\n");
            builder.append("authorization.scope=manual-review-only\n");
            appendPortable(builder, "formatVersion", "1");
            appendPortable(builder, "status", status);
            appendPortable(builder, "valid", valid);
            appendPortable(builder, "scope", SCOPE + "-validation");
            appendPortable(builder, "approval.id", propertyValue(approvalId));
            appendPortable(builder, "approval.approvedBy", propertyValue(approvedBy));
            appendPortable(builder, "approval.approvedAtUtc", propertyValue(approvedAtUtc));
            appendPortable(builder, "binding.expectedGitSha", expectedGitSha);
            appendPortable(builder, "binding.manifestGitSha", manifestGitSha);
            appendPortable(builder, "binding.gitShaMatched", gitShaMatched);
            appendPortable(builder, "binding.actualCandidateArtifact.sha256", actualCandidateSha256);
            appendPortable(builder, "binding.manifestCandidateArtifact.sha256", manifestCandidateSha256);
            appendPortable(builder, "binding.candidateArtifactSha256Matched", artifactShaMatched);
            appendPortable(builder, "binding.backendTarget", "OPENCL");
            appendPortable(builder, "binding.deviceVendor", propertyValue(deviceVendor));
            appendPortable(builder, "binding.deviceLabel", propertyValue(deviceLabel));
            appendPortable(builder, "binding.driverVersion", propertyValue(driverVersion));
            appendPortable(builder, "binding.kernel.count", kernelCount);
            appendPortable(builder, "authorization.defaultProductionSourceSwitching", "disabled");
            appendPortable(builder, "authorization.productionMutation", "disabled");
            appendPortable(builder, "authorization.scope", "manual-review-only");
            builder.append("blocker.count=").append(blockers.size()).append('\n');
            appendPortable(builder, "blocker.count", blockers.size());
            for (int index = 0; index < blockers.size(); index++) {
                builder.append("blocker.").append(index).append('=').append(blockers.get(index)).append('\n');
                appendPortable(builder, "blocker." + index, blockers.get(index));
            }
            String diagnostic = valid
                    ? "manual promotion manifest is valid for the reviewed candidate; production remains disabled"
                    : "manual promotion manifest is blocked by " + firstBlocker();
            builder.append("diagnostic=").append(diagnostic).append('\n');
            appendPortable(builder, "diagnostic", diagnostic);
            return builder.toString();
        }
    }
}
