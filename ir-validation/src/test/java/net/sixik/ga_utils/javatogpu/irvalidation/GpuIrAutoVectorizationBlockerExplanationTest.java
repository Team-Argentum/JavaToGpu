package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationBlockerExplanationTest {
    @Test
    void explainsNoCandidateReadinessWithActionableWork() {
        GpuIrAutoVectorizationBlockerExplanation explanation =
                GpuIrAutoVectorizationBlockerExplanation.from(noCandidateReadiness());

        Map<String, String> fields = explanation.artifactFields();

        assertTrue(explanation.blocked());
        assertEquals("blocked", explanation.verdict());
        assertEquals("candidateDiscovery.noRewriteCandidates", explanation.firstBlockerFamily());
        assertEquals("collectRewriteCandidates", explanation.firstRemainingWork());
        assertEquals("kernel", fields.get("autoVectorizationBlockerMethod"));
        assertEquals("blocked", fields.get("autoVectorizationBlockerVerdict"));
        assertEquals("notReady/noCandidates", fields.get("autoVectorizationBlockerReadinessVerdict"));
        assertEquals("noRewriteCandidates", fields.get("autoVectorizationBlockerFirstReason"));
        assertEquals("candidateDiscovery.noRewriteCandidates", fields.get("autoVectorizationBlockerFirstFamily"));
        assertEquals("collectRewriteCandidates", fields.get("autoVectorizationBlockerFirstRemainingWork"));
        assertTrue(fields.get("autoVectorizationBlockerFirstHint").contains("fixed-width rewrite candidate"));
        assertTrue(fields.get("autoVectorizationBlockerSummary").contains("candidateDiscovery.noRewriteCandidates"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void mapsRewritePolicyAndDryRunBlockersToStableFamilies() {
        GpuIrAutoVectorizationBlockerExplanation explanation = new GpuIrAutoVectorizationBlockerExplanation(
                "kernel",
                "notReady/rewritePolicyBlocked",
                false,
                List.of("rewritePolicyBlocksRewrite", "dryRunNotReady"),
                List.of("enableRewritePolicy", "fixRewriteDryRun"),
                "ready",
                "blockedByGuard",
                "failed"
        );

        assertEquals(List.of("rewritePolicy.blocksRewrite", "dryRun.notReady"), explanation.blockerFamilies());
        assertEquals("rewritePolicy.blocksRewrite", explanation.firstBlockerFamily());
        assertEquals("enableRewritePolicy", explanation.firstRemainingWork());
        assertTrue(explanation.summary().contains("keep production mutation disabled"));
    }

    @Test
    void reportsReadyWithoutFakeBlockers() {
        GpuIrAutoVectorizationBlockerExplanation explanation = GpuIrAutoVectorizationBlockerExplanation.from(readyReadiness());
        Map<String, String> fields = explanation.artifactFields();

        assertFalse(explanation.blocked());
        assertEquals("ready", explanation.verdict());
        assertEquals("none", explanation.firstBlockerFamily());
        assertEquals("none", explanation.firstRemainingWork());
        assertEquals("", fields.get("autoVectorizationBlockerFamilies"));
        assertEquals("auto-vectorization is ready for prototype rewrite review", fields.get("autoVectorizationBlockerFirstHint"));
    }

    @Test
    void rejectsInvalidMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationBlockerExplanation(
                "",
                "notReady/noCandidates",
                false,
                List.of(),
                List.of(),
                "none",
                "none",
                "skipped"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationBlockerExplanation(
                "kernel",
                "",
                false,
                List.of(),
                List.of(),
                "none",
                "none",
                "skipped"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrAutoVectorizationBlockerExplanation(
                "kernel",
                "notReady/noCandidates",
                false,
                List.of(""),
                List.of(),
                "none",
                "none",
                "skipped"
        ));
        assertThrows(IllegalArgumentException.class, () -> GpuIrAutoVectorizationBlockerExplanation
                .from(noCandidateReadiness())
                .artifactFields(""));
    }

    private GpuIrAutoVectorizationReadinessSummaryReport noCandidateReadiness() {
        GpuIrAutoVectorizationPreview preview = new GpuIrAutoVectorizationPreview(
                "kernel",
                List.of(),
                List.of(),
                List.of()
        );
        return GpuIrAutoVectorizationReadinessSummaryReport.from(
                preview,
                GpuIrAutoVectorizationRewriteDryRunReport.skipped(
                        "kernel",
                        0,
                        0,
                        0,
                        List.of("Auto-vectorization rewrite dry-run skipped: no rewrite candidates")
                ),
                GpuIrAutoVectorizationResolvedRewriteOperations.empty("kernel")
        );
    }

    private GpuIrAutoVectorizationReadinessSummaryReport readyReadiness() {
        GpuIrAutoVectorizationPreview preview = new GpuIrAutoVectorizationPreview(
                "kernel",
                List.of(new GpuIrAutoVectorizationRewriteCandidatePreview(
                        "stmt[0]",
                        "i",
                        0,
                        4,
                        4,
                        1,
                        1,
                        "x4",
                        "int",
                        "int4",
                        List.of("write out[i=0..3]"),
                        List.of("read input[i=0..3]"),
                        List.of(),
                        List.of("out"),
                        List.of("input")
                )),
                List.of(),
                List.of()
        );
        return GpuIrAutoVectorizationReadinessSummaryReport.from(
                preview,
                GpuIrAutoVectorizationRewriteDryRunReport.ready("kernel", 1, 1, 1),
                new GpuIrAutoVectorizationResolvedRewriteOperations(
                        "kernel",
                        List.of(new GpuIrAutoVectorizationResolvedInsertionOperation(
                                "stmt[0]",
                                0,
                                "int4",
                                0,
                                4,
                                1,
                                1,
                                List.of("read input[i=0..3]")
                        )),
                        List.of(new GpuIrAutoVectorizationResolvedReplacementOperation(
                                "stmt[0]",
                                0,
                                "i",
                                0,
                                4,
                                1,
                                1,
                                List.of("write out[i=0..3]")
                        ))
                )
        );
    }
}
