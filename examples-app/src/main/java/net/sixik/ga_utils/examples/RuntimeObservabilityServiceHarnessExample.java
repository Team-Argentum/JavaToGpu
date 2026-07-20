package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeObservabilityServiceHarness;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeObservabilityServiceHarnessReport;

/**
 * Runnable example for lifecycle/log ServiceLoader observability checks.
 */
public final class RuntimeObservabilityServiceHarnessExample {

    private RuntimeObservabilityServiceHarnessExample() {
    }

    public static void main(String[] args) {
        System.out.println(renderServiceHarnessPreview());
    }

    static String renderServiceHarnessPreview() {
        GpuRuntimeObservabilityServiceHarnessReport report = GpuRuntimeObservabilityServiceHarness
                .loadFromServiceLoader()
                .runSyntheticOpenCl();

        StringBuilder builder = new StringBuilder();
        builder.append("Runtime observability service harness:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- backend=").append(report.backendTarget()).append(System.lineSeparator());
        builder.append("- lifecycleListeners=").append(report.lifecycleListenerCount()).append(System.lineSeparator());
        builder.append("- logServices=").append(report.logServiceCount()).append(System.lineSeparator());
        builder.append("- lifecycleAllSucceeded=")
                .append(report.lifecycleReport().allListenersSucceeded())
                .append(System.lineSeparator());
        builder.append("- logAllSucceeded=")
                .append(report.logReport().allServicesSucceeded())
                .append(System.lineSeparator());
        builder.append("- event=").append(report.lifecycleReport().event().kind()).append(System.lineSeparator());
        builder.append("- logLevel=").append(report.logReport().record().level()).append(System.lineSeparator());
        builder.append("- rule=ServiceLoader services are tested with synthetic records; no OpenCL/CUDA runtime is opened")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
