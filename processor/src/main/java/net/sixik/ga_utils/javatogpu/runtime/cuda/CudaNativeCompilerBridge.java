package net.sixik.ga_utils.javatogpu.runtime.cuda;

/**
 * Optional CUDA compiler bridge boundary.
 *
 * <p>Implementations may call nvcc, NVRTC, or another CUDA compiler path. They must not launch kernels or bind native
 * memory; this bridge only turns CUDA source/PTX input into a compiled module artifact for later stages.</p>
 */
public interface CudaNativeCompilerBridge {

    String bridgeId();

    default String bridgeVersion() {
        return "1";
    }

    default int bridgeOrder() {
        return 1000;
    }

    boolean supports(CudaNativeCompilationRequest request);

    CudaNativeCompilationResult compile(CudaNativeCompilationRequest request);
}
