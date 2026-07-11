package net.sixik.ga_utils.javatogpu.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Fail-closed production-candidate view that joins real workload readiness with identity-bound operator acceptance.
 */
public record GpuBackendSourcePromotionCandidateGate(
        String status,
        boolean reviewReady,
        int kernelCount,
        int candidateReadyCount,
        int operatorAcceptedCount,
        int operatorBoundCount,
        boolean sourceParityMatched,
        boolean runtimeEquivalencePassed,
        String controlledSourceSwitchingStatus,
        String operatorAcceptanceMode,
        String deviceVendor,
        String deviceLabel,
        String driverVersion,
        List<KernelCandidate> kernels,
        List<String> blockers
) {

    public GpuBackendSourcePromotionCandidateGate {
        status = normalize(status, reviewReady ? "review-ready" : "blocked");
        controlledSourceSwitchingStatus = normalize(controlledSourceSwitchingStatus, "not-recorded");
        operatorAcceptanceMode = normalize(operatorAcceptanceMode, "not-recorded");
        deviceVendor = normalize(deviceVendor, "unknown");
        deviceLabel = normalize(deviceLabel, "unknown");
        driverVersion = normalize(driverVersion, "unknown");
        kernels = kernels == null ? List.of() : List.copyOf(kernels);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        if (reviewReady && !blockers.isEmpty()) {
            throw new IllegalArgumentException("Review-ready production candidate gate must not contain blockers");
        }
    }

    public static GpuBackendSourcePromotionCandidateGate from(
            Properties workloadGate,
            Properties controlledSourceSwitching
    ) {
        Properties workload = workloadGate == null ? new Properties() : workloadGate;
        Properties controlled = controlledSourceSwitching == null ? new Properties() : controlledSourceSwitching;
        int workloadKernelCount = parseInt(workload.getProperty("kernel.count"), 0);
        int controlledKernelCount = parseInt(controlled.getProperty("kernel.count"), 0);
        Map<String, ControlledKernel> controlledByResource = new LinkedHashMap<>();
        for (int index = 0; index < controlledKernelCount; index++) {
            String prefix = "kernel." + index + ".";
            String resource = controlled.getProperty(prefix + "resource", "");
            if (!resource.isBlank()) {
                controlledByResource.put(resource, new ControlledKernel(
                        controlled.getProperty(prefix + "status", "not-recorded"),
                        controlled.getProperty(prefix + "operatorAcceptance.id", "acceptance:missing"),
                        controlled.getProperty(prefix + "operatorAcceptance.status", "not-recorded"),
                        parseBoolean(controlled.getProperty(prefix + "operatorAcceptance.bound"))
                ));
            }
        }

        ArrayList<KernelCandidate> kernels = new ArrayList<>();
        LinkedHashSet<String> aggregateBlockers = new LinkedHashSet<>();
        int candidateReadyCount = 0;
        int acceptedCount = 0;
        int boundCount = 0;
        boolean allParityMatched = workloadKernelCount > 0;
        boolean allRuntimeEquivalent = workloadKernelCount > 0;
        for (int index = 0; index < workloadKernelCount; index++) {
            String prefix = "kernel." + index + ".";
            String resource = workload.getProperty(prefix + "sourceKernelResource", "unknown");
            boolean workloadReviewReady = parseBoolean(workload.getProperty(prefix + "reviewReady"));
            boolean parityMatched = parseBoolean(workload.getProperty(prefix + "sourceParityMatched"));
            boolean runtimeEquivalent = parseBoolean(workload.getProperty(prefix + "runtimeEquivalencePassed"));
            ControlledKernel controlledKernel = controlledByResource.get(resource);
            ArrayList<String> kernelBlockers = new ArrayList<>();
            if (!workloadReviewReady) {
                kernelBlockers.add("workload-kernel-not-review-ready");
            }
            if (!parityMatched) {
                kernelBlockers.add("workload-source-parity-not-matched");
            }
            if (!runtimeEquivalent) {
                kernelBlockers.add("workload-runtime-equivalence-not-passed");
            }
            if (controlledKernel == null) {
                kernelBlockers.add("controlled-kernel-evidence-missing");
            } else {
                if (!"passed".equals(controlledKernel.status())) {
                    kernelBlockers.add("controlled-kernel-not-passed");
                }
                if (!"accepted".equals(controlledKernel.acceptanceStatus())) {
                    kernelBlockers.add("operator-acceptance-not-accepted");
                } else {
                    acceptedCount++;
                }
                if (!controlledKernel.acceptanceBound()) {
                    kernelBlockers.add("operator-acceptance-not-bound");
                } else {
                    boundCount++;
                }
            }
            boolean candidateReady = kernelBlockers.isEmpty();
            if (candidateReady) {
                candidateReadyCount++;
            } else {
                aggregateBlockers.addAll(kernelBlockers);
            }
            allParityMatched &= parityMatched;
            allRuntimeEquivalent &= runtimeEquivalent;
            kernels.add(new KernelCandidate(
                    resource,
                    workloadReviewReady,
                    parityMatched,
                    runtimeEquivalent,
                    controlledKernel == null ? "not-recorded" : controlledKernel.status(),
                    controlledKernel == null ? "acceptance:missing" : controlledKernel.acceptanceId(),
                    controlledKernel == null ? "not-recorded" : controlledKernel.acceptanceStatus(),
                    controlledKernel != null && controlledKernel.acceptanceBound(),
                    candidateReady,
                    kernelBlockers
            ));
        }

        boolean workloadReviewReady = parseBoolean(workload.getProperty("reviewReady"));
        boolean controlledPassed = "passed".equals(controlled.getProperty("status"));
        boolean controlledReviewReady = parseBoolean(controlled.getProperty("reviewReady"));
        boolean identityBoundMode = "identity-bound".equals(controlled.getProperty("operatorAcceptance.mode"));
        boolean globalAcceptanceBound = parseBoolean(controlled.getProperty("operatorAcceptance.bound"));
        if (workloadKernelCount <= 0) {
            aggregateBlockers.add("workload-kernels-missing");
        }
        if (!workloadReviewReady) {
            aggregateBlockers.add("workload-gate-not-review-ready");
        }
        if (!controlledPassed || !controlledReviewReady) {
            aggregateBlockers.add("controlled-source-switching-not-passed");
        }
        if (!identityBoundMode) {
            aggregateBlockers.add("operator-acceptance-mode-not-identity-bound");
        }
        if (!globalAcceptanceBound) {
            aggregateBlockers.add("operator-acceptance-context-not-bound");
        }
        boolean reviewReady = aggregateBlockers.isEmpty()
                && candidateReadyCount == workloadKernelCount
                && acceptedCount == workloadKernelCount
                && boundCount == workloadKernelCount;
        return new GpuBackendSourcePromotionCandidateGate(
                reviewReady ? "review-ready" : "blocked",
                reviewReady,
                workloadKernelCount,
                candidateReadyCount,
                acceptedCount,
                boundCount,
                allParityMatched,
                allRuntimeEquivalent,
                controlled.getProperty("status", "not-recorded"),
                controlled.getProperty("operatorAcceptance.mode", "not-recorded"),
                controlled.getProperty("operatorAcceptance.deviceVendor", "unknown"),
                controlled.getProperty("operatorAcceptance.deviceLabel", "unknown"),
                controlled.getProperty("operatorAcceptance.driverVersion", "unknown"),
                kernels,
                List.copyOf(aggregateBlockers)
        );
    }

    public String toPropertiesText() {
        StringBuilder builder = new StringBuilder();
        builder.append("formatVersion=1\n");
        builder.append("status=").append(status).append('\n');
        builder.append("reviewReady=").append(reviewReady).append('\n');
        builder.append("scope=real-workload-production-candidate\n");
        builder.append("defaultProductionSourceSwitching=disabled\n");
        builder.append("candidateProductionSourceSwitching=")
                .append(reviewReady ? "review-ready" : "blocked")
                .append('\n');
        builder.append("productionMutation=disabled\n");
        builder.append("kernel.count=").append(kernelCount).append('\n');
        builder.append("candidateReady.count=").append(candidateReadyCount).append('\n');
        builder.append("candidateReady.all=").append(candidateReadyCount == kernelCount && kernelCount > 0).append('\n');
        builder.append("sourceParityMatched=").append(sourceParityMatched).append('\n');
        builder.append("runtimeEquivalencePassed=").append(runtimeEquivalencePassed).append('\n');
        builder.append("controlledSourceSwitching.status=").append(controlledSourceSwitchingStatus).append('\n');
        builder.append("operatorAcceptance.mode=").append(operatorAcceptanceMode).append('\n');
        builder.append("operatorAcceptance.accepted.count=").append(operatorAcceptedCount).append('\n');
        builder.append("operatorAcceptance.accepted.all=")
                .append(operatorAcceptedCount == kernelCount && kernelCount > 0)
                .append('\n');
        builder.append("operatorAcceptance.bound.count=").append(operatorBoundCount).append('\n');
        builder.append("operatorAcceptance.bound.all=")
                .append(operatorBoundCount == kernelCount && kernelCount > 0)
                .append('\n');
        builder.append("operatorAcceptance.deviceVendor=").append(propertyValue(deviceVendor)).append('\n');
        builder.append("operatorAcceptance.deviceLabel=").append(propertyValue(deviceLabel)).append('\n');
        builder.append("operatorAcceptance.driverVersion=").append(propertyValue(driverVersion)).append('\n');
        for (int index = 0; index < kernels.size(); index++) {
            KernelCandidate kernel = kernels.get(index);
            String prefix = "kernel." + index + ".";
            builder.append(prefix).append("resource=").append(propertyValue(kernel.resource())).append('\n');
            builder.append(prefix).append("workloadReviewReady=").append(kernel.workloadReviewReady()).append('\n');
            builder.append(prefix).append("sourceParityMatched=").append(kernel.sourceParityMatched()).append('\n');
            builder.append(prefix).append("runtimeEquivalencePassed=").append(kernel.runtimeEquivalencePassed()).append('\n');
            builder.append(prefix).append("controlledStatus=").append(kernel.controlledStatus()).append('\n');
            builder.append(prefix).append("operatorAcceptance.id=")
                    .append(propertyValue(kernel.operatorAcceptanceId()))
                    .append('\n');
            builder.append(prefix).append("operatorAcceptance.status=")
                    .append(kernel.operatorAcceptanceStatus())
                    .append('\n');
            builder.append(prefix).append("operatorAcceptance.bound=")
                    .append(kernel.operatorAcceptanceBound())
                    .append('\n');
            builder.append(prefix).append("candidateReady=").append(kernel.candidateReady()).append('\n');
            builder.append(prefix).append("blocker.count=").append(kernel.blockers().size()).append('\n');
            for (int blockerIndex = 0; blockerIndex < kernel.blockers().size(); blockerIndex++) {
                builder.append(prefix).append("blocker.").append(blockerIndex).append('=')
                        .append(kernel.blockers().get(blockerIndex))
                        .append('\n');
            }
        }
        builder.append("blocker.count=").append(blockers.size()).append('\n');
        for (int index = 0; index < blockers.size(); index++) {
            builder.append("blocker.").append(index).append('=').append(blockers.get(index)).append('\n');
        }
        builder.append("diagnostic=").append(reviewReady
                ? "real workload production candidate is review-ready; default production source switching remains disabled"
                : "real workload production candidate is blocked by " + firstBlocker()).append('\n');
        return builder.toString();
    }

    public String firstBlocker() {
        return blockers.isEmpty() ? "none" : blockers.get(0);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value) {
        return Boolean.parseBoolean(value);
    }

    private static String propertyValue(String value) {
        return normalize(value, "unknown").replace('\\', '/').replace('\r', ' ').replace('\n', ' ');
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record KernelCandidate(
            String resource,
            boolean workloadReviewReady,
            boolean sourceParityMatched,
            boolean runtimeEquivalencePassed,
            String controlledStatus,
            String operatorAcceptanceId,
            String operatorAcceptanceStatus,
            boolean operatorAcceptanceBound,
            boolean candidateReady,
            List<String> blockers
    ) {
        public KernelCandidate {
            resource = normalize(resource, "unknown");
            controlledStatus = normalize(controlledStatus, "not-recorded");
            operatorAcceptanceId = normalize(operatorAcceptanceId, "acceptance:missing");
            operatorAcceptanceStatus = normalize(operatorAcceptanceStatus, "not-recorded");
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }

    private record ControlledKernel(
            String status,
            String acceptanceId,
            String acceptanceStatus,
            boolean acceptanceBound
    ) {
    }
}
