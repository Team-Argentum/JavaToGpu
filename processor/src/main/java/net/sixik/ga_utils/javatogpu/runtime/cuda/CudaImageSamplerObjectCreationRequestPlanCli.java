package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the hardware-free CUDA image/sampler object-creation request-plan contract.
 */
public final class CudaImageSamplerObjectCreationRequestPlanCli {
    private CudaImageSamplerObjectCreationRequestPlanCli() {
    }

    public static void main(String[] args) {
        CudaImageSamplerObjectCreationRequestPlanReport report = CudaImageSamplerObjectCreationRequestPlanReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaImageSamplerObjectCreationRequestPlanReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler object creation request plan:").append(System.lineSeparator());
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
        builder.append("- objectRequests=").append(report.objectCreationRequestCount()).append(System.lineSeparator());
        builder.append("- textureObjectRequests=").append(report.textureObjectRequestCount()).append(System.lineSeparator());
        builder.append("- surfaceObjectRequests=").append(report.surfaceObjectRequestCount()).append(System.lineSeparator());
        builder.append("- foldedSamplers=").append(report.foldedSamplerCount()).append(System.lineSeparator());
        builder.append("- objectCreationCallEnabledCount=").append(report.objectCreationCallEnabledCount()).append(System.lineSeparator());
        builder.append("- activeObjects=").append(report.activeObjectCount()).append(System.lineSeparator());
        builder.append("- objectCreationCallEnabled=false").append(System.lineSeparator());
        builder.append("- runtimeBindingEnabled=false").append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        for (CudaImageSamplerObjectCreationRequestPlanReport.Case testCase : report.cases()) {
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
        builder.append("- rule=object creation requests are planned only; cuTexObjectCreate/cuSurfObjectCreate are not called")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
