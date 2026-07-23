package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the hardware-free CUDA image/sampler Java descriptor payload model contract.
 */
public final class CudaImageSamplerDescriptorPayloadModelCli {
    private CudaImageSamplerDescriptorPayloadModelCli() {
    }

    public static void main(String[] args) {
        CudaImageSamplerDescriptorPayloadModelReport report = CudaImageSamplerDescriptorPayloadModelReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaImageSamplerDescriptorPayloadModelReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler descriptor payload model:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- caseReady=")
                .append(report.caseReadyCount())
                .append('/')
                .append(report.cases().size())
                .append(System.lineSeparator());
        builder.append("- caseBlocked=").append(report.caseBlockedCount()).append(System.lineSeparator());
        builder.append("- modelReady=").append(report.modelReadyCount()).append(System.lineSeparator());
        builder.append("- modelBlocked=").append(report.modelBlockedCount()).append(System.lineSeparator());
        builder.append("- entries=").append(report.entryCount()).append(System.lineSeparator());
        builder.append("- resourcePayloads=").append(report.resourcePayloadBuiltCount()).append(System.lineSeparator());
        builder.append("- texturePayloads=").append(report.texturePayloadBuiltCount()).append(System.lineSeparator());
        builder.append("- samplerPayloads=").append(report.samplerPayloadBuiltCount()).append(System.lineSeparator());
        builder.append("- activeNativeDescriptors=").append(report.activeNativeDescriptorCount()).append(System.lineSeparator());
        builder.append("- nativeDescriptorAllocationEnabled=false").append(System.lineSeparator());
        builder.append("- objectCreationEnabled=false").append(System.lineSeparator());
        builder.append("- runtimeBindingEnabled=false").append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        for (CudaImageSamplerDescriptorPayloadModelReport.Case testCase : report.cases()) {
            builder.append("- case.")
                    .append(testCase.key())
                    .append("=case:")
                    .append(testCase.status())
                    .append(",model:")
                    .append(testCase.model().status())
                    .append(",firstBlocker:")
                    .append(testCase.model().firstBlocker())
                    .append(System.lineSeparator());
        }
        builder.append("- rule=Java descriptor payloads are modeled only; native descriptor allocation and object creation stay disabled")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
