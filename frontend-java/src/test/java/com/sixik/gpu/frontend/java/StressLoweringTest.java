package com.sixik.gpu.frontend.java;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Широкий набор допустимых конструкций: проверяется, что фронтенд не падает, не выдаёт диагностик и что каждый
 * построенный модуль проходит валидатор {@code ir}. Содержимое IR проверяют golden-тесты соседних классов.
 */
class StressLoweringTest {

    private static final String SOURCE = """
            package demo;

            import com.sixik.gpu.api.Gpu;
            import com.sixik.gpu.api.annotations.DeviceFunction;
            import com.sixik.gpu.api.annotations.Global;
            import com.sixik.gpu.api.annotations.Kernel;

            public final class Stress {
                static final double HALF = Math.PI / 2;
                static final int MASK = (1 << 4) - 1;

                @DeviceFunction
                static boolean isEven(int x) { return (x & 1) == 0; }

                @DeviceFunction
                static double hyp(double a, double b) { return Gpu.sqrt(Gpu.fma(a, a, b * b)); }

                @DeviceFunction
                static long mix(long a, int b, float c) {
                    if (b > 0) {
                        return a * b;
                    }
                    return (long) c;
                }

                @DeviceFunction
                static int early(int x) {
                    if (x < 0) return -1;
                    for (int i = 0; i < x; i++) {
                        if (i * i > x) return i;
                    }
                    return x;
                }

                @DeviceFunction
                static float sum(@Global float[] a, int n) {
                    float s = 0;
                    for (int i = 0; i < n; i++) s += a[i];
                    return s;
                }

                @Kernel
                public static void mixedTypes(@Global double[] out, @Global float[] data) {
                    int id = Gpu.getGlobalId(0);
                    int a = 1, b = 2;
                    var v = id * 2L;
                    final int n = 4;
                    int[] t = new int[n];
                    long r = isEven(id) ? mix(v, a, 1.5f) : early(b);
                    double d = hyp(a, b) + HALF + (id > 0 ? 1 : 2.5);
                    for (int i = 0, j = 10; i < j; i++, j--) {
                        t[i & 3] += j;
                    }
                    out[id] = d + r + t[MASK & 1] + sum(data, 3);
                }

                @Kernel
                public static void nestedLoops(@Global int[] out) {
                    int acc = 0;
                    for (int i = 0; i < 4; i++) {
                        for (int j = 0; j < 4; j++) {
                            if (j == 2) continue;
                            if (i == 3) break;
                            switch (j) {
                                case 0:
                                    continue;
                                case 1:
                                    break;
                                default:
                                    acc++;
                            }
                            acc += j;
                        }
                    }
                    int k;
                    k = 5;
                    out[0] = acc + k;
                }

                @Kernel
                public static void smallTypes(@Global int[] out) {
                    char c = 'a';
                    c += 2;
                    c++;
                    int code = c - 'a';
                    byte bb = (byte) 200;
                    bb >>= 1;
                    short s = (short) (bb << 3);
                    s ^= 0x0F;
                    out[0] = code + s;
                }

                @Kernel
                public static void booleanLogic(@Global int[] out) {
                    int id = Gpu.getGlobalId(0);
                    boolean p = id > 1, q = id < 8;
                    boolean r = (p && q) || (!p && !q) ^ p;
                    r &= q;
                    r |= p;
                    out[id] = r ? 1 : 0;
                }

                @Kernel
                public static void compoundOps(@Global int[] ints, @Global long[] longs, @Global double[] doubles) {
                    int x = ints[0];
                    x += 1; x -= 1; x *= 2; x /= 2; x %= 5; x <<= 1; x >>= 1; x >>>= 1; x &= 7; x |= 8; x ^= 1;
                    ints[0] = x;
                    long y = longs[0];
                    y += 1; y -= 1; y *= 2; y /= 2; y %= 5; y <<= 1; y >>= 1; y >>>= 1; y &= 7; y |= 8; y ^= 1;
                    longs[0] = y;
                    double z = doubles[0];
                    z += 1; z -= 1; z *= 2; z /= 2; z %= 5;
                    doubles[0] = z;
                    ints[1] += 2.7;
                    ints[1]--;
                    --ints[2];
                    longs[1] <<= 3;
                }

                @Kernel
                public static void callsInConditions(@Global int[] a) {
                    int i = 0;
                    while (isEven(i) && i < 10) {
                        i += 2;
                    }
                    if (early(i) > 2 || isEven(i + 1)) {
                        a[0] = i;
                    }
                    a[1] = isEven(i) ? early(i) : -early(i);
                }
            }
            """;

    @Test
    void wideSubsetLowersToValidIr() {
        FrontendHarness.Outcome r = FrontendHarness.compile(SOURCE);
        assertTrue(r.success(), r.describe());
        assertTrue(r.gpu().isEmpty(), r.describe());
        assertEquals(6, r.modules().size(), "one module per kernel");
        for (IrModule m : r.modules()) {
            Validator.Report v = Validator.validate(m);
            assertFalse(v.hasErrors(), v.diagnostics().toString());
            assertTrue(v.diagnostics().isEmpty(), v.diagnostics().toString());
        }
    }
}
