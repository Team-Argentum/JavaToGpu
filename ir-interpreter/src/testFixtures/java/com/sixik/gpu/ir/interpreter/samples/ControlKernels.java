package com.sixik.gpu.ir.interpreter.samples;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.DeviceFunction;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;

/**
 * Управление потоком и нормализация эффектов: вложенные циклы с {@code break/continue}, {@code switch} с
 * проваливанием и {@code default} посередине, порядок вычислений Java ({@code a[i++] = i++ + ++i}), составные
 * присваивания, ленивые {@code &&}, {@code ||}, {@code ?:}.
 */
public class ControlKernels {

    @DeviceFunction
    static boolean isEven(int x) {
        return (x & 1) == 0;
    }

    @DeviceFunction
    static int isqrt(int x) {
        if (x < 0) {
            return -1;
        }
        for (int i = 0; i < 50; i++) {
            if (i * i > x) {
                return i - 1;
            }
        }
        return 99;
    }

    @DeviceFunction
    static long collatz(long n) {
        long steps = 0;
        while (n != 1 && steps < 1000) {
            if ((n & 1) == 0) {
                n >>= 1;
            } else {
                n = 3 * n + 1;
            }
            steps++;
        }
        return steps;
    }

    @Kernel
    @SuppressWarnings("fallthrough") // проваливание в switch - предмет проверки
    public static void loops(@Global int[] in, @Global int[] out) {
        int g = Gpu.getGlobalId(0);
        int n = in[g];
        int acc = 0;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (j == 2) {
                    continue;
                }
                if (i == 6) {
                    break;
                }
                switch ((n + i + j) & 3) {
                    case 0:
                        continue;
                    case 1:
                        acc += 1;
                    case 2:
                        acc += 10;
                        break;
                    default:
                        acc += 100;
                    case 3:
                        acc += 1000;
                }
                acc += j;
            }
        }
        int k = 0;
        while (k < (n & 7)) {
            k++;
            if (k == 4) {
                break;
            }
        }
        int d = 0;
        do {
            d += 2;
            if (d > 6) {
                continue;
            }
            d++;
        } while (d < 20);
        out[g * 3] = acc;
        out[g * 3 + 1] = k;
        out[g * 3 + 2] = d;
    }

    @Kernel
    public static void effects(@Global int[] in, @Global int[] scratch, @Global int[] out) {
        int g = Gpu.getGlobalId(0);
        int i = in[g] & 3;
        int b = g * 4;
        scratch[b + i++] = i++ + ++i;
        int x = in[g];
        int y = x++ + x++;
        int z = x > 3 && y++ > 0 ? y : -y;
        int w = 0;
        boolean q = w++ > 0 || w++ > 0;
        int c1;
        int c2;
        c1 = c2 = x * 2;
        int o = g * 8;
        out[o] += x;
        out[o]++;
        --out[o + 1];
        out[o + 2] <<= 2;
        out[o + 3] = y;
        out[o + 4] = z + w + (q ? 1000 : 0);
        out[o + 5] = c1 + c2 + i;
        out[o + 6] = isEven(x) ? isqrt(x) : -isqrt(x);
        out[o + 7] = (int) collatz((x & 1023) | 1);
    }

    @Kernel
    public static void parameters(int a, long b, float c, double d, @Global double[] out) {
        a += 1;
        a *= 2;
        b -= a;
        c /= 3;
        d = d * c + b;
        out[0] = a + b;
        out[1] = c * d;
        out[2] = d;
        out[3] = Gpu.getGlobalSize(0);
    }

    @Kernel
    public static void earlyReturn(@Global int[] in, @Global int[] out) {
        int g = Gpu.getGlobalId(0);
        if (in[g] < 0) {
            out[g] = -1;
            return;
        }
        for (int i = 0; i < 100; i++) {
            if (i * i >= in[g]) {
                out[g] = i;
                return;
            }
        }
        out[g] = 100;
    }
}
