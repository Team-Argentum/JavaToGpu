package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.Types;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Вспомогательные функции преамбулы, которые нельзя выразить одним оператором C без расхождения с Java
 * (REWORK_ARCHITECTURE.md, 8.A). В текст попадают только использованные, всегда в одном порядке.
 *
 * <ul>
 *   <li>{@code gpu_div_*}, {@code gpu_rem_*} - знаковое деление: {@code MIN / -1} в C - неопределённое поведение,
 *       у Java - {@code MIN}, а {@code MIN % -1 == 0}. Делитель {@code -1} разбирается отдельно;</li>
 *   <li>{@code gpu_<f32|f64>_to_<i32|u32|i64|u64>} - насыщающее преобразование {@code float -> int} как в Java:
 *       {@code NaN -> 0}, выход за диапазон - ближайшая граница, дробная часть отбрасывается
 *       ({@code convert_*_sat} округляет к нулю, а {@code NaN} guard-ом исключён из зависимости от драйвера);</li>
 *   <li>{@code gpu_max_*}, {@code gpu_min_*} - {@code max/min} вещественных с семантикой {@code Math.max/min}:
 *       {@code NaN} побеждает, {@code +0 > -0}. {@code fmax/fmin} возвращают не-{@code NaN} аргумент, а
 *       {@code max/min} OpenCL для {@code NaN} не определены.</li>
 * </ul>
 */
final class Helpers {

    static final int DIV_I32 = 0;
    static final int REM_I32 = 1;
    static final int DIV_I64 = 2;
    static final int REM_I64 = 3;
    static final int F32_TO_I32 = 4;
    static final int F32_TO_U32 = 5;
    static final int F32_TO_I64 = 6;
    static final int F32_TO_U64 = 7;
    static final int F64_TO_I32 = 8;
    static final int F64_TO_U32 = 9;
    static final int F64_TO_I64 = 10;
    static final int F64_TO_U64 = 11;
    static final int MAX_F32 = 12;
    static final int MIN_F32 = 13;
    static final int MAX_F64 = 14;
    static final int MIN_F64 = 15;
    private static final int COUNT = 16;

    private static final String[] TEXT = new String[COUNT];

    static {
        TEXT[DIV_I32] = "static inline int gpu_div_i32(int a, int b) { return b == -1 ? (int)(0u - (uint)a) : a / b; }";
        TEXT[REM_I32] = "static inline int gpu_rem_i32(int a, int b) { return b == -1 ? 0 : a % b; }";
        TEXT[DIV_I64] = "static inline long gpu_div_i64(long a, long b) { return b == -1 ? (long)(0ul - (ulong)a) : a / b; }";
        TEXT[REM_I64] = "static inline long gpu_rem_i64(long a, long b) { return b == -1 ? 0 : a % b; }";
        String[] floats = {"float", "double"};
        String[] fn = {"f32", "f64"};
        String[] ints = {"int", "uint", "long", "ulong"};
        String[] in = {"i32", "u32", "i64", "u64"};
        for (int f = 0; f < 2; f++) {
            for (int i = 0; i < 4; i++) {
                TEXT[F32_TO_I32 + 4 * f + i] = "static inline " + ints[i] + " gpu_" + fn[f] + "_to_" + in[i] + "("
                        + floats[f] + " x) { return isnan(x) ? 0 : convert_" + ints[i] + "_sat(x); }";
            }
        }
        TEXT[MAX_F32] = maxMin("float", "f32", "max", "0.0f", ">");
        TEXT[MIN_F32] = maxMin("float", "f32", "min", "0.0f", "<");
        TEXT[MAX_F64] = maxMin("double", "f64", "max", "0.0", ">");
        TEXT[MIN_F64] = maxMin("double", "f64", "min", "0.0", "<");
    }

    private static String maxMin(String type, String suffix, String name, String zero, String cmp) {
        boolean max = name.equals("max");
        return "static inline " + type + " gpu_" + name + "_" + suffix + "(" + type + " a, " + type + " b) {\n"
                + "    if (isnan(a)) { return a; }\n"
                + "    if (isnan(b)) { return b; }\n"
                + "    if (a == " + zero + " && b == " + zero + ") { return signbit(a) ? " + (max ? "b : a" : "a : b") + "; }\n"
                + "    return a " + cmp + " b ? a : b;\n"
                + "}";
    }

    private final boolean[] used = new boolean[COUNT];
    /** Функции над агрегатами ({@link Layouts}): имя -> определение; порядок вставки = порядок зависимостей. */
    private final Map<String, String> dynamic = new LinkedHashMap<>();

    /**
     * Отмечает функцию как использованную и возвращает её имя.
     *
     * @param helper одна из констант {@code DIV_I32 ... MIN_F64}
     * @return имя функции в тексте C
     */
    String use(int helper) {
        used[helper] = true;
        String t = TEXT[helper];
        int open = t.indexOf('(');
        int start = t.lastIndexOf(' ', open) + 1;
        return t.substring(start, open);
    }

    /**
     * @param from {@code F32} или {@code F64}
     * @param to   {@code I32, U32, I64, U64}
     * @return константа функции {@code float -> int}
     */
    static int floatToInt(int from, int to) {
        int base = from == Types.F32 ? F32_TO_I32 : F64_TO_I32;
        return base + switch (to) {
            case Types.I32 -> 0;
            case Types.U32 -> 1;
            case Types.I64 -> 2;
            default -> 3;
        };
    }

    /**
     * @return {@code true}, если использована хотя бы одна функция
     */
    boolean any() {
        for (boolean u : used) {
            if (u) {
                return true;
            }
        }
        return !dynamic.isEmpty();
    }

    /**
     * Регистрирует функцию над агрегатом. Определение строится один раз; функции, которые оно использует, регистрируются
     * внутри {@code text} и потому попадают в вывод раньше (порядок вставки).
     *
     * @param name имя функции
     * @param text источник определения
     * @return имя функции
     */
    String dynamic(String name, Supplier<String> text) {
        if (!dynamic.containsKey(name)) {
            String body = text.get();
            dynamic.put(name, body);
        }
        return name;
    }

    /**
     * Дописывает определения использованных функций в порядке констант.
     *
     * @param out приёмник
     */
    void appendTo(StringBuilder out) {
        for (int i = 0; i < COUNT; i++) {
            if (used[i]) {
                out.append(TEXT[i]).append('\n');
            }
        }
        for (String text : dynamic.values()) {
            out.append(text).append('\n');
        }
    }
}
