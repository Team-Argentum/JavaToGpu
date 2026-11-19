package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Types;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Таблицы констант программы ({@code Op.CONST_TABLE}, FRONTEND_ASM_RUNTIME.md, S3, решение 18.6-5): каждая
 * различная таблица - один инициализированный {@code __constant}-массив области программы. Имена выдаются по первому
 * использованию, поэтому текст детерминирован.
 */
final class Tables {

    private final IrModule m;
    private final Names global;
    /** Ключ {@code (элемент, начало, длина)} - имя массива; порядок - порядок первого использования. */
    private final Map<String, String> names = new LinkedHashMap<>();
    private final Map<String, int[]> shapes = new LinkedHashMap<>();

    Tables(IrModule m, Names global) {
        this.m = m;
        this.global = global;
    }

    /**
     * @param node узел {@code CONST_TABLE}
     * @return имя массива в тексте C
     */
    String name(int node) {
        int elem = m.typeB(m.type(node));
        String key = elem + ":" + m.a(node) + ":" + m.b(node);
        String n = names.get(key);
        if (n == null) {
            n = global.unique(null, "tbl" + names.size());
            names.put(key, n);
            shapes.put(key, new int[]{elem, m.a(node), m.b(node)});
        }
        return n;
    }

    /** @return есть ли таблицы */
    boolean any() {
        return !names.isEmpty();
    }

    /** @return суммарный размер таблиц в байтах (для проверки предела {@code __constant} устройства) */
    long bytes() {
        long b = 0;
        for (int[] s : shapes.values()) {
            b += (long) s[2] * (Types.bits(s[0]) / 8);
        }
        return b;
    }

    /** Печатает объявления: {@code __constant int tbl0[3] = {1, 2, 3};}. */
    void appendTo(StringBuilder out) {
        for (Map.Entry<String, String> e : names.entrySet()) {
            int[] s = shapes.get(e.getKey());
            int elem = s[0];
            out.append("__constant ").append(CTypes.scalar(elem)).append(' ').append(e.getValue())
                    .append('[').append(s[2]).append("] = {");
            for (int i = 0; i < s[2]; i++) {
                if (i > 0) {
                    out.append(i % 16 == 0 ? ",\n    " : ", ");
                }
                out.append(literal(elem, m.constant(s[1] + i)));
            }
            out.append("};\n");
        }
    }

    /**
     * Литерал элемента - константное выражение C. Инициализатор области программы требует константу времени
     * компиляции, а у NVIDIA макросы {@code NAN} и {@code INFINITY} раскрываются в вызов {@code __int_as_float},
     * поэтому особые значения записываются делением литералов ({@code 0.0f / 0.0f}, {@code 1.0f / 0.0f}).
     */
    private static String literal(int elem, long v) {
        if (elem == Types.F32) {
            float f = Float.intBitsToFloat((int) v);
            if (Float.isNaN(f)) {
                return "(0.0f / 0.0f)";
            }
            if (Float.isInfinite(f)) {
                return f > 0 ? "(1.0f / 0.0f)" : "(-1.0f / 0.0f)";
            }
            return CTypes.float32((int) v);
        }
        if (elem == Types.F64) {
            double d = Double.longBitsToDouble(v);
            if (Double.isNaN(d)) {
                return "(0.0 / 0.0)";
            }
            if (Double.isInfinite(d)) {
                return d > 0 ? "(1.0 / 0.0)" : "(-1.0 / 0.0)";
            }
            return CTypes.float64(v);
        }
        return CTypes.integer(elem, v);
    }
}
