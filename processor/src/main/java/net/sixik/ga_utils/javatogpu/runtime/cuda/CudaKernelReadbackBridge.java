package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Optional CUDA bridge that copies launched kernel outputs back to host-visible arguments.
 */
public interface CudaKernelReadbackBridge {

    String readbackId();

    default String readbackVersion() {
        return "1";
    }

    default int readbackOrder() {
        return 1000;
    }

    boolean supports(CudaKernelReadbackRequest request);

    CudaKernelReadbackResult readBack(CudaKernelReadbackRequest request);
}
