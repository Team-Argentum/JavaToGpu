package demo;

import java.util.Arrays;
import demo.generated.Main_Kernels_scaleAndBias_GpuLauncher;
import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.GpuScope;
import net.sixik.ga_utils.javatogpu.api.JavaToGpu;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUOptimize;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        float[] input = new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f};
        float[] output = new float[input.length];

        try {
            try (GpuScope ignored = JavaToGpu.useOpenClSharedCache()) {
                Main_Kernels_scaleAndBias_GpuLauncher.invokeWithGlobalWorkSize(input.length, input, output);
            } finally {
                JavaToGpu.shutdownOpenClSharedCache();
            }

            System.out.println("input  = " + Arrays.toString(input));
            System.out.println("output = " + Arrays.toString(output));
        } catch (RuntimeException exception) {
            System.err.println("JavaToGpu/OpenCL run failed: " + exception.getMessage());
            System.err.println("Check OpenCL driver visibility and the LWJGL native classifier in build.gradle.");
            throw exception;
        }
    }

    public static final class Kernels {
        private Kernels() {
        }

        @net.sixik.ga_utils.javatogpu.api.annotations.GPU
        @GPUOptimize(fastMath = false)
        public static void scaleAndBias(
                @GPUGlobal float[] input,
                @GPUGlobal float[] output
        ) {
            int id = GPU.get_global_id(0);
            output[id] = input[id] * 2.0f + 1.0f;
        }
    }
}
