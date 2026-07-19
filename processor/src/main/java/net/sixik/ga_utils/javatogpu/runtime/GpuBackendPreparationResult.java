package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed result for preparing a compiled backend module for invocation.
 */
public record GpuBackendPreparationResult(
        GpuBackendStageResult stageResult,
        GpuBackendCompilationResult compilationResult,
        boolean preparedKernelPresent,
        String preparedKernelKind,
        GpuRuntimeInvocationBindingSummary bindingSummary
) {

    public GpuBackendPreparationResult {
        compilationResult = compilationResult == null
                ? new GpuBackendCompilationResult(null, null, null, "")
                : compilationResult;
        preparedKernelKind = preparedKernelKind == null || preparedKernelKind.isBlank()
                ? "unknown"
                : preparedKernelKind.trim();
        bindingSummary = bindingSummary == null ? GpuRuntimeInvocationBindingSummary.empty() : bindingSummary;
        stageResult = stageResult == null
                ? GpuBackendStageResult.notStarted(
                        GpuBackendPipelineStage.PREPARE,
                        compilationResult.moduleArtifact().backendTarget()
                )
                : stageResult;
    }

    public static GpuBackendPreparationResult prepared(
            GpuBackendCompilationResult compilationResult,
            String preparedKernelKind,
            GpuRuntimeInvocationBindingSummary bindingSummary,
            List<String> diagnostics
    ) {
        GpuBackendCompilationResult compilation = compilationResult == null
                ? new GpuBackendCompilationResult(null, null, null, "")
                : compilationResult;
        return new GpuBackendPreparationResult(
                GpuBackendStageResult.succeeded(
                        GpuBackendPipelineStage.PREPARE,
                        compilation.moduleArtifact().backendTarget(),
                        "backend kernel prepared",
                        diagnostics
                ),
                compilation,
                true,
                preparedKernelKind,
                bindingSummary
        );
    }

    public static GpuBackendPreparationResult unsupported(
            GpuBackendTarget backendTarget,
            GpuBackendCompilationResult compilationResult,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new GpuBackendPreparationResult(
                GpuBackendStageResult.unsupported(
                        GpuBackendPipelineStage.PREPARE,
                        backendTarget,
                        "backend kernel preparation is not implemented",
                        blockers,
                        diagnostics
                ),
                compilationResult,
                false,
                "unsupported",
                GpuRuntimeInvocationBindingSummary.empty()
        );
    }

    public static GpuBackendPreparationResult skipped(
            GpuBackendTarget backendTarget,
            GpuBackendCompilationResult compilationResult,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new GpuBackendPreparationResult(
                GpuBackendStageResult.skipped(
                        GpuBackendPipelineStage.PREPARE,
                        backendTarget,
                        "backend kernel preparation skipped",
                        blockers,
                        diagnostics
                ),
                compilationResult,
                false,
                "skipped",
                GpuRuntimeInvocationBindingSummary.empty()
        );
    }

    public static GpuBackendPreparationResult failed(
            GpuBackendTarget backendTarget,
            GpuBackendCompilationResult compilationResult,
            Throwable failure,
            List<String> diagnostics
    ) {
        return new GpuBackendPreparationResult(
                GpuBackendStageResult.failed(
                        GpuBackendPipelineStage.PREPARE,
                        backendTarget,
                        "backend kernel preparation failed",
                        failure,
                        diagnostics
                ),
                compilationResult,
                false,
                "failed",
                GpuRuntimeInvocationBindingSummary.empty()
        );
    }

    public boolean prepared() {
        return stageResult.succeeded() && preparedKernelPresent;
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.backend.prepare" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.putAll(stageResult.artifactFields(normalizedPrefix + ".stage"));
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".status", stageResult.status().name());
        fields.put(normalizedPrefix + ".prepared", Boolean.toString(prepared()));
        fields.put(normalizedPrefix + ".kernel.present", Boolean.toString(preparedKernelPresent));
        fields.put(normalizedPrefix + ".kernel.kind", preparedKernelKind);
        fields.put(normalizedPrefix + ".binding.argument.count", Integer.toString(bindingSummary.argumentBindingCount()));
        fields.put(normalizedPrefix + ".binding.buffer.count", Integer.toString(bindingSummary.bufferBindingCount()));
        fields.put(normalizedPrefix + ".binding.local.count", Integer.toString(bindingSummary.localBindingCount()));
        fields.put(normalizedPrefix + ".binding.scalar.count", Integer.toString(bindingSummary.scalarBindingCount()));
        fields.put("runtime.backend.prepare.present", "true");
        fields.put("runtime.backend.prepare.status", stageResult.status().name());
        fields.put("runtime.backend.prepare.prepared", Boolean.toString(prepared()));
        fields.put("runtime.backend.prepare.kernel.kind", preparedKernelKind);
        fields.put("runtime.backend.target", stageResult.backendTarget().name());
        return Collections.unmodifiableMap(fields);
    }
}
