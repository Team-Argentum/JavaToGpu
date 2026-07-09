package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendSourceSwitchingDecisionTest {

    @Test
    void descriptorDefaultDoesNotRequireIrGpuSourceEvidence() {
        GpuBackendSourceSwitchingDecision decision = GpuBackendSourceSwitchingDecision.evaluate(
                provenance(GpuBackendCompileOptions.openCl(List.of()), "off"),
                module(),
                GpuBackendSourceReconstructionResult.notAttempted(
                        GpuBackendTarget.OPENCL,
                        "descriptor-opencl-source",
                        "unknown",
                        "opencl-descriptor-source-compile",
                        List.of("irgpu-artifact-missing"),
                        List.of("OpenCL reconstruction preview skipped because no IrGpu artifact was available")
                ),
                null
        );

        assertEquals("descriptor-default", decision.status());
        assertEquals("compile-descriptor-source", decision.decision());
        assertEquals("descriptor", decision.sourceSelection());
        assertFalse(decision.irGpuSourceRequested());
        assertFalse(decision.productionProfileRequested());
        assertTrue(decision.toPropertiesText().contains("diagnostic.0=descriptor source remains selected"));
    }

    @Test
    void blocksIrGpuSourceWhenReconstructedSourceIsUnavailable() {
        GpuBackendSourceSwitchingDecision decision = GpuBackendSourceSwitchingDecision.evaluate(
                provenance(
                        GpuBackendCompileOptions.openClIrGpuSource(List.of()),
                        GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE
                ),
                module(),
                GpuBackendSourceReconstructionResult.blocked(
                        GpuBackendTarget.OPENCL,
                        "irgpu-backend-neutral-source",
                        "ir-text-v1",
                        "opencl-irgpu-source-compile",
                        List.of("irgpu-entry-parameter-metadata-missing"),
                        List.of("OpenCL source can be reconstructed from IrGpu")
                ),
                null
        );

        assertEquals("blocked", decision.status());
        assertEquals("reject-irgpu-source-unavailable", decision.decision());
        assertTrue(decision.irGpuSourceRequested());
        assertFalse(decision.sourceReady());
        assertFalse(decision.sourceReconstructed());
        assertFalse(decision.sourceAvailable());
        assertFalse(decision.sourceParityChecked());
        assertFalse(decision.sourcePromotionReviewReady());
        assertTrue(decision.toPropertiesText().contains(
                "diagnostic.0=IrGpu source was requested but reconstructed source is not available"
        ));
    }

    @Test
    void blocksIrGpuSourceWhenReconstructionIsReadyButSourcePayloadIsUnavailable() {
        GpuBackendSourceSwitchingDecision decision = GpuBackendSourceSwitchingDecision.evaluate(
                provenance(
                        GpuBackendCompileOptions.openClIrGpuSource(List.of()),
                        GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE
                ),
                module(),
                GpuBackendSourceReconstructionResult.ready(
                        GpuBackendTarget.OPENCL,
                        "irgpu-backend-neutral-source",
                        "ir-text-v1",
                        "opencl-irgpu-source-compile",
                        List.of("OpenCL source can be reconstructed from IrGpu when the runtime source path is enabled")
                ),
                null
        );

        assertEquals("blocked", decision.status());
        assertEquals("reject-irgpu-source-unavailable", decision.decision());
        assertTrue(decision.sourceReady());
        assertFalse(decision.sourceReconstructed());
        assertFalse(decision.sourceAvailable());
        assertTrue(decision.toPropertiesText().contains("sourceReady=true"));
        assertTrue(decision.toPropertiesText().contains(
                "diagnostic.0=IrGpu source was requested and reconstruction is ready, but assembled source is not available"
        ));
    }

    @Test
    void blocksIrGpuSourceWhenParityDoesNotMatch() {
        GpuBackendSourceReconstructionResult reconstruction = GpuBackendSourceReconstructionResult.reconstructedSource(
                GpuBackendTarget.OPENCL,
                "__kernel void jtg_kernel(__global int* output) { output[0] = 2; }",
                "irgpu-backend-neutral-source",
                "ir-text-v1",
                "opencl-irgpu-source-compile",
                List.of("sourceParity.checked=true", "sourceParity.matched=false")
        );

        GpuBackendSourceSwitchingDecision decision = GpuBackendSourceSwitchingDecision.evaluate(
                provenance(
                        GpuBackendCompileOptions.openClIrGpuSource(List.of()),
                        GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE
                ),
                module(),
                reconstruction,
                GpuBackendSourcePromotionGate.evaluate(
                        reconstruction,
                        GpuRuntimeEquivalenceEvidence.passed(null, 1, 1, List.of("production source fixture equivalent")),
                        GpuRuntimeFallbackEvidence.none()
                )
        );

        assertEquals("blocked", decision.status());
        assertEquals("reject-irgpu-source-parity", decision.decision());
        assertTrue(decision.sourceReady());
        assertTrue(decision.sourceReconstructed());
        assertTrue(decision.sourceAvailable());
        assertTrue(decision.sourceParityChecked());
        assertFalse(decision.sourceParityMatched());
        assertTrue(decision.toPropertiesText().contains(
                "diagnostic.0=IrGpu source was requested but reconstructed source parity has not matched descriptor source"
        ));
    }

    @Test
    void allowsReviewIrGpuSourceWhenSourceParityMatches() {
        GpuBackendSourceReconstructionResult reconstruction = parityMatchedReconstruction();

        GpuBackendSourceSwitchingDecision decision = GpuBackendSourceSwitchingDecision.evaluate(
                provenance(
                        GpuBackendCompileOptions.openClIrGpuSource(List.of()),
                        GpuRuntimeCompileOptions.OPENCL_IRGPU_SOURCE_REVIEW_PROFILE
                ),
                module(),
                reconstruction,
                GpuBackendSourcePromotionGate.evaluate(
                        reconstruction,
                        GpuRuntimeEquivalenceEvidence.notRun(null, "runtime equivalence was not executed"),
                        GpuRuntimeFallbackEvidence.none()
                )
        );

        assertEquals("review-ready", decision.status());
        assertEquals("compile-irgpu-source-review", decision.decision());
        assertTrue(decision.sourceReady());
        assertTrue(decision.sourceReconstructed());
        assertTrue(decision.sourceAvailable());
        assertTrue(decision.sourceParityChecked());
        assertTrue(decision.sourceParityMatched());
        assertFalse(decision.productionProfileRequested());
        assertFalse(decision.productionSourceSwitchingEnabled());
    }

    @Test
    void blocksProductionIrGpuSourceUntilProductionSwitchingIsEnabled() {
        GpuBackendSourceReconstructionResult reconstruction = parityMatchedReconstruction();

        GpuBackendSourceSwitchingDecision decision = GpuBackendSourceSwitchingDecision.evaluate(
                provenance(GpuBackendCompileOptions.openClIrGpuSource(List.of()), "vendor-tuned"),
                module(),
                reconstruction,
                GpuBackendSourcePromotionGate.evaluate(
                        reconstruction,
                        GpuRuntimeEquivalenceEvidence.notRun(null, "runtime equivalence was not executed"),
                        GpuRuntimeFallbackEvidence.none()
                )
        );

        assertEquals("blocked", decision.status());
        assertEquals("reject-production-irgpu-source", decision.decision());
        assertTrue(decision.productionProfileRequested());
        assertFalse(decision.productionSourceSwitchingEnabled());
        assertTrue(decision.toPropertiesText().contains(
                "diagnostic.0=production-like profile requested IrGpu source but opencl.productionSourceSwitching is disabled"
        ));
    }

    @Test
    void allowsProductionIrGpuSourceWhenProductionSwitchingIsEnabled() {
        GpuBackendSourceReconstructionResult reconstruction = parityMatchedReconstruction();

        GpuBackendSourceSwitchingDecision decision = GpuBackendSourceSwitchingDecision.evaluate(
                provenance(
                        GpuBackendCompileOptions.openClProductionIrGpuSource(List.of())
                                .withProductionPromotionDecision(productionEnabledDecision())
                                .withProductionPromotionOperatorAccepted(true),
                        "vendor-tuned"
                ),
                module(),
                reconstruction,
                GpuBackendSourcePromotionGate.evaluate(
                        reconstruction,
                        GpuRuntimeEquivalenceEvidence.passed(null, 1, 1, List.of("production source fixture equivalent")),
                        GpuRuntimeFallbackEvidence.none()
                )
        );

        assertEquals("production-switch-enabled", decision.status());
        assertEquals("compile-irgpu-source-production", decision.decision());
        assertTrue(decision.productionProfileRequested());
        assertTrue(decision.productionSourceSwitchingEnabled());
        assertEquals("enabled", decision.productionSourceSwitching());
        assertEquals("production-enabled", decision.productionPromotionDecisionMode());
        assertTrue(decision.productionPromotionOperatorAccepted());
    }

    @Test
    void blocksProductionIrGpuSourceUntilOperatorAcceptsPromotion() {
        GpuBackendSourceReconstructionResult reconstruction = parityMatchedReconstruction();

        GpuBackendSourceSwitchingDecision decision = GpuBackendSourceSwitchingDecision.evaluate(
                provenance(
                        GpuBackendCompileOptions.openClProductionIrGpuSource(List.of())
                                .withProductionPromotionDecision(productionEnabledDecision()),
                        "vendor-tuned"
                ),
                module(),
                reconstruction,
                GpuBackendSourcePromotionGate.evaluate(
                        reconstruction,
                        GpuRuntimeEquivalenceEvidence.passed(null, 1, 1, List.of("production source fixture equivalent")),
                        GpuRuntimeFallbackEvidence.none()
                )
        );

        assertEquals("blocked", decision.status());
        assertEquals("reject-production-irgpu-source", decision.decision());
        assertTrue(decision.productionProfileRequested());
        assertTrue(decision.productionSourceSwitchingEnabled());
        assertEquals("production-enabled", decision.productionPromotionDecisionMode());
        assertFalse(decision.productionPromotionOperatorAccepted());
        assertTrue(decision.toPropertiesText().contains(
                "diagnostic.0=production-like profile requested IrGpu source but production promotion was not explicitly accepted by the operator"
        ));
    }

    @Test
    void blocksProductionIrGpuSourceUntilSourcePromotionGateIsReviewReady() {
        GpuBackendSourceReconstructionResult reconstruction = parityMatchedReconstruction();

        GpuBackendSourceSwitchingDecision decision = GpuBackendSourceSwitchingDecision.evaluate(
                provenance(
                        GpuBackendCompileOptions.openClProductionIrGpuSource(List.of())
                                .withProductionPromotionDecision(productionEnabledDecision()),
                        "vendor-tuned"
                ),
                module(),
                reconstruction,
                GpuBackendSourcePromotionGate.evaluate(
                        reconstruction,
                        GpuRuntimeEquivalenceEvidence.notRun(null, "runtime equivalence was not executed"),
                        GpuRuntimeFallbackEvidence.none()
                )
        );

        assertEquals("blocked", decision.status());
        assertEquals("reject-production-irgpu-source", decision.decision());
        assertTrue(decision.productionProfileRequested());
        assertTrue(decision.productionSourceSwitchingEnabled());
        assertEquals("production-enabled", decision.productionPromotionDecisionMode());
        assertFalse(decision.sourcePromotionReviewReady());
        assertTrue(decision.toPropertiesText().contains(
                "sourcePromotionFirstBlocker=runtime equivalence must execute and pass before backend source promotion"
        ));
        assertTrue(decision.toPropertiesText().contains(
                "diagnostic.0=production-like profile requested IrGpu source but backend source promotion gate is not review-ready"
        ));
    }

    @Test
    void blocksProductionIrGpuSourceUntilProductionPromotionDecisionIsEnabled() {
        GpuBackendSourceReconstructionResult reconstruction = parityMatchedReconstruction();

        GpuBackendSourceSwitchingDecision decision = GpuBackendSourceSwitchingDecision.evaluate(
                provenance(GpuBackendCompileOptions.openClProductionIrGpuSource(List.of()), "vendor-tuned"),
                module(),
                reconstruction,
                GpuBackendSourcePromotionGate.evaluate(
                        reconstruction,
                        GpuRuntimeEquivalenceEvidence.passed(null, 1, 1, List.of("production source fixture equivalent")),
                        GpuRuntimeFallbackEvidence.none()
                )
        );

        assertEquals("blocked", decision.status());
        assertEquals("reject-production-irgpu-source", decision.decision());
        assertTrue(decision.productionProfileRequested());
        assertTrue(decision.productionSourceSwitchingEnabled());
        assertEquals("diagnostic-only", decision.productionPromotionDecisionMode());
        assertTrue(decision.toPropertiesText().contains(
                "diagnostic.0=production-like profile requested IrGpu source but production promotion decision is not production-enabled"
        ));
    }

    @Test
    void blocksProductionIrGpuSourceWhenPromotionDecisionIsOnlyReviewReady() {
        GpuBackendSourceReconstructionResult reconstruction = parityMatchedReconstruction();

        GpuBackendSourceSwitchingDecision decision = GpuBackendSourceSwitchingDecision.evaluate(
                provenance(
                        GpuBackendCompileOptions.openClProductionIrGpuSource(List.of())
                                .withProductionPromotionDecision(reviewReadyDecision()),
                        "vendor-tuned"
                ),
                module(),
                reconstruction,
                GpuBackendSourcePromotionGate.evaluate(
                        reconstruction,
                        GpuRuntimeEquivalenceEvidence.passed(null, 1, 1, List.of("production source fixture equivalent")),
                        GpuRuntimeFallbackEvidence.none()
                )
        );

        assertEquals("blocked", decision.status());
        assertEquals("reject-production-irgpu-source", decision.decision());
        assertTrue(decision.productionProfileRequested());
        assertTrue(decision.productionSourceSwitchingEnabled());
        assertEquals("review-ready", decision.productionPromotionDecisionMode());
        assertTrue(decision.toPropertiesText().contains(
                "diagnostic.0=production-like profile requested IrGpu source but production promotion decision is not production-enabled"
        ));
    }

    private static GpuProductionPromotionDecision reviewReadyDecision() {
        return new GpuProductionPromotionDecision(
                GpuProductionPromotionDecision.REVIEW_READY,
                "blocked",
                true,
                false,
                false,
                "production-source-switching-disabled",
                "none",
                "test review-ready production promotion decision"
        );
    }

    private static GpuProductionPromotionDecision productionEnabledDecision() {
        return new GpuProductionPromotionDecision(
                GpuProductionPromotionDecision.PRODUCTION_ENABLED,
                "production-ready",
                true,
                true,
                true,
                "none",
                "none",
                "test production promotion decision"
        );
    }

    private static GpuBackendSourceReconstructionResult parityMatchedReconstruction() {
        return GpuBackendSourceReconstructionResult.reconstructedSource(
                GpuBackendTarget.OPENCL,
                "__kernel void jtg_kernel(__global int* output) { output[0] = 1; }",
                "irgpu-backend-neutral-source",
                "ir-text-v1",
                "opencl-irgpu-source-compile",
                List.of("sourceParity.checked=true", "sourceParity.matched=true")
        );
    }

    private static GpuBackendModuleArtifact module() {
        return GpuBackendModuleArtifact.openClSource(
                "__kernel void jtg_kernel(__global int* output) { output[0] = 1; }",
                "javatogpu/sample/Demo/kernel.cl",
                "test-lowerer-v1",
                "irgpu-backend-neutral-source",
                "opencl-irgpu-source-compile"
        );
    }

    private static GpuRuntimeCompileProvenance provenance(GpuBackendCompileOptions backendOptions, String optimizationProfile) {
        return new GpuRuntimeCompileProvenance(
                GpuBackendTarget.OPENCL,
                "OpenCL",
                "Mock GPU",
                "Mock Vendor",
                "Mock Driver",
                "OpenCL 3.0 Mock",
                48L,
                65_536L,
                512L,
                1L,
                true,
                true,
                false,
                backendOptions.flags(),
                backendOptions,
                optimizationProfile,
                GpuRuntimeCompileProvenance.NO_FALLBACK
        );
    }
}
