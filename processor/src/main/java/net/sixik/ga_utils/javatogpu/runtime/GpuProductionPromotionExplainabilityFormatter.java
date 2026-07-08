package net.sixik.ga_utils.javatogpu.runtime;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Formats backend-neutral production-promotion explainability artifacts.
 *
 * <p>The formatter consumes workload source-promotion evidence plus I3 readiness evidence and emits a compact
 * properties artifact that CI, validation history, and future backend implementations can share.</p>
 */
public final class GpuProductionPromotionExplainabilityFormatter {

    private GpuProductionPromotionExplainabilityFormatter() {
    }

    public static String format(Properties workloadGate, Properties i3Summary) {
        Properties gate = workloadGate == null ? new Properties() : workloadGate;
        Properties readiness = i3Summary == null ? new Properties() : i3Summary;
        if (gate.isEmpty()) {
            return appendContractFields("status=blocked\n"
                    + "productionSourceSwitchingAllowed=false\n"
                    + "productionMutationAllowed=false\n"
                    + "kernel.count=0\n"
                    + "blocker.count=1\n"
                    + "blocker.0=workload-promotion-gate-not-recorded\n"
                    + "diagnostic.0=production promotion is blocked because workload promotion evidence was not recorded\n");
        }

        int kernelCount = parsePositiveInt(gate.getProperty("kernel.count", "0"));
        boolean gateReviewReady = "true".equals(gate.getProperty("reviewReady", "false"));
        boolean sourceParityMatched = "true".equals(gate.getProperty("sourceParityMatched", "false"));
        boolean runtimeEquivalencePassed = "true".equals(gate.getProperty("runtimeEquivalencePassed", "false"));
        boolean productionSourceSwitchingEnabled = "true".equals(gate.getProperty("productionSourceSwitching", "false"))
                || "enabled".equals(gate.getProperty("productionSourceSwitching", "false"));
        boolean productionMutationEnabled = "true".equals(readiness.getProperty("productionMutationEnabled", "false"));
        int i3ReviewReadyCount = parsePositiveInt(readiness.getProperty("reviewReady.count", "0"));
        int i3BlockedCount = parsePositiveInt(readiness.getProperty("blocked.count", Integer.toString(kernelCount)));
        int i3SourceReadyCount = parsePositiveInt(readiness.getProperty("sourceReady.count", "0"));
        boolean allKernelsI3ReviewReady = kernelCount > 0 && i3ReviewReadyCount == kernelCount && i3BlockedCount == 0;
        boolean allKernelsSourceReady = kernelCount > 0 && i3SourceReadyCount == kernelCount;

        List<String> blockers = new ArrayList<>();
        if (!gateReviewReady) {
            blockers.add("workload-source-promotion-gate-not-review-ready");
        }
        if (!sourceParityMatched) {
            blockers.add("source-parity-not-matched");
        }
        if (!runtimeEquivalencePassed) {
            blockers.add("runtime-equivalence-not-passed");
        }
        if (!allKernelsI3ReviewReady) {
            blockers.add("i3-workload-readiness-not-review-ready");
        }
        if (!allKernelsSourceReady) {
            blockers.add("i3-source-readiness-not-complete");
        }
        if (!productionSourceSwitchingEnabled) {
            blockers.add("production-source-switching-disabled");
        }
        if (!productionMutationEnabled) {
            blockers.add("production-mutation-disabled");
        }

        StringBuilder builder = new StringBuilder();
        builder.append("status=").append(blockers.isEmpty() ? "production-ready" : "blocked").append('\n');
        builder.append("gateStatus=").append(gate.getProperty("status", "unknown")).append('\n');
        builder.append("gateReviewReady=").append(gateReviewReady).append('\n');
        builder.append("sourceParityMatched=").append(sourceParityMatched).append('\n');
        builder.append("runtimeEquivalencePassed=").append(runtimeEquivalencePassed).append('\n');
        builder.append("realWorkloadEvidence=").append(gate.getProperty("realWorkloadEvidence", "not-wired")).append('\n');
        builder.append("kernel.count=").append(kernelCount).append('\n');
        builder.append("i3ReviewReady.count=").append(i3ReviewReadyCount).append('\n');
        builder.append("i3Blocked.count=").append(i3BlockedCount).append('\n');
        builder.append("i3SourceReady.count=").append(i3SourceReadyCount).append('\n');
        builder.append("i3SourceReady.all=").append(allKernelsSourceReady).append('\n');
        builder.append("productionSourceSwitchingAllowed=").append(productionSourceSwitchingEnabled && blockers.isEmpty()).append('\n');
        builder.append("productionSourceSwitchingEnabled=").append(productionSourceSwitchingEnabled).append('\n');
        builder.append("productionMutationAllowed=").append(productionMutationEnabled && blockers.isEmpty()).append('\n');
        builder.append("productionMutationEnabled=").append(productionMutationEnabled).append('\n');
        builder.append("blocker.count=").append(blockers.size()).append('\n');
        for (int index = 0; index < blockers.size(); index++) {
            builder.append("blocker.").append(index).append('=').append(blockers.get(index)).append('\n');
        }
        builder.append("diagnostic.0=").append(diagnostic(blockers, i3ReviewReadyCount, i3BlockedCount)).append('\n');
        return appendContractFields(builder.toString());
    }

    private static String appendContractFields(String propertiesText) {
        try {
            Properties properties = new Properties();
            properties.load(new StringReader(propertiesText));
            GpuProductionPromotionExplainabilityValidation.Result contract =
                    GpuProductionPromotionExplainabilityValidation.validate(properties);
            StringBuilder builder = new StringBuilder(propertiesText);
            builder.append("contract.status=").append(contract.valid() ? "valid" : "invalid").append('\n');
            builder.append("contract.valid=").append(contract.valid()).append('\n');
            builder.append("contract.violation.count=").append(contract.violations().size()).append('\n');
            for (int index = 0; index < contract.violations().size(); index++) {
                builder.append("contract.violation.").append(index).append('=').append(contract.violations().get(index)).append('\n');
            }
            builder.append(GpuProductionPromotionDecision.fromExplainability(properties).toPropertiesText());
            return builder.toString();
        } catch (IOException failure) {
            return propertiesText
                    + "contract.status=invalid\n"
                    + "contract.valid=false\n"
                    + "contract.violation.count=1\n"
                    + "contract.violation.0=production-promotion explainability contract could not parse generated properties\n"
                    + "decision.mode=diagnostic-only\n"
                    + "decision.status=blocked\n"
                    + "decision.contractValid=false\n"
                    + "decision.productionSourceSwitchingAllowed=false\n"
                    + "decision.productionMutationAllowed=false\n"
                    + "decision.firstBlocker=none\n"
                    + "decision.firstViolation=production-promotion explainability contract could not parse generated properties\n"
                    + "decision.diagnostic=production promotion remains diagnostic-only because the explainability contract is invalid\n";
        }
    }

    private static String diagnostic(List<String> blockers, int i3ReviewReadyCount, int i3BlockedCount) {
        if (blockers.isEmpty()) {
            return "production promotion gates are ready for explicit operator review";
        }
        return "production promotion remains blocked: first=" + blockers.get(0)
                + ", i3ReviewReady=" + i3ReviewReadyCount
                + ", i3Blocked=" + i3BlockedCount;
    }

    private static int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value == null ? "0" : value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
