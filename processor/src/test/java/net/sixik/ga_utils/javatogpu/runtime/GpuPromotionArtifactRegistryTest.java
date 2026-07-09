package net.sixik.ga_utils.javatogpu.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuPromotionArtifactRegistryTest {

    @Test
    void exposesBackendNeutralPromotionArtifactFilenames() {
        assertEquals("i3-readiness-summary.properties", GpuPromotionArtifactRegistry.I3_READINESS_SUMMARY);
        assertEquals("backend-source-promotion-gate.properties", GpuPromotionArtifactRegistry.BACKEND_SOURCE_PROMOTION_GATE);
        assertEquals("backend-source-switching-decision.properties", GpuPromotionArtifactRegistry.BACKEND_SOURCE_SWITCHING_DECISION);
        assertEquals("backend-source-promotion-workload-gate.properties", GpuPromotionArtifactRegistry.BACKEND_SOURCE_PROMOTION_WORKLOAD_GATE);
        assertEquals("backend-source-promotion-workload-summary.properties", GpuPromotionArtifactRegistry.BACKEND_SOURCE_PROMOTION_WORKLOAD_SUMMARY);
        assertEquals("production-promotion-explainability.properties", GpuPromotionArtifactRegistry.PRODUCTION_PROMOTION_EXPLAINABILITY);
        assertEquals("production-promotion-explainability-summary.properties", GpuPromotionArtifactRegistry.PRODUCTION_PROMOTION_EXPLAINABILITY_SUMMARY);
        assertEquals("backend-promotion-artifact-support.properties", GpuPromotionArtifactRegistry.BACKEND_PROMOTION_ARTIFACT_SUPPORT);
        assertEquals("runtime-ir-handoff.properties", GpuPromotionArtifactRegistry.RUNTIME_IR_HANDOFF);
        assertEquals("runtime-production-mutation-safety.properties", GpuPromotionArtifactRegistry.RUNTIME_PRODUCTION_MUTATION_SAFETY);
        assertEquals("runtime-optimizer-drift.properties", GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_DRIFT);
        assertEquals(
                "runtime-optimizer-family-equivalence-payload.properties",
                GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD
        );
    }

    @Test
    void keepsPromotionArtifactListStableAndUnique() {
        assertEquals(12, GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS.size());
        assertEquals(
                GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS.size(),
                GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS.stream().distinct().count()
        );
        assertTrue(GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS.contains(
                GpuPromotionArtifactRegistry.BACKEND_SOURCE_PROMOTION_WORKLOAD_SUMMARY
        ));
        assertTrue(GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS.contains(
                GpuPromotionArtifactRegistry.PRODUCTION_PROMOTION_EXPLAINABILITY_SUMMARY
        ));
        assertTrue(GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS.contains(
                GpuPromotionArtifactRegistry.BACKEND_PROMOTION_ARTIFACT_SUPPORT
        ));
        assertTrue(GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS.contains(
                GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD
        ));
    }

    @Test
    void promotionArtifactSupportDefaultsToMissingEverything() {
        GpuPromotionArtifactSupport support = GpuPromotionArtifactSupport.none(
                net.sixik.ga_utils.javatogpu.api.GpuBackendTarget.CUDA
        );

        assertEquals(net.sixik.ga_utils.javatogpu.api.GpuBackendTarget.CUDA, support.backendTarget());
        assertTrue(support.supportedArtifacts().isEmpty());
        assertEquals(GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS, support.missingArtifacts());
        assertTrue(!support.complete());
    }

    @Test
    void promotionArtifactSupportCanAdvertisePartialBackendProgress() {
        GpuPromotionArtifactSupport support = GpuPromotionArtifactSupport.partial(
                net.sixik.ga_utils.javatogpu.api.GpuBackendTarget.VULKAN,
                java.util.List.of(
                        GpuPromotionArtifactRegistry.RUNTIME_IR_HANDOFF,
                        GpuPromotionArtifactRegistry.RUNTIME_IR_HANDOFF,
                        GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_DRIFT,
                        GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD
                )
        );

        assertEquals(3, support.supportedArtifacts().size());
        assertTrue(support.supports(GpuPromotionArtifactRegistry.RUNTIME_IR_HANDOFF));
        assertTrue(support.supports(GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_DRIFT));
        assertTrue(support.supports(GpuPromotionArtifactRegistry.RUNTIME_OPTIMIZER_FAMILY_EQUIVALENCE_PAYLOAD));
        assertTrue(support.missingArtifacts().contains(GpuPromotionArtifactRegistry.I3_READINESS_SUMMARY));
        assertTrue(!support.complete());
    }

    @Test
    void promotionArtifactSupportCanAdvertiseCompleteBackendProgress() {
        GpuPromotionArtifactSupport support = GpuPromotionArtifactSupport.complete(
                net.sixik.ga_utils.javatogpu.api.GpuBackendTarget.OPENCL
        );

        assertEquals(GpuPromotionArtifactRegistry.PROMOTION_ARTIFACTS, support.supportedArtifacts());
        assertTrue(support.missingArtifacts().isEmpty());
        assertTrue(support.complete());
    }

    @Test
    void promotionArtifactSupportFormatsMachineReadableProperties() {
        GpuPromotionArtifactSupport support = GpuPromotionArtifactSupport.partial(
                net.sixik.ga_utils.javatogpu.api.GpuBackendTarget.METAL,
                java.util.List.of(GpuPromotionArtifactRegistry.RUNTIME_IR_HANDOFF)
        );

        String properties = support.toPropertiesText();

        assertTrue(properties.contains("backendTarget=METAL"));
        assertTrue(properties.contains("complete=false"));
        assertTrue(properties.contains("supported.count=1"));
        assertTrue(properties.contains("supported.0=runtime-ir-handoff.properties"));
        assertTrue(properties.contains("missing.count=11"));
        assertTrue(properties.contains("registry.count=12"));
    }
}
