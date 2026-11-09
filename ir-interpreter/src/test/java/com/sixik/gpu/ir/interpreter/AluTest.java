package com.sixik.gpu.ir.interpreter;

import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Семантика скалярных операций, проверенная независимым эталоном: арифметика {@link BigInteger} (бесконечная
 * точность + явное взятие по модулю ширины) для целых и {@link BigDecimal} для насыщающих преобразований.
 * Беззнаковые типы (в Java их нет, поэтому дифференциальные тесты их не покрывают) проверяются только здесь.
 */
class AluTest {

    private static final int[] INT_TYPES = {Types.I32, Types.U32, Types.I64, Types.U64};

    // ---- эталон на BigInteger ---------------------------------------------------------------------

    private static int width(int t) {
        return Types.bits(t);
    }

    private static boolean unsigned(int t) {
        return t == Types.U32 || t == Types.U64;
    }

    /** Математическое значение канонического представления. */
    private static BigInteger value(int t, long canonical) {
        if (t == Types.U32) {
            return BigInteger.valueOf(canonical & 0xFFFFFFFFL);
        }
        if (t == Types.U64) {
            return new BigInteger(Long.toUnsignedString(canonical));
        }
        return BigInteger.valueOf(canonical); // I32 знаково расширен, I64 как есть
    }

    /** Каноническое представление математического значения после взятия по модулю 2^ширина. */
    private static long canonical(int t, BigInteger v) {
        long low = v.longValue(); // младшие 64 бита в дополнительном коде
        return width(t) == 32 ? (long) (int) low : low;
    }

    private static long[] samples(int t, Random r) {
        long[] edge = width(t) == 32
                ? new long[]{0, 1, 2, 3, -1, -2, -3, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MAX_VALUE,
                Integer.MAX_VALUE - 1, 31, 32, 33, 0x55555555, 0xAAAAAAAA, 0x80000000, 0xFFFFFFFF}
                : new long[]{0, 1, 2, 3, -1, -2, -3, Long.MIN_VALUE, Long.MIN_VALUE + 1, Long.MAX_VALUE,
                Long.MAX_VALUE - 1, 31, 32, 33, 63, 64, 65, 0x5555555555555555L, 0xAAAAAAAAAAAAAAAAL, 0xFFFFFFFFL};
        long[] all = new long[edge.length + 40];
        for (int i = 0; i < edge.length; i++) {
            all[i] = canonical(t, BigInteger.valueOf(edge[i]));
        }
        for (int i = edge.length; i < all.length; i++) {
            all[i] = canonical(t, BigInteger.valueOf(width(t) == 32 ? r.nextInt() : r.nextLong()));
        }
        return all;
    }

    @Test
    void addSubMulWrapLikeJava() {
        Random r = new Random(10);
        for (int t : INT_TYPES) {
            long[] s = samples(t, r);
            for (long a : s) {
                for (long b : s) {
                    BigInteger x = value(t, a);
                    BigInteger y = value(t, b);
                    assertEquals(canonical(t, x.add(y)), Alu.arith(Op.ADD, t, a, b), "add " + x + " " + y + " t" + t);
                    assertEquals(canonical(t, x.subtract(y)), Alu.arith(Op.SUB, t, a, b), "sub " + x + " " + y);
                    assertEquals(canonical(t, x.multiply(y)), Alu.arith(Op.MUL, t, a, b), "mul " + x + " " + y);
                }
            }
        }
    }

    @Test
    void divRemTruncateTowardZeroAndUnsignedDivideUnsigned() {
        Random r = new Random(11);
        for (int t : INT_TYPES) {
            long[] s = samples(t, r);
            for (long a : s) {
                for (long b : s) {
                    if (b == 0) {
                        continue;
                    }
                    BigInteger x = value(t, a);
                    BigInteger y = value(t, b);
                    assertEquals(canonical(t, x.divide(y)), Alu.arith(Op.DIV, t, a, b), "div " + x + " / " + y + " t" + t);
                    assertEquals(canonical(t, x.remainder(y)), Alu.arith(Op.REM, t, a, b), "rem " + x + " % " + y);
                }
            }
        }
    }

    @Test
    void minValueDividedByMinusOneWrapsWithoutFailure() {
        assertEquals(Integer.MIN_VALUE, Alu.arith(Op.DIV, Types.I32, Integer.MIN_VALUE, -1));
        assertEquals(0, Alu.arith(Op.REM, Types.I32, Integer.MIN_VALUE, -1));
        assertEquals(Long.MIN_VALUE, Alu.arith(Op.DIV, Types.I64, Long.MIN_VALUE, -1));
        assertEquals(0, Alu.arith(Op.REM, Types.I64, Long.MIN_VALUE, -1));
    }

    @Test
    void integerDivisionByZeroIsDetectedForIntegersOnly() {
        for (int t : INT_TYPES) {
            assertEquals(true, Alu.divisorIsZero(t, 0));
            assertEquals(false, Alu.divisorIsZero(t, 1));
        }
        assertEquals(false, Alu.divisorIsZero(Types.F32, 0));
        assertEquals(false, Alu.divisorIsZero(Types.F64, 0));
    }

    @Test
    void negAndNotWrap() {
        Random r = new Random(12);
        for (int t : INT_TYPES) {
            for (long a : samples(t, r)) {
                BigInteger x = value(t, a);
                assertEquals(canonical(t, x.negate()), Alu.neg(t, a), "neg " + x);
                assertEquals(canonical(t, x.not()), Alu.not(t, a), "not " + x);
            }
        }
        assertEquals(Integer.MIN_VALUE, Alu.neg(Types.I32, Integer.MIN_VALUE));
        assertEquals(1, Alu.not(Types.BOOL, 0));
        assertEquals(0, Alu.not(Types.BOOL, 1));
    }

    @Test
    void bitwiseOpsMatchBigIntegerTwosComplement() {
        Random r = new Random(13);
        for (int t : INT_TYPES) {
            long[] s = samples(t, r);
            for (long a : s) {
                for (long b : s) {
                    BigInteger x = BigInteger.valueOf(a);
                    BigInteger y = BigInteger.valueOf(b);
                    assertEquals(canonical(t, x.and(y)), Alu.bitwise(Op.AND, a, b));
                    assertEquals(canonical(t, x.or(y)), Alu.bitwise(Op.OR, a, b));
                    assertEquals(canonical(t, x.xor(y)), Alu.bitwise(Op.XOR, a, b));
                }
            }
        }
    }

    @Test
    void shiftsMaskTheCountByTypeWidth() {
        Random r = new Random(14);
        for (int t : INT_TYPES) {
            int w = width(t);
            BigInteger modulus = BigInteger.ONE.shiftLeft(w);
            long[] s = samples(t, r);
            for (long a : s) {
                for (long n : s) {
                    int k = (int) (n & (w - 1)); // счётчик маскируется шириной типа (n - в дополнительном коде)
                    BigInteger x = value(t, a);
                    BigInteger bits = x.mod(modulus); // беззнаковый образ битов
                    assertEquals(canonical(t, x.shiftLeft(k)), Alu.shift(Op.SHL, t, a, n), "shl " + x + " " + n);
                    BigInteger shr = unsigned(t) ? bits.shiftRight(k) : x.shiftRight(k);
                    assertEquals(canonical(t, shr), Alu.shift(Op.SHR, t, a, n), "shr " + x + " " + n + " t" + t);
                    assertEquals(canonical(t, bits.shiftRight(k)), Alu.shift(Op.USHR, t, a, n), "ushr " + x + " " + n);
                }
            }
        }
    }

    @Test
    void shiftCountsAtAndBeyondWidthWrapAround() {
        assertEquals(1, Alu.shift(Op.SHL, Types.I32, 1, 32));
        assertEquals(2, Alu.shift(Op.SHL, Types.I32, 1, 33));
        assertEquals(Integer.MIN_VALUE, Alu.shift(Op.SHL, Types.I32, 1, -1));
        assertEquals(1, Alu.shift(Op.SHL, Types.I64, 1, 64));
        assertEquals(-1, Alu.shift(Op.SHR, Types.I32, -8, 100));
        assertEquals(0x7FFFFFFF, Alu.shift(Op.USHR, Types.I32, -1, 1));
        assertEquals(Long.MAX_VALUE, Alu.shift(Op.USHR, Types.I64, -1, 1));
        // беззнаковый shr логический
        assertEquals(0x7FFFFFFF, Alu.shift(Op.SHR, Types.U32, 0xFFFFFFFF, 1));
        assertEquals(0x7FFFFFFFFFFFFFFFL, Alu.shift(Op.SHR, Types.U64, -1, 1));
    }

    @Test
    void integerComparisonsRespectSignedness() {
        Random r = new Random(15);
        for (int t : INT_TYPES) {
            long[] s = samples(t, r);
            for (long a : s) {
                for (long b : s) {
                    int c = value(t, a).compareTo(value(t, b));
                    assertEquals(c < 0 ? 1 : 0, Alu.compare(Op.LT, t, a, b));
                    assertEquals(c <= 0 ? 1 : 0, Alu.compare(Op.LE, t, a, b));
                    assertEquals(c > 0 ? 1 : 0, Alu.compare(Op.GT, t, a, b));
                    assertEquals(c >= 0 ? 1 : 0, Alu.compare(Op.GE, t, a, b));
                    assertEquals(c == 0 ? 1 : 0, Alu.compare(Op.EQ, t, a, b));
                    assertEquals(c != 0 ? 1 : 0, Alu.compare(Op.NE, t, a, b));
                }
            }
        }
        // 0xFFFFFFFF как U32 больше единицы, как I32 - меньше
        assertEquals(1, Alu.compare(Op.GT, Types.U32, 0xFFFFFFFF, 1));
        assertEquals(0, Alu.compare(Op.GT, Types.I32, 0xFFFFFFFF, 1));
    }

    @Test
    void floatComparisonsFollowIeee754() {
        long nan = Float.floatToRawIntBits(Float.NaN);
        long one = Float.floatToRawIntBits(1f);
        long pz = Float.floatToRawIntBits(0f);
        long nz = Float.floatToRawIntBits(-0f);
        for (int op : new int[]{Op.EQ, Op.LT, Op.LE, Op.GT, Op.GE}) {
            assertEquals(0, Alu.compare(op, Types.F32, nan, one), "NaN vs 1, op " + Op.nameOf(op));
            assertEquals(0, Alu.compare(op, Types.F32, nan, nan), "NaN vs NaN, op " + Op.nameOf(op));
        }
        assertEquals(1, Alu.compare(Op.NE, Types.F32, nan, nan));
        assertEquals(1, Alu.compare(Op.EQ, Types.F32, pz, nz), "-0 == +0");
        assertEquals(0, Alu.compare(Op.LT, Types.F32, nz, pz));
        long dnan = Double.doubleToRawLongBits(Double.NaN);
        assertEquals(1, Alu.compare(Op.NE, Types.F64, dnan, dnan));
        assertEquals(0, Alu.compare(Op.GE, Types.F64, dnan, dnan));
    }

    @Test
    void floatArithmeticAndNegation() {
        long a = Float.floatToRawIntBits(7.5f);
        long b = Float.floatToRawIntBits(2f);
        assertEquals(Float.floatToRawIntBits(9.5f), Alu.arith(Op.ADD, Types.F32, a, b));
        assertEquals(Float.floatToRawIntBits(1.5f), Alu.arith(Op.REM, Types.F32, a, b));
        assertEquals(Float.floatToRawIntBits(Float.POSITIVE_INFINITY),
                Alu.arith(Op.DIV, Types.F32, a, Float.floatToRawIntBits(0f)));
        assertEquals(Float.floatToRawIntBits(-0f), Alu.neg(Types.F32, Float.floatToRawIntBits(0f)));
        assertEquals(Double.doubleToRawLongBits(-0d), Alu.neg(Types.F64, Double.doubleToRawLongBits(0d)));
        assertEquals(Double.doubleToRawLongBits(0.1 + 0.2),
                Alu.arith(Op.ADD, Types.F64, Double.doubleToRawLongBits(0.1), Double.doubleToRawLongBits(0.2)));
    }

    // ---- convert -----------------------------------------------------------------------------------

    @Test
    void integerToIntegerExtendsBySourceSignAndTruncates() {
        assertEquals(-1L, Alu.convert(Types.I32, Types.I64, -1));
        assertEquals(0xFFFFFFFFL, Alu.convert(Types.U32, Types.I64, -1), "U32 is zero-extended");
        assertEquals(-1L, Alu.convert(Types.I32, Types.U64, -1), "I32 -> U64 is sign-extended (as in C)");
        assertEquals(-1, Alu.convert(Types.I64, Types.I32, 0xFFFFFFFFL));
        assertEquals(Integer.MIN_VALUE, Alu.convert(Types.I64, Types.I32, 0x80000000L));
        assertEquals(-128, Alu.convert(Types.I32, Types.I8, 128));
        assertEquals(128, Alu.convert(Types.I32, Types.U8, 128));
        assertEquals(255, Alu.convert(Types.I32, Types.U8, -1));
        assertEquals(-32768, Alu.convert(Types.I32, Types.I16, 32768));
        assertEquals(65535, Alu.convert(Types.I32, Types.U16, -1));
        assertEquals(-1, Alu.convert(Types.I8, Types.I32, -1));
        assertEquals(255, Alu.convert(Types.U8, Types.I32, 255));
        assertEquals(1, Alu.convert(Types.I32, Types.BOOL, 42));
        assertEquals(0, Alu.convert(Types.I64, Types.BOOL, 0));
        assertEquals(1, Alu.convert(Types.BOOL, Types.I32, 1));
    }

    @Test
    void floatToIntegerSaturatesAndMapsNanToZero() {
        float[] fs = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -0f, 0.99f, -0.99f, 2.9f, -2.9f,
                2147483648f, -2147483648f, 3e9f, -3e9f, 4294967296f, 1e20f, -1e20f, 9.223372e18f, 1.8446744e19f,
                100f, 255f, 256f, -129f, 40000f, -40000f, 65535f, 65536f};
        for (float f : fs) {
            long bits = Float.floatToRawIntBits(f);
            BigInteger t = f != f ? BigInteger.ZERO : truncate(f);
            assertEquals(canonical(Types.I32, clamp(t, Integer.MIN_VALUE, Integer.MAX_VALUE)),
                    Alu.convert(Types.F32, Types.I32, bits), "f32->i32 " + f);
            assertEquals(canonical(Types.I64, clamp(t, Long.MIN_VALUE, Long.MAX_VALUE)),
                    Alu.convert(Types.F32, Types.I64, bits), "f32->i64 " + f);
            assertEquals(canonical(Types.U32, clamp(t, 0, 0xFFFFFFFFL)),
                    Alu.convert(Types.F32, Types.U32, bits), "f32->u32 " + f);
            assertEquals(canonical(Types.U64, clamp(t, BigInteger.ZERO, BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE))),
                    Alu.convert(Types.F32, Types.U64, bits), "f32->u64 " + f);
        }
    }

    @Test
    void doubleToIntegerSaturatesAcrossTheWholeRange() {
        Random r = new Random(16);
        double[] ds = new double[400];
        for (int i = 0; i < ds.length; i++) {
            int e = r.nextInt(80) - 10;
            ds[i] = (r.nextDouble() - 0.5) * Math.pow(2, e);
        }
        double[] edge = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0d, -0d, 0x1p31, 0x1p32,
                0x1p63, 0x1p64, 0x1p63 + 2048, 0x1.fffffffffffffp63, 0x1.fffffffffffffp62, -0x1p63, -0x1p63 - 2048,
                2147483647.5, -2147483648.5, 4294967295.9, 4294967296.0};
        for (double[] group : new double[][]{ds, edge}) {
            for (double d : group) {
                long bits = Double.doubleToRawLongBits(d);
                BigInteger t = Double.isNaN(d) ? BigInteger.ZERO : truncate(d);
                assertEquals(canonical(Types.I32, clamp(t, Integer.MIN_VALUE, Integer.MAX_VALUE)),
                        Alu.convert(Types.F64, Types.I32, bits), "f64->i32 " + d);
                assertEquals(canonical(Types.I64, clamp(t, Long.MIN_VALUE, Long.MAX_VALUE)),
                        Alu.convert(Types.F64, Types.I64, bits), "f64->i64 " + d);
                assertEquals(canonical(Types.U32, clamp(t, 0, 0xFFFFFFFFL)),
                        Alu.convert(Types.F64, Types.U32, bits), "f64->u32 " + d);
                assertEquals(canonical(Types.U64, clamp(t, BigInteger.ZERO, BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE))),
                        Alu.convert(Types.F64, Types.U64, bits), "f64->u64 " + d);
            }
        }
    }

    @Test
    void floatToNarrowIntegerIsJavaNarrowing() {
        // (byte) f == (byte) (int) f : сначала насыщение к int, затем усечение
        assertEquals((byte) 200.0f, Alu.convert(Types.F32, Types.I8, Float.floatToRawIntBits(200f)));
        assertEquals((char) -1.0f, Alu.convert(Types.F32, Types.U16, Float.floatToRawIntBits(-1f)));
        assertEquals((short) 40000.0, Alu.convert(Types.F64, Types.I16, Double.doubleToRawLongBits(40000d)));
        assertEquals((byte) 1e20f, Alu.convert(Types.F32, Types.I8, Float.floatToRawIntBits(1e20f)));
        assertEquals((short) Float.NaN, Alu.convert(Types.F32, Types.I16, Float.floatToRawIntBits(Float.NaN)));
        assertEquals(300 & 0xFF, Alu.convert(Types.F32, Types.U8, Float.floatToRawIntBits(300f)));
    }

    @Test
    void integerToFloatRoundsToNearestEvenIncludingUnsigned() {
        Random r = new Random(17);
        long[] s = new long[300];
        for (int i = 0; i < s.length; i++) {
            long v = r.nextLong();
            // чаще попадаем в «трудные» случаи: единицы около границы точности
            s[i] = i % 3 == 0 ? v >>> r.nextInt(64) : (i % 3 == 1 ? v | Long.MIN_VALUE : v);
        }
        long[] extra = {0x8000000000000400L, 0x8000000000000401L, 0x8000000000000C00L, -1L, Long.MIN_VALUE, Long.MAX_VALUE,
                0x20000000000001L, 0x1FFFFFFFFFFFFFL, 0xFFFFFF8000000001L};
        for (long[] group : new long[][]{s, extra}) {
            for (long v : group) {
                BigInteger u = value(Types.U64, v);
                BigInteger sgn = BigInteger.valueOf(v);
                assertEquals(Double.doubleToRawLongBits(u.doubleValue()), Alu.convert(Types.U64, Types.F64, v), "u64->f64 " + u);
                assertEquals(Double.doubleToRawLongBits(sgn.doubleValue()), Alu.convert(Types.I64, Types.F64, v), "i64->f64 " + sgn);
                assertEquals((long) Float.floatToRawIntBits(u.floatValue()), Alu.convert(Types.U64, Types.F32, v), "u64->f32 " + u);
                assertEquals((long) Float.floatToRawIntBits(sgn.floatValue()), Alu.convert(Types.I64, Types.F32, v), "i64->f32 " + sgn);
                long w = (int) v;
                assertEquals((long) Float.floatToRawIntBits(value(Types.U32, w).floatValue()),
                        Alu.convert(Types.U32, Types.F32, w), "u32->f32");
                assertEquals(Double.doubleToRawLongBits(value(Types.I32, w).doubleValue()),
                        Alu.convert(Types.I32, Types.F64, w), "i32->f64");
            }
        }
    }

    @Test
    void floatWidthConversions() {
        assertEquals(Double.doubleToRawLongBits(0.1f), Alu.convert(Types.F32, Types.F64, Float.floatToRawIntBits(0.1f)));
        assertEquals((long) Float.floatToRawIntBits((float) 1e40), Alu.convert(Types.F64, Types.F32, Double.doubleToRawLongBits(1e40)));
        assertEquals((long) Float.floatToRawIntBits((float) 0.1), Alu.convert(Types.F64, Types.F32, Double.doubleToRawLongBits(0.1)));
    }

    @Test
    void bitcastKeepsBits() {
        assertEquals(0x3F800000, Alu.bitcast(Types.I32, Float.floatToRawIntBits(1f)));
        assertEquals(Float.floatToRawIntBits(1f), Alu.bitcast(Types.F32, 0x3F800000));
        assertEquals(Double.doubleToRawLongBits(1d), Alu.bitcast(Types.F64, 0x3FF0000000000000L));
        assertEquals(-1L, Alu.bitcast(Types.U64, -1L));
        assertEquals(255, Alu.bitcast(Types.U8, -1), "bitcast I8 -> U8 takes the bits");
        assertEquals(-1, Alu.bitcast(Types.I8, 255));
    }

    // ---- помощники эталона -------------------------------------------------------------------------

    /** Усечение к нулю, точное (через {@link BigDecimal}). */
    private static BigInteger truncate(double d) {
        if (Double.isInfinite(d)) {
            return d > 0 ? BigInteger.ONE.shiftLeft(200) : BigInteger.ONE.shiftLeft(200).negate();
        }
        return new BigDecimal(d).toBigInteger();
    }

    private static BigInteger clamp(BigInteger v, long lo, long hi) {
        return clamp(v, BigInteger.valueOf(lo), BigInteger.valueOf(hi));
    }

    private static BigInteger clamp(BigInteger v, BigInteger lo, BigInteger hi) {
        return v.max(lo).min(hi);
    }
}
