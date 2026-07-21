package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the hardware-free CUDA image/sampler runtime object-binding plan contract.
 */
public final class CudaImageSamplerRuntimeObjectBindingPlanCli {
    private CudaImageSamplerRuntimeObjectBindingPlanCli() {
    }

    public static void main(String[] args) {
        CudaImageSamplerRuntimeObjectBindingPlanReport report = CudaImageSamplerRuntimeObjectBindingPlanReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaImageSamplerRuntimeObjectBindingPlanReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler runtime object binding plan:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- caseReady=")
                .append(report.caseReadyCount())
                .append('/')
                .append(report.cases().size())
                .append(System.lineSeparator());
        builder.append("- caseBlocked=").append(report.caseBlockedCount()).append(System.lineSeparator());
        builder.append("- planReady=").append(report.planReadyCount()).append(System.lineSeparator());
        builder.append("- planBlocked=").append(report.planBlockedCount()).append(System.lineSeparator());
        builder.append("- entries=").append(report.entryCount()).append(System.lineSeparator());
        builder.append("- objectBindings=").append(report.objectBindingCount()).append(System.lineSeparator());
        builder.append("- textureObjectBindings=").append(report.textureObjectBindingCount()).append(System.lineSeparator());
        builder.append("- surfaceObjectBindings=").append(report.surfaceObjectBindingCount()).append(System.lineSeparator());
        builder.append("- foldedSamplers=").append(report.foldedSamplerBindingCount()).append(System.lineSeparator());
        builder.append("- plannedObjectKernelParameterSlots=")
                .append(report.plannedObjectKernelParameterSlotCount())
                .append(System.lineSeparator());
        builder.append("- plannedMetadataKernelParameterSlots=")
                .append(report.plannedMetadataKernelParameterSlotCount())
                .append(System.lineSeparator());
        builder.append("- plannedKernelParameterSlots=")
                .append(report.plannedKernelParameterSlotCount())
                .append(System.lineSeparator());
        builder.append("- runtimeBindingKernelParameterSlots=")
                .append(report.runtimeBindingKernelParameterSlotCount())
                .append(System.lineSeparator());
        builder.append("- objectCreationCallEnabledCount=").append(report.objectCreationCallEnabledCount()).append(System.lineSeparator());
        builder.append("- activeObjects=").append(report.activeObjectCount()).append(System.lineSeparator());
        builder.append("- objectCreationCallEnabled=false").append(System.lineSeparator());
        builder.append("- runtimeBindingEnabled=false").append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        for (CudaImageSamplerRuntimeObjectBindingPlanReport.Case testCase : report.cases()) {
            builder.append("- case.")
                    .append(testCase.key())
                    .append("=case:")
                    .append(testCase.status())
                    .append(",plan:")
                    .append(testCase.plan().status())
                    .append(",firstBlocker:")
                    .append(testCase.plan().firstBlocker())
                    .append(System.lineSeparator());
        }
        builder.append("- rule=runtime object bindings are planned only; no texture/surface object handles are bound")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
