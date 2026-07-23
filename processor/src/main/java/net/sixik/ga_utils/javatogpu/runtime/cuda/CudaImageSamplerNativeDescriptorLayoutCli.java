package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the hardware-free CUDA image/sampler native descriptor layout preview.
 */
public final class CudaImageSamplerNativeDescriptorLayoutCli {
    private CudaImageSamplerNativeDescriptorLayoutCli() {
    }

    public static void main(String[] args) {
        CudaImageSamplerNativeDescriptorLayoutReport report = CudaImageSamplerNativeDescriptorLayoutReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaImageSamplerNativeDescriptorLayoutReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler native descriptor layout:").append(System.lineSeparator());
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
        builder.append("- resourceLayouts=").append(report.resourceLayoutRequiredCount()).append(System.lineSeparator());
        builder.append("- resourceLayoutFields=").append(report.resourceLayoutFieldCount()).append(System.lineSeparator());
        builder.append("- textureLayouts=").append(report.textureLayoutRequiredCount()).append(System.lineSeparator());
        builder.append("- textureLayoutFields=").append(report.textureLayoutFieldCount()).append(System.lineSeparator());
        builder.append("- nativeLayoutPreview=").append(report.nativeLayoutPreviewCount()).append(System.lineSeparator());
        builder.append("- nativeLayoutBuildEnabled=").append(report.nativeLayoutBuildEnabled()).append(System.lineSeparator());
        builder.append("- nativeDescriptorAllocationEnabled=").append(report.nativeDescriptorAllocationEnabled()).append(System.lineSeparator());
        builder.append("- activeNativeDescriptorCount=").append(report.activeNativeDescriptorCount()).append(System.lineSeparator());
        builder.append("- descriptorContractStatus=").append(report.descriptorContract().status()).append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        for (CudaImageSamplerNativeDescriptorLayoutReport.Entry entry : report.entries()) {
            builder.append("- entry.")
                    .append(entry.key())
                    .append("=resource:")
                    .append(entry.resourceLayoutStruct())
                    .append('/')
                    .append(entry.resourceLayoutFieldCount())
                    .append(",texture:")
                    .append(entry.textureLayoutStruct())
                    .append('/')
                    .append(entry.textureLayoutFieldCount())
                    .append(",status:")
                    .append(entry.nativeLayoutStatus())
                    .append(System.lineSeparator());
        }
        builder.append("- rule=native descriptor layout is a preview only; no CUDA descriptor memory or objects are created")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
