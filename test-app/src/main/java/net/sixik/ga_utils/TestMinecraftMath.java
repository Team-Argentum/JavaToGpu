package net.sixik.ga_utils;

import net.sixik.ga_utils.javatogpu.api.Double3;
import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.Int3;
import net.sixik.ga_utils.javatogpu.api.annotations.CCode;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUStruct;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope;

import java.util.Random;

public final class TestMinecraftMath {

    private static final int IMPROVED_NOISE_PERMUTATION_SIZE = 256;

    public static void main(String[] args) {
        Random random = new Random(255);

        double[] outArray = new double[256];
        GpuPerlinNoiseData noise = Utils.createDefaultNoise(random);
        try (GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {
            GPUCode.kernel(
                    noise.info,
                    noise.permutation0,
                    noise.permutation1,
                    noise.permutation2,
                    outArray
            );
            for (int i = 0; i < 256; i++) {
                System.out.println("outArray[" + i + "] = " + outArray[i]);
            }
        }
    }


    private static class GPUCode {

        @net.sixik.ga_utils.javatogpu.api.annotations.GPU
        public static void kernel(PerlinNoiseInfo noise,
                                  @GPUGlobal byte[] permutation0,
                                  @GPUGlobal byte[] permutation1,
                                  @GPUGlobal byte[] permutation2,
                                  @GPUGlobal double[] outValues) {
            int id = GPU.get_global_id(0);
            double x = (id & 15) * 0.125;
            double z = (id >> 4) * 0.125;
            outValues[id] = NoiseMath.perlinValue(
                    noise,
                    permutation0,
                    permutation1,
                    permutation2,
                    x,
                    0.0,
                    z
            );
        }
    }

    @GPUStruct
    public static class PerlinNoiseInfo {
        public int firstOctave;
        public int noiseLevelCount;
        public double lowestFreqValueFactor;
        public double lowestFreqInputFactor;
        public double maxValue;
        public Int3 levelActive;
        public Double3 levelXo;
        public Double3 levelYo;
        public Double3 levelZo;
        public Double3 amplitudes;
    }

    public static final class NoiseMath {

        private NoiseMath() {
        }

        @CCode
        public static double perlinValue(
                PerlinNoiseInfo noise,
                @GPUGlobal byte[] permutation0,
                @GPUGlobal byte[] permutation1,
                @GPUGlobal byte[] permutation2,
                double x,
                double y,
                double z
        ) {
            double value = 0.0;
            double inputFactor0 = noise.lowestFreqInputFactor;
            double valueFactor0 = noise.lowestFreqValueFactor;
            double inputFactor1 = inputFactor0 * 2.0;
            double valueFactor1 = valueFactor0 * 0.5;
            double inputFactor2 = inputFactor1 * 2.0;
            double valueFactor2 = valueFactor1 * 0.5;

            if (noise.levelActive.x != 0) {
                value += noise.amplitudes.x * improvedNoise(
                        permutation0,
                        noise.levelXo.x,
                        noise.levelYo.x,
                        noise.levelZo.x,
                        wrap(x * inputFactor0),
                        wrap(y * inputFactor0),
                        wrap(z * inputFactor0),
                        0.0,
                        0.0
                ) * valueFactor0;
            }

            if (noise.levelActive.y != 0) {
                value += noise.amplitudes.y * improvedNoise(
                        permutation1,
                        noise.levelXo.y,
                        noise.levelYo.y,
                        noise.levelZo.y,
                        wrap(x * inputFactor1),
                        wrap(y * inputFactor1),
                        wrap(z * inputFactor1),
                        0.0,
                        0.0
                ) * valueFactor1;
            }

            if (noise.levelActive.z != 0) {
                value += noise.amplitudes.z * improvedNoise(
                        permutation2,
                        noise.levelXo.z,
                        noise.levelYo.z,
                        noise.levelZo.z,
                        wrap(x * inputFactor2),
                        wrap(y * inputFactor2),
                        wrap(z * inputFactor2),
                        0.0,
                        0.0
                ) * valueFactor2;
            }

            return value;
        }

        @CCode(inline = true)
        public static double improvedNoise(
                @GPUGlobal byte[] permutations,
                double xo,
                double yo,
                double zo,
                double x,
                double y,
                double z,
                double step,
                double limit
        ) {
            double shiftedX = x + xo;
            double shiftedY = y + yo;
            double shiftedZ = z + zo;
            int floorX = floorToInt(shiftedX);
            int floorY = floorToInt(shiftedY);
            int floorZ = floorToInt(shiftedZ);
            double localX = shiftedX - floorX;
            double localY = shiftedY - floorY;
            double localZ = shiftedZ - floorZ;
            double snappedY = 0.0;

            if (step != 0.0) {
                double clampedY = limit >= 0.0 && limit < localY ? limit : localY;
                snappedY = floorToLong(clampedY / step + 1.0E-7) * step;
            }

            return sampleAndLerp(
                    permutations,
                    floorX,
                    floorY,
                    floorZ,
                    localX,
                    localY - snappedY,
                    localZ,
                    localY
            );
        }

        @CCode(inline = true)
        public static double sampleAndLerp(
                @GPUGlobal byte[] permutations,
                int x,
                int y,
                int z,
                double localX,
                double localY,
                double localZ,
                double smoothYInput
        ) {
            int px0 = permutation(permutations, x);
            int px1 = permutation(permutations, x + 1);
            int py00 = permutation(permutations, px0 + y);
            int py01 = permutation(permutations, px0 + y + 1);
            int py10 = permutation(permutations, px1 + y);
            int py11 = permutation(permutations, px1 + y + 1);

            double g000 = gradDot(permutation(permutations, py00 + z), localX, localY, localZ);
            double g100 = gradDot(permutation(permutations, py10 + z), localX - 1.0, localY, localZ);
            double g010 = gradDot(permutation(permutations, py01 + z), localX, localY - 1.0, localZ);
            double g110 = gradDot(permutation(permutations, py11 + z), localX - 1.0, localY - 1.0, localZ);
            double g001 = gradDot(permutation(permutations, py00 + z + 1), localX, localY, localZ - 1.0);
            double g101 = gradDot(permutation(permutations, py10 + z + 1), localX - 1.0, localY, localZ - 1.0);
            double g011 = gradDot(permutation(permutations, py01 + z + 1), localX, localY - 1.0, localZ - 1.0);
            double g111 = gradDot(permutation(permutations, py11 + z + 1), localX - 1.0, localY - 1.0, localZ - 1.0);

            double smoothX = smoothstep(localX);
            double smoothY = smoothstep(smoothYInput);
            double smoothZ = smoothstep(localZ);

            return lerp3(smoothX, smoothY, smoothZ, g000, g100, g010, g110, g001, g101, g011, g111);
        }

        @CCode(inline = true)
        public static int permutation(@GPUGlobal byte[] permutations, int index) {
            byte value = permutations[index & 255];
            return value < 0 ? value + 256 : value;
        }

        @CCode(inline = true)
        public static double gradDot(int gradient, double x, double y, double z) {
            switch (gradient & 15) {
                case 0:
                    return x + y;
                case 1:
                    return -x + y;
                case 2:
                    return x - y;
                case 3:
                    return -x - y;
                case 4:
                    return x + z;
                case 5:
                    return -x + z;
                case 6:
                    return x - z;
                case 7:
                    return -x - z;
                case 8:
                    return y + z;
                case 9:
                    return -y + z;
                case 10:
                    return y - z;
                case 11:
                    return -y - z;
                case 12:
                    return x + y;
                case 13:
                    return -y + z;
                case 14:
                    return -x + y;
                default:
                    return -y - z;
            }
        }

        @CCode(inline = true)
        public static double smoothstep(double value) {
            return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
        }

        @CCode(inline = true)
        public static double lerp(double delta, double start, double end) {
            return start + delta * (end - start);
        }

        @CCode(inline = true)
        public static double lerp2(double dx, double dy, double x0y0, double x1y0, double x0y1, double x1y1) {
            return lerp(dy, lerp(dx, x0y0, x1y0), lerp(dx, x0y1, x1y1));
        }

        @CCode(inline = true)
        public static double lerp3(
                double dx,
                double dy,
                double dz,
                double x0y0z0,
                double x1y0z0,
                double x0y1z0,
                double x1y1z0,
                double x0y0z1,
                double x1y0z1,
                double x0y1z1,
                double x1y1z1
        ) {
            return lerp(
                    dz,
                    lerp2(dx, dy, x0y0z0, x1y0z0, x0y1z0, x1y1z0),
                    lerp2(dx, dy, x0y0z1, x1y0z1, x0y1z1, x1y1z1)
            );
        }

        @CCode(inline = true)
        public static double wrap(double value) {
            return value - (double) floorToLong(value / 3.3554432E7 + 0.5) * 3.3554432E7;
        }

        @CCode(inline = true)
        public static long floorToLong(double value) {
            long whole = (long) value;
            return value < whole ? whole - 1L : whole;
        }

        @CCode(inline = true)
        public static int floorToInt(double value) {
            return (int) floorToLong(value);
        }
    }

    private static final class GpuPerlinNoiseData {

        private final PerlinNoiseInfo info;
        private final byte[] permutation0;
        private final byte[] permutation1;
        private final byte[] permutation2;

        private GpuPerlinNoiseData(
                PerlinNoiseInfo info,
                byte[] permutation0,
                byte[] permutation1,
                byte[] permutation2
        ) {
            this.info = info;
            this.permutation0 = permutation0;
            this.permutation1 = permutation1;
            this.permutation2 = permutation2;
        }
    }

    private static class Utils {

        private static GpuPerlinNoiseData createDefaultNoise(Random random) {
            PerlinNoiseInfo noise = new PerlinNoiseInfo();

            noise.firstOctave = -3;
            double[] amplitudes = new double[]{
                    1.0, 1.0, 0.0
            };

            noise.levelActive = new Int3(0, 0, 0);
            noise.levelXo = new Double3(0.0, 0.0, 0.0);
            noise.levelYo = new Double3(0.0, 0.0, 0.0);
            noise.levelZo = new Double3(0.0, 0.0, 0.0);
            noise.amplitudes = new Double3(amplitudes[0], amplitudes[1], amplitudes[2]);

            byte[] permutation0 = new byte[IMPROVED_NOISE_PERMUTATION_SIZE];
            byte[] permutation1 = new byte[IMPROVED_NOISE_PERMUTATION_SIZE];
            byte[] permutation2 = new byte[IMPROVED_NOISE_PERMUTATION_SIZE];
            int zeroOctaveIndex = -noise.firstOctave;

            createDiscardedImprovedNoise(random);
            if (zeroOctaveIndex >= 0 && zeroOctaveIndex < amplitudes.length && amplitudes[zeroOctaveIndex] != 0.0) {
                storeImprovedNoise(
                        random,
                        zeroOctaveIndex,
                        noise,
                        permutation0,
                        permutation1,
                        permutation2
                );
            }

            for (int k = zeroOctaveIndex - 1; k >= 0; k--) {
                if (k < amplitudes.length) {
                    double amplitude = amplitudes[k];
                    if (amplitude != 0.0) {
                        storeImprovedNoise(
                                random,
                                k,
                                noise,
                                permutation0,
                                permutation1,
                                permutation2
                        );
                    } else {
                        skipOctave(random, 262);
                    }
                } else {
                    skipOctave(random, 262);
                }
            }

            noise.noiseLevelCount = amplitudes.length;
            noise.lowestFreqInputFactor = Math.pow(2.0, -zeroOctaveIndex);
            noise.lowestFreqValueFactor = Math.pow(2.0, amplitudes.length - 1) / (Math.pow(2.0, amplitudes.length) - 1.0);
            noise.maxValue = edgeValue(noise.levelActive, noise.amplitudes, noise.lowestFreqValueFactor, 2.0);

            return new GpuPerlinNoiseData(
                    noise,
                    permutation0,
                    permutation1,
                    permutation2
            );
        }

        private static double edgeValue(
                Int3 noiseLevelActive,
                Double3 amplitudes,
                double lowestFreqValueFactor,
                double d
        ) {
            double e = 0.0;
            double f = lowestFreqValueFactor;
            if (noiseLevelActive.x != 0) {
                e += amplitudes.x * d * f;
            }
            f *= 0.5;
            if (noiseLevelActive.y != 0) {
                e += amplitudes.y * d * f;
            }
            f *= 0.5;
            if (noiseLevelActive.z != 0) {
                e += amplitudes.z * d * f;
            }
            return e;
        }

        private static void skipOctave(Random random, int value) {
            for (int i = 0; i < value; i++) {
                random.nextInt();
            }
        }

        private static void createDiscardedImprovedNoise(Random random) {
            createImprovedNoise(random, null, null, null, null, -1);
        }

        private static void storeImprovedNoise(
                Random random,
                int levelIndex,
                PerlinNoiseInfo noise,
                byte[] permutation0,
                byte[] permutation1,
                byte[] permutation2
        ) {
            createImprovedNoise(
                    random,
                    noise,
                    permutation0,
                    permutation1,
                    permutation2,
                    levelIndex
            );
        }

        private static void createImprovedNoise(
                Random random,
                PerlinNoiseInfo noise,
                byte[] permutation0,
                byte[] permutation1,
                byte[] permutation2,
                int levelIndex
        ) {
            double xo = random.nextDouble() * 256.0;
            double yo = random.nextDouble() * 256.0;
            double zo = random.nextDouble() * 256.0;

            byte[] p = new byte[256];
            for (int i = 0; i < p.length; i++) {
                p[i] = (byte) i;
            }

            for (int i = 0; i < 256; ++i) {
                int j = random.nextInt(256 - i);
                byte b = p[i];
                p[i] = p[i + j];
                p[i + j] = b;
            }

            if (levelIndex >= 0) {
                switch (levelIndex) {
                    case 0 -> {
                        noise.levelActive.x = 1;
                        noise.levelXo.x = xo;
                        noise.levelYo.x = yo;
                        noise.levelZo.x = zo;
                        System.arraycopy(p, 0, permutation0, 0, p.length);
                    }
                    case 1 -> {
                        noise.levelActive.y = 1;
                        noise.levelXo.y = xo;
                        noise.levelYo.y = yo;
                        noise.levelZo.y = zo;
                        System.arraycopy(p, 0, permutation1, 0, p.length);
                    }
                    case 2 -> {
                        noise.levelActive.z = 1;
                        noise.levelXo.z = xo;
                        noise.levelYo.z = yo;
                        noise.levelZo.z = zo;
                        System.arraycopy(p, 0, permutation2, 0, p.length);
                    }
                    default -> {
                    }
                }
            }
        }
    }
}
