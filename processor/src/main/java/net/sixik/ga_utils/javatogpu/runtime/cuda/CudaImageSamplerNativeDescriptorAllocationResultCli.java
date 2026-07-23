package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the explicit opt-in CUDA image/sampler native descriptor allocation-result diagnostic.
 */
public final class CudaImageSamplerNativeDescriptorAllocationResultCli {
    private CudaImageSamplerNativeDescriptorAllocationResultCli() {
    }

    public static void main(String[] args) {
        CudaImageSamplerNativeDescriptorAllocationResultReport report =
                CudaImageSamplerNativeDescriptorAllocationResultReport.inspectSample();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaImageSamplerNativeDescriptorAllocationResultReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler native descriptor allocation result:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- allocationTransactionPlanStatus=").append(report.allocationTransactionPlanStatus()).append(System.lineSeparator());
        builder.append("- allocationTransactionPlanPresent=").append(report.allocationTransactionPlanPresent()).append(System.lineSeparator());
        builder.append("- allocationApplyEnabled=").append(report.allocationApplyEnabled()).append(System.lineSeparator());
        builder.append("- nativeMemoryAllocationEnabled=").append(report.nativeMemoryAllocationEnabled()).append(System.lineSeparator());
        builder.append("- sdkStructByteEncodingEnabled=").append(report.sdkStructByteEncodingEnabled()).append(System.lineSeparator());
        builder.append("- objectCreationEnabled=").append(report.objectCreationEnabled()).append(System.lineSeparator());
        builder.append("- runtimeBindingEnabled=").append(report.runtimeBindingEnabled()).append(System.lineSeparator());
        builder.append("- nativeMemoryServices=").append(report.nativeMemoryServiceSummary()).append(System.lineSeparator());
        builder.append("- entryReady=")
                .append(report.entryReadyCount())
                .append('/')
                .append(report.entryCount())
                .append(System.lineSeparator());
        builder.append("- descriptorOwners=").append(report.descriptorOwnerCount()).append(System.lineSeparator());
        builder.append("- resourceDescriptorOwners=").append(report.resourceDescriptorOwnerCount()).append(System.lineSeparator());
        builder.append("- textureDescriptorOwners=").append(report.textureDescriptorOwnerCount()).append(System.lineSeparator());
        builder.append("- activeDescriptorOwnersBeforeClose=").append(report.activeDescriptorOwnerCountBeforeClose()).append(System.lineSeparator());
        builder.append("- nativeAddressesPresentBeforeClose=").append(report.nativeAddressPresentCountBeforeClose()).append(System.lineSeparator());
        builder.append("- nativeByteSizeExpected=").append(report.expectedNativeByteSize()).append(System.lineSeparator());
        builder.append("- nativeByteSize=").append(report.nativeByteSize()).append(System.lineSeparator());
        builder.append("- closedAfterClose=").append(report.closedAfterClose()).append(System.lineSeparator());
        builder.append("- activeDescriptorOwnersAfterClose=").append(report.activeDescriptorOwnerCountAfterClose()).append(System.lineSeparator());
        builder.append("- nativeAddressesPresentAfterClose=").append(report.nativeAddressPresentCountAfterClose()).append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        builder.append("- rule=explicit opt-in host descriptor memory is allocated and released without SDK byte encoding, CUDA object creation, or runtime binding")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
