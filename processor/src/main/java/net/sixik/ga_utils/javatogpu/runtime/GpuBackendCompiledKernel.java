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

    /**
     * Stable compile cache key when the backend has one, otherwise an empty or null value.
     */
    String cacheKey();

    /**
     * Compile artifacts and module/source/binary receipts produced for this handle.
     */
    GpuRuntimeCompileArtifactSnapshot artifactSnapshot();

    /**
     * Backend family that produced this compiled handle.
     */
    default GpuBackendTarget backendTarget() {
        return moduleArtifact().backendTarget();
    }

    /**
     * Backend module artifact represented by this compiled handle.
     */
    default GpuBackendModuleArtifact moduleArtifact() {
        GpuRuntimeCompileArtifactSnapshot snapshot = artifactSnapshot();
        return snapshot == null ? GpuBackendModuleArtifact.unknown() : snapshot.backendModuleArtifact();
    }

    /**
     * Short kind string used in diagnostic output.
     */
    default String compiledKernelKind() {
        return backendTarget().name().toLowerCase(java.util.Locale.ROOT) + "-kernel";
    }

    /**
     * Stable property map for artifacts and lifecycle events.
     */
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
