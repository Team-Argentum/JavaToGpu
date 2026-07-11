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
        int kernelCount = parseInt(normalizedCandidate.getProperty("kernel.count"), 0);
        StringBuilder builder = new StringBuilder();
        builder.append("formatVersion=1\n");
        builder.append("status=pending\n");
        builder.append("scope=").append(SCOPE).append('\n');
        builder.append("approval.id=").append(REQUIRED).append('\n');
        builder.append("approval.approvedBy=").append(REQUIRED).append('\n');
        builder.append("approval.approvedAtUtc=").append(REQUIRED).append('\n');
        builder.append("binding.gitSha=").append(normalizedGitSha).append('\n');
        builder.append("binding.candidateArtifact.sha256=").append(sha256(normalizedBytes)).append('\n');
        builder.append("binding.backendTarget=OPENCL\n");
        builder.append("binding.deviceVendor=")
                .append(propertyValue(normalizedCandidate.getProperty("operatorAcceptance.deviceVendor")))
                .append('\n');
        builder.append("binding.deviceLabel=")
                .append(propertyValue(normalizedCandidate.getProperty("operatorAcceptance.deviceLabel")))
                .append('\n');
        builder.append("binding.driverVersion=")
                .append(propertyValue(normalizedCandidate.getProperty("operatorAcceptance.driverVersion")))
                .append('\n');
        builder.append("binding.kernel.count=").append(kernelCount).append('\n');
        for (int index = 0; index < kernelCount; index++) {
            builder.append("binding.kernel.").append(index).append(".resource=")
                    .append(propertyValue(normalizedCandidate.getProperty("kernel." + index + ".resource")))
                    .append('\n');
        }
        builder.append("authorization.defaultProductionSourceSwitching=disabled\n");
        builder.append("authorization.productionMutation=disabled\n");
        builder.append("authorization.scope=manual-review-only\n");
        builder.append("diagnostic=complete approval fields and set status=approved; validation does not enable production\n");
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
                normalizedManifest.getProperty("binding.candidateArtifact.sha256"),
                "missing"
        );
        String normalizedExpectedGitSha = normalize(expectedGitSha, "unknown");
        String manifestGitSha = normalize(normalizedManifest.getProperty("binding.gitSha"), "missing");
        String approvalId = normalize(normalizedManifest.getProperty("approval.id"), "approval:missing");
        String approvedBy = normalize(normalizedManifest.getProperty("approval.approvedBy"), "missing");
        String approvedAtUtc = normalize(normalizedManifest.getProperty("approval.approvedAtUtc"), "missing");

        requireEquals(blockers, normalizedManifest, "formatVersion", "1", "manifest-format-version-mismatch");
        requireEquals(blockers, normalizedManifest, "status", "approved", "manifest-not-approved");
        requireEquals(blockers, normalizedManifest, "scope", SCOPE, "manifest-scope-mismatch");
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
        requireEquals(blockers, normalizedManifest, "binding.backendTarget", "OPENCL", "manifest-backend-target-mismatch");
        requireBindingEquals(
                blockers,
                normalizedManifest,
                "binding.deviceVendor",
                normalizedCandidate.getProperty("operatorAcceptance.deviceVendor"),
                "manifest-device-vendor-mismatch"
        );
        requireBindingEquals(
                blockers,
                normalizedManifest,
                "binding.deviceLabel",
                normalizedCandidate.getProperty("operatorAcceptance.deviceLabel"),
                "manifest-device-label-mismatch"
        );
        requireBindingEquals(
                blockers,
                normalizedManifest,
                "binding.driverVersion",
                normalizedCandidate.getProperty("operatorAcceptance.driverVersion"),
                "manifest-driver-version-mismatch"
        );
        int candidateKernelCount = parseInt(normalizedCandidate.getProperty("kernel.count"), 0);
        int manifestKernelCount = parseInt(normalizedManifest.getProperty("binding.kernel.count"), -1);
        if (candidateKernelCount != manifestKernelCount) {
            blockers.add("manifest-kernel-count-mismatch");
        }
        for (int index = 0; index < candidateKernelCount; index++) {
            requireBindingEquals(
                    blockers,
                    normalizedManifest,
                    "binding.kernel." + index + ".resource",
                    normalizedCandidate.getProperty("kernel." + index + ".resource"),
                    "manifest-kernel-resource-mismatch-" + index
            );
        }
        requireEquals(
                blockers,
                normalizedManifest,
                "authorization.defaultProductionSourceSwitching",
                "disabled",
                "manifest-default-production-source-switching-not-disabled"
        );
        requireEquals(
                blockers,
                normalizedManifest,
                "authorization.productionMutation",
                "disabled",
                "manifest-production-mutation-not-disabled"
        );
        requireEquals(
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
                normalizedCandidate.getProperty("operatorAcceptance.deviceVendor", "unknown"),
                normalizedCandidate.getProperty("operatorAcceptance.deviceLabel", "unknown"),
                normalizedCandidate.getProperty("operatorAcceptance.driverVersion", "unknown"),
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
        if (!"review-ready".equals(candidate.getProperty("status"))) {
            blockers.add("candidate-status-not-review-ready");
        }
        if (!Boolean.parseBoolean(candidate.getProperty("reviewReady"))) {
            blockers.add("candidate-review-ready-false");
        }
        if (!"real-workload-production-candidate".equals(candidate.getProperty("scope"))) {
            blockers.add("candidate-scope-mismatch");
        }
        if (!Boolean.parseBoolean(candidate.getProperty("candidateReady.all"))) {
            blockers.add("candidate-kernels-not-all-ready");
        }
        if (!Boolean.parseBoolean(candidate.getProperty("sourceParityMatched"))) {
            blockers.add("candidate-source-parity-not-matched");
        }
        if (!Boolean.parseBoolean(candidate.getProperty("runtimeEquivalencePassed"))) {
            blockers.add("candidate-runtime-equivalence-not-passed");
        }
        if (!"passed".equals(candidate.getProperty("controlledSourceSwitching.status"))) {
            blockers.add("candidate-controlled-source-switching-not-passed");
        }
        if (!"identity-bound".equals(candidate.getProperty("operatorAcceptance.mode"))) {
            blockers.add("candidate-operator-acceptance-mode-not-identity-bound");
        }
        if (!Boolean.parseBoolean(candidate.getProperty("operatorAcceptance.accepted.all"))) {
            blockers.add("candidate-operator-acceptance-not-all-accepted");
        }
        if (!Boolean.parseBoolean(candidate.getProperty("operatorAcceptance.bound.all"))) {
            blockers.add("candidate-operator-acceptance-not-all-bound");
        }
        if (!"disabled".equals(candidate.getProperty("defaultProductionSourceSwitching"))) {
            blockers.add("candidate-default-production-source-switching-not-disabled");
        }
        if (!"disabled".equals(candidate.getProperty("productionMutation"))) {
            blockers.add("candidate-production-mutation-not-disabled");
        }
        int kernelCount = parseInt(candidate.getProperty("kernel.count"), 0);
        if (kernelCount <= 0) {
            blockers.add("candidate-kernels-missing");
        }
        for (int index = 0; index < kernelCount; index++) {
            if (normalize(candidate.getProperty("kernel." + index + ".resource"), "unknown").equals("unknown")) {
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

    private static void requireBindingEquals(
            LinkedHashSet<String> blockers,
            Properties manifest,
            String key,
            String expected,
            String blocker
    ) {
        if (!normalize(expected, "unknown").equals(normalize(manifest.getProperty(key), "missing"))) {
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
            builder.append("formatVersion=1\n");
            builder.append("status=").append(status).append('\n');
            builder.append("valid=").append(valid).append('\n');
            builder.append("scope=").append(SCOPE).append("-validation\n");
            builder.append("approval.id=").append(propertyValue(approvalId)).append('\n');
            builder.append("approval.approvedBy=").append(propertyValue(approvedBy)).append('\n');
            builder.append("approval.approvedAtUtc=").append(propertyValue(approvedAtUtc)).append('\n');
            builder.append("binding.expectedGitSha=").append(expectedGitSha).append('\n');
            builder.append("binding.manifestGitSha=").append(manifestGitSha).append('\n');
            builder.append("binding.gitShaMatched=").append(expectedGitSha.equals(manifestGitSha)).append('\n');
            builder.append("binding.actualCandidateArtifact.sha256=").append(actualCandidateSha256).append('\n');
            builder.append("binding.manifestCandidateArtifact.sha256=").append(manifestCandidateSha256).append('\n');
            builder.append("binding.candidateArtifactSha256Matched=")
                    .append(actualCandidateSha256.equals(manifestCandidateSha256))
                    .append('\n');
            builder.append("binding.backendTarget=OPENCL\n");
            builder.append("binding.deviceVendor=").append(propertyValue(deviceVendor)).append('\n');
            builder.append("binding.deviceLabel=").append(propertyValue(deviceLabel)).append('\n');
            builder.append("binding.driverVersion=").append(propertyValue(driverVersion)).append('\n');
            builder.append("binding.kernel.count=").append(kernelCount).append('\n');
            builder.append("authorization.defaultProductionSourceSwitching=disabled\n");
            builder.append("authorization.productionMutation=disabled\n");
            builder.append("authorization.scope=manual-review-only\n");
            builder.append("blocker.count=").append(blockers.size()).append('\n');
            for (int index = 0; index < blockers.size(); index++) {
                builder.append("blocker.").append(index).append('=').append(blockers.get(index)).append('\n');
            }
            builder.append("diagnostic=").append(valid
                    ? "manual promotion manifest is valid for the reviewed candidate; production remains disabled"
                    : "manual promotion manifest is blocked by " + firstBlocker()).append('\n');
            return builder.toString();
        }
    }
}
