package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the hardware-free CUDA image/sampler native object-preparation preflight contract.
 */
public final class CudaImageSamplerNativeObjectPreparationPreflightCli {
    private CudaImageSamplerNativeObjectPreparationPreflightCli() {
    }

    public static void main(String[] args) {
        CudaImageSamplerNativeObjectPreparationPreflightReport report =
                CudaImageSamplerNativeObjectPreparationPreflightReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaImageSamplerNativeObjectPreparationPreflightReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler native object preparation preflight:").append(System.lineSeparator());
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
        builder.append("- objectPreparations=").append(report.objectPreparationCount()).append(System.lineSeparator());
        builder.append("- textureObjectPreparations=").append(report.textureObjectPreparationCount()).append(System.lineSeparator());
        builder.append("- surfaceObjectPreparations=").append(report.surfaceObjectPreparationCount()).append(System.lineSeparator());
        builder.append("- foldedSamplers=").append(report.foldedSamplerPreparationCount()).append(System.lineSeparator());
        builder.append("- resourceDescriptorsRequired=").append(report.resourceDescriptorRequiredCount()).append(System.lineSeparator());
        builder.append("- resourceDescriptorsAvailable=").append(report.resourceDescriptorAvailableCount()).append(System.lineSeparator());
        builder.append("- resourceDescriptorOwnersPresent=").append(report.resourceDescriptorOwnerPresentCount()).append(System.lineSeparator());
        builder.append("- resourceDescriptorNativeAddressesPresent=").append(report.resourceDescriptorNativeAddressPresentCount()).append(System.lineSeparator());
        builder.append("- resourceDescriptorWritesPlanned=").append(report.resourceDescriptorWritePlannedCount()).append(System.lineSeparator());
        builder.append("- resourceDescriptorNativeWritesEnabled=").append(report.resourceDescriptorNativeWriteEnabledCount()).append(System.lineSeparator());
        builder.append("- textureDescriptorsRequired=").append(report.textureDescriptorRequiredCount()).append(System.lineSeparator());
        builder.append("- textureDescriptorsAvailable=").append(report.textureDescriptorAvailableCount()).append(System.lineSeparator());
        builder.append("- textureDescriptorOwnersPresent=").append(report.textureDescriptorOwnerPresentCount()).append(System.lineSeparator());
        builder.append("- textureDescriptorNativeAddressesPresent=").append(report.textureDescriptorNativeAddressPresentCount()).append(System.lineSeparator());
        builder.append("- textureDescriptorWritesPlanned=").append(report.textureDescriptorWritePlannedCount()).append(System.lineSeparator());
        builder.append("- textureDescriptorNativeWritesEnabled=").append(report.textureDescriptorNativeWriteEnabledCount()).append(System.lineSeparator());
        builder.append("- createFunctionsRequired=").append(report.createFunctionRequiredCount()).append(System.lineSeparator());
        builder.append("- createFunctionsAvailable=").append(report.createFunctionAvailableCount()).append(System.lineSeparator());
        builder.append("- destroyFunctionsRequired=").append(report.destroyFunctionRequiredCount()).append(System.lineSeparator());
        builder.append("- destroyFunctionsAvailable=").append(report.destroyFunctionAvailableCount()).append(System.lineSeparator());
        builder.append("- objectHandlesRequired=").append(report.objectHandleRequiredCount()).append(System.lineSeparator());
        builder.append("- objectHandlesAvailable=").append(report.objectHandleAvailableCount()).append(System.lineSeparator());
        builder.append("- objectOwnershipAvailable=").append(report.objectOwnershipAvailableCount()).append(System.lineSeparator());
        builder.append("- objectCreationCallEnabledCount=").append(report.objectCreationCallEnabledCount()).append(System.lineSeparator());
        builder.append("- activeNativeDescriptors=").append(report.activeNativeDescriptorCount()).append(System.lineSeparator());
        builder.append("- activeObjects=").append(report.activeObjectCount()).append(System.lineSeparator());
        builder.append("- nativeDescriptorAllocationEnabled=false").append(System.lineSeparator());
        builder.append("- objectCreationCallEnabled=false").append(System.lineSeparator());
        builder.append("- objectOwnershipEnabled=false").append(System.lineSeparator());
        builder.append("- runtimeBindingEnabled=false").append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        for (CudaImageSamplerNativeObjectPreparationPreflightReport.Case testCase : report.cases()) {
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
        builder.append("- rule=native object preparation is blocked until descriptor allocation and object creation exist")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
