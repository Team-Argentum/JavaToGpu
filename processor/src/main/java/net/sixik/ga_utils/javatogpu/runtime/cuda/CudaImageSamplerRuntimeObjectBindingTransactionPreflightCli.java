package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the hardware-free CUDA image/sampler runtime object-binding transaction preflight contract.
 */
public final class CudaImageSamplerRuntimeObjectBindingTransactionPreflightCli {
    private CudaImageSamplerRuntimeObjectBindingTransactionPreflightCli() {
    }

    public static void main(String[] args) {
        CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport report =
                CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler runtime object binding transaction preflight:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- caseReady=")
                .append(report.caseReadyCount())
                .append('/')
                .append(report.cases().size())
                .append(System.lineSeparator());
        builder.append("- caseBlocked=").append(report.caseBlockedCount()).append(System.lineSeparator());
        builder.append("- planReady=").append(report.planReadyCount()).append(System.lineSeparator());
        builder.append("- planBlocked=").append(report.planBlockedCount()).append(System.lineSeparator());
        builder.append("- preflightReady=").append(report.preflightReadyCount()).append(System.lineSeparator());
        builder.append("- preflightBlocked=").append(report.preflightBlockedCount()).append(System.lineSeparator());
        builder.append("- entries=").append(report.entryCount()).append(System.lineSeparator());
        builder.append("- objectBindingTransactions=").append(report.objectBindingTransactionCount()).append(System.lineSeparator());
        builder.append("- textureObjectTransactions=").append(report.textureObjectTransactionCount()).append(System.lineSeparator());
        builder.append("- surfaceObjectTransactions=").append(report.surfaceObjectTransactionCount()).append(System.lineSeparator());
        builder.append("- foldedSamplers=").append(report.foldedSamplerTransactionCount()).append(System.lineSeparator());
        builder.append("- plannedObjectKernelParameterSlots=")
                .append(report.plannedObjectKernelParameterSlotCount())
                .append(System.lineSeparator());
        builder.append("- plannedMetadataKernelParameterSlots=")
                .append(report.plannedMetadataKernelParameterSlotCount())
                .append(System.lineSeparator());
        builder.append("- plannedKernelParameterSlots=")
                .append(report.plannedKernelParameterSlotCount())
                .append(System.lineSeparator());
        builder.append("- objectHandlesRequired=").append(report.objectHandleRequiredCount()).append(System.lineSeparator());
        builder.append("- objectHandlesAvailable=").append(report.objectHandleAvailableCount()).append(System.lineSeparator());
        builder.append("- nativeDescriptorsAvailable=").append(report.nativeDescriptorAvailableCount()).append(System.lineSeparator());
        builder.append("- resourceDescriptorsRequired=").append(report.resourceDescriptorRequiredCount()).append(System.lineSeparator());
        builder.append("- resourceDescriptorOwnersPresent=").append(report.resourceDescriptorOwnerPresentCount()).append(System.lineSeparator());
        builder.append("- resourceDescriptorNativeAddressesPresent=").append(report.resourceDescriptorNativeAddressPresentCount()).append(System.lineSeparator());
        builder.append("- resourceDescriptorWritesPlanned=").append(report.resourceDescriptorWritePlannedCount()).append(System.lineSeparator());
        builder.append("- resourceDescriptorNativeWritesEnabled=").append(report.resourceDescriptorNativeWriteEnabledCount()).append(System.lineSeparator());
        builder.append("- textureDescriptorsRequired=").append(report.textureDescriptorRequiredCount()).append(System.lineSeparator());
        builder.append("- textureDescriptorOwnersPresent=").append(report.textureDescriptorOwnerPresentCount()).append(System.lineSeparator());
        builder.append("- textureDescriptorNativeAddressesPresent=").append(report.textureDescriptorNativeAddressPresentCount()).append(System.lineSeparator());
        builder.append("- textureDescriptorWritesPlanned=").append(report.textureDescriptorWritePlannedCount()).append(System.lineSeparator());
        builder.append("- textureDescriptorNativeWritesEnabled=").append(report.textureDescriptorNativeWriteEnabledCount()).append(System.lineSeparator());
        builder.append("- transactionApplyEnabledCount=").append(report.transactionApplyEnabledCount()).append(System.lineSeparator());
        builder.append("- kernelParameterWriteEnabledCount=").append(report.kernelParameterWriteEnabledCount()).append(System.lineSeparator());
        builder.append("- activeObjects=").append(report.activeObjectCount()).append(System.lineSeparator());
        builder.append("- nativeDescriptorAllocationEnabled=false").append(System.lineSeparator());
        builder.append("- objectCreationCallEnabled=false").append(System.lineSeparator());
        builder.append("- transactionApplyEnabled=false").append(System.lineSeparator());
        builder.append("- kernelParameterWriteEnabled=false").append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        for (CudaImageSamplerRuntimeObjectBindingTransactionPreflightReport.Case testCase : report.cases()) {
            builder.append("- case.")
                    .append(testCase.key())
                    .append("=case:")
                    .append(testCase.status())
                    .append(",preflight:")
                    .append(testCase.preflight().status())
                    .append(",firstBlocker:")
                    .append(testCase.preflight().firstBlocker())
                    .append(System.lineSeparator());
        }
        builder.append("- rule=transaction preflight is blocked until native descriptors, object handles, and kernel writes exist")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
