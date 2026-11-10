package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

import java.util.BitSet;

/**
 * Обратный анализ живости переменных по структурированному потоку управления и на его основе - удаление мёртвых записей.
 *
 * <p>Переменные: локали {@code 0..L-1} и параметры {@code L..L+P-1} (параметр «живёт» от входа до последнего чтения -
 * это нужно оценке давления на регистры). Живость считается обратным проходом по дереву:</p>
 * <ul>
 *   <li>запись в локаль убивает её живость, чтение - добавляет;</li>
 *   <li>{@code if}: объединение живости веток; {@code break/continue/return} подменяют множество живых на живые в цели
 *       перехода (выход из цикла, голова/update цикла, пусто);</li>
 *   <li>циклы - итерация до неподвижной точки (множества только растут, число итераций ограничено числом локалей);</li>
 *   <li>{@code switch} - цепочка «проваливания» тел с объединением входов всех case.</li>
 * </ul>
 *
 * <p><b>Удаление мёртвых записей</b> ({@code dse}): присваивание локали, которая нигде дальше не читается, удаляется
 * (если значение без эффектов) либо превращается в вычисление ради эффекта; объявление мёртвой локали получает нулевую
 * инициализацию вместо вычисления. Локали с взятым адресом и массивы не трогаются.</p>
 *
 * <p>Второе применение - оценка давления: слушатель получает множество переменных, удерживаемых в регистрах на
 * время вычисления каждого выражения ({@link RegisterPressure}).</p>
 */
final class Liveness {

    /** Получатель точек вычисления выражений. */
    interface Listener {
        /**
         * @param t      вычисляемое выражение либо стейтмент (для простых стейтментов)
         * @param across переменные, удерживаемые на время вычисления (живые до и после)
         */
        void point(Tree t, BitSet across);
    }

    private final boolean[] tracked;
    private final int paramBase;
    private final Effects fx;
    private final boolean dse;
    private final Listener listener;
    private final FnTree fn;
    private BitSet breakLive = new BitSet();
    private BitSet contLive = new BitSet();

    private Liveness(FnTree fn, IrModule src, Effects fx, boolean dse, Listener listener) {
        this.fn = fn;
        this.tracked = Locals.tracked(fn, src);
        this.paramBase = fn.localCount;
        this.fx = fx;
        this.dse = dse;
        this.listener = listener;
    }

    /**
     * Удаляет мёртвые записи в локали.
     *
     * @param fn  функция
     * @param src модуль
     * @param fx  анализ эффектов
     * @return новое тело (то же, если удалять нечего)
     */
    static Tree eliminateDeadStores(FnTree fn, IrModule src, Effects fx) {
        Liveness l = new Liveness(fn, src, fx, true, null);
        return l.walk(fn.body, new BitSet(), true);
    }

    /**
     * Обходит функцию, сообщая слушателю точки вычисления; ничего не удаляет.
     *
     * @param fn       функция
     * @param src      модуль
     * @param fx       анализ эффектов
     * @param listener получатель точек
     */
    static void scan(FnTree fn, IrModule src, Effects fx, Listener listener) {
        Liveness l = new Liveness(fn, src, fx, false, listener);
        l.walk(fn.body, new BitSet(), true);
    }

    /**
     * Живые переменные на входе каждого стейтмента верхнего уровня тела (индекс {@code i} - перед {@code i}-м,
     * последний элемент - после последнего).
     *
     * @param fn  функция
     * @param src модуль
     * @param fx  анализ эффектов
     * @return массив множеств длиной {@code kids + 1}
     */
    static BitSet[] topLevelLiveIn(FnTree fn, IrModule src, Effects fx) {
        Liveness l = new Liveness(fn, src, fx, false, null);
        Tree[] kids = fn.body.kids;
        BitSet[] ins = new BitSet[kids.length + 1];
        BitSet live = new BitSet();
        ins[kids.length] = (BitSet) live.clone();
        for (int i = kids.length - 1; i >= 0; i--) {
            l.walk(kids[i], live, false);
            ins[i] = (BitSet) live.clone();
        }
        return ins;
    }

    /** @return число переменных учёта: локали плюс параметры */
    int variableCount() {
        return fn.localCount + fn.params.length;
    }

    // =============================================================================================

    private void addUses(Tree e, BitSet live) {
        Locals.used(e, live, paramBase);
    }

    private void report(Tree t, BitSet liveOut, BitSet liveIn, boolean fin) {
        if (fin && listener != null) {
            BitSet across = (BitSet) liveOut.clone();
            across.or(liveIn);
            listener.point(t, across);
        }
    }

    /**
     * Обратный шаг по стейтменту. На входе {@code live} - живые после стейтмента, на выходе - живые перед ним.
     */
    private Tree walk(Tree s, BitSet live, boolean fin) {
        switch (s.op) {
            case Op.BLOCK -> {
                return block(s, live, fin);
            }
            case Op.LOCAL_DECL -> {
                return decl(s, live, fin);
            }
            case Op.LOCAL_SET -> {
                return set(s, live, fin);
            }
            case Op.EXPR_STMT, Op.STORE -> {
                BitSet out = (BitSet) live.clone();
                addUses(s, live);
                report(s, out, live, fin);
                return s;
            }
            case Op.RETURN -> {
                live.clear();
                addUses(s, live);
                report(s, new BitSet(), live, fin);
                return s;
            }
            case Op.BREAK -> {
                live.clear();
                live.or(breakLive);
                return s;
            }
            case Op.CONTINUE -> {
                live.clear();
                live.or(contLive);
                return s;
            }
            case Op.IF -> {
                return ifStmt(s, live, fin);
            }
            case Op.LOOP -> {
                return loop(s, live, fin);
            }
            case Op.DO_WHILE -> {
                return doWhile(s, live, fin);
            }
            case Op.SWITCH -> {
                return switchStmt(s, live, fin);
            }
            default -> {
                return s;
            }
        }
    }

    private Tree block(Tree s, BitSet live, boolean fin) {
        int n = s.kids.length;
        Tree[] rev = new Tree[n];
        int cnt = 0;
        for (int i = n - 1; i >= 0; i--) {
            Tree r = walk(s.kids[i], live, fin);
            if (!Simplify.isEmpty(r)) {
                rev[cnt++] = r;
            }
        }
        if (!fin) {
            return s;
        }
        Tree[] nk = new Tree[cnt];
        for (int i = 0; i < cnt; i++) {
            nk[i] = rev[cnt - 1 - i];
        }
        return s.withKids(nk);
    }

    private Tree decl(Tree s, BitSet live, boolean fin) {
        int l = s.imm;
        boolean dead = dse && tracked[l] && !live.get(l);
        BitSet out = (BitSet) live.clone();
        live.clear(l);
        Tree init = s.kids[0];
        Tree result = s;
        if (dead && !init.isConst() && Types.isScalar(fn.locals[l]) && fx.isRemovable(init)) {
            Tree zero = Tree.constLike(init, fn.locals[l], 0);
            result = s.withKid(0, zero);
            init = zero;
        }
        addUses(init, live);
        report(result, out, live, fin);
        return result;
    }

    private Tree set(Tree s, BitSet live, boolean fin) {
        int l = s.imm;
        boolean dead = dse && tracked[l] && !live.get(l);
        BitSet out = (BitSet) live.clone();
        Tree value = s.kids[0];
        if (dead) {
            if (fx.isRemovable(value)) {
                return Simplify.EMPTY;
            }
            Tree stmt = Tree.of(Op.EXPR_STMT, Types.VOID, value);
            addUses(value, live);
            report(stmt, out, live, fin);
            return stmt;
        }
        live.clear(l);
        addUses(value, live);
        report(s, out, live, fin);
        return s;
    }

    private Tree ifStmt(Tree s, BitSet live, boolean fin) {
        BitSet lt = (BitSet) live.clone();
        BitSet le = (BitSet) live.clone();
        Tree thenB = walk(s.kids[1], lt, fin);
        Tree elseB = s.kids.length == 3 ? walk(s.kids[2], le, fin) : null;
        live.clear();
        live.or(lt);
        live.or(le);
        BitSet afterCond = (BitSet) live.clone();
        addUses(s.kids[0], live);
        report(s.kids[0], afterCond, live, fin);
        if (!fin) {
            return s;
        }
        Tree[] nk = elseB == null ? new Tree[]{s.kids[0], thenB} : new Tree[]{s.kids[0], thenB, elseB};
        return s.withKids(nk);
    }

    private Tree loop(Tree s, BitSet live, boolean fin) {
        BitSet exit = (BitSet) live.clone();
        BitSet head = (BitSet) exit.clone();
        addUses(s.kids[0], head);
        boolean hasUpdate = s.kids.length == 3;

        BitSet savedBreak = breakLive;
        BitSet savedCont = contLive;
        for (int iter = 0, max = variableCount() + 2; iter < max; iter++) {
            BitSet afterBody = (BitSet) head.clone();
            if (hasUpdate) {
                walk(s.kids[2], afterBody, false);
            }
            breakLive = exit;
            contLive = afterBody;
            BitSet bodyIn = (BitSet) afterBody.clone();
            walk(s.kids[1], bodyIn, false);
            BitSet condIn = (BitSet) bodyIn.clone();
            condIn.or(exit);
            addUses(s.kids[0], condIn);
            BitSet merged = (BitSet) head.clone();
            merged.or(condIn);
            if (merged.equals(head)) {
                break;
            }
            head = merged;
        }

        Tree[] nk = s.kids.clone();
        BitSet afterBody = (BitSet) head.clone();
        if (hasUpdate) {
            nk[2] = walk(s.kids[2], afterBody, fin);
        }
        breakLive = exit;
        contLive = afterBody;
        BitSet bodyIn = (BitSet) afterBody.clone();
        nk[1] = walk(s.kids[1], bodyIn, fin);
        breakLive = savedBreak;
        contLive = savedCont;

        report(s.kids[0], exit, head, fin);
        live.clear();
        live.or(head);
        return fin ? s.withKids(nk) : s;
    }

    private Tree doWhile(Tree s, BitSet live, boolean fin) {
        BitSet exit = (BitSet) live.clone();
        BitSet head = (BitSet) exit.clone();
        BitSet savedBreak = breakLive;
        BitSet savedCont = contLive;
        BitSet condLive = null;
        for (int iter = 0, max = variableCount() + 2; iter < max; iter++) {
            condLive = (BitSet) head.clone();
            condLive.or(exit);
            addUses(s.kids[1], condLive);
            breakLive = exit;
            contLive = condLive;
            BitSet bodyIn = (BitSet) condLive.clone();
            walk(s.kids[0], bodyIn, false);
            BitSet merged = (BitSet) head.clone();
            merged.or(bodyIn);
            if (merged.equals(head)) {
                break;
            }
            head = merged;
        }
        condLive = (BitSet) head.clone();
        condLive.or(exit);
        BitSet beforeCond = (BitSet) condLive.clone();
        addUses(s.kids[1], condLive);
        report(s.kids[1], beforeCond, condLive, fin);
        breakLive = exit;
        contLive = condLive;
        BitSet bodyIn = (BitSet) condLive.clone();
        Tree body = walk(s.kids[0], bodyIn, fin);
        breakLive = savedBreak;
        contLive = savedCont;
        live.clear();
        live.or(bodyIn);
        live.or(head);
        return fin ? s.withKids(new Tree[]{body, s.kids[1]}) : s;
    }

    private Tree switchStmt(Tree s, BitSet live, boolean fin) {
        BitSet exit = (BitSet) live.clone();
        BitSet savedBreak = breakLive;
        breakLive = exit;
        int n = s.kids.length - 1;
        Tree[] nk = s.kids.clone();
        BitSet next = (BitSet) exit.clone();
        BitSet total = new BitSet();
        boolean hasDefault = false;
        for (int i = n - 1; i >= 0; i--) {
            BitSet in = (BitSet) next.clone();
            nk[1 + i] = walk(s.kids[1 + i], in, fin);
            total.or(in);
            next = in;
            hasDefault |= s.ints[2 * i] != 0;
        }
        breakLive = savedBreak;
        if (!hasDefault) {
            total.or(exit);
        }
        BitSet afterKey = (BitSet) total.clone();
        addUses(s.kids[0], total);
        report(s.kids[0], afterKey, total, fin);
        live.clear();
        live.or(total);
        return fin ? s.withKids(nk) : s;
    }
}
