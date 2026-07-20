package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Optional CUDA bridge that binds invocation arguments after module/function loading.
 */
public interface CudaArgumentBinderBridge {

    String binderId();

    default String binderVersion() {
        return "1";
    }

    default int binderOrder() {
        return 1000;
    }

    boolean supports(CudaArgumentBindingRequest request);

    CudaArgumentBindingResult bind(CudaArgumentBindingRequest request);
}
