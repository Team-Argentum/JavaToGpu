package com.sixik.gpu.app;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.*;
import com.sixik.gpu.api.enums.InlinePolicy;

/** Примеры GPU-ядер для ручных экспериментов и интеграционных тестов приложения. */
public final class Kernels {

    /** Запрещает создание экземпляров набора ядер. */
    private Kernels() {
    }

    /** Безразмерная добавка helper-функции, равная 50. */
    @Constant
    public static final int CONST = 50;

    /**
     * Складывает элементы двух массивов с результатом экспериментальной device-функции math.
     *
     * <p>Формула: {@code output[i] = (a + b) + (a * s + b + CONST)}, где
     * {@code a = left[i]}, {@code b = right[i]}, {@code s = a > 0 ? 1 : 0}, а {@code i} — глобальный
     * индекс work-item по оси X. Размер запуска по умолчанию равен длине {@code left}.
     * Значения массивов безразмерные; сложение следует правилам IEEE 754 для {@code float}.
     *
     * @param left первый входной массив, ненулевой длины
     * @param right второй входной массив длиной не меньше {@code left.length}
     * @param output выходной массив длиной не меньше {@code left.length}
     */
    @Kernel
    public static void vectorAdd(@Global float[] left, @Global float[] right, @Global float[] output) {
        int i = Gpu.getGlobalId(0);
        output[i] = left[i] + right[i] + math(left[i], right[i]);
    }

    @DeviceFunction
    private static float math(float a, float b) {
        int i = 0;

        do {
            if (i < a) {
                i++;
            }

            return a * i + b + CONST;
        } while (true);
    }

    @Struct
    public static class BlockPos {
        public float x;
        public float y;
        public float z;

        public BlockPos() {

        }

        public BlockPos(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public String toString() {
            return "BlockPos{" +
                    "x=" + x +
                    ", y=" + y +
                    ", z=" + z +
                    '}';
        }
    }

    @Kernel
    public static void calculatePositionRandom(@Global BlockPos[] pos, @Global BlockPos[] out) {
        int id = Gpu.getGlobalId(0);

        BlockPos pb = new BlockPos(pos[0].x, pos[0].y, pos[0].z);

        out[id].x = pos[id].x * 2 + pb.z;
        out[id].y = pos[id].y % 20 + pb.y;
        out[id].z = pos[id].z + 8 + pb.x;
    }
}
