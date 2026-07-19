package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend-neutral result of one compile -> prepare -> invoke execution pipeline run.
 */
public record GpuBackendExecutionPipelineResult<
        C extends GpuBackendCompiledKernel,
        P extends GpuPreparedKernel>(
        C compiledKernel,
        P preparedKernel,
        GpuBackendCompilationResult compilationResult,
        GpuBackendPreparationResult preparationResult,
        GpuBackendInvocationResult invocationResult
) {

    public GpuBackendExecutionPipelineResult {
        compilationResult = compilationResult == null
                ? new GpuBackendCompilationResult(null, null, null, "")
                : compilationResult;
        preparationResult = preparationResult == null
                ? new GpuBackendPreparationResult(null, compilationResult, false, "unknown", GpuRuntimeInvocationBindingSummary.empty())
                : preparationResult;
        invocationResult = invocationResult == null
                ? new GpuBackendInvocationResult(null, preparationResult, null, GpuRuntimeInvocationBindingSummary.empty(), 0, 0)
                : invocationResult;
    }

    public boolean succeeded() {
        return compilationResult.compiled() && preparationResult.prepared() && invocationResult.invoked();
    }

    public static GpuBackendExecutionPipelineResult<GpuBackendCompiledKernel, GpuPreparedKernel> unsupported(
            GpuBackendTarget backendTarget,
            GpuBackendLoweringResult loweringResult,
            List<String> blockers,
            List<String> diagnostics
    ) {
        List<String> normalizedBlockers = blockers == null || blockers.isEmpty()
                ? List.of("backend-execution-pipeline-unavailable")
                : List.copyOf(blockers);
        GpuBackendCompilationResult compilationResult = GpuBackendCompilationResult.unsupported(
                backendTarget,
                loweringResult,
                normalizedBlockers,
                diagnostics
        );
        GpuBackendPreparationResult preparationResult = GpuBackendPreparationResult.skipped(
                backendTarget,
                compilationResult,
                List.of("compile-stage-unsupported"),
                diagnostics
        );
        GpuBackendInvocationResult invocationResult = GpuBackendInvocationResult.skipped(
                backendTarget,
                preparationResult,
                List.of("prepare-stage-skipped"),
                diagnostics
        );
        return new GpuBackendExecutionPipelineResult<>(
                null,
                null,
                compilationResult,
                preparationResult,
                invocationResult
        );
    }

    public static <C extends GpuBackendCompiledKernel, P extends GpuPreparedKernel>
    GpuBackendExecutionPipelineResult<C, P> failedDuringCompile(
            GpuBackendTarget backendTarget,
            GpuBackendLoweringResult loweringResult,
            Throwable failure,
            List<String> diagnostics
    ) {
        GpuBackendCompilationResult compilationResult = GpuBackendCompilationResult.failed(
                backendTarget,
                loweringResult,
                failure,
                diagnostics
        );
        GpuBackendPreparationResult preparationResult = GpuBackendPreparationResult.skipped(
                backendTarget,
                compilationResult,
                List.of("compile-stage-failed"),
                diagnostics
        );
        GpuBackendInvocationResult invocationResult = GpuBackendInvocationResult.skipped(
                backendTarget,
                preparationResult,
                List.of("prepare-stage-skipped"),
                diagnostics
        );
        return new GpuBackendExecutionPipelineResult<>(null, null, compilationResult, preparationResult, invocationResult);
    }

    public static <C extends GpuBackendCompiledKernel, P extends GpuPreparedKernel>
    GpuBackendExecutionPipelineResult<C, P> failedDuringPrepare(
            C compiledKernel,
            GpuBackendCompilationResult compilationResult,
            GpuBackendTarget backendTarget,
            Throwable failure,
            List<String> diagnostics
    ) {
        GpuBackendPreparationResult preparationResult = GpuBackendPreparationResult.failed(
                backendTarget,
                compilationResult,
                failure,
                diagnostics
        );
        GpuBackendInvocationResult invocationResult = GpuBackendInvocationResult.skipped(
                backendTarget,
                preparationResult,
                List.of("prepare-stage-failed"),
                diagnostics
        );
        return new GpuBackendExecutionPipelineResult<>(
                compiledKernel,
                null,
                compilationResult,
                preparationResult,
                invocationResult
        );
    }

    public static <C extends GpuBackendCompiledKernel, P extends GpuPreparedKernel>
    GpuBackendExecutionPipelineResult<C, P> failedDuringInvoke(
            C compiledKernel,
            P preparedKernel,
            GpuBackendCompilationResult compilationResult,
            GpuBackendPreparationResult preparationResult,
            GpuBackendTarget backendTarget,
            GpuExecutionConfig executionConfig,
            Throwable failure,
            List<String> diagnostics
    ) {
        int readbackRequiredCount = preparedKernel == null ? 0 : preparedKernel.readbackRequiredCount();
        GpuBackendInvocationResult invocationResult = GpuBackendInvocationResult.failed(
                backendTarget,
                preparationResult,
                executionConfig,
                readbackRequiredCount,
                failure,
                diagnostics
        );
        return new GpuBackendExecutionPipelineResult<>(
                compiledKernel,
                preparedKernel,
                compilationResult,
                preparationResult,
                invocationResult
        );
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.backend.executionPipeline" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.putAll(compilationResult.artifactFields("runtime.backend.compilation"));
        fields.putAll(preparationResult.artifactFields("runtime.backend.prepare"));
        fields.putAll(invocationResult.artifactFields("runtime.backend.invoke"));
        if (compiledKernel != null) {
            fields.putAll(compiledKernel.artifactFields("runtime.backend.compiledKernel"));
        }
        if (preparedKernel != null) {
            fields.putAll(preparedKernel.artifactFields("runtime.backend.preparedKernel"));
        }
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".succeeded", Boolean.toString(succeeded()));
        fields.put(normalizedPrefix + ".compiled", Boolean.toString(compilationResult.compiled()));
        fields.put(normalizedPrefix + ".prepared", Boolean.toString(preparationResult.prepared()));
        fields.put(normalizedPrefix + ".invoked", Boolean.toString(invocationResult.invoked()));
        fields.put("runtime.backend.executionPipeline.present", "true");
        fields.put("runtime.backend.executionPipeline.succeeded", Boolean.toString(succeeded()));
        return Collections.unmodifiableMap(fields);
    }
}
