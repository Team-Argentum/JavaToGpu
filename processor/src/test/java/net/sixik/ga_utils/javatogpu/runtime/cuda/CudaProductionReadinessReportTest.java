package net.sixik.ga_utils.javatogpu.runtime.cuda;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaProductionReadinessReportTest {

    @Test
    void reportsReviewReadyWhenBinarySmokeHasRichEvidenceAndProductionIsDisabled() {
        CudaProductionReadinessReport report = CudaProductionReadinessReport.inspect(
                skippedPtxSummary(),
                richSummary("integrationCudaCubinSmokeTest", "cubin"),
                richSummary("integrationCudaFatbinSmokeTest", "fatbin")
        );
        Map<String, String> fields = report.artifactFields("test.cuda.productionReadiness");
        Properties properties = report.toProperties();
        String rendered = CudaProductionReadinessCli.render(report);

        assertEquals("review-ready", report.status());
        assertFalse(report.blocked());
        assertTrue(report.reviewReady());
        assertFalse(report.productionReady());
        assertFalse(report.productionExecutionEnabled());
        assertFalse(report.productionPolicyAccepted());
        assertTrue(report.binaryEvidenceReady());
        assertEquals(2, report.binaryRichEvidenceCount());
        assertTrue(report.structValueEvidenceReady());
        assertTrue(report.localStructEvidenceReady());
        assertTrue(report.ptxEvidenceAcceptable());
        assertEquals("none", report.firstBlocker());
        assertTrue(report.remainingWork().contains("cuda-production-policy-not-accepted"));
        assertFalse(report.remainingWork().contains("cuda-local-struct-coverage-pending"));
        assertFalse(report.remainingWork().contains("cuda-struct-value-real-kernel-validation-pending"));
        assertFalse(report.remainingWork().contains("cuda-struct-by-value-and-local-struct-coverage-pending"));
        assertEquals("review-ready", fields.get("runtime.cuda.productionReadiness.status"));
        assertEquals("true", fields.get("runtime.cuda.productionReadiness.reviewReady"));
        assertEquals("false", fields.get("runtime.cuda.productionReadiness.productionReady"));
        assertEquals("true", fields.get("runtime.cuda.productionReadiness.binaryEvidence.ready"));
        assertEquals("2", fields.get("runtime.cuda.productionReadiness.binaryEvidence.rich.count"));
        assertEquals("true", fields.get("runtime.cuda.productionReadiness.structValueEvidence.ready"));
        assertEquals("true", fields.get("runtime.cuda.productionReadiness.localStructEvidence.ready"));
        assertEquals("review-ready", properties.getProperty("status"));
        assertEquals("true", properties.getProperty("binaryEvidence.ready"));
        assertEquals("true", properties.getProperty("structValueEvidence.ready"));
        assertEquals("true", properties.getProperty("localStructEvidence.ready"));
        assertEquals("false", properties.getProperty("productionExecution.enabled"));
        assertTrue(rendered.contains("status=review-ready"));
        assertTrue(rendered.contains("binaryRichEvidence=2/2"));
        assertTrue(rendered.contains("structValueEvidenceReady=true"));
        assertTrue(rendered.contains("localStructEvidenceReady=true"));
    }

    @Test
    void keepsStructValuePendingUntilBinarySmokeHasScenarioEvidence() {
        CudaProductionReadinessReport report = CudaProductionReadinessReport.inspect(
                skippedPtxSummary(),
                richSummaryWithoutStructValueScenario("integrationCudaCubinSmokeTest", "cubin"),
                richSummary("integrationCudaFatbinSmokeTest", "fatbin")
        );

        assertEquals("review-ready", report.status());
        assertFalse(report.blocked());
        assertFalse(report.structValueEvidenceReady());
        assertTrue(report.remainingWork().contains("cuda-struct-value-real-kernel-validation-pending"));
        assertEquals("false", report.toProperties().getProperty("structValueEvidence.ready"));
    }

    @Test
    void keepsLocalStructPendingUntilBinarySmokeHasScenarioEvidence() {
        CudaProductionReadinessReport report = CudaProductionReadinessReport.inspect(
                skippedPtxSummary(),
                richSummaryWithoutLocalStructScenario("integrationCudaCubinSmokeTest", "cubin"),
                richSummary("integrationCudaFatbinSmokeTest", "fatbin")
        );

        assertEquals("review-ready", report.status());
        assertFalse(report.blocked());
        assertTrue(report.structValueEvidenceReady());
        assertFalse(report.localStructEvidenceReady());
        assertTrue(report.remainingWork().contains("cuda-local-struct-coverage-pending"));
        assertEquals("false", report.toProperties().getProperty("localStructEvidence.ready"));
    }

    @Test
    void blocksWhenCubinOrFatbinRichEvidenceIsMissing() {
        CudaProductionReadinessReport report = CudaProductionReadinessReport.inspect(
                skippedPtxSummary(),
                richSummary("integrationCudaCubinSmokeTest", "cubin"),
                skippedBinarySummary("integrationCudaFatbinSmokeTest", "fatbin")
        );

        assertEquals("blocked", report.status());
        assertTrue(report.blocked());
        assertFalse(report.binaryEvidenceReady());
        assertTrue(report.blockers().contains(
                "cuda-binary-smoke-rich-evidence-missing:fatbin:status-skipped:cuda-tooling-unavailable"
        ));
        assertEquals("resolve-blockers-before-production-review", report.remainingWork().get(0));
    }

    @Test
    void blocksWhenPtxPassedWithoutRichEvidenceBecauseThatLooksLikeAFalsePositive() {
        CudaProductionReadinessReport report = CudaProductionReadinessReport.inspect(
                passedWithoutRichSummary("integrationCudaPtxSmokeTest", "ptx"),
                richSummary("integrationCudaCubinSmokeTest", "cubin"),
                richSummary("integrationCudaFatbinSmokeTest", "fatbin")
        );

        assertEquals("blocked", report.status());
        assertFalse(report.ptxEvidenceAcceptable());
        assertTrue(report.blockers().contains("cuda-ptx-smoke-rich-evidence-incomplete:none"));
    }

    private static Properties richSummary(String taskName, String outputFormat) {
        Properties properties = richSummaryWithoutLocalStructScenario(taskName, outputFormat);
        properties.setProperty("evidence.scenario.local-struct.count", "1");
        properties.setProperty("evidence.scenario.local-struct.realDriver.count", "1");
        return properties;
    }

    private static Properties richSummaryWithoutLocalStructScenario(String taskName, String outputFormat) {
        Properties properties = richSummaryWithoutStructValueScenario(taskName, outputFormat);
        properties.setProperty("evidence.scenario.struct-value.count", "1");
        properties.setProperty("evidence.scenario.struct-value.realDriver.count", "1");
        return properties;
    }

    private static Properties richSummaryWithoutStructValueScenario(String taskName, String outputFormat) {
        Properties properties = baseSummary(taskName, outputFormat, "passed", "none");
        properties.setProperty("test.executed.count", "5");
        properties.setProperty("evidence.count", "5");
        properties.setProperty("evidence.realDriver.count", "5");
        properties.setProperty("realDriverExecutionEvidence", "true");
        properties.setProperty("realDriverExecutionEvidence.rich", "true");
        return properties;
    }

    private static Properties skippedPtxSummary() {
        return baseSummary(
                "integrationCudaPtxSmokeTest",
                "ptx",
                "skipped",
                "cuda-driver-ptx-version-unsupported:ptx-9.3:driver-13.2:requires-13.3"
        );
    }

    private static Properties skippedBinarySummary(String taskName, String outputFormat) {
        return baseSummary(taskName, outputFormat, "skipped", "cuda-tooling-unavailable");
    }

    private static Properties passedWithoutRichSummary(String taskName, String outputFormat) {
        Properties properties = baseSummary(taskName, outputFormat, "passed", "none");
        properties.setProperty("test.executed.count", "5");
        properties.setProperty("evidence.count", "0");
        properties.setProperty("evidence.realDriver.count", "0");
        return properties;
    }

    private static Properties baseSummary(String taskName, String outputFormat, String status, String firstBlocker) {
        Properties properties = new Properties();
        properties.setProperty("format", "javatogpu.cuda-smoke-summary.v1");
        properties.setProperty("taskName", taskName);
        properties.setProperty("status", status);
        properties.setProperty("nvcc.outputFormat", outputFormat);
        properties.setProperty("test.executed.count", "0");
        properties.setProperty("evidence.count", "0");
        properties.setProperty("evidence.realDriver.count", "0");
        properties.setProperty("realDriverExecutionEvidence", "false");
        properties.setProperty("realDriverExecutionEvidence.rich", "false");
        properties.setProperty("firstBlocker", firstBlocker);
        return properties;
    }
}
