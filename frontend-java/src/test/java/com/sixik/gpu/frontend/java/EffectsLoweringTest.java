package com.sixik.gpu.frontend.java;

import org.junit.jupiter.api.Test;

/**
 * Нормализация эффектов (правила IR-E01/E02): {@code i++}, присваивания и вызовы с эффектами внутри выражений,
 * ленивые {@code &&}/{@code ||}/{@code ?:} с эффектами, составные присваивания, порядок вычисления слева направо.
 */
class EffectsLoweringTest {

    @Test
    void sideEffectsInsideExpressions() {
        Golden.assertLowers("effects.ir.txt", """
                package demo;

                import com.sixik.gpu.api.annotations.DeviceFunction;
                import com.sixik.gpu.api.annotations.Global;
                import com.sixik.gpu.api.annotations.Kernel;

                public final class Effects {
                    @DeviceFunction
                    static int bump(@Global int[] a, int i) {
                        a[i] += 1;
                        return a[i];
                    }

                    @Kernel
                    public static void postfixIndex(@Global int[] a) {
                        int i = 0;
                        a[i++] = i;
                    }

                    @Kernel
                    public static void readThenWrite(@Global int[] a) {
                        int i = 0;
                        int j = a[i] + a[i++];
                        a[0] = j;
                    }

                    @Kernel
                    public static void compound(@Global int[] b) {
                        int i = 1;
                        b[i] += i++;
                    }

                    @Kernel
                    public static void shortCircuit(@Global int[] a, int n) {
                        int i = 0;
                        boolean ok = i < n && a[i++] > 3;
                        boolean ok2 = i > 0 || a[i++] > 1;
                        a[0] = ok ? 1 : 0;
                        a[1] = ok2 ? 1 : 0;
                    }

                    @Kernel
                    public static void ternaryEffects(@Global int[] a, int n) {
                        int i = 0;
                        int v = n > 0 ? a[i++] : a[i--];
                        a[0] = v;
                    }

                    @Kernel
                    public static void calls(@Global int[] a) {
                        int s = bump(a, 0) + bump(a, 1);
                        int t = bump(a, 2) * a[2];
                        a[3] = s + t;
                    }

                    @Kernel
                    public static void assignAsValue(@Global int[] a) {
                        int i = 0;
                        int j = (i = 3) + 1;
                        a[0] = j = 5;
                        int k = i++ + ++i;
                        a[1] = k;
                    }

                    @Kernel
                    public static void loopsWithEffects(@Global int[] a, int n) {
                        int i = 0;
                        while (i++ < n) {
                            a[i] = i;
                        }
                        do {
                            a[0] += 1;
                        } while (a[--i] > 0);
                    }
                }
                """);
    }
}
