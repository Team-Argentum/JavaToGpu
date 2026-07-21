package net.sixik.ga_utils.javatogpu.runtime.cuda;

import net.sixik.ga_utils.javatogpu.runtime.GpuBackendCompileOptions;

/**
 * Built-in CUDA Driver API module-loader bridge entry point.
 */
final class CudaDriverModuleLoaderBridge implements CudaModuleLoaderBridge {

    @Override
    public String loaderId() {
        return "cuda-module-loader:driver";
    }

    @Override
    public int loaderOrder() {
        return 100;
    }

    @Override
    public boolean supports(CudaModuleLoadRequest request) {
        return request != null
                && GpuBackendCompileOptions.CUDA_MODULE_LOADER_DRIVER.equals(request.loaderMode());
    }

    @Override
    public CudaModuleLoadResult load(CudaModuleLoadRequest request) {
        return CudaDriverLibrary.loadModule(request, loaderId());
    }
}
