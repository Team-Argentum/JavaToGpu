package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrAutoVectorizationPrototypeRewriteReportTest {
    @Test
    void countsAppliedRewriteFamiliesAcrossMixedPrototypeShapes() {
        GpuIrAutoVectorizationPrototypeRewriteReport report = new GpuIrAutoVectorizationPrototypeRewriteReport(
                new GpuIrMethod("kernel", List.of()),
                List.of(
                        appliedRewrite("stmt[0]", GpuIrAutoVectorizationPrototypeExpressionKind.LANE_COPY, "", ""),
                        appliedRewrite("stmt[1]", GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP, "", "-"),
                        appliedRewrite("stmt[2]", GpuIrAutoVectorizationPrototypeExpressionKind.BINARY_LANE_OP, "+", ""),
                        appliedRewrite("stmt[3]", GpuIrAutoVectorizationPrototypeExpressionKind.BINARY_LANE_OP, "^", ""),
                        appliedRewrite("stmt[4]", GpuIrAutoVectorizationPrototypeExpressionKind.LANE_LITERAL_BINARY_OP, "+", "")
                )
        );

        assertEquals(5, report.appliedRewriteCount());
        assertEquals(1, report.appliedRewriteCount(GpuIrAutoVectorizationPrototypeExpressionKind.LANE_COPY));
        assertEquals(1, report.appliedRewriteCount(GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP));
        assertEquals(2, report.appliedRewriteCount(GpuIrAutoVectorizationPrototypeExpressionKind.BINARY_LANE_OP));
        assertEquals(1, report.appliedRewriteCount(GpuIrAutoVectorizationPrototypeExpressionKind.LANE_LITERAL_BINARY_OP));
        assertEquals(2, report.appliedRewriteCount("binaryLaneOp"));

        Map<String, Integer> artifactCounts = report.appliedRewriteCountsByArtifactValue();
        assertEquals(1, artifactCounts.get("laneCopy"));
        assertEquals(1, artifactCounts.get("unaryLaneOp"));
        assertEquals(2, artifactCounts.get("binaryLaneOp"));
        assertEquals(1, artifactCounts.get("laneLiteralBinaryOp"));

        assertEquals(
                "{laneCopy=1,unaryLaneOp=1,binaryLaneOp=2,laneLiteralBinaryOp=1}",
                report.appliedRewriteFamilyCountersSummary()
        );
        assertTrue(report.summary().contains("appliedRewrites=5"));
        assertTrue(report.summary().contains("appliedRewriteFamilies="));
    }

    @Test
    void countsAbsentFamiliesAsZero() {
        GpuIrAutoVectorizationPrototypeRewriteReport report = new GpuIrAutoVectorizationPrototypeRewriteReport(
                new GpuIrMethod("kernel", List.of()),
                List.of(appliedRewrite("stmt[0]", GpuIrAutoVectorizationPrototypeExpressionKind.LANE_COPY, "", ""))
        );

        assertEquals(0, report.appliedRewriteCount(GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP));
        assertEquals(0, report.appliedRewriteCount("binaryLaneOp"));
        assertEquals(0, report.appliedRewriteCountsByKind().get(GpuIrAutoVectorizationPrototypeExpressionKind.LANE_LITERAL_BINARY_OP));
        assertEquals(0, report.appliedRewriteCountsByArtifactValue().get("laneLiteralBinaryOp"));
    }

    @Test
    void returnsImmutableAppliedRewriteCounterMaps() {
        GpuIrAutoVectorizationPrototypeRewriteReport report = new GpuIrAutoVectorizationPrototypeRewriteReport(
                new GpuIrMethod("kernel", List.of()),
                List.of(appliedRewrite("stmt[0]", GpuIrAutoVectorizationPrototypeExpressionKind.LANE_COPY, "", ""))
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> report.appliedRewriteCountsByKind().put(
                        GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP,
                        99
                )
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> report.appliedRewriteCountsByArtifactValue().put("unaryLaneOp", 99)
        );
    }

    @Test
    void exposesPropertiesFriendlyArtifactFieldsWithStableKeys() {
        GpuIrAutoVectorizationPrototypeRewriteReport report = new GpuIrAutoVectorizationPrototypeRewriteReport(
                new GpuIrMethod("kernel", List.of()),
                List.of(
                        appliedRewrite("stmt[0]", GpuIrAutoVectorizationPrototypeExpressionKind.UNARY_LANE_OP, "", "-"),
                        appliedRewrite("stmt[1]", GpuIrAutoVectorizationPrototypeExpressionKind.BINARY_LANE_OP, "+", "")
                )
        );

        Map<String, String> fields = report.artifactFields();

        assertEquals("2", fields.get("autoVectorizationPrototypeRewriteAppliedRewrites"));
        assertEquals("true", fields.get("autoVectorizationPrototypeRewriteHasAppliedRewrites"));
        assertEquals(
                "{laneCopy=0,unaryLaneOp=1,binaryLaneOp=1,laneLiteralBinaryOp=0}",
                fields.get("autoVectorizationPrototypeRewriteAppliedRewriteFamilies")
        );
        assertEquals("0", fields.get("autoVectorizationPrototypeRewriteAppliedRewriteFamily.laneCopy"));
        assertEquals("1", fields.get("autoVectorizationPrototypeRewriteAppliedRewriteFamily.unaryLaneOp"));
        assertEquals("1", fields.get("autoVectorizationPrototypeRewriteAppliedRewriteFamily.binaryLaneOp"));
        assertEquals("0", fields.get("autoVectorizationPrototypeRewriteAppliedRewriteFamily.laneLiteralBinaryOp"));
        assertEquals("unaryLaneOp", fields.get("autoVectorizationPrototypeRewriteFirstAppliedRewriteExpressionKind"));
        assertEquals("-", fields.get("autoVectorizationPrototypeRewriteFirstAppliedRewriteUnaryOperator"));
        assertTrue(fields.get("autoVectorizationPrototypeRewriteFirstAppliedRewrite").contains("expressionKind=unaryLaneOp"));
    }

    @Test
    void exposesCustomPrefixedArtifactFieldsWithoutFirstRewriteWhenEmpty() {
        GpuIrAutoVectorizationPrototypeRewriteReport report = new GpuIrAutoVectorizationPrototypeRewriteReport(
                new GpuIrMethod("kernel", List.of()),
                List.of()
        );

        Map<String, String> fields = report.artifactFields("prototype.");

        assertEquals("0", fields.get("prototype.AppliedRewrites"));
        assertEquals("false", fields.get("prototype.HasAppliedRewrites"));
        assertEquals("{laneCopy=0,unaryLaneOp=0,binaryLaneOp=0,laneLiteralBinaryOp=0}", fields.get("prototype.AppliedRewriteFamilies"));
        assertEquals("0", fields.get("prototype.AppliedRewriteFamily.unaryLaneOp"));
        assertTrue(!fields.containsKey("prototype.FirstAppliedRewrite"));
    }

    @Test
    void returnsImmutableArtifactFieldMapAndRejectsBlankPrefix() {
        GpuIrAutoVectorizationPrototypeRewriteReport report = new GpuIrAutoVectorizationPrototypeRewriteReport(
                new GpuIrMethod("kernel", List.of()),
                List.of()
        );

        assertThrows(UnsupportedOperationException.class, () -> report.artifactFields().put("x", "y"));
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> report.artifactFields("")
        );

        assertTrue(exception.getMessage().contains("prefix must not be blank"));
    }

    @Test
    void rejectsBlankArtifactValueLookup() {
        GpuIrAutoVectorizationPrototypeRewriteReport report = new GpuIrAutoVectorizationPrototypeRewriteReport(
                new GpuIrMethod("kernel", List.of()),
                List.of()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> report.appliedRewriteCount("")
        );

        assertTrue(exception.getMessage().contains("expressionKindArtifactValue must not be blank"));
    }

    private GpuIrAutoVectorizationPrototypeAppliedRewrite appliedRewrite(
            String loopLocation,
            GpuIrAutoVectorizationPrototypeExpressionKind expressionKind,
            String binaryOperator,
            String unaryOperator
    ) {
        return new GpuIrAutoVectorizationPrototypeAppliedRewrite(
                loopLocation,
                Integer.parseInt(loopLocation.substring("stmt[".length(), loopLocation.length() - 1)),
                "Int4",
                0,
                4,
                List.of("out"),
                List.of("left"),
                expressionKind,
                binaryOperator,
                unaryOperator
        );
    }
}
