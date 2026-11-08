package com.sixik.gpu.ir.interpreter.samples;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;

/** 64-битная арифметика Java: те же операции, что в {@link IntKernels}, плюс смешанные {@code int}/{@code long}. */
public class LongKernels {

    /** Число результатов на пару. */
    public static final int WIDTH = 26;

    @Kernel
    public static void ops(@Global long[] a, @Global long[] b, @Global long[] out) {
        int g = Gpu.getGlobalId(0);
        long x = a[g];
        long y = b[g];
        int o = g * 26;
        out[o] = x + y;
        out[o + 1] = x - y;
        out[o + 2] = x * y;
        out[o + 3] = y != 0 ? x / y : 0;
        out[o + 4] = y != 0 ? x % y : 0;
        out[o + 5] = -x;
        out[o + 6] = ~x;
        out[o + 7] = x & y;
        out[o + 8] = x | y;
        out[o + 9] = x ^ y;
        out[o + 10] = x << y;
        out[o + 11] = x >> y;
        out[o + 12] = x >>> y;
        out[o + 13] = x < y ? 1 : 0;
        out[o + 14] = x <= y ? 1 : 0;
        out[o + 15] = x > y ? 1 : 0;
        out[o + 16] = x >= y ? 1 : 0;
        out[o + 17] = x == y ? 1 : 0;
        out[o + 18] = x != y ? 1 : 0;
        out[o + 19] = Gpu.max(x, y);
        out[o + 20] = Gpu.min(x, y);
        out[o + 21] = (int) x;
        out[o + 22] = (int) x >>> (int) y;
        out[o + 23] = (int) x << y;
        out[o + 24] = x << (int) y;
        out[o + 25] = (short) x;
    }
}
