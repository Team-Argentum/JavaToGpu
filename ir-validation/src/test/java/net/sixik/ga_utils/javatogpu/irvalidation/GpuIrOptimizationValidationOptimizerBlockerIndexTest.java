package net.sixik.ga_utils.javatogpu.irvalidation;

import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrBinary;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrLiteral;
import net.sixik.ga_utils.javatogpu.frontend.ir.expression.GpuIrVariableRef;
import net.sixik.ga_utils.javatogpu.frontend.ir.model.GpuIrMethod;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrAssignment;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrReturn;
import net.sixik.ga_utils.javatogpu.frontend.ir.statement.GpuIrVariableDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrOptimizationValidationOptimizerBlockerIndexTest {
    @Test
    void safetyErrorWinsOverOptimizerBlockers() {
        GpuIrOptimizationValidationReport report = reportWithSafetyErrorAndCseBlocker();

        GpuIrOptimizationValidationOptimizerBlockerIndex index = report.optimizerBlockerIndex();
        Map<String, String> fields = index.artifactFields();

        assertTrue(index.blocked());
        assertEquals("blocked", index.verdict());
        assertEquals("safety", index.source());
        assertEquals("safety.validationError", index.family());
        assertEquals("fixIrSafetyError", index.remainingWork());
        assertEquals("skipReason.MUTATED_BETWEEN_OCCURRENCES", index.cseFamily());
        assertEquals("candidateDiscovery.noRewriteCandidates", index.autoVectorizationFamily());
        assertEquals("safety", fields.get("optimizerBlockerSource"));
        assertTrue(fields.get("optimizerBlockerHint").contains("safety validation error"));
        assertThrows(UnsupportedOperationException.class, () -> fields.put("x", "y"));
    }

    @Test
    void cseBlockerWinsOverAutoVectorizationBlockerWhenSafetyIsClean() {
        GpuIrOptimizationValidationReport report = cseBlockedReport();

        GpuIrOptimizationValidationOptimizerBlockerIndex index = report.optimizerBlockerIndex();

        assertTrue(index.blocked());
        assertEquals("cseRewritePolicy", index.source());
        assertEquals("skipReason.MUTATED_BETWEEN_OCCURRENCES", index.family());
        assertEquals("MUTATED_BETWEEN_OCCURRENCES", index.reason());
        assertEquals("proveOperandStabilityBetweenOccurrences", index.remainingWork());
        assertTrue(index.hint().contains("proveOperandStabilityBetweenOccurrences"));
        assertEquals("candidateDiscovery.noRewriteCandidates", index.autoVectorizationFamily());
    }

    @Test
    void autoVectorizationBlockerIsUsedWhenCseHasNoBlocker() {
        GpuIrOptimizationValidationReport report = GpuIrOptimizationValidationRuleTestFixtures.validationReport("kernel");

        GpuIrOptimizationValidationOptimizerBlockerIndex index = report.optimizerBlockerIndex();

        assertTrue(index.blocked());
        assertEquals("autoVectorization", index.source());
        assertEquals("candidateDiscovery.noRewriteCandidates", index.family());
        assertEquals("noRewriteCandidates", index.reason());
        assertEquals("collectRewriteCandidates", index.remainingWork());
        assertEquals("none", index.cseFamily());
    }

    @Test
    void readyReportUsesNoneBlockerWithoutFakeWork() {
        GpuIrOptimizationValidationReport report = autoVectorizationReadyReport("kernel");

        GpuIrOptimizationValidationOptimizerBlockerIndex index = report.optimizerBlockerIndex();
        Map<String, String> fields = index.artifactFields();

        assertFalse(index.blocked());
        assertEquals("ready", index.verdict());
        assertEquals("none", index.source());
        assertEquals("none", index.family());
        assertEquals("none", index.remainingWork());
        assertEquals("none", fields.get("optimizerBlockerSource"));
        assertTrue(fields.get("optimizerBlockerSummary").contains("verdict=ready"));
    }

    @Test
    void rejectsBlankContractFieldsAndPrefixes() {
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationOptimizerBlockerIndex(
                "",
                "ready",
                "none",
                "none",
                "none",
                "none",
                "none",
                "none",
                "none"
        ));
        assertThrows(IllegalArgumentException.class, () -> new GpuIrOptimizationValidationOptimizerBlockerIndex(
                "kernel",
                "",
                "none",
                "none",
                "none",
                "none",
                "none",
                "none",
                "none"
        ));
        assertThrows(IllegalArgumentException.class, () -> GpuIrOptimizationValidationRuleTestFixtures
                .validationReport("kernel")
                .optimizerBlockerIndex()
                .artifactFields(""));
    }

    private static GpuIrOptimizationValidationReport reportWithSafetyErrorAndCseBlocker() {
        GpuIrOptimizationValidationReport report = cseBlockedReport();
        return new GpuIrOptimizationValidationReport(
                report.methodName(),
                Optional.of("undefined variable missing"),
                report.commonSubexpressionPreview(),
                report.commonSubexpressionNumericBoundaryReport(),
                report.commonSubexpressionLiteralProofReport(),
                report.commonSubexpressionLiteralCanonicalizationReport(),
                report.commonSubexpressionLiteralNumericSemanticsProofReport(),
                report.commonSubexpressionLiteralTypedNumericBlockerSummaryReport(),
                report.commonSubexpressionLiteralRuntimeEquivalenceReport(),
                report.commonSubexpressionLiteralCanonicalizationGate(),
                report.commonSubexpressionLiteralFingerprintDecisionReport(),
                report.commonSubexpressionLiteralFingerprintParityReport(),
                report.commonSubexpressionLiteralEnablementReport(),
                report.commonSubexpressionLiteralRewritePreflightReport(),
                report.commonSubexpressionLiteralRewriteOperationPreviewReport(),
                report.commonSubexpressionLiteralPromotionChecklistReport(),
                report.commonSubexpressionLiteralPromotionReadinessSummaryReport(),
                report.commonSubexpressionLiteralConsistencyCheckReport(),
                report.autoVectorizationPreview(),
                report.autoVectorizationRewriteDryRunReport(),
                report.autoVectorizationResolvedRewriteOperations()
        );
    }

    private static GpuIrOptimizationValidationReport cseBlockedReport() {
        return GpuIrOptimizationValidationRuleTestFixtures.validate(new GpuIrMethod("kernel", List.of(
                new GpuIrVariableDeclaration("int", "z", new GpuIrVariableRef("x")),
                new GpuIrVariableDeclaration("int", "first", new GpuIrBinary("+", new GpuIrVariableRef("z"), new GpuIrLiteral("1"))),
                new GpuIrAssignment(new GpuIrVariableRef("z"), new GpuIrLiteral("7")),
                new GpuIrVariableDeclaration("int", "second", new GpuIrBinary("+", new GpuIrVariableRef("z"), new GpuIrLiteral("1"))),
                new GpuIrReturn(null)
        )));
    }

    private static GpuIrOptimizationValidationReport autoVectorizationReadyReport(String methodName) {
        GpuIrOptimizationValidationReport baseReport = GpuIrOptimizationValidationRuleTestFixtures.validationReport(methodName);
        return new GpuIrOptimizationValidationReport(
                baseReport.methodName(),
                baseReport.safetyError(),
                baseReport.commonSubexpressionPreview(),
                baseReport.commonSubexpressionNumericBoundaryReport(),
                baseReport.commonSubexpressionLiteralProofReport(),
                baseReport.commonSubexpressionLiteralCanonicalizationReport(),
                baseReport.commonSubexpressionLiteralNumericSemanticsProofReport(),
                baseReport.commonSubexpressionLiteralTypedNumericBlockerSummaryReport(),
                baseReport.commonSubexpressionLiteralRuntimeEquivalenceReport(),
                baseReport.commonSubexpressionLiteralCanonicalizationGate(),
                baseReport.commonSubexpressionLiteralFingerprintDecisionReport(),
                baseReport.commonSubexpressionLiteralFingerprintParityReport(),
                baseReport.commonSubexpressionLiteralEnablementReport(),
                baseReport.commonSubexpressionLiteralRewritePreflightReport(),
                baseReport.commonSubexpressionLiteralRewriteOperationPreviewReport(),
                baseReport.commonSubexpressionLiteralPromotionChecklistReport(),
                baseReport.commonSubexpressionLiteralPromotionReadinessSummaryReport(),
                baseReport.commonSubexpressionLiteralConsistencyCheckReport(),
                readyAutoVectorizationPreview(methodName),
                GpuIrAutoVectorizationRewriteDryRunReport.ready(methodName, 1, 1, 1),
                readyAutoVectorizationResolvedOperations(methodName)
        );
    }

    private static GpuIrAutoVectorizationPreview readyAutoVectorizationPreview(String methodName) {
        return new GpuIrAutoVectorizationPreview(
                methodName,
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
                        List.of("read left[i=0..3]"),
                        List.of(),
                        List.of("out"),
                        List.of("left")
                )),
                List.of(),
                List.of()
        );
    }

    private static GpuIrAutoVectorizationResolvedRewriteOperations readyAutoVectorizationResolvedOperations(
            String methodName
    ) {
        return new GpuIrAutoVectorizationResolvedRewriteOperations(
                methodName,
                List.of(new GpuIrAutoVectorizationResolvedInsertionOperation(
                        "stmt[0]",
                        0,
                        "int4",
                        0,
                        4,
                        1,
                        1,
                        List.of("read left[i=0..3]")
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
        );
    }
}
