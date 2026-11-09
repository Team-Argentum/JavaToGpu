package com.sixik.gpu.ir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Builder: вывод типов, нумерация, дедупликация типов, защита от неправильного использования. */
class IrBuilderTest {

    @Test
    void presetTypeIdsAreFixed() {
        IrModule m = new IrBuilder().build();
        assertEquals(Types.FIRST_DYNAMIC, m.typeCount());
        for (int t = Types.BOOL; t <= Types.F64; t++) {
            assertEquals(Types.KIND_SCALAR, m.typeKind(t));
        }
        for (int t = 12; t < Types.FIRST_DYNAMIC; t++) {
            assertEquals(Types.KIND_RESERVED, m.typeKind(t));
        }
    }

    @Test
    void vectorPointerAndArrayTypesAreDeduplicated() {
        IrBuilder b = new IrBuilder();
        assertEquals(b.vec(Types.F32, 4), b.vec(Types.F32, 4));
        assertNotEquals(b.vec(Types.F32, 4), b.vec(Types.F32, 2));
        assertNotEquals(b.vec(Types.F32, 4), b.vec(Types.I32, 4));
        assertEquals(b.ptr(AddressSpace.GLOBAL, Types.F32), b.ptr(AddressSpace.GLOBAL, Types.F32));
        assertNotEquals(b.ptr(AddressSpace.GLOBAL, Types.F32), b.ptr(AddressSpace.LOCAL, Types.F32));
        assertEquals(b.array(AddressSpace.GLOBAL, Types.I32), b.array(AddressSpace.GLOBAL, Types.I32));
        assertNotEquals(b.array(AddressSpace.GLOBAL, Types.I32), b.ptr(AddressSpace.GLOBAL, Types.I32));
    }

    @Test
    void structsAreNominal() {
        IrBuilder b = new IrBuilder();
        int s1 = b.struct(new int[]{Types.F32}, 0);
        int s2 = b.struct(new int[]{Types.F32}, 0);
        assertNotEquals(s1, s2);
    }

    @Test
    void nodeIdsAreSequentialAndOperandsPrecedeUsers() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        int a = b.constI32(1);
        int c = b.constI32(2);
        int sum = b.binary(Op.ADD, a, c);
        assertEquals(0, a);
        assertEquals(1, c);
        assertEquals(2, sum);
        b.endFunction(b.block(b.exprStmt(sum)));
    }

    @Test
    void resultTypesAreInferred() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        int v4 = b.vec(Types.F32, 4);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int p = b.param(0);
        assertEquals(arr, b.typeOf(p));
        int ep = b.elemPtr(p, b.constI32(0));
        assertEquals(b.ptr(AddressSpace.GLOBAL, Types.F32), b.typeOf(ep));
        assertEquals(Types.F32, b.typeOf(b.load(ep)));
        assertEquals(Types.I32, b.typeOf(b.arrayLen(p)));
        assertEquals(Types.BOOL, b.typeOf(b.binary(Op.LT, b.constF32(1.0f), b.constF32(2.0f))));
        assertEquals(Types.F32, b.typeOf(b.extract(b.splat(v4, b.constF32(1.0f)), 0)));
        assertEquals(v4, b.typeOf(b.shuffle(b.splat(v4, b.constF32(1.0f)), 3, 2, 1, 0)));
        assertEquals(b.vec(Types.F32, 2), b.typeOf(b.shuffle(b.splat(v4, b.constF32(1.0f)), 0, 1)));
        b.endFunction(b.block());
    }

    @Test
    void constantsAreStoredAccordingToTheirWidth() {
        IrBuilder b = new IrBuilder();
        int small = b.constI32(-5);
        int wide = b.constI64(1L << 40);
        int f32 = b.constF32(2.0f);
        int f64 = b.constF64(0.5);
        IrModule m = b.build();
        assertEquals(-5, m.a(small), "up to 32 bits - directly in column a");
        assertEquals(1L << 40, m.constant(m.a(wide)), "64 bits - in the constant table");
        assertEquals(0x40000000, m.a(f32), "F32 - raw bits in column a");
        assertEquals(Double.doubleToRawLongBits(0.5), m.constant(m.a(f64)));
        assertEquals(2, m.constCount());
    }

    @Test
    void localsAreNumberedPerFunction() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        assertEquals(0, b.declareLocal(Types.I32));
        assertEquals(1, b.declareLocal(Types.F32));
        b.endFunction(b.block());
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        assertEquals(0, b.declareLocal(Types.F64), "local numbering starts anew in every function");
        b.endFunction(b.block());
        IrModule m = b.build();
        assertEquals(2, m.fLocalCount(0));
        assertEquals(Types.F32, m.fLocalType(0, 1));
        assertEquals(1, m.fLocalCount(1));
        assertEquals(Types.F64, m.fLocalType(1, 0));
    }

    @Test
    void functionAttributesAreRecorded() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        b.requireWorkGroup(8, 4, 2);
        b.inlinePolicy(IrModule.INLINE_NEVER);
        b.optLevel(2);
        b.required("SIMD", "FP64");
        b.endFunction(b.block());
        IrModule m = b.build();
        assertEquals(8, m.fReqWorkGroup(0, 0));
        assertEquals(4, m.fReqWorkGroup(0, 1));
        assertEquals(2, m.fReqWorkGroup(0, 2));
        assertEquals(IrModule.INLINE_NEVER, m.fInline(0));
        assertEquals(2, m.fOpt(0));
        assertEquals(2, m.fRequiredCount(0));
        assertEquals("FP64", m.string(m.fRequiredName(0, 1)));
        assertEquals(-1, m.fFallback(0));
        assertEquals(IrModule.BODY_IR, m.fBodyKind(0));
        assertEquals(0, m.fTuneCount(0));
    }

    @Test
    void semanticStringsAreInterned() {
        IrBuilder b = new IrBuilder();
        assertEquals(b.string("a"), b.string("a"));
        assertNotEquals(b.string("a"), b.string("b"));
    }

    @Test
    void misuseIsReportedImmediately() {
        IrBuilder b = new IrBuilder();
        assertThrows(IllegalArgumentException.class, () -> b.vec(Types.F32, 5));
        assertThrows(IllegalArgumentException.class, () -> b.vec(Types.BOOL, 4));
        assertThrows(IllegalArgumentException.class, () -> b.constInt(Types.F32, 1));
        assertThrows(IllegalStateException.class, () -> b.param(0));
        assertThrows(IllegalStateException.class, () -> b.declareLocal(Types.I32));
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        assertThrows(IllegalStateException.class, () -> b.beginFunction(IrModule.KERNEL, Types.VOID));
        assertThrows(IllegalStateException.class, b::build);
        assertThrows(IllegalArgumentException.class, () -> b.param(3));
        assertThrows(IllegalArgumentException.class, () -> b.elemPtr(b.constI32(0), b.constI32(0)));
        assertThrows(IllegalArgumentException.class, () -> b.load(b.constI32(0)));
        assertThrows(IllegalArgumentException.class, () -> b.intrinsic(Fixtures.SIN, b.constI32(0)));
        assertThrows(IllegalArgumentException.class, () -> b.binary(Op.BLOCK, 0, 0));
    }

    @Test
    void builtModuleIsIndependentOfLaterBuilderUse() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        b.endFunction(b.block());
        IrModule first = b.build();
        int nodes = first.nodeCount();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        b.endFunction(b.block());
        assertEquals(nodes, first.nodeCount(), "a previously built module is immutable");
        assertTrue(b.build().nodeCount() > nodes);
    }

    @Test
    void typesPredicates() {
        assertTrue(Types.isScalar(Types.F32));
        assertTrue(!Types.isScalar(Types.VOID));
        assertTrue(Types.isInteger(Types.U64));
        assertTrue(!Types.isInteger(Types.F32));
        assertTrue(Types.isSigned(Types.I8));
        assertTrue(!Types.isSigned(Types.U32));
        assertTrue(Types.isArith(Types.I32));
        assertTrue(!Types.isArith(Types.I16), "I8..U16 are storage types");
        assertTrue(Types.isInt32or64(Types.U64));
        assertEquals(16, Types.bits(Types.U16));
        assertEquals(0, Types.bits(Types.BOOL));
        assertTrue(Types.isValidLanes(3));
        assertTrue(!Types.isValidLanes(5));
    }

    @Test
    void opCatalogIsConsistent() {
        int statements = 0;
        for (int op = 1; op < Op.LIMIT; op++) {
            if (Op.isValid(op)) {
                assertTrue(!Op.nameOf(op).startsWith("op#"));
                if (Op.isStatement(op)) {
                    statements++;
                }
            }
        }
        assertTrue(statements >= 12);
        assertEquals("op#7", Op.nameOf(7));
        assertTrue(Op.isStatement(Op.STORE));
        assertTrue(!Op.isStatement(Op.LOAD));
    }
}
