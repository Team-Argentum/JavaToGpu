package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.runtime.cuda.CudaExecutionReadinessCli;
import net.sixik.ga_utils.javatogpu.runtime.cuda.CudaExecutionReadinessReport;
import net.sixik.ga_utils.javatogpu.runtime.cuda.CudaInventoryContractCli;
import net.sixik.ga_utils.javatogpu.runtime.cuda.CudaInventoryContractReport;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceLoweringContractCli;
import net.sixik.ga_utils.javatogpu.runtime.GpuBackendSourceLoweringContractReport;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClBackendSpiContractCli;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClBackendSpiContractReport;

/**
 * Hardware-free dashboard for backend SPI readiness before native backend work.
 */
public final class BackendContractReadinessExample {

    private BackendContractReadinessExample() {
    }

    public static void main(String[] args) {
        System.out.println(renderBackendContractReadiness());
    }

    static String renderBackendContractReadiness() {
        OpenClBackendSpiContractReport openCl = OpenClBackendSpiContractReport.inspectBuiltInProvider();
        GpuBackendSourceLoweringContractReport sourceLowering = GpuBackendSourceLoweringContractReport.inspectBuiltIns();
        CudaInventoryContractReport cudaInventory = CudaInventoryContractReport.inspectBuiltInProvider();
        CudaExecutionReadinessReport cudaExecution = CudaExecutionReadinessReport.inspectBuiltIns();
        StringBuilder builder = new StringBuilder();
        builder.append("Backend contract readiness dashboard:").append(System.lineSeparator());
        builder.append("- rule=metadata-only; no OpenCL platform, CUDA runtime, nvidia-smi, program, or kernel is opened")
                .append(System.lineSeparator())
                .append(System.lineSeparator());
        builder.append(OpenClBackendSpiContractCli.render(openCl)).append(System.lineSeparator());
        builder.append(GpuBackendSourceLoweringContractCli.render(sourceLowering)).append(System.lineSeparator());
        builder.append(CudaInventoryContractCli.render(cudaInventory)).append(System.lineSeparator());
        builder.append(CudaExecutionReadinessCli.render(cudaExecution));
        return builder.toString();
    }
}
