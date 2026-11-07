package com.sixik.gpu.ir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Раскладка типов в памяти по правилам OpenCL C. */
class AbiLayoutTest {

    private final IrBuilder b = new IrBuilder();

    private IrModule m() {
        return b.build();
    }

    @Test
    void scalars() {
        IrModule m = m();
        assertEquals(1, AbiLayout.sizeOf(m, Types.I8));
        assertEquals(2, AbiLayout.sizeOf(m, Types.U16));
        assertEquals(4, AbiLayout.sizeOf(m, Types.F32));
        assertEquals(8, AbiLayout.sizeOf(m, Types.I64));
        assertEquals(8, AbiLayout.alignOf(m, Types.F64));
    }

    @Test
    void boolAndVoidHaveNoMemoryLayout() {
        IrModule m = m();
        assertEquals(-1, AbiLayout.sizeOf(m, Types.BOOL));
        assertEquals(-1, AbiLayout.sizeOf(m, Types.VOID));
    }

    @Test
    void vectorsAreAlignedToTheirSize() {
        int v2 = b.vec(Types.F32, 2);
        int v4 = b.vec(Types.F32, 4);
        int v16 = b.vec(Types.U8, 16);
        IrModule m = m();
        assertEquals(8, AbiLayout.sizeOf(m, v2));
        assertEquals(8, AbiLayout.alignOf(m, v2));
        assertEquals(16, AbiLayout.sizeOf(m, v4));
        assertEquals(16, AbiLayout.alignOf(m, v4));
        assertEquals(16, AbiLayout.sizeOf(m, v16));
    }

    @Test
    void threeComponentVectorOccupiesFour() {
        int v3 = b.vec(Types.F32, 3);
        int d3 = b.vec(Types.F64, 3);
        IrModule m = m();
        assertEquals(16, AbiLayout.sizeOf(m, v3), "sizeof(float3) == 16 в OpenCL C");
        assertEquals(16, AbiLayout.alignOf(m, v3));
        assertEquals(32, AbiLayout.sizeOf(m, d3));
    }

    @Test
    void pointerIsEightBytes() {
        int p = b.ptr(AddressSpace.GLOBAL, Types.F32);
        IrModule m = m();
        assertEquals(8, AbiLayout.sizeOf(m, p));
        assertEquals(8, AbiLayout.alignOf(m, p));
    }

    @Test
    void arrayHasNoLayout() {
        int a = b.array(AddressSpace.GLOBAL, Types.F32);
        assertEquals(-1, AbiLayout.sizeOf(m(), a));
    }

    @Test
    void gamePosFromTheConceptHasSize16() {
        // @Struct(alignment = 16) { float x, y, z; }
        int s = b.struct(new int[]{Types.F32, Types.F32, Types.F32}, 16);
        IrModule m = m();
        assertEquals(16, AbiLayout.sizeOf(m, s));
        assertEquals(16, AbiLayout.alignOf(m, s));
        assertEquals(0, AbiLayout.fieldOffset(m, s, 0));
        assertEquals(4, AbiLayout.fieldOffset(m, s, 1));
        assertEquals(8, AbiLayout.fieldOffset(m, s, 2));
    }

    @Test
    void structWithoutExplicitAlignUsesNaturalOne() {
        int s = b.struct(new int[]{Types.F32, Types.F32, Types.F32}, 0);
        IrModule m = m();
        assertEquals(12, AbiLayout.sizeOf(m, s));
        assertEquals(4, AbiLayout.alignOf(m, s));
    }

    @Test
    void paddingBetweenFieldsAndAtTheEnd() {
        int s = b.struct(new int[]{Types.U8, Types.I32, Types.U8}, 0);
        IrModule m = m();
        assertEquals(0, AbiLayout.fieldOffset(m, s, 0));
        assertEquals(4, AbiLayout.fieldOffset(m, s, 1));
        assertEquals(8, AbiLayout.fieldOffset(m, s, 2));
        assertEquals(12, AbiLayout.sizeOf(m, s), "9 байт округляются до кратного 4");
        assertEquals(4, AbiLayout.alignOf(m, s));
    }

    @Test
    void longFieldRaisesStructAlignmentToEight() {
        int s = b.struct(new int[]{Types.I64, Types.U8}, 0);
        IrModule m = m();
        assertEquals(16, AbiLayout.sizeOf(m, s));
        assertEquals(8, AbiLayout.alignOf(m, s));
    }

    @Test
    void nestedStructAndVectorField() {
        int v4 = b.vec(Types.F32, 4);
        int inner = b.struct(new int[]{Types.F32, v4}, 0);     // f32 @0, float4 @16 => размер 32, align 16
        int outer = b.struct(new int[]{Types.U8, inner}, 0);   // u8 @0, inner @16 => размер 48
        IrModule m = m();
        assertEquals(32, AbiLayout.sizeOf(m, inner));
        assertEquals(16, AbiLayout.fieldOffset(m, inner, 1));
        assertEquals(16, AbiLayout.fieldOffset(m, outer, 1));
        assertEquals(48, AbiLayout.sizeOf(m, outer));
    }

    @Test
    void structWithBoolFieldHasNoLayout() {
        int s = b.struct(new int[]{Types.I32, Types.BOOL}, 0);
        assertEquals(-1, AbiLayout.sizeOf(m(), s));
    }

    @Test
    void outOfRangeTypeIsHandled() {
        assertEquals(-1, AbiLayout.sizeOf(m(), 9999));
        assertEquals(-1, AbiLayout.sizeOf(m(), -3));
    }
}
