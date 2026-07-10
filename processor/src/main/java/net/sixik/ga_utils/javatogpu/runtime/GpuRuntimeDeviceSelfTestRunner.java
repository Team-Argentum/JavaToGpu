package net.sixik.ga_utils.javatogpu.runtime;

/**
 * Backend hook that executes a bounded correctness smoke on one concrete runtime device.
 */
@FunctionalInterface
public interface GpuRuntimeDeviceSelfTestRunner {

    GpuRuntimeDeviceSelfTestResult run(GpuRuntimeDeviceSelfTestRequest request);

    default boolean supports(GpuRuntimeDeviceProfile profile) {
        return true;
    }

    default String runnerId() {
        return getClass().getName();
    }

    default String runnerVersion() {
        return "1";
    }
}
