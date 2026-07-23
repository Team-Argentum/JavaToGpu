package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the hardware-free CUDA image/sampler descriptor contract.
 */
public final class CudaImageSamplerDescriptorContractCli {
    private CudaImageSamplerDescriptorContractCli() {
    }

    public static void main(String[] args) {
        CudaImageSamplerDescriptorContractReport report = CudaImageSamplerDescriptorContractReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaImageSamplerDescriptorContractReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler descriptor contract:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- entryReady=")
                .append(report.entryReadyCount())
                .append('/')
                .append(report.entries().size())
                .append(System.lineSeparator());
        builder.append("- entryBlocked=").append(report.entryBlockedCount()).append(System.lineSeparator());
        builder.append("- textureEntries=").append(report.textureEntryCount()).append(System.lineSeparator());
        builder.append("- surfaceEntries=").append(report.surfaceEntryCount()).append(System.lineSeparator());
        builder.append("- samplerEntries=").append(report.samplerEntryCount()).append(System.lineSeparator());
        builder.append("- resourceDescriptors=").append(report.resourceDescriptorRequiredCount()).append(System.lineSeparator());
        builder.append("- textureDescriptors=").append(report.textureDescriptorRequiredCount()).append(System.lineSeparator());
        builder.append("- descriptorBuildEnabled=").append(report.descriptorBuildEnabled()).append(System.lineSeparator());
        builder.append("- nativeDescriptorAllocationEnabled=").append(report.nativeDescriptorAllocationEnabled()).append(System.lineSeparator());
        builder.append("- activeDescriptorCount=").append(report.activeDescriptorCount()).append(System.lineSeparator());
        builder.append("- nativeLayoutPending=").append(report.nativeLayoutPendingCount()).append(System.lineSeparator());
        builder.append("- objectCreationContractStatus=").append(report.objectCreationContract().status()).append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        for (CudaImageSamplerDescriptorContractReport.Entry entry : report.entries()) {
            builder.append("- entry.")
                    .append(entry.key())
                    .append("=resource:")
                    .append(entry.resourceDescriptorKind())
                    .append('/')
                    .append(entry.resourceDescriptorDimension())
                    .append(",textureRequired:")
                    .append(entry.textureDescriptorRequired())
                    .append(",samplerState:")
                    .append(entry.samplerStateSource())
                    .append(",status:")
                    .append(entry.descriptorBuildStatus())
                    .append(System.lineSeparator());
        }
        builder.append("- rule=descriptor contract is metadata only; native descriptor allocation and object creation remain disabled")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
