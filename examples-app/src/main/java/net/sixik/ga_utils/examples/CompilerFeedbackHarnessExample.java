package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.api.GpuBackendTarget;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilerFeedbackHarness;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilerFeedbackHarnessReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompilerFeedbackRequest;

/**
 * Runnable example for hardware-free compiler feedback provider checks.
 */
public final class CompilerFeedbackHarnessExample {

    private CompilerFeedbackHarnessExample() {
    }

    public static void main(String[] args) {
        System.out.println(renderCompilerFeedbackHarnessPreview());
    }

    static String renderCompilerFeedbackHarnessPreview() {
        GpuBackendCompilerFeedbackHarnessReport report = GpuBackendCompilerFeedbackHarness
                .loadWithBuiltIns()
                .runSynthetic(exampleRequest());

        StringBuilder builder = new StringBuilder();
        builder.append("Backend compiler feedback harness:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- backend=").append(report.backendTarget()).append(System.lineSeparator());
        builder.append("- providerExecutions=").append(report.feedbackReport().executions().size()).append(System.lineSeparator());
        builder.append("- feedbackCount=").append(report.feedbackReport().feedback().size()).append(System.lineSeparator());
        builder.append("- selectedProvider=").append(report.selectedProviderId()).append(System.lineSeparator());
        builder.append("- available=").append(report.available()).append(System.lineSeparator());
        report.feedbackReport().selected().ifPresent(feedback -> {
            builder.append("- effectiveRegisters=").append(feedback.effectiveRegisterCount()).append(System.lineSeparator());
            builder.append("- localMemoryBytes=").append(feedback.localMemoryBytes()).append(System.lineSeparator());
            builder.append("- occupancyPermille=").append(feedback.occupancyPermille()).append(System.lineSeparator());
        });
        builder.append("- rule=compiler logs are parsed from synthetic text; no backend compiler or GPU is opened")
                .append(System.lineSeparator());
        return builder.toString();
    }

    static GpuBackendCompilerFeedbackRequest exampleRequest() {
        return new GpuBackendCompilerFeedbackRequest(
                GpuBackendTarget.OPENCL,
                "opencl-c",
                "synthetic/example-compiler-feedback.opencl-c",
                """
                        example.registers=18
                        example.localMemoryBytes=512
                        example.occupancyPermille=875
                        """
        );
    }
}
