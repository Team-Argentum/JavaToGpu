package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Backend-neutral runner for one compile -> prepare -> invoke execution slice.
 *
 * <p>The pipeline separates backend work into three explicit stages so OpenCL, CUDA, and future adapters can share the
 * same lifecycle events, structured receipts, and fail-closed diagnostics. Backend-specific objects stay inside the
 * generic compiled/prepared handles while the surrounding reports use portable runtime field names.</p>
 *
 * @param <C> backend-specific compiled kernel/module handle
 * @param <P> backend-specific prepared invocation handle
 * @param <PLAN> backend-specific execution plan produced before argument binding/preparation
 */
public final class GpuBackendExecutionPipeline<
        C extends GpuBackendCompiledKernel,
        P extends GpuPreparedKernel,
        PLAN> {

    private final GpuBackendKernelCompiler<C> compiler;
    private final GpuBackendKernelPreparer<C, P, PLAN> preparer;
    private final GpuBackendKernelInvoker<P> invoker;

    /**
     * Creates a pipeline from matching backend-target stage implementations.
     */
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

    /**
     * Backend family handled by every stage in this pipeline.
     */
    public GpuBackendTarget backendTarget() {
        return compiler.backendTarget();
    }

    /**
     * Executes the strict pipeline and throws if any stage fails.
     */
    public GpuBackendExecutionPipelineResult<C, P> execute(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendLoweringResult loweringResult,
            GpuBackendModuleArtifact moduleArtifact,
            PLAN executionPlan,
            GpuExecutionConfig executionConfig
    ) {
        return execute(
                compileRequest,
                loweringResult,
                moduleArtifact,
                executionPlan,
                executionConfig,
                GpuRuntimeLifecycleEventBus.empty()
        );
    }

    /**
     * Executes the strict pipeline with lifecycle events and throws if any stage fails.
     */
    public GpuBackendExecutionPipelineResult<C, P> execute(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendLoweringResult loweringResult,
            GpuBackendModuleArtifact moduleArtifact,
            PLAN executionPlan,
            GpuExecutionConfig executionConfig,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        Objects.requireNonNull(compileRequest, "compileRequest");
        GpuBackendModuleArtifact module = moduleArtifact == null
                ? GpuBackendModuleArtifact.unknown()
                : moduleArtifact;
        GpuRuntimeLifecycleEventBus events = lifecycleEventBus(lifecycleEventBus);

        publishCompileStarted(events, compileRequest, module);
        C compiledKernel;
        GpuBackendCompilationResult compilationResult;
        try {
            compiledKernel = compiler.compile(compileRequest, module);
            compilationResult = compiler.compilationResult(compiledKernel, loweringResult);
            publishCompileCompleted(events, compileRequest, module, compiledKernel, compilationResult, null);
        } catch (RuntimeException exception) {
            publishCompileCompleted(events, compileRequest, module, null, null, exception);
            throw exception;
        }

        publishModuleLoadStarted(events, compileRequest, compiledKernel, compilationResult);
        P preparedKernel;
        GpuBackendPreparationResult preparationResult;
        try {
            preparedKernel = preparer.prepare(compiledKernel, executionPlan);
            preparationResult = preparer.preparationResult(preparedKernel, compilationResult);
            publishModuleLoadCompleted(events, compileRequest, compiledKernel, preparedKernel, preparationResult, null);
        } catch (RuntimeException exception) {
            publishModuleLoadCompleted(events, compileRequest, compiledKernel, null, null, exception);
            throw exception;
        }

        GpuExecutionConfig effectiveExecutionConfig = executionConfig != null
                ? executionConfig
                : preparedKernel == null ? null : preparedKernel.explicitExecutionConfig();
        publishInvocationStarted(events, compileRequest, preparedKernel, preparationResult, effectiveExecutionConfig);
        GpuBackendInvocationResult invocationResult;
        try {
            invoker.invoke(preparedKernel, effectiveExecutionConfig);
            int readbackCompletedCount = preparedKernel == null ? 0 : preparedKernel.readbackRequiredCount();
            invocationResult = invoker.invocationResult(
                    preparedKernel,
                    preparationResult,
                    effectiveExecutionConfig,
                    readbackCompletedCount
            );
            publishInvocationCompleted(events, compileRequest, preparedKernel, invocationResult, null);
        } catch (RuntimeException exception) {
            publishInvocationCompleted(events, compileRequest, preparedKernel, null, exception);
            throw exception;
        }
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
    /**
     * Executes the pipeline with lifecycle events and converts stage failures into typed receipts.
     */
    public GpuBackendExecutionPipelineResult<C, P> executeSafely(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendLoweringResult loweringResult,
            GpuBackendModuleArtifact moduleArtifact,
            PLAN executionPlan,
            GpuExecutionConfig executionConfig
    ) {
        return executeSafely(
                compileRequest,
                loweringResult,
                moduleArtifact,
                executionPlan,
                executionConfig,
                GpuRuntimeLifecycleEventBus.empty()
        );
    }

    public GpuBackendExecutionPipelineResult<C, P> executeSafely(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendLoweringResult loweringResult,
            GpuBackendModuleArtifact moduleArtifact,
            PLAN executionPlan,
            GpuExecutionConfig executionConfig,
            GpuRuntimeLifecycleEventBus lifecycleEventBus
    ) {
        Objects.requireNonNull(compileRequest, "compileRequest");
        GpuBackendModuleArtifact module = moduleArtifact == null
                ? GpuBackendModuleArtifact.unknown()
                : moduleArtifact;
        GpuRuntimeLifecycleEventBus events = lifecycleEventBus(lifecycleEventBus);

        C compiledKernel;
        GpuBackendCompilationResult compilationResult;
        publishCompileStarted(events, compileRequest, module);
        try {
            compiledKernel = compiler.compile(compileRequest, module);
            compilationResult = compiler.compilationResult(compiledKernel, loweringResult);
            publishCompileCompleted(events, compileRequest, module, compiledKernel, compilationResult, null);
        } catch (RuntimeException exception) {
            GpuBackendExecutionPipelineResult<C, P> failed = GpuBackendExecutionPipelineResult.failedDuringCompile(
                    backendTarget(),
                    loweringResult,
                    exception,
                    List.of("backend compile stage failed before a compiled kernel handle was returned")
            );
            publishCompileCompleted(events, compileRequest, module, null, failed.compilationResult(), exception);
            return failed;
        }

        P preparedKernel;
        GpuBackendPreparationResult preparationResult;
        publishModuleLoadStarted(events, compileRequest, compiledKernel, compilationResult);
        try {
            preparedKernel = preparer.prepare(compiledKernel, executionPlan);
            preparationResult = preparer.preparationResult(preparedKernel, compilationResult);
            publishModuleLoadCompleted(events, compileRequest, compiledKernel, preparedKernel, preparationResult, null);
        } catch (RuntimeException exception) {
            GpuBackendExecutionPipelineResult<C, P> failed = GpuBackendExecutionPipelineResult.failedDuringPrepare(
                    compiledKernel,
                    compilationResult,
                    backendTarget(),
                    exception,
                    List.of("backend prepare stage failed before invocation")
            );
            publishModuleLoadCompleted(events, compileRequest, compiledKernel, null, failed.preparationResult(), exception);
            return failed;
        }

        GpuExecutionConfig effectiveExecutionConfig = executionConfig != null
                ? executionConfig
                : preparedKernel == null ? null : preparedKernel.explicitExecutionConfig();
        publishInvocationStarted(events, compileRequest, preparedKernel, preparationResult, effectiveExecutionConfig);
        try {
            invoker.invoke(preparedKernel, effectiveExecutionConfig);
        } catch (RuntimeException exception) {
            GpuBackendExecutionPipelineResult<C, P> failed = GpuBackendExecutionPipelineResult.failedDuringInvoke(
                    compiledKernel,
                    preparedKernel,
                    compilationResult,
                    preparationResult,
                    backendTarget(),
                    effectiveExecutionConfig,
                    exception,
                    List.of("backend invoke stage failed before readback completed")
            );
            publishInvocationCompleted(events, compileRequest, preparedKernel, failed.invocationResult(), exception);
            return failed;
        }
        int readbackCompletedCount = preparedKernel == null ? 0 : preparedKernel.readbackRequiredCount();
        GpuBackendInvocationResult invocationResult = invoker.invocationResult(
                preparedKernel,
                preparationResult,
                effectiveExecutionConfig,
                readbackCompletedCount
        );
        publishInvocationCompleted(events, compileRequest, preparedKernel, invocationResult, null);
        return new GpuBackendExecutionPipelineResult<>(
                compiledKernel,
                preparedKernel,
                compilationResult,
                preparationResult,
                invocationResult
        );
    }

    private GpuRuntimeLifecycleEventBus lifecycleEventBus(GpuRuntimeLifecycleEventBus lifecycleEventBus) {
        return lifecycleEventBus == null ? GpuRuntimeLifecycleEventBus.empty() : lifecycleEventBus;
    }

    private void publishCompileStarted(
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact
    ) {
        if (!lifecycleEnabled(lifecycleEventBus)) {
            return;
        }
        publishLifecycleEvent(
                lifecycleEventBus,
                GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_STARTED,
                compileRequest,
                "Backend compilation started",
                compileFields(compileRequest, moduleArtifact, null, null, "started", null)
        );
    }

    private void publishCompileCompleted(
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact,
            C compiledKernel,
            GpuBackendCompilationResult compilationResult,
            RuntimeException failure
    ) {
        if (!lifecycleEnabled(lifecycleEventBus)) {
            return;
        }
        String status = stageStatus(compilationResult == null ? null : compilationResult.stageResult(), failure);
        publishLifecycleEvent(
                lifecycleEventBus,
                GpuRuntimeLifecycleEventKind.BACKEND_COMPILATION_COMPLETED,
                compileRequest,
                "Backend compilation completed",
                compileFields(compileRequest, moduleArtifact, compiledKernel, compilationResult, status, failure)
        );
    }

    private LinkedHashMap<String, String> compileFields(
            GpuRuntimeCompileRequest compileRequest,
            GpuBackendModuleArtifact moduleArtifact,
            C compiledKernel,
            GpuBackendCompilationResult compilationResult,
            String status,
            RuntimeException failure
    ) {
        LinkedHashMap<String, String> fields = GpuRuntimeLifecycleFields.backendCompilationFields(
                compileRequest,
                moduleArtifact,
                compiledKernel == null ? null : compiledKernel.artifactSnapshot(),
                null,
                compilationResult == null ? null : compilationResult.compilationSummary(),
                status,
                compiledKernel == null ? "" : compiledKernel.cacheKey(),
                failure
        );
        putPipelineStage(fields, "compile", status);
        putAll(fields, compilationResult == null ? null : compilationResult.artifactFields("runtime.backend.compilation"));
        putAll(fields, compiledKernel == null ? null : compiledKernel.artifactFields("runtime.backend.compiledKernel"));
        return fields;
    }

    private void publishModuleLoadStarted(
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            GpuRuntimeCompileRequest compileRequest,
            C compiledKernel,
            GpuBackendCompilationResult compilationResult
    ) {
        if (!lifecycleEnabled(lifecycleEventBus)) {
            return;
        }
        publishLifecycleEvent(
                lifecycleEventBus,
                GpuRuntimeLifecycleEventKind.MODULE_LOAD_STARTED,
                compileRequest,
                "Backend module load and preparation started",
                moduleLoadFields(compileRequest, compiledKernel, compilationResult, null, null, "started", null)
        );
    }

    private void publishModuleLoadCompleted(
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            GpuRuntimeCompileRequest compileRequest,
            C compiledKernel,
            P preparedKernel,
            GpuBackendPreparationResult preparationResult,
            RuntimeException failure
    ) {
        if (!lifecycleEnabled(lifecycleEventBus)) {
            return;
        }
        String status = stageStatus(preparationResult == null ? null : preparationResult.stageResult(), failure);
        publishLifecycleEvent(
                lifecycleEventBus,
                GpuRuntimeLifecycleEventKind.MODULE_LOAD_COMPLETED,
                compileRequest,
                "Backend module load and preparation completed",
                moduleLoadFields(compileRequest, compiledKernel, null, preparedKernel, preparationResult, status, failure)
        );
    }

    private LinkedHashMap<String, String> moduleLoadFields(
            GpuRuntimeCompileRequest compileRequest,
            C compiledKernel,
            GpuBackendCompilationResult compilationResult,
            P preparedKernel,
            GpuBackendPreparationResult preparationResult,
            String status,
            RuntimeException failure
    ) {
        LinkedHashMap<String, String> fields = GpuRuntimeLifecycleFields.backendCompilationFields(
                compileRequest,
                compiledKernel == null ? null : compiledKernel.moduleArtifact(),
                compiledKernel == null ? null : compiledKernel.artifactSnapshot(),
                null,
                compilationResult == null ? null : compilationResult.compilationSummary(),
                status,
                compiledKernel == null ? "" : compiledKernel.cacheKey(),
                failure
        );
        putPipelineStage(fields, "prepare", status);
        fields.put("runtime.backend.prepare.phase", "module-load-and-argument-binding");
        putAll(fields, compilationResult == null ? null : compilationResult.artifactFields("runtime.backend.compilation"));
        putAll(fields, preparationResult == null ? null : preparationResult.artifactFields("runtime.backend.prepare"));
        putAll(fields, compiledKernel == null ? null : compiledKernel.artifactFields("runtime.backend.compiledKernel"));
        putAll(fields, preparedKernel == null ? null : preparedKernel.artifactFields("runtime.backend.preparedKernel"));
        return fields;
    }

    private void publishInvocationStarted(
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            GpuRuntimeCompileRequest compileRequest,
            P preparedKernel,
            GpuBackendPreparationResult preparationResult,
            GpuExecutionConfig executionConfig
    ) {
        if (!lifecycleEnabled(lifecycleEventBus)) {
            return;
        }
        publishLifecycleEvent(
                lifecycleEventBus,
                GpuRuntimeLifecycleEventKind.INVOCATION_STARTED,
                compileRequest,
                "Backend invocation started",
                invocationFields(compileRequest, preparedKernel, preparationResult, null, executionConfig, "started", null)
        );
    }

    private void publishInvocationCompleted(
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            GpuRuntimeCompileRequest compileRequest,
            P preparedKernel,
            GpuBackendInvocationResult invocationResult,
            RuntimeException failure
    ) {
        if (!lifecycleEnabled(lifecycleEventBus)) {
            return;
        }
        String status = stageStatus(invocationResult == null ? null : invocationResult.stageResult(), failure);
        GpuExecutionConfig executionConfig = invocationResult == null ? null : invocationResult.executionConfig();
        publishLifecycleEvent(
                lifecycleEventBus,
                GpuRuntimeLifecycleEventKind.INVOCATION_COMPLETED,
                compileRequest,
                "Backend invocation completed",
                invocationFields(compileRequest, preparedKernel, null, invocationResult, executionConfig, status, failure)
        );
    }

    private LinkedHashMap<String, String> invocationFields(
            GpuRuntimeCompileRequest compileRequest,
            P preparedKernel,
            GpuBackendPreparationResult preparationResult,
            GpuBackendInvocationResult invocationResult,
            GpuExecutionConfig executionConfig,
            String status,
            RuntimeException failure
    ) {
        GpuBackendCompiledKernel compiledKernel = preparedKernel == null ? null : preparedKernel.compiledKernel();
        GpuRuntimeCompileArtifactSnapshot snapshot = compiledKernel == null ? null : compiledKernel.artifactSnapshot();
        GpuRuntimeInvocationBindingSummary bindingSummary = preparedKernel == null
                ? null
                : preparedKernel.bindingSummary();
        LinkedHashMap<String, String> fields = GpuRuntimeLifecycleFields.invocationFields(
                snapshot,
                null,
                executionConfig,
                bindingSummary,
                status,
                compiledKernel == null ? "" : compiledKernel.cacheKey(),
                failure
        );
        GpuRuntimeLifecycleFields.putAllMissing(fields, GpuRuntimeLifecycleFields.compileRequestFields(compileRequest));
        putPipelineStage(fields, "invoke", status);
        putAll(fields, preparationResult == null ? null : preparationResult.artifactFields("runtime.backend.prepare"));
        putAll(fields, invocationResult == null ? null : invocationResult.artifactFields("runtime.backend.invoke"));
        putAll(fields, preparedKernel == null ? null : preparedKernel.artifactFields("runtime.backend.preparedKernel"));
        return fields;
    }

    private void publishLifecycleEvent(
            GpuRuntimeLifecycleEventBus lifecycleEventBus,
            GpuRuntimeLifecycleEventKind kind,
            GpuRuntimeCompileRequest compileRequest,
            String message,
            Map<String, String> fields
    ) {
        if (lifecycleEventBus.listenerCount() == 0) {
            return;
        }
        lifecycleEventBus.publish(new GpuRuntimeLifecycleEvent(
                kind,
                backendTarget(),
                kernelResource(compileRequest),
                optimizationProfile(compileRequest),
                message,
                fields
        ));
    }

    private static boolean lifecycleEnabled(GpuRuntimeLifecycleEventBus lifecycleEventBus) {
        return lifecycleEventBus != null && lifecycleEventBus.listenerCount() > 0;
    }

    private static void putPipelineStage(LinkedHashMap<String, String> fields, String stage, String status) {
        fields.put("runtime.backend.executionPipeline.stage", normalize(stage, "unknown"));
        fields.put("runtime.backend.executionPipeline.stage.status", normalize(status, "unknown"));
        GpuRuntimeLifecycleFields.putStatus(fields, normalize(status, "unknown"));
    }

    private static void putAll(LinkedHashMap<String, String> fields, Map<String, String> additions) {
        if (additions != null && !additions.isEmpty()) {
            fields.putAll(additions);
        }
    }

    private static String stageStatus(GpuBackendStageResult stageResult, RuntimeException failure) {
        if (failure != null) {
            return "failed";
        }
        if (stageResult == null) {
            return "completed";
        }
        return stageResult.status().name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String kernelResource(GpuRuntimeCompileRequest compileRequest) {
        return compileRequest == null || compileRequest.descriptor() == null
                ? "unknown"
                : compileRequest.descriptor().kernelResource();
    }

    private static String optimizationProfile(GpuRuntimeCompileRequest compileRequest) {
        return compileRequest == null || compileRequest.options() == null
                ? "off"
                : compileRequest.options().optimizationProfile();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
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
