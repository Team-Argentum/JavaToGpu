package net.sixik.ga_utils.javatogpu.runtime.opencl;

/**
 * Prints the hardware-free OpenCL backend SPI contract snapshot.
 */
public final class OpenClBackendSpiContractCli {
    private OpenClBackendSpiContractCli() {
    }

    public static void main(String[] args) {
        OpenClBackendSpiContractReport report = OpenClBackendSpiContractReport.inspectBuiltInProvider();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(OpenClBackendSpiContractReport report) {
        return String.join(System.lineSeparator(),
                "OpenCL backend SPI contract:",
                "- status=" + report.status(),
                "- provider=" + report.providerId(),
                "- backend=" + report.backendTarget(),
                "- productionExecution=" + report.executionSupport().productionExecution(),
                "- pipelineAvailable=" + report.executionSupport().executionPipelineAvailable(),
                "- pipelineFactoryPresent=" + report.executionPipelineFactory().isPresent(),
                "- moduleFormats=" + report.executionSupport().moduleFormatKeys(),
                "- supportedStages=" + report.executionSupport().supportedStageKeys(),
                "- firstBlocker=" + report.firstBlocker(),
                "- rule=metadata-only; no OpenCL platform, context, program, or kernel is opened",
                ""
        );
    }
}
