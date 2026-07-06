package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.sixik.ga_utils.javatogpu.irvalidation.GpuIrOptimizationValidationRuleTestFixtures.validationReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationCiGateIndexAcceptanceTest {
    @Test
    void canRepresentAcceptedGateIndexArtifacts() {
        GpuIrOptimizationValidationCiGateIndexAcceptance acceptance =
                new GpuIrOptimizationValidationCiGateIndexAcceptance(
                        "acceptedKernel",
                        "accepted",
                        true,
                        false,
                        "accepted/allGatesAccepted",
                        "none",
                        "",
                        "optimizer CI gate index acceptance method=acceptedKernel verdict=accepted accepted=true"
                );
        Map<String, String> fields = acceptance.artifactFields();

        assertTrue(acceptance.accepted());
        assertFalse(acceptance.rejected());
        assertFalse(acceptance.failBuild());
        assertEquals("accepted", acceptance.verdict());
        assertEquals("accepted/allGatesAccepted", acceptance.reason());
        assertEquals("none", acceptance.firstRejectedGate());
        assertEquals("", acceptance.firstConsistencyFailedCheck());
        assertEquals("acceptedKernel", fields.get("optimizerCiGateIndexAcceptanceMethod"));
        assertEquals("accepted", fields.get("optimizerCiGateIndexAcceptanceVerdict"));
        assertEquals("true", fields.get("optimizerCiGateIndexAcceptanceAccepted"));
        assertEquals("false", fields.get("optimizerCiGateIndexAcceptanceRejected"));
        assertEquals("false", fields.get("optimizerCiGateIndexAcceptanceFailBuild"));
        assertEquals("accepted/allGatesAccepted", fields.get("optimizerCiGateIndexAcceptanceReason"));
        assertTrue(fields.get("optimizerCiGateIndexAcceptanceCiSummaryLine").contains("accepted=true"));
    }

    @Test
    void rejectsGateRejectedIndexArtifacts() {
        GpuIrOptimizationValidationCiGateIndex index = new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner()
                .runCiGateIndex(validationReport("gateRejectedKernel"));

        GpuIrOptimizationValidationCiGateIndexAcceptance acceptance = index.acceptance();
        Map<String, String> fields = acceptance.artifactFields("indexAcceptance.");

        assertFalse(acceptance.accepted());
        assertTrue(acceptance.rejected());
        assertTrue(acceptance.failBuild());
        assertEquals("rejected", acceptance.verdict());
        assertEquals("rejected/gateRejected", acceptance.reason());
        assertEquals("autoVectorization", acceptance.firstRejectedGate());
        assertEquals("", acceptance.firstConsistencyFailedCheck());
        assertEquals("false", fields.get("indexAcceptance.Accepted"));
        assertEquals("true", fields.get("indexAcceptance.Rejected"));
        assertEquals("true", fields.get("indexAcceptance.FailBuild"));
        assertEquals("rejected/gateRejected", fields.get("indexAcceptance.Reason"));
        assertTrue(fields.get("indexAcceptance.CiSummaryLine").contains("firstRejectedGate=autoVectorization"));
    }

    @Test
    void rejectsInconsistentArtifactsBeforeNormalGateDecision() {
        GpuIrOptimizationValidationCiGateIndex index = new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner()
                .runCiGateIndex(validationReport("inconsistentIndexKernel"));
        GpuIrOptimizationValidationCiGateIndexConsistencyReport consistency =
                new GpuIrOptimizationValidationCiGateIndexConsistencyReport(
                        "inconsistentIndexKernel",
                        "inconsistent",
                        false,
                        10,
                        1,
                        List.of("acceptedMatchesGateFlags")
                );

        GpuIrOptimizationValidationCiGateIndexAcceptance acceptance =
                GpuIrOptimizationValidationCiGateIndexAcceptance.from(index, consistency);
        Map<String, String> fields = acceptance.artifactFields();

        assertFalse(acceptance.accepted());
        assertTrue(acceptance.rejected());
        assertTrue(acceptance.failBuild());
        assertEquals("rejected", acceptance.verdict());
        assertEquals("rejected/inconsistentArtifact", acceptance.reason());
        assertEquals("acceptedMatchesGateFlags", acceptance.firstConsistencyFailedCheck());
        assertEquals("false", fields.get("optimizerCiGateIndexAcceptanceAccepted"));
        assertEquals("true", fields.get("optimizerCiGateIndexAcceptanceRejected"));
        assertEquals("rejected/inconsistentArtifact", fields.get("optimizerCiGateIndexAcceptanceReason"));
        assertTrue(fields.get("optimizerCiGateIndexAcceptanceCiSummaryLine").contains("consistent=false"));
        assertTrue(fields.get("optimizerCiGateIndexAcceptanceCiSummaryLine")
                .contains("firstConsistencyFailedCheck=acceptedMatchesGateFlags"));
    }

    @Test
    void rejectsInvalidInputsAndReturnsImmutableFields() {
        GpuIrOptimizationValidationCiGateIndex index = new GpuIrOptimizationValidationOptimizerEnablementArtifactRunner()
                .runCiGateIndex(validationReport("invalidInputKernel"));
        GpuIrOptimizationValidationCiGateIndexAcceptance acceptance = index.acceptance();
        Map<String, String> fields = acceptance.artifactFields();

        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationCiGateIndexAcceptance.from(
                (GpuIrOptimizationValidationCiGateIndex) null
        ));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationCiGateIndexAcceptance.from(
                null,
                index.consistencyReport()
        ));
        assertThrows(NullPointerException.class, () -> GpuIrOptimizationValidationCiGateIndexAcceptance.from(
                index,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> acceptance.artifactFields(""));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationCiGateIndexAcceptance(
                "",
                "accepted",
                true,
                false,
                "accepted/allGatesAccepted",
                "none",
                "",
                "summary"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationCiGateIndexAcceptance(
                "kernel",
                "accepted",
                true,
                true,
                "accepted/allGatesAccepted",
                "none",
                "",
                "summary"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationCiGateIndexAcceptance(
                "kernel",
                "accepted",
                true,
                false,
                "rejected/gateRejected",
                "none",
                "",
                "summary"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationCiGateIndexAcceptance(
                "kernel",
                "rejected",
                false,
                true,
                "accepted/allGatesAccepted",
                "autoVectorization",
                "",
                "summary"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationCiGateIndexAcceptance(
                "kernel",
                "accepted",
                true,
                false,
                "accepted/allGatesAccepted",
                "none",
                "",
                ""
        ));
    }
}
