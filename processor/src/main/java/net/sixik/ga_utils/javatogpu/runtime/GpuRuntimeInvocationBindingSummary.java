package net.sixik.ga_utils.javatogpu.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backend-neutral summary of argument bindings prepared for one kernel invocation.
 *
 * <p>Backends may keep their own detailed binding objects, but lifecycle journals should expose this compact
 * {@code runtime.invocation.binding.*} shape so OpenCL, CUDA, and future adapters can be parsed uniformly.</p>
 *
 * @param bufferBindingCount number of buffer-like arguments prepared for the kernel invocation
 * @param localBindingCount number of local/shared-memory arguments prepared for the kernel invocation
 * @param scalarBindingCount number of scalar arguments prepared for the kernel invocation
 * @param argumentBindingCount total number of prepared kernel argument slots
 */
public record GpuRuntimeInvocationBindingSummary(
        int bufferBindingCount,
        int localBindingCount,
        int scalarBindingCount,
        int argumentBindingCount
) {

    public GpuRuntimeInvocationBindingSummary {
        bufferBindingCount = Math.max(0, bufferBindingCount);
        localBindingCount = Math.max(0, localBindingCount);
        scalarBindingCount = Math.max(0, scalarBindingCount);
        argumentBindingCount = Math.max(0, argumentBindingCount);
    }

    public static GpuRuntimeInvocationBindingSummary empty() {
        return new GpuRuntimeInvocationBindingSummary(0, 0, 0, 0);
    }

    /**
     * Renders the summary using the portable {@code runtime.invocation.binding.*} lifecycle vocabulary.
     *
     * @param prefix field prefix, or {@code runtime.invocation.binding} when blank
     * @return immutable portable field map
     */
    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.invocation.binding"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        GpuRuntimeArtifactProperties.putPortable(fields, normalizedPrefix, "present", true);
        GpuRuntimeArtifactProperties.putPortable(fields, normalizedPrefix, "buffer.count", bufferBindingCount);
        GpuRuntimeArtifactProperties.putPortable(fields, normalizedPrefix, "local.count", localBindingCount);
        GpuRuntimeArtifactProperties.putPortable(fields, normalizedPrefix, "scalar.count", scalarBindingCount);
        GpuRuntimeArtifactProperties.putPortable(fields, normalizedPrefix, "argument.count", argumentBindingCount);
        return Collections.unmodifiableMap(fields);
    }
}
