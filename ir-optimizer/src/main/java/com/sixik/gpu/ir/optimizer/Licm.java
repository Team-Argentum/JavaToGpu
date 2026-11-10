package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Вынос инвариантов из циклов (loop-invariant code motion) с контролем давления на регистры.
 *
 * <p>Инвариантное выражение вычисляется один раз перед циклом в новую локаль. Выражение инвариантно, если оно чистое,
 * не читает локали, которым присваивают внутри цикла (множество {@code A}), и - для чтения памяти - в цикле нет записей
 * в память, барьеров и вызовов с эффектами. Спекулятивность:</p>
 * <ul>
 *   <li>Условие цикла {@code LOOP} вычисляется хотя бы раз - из него можно выносить всё, включая чтения памяти и деление.</li>
 *   <li>Всё остальное (тело, {@code update}, {@code do-while}) может не выполниться - оттуда выносятся только вычисления,
 *       которые не читают память и не могут «упасть» (иначе вынос добавил бы выход за границы массива или деление на ноль).</li>
 * </ul>
 *
 * <p>Вынос <b>увеличивает</b> давление на регистры (значение живёт весь цикл), поэтому каждая новая локаль проходит через
 * {@link Costs.Budget}. Внешние циклы обрабатываются раньше внутренних: инвариант внешнего цикла уходит дальше всех.</p>
 */
final class Licm {

    private final FnTree fn;
    private final IrModule src;
    private final Effects fx;
    private final boolean[] tracked;
    private final boolean[] array;
    private final Costs.Budget budget;
    private int counter;

    private Licm(FnTree fn, IrModule src, Effects fx, Costs.Budget budget) {
        this.fn = fn;
        this.src = src;
        this.fx = fx;
        this.tracked = Locals.tracked(fn, src);
        this.array = new boolean[fn.localCount];
        for (int l = 0; l < array.length; l++) {
            array[l] = Types.kind(src, fn.locals[l]) == Types.KIND_ARRAY;
        }
        this.budget = budget;
    }

    /**
     * Выносит инварианты из всех циклов функции.
     *
     * @param fn     функция
     * @param src    модуль
     * @param fx     анализ эффектов
     * @param budget разделяемый запас регистров
     * @return новое тело
     */
    static Tree run(FnTree fn, IrModule src, Effects fx, Costs.Budget budget) {
        return new Licm(fn, src, fx, budget).stmt(fn.body);
    }

    private Tree stmt(Tree s) {
        if (s.op == Op.LOOP || s.op == Op.DO_WHILE) {
            return loop(s);
        }
        return DeadCode.mapStmtKids(s, this::stmt);
    }

    /** Контекст одного цикла. */
    private final class Ctx {
        final BitSet assigned = new BitSet();
        boolean memWrites;
        final Map<String, Integer> hoisted = new HashMap<>();
        final List<Tree> decls = new ArrayList<>();
    }

    private static boolean writesMemory(Tree t, Effects fx) {
        if (t.op == Op.STORE || t.op == Op.BARRIER) {
            return true;
        }
        if (t.op == Op.INTRINSIC && Builtins.has(t.imm, Builtins.EFFECT)) {
            return true;
        }
        if (t.op == Op.CALL && fx.functionHasEffect(t.imm)) {
            return true;
        }
        for (Tree c : t.kids) {
            if (writesMemory(c, fx)) {
                return true;
            }
        }
        return false;
    }

    private Tree loop(Tree s) {
        Ctx ctx = new Ctx();
        Locals.assigned(s, ctx.assigned);
        ctx.memWrites = writesMemory(s, fx);

        Tree rewritten;
        if (s.op == Op.LOOP) {
            Tree[] nk = s.kids.clone();
            nk[0] = hoist(s.kids[0], ctx, true);
            nk[1] = rewriteStmt(s.kids[1], ctx);
            if (nk.length == 3) {
                nk[2] = rewriteStmt(s.kids[2], ctx);
            }
            rewritten = s.withKids(nk);
        } else {
            rewritten = s.withKids(new Tree[]{rewriteStmt(s.kids[0], ctx), hoist(s.kids[1], ctx, false)});
        }
        // внутренние циклы - после внешнего
        Tree inner = DeadCode.mapStmtKids(rewritten, this::stmt);
        if (ctx.decls.isEmpty()) {
            return inner;
        }
        List<Tree> out = new ArrayList<>(ctx.decls);
        out.add(inner);
        return Tree.of(Op.BLOCK, Types.VOID, out.toArray(Tree.NONE));
    }

    /** Переписывает выражения внутри стейтмента тела цикла: всё здесь выполняется условно. */
    private Tree rewriteStmt(Tree s, Ctx ctx) {
        switch (s.op) {
            case Op.LOCAL_DECL, Op.LOCAL_SET, Op.EXPR_STMT, Op.RETURN, Op.STORE -> {
                Tree[] nk = null;
                for (int i = 0; i < s.kids.length; i++) {
                    Tree r = hoist(s.kids[i], ctx, false);
                    if (r != s.kids[i]) {
                        if (nk == null) {
                            nk = s.kids.clone();
                        }
                        nk[i] = r;
                    }
                }
                return nk == null ? s : s.withKids(nk);
            }
            case Op.IF, Op.LOOP, Op.DO_WHILE, Op.SWITCH -> {
                Tree head = s;
                if (s.op == Op.IF || s.op == Op.LOOP || s.op == Op.SWITCH) {
                    head = s.withKid(0, hoist(s.kids[0], ctx, false));
                } else {
                    head = s.withKid(1, hoist(s.kids[1], ctx, false));
                }
                final Tree h = head;
                return DeadCode.mapStmtKids(h, k -> rewriteStmt(k, ctx));
            }
            default -> {
                return DeadCode.mapStmtKids(s, k -> rewriteStmt(k, ctx));
            }
        }
    }

    private boolean invariant(Tree e, Ctx ctx) {
        switch (e.op) {
            case Op.LOCAL_GET -> {
                boolean known = e.imm < tracked.length;
                if (known && array[e.imm]) {
                    return true;
                }
                if ((known && !tracked[e.imm]) || ctx.assigned.get(e.imm)) {
                    return false;
                }
            }
            case Op.LOCAL_ADDR -> {
                return false;
            }
            case Op.LOAD -> {
                if (ctx.memWrites) {
                    return false;
                }
            }
            case Op.CALL, Op.INTRINSIC -> {
                if (fx.hasEffect(e)) {
                    return false;
                }
                if (fx.readsMemory(e) && ctx.memWrites) {
                    return false;
                }
            }
            default -> {
            }
        }
        for (Tree k : e.kids) {
            if (!invariant(k, ctx)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Ищет в выражении максимальные инвариантные поддеревья, достойные выноса, и заменяет их чтением временной локали.
     *
     * @param e      выражение
     * @param ctx    контекст цикла
     * @param always выполняется ли выражение при каждом входе в цикл (условие {@code LOOP})
     * @return выражение с вынесенными частями
     */
    private Tree hoist(Tree e, Ctx ctx, boolean always) {
        if (e.kids.length == 0) {
            return e;
        }
        if (worthHoisting(e, always) && invariant(e, ctx)) {
            String key = structKey(e, new StringBuilder()).toString();
            Integer local = ctx.hoisted.get(key);
            if (local == null) {
                int words = RegisterPressure.words(src, e.type);
                if (budget.tryTake(words, Costs.cost(e))) {
                    local = fn.newLocal(e.type, "licm" + (counter++));
                    ctx.hoisted.put(key, local);
                    ctx.decls.add(Tree.ofImm(Op.LOCAL_DECL, Types.VOID, local, e));
                }
            }
            if (local != null) {
                return Tree.ofImm(Op.LOCAL_GET, e.type, local);
            }
        }
        Tree[] nk = null;
        for (int i = 0; i < e.kids.length; i++) {
            // ветки select выполняются условно
            Tree r = hoist(e.kids[i], ctx, always && !(e.op == Op.SELECT && i >= 1));
            if (r != e.kids[i]) {
                if (nk == null) {
                    nk = e.kids.clone();
                }
                nk[i] = r;
            }
        }
        return nk == null ? e : e.withKids(nk);
    }

    /** Точный структурный ключ (опкод, тип, поля и операнды) для поиска одинаковых инвариантов. */
    private static StringBuilder structKey(Tree e, StringBuilder sb) {
        sb.append(e.op).append(':').append(e.type).append('#').append(e.imm).append('=').append(e.k);
        if (e.ints.length > 0) {
            sb.append(java.util.Arrays.toString(e.ints));
        }
        if (e.kids.length > 0) {
            sb.append('(');
            for (Tree k : e.kids) {
                structKey(k, sb).append(',');
            }
            sb.append(')');
        }
        return sb;
    }

    private boolean worthHoisting(Tree e, boolean always) {
        if (e.isStmt() || !Costs.storable(src, e.type) || Costs.cost(e) < Costs.MIN_COST) {
            return false;
        }
        if (fx.hasEffect(e) || Costs.contains(e, Op.LOCAL_ADDR)) {
            return false;
        }
        return always || (!fx.readsMemory(e) && !Effects.mayTrap(e));
    }
}
