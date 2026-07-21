package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the hardware-free CUDA image/sampler ABI plan.
 */
public final class CudaImageSamplerAbiPlanCli {
    private CudaImageSamplerAbiPlanCli() {
    }

    public static void main(String[] args) {
        CudaImageSamplerAbiPlanReport report = CudaImageSamplerAbiPlanReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaImageSamplerAbiPlanReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler ABI plan:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- entryReady=")
                .append(report.entryReadyCount())
                .append('/')
                .append(report.entries().size())
                .append(System.lineSeparator());
        builder.append("- entryBlocked=").append(report.entryBlockedCount()).append(System.lineSeparator());
        builder.append("- implementationStatus=planned").append(System.lineSeparator());
        builder.append("- productionSupportEnabled=false").append(System.lineSeparator());
        builder.append("- sourcePreviewEnabled=").append(report.sourcePreviewEnabledCount()).append(System.lineSeparator());
        builder.append("- sourcePreviewFolded=").append(report.sourcePreviewFoldedCount()).append(System.lineSeparator());
        builder.append("- sourcePreviewPending=").append(report.sourcePreviewPendingCount()).append(System.lineSeparator());
        builder.append("- sourcePreviewKernelParameterSlots=").append(report.sourcePreviewKernelParameterSlotCount()).append(System.lineSeparator());
        builder.append("- sourcePreviewMetadataSlots=").append(report.sourcePreviewMetadataSlotCount()).append(System.lineSeparator());
        builder.append("- runtimeBindingEnabled=").append(report.runtimeBindingEnabledCount()).append(System.lineSeparator());
        builder.append("- runtimeBindingKernelParameterSlots=").append(report.runtimeBindingKernelParameterSlotCount()).append(System.lineSeparator());
        builder.append("- plannedRuntimeKernelParameterSlots=").append(report.plannedRuntimeKernelParameterSlotCount()).append(System.lineSeparator());
        builder.append("- plannedRuntimeMetadataSlots=").append(report.plannedRuntimeMetadataSlotCount()).append(System.lineSeparator());
        builder.append("- failClosedContractStatus=").append(report.failClosedContract().status()).append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        for (CudaImageSamplerAbiPlanReport.Entry entry : report.entries()) {
            builder.append("- entry.")
                    .append(entry.key())
                    .append("=role:")
                    .append(entry.cudaAbiRole())
                    .append(",carrier:")
                    .append(entry.parameterCarrier())
                    .append(",resource:")
                    .append(entry.cudaResourceKind())
                    .append(",sourcePreview:")
                    .append(entry.sourcePreviewStatus())
                    .append(",sourceCarrier:")
                    .append(entry.sourcePreviewCarrier())
                    .append(",sourcePreviewSlots:")
                    .append(entry.sourcePreviewKernelParameterSlotCount())
                    .append(",sourceMetadataSlots:")
                    .append(entry.sourcePreviewMetadataSlotCount())
                    .append(",runtimeBinding:")
                    .append(entry.runtimeBindingStatus())
                    .append(",runtimeSlots:")
                    .append(entry.runtimeBindingKernelParameterSlotCount())
                    .append(",plannedRuntimeSlots:")
                    .append(entry.plannedRuntimeKernelParameterSlotCount())
                    .append(",plannedMetadataSlots:")
                    .append(entry.plannedRuntimeMetadataSlotCount())
                    .append(",status:")
                    .append(entry.implementationStatus())
                    .append(System.lineSeparator());
        }
        builder.append("- rule=ABI plan is metadata only; CUDA image/sampler binding remains fail-closed")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
