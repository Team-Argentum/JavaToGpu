package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the hardware-free CUDA image/sampler native descriptor allocation-transaction plan contract.
 */
public final class CudaImageSamplerNativeDescriptorAllocationTransactionPlanCli {
    private CudaImageSamplerNativeDescriptorAllocationTransactionPlanCli() {
    }

    public static void main(String[] args) {
        CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport report =
                CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler native descriptor allocation transaction plan:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- caseReady=")
                .append(report.caseReadyCount())
                .append('/')
                .append(report.cases().size())
                .append(System.lineSeparator());
        builder.append("- caseBlocked=").append(report.caseBlockedCount()).append(System.lineSeparator());
        builder.append("- preflightReady=").append(report.preflightReadyCount()).append(System.lineSeparator());
        builder.append("- preflightBlocked=").append(report.preflightBlockedCount()).append(System.lineSeparator());
        builder.append("- transactionReady=").append(report.transactionReadyCount()).append(System.lineSeparator());
        builder.append("- transactionBlocked=").append(report.transactionBlockedCount()).append(System.lineSeparator());
        builder.append("- entries=").append(report.entryCount()).append(System.lineSeparator());
        builder.append("- descriptorOwners=").append(report.descriptorOwnerCount()).append(System.lineSeparator());
        builder.append("- resourceDescriptorOwners=").append(report.resourceDescriptorOwnerCount()).append(System.lineSeparator());
        builder.append("- textureDescriptorOwners=").append(report.textureDescriptorOwnerCount()).append(System.lineSeparator());
        builder.append("- activeDescriptorOwners=").append(report.activeDescriptorOwnerCount()).append(System.lineSeparator());
        builder.append("- nativeAddressesPresent=").append(report.nativeAddressPresentCount()).append(System.lineSeparator());
        builder.append("- allocationEnabledCount=").append(report.allocationEnabledCount()).append(System.lineSeparator());
        builder.append("- cleanupPlanned=").append(report.cleanupPlannedCount()).append(System.lineSeparator());
        builder.append("- cleanupActive=").append(report.cleanupActiveCount()).append(System.lineSeparator());
        builder.append("- rollbackPlanned=").append(report.rollbackPlannedCount()).append(System.lineSeparator());
        builder.append("- rollbackActive=").append(report.rollbackActiveCount()).append(System.lineSeparator());
        builder.append("- activeNativeDescriptors=").append(report.activeNativeDescriptorCount()).append(System.lineSeparator());
        builder.append("- allocationApplyEnabled=false").append(System.lineSeparator());
        builder.append("- nativeMemoryAllocationEnabled=false").append(System.lineSeparator());
        builder.append("- sdkStructByteEncodingEnabled=false").append(System.lineSeparator());
        builder.append("- cleanupApplyEnabled=false").append(System.lineSeparator());
        builder.append("- rollbackApplyEnabled=false").append(System.lineSeparator());
        builder.append("- objectCreationEnabled=false").append(System.lineSeparator());
        builder.append("- runtimeBindingEnabled=false").append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        for (CudaImageSamplerNativeDescriptorAllocationTransactionPlanReport.Case testCase : report.cases()) {
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
        builder.append("- rule=native descriptor allocation owners and cleanup/rollback order are planned only")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
