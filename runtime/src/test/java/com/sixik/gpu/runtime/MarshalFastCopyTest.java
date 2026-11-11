package com.sixik.gpu.runtime;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** Параллельное копирование массив-нативная память ({@code Marshal.*Fast}) даёт тот же результат, что поэлементное. */
class MarshalFastCopyTest {

    /** Больше порога PARALLEL_COPY_BYTES для любого типа и не кратно числу частей. */
    private static final int N = (int) (Marshal.PARALLEL_COPY_BYTES / 2) + 12_345;

    private static void roundTrip(Object src, Object dst) {
        try (Arena a = Arena.ofShared()) {
            MemorySegment seg = a.allocate(Math.max(Marshal.byteSize(src), 8), 8);
            Marshal.copyToNativeFast(src, seg);
            Object check = java.lang.reflect.Array.newInstance(src.getClass().getComponentType(), Marshal.length(src));
            Marshal.fromNative(seg, check); // эталонное однопоточное чтение
            Marshal.fromNativeFast(seg, dst);
            switch (src) {
                case byte[] x -> {
                    assertArrayEquals(x, (byte[]) check);
                    assertArrayEquals(x, (byte[]) dst);
                }
                case short[] x -> {
                    assertArrayEquals(x, (short[]) check);
                    assertArrayEquals(x, (short[]) dst);
                }
                case char[] x -> {
                    assertArrayEquals(x, (char[]) check);
                    assertArrayEquals(x, (char[]) dst);
                }
                case int[] x -> {
                    assertArrayEquals(x, (int[]) check);
                    assertArrayEquals(x, (int[]) dst);
                }
                case long[] x -> {
                    assertArrayEquals(x, (long[]) check);
                    assertArrayEquals(x, (long[]) dst);
                }
                case float[] x -> {
                    assertArrayEquals(x, (float[]) check);
                    assertArrayEquals(x, (float[]) dst);
                }
                case double[] x -> {
                    assertArrayEquals(x, (double[]) check);
                    assertArrayEquals(x, (double[]) dst);
                }
                case boolean[] x -> {
                    assertArrayEquals(x, (boolean[]) check);
                    assertArrayEquals(x, (boolean[]) dst);
                }
                default -> throw new AssertionError(src.getClass());
            }
        }
    }

    @Test
    void everyPrimitiveTypeRoundTrips() {
        Random r = new Random(1);
        byte[] b = new byte[N * 4];
        r.nextBytes(b);
        short[] s = new short[N * 2];
        char[] c = new char[N * 2];
        int[] i = new int[N];
        long[] l = new long[N];
        float[] f = new float[N];
        double[] d = new double[N];
        boolean[] z = new boolean[N * 4];
        for (int k = 0; k < N; k++) {
            i[k] = r.nextInt();
            l[k] = r.nextLong();
            f[k] = r.nextFloat();
            d[k] = r.nextDouble();
        }
        for (int k = 0; k < s.length; k++) {
            s[k] = (short) r.nextInt();
            c[k] = (char) r.nextInt();
        }
        for (int k = 0; k < z.length; k++) {
            z[k] = r.nextBoolean();
        }
        roundTrip(b, new byte[b.length]);
        roundTrip(s, new short[s.length]);
        roundTrip(c, new char[c.length]);
        roundTrip(i, new int[i.length]);
        roundTrip(l, new long[l.length]);
        roundTrip(f, new float[f.length]);
        roundTrip(d, new double[d.length]);
        roundTrip(z, new boolean[z.length]);
    }

    @Test
    void confinedMemoryIsCopiedInOneThread() {
        // confined-память чужим потокам недоступна: копирование не должно падать с WrongThreadException
        int[] src = new int[N];
        java.util.Arrays.fill(src, 7);
        int[] dst = new int[N];
        try (Arena a = Arena.ofConfined()) {
            MemorySegment seg = a.allocate(Marshal.byteSize(src), 8);
            Marshal.copyToNativeFast(src, seg);
            Marshal.fromNativeFast(seg, dst);
        }
        assertArrayEquals(src, dst);
    }

    @Test
    void smallArraysUseTheSimplePath() {
        roundTrip(new int[]{1, 2, 3}, new int[3]);
        roundTrip(new float[0], new float[0]);
    }
}
