package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the metadata-only CUDA execution green-light checklist.
 */
public final class CudaExecutionReadinessCli {
    private CudaExecutionReadinessCli() {
    }

    public static void main(String[] args) {
        CudaExecutionReadinessReport report = CudaExecutionReadinessReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaExecutionReadinessReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA execution green-light checklist:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- openClSpiStatus=").append(report.openClSpiContract().status()).append(System.lineSeparator());
        builder.append("- providerCatalogCount=").append(report.providerCatalog().providers().size()).append(System.lineSeparator());
        builder.append("- sharedRunnerAvailableCount=")
                .append(report.providerCatalog().sharedPipelineRunnerAvailableCount())
                .append(System.lineSeparator());
        builder.append("- cudaProviderPresent=").append(report.cudaProvider().isPresent()).append(System.lineSeparator());
        builder.append("- cudaProvider=").append(report.cudaProvider()
                .map(provider -> provider.providerId())
                .orElse("missing")).append(System.lineSeparator());
        builder.append("- cudaExecutionAvailability=").append(report.cudaExecutionAvailability()
                .map(availability -> availability.status())
                .orElse("missing")).append(System.lineSeparator());
        builder.append("- cudaPipelineAvailable=").append(report.cudaProvider()
                .map(provider -> provider.executionSupport().executionPipelineAvailable())
                .orElse(false)).append(System.lineSeparator());
        builder.append("- cudaPipelineFactoryPresent=").append(report.cudaProvider()
                .map(provider -> provider.executionPipelineFactory().isPresent())
                .orElse(false)).append(System.lineSeparator());
        builder.append("- cudaModuleFormats=").append(report.cudaProvider()
                .map(provider -> provider.executionSupport().moduleFormatKeys())
                .orElse("")).append(System.lineSeparator());
        builder.append("- unsupportedReceipt=compile:")
                .append(report.cudaUnsupportedReceipt().compilationResult().stageResult().status())
                .append(",prepare:")
                .append(report.cudaUnsupportedReceipt().preparationResult().stageResult().status())
                .append(",invoke:")
                .append(report.cudaUnsupportedReceipt().invocationResult().stageResult().status())
                .append(System.lineSeparator());
        builder.append("- checklistReady=")
                .append(report.checklistReadyCount())
                .append('/')
                .append(report.checklist().size())
                .append(System.lineSeparator());
        builder.append("- checklistBlocked=").append(report.checklistBlockedCount()).append(System.lineSeparator());
        builder.append("- checklistFirstBlocked=").append(report.firstBlockedChecklistItem()).append(System.lineSeparator());
        for (CudaExecutionReadinessReport.ChecklistItem item : report.checklist()) {
            builder.append("- checklist.")
                    .append(item.key())
                    .append('=')
                    .append(item.status())
                    .append(System.lineSeparator());
        }
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        builder.append("- rule=metadata-only; CUDA inventory is allowed, CUDA kernel execution must remain disabled until the vertical slice starts")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
