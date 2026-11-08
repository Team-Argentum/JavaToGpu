package com.sixik.gpu.ir.interpreter.samples;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.DeviceFunction;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.types.ConstantFloatPtr;
import com.sixik.gpu.api.types.FloatPtr;
import com.sixik.gpu.api.types.GlobalFloatPtr;
import com.sixik.gpu.api.types.GlobalIntPtr;
import com.sixik.gpu.api.types.IntPtr;
import com.sixik.gpu.api.types.LongPtr;

/**
 * Указатели (M9): ссылки {@code FloatPtr ...} (значение по ссылке между device-функциями) и явные указатели
 * {@code GlobalFloatPtr ...}/{@code ConstantFloatPtr} с арифметикой. Результат на JVM, в интерпретаторе и на устройстве
 * обязан совпасть: ссылка - это указатель на локаль, а не копия.
 */
public class PointerKernels {

    /** Результатов {@link #refs} на work-item. */
    public static final int REFS_WIDTH = 3;
    /** Результатов {@link #order} на work-item. */
    public static final int ORDER_WIDTH = 3;
    /** Результатов {@link #longs} на work-item. */
    public static final int LONGS_WIDTH = 2;
    /** Элементов {@link #spaces}, принадлежащих одному work-item. */
    public static final int ROW = 4;

    @DeviceFunction
    static void addTo(FloatPtr acc, float v) {
        acc.value += v;
    }

    @DeviceFunction
    static void minMax(float a, float b, FloatPtr lo, FloatPtr hi) {
        lo.value = Gpu.min(a, b);
        hi.value = Gpu.max(a, b);
    }

    @DeviceFunction
    static int bump(IntPtr p, int by) {
        p.value = p.value * 3 + by;
        return p.value;
    }

    @DeviceFunction
    static long accumulate(LongPtr total, int n) {
        for (int i = 0; i < n; i++) {
            total.value += i;
        }
        return total.value;
    }

    @DeviceFunction
    static void scale(GlobalFloatPtr p, int n, float k) {
        for (int i = 0; i < n; i++) {
            p.set(i, p.get(i) * k);
        }
    }

    @DeviceFunction
    static float sum(ConstantFloatPtr t, int n) {
        float s = 0f;
        for (int i = 0; i < n; i++) {
            s += t.get(i);
        }
        return s;
    }

    /** Две выходные ссылки сразу и накопление в третьей. */
    @Kernel
    public static void refs(@Global float[] in, @Global float[] out) {
        int g = Gpu.getGlobalId(0);
        FloatPtr acc = new FloatPtr(in[g]);
        addTo(acc, 2f);
        addTo(acc, in[g] * 0.5f);
        FloatPtr lo = new FloatPtr();
        FloatPtr hi = new FloatPtr();
        minMax(in[g], acc.value, lo, hi);
        out[g * REFS_WIDTH] = acc.value;
        out[g * REFS_WIDTH + 1] = lo.value;
        out[g * REFS_WIDTH + 2] = hi.value;
    }

    /** Порядок вычисления: значение до вызова, значение после вызова, постфиксный и префиксный операторы над value. */
    @Kernel
    public static void order(@Global int[] in, @Global int[] out) {
        int g = Gpu.getGlobalId(0);
        IntPtr c = new IntPtr(in[g]);
        out[g * ORDER_WIDTH] = c.value + bump(c, 1);
        out[g * ORDER_WIDTH + 1] = bump(c, 2) + c.value;
        int x = c.value++;
        out[g * ORDER_WIDTH + 2] = x * 1000 + (++c.value);
    }

    /** Ссылка на long: цикл в вызываемой функции и составное присваивание сдвигом. */
    @Kernel
    public static void longs(@Global long[] in, @Global long[] out) {
        int g = Gpu.getGlobalId(0);
        LongPtr t = new LongPtr(in[g]);
        out[g * LONGS_WIDTH] = accumulate(t, 5);
        t.value <<= 2;
        out[g * LONGS_WIDTH + 1] = t.value;
    }

    /** Явные указатели: запись через смещённый указатель, переприсваивание указателя и чтение из constant. */
    @Kernel
    public static void spaces(@Global float[] data, @Global(constant = true) float[] table, @Global float[] out) {
        int g = Gpu.getGlobalId(0);
        GlobalFloatPtr row = GlobalFloatPtr.of(data, g * ROW);
        scale(row.plus(1), 2, 3f);
        row = row.plus(2);
        out[g] = row.get(0) + sum(ConstantFloatPtr.of(table, 1), 3);
    }

    /** Указатель в цикле: сдвиг на каждой итерации и независимая запись через второй указатель. */
    @Kernel
    public static void walk(@Global int[] in, @Global int[] out) {
        int g = Gpu.getGlobalId(0);
        GlobalIntPtr p = GlobalIntPtr.of(in, g * ROW);
        int s = 0;
        for (int i = 0; i < ROW; i++) {
            s += p.get(0) * (i + 1);
            p = p.plus(1);
        }
        GlobalIntPtr q = GlobalIntPtr.of(out, g);
        q.set(0, s);
    }
}
