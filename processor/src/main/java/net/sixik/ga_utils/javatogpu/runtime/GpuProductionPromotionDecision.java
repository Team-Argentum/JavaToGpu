package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Objects;
import java.util.Properties;

/**
 * Runtime-facing decision derived from production-promotion explainability evidence.
 *
 * <p>The decision is intentionally small: runtime code should not re-interpret every promotion property. It only needs
 * to know whether the current backend/IR path is diagnostic-only, review-ready for an operator, or explicitly enabled
 * for production mutation.</p>
 */
public record GpuProductionPromotionDecision(
        String mode,
        String status,
        boolean contractValid,
        boolean productionSourceSwitchingAllowed,
        boolean productionMutationAllowed,
        String firstBlocker,
        String firstViolation,
        String diagnostic
) {

    public static final String DIAGNOSTIC_ONLY = "diagnostic-only";
    public static final String REVIEW_READY = "review-ready";
    public static final String PRODUCTION_ENABLED = "production-enabled";

    public GpuProductionPromotionDecision {
        mode = normalize(mode, DIAGNOSTIC_ONLY);
        status = normalize(status, GpuProductionPromotionExplainabilityValidation.BLOCKED);
        firstBlocker = normalize(firstBlocker, "none");
        firstViolation = normalize(firstViolation, "none");
        diagnostic = normalize(diagnostic, "production promotion decision was derived from explainability evidence");
    }

    public static GpuProductionPromotionDecision fromExplainability(Properties explainability) {
        Properties properties = Objects.requireNonNull(explainability, "explainability");
        GpuProductionPromotionExplainabilityValidation.Result contract =
                GpuProductionPromotionExplainabilityValidation.validate(properties);
        String firstBlocker = properties.getProperty("blocker.0", "none");
        if (!contract.valid()) {
            return new GpuProductionPromotionDecision(
                    DIAGNOSTIC_ONLY,
                    contract.status(),
                    false,
                    false,
                    false,
                    firstBlocker,
                    contract.firstViolation(),
                    "production promotion remains diagnostic-only because the explainability contract is invalid"
            );
        }
        if (GpuProductionPromotionExplainabilityValidation.PRODUCTION_READY.equals(contract.status())) {
            return new GpuProductionPromotionDecision(
                    PRODUCTION_ENABLED,
                    contract.status(),
                    true,
                    contract.sourceSwitchingAllowed(),
                    contract.mutationAllowed(),
                    firstBlocker,
                    "none",
                    "production promotion is enabled by a valid production-ready explainability contract"
            );
        }
        if (contract.i3ReviewReadyCount() == contract.kernelCount()
                && contract.i3BlockedCount() == 0
                && contract.blockerCount() > 0) {
            return new GpuProductionPromotionDecision(
                    REVIEW_READY,
                    contract.status(),
                    true,
                    false,
                    false,
                    firstBlocker,
                    "none",
                    "production promotion is review-ready but remains fail-closed until production gates are accepted"
            );
        }
        return new GpuProductionPromotionDecision(
                DIAGNOSTIC_ONLY,
                contract.status(),
                true,
                false,
                false,
                firstBlocker,
                "none",
                "production promotion remains diagnostic-only while workload readiness or blockers are unresolved"
        );
    }

    public String toPropertiesText() {
        return "decision.mode=" + mode + '\n'
                + "decision.status=" + status + '\n'
                + "decision.contractValid=" + contractValid + '\n'
                + "decision.productionSourceSwitchingAllowed=" + productionSourceSwitchingAllowed + '\n'
                + "decision.productionMutationAllowed=" + productionMutationAllowed + '\n'
                + "decision.firstBlocker=" + firstBlocker + '\n'
                + "decision.firstViolation=" + firstViolation + '\n'
                + "decision.diagnostic=" + diagnostic + '\n';
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
