package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Optional CUDA bridge that submits an argument-bound kernel launch.
 */
public interface CudaKernelLauncherBridge {

    String launcherId();

    default String launcherVersion() {
        return "1";
    }

    default int launcherOrder() {
        return 1000;
    }

    boolean supports(CudaKernelLaunchRequest request);

    CudaKernelLaunchResult launch(CudaKernelLaunchRequest request);
}
