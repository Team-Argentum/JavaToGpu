package com.sixik.gpu.frontend.java;

import com.sixik.gpu.ir.Types;

import javax.lang.model.type.TypeMirror;

/**
 * Соответствие типов Java типам IR (REWORK_ARCHITECTURE.md, 5.1) и правила числового повышения Java.
 *
 * <p>Тип Java описывается целым «видом» ({@code K_*}). Вид {@code byte/short/char} соответствует типам хранения
 * IR {@code I8/I16/U16}: арифметики на них в IR нет, как и в Java (операнды повышаются до {@code int}).
 * {@code boolean} как значение - {@code BOOL}, но элемент массива {@code boolean[]} хранится как {@code U8}
 * (BOOL нельзя в массиве, правило IR-T06).</p>
 *
 * <p>Класс не хранит состояния: это статические функции над видами.</p>
 */
final class JavaTypes {

    /** Вид: {@code void}. */
    static final int K_VOID = 0;
    /** Вид: {@code boolean}. */
    static final int K_BOOLEAN = 1;
    /** Вид: {@code byte}. */
    static final int K_BYTE = 2;
    /** Вид: {@code short}. */
    static final int K_SHORT = 3;
    /** Вид: {@code char}. */
    static final int K_CHAR = 4;
    /** Вид: {@code int}. */
    static final int K_INT = 5;
    /** Вид: {@code long}. */
    static final int K_LONG = 6;
    /** Вид: {@code float}. */
    static final int K_FLOAT = 7;
    /** Вид: {@code double}. */
    static final int K_DOUBLE = 8;
    /** Вид: массив (вид элемента хранится отдельно). */
    static final int K_ARRAY = 9;
    /** Вид: любой другой тип (объект и т.п.), в GPU-коде не поддерживается. */
    static final int K_OTHER = 10;

    private JavaTypes() {
    }

    /**
     * @param t тип javac
     * @return вид типа
     */
    static int kindOf(TypeMirror t) {
        return switch (t.getKind()) {
            case VOID -> K_VOID;
            case BOOLEAN -> K_BOOLEAN;
            case BYTE -> K_BYTE;
            case SHORT -> K_SHORT;
            case CHAR -> K_CHAR;
            case INT -> K_INT;
            case LONG -> K_LONG;
            case FLOAT -> K_FLOAT;
            case DOUBLE -> K_DOUBLE;
            case ARRAY -> K_ARRAY;
            default -> K_OTHER;
        };
    }

    /**
     * @param k вид
     * @return {@code true} для примитивных скаляров ({@code boolean}..{@code double})
     */
    static boolean isPrimitive(int k) {
        return k >= K_BOOLEAN && k <= K_DOUBLE;
    }

    /**
     * @param k вид
     * @return {@code true} для числовых примитивов ({@code byte}..{@code double})
     */
    static boolean isNumeric(int k) {
        return k >= K_BYTE && k <= K_DOUBLE;
    }

    /**
     * @param k вид
     * @return {@code true} для целых примитивов ({@code byte}..{@code long})
     */
    static boolean isIntegral(int k) {
        return k >= K_BYTE && k <= K_LONG;
    }

    /**
     * Тип IR значения данного вида.
     *
     * @param k примитивный вид
     * @return id скалярного типа IR
     */
    static int irType(int k) {
        return switch (k) {
            case K_BOOLEAN -> Types.BOOL;
            case K_BYTE -> Types.I8;
            case K_SHORT -> Types.I16;
            case K_CHAR -> Types.U16;
            case K_INT -> Types.I32;
            case K_LONG -> Types.I64;
            case K_FLOAT -> Types.F32;
            case K_DOUBLE -> Types.F64;
            default -> throw new IllegalArgumentException("нет типа IR для вида " + k);
        };
    }

    /**
     * Тип IR элемента массива данного вида: как {@link #irType(int)}, но {@code boolean} хранится как {@code U8}.
     *
     * @param k примитивный вид элемента
     * @return id скалярного типа IR
     */
    static int storageType(int k) {
        return k == K_BOOLEAN ? Types.U8 : irType(k);
    }

    /**
     * Унарное числовое повышение Java: {@code byte/short/char} становятся {@code int}.
     *
     * @param k вид
     * @return вид после повышения
     */
    static int unaryPromoted(int k) {
        return (k == K_BYTE || k == K_SHORT || k == K_CHAR) ? K_INT : k;
    }

    /**
     * Бинарное числовое повышение Java: общий вид арифметики над двумя операндами.
     *
     * @param a вид левого операнда
     * @param b вид правого операнда
     * @return {@code double}, если есть double; иначе {@code float}; иначе {@code long}; иначе {@code int}
     */
    static int binaryPromoted(int a, int b) {
        if (a == K_DOUBLE || b == K_DOUBLE) {
            return K_DOUBLE;
        }
        if (a == K_FLOAT || b == K_FLOAT) {
            return K_FLOAT;
        }
        if (a == K_LONG || b == K_LONG) {
            return K_LONG;
        }
        return K_INT;
    }

    /**
     * @param k вид
     * @return имя вида в терминах Java (для сообщений)
     */
    static String name(int k) {
        return switch (k) {
            case K_VOID -> "void";
            case K_BOOLEAN -> "boolean";
            case K_BYTE -> "byte";
            case K_SHORT -> "short";
            case K_CHAR -> "char";
            case K_INT -> "int";
            case K_LONG -> "long";
            case K_FLOAT -> "float";
            case K_DOUBLE -> "double";
            case K_ARRAY -> "array";
            default -> "object";
        };
    }
}
