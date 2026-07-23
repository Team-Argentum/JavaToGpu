package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the hardware-free CUDA inventory provider/adapter contract snapshot.
 */
public final class CudaInventoryContractCli {
    private CudaInventoryContractCli() {
    }

    public static void main(String[] args) {
        CudaInventoryContractReport report = CudaInventoryContractReport.inspectBuiltInProvider();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaInventoryContractReport report) {
        return String.join(System.lineSeparator(),
                "CUDA inventory contract:",
                "- status=" + report.status(),
                "- provider=" + report.provider().providerId(),
                "- backend=" + report.provider().backendTarget(),
                "- adapter=" + report.adapter().backendName(),
                "- catalogProductionAdapter=" + report.catalogEntry().productionAdapter(),
                "- executionPipelineAvailable=" + report.provider().executionSupport().executionPipelineAvailable(),
                "- executionPipelineFactoryPresent=" + report.provider().executionPipelineFactory().isPresent(),
                "- moduleFormats=" + report.provider().executionSupport().moduleFormatKeys(),
                "- capabilities=" + report.provider().executionSupport().capabilityKeys(),
                "- lowererSelectedSource=" + report.lowererSourceSelectionPlan().selectedSource(),
                "- lowererPayloadFormat=" + report.lowererSourceSelectionPlan().payloadFormat(),
                "- firstBlocker=" + report.firstBlocker(),
                "- rule=metadata-only; does not run nvidia-smi or open native CUDA/OpenCL state",
                ""
        );
    }
}
