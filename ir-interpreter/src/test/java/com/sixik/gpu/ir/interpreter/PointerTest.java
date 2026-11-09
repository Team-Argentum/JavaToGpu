package com.sixik.gpu.ir.interpreter;

import com.sixik.gpu.api.types.Float4;
import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Указатели в интерпретаторе (M9): {@code local_addr}, {@code field_ptr}, арифметика {@code elem_ptr} над указателями и
 * границы доступа. Модули собраны вручную; те же конструкции из Java-исходника проверяются в {@code frontend-java} и на
 * устройстве.
 */
class PointerTest {

    /** Struct хоста: поля идут в порядке объявления. */
    public static final class Particle {
        public float x;
        public Float4 v;
        public int id;

        public Particle() {
            v = new Float4(0, 0, 0, 0);
        }

        public Particle(float x, Float4 v, int id) {
            this.x = x;
            this.v = v;
            this.id = id;
        }
    }

    private static Result<Interpreter.Execution> run(IrModule m, NdRange range, Object... args) {
        return Interpreter.run(m, range, args);
    }

    private static void ok(Result<?> r) {
        assertTrue(r.isOk(), r.diagnostics().toString());
    }

    private static void fails(Result<?> r, String code) {
        assertFalse(r.isOk(), "expected error " + code);
        assertEquals(code, r.diagnostics().get(0).code(), r.diagnostics().toString());
    }

    @Test
    void writeThroughPointerToLocalIsVisibleToTheOwner() {
        IrBuilder b = new IrBuilder();
        int pf = b.ptr(AddressSpace.PRIVATE, Types.F32);
        // void addTo(float* out, float v) { *out = *out + v; }
        int add = b.beginFunction(IrModule.DEVICE, Types.VOID, pf, Types.F32);
        b.endFunction(b.block(b.store(b.param(0), b.binary(Op.ADD, b.load(b.param(0)), b.param(1)))));
        int out = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, out);
        int r = b.declareLocal(Types.F32);
        b.endFunction(b.block(
                b.localDecl(r, b.constF32(1)),
                b.exprStmt(b.call(add, b.localAddr(r), b.constF32(2))),
                b.exprStmt(b.call(add, b.localAddr(r), b.constF32(4))),
                b.store(b.elemPtr(b.param(0), b.constI32(0)), b.localGet(r))));
        float[] result = new float[1];
        ok(run(b.build(), NdRange.of1D(1), (Object) result));
        assertEquals(7f, result[0]);
    }

    @Test
    void nestedCallsPassTheSamePointerDown() {
        IrBuilder b = new IrBuilder();
        int pi = b.ptr(AddressSpace.PRIVATE, Types.I32);
        int inner = b.beginFunction(IrModule.DEVICE, Types.VOID, pi);
        b.endFunction(b.block(b.store(b.param(0), b.binary(Op.MUL, b.load(b.param(0)), b.constI32(3)))));
        int outer = b.beginFunction(IrModule.DEVICE, Types.VOID, pi);
        b.endFunction(b.block(
                b.exprStmt(b.call(inner, b.param(0))),
                b.store(b.param(0), b.binary(Op.ADD, b.load(b.param(0)), b.constI32(1)))));
        int out = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, out);
        int r = b.declareLocal(Types.I32);
        b.endFunction(b.block(
                b.localDecl(r, b.constI32(5)),
                b.exprStmt(b.call(outer, b.localAddr(r))),
                b.store(b.elemPtr(b.param(0), b.constI32(0)), b.localGet(r))));
        int[] result = new int[1];
        ok(run(b.build(), NdRange.of1D(1), (Object) result));
        assertArrayEquals(new int[]{16}, result);
    }

    @Test
    void pointerIntoGlobalArrayWithOffsetArithmetic() {
        IrBuilder b = new IrBuilder();
        int pg = b.ptr(AddressSpace.GLOBAL, Types.I32);
        // void bump(int* p, int k) { p[k] = p[k] + 100; }
        int bump = b.beginFunction(IrModule.DEVICE, Types.VOID, pg, Types.I32);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.param(1)),
                b.binary(Op.ADD, b.load(b.elemPtr(b.param(0), b.param(1))), b.constI32(100)))));
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.endFunction(b.block(
                b.exprStmt(b.call(bump, b.elemPtr(b.param(0), b.constI32(1)), b.constI32(2)))));
        int[] data = {0, 1, 2, 3, 4};
        ok(run(b.build(), NdRange.of1D(1), (Object) data));
        assertArrayEquals(new int[]{0, 1, 2, 103, 4}, data, "p = a + 1; p[2] is a[3]");
    }

    @Test
    void pointerOutOfBoundsIsAFaultNotACrash() {
        for (int shift : new int[]{3, -2}) {
            IrBuilder b = new IrBuilder();
            int pg = b.ptr(AddressSpace.GLOBAL, Types.I32);
            int read = b.beginFunction(IrModule.DEVICE, Types.I32, pg, Types.I32);
            b.endFunction(b.block(b.returnStmt(b.load(b.elemPtr(b.param(0), b.param(1))))));
            int arr = b.array(AddressSpace.GLOBAL, Types.I32);
            b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
            // указатель на a[1], сдвиг на 3 даёт a[4] (длина 4) либо на -2 даёт a[-1]
            b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)),
                    b.call(read, b.elemPtr(b.param(0), b.constI32(1)), b.constI32(shift)))));
            fails(run(b.build(), NdRange.of1D(1), (Object) new int[4]), "IN-R01");
        }
    }

    @Test
    void pointerToLocalDoesNotAllowReachingBeyondTheStack() {
        IrBuilder b = new IrBuilder();
        int pi = b.ptr(AddressSpace.PRIVATE, Types.I32);
        int peek = b.beginFunction(IrModule.DEVICE, Types.I32, pi);
        b.endFunction(b.block(b.returnStmt(b.load(b.elemPtr(b.param(0), b.constI32(1_000_000))))));
        int out = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, out);
        int r = b.declareLocal(Types.I32);
        b.endFunction(b.block(
                b.localDecl(r, b.constI32(1)),
                b.store(b.elemPtr(b.param(0), b.constI32(0)), b.call(peek, b.localAddr(r)))));
        fails(run(b.build(), NdRange.of1D(1), (Object) new int[1]), "IN-R01");
    }

    @Test
    void localWrittenThroughPointerIsReadBackAfterTheCallEvenInLoops() {
        IrBuilder b = new IrBuilder();
        int pi = b.ptr(AddressSpace.PRIVATE, Types.I32);
        int inc = b.beginFunction(IrModule.DEVICE, Types.VOID, pi);
        b.endFunction(b.block(b.store(b.param(0), b.binary(Op.ADD, b.load(b.param(0)), b.constI32(1)))));
        int out = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, out);
        int counter = b.declareLocal(Types.I32);
        int i = b.declareLocal(Types.I32);
        int init = b.localDecl(i, b.constI32(0));
        int loop = b.loop(b.binary(Op.LT, b.localGet(i), b.constI32(10)),
                b.block(b.exprStmt(b.call(inc, b.localAddr(counter)))),
                b.localSet(i, b.binary(Op.ADD, b.localGet(i), b.constI32(1))));
        b.endFunction(b.block(b.localDecl(counter, b.constI32(0)), init, loop,
                b.store(b.elemPtr(b.param(0), b.constI32(0)), b.localGet(counter))));
        int[] result = new int[1];
        ok(run(b.build(), NdRange.of1D(1), (Object) result));
        assertEquals(10, result[0]);
    }

    @Test
    void pointerToStructFieldWritesOnlyThatFieldInHostArray() {
        IrBuilder b = new IrBuilder();
        int v4 = b.vec(Types.F32, 4);
        int s = b.struct(new int[]{Types.F32, v4, Types.I32}, 0);
        b.nameStruct(s, "Particle", "x", "v", "id");
        int arr = b.array(AddressSpace.GLOBAL, s);
        int ps = b.ptr(AddressSpace.GLOBAL, s);
        // id у элемента k получает значение k * 10 через указатель на поле
        int setId = b.beginFunction(IrModule.DEVICE, Types.VOID, ps, Types.I32);
        b.endFunction(b.block(b.store(b.fieldPtr(b.param(0), 2), b.param(1))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int gid = b.declareLocal(Types.I32);
        b.endFunction(b.block(
                b.localDecl(gid, b.intrinsic(0, b.constI32(0))),
                b.exprStmt(b.call(setId, b.elemPtr(b.param(0), b.localGet(gid)),
                        b.binary(Op.MUL, b.localGet(gid), b.constI32(10))))));
        Particle[] data = {
                new Particle(1, new Float4(1, 2, 3, 4), 7),
                new Particle(2, new Float4(5, 6, 7, 8), 8),
                new Particle(3, new Float4(9, 9, 9, 9), 9)};
        ok(run(b.build(), NdRange.of1D(3), (Object) data));
        assertArrayEquals(new int[]{0, 10, 20}, new int[]{data[0].id, data[1].id, data[2].id});
        assertEquals(new Float4(5, 6, 7, 8), data[1].v, "neighbouring fields are untouched");
        assertEquals(2f, data[1].x);
    }

    @Test
    void pointerToVectorFieldAndToLocalStructField() {
        IrBuilder b = new IrBuilder();
        int v4 = b.vec(Types.F32, 4);
        int s = b.struct(new int[]{Types.F32, v4, Types.I32}, 0);
        int pv = b.ptr(AddressSpace.PRIVATE, v4);
        int out = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, out);
        int loc = b.declareLocal(s);
        int ptr = b.declareLocal(pv);
        b.endFunction(b.block(
                b.localDecl(loc, b.structBuild(s, b.constF32(1), b.build(v4, b.constF32(10), b.constF32(20),
                        b.constF32(30), b.constF32(40)), b.constI32(5))),
                b.localDecl(ptr, b.fieldPtr(b.localAddr(loc), 1)),
                b.store(b.fieldPtr(b.localAddr(loc), 0), b.constF32(-1)),
                b.store(b.elemPtr(b.param(0), b.constI32(0)), b.extract(b.load(b.localGet(ptr)), 2)),
                b.store(b.elemPtr(b.param(0), b.constI32(1)), b.fieldGet(b.localGet(loc), 0)),
                b.store(b.elemPtr(b.param(0), b.constI32(2)), b.convert(Types.F32, b.fieldGet(b.localGet(loc), 2)))));
        float[] result = new float[3];
        ok(run(b.build(), NdRange.of1D(1), (Object) result));
        assertArrayEquals(new float[]{30f, -1f, 5f}, result);
    }

    @Test
    void constantSpaceArrayIsReadable() {
        IrBuilder b = new IrBuilder();
        int table = b.array(AddressSpace.CONSTANT, Types.F32);
        int out = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, table, out);
        int gid = b.declareLocal(Types.I32);
        b.endFunction(b.block(
                b.localDecl(gid, b.intrinsic(0, b.constI32(0))),
                b.store(b.elemPtr(b.param(1), b.localGet(gid)), b.load(b.elemPtr(b.param(0), b.localGet(gid))))));
        float[] t = {3, 1, 4};
        float[] r = new float[3];
        ok(run(b.build(), NdRange.of1D(3), t, r));
        assertArrayEquals(t, r);
    }
}
