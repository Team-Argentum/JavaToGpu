package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendArtifactHook;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Example artifact hook that contributes traceable metadata instead of mutating root lifecycle fields.
 */
public final class ExampleBackendArtifactHook implements GpuBackendArtifactHook {

    @Override
    public String extensionId() {
        return "examples.backend-hook.artifact";
    }

    @Override
    public String extensionVersion() {
        return "1";
    }

    @Override
    public int extensionOrder() {
        return 30_500;
    }

    @Override
    public Set<GpuBackendTarget> backendTargets() {
        return Set.of(GpuBackendTarget.OPENCL);
    }

    @Override
    public Map<String, String> contributeArtifactFields(
            GpuRuntimeCompileRequest compileRequest,
            Map<String, String> currentFields
    ) {
        Map<String, String> fields = currentFields == null ? Map.of() : currentFields;
        LinkedHashMap<String, String> contribution = new LinkedHashMap<>();
        contribution.put("examples.backendHook.artifact.status", fields.getOrDefault("status", "unknown"));
        contribution.put("examples.backendHook.artifact.backendTarget", fields.getOrDefault("runtime.backend.target", "unknown"));
        contribution.put("examples.backendHook.artifact.kernelResource", compileRequest == null
                ? "unknown"
                : compileRequest.descriptor().kernelResource());
        return Collections.unmodifiableMap(contribution);
    }
}
