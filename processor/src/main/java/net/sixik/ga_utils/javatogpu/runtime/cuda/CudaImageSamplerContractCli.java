package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the hardware-free CUDA image/sampler contract report.
 */
public final class CudaImageSamplerContractCli {
    private CudaImageSamplerContractCli() {
    }

    public static void main(String[] args) {
        CudaImageSamplerContractReport report = CudaImageSamplerContractReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaImageSamplerContractReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler contract:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- caseReady=")
                .append(report.readyCount())
                .append('/')
                .append(report.cases().size())
                .append(System.lineSeparator());
        builder.append("- caseBlocked=").append(report.blockedCount()).append(System.lineSeparator());
        builder.append("- productionSupportEnabled=false").append(System.lineSeparator());
        builder.append("- runtimeBindingPlanEntries=").append(report.runtimeBindingPlanEntryCount()).append(System.lineSeparator());
        builder.append("- runtimeBindingPlanSourcePreviewSlots=").append(report.sourcePreviewKernelParameterSlotCount()).append(System.lineSeparator());
        builder.append("- runtimeBindingPlanPlannedSlots=").append(report.runtimeBindingPlannedKernelParameterSlotCount()).append(System.lineSeparator());
        builder.append("- runtimeBindingPlanActiveSlots=").append(report.runtimeBindingKernelParameterSlotCount()).append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        for (CudaImageSamplerContractReport.Case testCase : report.cases()) {
            builder.append("- case.")
                    .append(testCase.key())
                    .append('=')
                    .append(testCase.status())
                    .append(",actual=")
                    .append(testCase.bindingResult() == null ? "missing" : testCase.bindingResult().status())
                    .append(",firstBlocker=")
                    .append(testCase.firstBlocker())
                    .append(System.lineSeparator());
        }
        builder.append("- rule=CUDA image/sampler arguments are recognized but fail-closed until a CUDA ABI is implemented")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
