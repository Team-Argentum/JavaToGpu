package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the hardware-free CUDA image/sampler object-creation boundary contract.
 */
public final class CudaImageSamplerObjectCreationContractCli {
    private CudaImageSamplerObjectCreationContractCli() {
    }

    public static void main(String[] args) {
        CudaImageSamplerObjectCreationContractReport report = CudaImageSamplerObjectCreationContractReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaImageSamplerObjectCreationContractReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA image/sampler object creation contract:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- entryReady=")
                .append(report.entryReadyCount())
                .append('/')
                .append(report.entries().size())
                .append(System.lineSeparator());
        builder.append("- entryBlocked=").append(report.entryBlockedCount()).append(System.lineSeparator());
        builder.append("- textureEntries=").append(report.textureEntryCount()).append(System.lineSeparator());
        builder.append("- surfaceEntries=").append(report.surfaceEntryCount()).append(System.lineSeparator());
        builder.append("- samplerEntries=").append(report.samplerEntryCount()).append(System.lineSeparator());
        builder.append("- objectCreationEnabled=").append(report.objectCreationEnabled()).append(System.lineSeparator());
        builder.append("- objectOwnershipBoundary=").append(report.objectOwnershipBoundaryStatus()).append(System.lineSeparator());
        builder.append("- activeObjectCount=").append(report.activeObjectCount()).append(System.lineSeparator());
        builder.append("- requiredDriverSymbols=").append(report.driverSymbols().size()).append(System.lineSeparator());
        builder.append("- resolvedDriverSymbols=").append(report.resolvedDriverSymbolCount()).append(System.lineSeparator());
        builder.append("- missingDriverSymbols=").append(report.missingDriverSymbolCount()).append(System.lineSeparator());
        builder.append("- objectDriverSymbols=").append(report.objectDriverSymbolCount()).append(System.lineSeparator());
        builder.append("- resolvedObjectDriverSymbols=").append(report.resolvedObjectDriverSymbolCount()).append(System.lineSeparator());
        builder.append("- resourceDriverSymbols=").append(report.resourceDriverSymbolCount()).append(System.lineSeparator());
        builder.append("- resolvedResourceDriverSymbols=").append(report.resolvedResourceDriverSymbolCount()).append(System.lineSeparator());
        builder.append("- abiPlanStatus=").append(report.abiPlan().status()).append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        for (CudaImageSamplerObjectCreationContractReport.DriverSymbol symbol : report.driverSymbols()) {
            builder.append("- symbol.")
                    .append(symbol.name())
                    .append("=")
                    .append(symbol.status())
                    .append(",kind:")
                    .append(symbol.kind())
                    .append(System.lineSeparator());
        }
        builder.append("- rule=object creation is metadata/preflight only; no CUDA texture or surface objects are created")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
