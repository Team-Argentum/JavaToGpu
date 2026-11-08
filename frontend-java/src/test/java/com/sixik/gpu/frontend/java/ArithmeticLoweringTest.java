package com.sixik.gpu.frontend.java;

import org.junit.jupiter.api.Test;

/** Арифметика Java: числовое повышение типов, сдвиги, приведения, булевы массивы, константы. */
class ArithmeticLoweringTest {

    @Test
    void promotionShiftsAndCasts() {
        Golden.assertLowers("arithmetic.ir.txt", """
                package demo;

                import com.sixik.gpu.api.Gpu;
                import com.sixik.gpu.api.annotations.Global;
                import com.sixik.gpu.api.annotations.Kernel;

                public final class Arith {
                    static final int K = 5;
                    static final long BIG = 1L << 40;

                    @Kernel
                    public static void mixed(@Global int[] ia, @Global long[] la, @Global float[] fa, @Global double[] da) {
                        int i = Gpu.getGlobalId(0);
                        int a = ia[i];
                        long l = la[i];
                        float f = fa[i];
                        double d = da[i];
                        la[i] = a * l;
                        fa[i] = f / 2 + a;
                        da[i] = f * d - a % 3;
                        ia[i] = (int) d;
                        fa[i] = (float) a;
                    }

                    @Kernel
                    public static void shifts(@Global int[] ia, @Global long[] la) {
                        int i = Gpu.getGlobalId(0);
                        int a = ia[i];
                        long l = la[i];
                        ia[i] = a >> 2;
                        ia[i] = a >>> 3;
                        la[i] = l << 1;
                        ia[i] = a << l;
                        la[i] = l >>> a;
                    }

                    @Kernel
                    public static void unary(@Global int[] ia, @Global float[] fa) {
                        int i = Gpu.getGlobalId(0);
                        int a = ia[i];
                        float f = fa[i];
                        ia[i] = -a;
                        ia[i] = ~a;
                        fa[i] = +f;
                        fa[i] = -f;
                    }

                    @Kernel
                    public static void narrow(@Global byte[] ba, @Global char[] ca, @Global short[] sa, @Global int[] ia) {
                        int i = Gpu.getGlobalId(0);
                        ba[i] = (byte) (ba[i] + 1);
                        ba[i] += 2;
                        ia[i] = (ba[i] & 0xFF) + ca[i];
                        sa[i] = (short) ia[i];
                        ca[i]++;
                    }

                    @Kernel
                    public static void booleans(@Global boolean[] flags, @Global int[] ia) {
                        int i = Gpu.getGlobalId(0);
                        boolean t = flags[i];
                        flags[i] = !t && ia[i] > 0;
                        flags[i] = t | ia[i] > 1;
                        flags[i] = t ^ (ia[i] == 3);
                    }

                    @Kernel
                    public static void constants(@Global int[] ia, @Global long[] la) {
                        int i = Gpu.getGlobalId(0);
                        ia[i] = K * 2;
                        la[i] = BIG + Integer.MAX_VALUE;
                        ia[i] = ia[i] + 'a';
                    }
                }
                """);
    }
}
