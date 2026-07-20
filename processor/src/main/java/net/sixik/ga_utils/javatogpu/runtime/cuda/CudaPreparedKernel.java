package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuPreparedKernel;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeInvocationBindingSummary;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * CUDA prepared-kernel placeholder after module/function loading, before kernel launch exists.
 */
public final class CudaPreparedKernel implements GpuPreparedKernel {

    private final CudaCompiledKernel compiledKernel;
    private final CudaModuleLoadResult moduleLoadResult;
    private final CudaArgumentBindingResult argumentBindingResult;
    private final GpuRuntimeInvocationBindingSummary bindingSummary;
    private CudaKernelLaunchResult kernelLaunchResult;
    private CudaKernelReadbackResult kernelReadbackResult;

    public CudaPreparedKernel(
            CudaCompiledKernel compiledKernel,
            CudaModuleLoadResult moduleLoadResult,
            GpuRuntimeInvocationBindingSummary bindingSummary
    ) {
        this(compiledKernel, moduleLoadResult, null, bindingSummary);
    }

    public CudaPreparedKernel(
            CudaCompiledKernel compiledKernel,
            CudaModuleLoadResult moduleLoadResult,
            CudaArgumentBindingResult argumentBindingResult,
            GpuRuntimeInvocationBindingSummary bindingSummary
    ) {
        this.compiledKernel = Objects.requireNonNull(compiledKernel, "compiledKernel");
        this.moduleLoadResult = Objects.requireNonNull(moduleLoadResult, "moduleLoadResult");
        this.argumentBindingResult = argumentBindingResult;
        this.bindingSummary = argumentBindingResult != null && argumentBindingResult.succeeded()
                ? argumentBindingResult.bindingSummary()
                : bindingSummary == null ? GpuRuntimeInvocationBindingSummary.empty() : bindingSummary;
    }

    @Override
    public CudaCompiledKernel compiledKernel() {
        return compiledKernel;
    }

    @Override
    public GpuRuntimeInvocationBindingSummary bindingSummary() {
        return bindingSummary;
    }

    @Override
    public GpuExecutionConfig explicitExecutionConfig() {
        return null;
    }

    @Override
    public int readbackRequiredCount() {
        if (argumentBindingResult != null && argumentBindingResult.argumentFrame() != null) {
            return argumentBindingResult.argumentFrame().readbackRequiredCount();
        }
        return GpuPreparedKernel.super.readbackRequiredCount();
    }

    @Override
    public String preparedKernelKind() {
        if (argumentBindingResult != null && argumentBindingResult.succeeded()) {
            return "cuda-argument-bound-preview";
        }
        if (moduleLoadResult.loadedModule() != null) {
            return "cuda-driver-module-function";
        }
        return "cuda-module-function-preview";
    }

    public CudaModuleLoadResult moduleLoadResult() {
        return moduleLoadResult;
    }

    public CudaArgumentBindingResult argumentBindingResult() {
        return argumentBindingResult;
    }

    public CudaKernelLaunchResult kernelLaunchResult() {
        return kernelLaunchResult;
    }

    public CudaKernelReadbackResult kernelReadbackResult() {
        return kernelReadbackResult;
    }

    void recordKernelLaunchResult(CudaKernelLaunchResult kernelLaunchResult) {
        this.kernelLaunchResult = kernelLaunchResult;
    }

    void recordKernelReadbackResult(CudaKernelReadbackResult kernelReadbackResult) {
        this.kernelReadbackResult = kernelReadbackResult;
    }

    @Override
    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.backend.preparedKernel" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>(GpuPreparedKernel.super.artifactFields(normalizedPrefix));
        fields.putAll(moduleLoadResult.artifactFields(normalizedPrefix + ".cudaModuleLoad"));
        if (argumentBindingResult != null) {
            fields.putAll(argumentBindingResult.artifactFields(normalizedPrefix + ".cudaArgumentBinding"));
            fields.put(normalizedPrefix + ".cuda.argumentBinding.status", argumentBindingResult.status());
            fields.put(normalizedPrefix + ".cuda.argumentBinding.succeeded", Boolean.toString(argumentBindingResult.succeeded()));
            fields.put(
                    normalizedPrefix + ".cuda.argumentFrame.present",
                    Boolean.toString(argumentBindingResult.argumentFrame() != null)
            );
        }
        if (kernelLaunchResult != null) {
            fields.putAll(kernelLaunchResult.artifactFields(normalizedPrefix + ".cudaKernelLaunch"));
            fields.put(normalizedPrefix + ".cuda.kernelLaunch.status", kernelLaunchResult.status());
            fields.put(normalizedPrefix + ".cuda.kernelLaunch.succeeded", Boolean.toString(kernelLaunchResult.succeeded()));
        }
        if (kernelReadbackResult != null) {
            fields.putAll(kernelReadbackResult.artifactFields(normalizedPrefix + ".cudaReadback"));
            fields.put(normalizedPrefix + ".cuda.readback.status", kernelReadbackResult.status());
            fields.put(normalizedPrefix + ".cuda.readback.complete", Boolean.toString(kernelReadbackResult.complete()));
        }
        fields.put(normalizedPrefix + ".cuda.moduleHandle.kind", moduleLoadResult.moduleHandleKind());
        fields.put(normalizedPrefix + ".cuda.functionHandle.kind", moduleLoadResult.functionHandleKind());
        fields.put("runtime.cuda.preparedKernel.present", "true");
        fields.put("runtime.cuda.preparedKernel.moduleHandle.kind", moduleLoadResult.moduleHandleKind());
        fields.put("runtime.cuda.preparedKernel.functionHandle.kind", moduleLoadResult.functionHandleKind());
        fields.put("runtime.cuda.preparedKernel.nativeHandle.present", Boolean.toString(moduleLoadResult.loadedModule() != null));
        if (argumentBindingResult != null) {
            fields.put("runtime.cuda.preparedKernel.argumentBinding.status", argumentBindingResult.status());
            fields.put("runtime.cuda.preparedKernel.argumentBinding.succeeded", Boolean.toString(argumentBindingResult.succeeded()));
            fields.put(
                    "runtime.cuda.preparedKernel.argumentFrame.present",
                    Boolean.toString(argumentBindingResult.argumentFrame() != null)
            );
            fields.put(
                    "runtime.cuda.preparedKernel.argumentBinding.argument.count",
                    Integer.toString(argumentBindingResult.bindingSummary().argumentBindingCount())
            );
            fields.put(
                    "runtime.cuda.preparedKernel.argumentBinding.buffer.count",
                    Integer.toString(argumentBindingResult.bindingSummary().bufferBindingCount())
            );
            fields.put(
                    "runtime.cuda.preparedKernel.argumentBinding.local.count",
                    Integer.toString(argumentBindingResult.bindingSummary().localBindingCount())
            );
            fields.put(
                    "runtime.cuda.preparedKernel.argumentBinding.scalar.count",
                    Integer.toString(argumentBindingResult.bindingSummary().scalarBindingCount())
            );
        }
        if (kernelLaunchResult != null) {
            fields.put("runtime.cuda.preparedKernel.kernelLaunch.status", kernelLaunchResult.status());
            fields.put("runtime.cuda.preparedKernel.kernelLaunch.succeeded", Boolean.toString(kernelLaunchResult.succeeded()));
        }
        if (kernelReadbackResult != null) {
            fields.put("runtime.cuda.preparedKernel.readback.status", kernelReadbackResult.status());
            fields.put("runtime.cuda.preparedKernel.readback.succeeded", Boolean.toString(kernelReadbackResult.succeeded()));
            fields.put("runtime.cuda.preparedKernel.readback.complete", Boolean.toString(kernelReadbackResult.complete()));
        }
        return Collections.unmodifiableMap(fields);
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        if (argumentBindingResult != null && argumentBindingResult.argumentFrame() != null) {
            try {
                argumentBindingResult.argumentFrame().close();
            } catch (RuntimeException exception) {
                failure = exception;
            }
        }
        if (moduleLoadResult.loadedModule() != null) {
            try {
                moduleLoadResult.loadedModule().close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
