package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Генератор случайных корректных ядер для дифференциального фаззинга оптимизатора.
 *
 * <p>Ядро {@code (int[] inI, float[] inF, int[] outI, float[] outF)}: несколько целых и вещественных локалей, случайные
 * присваивания, {@code if/else}, ограниченные циклы со счётчиком, вложенные блоки с собственными объявлениями, чтения
 * входа и записи в выход по индексу {@code gid * SLOTS + k}. В конце все локали пишутся в выход, поэтому любое неверное
 * преобразование видно в результате. Целое деление и остаток - только на ненулевую константу (деление на ноль -
 * ошибка исполнения, а не значение).</p>
 */
final class RandomPrograms {

    /** Элементов входа/выхода на work-item. */
    static final int SLOTS = 8;

    private static final int SIN = Builtins.idOf("sin");
    private static final int SQRT = Builtins.idOf("sqrt");
    private static final int MIN = Builtins.idOf("min");
    private static final int MAX = Builtins.idOf("max");
    private static final int FMA = Builtins.idOf("fma");
    private static final int GID = Builtins.idOf("getGlobalId");
    private static final int[] INT_CONSTS = {0, 1, -1, 2, 3, 7, 8, 16, 31, 32, 255, -128, Integer.MAX_VALUE,
            Integer.MIN_VALUE, 1 << 20};
    private static final float[] FLOAT_CONSTS = {0f, -0f, 1f, -1f, 0.5f, 2f, 4f, 3f, 0.1f, 1e10f, -1e-10f};

    private final Random r;
    private final IrBuilder b = new IrBuilder();
    private final List<Integer> ints = new ArrayList<>();
    private final List<Integer> floats = new ArrayList<>();
    private int gid;
    private int budget;

    private RandomPrograms(long seed) {
        this.r = new Random(seed);
    }

    /**
     * @param seed зерно
     * @return случайное корректное ядро
     */
    static IrModule generate(long seed) {
        return new RandomPrograms(seed).build();
    }

    private IrModule build() {
        int ai = b.array(AddressSpace.GLOBAL, Types.I32);
        int af = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, ai, af, ai, af);
        List<Integer> st = new ArrayList<>();
        gid = b.declareLocal(Types.I32);
        st.add(b.localDecl(gid, b.intrinsic(GID, b.constI32(0))));
        int nInts = 2 + r.nextInt(4);
        int nFloats = 1 + r.nextInt(4);
        for (int i = 0; i < nInts; i++) {
            int l = b.declareLocal(Types.I32);
            budget = 6;
            st.add(b.localDecl(l, intExpr(2)));
            ints.add(l);
        }
        for (int i = 0; i < nFloats; i++) {
            int l = b.declareLocal(Types.F32);
            budget = 6;
            st.add(b.localDecl(l, floatExpr(2)));
            floats.add(l);
        }
        int n = 3 + r.nextInt(8);
        for (int i = 0; i < n; i++) {
            st.add(stmt(2));
        }
        // все значения видимы в выходе
        for (int i = 0; i < ints.size(); i++) {
            st.add(b.store(b.elemPtr(b.param(2), slot(i % SLOTS)), b.localGet(ints.get(i))));
        }
        for (int i = 0; i < floats.size(); i++) {
            st.add(b.store(b.elemPtr(b.param(3), slot(i % SLOTS)), b.localGet(floats.get(i))));
        }
        b.endFunction(b.block(toArray(st)));
        return b.build();
    }

    private static int[] toArray(List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = l.get(i);
        }
        return a;
    }

    /** {@code gid * SLOTS + k}. */
    private int slot(int k) {
        return b.binary(Op.ADD, b.binary(Op.MUL, b.localGet(gid), b.constI32(SLOTS)), b.constI32(k));
    }

    private <T> T pick(List<T> l) {
        return l.get(r.nextInt(l.size()));
    }

    // ---- стейтменты ----------------------------------------------------------------------------

    private int stmt(int depth) {
        budget = 8;
        int c = r.nextInt(depth > 0 ? 9 : 5);
        switch (c) {
            case 0, 1 -> {
                return b.localSet(pick(ints), intExpr(3));
            }
            case 2, 3 -> {
                return b.localSet(pick(floats), floatExpr(3));
            }
            case 4 -> {
                return r.nextBoolean()
                        ? b.store(b.elemPtr(b.param(2), slot(r.nextInt(SLOTS))), intExpr(2))
                        : b.store(b.elemPtr(b.param(3), slot(r.nextInt(SLOTS))), floatExpr(2));
            }
            case 5, 6 -> {
                int cond = boolExpr(2);
                int then = block(depth - 1);
                int other = r.nextBoolean() ? block(depth - 1) : -1;
                return b.ifStmt(cond, then, other);
            }
            case 7 -> {
                // { int t = expr; x = x op t; } - объявление во вложенной области
                int t = b.declareLocal(Types.I32);
                int target = pick(ints);
                int decl = b.localDecl(t, intExpr(2));
                int use = b.localSet(target, b.binary(Op.XOR, b.localGet(target), b.localGet(t)));
                return b.block(decl, use);
            }
            default -> {
                // for (c = 0; c < K; c++) { ... }
                int counter = b.declareLocal(Types.I32);
                int k = 1 + r.nextInt(4);
                int decl = b.localDecl(counter, b.constI32(0));
                int body = block(depth - 1);
                int loop = b.loop(b.binary(Op.LT, b.localGet(counter), b.constI32(k)), body,
                        b.localSet(counter, b.binary(Op.ADD, b.localGet(counter), b.constI32(1))));
                return b.block(decl, loop);
            }
        }
    }

    private int block(int depth) {
        int n = 1 + r.nextInt(3);
        int[] s = new int[n];
        for (int i = 0; i < n; i++) {
            s[i] = stmt(Math.max(0, depth));
        }
        return b.block(s);
    }

    // ---- выражения -----------------------------------------------------------------------------

    private boolean leaf(int depth) {
        budget--;
        return depth <= 0 || budget <= 0 || r.nextInt(4) == 0;
    }

    private int intExpr(int depth) {
        if (leaf(depth)) {
            return switch (r.nextInt(4)) {
                case 0 -> b.constI32(INT_CONSTS[r.nextInt(INT_CONSTS.length)]);
                case 1 -> b.load(b.elemPtr(b.param(0), slot(r.nextInt(SLOTS))));
                default -> ints.isEmpty() ? b.constI32(r.nextInt(100)) : b.localGet(pick(ints));
            };
        }
        int c = r.nextInt(12);
        switch (c) {
            case 0, 1, 2 -> {
                int[] ops = {Op.ADD, Op.SUB, Op.MUL, Op.AND, Op.OR, Op.XOR, Op.SHL, Op.SHR, Op.USHR};
                return b.binary(ops[r.nextInt(ops.length)], intExpr(depth - 1), intExpr(depth - 1));
            }
            case 3 -> {
                int[] divisors = {1, 2, 3, 4, 7, 8, -1, -4, 16};
                return b.binary(r.nextBoolean() ? Op.DIV : Op.REM, intExpr(depth - 1),
                        b.constI32(divisors[r.nextInt(divisors.length)]));
            }
            case 4 -> {
                return b.select(boolExpr(depth - 1), intExpr(depth - 1), intExpr(depth - 1));
            }
            case 5 -> {
                return b.convert(Types.I32, floatExpr(depth - 1));
            }
            case 6 -> {
                return r.nextBoolean() ? b.neg(intExpr(depth - 1)) : b.not(intExpr(depth - 1));
            }
            case 7 -> {
                return b.intrinsic(r.nextBoolean() ? MIN : MAX, intExpr(depth - 1), intExpr(depth - 1));
            }
            case 8 -> {
                // x * 2^k и x / 2^k для беззнакового: проверка strength reduction
                int u = b.convert(Types.U32, intExpr(depth - 1));
                int p = b.constU32(1 << r.nextInt(6));
                return b.convert(Types.I32, b.binary(r.nextBoolean() ? Op.DIV : Op.REM, u, p));
            }
            default -> {
                int x = intExpr(depth - 1);
                return b.binary(Op.ADD, x, b.constI32(r.nextInt(3) - 1));
            }
        }
    }

    private int floatExpr(int depth) {
        if (leaf(depth)) {
            return switch (r.nextInt(4)) {
                case 0 -> b.constF32(FLOAT_CONSTS[r.nextInt(FLOAT_CONSTS.length)]);
                case 1 -> b.load(b.elemPtr(b.param(1), slot(r.nextInt(SLOTS))));
                default -> floats.isEmpty() ? b.constF32(1.5f) : b.localGet(pick(floats));
            };
        }
        int c = r.nextInt(10);
        switch (c) {
            case 0, 1, 2 -> {
                int[] ops = {Op.ADD, Op.SUB, Op.MUL, Op.DIV};
                return b.binary(ops[r.nextInt(ops.length)], floatExpr(depth - 1), floatExpr(depth - 1));
            }
            case 3 -> {
                return b.select(boolExpr(depth - 1), floatExpr(depth - 1), floatExpr(depth - 1));
            }
            case 4 -> {
                return b.convert(Types.F32, intExpr(depth - 1));
            }
            case 5 -> {
                return b.intrinsic(r.nextBoolean() ? SIN : SQRT, floatExpr(depth - 1));
            }
            case 6 -> {
                return b.intrinsic(FMA, floatExpr(depth - 1), floatExpr(depth - 1), floatExpr(depth - 1));
            }
            case 7 -> {
                return b.neg(floatExpr(depth - 1));
            }
            default -> {
                float[] k = {1f, -1f, 0f, -0f, 2f, 0.25f};
                int[] ops = {Op.ADD, Op.SUB, Op.MUL, Op.DIV};
                return b.binary(ops[r.nextInt(ops.length)], floatExpr(depth - 1), b.constF32(k[r.nextInt(k.length)]));
            }
        }
    }

    private int boolExpr(int depth) {
        if (leaf(depth)) {
            return b.binary(Op.LT, intExpr(0), intExpr(0));
        }
        int c = r.nextInt(5);
        int[] cmp = {Op.EQ, Op.NE, Op.LT, Op.LE, Op.GT, Op.GE};
        return switch (c) {
            case 0 -> b.binary(cmp[r.nextInt(cmp.length)], intExpr(depth - 1), intExpr(depth - 1));
            case 1 -> b.binary(cmp[r.nextInt(cmp.length)], floatExpr(depth - 1), floatExpr(depth - 1));
            case 2 -> b.not(boolExpr(depth - 1));
            case 3 -> b.binary(r.nextBoolean() ? Op.AND : Op.OR, boolExpr(depth - 1), boolExpr(depth - 1));
            default -> b.binary(Op.EQ, intExpr(depth - 1), intExpr(depth - 1));
        };
    }
}
