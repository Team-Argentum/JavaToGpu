package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Устранение общих подвыражений с временными локалями и контролем давления на регистры.
 *
 * <p>Закрытые чистые узлы IR можно разделять, но «открытые» (чтение локали и памяти) - нельзя (правило {@code IR-E02}),
 * поэтому общее подвыражение материализуется в новую локаль: {@code t = expr; ... t ...}. Проход нумерует значения
 * <b>с версиями</b>: ключ выражения включает версию каждой прочитанной локали (растёт при каждой записи) и версию
 * памяти (растёт при {@code store}, барьере и вызове с эффектами). Два выражения с равными ключами гарантированно равны
 * по значению, поэтому не нужно искать «разрыв» между ними вручную. Составной стейтмент ({@code if}, цикл, {@code switch},
 * блок) сбрасывает версии целиком; его собственные блоки обрабатываются отдельно с чистого листа.</p>
 *
 * <p><b>Что считается кандидатом.</b> Чистое выражение стоимостью не ниже {@link Costs#MIN_COST} типа, который можно
 * держать в локали. Загрузки и выражения, способные «упасть» (целое деление), не поднимаются из веток {@code select} -
 * исходная программа вычисляла их условно.</p>
 *
 * <p><b>Давление на регистры.</b> Каждая временная локаль удлиняет время жизни значения; {@link Costs.Budget} ограничивает
 * суммарный вес новых локалей запасом до целевого бюджета регистров. Дорогие вычисления (деление, sin/cos, чтение памяти)
 * на уровне 3 получают временную локаль и при нулевом запасе.</p>
 */
final class Cse {

    private static final int MAX_TEMPS_PER_BLOCK = 24;

    private final FnTree fn;
    private final IrModule src;
    private final Effects fx;
    private final boolean[] tracked;
    private final Costs.Budget budget;
    private int counter;

    private Cse(FnTree fn, IrModule src, Effects fx, Costs.Budget budget) {
        this.fn = fn;
        this.src = src;
        this.fx = fx;
        this.tracked = Locals.tracked(fn, src);
        this.budget = budget;
    }

    /**
     * Выполняет CSE для тела функции. Может завести новые локали ({@link FnTree#newLocal}).
     *
     * @param fn     функция
     * @param src    модуль
     * @param fx     анализ эффектов
     * @param budget разделяемый запас регистров
     * @return новое тело
     */
    static Tree run(FnTree fn, IrModule src, Effects fx, Costs.Budget budget) {
        Cse c = new Cse(fn, src, fx, budget);
        return c.stmt(fn.body);
    }

    private Tree stmt(Tree s) {
        if (s.op == Op.BLOCK) {
            Tree[] kids = new Tree[s.kids.length];
            for (int i = 0; i < kids.length; i++) {
                kids[i] = stmt(s.kids[i]);
            }
            return block(s.withKids(kids));
        }
        return DeadCode.mapStmtKids(s, this::stmt);
    }

    /** Временные локали, заведённые самим проходом, всегда отслеживаемые скаляры/векторы. */
    private boolean isTracked(int local) {
        return local >= tracked.length || tracked[local];
    }

    // ---- состояние версий ----------------------------------------------------------------------

    private final class State {
        final int[] ver = new int[fn.localCount];
        int memVer;

        void advance(Tree s) {
            switch (s.op) {
                case Op.LOCAL_DECL, Op.LOCAL_SET -> ver[s.imm]++;
                case Op.STORE, Op.BARRIER -> memVer++;
                case Op.EXPR_STMT -> {
                    if (fx.hasEffect(s.kids[0])) {
                        memVer++;
                    }
                }
                case Op.RETURN, Op.BREAK, Op.CONTINUE, Op.PRIVATE_ARRAY_DECL, Op.LOCAL_ARRAY_DECL -> {
                }
                default -> {
                    for (int i = 0; i < ver.length; i++) {
                        ver[i]++;
                    }
                    memVer++;
                }
            }
        }
    }

    private static final class Cand {
        final Tree repr;
        final int cost;
        final int firstStmt;
        int count;

        Cand(Tree repr, int cost, int firstStmt) {
            this.repr = repr;
            this.cost = cost;
            this.firstStmt = firstStmt;
        }
    }

    // ---- ключи и кандидаты ---------------------------------------------------------------------

    private String key(Tree e, State st, Map<Tree, String> memo) {
        String cached = memo.get(e);
        if (cached != null) {
            return cached;
        }
        StringBuilder sb = new StringBuilder(24);
        sb.append(e.op).append(':').append(e.type);
        if (e.isConst()) {
            sb.append('=').append(e.k);
        } else {
            sb.append('#').append(e.imm);
        }
        if (e.ints.length > 0) {
            sb.append(java.util.Arrays.toString(e.ints));
        }
        switch (e.op) {
            case Op.LOCAL_GET -> {
                sb.append('@').append(st.ver[e.imm]);
                if (!isTracked(e.imm)) {
                    sb.append('m').append(st.memVer);
                }
            }
            case Op.LOAD -> sb.append('m').append(st.memVer);
            case Op.CALL -> sb.append('m').append(st.memVer);
            case Op.INTRINSIC -> {
                if (Builtins.has(e.imm, Builtins.READS)) {
                    sb.append('m').append(st.memVer);
                }
            }
            default -> {
            }
        }
        if (e.kids.length > 0) {
            sb.append('(');
            for (Tree k : e.kids) {
                sb.append(key(k, st, memo)).append(',');
            }
            sb.append(')');
        }
        String r = sb.toString();
        memo.put(e, r);
        return r;
    }

    private boolean eligible(Tree e, boolean conditional) {
        if (e.kids.length == 0 || e.isStmt() || !Costs.storable(src, e.type)) {
            return false;
        }
        if (fx.hasEffect(e) || Costs.contains(e, Op.LOCAL_ADDR) || Costs.cost(e) < Costs.MIN_COST) {
            return false;
        }
        return !conditional || (!fx.readsMemory(e) && !Effects.mayTrap(e));
    }

    private static Tree[] exprRoots(Tree s) {
        switch (s.op) {
            case Op.LOCAL_DECL, Op.LOCAL_SET, Op.EXPR_STMT, Op.RETURN -> {
                return s.kids;
            }
            case Op.STORE -> {
                return s.kids;
            }
            case Op.IF, Op.SWITCH -> {
                return new Tree[]{s.kids[0]};
            }
            default -> {
                return Tree.NONE;
            }
        }
    }

    private void collect(Tree e, int stmtIdx, State st, boolean conditional, Map<Tree, String> memo,
                         Map<String, Cand> out) {
        if (e.kids.length == 0) {
            return;
        }
        if (eligible(e, conditional)) {
            String k = key(e, st, memo);
            Cand c = out.get(k);
            if (c == null) {
                c = new Cand(e, Costs.cost(e), stmtIdx);
                out.put(k, c);
            }
            c.count++;
        }
        for (int i = 0; i < e.kids.length; i++) {
            collect(e.kids[i], stmtIdx, st, conditional || (e.op == Op.SELECT && i >= 1), memo, out);
        }
    }

    private Tree replace(Tree e, String key, int local, State st, boolean conditional, Map<Tree, String> memo) {
        if (e.kids.length == 0) {
            return e;
        }
        if (eligible(e, conditional) && key(e, st, memo).equals(key)) {
            return Tree.ofImm(Op.LOCAL_GET, e.type, local);
        }
        Tree[] nk = null;
        for (int i = 0; i < e.kids.length; i++) {
            Tree r = replace(e.kids[i], key, local, st, conditional || (e.op == Op.SELECT && i >= 1), memo);
            if (r != e.kids[i]) {
                if (nk == null) {
                    nk = e.kids.clone();
                }
                nk[i] = r;
            }
        }
        return nk == null ? e : e.withKids(nk);
    }

    private Tree replaceInStmt(Tree s, String key, int local, State st, Map<Tree, String> memo) {
        switch (s.op) {
            case Op.LOCAL_DECL, Op.LOCAL_SET, Op.EXPR_STMT, Op.RETURN, Op.STORE -> {
                Tree[] nk = null;
                for (int i = 0; i < s.kids.length; i++) {
                    Tree r = replace(s.kids[i], key, local, st, false, memo);
                    if (r != s.kids[i]) {
                        if (nk == null) {
                            nk = s.kids.clone();
                        }
                        nk[i] = r;
                    }
                }
                return nk == null ? s : s.withKids(nk);
            }
            case Op.IF, Op.SWITCH -> {
                return s.withKid(0, replace(s.kids[0], key, local, st, false, memo));
            }
            default -> {
                return s;
            }
        }
    }

    // ---- обработка одного блока ----------------------------------------------------------------

    private Tree block(Tree b) {
        List<Tree> stmts = new ArrayList<>(List.of(b.kids));
        for (int round = 0; round < MAX_TEMPS_PER_BLOCK; round++) {
            Map<String, Cand> cands = new LinkedHashMap<>();
            State st = new State();
            for (int i = 0; i < stmts.size(); i++) {
                Map<Tree, String> memo = new IdentityHashMap<>();
                for (Tree root : exprRoots(stmts.get(i))) {
                    collect(root, i, st, false, memo, cands);
                }
                st.advance(stmts.get(i));
            }
            String bestKey = null;
            long bestGain = 0;
            for (Map.Entry<String, Cand> e : cands.entrySet()) {
                Cand c = e.getValue();
                long gain = (long) c.cost * (c.count - 1);
                if (c.count >= 2 && gain > bestGain) {
                    bestGain = gain;
                    bestKey = e.getKey();
                }
            }
            if (bestKey == null) {
                break;
            }
            Cand best = cands.get(bestKey);
            int words = RegisterPressure.words(src, best.repr.type);
            if (!budget.tryTake(words, best.cost)) {
                // нет запаса регистров: этот кандидат отвергнут; остальные тоже не дешевле по словам - выходим
                break;
            }
            int local = fn.newLocal(best.repr.type, "cse" + (counter++));
            State st2 = new State();
            for (int i = 0; i < stmts.size(); i++) {
                if (i >= best.firstStmt) {
                    Map<Tree, String> memo = new IdentityHashMap<>();
                    stmts.set(i, replaceInStmt(stmts.get(i), bestKey, local, st2, memo));
                }
                st2.advance(stmts.get(i));
            }
            Tree decl = Tree.ofImm(Op.LOCAL_DECL, com.sixik.gpu.ir.Types.VOID, local, best.repr);
            stmts.add(best.firstStmt, decl);
        }
        return b.withKids(stmts.toArray(Tree.NONE));
    }
}
