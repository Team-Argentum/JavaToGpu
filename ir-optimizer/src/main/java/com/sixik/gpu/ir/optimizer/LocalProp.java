package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.Op;

import java.util.BitSet;

/**
 * Протяжка констант и копий по изменяемым локалям (аналог constant/copy propagation над не-SSA IR).
 *
 * <p>IR хранит локали как изменяемые переменные, поэтому проход ведёт прямой анализ по структурированному потоку
 * управления: окружение {@code локаль -> атом}, где атом - константа, параметр либо чтение другой локали.
 * {@code LOCAL_GET}, для которого значение известно, заменяется атомом.</p>
 *
 * <ul>
 *   <li>Присваивание локали {@code x} убивает факт про {@code x} и факты вида «{@code y} - копия {@code x}».</li>
 *   <li>{@code if}: окружение после - пересечение окружений веток (ветка, заканчивающаяся переходом, не участвует).</li>
 *   <li>Циклы и {@code switch}: перед входом убиваются все локали, которым присваивают внутри (консервативно и
 *       корректно для любых {@code break/continue}); факты о остальных локалях сохраняются внутри и после.</li>
 *   <li>Локали с взятым адресом и массивы не отслеживаются ({@link Locals#tracked}).</li>
 * </ul>
 */
final class LocalProp {

    /** Окружение; {@code dead} - точка недостижима (join игнорирует такое окружение). */
    private static final class Env {
        final Tree[] v;
        boolean dead;

        Env(int n) {
            v = new Tree[n];
        }

        Env copy() {
            Env e = new Env(v.length);
            System.arraycopy(v, 0, e.v, 0, v.length);
            e.dead = dead;
            return e;
        }

        void kill(int local) {
            v[local] = null;
            for (int j = 0; j < v.length; j++) {
                if (v[j] != null && v[j].op == Op.LOCAL_GET && v[j].imm == local) {
                    v[j] = null;
                }
            }
        }

        void killAll(BitSet locals) {
            for (int l = locals.nextSetBit(0); l >= 0; l = locals.nextSetBit(l + 1)) {
                kill(l);
            }
        }
    }

    private final boolean[] tracked;
    private Env cur;

    private LocalProp(boolean[] tracked) {
        this.tracked = tracked;
    }

    /**
     * Выполняет протяжку для тела функции.
     *
     * @param fn  функция
     * @param src модуль (таблица типов)
     * @return новое тело (то же, если ничего не изменилось)
     */
    static Tree run(FnTree fn, com.sixik.gpu.ir.IrModule src) {
        LocalProp p = new LocalProp(Locals.tracked(fn, src));
        p.cur = new Env(fn.localCount);
        return p.stmt(fn.body);
    }

    private boolean isAtom(Tree v) {
        return v.isConst() || v.op == Op.PARAM || (v.op == Op.LOCAL_GET && tracked[v.imm]);
    }

    private Tree sub(Tree e) {
        if (e.op == Op.LOCAL_GET) {
            if (tracked[e.imm] && cur.v[e.imm] != null) {
                return cur.v[e.imm];
            }
            return e;
        }
        if (e.kids.length == 0) {
            return e;
        }
        Tree[] nk = null;
        for (int i = 0; i < e.kids.length; i++) {
            Tree r = sub(e.kids[i]);
            if (r != e.kids[i]) {
                if (nk == null) {
                    nk = e.kids.clone();
                }
                nk[i] = r;
            }
        }
        return nk == null ? e : e.withKids(nk);
    }

    private void join(Env a, Env b) {
        if (a.dead) {
            cur = b;
            return;
        }
        if (b.dead) {
            cur = a;
            return;
        }
        Env r = new Env(a.v.length);
        for (int i = 0; i < r.v.length; i++) {
            if (a.v[i] != null && b.v[i] != null && Tree.same(a.v[i], b.v[i])) {
                r.v[i] = a.v[i];
            }
        }
        cur = r;
    }

    private Tree stmt(Tree s) {
        switch (s.op) {
            case Op.LOCAL_DECL, Op.LOCAL_SET -> {
                Tree v = sub(s.kids[0]);
                cur.kill(s.imm);
                if (tracked[s.imm] && isAtom(v) && !(v.op == Op.LOCAL_GET && v.imm == s.imm)) {
                    cur.v[s.imm] = v;
                }
                return s.withKid(0, v);
            }
            case Op.EXPR_STMT, Op.STORE -> {
                return exprKids(s);
            }
            case Op.RETURN -> {
                Tree r = exprKids(s);
                kill();
                return r;
            }
            case Op.BREAK, Op.CONTINUE -> {
                kill();
                return s;
            }
            case Op.BLOCK -> {
                Tree[] nk = null;
                for (int i = 0; i < s.kids.length; i++) {
                    Tree r = stmt(s.kids[i]);
                    if (r != s.kids[i]) {
                        if (nk == null) {
                            nk = s.kids.clone();
                        }
                        nk[i] = r;
                    }
                }
                // локали блока вышли из области видимости: ни их значения, ни копии на них дальше использовать нельзя
                for (Tree k : s.kids) {
                    if (Simplify.isDecl(k)) {
                        cur.kill(k.imm);
                    }
                }
                return nk == null ? s : s.withKids(nk);
            }
            case Op.IF -> {
                return ifStmt(s);
            }
            case Op.LOOP -> {
                return loop(s);
            }
            case Op.DO_WHILE -> {
                return doWhile(s);
            }
            case Op.SWITCH -> {
                return switchStmt(s);
            }
            default -> {
                return s;
            }
        }
    }

    private void kill() {
        java.util.Arrays.fill(cur.v, null);
        cur.dead = true;
    }

    private Tree exprKids(Tree s) {
        Tree[] nk = null;
        for (int i = 0; i < s.kids.length; i++) {
            Tree r = sub(s.kids[i]);
            if (r != s.kids[i]) {
                if (nk == null) {
                    nk = s.kids.clone();
                }
                nk[i] = r;
            }
        }
        return nk == null ? s : s.withKids(nk);
    }

    private Tree ifStmt(Tree s) {
        Tree cond = sub(s.kids[0]);
        Env entry = cur;
        cur = entry.copy();
        Tree thenB = stmt(s.kids[1]);
        Env afterThen = cur;
        cur = entry.copy();
        Tree elseB = s.kids.length == 3 ? stmt(s.kids[2]) : null;
        Env afterElse = cur;
        join(afterThen, afterElse);
        Tree[] nk = elseB == null ? new Tree[]{cond, thenB} : new Tree[]{cond, thenB, elseB};
        return s.withKids(nk);
    }

    private Tree loop(Tree s) {
        BitSet assigned = new BitSet();
        Locals.assigned(s, assigned);
        cur.killAll(assigned);
        Env head = cur.copy();
        Tree cond = sub(s.kids[0]);
        cur = head.copy();
        Tree body = stmt(s.kids[1]);
        Tree[] nk = new Tree[s.kids.length];
        nk[0] = cond;
        nk[1] = body;
        if (s.kids.length == 3) {
            cur = head.copy();
            nk[2] = stmt(s.kids[2]);
        }
        cur = head;
        return s.withKids(nk);
    }

    private Tree doWhile(Tree s) {
        BitSet assigned = new BitSet();
        Locals.assigned(s, assigned);
        cur.killAll(assigned);
        Env head = cur.copy();
        Tree body = stmt(s.kids[0]);
        cur = head.copy();
        Tree cond = sub(s.kids[1]);
        cur = head;
        return s.withKids(new Tree[]{body, cond});
    }

    private Tree switchStmt(Tree s) {
        BitSet assigned = new BitSet();
        Locals.assigned(s, assigned);
        cur.killAll(assigned);
        Tree key = sub(s.kids[0]);
        Env head = cur.copy();
        Tree[] nk = s.kids.clone();
        nk[0] = key;
        for (int i = 1; i < nk.length; i++) {
            cur = head.copy();
            nk[i] = stmt(s.kids[i]);
        }
        cur = head;
        return s.withKids(nk);
    }
}
