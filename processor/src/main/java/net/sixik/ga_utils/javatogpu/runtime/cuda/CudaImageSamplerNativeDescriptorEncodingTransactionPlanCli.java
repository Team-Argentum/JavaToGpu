package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the hardware-free CUDA image/sampler native descriptor encoding-transaction plan contract.
 */
public final class CudaImageSamplerNativeDescriptorEncodingTransactionPlanCli {
    private CudaImageSamplerNativeDescriptorEncodingTransactionPlanCli() {
    }

    public static void main(String[] args) {
        CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport report =
                CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler native descriptor encoding transaction plan:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- caseReady=")
                .append(report.caseReadyCount())
                .append('/')
                .append(report.cases().size())
                .append(System.lineSeparator());
        builder.append("- caseBlocked=").append(report.caseBlockedCount()).append(System.lineSeparator());
        builder.append("- encodingPlanReady=").append(report.encodingPlanReadyCount()).append(System.lineSeparator());
        builder.append("- encodingPlanBlocked=").append(report.encodingPlanBlockedCount()).append(System.lineSeparator());
        builder.append("- allocationTransactionReady=").append(report.allocationTransactionReadyCount()).append(System.lineSeparator());
        builder.append("- allocationTransactionBlocked=").append(report.allocationTransactionBlockedCount()).append(System.lineSeparator());
        builder.append("- transactionReady=").append(report.transactionReadyCount()).append(System.lineSeparator());
        builder.append("- transactionBlocked=").append(report.transactionBlockedCount()).append(System.lineSeparator());
        builder.append("- entries=").append(report.entryCount()).append(System.lineSeparator());
        builder.append("- descriptorWrites=").append(report.descriptorWriteCount()).append(System.lineSeparator());
        builder.append("- resourceDescriptorWrites=").append(report.resourceDescriptorWriteCount()).append(System.lineSeparator());
        builder.append("- textureDescriptorWrites=").append(report.textureDescriptorWriteCount()).append(System.lineSeparator());
        builder.append("- resourceFieldWrites=").append(report.resourceFieldWriteCount()).append(System.lineSeparator());
        builder.append("- textureFieldWrites=").append(report.textureFieldWriteCount()).append(System.lineSeparator());
        builder.append("- fieldWrites=").append(report.fieldWriteCount()).append(System.lineSeparator());
        builder.append("- ownersPresent=").append(report.ownerPresentCount()).append(System.lineSeparator());
        builder.append("- ownersActive=").append(report.ownerActiveCount()).append(System.lineSeparator());
        builder.append("- nativeAddressesPresent=").append(report.nativeAddressPresentCount()).append(System.lineSeparator());
        builder.append("- nativeWriteEnabledCount=").append(report.nativeWriteEnabledCount()).append(System.lineSeparator());
        builder.append("- sdkStructByteEncodingEnabledCount=").append(report.sdkStructByteEncodingEnabledCount()).append(System.lineSeparator());
        builder.append("- activeNativeDescriptors=").append(report.activeNativeDescriptorCount()).append(System.lineSeparator());
        builder.append("- writeTransactionApplyEnabled=false").append(System.lineSeparator());
        builder.append("- nativeMemoryAllocationEnabled=false").append(System.lineSeparator());
        builder.append("- sdkStructByteEncodingEnabled=false").append(System.lineSeparator());
        builder.append("- objectCreationEnabled=false").append(System.lineSeparator());
        builder.append("- runtimeBindingEnabled=false").append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        for (CudaImageSamplerNativeDescriptorEncodingTransactionPlanReport.Case testCase : report.cases()) {
            builder.append("- case.")
                    .append(testCase.key())
                    .append("=case:")
                    .append(testCase.status())
                    .append(",transaction:")
                    .append(testCase.plan().status())
                    .append(",firstBlocker:")
                    .append(testCase.plan().firstBlocker())
                    .append(System.lineSeparator());
        }
        builder.append("- rule=native descriptor field writes are mapped to owner slots only; no native memory is written")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
