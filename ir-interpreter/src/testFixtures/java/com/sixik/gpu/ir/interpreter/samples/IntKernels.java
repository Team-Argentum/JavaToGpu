package com.sixik.gpu.ir.interpreter.samples;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;

/**
 * Образцы с целочисленной арифметикой: work-item {@code g} обрабатывает пару {@code (a[g], b[g])} и пишет
 * {@value #WIDTH} результатов в {@code out[g * WIDTH ...]}. Все операции Java, включая оборот, сдвиги со
 * счётчиком вне {@code 0..31} и сужающие приведения.
 */
public class IntKernels {

    /** Число результатов на пару. */
    public static final int WIDTH = 24;

    @Kernel
    public static void ops(@Global int[] a, @Global int[] b, @Global int[] out) {
        int g = Gpu.getGlobalId(0);
        int x = a[g];
        int y = b[g];
        int o = g * 24;
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
        out[o + 21] = (int) ((long) x * y);
        out[o + 22] = (byte) x;
        out[o + 23] = (short) y + (char) x;
    }
}
