package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.DebugInfo;
import com.sixik.gpu.ir.IrBuilder;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Преобразование между плоской ареной {@link IrModule} и рабочими деревьями оптимизатора.
 *
 * <p>{@link #lift} поднимает каждую функцию в {@link FnTree}; {@link #lower} собирает модуль заново через
 * {@link IrBuilder}. Сборка детерминирована: типы пересоздаются в исходном порядке (id типов сохраняются),
 * функции идут в исходном порядке, но «вызываемые раньше вызывающих» (билдеру нужен тип результата вызываемой
 * функции), недостижимые device-функции пропускаются, а локали без ссылок выбрасываются.</p>
 *
 * <p>Тип каждого пересобранного узла сверяется с типом дерева: ошибка прохода ловится в момент сборки
 * ({@link IllegalStateException}), а не позже в валидаторе.</p>
 */
final class Codec {

    private Codec() {
    }

    /** Поднятый модуль: исходный модуль (таблица типов, отладка) и деревья функций. */
    static final class ModuleTree {
        /** Исходный модуль. */
        final IrModule source;
        /** Функции в исходной нумерации. */
        final FnTree[] fns;
        /** Типы, добавленные поверх таблицы исходного модуля: тройки {@code (вид, a, b)} в порядке создания. */
        final List<int[]> extraTypes;

        ModuleTree(IrModule source, FnTree[] fns) {
            this(source, fns, new ArrayList<>());
        }

        ModuleTree(IrModule source, FnTree[] fns, List<int[]> extraTypes) {
            this.source = source;
            this.fns = fns;
            this.extraTypes = extraTypes;
        }

        /**
         * Находит тип {@code (вид, a, b)} в исходной таблице либо регистрирует новый; новые типы создаются при сборке
         * сразу после исходных, поэтому их id известны заранее.
         *
         * @param kind {@code Types.KIND_PTR}, {@code KIND_ARRAY} или {@code KIND_VEC}
         * @param a    address space либо скаляр
         * @param b    pointee, элемент либо число компонент
         * @return id типа
         */
        int typeId(int kind, int a, int b) {
            for (int t = Types.FIRST_DYNAMIC; t < source.typeCount(); t++) {
                if (source.typeKind(t) == kind && source.typeA(t) == a && source.typeB(t) == b) {
                    return t;
                }
            }
            for (int i = 0; i < extraTypes.size(); i++) {
                int[] e = extraTypes.get(i);
                if (e[0] == kind && e[1] == a && e[2] == b) {
                    return source.typeCount() + i;
                }
            }
            extraTypes.add(new int[]{kind, a, b});
            return source.typeCount() + extraTypes.size() - 1;
        }

        /**
         * @return копия, где каждая функция скопирована отдельно
         */
        ModuleTree copy() {
            FnTree[] c = new FnTree[fns.length];
            for (int i = 0; i < c.length; i++) {
                c[i] = fns[i].copy();
            }
            return new ModuleTree(source, c, new ArrayList<>(extraTypes));
        }
    }

    // =============================================================================================
    // подъём
    // =============================================================================================

    /**
     * Поднимает все функции модуля.
     *
     * @param m корректный (прошедший валидатор) модуль
     * @return деревья функций
     * @throws UnsupportedOperationException если в таблице типов есть виды, которых нет в v1, либо тело нативное
     */
    static ModuleTree lift(IrModule m) {
        for (int t = Types.FIRST_DYNAMIC; t < m.typeCount(); t++) {
            int k = m.typeKind(t);
            if (k != Types.KIND_VEC && k != Types.KIND_PTR && k != Types.KIND_ARRAY && k != Types.KIND_STRUCT) {
                throw new UnsupportedOperationException("type kind " + k + " is not supported by the optimizer");
            }
        }
        DebugInfo dbg = m.debug();
        Tree[] memo = new Tree[m.nodeCount()];
        FnTree[] fns = new FnTree[m.functionCount()];
        for (int f = 0; f < fns.length; f++) {
            if (m.fBodyKind(f) != IrModule.BODY_IR || m.fBody(f) < 0) {
                throw new UnsupportedOperationException("function f" + f + " has a native body");
            }
            FnTree fn = new FnTree();
            fn.kind = m.fKind(f);
            fn.ret = m.fRet(f);
            fn.params = new int[m.fParamCount(f)];
            for (int i = 0; i < fn.params.length; i++) {
                fn.params[i] = m.fParamType(f, i);
            }
            for (int l = 0; l < m.fLocalCount(f); l++) {
                fn.newLocal(m.fLocalType(f, l), dbg.localName(f, l));
            }
            fn.wgx = m.fReqWorkGroup(f, 0);
            fn.wgy = m.fReqWorkGroup(f, 1);
            fn.wgz = m.fReqWorkGroup(f, 2);
            fn.inline = m.fInline(f);
            fn.opt = m.fOpt(f);
            fn.required = new String[m.fRequiredCount(f)];
            for (int i = 0; i < fn.required.length; i++) {
                fn.required[i] = m.string(m.fRequiredName(f, i));
            }
            fn.fallback = m.fFallback(f);
            fn.name = dbg.functionName(f);
            String[] pn = new String[fn.params.length];
            boolean anyName = false;
            for (int i = 0; i < pn.length; i++) {
                pn[i] = dbg.paramName(f, i);
                anyName |= pn[i] != null;
            }
            fn.paramNames = anyName ? pn : null;
            fn.body = liftNode(m, m.fBody(f), memo);
            fns[f] = fn;
        }
        return new ModuleTree(m, fns);
    }

    private static Tree[] kidsFromList(IrModule m, int off, int len, Tree[] memo) {
        Tree[] k = new Tree[len];
        for (int i = 0; i < len; i++) {
            k[i] = liftNode(m, m.list(off + i), memo);
        }
        return k;
    }

    private static Tree liftNode(IrModule m, int n, Tree[] memo) {
        Tree cached = memo[n];
        if (cached != null) {
            return cached;
        }
        int op = m.op(n);
        int type = m.type(n);
        int a = m.a(n);
        int b = m.b(n);
        int c = m.c(n);
        int line = m.debug().line(n);
        int col = m.debug().col(n);
        Tree t;
        switch (op) {
            case Op.CONST_INT, Op.CONST_FLOAT -> {
                boolean wide = op == Op.CONST_INT ? Types.bits(type) == 64 : type == Types.F64;
                t = new Tree(op, type, 0, wide ? m.constant(a) : a, Tree.NONE, Tree.NO_INTS, line, col);
            }
            case Op.CONST_BOOL -> t = new Tree(op, type, 0, a, Tree.NONE, Tree.NO_INTS, line, col);
            case Op.SPEC_CONST, Op.PARAM, Op.LOCAL_GET, Op.LOCAL_ADDR, Op.BARRIER ->
                    t = new Tree(op, type, a, 0, Tree.NONE, Tree.NO_INTS, line, col);
            case Op.NEG, Op.NOT, Op.CONVERT, Op.BITCAST, Op.LOAD, Op.ARRAY_LEN, Op.SPLAT, Op.EXPR_STMT ->
                    t = new Tree(op, type, 0, 0, new Tree[]{liftNode(m, a, memo)}, Tree.NO_INTS, line, col);
            case Op.EXTRACT, Op.FIELD_GET, Op.FIELD_PTR ->
                    t = new Tree(op, type, b, 0, new Tree[]{liftNode(m, a, memo)}, Tree.NO_INTS, line, col);
            case Op.SHUFFLE -> {
                int[] lanes = new int[c];
                for (int i = 0; i < c; i++) {
                    lanes[i] = m.list(b + i);
                }
                t = new Tree(op, type, 0, 0, new Tree[]{liftNode(m, a, memo)}, lanes, line, col);
            }
            case Op.ADD, Op.SUB, Op.MUL, Op.DIV, Op.REM, Op.AND, Op.OR, Op.XOR, Op.SHL, Op.SHR, Op.USHR,
                 Op.EQ, Op.NE, Op.LT, Op.LE, Op.GT, Op.GE, Op.ELEM_PTR, Op.STORE ->
                    t = new Tree(op, type, 0, 0, new Tree[]{liftNode(m, a, memo), liftNode(m, b, memo)},
                            Tree.NO_INTS, line, col);
            case Op.INSERT, Op.FIELD_SET ->
                    t = new Tree(op, type, b, 0, new Tree[]{liftNode(m, a, memo), liftNode(m, c, memo)},
                            Tree.NO_INTS, line, col);
            case Op.SELECT ->
                    t = new Tree(op, type, 0, 0,
                            new Tree[]{liftNode(m, a, memo), liftNode(m, b, memo), liftNode(m, c, memo)},
                            Tree.NO_INTS, line, col);
            case Op.LOCAL_DECL, Op.LOCAL_SET, Op.PRIVATE_ARRAY_DECL, Op.LOCAL_ARRAY_DECL ->
                    t = new Tree(op, type, a, 0, new Tree[]{liftNode(m, b, memo)}, Tree.NO_INTS, line, col);
            case Op.BUILD, Op.STRUCT_BUILD, Op.BLOCK ->
                    t = new Tree(op, type, 0, 0, kidsFromList(m, a, b, memo), Tree.NO_INTS, line, col);
            case Op.CALL, Op.INTRINSIC ->
                    t = new Tree(op, type, a, 0, kidsFromList(m, b, c, memo), Tree.NO_INTS, line, col);
            case Op.IF, Op.LOOP -> {
                Tree[] kids = c >= 0
                        ? new Tree[]{liftNode(m, a, memo), liftNode(m, b, memo), liftNode(m, c, memo)}
                        : new Tree[]{liftNode(m, a, memo), liftNode(m, b, memo)};
                t = new Tree(op, type, 0, 0, kids, Tree.NO_INTS, line, col);
            }
            case Op.DO_WHILE ->
                    t = new Tree(op, type, 0, 0, new Tree[]{liftNode(m, a, memo), liftNode(m, b, memo)},
                            Tree.NO_INTS, line, col);
            case Op.SWITCH -> {
                Tree[] kids = new Tree[1 + c];
                int[] meta = new int[2 * c];
                kids[0] = liftNode(m, a, memo);
                for (int i = 0; i < c; i++) {
                    meta[2 * i] = m.list(b + 3 * i);
                    meta[2 * i + 1] = m.list(b + 3 * i + 1);
                    kids[1 + i] = liftNode(m, m.list(b + 3 * i + 2), memo);
                }
                t = new Tree(op, type, 0, 0, kids, meta, line, col);
            }
            case Op.BREAK, Op.CONTINUE -> t = new Tree(op, type, 0, 0, Tree.NONE, Tree.NO_INTS, line, col);
            case Op.RETURN ->
                    t = new Tree(op, type, 0, 0, a >= 0 ? new Tree[]{liftNode(m, a, memo)} : Tree.NONE,
                            Tree.NO_INTS, line, col);
            default -> throw new UnsupportedOperationException("opcode " + Op.nameOf(op));
        }
        memo[n] = t;
        return t;
    }

    // =============================================================================================
    // сборка
    // =============================================================================================

    /**
     * Собирает модуль из деревьев.
     *
     * @param mt поднятый (и, возможно, изменённый) модуль
     * @return новый неизменяемый модуль
     */
    static IrModule lower(ModuleTree mt) {
        IrModule src = mt.source;
        IrBuilder b = new IrBuilder();
        copyTypes(src, mt.extraTypes, b);

        int[] order = emissionOrder(mt);
        int[] newIndex = new int[mt.fns.length];
        Arrays.fill(newIndex, -1);
        for (int i = 0; i < order.length; i++) {
            newIndex[order[i]] = i;
        }
        for (int oldIdx : order) {
            lowerFunction(mt.fns[oldIdx], b, newIndex);
        }
        return b.build();
    }

    private static void copyTypes(IrModule src, List<int[]> extras, IrBuilder b) {
        DebugInfo dbg = src.debug();
        for (int t = Types.FIRST_DYNAMIC; t < src.typeCount(); t++) {
            int id;
            switch (src.typeKind(t)) {
                case Types.KIND_VEC -> id = b.vec(src.typeA(t), src.typeB(t));
                case Types.KIND_PTR -> id = b.ptr(src.typeA(t), src.typeB(t));
                case Types.KIND_ARRAY -> id = b.array(src.typeA(t), src.typeB(t));
                default -> {
                    int[] fields = new int[src.structFieldCount(t)];
                    for (int i = 0; i < fields.length; i++) {
                        fields[i] = src.structFieldType(t, i);
                    }
                    id = b.struct(fields, src.typeD(t));
                    String name = dbg.structName(t);
                    if (name != null) {
                        String[] fn = new String[fields.length];
                        for (int i = 0; i < fn.length; i++) {
                            String s = dbg.fieldName(t, i);
                            fn[i] = s == null ? "f" + i : s;
                        }
                        b.nameStruct(id, name, fn);
                    }
                }
            }
            if (id != t) {
                throw new IllegalStateException("type table was renumbered: t" + t + " -> t" + id);
            }
        }
        for (int i = 0; i < extras.size(); i++) {
            int[] e = extras.get(i);
            int id = switch (e[0]) {
                case Types.KIND_PTR -> b.ptr(e[1], e[2]);
                case Types.KIND_ARRAY -> b.array(e[1], e[2]);
                default -> b.vec(e[1], e[2]);
            };
            if (id != src.typeCount() + i) {
                throw new IllegalStateException("extra type was renumbered: " + (src.typeCount() + i) + " -> " + id);
            }
        }
    }

    /**
     * Порядок эмиссии: исходный порядок живых функций, но вызываемая функция всегда раньше вызывающей.
     */
    static int[] emissionOrder(ModuleTree mt) {
        int n = mt.fns.length;
        boolean[] done = new boolean[n];
        List<Integer> out = new ArrayList<>();
        for (int f = 0; f < n; f++) {
            visit(mt, f, done, out);
        }
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = out.get(i);
        }
        return r;
    }

    private static void visit(ModuleTree mt, int f, boolean[] done, List<Integer> out) {
        if (done[f] || !mt.fns[f].alive) {
            return;
        }
        done[f] = true;
        collectCallees(mt.fns[f].body, callee -> visit(mt, callee, done, out));
        out.add(f);
    }

    /** Обходит дерево и сообщает индексы всех вызываемых функций. */
    static void collectCallees(Tree t, java.util.function.IntConsumer sink) {
        if (t.op == Op.CALL) {
            sink.accept(t.imm);
        }
        for (Tree c : t.kids) {
            collectCallees(c, sink);
        }
    }

    private static void lowerFunction(FnTree fn, IrBuilder b, int[] newIndex) {
        b.beginFunction(fn.kind, fn.ret, fn.params);
        if (fn.wgx != 0 || fn.wgy != 0 || fn.wgz != 0) {
            b.requireWorkGroup(fn.wgx, fn.wgy, fn.wgz);
        }
        b.inlinePolicy(fn.inline);
        b.optLevel(fn.opt);
        if (fn.required.length > 0) {
            b.required(fn.required);
        }
        if (fn.fallback >= 0) {
            b.fallback(newIndex[fn.fallback]);
        }
        if (fn.name != null) {
            b.nameFunction(fn.name);
        }
        if (fn.paramNames != null) {
            String[] pn = new String[fn.paramNames.length];
            for (int i = 0; i < pn.length; i++) {
                pn[i] = fn.paramNames[i] == null ? "p" + i : fn.paramNames[i];
            }
            b.nameParams(pn);
        }

        // только используемые локали, подряд
        boolean[] used = new boolean[fn.localCount];
        markLocals(fn.body, used);
        int[] map = new int[fn.localCount];
        Arrays.fill(map, -1);
        for (int l = 0; l < fn.localCount; l++) {
            if (used[l]) {
                map[l] = b.declareLocal(fn.locals[l]);
                if (fn.localNames[l] != null) {
                    b.nameLocal(map[l], fn.localNames[l]);
                }
            }
        }
        int root = emit(fn.body, b, map, newIndex);
        b.endFunction(root);
    }

    private static void markLocals(Tree t, boolean[] used) {
        switch (t.op) {
            case Op.LOCAL_GET, Op.LOCAL_ADDR, Op.LOCAL_DECL, Op.LOCAL_SET, Op.PRIVATE_ARRAY_DECL,
                 Op.LOCAL_ARRAY_DECL -> used[t.imm] = true;
            default -> {
            }
        }
        for (Tree c : t.kids) {
            markLocals(c, used);
        }
    }

    private static int emit(Tree t, IrBuilder b, int[] locals, int[] funcs) {
        int[] k = new int[t.kids.length];
        for (int i = 0; i < k.length; i++) {
            k[i] = emit(t.kids[i], b, locals, funcs);
        }
        int id;
        switch (t.op) {
            case Op.CONST_INT -> id = b.constInt(t.type, t.k);
            case Op.CONST_FLOAT -> id = t.type == Types.F64
                    ? b.constF64(Double.longBitsToDouble(t.k))
                    : b.node(Op.CONST_FLOAT, Types.F32, (int) t.k, 0, 0);
            case Op.CONST_BOOL -> id = b.constBool(t.k != 0);
            case Op.SPEC_CONST -> id = b.specConst(t.imm, t.type);
            case Op.PARAM -> id = b.param(t.imm);
            case Op.ADD, Op.SUB, Op.MUL, Op.DIV, Op.REM, Op.AND, Op.OR, Op.XOR, Op.SHL, Op.SHR, Op.USHR,
                 Op.EQ, Op.NE, Op.LT, Op.LE, Op.GT, Op.GE -> id = b.binary(t.op, k[0], k[1]);
            case Op.NEG -> id = b.neg(k[0]);
            case Op.NOT -> id = b.not(k[0]);
            case Op.CONVERT -> id = b.convert(t.type, k[0]);
            case Op.BITCAST -> id = b.bitcast(t.type, k[0]);
            case Op.SELECT -> id = b.select(k[0], k[1], k[2]);
            case Op.LOCAL_DECL -> id = b.localDecl(locals[t.imm], k[0]);
            case Op.LOCAL_GET -> id = b.localGet(locals[t.imm]);
            case Op.LOCAL_SET -> id = b.localSet(locals[t.imm], k[0]);
            case Op.LOCAL_ADDR -> id = b.localAddr(locals[t.imm]);
            case Op.PRIVATE_ARRAY_DECL -> id = b.privateArrayDecl(locals[t.imm], k[0]);
            case Op.LOCAL_ARRAY_DECL -> id = b.localArrayDecl(locals[t.imm], k[0]);
            case Op.ELEM_PTR -> id = b.elemPtr(k[0], k[1]);
            case Op.FIELD_PTR -> id = b.fieldPtr(k[0], t.imm);
            case Op.LOAD -> id = b.load(k[0]);
            case Op.STORE -> id = b.store(k[0], k[1]);
            case Op.ARRAY_LEN -> id = b.arrayLen(k[0]);
            case Op.SPLAT -> id = b.splat(t.type, k[0]);
            case Op.EXTRACT -> id = b.extract(k[0], t.imm);
            case Op.INSERT -> id = b.insert(k[0], t.imm, k[1]);
            case Op.SHUFFLE -> id = b.shuffle(k[0], t.ints);
            case Op.BUILD -> id = b.build(t.type, k);
            case Op.STRUCT_BUILD -> id = b.structBuild(t.type, k);
            case Op.FIELD_GET -> id = b.fieldGet(k[0], t.imm);
            case Op.FIELD_SET -> id = b.fieldSet(k[0], t.imm, k[1]);
            case Op.CALL -> id = b.call(funcs[t.imm], k);
            case Op.INTRINSIC -> id = b.intrinsic(t.imm, k);
            case Op.EXPR_STMT -> id = b.exprStmt(k[0]);
            case Op.BARRIER -> id = b.barrier(t.imm);
            case Op.BLOCK -> id = b.block(k);
            case Op.IF -> id = b.ifStmt(k[0], k[1], k.length > 2 ? k[2] : -1);
            case Op.LOOP -> id = b.loop(k[0], k[1], k.length > 2 ? k[2] : -1);
            case Op.DO_WHILE -> id = b.doWhile(k[0], k[1]);
            case Op.SWITCH -> {
                int n = k.length - 1;
                boolean[] isDefault = new boolean[n];
                int[] values = new int[n];
                int[] bodies = new int[n];
                for (int i = 0; i < n; i++) {
                    isDefault[i] = t.ints[2 * i] != 0;
                    values[i] = t.ints[2 * i + 1];
                    bodies[i] = k[1 + i];
                }
                id = b.switchStmt(k[0], isDefault, values, bodies);
            }
            case Op.BREAK -> id = b.breakStmt();
            case Op.CONTINUE -> id = b.continueStmt();
            case Op.RETURN -> id = b.returnStmt(k.length > 0 ? k[0] : -1);
            default -> throw new IllegalStateException("cannot emit opcode " + Op.nameOf(t.op));
        }
        if (b.typeOf(id) != t.type) {
            throw new IllegalStateException("optimizer produced a node " + Op.nameOf(t.op) + " of type t"
                    + b.typeOf(id) + " where the tree says t" + t.type);
        }
        if (t.line > 0) {
            b.span(id, t.line, t.col);
        }
        return id;
    }
}
