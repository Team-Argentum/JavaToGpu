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
        Map<String, String> defaultFields = summary.artifactFields();

        assertEquals("rewritePlan", fields.get("autoVectorizationProofRewritePlanKind"));
        assertEquals("kernel", fields.get("autoVectorizationProofRewritePlanLocation"));
        assertEquals("false", fields.get("autoVectorizationProofRewritePlanRewriteSafe"));
        assertEquals("1", fields.get("autoVectorizationProofRewritePlanWarnings"));
        assertEquals("1", fields.get("autoVectorizationProofRewritePlanGuardDiagnostics"));
        assertEquals("2", fields.get("autoVectorizationProofRewritePlanDiagnostics"));
        assertEquals("1", fields.get("autoVectorizationProofRewritePlanGuardFamily.controlFlowBoundary"));
        assertTrue(fields.get("autoVectorizationProofRewritePlanSummary").contains("kind=rewritePlan"));
        assertEquals("rewritePlan", defaultFields.get("autoVectorizationProofKind"));
        assertEquals("kernel", defaultFields.get("autoVectorizationProofLocation"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
        assertThrows(UnsupportedOperationException.class, () -> defaultFields.put("x", "y"));
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
        Map<String, String> customFields = bundle.artifactFields("proofBundle");

        assertFalse(bundle.rewriteSafe());
        assertTrue(bundle.hasDiagnostics());
        assertEquals(List.of(rewritePlan, controlFlow), bundle.unsafeProofSummaries());
        assertEquals(rewritePlan, bundle.firstUnsafeProofSummary().orElseThrow());
        assertEquals(Map.of(
                "rewritePlan", 1L,
                "controlFlowBoundary", 1L
        ), bundle.unsafeProofKindCounts());
        assertEquals(GpuIrAutoVectorizationProofDecisionStatus.BLOCKED_BY_MULTIPLE_PROOFS, bundle.decision().status());
        assertTrue(bundle.decision().blocksRewrite());
        assertEquals(List.of("rewritePlan", "controlFlowBoundary"), bundle.decision().blockingProofKinds());
        assertEquals(rewritePlan, bundle.decision().firstBlockingProof().orElseThrow());
        assertEquals(1, bundle.warningCount());
        assertEquals(2, bundle.guardDiagnosticCount());
        assertEquals(3, bundle.diagnosticCount());
        assertEquals(List.of("rewritePlan", "controlFlowBoundary", "memoryLegality"), bundle.proofKinds());
        assertEquals(Map.of(
                "rewritePlan", 1L,
                "controlFlowBoundary", 1L,
                "memoryLegality", 1L
        ), bundle.proofKindCounts());
        assertEquals(Map.of(
                GpuIrAutoVectorizationRewriteGuardFamily.MEMORY_ADDRESS_SPACE, 1L,
                GpuIrAutoVectorizationRewriteGuardFamily.CONTROL_FLOW_BOUNDARY, 1L
        ), bundle.guardFamilyTypeCounts());
        assertEquals("3", fields.get("autoVectorizationProofBundleProofs"));
        assertEquals("rewritePlan,controlFlowBoundary,memoryLegality", fields.get("autoVectorizationProofBundleKinds"));
        assertEquals("{rewritePlan=1,controlFlowBoundary=1,memoryLegality=1}", fields.get("autoVectorizationProofBundleKindCounts"));
        assertEquals("1", fields.get("autoVectorizationProofBundleKind.rewritePlan"));
        assertEquals("1", fields.get("autoVectorizationProofBundleKind.controlFlowBoundary"));
        assertEquals("1", fields.get("autoVectorizationProofBundleKind.memoryLegality"));
        assertEquals("false", fields.get("autoVectorizationProofBundleRewriteSafe"));
        assertEquals("3", fields.get("autoVectorizationProofBundleDiagnostics"));
        assertEquals("2", fields.get("autoVectorizationProofBundleUnsafeProofs"));
        assertEquals("{rewritePlan=1,controlFlowBoundary=1}", fields.get("autoVectorizationProofBundleUnsafeProofKindCounts"));
        assertEquals("blockedByMultipleProofs", fields.get("autoVectorizationProofBundleDecisionStatus"));
        assertEquals("false", fields.get("autoVectorizationProofBundleDecisionAllowRewrite"));
        assertEquals("rewritePlan,controlFlowBoundary", fields.get("autoVectorizationProofBundleDecisionBlockingProofKinds"));
        assertEquals("rewritePlan", fields.get("autoVectorizationProofBundleDecisionFirstBlockingProofKind"));
        assertEquals("kernel", fields.get("autoVectorizationProofBundleDecisionFirstBlockingProofLocation"));
        assertEquals("2", fields.get("autoVectorizationProofBundleDecisionFirstBlockingProofDiagnostics"));
        assertTrue(fields.get("autoVectorizationProofBundleDecisionSummary").contains("status=blockedByMultipleProofs"));
        assertEquals("1", fields.get("autoVectorizationProofBundleUnsafeProofKind.rewritePlan"));
        assertEquals("1", fields.get("autoVectorizationProofBundleUnsafeProofKind.controlFlowBoundary"));
        assertEquals("rewritePlan", fields.get("autoVectorizationProofBundleFirstUnsafeProofKind"));
        assertEquals("kernel", fields.get("autoVectorizationProofBundleFirstUnsafeProofLocation"));
        assertEquals("2", fields.get("autoVectorizationProofBundleFirstUnsafeProofDiagnostics"));
        assertTrue(fields.get("autoVectorizationProofBundleFirstUnsafeProofSummary").contains("kind=rewritePlan"));
        assertEquals("1", fields.get("autoVectorizationProofBundleGuardFamily.memoryAddressSpace"));
        assertEquals("1", fields.get("autoVectorizationProofBundleGuardFamily.controlFlowBoundary"));
        assertEquals("3", customFields.get("proofBundleProofs"));
        assertEquals("rewritePlan,controlFlowBoundary,memoryLegality", customFields.get("proofBundleKinds"));
        assertTrue(bundle.summaryLine().contains("proofs=3"));
        assertTrue(bundle.summaryLine().contains("unsafeProofs=2"));
        assertTrue(bundle.summaryLine().contains("unsafeProofKindCounts={rewritePlan=1, controlFlowBoundary=1}"));
        assertTrue(bundle.summaryLine().contains("decision=blockedByMultipleProofs"));
        assertTrue(bundle.summaryLine().contains("firstUnsafeProof=rewritePlan@kernel"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
        assertThrows(UnsupportedOperationException.class, () -> customFields.put("x", "y"));
    }

    @Test
    void proofBundleDecisionReportsSingleBlockingProofKind() {
        GpuIrAutoVectorizationProofSummary memory = GpuIrAutoVectorizationProofSummary.fromGuards(
                "memoryLegality",
                "stmt[0]",
                1,
                List.of()
        );

        GpuIrAutoVectorizationProofDecision decision = GpuIrAutoVectorizationProofBundle.of(memory).decision();
        Map<String, String> fields = decision.artifactFields();

        assertEquals(GpuIrAutoVectorizationProofDecisionStatus.BLOCKED_BY_MEMORY, decision.status());
        assertFalse(decision.allowRewrite());
        assertEquals(List.of("memoryLegality"), decision.blockingProofKinds());
        assertEquals(memory, decision.firstBlockingProof().orElseThrow());
        assertEquals("blockedByMemory", fields.get("autoVectorizationProofDecisionStatus"));
        assertEquals("false", fields.get("autoVectorizationProofDecisionAllowRewrite"));
        assertEquals("memoryLegality", fields.get("autoVectorizationProofDecisionBlockingProofKinds"));
        assertEquals("memoryLegality", fields.get("autoVectorizationProofDecisionFirstBlockingProofKind"));
        assertTrue(decision.summary().contains("firstBlockingProof=memoryLegality@stmt[0]"));
    }

    @Test
    void previewUsesProofDecisionAsBlockingDiagnosticFallback() {
        GpuIrAutoVectorizationPreview preview = new GpuIrAutoVectorizationPreview(
                "kernel",
                List.of(),
                List.of(),
                List.of(),
                List.of(GpuIrAutoVectorizationProofSummary.fromGuards(
                        "memoryLegality",
                        "stmt[0]",
                        1,
                        List.of()
                ))
        );

        assertTrue(preview.hasBlockingDiagnostics());
        assertEquals("proofDecision.blockedByMemory", preview.firstBlockingDiagnosticFamily().orElseThrow());
        assertTrue(preview.firstBlockingDiagnosticSummary().orElseThrow().contains("proof decision blockedByMemory"));
        assertTrue(preview.firstBlockingDiagnosticSummary().orElseThrow().contains("memoryLegality@stmt[0]"));
    }

    @Test
    void proofBundleCompactKindsCollapseRepeatedProofSurfaces() {
        GpuIrAutoVectorizationProofSummary rewritePlan = GpuIrAutoVectorizationProofSummary.fromGuards(
                "rewritePlan",
                "kernel",
                0,
                List.of()
        );
        GpuIrAutoVectorizationProofSummary firstControlFlow = GpuIrAutoVectorizationProofSummary.fromGuards(
                "controlFlowBoundary",
                "stmt[0]",
                0,
                List.of()
        );
        GpuIrAutoVectorizationProofSummary secondControlFlow = GpuIrAutoVectorizationProofSummary.fromGuards(
                "controlFlowBoundary",
                "stmt[1]",
                0,
                List.of()
        );
        GpuIrAutoVectorizationProofSummary memory = GpuIrAutoVectorizationProofSummary.fromGuards(
                "memoryLegality",
                "stmt[0]",
                0,
                List.of()
        );

        GpuIrAutoVectorizationProofBundle bundle = GpuIrAutoVectorizationProofBundle.of(
                rewritePlan,
                firstControlFlow,
                secondControlFlow,
                memory
        );
        Map<String, String> fields = bundle.artifactFields();

        assertEquals(List.of("rewritePlan", "controlFlowBoundary", "controlFlowBoundary", "memoryLegality"), bundle.proofKinds());
        assertEquals(List.of("rewritePlan", "controlFlowBoundary", "memoryLegality"), bundle.compactProofKinds());
        assertEquals(Map.of(
                "rewritePlan", 1L,
                "controlFlowBoundary", 2L,
                "memoryLegality", 1L
        ), bundle.proofKindCounts());
        assertEquals("rewritePlan,controlFlowBoundary,memoryLegality", fields.get("autoVectorizationProofBundleKinds"));
        assertEquals("{rewritePlan=1,controlFlowBoundary=2,memoryLegality=1}", fields.get("autoVectorizationProofBundleKindCounts"));
        assertEquals("2", fields.get("autoVectorizationProofBundleKind.controlFlowBoundary"));
        assertTrue(bundle.unsafeProofSummaries().isEmpty());
        assertTrue(bundle.firstUnsafeProofSummary().isEmpty());
        assertEquals(GpuIrAutoVectorizationProofDecisionStatus.ALLOW, bundle.decision().status());
        assertTrue(bundle.decision().allowRewrite());
        assertEquals(List.of(), bundle.decision().blockingProofKinds());
        assertEquals(Map.of(), bundle.unsafeProofKindCounts());
        assertEquals("0", fields.get("autoVectorizationProofBundleUnsafeProofs"));
        assertEquals("{}", fields.get("autoVectorizationProofBundleUnsafeProofKindCounts"));
        assertEquals("allow", fields.get("autoVectorizationProofBundleDecisionStatus"));
        assertEquals("true", fields.get("autoVectorizationProofBundleDecisionAllowRewrite"));
        assertEquals("", fields.get("autoVectorizationProofBundleDecisionBlockingProofKinds"));
        assertTrue(bundle.summaryLine().contains("kinds=[rewritePlan,controlFlowBoundary,memoryLegality]"));
        assertTrue(bundle.summaryLine().contains("kindCounts={rewritePlan=1, controlFlowBoundary=2, memoryLegality=1}"));
        assertTrue(bundle.summaryLine().contains("unsafeProofs=0"));
        assertTrue(bundle.summaryLine().contains("unsafeProofKindCounts={}"));
        assertTrue(bundle.summaryLine().contains("decision=allow"));
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
