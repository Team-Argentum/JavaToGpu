package com.sixik.gpu.ir.interpreter.samples;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.DeviceFunction;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.types.Double2;
import com.sixik.gpu.api.types.Double4;
import com.sixik.gpu.api.types.Float2;
import com.sixik.gpu.api.types.Float3;
import com.sixik.gpu.api.types.Float4;
import com.sixik.gpu.api.types.Float8;
import com.sixik.gpu.api.types.Int2;
import com.sixik.gpu.api.types.Int4;
import com.sixik.gpu.api.types.Long2;

/** Векторы: сборка, компоненты, {@code with}, {@code shuffle}, арифметика, builtins, device-функции, массивы векторов. */
public class VectorKernels {

    /** Результатов на work-item в {@link #floatOps}. */
    public static final int FLOAT_WIDTH = 24;
    /** Результатов на work-item в {@link #intOps}. */
    public static final int INT_WIDTH = 14;

    @DeviceFunction
    static Float4 lerp(Float4 a, Float4 b, float t) {
        return a.add(b.sub(a).mul(t));
    }

    @DeviceFunction
    static float dot(Float4 a, Float4 b) {
        return a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
    }

    @DeviceFunction
    static Float4 reflectX(Float4 v) {
        return new Float4(-v.x, v.y, v.z, v.w);
    }

    @Kernel
    public static void floatOps(@Global Float4[] in, @Global Float4[] out) {
        int g = Gpu.getGlobalId(0);
        Float4 a = in[g];
        Float4 b = in[(g + 1) % in.length];
        int o = g * FLOAT_WIDTH;
        out[o] = a.add(b);
        out[o + 1] = a.sub(b);
        out[o + 2] = a.mul(b);
        out[o + 3] = a.div(b);
        out[o + 4] = a.rem(b);
        out[o + 5] = a.neg();
        out[o + 6] = a.mul(2.5f);
        out[o + 7] = a.add(1);
        out[o + 8] = Float4.splat(a.w);
        out[o + 9] = a.withX(b.w).withW(7f);
        out[o + 10] = new Float4(a.y, a.x, b.w, b.z);
        out[o + 11] = a.shuffle(3, 2, 1, 0);
        Float2 lo = a.shuffle(0, 1);
        Float3 mid = b.shuffle(1, 2, 3);
        out[o + 12] = new Float4(lo.x, lo.y, mid.x, mid.z);
        Float8 wide = a.shuffle(0, 0, 1, 1, 2, 2, 3, 3);
        out[o + 13] = new Float4(wide.s1, wide.s2, wide.s5, wide.s7);
        out[o + 14] = Gpu.sin(a);
        out[o + 15] = Gpu.cos(b);
        out[o + 16] = Gpu.sqrt(Gpu.max(a, b));
        out[o + 17] = Gpu.fma(a, b, a);
        out[o + 18] = Gpu.min(a, b);
        out[o + 19] = lerp(a, b, 0.25f);
        out[o + 20] = reflectX(a).add(reflectX(b));
        out[o + 21] = new Float4(dot(a, b), a.x * b.y - a.y * b.x, a.z, 0f);
        out[o + 22] = a.x < b.x ? a : b;
        Float4 acc = Float4.splat(0f);
        for (int i = 0; i < 3; i++) {
            acc = acc.add(in[(g + i) % in.length]).mul(0.5f);
        }
        out[o + 23] = acc;
    }

    @Kernel
    public static void intOps(@Global Int4[] in, @Global Int4[] out) {
        int g = Gpu.getGlobalId(0);
        Int4 a = in[g];
        Int4 b = in[(g + 1) % in.length];
        int o = g * INT_WIDTH;
        out[o] = a.add(b);
        out[o + 1] = a.sub(b);
        out[o + 2] = a.mul(b);
        out[o + 3] = a.div(b);
        out[o + 4] = a.rem(b);
        out[o + 5] = a.neg();
        out[o + 6] = a.not();
        out[o + 7] = a.and(b).or(a.xor(b));
        out[o + 8] = a.shl(b);
        out[o + 9] = a.shr(33);
        out[o + 10] = a.ushr(b);
        out[o + 11] = a.mul(3).add(Int4.splat(g));
        out[o + 12] = Gpu.max(a, b);
        Int2 pair = a.shuffle(3, 0);
        out[o + 13] = new Int4(pair.x, pair.y, a.z << 31, b.w >>> 1);
    }

    @Kernel
    public static void wide(@Global Double2[] d, @Global Long2[] l, @Global Double4[] outD, @Global Long2[] outL) {
        int g = Gpu.getGlobalId(0);
        Double2 a = d[g];
        Long2 x = l[g];
        outD[g] = new Double4(a.x, a.y, a.x * a.y, Gpu.sqrt(a.y * a.y));
        outD[g] = outD[g].add(Double4.splat(0.5)).withY(Gpu.max(a.x, a.y));
        outL[g] = x.mul(x).add(7L).shl(65L);
    }

    @Kernel
    public static void byValue(@Global Float4[] out, Float4 scale, Int2 offset, int n) {
        int g = Gpu.getGlobalId(0);
        out[g] = scale.mul(g + 1).add(offset.x + offset.y + n);
    }

    @Kernel
    public static void fromScalars(@Global float[] in, @Global float[] out) {
        int g = Gpu.getGlobalId(0);
        Float4 v = new Float4(in[g], in[g] * 2, 3, g);
        Float4 w = v.shuffle(3, 3, 0, 1).add(v);
        out[g * 4] = w.x;
        out[g * 4 + 1] = w.y;
        out[g * 4 + 2] = w.z;
        out[g * 4 + 3] = w.w;
    }
}
