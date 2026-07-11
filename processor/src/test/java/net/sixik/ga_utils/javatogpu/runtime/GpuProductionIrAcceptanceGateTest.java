package net.sixik.ga_utils.javatogpu.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GpuProductionIrAcceptanceGateTest {

    @Test
    void reviewProfileIsAcceptedWithoutProductionSwitching() {
        GpuProductionIrAcceptanceGate.Result result = GpuProductionIrAcceptanceGate.evaluate(
                "OpenCL",
                "IrGpu source",
                GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE,
                false,
                false,
                GpuProductionPromotionDecision.DIAGNOSTIC_ONLY,
                false
        );

        assertTrue(result.accepted());
        assertEquals("review-profile", result.status());
        assertEquals(GpuProductionPromotionDecision.DIAGNOSTIC_ONLY, result.decisionMode());
        assertTrue(result.diagnostic().contains("is not production-like"));
    }

    @Test
    void productionProfileRejectsWhenBackendSwitchingIsDisabled() {
        GpuProductionIrAcceptanceGate.Result result = GpuProductionIrAcceptanceGate.evaluate(
                "OpenCL",
                "IrGpu source",
                "vendor-tuned",
                true,
                false,
                GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                true
        );

        assertFalse(result.accepted());
        assertEquals("blocked", result.status());
        assertEquals(GpuProductionPromotionDecision.PRODUCTION_ENABLED, result.decisionMode());
        assertTrue(result.diagnostic().contains("backend source switching is disabled"));
    }

    @Test
    void productionProfileRejectsWhenDecisionIsNotProductionEnabled() {
        GpuProductionIrAcceptanceGate.Result result = GpuProductionIrAcceptanceGate.evaluate(
                "OpenCL",
                "IrGpu source",
                "vendor-tuned",
                true,
                true,
                GpuProductionPromotionDecision.REVIEW_READY,
                true
        );

        assertFalse(result.accepted());
        assertEquals("blocked", result.status());
        assertEquals(GpuProductionPromotionDecision.REVIEW_READY, result.decisionMode());
        assertTrue(result.diagnostic().contains("production promotion decision mode is not production-enabled"));
        assertTrue(result.diagnostic().contains("current decision mode=review-ready"));
    }

    @Test
    void productionProfileRejectsWhenOperatorHasNotAcceptedPromotion() {
        GpuProductionIrAcceptanceGate.Result result = GpuProductionIrAcceptanceGate.evaluate(
                "OpenCL",
                "IrGpu source",
                "vendor-tuned",
                true,
                true,
                GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                false
        );

        assertFalse(result.accepted());
        assertEquals("blocked", result.status());
        assertEquals(GpuProductionPromotionDecision.PRODUCTION_ENABLED, result.decisionMode());
        assertTrue(result.diagnostic().contains("not explicitly accepted by the operator"));
    }

    @Test
    void productionProfileAcceptsOnlyWhenSwitchingDecisionAndOperatorAcceptanceAreEnabled() {
        GpuProductionIrAcceptanceGate.Result result = GpuProductionIrAcceptanceGate.evaluate(
                "OpenCL",
                "IrGpu source",
                "vendor-tuned",
                true,
                true,
                GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                true
        );

        assertTrue(result.accepted());
        assertEquals("production-enabled", result.status());
        assertEquals(GpuProductionPromotionDecision.PRODUCTION_ENABLED, result.decisionMode());
    }

    @Test
    void rejectedResultThrowsWithRemediation() {
        GpuProductionIrAcceptanceGate.Result result = GpuProductionIrAcceptanceGate.evaluate(
                "OpenCL",
                "IrGpu source",
                "vendor-tuned",
                true,
                true,
                GpuProductionPromotionDecision.DIAGNOSTIC_ONLY,
                true
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> result.throwIfRejected("load accepted production evidence")
        );

        assertTrue(exception.getMessage().contains("current decision mode=diagnostic-only"));
        assertTrue(exception.getMessage().contains("load accepted production evidence"));
    }
}
