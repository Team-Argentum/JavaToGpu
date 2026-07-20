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
        assertTrue(output.contains("--- cuda-c preview ---"), output);
        assertTrue(output.contains("__device__ float jtg_fn_square_float(float value);"), output);
        assertTrue(output.contains("extern \"C\" __global__ void jtg_kernel(const float* input, float* output)"), output);
        assertTrue(output.contains("blockIdx.x * blockDim.x + threadIdx.x"), output);
        assertTrue(output.contains("output[id] = jtg_fn_square_float(input[id]);"), output);
    }
}
