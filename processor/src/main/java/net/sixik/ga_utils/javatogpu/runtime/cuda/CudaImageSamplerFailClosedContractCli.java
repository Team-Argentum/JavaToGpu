package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the top-level hardware-free CUDA image/sampler fail-closed contract.
 */
public final class CudaImageSamplerFailClosedContractCli {
    private CudaImageSamplerFailClosedContractCli() {
    }

    public static void main(String[] args) {
        CudaImageSamplerFailClosedContractReport report = CudaImageSamplerFailClosedContractReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaImageSamplerFailClosedContractReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler fail-closed contract:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- componentReady=")
                .append(report.componentReadyCount())
                .append('/')
                .append(report.components().size())
                .append(System.lineSeparator());
        builder.append("- componentBlocked=").append(report.componentBlockedCount()).append(System.lineSeparator());
        builder.append("- plannedNativeDescriptors=").append(report.plannedNativeDescriptorCount()).append(System.lineSeparator());
        builder.append("- plannedObjectRequests=").append(report.plannedObjectRequestCount()).append(System.lineSeparator());
        builder.append("- plannedRuntimeKernelParameterSlots=")
                .append(report.plannedRuntimeKernelParameterSlotCount())
                .append(System.lineSeparator());
        builder.append("- nativeMutationCount=").append(report.nativeMutationCount()).append(System.lineSeparator());
        builder.append("- runtimeBindingKernelParameterSlots=")
                .append(report.runtimeBindingKernelParameterSlotCount())
                .append(System.lineSeparator());
        builder.append("- objectCreationCallEnabledCount=")
                .append(report.objectCreationCallEnabledCount())
                .append(System.lineSeparator());
        builder.append("- nativeDescriptorsAvailable=").append(report.nativeDescriptorAvailableCount()).append(System.lineSeparator());
        builder.append("- nativeDescriptorAddressesPresent=")
                .append(report.nativeDescriptorAddressPresentCount())
                .append(System.lineSeparator());
        builder.append("- nativeDescriptorWritesEnabled=")
                .append(report.nativeDescriptorWriteEnabledCount())
                .append(System.lineSeparator());
        builder.append("- objectHandlesAvailable=").append(report.objectHandleAvailableCount()).append(System.lineSeparator());
        builder.append("- activeNativeDescriptors=").append(report.activeNativeDescriptorCount()).append(System.lineSeparator());
        builder.append("- activeObjects=").append(report.activeObjectCount()).append(System.lineSeparator());
        builder.append("- nativeDescriptorAllocationEnabled=false").append(System.lineSeparator());
        builder.append("- nativeDescriptorWriteEnabled=false").append(System.lineSeparator());
        builder.append("- objectCreationEnabled=false").append(System.lineSeparator());
        builder.append("- objectBindingEnabled=false").append(System.lineSeparator());
        builder.append("- kernelParameterWriteEnabled=false").append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        for (CudaImageSamplerFailClosedContractReport.Component component : report.components()) {
            builder.append("- component.")
                    .append(component.key())
                    .append("=")
                    .append(component.status())
                    .append(",firstBlocker:")
                    .append(component.firstBlocker())
                    .append(System.lineSeparator());
        }
        builder.append("- rule=image/sampler native descriptor allocation, descriptor writes, object creation, object binding, and kernel writes remain disabled")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
