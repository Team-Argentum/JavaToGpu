package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrRuntimeEquivalenceCaseEvidenceTest {
    @Test
    void exposesDeterministicImmutableRawCaseFields() {
        GpuIrRuntimeEquivalenceCaseEvidence evidence = new GpuIrRuntimeEquivalenceCaseEvidence(
                "case-a",
                Map.of("y", "11", "x", "7"),
                Map.of("outB", "15", "outA", "36"),
                Map.of("outB", "15", "outA", "36"),
                Map.of("outB", "15", "outA", "36"),
                Map.of("outB", "exact-int", "outA", "exact-int"),
                Map.of("outB", true, "outA", true),
                List.of()
        );

        Map<String, String> fields = evidence.artifactFields("case.");

        assertTrue(evidence.successful());
        assertEquals("x", fields.get("case.Input.0.Name"));
        assertEquals("outA", fields.get("case.Output.0.Name"));
        assertEquals("36", fields.get("case.Output.0.CpuReference"));
        assertEquals("true", fields.get("case.Output.0.Equivalent"));
        assertThrows(UnsupportedOperationException.class, () -> evidence.inputs().put("z", "1"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void rejectsEmptyOrInconsistentOutputEvidence() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrRuntimeEquivalenceCaseEvidence(
                "case-a",
                Map.of("x", "7"),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrRuntimeEquivalenceCaseEvidence(
                "case-a",
                Map.of("x", "7"),
                Map.of("outA", "36"),
                Map.of("outB", "36"),
                Map.of("outA", "36"),
                Map.of("outA", "exact-int"),
                Map.of("outA", true),
                List.of()
        ));
    }
}
