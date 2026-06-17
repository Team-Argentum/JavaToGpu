package net.sixik.ga_utils;

import net.sixik.ga_utils.javatogpu.api.DoublePtr;
import net.sixik.ga_utils.javatogpu.api.FloatPtr;
import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.anotations.CCode;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend;

import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        double[] in = new double[256];
        double[] out = new double[256];

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
                @GPUGlobal double[] input,
                @GPUGlobal double[] output
        ) {
            int id = GPU.get_global_id(0);
            double value = input[id];

            DoublePtr ptr = new DoublePtr();
            GpuUtils.my_code(ptr);

            for (int i = 0; i < 50; i++) {
                value *= input[id] + 5 * GpuUtils.c_code(i * 0.75f, 10, i);

                for (int j = 0; j < 150; j++) {
                    value += j;

                    for (int k = 0; k < i * j; k++) {
                        value += k * 0.1f;
                    }
                }
            }

            if(value > 20) {
                value = 50 * (value / 50);
            }

            int index = value < 20 ? 1 : 0;
            switch (index) {
                case 1: {
                    value = 17 * (1 / value);
                }
                default:
                    break;
            }

            ptr.value = input[id];

            output[id] = GPU.sin(GpuUtils.c_code(value, value * 2, 0.15f)) * GPU.tan(GpuUtils.aditionalCode(ptr, value));
        }
    }

    public static class GpuUtils {

        @CCode
        public static double c_code(double a, double b, double t) {
            return a + b + t;
        }

        @CCode
        public static void my_code(DoublePtr ptr) {
            ptr.value = 50;
        }

        @CCode(inline = true)
        public static double aditionalCode(DoublePtr ptr, double value) {
            return (ptr.value * ptr.value * 0.5f) + value * ptr.value;
        }
    }
}
