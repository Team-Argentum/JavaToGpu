package com.sixik.gpu.ir.interpreter.samples;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.DeviceFunction;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.annotations.Local;
import com.sixik.gpu.api.annotations.WorkGroup;
import com.sixik.gpu.api.types.GlobalIntPtr;
import com.sixik.gpu.api.types.GlobalLongPtr;
import com.sixik.gpu.api.types.LocalIntPtr;

/**
 * Local-память, барьеры и атомики (M10): редукция, транспонирование через тайл, префиксная сумма, гистограмма и счётчики.
 *
 * <p>Эти ядра <b>нельзя</b> сверять с {@code $jvm}: на JVM work-item-ы идут по очереди и барьер не выполним. Эталон -
 * результат, посчитанный на хосте обычным Java-кодом; интерпретатор и устройство сверяются с ним и между собой.</p>
 */
public class LocalKernels {

    /** Размер группы редукции. */
    public static final int REDUCE_GROUP = 64;
    /** Сторона тайла транспонирования. */
    public static final int TILE = 8;
    /** Размер группы префиксной суммы. */
    public static final int SCAN_GROUP = 16;
    /** Размер группы гистограммы и число корзин. */
    public static final int HIST_GROUP = 32;
    /** Число корзин гистограммы. */
    public static final int BINS = 16;

    /** Один шаг дерева редукции: барьер внутри вызываемой функции с local-массивом в параметре. */
    @DeviceFunction
    static void fold(@Local int[] tile, int lid, int s) {
        if (lid < s) {
            tile[lid] += tile[lid + s];
        }
        Gpu.barrier();
    }

    /** Сумма каждой группы из 64 элементов: {@code partial[group] = sum(in[group*64 .. +64))}. */
    @Kernel
    @WorkGroup(x = 64)
    public static void reduceSum(@Global int[] in, @Global int[] partial) {
        @Local int[] tile = new int[64];
        int lid = Gpu.getLocalId(0);
        tile[lid] = in[Gpu.getGlobalId(0)];
        Gpu.barrier();
        for (int s = 32; s > 0; s >>= 1) {
            fold(tile, lid, s);
        }
        if (lid == 0) {
            partial[Gpu.getGroupId(0)] = tile[0];
        }
    }

    /** Транспонирование матрицы {@code width x height} блоками 8x8 через local-тайл (запуск 2D, группа 8x8). */
    @Kernel
    @WorkGroup(x = 8, y = 8)
    public static void transpose(@Global float[] out, @Global float[] in, int width, int height) {
        @Local float[] tile = new float[64];
        int lx = Gpu.getLocalId(0);
        int ly = Gpu.getLocalId(1);
        tile[ly * 8 + lx] = in[Gpu.getGlobalId(1) * width + Gpu.getGlobalId(0)];
        Gpu.barrier();
        int ox = Gpu.getGroupId(1) * 8 + lx;
        int oy = Gpu.getGroupId(0) * 8 + ly;
        out[oy * height + ox] = tile[lx * 8 + ly];
    }

    /** Включающая префиксная сумма в группе из 16 (Hillis-Steele): чтение и запись разделены барьерами. */
    @Kernel
    @WorkGroup(x = 16)
    public static void scan(@Global int[] in, @Global int[] out) {
        @Local int[] a = new int[16];
        int lid = Gpu.getLocalId(0);
        a[lid] = in[Gpu.getGlobalId(0)];
        Gpu.barrier();
        for (int s = 1; s < 16; s <<= 1) {
            int v = a[lid];
            if (lid >= s) {
                v += a[lid - s];
            }
            Gpu.barrier();
            a[lid] = v;
            Gpu.barrier();
        }
        out[Gpu.getGlobalId(0)] = a[lid];
    }

    /** Гистограмма по младшим 4 битам: корзины в local-памяти (атомики), затем слияние в global. */
    @Kernel
    @WorkGroup(x = 32)
    public static void histogram(@Global int[] data, @Global int[] bins) {
        @Local int[] local = new int[16];
        int lid = Gpu.getLocalId(0);
        if (lid < 16) {
            local[lid] = 0;
        }
        Gpu.barrier();
        Gpu.atomicAdd(LocalIntPtr.of(local, data[Gpu.getGlobalId(0)] & 15), 1);
        Gpu.barrier();
        if (lid < 16) {
            Gpu.atomicAdd(GlobalIntPtr.of(bins, lid), local[lid]);
        }
    }

    /**
     * Атомики над global без local-памяти. {@code counters}: [0] счётчик, [1] максимум, [2] минимум, [3] ИЛИ, [4] исключающее
     * ИЛИ, [5] И, [6] убывающий счётчик; прежние значения убывающего счётчика сохраняются в {@code olds}.
     */
    @Kernel
    public static void counters(@Global int[] data, @Global int[] counters, @Global int[] olds) {
        int g = Gpu.getGlobalId(0);
        int v = data[g];
        Gpu.atomicAdd(GlobalIntPtr.of(counters, 0), 1);
        Gpu.atomicMax(GlobalIntPtr.of(counters, 1), v);
        Gpu.atomicMin(GlobalIntPtr.of(counters, 2), v);
        Gpu.atomicOr(GlobalIntPtr.of(counters, 3), v);
        Gpu.atomicXor(GlobalIntPtr.of(counters, 4), v);
        Gpu.atomicAnd(GlobalIntPtr.of(counters, 5), v);
        olds[g] = Gpu.atomicSub(GlobalIntPtr.of(counters, 6), 1);
    }

    /** 64-битные атомики: сумма, максимум и обмен ({@code sums}: [0] сумма, [1] максимум, [2] последнее записанное). */
    @Kernel
    public static void wide(@Global long[] data, @Global long[] sums) {
        long v = data[Gpu.getGlobalId(0)];
        Gpu.atomicAdd(GlobalLongPtr.of(sums, 0), v);
        Gpu.atomicMax(GlobalLongPtr.of(sums, 1), v);
        Gpu.atomicXchg(GlobalLongPtr.of(sums, 2), v);
    }
}
