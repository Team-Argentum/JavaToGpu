package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.List;
import java.util.Objects;

/**
 * Small backend-neutral runner for the compile -> prepare -> invoke execution slice.
 */
public final class GpuBackendExecutionPipeline<
        C extends GpuBackendCompiledKernel,
        P extends GpuPreparedKernel,
        PLAN> {

    private final GpuBackendKernelCompiler<C> compiler;
    private final GpuBackendKernelPreparer<C, P, PLAN> preparer;
    private final GpuBackendKernelInvoker<P> invoker;

    public GpuBackendExecutionPipeline(
            GpuBackendKernelCompiler<C> compiler,
            GpuBackendKernelPreparer<C, P, PLAN> preparer,
            GpuBackendKernelInvoker<P> invoker
    ) {
        this.compiler = Objects.requireNonNull(compiler, "compiler");
        this.preparer = Objects.requireNonNull(preparer, "preparer");
        this.invoker = Objects.requireNonNull(invoker, "invoker");
        validateBackendTargets(compiler.backendTarget(), preparer.backendTarget(), invoker.backendTarget());
    }

    public GpuBackendTarget backendTarget() {
        return compiler.backendTarget();
    }

    public GpuBackendExecutionPipelineResult<C, P> execute(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendLoweringResult loweringResult,
            GpuBackendModuleArtifact moduleArtifact,
            PLAN executionPlan,
            GpuExecutionConfig executionConfig
    ) {
        Objects.requireNonNull(compileRequest, "compileRequest");
        GpuBackendModuleArtifact module = moduleArtifact == null
                ? GpuBackendModuleArtifact.unknown()
                : moduleArtifact;
        C compiledKernel = compiler.compile(compileRequest, module);
        GpuBackendCompilationResult compilationResult = compiler.compilationResult(compiledKernel, loweringResult);
        P preparedKernel = preparer.prepare(compiledKernel, executionPlan);
        GpuBackendPreparationResult preparationResult = preparer.preparationResult(preparedKernel, compilationResult);
        GpuExecutionConfig effectiveExecutionConfig = executionConfig != null
                ? executionConfig
                : preparedKernel == null ? null : preparedKernel.explicitExecutionConfig();
        invoker.invoke(preparedKernel, effectiveExecutionConfig);
        int readbackCompletedCount = preparedKernel == null ? 0 : preparedKernel.readbackRequiredCount();
        GpuBackendInvocationResult invocationResult = invoker.invocationResult(
                preparedKernel,
                preparationResult,
                effectiveExecutionConfig,
                readbackCompletedCount
        );
        return new GpuBackendExecutionPipelineResult<>(
                compiledKernel,
                preparedKernel,
                compilationResult,
                preparationResult,
                invocationResult
        );
    }

    /**
     * Executes the pipeline and converts runtime stage failures into typed receipts.
     *
     * <p>The strict {@link #execute(GpuRuntimeCompileRequest, GpuBackendLoweringResult, GpuBackendModuleArtifact,
     * Object, GpuExecutionConfig)} method keeps throwing exceptions. Use this method when a caller needs a structured
     * journal/report result for backend bring-up, CI diagnostics, or discovery-only backend placeholders.</p>
     */
    public GpuBackendExecutionPipelineResult<C, P> executeSafely(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendLoweringResult loweringResult,
            GpuBackendModuleArtifact moduleArtifact,
            PLAN executionPlan,
            GpuExecutionConfig executionConfig
    ) {
        Objects.requireNonNull(compileRequest, "compileRequest");
        GpuBackendModuleArtifact module = moduleArtifact == null
                ? GpuBackendModuleArtifact.unknown()
                : moduleArtifact;
        C compiledKernel;
        GpuBackendCompilationResult compilationResult;
        try {
            compiledKernel = compiler.compile(compileRequest, module);
            compilationResult = compiler.compilationResult(compiledKernel, loweringResult);
        } catch (RuntimeException exception) {
            return GpuBackendExecutionPipelineResult.failedDuringCompile(
                    backendTarget(),
                    loweringResult,
                    exception,
                    List.of("backend compile stage failed before a compiled kernel handle was returned")
            );
        }

        P preparedKernel;
        GpuBackendPreparationResult preparationResult;
        try {
            preparedKernel = preparer.prepare(compiledKernel, executionPlan);
            preparationResult = preparer.preparationResult(preparedKernel, compilationResult);
        } catch (RuntimeException exception) {
            return GpuBackendExecutionPipelineResult.failedDuringPrepare(
                    compiledKernel,
                    compilationResult,
                    backendTarget(),
                    exception,
                    List.of("backend prepare stage failed before invocation")
            );
        }

        GpuExecutionConfig effectiveExecutionConfig = executionConfig != null
                ? executionConfig
                : preparedKernel == null ? null : preparedKernel.explicitExecutionConfig();
        try {
            invoker.invoke(preparedKernel, effectiveExecutionConfig);
        } catch (RuntimeException exception) {
            return GpuBackendExecutionPipelineResult.failedDuringInvoke(
                    compiledKernel,
                    preparedKernel,
                    compilationResult,
                    preparationResult,
                    backendTarget(),
                    effectiveExecutionConfig,
                    exception,
                    List.of("backend invoke stage failed before readback completed")
            );
        }
        int readbackCompletedCount = preparedKernel == null ? 0 : preparedKernel.readbackRequiredCount();
        GpuBackendInvocationResult invocationResult = invoker.invocationResult(
                preparedKernel,
                preparationResult,
                effectiveExecutionConfig,
                readbackCompletedCount
        );
        return new GpuBackendExecutionPipelineResult<>(
                compiledKernel,
                preparedKernel,
                compilationResult,
                preparationResult,
                invocationResult
        );
    }

    private static void validateBackendTargets(
            GpuBackendTarget compilerTarget,
            GpuBackendTarget preparerTarget,
            GpuBackendTarget invokerTarget
    ) {
        GpuBackendTarget expected = compilerTarget == null ? GpuBackendTarget.UNKNOWN : compilerTarget;
        if (preparerTarget != expected || invokerTarget != expected) {
            throw new IllegalArgumentException(
                    "backend execution pipeline target mismatch: compiler="
                            + expected
                            + ", preparer="
                            + preparerTarget
                            + ", invoker="
                            + invokerTarget
            );
        }
    }
}
