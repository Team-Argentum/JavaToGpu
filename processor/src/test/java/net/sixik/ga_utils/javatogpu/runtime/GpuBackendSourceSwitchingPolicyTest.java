package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendSourceSwitchingPolicyTest {

    @Test
    void descriptorDefaultIsBackendNeutralAndFailClosed() {
        GpuBackendSourceSwitchingPolicy policy = GpuBackendSourceSwitchingPolicy.descriptorDefault();

        assertEquals("descriptor", policy.sourceSelection());
        assertFalse(policy.irGpuSourceRequested());
        assertEquals("disabled", policy.productionSourceSwitching());
        assertFalse(policy.productionSourceSwitchingEnabled());
        assertEquals(GpuProductionPromotionDecision.DIAGNOSTIC_ONLY, policy.productionPromotionDecisionMode());
    }

    @Test
    void translatesOpenClReviewSourceSelection() {
        GpuBackendSourceSwitchingPolicy policy = GpuBackendSourceSwitchingPolicy.from(
                GpuBackendCompileOptions.openClIrGpuSource(List.of("-cl-fast-relaxed-math"))
        );

        assertEquals("irgpu", policy.sourceSelection());
        assertTrue(policy.irGpuSourceRequested());
        assertEquals("disabled", policy.productionSourceSwitching());
        assertFalse(policy.productionSourceSwitchingEnabled());
    }

    @Test
    void translatesOpenClProductionSourceSwitching() {
        GpuBackendSourceSwitchingPolicy policy = GpuBackendSourceSwitchingPolicy.from(
                GpuBackendCompileOptions.openClProductionIrGpuSource(List.of())
        );

        assertEquals("irgpu", policy.sourceSelection());
        assertTrue(policy.irGpuSourceRequested());
        assertEquals("enabled", policy.productionSourceSwitching());
        assertTrue(policy.productionSourceSwitchingEnabled());
    }

    @Test
    void keepsDecisionModeForFutureBackendsWithoutOpenClSourceKeys() {
        GpuBackendCompileOptions options = GpuBackendCompileOptions.cuda(
                List.of("--use_fast_math"),
                Map.of(GpuBackendCompileOptions.PRODUCTION_PROMOTION_DECISION_MODE_PROPERTY, "review-ready")
        );

        GpuBackendSourceSwitchingPolicy policy = GpuBackendSourceSwitchingPolicy.from(options);

        assertEquals("descriptor", policy.sourceSelection());
        assertFalse(policy.irGpuSourceRequested());
        assertEquals("disabled", policy.productionSourceSwitching());
        assertFalse(policy.productionSourceSwitchingEnabled());
        assertEquals("review-ready", policy.productionPromotionDecisionMode());
    }

    @Test
    void ignoresOpenClSourceKeysOnNonOpenClBackends() {
        GpuBackendCompileOptions options = new GpuBackendCompileOptions(
                GpuBackendTarget.CUDA,
                List.of(),
                Map.of(
                        GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_PROPERTY,
                        GpuBackendCompileOptions.OPENCL_SOURCE_SELECTION_IRGPU,
                        GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_PROPERTY,
                        GpuBackendCompileOptions.OPENCL_PRODUCTION_SOURCE_SWITCHING_ENABLED
                )
        );

        GpuBackendSourceSwitchingPolicy policy = GpuBackendSourceSwitchingPolicy.from(options);

        assertEquals("descriptor", policy.sourceSelection());
        assertFalse(policy.irGpuSourceRequested());
        assertEquals("disabled", policy.productionSourceSwitching());
        assertFalse(policy.productionSourceSwitchingEnabled());
    }
}
