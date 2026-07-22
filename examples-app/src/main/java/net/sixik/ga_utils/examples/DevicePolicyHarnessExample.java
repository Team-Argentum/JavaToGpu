package net.sixik.ga_utils.examples;

import net.sixik.ga_utils.javatogpu.runtime.validation.GpuRuntimeDevicePolicyHarness;
import net.sixik.ga_utils.javatogpu.runtime.validation.GpuRuntimeDevicePolicyHarnessReport;

/**
 * Runnable example for hardware-free device policy checks.
 */
public final class DevicePolicyHarnessExample {

    private DevicePolicyHarnessExample() {
    }

    public static void main(String[] args) {
        System.out.println(renderDevicePolicyHarnessPreview());
    }

    static String renderDevicePolicyHarnessPreview() {
        GpuRuntimeDevicePolicyHarnessReport report = GpuRuntimeDevicePolicyHarness
                .loadWithBuiltIns()
                .runSyntheticOpenCl();

        StringBuilder builder = new StringBuilder();
        builder.append("Runtime device policy harness:").append(System.lineSeparator());
        builder.append("- status=").append(report.status()).append(System.lineSeparator());
        builder.append("- backend=").append(report.backendTarget()).append(System.lineSeparator());
        builder.append("- candidates=").append(report.candidates().size()).append(System.lineSeparator());
        builder.append("- policyCount=").append(report.selection().policyDecisions().size()).append(System.lineSeparator());
        builder.append("- executionCount=").append(report.selection().executionReports().size()).append(System.lineSeparator());
        builder.append("- selectedDevice=").append(report.selectedDeviceLabel()).append(System.lineSeparator());
        builder.append("- selectedDeviceKey=").append(report.selectedDeviceKey()).append(System.lineSeparator());
        builder.append("- firstBlocker=").append(report.selection().firstBlocker()).append(System.lineSeparator());
        builder.append("- allPoliciesSucceeded=").append(report.allPoliciesSucceeded()).append(System.lineSeparator());
        builder.append("- rule=synthetic candidates exercise policy ranking; no OpenCL/CUDA runtime is opened")
                .append(System.lineSeparator());
        return builder.toString();
    }
}
