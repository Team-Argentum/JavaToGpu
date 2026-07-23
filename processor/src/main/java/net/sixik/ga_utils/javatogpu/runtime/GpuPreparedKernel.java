package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backend-neutral handle for a compiled kernel after arguments/resources are prepared for invocation.
 *
 * <p>Prepared handles usually own native argument frames, device buffers, dynamic shared/local-memory layout, and
 * readback bookkeeping. Close them through {@link GpuBackendExecutionPipelineResult#close()} or directly when the
 * pipeline result is not used.</p>
 */
public interface GpuPreparedKernel extends AutoCloseable {

    /**
     * Compiled kernel/module handle this prepared invocation belongs to.
     */
    GpuBackendCompiledKernel compiledKernel();

    /**
     * Portable summary of argument, buffer, scalar, local-memory, and readback bindings.
     */
    GpuRuntimeInvocationBindingSummary bindingSummary();

    /**
     * Explicit launch shape selected during preparation, or {@code null} when the caller must provide one.
     */
    GpuExecutionConfig explicitExecutionConfig();

    /**
     * Short kind string used in diagnostic output.
     */
    default String preparedKernelKind() {
        return compiledKernel() == null ? "unknown" : compiledKernel().compiledKernelKind();
    }

    /**
     * Number of host-visible outputs that must be copied back after invocation.
     */
    default int readbackRequiredCount() {
        return 0;
    }

    /**
     * Stable property map for artifacts and lifecycle events.
     */
    default Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.backend.preparedKernel" : prefix.trim();
        GpuRuntimeInvocationBindingSummary bindingSummary = bindingSummary() == null
                ? GpuRuntimeInvocationBindingSummary.empty()
                : bindingSummary();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".kind", preparedKernelKind());
        fields.put(normalizedPrefix + ".readback.required.count", Integer.toString(Math.max(0, readbackRequiredCount())));
        fields.put(normalizedPrefix + ".binding.argument.count", Integer.toString(bindingSummary.argumentBindingCount()));
        fields.put(normalizedPrefix + ".binding.buffer.count", Integer.toString(bindingSummary.bufferBindingCount()));
        fields.put(normalizedPrefix + ".binding.local.count", Integer.toString(bindingSummary.localBindingCount()));
        fields.put(normalizedPrefix + ".binding.scalar.count", Integer.toString(bindingSummary.scalarBindingCount()));
        fields.put("runtime.backend.preparedKernel.present", "true");
        fields.put("runtime.backend.preparedKernel.kind", preparedKernelKind());
        fields.put("runtime.backend.preparedKernel.readback.required.count", Integer.toString(Math.max(0, readbackRequiredCount())));
        if (compiledKernel() != null) {
            fields.put("runtime.backend.target", compiledKernel().backendTarget().name());
        }
        return Collections.unmodifiableMap(fields);
    }

    @Override
    default void close() {
    }
}
