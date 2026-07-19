package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed result for one backend kernel invocation.
 */
public record GpuBackendInvocationResult(
        GpuBackendStageResult stageResult,
        GpuBackendPreparationResult preparationResult,
        GpuExecutionConfig executionConfig,
        GpuRuntimeInvocationBindingSummary bindingSummary,
        int readbackRequiredCount,
        int readbackCompletedCount
) {

    public GpuBackendInvocationResult {
        preparationResult = preparationResult == null
                ? new GpuBackendPreparationResult(null, null, false, "unknown", GpuRuntimeInvocationBindingSummary.empty())
                : preparationResult;
        bindingSummary = bindingSummary == null ? preparationResult.bindingSummary() : bindingSummary;
        readbackRequiredCount = Math.max(0, readbackRequiredCount);
        readbackCompletedCount = Math.max(0, readbackCompletedCount);
        stageResult = stageResult == null
                ? GpuBackendStageResult.notStarted(
                        GpuBackendPipelineStage.INVOKE,
                        preparationResult.compilationResult().moduleArtifact().backendTarget()
                )
                : stageResult;
    }

    public static GpuBackendInvocationResult invoked(
            GpuBackendPreparationResult preparationResult,
            GpuExecutionConfig executionConfig,
            int readbackRequiredCount,
            int readbackCompletedCount,
            List<String> diagnostics
    ) {
        GpuBackendPreparationResult preparation = preparationResult == null
                ? new GpuBackendPreparationResult(null, null, false, "unknown", GpuRuntimeInvocationBindingSummary.empty())
                : preparationResult;
        return new GpuBackendInvocationResult(
                GpuBackendStageResult.succeeded(
                        GpuBackendPipelineStage.INVOKE,
                        preparation.compilationResult().moduleArtifact().backendTarget(),
                        "backend kernel invoked",
                        diagnostics
                ),
                preparation,
                executionConfig,
                preparation.bindingSummary(),
                readbackRequiredCount,
                readbackCompletedCount
        );
    }

    public static GpuBackendInvocationResult unsupported(
            GpuBackendTarget backendTarget,
            GpuBackendPreparationResult preparationResult,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new GpuBackendInvocationResult(
                GpuBackendStageResult.unsupported(
                        GpuBackendPipelineStage.INVOKE,
                        backendTarget,
                        "backend invocation is not implemented",
                        blockers,
                        diagnostics
                ),
                preparationResult,
                null,
                preparationResult == null ? GpuRuntimeInvocationBindingSummary.empty() : preparationResult.bindingSummary(),
                0,
                0
        );
    }

    public static GpuBackendInvocationResult skipped(
            GpuBackendTarget backendTarget,
            GpuBackendPreparationResult preparationResult,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new GpuBackendInvocationResult(
                GpuBackendStageResult.skipped(
                        GpuBackendPipelineStage.INVOKE,
                        backendTarget,
                        "backend invocation skipped",
                        blockers,
                        diagnostics
                ),
                preparationResult,
                null,
                preparationResult == null ? GpuRuntimeInvocationBindingSummary.empty() : preparationResult.bindingSummary(),
                0,
                0
        );
    }

    public static GpuBackendInvocationResult failed(
            GpuBackendTarget backendTarget,
            GpuBackendPreparationResult preparationResult,
            GpuExecutionConfig executionConfig,
            int readbackRequiredCount,
            Throwable failure,
            List<String> diagnostics
    ) {
        return new GpuBackendInvocationResult(
                GpuBackendStageResult.failed(
                        GpuBackendPipelineStage.INVOKE,
                        backendTarget,
                        "backend invocation failed",
                        failure,
                        diagnostics
                ),
                preparationResult,
                executionConfig,
                preparationResult == null ? GpuRuntimeInvocationBindingSummary.empty() : preparationResult.bindingSummary(),
                readbackRequiredCount,
                0
        );
    }

    public boolean invoked() {
        return stageResult.succeeded();
    }

    public boolean readbackComplete() {
        return readbackCompletedCount >= readbackRequiredCount;
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.backend.invoke" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.putAll(preparationResult.artifactFields("runtime.backend.prepare"));
        fields.putAll(stageResult.artifactFields(normalizedPrefix + ".stage"));
        fields.putAll(bindingSummary.artifactFields("runtime.invocation.binding"));
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".status", stageResult.status().name());
        fields.put(normalizedPrefix + ".invoked", Boolean.toString(invoked()));
        fields.put(normalizedPrefix + ".readback.required.count", Integer.toString(readbackRequiredCount));
        fields.put(normalizedPrefix + ".readback.completed.count", Integer.toString(readbackCompletedCount));
        fields.put(normalizedPrefix + ".readback.complete", Boolean.toString(readbackComplete()));
        if (executionConfig != null) {
            fields.put(normalizedPrefix + ".work.dimensions", Integer.toString(executionConfig.dimensions()));
            fields.put(normalizedPrefix + ".work.globalShape", executionConfig.globalShape());
            fields.put(normalizedPrefix + ".work.localShape", executionConfig.localShape());
            fields.put(normalizedPrefix + ".work.globalItem.count", Long.toString(executionConfig.globalItemCount()));
            fields.put(normalizedPrefix + ".work.localItem.count", Long.toString(executionConfig.localItemCount()));
        }
        fields.put("runtime.backend.invoke.present", "true");
        fields.put("runtime.backend.invoke.status", stageResult.status().name());
        fields.put("runtime.backend.invoke.invoked", Boolean.toString(invoked()));
        fields.put("runtime.backend.invoke.readback.complete", Boolean.toString(readbackComplete()));
        fields.put("runtime.backend.target", stageResult.backendTarget().name());
        return Collections.unmodifiableMap(fields);
    }
}
