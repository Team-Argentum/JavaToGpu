package net.sixik.ga_utils.javatogpu.runtime.diagnostics;

import java.util.List;

/**
 * Backend-neutral registry of promotion-related runtime artifact filenames.
 *
 * <p>OpenCL is the first backend that writes these artifacts, but the names are intentionally kept outside the OpenCL
 * package so CUDA, Vulkan, and Metal can reuse the same promotion evidence contract later.</p>
 */
public final class GpuPromotionArtifactRegistry {

    public static final String I3_READINESS_SUMMARY = "i3-readiness-summary.properties";
    public static final String BACKEND_SOURCE_PROMOTION_GATE = "backend-source-promotion-gate.properties";
    public static final String BACKEND_SOURCE_SWITCHING_DECISION = "backend-source-switching-decision.properties";
    public static final String BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE = "backend-source-promotion-workload-gate.properties";
    public static final String BACKEND_SOURCE_PROMOTION_WORKLOAD_SUMMARY = "backend-source-promotion-workload-summary.properties";
    public static final String PRODUCTION_PROMOTION_EXPLAINABILITY = "production-promotion-explainability.properties";
    public static final String PRODUCTION_PROMOTION_EXPLAINABILITY_SUMMARY = "production-promotion-explainability-summary.properties";
    public static final String BACKEND_PROMOTION_ARTIFACT_SUPPORT = "backend-promotion-artifact-support.properties";
    public static final String RUNTIME_IR_HANDOFF = "runtime-ir-handoff.properties";
    public static final String RUNTIME_PRODUCTION_MUTATION_SAFETY = "runtime-production-mutation-safety.properties";
    public static final String RUNTIME_OPTIMIZER_DRIFT = "runtime-optimizer-drift.properties";
    public static final String RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD =
            "runtime-optimizer-family-equivalence-payload.properties";

    public static final List<String> PROMOTION_ARTIFACTS = List.of(
            I3_READINESS_SUMMARY,
            BACKEND_SOURCE_PROMOTION_GATE,
            BACKEND_SOURCE_SWITCHING_DECISION,
            BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE,
            BACKEND_SOURCE_PROMOTION_WORKLOAD_SUMMARY,
            PRODUCTION_PROMOTION_EXPLAINABILITY,
            PRODUCTION_PROMOTION_EXPLAINABILITY_SUMMARY,
            BACKEND_PROMOTION_ARTIFACT_SUPPORT,
            RUNTIME_IR_HANDOFF,
            RUNTIME_PRODUCTION_MUTATION_SAFETY,
            RUNTIME_OPTIMIZER_DRIFT,
            RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD
    );

    private GpuPromotionArtifactRegistry() {
    }
}
