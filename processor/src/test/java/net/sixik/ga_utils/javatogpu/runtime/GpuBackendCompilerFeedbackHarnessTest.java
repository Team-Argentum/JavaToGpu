package net.sixik.ga_utils.javatogpu.runtime;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.extension.GpuExtensionExecutionOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuBackendCompilerFeedbackHarnessTest {

    @Test
    void runsGenericProviderAgainstSyntheticCompileLogWithoutNativeCompiler() {
        GpuBackendCompilerFeedbackHarnessReport report = GpuBackendCompilerFeedbackHarness
                .of(List.of(new GpuGenericCompilerFeedbackProvider()))
                .runSyntheticOpenCl();

        GpuBackendCompilerFeedback selected = report.feedbackReport().selected().orElseThrow();

        assertEquals(GpuBackendTarget.OPENCL, report.backendTarget());
        assertEquals("recorded", report.status());
        assertTrue(report.available());
        assertTrue(report.allProvidersCompleted());
        assertEquals(GpuGenericCompilerFeedbackProvider.PROVIDER_ID, report.selectedProviderId());
        assertEquals(32, selected.effectiveRegisterCount());
        assertEquals(0, selected.knownSpillBytes());
        assertEquals(2_048, selected.localMemoryBytes());
        assertEquals(750, selected.occupancyPermille());
        assertEquals("recorded", report.artifactFields("compilerHarness").get("compilerHarness.status"));
        assertEquals("32", report.artifactFields("compilerHarness")
                .get("compilerHarness.feedback.selected.register.effective"));
        assertTrue(report.toMarkdown().contains("Backend compiler feedback harness: recorded"));
        assertTrue(report.toMarkdown().contains("effectiveRegisters=32"));
    }

    @Test
    void reportsProviderFailuresWhileContinuingWithLaterProviders() {
        GpuBackendCompilerFeedbackHarnessReport report = GpuBackendCompilerFeedbackHarness
                .of(List.of(new FailingProvider("compiler-feedback:harness-failing"), new GpuGenericCompilerFeedbackProvider()))
                .runSyntheticOpenCl();

        assertEquals("recorded-with-provider-failures", report.status());
        assertTrue(report.available());
        assertFalse(report.allProvidersCompleted());
        assertEquals(GpuGenericCompilerFeedbackProvider.PROVIDER_ID, report.selectedProviderId());
        assertEquals(GpuExtensionExecutionOutcome.FAILED_CONTINUED,
                report.feedbackReport().executions().get(0).outcome());
        assertEquals(GpuExtensionExecutionOutcome.SUCCEEDED,
                report.feedbackReport().executions().get(1).outcome());
        assertTrue(report.artifactFields("compilerHarness")
                .get("compilerHarness.feedback.execution.0.message")
                .contains("synthetic feedback provider failure"));
    }

    @Test
    void reportsUnavailableWhenSyntheticLogDoesNotContainResourceMetrics() {
        GpuBackendCompilerFeedbackHarnessReport report = GpuBackendCompilerFeedbackHarness
                .of(List.of(new GpuGenericCompilerFeedbackProvider()))
                .runSynthetic(new GpuBackendCompilerFeedbackRequest(
                        GpuBackendTarget.OPENCL,
                        "opencl-c",
                        "synthetic/no-metrics.opencl-c",
                        "backend compilation completed successfully"
                ));

        assertEquals("unavailable", report.status());
        assertFalse(report.available());
        assertTrue(report.allProvidersCompleted());
        assertEquals("none", report.selectedProviderId());
        assertEquals("unavailable", report.artifactFields("compilerHarness").get("compilerHarness.status"));
    }

    private record FailingProvider(String id) implements GpuBackendCompilerFeedbackProvider {

        @Override
        public Optional<GpuBackendCompilerFeedback> inspect(GpuBackendCompilerFeedbackRequest request) {
            throw new IllegalStateException("synthetic feedback provider failure");
        }

        @Override
        public String extensionId() {
            return id;
        }
    }
}
