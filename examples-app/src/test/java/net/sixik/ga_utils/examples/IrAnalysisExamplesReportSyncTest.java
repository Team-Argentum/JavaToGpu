package net.sixik.ga_utils.examples;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class IrAnalysisExamplesReportSyncTest {
    private static final Path IR_VALIDATION_REPORT = Path.of(
            "build",
            "generated",
            "sources",
            "annotationProcessor",
            "java",
            "main",
            "reports",
            "examples-app-ir-validation.properties"
    );

    @Test
    void optimizerBlockerTableMatchesGeneratedIrValidationReportWhenEnabled() throws IOException {
        Assumptions.assumeTrue(
                Files.isRegularFile(IR_VALIDATION_REPORT),
                "IR validation report is generated only with -Pjavatogpu.enableIrValidationExamples=true"
        );

        Properties report = new Properties();
        try (InputStream input = Files.newInputStream(IR_VALIDATION_REPORT)) {
            report.load(input);
        }

        Map<String, IrAnalysisExamples.IrOptimizerBlockerRow> expectedRows = IrAnalysisExamples
                .optimizerBlockerRows()
                .stream()
                .filter(row -> row.method().startsWith("irPasses"))
                .collect(Collectors.toMap(
                        IrAnalysisExamples.IrOptimizerBlockerRow::method,
                        Function.identity()
                ));

        for (IrAnalysisExamples.IrOptimizerBlockerRow row : expectedRows.values()) {
            String entryPrefix = findReportEntryPrefix(report, row.method());
            assertEquals("ok", report.getProperty(entryPrefix + "safety"), row.method());
            assertEquals(row.source(), report.getProperty(entryPrefix + "optimizerBlockerSource"), row.method());
            assertEquals(row.family(), report.getProperty(entryPrefix + "optimizerBlockerFamily"), row.method());
            assertEquals(row.remainingWork(), report.getProperty(entryPrefix + "optimizerBlockerRemainingWork"), row.method());
        }
    }

    @Test
    void productionReadinessStageTableMatchesGeneratedIrValidationReportWhenEnabled() throws IOException {
        Assumptions.assumeTrue(
                Files.isRegularFile(IR_VALIDATION_REPORT),
                "IR validation report is generated only with -Pjavatogpu.enableIrValidationExamples=true"
        );

        Properties report = new Properties();
        try (InputStream input = Files.newInputStream(IR_VALIDATION_REPORT)) {
            report.load(input);
        }

        Map<String, IrAnalysisExamples.IrProductionReadinessRow> expectedRows = IrAnalysisExamples
                .productionReadinessRows()
                .stream()
                .filter(row -> row.method().startsWith("irPasses"))
                .collect(Collectors.toMap(
                        IrAnalysisExamples.IrProductionReadinessRow::method,
                        Function.identity()
                ));

        for (IrAnalysisExamples.IrProductionReadinessRow row : expectedRows.values()) {
            String entryPrefix = findReportEntryPrefix(report, row.method());
            assertEquals(row.firstBlockingStage(), report.getProperty(entryPrefix + "optimizerProductionReadinessFirstBlockingStage"), row.method());
            assertEquals(row.verdict(), report.getProperty(entryPrefix + "optimizerProductionReadinessVerdict"), row.method());
            assertEquals("false", report.getProperty(entryPrefix + "optimizerProductionReadinessProductionMutationEnabled"), row.method());
            assertEquals("false", report.getProperty(entryPrefix + "optimizerProductionReadinessAcceptance.Accepted"), row.method());
        }
    }

    private static String findReportEntryPrefix(Properties report, String methodName) {
        int entryCount = Integer.parseInt(report.getProperty("entry.count"));
        for (int index = 0; index < entryCount; index++) {
            String prefix = "entry." + index + ".";
            if (methodName.equals(report.getProperty(prefix + "methodName"))) {
                return prefix;
            }
        }
        fail("Missing IR validation report entry for " + methodName);
        return "";
    }
}
