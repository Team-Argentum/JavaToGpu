package com.sixik.gpu.ir.interpreter.samples;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;

/** Типы хранения {@code byte/short/char/boolean}: повышение до {@code int}, сужение, запись обратно. */
public class SmallTypeKernels {

    @Kernel
    public static void storage(@Global byte[] bytes, @Global short[] shorts, @Global char[] chars,
                               @Global boolean[] flags, @Global int[] out) {
        int g = Gpu.getGlobalId(0);
        byte b = bytes[g];
        short s = shorts[g];
        char c = chars[g];
        boolean f = flags[g];
        int o = g * 8;
        out[o] = b;
        out[o + 1] = s;
        out[o + 2] = c;
        out[o + 3] = f ? 1 : 0;
        out[o + 4] = b + s + c;
        out[o + 5] = (byte) (b + 100);
        out[o + 6] = (short) (c * 3);
        out[o + 7] = (char) (s - 1);
        bytes[g] = (byte) (b * 3);
        shorts[g] = (short) (s + c);
        chars[g] = (char) (c + b);
        flags[g] = !f && b > 0;
    }

    @Kernel
    @SuppressWarnings("lossy-conversions") // неявное сужение в составном присваивании - предмет проверки
    public static void compound(@Global int[] out) {
        char c = 'a';
        c += 2;
        c++;
        byte bb = (byte) 200;
        bb >>= 1;
        short s = (short) (bb << 3);
        s ^= 0x0F;
        s -= 40000;
        out[0] = c - 'a';
        out[1] = bb;
        out[2] = s;
    }
}
