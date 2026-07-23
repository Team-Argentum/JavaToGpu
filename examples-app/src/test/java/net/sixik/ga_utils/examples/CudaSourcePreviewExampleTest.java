package net.sixik.ga_utils.examples;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CudaSourcePreviewExampleTest {

    @Test
    void rendersHardwareFreeCudaSourcePreview() {
        String output = CudaSourcePreviewExample.renderCudaSourcePreview();

        assertTrue(output.contains("CUDA source preview example:"), output);
        assertTrue(output.contains("hardware-free"), output);
        assertTrue(output.contains("- backend=CUDA"), output);
        assertTrue(output.contains("- lowerStage=SUCCEEDED"), output);
        assertTrue(output.contains("- lowered=true"), output);
        assertTrue(output.contains("- selectedSource=irgpu-cuda-source"), output);
        assertTrue(output.contains("- moduleFormat=cuda-c"), output);
        assertTrue(output.contains("- execution=disabled"), output);
        assertTrue(output.contains("- nativeCompilerBridge.example=GpuRuntimeCompileOptions.cudaNvcc(...)"), output);
        assertTrue(output.contains("- nativeCompilerBridge.mode=nvcc"), output);
        assertTrue(output.contains("- nativeCompilerBridge.execution=not-run-by-this-example"), output);
        assertTrue(output.contains("- nativeModuleLoader.mode=driver"), output);
        assertTrue(output.contains("- nativeModuleLoader.execution=not-run-by-this-example"), output);
        assertTrue(output.contains("- nativeArgumentBinder.mode=driver"), output);
        assertTrue(output.contains("- nativeArgumentBinder.execution=not-run-by-this-example"), output);
        assertTrue(output.contains("- nativeKernelLauncher.mode=driver"), output);
        assertTrue(output.contains("- nativeKernelLauncher.execution=not-run-by-this-example"), output);
        assertTrue(output.contains("- nativeReadback.mode=driver"), output);
        assertTrue(output.contains("- nativeReadback.execution=not-run-by-this-example"), output);
        assertTrue(output.contains("--- cuda-c preview ---"), output);
        assertTrue(output.contains("__device__ float jtg_fn_square_float(float value);"), output);
        assertTrue(output.contains("extern \"C\" __global__ void jtg_kernel(const float* input, float* output)"), output);
        assertTrue(output.contains("blockIdx.x * blockDim.x + threadIdx.x"), output);
        assertTrue(output.contains("output[id] = jtg_fn_square_float(input[id]);"), output);
        assertTrue(output.contains("--- runtime dump preview ---"), output);
        assertTrue(output.contains("- dumpArtifact.cudaProperties=true"), output);
        assertTrue(output.contains("- dumpArtifact.originalCudaPreview=true"), output);
        assertTrue(output.contains("- dumpArtifact.optimizedCudaPreview=true"), output);
        assertTrue(output.contains("- dumpArtifact.selectedBackendOpenCl=true"), output);
        assertTrue(output.contains("- dumpPreview.selectedStage=optimized"), output);
        assertTrue(output.contains("- dumpPreview.execution=disabled"), output);
        assertTrue(output.contains("- dumpPreview.optimizedContainsHelper=true"), output);
        assertTrue(output.contains("- dumpPreview.selectedBackendStillOpenCl=true"), output);
    }

    @Test
    void rendersRuntimeDumpPreviewWithoutCudaExecution() {
        String output = CudaSourcePreviewExample.renderRuntimeDumpPreview();

        assertTrue(output.contains("dumpArtifact.cudaProperties=true"), output);
        assertTrue(output.contains("dumpArtifact.originalCudaPreview=true"), output);
        assertTrue(output.contains("dumpArtifact.optimizedCudaPreview=true"), output);
        assertTrue(output.contains("dumpPreview.execution=disabled"), output);
        assertTrue(output.contains("dumpPreview.selectedBackendStillOpenCl=true"), output);
    }

    @Test
    void rendersNativeCompilerBridgeOptionsWithoutRunningNvcc() {
        String output = CudaSourcePreviewExample.renderNativeCompilerBridgeOptions();

        assertTrue(output.contains("nativeCompilerBridge.example=GpuRuntimeCompileOptions.cudaNvcc(...)"), output);
        assertTrue(output.contains("nativeCompilerBridge.mode=nvcc"), output);
        assertTrue(output.contains("nativeCompilerBridge.nvccPath=nvcc"), output);
        assertTrue(output.contains("nativeCompilerBridge.execution=not-run-by-this-example"), output);
        assertTrue(output.contains("nativeModuleLoader.mode=driver"), output);
        assertTrue(output.contains("nativeModuleLoader.execution=not-run-by-this-example"), output);
        assertTrue(output.contains("nativeArgumentBinder.mode=driver"), output);
        assertTrue(output.contains("nativeArgumentBinder.execution=not-run-by-this-example"), output);
        assertTrue(output.contains("nativeKernelLauncher.mode=driver"), output);
        assertTrue(output.contains("nativeKernelLauncher.execution=not-run-by-this-example"), output);
        assertTrue(output.contains("nativeReadback.mode=driver"), output);
        assertTrue(output.contains("nativeReadback.execution=not-run-by-this-example"), output);
    }
}
