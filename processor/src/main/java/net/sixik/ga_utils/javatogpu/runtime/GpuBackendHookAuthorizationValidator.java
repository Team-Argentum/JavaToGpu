package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

/**
 * Hardware-free validator for backend hook authorization catalogs.
 */
public final class GpuBackendHookAuthorizationValidator {

    private GpuBackendHookAuthorizationValidator() {
    }

    public static GpuBackendHookAuthorizationValidationResult validateReadOnlyClasspath(
            GpuBackendTarget backendTarget
    ) {
        return validateReadOnlyClasspath(GpuBackendHookRegistry.loadWithServiceLoader(), backendTarget);
    }

    public static GpuBackendHookAuthorizationValidationResult validateReadOnlyClasspath(
            GpuBackendHookRegistry registry,
            GpuBackendTarget backendTarget
    ) {
        return validate(registry, backendTarget, GpuBackendHookAuthorizationPolicy.readOnlyOnly(), false);
    }

    public static GpuBackendHookAuthorizationValidationResult validate(
            GpuBackendHookRegistry registry,
            GpuBackendTarget backendTarget,
            GpuBackendHookAuthorizationPolicy policy,
            boolean allowFutureAuthorizedButDisabled
    ) {
        GpuBackendHookRegistry effectiveRegistry = registry == null ? GpuBackendHookRegistry.empty() : registry;
        GpuBackendHookAuthorizationPolicy effectivePolicy = policy == null
                ? GpuBackendHookAuthorizationPolicy.readOnlyOnly()
                : policy;
        return new GpuBackendHookAuthorizationValidationResult(
                effectiveRegistry.authorizationCatalog(backendTarget, effectivePolicy),
                allowFutureAuthorizedButDisabled
        );
    }

    public static void requireReadOnlyClasspath(
            GpuBackendTarget backendTarget
    ) {
        validateReadOnlyClasspath(backendTarget).throwIfFailed();
    }

    public static void requireReadOnlyClasspath(
            GpuBackendHookRegistry registry,
            GpuBackendTarget backendTarget
    ) {
        validateReadOnlyClasspath(registry, backendTarget).throwIfFailed();
    }
}
