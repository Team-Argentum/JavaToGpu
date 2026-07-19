package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backend-neutral handle for a compiled kernel/module entrypoint.
 *
 * <p>The native payload stays backend-specific. This contract only exposes the stable facts that the runtime pipeline,
 * lifecycle journal, cache diagnostics, and future CUDA/Vulkan/Metal adapters need to share.</p>
 */
public interface GpuBackendCompiledKernel extends AutoCloseable {

    GpuKernelDescriptor descriptor();

    String cacheKey();

    GpuRuntimeCompileArtifactSnapshot artifactSnapshot();

    default GpuBackendTarget backendTarget() {
        return moduleArtifact().backendTarget();
    }

    default GpuBackendModuleArtifact moduleArtifact() {
        GpuRuntimeCompileArtifactSnapshot snapshot = artifactSnapshot();
        return snapshot == null ? GpuBackendModuleArtifact.unknown() : snapshot.backendModuleArtifact();
    }

    default String compiledKernelKind() {
        return backendTarget().name().toLowerCase(java.util.Locale.ROOT) + "-kernel";
    }

    default Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.backend.compiledKernel" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        GpuBackendModuleArtifact moduleArtifact = moduleArtifact();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".backendTarget", backendTarget().name());
        fields.put(normalizedPrefix + ".kind", compiledKernelKind());
        fields.put(normalizedPrefix + ".cacheKey.present", Boolean.toString(cacheKey() != null && !cacheKey().isBlank()));
        fields.put(normalizedPrefix + ".module.kind", moduleArtifact.kind());
        fields.put(normalizedPrefix + ".module.format", moduleArtifact.format());
        fields.put(normalizedPrefix + ".module.format.canonical", moduleArtifact.moduleFormat().key());
        fields.put(normalizedPrefix + ".module.resource", moduleArtifact.resource());
        fields.put(normalizedPrefix + ".module.sourceAvailable", Boolean.toString(moduleArtifact.sourceAvailable()));
        fields.put(normalizedPrefix + ".module.binaryAvailable", Boolean.toString(moduleArtifact.binaryAvailable()));
        fields.put(normalizedPrefix + ".descriptor.kernelName", descriptor() == null ? "" : descriptor().kernelName());
        fields.put("runtime.backend.compiledKernel.present", "true");
        fields.put("runtime.backend.compiledKernel.kind", compiledKernelKind());
        fields.put("runtime.backend.compiledKernel.module.format", moduleArtifact.format());
        fields.put("runtime.backend.compiledKernel.module.format.canonical", moduleArtifact.moduleFormat().key());
        fields.put("runtime.backend.target", backendTarget().name());
        return Collections.unmodifiableMap(fields);
    }

    @Override
    default void close() {
    }
}
