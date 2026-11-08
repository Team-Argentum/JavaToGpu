package com.sixik.gpu.ir.interpreter;

import com.sixik.gpu.api.types.Double2;
import com.sixik.gpu.api.types.Double4;
import com.sixik.gpu.api.types.Float4;
import com.sixik.gpu.api.types.Int2;
import com.sixik.gpu.api.types.Int4;
import com.sixik.gpu.api.types.Long2;
import com.sixik.gpu.ir.interpreter.samples.StructKernels;
import com.sixik.gpu.ir.interpreter.samples.VectorKernels;
import com.sixik.gpu.ir.interpreter.samples.ControlKernels;
import com.sixik.gpu.ir.interpreter.samples.FloatKernels;
import com.sixik.gpu.ir.interpreter.samples.IndexKernels;
import com.sixik.gpu.ir.interpreter.samples.IntKernels;
import com.sixik.gpu.ir.interpreter.samples.LongKernels;
import com.sixik.gpu.ir.interpreter.samples.MemoryKernels;
import com.sixik.gpu.ir.interpreter.samples.PointerKernels;
import com.sixik.gpu.ir.interpreter.samples.SmallTypeKernels;
import org.junit.jupiter.api.Test;

import java.util.Random;

/**
 * Критерий приёмки M4: результат интерпретатора = результат прямого вызова оригинального Java-метода по
 * work-item'ам. Целые и логика - точно, вещественные - побитово (все builtins в интерпретаторе и в {@code Gpu}
 * считаются одними и теми же операциями JDK, поэтому допуск в ULP здесь 0; допуски таблицы нужны будут
 * для GPU-бэкенда).
 */
class DifferentialTest {

    private static final int[] EDGE_INT = {
            Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -65536, -257, -128, -33, -32, -31, -3, -2, -1, 0, 1, 2, 3,
            7, 31, 32, 33, 63, 64, 127, 255, 256, 65535, 65536, Integer.MAX_VALUE - 1, Integer.MAX_VALUE,
            0x55555555, 0xAAAAAAAA, 0x7FFF, 0x8000, 0xFFFF
    };

    private static final long[] EDGE_LONG = {
            Long.MIN_VALUE, Long.MIN_VALUE + 1, Integer.MIN_VALUE - 1L, Integer.MIN_VALUE, -65536, -64, -63, -33,
            -32, -31, -3, -2, -1, 0, 1, 2, 3, 31, 32, 33, 63, 64, 65, 127, 255, 65535, Integer.MAX_VALUE,
            Integer.MAX_VALUE + 1L, 0xFFFFFFFFL, 0x100000000L, Long.MAX_VALUE - 1, Long.MAX_VALUE,
            0x5555555555555555L, 0xAAAAAAAAAAAAAAAAL
    };

    private static final float[] EDGE_FLOAT = {
            0f, -0f, 1f, -1f, 0.5f, 1.5f, 2.5f, -2.5f, 2.9f, -2.9f, Float.MIN_VALUE, Float.MIN_NORMAL, Float.MAX_VALUE,
            -Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN, 2147483648f, -2147483648f,
            3e9f, -3e9f, 1e20f, 4294967296f, 9.223372e18f, -9.223372e18f, 1.8446744e19f, 100f, 127f, 128f, 255f,
            256f, 40000f, 65535f, 65536f, -129f, 1234.5678f, 3.14159f, 1e-20f
    };

    private static final double[] EDGE_DOUBLE = {
            0d, -0d, 1d, -1d, 0.5d, 1.5d, 2.5d, -2.5d, 2.9d, -2.9d, Double.MIN_VALUE, Double.MIN_NORMAL,
            Double.MAX_VALUE, -Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN,
            2147483648d, 2147483647.9d, -2147483649d, 4294967296d, 9.223372036854775807e18, -9.223372036854775808e18,
            1.8446744073709552e19, 1e300, 100d, 127.5d, 255d, 32768d, -32769d, 65536d, 1234.5678d, 3.141592653589793d,
            1e-300
    };

    // ---- генераторы пар: все пары крайних значений + случайные -----------------------------------

    private record IntPairs(int[] a, int[] b) {
    }

    private static IntPairs intPairs() {
        int n = EDGE_INT.length;
        int total = n * n + 400;
        int[] a = new int[total];
        int[] b = new int[total];
        for (int i = 0; i < n * n; i++) {
            a[i] = EDGE_INT[i / n];
            b[i] = EDGE_INT[i % n];
        }
        Random r = new Random(1);
        for (int i = n * n; i < total; i++) {
            a[i] = r.nextInt();
            b[i] = i % 3 == 0 ? r.nextInt(70) - 5 : r.nextInt();
        }
        return new IntPairs(a, b);
    }

    private record LongPairs(long[] a, long[] b) {
    }

    private static LongPairs longPairs() {
        int n = EDGE_LONG.length;
        int total = n * n + 400;
        long[] a = new long[total];
        long[] b = new long[total];
        for (int i = 0; i < n * n; i++) {
            a[i] = EDGE_LONG[i / n];
            b[i] = EDGE_LONG[i % n];
        }
        Random r = new Random(2);
        for (int i = n * n; i < total; i++) {
            a[i] = r.nextLong();
            b[i] = i % 3 == 0 ? r.nextInt(140) - 10 : r.nextLong();
        }
        return new LongPairs(a, b);
    }

    private static float[][] floatPairs() {
        int n = EDGE_FLOAT.length;
        int total = n * n + 300;
        float[] a = new float[total];
        float[] b = new float[total];
        for (int i = 0; i < n * n; i++) {
            a[i] = EDGE_FLOAT[i / n];
            b[i] = EDGE_FLOAT[i % n];
        }
        Random r = new Random(3);
        for (int i = n * n; i < total; i++) {
            a[i] = (r.nextFloat() - 0.5f) * (float) Math.pow(10, r.nextInt(14) - 4);
            b[i] = (r.nextFloat() - 0.5f) * (float) Math.pow(10, r.nextInt(14) - 4);
        }
        return new float[][]{a, b};
    }

    private static double[][] doublePairs() {
        int n = EDGE_DOUBLE.length;
        int total = n * n + 300;
        double[] a = new double[total];
        double[] b = new double[total];
        for (int i = 0; i < n * n; i++) {
            a[i] = EDGE_DOUBLE[i / n];
            b[i] = EDGE_DOUBLE[i % n];
        }
        Random r = new Random(4);
        for (int i = n * n; i < total; i++) {
            a[i] = (r.nextDouble() - 0.5) * Math.pow(10, r.nextInt(30) - 10);
            b[i] = (r.nextDouble() - 0.5) * Math.pow(10, r.nextInt(30) - 10);
        }
        return new double[][]{a, b};
    }

    // ---- целые -----------------------------------------------------------------------------------

    @Test
    void intArithmeticAndBitOps() {
        IntPairs p = intPairs();
        Differential.check(IntKernels.class, "ops", NdRange.of1D(p.a().length), () -> new Object[]{
                p.a().clone(), p.b().clone(), new int[p.a().length * IntKernels.WIDTH]});
    }

    @Test
    void longArithmeticAndBitOps() {
        LongPairs p = longPairs();
        Differential.check(LongKernels.class, "ops", NdRange.of1D(p.a().length), () -> new Object[]{
                p.a().clone(), p.b().clone(), new long[p.a().length * LongKernels.WIDTH]});
    }

    // ---- вещественные ----------------------------------------------------------------------------

    @Test
    void floatArithmeticBuiltinsAndConversions() {
        float[][] p = floatPairs();
        int n = p[0].length;
        Differential.check(FloatKernels.class, "floatOps", NdRange.of1D(n), () -> new Object[]{
                p[0].clone(), p[1].clone(), new float[n * 12], new int[n * 10], new long[n * 3], new double[n * 3]});
    }

    @Test
    void doubleArithmeticBuiltinsAndConversions() {
        double[][] p = doublePairs();
        int n = p[0].length;
        Differential.check(FloatKernels.class, "doubleOps", NdRange.of1D(n), () -> new Object[]{
                p[0].clone(), p[1].clone(), new double[n * 12], new int[n * 8], new long[n * 2], new float[n * 2]});
    }

    @Test
    void integersConvertToFloatingPoint() {
        IntPairs ip = intPairs();
        LongPairs lp = longPairs();
        int n = Math.min(ip.a().length, lp.a().length);
        int[] ints = java.util.Arrays.copyOf(ip.a(), n);
        long[] longs = java.util.Arrays.copyOf(lp.a(), n);
        Differential.check(FloatKernels.class, "fromIntegers", NdRange.of1D(n), () -> new Object[]{
                ints.clone(), longs.clone(), new float[n * 4], new double[n * 4]});
    }

    // ---- типы хранения ---------------------------------------------------------------------------

    @Test
    void storageTypesByteShortCharBoolean() {
        int n = 700;
        Random r = new Random(5);
        byte[] bytes = new byte[n];
        short[] shorts = new short[n];
        char[] chars = new char[n];
        boolean[] flags = new boolean[n];
        r.nextBytes(bytes);
        for (int i = 0; i < n; i++) {
            shorts[i] = (short) r.nextInt();
            chars[i] = (char) r.nextInt();
            flags[i] = r.nextBoolean();
        }
        bytes[0] = Byte.MIN_VALUE;
        bytes[1] = Byte.MAX_VALUE;
        shorts[0] = Short.MIN_VALUE;
        shorts[1] = Short.MAX_VALUE;
        chars[0] = Character.MAX_VALUE;
        chars[1] = 0;
        Differential.check(SmallTypeKernels.class, "storage", NdRange.of1D(n), () -> new Object[]{
                bytes.clone(), shorts.clone(), chars.clone(), flags.clone(), new int[n * 8]});
    }

    @Test
    void compoundAssignmentOnSmallTypes() {
        Differential.check(SmallTypeKernels.class, "compound", NdRange.of1D(1), () -> new Object[]{new int[3]});
    }

    // ---- управление потоком ----------------------------------------------------------------------

    @Test
    void nestedLoopsSwitchFallthroughAndContinue() {
        int n = 64;
        Differential.check(ControlKernels.class, "loops", NdRange.of1D(n), () -> {
            int[] in = new int[n];
            for (int i = 0; i < n; i++) {
                in[i] = i * 7 - 20;
            }
            return new Object[]{in, new int[n * 3]};
        });
    }

    @Test
    void evaluationOrderAndSideEffectsInExpressions() {
        int n = 40;
        Differential.check(ControlKernels.class, "effects", NdRange.of1D(n), () -> {
            int[] in = new int[n];
            int[] out = new int[n * 8];
            for (int i = 0; i < n; i++) {
                in[i] = i * 5 - 9;
            }
            for (int i = 0; i < out.length; i++) {
                out[i] = i * 3 + 1;
            }
            return new Object[]{in, new int[n * 4], out};
        });
    }

    @Test
    void scalarParametersAreReassignable() {
        Differential.check(ControlKernels.class, "parameters", NdRange.of1D(1), () -> new Object[]{
                7, 1L << 40, 2.5f, -1.25d, new double[4]});
    }

    @Test
    void returnFromInsideLoops() {
        int n = 30;
        Differential.check(ControlKernels.class, "earlyReturn", NdRange.of1D(n), () -> {
            int[] in = new int[n];
            for (int i = 0; i < n; i++) {
                in[i] = (i - 5) * 13;
            }
            return new Object[]{in, new int[n]};
        });
    }


    // ---- указатели (M9) ---------------------------------------------------------------------------

    @Test
    void referencesAreSharedBetweenDeviceFunctions() {
        int n = 24;
        Differential.check(PointerKernels.class, "refs", NdRange.of1D(n), () -> {
            float[] in = new float[n];
            for (int i = 0; i < n; i++) {
                in[i] = (i - 9) * 1.75f;
            }
            return new Object[]{in, new float[n * PointerKernels.REFS_WIDTH]};
        });
    }

    @Test
    void referenceValueIsReadInJavaOrder() {
        int n = 24;
        Differential.check(PointerKernels.class, "order", NdRange.of1D(n), () -> {
            int[] in = new int[n];
            for (int i = 0; i < n; i++) {
                in[i] = i == 3 ? Integer.MAX_VALUE : (i - 7) * 19_000_001; // и переполнение int
            }
            return new Object[]{in, new int[n * PointerKernels.ORDER_WIDTH]};
        });
    }

    @Test
    void longReferenceAndCompoundShift() {
        int n = 10;
        Differential.check(PointerKernels.class, "longs", NdRange.of1D(n), () -> {
            long[] in = new long[n];
            for (int i = 0; i < n; i++) {
                in[i] = i == 2 ? Long.MAX_VALUE - 3 : (i - 4) * 0x1234567890L;
            }
            return new Object[]{in, new long[n * PointerKernels.LONGS_WIDTH]};
        });
    }

    @Test
    void explicitPointersWithOffsetsAndConstantTable() {
        int n = 16;
        Differential.check(PointerKernels.class, "spaces", NdRange.of1D(n), () -> {
            float[] data = new float[n * PointerKernels.ROW];
            for (int i = 0; i < data.length; i++) {
                data[i] = i * 0.5f - 7;
            }
            return new Object[]{data, new float[]{1.5f, -2f, 4.25f, 8f, 100f}, new float[n]};
        });
    }

    @Test
    void pointerAdvancedInALoop() {
        int n = 16;
        Differential.check(PointerKernels.class, "walk", NdRange.of1D(n), () -> {
            int[] in = new int[n * PointerKernels.ROW];
            for (int i = 0; i < in.length; i++) {
                in[i] = i * 7 - 40;
            }
            return new Object[]{in, new int[n]};
        });
    }

    // ---- векторы и struct (M8) --------------------------------------------------------------------

    private static Float4[] floats4(int n, long seed, boolean edges) {
        java.util.Random r = new java.util.Random(seed);
        Float4[] a = new Float4[n];
        for (int i = 0; i < n; i++) {
            float[] v = new float[4];
            for (int k = 0; k < 4; k++) {
                v[k] = (r.nextFloat() - 0.5f) * 30f;
                if (edges && r.nextInt(5) == 0) {
                    v[k] = new float[]{0f, -0f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 1e30f}[r.nextInt(6)];
                }
            }
            a[i] = new Float4(v[0], v[1], v[2], v[3]);
        }
        return a;
    }

    private static Float4[] zeros4(int n) {
        Float4[] a = new Float4[n];
        java.util.Arrays.fill(a, new Float4(0, 0, 0, 0));
        return a;
    }

    @Test
    void floatVectorOperations() {
        for (boolean edges : new boolean[]{false, true}) {
            int n = 10;
            Differential.check(VectorKernels.class, "floatOps", NdRange.of1D(n), () -> new Object[]{
                    floats4(n, 3, edges), zeros4(n * VectorKernels.FLOAT_WIDTH)});
        }
    }

    @Test
    void integerVectorOperations() {
        int n = 9;
        Differential.check(VectorKernels.class, "intOps", NdRange.of1D(n), () -> {
            java.util.Random r = new java.util.Random(11);
            Int4[] in = new Int4[n];
            int[] edge = {Integer.MIN_VALUE, Integer.MAX_VALUE, -1, 1, 31, 32, 33, -33, 100, -100};
            for (int i = 0; i < n; i++) {
                int[] v = new int[4];
                for (int k = 0; k < 4; k++) {
                    v[k] = r.nextBoolean() ? edge[r.nextInt(edge.length)] : r.nextInt(2001) - 1000;
                    if (v[k] == 0) {
                        v[k] = 5;
                    }
                }
                in[i] = new Int4(v[0], v[1], v[2], v[3]);
            }
            Int4[] out = new Int4[n * VectorKernels.INT_WIDTH];
            java.util.Arrays.fill(out, new Int4(0, 0, 0, 0));
            return new Object[]{in, out};
        });
    }

    @Test
    void doubleAndLongVectors() {
        int n = 6;
        Differential.check(VectorKernels.class, "wide", NdRange.of1D(n), () -> {
            Double2[] d = new Double2[n];
            Long2[] l = new Long2[n];
            for (int i = 0; i < n; i++) {
                d[i] = new Double2(i * 1.5 - 3, 2 - i * 0.25);
                l[i] = new Long2(Long.MAX_VALUE - i, i - 3L);
            }
            Double4[] outD = new Double4[n];
            Long2[] outL = new Long2[n];
            java.util.Arrays.fill(outD, new Double4(0, 0, 0, 0));
            java.util.Arrays.fill(outL, new Long2(0, 0));
            return new Object[]{d, l, outD, outL};
        });
    }

    @Test
    void vectorParametersByValue() {
        int n = 5;
        Differential.check(VectorKernels.class, "byValue", NdRange.of1D(n), () -> new Object[]{
                zeros4(n), new Float4(1, -2, 0.5f, 4), new Int2(3, -4), 7});
    }

    @Test
    void vectorsBuiltFromScalars() {
        int n = 7;
        Differential.check(VectorKernels.class, "fromScalars", NdRange.of1D(n), () -> {
            float[] in = new float[n];
            for (int i = 0; i < n; i++) {
                in[i] = i * 0.75f - 2;
            }
            return new Object[]{in, new float[n * 4]};
        });
    }

    private static StructKernels.Particle[] particles(int n) {
        StructKernels.Particle[] ps = new StructKernels.Particle[n];
        for (int i = 0; i < n; i++) {
            ps[i] = new StructKernels.Particle(i * 1.5f, -i, new Float4(i + 1, 2 * i, -3, 0.5f), 100 + i);
        }
        return ps;
    }

    @Test
    void structFieldsAreUpdatedInPlace() {
        int n = 8;
        Differential.check(StructKernels.class, "step", NdRange.of1D(n), () -> new Object[]{particles(n), 0.25f});
    }

    @Test
    void structLocalsCopiesAndNestedFields() {
        int n = 6;
        Differential.check(StructKernels.class, "locals", NdRange.of1D(n), () -> new Object[]{
                particles(n), new float[n * 8]});
    }

    @Test
    void nestedStructArraysAndMixedTypes() {
        int n = 5;
        Differential.check(StructKernels.class, "bodies", NdRange.of1D(n), () -> {
            StructKernels.Body[] bs = new StructKernels.Body[n];
            StructKernels.Pair[] pairs = new StructKernels.Pair[n];
            for (int i = 0; i < n; i++) {
                bs[i] = new StructKernels.Body(new StructKernels.Particle(i, i * 2, new Float4(1, 2, 3, 4), i), 10.0 * i);
                pairs[i] = new StructKernels.Pair(i - 2, Long.MAX_VALUE - i);
            }
            return new Object[]{bs, pairs};
        });
    }

    @Test
    void structParameterByValue() {
        int n = 4;
        Differential.check(StructKernels.class, "withParameter", NdRange.of1D(n), () -> new Object[]{
                new float[n * 3], new StructKernels.Particle(2, 3, new Float4(0, 1, 2, 3), 9), new Float4(1, 2, 3, 4)});
    }

    // ---- память и функции ------------------------------------------------------------------------

    @Test
    void privateArraysAreZeroedOnEachIteration() {
        int n = 25;
        Differential.check(MemoryKernels.class, "privateArrays", NdRange.of1D(n), () -> {
            int[] in = new int[n];
            for (int i = 0; i < n; i++) {
                in[i] = i * 17 - 100;
            }
            return new Object[]{in, new int[n]};
        });
    }

    @Test
    void privateArraysOfCalleesAreReleasedOnReturn() {
        // 1500 вызовов по 2 МиБ (int[262144] хранится как long[262144]) = 3 ГиБ, если не освобождать: упадёт по памяти
        Differential.check(MemoryKernels.class, "manyCalls", NdRange.of1D(2), () -> new Object[]{new int[2]});
    }

    @Test
    void workItemsObserveEachOthersWritesInOrder() {
        int n = 50;
        Differential.check(MemoryKernels.class, "prefixSum", NdRange.of1D(n), () -> {
            int[] data = new int[n];
            for (int i = 0; i < n; i++) {
                data[i] = i % 7 + 1;
            }
            return new Object[]{data};
        });
    }

    @Test
    void sameArrayPassedTwiceIsOneBuffer() {
        int n = 20;
        Differential.check(MemoryKernels.class, "aliasing", NdRange.of1D(n), () -> {
            int[] shared = new int[n];
            return new Object[]{shared, shared};
        });
    }

    @Test
    void arrayLengthsAndLocalIdsOfLaunch() {
        Differential.check(MemoryKernels.class, "lengths", NdRange.of1D(3), () -> new Object[]{
                new int[17], new float[5], new int[3]});
    }

    @Test
    void helperFunctionsCallsAndArrayArguments() {
        int n = 24;
        Differential.check(MemoryKernels.class, "helpers", NdRange.of1D(n), () -> {
            int[] in = new int[n];
            float[] fin = new float[n];
            for (int i = 0; i < n; i++) {
                in[i] = i * 3 - 8;
                fin[i] = i * 0.75f;
            }
            return new Object[]{in, fin, new int[n * 4], new float[n]};
        });
    }

    // ---- индексы work-item'а ---------------------------------------------------------------------

    @Test
    void ids1D() {
        Differential.check(IndexKernels.class, "ids", NdRange.of1D(24, 8), () -> new Object[]{
                new int[24 * IndexKernels.WIDTH]});
    }

    @Test
    void ids2D() {
        Differential.check(IndexKernels.class, "ids", NdRange.of2D(6, 4, 3, 2), () -> new Object[]{
                new int[24 * IndexKernels.WIDTH]});
    }

    @Test
    void ids3D() {
        Differential.check(IndexKernels.class, "ids", NdRange.of3D(4, 3, 2, 2, 3, 1), () -> new Object[]{
                new int[24 * IndexKernels.WIDTH]});
    }

    @Test
    void idsWithoutExplicitGroupSizeUseOne() {
        Differential.check(IndexKernels.class, "ids", NdRange.of2D(3, 2), () -> new Object[]{
                new int[6 * IndexKernels.WIDTH]});
    }

    @Test
    void requiredWorkGroupSizeFromTheKernel() {
        Differential.check(IndexKernels.class, "grouped", NdRange.of2D(8, 6), () -> new Object[]{new int[48 * 3]});
    }

    @Test
    void computeSineFromReadme() {
        int n = 256;
        Differential.check(IndexKernels.class, "sine", NdRange.of1D(n), () -> {
            float[] in = new float[n];
            for (int i = 0; i < n; i++) {
                in[i] = (i - 128) * 0.37f;
            }
            return new Object[]{in, new float[n]};
        });
    }
}
