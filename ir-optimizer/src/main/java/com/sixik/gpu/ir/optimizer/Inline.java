package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

import java.util.ArrayList;
import java.util.List;

/**
 * Встраивание вызовов функций с политикой {@link IrModule#INLINE_ALWAYS} ({@code @DeviceFunction(inline = ALWAYS)},
 * FRONTEND_ASM_RUNTIME.md, S4-3).
 *
 * <h2>Как встраивается вызов</h2>
 * <p>Вызов - выражение, а тело функции - стейтменты, поэтому тело выносится <b>перед стейтментом</b>, в котором стоит
 * вызов, а сам вызов заменяется значением:</p>
 * <ul>
 *   <li>аргументы: константа, параметр, чтение локали без взятого адреса и указатель/массив без эффектов и чтений памяти
 *       подставляются как есть; остальные вычисляются во временную локаль (порядок слева направо сохраняется);</li>
 *   <li>локали функции получают новые номера в вызывающей;</li>
 *   <li>{@code return} только последним стейтментом тела - тело выносится как есть, значением вызова становится
 *       выражение {@code return};</li>
 *   <li>иначе тело оборачивается в {@code do { ... } while (false)}: {@code return e} - {@code ret = e; break;}.
 *       Так нельзя, если {@code return} стоит внутри цикла или {@code switch} функции ({@code break} ушёл бы не туда), и
 *       если результат не скаляр и не вектор (для временной {@code ret} нужно начальное значение).</li>
 * </ul>
 *
 * <h2>Когда вызов не встраивается</h2>
 * <p>Перенос тела вперёд не должен менять смысл, поэтому всё, что в стейтменте вычисляется <b>до</b> вызова, обязано
 * не зависеть от памяти и эффектов ({@link #stable}); вызов в ветке {@code select}, в условии или шаге цикла не
 * встраивается (он выполняется не всегда либо много раз). Не встраиваются также рекурсия, функция с другим действующим
 * режимом {@code fp} ({@link FpModes}) - её операции значат другое - и функция с {@code __local}-массивом. Такие вызовы
 * остаются обычными вызовами: политика - пожелание, а не условие корректности.</p>
 *
 * <p>Функции обрабатываются от вызываемых к вызывающим, поэтому вложенные {@code ALWAYS}-вызовы встраиваются за один
 * проход. Ставшая недостижимой функция удаляется следующим проходом {@code dead-functions}.</p>
 */
final class Inline {

    private final Codec.ModuleTree mt;
    private final Effects fx;
    private final int[] fp;
    /** Достижимость по вызовам: {@code reach[g][f]} - из {@code g} достижима {@code f}. */
    private final boolean[][] reach;
    /** Текущая вызывающая функция. */
    private FnTree fn;
    /** Локали текущей функции с взятым адресом: их значение может поменять и вызываемая функция. */
    private boolean[] addressed;
    private boolean changed;

    private Inline(Codec.ModuleTree mt) {
        this.mt = mt;
        this.fx = Effects.analyze(mt);
        this.fp = FpModes.effective(mt);
        int n = mt.fns.length;
        boolean[][] calls = new boolean[n][n];
        for (int f = 0; f < n; f++) {
            if (mt.fns[f].alive) {
                collectCalls(mt.fns[f].body, calls[f]);
            }
        }
        // транзитивное замыкание (функций в модуле немного)
        reach = new boolean[n][];
        for (int f = 0; f < n; f++) {
            reach[f] = calls[f].clone();
        }
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                if (reach[i][k]) {
                    for (int j = 0; j < n; j++) {
                        reach[i][j] |= reach[k][j];
                    }
                }
            }
        }
    }

    /**
     * @param mt поднятый модуль (функции меняются на месте)
     * @return {@code true}, если встроен хотя бы один вызов
     */
    static boolean run(Codec.ModuleTree mt) {
        boolean any = false;
        for (FnTree f : mt.fns) {
            any |= f.alive && f.inline == IrModule.INLINE_ALWAYS;
        }
        if (!any) {
            return false;
        }
        Inline in = new Inline(mt);
        for (int f : in.order()) {
            in.fn = mt.fns[f];
            in.addressed = addressedLocals(in.fn);
            Deadline.check();
            Tree nb = in.block(in.fn.body, f);
            if (nb != in.fn.body) {
                in.fn.body = nb;
            }
        }
        return in.changed;
    }

    private static void collectCalls(Tree t, boolean[] out) {
        if (t.op == Op.CALL && t.imm >= 0 && t.imm < out.length) {
            out[t.imm] = true;
        }
        for (Tree c : t.kids) {
            collectCalls(c, out);
        }
    }

    /** @return живые функции от вызываемых к вызывающим (обратный порядок обхода в глубину) */
    private int[] order() {
        int n = mt.fns.length;
        int[] state = new int[n];
        List<Integer> out = new ArrayList<>();
        for (int f = 0; f < n; f++) {
            visit(f, state, out);
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private void visit(int f, int[] state, List<Integer> out) {
        if (state[f] != 0 || !mt.fns[f].alive) {
            return;
        }
        state[f] = 1;
        boolean[] calls = new boolean[mt.fns.length];
        collectCalls(mt.fns[f].body, calls);
        for (int g = 0; g < calls.length; g++) {
            if (calls[g]) {
                visit(g, state, out);
            }
        }
        state[f] = 2;
        out.add(f);
    }

    private static boolean[] addressedLocals(FnTree fn) {
        boolean[] a = new boolean[fn.localCount];
        markAddressed(fn.body, a);
        return a;
    }

    private static void markAddressed(Tree t, boolean[] a) {
        if (t.op == Op.LOCAL_ADDR && t.imm < a.length) {
            a[t.imm] = true;
        }
        for (Tree c : t.kids) {
            markAddressed(c, a);
        }
    }

    // =============================================================================================
    // обход стейтментов
    // =============================================================================================

    /** Блок (или одиночный стейтмент) с встроенными вызовами; результат - блок. */
    private Tree block(Tree s, int f) {
        Tree[] stmts = s.op == Op.BLOCK ? s.kids : new Tree[]{s};
        List<Tree> out = new ArrayList<>(stmts.length);
        boolean same = s.op == Op.BLOCK;
        for (Tree st : stmts) {
            Tree t = nested(st, f);
            while (true) {
                Expansion e = expand(t, f);
                if (e == null) {
                    break;
                }
                changed = true;
                out.addAll(e.prefix);
                t = e.rest;
                if (t == null) {
                    break;
                }
            }
            if (t != st) {
                same = false;
            }
            if (t != null) {
                out.add(t);
            } else {
                same = false;
            }
        }
        if (same && out.size() == stmts.length) {
            return s;
        }
        return s.op == Op.BLOCK ? s.withKids(out.toArray(Tree.NONE)) : Tree.of(Op.BLOCK, Types.VOID, out.toArray(Tree.NONE));
    }

    /** Встраивание в стейтменты, вложенные в {@code st} (ветки, тела циклов, случаи {@code switch}). */
    private Tree nested(Tree st, int f) {
        switch (st.op) {
            case Op.BLOCK -> {
                return block(st, f);
            }
            case Op.IF -> {
                Tree[] k = st.kids.clone();
                for (int i = 1; i < k.length; i++) {
                    k[i] = block(k[i], f);
                }
                return st.withKids(k);
            }
            case Op.LOOP -> {
                return st.withKid(1, block(st.kids[1], f));
            }
            case Op.DO_WHILE -> {
                return st.withKid(0, block(st.kids[0], f));
            }
            case Op.SWITCH -> {
                Tree[] k = st.kids.clone();
                for (int i = 1; i < k.length; i++) {
                    k[i] = block(k[i], f);
                }
                return st.withKids(k);
            }
            default -> {
                return st;
            }
        }
    }

    /** Вынесенное тело и стейтмент, в котором вызов заменён значением ({@code null} - стейтмент исчез). */
    private record Expansion(List<Tree> prefix, Tree rest) {
    }

    /** Операнды стейтмента, которые вычисляются один раз перед ним самим, по порядку. */
    private static int expressionKids(Tree st) {
        return switch (st.op) {
            case Op.EXPR_STMT, Op.LOCAL_DECL, Op.LOCAL_SET, Op.RETURN, Op.IF, Op.SWITCH -> Math.min(1, st.kids.length);
            case Op.STORE -> 2;
            default -> 0;
        };
    }

    /** Путь от стейтмента до вызова: номера операндов по уровням. */
    private Expansion expand(Tree st, int f) {
        int n = expressionKids(st);
        for (int i = 0; i < n; i++) {
            int[] path = find(st.kids[i], f);
            if (path != null) {
                int[] full = new int[path.length + 1];
                full[0] = i;
                System.arraycopy(path, 0, full, 1, path.length);
                return expandAt(st, full);
            }
            if (!stable(st.kids[i])) {
                return null; // всё дальше вычисляется после того, что зависит от памяти или эффектов
            }
        }
        return null;
    }

    /**
     * Ищет первый в порядке вычисления встраиваемый вызов в выражении.
     *
     * @return путь по операндам от {@code t} до вызова ({@code []} - сам {@code t}) либо {@code null}
     */
    private int[] find(Tree t, int f) {
        if (t.op == Op.SELECT) {
            // ветки вычисляются не всегда: встраивается только условие
            int[] p = find(t.kids[0], f);
            return p == null ? null : prepend(0, p);
        }
        boolean self = t.op == Op.CALL && inlinable(f, t.imm);
        for (int i = 0; i < t.kids.length; i++) {
            int[] p = find(t.kids[i], f);
            if (p != null) {
                return prepend(i, p);
            }
            if (!stable(t.kids[i])) {
                // дальше вычисляется после зависящего от памяти; аргументы самого вызова уходят во временные локали
                // по порядку, поэтому он сам встраивается
                return self ? new int[0] : null;
            }
        }
        return self ? new int[0] : null;
    }

    private static int[] prepend(int i, int[] p) {
        int[] r = new int[p.length + 1];
        r[0] = i;
        System.arraycopy(p, 0, r, 1, p.length);
        return r;
    }

    /**
     * Значение не зависит от того, выполнится ли тело вызываемой функции раньше него: нет эффектов, нет чтения памяти,
     * нет чтения локали с взятым адресом.
     */
    private boolean stable(Tree t) {
        return !fx.hasEffect(t) && !fx.readsMemory(t) && !readsAddressed(t);
    }

    private boolean readsAddressed(Tree t) {
        if ((t.op == Op.LOCAL_GET || t.op == Op.LOCAL_ADDR) && t.imm < addressed.length && addressed[t.imm]) {
            return true;
        }
        for (Tree c : t.kids) {
            if (readsAddressed(c)) {
                return true;
            }
        }
        return false;
    }

    private boolean inlinable(int f, int g) {
        if (g < 0 || g >= mt.fns.length || g == f) {
            return false;
        }
        FnTree callee = mt.fns[g];
        if (!callee.alive || callee.inline != IrModule.INLINE_ALWAYS || callee.kind == IrModule.KERNEL
                || callee.body == null || reach[g][f] || reach[g][g] || fp[g] != fp[f]) {
            return false;
        }
        if (contains(callee.body, Op.LOCAL_ARRAY_DECL)) {
            return false;
        }
        Form form = form(callee);
        return form != Form.NONE;
    }

    private enum Form {
        /** {@code return} только последним стейтментом тела (или его нет вовсе). */
        TAIL,
        /** Ранние {@code return} вне циклов и {@code switch}: обёртка {@code do-while(false)}. */
        WRAPPED,
        /** Не встраивается. */
        NONE
    }

    private Form form(FnTree g) {
        Tree[] k = g.body.kids;
        int returns = count(g.body, Op.RETURN);
        if (returns == 0 || returns == 1 && k.length > 0 && k[k.length - 1].op == Op.RETURN) {
            return Form.TAIL;
        }
        if (g.ret != Types.VOID && !Types.isScalar(g.ret) && Types.kind(mt.source, g.ret) != Types.KIND_VEC) {
            return Form.NONE;
        }
        return returnInLoop(g.body, false) ? Form.NONE : Form.WRAPPED;
    }

    private static boolean returnInLoop(Tree t, boolean inLoop) {
        if (t.op == Op.RETURN && inLoop) {
            return true;
        }
        boolean loop = inLoop || t.op == Op.LOOP || t.op == Op.DO_WHILE || t.op == Op.SWITCH;
        for (Tree c : t.kids) {
            if (returnInLoop(c, loop)) {
                return true;
            }
        }
        return false;
    }

    private static int count(Tree t, int op) {
        int c = t.op == op ? 1 : 0;
        for (Tree k : t.kids) {
            c += count(k, op);
        }
        return c;
    }

    private static boolean contains(Tree t, int op) {
        return count(t, op) > 0;
    }

    // =============================================================================================
    // само встраивание
    // =============================================================================================

    private Expansion expandAt(Tree st, int[] path) {
        Tree call = st;
        for (int i : path) {
            call = call.kids[i];
        }
        FnTree g = mt.fns[call.imm];
        List<Tree> prefix = new ArrayList<>();
        Tree[] binding = new Tree[g.params.length];
        for (int i = 0; i < binding.length; i++) {
            Tree a = call.kids[i];
            int kind = Types.kind(mt.source, g.params[i]);
            boolean reference = kind == Types.KIND_ARRAY || kind == Types.KIND_PTR;
            if (trivial(a) || reference && stable(a)) {
                binding[i] = a;
            } else if (reference) {
                return null; // указатель, зависящий от памяти: во временную локаль его не положить
            } else {
                String hint = g.paramNames != null && i < g.paramNames.length ? g.paramNames[i] : null;
                int tmp = fn.newLocal(g.params[i], hint);
                prefix.add(Tree.ofImm(Op.LOCAL_DECL, Types.VOID, tmp, a));
                binding[i] = Tree.ofImm(Op.LOCAL_GET, g.params[i], tmp);
            }
        }
        int[] locals = new int[g.localCount];
        for (int l = 0; l < g.localCount; l++) {
            locals[l] = fn.newLocal(g.locals[l], g.localNames[l]);
        }
        Rewrite rw = new Rewrite(binding, locals, false, -1);
        Tree value;
        Tree[] k = g.body.kids;
        if (form(g) == Form.TAIL) {
            int end = k.length > 0 && k[k.length - 1].op == Op.RETURN ? k.length - 1 : k.length;
            for (int i = 0; i < end; i++) {
                prefix.add(rw.apply(k[i]));
            }
            value = end < k.length && k[end].kids.length > 0 ? rw.apply(k[end].kids[0]) : null;
        } else {
            int ret = -1;
            if (g.ret != Types.VOID) {
                ret = fn.newLocal(g.ret, g.name == null ? null : g.name + "_ret");
                prefix.add(Tree.ofImm(Op.LOCAL_DECL, Types.VOID, ret, zero(g.ret)));
            }
            Tree body = new Rewrite(binding, locals, true, ret).apply(g.body);
            prefix.add(Tree.of(Op.DO_WHILE, Types.VOID, body, Tree.constant(Op.CONST_BOOL, Types.BOOL, 0)));
            value = ret < 0 ? null : Tree.ofImm(Op.LOCAL_GET, g.ret, ret);
        }

        if (path.length == 1 && st.op == Op.EXPR_STMT) {
            // результат не нужен
            if (value == null || fx.isRemovable(value)) {
                return new Expansion(prefix, null);
            }
            int tmp = fn.newLocal(value.type, null);
            return new Expansion(prefix, Tree.ofImm(Op.LOCAL_DECL, Types.VOID, tmp, value));
        }
        if (value == null) {
            return null; // void-вызов внутри выражения - невалидный IR, не трогаем
        }
        return new Expansion(prefix, replace(st, path, 0, value));
    }

    private static Tree replace(Tree t, int[] path, int depth, Tree value) {
        if (depth == path.length) {
            return value;
        }
        return t.withKid(path[depth], replace(t.kids[path[depth]], path, depth + 1, value));
    }

    /** Константа, параметр или чтение локали без взятого адреса: подставляется без временной локали. */
    private boolean trivial(Tree a) {
        return a.isConst() || a.op == Op.PARAM
                || a.op == Op.LOCAL_GET && (a.imm >= addressed.length || !addressed[a.imm]);
    }

    private Tree zero(int type) {
        if (Types.isScalar(type)) {
            return type == Types.BOOL ? Tree.constant(Op.CONST_BOOL, Types.BOOL, 0)
                    : Tree.constLike(Simplify.EMPTY, type, 0);
        }
        return Tree.of(Op.SPLAT, type, Tree.constLike(Simplify.EMPTY, Types.scalarOf(mt.source, type), 0));
    }

    /** Перенос тела вызываемой функции в вызывающую. */
    private static final class Rewrite {
        final Tree[] binding;
        final int[] locals;
        /** Форма {@link Form#WRAPPED}: {@code return} переписывается в {@code break}. */
        final boolean wrapped;
        /** Локаль результата ({@code -1} - функция {@code void}). */
        final int ret;

        Rewrite(Tree[] binding, int[] locals, boolean wrapped, int ret) {
            this.binding = binding;
            this.locals = locals;
            this.wrapped = wrapped;
            this.ret = ret;
        }

        Tree apply(Tree t) {
            switch (t.op) {
                case Op.PARAM -> {
                    return binding[t.imm];
                }
                case Op.RETURN -> {
                    if (!wrapped) {
                        break; // TAIL: последний return уже снят вызывающим
                    }
                    if (t.kids.length == 0 || ret < 0) {
                        return Tree.of(Op.BREAK, Types.VOID);
                    }
                    // ret = e; break
                    return Tree.of(Op.BLOCK, Types.VOID, Tree.ofImm(Op.LOCAL_SET, Types.VOID, ret, apply(t.kids[0])),
                            Tree.of(Op.BREAK, Types.VOID));
                }
                default -> {
                }
            }
            Tree[] k = t.kids;
            Tree[] nk = null;
            for (int i = 0; i < k.length; i++) {
                Tree c = apply(k[i]);
                if (c != k[i]) {
                    if (nk == null) {
                        nk = k.clone();
                    }
                    nk[i] = c;
                }
            }
            Tree r = nk == null ? t : t.withKids(nk);
            if (isLocalOp(t.op)) {
                r = r.withImm(locals[t.imm]);
            }
            return r;
        }

        static boolean isLocalOp(int op) {
            return op == Op.LOCAL_GET || op == Op.LOCAL_SET || op == Op.LOCAL_DECL || op == Op.LOCAL_ADDR
                    || op == Op.PRIVATE_ARRAY_DECL || op == Op.LOCAL_ARRAY_DECL;
        }
    }
}
