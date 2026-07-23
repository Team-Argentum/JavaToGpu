package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the hardware-free CUDA image/sampler native descriptor allocation-preflight contract.
 */
public final class CudaImageSamplerNativeDescriptorAllocationPreflightCli {
    private CudaImageSamplerNativeDescriptorAllocationPreflightCli() {
    }

    public static void main(String[] args) {
        CudaImageSamplerNativeDescriptorAllocationPreflightReport report =
                CudaImageSamplerNativeDescriptorAllocationPreflightReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaImageSamplerNativeDescriptorAllocationPreflightReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler native descriptor allocation preflight:").append(System.lineSeparator());
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
        builder.append("- resourceDescriptorAllocations=").append(report.resourceDescriptorAllocationCount()).append(System.lineSeparator());
        builder.append("- resourceDescriptorsAllocated=").append(report.resourceDescriptorAllocatedCount()).append(System.lineSeparator());
        builder.append("- textureDescriptorAllocations=").append(report.textureDescriptorAllocationCount()).append(System.lineSeparator());
        builder.append("- textureDescriptorsAllocated=").append(report.textureDescriptorAllocatedCount()).append(System.lineSeparator());
        builder.append("- plannedNativeDescriptors=").append(report.plannedNativeDescriptorCount()).append(System.lineSeparator());
        builder.append("- allocatedNativeDescriptors=").append(report.allocatedNativeDescriptorCount()).append(System.lineSeparator());
        builder.append("- nativeDescriptorOwnershipPlanned=").append(report.nativeDescriptorOwnershipPlannedCount()).append(System.lineSeparator());
        builder.append("- nativeDescriptorOwnershipActive=").append(report.nativeDescriptorOwnershipActiveCount()).append(System.lineSeparator());
        builder.append("- cleanupPlanned=").append(report.cleanupPlannedCount()).append(System.lineSeparator());
        builder.append("- cleanupActive=").append(report.cleanupActiveCount()).append(System.lineSeparator());
        builder.append("- rollbackPlanned=").append(report.rollbackPlannedCount()).append(System.lineSeparator());
        builder.append("- rollbackActive=").append(report.rollbackActiveCount()).append(System.lineSeparator());
        builder.append("- allocationEnabledCount=").append(report.allocationEnabledCount()).append(System.lineSeparator());
        builder.append("- sdkStructByteEncodingEnabledCount=").append(report.sdkStructByteEncodingEnabledCount()).append(System.lineSeparator());
        builder.append("- activeNativeDescriptors=").append(report.activeNativeDescriptorCount()).append(System.lineSeparator());
        builder.append("- nativeDescriptorAllocationEnabled=false").append(System.lineSeparator());
        builder.append("- sdkStructByteEncodingEnabled=false").append(System.lineSeparator());
        builder.append("- objectCreationEnabled=false").append(System.lineSeparator());
        builder.append("- runtimeBindingEnabled=false").append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        for (CudaImageSamplerNativeDescriptorAllocationPreflightReport.Case testCase : report.cases()) {
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
        builder.append("- rule=native descriptor allocation, ownership, cleanup, and rollback are planned only")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
