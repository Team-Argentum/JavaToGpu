package net.sixik.ga_utils.javatogpu.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuRuntimeEquivalenceEvidenceTest {
    @Test
    void persistsDeterministicRawComparisonCaseProperties() {
        GpuRuntimeEquivalenceCaseEvidence caseEvidence = new GpuRuntimeEquivalenceCaseEvidence(
                "runtime-invocation-0",
                "descriptor-source-vs-irgpu-reconstructed-source",
                Map.of("output", "[0]"),
                Map.of("output", "[7]"),
                Map.of("output", "[7]"),
                Map.of("output", "exact-int-array"),
                Map.of("output", true),
                List.of()
        );
        GpuRuntimeEquivalenceEvidence evidence = GpuRuntimeEquivalenceEvidence.passed(
                null,
                1,
                1,
                List.of("outputs matched"),
                List.of(caseEvidence)
        );

        String properties = evidence.toPropertiesText();

        assertEquals(1, evidence.comparisonCases().size());
        assertTrue(properties.contains("comparison.case.count=1"));
        assertTrue(properties.contains("comparison.case.0.comparisonMode=descriptor-source-vs-irgpu-reconstructed-source"));
        assertTrue(properties.contains("comparison.case.0.input.0.value=[0]"));
        assertTrue(properties.contains("comparison.case.0.output.0.reference=[7]"));
        assertTrue(properties.contains("comparison.case.0.output.0.candidate=[7]"));
        assertTrue(properties.contains("comparison.case.0.output.0.tolerance=exact-int-array"));
        assertTrue(properties.contains("comparison.case.0.output.0.equivalent=true"));
        assertThrows(UnsupportedOperationException.class, () -> evidence.comparisonCases().add(caseEvidence));
    }

    @Test
    void rejectsComparisonCaseCountMismatches() {
        GpuRuntimeEquivalenceCaseEvidence caseEvidence = new GpuRuntimeEquivalenceCaseEvidence(
                "runtime-invocation-0",
                "test-comparison",
                Map.of("output", "[0]"),
                Map.of("output", "[7]"),
                Map.of("output", "[7]"),
                Map.of("output", "exact-int-array"),
                Map.of("output", true),
                List.of()
        );

        assertThrows(IllegalArgumentException.class, () -> GpuRuntimeEquivalenceEvidence.passed(
                null,
                2,
                1,
                List.of(),
                List.of(caseEvidence)
        ));
        assertThrows(IllegalArgumentException.class, () -> GpuRuntimeEquivalenceEvidence.passed(
                null,
                1,
                2,
                List.of(),
                List.of(caseEvidence)
        ));
    }
}
