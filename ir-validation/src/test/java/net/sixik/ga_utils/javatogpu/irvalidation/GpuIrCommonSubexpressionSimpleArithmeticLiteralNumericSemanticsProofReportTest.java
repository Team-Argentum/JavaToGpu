package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReportTest {
    @Test
    void provesIntLiteralPlusAndTimesPreviewCandidates() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport report =
                new GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport(
                        "kernel",
                        List.of(
                                candidate("stmt[0].initializer", "+", "plus:int,int,int", "literal_assoc_preview(plus:int,int,int;literals=1)", List.of("int", "int", "int"), "1"),
                                candidate("stmt[1].initializer", "*", "times:int,int,int", "literal_assoc_preview(times:int,int,int;literals=2)", List.of("int", "int", "int"), "2")
                        )
                );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport proof =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.from(report);
        Map<String, String> fields = proof.artifactFields("numericProof");

        assertEquals(2, proof.candidateCount());
        assertEquals(2, proof.provenCandidateCount());
        assertEquals(0, proof.blockedCandidateCount());
        assertTrue(proof.fullyProven());
        assertEquals("proven", proof.readiness());
        assertEquals("javaIntLiteralPlusTimesSemantics", proof.proofBoundary());
        assertEquals("nonIntOrUnsupportedLiteralOperator", proof.blockedBoundary());
        assertEquals(Map.of("plus:int,int,int", 1L, "times:int,int,int", 1L), proof.provenOperatorTypeCounts());
        assertEquals("2", fields.get("numericProofCandidates"));
        assertEquals("2", fields.get("numericProofProvenCandidates"));
        assertEquals("0", fields.get("numericProofBlockedCandidates"));
        assertEquals("true", fields.get("numericProofFullyProven"));
        assertEquals("proven", fields.get("numericProofReadiness"));
        assertEquals("stmt[0].initializer", fields.get("numericProofFirstProvenLocation"));
        assertEquals("+", fields.get("numericProofFirstProvenOperator"));
        assertEquals("plus:int,int,int", fields.get("numericProofFirstProvenOperatorTypeKey"));
        assertEquals("literal_assoc_preview(plus:int,int,int;literals=1)", fields.get("numericProofFirstProvenCanonicalKey"));
        assertEquals("1", fields.get("numericProofFirstProvenLiteralSources"));
        assertTrue(proof.summary().contains("fullyProven=true"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void blocksUnsupportedOperatorsAndNonIntOperands() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport report =
                new GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport(
                        "kernel",
                        List.of(
                                candidate("stmt[0].initializer", "-", "minus:int,int,int", "literal_assoc_preview(minus:int,int,int;literals=1)", List.of("int", "int", "int"), "1"),
                                candidate("stmt[1].initializer", "+", "plus:long,long,long", "literal_assoc_preview(plus:long,long,long;literals=1L)", List.of("long", "long", "long"), "1L")
                        )
                );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport proof =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.from(report);
        Map<String, String> fields = proof.artifactFields("numericProof");

        assertEquals(2, proof.candidateCount());
        assertEquals(0, proof.provenCandidateCount());
        assertEquals(2, proof.blockedCandidateCount());
        assertFalse(proof.fullyProven());
        assertEquals("blocked", proof.readiness());
        assertEquals(Map.of("unsupportedOperator", 1L, "nonIntOperand", 1L), proof.blockedReasonCounts());
        assertEquals("2", fields.get("numericProofBlockedCandidates"));
        assertEquals("false", fields.get("numericProofFullyProven"));
        assertEquals("blocked", fields.get("numericProofReadiness"));
        assertEquals("unsupportedOperator", fields.get("numericProofFirstBlockedReason"));
        assertEquals("minus:int,int,int", fields.get("numericProofFirstBlockedOperatorTypeKey"));
        assertEquals("literal_assoc_preview(minus:int,int,int;literals=1)", fields.get("numericProofFirstBlockedCanonicalKey"));
        assertTrue(fields.get("numericProofBlockedReasonCounts").contains("unsupportedOperator=1"));
        assertTrue(fields.get("numericProofBlockedReasonCounts").contains("nonIntOperand=1"));
    }

    @Test
    void reportsEmptyProofWhenNoPreviewCandidatesExist() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport proof =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.empty("kernel");
        Map<String, String> fields = proof.artifactFields("numericProof");

        assertEquals(0, proof.candidateCount());
        assertEquals(0, proof.provenCandidateCount());
        assertEquals(0, proof.blockedCandidateCount());
        assertFalse(proof.fullyProven());
        assertEquals("none", proof.readiness());
        assertEquals("0", fields.get("numericProofCandidates"));
        assertEquals("0", fields.get("numericProofProvenCandidates"));
        assertEquals("0", fields.get("numericProofBlockedCandidates"));
        assertEquals("false", fields.get("numericProofFullyProven"));
        assertEquals("none", fields.get("numericProofReadiness"));
        assertEquals("{}", fields.get("numericProofProvenOperatorTypeCounts"));
        assertEquals("{}", fields.get("numericProofBlockedReasonCounts"));
    }

    private GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.Candidate candidate(
            String location,
            String operator,
            String operatorTypeKey,
            String canonicalKey,
            List<String> operandTypes,
            String literalSource
    ) {
        return new GpuIrCommonSubexpressionSimpleArithmeticLiteralCanonicalizationReport.Candidate(
                location,
                operator,
                operatorTypeKey,
                canonicalKey,
                operandTypes,
                List.of(literalSource)
        );
    }
}
