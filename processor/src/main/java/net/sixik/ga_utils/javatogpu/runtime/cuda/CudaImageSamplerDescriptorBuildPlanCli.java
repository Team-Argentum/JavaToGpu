package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the hardware-free CUDA image/sampler descriptor build-plan contract.
 */
public final class CudaImageSamplerDescriptorBuildPlanCli {
    private CudaImageSamplerDescriptorBuildPlanCli() {
    }

    public static void main(String[] args) {
        CudaImageSamplerDescriptorBuildPlanReport report = CudaImageSamplerDescriptorBuildPlanReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaImageSamplerDescriptorBuildPlanReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler descriptor build plan:").append(System.lineSeparator());
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
        builder.append("- resourceDescriptorPayloads=").append(report.resourceDescriptorPayloadPlannedCount()).append(System.lineSeparator());
        builder.append("- textureDescriptorPayloads=").append(report.textureDescriptorPayloadPlannedCount()).append(System.lineSeparator());
        builder.append("- activeDescriptorPayloads=").append(report.activeDescriptorPayloadCount()).append(System.lineSeparator());
        builder.append("- activeNativeDescriptors=").append(report.activeNativeDescriptorCount()).append(System.lineSeparator());
        builder.append("- nativeDescriptorLayoutStatus=").append(report.nativeDescriptorLayoutStatus()).append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        for (CudaImageSamplerDescriptorBuildPlanReport.Case testCase : report.cases()) {
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
        builder.append("- rule=descriptor build planning validates Java image/sampler arguments without native allocation or object creation")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
