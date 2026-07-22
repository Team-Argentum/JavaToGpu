package net.sixik.ga_utils.javatogpu.runtime.validation;

/**
 * Prints the metadata-only backend source/lowering selection contract.
 */
public final class GpuBackendSourceLoweringContractCli {
    private GpuBackendSourceLoweringContractCli() {
    }

    public static void main(String[] args) {
        GpuBackendSourceLoweringContractReport report = GpuBackendSourceLoweringContractReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(GpuBackendSourceLoweringContractReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("Backend source/lowering contract:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- entryCount=").append(report.entries().size()).append(System.lineSeparator());
        builder.append("- readyEntries=")
                .append(report.readyEntryCount())
                .append('/')
                .append(report.entries().size())
                .append(System.lineSeparator());
        builder.append("- plannedUnsupported=")
                .append(report.plannedUnsupportedCount())
                .append('/')
                .append(report.plannedEntryCount())
                .append(System.lineSeparator());
        builder.append("- previewLowering=")
                .append(report.previewLoweringCount())
                .append(System.lineSeparator());
        for (GpuBackendSourceLoweringContractReport.Entry entry : report.entries()) {
            builder.append("- entry.")
                    .append(entry.backendTarget())
                    .append("=status:")
                    .append(entry.status())
                    .append(",stage:")
                    .append(entry.loweringResult().stageResult().status())
                    .append(",lowered:")
                    .append(entry.loweringResult().lowered())
                    .append(",selectedSource:")
                    .append(entry.sourceSelectionPlan().selectedSource())
                    .append(",moduleFormat:")
                    .append(entry.loweringResult().moduleArtifact().moduleFormat().key())
                    .append(System.lineSeparator());
        }
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        builder.append("- rule=metadata-only; source/lowering contracts are inspected without OpenCL/CUDA/Vulkan/Metal native sessions")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
