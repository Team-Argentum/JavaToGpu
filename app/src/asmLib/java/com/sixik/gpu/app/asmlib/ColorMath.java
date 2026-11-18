package com.sixik.gpu.app.asmlib;

import com.sixik.gpu.api.annotations.DeviceFunction;
import com.sixik.gpu.api.types.Float4;

/**
 * «Сторонняя библиотека» примера {@code asmExample}: собирается в отдельный jar без исходников. Annotation processor
 * не может связать её device-функции ({@code GPU0114}), а {@code frontend-asm} берёт их байткод с classpath.
 */
public final class ColorMath {

    /** Запрещает создание экземпляров библиотеки функций. */
    private ColorMath() {
    }

    /**
     * Яркость цвета по Rec. 709: {@code 0.2126 r + 0.7152 g + 0.0722 b}; альфа не учитывается.
     *
     * @param rgba цвет, компоненты в [0, 1]
     * @return яркость в [0, 1]
     */
    @DeviceFunction
    public static float luma(Float4 rgba) {
        return 0.2126f * rgba.x + 0.7152f * rgba.y + 0.0722f * rgba.z;
    }

    /**
     * Ограничение значения отрезком.
     *
     * @param v  значение
     * @param lo нижняя граница
     * @param hi верхняя граница, {@code hi >= lo}
     * @return {@code v}, прижатое к [lo, hi]
     */
    @DeviceFunction
    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }
}
