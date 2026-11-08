package com.sixik.gpu.ir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Соответствие значений-агрегатов IR Java-объектам хоста. */
class HostObjectsTest {

    /** Вектор из четырёх float, как генерируемый {@code Float4}. */
    public static final class V4 {
        public final float x;
        public final float y;
        public final float z;
        public final float w;

        public V4(float x, float y, float z, float w) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
        }
    }

    /** Struct {@code float x; V4 v; long id}. */
    public static final class S {
        public float x;
        public V4 v = new V4(0, 0, 0, 0);
        public long id;
        public static int ignoredStatic = 7;
        int ignoredPackage = 9;

        public S() {
        }

        public S(float x, V4 v, long id) {
            this.x = x;
            this.v = v;
            this.id = id;
        }
    }

    /** Struct с малыми типами: {@code byte a; char b; double c}. */
    public static final class Small {
        public byte a;
        public char b;
        public double c;
    }

    /** Struct с лишним полем. */
    public static final class Wide {
        public float x;
        public V4 v;
        public long id;
        public int extra;
    }

    /** Struct с полем неподходящего типа (double вместо float). */
    public static final class WrongField {
        public double x;
        public V4 v;
        public long id;
    }

    /** Struct с вложенным вектором неверной формы. */
    public static final class WrongNested {
        public float x;
        public S v;
        public long id;
    }

    /** Класс без открытого конструктора по умолчанию. */
    public static final class NoCtor {
        public float x;
        public V4 v;
        public long id;

        public NoCtor(int unused) {
        }
    }

    private final IrBuilder b = new IrBuilder();
    private final int v4 = b.vec(Types.F32, 4);
    private final int s = b.struct(new int[]{Types.F32, v4, Types.I64}, 0);
    private final int small = b.struct(new int[]{Types.I8, Types.U16, Types.F64}, 0);
    private final int arr = b.array(AddressSpace.GLOBAL, Types.F32);
    private final IrModule m = b.build();

    @Test
    void aggregateKinds() {
        assertTrue(HostObjects.isAggregate(m, v4));
        assertTrue(HostObjects.isAggregate(m, s));
        assertFalse(HostObjects.isAggregate(m, Types.F32));
        assertFalse(HostObjects.isAggregate(m, arr));
        assertFalse(HostObjects.isAggregate(m, -1));
        assertFalse(HostObjects.isAggregate(m, m.typeCount() + 5));
    }

    @Test
    void slotCounts() {
        assertEquals(1, HostObjects.slots(m, Types.I32));
        assertEquals(4, HostObjects.slots(m, v4));
        assertEquals(6, HostObjects.slots(m, s));
        assertEquals(3, HostObjects.slots(m, small));
        assertEquals(0, HostObjects.slots(m, arr));
        assertEquals(0, HostObjects.slots(m, Types.VOID));
        assertEquals(0, HostObjects.slots(m, m.typeCount() + 5));
    }

    @Test
    void slotOffsetsOfFields() {
        assertEquals(0, HostObjects.slotOffset(m, s, 0));
        assertEquals(1, HostObjects.slotOffset(m, s, 1));
        assertEquals(5, HostObjects.slotOffset(m, s, 2));
        assertEquals(6, HostObjects.slotOffset(m, s, 3), "за последним полем - общее число листьев");
    }

    @Test
    void leafTypesFollowDeclarationOrder() {
        assertArrayEquals(new int[]{Types.F32, Types.F32, Types.F32, Types.F32, Types.F32, Types.I64},
                HostObjects.leafTypes(m, s));
        assertArrayEquals(new int[]{Types.F32, Types.F32, Types.F32, Types.F32}, HostObjects.leafTypes(m, v4));
        assertArrayEquals(new int[]{Types.I8, Types.U16, Types.F64}, HostObjects.leafTypes(m, small));
        assertArrayEquals(new int[]{Types.I32}, HostObjects.leafTypes(m, Types.I32));
    }

    @Test
    void leafOffsetsFollowTheAbiLayout() {
        assertArrayEquals(new int[]{0, 4, 8, 12}, HostObjects.leafOffsets(m, v4));
        assertArrayEquals(new int[]{0, 16, 20, 24, 28, 32}, HostObjects.leafOffsets(m, s));
        assertArrayEquals(new int[]{0, 2, 8}, HostObjects.leafOffsets(m, small));
        assertArrayEquals(new int[]{0}, HostObjects.leafOffsets(m, Types.F64));
        assertEquals(48, AbiLayout.sizeOf(m, s));
    }

    @Test
    void canonicalValuesPerType() {
        assertEquals(1, HostObjects.canon(Types.BOOL, 5));
        assertEquals(0, HostObjects.canon(Types.BOOL, 0));
        assertEquals(-1, HostObjects.canon(Types.I8, 0xFF));
        assertEquals(255, HostObjects.canon(Types.U8, -1));
        assertEquals(-1, HostObjects.canon(Types.I16, 0xFFFF));
        assertEquals(65535, HostObjects.canon(Types.U16, -1));
        assertEquals(-1, HostObjects.canon(Types.I32, 0xFFFFFFFFL), "32-битные расширяются знаком");
        assertEquals(-1, HostObjects.canon(Types.U32, 0xFFFFFFFFL));
        assertEquals(Float.floatToRawIntBits(-2f), HostObjects.canon(Types.F32, Float.floatToRawIntBits(-2f) & 0xFFFFFFFFL));
        assertEquals(0x1_0000_0000L, HostObjects.canon(Types.I64, 0x1_0000_0000L));
        assertEquals(Double.doubleToRawLongBits(1.5), HostObjects.canon(Types.F64, Double.doubleToRawLongBits(1.5)));
    }

    @Test
    void matchingClassesAreAccepted() {
        assertEquals("", HostObjects.mismatch(m, v4, V4.class));
        assertEquals("", HostObjects.mismatch(m, s, S.class));
        assertEquals("", HostObjects.mismatch(m, small, Small.class));
    }

    @Test
    void mismatchesNameTheReason() {
        assertTrue(HostObjects.mismatch(m, s, Wide.class).contains("4 открытых полей"));
        assertTrue(HostObjects.mismatch(m, v4, S.class).contains("3 открытых полей"));
        assertTrue(HostObjects.mismatch(m, s, WrongField.class).contains("WrongField.x"));
        String nested = HostObjects.mismatch(m, s, WrongNested.class);
        assertTrue(nested.contains("S"), nested);
        assertFalse(nested.isEmpty());
        assertTrue(HostObjects.mismatch(m, v4, String.class).contains("полей"));
        assertTrue(HostObjects.mismatch(m, small, S.class).contains("поле S.x"), "число полей совпало - различается тип");
    }

    @Test
    void flattenProducesCanonicalLeaves() {
        S value = new S(1.5f, new V4(1, -2, 3, 4), -5L);
        long[] out = new long[8];
        HostObjects.flatten(m, s, value, out, 1);
        assertEquals(0, out[0], "запись идёт со смещения off");
        assertEquals(Float.floatToRawIntBits(1.5f), out[1]);
        assertEquals(Float.floatToRawIntBits(1f), out[2]);
        assertEquals(Float.floatToRawIntBits(-2f), out[3], "отрицательные float расширены знаком");
        assertEquals(Float.floatToRawIntBits(4f), out[5]);
        assertEquals(-5L, out[6]);
        assertEquals(0, out[7]);
    }

    @Test
    void flattenSmallTypes() {
        Small v = new Small();
        v.a = -3;
        v.b = 65535;
        v.c = 0.25;
        long[] out = new long[3];
        HostObjects.flatten(m, small, v, out, 0);
        assertArrayEquals(new long[]{-3, 65535, Double.doubleToRawLongBits(0.25)}, out);
    }

    @Test
    void flattenRejectsNullsAndWrongClasses() {
        long[] out = new long[6];
        assertThrows(IllegalArgumentException.class, () -> HostObjects.flatten(m, s, null, out, 0));
        S withNull = new S();
        withNull.v = null;
        assertThrows(IllegalArgumentException.class, () -> HostObjects.flatten(m, s, withNull, out, 0));
        assertThrows(IllegalArgumentException.class, () -> HostObjects.flatten(m, s, new Wide(), out, 0));
        assertThrows(IllegalArgumentException.class, () -> HostObjects.flatten(m, v4, new S(), out, 0));
    }

    @Test
    void rebuildUpdatesStructsInPlaceAndReplacesVectors() {
        S existing = new S(1, new V4(1, 1, 1, 1), 1);
        V4 oldVector = existing.v;
        long[] leaves = new long[6];
        HostObjects.flatten(m, s, new S(9f, new V4(5, 6, 7, 8), 77L), leaves, 0);
        Object result = HostObjects.rebuild(m, s, S.class, existing, leaves, 0);
        assertSame(existing, result);
        assertEquals(9f, existing.x);
        assertEquals(77L, existing.id);
        assertNotSame(oldVector, existing.v, "вектор неизменяем - создаётся новый объект");
        assertEquals(7f, existing.v.z);
        assertEquals(8f, existing.v.w);
    }

    @Test
    void rebuildCreatesObjectsWhenThereIsNoExisting() {
        long[] leaves = new long[6];
        HostObjects.flatten(m, s, new S(2f, new V4(1, 2, 3, 4), 3L), leaves, 0);
        S created = (S) HostObjects.rebuild(m, s, S.class, null, leaves, 0);
        assertEquals(2f, created.x);
        assertEquals(3L, created.id);
        assertEquals(4f, created.v.w);

        V4 vec = (V4) HostObjects.rebuild(m, v4, V4.class, null, new long[]{0,
                Float.floatToRawIntBits(1f), Float.floatToRawIntBits(2f), Float.floatToRawIntBits(3f),
                Float.floatToRawIntBits(4f)}, 1);
        assertEquals(1f, vec.x);
        assertEquals(4f, vec.w);
    }

    @Test
    void rebuildSmallTypes() {
        Small v = (Small) HostObjects.rebuild(m, small, Small.class, null,
                new long[]{-3, 65535, Double.doubleToRawLongBits(0.25)}, 0);
        assertEquals(-3, v.a);
        assertEquals((char) 65535, v.b);
        assertEquals(0.25, v.c);
    }

    @Test
    void rebuildNeedsSuitableConstructors() {
        long[] leaves = new long[6];
        assertThrows(IllegalArgumentException.class, () -> HostObjects.rebuild(m, s, NoCtor.class, null, leaves, 0));
        assertThrows(IllegalArgumentException.class, () -> HostObjects.rebuild(m, v4, S.class, null, leaves, 0));
    }

    @Test
    void ignoredFieldsAreNotPartOfTheValue() {
        S value = new S(1f, new V4(1, 2, 3, 4), 1L);
        value.ignoredPackage = 123;
        S.ignoredStatic = 456;
        long[] out = new long[6];
        HostObjects.flatten(m, s, value, out, 0);
        assertEquals(6, out.length);
        assertEquals("", HostObjects.mismatch(m, s, S.class));
    }
}
