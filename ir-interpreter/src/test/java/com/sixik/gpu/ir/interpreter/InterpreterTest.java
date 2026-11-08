package com.sixik.gpu.ir.interpreter;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Модули, собранные вручную: то, что Java-фронтенд породить не может (беззнаковые типы, указатели, atomics) или
 * что нельзя сравнить с JVM (нарушения времени выполнения, ошибки запуска и аргументов).
 */
class InterpreterTest {

    // =============================================================================================
    // помощники
    // =============================================================================================

    /** Ядро с одним массивом {@code out} типа {@code elemType}; значение {@code i} записывается в {@code out[i]}. */
    private static IrModule storing(int elemType, Function<IrBuilder, int[]> values) {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, elemType);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int[] vs = values.apply(b);
        int[] st = new int[vs.length];
        for (int i = 0; i < vs.length; i++) {
            int p = b.param(0);
            st[i] = b.store(b.elemPtr(p, b.constI32(i)), vs[i]);
        }
        b.endFunction(b.block(st));
        return b.build();
    }

    private static Result<Interpreter.Execution> run(IrModule m, NdRange range, Object... args) {
        return Interpreter.run(m, range, args);
    }

    private static void assertCode(Result<?> r, String code) {
        assertFalse(r.isOk(), "ожидалась ошибка " + code);
        assertEquals(code, r.diagnostics().get(0).code(), r.diagnostics().toString());
    }

    // =============================================================================================
    // эталонный модуль из README
    // =============================================================================================

    @Test
    void computeSineFromReadmeRunsOnTheReferenceModule() {
        IrModule m = Fixtures.computeSine(); // reqd_work_group_size = 256
        float[] in = new float[512];
        for (int i = 0; i < in.length; i++) {
            in[i] = (i - 256) * 0.05f;
        }
        float[] out = new float[512];
        Result<Interpreter.Execution> r = run(m, NdRange.of1D(512), in, out);
        assertTrue(r.isOk(), r.diagnostics().toString());
        for (int i = 0; i < in.length; i++) {
            assertEquals((float) StrictMath.sin(in[i]) + 2.0f, out[i], 0f, "i=" + i);
        }
        assertEquals(512, r.value().workItems());
        assertTrue(r.value().steps() > 512);
    }

    // =============================================================================================
    // беззнаковые типы (Java их не имеет - проверяются только здесь и в AluTest)
    // =============================================================================================

    @Test
    void unsigned32DivisionShiftComparisonAndConversion() {
        IrModule m = storing(Types.U32, b -> {
            int max = b.constU32(0xFFFFFFFF);
            return new int[]{
                    b.binary(Op.DIV, max, b.constU32(2)),
                    b.binary(Op.REM, max, b.constU32(7)),
                    b.binary(Op.SHR, max, b.constU32(4)),
                    b.binary(Op.USHR, max, b.constU32(36)),
                    b.convert(Types.U32, b.binary(Op.GT, max, b.constU32(1))),
                    b.binary(Op.ADD, max, b.constU32(2)),
                    b.convert(Types.U32, b.constU64(-1L)),
                    b.convert(Types.U32, b.constF32(5e9f)),
                    b.convert(Types.U32, b.constF32(-3f)),
            };
        });
        int[] out = new int[9];
        assertTrue(run(m, NdRange.of1D(1), (Object) out).isOk());
        assertArrayEquals(new int[]{0x7FFFFFFF, 3, 0x0FFFFFFF, 0x0FFFFFFF, 1, 1, -1, -1, 0}, out);
    }

    @Test
    void unsigned64DivisionRemainderAndFloatConversions() {
        IrModule m = storing(Types.U64, b -> {
            int max = b.constU64(-1L); // 2^64 - 1
            return new int[]{
                    b.binary(Op.DIV, max, b.constU64(10)),
                    b.binary(Op.REM, max, b.constU64(10)),
                    b.binary(Op.SHR, max, b.constU64(60)),
                    b.convert(Types.U64, b.constF64(1.8446744073709552E19)),
                    b.convert(Types.U64, b.constF64(9.223372036854775808E18)),
                    b.convert(Types.U64, b.constF64(-1.0)),
                    b.convert(Types.U64, b.constI32(-1)),
            };
        });
        long[] out = new long[7];
        assertTrue(run(m, NdRange.of1D(1), (Object) out).isOk());
        assertArrayEquals(new long[]{1844674407370955161L, 5, 15, -1L, Long.MIN_VALUE, 0, -1L}, out);

        IrModule toFloat = storing(Types.F64, b -> new int[]{
                b.convert(Types.F64, b.constU64(-1L)),
                b.convert(Types.F64, b.constU64(Long.MIN_VALUE)),
                b.convert(Types.F64, b.constU64(5)),
        });
        double[] d = new double[3];
        assertTrue(run(toFloat, NdRange.of1D(1), (Object) d).isOk());
        assertArrayEquals(new double[]{1.8446744073709552E19, 9.223372036854775808E18, 5.0}, d, 0.0);
    }

    @Test
    void bitcastKeepsBitsBetweenFloatAndInteger() {
        IrModule m = storing(Types.I32, b -> new int[]{
                b.bitcast(Types.I32, b.constF32(1.0f)),
                b.bitcast(Types.I32, b.constF32(-0.0f)),
        });
        int[] out = new int[2];
        assertTrue(run(m, NdRange.of1D(1), (Object) out).isOk());
        assertArrayEquals(new int[]{0x3F800000, 0x80000000}, out);
    }

    @Test
    void storageTypesAreWrittenWithTheirWidth() {
        // convert узкого типа и запись в массив U8/I16: значения усекаются на convert, в хост попадают как byte/short
        IrBuilder b = new IrBuilder();
        int bytes = b.array(AddressSpace.GLOBAL, Types.U8);
        int shorts = b.array(AddressSpace.GLOBAL, Types.I16);
        b.beginFunction(IrModule.KERNEL, Types.VOID, bytes, shorts);
        int s0 = b.store(b.elemPtr(b.param(0), b.constI32(0)), b.convert(Types.U8, b.constI32(300)));
        int s1 = b.store(b.elemPtr(b.param(1), b.constI32(0)), b.convert(Types.I16, b.constI32(70000)));
        int s2 = b.store(b.elemPtr(b.param(1), b.constI32(1)),
                b.convert(Types.I16, b.load(b.elemPtr(b.param(0), b.constI32(1)))));
        b.endFunction(b.block(s0, s1, s2));
        byte[] u = new byte[]{0, (byte) 0xF0};
        short[] i16 = new short[2];
        assertTrue(run(b.build(), NdRange.of1D(1), u, i16).isOk());
        assertEquals((byte) 44, u[0]);
        assertEquals((short) 4464, i16[0]);
        assertEquals((short) 240, i16[1], "U8 загружается без знакового расширения");
    }

    // =============================================================================================
    // ленивый select
    // =============================================================================================

    private static IrModule lazySelect(boolean cond) {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, Types.I32);
        int divide = b.binary(Op.DIV, b.constI32(1), b.param(1));
        int sel = b.select(b.constBool(cond), b.constI32(7), divide);
        int st = b.store(b.elemPtr(b.param(0), b.constI32(0)), sel);
        b.endFunction(b.block(st));
        return b.build();
    }

    @Test
    void selectDoesNotEvaluateTheUnchosenBranch() {
        int[] out = new int[1];
        assertTrue(run(lazySelect(true), NdRange.of1D(1), out, 0).isOk(), "деление на ноль в невыбранной ветке не вычисляется");
        assertEquals(7, out[0]);
    }

    @Test
    void selectEvaluatesTheChosenBranch() {
        int[] out = new int[1];
        Result<?> r = run(lazySelect(false), NdRange.of1D(1), out, 0);
        assertCode(r, "IN-R02");
        assertEquals(0, out[0], "при ошибке хостовые массивы не изменяются");
        int[] ok = new int[1];
        assertTrue(run(lazySelect(false), NdRange.of1D(1), ok, 4).isOk());
        assertEquals(0, ok[0]);
    }

    // =============================================================================================
    // указатели и atomics
    // =============================================================================================

    @Test
    void atomicAddReturnsTheOldValueAndAccumulates() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, arr);
        int gl = b.declareLocal(Types.I32);
        int g = b.localDecl(gl, b.intrinsic(Fixtures.GET_GLOBAL_ID, b.constI32(0)));
        int old = b.intrinsic(Fixtures.ATOMIC_ADD, b.elemPtr(b.param(0), b.constI32(0)), b.constI32(3));
        int st = b.store(b.elemPtr(b.param(1), b.localGet(gl)), old);
        b.endFunction(b.block(g, st));
        int[] counter = new int[1];
        int[] seen = new int[10];
        Result<Interpreter.Execution> r = run(b.build(), NdRange.of1D(10), counter, seen);
        assertTrue(r.isOk(), r.diagnostics().toString());
        assertEquals(30, counter[0]);
        assertArrayEquals(new int[]{0, 3, 6, 9, 12, 15, 18, 21, 24, 27}, seen);
    }

    @Test
    void pointerArithmeticThroughElemPtrOfPointer() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int p2 = b.elemPtr(b.elemPtr(b.param(0), b.constI32(1)), b.constI32(2)); // &a[3]
        int st = b.store(p2, b.constI32(99));
        int p0 = b.elemPtr(b.elemPtr(b.param(0), b.constI32(3)), b.constI32(-3)); // &a[0]
        int st2 = b.store(p0, b.constI32(11));
        b.endFunction(b.block(st, st2));
        int[] a = new int[5];
        assertTrue(run(b.build(), NdRange.of1D(1), (Object) a).isOk());
        assertArrayEquals(new int[]{11, 0, 0, 99, 0}, a);
    }

    // =============================================================================================
    // нарушения времени выполнения
    // =============================================================================================

    @Test
    void writeOutOfBoundsIsADiagnosticAndLeavesArraysUntouched() {
        IrModule m = storing(Types.I32, b -> new int[]{b.constI32(1), b.constI32(2), b.constI32(3)});
        int[] out = new int[2]; // третья запись выходит за границы
        Result<?> r = run(m, NdRange.of1D(1), (Object) out);
        assertCode(r, "IN-R01");
        assertArrayEquals(new int[]{0, 0}, out);
        Diagnostic d = r.diagnostics().get(0);
        assertTrue(d.message().contains("индекс 2"), d.message());
        assertTrue(d.message().contains("work-item"), d.message());
    }

    @Test
    void negativeIndexIsOutOfBounds() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int st = b.store(b.elemPtr(b.param(0), b.constI32(-1)), b.constI32(1));
        b.endFunction(b.block(st));
        assertCode(run(b.build(), NdRange.of1D(1), (Object) new int[4]), "IN-R01");
    }

    @Test
    void readOutOfBoundsIsADiagnostic() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, arr);
        int st = b.store(b.elemPtr(b.param(1), b.constI32(0)), b.load(b.elemPtr(b.param(0), b.constI32(10))));
        b.endFunction(b.block(st));
        assertCode(run(b.build(), NdRange.of1D(1), new int[4], new int[1]), "IN-R01");
    }

    @Test
    void integerDivisionAndRemainderByZeroAreDiagnostics() {
        for (int op : new int[]{Op.DIV, Op.REM}) {
            IrBuilder b = new IrBuilder();
            int arr = b.array(AddressSpace.GLOBAL, Types.I64);
            b.beginFunction(IrModule.KERNEL, Types.VOID, arr, Types.I64);
            int st = b.store(b.elemPtr(b.param(0), b.constI32(0)), b.binary(op, b.constI64(5), b.param(1)));
            b.endFunction(b.block(st));
            assertCode(run(b.build(), NdRange.of1D(1), new long[1], 0L), "IN-R02");
        }
    }

    @Test
    void floatingPointDivisionByZeroIsNotAFault() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, Types.F32);
        int st = b.store(b.elemPtr(b.param(0), b.constI32(0)), b.binary(Op.DIV, b.constF32(1f), b.param(1)));
        b.endFunction(b.block(st));
        float[] out = new float[1];
        assertTrue(run(b.build(), NdRange.of1D(1), out, 0f).isOk());
        assertEquals(Float.POSITIVE_INFINITY, out[0]);
    }

    @Test
    void stepBudgetStopsAnInfiniteLoop() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        int loop = b.loop(b.constBool(true), b.block(), -1);
        b.endFunction(b.block(loop));
        IrModule m = b.build();
        Result<?> r = Interpreter.run(m, 0, NdRange.of1D(1), new Interpreter.Options(10_000), new Object[0]);
        assertCode(r, "IN-R03");
    }

    @Test
    void absurdPrivateArrayLengthIsADiagnosticNotAnOutOfMemory() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        int l = b.declareLocal(b.array(AddressSpace.PRIVATE, Types.I32));
        int decl = b.privateArrayDecl(l, b.constI32(Integer.MAX_VALUE));
        b.endFunction(b.block(decl));
        assertCode(run(b.build(), NdRange.of1D(1)), "IN-R04");
    }

    @Test
    void privateArrayIsZeroInitializedAndReusableAcrossIterations() {
        // for (i < 3) { int[2] t; out[i] = t[1]; t[1] = 5; }  -- каждое объявление даёт свежий нулевой массив
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int t = b.declareLocal(b.array(AddressSpace.PRIVATE, Types.I32));
        int i = b.declareLocal(Types.I32);
        int declI = b.localDecl(i, b.constI32(0));
        int decl = b.privateArrayDecl(t, b.constI32(2));
        int read = b.store(b.elemPtr(b.param(0), b.localGet(i)), b.load(b.elemPtr(b.localGet(t), b.constI32(1))));
        int write = b.store(b.elemPtr(b.localGet(t), b.constI32(1)), b.constI32(5));
        int inc = b.localSet(i, b.binary(Op.ADD, b.localGet(i), b.constI32(1)));
        int body = b.block(decl, read, write);
        int loop = b.loop(b.binary(Op.LT, b.localGet(i), b.constI32(3)), body, inc);
        b.endFunction(b.block(declI, loop));
        int[] out = new int[]{9, 9, 9};
        assertTrue(run(b.build(), NdRange.of1D(1), (Object) out).isOk());
        assertArrayEquals(new int[]{0, 0, 0}, out);
    }

    // =============================================================================================
    // запуск и аргументы
    // =============================================================================================

    private static IrModule trivialKernel() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, Types.I32, Types.F64);
        int st = b.store(b.elemPtr(b.param(0), b.constI32(0)), b.param(1));
        b.endFunction(b.block(st));
        return b.build();
    }

    @Test
    void wrongArgumentCountIsReported() {
        assertCode(run(trivialKernel(), NdRange.of1D(1), new int[1], 1), "IN-A01");
    }

    @Test
    void wrongArgumentClassesAreReportedTogether() {
        Result<?> r = run(trivialKernel(), NdRange.of1D(1), new long[1], 1L, 2.0f);
        assertFalse(r.isOk());
        assertEquals(3, r.diagnostics().size(), r.diagnostics().toString());
        assertTrue(r.diagnostics().stream().allMatch(d -> d.code().equals("IN-A02")));
        assertTrue(r.diagnostics().get(0).message().contains("int[]"), r.diagnostics().get(0).message());
    }

    @Test
    void nullArgumentIsReported() {
        assertCode(run(trivialKernel(), NdRange.of1D(1), null, 1, 2.0), "IN-A02");
    }

    @Test
    void scalarArgumentsReachTheKernel() {
        int[] out = new int[1];
        assertTrue(run(trivialKernel(), NdRange.of1D(1), out, 123, 2.0).isOk());
        assertEquals(123, out[0]);
    }

    @Test
    void functionWhichIsNotAKernelIsRejected() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.DEVICE, Types.VOID);
        b.endFunction(b.block());
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        b.endFunction(b.block());
        IrModule m = b.build();
        assertCode(Interpreter.run(m, 0, NdRange.of1D(1), Interpreter.Options.DEFAULT, new Object[0]), "IN-A00");
        assertCode(Interpreter.run(m, 7, NdRange.of1D(1), Interpreter.Options.DEFAULT, new Object[0]), "IN-A00");
        assertTrue(Interpreter.run(m, 1, NdRange.of1D(1), Interpreter.Options.DEFAULT, new Object[0]).isOk());
    }

    @Test
    void ndRangeMustMatchRequiredWorkGroupSize() {
        IrModule m = Fixtures.computeSine(); // reqd (256,1,1)
        float[] a = new float[512];
        assertCode(run(m, NdRange.of1D(100), a, a), "IN-L01");          // не делится на 256
        assertCode(run(m, NdRange.of1D(512, 128), a, a), "IN-L01");     // группа не равна требуемой
    }

    @Test
    void requiredWorkGroupAxisBeyondTheLaunchDimensionIsRejected() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        b.requireWorkGroup(4, 2, 1);
        b.endFunction(b.block());
        IrModule m = b.build();
        assertCode(run(m, NdRange.of1D(8)), "IN-L01");
        assertTrue(run(m, NdRange.of2D(8, 4)).isOk());
        assertTrue(run(m, NdRange.of2D(8, 4, 4, 2)).isOk());
    }

    @Test
    void workGroupMustDivideTheRange() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        b.endFunction(b.block());
        IrModule m = b.build();
        assertCode(run(m, NdRange.of1D(10, 4)), "IN-L01");
        assertTrue(run(m, NdRange.of1D(12, 4)).isOk());
        assertTrue(run(m, NdRange.of3D(2, 3, 4, 1, 3, 2)).isOk());
        assertCode(run(m, NdRange.of2D(6, 6, 4, 3)), "IN-L01");
    }

    @Test
    void ndRangeFactoriesRejectNonPositiveSizes() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> NdRange.of1D(0));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> NdRange.of2D(4, -1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> NdRange.of1D(4, -2));
        assertEquals(24, NdRange.of3D(2, 3, 4).workItems());
        assertEquals(2, NdRange.of2D(5, 6).dim());
        assertEquals(1, NdRange.of1D(5).global(2));
    }

    // =============================================================================================
    // валидатор и поддержка
    // =============================================================================================

    @Test
    void invalidModuleIsRejectedWithValidatorDiagnostics() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        int bad = b.node(Op.ADD, Types.I32, b.constI32(1), b.constI64(2), 0); // типы операндов различаются
        int st = b.exprStmt(bad);
        b.endFunction(b.block(st));
        Result<?> r = run(b.build(), NdRange.of1D(1));
        assertFalse(r.isOk());
        assertTrue(r.diagnostics().get(0).code().startsWith("IR-"), r.diagnostics().toString());
    }

    @Test
    void barrierAndPointersToLocalsAreReportedAsNotSupportedYet() {
        IrBuilder b = new IrBuilder();
        b.beginFunction(IrModule.KERNEL, Types.VOID);
        int st = b.barrier(1);
        b.endFunction(b.block(st));
        Result<?> r = run(b.build(), NdRange.of1D(1));
        assertCode(r, "IN-U01");
        assertTrue(r.diagnostics().get(0).message().contains("M10"), r.diagnostics().get(0).message());

        IrBuilder v = new IrBuilder();
        v.beginFunction(IrModule.KERNEL, Types.VOID);
        int l = v.declareLocal(Types.F32);
        int p = v.declareLocal(v.ptr(AddressSpace.PRIVATE, Types.F32));
        int decl = v.localDecl(l, v.constF32(1f));
        int ptr = v.localDecl(p, v.localAddr(l));
        v.endFunction(v.block(decl, ptr));
        Result<?> rv = run(v.build(), NdRange.of1D(1));
        assertCode(rv, "IN-U01");
        assertTrue(rv.diagnostics().stream().anyMatch(d -> d.message().contains("M9")), rv.diagnostics().toString());
    }

    @Test
    void everyBuiltinOfTheTableIsExecutable() {
        for (int id = 0; id < Builtins.count(); id++) {
            if (Builtins.isValid(id)) {
                assertTrue(Machine.supportsBuiltin(id), "интерпретатор не знает builtin " + Builtins.name(id));
            }
        }
        assertFalse(Machine.supportsBuiltin(-1));
        assertFalse(Machine.supportsBuiltin(Builtins.count()));
    }
}
