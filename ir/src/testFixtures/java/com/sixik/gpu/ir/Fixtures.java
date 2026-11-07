package com.sixik.gpu.ir;

import java.util.ArrayList;
import java.util.List;

/**
 * Общие фикстуры тестов (source set {@code testFixtures}, доступны тестам всех модулей): эталонный модуль из REWORK_ARCHITECTURE.md (5.11) и «богатый» валидный модуль,
 * затрагивающий почти все опкоды каталога, а также мини-конструкторы для негативных тестов.
 */
public final class Fixtures {

    public static final int GET_GLOBAL_ID = Builtins.idOf("getGlobalId");
    public static final int GET_LOCAL_ID = Builtins.idOf("getLocalId");
    public static final int GET_GROUP_ID = Builtins.idOf("getGroupId");
    public static final int SIN = Builtins.idOf("sin");
    public static final int ASSUME_UNIFORM = Builtins.idOf("assumeUniform");
    public static final int ATOMIC_ADD = Builtins.idOf("atomicAdd");

    private Fixtures() {
    }

    /** Часть тела: параметры функции. */
    public interface Params {
        int[] types(IrBuilder b);
    }

    /** Часть тела: стейтменты функции (строятся после {@code beginFunction}). */
    public interface Body {
        int[] statements(IrBuilder b);
    }

    public static final Params NO_PARAMS = b -> new int[0];

    public static IrModule function(int kind, int ret, Params params, Body body) {
        IrBuilder b = new IrBuilder();
        int[] ps = params.types(b);
        b.beginFunction(kind, ret, ps);
        int[] st = body.statements(b);
        b.endFunction(b.block(st));
        return b.build();
    }

    public static IrModule kernel(Params params, Body body) {
        return function(IrModule.KERNEL, Types.VOID, params, body);
    }

    /** Эталон из REWORK_ARCHITECTURE.md, 5.11: 15 узлов, kernel {@code computeSine}. */
    public static IrModule computeSine() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, arr);
        b.requireWorkGroup(256, 1, 1);
        b.nameFunction("computeSine");
        b.nameParams("input", "output");
        int id = b.declareLocal(Types.I32);
        b.nameLocal(id, "id");
        int n0 = b.constI32(0);
        int n1 = b.intrinsic(GET_GLOBAL_ID, n0);
        int n2 = b.localDecl(id, n1);
        int n3 = b.param(0);
        int n4 = b.localGet(id);
        int n5 = b.elemPtr(n3, n4);
        int n6 = b.load(n5);
        int n7 = b.intrinsic(SIN, n6);
        int n8 = b.constF32(2.0f);
        int n9 = b.binary(Op.ADD, n7, n8);
        int n10 = b.param(1);
        int n11 = b.localGet(id);
        int n12 = b.elemPtr(n10, n11);
        int n13 = b.store(n12, n9);
        int n14 = b.block(n2, n13);
        b.span(n13, 3, 5);
        b.endFunction(n14);
        return b.build();
    }

    /** Тот же {@link #computeSine()}, но без единого отладочного имени и позиции. */
    public static IrModule computeSineWithoutDebug() {
        IrBuilder b = new IrBuilder();
        int arr = b.array(AddressSpace.GLOBAL, Types.F32);
        b.beginFunction(IrModule.KERNEL, Types.VOID, arr, arr);
        b.requireWorkGroup(256, 1, 1);
        int id = b.declareLocal(Types.I32);
        int n0 = b.constI32(0);
        int n1 = b.intrinsic(GET_GLOBAL_ID, n0);
        int n2 = b.localDecl(id, n1);
        int n3 = b.param(0);
        int n4 = b.localGet(id);
        int n5 = b.elemPtr(n3, n4);
        int n6 = b.load(n5);
        int n7 = b.intrinsic(SIN, n6);
        int n8 = b.constF32(2.0f);
        int n9 = b.binary(Op.ADD, n7, n8);
        int n10 = b.param(1);
        int n11 = b.localGet(id);
        int n12 = b.elemPtr(n10, n11);
        int n13 = b.store(n12, n9);
        b.endFunction(b.block(n2, n13));
        return b.build();
    }

    /**
     * Богатый валидный модуль: 3 функции (device-чистая, device-с-эффектами, kernel), все виды типов,
     * все виды control flow, векторы, struct, указатели, массивы private/local, atomics, barrier.
     */
    public static IrModule rich() {
        IrBuilder b = new IrBuilder();
        int a = b.array(AddressSpace.GLOBAL, Types.F32);
        int ai = b.array(AddressSpace.GLOBAL, Types.I32);
        int v4 = b.vec(Types.F32, 4);
        int s = b.struct(new int[]{Types.F32, Types.F32, v4}, 16);
        b.nameStruct(s, "GamePos", "x", "y", "v");
        int as = b.array(AddressSpace.GLOBAL, s);
        int pf = b.ptr(AddressSpace.PRIVATE, Types.F32);

        // f0: чистая device-функция scale(x, k)
        int f0 = b.beginFunction(IrModule.DEVICE, Types.F32, Types.F32, Types.F32);
        b.nameFunction("scale");
        b.endFunction(b.block(b.returnStmt(b.binary(Op.MUL, b.param(0), b.param(1)))));

        // f1: device-функция с эффектом inc(FloatPtr)
        b.beginFunction(IrModule.DEVICE, Types.VOID, pf);
        b.nameFunction("inc");
        int loaded = b.load(b.param(0));
        int sum = b.binary(Op.ADD, loaded, b.constF32(1.0f));
        int stored = b.store(b.param(0), sum);
        int f1 = 1;
        b.endFunction(b.block(stored));

        // f2: kernel
        b.beginFunction(IrModule.KERNEL, Types.VOID, a, ai, as, a);
        b.requireWorkGroup(64, 1, 1);
        b.inlinePolicy(IrModule.INLINE_ALWAYS);
        b.optLevel(0);
        b.required("SIMD");
        b.nameFunction("rich");
        b.nameParams("in", "idx", "items", "out");
        int id = b.declareLocal(Types.I32);
        int acc = b.declareLocal(Types.F32);
        int tmp = b.declareLocal(Types.F32);
        int pa = b.declareLocal(b.array(AddressSpace.PRIVATE, Types.F32));
        int i = b.declareLocal(Types.I32);
        int cnt = b.declareLocal(Types.I32);
        int v = b.declareLocal(v4);
        int st = b.declareLocal(s);
        int la = b.declareLocal(b.array(AddressSpace.LOCAL, Types.F32));
        int x2 = b.declareLocal(Types.F32);
        int iv = b.declareLocal(Types.I32);
        b.nameLocal(id, "id");
        b.nameLocal(acc, "acc");

        List<Integer> body = new ArrayList<>();
        body.add(b.localDecl(id, b.intrinsic(GET_GLOBAL_ID, b.constI32(0))));
        body.add(b.localDecl(acc, b.load(b.elemPtr(b.param(0), b.localGet(id)))));
        body.add(b.localDecl(tmp, b.constF32(0.0f)));
        body.add(b.exprStmt(b.call(f1, b.localAddr(tmp))));
        body.add(b.localArrayDecl(la, b.constI32(64)));
        body.add(b.privateArrayDecl(pa, b.constI32(4)));
        body.add(b.store(b.elemPtr(b.localGet(pa), b.constI32(0)), b.localGet(acc)));

        // for (i = 0; i < 4; i++) { acc += pa[i]; if (acc > 100) break; }
        int loopInit = b.localDecl(i, b.constI32(0));
        int cond = b.binary(Op.LT, b.localGet(i), b.constI32(4));
        int sumStmt = b.localSet(acc, b.binary(Op.ADD, b.localGet(acc),
                b.load(b.elemPtr(b.localGet(pa), b.localGet(i)))));
        int brk = b.ifStmt(b.binary(Op.GT, b.localGet(acc), b.constF32(100.0f)), b.block(b.breakStmt()), -1);
        int skip = b.ifStmt(b.binary(Op.LT, b.localGet(i), b.constI32(0)), b.block(b.continueStmt()), -1);
        int loopBody = b.block(sumStmt, brk, skip);
        int upd = b.localSet(i, b.binary(Op.ADD, b.localGet(i), b.constI32(1)));
        body.add(b.block(loopInit, b.loop(cond, loopBody, upd)));

        // do { cnt++; } while (cnt < 3);
        int dd = b.localDecl(cnt, b.constI32(0));
        int dbody = b.block(b.localSet(cnt, b.binary(Op.ADD, b.localGet(cnt), b.constI32(1))));
        int dcond = b.binary(Op.LT, b.localGet(cnt), b.constI32(3));
        body.add(b.block(dd, b.doWhile(dbody, dcond)));

        // switch (id % 4) { case 0: acc *= 2; break; case 1: break; default: break; }
        int key = b.binary(Op.REM, b.localGet(id), b.constI32(4));
        int c0 = b.block(b.localSet(acc, b.binary(Op.MUL, b.localGet(acc), b.constF32(2.0f))), b.breakStmt());
        int c1 = b.block(b.breakStmt());
        int cd = b.block(b.breakStmt());
        body.add(b.switchStmt(key, new boolean[]{false, false, true}, new int[]{0, 1, 0}, new int[]{c0, c1, cd}));

        // векторы
        int vbuild = b.build(v4, b.localGet(acc), b.localGet(acc), b.localGet(tmp), b.constF32(1.0f));
        body.add(b.localDecl(v, vbuild));
        int ins = b.insert(b.localGet(v), 1, b.constF32(2.0f));
        int shf = b.shuffle(ins, 3, 2, 1, 0);
        int ext = b.extract(shf, 2);
        body.add(b.localSet(acc, b.binary(Op.ADD, b.localGet(acc), ext)));
        body.add(b.localSet(v, b.binary(Op.ADD, b.splat(v4, b.constF32(1.0f)), b.localGet(v))));

        // struct
        body.add(b.localDecl(st, b.structBuild(s, b.localGet(acc), b.localGet(tmp), b.localGet(v))));
        body.add(b.localSet(st, b.fieldSet(b.localGet(st), 0, b.constF32(5.0f))));
        body.add(b.localDecl(x2, b.fieldGet(b.localGet(st), 1)));
        body.add(b.store(b.elemPtr(b.param(2), b.localGet(id)), b.localGet(st)));
        body.add(b.store(b.fieldPtr(b.elemPtr(b.param(2), b.localGet(id)), 1), b.localGet(x2)));

        // вызов чистой функции, преобразования, сдвиги, bitcast, select, логика
        body.add(b.localSet(acc, b.call(f0, b.localGet(acc), b.constF32(2.0f))));
        body.add(b.localDecl(iv, b.convert(Types.I32, b.localGet(acc))));
        int ush = b.binary(Op.USHR, b.localGet(iv), b.constI32(1));
        int shl = b.binary(Op.SHL, ush, b.constI32(2));
        int bc = b.bitcast(Types.U32, shl);
        int cmp = b.binary(Op.NE, bc, b.constU32(0));
        body.add(b.localSet(acc, b.select(cmp, b.localGet(acc), b.constF32(0.0f))));
        int both = b.binary(Op.AND, b.not(b.binary(Op.LT, b.localGet(id), b.constI32(0))),
                b.binary(Op.LE, b.localGet(id), b.constI32(10)));
        body.add(b.ifStmt(both, b.block(b.localSet(acc, b.constF32(0.0f))), b.block(b.localSet(acc, b.constF32(1.0f)))));

        // остальная арифметика, логика, сдвиги, длина массива
        body.add(b.localSet(acc, b.neg(b.binary(Op.DIV, b.binary(Op.SUB, b.localGet(acc), b.constF32(1.0f)),
                b.constF32(2.0f)))));
        body.add(b.localSet(iv, b.binary(Op.SHR, b.localGet(iv), b.constI32(3))));
        body.add(b.localSet(iv, b.arrayLen(b.param(0))));
        int logic = b.binary(Op.XOR,
                b.binary(Op.OR, b.constBool(false), b.binary(Op.EQ, b.localGet(id), b.constI32(0))),
                b.binary(Op.GE, b.localGet(id), b.constI32(1)));
        body.add(b.ifStmt(logic, b.block(b.localSet(acc, b.constF32(3.0f))), -1));

        // atomics, local memory и barrier на верхнем уровне
        body.add(b.exprStmt(b.intrinsic(ATOMIC_ADD, b.elemPtr(b.param(1), b.constI32(0)), b.constI32(1))));
        body.add(b.store(b.elemPtr(b.localGet(la), b.constI32(0)), b.localGet(acc)));
        body.add(b.barrier(1));
        body.add(b.localSet(acc, b.load(b.elemPtr(b.localGet(la), b.constI32(0)))));
        body.add(b.store(b.elemPtr(b.param(3), b.localGet(id)), b.localGet(acc)));

        int[] stmts = body.stream().mapToInt(Integer::intValue).toArray();
        b.endFunction(b.block(stmts));
        return b.build();
    }
}
