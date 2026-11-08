package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Types;

/**
 * Имена типов OpenCL C и литералы констант (REWORK_ARCHITECTURE.md, 8.A). Состояния нет, все функции чистые.
 *
 * <p>Литералы выбраны так, чтобы значение читалось однозначно и не зависело от правил C о типе константы:
 * знаковые минимумы пишутся через {@code INT_MIN/LONG_MIN} (литерала {@code -2147483648} в C не существует: это
 * унарный минус над {@code 2147483648}), беззнаковые - с суффиксами {@code u/UL}, вещественные - кратчайшей
 * десятичной записью, которая однозначно возвращает то же число (для NaN и бесконечностей - через
 * {@code NAN/INFINITY/as_float/as_double}).</p>
 */
final class CTypes {

    private CTypes() {
    }

    /**
     * @param t скалярный тип IR
     * @return имя типа OpenCL C: {@code bool, char, uchar, short, ushort, int, uint, long, ulong, float, double};
     *         для остальных - {@code null}
     */
    static String scalar(int t) {
        return switch (t) {
            case Types.BOOL -> "bool";
            case Types.I8 -> "char";
            case Types.U8 -> "uchar";
            case Types.I16 -> "short";
            case Types.U16 -> "ushort";
            case Types.I32 -> "int";
            case Types.U32 -> "uint";
            case Types.I64 -> "long";
            case Types.U64 -> "ulong";
            case Types.F32 -> "float";
            case Types.F64 -> "double";
            default -> null;
        };
    }

    /**
     * Беззнаковый тот же ширины: через него считается арифметика знаковых целых (оборот без UB).
     *
     * @param t {@code I32/U32/I64/U64}
     * @return {@code "uint"} для 32 бит, {@code "ulong"} для 64
     */
    static String unsignedOf(int t) {
        return Types.bits(t) == 64 ? "ulong" : "uint";
    }

    /**
     * @param space {@link AddressSpace}
     * @return ключевое слово OpenCL C
     */
    static String space(int space) {
        return switch (space) {
            case AddressSpace.GLOBAL -> "__global";
            case AddressSpace.LOCAL -> "__local";
            case AddressSpace.CONSTANT -> "__constant";
            default -> "__private";
        };
    }

    /**
     * Имя функции переинтерпретации битов {@code as_T}.
     *
     * @param t скалярный числовой тип
     * @return например {@code "as_uint"}
     */
    static String asName(int t) {
        return "as_" + scalar(t);
    }

    /**
     * Целочисленный литерал значения заданного типа.
     *
     * @param type  тип константы
     * @param value каноническое значение: для типов до 32 бит - число (со знаком или без, по типу), для
     *              {@code I32/U32} достаточно младших 32 бит, для 64-битных - все биты
     * @return выражение C; отрицательные числа взяты в скобки, чтобы не слипаться с соседним {@code -}
     */
    static String integer(int type, long value) {
        switch (type) {
            case Types.I8:
                return "((char)" + signed((byte) value) + ")";
            case Types.U8:
                return "((uchar)" + (value & 0xFFL) + ")";
            case Types.I16:
                return "((short)" + signed((short) value) + ")";
            case Types.U16:
                return "((ushort)" + (value & 0xFFFFL) + ")";
            case Types.I32: {
                int v = (int) value;
                if (v == Integer.MIN_VALUE) {
                    return "INT_MIN";
                }
                return v < 0 ? "(" + v + ")" : Integer.toString(v);
            }
            case Types.U32:
                return (value & 0xFFFFFFFFL) + "u";
            case Types.I64:
                if (value == Long.MIN_VALUE) {
                    return "LONG_MIN";
                }
                return value < 0 ? "(" + value + "L)" : value + "L";
            case Types.U64:
                return Long.toUnsignedString(value) + "UL";
            default:
                throw new IllegalArgumentException("не целый тип " + type);
        }
    }

    /**
     * Те же биты как беззнаковый литерал ширины типа - для арифметики знаковых целых через {@code uint/ulong}.
     *
     * @param type  {@code I32} или {@code I64}
     * @param value значение
     * @return {@code 5u} либо {@code 18446744073709551615UL}
     */
    static String unsignedBits(int type, long value) {
        return Types.bits(type) == 64
                ? Long.toUnsignedString(value) + "UL"
                : (value & 0xFFFFFFFFL) + "u";
    }

    private static String signed(long v) {
        return v < 0 ? "(" + v + ")" : Long.toString(v);
    }

    /**
     * Литерал {@code float}.
     *
     * @param bits биты IEEE 754 binary32
     * @return выражение C; не-канонические NaN записываются через {@code as_float}, чтобы сохранить биты
     */
    static String float32(int bits) {
        float f = Float.intBitsToFloat(bits);
        if (Float.isNaN(f)) {
            return bits == 0x7fc00000 ? "NAN" : "as_float(0x" + Integer.toHexString(bits) + "u)";
        }
        if (Float.isInfinite(f)) {
            return f > 0 ? "INFINITY" : "(-INFINITY)";
        }
        String s = Float.toString(f) + "f";
        return s.startsWith("-") ? "(" + s + ")" : s;
    }

    /**
     * Литерал {@code double}.
     *
     * @param bits биты IEEE 754 binary64
     * @return выражение C; не-канонические NaN записываются через {@code as_double}
     */
    static String float64(long bits) {
        double d = Double.longBitsToDouble(bits);
        if (Double.isNaN(d)) {
            return bits == 0x7ff8000000000000L ? "((double)NAN)" : "as_double(0x" + Long.toHexString(bits) + "UL)";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "((double)INFINITY)" : "((double)-INFINITY)";
        }
        String s = Double.toString(d);
        return s.startsWith("-") ? "(" + s + ")" : s;
    }
}
