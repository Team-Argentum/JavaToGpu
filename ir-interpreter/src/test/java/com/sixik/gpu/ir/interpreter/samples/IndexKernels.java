package com.sixik.gpu.ir.interpreter.samples;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.annotations.WorkGroup;

/** Индексы work-item'а и размеры запуска, в том числе оси вне диапазона (OpenCL: 0 для индексов, 1 для размеров). */
public class IndexKernels {

    /** Число значений на work-item. */
    public static final int WIDTH = 23;

    @Kernel
    public static void ids(@Global int[] out) {
        int lin = Gpu.getGlobalId(0) + Gpu.getGlobalId(1) * Gpu.getGlobalSize(0)
                + Gpu.getGlobalId(2) * Gpu.getGlobalSize(0) * Gpu.getGlobalSize(1);
        int o = lin * 23;
        for (int d = 0; d < 3; d++) {
            out[o + d] = Gpu.getGlobalId(d);
            out[o + 3 + d] = Gpu.getLocalId(d);
            out[o + 6 + d] = Gpu.getGroupId(d);
            out[o + 9 + d] = Gpu.getGlobalSize(d);
            out[o + 12 + d] = Gpu.getLocalSize(d);
            out[o + 15 + d] = Gpu.getNumGroups(d);
        }
        out[o + 18] = Gpu.getWorkDim();
        out[o + 19] = Gpu.getGlobalId(3);
        out[o + 20] = Gpu.getGlobalSize(7);
        out[o + 21] = Gpu.getLocalSize(-1);
        out[o + 22] = Gpu.getNumGroups(3);
    }

    @Kernel
    @WorkGroup(x = 4, y = 2)
    public static void grouped(@Global int[] out) {
        int lin = Gpu.getGlobalId(0) + Gpu.getGlobalId(1) * Gpu.getGlobalSize(0);
        out[lin * 3] = Gpu.getGroupId(0) * 100 + Gpu.getGroupId(1);
        out[lin * 3 + 1] = Gpu.getLocalId(0) * 10 + Gpu.getLocalId(1);
        out[lin * 3 + 2] = Gpu.getLocalSize(0) * 10 + Gpu.getLocalSize(1);
    }

    @Kernel
    public static void sine(@Global float[] input, @Global float[] output) {
        int id = Gpu.getGlobalId(0);
        output[id] = Gpu.sin(input[id]) + 2.0f;
    }
}
