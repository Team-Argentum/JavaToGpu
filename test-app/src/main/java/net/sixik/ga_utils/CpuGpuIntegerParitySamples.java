package net.sixik.ga_utils;

import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.anotations.CCode;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

public final class CpuGpuIntegerParitySamples {

    static final int INT_ITERATIONS = 3;
    static final long LONG_ITERATIONS = 4L;

    private CpuGpuIntegerParitySamples() {
    }

    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
    public static void transformInt(@GPUGlobal int[] input, @GPUGlobal int[] output) {
        int id = GPU.get_global_id(0);
        int value = input[id];
        short step = 2;
        byte delta = 3;

        for (int i = 0; i < INT_ITERATIONS; i++) {
            value += IntMath.mix(input[id], i);
            value += step + delta + 'A';
            value ^= (i + IntMath.BASE_XOR);
            value <<= 1;
            value--;
        }

        int branch = value & 3;
        switch (branch) {
            case 0: {
                value |= IntMath.FLAG;
                break;
            }
            case 1: {
                value &= IntMath.MASK;
                break;
            }
            default: {
                value ^= IntMath.FLAG;
                break;
            }
        }

        output[id] = value;
    }

    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
    public static void transformLong(@GPUGlobal long[] input, @GPUGlobal long[] output) {
        int id = GPU.get_global_id(0);
        long value = input[id];

        for (int i = 0; i < LONG_ITERATIONS; i++) {
            value += LongMath.mix(input[id], i);
            value ^= (i + LongMath.BASE_XOR);
            value >>= 1;
            value++;
        }

        int branch = (int) (value & 1L);
        switch (branch) {
            case 0: {
                value |= LongMath.FLAG;
                break;
            }
            default: {
                value ^= LongMath.FLAG;
                break;
            }
        }

        output[id] = value;
    }

    public static void transformIntCpu(int[] input, int[] output) {
        for (int id = 0; id < input.length; id++) {
            int value = input[id];
            short step = 2;
            byte delta = 3;

            for (int i = 0; i < INT_ITERATIONS; i++) {
                value += IntMath.mix(input[id], i);
                value += step + delta + 'A';
                value ^= (i + IntMath.BASE_XOR);
                value <<= 1;
                value--;
            }

            int branch = value & 3;
            switch (branch) {
                case 0: {
                    value |= IntMath.FLAG;
                    break;
                }
                case 1: {
                    value &= IntMath.MASK;
                    break;
                }
                default: {
                    value ^= IntMath.FLAG;
                    break;
                }
            }

            output[id] = value;
        }
    }

    public static void transformLongCpu(long[] input, long[] output) {
        for (int id = 0; id < input.length; id++) {
            long value = input[id];

            for (int i = 0; i < LONG_ITERATIONS; i++) {
                value += LongMath.mix(input[id], i);
                value ^= (i + LongMath.BASE_XOR);
                value >>= 1;
                value++;
            }

            int branch = (int) (value & 1L);
            switch (branch) {
                case 0: {
                    value |= LongMath.FLAG;
                    break;
                }
                default: {
                    value ^= LongMath.FLAG;
                    break;
                }
            }

            output[id] = value;
        }
    }

    public static final class IntMath {

        static final int BASE_XOR = 3;
        static final int OFFSET = 11;
        static final int FLAG = 0x12;
        static final int MASK = 0x7FFF;

        private IntMath() {
        }

        @CCode(inline = true)
        public static int mix(int value, int step) {
            return (value >> 1) + step + OFFSET;
        }
    }

    public static final class LongMath {

        static final long BASE_XOR = 5L;
        static final long OFFSET = 17L;
        static final long FLAG = 0x120L;

        private LongMath() {
        }

        @CCode(inline = true)
        public static long mix(long value, long step) {
            return (value << 1) + step + OFFSET;
        }
    }
}
