package com.sixik.gpu.app;

import com.sixik.gpu.api.Gpu;
import com.sixik.gpu.api.annotations.DeviceFunction;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;

/** Примеры GPU-ядер для ручных экспериментов и интеграционных тестов приложения. */
public final class Kernels {

    /** Запрещает создание экземпляров набора ядер. */
    private Kernels() {
    }

    /**
     * Складывает по одному элементу двух массивов на каждый work-item.
     *
     * <p>Формула: {@code output[i] = left[i] + right[i]}, где {@code i} — глобальный
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
        output[i] = left[i] + right[i];
    }

    @DeviceFunction
    private static float math(float a, float b) {
        int i = 0;

        do {

            if (i < a) {
                i++;
            }

            return a * i + b;
        } while (true);
    }
}
