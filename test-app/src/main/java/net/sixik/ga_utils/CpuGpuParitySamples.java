package net.sixik.ga_utils;

import net.sixik.ga_utils.javatogpu.api.FloatPtr;
import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.anotations.CCode;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

public final class CpuGpuParitySamples {

    static final float BASE_SCALE = 0.5f;
    static final float BASE_BIAS = 1.25f;
    static final int ITERATIONS = 4;

    private CpuGpuParitySamples() {
    }

    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
    public static void transform(@GPUGlobal float[] input, @GPUGlobal float[] output) {
        int id = GPU.get_global_id(0);
        float value = (input[id] * BASE_SCALE) + BASE_BIAS;
        FloatPtr ptr = new FloatPtr(value);
        KernelMath.clamp(ptr);

        for (int i = 0; i < ITERATIONS; i++) {
            value = KernelMath.blend(value, (input[id] + ptr.value) + i, 0.125f);
        }

        int branch = ((int) input[id]) & 1;
        switch (branch) {
            case 0: {
                value = KernelMath.finish(ptr, value) + KernelMath.SHIFT;
                break;
            }
            default: {
                value = KernelMath.finish(ptr, value) - KernelMath.SHIFT;
                break;
            }
        }

        output[id] = value;
    }

    public static void transformCpu(float[] input, float[] output) {
        for (int id = 0; id < input.length; id++) {
            float value = (input[id] * BASE_SCALE) + BASE_BIAS;
            FloatPtr ptr = new FloatPtr(value);
            KernelMath.clamp(ptr);

            for (int i = 0; i < ITERATIONS; i++) {
                value = KernelMath.blend(value, (input[id] + ptr.value) + i, 0.125f);
            }

            int branch = ((int) input[id]) & 1;
            switch (branch) {
                case 0: {
                    value = KernelMath.finish(ptr, value) + KernelMath.SHIFT;
                    break;
                }
                default: {
                    value = KernelMath.finish(ptr, value) - KernelMath.SHIFT;
                    break;
                }
            }

            output[id] = value;
        }
    }

    public static final class KernelMath {

        static final float SHIFT = 0.75f;
        static final float CLAMP_LIMIT = 32.0f;

        private KernelMath() {
        }

        @CCode
        public static float blend(float current, float target, float factor) {
            return current + ((target - current) * factor);
        }

        @CCode
        public static void clamp(FloatPtr ptr) {
            if (ptr.value > CLAMP_LIMIT) {
                ptr.value = CLAMP_LIMIT;
            }
        }

        @CCode(inline = true)
        public static float finish(FloatPtr ptr, float value) {
            return (ptr.value * SHIFT) + value;
        }
    }
}
