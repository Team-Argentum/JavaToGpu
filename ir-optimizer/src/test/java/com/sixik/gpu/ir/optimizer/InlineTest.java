package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Fixtures;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;
import com.sixik.gpu.ir.interpreter.Interpreter;
import com.sixik.gpu.ir.interpreter.NdRange;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Встраивание {@code INLINE_ALWAYS} ({@link Inline}, FRONTEND_ASM_RUNTIME.md, S4-3): после оптимизатора вызовов нет,
 * результат на интерпретаторе тот же; вызов, перенос которого изменил бы смысл, остаётся вызовом.
 */
class InlineTest {

    private static final int N = 8;
    private static final int[] INPUT = {-3, 0, 5, -7, 2, 9, -1, 4};

    private static int calls(IrModule m) {
        int c = 0;
        for (int n = 0; n < m.nodeCount(); n++) {
            if (m.op(n) == Op.CALL) {
                c++;
            }
        }
        return c;
    }

    private static int[] run(IrModule m) {
        assertFalse(Validator.validate(m).hasErrors(), Validator.validate(m).diagnostics().toString());
        int[] a = INPUT.clone();
        Result<Interpreter.Execution> r = Interpreter.run(m, m.functionCount() - 1, NdRange.of1D(N),
                Interpreter.Options.DEFAULT, new Object[]{a});
        assertTrue(r.isOk(), r.diagnostics().toString());
        return a;
    }

    private static OptimizeResult optimize(IrModule m, int level) {
        OptimizeResult r = Optimizer.optimize(m, OptimizeOptions.DEFAULT.withLevel(level).withTimeBudget(null));
        assertTrue(r.failures().isEmpty(), r.failures().toString());
        return r;
    }

    /** Результат совпадает на уровнях 1 и 2; число оставшихся вызовов - {@code expectedCalls}. */
    private static IrModule check(IrModule m, int expectedCalls) {
        int[] expected = run(m);
        IrModule out = null;
        for (int level = 1; level <= 2; level++) {
            out = optimize(m, level).module();
            assertArrayEquals(expected, run(out), "opt=" + level);
            assertEquals(expectedCalls, calls(out), "calls left at opt=" + level);
        }
        return out;
    }

    /** Ядро {@code a[i] = body(i)} над {@code int[] a}; {@code devices} строит device-функции заранее. */
    private static IrModule kernel(IrBuilder b, int arr, java.util.function.IntUnaryOperator value) {
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int id = b.declareLocal(Types.I32);
        int decl = b.localDecl(id, b.intrinsic(Fixtures.GET_GLOBAL_ID, b.constI32(0)));
        int v = value.applyAsInt(id);
        b.endFunction(b.block(decl, b.store(b.elemPtr(b.param(0), b.localGet(id)), v)));
        return b.build();
    }

    private static int device(IrBuilder b, int policy, int ret, int[] params, Consumer<IrBuilder> body) {
        int f = b.beginFunction(IrModule.DEVICE, ret, params);
        b.inlinePolicy(policy);
        body.accept(b);
        return f;
    }

    /** {@code sq(x) = x * x}; {@code a[i] = sq(a[i]) + 1}: вызов исчез, функция удалена. */
    @Test
    void tailReturnIsInlined() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        int sq = device(b, IrModule.INLINE_ALWAYS, Types.I32, new int[]{Types.I32},
                x -> x.endFunction(x.block(x.returnStmt(x.binary(Op.MUL, x.param(0), x.param(0))))));
        IrModule m = kernel(b, arr, id -> b.binary(Op.ADD,
                b.call(sq, b.load(b.elemPtr(b.param(0), b.localGet(id)))), b.constI32(1)));
        IrModule out = check(m, 0);
        assertEquals(1, out.functionCount(), "the inlined function is dead");
    }

    /** {@code relu(x) { if (x < 0) return 0; return x; }} - ранний return, обёртка {@code do-while(false)}. */
    @Test
    void earlyReturnIsInlined() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        int relu = device(b, IrModule.INLINE_ALWAYS, Types.I32, new int[]{Types.I32}, x -> x.endFunction(x.block(
                x.ifStmt(x.binary(Op.LT, x.param(0), x.constI32(0)), x.block(x.returnStmt(x.constI32(0))), -1),
                x.returnStmt(x.param(0)))));
        IrModule m = kernel(b, arr, id -> b.call(relu, b.load(b.elemPtr(b.param(0), b.localGet(id)))));
        check(m, 0);
    }

    /** {@code void bump(int[] a, int i) { a[i] = a[i] * 2; }} в {@code expr_stmt}, затем {@code a[i] = a[i] + 1}. */
    @Test
    void voidCallWithAnArrayIsInlined() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        int bump = device(b, IrModule.INLINE_ALWAYS, Types.VOID, new int[]{arr, Types.I32}, x -> x.endFunction(
                x.block(x.store(x.elemPtr(x.param(0), x.param(1)),
                        x.binary(Op.MUL, x.load(x.elemPtr(x.param(0), x.param(1))), x.constI32(2))))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int id = b.declareLocal(Types.I32);
        int decl = b.localDecl(id, b.intrinsic(Fixtures.GET_GLOBAL_ID, b.constI32(0)));
        int call = b.exprStmt(b.call(bump, b.param(0), b.localGet(id)));
        int st = b.store(b.elemPtr(b.param(0), b.localGet(id)),
                b.binary(Op.ADD, b.load(b.elemPtr(b.param(0), b.localGet(id))), b.constI32(1)));
        b.endFunction(b.block(decl, call, st));
        check(b.build(), 0);
    }

    /** {@code outer(x) = inner(x) + inner(x + 1)}, {@code inner(x) = x * 3}: оба ALWAYS - вызовов не остаётся. */
    @Test
    void nestedCallsAreInlined() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        int inner = device(b, IrModule.INLINE_ALWAYS, Types.I32, new int[]{Types.I32},
                x -> x.endFunction(x.block(x.returnStmt(x.binary(Op.MUL, x.param(0), x.constI32(3))))));
        int outer = device(b, IrModule.INLINE_ALWAYS, Types.I32, new int[]{Types.I32},
                x -> x.endFunction(x.block(x.returnStmt(x.binary(Op.ADD, x.call(inner, x.param(0)),
                        x.call(inner, x.binary(Op.ADD, x.param(0), x.constI32(1))))))));
        IrModule m = kernel(b, arr, id -> b.call(outer, b.load(b.elemPtr(b.param(0), b.localGet(id)))));
        IrModule out = check(m, 0);
        assertEquals(1, out.functionCount());
    }

    /** {@code return} внутри цикла: {@code break} ушёл бы в цикл функции - вызов остаётся. */
    @Test
    void returnInsideALoopIsNotInlined() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        int f = device(b, IrModule.INLINE_ALWAYS, Types.I32, new int[]{Types.I32}, x -> {
            int j = x.declareLocal(Types.I32);
            int d = x.localDecl(j, x.constI32(0));
            int body = x.block(x.ifStmt(x.binary(Op.GE, x.localGet(j), x.param(0)), x.block(x.returnStmt(x.localGet(j))),
                    -1));
            int loop = x.loop(x.binary(Op.LT, x.localGet(j), x.constI32(4)), body,
                    x.localSet(j, x.binary(Op.ADD, x.localGet(j), x.constI32(1))));
            x.endFunction(x.block(d, loop, x.returnStmt(x.constI32(-1))));
        });
        IrModule m = kernel(b, arr, id -> b.call(f, b.load(b.elemPtr(b.param(0), b.localGet(id)))));
        check(m, 1);
    }

    /**
     * {@code a[a[i] & 7] = set(a, i)}, где {@code set} пишет {@code a[i] = 100} и возвращает 1: индекс читает
     * {@code a[i]} до вызова, перенос тела вперёд изменил бы адрес записи - вызов остаётся. (Вызов с эффектом в IR
     * стоит только значением стейтмента, IR-E01, поэтому раньше него вычисляется лишь адрес {@code store}.)
     */
    @Test
    void callAfterAMemoryReadIsNotInlined() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        int set = device(b, IrModule.INLINE_ALWAYS, Types.I32, new int[]{arr, Types.I32}, x -> x.endFunction(x.block(
                x.store(x.elemPtr(x.param(0), x.param(1)), x.constI32(100)), x.returnStmt(x.constI32(1)))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int id = b.declareLocal(Types.I32);
        int decl = b.localDecl(id, b.intrinsic(Fixtures.GET_GLOBAL_ID, b.constI32(0)));
        int index = b.binary(Op.AND, b.load(b.elemPtr(b.param(0), b.localGet(id))), b.constI32(7));
        int st = b.store(b.elemPtr(b.param(0), index), b.call(set, b.param(0), b.localGet(id)));
        b.endFunction(b.block(decl, st));
        check(b.build(), 1);
    }

    /** Тот же {@code set} значением {@code local_decl}: до вызова ничего не вычисляется - встраивается. */
    @Test
    void effectCallAsAStatementValueIsInlined() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        int set = device(b, IrModule.INLINE_ALWAYS, Types.I32, new int[]{arr, Types.I32}, x -> x.endFunction(x.block(
                x.store(x.elemPtr(x.param(0), x.param(1)), x.constI32(100)), x.returnStmt(x.constI32(1)))));
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr);
        int id = b.declareLocal(Types.I32);
        int r = b.declareLocal(Types.I32);
        int decl = b.localDecl(id, b.intrinsic(Fixtures.GET_GLOBAL_ID, b.constI32(0)));
        int dr = b.localDecl(r, b.call(set, b.param(0), b.localGet(id)));
        int st = b.store(b.elemPtr(b.param(0), b.localGet(id)),
                b.binary(Op.ADD, b.load(b.elemPtr(b.param(0), b.localGet(id))), b.localGet(r)));
        b.endFunction(b.block(decl, dr, st));
        check(b.build(), 0);
    }

    /** Без политики ALWAYS вызов не встраивается (оптимизатор не делает этого по своей оценке). */
    @Test
    void defaultPolicyKeepsTheCall() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        int sq = device(b, IrModule.INLINE_DEFAULT, Types.I32, new int[]{Types.I32},
                x -> x.endFunction(x.block(x.returnStmt(x.binary(Op.MUL, x.param(0), x.param(0))))));
        IrModule m = kernel(b, arr, id -> b.call(sq, b.load(b.elemPtr(b.param(0), b.localGet(id)))));
        check(m, 1);
    }

    /** Ветка {@code select} вычисляется не всегда: вызов в ней остаётся. */
    @Test
    void callInASelectBranchIsNotInlined() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        int sq = device(b, IrModule.INLINE_ALWAYS, Types.I32, new int[]{Types.I32},
                x -> x.endFunction(x.block(x.returnStmt(x.binary(Op.MUL, x.param(0), x.param(0))))));
        IrModule m = kernel(b, arr, id -> b.select(
                b.binary(Op.GT, b.load(b.elemPtr(b.param(0), b.localGet(id))), b.constI32(0)),
                b.call(sq, b.load(b.elemPtr(b.param(0), b.localGet(id)))), b.constI32(-1)));
        check(m, 1);
    }
}
