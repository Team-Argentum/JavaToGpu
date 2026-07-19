package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed result for the backend compilation stage.
 */
public record GpuBackendCompilationResult(
        GpuBackendStageResult stageResult,
        GpuBackendLoweringResult loweringResult,
        GpuRuntimeBackendCompilationSummary compilationSummary,
        String cacheKey
) {

    public GpuBackendCompilationResult {
        loweringResult = loweringResult == null
                ? new GpuBackendLoweringResult(null, null, GpuBackendModuleArtifact.unknown())
                : loweringResult;
        compilationSummary = compilationSummary == null
                ? new GpuRuntimeBackendCompilationSummary(false, false, "unknown", false, 0, 0)
                : compilationSummary;
        cacheKey = cacheKey == null ? "" : cacheKey;
        stageResult = stageResult == null
                ? GpuBackendStageResult.notStarted(
                        GpuBackendPipelineStage.COMPILE,
                        loweringResult.moduleArtifact().backendTarget()
                )
                : stageResult;
    }

    public static GpuBackendCompilationResult succeeded(
            GpuBackendLoweringResult loweringResult,
            GpuRuntimeBackendCompilationSummary compilationSummary,
            String cacheKey,
            List<String> diagnostics
    ) {
        GpuBackendLoweringResult lowering = loweringResult == null
                ? new GpuBackendLoweringResult(null, null, GpuBackendModuleArtifact.unknown())
                : loweringResult;
        return new GpuBackendCompilationResult(
                GpuBackendStageResult.succeeded(
                        GpuBackendPipelineStage.COMPILE,
                        lowering.moduleArtifact().backendTarget(),
                        "backend module compiled",
                        diagnostics
                ),
                lowering,
                compilationSummary,
                cacheKey
        );
    }

    public static GpuBackendCompilationResult unsupported(
            GpuBackendTarget backendTarget,
            GpuBackendLoweringResult loweringResult,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new GpuBackendCompilationResult(
                GpuBackendStageResult.unsupported(
                        GpuBackendPipelineStage.COMPILE,
                        backendTarget,
                        "backend compiler is not implemented",
                        blockers,
                        diagnostics
                ),
                loweringResult,
                new GpuRuntimeBackendCompilationSummary(false, false, "unknown", false, 0, 0),
                ""
        );
    }

    public static GpuBackendCompilationResult failed(
            GpuBackendTarget backendTarget,
            GpuBackendLoweringResult loweringResult,
            Throwable failure,
            List<String> diagnostics
    ) {
        return new GpuBackendCompilationResult(
                GpuBackendStageResult.failed(
                        GpuBackendPipelineStage.COMPILE,
                        backendTarget,
                        "backend compilation failed",
                        failure,
                        diagnostics
                ),
                loweringResult,
                new GpuRuntimeBackendCompilationSummary(false, false, "unknown", false, 0, 0),
                ""
        );
    }

    public boolean compiled() {
        return stageResult.succeeded() && compilationSummary.modulePresent();
    }

    public GpuBackendModuleArtifact moduleArtifact() {
        return loweringResult.moduleArtifact();
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.backend.compilation" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.putAll(stageResult.artifactFields(normalizedPrefix + ".stage"));
        fields.putAll(compilationSummary.artifactFields("runtime.compilation"));
        fields.put(normalizedPrefix + ".present", "true");
        fields.put(normalizedPrefix + ".status", stageResult.status().name());
        fields.put(normalizedPrefix + ".compiled", Boolean.toString(compiled()));
        fields.put(normalizedPrefix + ".cacheKey.present", Boolean.toString(!cacheKey.isBlank()));
        fields.put(normalizedPrefix + ".module.format", moduleArtifact().format());
        fields.put(normalizedPrefix + ".module.format.canonical", moduleArtifact().moduleFormat().key());
        fields.put(normalizedPrefix + ".module.resource", moduleArtifact().resource());
        fields.put("runtime.backend.compilation.present", "true");
        fields.put("runtime.backend.compilation.status", stageResult.status().name());
        fields.put("runtime.backend.compilation.compiled", Boolean.toString(compiled()));
        fields.put("runtime.backend.compilation.module.format", moduleArtifact().format());
        fields.put("runtime.backend.compilation.module.format.canonical", moduleArtifact().moduleFormat().key());
        fields.put("runtime.backend.target", stageResult.backendTarget().name());
        return Collections.unmodifiableMap(fields);
    }
}
