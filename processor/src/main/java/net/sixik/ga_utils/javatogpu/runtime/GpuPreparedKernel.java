package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backend-neutral handle for a compiled kernel after arguments/resources are prepared for invocation.
 */
public interface GpuPreparedKernel {

    GpuBackendCompiledKernel compiledKernel();

    GpuRuntimeInvocationBindingSummary bindingSummary();

    GpuExecutionConfig explicitExecutionConfig();

    default String preparedKernelKind() {
        return compiledKernel() == null ? "unknown" : compiledKernel().compiledKernelKind();
    }

    default int readbackRequiredCount() {
        return 0;
    }

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
}
