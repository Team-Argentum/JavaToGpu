package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Optional CUDA module/function loader boundary.
 */
public interface CudaModuleLoaderBridge {

    String loaderId();

    default String loaderVersion() {
        return "1";
    }

    default int loaderOrder() {
        return 1000;
    }

    boolean supports(CudaModuleLoadRequest request);

    CudaModuleLoadResult load(CudaModuleLoadRequest request);
}
