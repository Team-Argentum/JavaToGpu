package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.autoVectorizationPrePostEvidence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GpuIrOptimizationValidationRuntimeEvidenceRegistryContractTest {
    @Test
    void defaultRegistryDoesNotIncludeRuntimeEvidenceRules() {
        List<String> ruleIds = ruleIds(GpuIrOptimizationValidationRules.defaultRegistry());

        assertEquals(List.of(
                "safety.clean",
                "optimizer.noBlockingDiagnostics",
                "optimizer.advisoryDiagnostics"
        ), ruleIds);
        assertFalse(ruleIds.contains("cse.literalRuntimeEquivalenceEvidence"));
        assertFalse(ruleIds.contains("cse.literalPromotionRuntimeEquivalenceGate"));
        assertFalse(ruleIds.contains("autoVectorization.prototypePrePostRuntimeEquivalenceEvidence"));
        assertFalse(ruleIds.contains("autoVectorization.prototypePrePostRuntimeEquivalenceGate"));
    }

    @Test
    void cseRuntimeEvidenceRegistryKeepsStableRuleOrder() {
        assertEquals(List.of(
                "cse.literalRuntimeEquivalenceEvidence",
                "cse.literalPromotionRuntimeEquivalenceGate"
        ), ruleIds(GpuIrOptimizationValidationRules.runtimeEquivalenceEvidenceRegistry()));
    }

    @Test
    void autoVectorizationPrePostRegistryKeepsStableRuleOrder() {
        assertEquals(List.of(
                "autoVectorization.prototypePrePostRuntimeEquivalenceEvidence",
                "autoVectorization.prototypePrePostRuntimeEquivalenceGate"
        ), ruleIds(GpuIrOptimizationValidationRules.autoVectorizationPrototypePrePostRuntimeEquivalenceRegistry(
                autoVectorizationPrePostEvidence(true, List.of())
        )));
    }

    @Test
    void combinedRuntimeEvidenceRegistryKeepsStableRuleOrder() {
        assertEquals(List.of(
                "cse.literalRuntimeEquivalenceEvidence",
                "cse.literalPromotionRuntimeEquivalenceGate",
                "autoVectorization.prototypePrePostRuntimeEquivalenceEvidence",
                "autoVectorization.prototypePrePostRuntimeEquivalenceGate"
        ), ruleIds(GpuIrOptimizationValidationRules.runtimeEquivalenceEvidenceRegistry(
                autoVectorizationPrePostEvidence(true, List.of())
        )));
    }

    @Test
    void autoVectorizationRuntimeEvidenceRulesRequireExplicitPrePostArtifact() {
        assertThrows(NullPointerException.class, () ->
                GpuIrOptimizationValidationRules.autoVectorizationPrototypePrePostRuntimeEquivalenceRegistry(null)
        );
        assertThrows(NullPointerException.class, () ->
                GpuIrOptimizationValidationRules.runtimeEquivalenceEvidenceRegistry(null)
        );
        assertThrows(NullPointerException.class, () ->
                GpuIrOptimizationValidationRules.autoVectorizationPrototypePrePostRuntimeEquivalenceRules(null)
        );
        assertThrows(NullPointerException.class, () ->
                GpuIrOptimizationValidationRules.runtimeEquivalenceEvidenceRules(null)
        );
    }

    private static List<String> ruleIds(GpuIrOptimizationValidationRuleRegistry registry) {
        return registry.rules().stream()
                .map(GpuIrOptimizationValidationRule::id)
                .toList();
    }
}
