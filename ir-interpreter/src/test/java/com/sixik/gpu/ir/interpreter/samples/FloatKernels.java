package com.sixik.gpu.ir.interpreter.samples;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;

/** Вещественная арифметика, сравнения (в том числе с NaN), builtins и приведения float/double и целых. */
public class FloatKernels {

    @Kernel
    public static void floatOps(@Global float[] a, @Global float[] b, @Global float[] out, @Global int[] iout,
                                @Global long[] lout, @Global double[] dout) {
        int g = Gpu.getGlobalId(0);
        float x = a[g];
        float y = b[g];
        int o = g * 12;
        out[o] = x + y;
        out[o + 1] = x - y;
        out[o + 2] = x * y;
        out[o + 3] = x / y;
        out[o + 4] = x % y;
        out[o + 5] = -x;
        out[o + 6] = Gpu.max(x, y);
        out[o + 7] = Gpu.min(x, y);
        out[o + 8] = Gpu.sqrt(x);
        out[o + 9] = Gpu.sin(x);
        out[o + 10] = Gpu.cos(x);
        out[o + 11] = Gpu.fma(x, y, x);
        int p = g * 10;
        iout[p] = x < y ? 1 : 0;
        iout[p + 1] = x <= y ? 1 : 0;
        iout[p + 2] = x > y ? 1 : 0;
        iout[p + 3] = x >= y ? 1 : 0;
        iout[p + 4] = x == y ? 1 : 0;
        iout[p + 5] = x != y ? 1 : 0;
        iout[p + 6] = (int) x;
        iout[p + 7] = (int) (x * y);
        iout[p + 8] = (byte) x;
        iout[p + 9] = (char) x;
        int q = g * 3;
        lout[q] = (long) x;
        lout[q + 1] = (long) (x + y);
        lout[q + 2] = (long) (double) x;
        dout[q] = x;
        dout[q + 1] = (double) x * y;
        dout[q + 2] = (float) ((double) x + y);
    }

    @Kernel
    public static void doubleOps(@Global double[] a, @Global double[] b, @Global double[] out, @Global int[] iout,
                                 @Global long[] lout, @Global float[] fout) {
        int g = Gpu.getGlobalId(0);
        double x = a[g];
        double y = b[g];
        int o = g * 12;
        out[o] = x + y;
        out[o + 1] = x - y;
        out[o + 2] = x * y;
        out[o + 3] = x / y;
        out[o + 4] = x % y;
        out[o + 5] = -x;
        out[o + 6] = Gpu.max(x, y);
        out[o + 7] = Gpu.min(x, y);
        out[o + 8] = Gpu.sqrt(x);
        out[o + 9] = Gpu.sin(x);
        out[o + 10] = Gpu.cos(x);
        out[o + 11] = Gpu.fma(x, y, x);
        int p = g * 8;
        iout[p] = x < y ? 1 : 0;
        iout[p + 1] = x <= y ? 1 : 0;
        iout[p + 2] = x > y ? 1 : 0;
        iout[p + 3] = x >= y ? 1 : 0;
        iout[p + 4] = x == y ? 1 : 0;
        iout[p + 5] = x != y ? 1 : 0;
        iout[p + 6] = (int) x;
        iout[p + 7] = (short) y;
        int q = g * 2;
        lout[q] = (long) x;
        lout[q + 1] = (long) (x * y);
        fout[q] = (float) x;
        fout[q + 1] = (float) (x + y);
    }

    @Kernel
    public static void fromIntegers(@Global int[] ints, @Global long[] longs, @Global float[] fout,
                                    @Global double[] dout) {
        int g = Gpu.getGlobalId(0);
        int i = ints[g];
        long l = longs[g];
        int o = g * 4;
        fout[o] = i;
        fout[o + 1] = l;
        fout[o + 2] = i * 0.5f;
        fout[o + 3] = l + 0.25f;
        dout[o] = i;
        dout[o + 1] = l;
        dout[o + 2] = l * 0.5;
        dout[o + 3] = i / 3.0;
    }
}
