package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationProofSummaryTest {
    @Test
    void summarizesWarningsAndGuardDiagnostics() {
        GpuIrAutoVectorizationProofSummary summary = GpuIrAutoVectorizationProofSummary.fromGuards(
                "memoryLegality",
                "stmt[0]",
                1,
                List.of(new GpuIrAutoVectorizationRewriteGuardDiagnostic(
                        GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE,
                        "stmt[0]",
                        "source array `left` uses constant memory address space"
                ))
        );

        assertFalse(summary.rewriteSafe());
        assertTrue(summary.hasDiagnostics());
        assertEquals(2, summary.diagnosticCount());
        assertEquals(Map.of(GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE, 1L), summary.guardFamilyTypeCounts());
        assertEquals(Map.of("memoryAddressSpace", 1L), summary.guardFamilyCounts());
        assertTrue(summary.summaryLine().contains("kind=memoryLegality"));
        assertTrue(summary.summaryLine().contains("diagnostics=2"));
    }

    @Test
    void reportsSafeWhenNoWarningsOrGuardsExist() {
        GpuIrAutoVectorizationProofSummary summary = GpuIrAutoVectorizationProofSummary.fromGuards(
                "controlFlowBoundary",
                "stmt[0]",
                0,
                List.of()
        );

        assertTrue(summary.rewriteSafe());
        assertFalse(summary.hasDiagnostics());
        assertEquals(0, summary.diagnosticCount());
        assertEquals(Map.of(), summary.guardFamilyCounts());
    }

    @Test
    void exposesStableArtifactFields() {
        GpuIrAutoVectorizationProofSummary summary = GpuIrAutoVectorizationProofSummary.fromGuards(
                "rewritePlan",
                "kernel",
                1,
                List.of(new GpuIrAutoVectorizationRewriteGuardDiagnostic(
                        GpuIrAutoVectorizationRewriteGuardFamily.CONTROL_FLOW_BOUNDARY,
                        "stmt[1]",
                        "previous statement stmt[0] is a control-flow boundary"
                ))
        );

        Map<String, String> fields = summary.artifactFields("autoVectorizationProofRewritePlan");

        assertEquals("rewritePlan", fields.get("autoVectorizationProofRewritePlanKind"));
        assertEquals("kernel", fields.get("autoVectorizationProofRewritePlanLocation"));
        assertEquals("false", fields.get("autoVectorizationProofRewritePlanRewriteSafe"));
        assertEquals("1", fields.get("autoVectorizationProofRewritePlanWarnings"));
        assertEquals("1", fields.get("autoVectorizationProofRewritePlanGuardDiagnostics"));
        assertEquals("2", fields.get("autoVectorizationProofRewritePlanDiagnostics"));
        assertEquals("1", fields.get("autoVectorizationProofRewritePlanGuardFamily.controlFlowBoundary"));
        assertTrue(fields.get("autoVectorizationProofRewritePlanSummary").contains("kind=rewritePlan"));
    }

    @Test
    void returnsImmutableGuardFamilyCountsAndRejectsInvalidMetadata() {
        GpuIrAutoVectorizationProofSummary summary = GpuIrAutoVectorizationProofSummary.fromGuards(
                "memoryLegality",
                "stmt[0]",
                0,
                List.of()
        );

        assertThrows(UnsupportedOperationException.class, () -> summary.guardFamilyTypeCounts().put(
                GpuIrAutoVectorizationRewriteGuardFamily.OTHER,
                1L
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationProofSummary(
                "",
                "stmt[0]",
                true,
                0,
                0,
                Map.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationProofSummary(
                "memoryLegality",
                "",
                true,
                0,
                0,
                Map.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationProofSummary(
                "memoryLegality",
                "stmt[0]",
                true,
                -1,
                0,
                Map.of()
        ));
    }
}
