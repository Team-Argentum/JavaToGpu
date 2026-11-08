package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.AbiLayout;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Types;

import java.util.ArrayList;
import java.util.List;

/**
 * Имена агрегатных типов OpenCL C и тексты вспомогательных функций над ними (REWORK_ARCHITECTURE.md, 5.20).
 *
 * <ul>
 *   <li><b>Векторы</b> - встроенные {@code float4, int2, double8 ...}; {@code float3} занимает 16 байт, как в
 *       {@link AbiLayout}.</li>
 *   <li><b>Struct</b> - {@code typedef struct { ... } имя;} в порядке полей IR, естественное выравнивание C совпадает с
 *       {@link AbiLayout}; явное выравнивание больше естественного пишется {@code __attribute__((aligned(N)))}. Имена:
 *       в release {@code S<id типа>}, {@code f0, f1 ...}; в debug - из отладочной секции IR (после манглинга).</li>
 * </ul>
 *
 * <p>Оператор там, где C расходится с Java или не умеет выражение (замена компоненты, сборка и обновление поля struct
 * в выражении), печатается вызовом небольшой функции {@code static inline}; тексты функций строятся здесь, регистрирует
 * их {@link Helpers#dynamic}.</p>
 */
final class Layouts {

    private final IrModule m;
    private final String[] structNames;
    private final String[][] fieldNames;
    private final boolean[] used;

    /**
     * @param m     модуль
     * @param used  признак «тип используется» по id типа (struct-типы из него получают определения)
     * @param debug использовать имена из отладочной секции IR
     * @param names набор имён уровня модуля (имена struct резервируются в нём)
     */
    Layouts(IrModule m, boolean[] used, boolean debug, Names names) {
        this.m = m;
        this.used = used;
        int tc = m.typeCount();
        this.structNames = new String[tc];
        this.fieldNames = new String[tc][];
        for (int t = 0; t < tc; t++) {
            if (used[t] && m.typeKind(t) == Types.KIND_STRUCT) {
                structNames[t] = names.unique(debug ? m.debug().structName(t) : null, "S" + t);
                Names fields = new Names();
                String[] fn = new String[m.structFieldCount(t)];
                for (int i = 0; i < fn.length; i++) {
                    fn[i] = fields.unique(debug ? m.debug().fieldName(t, i) : null, "f" + i);
                }
                fieldNames[t] = fn;
            }
        }
    }

    /**
     * @param t тип: скаляр, вектор, struct или указатель на них
     * @return имя типа в тексте C; указатель - {@code __global float*}, пространство адресов обязательно
     */
    String type(int t) {
        if (Types.isScalar(t)) {
            return CTypes.scalar(t);
        }
        if (m.typeKind(t) == Types.KIND_PTR) {
            return CTypes.space(Types.space(m, t)) + " " + type(Types.elem(m, t)) + "*";
        }
        if (m.typeKind(t) == Types.KIND_VEC) {
            return CTypes.scalar(m.typeA(t)) + m.typeB(t);
        }
        return structNames[t];
    }

    /** @return имя поля struct в тексте C */
    String field(int structType, int i) {
        return fieldNames[structType][i];
    }

    /** @param t тип @return число компонент вектора либо 1 */
    int lanes(int t) {
        return m.typeKind(t) == Types.KIND_VEC ? m.typeB(t) : 1;
    }

    /** @param t тип @return скалярный тип компоненты вектора либо сам тип */
    int elem(int t) {
        return m.typeKind(t) == Types.KIND_VEC ? m.typeA(t) : t;
    }

    /** @param t тип @return {@code true} для вектора */
    boolean isVector(int t) {
        return t >= 0 && t < m.typeCount() && m.typeKind(t) == Types.KIND_VEC;
    }

    /** @param t тип @return {@code true} для struct */
    boolean isStruct(int t) {
        return t >= 0 && t < m.typeCount() && m.typeKind(t) == Types.KIND_STRUCT;
    }

    /**
     * Обращение к компоненте вектора: {@code x,y,z,w} до четырёх компонент, иначе {@code s0..sF}.
     *
     * @param lanes число компонент вектора
     * @param index номер компоненты
     * @return суффикс после точки
     */
    static String lane(int lanes, int index) {
        return lanes <= 4 ? String.valueOf("xyzw".charAt(index)) : "s" + Integer.toHexString(index).toUpperCase();
    }

    /**
     * Перестановка компонент как swizzle: {@code wzyx}, {@code s0123}. Буквы допустимы, если и источник, и результат
     * не больше четырёх компонент.
     *
     * @param srcLanes число компонент источника
     * @param idx      номера компонент результата
     * @return суффикс после точки
     */
    static String swizzle(int srcLanes, int[] idx) {
        StringBuilder sb = new StringBuilder();
        boolean letters = srcLanes <= 4 && idx.length <= 4;
        if (!letters) {
            sb.append('s');
        }
        for (int i : idx) {
            sb.append(letters ? "xyzw".charAt(i) : Character.toUpperCase(Character.forDigit(i, 16)));
        }
        return sb.toString();
    }

    // ---- определения struct -----------------------------------------------------------------------

    /**
     * Определения используемых struct-типов в порядке id (вложенный тип всегда создаётся раньше объемлющего).
     *
     * @return текст {@code typedef}, по строке на поле; пусто, если struct нет
     */
    String typedefs() {
        StringBuilder sb = new StringBuilder();
        for (int t = 0; t < structNames.length; t++) {
            if (structNames[t] == null) {
                continue;
            }
            int natural = AbiLayout.alignOf(m, t);
            int explicit = m.typeD(t);
            sb.append("typedef struct");
            if (explicit > 0 && explicit >= natural) {
                sb.append(" __attribute__((aligned(").append(explicit).append(")))");
            }
            sb.append(" {\n");
            for (int i = 0; i < m.structFieldCount(t); i++) {
                sb.append("    ").append(type(m.structFieldType(t, i))).append(' ').append(fieldNames[t][i]).append(";\n");
            }
            sb.append("} ").append(structNames[t]).append(";\n");
        }
        return sb.toString();
    }

    /** @return {@code true}, если в модуле есть используемые struct */
    boolean hasStructs() {
        for (String n : structNames) {
            if (n != null) {
                return true;
            }
        }
        return false;
    }

    // ---- вспомогательные функции ------------------------------------------------------------------

    /** Замена компоненты вектора: {@code gpu_set_float4_1(v, s)}. */
    String insert(Helpers h, int vec, int lane) {
        String vt = type(vec);
        String name = "gpu_set_" + vt + "_" + lane;
        return h.dynamic(name, () -> "static inline " + vt + " " + name + "(" + vt + " v, " + type(elem(vec)) + " s) { v."
                + lane(lanes(vec), lane) + " = s; return v; }");
    }

    /** Сборка struct из полей: {@code gpu_make_S(f0, f1, ...)}. */
    String make(Helpers h, int st) {
        String name = "gpu_make_" + structNames[st];
        return h.dynamic(name, () -> {
            StringBuilder sb = new StringBuilder("static inline ").append(structNames[st]).append(' ').append(name).append('(');
            for (int i = 0; i < m.structFieldCount(st); i++) {
                sb.append(i == 0 ? "" : ", ").append(type(m.structFieldType(st, i))).append(" a").append(i);
            }
            sb.append(") { ").append(structNames[st]).append(" r;");
            for (int i = 0; i < m.structFieldCount(st); i++) {
                sb.append(" r.").append(fieldNames[st][i]).append(" = a").append(i).append(';');
            }
            return sb.append(" return r; }").toString();
        });
    }

    /** Новое значение struct с изменённым полем: {@code gpu_with_S_2(s, v)}. */
    String with(Helpers h, int st, int field) {
        String name = "gpu_with_" + structNames[st] + "_" + field;
        return h.dynamic(name, () -> "static inline " + structNames[st] + " " + name + "(" + structNames[st] + " s, "
                + type(m.structFieldType(st, field)) + " v) { s." + fieldNames[st][field] + " = v; return s; }");
    }

    /** Нулевое значение вектора или struct: {@code gpu_zero_S()}. */
    String zero(Helpers h, int t) {
        String name = "gpu_zero_" + type(t);
        return h.dynamic(name, () -> {
            if (isVector(t)) {
                return "static inline " + type(t) + " " + name + "(void) { return (" + type(t) + ")(0); }";
            }
            StringBuilder sb = new StringBuilder("static inline ").append(type(t)).append(' ').append(name)
                    .append("(void) { ").append(type(t)).append(" r;");
            for (int i = 0; i < m.structFieldCount(t); i++) {
                int ft = m.structFieldType(t, i);
                String value = Types.isScalar(ft) ? "0" : zero(h, ft) + "()";
                sb.append(" r.").append(fieldNames[t][i]).append(" = ").append(value).append(';');
            }
            return sb.append(" return r; }").toString();
        });
    }

    /**
     * Покомпонентная функция над целочисленным вектором через скалярную: {@code gpu_div_int4(a, b)}.
     *
     * @param scalarHelper имя скалярной функции из {@link Helpers}
     */
    String laneBinary(Helpers h, String prefix, int vec, String scalarHelper) {
        String vt = type(vec);
        String name = "gpu_" + prefix + "_" + vt;
        return h.dynamic(name, () -> {
            int n = lanes(vec);
            StringBuilder sb = new StringBuilder("static inline ").append(vt).append(' ').append(name).append('(')
                    .append(vt).append(" a, ").append(vt).append(" b) { return (").append(vt).append(")(");
            for (int i = 0; i < n; i++) {
                sb.append(i == 0 ? "" : ", ").append(scalarHelper).append("(a.").append(lane(n, i)).append(", b.")
                        .append(lane(n, i)).append(')');
            }
            return sb.append("); }").toString();
        });
    }

    /** Покомпонентное преобразование {@code float -> int} вектора: {@code gpu_conv_float4_int4(x)}. */
    String laneConvert(Helpers h, int from, int to, String scalarHelper) {
        String ft = type(from);
        String tt = type(to);
        String name = "gpu_conv_" + ft + "_" + tt;
        return h.dynamic(name, () -> {
            int n = lanes(from);
            StringBuilder sb = new StringBuilder("static inline ").append(tt).append(' ').append(name).append('(')
                    .append(ft).append(" x) { return (").append(tt).append(")(");
            for (int i = 0; i < n; i++) {
                sb.append(i == 0 ? "" : ", ").append(scalarHelper).append("(x.").append(lane(n, i)).append(')');
            }
            return sb.append("); }").toString();
        });
    }

    /** Список используемых struct-типов (для тестов и отладки). */
    List<Integer> structTypes() {
        List<Integer> out = new ArrayList<>();
        for (int t = 0; t < structNames.length; t++) {
            if (structNames[t] != null) {
                out.add(t);
            }
        }
        return out;
    }

    /** @return признак использования типа (как передан в конструктор) */
    boolean isUsed(int t) {
        return used[t];
    }
}
