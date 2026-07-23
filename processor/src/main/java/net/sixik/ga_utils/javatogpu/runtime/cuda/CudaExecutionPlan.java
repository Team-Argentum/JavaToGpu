package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuKernelInvocation;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationBindingSummary;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * CUDA execution-plan receipt passed from runtime invocation into native prepare stages.
 */
public record CudaExecutionPlan(
        GpuRuntimeInvocationBindingSummary bindingSummary,
        Object[] invocationArguments
) {

    public CudaExecutionPlan {
        bindingSummary = bindingSummary == null ? GpuRuntimeInvocationBindingSummary.empty() : bindingSummary;
        invocationArguments = invocationArguments == null ? null : invocationArguments.clone();
    }

    public CudaExecutionPlan(GpuRuntimeInvocationBindingSummary bindingSummary) {
        this(bindingSummary, null);
    }

    public static CudaExecutionPlan empty() {
        return new CudaExecutionPlan(GpuRuntimeInvocationBindingSummary.empty(), null);
    }

    public static CudaExecutionPlan from(GpuKernelInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        return new CudaExecutionPlan(GpuRuntimeInvocationBindingSummary.empty(), invocation.arguments());
    }

    @Override
    public Object[] invocationArguments() {
        return invocationArguments == null ? null : invocationArguments.clone();
    }

    public boolean invocationArgumentsPresent() {
        return invocationArguments != null;
    }

    public int invocationArgumentCount() {
        return invocationArguments == null ? 0 : invocationArguments.length;
    }

    public Object invocationArgumentAt(int index) {
        if (invocationArguments == null || index < 0 || index >= invocationArguments.length) {
            return null;
        }
        return invocationArguments[index];
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank()
                ? "runtime.cuda.executionPlan"
                : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        putFields(fields, normalizedPrefix);
        putFields(fields, "runtime.cuda.executionPlan");
        return Collections.unmodifiableMap(fields);
    }

    private void putFields(Map<String, String> fields, String prefix) {
        fields.put(prefix + ".present", "true");
        fields.put(prefix + ".invocationArguments.present", Boolean.toString(invocationArgumentsPresent()));
        fields.put(prefix + ".invocationArguments.count", Integer.toString(invocationArgumentCount()));
        fields.put(prefix + ".binding.argument.count", Integer.toString(bindingSummary.argumentBindingCount()));
        fields.put(prefix + ".binding.buffer.count", Integer.toString(bindingSummary.bufferBindingCount()));
        fields.put(prefix + ".binding.local.count", Integer.toString(bindingSummary.localBindingCount()));
        fields.put(prefix + ".binding.scalar.count", Integer.toString(bindingSummary.scalarBindingCount()));
    }
}
