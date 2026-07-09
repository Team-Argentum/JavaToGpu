package net.sixik.ga_utils.javatogpu.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Backend-neutral contract validator for production-promotion explainability artifacts.
 *
 * <p>The validator intentionally models both current fail-closed artifacts and future production-ready artifacts. This
 * keeps OpenCL, CUDA, Vulkan, and Metal promotion checks on the same rules before runtime IR mutation can become a
 * production path.</p>
 */
public final class GpuProductionPromotionExplainabilityValidation {

    public static final String BLOCKED = "blocked";
    public static final String PRODUCTION_READY = "production-ready";

    private GpuProductionPromotionExplainabilityValidation() {
    }

    public static Result validate(Properties explainability) {
        Properties properties = Objects.requireNonNull(explainability, "explainability");
        String status = properties.getProperty("status", BLOCKED);
        int kernelCount = parseInt(properties.getProperty("kernel.count"));
        int i3ReviewReadyCount = parseInt(properties.getProperty("i3ReviewReady.count"));
        int i3BlockedCount = parseInt(properties.getProperty("i3Blocked.count"));
        int i3SourceReadyCount = parseInt(properties.getProperty("i3SourceReady.count"));
        int blockerCount = parseInt(properties.getProperty("blocker.count"));
        int productionSourceSwitchingEnabledCount = parseInt(properties.getProperty("productionSourceSwitchingEnabled.count"));
        int productionPromotionDecisionEnabledCount = parseInt(properties.getProperty("productionPromotionDecisionMode.productionEnabled.count"));
        int productionSourceDecisionCount = parseInt(properties.getProperty("sourceSwitching.productionDecision.count"));
        boolean sourceSwitchingAllowed = propertyIsTrue(properties, "productionSourceSwitchingAllowed");
        boolean sourceSwitchingEnabled = propertyIsTrue(properties, "productionSourceSwitchingEnabled");
        boolean allSourceSwitchingEnabled = propertyIsTrue(properties, "productionSourceSwitchingEnabled.all");
        boolean allPromotionDecisionsEnabled = propertyIsTrue(properties, "productionPromotionDecisionMode.productionEnabled.all");
        boolean allProductionSourceDecisions = propertyIsTrue(properties, "sourceSwitching.productionDecision.all");
        boolean mutationAllowed = propertyIsTrue(properties, "productionMutationAllowed");
        boolean mutationEnabled = propertyIsTrue(properties, "productionMutationEnabled");
        boolean backendPromotionArtifactSupportComplete = propertyIsTrue(
                properties,
                "backendPromotionArtifactSupport.complete",
                true
        );

        ArrayList<String> violations = new ArrayList<>();
        if (kernelCount <= 0) {
            violations.add("production-promotion explainability has no workload kernels");
        }
        if (i3ReviewReadyCount + i3BlockedCount > kernelCount) {
            violations.add("production-promotion explainability has inconsistent I3 counts");
        }

        if (PRODUCTION_READY.equals(status)) {
            validateProductionReady(
                    kernelCount,
                    i3ReviewReadyCount,
                    i3BlockedCount,
                    i3SourceReadyCount,
                    blockerCount,
                    productionSourceSwitchingEnabledCount,
                    productionPromotionDecisionEnabledCount,
                    productionSourceDecisionCount,
                    sourceSwitchingAllowed,
                    sourceSwitchingEnabled,
                    allSourceSwitchingEnabled,
                    allPromotionDecisionsEnabled,
                    allProductionSourceDecisions,
                    mutationAllowed,
                    mutationEnabled,
                    backendPromotionArtifactSupportComplete,
                    violations
            );
        } else if (BLOCKED.equals(status)) {
            validateBlocked(
                    blockerCount,
                    sourceSwitchingAllowed,
                    sourceSwitchingEnabled,
                    allSourceSwitchingEnabled,
                    allPromotionDecisionsEnabled,
                    allProductionSourceDecisions,
                    mutationAllowed,
                    mutationEnabled,
                    violations
            );
        } else {
            violations.add("production-promotion explainability has unsupported status=" + status);
        }

        return new Result(
                violations.isEmpty(),
                status,
                kernelCount,
                i3ReviewReadyCount,
                i3BlockedCount,
                i3SourceReadyCount,
                blockerCount,
                productionSourceSwitchingEnabledCount,
                productionPromotionDecisionEnabledCount,
                productionSourceDecisionCount,
                sourceSwitchingAllowed,
                sourceSwitchingEnabled,
                allSourceSwitchingEnabled,
                allPromotionDecisionsEnabled,
                allProductionSourceDecisions,
                mutationAllowed,
                mutationEnabled,
                violations
        );
    }

    private static void validateProductionReady(
            int kernelCount,
            int i3ReviewReadyCount,
            int i3BlockedCount,
            int i3SourceReadyCount,
            int blockerCount,
            int productionSourceSwitchingEnabledCount,
            int productionPromotionDecisionEnabledCount,
            int productionSourceDecisionCount,
            boolean sourceSwitchingAllowed,
            boolean sourceSwitchingEnabled,
            boolean allSourceSwitchingEnabled,
            boolean allPromotionDecisionsEnabled,
            boolean allProductionSourceDecisions,
            boolean mutationAllowed,
            boolean mutationEnabled,
            boolean backendPromotionArtifactSupportComplete,
            List<String> violations
    ) {
        if (blockerCount != 0) {
            violations.add("production-ready explainability still has blockers");
        }
        if (!sourceSwitchingAllowed || !sourceSwitchingEnabled || !mutationAllowed || !mutationEnabled) {
            violations.add("production-ready explainability does not have every production toggle enabled");
        }
        if (productionSourceSwitchingEnabledCount != kernelCount || !allSourceSwitchingEnabled) {
            violations.add("production-ready explainability does not have source switching enabled for every workload kernel");
        }
        if (productionPromotionDecisionEnabledCount != kernelCount || !allPromotionDecisionsEnabled) {
            violations.add("production-ready explainability does not have production promotion decisions enabled for every workload kernel");
        }
        if (productionSourceDecisionCount != kernelCount || !allProductionSourceDecisions) {
            violations.add("production-ready explainability does not have production IrGpu source decisions for every workload kernel");
        }
        if (i3ReviewReadyCount != kernelCount || i3BlockedCount != 0) {
            violations.add("production-ready explainability does not have every workload kernel I3 review-ready");
        }
        if (i3SourceReadyCount != kernelCount) {
            violations.add("production-ready explainability does not have every workload kernel source-ready");
        }
        if (!backendPromotionArtifactSupportComplete) {
            violations.add("production-ready explainability does not have complete backend promotion artifact support");
        }
    }

    private static void validateBlocked(
            int blockerCount,
            boolean sourceSwitchingAllowed,
            boolean sourceSwitchingEnabled,
            boolean allSourceSwitchingEnabled,
            boolean allPromotionDecisionsEnabled,
            boolean allProductionSourceDecisions,
            boolean mutationAllowed,
            boolean mutationEnabled,
            List<String> violations
    ) {
        if (blockerCount <= 0) {
            violations.add("blocked explainability must include at least one blocker");
        }
        if (sourceSwitchingAllowed || mutationAllowed) {
            violations.add("blocked explainability cannot allow production source switching or mutation");
        }
    }

    private static boolean propertyIsTrue(Properties properties, String key) {
        return "true".equals(properties.getProperty(key, "false"));
    }

    private static boolean propertyIsTrue(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : "true".equals(value);
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value == null ? "0" : value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public record Result(
            boolean valid,
            String status,
            int kernelCount,
            int i3ReviewReadyCount,
            int i3BlockedCount,
            int i3SourceReadyCount,
            int blockerCount,
            int productionSourceSwitchingEnabledCount,
            int productionPromotionDecisionEnabledCount,
            int productionSourceDecisionCount,
            boolean sourceSwitchingAllowed,
            boolean sourceSwitchingEnabled,
            boolean allSourceSwitchingEnabled,
            boolean allPromotionDecisionsEnabled,
            boolean allProductionSourceDecisions,
            boolean mutationAllowed,
            boolean mutationEnabled,
            List<String> violations
    ) {

        public Result {
            status = status == null || status.isBlank() ? BLOCKED : status;
            kernelCount = Math.max(0, kernelCount);
            i3ReviewReadyCount = Math.max(0, i3ReviewReadyCount);
            i3BlockedCount = Math.max(0, i3BlockedCount);
            i3SourceReadyCount = Math.max(0, i3SourceReadyCount);
            blockerCount = Math.max(0, blockerCount);
            productionSourceSwitchingEnabledCount = Math.max(0, productionSourceSwitchingEnabledCount);
            productionPromotionDecisionEnabledCount = Math.max(0, productionPromotionDecisionEnabledCount);
            productionSourceDecisionCount = Math.max(0, productionSourceDecisionCount);
            violations = violations == null ? List.of() : List.copyOf(violations);
        }

        public String summary() {
            return "status=" + status
                    + ", kernels=" + kernelCount
                    + ", sourceReady=" + i3SourceReadyCount
                    + ", sourceSwitchingEnabled=" + productionSourceSwitchingEnabledCount
                    + ", productionDecisions=" + productionSourceDecisionCount
                    + ", blockers=" + blockerCount
                    + ", sourceSwitchingAllowed=" + sourceSwitchingAllowed
                    + ", mutationAllowed=" + mutationAllowed;
        }

        public String firstViolation() {
            return violations.isEmpty() ? "none" : violations.get(0);
        }
    }
}
