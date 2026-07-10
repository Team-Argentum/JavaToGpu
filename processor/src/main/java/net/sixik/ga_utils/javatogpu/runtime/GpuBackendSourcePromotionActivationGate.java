package net.sixik.ga_utils.javatogpu.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Fail-closed readiness gate for explicitly controlled activation of a manually approved production candidate.
 */
public record GpuBackendSourcePromotionActivationGate(
        String status,
        boolean activationReady,
        String activationScope,
        String approvalId,
        String candidateGitSha,
        String deviceVendor,
        String deviceLabel,
        String driverVersion,
        int kernelCount,
        int controlledCoverageCount,
        int operatorAcceptedCount,
        int operatorBoundCount,
        List<KernelActivation> kernels,
        List<String> blockers
) {

    public static final String ACTIVATION_SCOPE = "controlled-opt-in-only";

    public GpuBackendSourcePromotionActivationGate {
        status = normalize(status, activationReady ? "controlled-activation-ready" : "blocked");
        activationScope = normalize(activationScope, ACTIVATION_SCOPE);
        approvalId = normalize(approvalId, "approval:missing");
        candidateGitSha = normalize(candidateGitSha, "unknown");
        deviceVendor = normalize(deviceVendor, "unknown");
        deviceLabel = normalize(deviceLabel, "unknown");
        driverVersion = normalize(driverVersion, "unknown");
        kernels = kernels == null ? List.of() : List.copyOf(kernels);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        if (activationReady && !blockers.isEmpty()) {
            throw new IllegalArgumentException("Activation-ready gate must not contain blockers");
        }
    }

    public static GpuBackendSourcePromotionActivationGate from(
            Properties candidateGate,
            Properties manifestValidation,
            Properties controlledSourceSwitching
    ) {
        Properties candidate = candidateGate == null ? new Properties() : candidateGate;
        Properties manifest = manifestValidation == null ? new Properties() : manifestValidation;
        Properties controlled = controlledSourceSwitching == null ? new Properties() : controlledSourceSwitching;
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        int kernelCount = parseInt(candidate.getProperty("kernel.count"), 0);

        requireEquals(blockers, candidate, "status", "review-ready", "candidate-status-not-review-ready");
        requireTrue(blockers, candidate, "reviewReady", "candidate-review-ready-false");
        requireTrue(blockers, candidate, "candidateReady.all", "candidate-kernels-not-all-ready");
        requireTrue(blockers, candidate, "sourceParityMatched", "candidate-source-parity-not-matched");
        requireTrue(blockers, candidate, "runtimeEquivalencePassed", "candidate-runtime-equivalence-not-passed");
        requireTrue(blockers, candidate, "operatorAcceptance.accepted.all", "candidate-operator-acceptance-not-all-accepted");
        requireTrue(blockers, candidate, "operatorAcceptance.bound.all", "candidate-operator-acceptance-not-all-bound");
        requireEquals(
                blockers,
                candidate,
                "defaultProductionSourceSwitching",
                "disabled",
                "candidate-default-production-source-switching-not-disabled"
        );
        requireEquals(
                blockers,
                candidate,
                "productionMutation",
                "disabled",
                "candidate-production-mutation-not-disabled"
        );
        if (kernelCount <= 0) {
            blockers.add("candidate-kernels-missing");
        }

        requireEquals(blockers, manifest, "status", "approved", "manifest-validation-not-approved");
        requireTrue(blockers, manifest, "valid", "manifest-validation-invalid");
        requireEquals(
                blockers,
                manifest,
                "scope",
                GpuBackendSourcePromotionManifest.SCOPE + "-validation",
                "manifest-validation-scope-mismatch"
        );
        requireTrue(blockers, manifest, "binding.gitShaMatched", "manifest-git-sha-not-matched");
        requireTrue(
                blockers,
                manifest,
                "binding.candidateArtifactSha256Matched",
                "manifest-candidate-artifact-sha256-not-matched"
        );
        requireNonBlankEqual(
                blockers,
                manifest,
                "binding.expectedGitSha",
                "binding.manifestGitSha",
                "manifest-git-sha-values-mismatch"
        );
        requireNonBlankEqual(
                blockers,
                manifest,
                "binding.actualCandidateArtifact.sha256",
                "binding.manifestCandidateArtifact.sha256",
                "manifest-candidate-artifact-sha256-values-mismatch"
        );
        requireEquals(blockers, manifest, "binding.backendTarget", "OPENCL", "manifest-backend-target-mismatch");
        requireEquals(
                blockers,
                manifest,
                "authorization.defaultProductionSourceSwitching",
                "disabled",
                "manifest-default-production-source-switching-not-disabled"
        );
        requireEquals(
                blockers,
                manifest,
                "authorization.productionMutation",
                "disabled",
                "manifest-production-mutation-not-disabled"
        );
        requireEquals(
                blockers,
                manifest,
                "authorization.scope",
                "manual-review-only",
                "manifest-authorization-scope-mismatch"
        );
        if (parseInt(manifest.getProperty("binding.kernel.count"), -1) != kernelCount) {
            blockers.add("manifest-kernel-count-mismatch");
        }
        if (parseInt(manifest.getProperty("blocker.count"), -1) != 0) {
            blockers.add("manifest-validation-has-blockers");
        }

        requireEquals(blockers, controlled, "status", "passed", "controlled-source-switching-not-passed");
        requireTrue(blockers, controlled, "reviewReady", "controlled-source-switching-not-review-ready");
        requireEquals(
                blockers,
                controlled,
                "operatorAcceptance.mode",
                "identity-bound",
                "controlled-operator-acceptance-mode-not-identity-bound"
        );
        requireTrue(blockers, controlled, "operatorAcceptance.bound", "controlled-operator-acceptance-not-bound");

        String candidateVendor = candidate.getProperty("operatorAcceptance.deviceVendor", "unknown");
        String candidateLabel = candidate.getProperty("operatorAcceptance.deviceLabel", "unknown");
        String candidateDriver = candidate.getProperty("operatorAcceptance.driverVersion", "unknown");
        requireIdentityMatch(blockers, manifest, "binding.deviceVendor", candidateVendor, "manifest-device-vendor-mismatch");
        requireIdentityMatch(blockers, manifest, "binding.deviceLabel", candidateLabel, "manifest-device-label-mismatch");
        requireIdentityMatch(blockers, manifest, "binding.driverVersion", candidateDriver, "manifest-driver-version-mismatch");
        requireIdentityMatch(blockers, controlled, "operatorAcceptance.deviceVendor", candidateVendor, "controlled-device-vendor-mismatch");
        requireIdentityMatch(blockers, controlled, "operatorAcceptance.deviceLabel", candidateLabel, "controlled-device-label-mismatch");
        requireIdentityMatch(blockers, controlled, "operatorAcceptance.driverVersion", candidateDriver, "controlled-driver-version-mismatch");

        Map<String, ControlledKernel> controlledByResource = controlledKernels(controlled);
        ArrayList<KernelActivation> kernels = new ArrayList<>();
        int coverageCount = 0;
        int acceptedCount = 0;
        int boundCount = 0;
        for (int index = 0; index < kernelCount; index++) {
            String resource = normalize(candidate.getProperty("kernel." + index + ".resource"), "unknown");
            ControlledKernel controlledKernel = controlledByResource.get(resource);
            ArrayList<String> kernelBlockers = new ArrayList<>();
            if ("unknown".equals(resource)) {
                kernelBlockers.add("candidate-kernel-resource-missing");
            }
            if (controlledKernel == null) {
                kernelBlockers.add("controlled-kernel-evidence-missing");
            } else {
                coverageCount++;
                if (!"passed".equals(controlledKernel.status())) {
                    kernelBlockers.add("controlled-kernel-not-passed");
                }
                if (!"accepted".equals(controlledKernel.operatorAcceptanceStatus())) {
                    kernelBlockers.add("controlled-kernel-operator-acceptance-not-accepted");
                } else {
                    acceptedCount++;
                }
                if (!controlledKernel.operatorAcceptanceBound()) {
                    kernelBlockers.add("controlled-kernel-operator-acceptance-not-bound");
                } else {
                    boundCount++;
                }
            }
            if (!kernelBlockers.isEmpty()) {
                blockers.addAll(kernelBlockers);
            }
            kernels.add(new KernelActivation(
                    resource,
                    controlledKernel == null ? "not-recorded" : controlledKernel.status(),
                    controlledKernel == null ? "not-recorded" : controlledKernel.operatorAcceptanceStatus(),
                    controlledKernel != null && controlledKernel.operatorAcceptanceBound(),
                    kernelBlockers.isEmpty(),
                    kernelBlockers
            ));
        }
        boolean activationReady = blockers.isEmpty()
                && coverageCount == kernelCount
                && acceptedCount == kernelCount
                && boundCount == kernelCount;
        return new GpuBackendSourcePromotionActivationGate(
                activationReady ? "controlled-activation-ready" : "blocked",
                activationReady,
                ACTIVATION_SCOPE,
                manifest.getProperty("approval.id", "approval:missing"),
                manifest.getProperty("binding.expectedGitSha", "unknown"),
                candidateVendor,
                candidateLabel,
                candidateDriver,
                kernelCount,
                coverageCount,
                acceptedCount,
                boundCount,
                kernels,
                List.copyOf(blockers)
        );
    }

    public String firstBlocker() {
        return blockers.isEmpty() ? "none" : blockers.get(0);
    }

    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("formatVersion=1\n");
        builder.append("status=").append(status).append('\n');
        builder.append("activationReady=").append(activationReady).append('\n');
        builder.append("activationScope=").append(activationScope).append('\n');
        builder.append("defaultRuntimeActivation=false\n");
        builder.append("defaultProductionSourceSwitching=disabled\n");
        builder.append("productionMutation=disabled\n");
        builder.append("manifest.approval.id=").append(propertyValue(approvalId)).append('\n');
        builder.append("manifest.candidateGitSha=").append(candidateGitSha).append('\n');
        builder.append("deviceVendor=").append(propertyValue(deviceVendor)).append('\n');
        builder.append("deviceLabel=").append(propertyValue(deviceLabel)).append('\n');
        builder.append("driverVersion=").append(propertyValue(driverVersion)).append('\n');
        builder.append("kernel.count=").append(kernelCount).append('\n');
        builder.append("controlledCoverage.count=").append(controlledCoverageCount).append('\n');
        builder.append("controlledCoverage.all=")
                .append(kernelCount > 0 && controlledCoverageCount == kernelCount)
                .append('\n');
        builder.append("operatorAcceptance.accepted.count=").append(operatorAcceptedCount).append('\n');
        builder.append("operatorAcceptance.accepted.all=")
                .append(kernelCount > 0 && operatorAcceptedCount == kernelCount)
                .append('\n');
        builder.append("operatorAcceptance.bound.count=").append(operatorBoundCount).append('\n');
        builder.append("operatorAcceptance.bound.all=")
                .append(kernelCount > 0 && operatorBoundCount == kernelCount)
                .append('\n');
        for (int index = 0; index < kernels.size(); index++) {
            KernelActivation kernel = kernels.get(index);
            String prefix = "kernel." + index + ".";
            builder.append(prefix).append("resource=").append(propertyValue(kernel.resource())).append('\n');
            builder.append(prefix).append("controlledStatus=").append(kernel.controlledStatus()).append('\n');
            builder.append(prefix).append("operatorAcceptance.status=")
                    .append(kernel.operatorAcceptanceStatus()).append('\n');
            builder.append(prefix).append("operatorAcceptance.bound=")
                    .append(kernel.operatorAcceptanceBound()).append('\n');
            builder.append(prefix).append("activationReady=").append(kernel.activationReady()).append('\n');
            builder.append(prefix).append("blocker.count=").append(kernel.blockers().size()).append('\n');
            for (int blockerIndex = 0; blockerIndex < kernel.blockers().size(); blockerIndex++) {
                builder.append(prefix).append("blocker.").append(blockerIndex).append('=')
                        .append(kernel.blockers().get(blockerIndex)).append('\n');
            }
        }
        builder.append("blocker.count=").append(blockers.size()).append('\n');
        for (int index = 0; index < blockers.size(); index++) {
            builder.append("blocker.").append(index).append('=').append(blockers.get(index)).append('\n');
        }
        builder.append("diagnostic=").append(activationReady
                ? "controlled opt-in activation is ready; default runtime activation remains disabled"
                : "controlled opt-in activation is blocked by " + firstBlocker()).append('\n');
        return builder.toString();
    }

    private static Map<String, ControlledKernel> controlledKernels(Properties controlled) {
        int kernelCount = parseInt(controlled.getProperty("kernel.count"), 0);
        LinkedHashMap<String, ControlledKernel> byResource = new LinkedHashMap<>();
        for (int index = 0; index < kernelCount; index++) {
            String prefix = "kernel." + index + ".";
            String resource = controlled.getProperty(prefix + "resource", "");
            if (!resource.isBlank()) {
                byResource.put(resource, new ControlledKernel(
                        controlled.getProperty(prefix + "status", "not-recorded"),
                        controlled.getProperty(prefix + "operatorAcceptance.status", "not-recorded"),
                        Boolean.parseBoolean(controlled.getProperty(prefix + "operatorAcceptance.bound"))
                ));
            }
        }
        return Map.copyOf(byResource);
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

    private static void requireTrue(
            LinkedHashSet<String> blockers,
            Properties properties,
            String key,
            String blocker
    ) {
        if (!Boolean.parseBoolean(properties.getProperty(key))) {
            blockers.add(blocker);
        }
    }

    private static void requireNonBlankEqual(
            LinkedHashSet<String> blockers,
            Properties properties,
            String firstKey,
            String secondKey,
            String blocker
    ) {
        String first = normalize(properties.getProperty(firstKey), "missing");
        String second = normalize(properties.getProperty(secondKey), "missing");
        if ("missing".equals(first) || !first.equals(second)) {
            blockers.add(blocker);
        }
    }

    private static void requireIdentityMatch(
            LinkedHashSet<String> blockers,
            Properties properties,
            String key,
            String expected,
            String blocker
    ) {
        if (!normalize(expected, "unknown").equals(normalize(properties.getProperty(key), "missing"))) {
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

    private static String propertyValue(String value) {
        return normalize(value, "unknown").replace('\\', '/').replace('\r', ' ').replace('\n', ' ');
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record KernelActivation(
            String resource,
            String controlledStatus,
            String operatorAcceptanceStatus,
            boolean operatorAcceptanceBound,
            boolean activationReady,
            List<String> blockers
    ) {
        public KernelActivation {
            resource = normalize(resource, "unknown");
            controlledStatus = normalize(controlledStatus, "not-recorded");
            operatorAcceptanceStatus = normalize(operatorAcceptanceStatus, "not-recorded");
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }

    private record ControlledKernel(
            String status,
            String operatorAcceptanceStatus,
            boolean operatorAcceptanceBound
    ) {
    }
}
