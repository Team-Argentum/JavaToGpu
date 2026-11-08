package com.sixik.gpu.frontend.java;

import org.junit.jupiter.api.Test;

/** Управление потоком и device-функции: if, циклы, switch, break/continue, вызовы, массивы, параметры. */
class ControlFlowLoweringTest {

    @Test
    void loopsAndBranches() {
        Golden.assertLowers("control.ir.txt", """
                package demo;

                import com.sixik.gpu.api.Gpu;
                import com.sixik.gpu.api.annotations.Global;
                import com.sixik.gpu.api.annotations.Kernel;

                public final class Control {
                    @Kernel
                    public static void forLoop(@Global int[] data, int n) {
                        int acc = 0;
                        for (int i = 0; i < n; i++) {
                            if (data[i] < 0) {
                                continue;
                            }
                            if (acc > 100) {
                                break;
                            }
                            acc += data[i];
                        }
                        data[0] = acc;
                    }

                    @Kernel
                    public static void whileAndDo(@Global int[] data) {
                        int k = 0;
                        while (k < 4) {
                            k++;
                        }
                        do {
                            data[k] = k;
                            k--;
                        } while (k > 0);
                    }

                    @Kernel
                    public static void branches(@Global int[] data) {
                        int id = Gpu.getGlobalId(0);
                        if (id == 0) {
                            data[0] = 1;
                        } else if (id == 1) {
                            data[1] = 2;
                        } else {
                            data[id] = 3;
                        }
                        data[id] = id > 5 ? id : -id;
                        for (;;) {
                            break;
                        }
                    }

                    @Kernel
                    public static void switches(@Global int[] data, @Global char[] chars) {
                        int id = Gpu.getGlobalId(0);
                        int acc = 0;
                        switch (id % 3) {
                            case 0:
                                acc += 1;
                            case 1:
                            case 2:
                                acc += 2;
                                break;
                            default:
                                acc = 0;
                        }
                        switch (chars[id]) {
                            case 'a' -> acc += 10;
                            case 'b', 'c' -> {
                                acc += 20;
                            }
                            default -> acc += 30;
                        }
                        data[id] = acc;
                    }

                    @Kernel
                    public static void parameters(@Global int[] data, int a, long b) {
                        a += 1;
                        a *= 2;
                        data[0] = a + (int) b;
                    }
                }
                """);
    }

    @Test
    void deviceFunctionsAndPrivateArrays() {
        Golden.assertLowers("helpers.ir.txt", """
                package demo;

                import com.sixik.gpu.api.Gpu;
                import com.sixik.gpu.api.annotations.DeviceFunction;
                import com.sixik.gpu.api.annotations.Global;
                import com.sixik.gpu.api.annotations.Kernel;

                public final class Helpers {
                    @DeviceFunction
                    static float lerp(float a, float b, float t) {
                        return a + (b - a) * t;
                    }

                    @DeviceFunction
                    static int clampi(int v, int lo, int hi) {
                        return Gpu.min(Gpu.max(v, lo), hi);
                    }

                    @DeviceFunction
                    static void put(@Global float[] out, int i, float v) {
                        out[i] = v;
                    }

                    @DeviceFunction
                    static float twice(float v) {
                        return lerp(v, v, 2.0f) * 2.0f;
                    }

                    @Kernel
                    public static void helpers(@Global float[] in, @Global float[] out) {
                        int id = Gpu.getGlobalId(0);
                        float x = lerp(in[id], 1.0f, 0.5f) + twice(in[id]);
                        put(out, clampi(id, 0, 9), x);
                    }

                    @Kernel
                    public static void privateArray(@Global int[] out) {
                        int[] tmp = new int[4];
                        int id = Gpu.getGlobalId(0);
                        for (int i = 0; i < tmp.length; i++) {
                            tmp[i] = id * i;
                        }
                        out[id] = tmp[0] + tmp[3];
                    }
                }
                """);
    }
}
