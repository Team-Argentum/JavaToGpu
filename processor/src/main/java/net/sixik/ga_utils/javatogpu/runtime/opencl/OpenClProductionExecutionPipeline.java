package net.sixik.ga_utils.javatogpu.runtime.opencl;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipeline;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendExecutionPipelineResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelCompiler;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelInvoker;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendKernelPreparer;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendLoweringResult;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendModuleArtifact;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceSelectionPlan;
import net.sixik.ga_utils.javatogpu.runtime.GpuExecutionConfig;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeCompileRequest;

import java.util.List;
import java.util.Objects;

/**
 * OpenCL production binding for the shared compile -> prepare -> invoke SPI runner.
 */
final class OpenClProductionExecutionPipeline {
    private final GpuBackendExecutionPipeline<OpenClCompiledKernel, OpenClPreparedExecution, OpenClExecutionPlan> pipeline;

    OpenClProductionExecutionPipeline(
            GpuBackendKernelCompiler<OpenClCompiledKernel> compiler,
            GpuBackendKernelPreparer<OpenClCompiledKernel, OpenClPreparedExecution, OpenClExecutionPlan> preparer,
            GpuBackendKernelInvoker<OpenClPreparedExecution> invoker
    ) {
        this.pipeline = createSharedPipeline(compiler, preparer, invoker);
    }

    static GpuBackendExecutionPipeline<OpenClCompiledKernel, OpenClPreparedExecution, OpenClExecutionPlan> createSharedPipeline(
            GpuBackendKernelCompiler<OpenClCompiledKernel> compiler,
            GpuBackendKernelPreparer<OpenClCompiledKernel, OpenClPreparedExecution, OpenClExecutionPlan> preparer,
            GpuBackendKernelInvoker<OpenClPreparedExecution> invoker
    ) {
        return new GpuBackendExecutionPipeline<>(
                Objects.requireNonNull(compiler, "compiler"),
                Objects.requireNonNull(preparer, "preparer"),
                Objects.requireNonNull(invoker, "invoker")
        );
    }

    GpuBackendExecutionPipelineResult<OpenClCompiledKernel, OpenClPreparedExecution> execute(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact,
            OpenClExecutionPlan plan,
            GpuExecutionConfig executionConfig
    ) {
        Objects.requireNonNull(compileRequest, "compileRequest");
        GpuBackendModuleArtifact module = moduleArtifact == null
                ? GpuBackendModuleArtifact.unknown()
                : moduleArtifact;
        return pipeline.execute(
                compileRequest,
                productionLoweringResult(module),
                module,
                plan,
                executionConfig
        );
    }

    private static GpuBackendLoweringResult productionLoweringResult(GpuBackendModuleArtifact moduleArtifact) {
        GpuBackendModuleArtifact module = moduleArtifact == null ? GpuBackendModuleArtifact.unknown() : moduleArtifact;
        return GpuBackendLoweringResult.succeeded(
                module,
                GpuBackendSourceSelectionPlan.descriptorSource(
                        module.backendTarget(),
                        module.format(),
                        "OpenCL production pipeline received a preselected backend module artifact"
                ),
                List.of("OpenCL production execution pipeline is using the selected backend module artifact")
        );
    }
}
