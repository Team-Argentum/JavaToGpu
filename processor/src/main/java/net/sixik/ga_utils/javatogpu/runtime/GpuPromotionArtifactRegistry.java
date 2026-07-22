package net.sixik.ga_utils.javatogpu.runtime;

import java.util.List;

/**
 * Compatibility facade for backend-neutral promotion artifact names.
 */
public final class GpuPromotionArtifactRegistry {

    public static final String I3_READINESS_SUMMARY =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuPromotionArtifactRegistry.I3_READINESS_SUMMARY;
    public static final String BACKEND_SOURCE_PROMOTION_GATE =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuPromotionArtifactRegistry.BACKEND_SOURCE_PROMOTION_GATE;
    public static final String BACKEND_SOURCE_SWITCHING_DECISION =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuPromotionArtifactRegistry.BACKEND_SOURCE_SWITCHING_DECISION;
    public static final String BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuPromotionArtifactRegistry.BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE;
    public static final String BACKEND_SOURCE_PROMOTION_WORKLOAD_SUMMARY =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuPromotionArtifactRegistry.BACKEND_SOURCE_PROMOTION_WORKLOAD_SUMMARY;
    public static final String PRODUCTION_PROMOTION_EXPLAINABILITY =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuPromotionArtifactRegistry.PRODUCTION_PROMOTION_EXPLAINABILITY;
    public static final String PRODUCTION_PROMOTION_EXPLAINABILITY_SUMMARY =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuPromotionArtifactRegistry.PRODUCTION_PROMOTION_EXPLAINABILITY_SUMMARY;
    public static final String BACKEND_PROMOTION_ARTIFACT_SUPPORT =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuPromotionArtifactRegistry.BACKEND_PROMOTION_ARTIFACT_SUPPORT;
    public static final String RUNTIME_IR_HANDOFF =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuPromotionArtifactRegistry.RUNTIME_IR_HANDOFF;
    public static final String RUNTIME_PRODUCTION_MUTATION_SAFETY =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuPromotionArtifactRegistry.RUNTIME_PRODUCTION_MUTATION_SAFETY;
    public static final String RUNTIME_OPTIMIZER_DRIFT =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_DRIFT;
    public static final String RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD;

    public static final List<String> PROMOTION_ARTIFACTS =
            net.sixik.ga_utils.javatogpu.runtime.diagnostics.GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS;

    private GpuPromotionArtifactRegistry() {
    }
}
