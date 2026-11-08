package com.sixik.gpu.ir.interpreter;

import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

/**
 * Семантика скалярных операций IR над «каноническими» значениями (REWORK_ARCHITECTURE.md, 5.1, 5.10, 5.16).
 *
 * <p><b>Каноническое представление значения</b> - один {@code long} на значение любого скалярного типа:</p>
 * <ul>
 *   <li>{@code BOOL}: {@code 0} или {@code 1};</li>
 *   <li>{@code I8, U8, I16, U16}: числовое значение (знаковое расширение для {@code I*}, нулевое для {@code U*});
 *       эти типы только хранятся и загружаются, арифметики над ними нет;</li>
 *   <li>{@code I32, U32, F32}: 32 бита значения, <b>знаково расширенные</b> до {@code long} независимо от знака типа
 *       ({@code F32} - сырые биты {@link Float#floatToRawIntBits}); так сложение, вычитание, умножение, побитовые
 *       операции и {@code bitcast} между {@code I32/U32/F32} - обычные операции над {@code int};</li>
 *   <li>{@code I64, U64, F64}: сырые 64 бита ({@code F64} - {@link Double#doubleToRawLongBits}).</li>
 * </ul>
 *
 * <p>Знак учитывают только деление, остаток, сдвиг вправо, сравнения и {@code convert} - ровно как предписывает
 * архитектура. Класс без состояния: все методы - чистые статические функции, поэтому их можно проверять на
 * крайних значениях независимо от остального интерпретатора.</p>
 */
final class Alu {

    private static final long MASK32 = 0xFFFFFFFFL;
    private static final double TWO_POW_63 = 0x1p63;
    private static final double TWO_POW_64 = 0x1p64;

    private Alu() {
    }

    // =============================================================================================
    // нормализация и вспомогательные преобразования
    // =============================================================================================

    /**
     * Приводит произвольные биты к каноническому значению типа {@code t}.
     *
     * @param t    скалярный тип ({@code BOOL..F64})
     * @param bits младшие биты значения (для типов уже и 32 бит лишние старшие биты отбрасываются)
     * @return каноническое значение
     */
    static long canon(int t, long bits) {
        return switch (t) {
            case Types.BOOL -> bits != 0 ? 1 : 0;
            case Types.I8 -> (byte) bits;
            case Types.U8 -> bits & 0xFF;
            case Types.I16 -> (short) bits;
            case Types.U16 -> bits & 0xFFFF;
            case Types.I32, Types.U32, Types.F32 -> (int) bits;
            default -> bits;
        };
    }

    /**
     * Значение целого типа как математическое целое, расширенное до 64 бит по знаку типа.
     * Для {@code U64} это те же 64 бита (старший бит остаётся знаковым - используйте беззнаковые сравнения).
     *
     * @param t целый тип
     * @param v каноническое значение
     * @return расширение до 64 бит
     */
    static long widen(int t, long v) {
        return t == Types.U32 ? v & MASK32 : v;
    }

    /**
     * Число ли тип 64-битным.
     *
     * @param t скалярный тип
     * @return {@code true} для {@code I64, U64, F64}
     */
    static boolean is64(int t) {
        return t == Types.I64 || t == Types.U64 || t == Types.F64;
    }

    private static float f32(long v) {
        return Float.intBitsToFloat((int) v);
    }

    private static long f32bits(float f) {
        return Float.floatToRawIntBits(f);
    }

    private static double f64(long v) {
        return Double.longBitsToDouble(v);
    }

    private static long f64bits(double d) {
        return Double.doubleToRawLongBits(d);
    }

    // =============================================================================================
    // арифметика и логика
    // =============================================================================================

    /**
     * Бинарная арифметическая операция {@code add, sub, mul, div, rem}.
     *
     * <p>Целые оборачиваются как в Java (по модулю 2^32 или 2^64). Деление и остаток знаковых типов - Java
     * ({@code MIN / -1 = MIN}, {@code MIN % -1 = 0}), беззнаковых - {@code Integer/Long.divideUnsigned}.
     * Вещественные - строго IEEE 754 (Java {@code float/double}; {@code rem} - {@code fmod} как оператор
     * {@code %}). Деление и остаток целых на ноль вызывающий обязан отсечь заранее ({@link #divisorIsZero}).</p>
     *
     * @param op опкод ({@link Op#ADD}..{@link Op#REM})
     * @param t  тип операндов ({@code I32, U32, I64, U64, F32, F64})
     * @param a  левый операнд
     * @param b  правый операнд
     * @return результат в каноническом виде
     */
    static long arith(int op, int t, long a, long b) {
        switch (t) {
            case Types.I32: {
                int x = (int) a, y = (int) b;
                return switch (op) {
                    case Op.ADD -> x + y;
                    case Op.SUB -> x - y;
                    case Op.MUL -> x * y;
                    case Op.DIV -> x / y;
                    default -> x % y;
                };
            }
            case Types.U32: {
                int x = (int) a, y = (int) b;
                return switch (op) {
                    case Op.ADD -> x + y;
                    case Op.SUB -> x - y;
                    case Op.MUL -> x * y;
                    case Op.DIV -> Integer.divideUnsigned(x, y);
                    default -> Integer.remainderUnsigned(x, y);
                };
            }
            case Types.I64:
                return switch (op) {
                    case Op.ADD -> a + b;
                    case Op.SUB -> a - b;
                    case Op.MUL -> a * b;
                    case Op.DIV -> a / b;
                    default -> a % b;
                };
            case Types.U64:
                return switch (op) {
                    case Op.ADD -> a + b;
                    case Op.SUB -> a - b;
                    case Op.MUL -> a * b;
                    case Op.DIV -> Long.divideUnsigned(a, b);
                    default -> Long.remainderUnsigned(a, b);
                };
            case Types.F32: {
                float x = f32(a), y = f32(b);
                return f32bits(switch (op) {
                    case Op.ADD -> x + y;
                    case Op.SUB -> x - y;
                    case Op.MUL -> x * y;
                    case Op.DIV -> x / y;
                    default -> x % y;
                });
            }
            default: {
                double x = f64(a), y = f64(b);
                return f64bits(switch (op) {
                    case Op.ADD -> x + y;
                    case Op.SUB -> x - y;
                    case Op.MUL -> x * y;
                    case Op.DIV -> x / y;
                    default -> x % y;
                });
            }
        }
    }

    /**
     * Нужно ли отсечь деление: целый делитель равен нулю.
     *
     * @param t тип операндов
     * @param b каноническое значение делителя
     * @return {@code true} для целого типа и нулевого делителя
     */
    static boolean divisorIsZero(int t, long b) {
        return Types.isInteger(t) && b == 0;
    }

    /**
     * Арифметическое отрицание {@code -a}: для целых - по модулю ширины ({@code -MIN = MIN}), для вещественных -
     * смена знакового бита (в том числе у нуля и NaN).
     *
     * @param t тип операнда
     * @param a операнд
     * @return результат
     */
    static long neg(int t, long a) {
        return switch (t) {
            case Types.I32, Types.U32 -> -(int) a;
            case Types.F32 -> f32bits(-f32(a));
            case Types.F64 -> f64bits(-f64(a));
            default -> -a;
        };
    }

    /**
     * Побитовые операции {@code and, or, xor}. Для {@code BOOL} - те же операции над {@code 0/1} (eager).
     *
     * @param op опкод ({@link Op#AND}, {@link Op#OR}, {@link Op#XOR})
     * @param a  левый операнд
     * @param b  правый операнд
     * @return результат (знаково расширенные операнды сохраняют знаковое расширение результата)
     */
    static long bitwise(int op, long a, long b) {
        return switch (op) {
            case Op.AND -> a & b;
            case Op.OR -> a | b;
            default -> a ^ b;
        };
    }

    /**
     * Побитовое/логическое НЕ.
     *
     * @param t тип операнда
     * @param a операнд
     * @return {@code ~a}, а для {@code BOOL} - {@code 1 - a}
     */
    static long not(int t, long a) {
        return t == Types.BOOL ? 1 - a : ~a;
    }

    /**
     * Сдвиги {@code shl, shr, ushr}. Счётчик маскируется шириной типа ({@code n & 31} либо {@code n & 63}) -
     * Java-семантика, гарантируемая контрактом IR. {@code shr} арифметический для знаковых и логический для
     * беззнаковых типов; {@code ushr} логический для любого типа.
     *
     * @param op опкод
     * @param t  тип значения (и счётчика)
     * @param a  значение
     * @param n  счётчик сдвига (любое значение типа {@code t}, в том числе отрицательное или не меньше ширины)
     * @return результат
     */
    static long shift(int op, int t, long a, long n) {
        if (is64(t)) {
            int s = (int) (n & 63);
            return switch (op) {
                case Op.SHL -> a << s;
                case Op.USHR -> a >>> s;
                default -> t == Types.U64 ? a >>> s : a >> s;
            };
        }
        int x = (int) a;
        int s = (int) (n & 31);
        return switch (op) {
            case Op.SHL -> x << s;
            case Op.USHR -> x >>> s;
            default -> t == Types.U32 ? x >>> s : x >> s;
        };
    }

    /**
     * Сравнения {@code eq, ne, lt, le, gt, ge}.
     *
     * <p>Вещественные - по IEEE 754: любое сравнение с NaN ложно, кроме {@code ne} (истинно), {@code -0 == +0}.
     * Беззнаковые - беззнаковый порядок.</p>
     *
     * @param op опкод
     * @param t  тип операндов
     * @param a  левый операнд
     * @param b  правый операнд
     * @return {@code 1} либо {@code 0}
     */
    static long compare(int op, int t, long a, long b) {
        boolean r;
        if (t == Types.F32) {
            float x = f32(a), y = f32(b);
            r = switch (op) {
                case Op.EQ -> x == y;
                case Op.NE -> x != y;
                case Op.LT -> x < y;
                case Op.LE -> x <= y;
                case Op.GT -> x > y;
                default -> x >= y;
            };
        } else if (t == Types.F64) {
            double x = f64(a), y = f64(b);
            r = switch (op) {
                case Op.EQ -> x == y;
                case Op.NE -> x != y;
                case Op.LT -> x < y;
                case Op.LE -> x <= y;
                case Op.GT -> x > y;
                default -> x >= y;
            };
        } else {
            int c;
            if (op == Op.EQ || op == Op.NE) {
                c = a == b ? 0 : 1;
            } else if (t == Types.U32) {
                c = Long.compare(a & MASK32, b & MASK32);
            } else if (t == Types.U64) {
                c = Long.compareUnsigned(a, b);
            } else {
                c = Long.compare(a, b);
            }
            r = switch (op) {
                case Op.EQ -> c == 0;
                case Op.NE -> c != 0;
                case Op.LT -> c < 0;
                case Op.LE -> c <= 0;
                case Op.GT -> c > 0;
                default -> c >= 0;
            };
        }
        return r ? 1 : 0;
    }

    // =============================================================================================
    // convert / bitcast
    // =============================================================================================

    /**
     * Явное преобразование скаляра ({@code convert}).
     *
     * <ul>
     *   <li>целое &rarr; целое: значение расширяется по знаку <em>исходного</em> типа до 64 бит и усекается до
     *       ширины целевого типа (как Java {@code (int) long}, {@code (long) int});</li>
     *   <li>{@code BOOL} &harr; целое: {@code 0/1} и {@code != 0};</li>
     *   <li>целое &rarr; вещественное: округление к ближайшему (беззнаковые трактуются как беззнаковые);</li>
     *   <li>вещественное &rarr; целое: <b>насыщение</b> к {@code MIN/MAX} целевого типа, {@code NaN -> 0}
     *       (контракт IR V2 = Java {@code (int) float}); для {@code I8, U8, I16, U16} - как Java
     *       {@code (byte) f}: сначала насыщение к {@code int}, затем усечение до ширины типа хранения
     *       ({@code (char) -1.0f == 65535}), а не насыщение к диапазону узкого типа;</li>
     *   <li>вещественное &harr; вещественное: Java {@code (float) double} и {@code (double) float}.</li>
     * </ul>
     *
     * @param from исходный скалярный тип
     * @param to   целевой скалярный тип
     * @param v    каноническое значение исходного типа
     * @return каноническое значение целевого типа
     */
    static long convert(int from, int to, long v) {
        if (from == to) {
            return v;
        }
        if (to == Types.BOOL) {
            return v != 0 ? 1 : 0;
        }
        if (Types.isFloat(from)) {
            double d = from == Types.F32 ? (double) f32(v) : f64(v);
            if (to == Types.F32) {
                return f32bits((float) d);
            }
            if (to == Types.F64) {
                return f64bits(d);
            }
            return floatToInt(to, d);
        }
        // источник - BOOL либо целое
        if (to == Types.F32) {
            return f32bits(intToFloat(from, v));
        }
        if (to == Types.F64) {
            return f64bits(intToDouble(from, v));
        }
        return canon(to, widen(from, v));
    }

    private static double intToDouble(int from, long v) {
        if (from == Types.U64) {
            return v >= 0 ? (double) v : unsignedLongToDouble(v);
        }
        return (double) widen(from, v);
    }

    /** {@code (double)} для {@code v < 0} как беззнакового 64-битного числа: деление пополам со сохранением липкого бита. */
    private static double unsignedLongToDouble(long v) {
        long half = (v >>> 1) | (v & 1);
        return (double) half * 2.0;
    }

    private static float intToFloat(int from, long v) {
        if (from == Types.U64) {
            if (v >= 0) {
                return (float) v;
            }
            long half = (v >>> 1) | (v & 1);
            return (float) half * 2.0f;
        }
        // float(long) в Java округляет верно, для U32 значение уже нулевым расширением помещается в long
        return (float) widen(from, v);
    }

    /**
     * {@code float/double -> целое} с насыщением и {@code NaN -> 0}.
     *
     * <p>Идея: найти ближайшее целое к нулю ({@code (long) d} уже насыщает к диапазону {@code long} и даёт
     * {@code NaN -> 0}), затем зажать в диапазон целевого типа. Для {@code U64} диапазон не помещается в
     * {@code long}, поэтому значения от {@code 2^63} обрабатываются отдельно: {@code d - 2^63} переводится
     * в {@code long}, и к результату добавляется бит {@code 2^63} (в {@code long} это {@code MIN_VALUE}).
     * Все значения {@code double} от {@code 2^63} - целые, так что вычитание точное.</p>
     *
     * @param to целевой целый тип
     * @param d  значение (для {@code float} - расширенное до {@code double} без потерь)
     * @return каноническое значение
     */
    private static long floatToInt(int to, double d) {
        if (Double.isNaN(d)) {
            return 0;
        }
        switch (to) {
            case Types.U64:
                if (d <= 0) {
                    return 0;
                }
                if (d >= TWO_POW_64) {
                    return -1L;
                }
                if (d < TWO_POW_63) {
                    return (long) d;
                }
                return ((long) (d - TWO_POW_63)) | Long.MIN_VALUE;
            case Types.I64:
                return (long) d;
            case Types.U32: {
                long t = (long) d;
                return canon(to, Math.max(0, Math.min(MASK32, t)));
            }
            default: {
                // I32 насыщается к своему диапазону; I8/U8/I16/U16 - как Java (byte) f: сначала float -> int
                // с насыщением, затем усечение до ширины типа хранения (JLS 5.1.3)
                long t = (long) d;
                return canon(to, Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, t)));
            }
        }
    }

    /**
     * Переинтерпретация битов между скалярами одной ширины ({@code bitcast}).
     *
     * @param to целевой тип
     * @param v  каноническое значение исходного типа (той же ширины)
     * @return каноническое значение целевого типа с теми же битами
     */
    static long bitcast(int to, long v) {
        return canon(to, v);
    }
}
