package com.sixik.gpu.ir.interpreter.samples;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.DeviceFunction;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;

/**
 * Ядра {@code exact} (FRONTEND_ASM_RUNTIME.md, 18.2-S6): вещественная арифметика и JDK-интринсики, результат которых
 * побитово равен JVM на любом уровне оптимизации и на устройстве с корректно округлёнными делением и {@code sqrt}.
 */
public class ExactKernels {

    /** Линейная интерполяция: общая функция, достижимая из ядра {@code exact}. */
    @DeviceFunction
    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** F64: арифметика, деление, {@code sqrt}, {@code fma} и точные методы {@code Math}, битовое представление. */
    @Kernel(compilationProperties = "exact=true")
    public static void doubles(@Global double[] a, @Global double[] b, @Global double[] out, @Global long[] bits) {
        int g = Gpu.getGlobalId(0);
        double x = a[g];
        double y = b[g];
        int o = g * 20;
        out[o] = x * y + x / y - lerp(x, y, 0.25);
        out[o + 1] = Math.floor(x);
        out[o + 2] = Math.ceil(x);
        out[o + 3] = Math.rint(x * 3.5);
        out[o + 4] = Math.abs(x);
        out[o + 5] = Math.sqrt(x);
        out[o + 6] = Math.min(x, y);
        out[o + 7] = Math.max(x, y);
        out[o + 8] = Math.fma(x, y, x);
        out[o + 9] = Math.copySign(x, y);
        out[o + 10] = Math.nextUp(x);
        out[o + 11] = Math.nextDown(x);
        out[o + 12] = Math.nextAfter(x, y);
        out[o + 13] = Math.signum(x);
        out[o + 14] = StrictMath.floor(x * 1.0e-3) + x % y;
        out[o + 15] = Double.longBitsToDouble(Double.doubleToRawLongBits(y) ^ 1L);
        out[o + 16] = x - Math.floor(x) * 0.5 > y ? Math.ceil(y) : Math.floor(y);
        out[o + 17] = Math.floorMod((long) x, 7L);
        out[o + 18] = (float) x * (float) y;
        out[o + 19] = (long) Math.rint(x) >> 2;
        bits[g * 2] = Double.doubleToRawLongBits(x);
        bits[g * 2 + 1] = Double.doubleToRawLongBits(Math.nextUp(y)) - Double.doubleToRawLongBits(y);
    }

    /** F32 (деление - корректно округлённое) и целые JDK-интринсики {@code Math}, {@code Integer}, {@code Long}. */
    @Kernel(compilationProperties = "exact=true")
    public static void floatsAndInts(@Global float[] a, @Global float[] b, @Global int[] ia, @Global long[] la,
                                     @Global float[] out, @Global int[] iout, @Global long[] lout) {
        int g = Gpu.getGlobalId(0);
        float x = a[g];
        float y = b[g];
        int o = g * 12;
        out[o] = x / y + x * y;
        out[o + 1] = (float) Math.sqrt(x);
        out[o + 2] = Math.abs(x);
        out[o + 3] = Math.copySign(x, y);
        out[o + 4] = Math.nextUp(x);
        out[o + 5] = Math.nextDown(x);
        out[o + 6] = Math.signum(y);
        out[o + 7] = Math.min(x, y);
        out[o + 8] = Math.max(x, y);
        out[o + 9] = Math.fma(x, y, y);
        out[o + 10] = Float.intBitsToFloat(Float.floatToRawIntBits(x) & 0x7fffffff);
        out[o + 11] = (float) Math.floor(x);
        int i = ia[g];
        int j = ia[g + 1];
        int p = g * 12;
        iout[p] = Math.abs(i);
        iout[p + 1] = Math.floorDiv(i, j);
        iout[p + 2] = Math.floorMod(i, j);
        iout[p + 3] = Integer.rotateLeft(i, j);
        iout[p + 4] = Integer.rotateRight(i, j);
        iout[p + 5] = Integer.numberOfLeadingZeros(i >>> (j & 31));
        iout[p + 6] = Integer.numberOfTrailingZeros(i << (j & 31));
        iout[p + 7] = Integer.bitCount(i);
        iout[p + 8] = Integer.compareUnsigned(i, j);
        iout[p + 9] = Math.min(i, j) + Math.max(i, j);
        iout[p + 10] = Float.floatToRawIntBits(y);
        iout[p + 11] = Long.numberOfLeadingZeros(la[g]) + Long.numberOfTrailingZeros(la[g] >>> 40)
                + Long.bitCount(la[g]) + Long.compareUnsigned(la[g], la[g + 1]);
        long l = la[g];
        long m = la[g + 1];
        int q = g * 6;
        lout[q] = Math.abs(l);
        lout[q + 1] = Math.floorDiv(l, m);
        lout[q + 2] = Math.floorMod(l, m);
        lout[q + 3] = Long.rotateLeft(l, i);
        lout[q + 4] = Long.rotateRight(l, j);
        lout[q + 5] = Math.multiplyHigh(l, m);
    }
}
