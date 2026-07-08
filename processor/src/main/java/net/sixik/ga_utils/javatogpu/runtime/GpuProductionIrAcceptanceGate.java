package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Backend-neutral guard for production IR source selection.
 *
 * <p>Backends may expose their own source-switching flags, but those flags are not sufficient by themselves. The
 * runtime must also carry an accepted production-promotion decision before generated or reconstructed IrGpu source can
 * replace the descriptor source in production-like profiles.</p>
 */
public final class GpuProductionIrAcceptanceGate {

    private GpuProductionIrAcceptanceGate() {
    }

    public static Result evaluate(
            String backendName,
            String sourceName,
            String optimizationProfile,
            boolean productionProfileRequested,
            boolean backendSourceSwitchingEnabled,
            String decisionMode
    ) {
        String normalizedBackendName = normalize(backendName, "GPU backend");
        String normalizedSourceName = normalize(sourceName, "IrGpu source");
        String normalizedOptimizationProfile = normalize(optimizationProfile, "off");
        String normalizedDecisionMode = normalize(decisionMode, GpuProductionPromotionDecision.DIAGNOSTIC_ONLY);
        if (!productionProfileRequested) {
            return new Result(true, "review-profile", normalizedDecisionMode, normalizedBackendName
                    + " " + normalizedSourceName
                    + " may be selected because optimization profile '"
                    + normalizedOptimizationProfile
                    + "' is not production-like");
        }
        if (!backendSourceSwitchingEnabled) {
            return rejected(
                    normalizedBackendName,
                    normalizedSourceName,
                    normalizedOptimizationProfile,
                    normalizedDecisionMode,
                    "backend source switching is disabled"
            );
        }
        if (!GpuProductionPromotionDecision.PRODUCTION_ENABLED.equals(normalizedDecisionMode)) {
            return rejected(
                    normalizedBackendName,
                    normalizedSourceName,
                    normalizedOptimizationProfile,
                    normalizedDecisionMode,
                    "production promotion decision mode is not production-enabled"
            );
        }
        return new Result(true, "production-enabled", normalizedDecisionMode, normalizedBackendName
                + " " + normalizedSourceName
                + " may be selected for production-like optimization profile '"
                + normalizedOptimizationProfile
                + "' because backend source switching and production decision are both enabled");
    }

    private static Result rejected(
            String backendName,
            String sourceName,
            String optimizationProfile,
            String decisionMode,
            String reason
    ) {
        return new Result(false, "blocked", decisionMode, backendName
                + " "
                + sourceName
                + " cannot be selected for production-like optimization profile '"
                + optimizationProfile
                + "': "
                + reason
                + "; current decision mode="
                + decisionMode);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record Result(boolean accepted, String status, String decisionMode, String diagnostic) {
        public Result {
            status = normalize(status, "blocked");
            decisionMode = normalize(decisionMode, GpuProductionPromotionDecision.DIAGNOSTIC_ONLY);
            diagnostic = normalize(diagnostic, "production IR acceptance gate did not provide diagnostics");
        }

        public void throwIfRejected(String remediation) {
            if (accepted) {
                return;
            }
            String message = diagnostic;
            if (remediation != null && !remediation.isBlank()) {
                message += "; " + remediation;
            }
            throw new IllegalStateException(message);
        }
    }
}
