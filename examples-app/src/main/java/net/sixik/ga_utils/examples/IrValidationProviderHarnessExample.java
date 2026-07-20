package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationProviderHarness;
import net.sixik.ga_utils.javatogpu.frontend.ir.validation.GpuIrValidationProviderHarnessReport;

/**
 * Shows how to smoke-test ServiceLoader IR validation providers without running javac annotation processing.
 */
public final class IrValidationProviderHarnessExample {
    private IrValidationProviderHarnessExample() {
    }

    public static void main(String[] args) {
        GpuIrValidationProviderHarnessReport report = GpuIrValidationProviderHarness
                .loadFromServiceLoader()
                .runSynthetic();

        System.out.println(renderIrValidationProviderHarnessPreview(report));
    }

    public static String renderIrValidationProviderHarnessPreview() {
        return renderIrValidationProviderHarnessPreview(GpuIrValidationProviderHarness
                .loadFromServiceLoader()
                .runSynthetic());
    }

    public static String renderIrValidationProviderHarnessPreview(GpuIrValidationProviderHarnessReport report) {
        return String.join(System.lineSeparator(),
                "IR validation provider harness:",
                "- status=" + report.status(),
                "- mode=" + report.mode(),
                "- providers=" + report.providerCount(),
                "- executions=" + report.executionReports().size(),
                "- entries=" + report.validationEntries().size(),
                "- allProvidersCompleted=" + report.allProvidersCompleted(),
                "- firstBlocker=" + report.firstBlocker(),
                "- rule=synthetic IR methods exercise validation providers; no GPU or annotation processor run is required",
                ""
        );
    }
}
