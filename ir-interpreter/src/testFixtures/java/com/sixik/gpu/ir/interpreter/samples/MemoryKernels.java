package com.sixik.gpu.ir.interpreter.samples;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.DeviceFunction;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;

/** Память: private-массивы, длины, зависимость между work-item'ами, один массив под двумя параметрами, helper'ы. */
public class MemoryKernels {

    @DeviceFunction
    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @DeviceFunction
    static int add(int x, int y) {
        return x + y;
    }

    @DeviceFunction
    static int twice(int x) {
        return add(x, x);
    }

    @DeviceFunction
    static int sum(@Global int[] a, int n) {
        int s = 0;
        for (int i = 0; i < n; i++) {
            s += a[i];
        }
        return s;
    }

    @DeviceFunction
    static void put(@Global int[] a, int i, int v) {
        a[i] = v;
    }

    @DeviceFunction
    static int fillAndSum(int seed) {
        int[] t = new int[6];
        for (int i = 0; i < t.length; i++) {
            t[i] = seed * (i + 1);
        }
        int s = 0;
        for (int i = 0; i < t.length; i++) {
            s += t[i];
        }
        return s;
    }

    /** Крупный приватный массив в вызываемой функции: без освобождения при возврате вызовы исчерпали бы память. */
    @DeviceFunction
    static int bigScratch(int x) {
        int[] t = new int[262144];
        t[x & 1023] = x;
        return t[x & 1023] + t[1023 - (x & 1023)];
    }

    @Kernel
    public static void manyCalls(@Global int[] out) {
        int g = Gpu.getGlobalId(0);
        int acc = 0;
        for (int i = 0; i < 1500; i++) {
            acc += bigScratch(i + g);
        }
        out[g] = acc;
    }

    @Kernel
    public static void privateArrays(@Global int[] in, @Global int[] out) {
        int g = Gpu.getGlobalId(0);
        int[] t = new int[8];
        for (int i = 0; i < 8; i++) {
            t[i] = in[g] + i * i;
        }
        int s = 0;
        for (int i = 7; i >= 0; i--) {
            s = s * 31 + t[i];
        }
        for (int r = 0; r < 3; r++) {
            float[] f = new float[4];
            f[r] += r + 1.5f;
            s += (int) (f[r] * 10) + (int) f[3];
        }
        out[g] = s + t.length + fillAndSum(in[g]) + fillAndSum(g);
    }

    @Kernel
    public static void prefixSum(@Global int[] data) {
        int g = Gpu.getGlobalId(0);
        if (g > 0) {
            data[g] += data[g - 1];
        }
    }

    @Kernel
    public static void aliasing(@Global int[] x, @Global int[] y) {
        int g = Gpu.getGlobalId(0);
        x[g] = g + 10;
        y[g] += 1;
    }

    @Kernel
    public static void lengths(@Global int[] a, @Global float[] b, @Global int[] out) {
        out[0] = a.length;
        out[1] = b.length;
        out[2] = Gpu.getGlobalId(0);
    }

    @Kernel
    public static void helpers(@Global int[] in, @Global float[] fin, @Global int[] out, @Global float[] fout) {
        int g = Gpu.getGlobalId(0);
        out[g * 4] = sum(in, 1 + g % 5);
        put(out, g * 4 + 1, twice(in[g]) + add(1, twice(2)));
        out[g * 4 + 2] = add(twice(g), sum(in, 2));
        out[g * 4 + 3] = in.length;
        fout[g] = lerp(fin[g], fin[(g + 1) % fin.length], 0.25f);
    }
}
