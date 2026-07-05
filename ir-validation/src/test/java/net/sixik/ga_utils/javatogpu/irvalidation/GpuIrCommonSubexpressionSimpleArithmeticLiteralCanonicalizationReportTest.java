package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReportTest {
    @Test
    void previewsCanonicalKeysForSafeIntegerLiteralCandidates() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport proofReport = new GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport(
                "kernel",
                List.of(
                        new GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.SafeCandidate(
                                "stmt[0].initializer",
                                "+",
                                List.of("int", "int", "int"),
                                List.of("1")
                        ),
                        new GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.SafeCandidate(
                                "stmt[1].initializer",
                                "*",
                                List.of("int", "int", "int"),
                                List.of("2")
                        )
                ),
                List.of()
        );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.from(proofReport);
        Map<String, String> fields = report.artifactFields("literalCanonicalization");

        assertEquals(2, report.candidateCount());
        assertTrue(report.hasCandidates());
        assertEquals("preview", report.readiness());
        assertEquals("safeIntLiteralCanonicalKeyPreview", report.proofBoundary());
        assertEquals("previewOnlyNoFingerprintRewrite", report.blockedBoundary());
        assertEquals(2, report.uniqueCanonicalKeyCount());
        assertEquals(Map.of("plus:int,int,int", 1L, "times:int,int,int", 1L), report.operatorTypeCounts());
        assertEquals("2", fields.get("literalCanonicalizationCandidates"));
        assertEquals("true", fields.get("literalCanonicalizationHasCandidates"));
        assertEquals("preview", fields.get("literalCanonicalizationReadiness"));
        assertEquals("2", fields.get("literalCanonicalizationUniqueCanonicalKeys"));
        assertEquals("{plus:int,int,int=1,times:int,int,int=1}", fields.get("literalCanonicalizationOperatorTypeCounts"));
        assertTrue(fields.get("literalCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(plus:int,int,int;literals=1)=1"));
        assertTrue(fields.get("literalCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(times:int,int,int;literals=2)=1"));
        assertEquals("stmt[0].initializer", fields.get("literalCanonicalizationFirstLocation"));
        assertEquals("+", fields.get("literalCanonicalizationFirstOperator"));
        assertEquals("plus:int,int,int", fields.get("literalCanonicalizationFirstOperatorTypeKey"));
        assertEquals("literal_assoc_preview(plus:int,int,int;literals=1)", fields.get("literalCanonicalizationFirstCanonicalKey"));
        assertEquals("1", fields.get("literalCanonicalizationFirstLiteralSources"));
        assertTrue(report.summary().contains("canonicalKeys={literal_assoc_preview(plus:int,int,int;literals=1)=1"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void groupsIdenticalPreviewCanonicalKeys() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport proofReport = new GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport(
                "kernel",
                List.of(
                        safeCandidate("stmt[0].initializer", "+", "1"),
                        safeCandidate("stmt[1].initializer", "+", "1")
                ),
                List.of()
        );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.from(proofReport);
        Map<String, String> fields = report.artifactFields("literalCanonicalization");

        assertEquals(2, report.candidateCount());
        assertEquals(1, report.uniqueCanonicalKeyCount());
        assertEquals(Map.of("plus:int,int,int", 2L), report.operatorTypeCounts());
        assertEquals(Map.of("literal_assoc_preview(plus:int,int,int;literals=1)", 2L), report.canonicalKeyCounts());
        assertEquals("{plus:int,int,int=2}", fields.get("literalCanonicalizationOperatorTypeCounts"));
        assertEquals("{literal_assoc_preview(plus:int,int,int;literals=1)=2}", fields.get("literalCanonicalizationCanonicalKeyCounts"));
    }

    @Test
    void keepsDifferentLiteralSetsSeparateForSameOperatorType() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport proofReport = new GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport(
                "kernel",
                List.of(
                        safeCandidate("stmt[0].initializer", "+", "1"),
                        safeCandidate("stmt[1].initializer", "+", "2")
                ),
                List.of()
        );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.from(proofReport);
        Map<String, String> fields = report.artifactFields("literalCanonicalization");

        assertEquals(Map.of("plus:int,int,int", 2L), report.operatorTypeCounts());
        assertEquals(2, report.uniqueCanonicalKeyCount());
        assertEquals(2, report.canonicalKeyCounts().size());
        assertEquals(1L, report.canonicalKeyCounts().get("literal_assoc_preview(plus:int,int,int;literals=1)"));
        assertEquals(1L, report.canonicalKeyCounts().get("literal_assoc_preview(plus:int,int,int;literals=2)"));
        assertTrue(fields.get("literalCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(plus:int,int,int;literals=1)=1"));
        assertTrue(fields.get("literalCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(plus:int,int,int;literals=2)=1"));
    }

    @Test
    void keepsDifferentOperatorsSeparateForSameLiteralSet() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport proofReport = new GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport(
                "kernel",
                List.of(
                        safeCandidate("stmt[0].initializer", "+", "2"),
                        safeCandidate("stmt[1].initializer", "*", "2")
                ),
                List.of()
        );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.from(proofReport);
        Map<String, String> fields = report.artifactFields("literalCanonicalization");

        assertEquals(Map.of("plus:int,int,int", 1L, "times:int,int,int", 1L), report.operatorTypeCounts());
        assertEquals(2, report.canonicalKeyCounts().size());
        assertEquals(1L, report.canonicalKeyCounts().get("literal_assoc_preview(plus:int,int,int;literals=2)"));
        assertEquals(1L, report.canonicalKeyCounts().get("literal_assoc_preview(times:int,int,int;literals=2)"));
        assertTrue(fields.get("literalCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(plus:int,int,int;literals=2)=1"));
        assertTrue(fields.get("literalCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(times:int,int,int;literals=2)=1"));
    }

    @Test
    void preservesLiteralSpellingsInPreviewCanonicalKeys() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport proofReport = new GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport(
                "kernel",
                List.of(
                        safeCandidate("stmt[0].initializer", "+", "1_000"),
                        safeCandidate("stmt[1].initializer", "+", "0x10"),
                        safeCandidate("stmt[2].initializer", "+", "-1")
                ),
                List.of()
        );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.from(proofReport);
        Map<String, String> fields = report.artifactFields("literalCanonicalization");

        assertEquals(Map.of("plus:int,int,int", 3L), report.operatorTypeCounts());
        assertEquals(3, report.uniqueCanonicalKeyCount());
        assertEquals(3, report.canonicalKeyCounts().size());
        assertEquals(1L, report.canonicalKeyCounts().get("literal_assoc_preview(plus:int,int,int;literals=1_000)"));
        assertEquals(1L, report.canonicalKeyCounts().get("literal_assoc_preview(plus:int,int,int;literals=0x10)"));
        assertEquals(1L, report.canonicalKeyCounts().get("literal_assoc_preview(plus:int,int,int;literals=-1)"));
        assertTrue(fields.get("literalCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(plus:int,int,int;literals=1_000)=1"));
        assertTrue(fields.get("literalCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(plus:int,int,int;literals=0x10)=1"));
        assertTrue(fields.get("literalCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(plus:int,int,int;literals=-1)=1"));
    }

    @Test
    void escapesAmbiguousLiteralSeparatorsInPreviewCanonicalKeys() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport proofReport = new GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport(
                "kernel",
                List.of(
                        safeCandidate("stmt[0].initializer", "+", "1,2"),
                        safeCandidate("stmt[1].initializer", "+", "3;4"),
                        safeCandidate("stmt[2].initializer", "+", "5)6"),
                        safeCandidate("stmt[3].initializer", "+", "7\\8")
                ),
                List.of()
        );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.from(proofReport);
        Map<String, String> fields = report.artifactFields("literalCanonicalization");

        assertEquals(4, report.canonicalKeyCounts().size());
        assertEquals(4, report.uniqueCanonicalKeyCount());
        assertEquals(1L, report.canonicalKeyCounts().get("literal_assoc_preview(plus:int,int,int;literals=1\\,2)"));
        assertEquals(1L, report.canonicalKeyCounts().get("literal_assoc_preview(plus:int,int,int;literals=3\\;4)"));
        assertEquals(1L, report.canonicalKeyCounts().get("literal_assoc_preview(plus:int,int,int;literals=5\\)6)"));
        assertEquals(1L, report.canonicalKeyCounts().get("literal_assoc_preview(plus:int,int,int;literals=7\\\\8)"));
        assertTrue(fields.get("literalCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(plus:int,int,int;literals=1\\,2)=1"));
        assertTrue(fields.get("literalCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(plus:int,int,int;literals=3\\;4)=1"));
        assertTrue(fields.get("literalCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(plus:int,int,int;literals=5\\)6)=1"));
        assertTrue(fields.get("literalCanonicalizationCanonicalKeyCounts").contains("literal_assoc_preview(plus:int,int,int;literals=7\\\\8)=1"));
    }

    @Test
    void reportsEmptyPreviewWhenNoSafeCandidatesExist() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.empty("kernel");
        Map<String, String> fields = report.artifactFields("literalCanonicalization");

        assertEquals(0, report.candidateCount());
        assertFalse(report.hasCandidates());
        assertEquals("none", report.readiness());
        assertEquals("0", fields.get("literalCanonicalizationCandidates"));
        assertEquals("false", fields.get("literalCanonicalizationHasCandidates"));
        assertEquals("{}", fields.get("literalCanonicalizationOperatorTypeCounts"));
        assertEquals("{}", fields.get("literalCanonicalizationCanonicalKeyCounts"));
        assertEquals("0", fields.get("literalCanonicalizationUniqueCanonicalKeys"));
    }

    private GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.SafeCandidate safeCandidate(
            String location,
            String operator,
            String literalSource
    ) {
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.SafeCandidate(
                location,
                operator,
                List.of("int", "int", "int"),
                List.of(literalSource)
        );
    }
}
