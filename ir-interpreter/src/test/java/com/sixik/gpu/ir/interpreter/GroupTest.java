package com.sixik.gpu.ir.interpreter;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Группы work-item-ов (M10): local-память на группу, барьер как точка передачи управления, нарушения барьера и атомики.
 * Модули собраны вручную; те же конструкции из Java-исходника проверяет {@code LocalMemoryTest} на образцах-ядрах.
 */
class GroupTest {

    private static final int GID = Builtins.idOf("getGlobalId");
    private static final int LID = Builtins.idOf("getLocalId");

    private static Result<Interpreter.Execution> run(IrModule m, NdRange range, Object... args) {
        return Interpreter.run(m, range, args);
    }

    private static void ok(Result<?> r) {
        assertTrue(r.isOk(), r.diagnostics().toString());
    }

    private static String fails(Result<?> r, String code) {
        assertFalse(r.isOk(), "expected error " + code);
        assertEquals(code, r.diagnostics().get(0).code(), r.diagnostics().toString());
        return r.diagnostics().get(0).message();
    }

    /** Ядро с группой {@code wg}, массивом {@code tile[wg]} в local и выходом {@code out}; тело задаёт {@code body}. */
    private interface Body {
        int[] build(IrBuilder b, int gid, int lid, int tile);
    }

    private static IrModule kernel(int wg, Body body) {
        IrBuilder b = new IrBuilder();
        int out = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, out);
        b.requireWorkGroup(wg, 1, 1);
        int gid = b.declareLocal(Types.I32);
        int lid = b.declareLocal(Types.I32);
        int tile = b.declareLocal(b.array(AddressSpace.LOCAL, Types.I32));
        java.util.List<Integer> st = new java.util.ArrayList<>();
        st.add(b.localArrayDecl(tile, b.constI32(wg)));
        st.add(b.localDecl(gid, b.intrinsic(GID, b.constI32(0))));
        st.add(b.localDecl(lid, b.intrinsic(LID, b.constI32(0))));
        for (int s : body.build(b, gid, lid, tile)) {
            st.add(s);
        }
        b.endFunction(b.block(st.stream().mapToInt(Integer::intValue).toArray()));
        return b.build();
    }

    private static int at(IrBuilder b, int array, int index) {
        return b.elemPtr(array, index);
    }

    @Test
    void barrierMakesWritesOfTheWholeGroupVisible() {
        // tile[lid] = lid + 1; barrier; out[gid] = tile[(lid + 1) % 4]: без барьера поздние work-item-ы читали бы нули
        IrModule m = kernel(4, (b, gid, lid, tile) -> new int[]{
                b.store(at(b, b.localGet(tile), b.localGet(lid)), b.binary(Op.ADD, b.localGet(lid), b.constI32(1))),
                b.barrier(1),
                b.store(at(b, b.param(0), b.localGet(gid)), b.load(at(b, b.localGet(tile),
                        b.binary(Op.REM, b.binary(Op.ADD, b.localGet(lid), b.constI32(1)), b.constI32(4)))))});
        int[] out = new int[8];
        ok(run(m, NdRange.of1D(8), (Object) out));
        assertArrayEquals(new int[]{2, 3, 4, 1, 2, 3, 4, 1}, out);
    }

    @Test
    void localMemoryBelongsToTheGroupAndStartsZeroed() {
        // все work-item-ы группы атомарно прибавляют 1 к tile[0]; после барьера каждый видит число work-item-ов СВОЕЙ группы
        IrModule m = kernel(3, (b, gid, lid, tile) -> new int[]{
                b.exprStmt(b.intrinsic(Builtins.idOf("atomicAdd"), at(b, b.localGet(tile), b.constI32(0)), b.constI32(1))),
                b.barrier(1),
                b.store(at(b, b.param(0), b.localGet(gid)), b.load(at(b, b.localGet(tile), b.constI32(0))))});
        int[] out = new int[9];
        ok(run(m, NdRange.of1D(9), (Object) out));
        assertArrayEquals(new int[]{3, 3, 3, 3, 3, 3, 3, 3, 3}, out, "three groups, each with its own memory, starting from zero");
    }

    @Test
    void localMemoryIsSharedByTheGroupAlsoWithoutBarriers() {
        // барьеров нет: work-item-ы идут по очереди, поздние видят запись lid == 0
        IrModule m = kernel(4, (b, gid, lid, tile) -> new int[]{
                b.ifStmt(b.binary(Op.EQ, b.localGet(lid), b.constI32(0)),
                        b.block(b.store(at(b, b.localGet(tile), b.constI32(0)), b.binary(Op.ADD, b.localGet(gid), b.constI32(100)))), -1),
                b.store(at(b, b.param(0), b.localGet(gid)), b.load(at(b, b.localGet(tile), b.constI32(0))))});
        int[] out = new int[8];
        ok(run(m, NdRange.of1D(8), (Object) out));
        assertArrayEquals(new int[]{100, 100, 100, 100, 104, 104, 104, 104}, out);
    }

    @Test
    void severalPhasesAreRunInOrderAndStepsAreCounted() {
        // пять фаз, разделённых барьерами: результат зависит от строгой синхронности фаз
        IrModule m = kernel(4, (b, gid, lid, tile) -> {
            // узлы, читающие память, нельзя делить между стейтментами (IR-E02): каждое использование строится заново
            java.util.function.IntSupplier mine = () -> at(b, b.localGet(tile), b.localGet(lid));
            java.util.function.IntSupplier next = () -> at(b, b.localGet(tile),
                    b.binary(Op.REM, b.binary(Op.ADD, b.localGet(lid), b.constI32(1)), b.constI32(4)));
            // чтение соседа и запись в свою ячейку разделены барьером: иначе work-item-ы, идущие по очереди, гонялись бы
            int seen = b.declareLocal(Types.I32);
            return new int[]{
                    b.store(mine.getAsInt(), b.localGet(lid)),
                    b.barrier(1),
                    b.localDecl(seen, b.load(next.getAsInt())),
                    b.barrier(1),
                    b.store(mine.getAsInt(), b.binary(Op.ADD, b.localGet(seen), b.constI32(10))),
                    b.barrier(1),
                    b.localSet(seen, b.load(next.getAsInt())),
                    b.barrier(1),
                    b.store(mine.getAsInt(), b.binary(Op.ADD, b.localGet(seen), b.constI32(100))),
                    b.barrier(1),
                    b.store(at(b, b.param(0), b.localGet(gid)), b.load(mine.getAsInt()))};
        });
        int[] out = new int[4];
        Result<Interpreter.Execution> r = run(m, NdRange.of1D(4), (Object) out);
        ok(r);
        // после фазы 1: t[i] = i; фаза 2: t[i] = t[i+1] + 10; фаза 3: t[i] = t[i+1] + 100 (чтение до записей фазы нужны барьеры)
        int[] p1 = {0, 1, 2, 3};
        int[] p2 = new int[4];
        int[] p3 = new int[4];
        for (int i = 0; i < 4; i++) {
            p2[i] = p1[(i + 1) % 4] + 10;
        }
        for (int i = 0; i < 4; i++) {
            p3[i] = p2[(i + 1) % 4] + 100;
        }
        assertArrayEquals(p3, out);
        assertTrue(r.value().steps() >= 4 * 12, "steps of the group's work-items are accounted for");
    }

    @Test
    void twoDimensionalGroupsRunInRowMajorOrderWithinTheGroup() {
        IrBuilder b = new IrBuilder();
        int out = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, out);
        b.requireWorkGroup(2, 2, 1);
        int tile = b.declareLocal(b.array(AddressSpace.LOCAL, Types.I32));
        int lx = b.declareLocal(Types.I32);
        int ly = b.declareLocal(Types.I32);
        int gx = b.declareLocal(Types.I32);
        int gy = b.declareLocal(Types.I32);
        int slot = b.declareLocal(Types.I32);
        b.endFunction(b.block(
                b.localArrayDecl(tile, b.constI32(4)),
                b.localDecl(lx, b.intrinsic(LID, b.constI32(0))),
                b.localDecl(ly, b.intrinsic(LID, b.constI32(1))),
                b.localDecl(gx, b.intrinsic(GID, b.constI32(0))),
                b.localDecl(gy, b.intrinsic(GID, b.constI32(1))),
                b.localDecl(slot, b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(ly), b.constI32(2)), b.localGet(lx))),
                b.store(at(b, b.localGet(tile), b.localGet(slot)),
                        b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(gy), b.constI32(4)), b.localGet(gx))),
                b.barrier(1),
                b.store(at(b, b.param(0), b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(gy), b.constI32(4)), b.localGet(gx))),
                        b.load(at(b, b.localGet(tile), b.binary(Op.SUB, b.constI32(3), b.localGet(slot)))))));
        int[] result = new int[16];
        ok(run(b.build(), NdRange.of2D(4, 4), (Object) result));
        // значение клетки (x, y) группы 2x2 приходит от клетки, зеркальной внутри группы
        int[] expected = new int[16];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int mx = (x / 2) * 2 + (1 - x % 2);
                int my = (y / 2) * 2 + (1 - y % 2);
                expected[y * 4 + x] = my * 4 + mx;
            }
        }
        assertArrayEquals(expected, result);
    }

    @Test
    void barrierInsideADeviceFunctionIsReachedByTheWholeGroup() {
        IrBuilder b = new IrBuilder();
        int f = b.beginFunction(IrModule.DEVICE, Types.VOID);
        b.endFunction(b.block(b.barrier(1)));
        int out = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, out);
        b.requireWorkGroup(2, 1, 1);
        int tile = b.declareLocal(b.array(AddressSpace.LOCAL, Types.I32));
        int gid = b.declareLocal(Types.I32);
        int lid = b.declareLocal(Types.I32);
        b.endFunction(b.block(
                b.localArrayDecl(tile, b.constI32(2)),
                b.localDecl(gid, b.intrinsic(GID, b.constI32(0))),
                b.localDecl(lid, b.intrinsic(LID, b.constI32(0))),
                b.store(at(b, b.localGet(tile), b.localGet(lid)), b.constI32(7)),
                b.exprStmt(b.call(f)),
                b.store(at(b, b.param(0), b.localGet(gid)), b.load(at(b, b.localGet(tile), b.binary(Op.SUB, b.constI32(1), b.localGet(lid)))))));
        int[] result = new int[4];
        ok(run(b.build(), NdRange.of1D(4), (Object) result));
        assertArrayEquals(new int[]{7, 7, 7, 7}, result);
    }

    // ---- нарушения -----------------------------------------------------------------------------

    @Test
    void workItemThatSkipsTheBarrierIsReported() {
        IrModule m = kernel(4, (b, gid, lid, tile) -> new int[]{
                b.ifStmt(b.binary(Op.NE, b.localGet(lid), b.constI32(0)), b.block(b.barrier(1)), -1)});
        String msg = fails(run(m, NdRange.of1D(4), (Object) new int[4]), "IN-R05");
        assertTrue(msg.contains("without reaching barrier"), msg);
    }

    @Test
    void workItemsWaitingOnDifferentBarriersAreReported() {
        IrModule m = kernel(4, (b, gid, lid, tile) -> new int[]{
                b.ifStmt(b.binary(Op.EQ, b.binary(Op.REM, b.localGet(lid), b.constI32(2)), b.constI32(0)),
                        b.block(b.barrier(1)), b.block(b.barrier(1)))});
        String msg = fails(run(m, NdRange.of1D(4), (Object) new int[4]), "IN-R05");
        assertTrue(msg.contains("different barriers"), msg);
    }

    @Test
    void faultOfOneWorkItemStopsTheWholeGroupWithoutHanging() {
        IrModule m = kernel(4, (b, gid, lid, tile) -> new int[]{
                b.ifStmt(b.binary(Op.EQ, b.localGet(lid), b.constI32(2)),
                        b.block(b.store(at(b, b.param(0), b.constI32(1000)), b.constI32(1))), -1),
                b.barrier(1),
                b.store(at(b, b.param(0), b.localGet(gid)), b.constI32(5))});
        int[] out = new int[4];
        Result<Interpreter.Execution> r = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> run(m, NdRange.of1D(4), (Object) out));
        fails(r, "IN-R01");
        assertArrayEquals(new int[4], out, "on an error the host arrays are not changed");
    }

    @Test
    void infiniteLoopBetweenBarriersHitsTheStepBudget() {
        IrModule m = kernel(2, (b, gid, lid, tile) -> new int[]{
                b.loop(b.constBool(true), b.block(), -1), b.barrier(1)});
        Result<Interpreter.Execution> r = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> Interpreter.run(m, m.functionCount() - 1, NdRange.of1D(2), new Interpreter.Options(10_000), new Object[]{new int[2]}));
        fails(r, "IN-R03");
    }

    // ---- атомики -------------------------------------------------------------------------------

    private static int atomic(IrBuilder b, String name, int ptr, int value) {
        return b.intrinsic(Builtins.idOf(name), ptr, value);
    }

    @Test
    void everyAtomicOperationReturnsTheOldValueAndStoresTheNewOne() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.I32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, arr);
        // cell = a[0]; результаты старых значений в r[0..7]
        String[] ops = {"atomicAdd", "atomicSub", "atomicMin", "atomicMax", "atomicAnd", "atomicOr", "atomicXor", "atomicXchg"};
        int[] operands = {5, 3, 4, 40, 0b1100, 0b0011, 0b0101, 77};
        int[] st = new int[ops.length];
        for (int i = 0; i < ops.length; i++) {
            st[i] = b.store(at(b, b.param(1), b.constI32(i)), atomic(b, ops[i], at(b, b.param(0), b.constI32(0)), b.constI32(operands[i])));
        }
        b.endFunction(b.block(st));
        int[] cell = {10};
        int[] old = new int[ops.length];
        ok(run(b.build(), NdRange.of1D(1), cell, old));
        // 10 +5=15 -3=12 min4=4 max40=40 &12=8 |3=11 ^5=14 xchg77=77
        assertArrayEquals(new int[]{10, 15, 12, 4, 40, 8, 11, 14}, old);
        assertEquals(77, cell[0]);
    }

    @Test
    void minMaxOfUnsignedAndLongTypesAreComparedByTheirOwnOrder() {
        IrBuilder b = new IrBuilder();
        int u32 = b.array(AddressSpace.GLOBAL, Types.U32);
        int i64 = b.array(AddressSpace.GLOBAL, Types.I64);
        int u64 = b.array(AddressSpace.GLOBAL, Types.U64);
        b.beginFunction(IrModule.KERNEL, Types.VOID, u32, i64, u64);
        b.endFunction(b.block(
                b.exprStmt(atomic(b, "atomicMax", at(b, b.param(0), b.constI32(0)), b.constU32(0xFFFFFFFE))),
                b.exprStmt(atomic(b, "atomicMin", at(b, b.param(0), b.constI32(1)), b.constU32(0xFFFFFFFE))),
                b.exprStmt(atomic(b, "atomicMax", at(b, b.param(1), b.constI32(0)), b.constI64(-5))),
                b.exprStmt(atomic(b, "atomicMin", at(b, b.param(1), b.constI32(1)), b.constI64(Long.MIN_VALUE))),
                b.exprStmt(atomic(b, "atomicMax", at(b, b.param(2), b.constI32(0)), b.constU64(-2L))),
                b.exprStmt(atomic(b, "atomicAdd", at(b, b.param(2), b.constI32(1)), b.constU64(1)))));
        int[] a = {3, 3};
        long[] l = {-9, 4};
        long[] u = {7, -1L};
        ok(run(b.build(), NdRange.of1D(1), a, l, u));
        assertEquals(0xFFFFFFFE, a[0], "u32: 0xFFFFFFFE > 3");
        assertEquals(3, a[1], "u32: min(3, 0xFFFFFFFE) = 3");
        assertEquals(-5, l[0], "i64: max(-9, -5)");
        assertEquals(Long.MIN_VALUE, l[1]);
        assertEquals(-2L, u[0], "u64: 2^64-2 > 7");
        assertEquals(0L, u[1], "u64: 2^64-1 + 1 wraps to 0");
    }

    @Test
    void atomicsWorkOnLocalMemoryAndReturnPreviousValues() {
        // гистограмма в local: каждый work-item добавляет 1 в ячейку lid % 2; после барьера каждый читает свою ячейку
        IrModule m = kernel(6, (b, gid, lid, tile) -> new int[]{
                b.exprStmt(atomic(b, "atomicAdd", at(b, b.localGet(tile), b.binary(Op.REM, b.localGet(lid), b.constI32(2))), b.constI32(1))),
                b.barrier(1),
                b.store(at(b, b.param(0), b.localGet(gid)), b.load(at(b, b.localGet(tile), b.binary(Op.REM, b.localGet(lid), b.constI32(2)))))});
        int[] out = new int[12];
        ok(run(m, NdRange.of1D(12), (Object) out));
        assertArrayEquals(new int[]{3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3}, out);
    }
}
