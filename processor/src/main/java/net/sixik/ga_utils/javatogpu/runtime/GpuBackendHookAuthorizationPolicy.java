package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Report-only authorization policy for backend hooks.
 *
 * <p>The current backend hook runner still executes only {@link GpuExtensionPermission#READ_ONLY} hooks. This policy
 * lets tools and tests preview which stronger hooks would require explicit authorization before a future mutating
 * runner can exist.</p>
 */
public record GpuBackendHookAuthorizationPolicy(
        GpuExtensionPermission maximumPermission,
        List<String> explicitlyAuthorizedHookIds
) {

    public GpuBackendHookAuthorizationPolicy {
        maximumPermission = maximumPermission == null ? GpuExtensionPermission.READ_ONLY : maximumPermission;
        explicitlyAuthorizedHookIds = normalizeIds(explicitlyAuthorizedHookIds);
    }

    public static GpuBackendHookAuthorizationPolicy readOnlyOnly() {
        return new GpuBackendHookAuthorizationPolicy(GpuExtensionPermission.READ_ONLY, List.of());
    }

    public static GpuBackendHookAuthorizationPolicy previewExplicitAuthorization(
            GpuExtensionPermission maximumPermission,
            Collection<String> explicitlyAuthorizedHookIds
    ) {
        return new GpuBackendHookAuthorizationPolicy(
                maximumPermission,
                explicitlyAuthorizedHookIds == null ? List.of() : List.copyOf(explicitlyAuthorizedHookIds)
        );
    }

    public boolean explicitlyAuthorizes(String hookId) {
        if (hookId == null || hookId.isBlank()) {
            return false;
        }
        return explicitlyAuthorizedHookIds.contains(hookId.trim());
    }

    public GpuBackendHookAuthorizationDecision evaluate(
            GpuBackendHook hook,
            GpuBackendTarget backendTarget,
            GpuExtensionPhase requestedPhase
    ) {
        Objects.requireNonNull(hook, "hook");
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        GpuExtensionPhase phase = requestedPhase == null ? hook.extensionPhase() : requestedPhase;
        boolean targetMatched = hook.appliesTo(target);
        boolean phaseMatched = hook.extensionPhase() == phase;
        GpuExtensionPermission permission = hook.extensionPermission();
        boolean withinPolicy = permission.isAtMost(maximumPermission);
        boolean explicitAuthorizationPresent = explicitlyAuthorizes(hook.extensionId());

        if (!targetMatched) {
            return decision(
                    hook,
                    target,
                    phase,
                    false,
                    phaseMatched,
                    false,
                    false,
                    GpuBackendHookAuthorizationStatus.TARGET_FILTERED,
                    "hook target filter does not include " + target
            );
        }
        if (!phaseMatched) {
            return decision(
                    hook,
                    target,
                    phase,
                    true,
                    false,
                    false,
                    false,
                    GpuBackendHookAuthorizationStatus.PHASE_FILTERED,
                    "hook declares phase " + hook.extensionPhase() + " but authorization report requested " + phase
            );
        }
        if (permission == GpuExtensionPermission.READ_ONLY) {
            return decision(
                    hook,
                    target,
                    phase,
                    true,
                    true,
                    true,
                    true,
                    GpuBackendHookAuthorizationStatus.READ_ONLY_AUTHORIZED,
                    "read-only backend hook is authorized for the current registry runner"
            );
        }
        if (!withinPolicy) {
            return decision(
                    hook,
                    target,
                    phase,
                    true,
                    true,
                    false,
                    false,
                    GpuBackendHookAuthorizationStatus.PERMISSION_EXCEEDS_POLICY,
                    "hook declares " + permission + " but policy maximum is " + maximumPermission
            );
        }
        if (!explicitAuthorizationPresent) {
            return decision(
                    hook,
                    target,
                    phase,
                    true,
                    true,
                    false,
                    false,
                    GpuBackendHookAuthorizationStatus.EXPLICIT_AUTHORIZATION_MISSING,
                    "hook declares " + permission + " and requires explicit authorization by extension id"
            );
        }
        return decision(
                hook,
                target,
                phase,
                true,
                true,
                true,
                false,
                GpuBackendHookAuthorizationStatus.AUTHORIZED_BUT_EXECUTION_DISABLED,
                "hook is explicitly authorized by policy, but the current backend hook runner still executes only READ_ONLY hooks"
        );
    }

    private GpuBackendHookAuthorizationDecision decision(
            GpuBackendHook hook,
            GpuBackendTarget backendTarget,
            GpuExtensionPhase requestedPhase,
            boolean targetMatched,
            boolean phaseMatched,
            boolean policyAuthorized,
            boolean currentRegistryExecutable,
            GpuBackendHookAuthorizationStatus status,
            String diagnostic
    ) {
        return new GpuBackendHookAuthorizationDecision(
                hook.extensionId(),
                hook.extensionVersion(),
                hook.getClass().getName(),
                backendTarget,
                requestedPhase,
                hook.extensionPhase(),
                hook.extensionPermission(),
                targetMatched,
                phaseMatched,
                policyAuthorized,
                currentRegistryExecutable,
                status,
                diagnostic
        );
    }

    public String summary() {
        return "maximumPermission=" + maximumPermission
                + ", explicitlyAuthorizedHookIds=" + explicitlyAuthorizedHookIds.size();
    }

    private static List<String> normalizeIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                normalized.add(id.trim());
            }
        }
        return normalized.stream().sorted().toList();
    }
}
