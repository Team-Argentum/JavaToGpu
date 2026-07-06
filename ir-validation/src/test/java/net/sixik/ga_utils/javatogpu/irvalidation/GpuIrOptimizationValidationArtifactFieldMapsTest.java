package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GpuIrOptimizationValidationArtifactFieldMapsTest {
    @Test
    void appendsNestedPrefixWithoutDroppingExistingTargetFields() {
        Map<String, String> target = new LinkedHashMap<>();
        target.put("outerMethod", "kernel");
        Map<String, String> source = new LinkedHashMap<>();
        source.put("validationRulesVerdict", "pass");
        source.put("validationRulesAccepted", "true");

        GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(
                target,
                "optimizerCiGateIndexCseGate.RuleArtifact.",
                source
        );

        assertEquals("kernel", target.get("outerMethod"));
        assertEquals(
                "pass",
                target.get("optimizerCiGateIndexCseGate.RuleArtifact.validationRulesVerdict")
        );
        assertEquals(
                "true",
                target.get("optimizerCiGateIndexCseGate.RuleArtifact.validationRulesAccepted")
        );
        assertEquals(
                "outerMethod,optimizerCiGateIndexCseGate.RuleArtifact.validationRulesVerdict,"
                        + "optimizerCiGateIndexCseGate.RuleArtifact.validationRulesAccepted",
                String.join(",", target.keySet())
        );
    }

    @Test
    void rejectsInvalidNestedFieldInputs() {
        Map<String, String> target = new LinkedHashMap<>();
        Map<String, String> source = Map.of("validationRulesVerdict", "pass");

        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(
                null,
                "nested.",
                source
        ));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(
                target,
                "",
                source
        ));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(
                target,
                " ",
                source
        ));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationArtifactFieldMaps.putNestedFields(
                target,
                "nested.",
                null
        ));
    }
}
