package com.sixik.gpu.api;

import com.sixik.gpu.api.annotations.Constant;
import com.sixik.gpu.api.annotations.DeviceFunction;
import com.sixik.gpu.api.annotations.Global;
import com.sixik.gpu.api.annotations.Kernel;
import com.sixik.gpu.api.annotations.Struct;
import com.sixik.gpu.api.annotations.WorkGroup;
import com.sixik.gpu.api.enums.InlinePolicy;
import com.sixik.gpu.api.types.Float2;
import com.sixik.gpu.api.types.FloatPtr;

/**
 * Синтаксис из {@code OldCodeConcept} (корень репозитория) - критерий приёмки M2: файл должен компилироваться.
 *
 * <p>Отличия от концепта: нет {@code @GpuNative}/{@code @GpuFallback}/{@code @AutoTune} (не входят в v1,
 * REWORK_ARCHITECTURE.md, 3.B), убрана опечатка {@code ;} после параметра {@code a}, вариант
 * {@code processStepsSimple} из директивы {@code fallback} описан явно.</p>
 */
public final class ConceptSample {

    private ConceptSample() {
    }

    /** Структура данных (пакуется в struct). */
    @Struct(alignment = 16)
    public static class GamePos {
        public float x;
        public float y;
        public float z;

        public GamePos() {
        }

        public GamePos(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** Точка входа. */
    @Kernel
    @WorkGroup(x = 256, y = 1, z = 1)
    public static void computeSine(
            @Global float[] input,
            @Global float[] output
    ) {
        int id = Gpu.getGlobalId(0);
        output[id] = Gpu.sin(input[id]) + 2.0f;
    }

    /** Kernel с явными директивами кодогенерации. */
    @Kernel(inline = InlinePolicy.ALWAYS, optLevel = 0, workgroup = 32, required = {"SIMD"},
            fallback = "processStepsSimple")
    public static void processSteps(
            @Global int[] input,
            @Global int[] output,
            @Global(constant = true) int a
    ) {
        int id = Gpu.getGlobalId(0);
        int value = input[id];
        int step = 0;

        do {
            value = Gpu.max(value, step) + a;
            step++;
        } while (step < 2);

        output[id] = value;
    }

    /** Вариант {@code processSteps} с той же сигнатурой (адресуется директивой {@code fallback}). */
    @Kernel
    public static void processStepsSimple(
            @Global int[] input,
            @Global int[] output,
            @Global(constant = true) int a
    ) {
        int id = Gpu.getGlobalId(0);
        output[id] = Gpu.max(Gpu.max(input[id], 0) + a, 1) + a;
    }

    private static final float LIMIT = 32.0f;

    /** Вспомогательная функция. */
    @DeviceFunction
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Работа с мутабельным скаляром по ссылке. */
    @DeviceFunction
    public static void clamp(FloatPtr ptr) {
        if (ptr.value > LIMIT) {
            ptr.value = LIMIT;
        }
    }

    /** Библиотека переиспользуемых функций и констант. */
    public static final class MathLibrary {

        private MathLibrary() {
        }

        @Constant
        public static final float VALUE = 32.0f;

        @DeviceFunction
        public static Float2 add(Float2 left, Float2 right) {
            return new Float2(left.x + right.x, left.y + right.y);
        }
    }
}
