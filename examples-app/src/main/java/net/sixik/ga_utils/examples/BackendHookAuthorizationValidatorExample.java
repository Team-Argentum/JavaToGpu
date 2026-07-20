package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendHookAuthorizationValidationResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendHookAuthorizationValidator;

/**
 * Runnable example for a hardware-free backend hook authorization CI gate.
 */
public final class BackendHookAuthorizationValidatorExample {

    private BackendHookAuthorizationValidatorExample() {
    }

    public static void main(String[] args) {
        GpuBackendHookAuthorizationValidationResult result = validateOpenClHooks();
        System.out.println(renderValidation(result));
        if (!result.passed()) {
            System.exit(result.recommendedExitCode());
        }
    }

    static GpuBackendHookAuthorizationValidationResult validateOpenClHooks() {
        return GpuBackendHookAuthorizationValidator.validateReadOnlyClasspath(GpuBackendTarget.OPENCL);
    }

    static String renderValidation(GpuBackendHookAuthorizationValidationResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("Backend hook authorization validator:").append(System.lineSeparator());
        builder.append("- status=").append(result.status()).append(System.lineSeparator());
        builder.append("- backend=").append(result.catalog().backendTarget()).append(System.lineSeparator());
        builder.append("- decisionCount=").append(result.catalog().decisionCount()).append(System.lineSeparator());
        builder.append("- executableHooks=")
                .append(result.catalog().currentRegistryExecutableCount())
                .append(System.lineSeparator());
        builder.append("- blockedHooks=").append(result.catalog().blockedCount()).append(System.lineSeparator());
        builder.append("- futureAuthorizedButDisabled=")
                .append(result.catalog().futureAuthorizedButDisabledCount())
                .append(System.lineSeparator());
        builder.append("- firstBlocker=").append(result.catalog().firstBlocker()).append(System.lineSeparator());
        builder.append("- recommendedExitCode=").append(result.recommendedExitCode()).append(System.lineSeparator());
        builder.append("- rule=fail CI when status is not passed; this check does not open OpenCL/CUDA")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
