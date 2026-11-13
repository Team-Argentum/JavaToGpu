package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.ir.Types;

/**
 * Виды значений Java по дескрипторам JVM и их типы IR - те же правила, что {@code JavaTypes} в {@code frontend-java}
 * (REWORK_ARCHITECTURE.md, 5.1, 17.10): {@code Z} - {@code BOOL}, {@code B/S/C} - {@code I8/I16/U16}, элемент
 * {@code boolean[]} хранится как {@code U8}; арифметика повышает {@code byte/short/char} до {@code int}.
 */
final class Kinds {

    static final int K_VOID = 0;
    static final int K_BOOLEAN = 1;
    static final int K_BYTE = 2;
    static final int K_SHORT = 3;
    static final int K_CHAR = 4;
    static final int K_INT = 5;
    static final int K_LONG = 6;
    static final int K_FLOAT = 7;
    static final int K_DOUBLE = 8;
    static final int K_ARRAY = 9;
    /** Ссылка на объект, который ядро использовать не может. */
    static final int K_OTHER = 10;
    /** Вектор или {@code @Struct} (значение-агрегат, 5.20). */
    static final int K_AGG = 11;
    /** Указатель или ссылка {@code FloatPtr} (5.21). */
    static final int K_PTR = 12;

    private Kinds() {
    }

    /**
     * @param desc дескриптор типа поля/локали/параметра ({@code I}, {@code [F}, {@code Lpkg/T;})
     * @return вид
     */
    static int ofDescriptor(String desc) {
        return switch (desc.charAt(0)) {
            case 'V' -> K_VOID;
            case 'Z' -> K_BOOLEAN;
            case 'B' -> K_BYTE;
            case 'S' -> K_SHORT;
            case 'C' -> K_CHAR;
            case 'I' -> K_INT;
            case 'J' -> K_LONG;
            case 'F' -> K_FLOAT;
            case 'D' -> K_DOUBLE;
            case '[' -> K_ARRAY;
            default -> K_OTHER;
        };
    }

    /**
     * @param k вид примитива
     * @return тип IR значения
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
            default -> throw new IllegalArgumentException("no IR type for kind " + k);
        };
    }

    /**
     * @param k вид элемента массива
     * @return тип хранения элемента ({@code boolean} - {@code U8})
     */
    static int storageType(int k) {
        return k == K_BOOLEAN ? Types.U8 : irType(k);
    }

    /** @return вид после унарного числового повышения ({@code byte/short/char} - {@code int}) */
    static int unaryPromoted(int k) {
        return (k == K_BYTE || k == K_SHORT || k == K_CHAR) ? K_INT : k;
    }

    /** @return общий вид бинарной операции по правилам числового повышения Java */
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

    /** @return {@code true}, если вид занимает два слота локалей JVM ({@code long}, {@code double}) */
    static boolean wide(int k) {
        return k == K_LONG || k == K_DOUBLE;
    }

    /**
     * Расширяющее преобразование Java (JLS 5.1.2): его javac вставляет и для неявного повышения, поэтому оно
     * может быть «неявным» приведением исходника.
     */
    static boolean widening(int from, int to) {
        if (from == to) {
            return false;
        }
        return switch (from) {
            case K_BYTE -> to == K_SHORT || to == K_INT || to == K_LONG || to == K_FLOAT || to == K_DOUBLE;
            case K_SHORT, K_CHAR -> to == K_INT || to == K_LONG || to == K_FLOAT || to == K_DOUBLE;
            case K_INT -> to == K_LONG || to == K_FLOAT || to == K_DOUBLE;
            case K_LONG -> to == K_FLOAT || to == K_DOUBLE;
            case K_FLOAT -> to == K_DOUBLE;
            default -> false;
        };
    }
}
