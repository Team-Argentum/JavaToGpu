package net.sixik.ga_utils;

import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.anotations.CCode;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

public final class CpuGpuNumericEdgeCases {

    private CpuGpuNumericEdgeCases() {
    }

    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
    public static void transformFloat(@GPUGlobal float[] input, @GPUGlobal float[] output) {
        int id = GPU.get_global_id(0);
        float value = input[id];
        float result = value;

        if (value != value) {
            result = value;
        } else if (value > FloatMath.LARGE_LIMIT) {
            result = value * FloatMath.POSITIVE_SCALE;
        } else if (value < (-FloatMath.LARGE_LIMIT)) {
            result = value * FloatMath.NEGATIVE_SCALE;
        } else {
            int branch = value > 0.0f ? 1 : 0;
            switch (branch) {
                case 1: {
                    result = FloatMath.adjust(value) * FloatMath.POSITIVE_BRANCH_SCALE;
                    break;
                }
                default: {
                    result = FloatMath.adjust(value) / FloatMath.NEGATIVE_BRANCH_DIVISOR;
                    break;
                }
            }
        }

        output[id] = result;
    }

    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
    public static void transformDouble(@GPUGlobal double[] input, @GPUGlobal double[] output) {
        int id = GPU.get_global_id(0);
        double value = input[id];
        double result = value;

        if (value != value) {
            result = value;
        } else if (value > DoubleMath.LARGE_LIMIT) {
            result = value * DoubleMath.POSITIVE_SCALE;
        } else if (value < (-DoubleMath.LARGE_LIMIT)) {
            result = value * DoubleMath.NEGATIVE_SCALE;
        } else {
            int branch = value > 0.0 ? 1 : 0;
            switch (branch) {
                case 1: {
                    result = DoubleMath.adjust(value) * DoubleMath.POSITIVE_BRANCH_SCALE;
                    break;
                }
                default: {
                    result = DoubleMath.adjust(value) / DoubleMath.NEGATIVE_BRANCH_DIVISOR;
                    break;
                }
            }
        }

        output[id] = result;
    }

    public static void transformFloatCpu(float[] input, float[] output) {
        for (int i = 0; i < input.length; i++) {
            float value = input[i];
            float result = value;

            if (value != value) {
                result = value;
            } else if (value > FloatMath.LARGE_LIMIT) {
                result = value * FloatMath.POSITIVE_SCALE;
            } else if (value < (-FloatMath.LARGE_LIMIT)) {
                result = value * FloatMath.NEGATIVE_SCALE;
            } else {
                int branch = value > 0.0f ? 1 : 0;
                switch (branch) {
                    case 1: {
                        result = FloatMath.adjust(value) * FloatMath.POSITIVE_BRANCH_SCALE;
                        break;
                    }
                    default: {
                        result = FloatMath.adjust(value) / FloatMath.NEGATIVE_BRANCH_DIVISOR;
                        break;
                    }
                }
            }

            output[i] = result;
        }
    }

    public static void transformDoubleCpu(double[] input, double[] output) {
        for (int i = 0; i < input.length; i++) {
            double value = input[i];
            double result = value;

            if (value != value) {
                result = value;
            } else if (value > DoubleMath.LARGE_LIMIT) {
                result = value * DoubleMath.POSITIVE_SCALE;
            } else if (value < (-DoubleMath.LARGE_LIMIT)) {
                result = value * DoubleMath.NEGATIVE_SCALE;
            } else {
                int branch = value > 0.0 ? 1 : 0;
                switch (branch) {
                    case 1: {
                        result = DoubleMath.adjust(value) * DoubleMath.POSITIVE_BRANCH_SCALE;
                        break;
                    }
                    default: {
                        result = DoubleMath.adjust(value) / DoubleMath.NEGATIVE_BRANCH_DIVISOR;
                        break;
                    }
                }
            }

            output[i] = result;
        }
    }

    public static final class FloatMath {

        static final float LARGE_LIMIT = 1.0e30f;
        static final float SHIFT = 4.0f;
        static final float POSITIVE_SCALE = 0.5f;
        static final float NEGATIVE_SCALE = 2.0f;
        static final float POSITIVE_BRANCH_SCALE = 1.5f;
        static final float NEGATIVE_BRANCH_DIVISOR = 3.0f;

        private FloatMath() {
        }

        @CCode(inline = true)
        public static float adjust(float value) {
            return value + SHIFT;
        }
    }

    public static final class DoubleMath {

        static final double LARGE_LIMIT = 1.0e200;
        static final double SHIFT = 6.0;
        static final double POSITIVE_SCALE = 0.25;
        static final double NEGATIVE_SCALE = 1.5;
        static final double POSITIVE_BRANCH_SCALE = 2.0;
        static final double NEGATIVE_BRANCH_DIVISOR = 5.0;

        private DoubleMath() {
        }

        @CCode(inline = true)
        public static double adjust(double value) {
            return value + SHIFT;
        }
    }
}
