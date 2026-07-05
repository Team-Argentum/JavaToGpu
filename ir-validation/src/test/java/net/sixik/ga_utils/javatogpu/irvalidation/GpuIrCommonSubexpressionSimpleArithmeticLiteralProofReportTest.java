package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReportTest {
    @Test
    void reportsSafeIntegerLiteralCandidatesWithoutEnablingRewrite() {
        GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport boundaryReport = new GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport(
                "kernel",
                List.of(new GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport.BlockedCandidate(
                        "stmt[0].initializer",
                        "+",
                        "literalOperand",
                        List.of("int", "int", "int"),
                        List.of("1"),
                        List.of(),
                        List.of()
                ))
        );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.from(boundaryReport);
        Map<String, String> fields = report.artifactFields("literalProof");

        assertEquals(1, report.candidateCount());
        assertEquals(1, report.safeCandidateCount());
        assertEquals(0, report.blockedCandidateCount());
        assertTrue(report.hasSafeCandidates());
        assertEquals("safeIntLiteralNestedArithmetic", report.proofBoundary());
        assertEquals("nonIntOrCastLiteralArithmetic", report.blockedBoundary());
        assertEquals("1", fields.get("literalProofCandidates"));
        assertEquals("1", fields.get("literalProofSafeCandidates"));
        assertEquals("0", fields.get("literalProofBlockedCandidates"));
        assertEquals("true", fields.get("literalProofHasSafeCandidates"));
        assertEquals("stmt[0].initializer", fields.get("literalProofFirstSafeLocation"));
        assertEquals("+", fields.get("literalProofFirstSafeOperator"));
        assertEquals("plus:int,int,int", fields.get("literalProofFirstSafeOperatorTypeKey"));
        assertEquals("int,int,int", fields.get("literalProofFirstSafeOperandTypes"));
        assertEquals("1", fields.get("literalProofFirstSafeLiteralSources"));
        assertEquals("{}", fields.get("literalProofBlockedReasonCounts"));
        assertEquals("{plus:int,int,int=1}", fields.get("literalProofSafeOperatorTypeCounts"));
        assertEquals("{}", fields.get("literalProofBlockedOperatorTypeCounts"));
        assertTrue(report.summary().contains("safeCandidates=1"));
        assertTrue(report.summary().contains("safeOperatorTypes={plus:int,int,int=1}"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void reportsSafeIntegerLiteralMultiplicationCandidatesWithoutEnablingRewrite() {
        GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport boundaryReport = new GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport(
                "kernel",
                List.of(new GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport.BlockedCandidate(
                        "stmt[0].initializer",
                        "*",
                        "literalOperand",
                        List.of("int", "int", "int"),
                        List.of("2"),
                        List.of(),
                        List.of()
                ))
        );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.from(boundaryReport);
        Map<String, String> fields = report.artifactFields("literalProof");

        assertEquals(1, report.candidateCount());
        assertEquals(1, report.safeCandidateCount());
        assertEquals(0, report.blockedCandidateCount());
        assertTrue(report.hasSafeCandidates());
        assertEquals("*", fields.get("literalProofFirstSafeOperator"));
        assertEquals("times:int,int,int", fields.get("literalProofFirstSafeOperatorTypeKey"));
        assertEquals("int,int,int", fields.get("literalProofFirstSafeOperandTypes"));
        assertEquals("2", fields.get("literalProofFirstSafeLiteralSources"));
        assertEquals("{}", fields.get("literalProofBlockedReasonCounts"));
        assertEquals("{times:int,int,int=1}", fields.get("literalProofSafeOperatorTypeCounts"));
        assertEquals("{}", fields.get("literalProofBlockedOperatorTypeCounts"));
    }

    @Test
    void blocksNonIntLiteralCandidates() {
        GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport boundaryReport = new GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport(
                "kernel",
                List.of(new GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport.BlockedCandidate(
                        "stmt[0].initializer",
                        "+",
                        "literalOperand",
                        List.of("float", "float", "float"),
                        List.of("1.0f"),
                        List.of(),
                        List.of()
                ))
        );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.from(boundaryReport);
        Map<String, String> fields = report.artifactFields("literalProof");

        assertEquals(1, report.candidateCount());
        assertEquals(0, report.safeCandidateCount());
        assertEquals(1, report.blockedCandidateCount());
        assertFalse(report.hasSafeCandidates());
        assertEquals(Map.of("nonIntLiteral", 1L), report.blockedReasonCounts());
        assertEquals("nonIntLiteral", fields.get("literalProofFirstBlockedReason"));
        assertEquals("plus:float,float,float", fields.get("literalProofFirstBlockedOperatorTypeKey"));
        assertEquals("float,float,float", fields.get("literalProofFirstBlockedOperandTypes"));
        assertEquals("1.0f", fields.get("literalProofFirstBlockedLiteralSources"));
        assertEquals("1", fields.get("literalProofBlockedReason.nonIntLiteral"));
        assertEquals("{}", fields.get("literalProofSafeOperatorTypeCounts"));
        assertEquals("{plus:float,float,float=1}", fields.get("literalProofBlockedOperatorTypeCounts"));
    }

    @Test
    void blocksLongLiteralCandidates() {
        GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport boundaryReport = new GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport(
                "kernel",
                List.of(new GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport.BlockedCandidate(
                        "stmt[0].initializer",
                        "+",
                        "literalOperand",
                        List.of("long", "long", "long"),
                        List.of("1L"),
                        List.of(),
                        List.of()
                ))
        );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.from(boundaryReport);
        Map<String, String> fields = report.artifactFields("literalProof");

        assertEquals(0, report.safeCandidateCount());
        assertEquals(1, report.blockedCandidateCount());
        assertEquals(Map.of("nonIntLiteral", 1L), report.blockedReasonCounts());
        assertEquals("nonIntLiteral", fields.get("literalProofFirstBlockedReason"));
        assertEquals("plus:long,long,long", fields.get("literalProofFirstBlockedOperatorTypeKey"));
        assertEquals("long,long,long", fields.get("literalProofFirstBlockedOperandTypes"));
        assertEquals("1L", fields.get("literalProofFirstBlockedLiteralSources"));
        assertEquals("{plus:long,long,long=1}", fields.get("literalProofBlockedOperatorTypeCounts"));
    }

    @Test
    void blocksDoubleLiteralCandidates() {
        GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport boundaryReport = new GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport(
                "kernel",
                List.of(new GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport.BlockedCandidate(
                        "stmt[0].initializer",
                        "+",
                        "literalOperand",
                        List.of("double", "double", "double"),
                        List.of("1.0"),
                        List.of(),
                        List.of()
                ))
        );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.from(boundaryReport);
        Map<String, String> fields = report.artifactFields("literalProof");

        assertEquals(0, report.safeCandidateCount());
        assertEquals(1, report.blockedCandidateCount());
        assertEquals(Map.of("nonIntLiteral", 1L), report.blockedReasonCounts());
        assertEquals("nonIntLiteral", fields.get("literalProofFirstBlockedReason"));
        assertEquals("plus:double,double,double", fields.get("literalProofFirstBlockedOperatorTypeKey"));
        assertEquals("double,double,double", fields.get("literalProofFirstBlockedOperandTypes"));
        assertEquals("1.0", fields.get("literalProofFirstBlockedLiteralSources"));
        assertEquals("{plus:double,double,double=1}", fields.get("literalProofBlockedOperatorTypeCounts"));
    }

    @Test
    void keepsCastCandidatesBlocked() {
        GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport boundaryReport = new GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport(
                "kernel",
                List.of(new GpuIrCommonSubexpressionSimpleArithmeticNumericBoundaryReport.BlockedCandidate(
                        "stmt[0].initializer",
                        "+",
                        "castOperand",
                        List.of("int", "int", "int"),
                        List.of(),
                        List.of("int"),
                        List.of("long")
                ))
        );

        GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.from(boundaryReport);
        Map<String, String> fields = report.artifactFields("literalProof");

        assertEquals(0, report.safeCandidateCount());
        assertEquals(1, report.blockedCandidateCount());
        assertEquals(Map.of("castOperand", 1L), report.blockedReasonCounts());
        assertEquals("castOperand", fields.get("literalProofFirstBlockedReason"));
        assertEquals("plus:int,int,int", fields.get("literalProofFirstBlockedOperatorTypeKey"));
        assertEquals("int", fields.get("literalProofFirstBlockedCastTargets"));
        assertEquals("long", fields.get("literalProofFirstBlockedCastSourceTypes"));
        assertEquals("{plus:int,int,int=1}", fields.get("literalProofBlockedOperatorTypeCounts"));
    }
}
