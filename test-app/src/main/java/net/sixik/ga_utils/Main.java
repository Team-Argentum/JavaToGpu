package net.sixik.ga_utils;

import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.anotations.CCode;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend;

import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        float[] in = new float[256];
        float[] out = new float[256];

        for (int i = 0; i < in.length; i++) {
            in[i] = 1 + (i ^ 2) * .5f;
        }

        try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
            GpuRuntime.setBackend(backend);
            System.out.println("Invoking @GPU method directly...");
            GpuTest.my_gpu_code(in, out);
            System.out.println("GPU result: " + out[0]);
        } catch (RuntimeException exception) {
            System.out.println("GPU execution failed: " + exception.getMessage());
        } finally {
            GpuRuntime.setBackend(GpuRuntime.defaultBackend());
        }
    }

    public static class GpuTest {

        @net.sixik.ga_utils.javatogpu.api.anotations.GPU
        public static void my_gpu_code(
                @GPUGlobal float[] input,
                @GPUGlobal float[] output
        ) {
            int id = GPU.get_global_id(0);
            float value = input[id];

            output[id] = GPU.sin(GpuUtils.c_code(value, value * 2, 0.15f)) * GPU.tan(input[id]);
        }


    }

    public static class GpuUtils {

        @CCode
        public static float c_code(float a, float b, float t) {
            return a - (b + a) * t;
        }
    }
}
