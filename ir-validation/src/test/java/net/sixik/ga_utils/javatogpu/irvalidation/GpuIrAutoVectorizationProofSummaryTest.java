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
    void proofSurfacesExposeSameBaseArtifactContract() {
        GpuIrAutoVectorizationProofSummary rewritePlan = GpuIrAutoVectorizationProofSummary.fromGuards(
                "rewritePlan",
                "kernel",
                1,
                List.of(new GpuIrAutoVectorizationRewriteGuardDiagnostic(
                        GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE,
                        "stmt[0]",
                        "source array `input` uses constant memory address space"
                ))
        );
        GpuIrAutoVectorizationMemoryLegalityReport memoryLegality = new GpuIrAutoVectorizationMemoryLegalityReport(
                "stmt[0]",
                List.of("out"),
                List.of("input"),
                List.of("target array `out` is also read in the loop body"),
                List.of(new GpuIrAutoVectorizationRewriteGuardDiagnostic(
                        GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE,
                        "stmt[0]",
                        "source array `input` uses constant memory address space"
                ))
        );
        GpuIrAutoVectorizationControlFlowBoundaryReport controlFlowBoundary = new GpuIrAutoVectorizationControlFlowBoundaryReport(
                "stmt[1]",
                "stmt[0]",
                "previous",
                GpuIrAutoVectorizationControlFlowBoundaryKind.CONTROL_FLOW_BOUNDARY
        );

        assertBaseArtifactContract(rewritePlan.artifactFields("autoVectorizationProofRewritePlan"), "autoVectorizationProofRewritePlan");
        assertBaseArtifactContract(memoryLegality.artifactFields(), "autoVectorizationProofMemoryLegality");
        assertBaseArtifactContract(controlFlowBoundary.artifactFields(), "autoVectorizationProofControlFlowBoundary");
    }

    @Test
    void proofBundleAggregatesMultipleProofSurfaces() {
        GpuIrAutoVectorizationProofSummary rewritePlan = GpuIrAutoVectorizationProofSummary.fromGuards(
                "rewritePlan",
                "kernel",
                1,
                List.of(new GpuIrAutoVectorizationRewriteGuardDiagnostic(
                        GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE,
                        "stmt[0]",
                        "source array `input` uses constant memory address space"
                ))
        );
        GpuIrAutoVectorizationProofSummary controlFlow = GpuIrAutoVectorizationProofSummary.fromGuards(
                "controlFlowBoundary",
                "stmt[1]",
                0,
                List.of(new GpuIrAutoVectorizationRewriteGuardDiagnostic(
                        GpuIrAutoVectorizationRewriteGuardFamily.CONTROL_FLOW_BOUNDARY,
                        "stmt[1]",
                        "previous statement stmt[0] is a control-flow boundary"
                ))
        );
        GpuIrAutoVectorizationProofSummary memory = GpuIrAutoVectorizationProofSummary.fromGuards(
                "memoryLegality",
                "stmt[0]",
                0,
                List.of()
        );

        GpuIrAutoVectorizationProofBundle bundle = GpuIrAutoVectorizationProofBundle.of(rewritePlan, controlFlow, memory);
        Map<String, String> fields = bundle.artifactFields();

        assertFalse(bundle.rewriteSafe());
        assertTrue(bundle.hasDiagnostics());
        assertEquals(1, bundle.warningCount());
        assertEquals(2, bundle.guardDiagnosticCount());
        assertEquals(3, bundle.diagnosticCount());
        assertEquals(List.of("rewritePlan", "controlFlowBoundary", "memoryLegality"), bundle.proofKinds());
        assertEquals(Map.of(
                GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE, 1L,
                GpuIrAutoVectorizationRewriteGuardFamily.CONTROL_FLOW_BOUNDARY, 1L
        ), bundle.guardFamilyTypeCounts());
        assertEquals("3", fields.get("autoVectorizationProofBundleProofs"));
        assertEquals("rewritePlan,controlFlowBoundary,memoryLegality", fields.get("autoVectorizationProofBundleKinds"));
        assertEquals("false", fields.get("autoVectorizationProofBundleRewriteSafe"));
        assertEquals("3", fields.get("autoVectorizationProofBundleDiagnostics"));
        assertEquals("1", fields.get("autoVectorizationProofBundleGuardFamily.memoryAddressSpace"));
        assertEquals("1", fields.get("autoVectorizationProofBundleGuardFamily.controlFlowBoundary"));
        assertTrue(bundle.summaryLine().contains("proofs=3"));
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
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationProofBundle(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationProofBundle(java.util.Arrays.asList(summary, null)));
    }

    private void assertBaseArtifactContract(Map<String, String> fields, String prefix) {
        assertTrue(fields.containsKey(prefix + "Kind"));
        assertTrue(fields.containsKey(prefix + "Location"));
        assertTrue(fields.containsKey(prefix + "RewriteSafe"));
        assertTrue(fields.containsKey(prefix + "Warnings"));
        assertTrue(fields.containsKey(prefix + "GuardDiagnostics"));
        assertTrue(fields.containsKey(prefix + "Diagnostics"));
        assertTrue(fields.containsKey(prefix + "Summary"));
    }
}
