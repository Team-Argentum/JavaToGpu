package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed receipt for one backend kernel invocation.
 *
 * <p>Invocation covers native kernel submission and the synchronous readback work promised by the prepared handle.
 * Backends should report unsupported launch shapes as structured blockers rather than silently changing the launch or
 * skipping readback.</p>
 *
 * @param stageResult portable invocation-stage status, blockers, diagnostics, and failure details
 * @param preparationResult prepare receipt that produced the invoked handle
 * @param executionConfig effective launch shape used by the backend, when known
 * @param bindingSummary portable binding summary used for lifecycle fields
 * @param readbackRequiredCount number of outputs that were expected to be copied back
 * @param readbackCompletedCount number of outputs that were actually copied back
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

    /**
     * Creates a successful invocation receipt.
     */
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

    /**
     * Creates a fail-closed receipt for adapters that cannot invoke this prepared kernel yet.
     */
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

    /**
     * Creates a receipt for an invocation stage intentionally skipped because preparation did not succeed.
     */
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

    /**
     * Creates a receipt for a hard invocation/readback-stage failure.
     */
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

    /**
     * Returns true when the native invocation stage reached a successful terminal status.
     */
    public boolean invoked() {
        return stageResult.succeeded();
    }

    /**
     * Returns true when every required host-visible readback completed.
     */
    public boolean readbackComplete() {
        return readbackCompletedCount >= readbackRequiredCount;
    }

    /**
     * Stable property map for artifacts, lifecycle events, and validation summaries.
     */
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
