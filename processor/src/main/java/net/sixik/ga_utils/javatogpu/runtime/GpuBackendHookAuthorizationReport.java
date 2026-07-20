package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Report-only authorization view for backend hook execution.
 */
public record GpuBackendHookAuthorizationReport(
        GpuBackendTarget backendTarget,
        GpuExtensionPhase phase,
        GpuBackendHookAuthorizationPolicy policy,
        List<GpuBackendHookAuthorizationDecision> decisions
) {

    public GpuBackendHookAuthorizationReport {
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        phase = phase == null ? GpuExtensionPhase.BACKEND_INVOCATION : phase;
        policy = policy == null ? GpuBackendHookAuthorizationPolicy.readOnlyOnly() : policy;
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }

    public long currentRegistryExecutableCount() {
        return decisions.stream()
                .filter(GpuBackendHookAuthorizationDecision::currentRegistryExecutable)
                .count();
    }

    public long blockedCount() {
        return decisions.stream()
                .filter(GpuBackendHookAuthorizationDecision::blocked)
                .count();
    }

    public long futureAuthorizedButDisabledCount() {
        return decisions.stream()
                .filter(GpuBackendHookAuthorizationDecision::futureAuthorizedButDisabled)
                .count();
    }

    public long authorizationRequiredCount() {
        return decisions.stream()
                .filter(decision -> decision.status()
                        == GpuBackendHookAuthorizationStatus.EXPLICIT_AUTHORIZATION_MISSING)
                .count();
    }

    public String status() {
        if (decisions.isEmpty()) {
            return "empty";
        }
        if (blockedCount() > 0) {
            return "blocked";
        }
        if (futureAuthorizedButDisabledCount() > 0) {
            return "future-authorized-execution-disabled";
        }
        return "read-only-ready";
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.backend.hookAuthorization"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".backendTarget", backendTarget.name());
        fields.put(normalizedPrefix + ".phase", phase.name());
        fields.put(normalizedPrefix + ".status", status());
        fields.put(normalizedPrefix + ".policy.maximumPermission", policy.maximumPermission().name());
        fields.put(normalizedPrefix + ".policy.explicitlyAuthorizedHook.count",
                Integer.toString(policy.explicitlyAuthorizedHookIds().size()));
        for (int index = 0; index < policy.explicitlyAuthorizedHookIds().size(); index++) {
            fields.put(normalizedPrefix + ".policy.explicitlyAuthorizedHook." + index,
                    policy.explicitlyAuthorizedHookIds().get(index));
        }
        fields.put(normalizedPrefix + ".decision.count", Integer.toString(decisions.size()));
        fields.put(normalizedPrefix + ".currentRegistryExecutable.count",
                Long.toString(currentRegistryExecutableCount()));
        fields.put(normalizedPrefix + ".blocked.count", Long.toString(blockedCount()));
        fields.put(normalizedPrefix + ".authorizationRequired.count", Long.toString(authorizationRequiredCount()));
        fields.put(normalizedPrefix + ".futureAuthorizedButDisabled.count",
                Long.toString(futureAuthorizedButDisabledCount()));
        for (int index = 0; index < decisions.size(); index++) {
            fields.putAll(decisions.get(index).artifactFields(normalizedPrefix + ".decision." + index));
        }
        fields.put("runtime.backend.hookAuthorization.present", "true");
        fields.put("runtime.backend.hookAuthorization.status", status());
        fields.put("runtime.backend.hookAuthorization.backendTarget", backendTarget.name());
        fields.put("runtime.backend.hookAuthorization.phase", phase.name());
        fields.put("runtime.backend.hookAuthorization.currentRegistryExecutable.count",
                Long.toString(currentRegistryExecutableCount()));
        fields.put("runtime.backend.hookAuthorization.blocked.count", Long.toString(blockedCount()));
        fields.put("runtime.backend.hookAuthorization.futureAuthorizedButDisabled.count",
                Long.toString(futureAuthorizedButDisabledCount()));
        return Collections.unmodifiableMap(fields);
    }

    public String toMarkdown() {
        StringBuilder builder = new StringBuilder();
        builder.append("Backend hook authorization: ").append(status()).append('\n');
        builder.append("Backend: ").append(backendTarget).append('\n');
        builder.append("Phase: ").append(phase).append('\n');
        builder.append("Policy: ").append(policy.summary()).append('\n');
        if (!decisions.isEmpty()) {
            builder.append('\n').append("Decisions:").append('\n');
            for (GpuBackendHookAuthorizationDecision decision : decisions) {
                builder.append("- ").append(decision.summary()).append('\n');
            }
        }
        return builder.toString();
    }
}
