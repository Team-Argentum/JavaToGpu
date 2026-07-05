package net.sixik.ga_utils.javatogpu.irvalidation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReportTest {
    @Test
    void reportsClearWhenNoTypedNumericBlockersExist() {
        GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport report =
                GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport.from(
                        GpuIrCommonSubexpressionSimpleArithmeticLiteralProofReport.empty("kernel"),
                        GpuIrCommonSubexpressionSimpleArithmeticLiteralNumericSemanticsProofReport.empty("kernel")
                );
        Map<String, String> fields = report.artifactFields("typedBlockers");

        assertFalse(report.hasBlockers());
        assertEquals("clear", report.readiness());
        assertEquals(0, report.totalBlockedCandidateCount());
        assertEquals(0, report.uniqueBlockerFamilyCount());
        assertEquals("literal typed numeric blockers clear", report.ciSummaryLine());
        assertEquals("clear", fields.get("typedBlockersReadiness"));
        assertEquals("false", fields.get("typedBlockersHasBlockers"));
        assertEquals("0", fields.get("typedBlockersTotalBlockedCandidates"));
        assertEquals("{}", fields.get("typedBlockersCombinedBlockerCounts"));
        assertEquals("literal typed numeric blockers clear", fields.get("typedBlockersCiSummaryLine"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void combinesLiteralProofAndNumericSemanticsBlockerFamilies() {
        Map<String, Long> literalProofBlockers = new LinkedHashMap<>();
        literalProofBlockers.put("longLiteralOverflowSemanticsRequireProof", 1L);
        literalProofBlockers.put("floatingLiteralSemanticsRequireProof", 2L);
        Map<String, Long> numericSemanticsBlockers = new LinkedHashMap<>();
        numericSemanticsBlockers.put("longOperandOverflowSemanticsRequireProof", 1L);
        numericSemanticsBlockers.put("floatingOperandSemanticsRequireProof", 1L);

        GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport report =
                new GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport(
                        "kernel",
                        3,
                        2,
                        literalProofBlockers,
                        numericSemanticsBlockers
                );
        Map<String, String> fields = report.artifactFields("typedBlockers");

        assertTrue(report.hasBlockers());
        assertEquals("blocked", report.readiness());
        assertEquals(5, report.totalBlockedCandidateCount());
        assertEquals(4, report.uniqueBlockerFamilyCount());
        assertEquals("longLiteralOverflowSemanticsRequireProof", report.firstBlockerFamily().orElseThrow());
        assertEquals("long arithmetic requires explicit overflow and backend-equivalence proof", report.firstBlockerExplanation().orElseThrow());
        assertEquals("blocked", fields.get("typedBlockersReadiness"));
        assertEquals("true", fields.get("typedBlockersHasBlockers"));
        assertEquals("5", fields.get("typedBlockersTotalBlockedCandidates"));
        assertEquals("3", fields.get("typedBlockersLiteralProofBlockedCandidates"));
        assertEquals("2", fields.get("typedBlockersNumericSemanticsBlockedCandidates"));
        assertEquals("4", fields.get("typedBlockersUniqueBlockerFamilies"));
        assertEquals("1", fields.get("typedBlockersFamily.longLiteralOverflowSemanticsRequireProof"));
        assertEquals("2", fields.get("typedBlockersFamily.floatingLiteralSemanticsRequireProof"));
        assertEquals("1", fields.get("typedBlockersFamily.longOperandOverflowSemanticsRequireProof"));
        assertEquals("1", fields.get("typedBlockersFamily.floatingOperandSemanticsRequireProof"));
        assertEquals("longLiteralOverflowSemanticsRequireProof", fields.get("typedBlockersFirstBlockerFamily"));
        assertEquals("long arithmetic requires explicit overflow and backend-equivalence proof", fields.get("typedBlockersFirstBlockerExplanation"));
        assertTrue(fields.get("typedBlockersCiSummaryLine").contains("literal typed numeric blockers=5"));
    }

    @Test
    void rejectsInvalidMetadata() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport(
                "",
                0,
                0,
                Map.of(),
                Map.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport(
                "kernel",
                -1,
                0,
                Map.of(),
                Map.of()
        ));
        assertThrows(NullPointerException.class, () -> new GpuIrCommonSubexpressionSimpleArithmeticLiteralTypedNumericBlockerSummaryReport(
                "kernel",
                0,
                0,
                null,
                Map.of()
        ));
    }
}
