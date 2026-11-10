package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

/**
 * Вычисление опкодов IR над константами (свёртка).
 *
 * <p>Семантика <b>обязана совпадать</b> с эталонным интерпретатором: целые оборачиваются как в Java, делимое
 * знаковых {@code MIN / -1 = MIN}, беззнаковые деления - {@code divideUnsigned}, сдвиги маскируют счётчик, вещественные -
 * строго IEEE 754, {@code float -> int} насыщается. Расхождение превратило бы оптимизацию в смену результата, поэтому
 * тест {@code EvalMatchesInterpreterTest} сверяет таблицу крайних значений с интерпретатором.</p>
 *
 * <p>Значения - «канонические биты»: {@code BOOL} - 0/1, {@code I8/I16} - знаково расширенные, {@code U8/U16} -
 * нулевое расширение, 32-битные типы (включая {@code F32}) - {@code (int)} знаково расширенный в {@code long},
 * 64-битные - как есть.</p>
 */
final class Eval {

    private static final long MASK32 = 0xFFFFFFFFL;
    private static final double TWO_POW_63 = 9223372036854775808.0;
    private static final double TWO_POW_64 = 18446744073709551616.0;

    private Eval() {
    }

    /**
     * Каноническое значение скалярного типа из произвольных битов.
     *
     * @param t    скалярный тип
     * @param bits биты
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
     * @param t скалярный тип
     * @return {@code true}, если над значениями типа свёртка арифметики определена ({@code I32,U32,I64,U64,F32,F64})
     */
    static boolean isArith(int t) {
        return Types.isArith(t);
    }

    private static long widen(int t, long v) {
        return t == Types.U32 ? v & MASK32 : v;
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

    /**
     * Арифметика {@code add, sub, mul, div, rem}. Целое деление на ноль вызывающий отсекает заранее.
     *
     * @param op опкод
     * @param t  тип операндов
     * @param a  левый операнд
     * @param b  правый операнд
     * @return результат
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
     * @param op {@code DIV} или {@code REM}
     * @param t  тип
     * @param b  делитель
     * @return {@code true}, если операция целая и делитель нулевой (свёртка запрещена)
     */
    static boolean divByZero(int op, int t, long b) {
        return (op == Op.DIV || op == Op.REM) && Types.isInteger(t) && b == 0;
    }

    /**
     * Арифметическое отрицание.
     *
     * @param t тип
     * @param a операнд
     * @return {@code -a}
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
     * Побитовые {@code and, or, xor}.
     *
     * @param op опкод
     * @param t  тип
     * @param a  левый операнд
     * @param b  правый операнд
     * @return результат в каноническом виде
     */
    static long bitwise(int op, int t, long a, long b) {
        long r = switch (op) {
            case Op.AND -> a & b;
            case Op.OR -> a | b;
            default -> a ^ b;
        };
        return canon(t, r);
    }

    /**
     * Побитовое/логическое НЕ.
     *
     * @param t тип
     * @param a операнд
     * @return результат в каноническом виде
     */
    static long not(int t, long a) {
        return t == Types.BOOL ? 1 - a : canon(t, ~a);
    }

    /**
     * Сдвиги.
     *
     * @param op опкод
     * @param t  тип значения
     * @param a  значение
     * @param n  счётчик
     * @return результат в каноническом виде
     */
    static long shift(int op, int t, long a, long n) {
        if (t == Types.I64 || t == Types.U64) {
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
     * Сравнения.
     *
     * @param op опкод
     * @param t  тип операндов
     * @param a  левый операнд
     * @param b  правый операнд
     * @return {@code 1} либо {@code 0}
     */
    static long compare(int op, int t, long a, long b) {
        boolean r;
        if (t == Types.F32 || t == Types.F64) {
            double x = t == Types.F32 ? f32(a) : f64(a);
            double y = t == Types.F32 ? f32(b) : f64(b);
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

    /**
     * Явное преобразование скаляра (семантика {@code convert} интерпретатора).
     *
     * @param from исходный тип
     * @param to   целевой тип
     * @param v    каноническое значение
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
            return v >= 0 ? (double) v : (double) ((v >>> 1) | (v & 1)) * 2.0;
        }
        return (double) widen(from, v);
    }

    private static float intToFloat(int from, long v) {
        if (from == Types.U64) {
            if (v >= 0) {
                return (float) v;
            }
            return (float) ((v >>> 1) | (v & 1)) * 2.0f;
        }
        return (float) widen(from, v);
    }

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
                long t = (long) d;
                return canon(to, Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, t)));
            }
        }
    }

    /**
     * Переинтерпретация битов.
     *
     * @param to целевой тип
     * @param v  каноническое значение
     * @return каноническое значение целевого типа
     */
    static long bitcast(int to, long v) {
        return canon(to, v);
    }

    /**
     * Свёртка {@code fma(a, b, c)} с единственным округлением.
     *
     * @param t тип ({@code F32} либо {@code F64})
     * @param a первый множитель
     * @param b второй множитель
     * @param c слагаемое
     * @return результат
     */
    static long fma(int t, long a, long b, long c) {
        if (t == Types.F32) {
            return f32bits(Math.fma(f32(a), f32(b), f32(c)));
        }
        return f64bits(Math.fma(f64(a), f64(b), f64(c)));
    }

    /**
     * @param t    вещественный тип
     * @param bits биты
     * @return значение как {@code double} (для проверок степени двойки и т.п.)
     */
    static double toDouble(int t, long bits) {
        return t == Types.F32 ? (double) f32(bits) : f64(bits);
    }

    /**
     * @param t вещественный тип
     * @param d значение
     * @return канонические биты значения типа {@code t}
     */
    static long fromDouble(int t, double d) {
        return t == Types.F32 ? f32bits((float) d) : f64bits(d);
    }
}
