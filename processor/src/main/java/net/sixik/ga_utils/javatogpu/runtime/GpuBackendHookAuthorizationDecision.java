package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPhase;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One backend hook authorization decision for a target/stage report.
 */
public record GpuBackendHookAuthorizationDecision(
        String hookId,
        String hookVersion,
        String implementationClass,
        GpuBackendTarget backendTarget,
        GpuExtensionPhase requestedPhase,
        GpuExtensionPhase hookPhase,
        GpuExtensionPermission permission,
        boolean targetMatched,
        boolean phaseMatched,
        boolean policyAuthorized,
        boolean currentRegistryExecutable,
        GpuBackendHookAuthorizationStatus status,
        String diagnostic
) {

    public GpuBackendHookAuthorizationDecision {
        hookId = hookId == null || hookId.isBlank() ? "unknown-hook" : hookId.trim();
        hookVersion = hookVersion == null || hookVersion.isBlank() ? "unknown" : hookVersion.trim();
        implementationClass = implementationClass == null || implementationClass.isBlank()
                ? "unknown"
                : implementationClass.trim();
        backendTarget = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        requestedPhase = requestedPhase == null ? GpuExtensionPhase.BACKEND_INVOCATION : requestedPhase;
        hookPhase = hookPhase == null ? requestedPhase : hookPhase;
        permission = permission == null ? GpuExtensionPermission.READ_ONLY : permission;
        status = status == null ? GpuBackendHookAuthorizationStatus.EXPLICIT_AUTHORIZATION_MISSING : status;
        diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public boolean blocked() {
        return status == GpuBackendHookAuthorizationStatus.EXPLICIT_AUTHORIZATION_MISSING
                || status == GpuBackendHookAuthorizationStatus.PERMISSION_EXCEEDS_POLICY;
    }

    public boolean futureAuthorizedButDisabled() {
        return status == GpuBackendHookAuthorizationStatus.AUTHORIZED_BUT_EXECUTION_DISABLED;
    }

    public String summary() {
        return hookId + ": status=" + status
                + ", permission=" + permission
                + ", currentRegistryExecutable=" + currentRegistryExecutable;
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.backend.hookAuthorization.decision"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".id", hookId);
        fields.put(normalizedPrefix + ".version", hookVersion);
        fields.put(normalizedPrefix + ".implementationClass", implementationClass);
        fields.put(normalizedPrefix + ".backendTarget", backendTarget.name());
        fields.put(normalizedPrefix + ".requestedPhase", requestedPhase.name());
        fields.put(normalizedPrefix + ".hookPhase", hookPhase.name());
        fields.put(normalizedPrefix + ".permission", permission.name());
        fields.put(normalizedPrefix + ".targetMatched", Boolean.toString(targetMatched));
        fields.put(normalizedPrefix + ".phaseMatched", Boolean.toString(phaseMatched));
        fields.put(normalizedPrefix + ".policyAuthorized", Boolean.toString(policyAuthorized));
        fields.put(normalizedPrefix + ".currentRegistryExecutable", Boolean.toString(currentRegistryExecutable));
        fields.put(normalizedPrefix + ".status", status.name());
        fields.put(normalizedPrefix + ".blocked", Boolean.toString(blocked()));
        fields.put(normalizedPrefix + ".futureAuthorizedButDisabled", Boolean.toString(futureAuthorizedButDisabled()));
        fields.put(normalizedPrefix + ".diagnostic", diagnostic);
        return Collections.unmodifiableMap(fields);
    }
}
