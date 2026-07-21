package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the hardware-free CUDA image/sampler native descriptor encoding-plan contract.
 */
public final class CudaImageSamplerNativeDescriptorEncodingPlanCli {
    private CudaImageSamplerNativeDescriptorEncodingPlanCli() {
    }

    public static void main(String[] args) {
        CudaImageSamplerNativeDescriptorEncodingPlanReport report = CudaImageSamplerNativeDescriptorEncodingPlanReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaImageSamplerNativeDescriptorEncodingPlanReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler native descriptor encoding plan:").append(System.lineSeparator());
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
        builder.append("- resourceFieldWrites=").append(report.resourceFieldWriteCount()).append(System.lineSeparator());
        builder.append("- textureFieldWrites=").append(report.textureFieldWriteCount()).append(System.lineSeparator());
        builder.append("- fieldWrites=").append(report.fieldWriteCount()).append(System.lineSeparator());
        builder.append("- samplerTextureFieldWrites=").append(report.samplerTextureFieldWriteCount()).append(System.lineSeparator());
        builder.append("- nativeWriteEnabledCount=").append(report.nativeWriteEnabledCount()).append(System.lineSeparator());
        builder.append("- sdkStructByteEncodingEnabledCount=").append(report.sdkStructByteEncodingEnabledCount()).append(System.lineSeparator());
        builder.append("- activeNativeDescriptors=").append(report.activeNativeDescriptorCount()).append(System.lineSeparator());
        builder.append("- nativeDescriptorMemoryAllocationEnabled=false").append(System.lineSeparator());
        builder.append("- objectCreationEnabled=false").append(System.lineSeparator());
        builder.append("- runtimeBindingEnabled=false").append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        for (CudaImageSamplerNativeDescriptorEncodingPlanReport.Case testCase : report.cases()) {
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
        builder.append("- rule=native descriptor field encoding is planned only; no native memory or SDK struct bytes are written")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
