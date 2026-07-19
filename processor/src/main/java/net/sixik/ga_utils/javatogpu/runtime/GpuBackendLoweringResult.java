package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed result for the backend lowering stage.
 */
public record GpuBackendLoweringResult(
        GpuBackendStageResult stageResult,
        GpuBackendSourceSelectionPlan sourceSelectionPlan,
        GpuBackendModuleArtifact moduleArtifact
) {

    public GpuBackendLoweringResult {
        moduleArtifact = moduleArtifact == null ? GpuBackendModuleArtifact.unknown() : moduleArtifact;
        GpuBackendTarget target = moduleArtifact.backendTarget();
        sourceSelectionPlan = sourceSelectionPlan == null
                ? GpuBackendSourceSelectionPlan.descriptorSource(target, "unknown", "source selection was not recorded")
                : sourceSelectionPlan;
        stageResult = stageResult == null
                ? GpuBackendStageResult.notStarted(GpuBackendPipelineStage.LOWER, target)
                : stageResult;
    }

    public static GpuBackendLoweringResult succeeded(
            GpuBackendModuleArtifact moduleArtifact,
            GpuBackendSourceSelectionPlan sourceSelectionPlan,
            List<String> diagnostics
    ) {
        GpuBackendModuleArtifact module = moduleArtifact == null ? GpuBackendModuleArtifact.unknown() : moduleArtifact;
        return new GpuBackendLoweringResult(
                GpuBackendStageResult.succeeded(
                        GpuBackendPipelineStage.LOWER,
                        module.backendTarget(),
                        "backend module lowered",
                        diagnostics
                ),
                sourceSelectionPlan,
                module
        );
    }

    public static GpuBackendLoweringResult unsupported(
            GpuBackendTarget backendTarget,
            GpuBackendSourceSelectionPlan sourceSelectionPlan,
            List<String> blockers,
            List<String> diagnostics
    ) {
        return new GpuBackendLoweringResult(
                GpuBackendStageResult.unsupported(
                        GpuBackendPipelineStage.LOWER,
                        backendTarget,
                        "backend lowerer is not implemented",
                        blockers,
                        diagnostics
                ),
                sourceSelectionPlan,
                GpuBackendModuleArtifact.unknown()
        );
    }

    public boolean lowered() {
        return stageResult.succeeded() && moduleArtifact.sourceAvailable();
    }

    public Map<String, String> artifactFields(String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "runtime.backend.lowering" : prefix.trim();
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.putAll(stageResult.artifactFields(normalizedPrefix + ".stage"));
        fields.put(normalizedPrefix + ".lowered", Boolean.toString(lowered()));
        fields.put(normalizedPrefix + ".selectedSource", sourceSelectionPlan.selectedSource());
        fields.put(normalizedPrefix + ".payloadFormat", sourceSelectionPlan.payloadFormat());
        fields.put(normalizedPrefix + ".runtimeLoadMode", sourceSelectionPlan.runtimeLoadMode());
        fields.put(normalizedPrefix + ".sourceSelection.blocker.count", Integer.toString(sourceSelectionPlan.blockers().size()));
        for (int index = 0; index < sourceSelectionPlan.blockers().size(); index++) {
            fields.put(normalizedPrefix + ".sourceSelection.blocker." + index, sourceSelectionPlan.blockers().get(index));
        }
        fields.put(normalizedPrefix + ".module.backendTarget", moduleArtifact.backendTarget().name());
        fields.put(normalizedPrefix + ".module.kind", moduleArtifact.kind());
        fields.put(normalizedPrefix + ".module.format", moduleArtifact.format());
        fields.put(normalizedPrefix + ".module.format.canonical", moduleArtifact.moduleFormat().key());
        fields.put(normalizedPrefix + ".module.format.sourceLike", Boolean.toString(moduleArtifact.sourceLikeFormat()));
        fields.put(normalizedPrefix + ".module.format.binaryLike", Boolean.toString(moduleArtifact.binaryLikeFormat()));
        fields.put(
                normalizedPrefix + ".module.format.matchesBackendTarget",
                Boolean.toString(moduleArtifact.formatMatchesBackendTarget())
        );
        fields.put(normalizedPrefix + ".module.resource", moduleArtifact.resource());
        fields.put(normalizedPrefix + ".module.sourceAvailable", Boolean.toString(moduleArtifact.sourceAvailable()));
        fields.put(normalizedPrefix + ".module.binaryAvailable", Boolean.toString(moduleArtifact.binaryAvailable()));
        fields.put("runtime.backend.lowering.present", "true");
        fields.put("runtime.backend.lowering.status", stageResult.status().name());
        fields.put("runtime.backend.lowering.lowered", Boolean.toString(lowered()));
        fields.put("runtime.backend.lowering.module.format", moduleArtifact.format());
        fields.put("runtime.backend.lowering.module.format.canonical", moduleArtifact.moduleFormat().key());
        fields.put("runtime.backend.lowering.selectedSource", sourceSelectionPlan.selectedSource());
        fields.put("runtime.backend.lowering.runtimeLoadMode", sourceSelectionPlan.runtimeLoadMode());
        fields.put("runtime.backend.target", stageResult.backendTarget().name());
        return Collections.unmodifiableMap(fields);
    }
}
