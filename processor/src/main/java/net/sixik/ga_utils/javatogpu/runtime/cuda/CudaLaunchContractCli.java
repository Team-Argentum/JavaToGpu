package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Prints the hardware-free CUDA launch contract report.
 */
public final class CudaLaunchContractCli {
    private CudaLaunchContractCli() {
    }

    public static void main(String[] args) {
        CudaLaunchContractReport report = CudaLaunchContractReport.inspectBuiltIns();
        System.out.println(render(report));
        if (!report.ready()) {
            System.exit(1);
        }
    }

    public static String render(CudaLaunchContractReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("CUDA launch contract:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- caseReady=")
                .append(report.readyCount())
                .append('/')
                .append(report.cases().size())
                .append(System.lineSeparator());
        builder.append("- caseBlocked=").append(report.blockedCount()).append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.firstBlocker()).append(System.lineSeparator());
        for (CudaLaunchContractReport.Case testCase : report.cases()) {
            builder.append("- case.")
                    .append(testCase.key())
                    .append('=')
                    .append(testCase.status())
                    .append(",expected=")
                    .append(testCase.expectedStatus())
                    .append(",actual=")
                    .append(testCase.result() == null ? "missing" : testCase.result().status())
                    .append(",firstBlocker=")
                    .append(testCase.firstBlocker())
                    .append(System.lineSeparator());
        }
        builder.append("- rule=CUDA Driver API launch shapes are validated without opening native CUDA state")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
