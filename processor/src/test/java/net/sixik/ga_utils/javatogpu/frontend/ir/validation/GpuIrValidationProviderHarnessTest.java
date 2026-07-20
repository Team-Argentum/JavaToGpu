package net.sixik.ga_utils.javatogpu.frontend.ir.validation;

import net.sixik.ga_utils.javatogpu.extension.GpuExtensionPermission;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuIrValidationProviderHarnessTest {
    @Test
    void runsSyntheticKernelAndHelperThroughValidationProvider() {
        GpuIrValidationProviderHarness harness = new GpuIrValidationProviderHarness(List.of(new ReportingProvider()));

        GpuIrValidationProviderHarnessReport report = harness.runSynthetic();

        assertEquals("completed", report.status());
        assertEquals(1, report.providerCount());
        assertEquals(2, report.executionReports().size());
        assertEquals(2, report.validationEntries().size());
        assertEquals("none", report.firstBlocker());
        assertEquals("true", report.artifactFields("test.irValidation").get("irValidation.harness.present"));
        assertTrue(report.toMarkdown().contains("IR validation provider harness: completed"));
    }

    @Test
    void diagnosticFailuresAreReportedWithoutStoppingHarness() {
        GpuIrValidationProviderHarness harness = new GpuIrValidationProviderHarness(List.of(request -> {
            throw new IllegalStateException("synthetic validator failure");
        }));

        GpuIrValidationProviderHarnessReport report = harness.runSynthetic();

        assertEquals("completed-with-provider-failures", report.status());
        assertEquals(2, report.executionReports().size());
        assertTrue(report.firstBlocker().contains("synthetic validator failure"));
        assertTrue(report.diagnostics().stream().anyMatch(line -> line.contains("synthetic validator failure")));
    }

    @Test
    void contractErrorsAreReturnedAsHarnessReport() {
        GpuIrValidationProvider provider = new ReportingProvider() {
            @Override
            public GpuExtensionPermission extensionPermission() {
                return GpuExtensionPermission.MUTATION_PROPOSAL;
            }
        };
        GpuIrValidationProviderHarness harness = new GpuIrValidationProviderHarness(List.of(provider));

        GpuIrValidationProviderHarnessReport report = harness.runSynthetic();

        assertEquals("contract-error", report.status());
        assertTrue(report.firstBlocker().contains("permission MUTATION_PROPOSAL"));
    }

    private static class ReportingProvider implements GpuIrValidationProvider {
        @Override
        public void validate(GpuIrValidationRequest request) {
            request.reportEntry(new GpuIrValidationReportEntry(
                    "test.ir-validation.provider",
                    request.method().parsedMethod().name(),
                    request.entryPoint(),
                    Map.of("entryPoint", Boolean.toString(request.entryPoint()))
            ));
        }

        @Override
        public String extensionId() {
            return "test.ir-validation.provider";
        }
    }
}
