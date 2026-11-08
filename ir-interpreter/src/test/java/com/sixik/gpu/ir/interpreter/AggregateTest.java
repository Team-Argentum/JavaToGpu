package com.sixik.gpu.ir.interpreter;

import com.sixik.gpu.api.types.Float4;
import com.sixik.gpu.api.types.Int2;
import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Векторы и struct в интерпретаторе: модули, собранные вручную, и объекты хоста (REWORK_ARCHITECTURE.md, 5.20).
 * Java-ядра с теми же операциями проверяются дифференциально в {@link DifferentialTest}.
 */
class AggregateTest {

    /** Struct хоста: поля идут в порядке объявления и соответствуют struct-типу IR. */
    public static final class Particle {
        public float x;
        public float y;
        public Float4 v;
        public int id;

        public Particle() {
            v = new Float4(0, 0, 0, 0);
        }

        public Particle(float x, float y, Float4 v, int id) {
            this.x = x;
            this.y = y;
            this.v = v;
            this.id = id;
        }
    }

    /** Класс с неподходящим числом полей. */
    public static final class Wrong {
        public float x;
    }

    private static Result<Interpreter.Execution> run(IrModule m, NdRange range, Object... args) {
        return Interpreter.run(m, range, args);
    }

    private static void ok(Result<?> r) {
        assertTrue(r.isOk(), r.diagnostics().toString());
    }

    private static int particle(IrBuilder b) {
        int v4 = b.vec(Types.F32, 4);
        int s = b.struct(new int[]{Types.F32, Types.F32, v4, Types.I32}, 0);
        b.nameStruct(s, "Particle", "x", "y", "v", "id");
        return s;
    }

    // ---- векторы ----------------------------------------------------------------------------------

    @Test
    void vectorOperationsAreLaneWise() {
        IrBuilder b = new IrBuilder();
        int v4 = b.vec(Types.F32, 4);
        int out = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, out);
        int a = b.declareLocal(v4);
        int c = b.declareLocal(v4);
        java.util.List<Integer> st = new java.util.ArrayList<>();
        st.add(b.localDecl(a, b.build(v4, b.constF32(1), b.constF32(2), b.constF32(3), b.constF32(4))));
        st.add(b.localDecl(c, b.binary(Op.MUL, b.binary(Op.ADD, b.localGet(a), b.splat(v4, b.constF32(10))),
                b.build(v4, b.constF32(1), b.constF32(-1), b.constF32(2), b.constF32(0.5f)))));
        for (int lane = 0; lane < 4; lane++) {
            st.add(b.store(b.elemPtr(b.param(0), b.constI32(lane)), b.extract(b.localGet(c), lane)));
        }
        // insert, shuffle (с повтором и с уменьшением), neg
        int ins = b.insert(b.localGet(a), 2, b.constF32(99));
        st.add(b.store(b.elemPtr(b.param(0), b.constI32(4)), b.extract(ins, 2)));
        st.add(b.store(b.elemPtr(b.param(0), b.constI32(5)), b.extract(b.localGet(a), 2)));
        int sh = b.shuffle(b.localGet(a), 3, 3, 0);
        int v3 = b.vec(Types.F32, 3);
        assertEquals(v3, b.typeOf(sh));
        st.add(b.store(b.elemPtr(b.param(0), b.constI32(6)), b.extract(sh, 0)));
        st.add(b.store(b.elemPtr(b.param(0), b.constI32(7)), b.extract(b.shuffle(b.localGet(a), 3, 3, 0), 2)));
        st.add(b.store(b.elemPtr(b.param(0), b.constI32(8)), b.extract(b.neg(b.localGet(a)), 1)));
        b.endFunction(b.block(st.stream().mapToInt(Integer::intValue).toArray()));
        float[] result = new float[9];
        ok(run(b.build(), NdRange.of1D(1), (Object) result));
        assertArrayEquals(new float[]{11, -12, 26, 7, 99, 3, 4, 1, -2}, result);
    }

    @Test
    void integerVectorDivisionByZeroFaultsInTheLane() {
        IrBuilder b = new IrBuilder();
        int v2 = b.vec(Types.I32, 2);
        int out = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, out);
        int q = b.binary(Op.DIV, b.build(v2, b.constI32(7), b.constI32(7)), b.build(v2, b.constI32(2), b.constI32(0)));
        b.endFunction(b.block(b.store(b.elemPtr(b.param(0), b.constI32(0)), b.extract(q, 0))));
        Result<Interpreter.Execution> r = run(b.build(), NdRange.of1D(1), (Object) new int[1]);
        assertEquals("IN-R02", r.diagnostics().get(0).code());
        assertTrue(r.diagnostics().get(0).message().contains("компоненте 1"));
    }

    @Test
    void builtinsAndConversionsWorkOnVectors() {
        IrBuilder b = new IrBuilder();
        int v2 = b.vec(Types.F32, 2);
        int i2 = b.vec(Types.I32, 2);
        int outF = b.array(AddressSpace.GLOBAL, Types.F32);
        int outI = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, outF, outI);
        int x = b.build(v2, b.constF32(0.5f), b.constF32(2f));
        int sin = b.intrinsic(Builtins.idOf("sin"), x);
        int mx = b.intrinsic(Builtins.idOf("max"), x, b.splat(v2, b.constF32(1f)));
        int fma = b.intrinsic(Builtins.idOf("fma"), x, x, b.splat(v2, b.constF32(1f)));
        int conv = b.convert(i2, b.build(v2, b.constF32(1.9f), b.constF32(-3e10f)));
        int bits = b.bitcast(i2, b.build(v2, b.constF32(1f), b.constF32(-0f)));
        b.endFunction(b.block(
                b.store(b.elemPtr(b.param(0), b.constI32(0)), b.extract(sin, 1)),
                b.store(b.elemPtr(b.param(0), b.constI32(1)), b.extract(mx, 0)),
                b.store(b.elemPtr(b.param(0), b.constI32(2)), b.extract(fma, 1)),
                b.store(b.elemPtr(b.param(1), b.constI32(0)), b.extract(conv, 0)),
                b.store(b.elemPtr(b.param(1), b.constI32(1)), b.extract(conv, 1)),
                b.store(b.elemPtr(b.param(1), b.constI32(2)), b.extract(bits, 0)),
                b.store(b.elemPtr(b.param(1), b.constI32(3)), b.extract(bits, 1))));
        float[] f = new float[3];
        int[] i = new int[4];
        ok(run(b.build(), NdRange.of1D(1), f, i));
        assertEquals((float) StrictMath.sin(2.0), f[0]);
        assertEquals(1f, f[1]);
        assertEquals(5f, f[2]);
        assertArrayEquals(new int[]{1, Integer.MIN_VALUE, 0x3f800000, 0x80000000}, i);
    }

    @Test
    void vectorHostArraysAreReplacedByNewObjects() {
        IrBuilder b = new IrBuilder();
        int v4 = b.vec(Types.F32, 4);
        int arr = b.array(AddressSpace.GLOBAL, v4);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int g = b.declareLocal(Types.I32);
        b.endFunction(b.block(
                b.localDecl(g, b.intrinsic(Builtins.idOf("getGlobalId"), b.constI32(0))),
                b.store(b.elemPtr(b.param(0), b.localGet(g)),
                        b.binary(Op.MUL, b.load(b.elemPtr(b.param(0), b.localGet(g))), b.splat(v4, b.constF32(2f))))));
        Float4[] data = {new Float4(1, 2, 3, 4), new Float4(-1, 0, 0.5f, 8)};
        Float4 first = data[0];
        ok(run(b.build(), NdRange.of1D(2), (Object) data));
        assertEquals(new Float4(2, 4, 6, 8), data[0]);
        assertEquals(new Float4(-2, 0, 1, 16), data[1]);
        assertEquals(new Float4(1, 2, 3, 4), first, "прежний объект неизменяем");
    }

    @Test
    void vectorByValueParameterAndPrivateArrayOfVectors() {
        IrBuilder b = new IrBuilder();
        int i2 = b.vec(Types.I32, 2);
        int out = b.array(AddressSpace.GLOBAL, Types.I32);
        int priv = b.array(AddressSpace.PRIVATE, i2);
        b.beginFunction(IrModule.KERNEL, Types.VOID, i2, out);
        int p = b.declareLocal(priv);
        b.endFunction(b.block(
                b.privateArrayDecl(p, b.constI32(3)),
                b.store(b.elemPtr(b.localGet(p), b.constI32(2)), b.binary(Op.ADD, b.param(0), b.splat(i2, b.constI32(100)))),
                b.store(b.elemPtr(b.param(1), b.constI32(0)), b.extract(b.load(b.elemPtr(b.localGet(p), b.constI32(2))), 0)),
                b.store(b.elemPtr(b.param(1), b.constI32(1)), b.extract(b.load(b.elemPtr(b.localGet(p), b.constI32(2))), 1)),
                b.store(b.elemPtr(b.param(1), b.constI32(2)), b.extract(b.load(b.elemPtr(b.localGet(p), b.constI32(1))), 1)),
                b.store(b.elemPtr(b.param(1), b.constI32(3)), b.arrayLen(b.localGet(p)))));
        int[] result = new int[4];
        ok(run(b.build(), NdRange.of1D(1), new Int2(1, 2), result));
        assertArrayEquals(new int[]{101, 102, 0, 3}, result);
    }

    @Test
    void lazySelectAndDeviceFunctionsWithVectors() {
        IrBuilder b = new IrBuilder();
        int v2 = b.vec(Types.F32, 2);
        int f = b.beginFunction(IrModule.DEVICE, v2, v2, Types.F32);
        b.endFunction(b.block(b.returnStmt(b.binary(Op.MUL, b.param(0), b.splat(v2, b.param(1))))));
        int out = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, out);
        int cond = b.binary(Op.GT, b.constI32(2), b.constI32(1));
        int pick = b.select(cond, b.call(f, b.build(v2, b.constF32(1), b.constF32(2)), b.constF32(3)),
                b.splat(v2, b.constF32(-1)));
        b.endFunction(b.block(
                b.store(b.elemPtr(b.param(0), b.constI32(0)), b.extract(pick, 0)),
                b.store(b.elemPtr(b.param(0), b.constI32(1)), b.extract(pick, 1))));
        float[] result = new float[2];
        ok(run(b.build(), NdRange.of1D(1), (Object) result));
        assertArrayEquals(new float[]{3, 6}, result);
    }

    // ---- struct -----------------------------------------------------------------------------------

    @Test
    void structValuesAreImmutableAndNested() {
        IrBuilder b = new IrBuilder();
        int s = particle(b);
        int v4 = b.vec(Types.F32, 4);
        int outer = b.struct(new int[]{s, Types.F64}, 0);
        int out = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, out);
        int o = b.declareLocal(outer);
        int vec = b.build(v4, b.constF32(5), b.constF32(6), b.constF32(7), b.constF32(8));
        int inner = b.structBuild(s, b.constF32(1), b.constF32(2), vec, b.constI32(9));
        int changed = b.fieldSet(b.localGet(o), 1, b.constF64(0.5));
        int changedInner = b.fieldSet(b.localGet(o), 0, b.fieldSet(b.fieldGet(b.localGet(o), 0), 3, b.constI32(77)));
        b.endFunction(b.block(
                b.localDecl(o, b.structBuild(outer, inner, b.constF64(4))),
                b.store(b.elemPtr(b.param(0), b.constI32(0)), b.fieldGet(b.fieldGet(b.localGet(o), 0), 0)),
                b.store(b.elemPtr(b.param(0), b.constI32(1)), b.extract(b.fieldGet(b.fieldGet(b.localGet(o), 0), 2), 3)),
                b.store(b.elemPtr(b.param(0), b.constI32(2)), b.convert(Types.F32, b.fieldGet(b.localGet(o), 1))),
                b.store(b.elemPtr(b.param(0), b.constI32(3)), b.convert(Types.F32, b.fieldGet(changed, 1))),
                b.store(b.elemPtr(b.param(0), b.constI32(4)), b.convert(Types.F32, b.fieldGet(b.fieldGet(changedInner, 0), 3))),
                b.store(b.elemPtr(b.param(0), b.constI32(5)), b.convert(Types.F32, b.fieldGet(b.fieldGet(b.localGet(o), 0), 3)))));
        float[] result = new float[6];
        ok(run(b.build(), NdRange.of1D(1), (Object) result));
        assertArrayEquals(new float[]{1, 8, 4, 0.5f, 77, 9}, result);
    }

    @Test
    void structArraysAreUpdatedInPlaceOnTheHost() {
        IrBuilder b = new IrBuilder();
        int s = particle(b);
        int arr = b.array(AddressSpace.GLOBAL, s);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int g = b.declareLocal(Types.I32);
        int cur = b.declareLocal(s);
        b.endFunction(b.block(
                b.localDecl(g, b.intrinsic(Builtins.idOf("getGlobalId"), b.constI32(0))),
                b.localDecl(cur, b.load(b.elemPtr(b.param(0), b.localGet(g)))),
                b.store(b.elemPtr(b.param(0), b.localGet(g)),
                        b.fieldSet(b.fieldSet(b.localGet(cur), 0,
                                        b.binary(Op.ADD, b.fieldGet(b.localGet(cur), 0), b.constF32(1))),
                                3, b.binary(Op.MUL, b.fieldGet(b.localGet(cur), 3), b.constI32(2))))));
        Particle p0 = new Particle(1, 2, new Float4(1, 1, 1, 1), 5);
        Particle p1 = new Particle(10, 20, new Float4(2, 2, 2, 2), -3);
        Particle[] data = {p0, p1};
        ok(run(b.build(), NdRange.of1D(2), (Object) data));
        assertSame(p0, data[0], "struct обновляется на месте: идентичность объекта сохраняется");
        assertEquals(2f, p0.x);
        assertEquals(2f, p0.y);
        assertEquals(10, p0.id);
        assertEquals(11f, p1.x);
        assertEquals(-6, p1.id);
        assertEquals(new Float4(2, 2, 2, 2), p1.v, "вектор-поле не тронуто");
    }

    @Test
    void structKernelParameterReturnAndDeviceFunctions() {
        IrBuilder b = new IrBuilder();
        int s = particle(b);
        int f = b.beginFunction(IrModule.DEVICE, s, s, Types.F32);
        b.endFunction(b.block(b.returnStmt(b.fieldSet(b.param(0), 1, b.binary(Op.ADD, b.fieldGet(b.param(0), 1), b.param(1))))));
        int out = b.array(AddressSpace.GLOBAL, s);
        b.beginFunction(IrModule.KERNEL, Types.VOID, s, out);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(1), b.constI32(1)), b.call(f, b.param(0), b.constF32(100)))));
        Particle in = new Particle(1, 2, new Float4(0, 0, 0, 1), 4);
        Particle[] res = {new Particle(), new Particle()};
        ok(run(b.build(), NdRange.of1D(1), in, res));
        assertEquals(102f, res[1].y);
        assertEquals(1f, res[1].x);
        assertEquals(4, res[1].id);
        assertEquals(2f, in.y, "параметр-значение хоста не изменяется");
    }

    // ---- ошибки -----------------------------------------------------------------------------------

    @Test
    void nullElementsAndMismatchedClassesAreArgumentErrors() {
        IrBuilder b = new IrBuilder();
        int s = particle(b);
        int arr = b.array(AddressSpace.GLOBAL, s);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        b.endFunction(b.block());
        IrModule m = b.build();

        Result<Interpreter.Execution> nulls = run(m, NdRange.of1D(1), (Object) new Particle[]{new Particle(), null});
        assertEquals("IN-A02", nulls.diagnostics().get(0).code());
        assertTrue(nulls.diagnostics().get(0).message().contains("элемент 1"), nulls.diagnostics().toString());

        Result<Interpreter.Execution> wrong = run(m, NdRange.of1D(1), (Object) new Wrong[]{new Wrong()});
        assertEquals("IN-A02", wrong.diagnostics().get(0).code());
        assertTrue(wrong.diagnostics().get(0).message().contains("Wrong"), wrong.diagnostics().toString());

        Result<Interpreter.Execution> prim = run(m, NdRange.of1D(1), (Object) new float[2]);
        assertEquals("IN-A02", prim.diagnostics().get(0).code());
    }

    @Test
    void outOfBoundsAccessToAggregateArraysIsAFault() {
        IrBuilder b = new IrBuilder();
        int s = particle(b);
        int arr = b.array(AddressSpace.GLOBAL, s);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int v = b.declareLocal(s);
        b.endFunction(b.block(b.localDecl(v, b.load(b.elemPtr(b.param(0), b.constI32(2))))));
        Result<Interpreter.Execution> r = run(b.build(), NdRange.of1D(1),
                (Object) new Particle[]{new Particle(), new Particle()});
        assertEquals("IN-R01", r.diagnostics().get(0).code());
        assertTrue(r.diagnostics().get(0).message().contains("длина 2"), r.diagnostics().get(0).message());
    }

    @Test
    void arrayLengthCountsElementsNotCells() {
        IrBuilder b = new IrBuilder();
        int s = particle(b);
        int arr = b.array(AddressSpace.GLOBAL, s);
        int out = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, out);
        b.endFunction(b.block(b.store(b.elemPtr(b.param(1), b.constI32(0)), b.arrayLen(b.param(0)))));
        int[] result = new int[1];
        ok(run(b.build(), NdRange.of1D(1), new Particle[]{new Particle(), new Particle(), new Particle()}, result));
        assertEquals(3, result[0]);
    }
}
