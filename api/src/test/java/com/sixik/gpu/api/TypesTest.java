package com.sixik.gpu.api;

import com.sixik.gpu.api.types.ConstantDoublePtr;
import com.sixik.gpu.api.types.ConstantFloatPtr;
import com.sixik.gpu.api.types.Double2;
import com.sixik.gpu.api.types.DoublePtr;
import com.sixik.gpu.api.types.GlobalFloatPtr;
import com.sixik.gpu.api.types.GlobalIntPtr;
import com.sixik.gpu.api.types.LocalIntPtr;
import com.sixik.gpu.api.types.LocalLongPtr;
import com.sixik.gpu.api.types.Float16;
import com.sixik.gpu.api.types.Float2;
import com.sixik.gpu.api.types.Float3;
import com.sixik.gpu.api.types.Float4;
import com.sixik.gpu.api.types.Float8;
import com.sixik.gpu.api.types.FloatPtr;
import com.sixik.gpu.api.types.Int4;
import com.sixik.gpu.api.types.IntPtr;
import com.sixik.gpu.api.types.Long2;
import com.sixik.gpu.api.types.LongPtr;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Сгенерированные типы: векторы (значения, неизменяемость, имена компонент) и классы-ссылки. */
class TypesTest {

    @Test
    void vectorEqualityIsByComponentBits() {
        assertEquals(new Float2(1f, 2f), new Float2(1f, 2f));
        assertEquals(new Float2(1f, 2f).hashCode(), new Float2(1f, 2f).hashCode());
        assertNotEquals(new Float2(1f, 2f), new Float2(1f, 3f));
        assertNotEquals(new Float2(1f, 2f), new Int4(1, 2, 0, 0));
        assertNotEquals(new Float2(1f, 2f), null);
        assertEquals(new Float2(Float.NaN, 0f), new Float2(Float.NaN, 0f), "NaN равен NaN (как Float.equals)");
        assertNotEquals(new Float2(0f, 0f), new Float2(-0f, 0f), "+0 и -0 различаются (как Float.equals)");
        assertNotEquals(new Double2(0.0, 1.0), new Double2(-0.0, 1.0));
        assertEquals(new Long2(Long.MIN_VALUE, 1), new Long2(Long.MIN_VALUE, 1));
    }

    @Test
    void toStringListsComponents() {
        assertEquals("Float3(1.0, 2.0, 3.0)", new Float3(1f, 2f, 3f).toString());
        assertEquals("Int4(1, 2, 3, 4)", new Int4(1, 2, 3, 4).toString());
    }

    @Test
    void componentNamesFollowOpenClConvention() {
        Float4 v = new Float4(1f, 2f, 3f, 4f);
        assertEquals(1f, v.x);
        assertEquals(2f, v.y);
        assertEquals(3f, v.z);
        assertEquals(4f, v.w);
        Float8 w = new Float8(0, 1, 2, 3, 4, 5, 6, 7);
        assertEquals(7f, w.s7);
        Float16 q = new Float16(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
        assertEquals(10f, q.sA);
        assertEquals(15f, q.sF);
    }

    @Test
    void vectorsAreImmutable() {
        for (Class<?> c : new Class<?>[]{Float2.class, Float4.class, Float16.class, Int4.class, Long2.class, Double2.class}) {
            assertTrue(Modifier.isFinal(c.getModifiers()), c.getSimpleName() + " должен быть final");
            for (Field f : c.getDeclaredFields()) {
                assertTrue(Modifier.isFinal(f.getModifiers()) && Modifier.isPublic(f.getModifiers()),
                        c.getSimpleName() + "." + f.getName() + " должно быть public final");
            }
        }
    }

    @Test
    void pointerClassesAreMutableReferences() {
        FloatPtr p = new FloatPtr(1.5f);
        assertEquals(1.5f, p.value);
        p.value = 2.5f;
        assertEquals(2.5f, p.value);
        assertEquals(0f, new FloatPtr().value);
        assertEquals(0, new IntPtr().value);
        assertEquals(7L, new LongPtr(7L).value);
        assertEquals(1.0, new DoublePtr(1.0).value);
        assertEquals("FloatPtr(2.5)", p.toString());
        assertNotEquals(new FloatPtr(1f), new FloatPtr(1f), "сравнение по ссылке");
    }

    @Test
    void spacePointersAreArraysWithOffsetsAndArithmetic() {
        float[] a = {0, 1, 2, 3, 4};
        GlobalFloatPtr p = GlobalFloatPtr.of(a, 1);
        assertEquals(1f, p.get(0));
        assertEquals(3f, p.get(2));
        p.set(2, 30f);
        assertEquals(30f, a[3], "запись видна в исходном массиве");
        GlobalFloatPtr q = p.plus(3);
        assertEquals(4f, q.get(0));
        assertEquals(1f, p.get(0), "plus не меняет исходный указатель");
        assertEquals(30f, q.plus(-1).get(0));
        assertEquals("GlobalFloatPtr(+1)", p.toString());
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> p.get(4), "на JVM выход за массив - исключение");
    }

    @Test
    void spacePointersExistForEveryScalarAndSpace() {
        assertEquals(7, GlobalIntPtr.of(new int[]{7}, 0).get(0));
        assertEquals(8L, LocalLongPtr.of(new long[]{8L}, 0).get(0));
        assertEquals(9.5, ConstantDoublePtr.of(new double[]{9.5}, 0).get(0));
        LocalIntPtr l = LocalIntPtr.of(new int[2], 0);
        l.set(1, 5);
        assertEquals(5, l.get(1));
    }

    @Test
    void constantPointerHasNoWriteMethod() {
        for (java.lang.reflect.Method m : ConstantFloatPtr.class.getDeclaredMethods()) {
            assertFalse(m.getName().startsWith("set"), "в constant нельзя писать: " + m);
        }
    }

    @Test
    void referenceSemanticsVisibleToCallee() {
        // по ссылке: изменение внутри функции видно вызывающему (так же, как указатель на GPU)
        FloatPtr x = new FloatPtr(40f);
        addTwo(x);
        assertEquals(42f, x.value);
    }

    private static void addTwo(FloatPtr p) {
        p.value += 2f;
    }
}
