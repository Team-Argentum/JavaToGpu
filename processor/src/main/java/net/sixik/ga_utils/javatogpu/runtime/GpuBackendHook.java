package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtension;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionFailurePolicy;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Common metadata contract for backend SPI hooks.
 *
 * <p>Backend hooks are ServiceLoader-friendly extension points for backend policy, discovery, lowering,
 * compilation, invocation, and artifact reporting. This base contract is intentionally passive: installing a hook does
 * not alter the runtime path unless a concrete hook registry/pipeline explicitly invokes it.</p>
 */
public interface GpuBackendHook extends GpuExtension {

    /**
     * Backend targets this hook applies to. An empty set means all backend targets.
     */
    default Set<GpuBackendTarget> backendTargets() {
        return Set.of();
    }

    /**
     * Returns true when this hook should be considered for the supplied backend family.
     */
    default boolean appliesTo(GpuBackendTarget backendTarget) {
        Set<GpuBackendTarget> targets = backendTargets();
        if (targets == null || targets.isEmpty()) {
            return true;
        }
        GpuBackendTarget target = backendTarget == null ? GpuBackendTarget.UNKNOWN : backendTarget;
        return targets.contains(target);
    }

    /**
     * Hook-local failure handling policy. Registries should keep read-only hooks fail-soft by default.
     */
    default GpuExtensionFailurePolicy failurePolicy() {
        return GpuExtensionFailurePolicy.CONTINUE;
    }

    /**
     * Stable fields for catalogs, CI receipts, and lifecycle journals.
     */
    default Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.backend.hook" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".id", extensionId());
        fields.put(normalizedPrefix + ".version", extensionVersion());
        fields.put(normalizedPrefix + ".order", Integer.toString(extensionOrder()));
        fields.put(normalizedPrefix + ".phase", extensionPhase().name());
        fields.put(normalizedPrefix + ".permission", extensionPermission().name());
        fields.put(normalizedPrefix + ".failurePolicy", failurePolicy().name());
        Set<GpuBackendTarget> targets = backendTargets();
        if (targets == null || targets.isEmpty()) {
            fields.put(normalizedPrefix + ".backendTarget.mode", "all");
            fields.put(normalizedPrefix + ".backendTarget.count", "0");
        } else {
            fields.put(normalizedPrefix + ".backendTarget.mode", "filtered");
            fields.put(normalizedPrefix + ".backendTarget.count", Integer.toString(targets.size()));
            int index = 0;
            for (GpuBackendTarget target : targets.stream()
                    .sorted(Comparator.comparingInt(GpuBackendTarget::ordinal))
                    .toList()) {
                fields.put(normalizedPrefix + ".backendTarget." + index, target.name());
                index++;
            }
        }
        fields.put("runtime.backend.hook.present", "true");
        fields.put("runtime.backend.hook.id", extensionId());
        fields.put("runtime.backend.hook.phase", extensionPhase().name());
        fields.put("runtime.backend.hook.permission", extensionPermission().name());
        fields.put("runtime.backend.hook.failurePolicy", failurePolicy().name());
        return Collections.unmodifiableMap(fields);
    }

    /**
     * Fields a future hook runner may attach to lifecycle events. Defaults to the artifact contract.
     */
    default Map<String, String> lifecycleFields(String prefix) {
        return artifactFields(prefix == null || prefix.isBlank() ? "runtime.backend.hook.lifecycle" : prefix);
    }
}
