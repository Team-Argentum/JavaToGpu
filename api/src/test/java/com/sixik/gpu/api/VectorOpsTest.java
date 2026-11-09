package com.sixik.gpu.api;

import com.sixik.gpu.api.types.Double2;
import com.sixik.gpu.api.types.Float16;
import com.sixik.gpu.api.types.Float2;
import com.sixik.gpu.api.types.Float3;
import com.sixik.gpu.api.types.Float4;
import com.sixik.gpu.api.types.Float8;
import com.sixik.gpu.api.types.Int2;
import com.sixik.gpu.api.types.Int4;
import com.sixik.gpu.api.types.Long2;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JVM-семантика операций векторов: покомпонентно и ровно как скалярная арифметика Java (эталон для GPU). */
class VectorOpsTest {

    @Test
    void splatFillsEveryLane() {
        assertEquals(new Float4(2, 2, 2, 2), Float4.splat(2f));
        assertEquals(new Int2(-7, -7), Int2.splat(-7));
        assertEquals(new Float16(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1), Float16.splat(1f));
    }

    @Test
    void withReplacesOneLaneAndLeavesTheOriginal() {
        Float4 v = new Float4(1, 2, 3, 4);
        assertEquals(new Float4(1, 9, 3, 4), v.withY(9f));
        assertEquals(new Float4(1, 2, 3, 4), v, "vector is immutable");
        assertEquals(new Float8(0, 0, 0, 0, 0, 0, 0, 5), Float8.splat(0f).withS7(5f));
        assertEquals(new Float16(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 7, 0, 0, 0, 0, 0), Float16.splat(0f).withSA(7f));
    }

    @Test
    void shuffleSelectsRepeatsAndChangesWidth() {
        Float4 v = new Float4(10, 11, 12, 13);
        assertEquals(new Float4(13, 12, 11, 10), v.shuffle(3, 2, 1, 0));
        assertEquals(new Float2(11, 11), v.shuffle(1, 1));
        assertEquals(new Float3(10, 12, 13), v.shuffle(0, 2, 3));
        assertEquals(new Float8(10, 10, 11, 11, 12, 12, 13, 13), v.shuffle(0, 0, 1, 1, 2, 2, 3, 3));
        assertEquals(16, List.of(Float16.class.getDeclaredFields()).stream().filter(f -> f.getType() == float.class).count());
        assertThrows(IndexOutOfBoundsException.class, () -> v.shuffle(0, 4));
    }

    @Test
    void floatArithmeticIsLaneWiseAndMatchesScalars() {
        Float4 a = new Float4(1, -2, 3.5f, Float.NaN);
        Float4 b = new Float4(4, 8, -0.5f, 1);
        assertEquals(new Float4(5, 6, 3, Float.NaN), a.add(b));
        assertEquals(new Float4(-3, -10, 4, Float.NaN), a.sub(b));
        assertEquals(new Float4(4, -16, -1.75f, Float.NaN), a.mul(b));
        assertEquals(new Float4(0.25f, -0.25f, -7, Float.NaN), a.div(b));
        assertEquals(new Float4(1, -2, 0f, Float.NaN), a.rem(b));
        assertEquals(new Float4(-1, 2, -3.5f, Float.NaN), a.neg());
        assertEquals(new Float4(3, 0, 5.5f, Float.NaN), a.add(2f), "scalar is broadcast");
        assertEquals(new Float4(2, -4, 7, Float.NaN), a.mul(2f));
    }

    @Test
    void floatNegationKeepsTheSignOfZero() {
        Float2 z = new Float2(0f, -0f).neg();
        assertEquals(Float.floatToRawIntBits(-0f), Float.floatToRawIntBits(z.x));
        assertEquals(Float.floatToRawIntBits(0f), Float.floatToRawIntBits(z.y));
    }

    @Test
    void integerArithmeticWrapsAndDividesLikeJava() {
        Int4 a = new Int4(Integer.MAX_VALUE, Integer.MIN_VALUE, 7, -7);
        Int4 b = new Int4(1, -1, 2, 2);
        assertEquals(new Int4(Integer.MIN_VALUE, Integer.MAX_VALUE, 9, -5), a.add(b));
        assertEquals(new Int4(Integer.MAX_VALUE, Integer.MIN_VALUE, 3, -3), a.div(b));
        assertEquals(new Int4(0, 0, 1, -1), a.rem(b));
        assertEquals(new Int4(Integer.MIN_VALUE + 1, Integer.MIN_VALUE, -7, 7), a.neg());
        assertEquals(new Int4(0, 0, 3, 3), new Int4(0, 0, 7, 7).div(new Int4(1, 1, 2, 2)));
        assertThrows(ArithmeticException.class, () -> a.div(new Int4(1, 1, 0, 1)), "division by zero as in Java");
    }

    @Test
    void integerBitOperationsAndShiftsMaskTheCount() {
        Int4 a = new Int4(-8, 0b1100, 1, -1);
        assertEquals(new Int4(-8 & 5, 0b1100 & 5, 1 & 5, -1 & 5), a.and(5));
        assertEquals(new Int4(-8 | 5, 0b1100 | 5, 1 | 5, -1 | 5), a.or(5));
        assertEquals(new Int4(-8 ^ 5, 0b1100 ^ 5, 1 ^ 5, -1 ^ 5), a.xor(5));
        assertEquals(new Int4(~-8, ~0b1100, ~1, ~-1), a.not());
        assertEquals(new Int4(-8 << 33, 0b1100 << 33, 1 << 33, -1 << 33), a.shl(33), "33 & 31 = 1");
        assertEquals(new Int4(-8 >> 33, 0b1100 >> 33, 1 >> 33, -1 >> 33), a.shr(33));
        assertEquals(new Int4(-8 >>> 33, 0b1100 >>> 33, 1 >>> 33, -1 >>> 33), a.ushr(33));
        assertEquals(new Int4(1, 2, 3, 4).shl(new Int4(1, 2, 3, 4)), new Int4(2, 8, 24, 64));
    }

    @Test
    void longAndDoubleVectorsHaveTheSameOperations() {
        assertEquals(new Long2(Long.MIN_VALUE, 0), new Long2(Long.MAX_VALUE, -1).add(1L));
        assertEquals(new Long2(1L << 1, 1L << 2), new Long2(1, 1).shl(new Long2(65, 66)));
        assertEquals(new Double2(0.5, -0.5), new Double2(1, -1).div(2.0));
    }

    @Test
    void floatVectorsHaveNoBitOperations() {
        for (Method m : Float4.class.getDeclaredMethods()) {
            assertTrue(!List.of("and", "or", "xor", "not", "shl", "shr", "ushr").contains(m.getName()), m.getName());
        }
    }

    @Test
    void everyVectorClassHasTheFullSetOfOperations() throws Exception {
        String[] scalars = {"Float", "Double", "Int", "Long"};
        int[] lanes = {2, 3, 4, 8, 16};
        for (String s : scalars) {
            for (int n : lanes) {
                Class<?> c = Class.forName("com.sixik.gpu.api.types." + s + n);
                for (String op : new String[]{"add", "sub", "mul", "div", "rem"}) {
                    long count = List.of(c.getDeclaredMethods()).stream().filter(m -> m.getName().equals(op)).count();
                    assertEquals(2, count, c.getSimpleName() + "." + op + " (vector and scalar)");
                }
                long withers = List.of(c.getDeclaredMethods()).stream().filter(m -> m.getName().startsWith("with")).count();
                assertEquals(n, withers, c.getSimpleName());
                long shuffles = List.of(c.getDeclaredMethods()).stream().filter(m -> m.getName().equals("shuffle")).count();
                assertEquals(5, shuffles, c.getSimpleName());
                c.getDeclaredMethod("splat", c.getDeclaredFields()[0].getType());
                c.getDeclaredMethod("neg");
            }
        }
    }
}
