package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

import java.util.BitSet;

/**
 * Слияние умножения и сложения в {@code fma} через локали (разрешается флагом ядра {@code fp=contract}).
 *
 * <p>{@link Simplify} сливает {@code a*b + c} только внутри одного выражения. Java-код обычно пишет произведение в
 * переменную: {@code float p = x * y; ... s = p + c;} - этот проход находит такие пары. Анализ прямой, по
 * структурированному потоку управления, как в {@link LocalProp}: окружение хранит факты «локаль {@code p} равна
 * {@code x * y}», где {@code x} и {@code y} - атомы (константа, параметр, чтение отслеживаемой локали). Факт живёт,
 * пока не присвоены ни {@code p}, ни локали внутри {@code x} и {@code y}, поэтому в точке сложения {@code x * y} даёт
 * то же значение, что и {@code p}, и {@code p + c} можно заменить на {@code fma(x, y, c)}.</p>
 *
 * <ul>
 *   <li>{@code p + c}, {@code c + p} - {@code fma(x, y, c)}; {@code p - c} - {@code fma(x, y, -c)};
 *       {@code c - p} - {@code fma(-x, y, c)}. Если оба слагаемых - произведения, сливается левое.</li>
 *   <li>Сложение, у которого слагаемое уже является умножением в самом выражении, оставляется {@link Simplify}.</li>
 *   <li>Если после замены {@code p} больше не читается, объявление удаляет проход мёртвых локалей: умножение
 *       исчезает совсем. Иначе умножение остаётся для других чтений, а сложение становится {@code fma} - число
 *       операций то же.</li>
 *   <li>Java-код обычно умножает чтения памяти: {@code float p = a[i] * b[i]}. Если {@code p} читается сложением,
 *       не-атомарные множители выносятся во временные локали ({@code t1 = a[i]; t2 = b[i]; p = t1 * t2}) - порядок
 *       вычислений тот же, а {@code fma(t1, t2, c)} берёт ровно те значения, что были при умножении (даже если память
 *       потом изменилась). Драйвер компилирует в SSA, поэтому лишние локали ничего не стоят.</li>
 *   <li>Только для типов, у которых устройство выполняет FMA аппаратно ({@link OptimizeOptions#fmaF32()},
 *       {@link OptimizeOptions#fmaF64()}): программный {@code fma()} медленнее пары операций.</li>
 * </ul>
 */
final class Contract {

    private static final int FMA = Builtins.idOf("fma");

    private final FnTree fn;
    private boolean[] tracked;
    private final IrModule src;
    private final OptimizeOptions opts;
    /** Факты: {@code facts[p]} - умножение атомов, равное значению локали {@code p}, либо {@code null}. */
    private Tree[] facts;
    /** Текущая точка недостижима. */
    private boolean dead;

    /** Локали, которые читаются непосредственно как слагаемое вещественного сложения или вычитания. */
    private final BitSet summands = new BitSet();

    private Contract(FnTree fn, IrModule src, OptimizeOptions opts) {
        this.fn = fn;
        this.src = src;
        this.opts = opts;
        boolean[] t = Locals.tracked(fn, src);
        collectSummands(fn.body);
        // запас под временные локали: по две на каждое умножение, которое может понадобиться разделить
        int cap = t.length + 2 * countSplits(fn.body, t);
        this.tracked = java.util.Arrays.copyOf(t, cap);
        this.facts = new Tree[cap];
    }

    /**
     * @param fn   функция
     * @param fp   маска {@code fp}, действующая в функции ({@link FpModes})
     * @param src  исходный модуль (таблица типов)
     * @param opts настройки (возможности устройства)
     * @return новое тело (то же, если сливать нечего или слияние не разрешено)
     */
    static Tree run(FnTree fn, int fp, IrModule src, OptimizeOptions opts) {
        if ((fp & IrModule.FP_CONTRACT) == 0 || FMA < 0 || !(opts.fmaF32() || opts.fmaF64())) {
            return fn.body;
        }
        Contract c = new Contract(fn, src, opts);
        return c.stmt(fn.body);
    }

    /** Можно ли сливать операцию типа {@code type}: вещественный скаляр или вектор с аппаратным FMA. */
    static boolean fusible(IrModule src, int type, OptimizeOptions opts) {
        int scalar = Types.isVector(src, type) ? Types.scalarOf(src, type) : type;
        return scalar == Types.F32 ? opts.fmaF32() : scalar == Types.F64 && opts.fmaF64();
    }

    // ---- временные локали для множителей -------------------------------------------------------

    private void collectSummands(Tree t) {
        if ((t.op == Op.ADD || t.op == Op.SUB) && fusible(src, t.type, opts)) {
            for (Tree k : t.kids) {
                if (k.op == Op.LOCAL_GET) {
                    summands.set(k.imm);
                }
            }
        }
        for (Tree k : t.kids) {
            collectSummands(k);
        }
    }

    /** Нужно ли разделить присваивание {@code s}: произведение с не-атомарным множителем в локаль-слагаемое. */
    private boolean needsSplit(Tree s, boolean[] t) {
        if ((s.op != Op.LOCAL_DECL && s.op != Op.LOCAL_SET) || !t[s.imm] || !summands.get(s.imm)) {
            return false;
        }
        Tree v = s.kids[0];
        if (v.op != Op.MUL || !fusible(src, v.type, opts)) {
            return false;
        }
        return !atom(v.kids[0], t) || !atom(v.kids[1], t);
    }

    private int countSplits(Tree t, boolean[] tr) {
        int c = 0;
        if (t.op == Op.BLOCK) {
            for (Tree k : t.kids) {
                c += needsSplit(k, tr) ? 1 : 0;
            }
        }
        for (Tree k : t.kids) {
            c += countSplits(k, tr);
        }
        return c;
    }

    /**
     * {@code p = X * Y} -> {@code t1 = X; t2 = Y; p = t1 * t2} (атомарный множитель остаётся на месте).
     *
     * @param s присваивание
     * @return стейтменты, заменяющие {@code s} в блоке
     */
    private Tree[] split(Tree s) {
        Tree v = s.kids[0];
        Tree[] out = new Tree[3];
        int n = 0;
        Tree[] factors = new Tree[2];
        for (int i = 0; i < 2; i++) {
            Tree k = v.kids[i];
            if (atom(k, tracked)) {
                factors[i] = k;
            } else {
                int l = fn.newLocal(k.type, "fma" + i);
                tracked[l] = true;
                out[n++] = Tree.ofImm(Op.LOCAL_DECL, Types.VOID, l, k);
                factors[i] = Tree.ofImm(Op.LOCAL_GET, k.type, l);
            }
        }
        out[n++] = s.withKid(0, v.withKids(factors));
        return java.util.Arrays.copyOf(out, n);
    }

    // ---- факты ---------------------------------------------------------------------------------

    private boolean isAtom(Tree v) {
        return atom(v, tracked);
    }

    private static boolean atom(Tree v, boolean[] tracked) {
        return v.isConst() || v.op == Op.PARAM || (v.op == Op.LOCAL_GET && v.imm < tracked.length && tracked[v.imm]);
    }

    private static boolean reads(Tree mul, int local) {
        for (Tree k : mul.kids) {
            if (k.op == Op.LOCAL_GET && k.imm == local) {
                return true;
            }
        }
        return false;
    }

    private void kill(int local) {
        facts[local] = null;
        for (int p = 0; p < facts.length; p++) {
            if (facts[p] != null && reads(facts[p], local)) {
                facts[p] = null;
            }
        }
    }

    private void killAll(BitSet locals) {
        for (int l = locals.nextSetBit(0); l >= 0; l = locals.nextSetBit(l + 1)) {
            kill(l);
        }
    }

    private void unreachable() {
        java.util.Arrays.fill(facts, null);
        dead = true;
    }

    private Tree[] snapshot() {
        return facts.clone();
    }

    /** Пересечение фактов двух путей; путь, закончившийся переходом, не участвует. */
    private void join(Tree[] a, boolean aDead, Tree[] b, boolean bDead) {
        if (aDead) {
            facts = b;
            dead = bDead;
            return;
        }
        if (bDead) {
            facts = a;
            dead = false;
            return;
        }
        Tree[] r = new Tree[a.length];
        for (int i = 0; i < r.length; i++) {
            if (a[i] != null && b[i] != null && Tree.same(a[i], b[i])) {
                r[i] = a[i];
            }
        }
        facts = r;
        dead = false;
    }

    // ---- выражения -----------------------------------------------------------------------------

    private Tree expr(Tree e) {
        if (e.kids.length == 0) {
            return e;
        }
        Tree[] nk = null;
        for (int i = 0; i < e.kids.length; i++) {
            Tree r = expr(e.kids[i]);
            if (r != e.kids[i]) {
                if (nk == null) {
                    nk = e.kids.clone();
                }
                nk[i] = r;
            }
        }
        Tree t = nk == null ? e : e.withKids(nk);
        return (t.op == Op.ADD || t.op == Op.SUB) ? contract(t) : t;
    }

    /** Произведение, равное значению выражения {@code v} (чтению локали с фактом), либо {@code null}. */
    private Tree product(Tree v, int type) {
        if (v.op != Op.LOCAL_GET || v.imm >= facts.length) {
            return null;
        }
        Tree m = facts[v.imm];
        return m != null && m.type == type ? m : null;
    }

    private Tree contract(Tree t) {
        Tree a = t.kids[0];
        Tree b = t.kids[1];
        if (a.op == Op.MUL || b.op == Op.MUL || !fusible(src, t.type, opts)) {
            return t;
        }
        Tree pa = product(a, t.type);
        if (pa != null) {
            Tree c = t.op == Op.ADD ? b : Tree.of(Op.NEG, t.type, b);
            return Tree.of(Op.INTRINSIC, t.type, pa.kids[0], pa.kids[1], c).withImm(FMA);
        }
        Tree pb = product(b, t.type);
        if (pb != null) {
            Tree x = t.op == Op.ADD ? pb.kids[0] : Tree.of(Op.NEG, t.type, pb.kids[0]);
            return Tree.of(Op.INTRINSIC, t.type, x, pb.kids[1], a).withImm(FMA);
        }
        return t;
    }

    // ---- стейтменты ----------------------------------------------------------------------------

    private Tree stmt(Tree s) {
        switch (s.op) {
            case Op.LOCAL_DECL, Op.LOCAL_SET -> {
                Tree v = expr(s.kids[0]);
                kill(s.imm);
                if (tracked[s.imm] && v.op == Op.MUL && fusible(src, v.type, opts)
                        && isAtom(v.kids[0]) && isAtom(v.kids[1]) && !reads(v, s.imm)) {
                    facts[s.imm] = v;
                }
                return s.withKid(0, v);
            }
            case Op.EXPR_STMT, Op.STORE -> {
                return kids(s);
            }
            case Op.RETURN -> {
                Tree r = kids(s);
                unreachable();
                return r;
            }
            case Op.BREAK, Op.CONTINUE -> {
                unreachable();
                return s;
            }
            case Op.BLOCK -> {
                java.util.List<Tree> out = new java.util.ArrayList<>(s.kids.length);
                boolean changed = false;
                for (Tree k : s.kids) {
                    Tree[] parts = needsSplit(k, tracked) ? split(k) : new Tree[]{k};
                    changed |= parts.length != 1;
                    for (Tree part : parts) {
                        Tree r = stmt(part);
                        changed |= r != k;
                        out.add(r);
                    }
                }
                // локали блока вышли из области видимости
                for (Tree k : out) {
                    if (Simplify.isDecl(k)) {
                        kill(k.imm);
                    }
                }
                return changed ? s.withKids(out.toArray(new Tree[0])) : s;
            }
            case Op.IF -> {
                Tree cond = expr(s.kids[0]);
                Tree[] entry = snapshot();
                boolean entryDead = dead;
                Tree thenB = stmt(s.kids[1]);
                Tree[] afterThen = facts;
                boolean thenDead = dead;
                facts = entry.clone();
                dead = entryDead;
                Tree elseB = s.kids.length == 3 ? stmt(s.kids[2]) : null;
                join(afterThen, thenDead, facts, dead);
                return s.withKids(elseB == null ? new Tree[]{cond, thenB} : new Tree[]{cond, thenB, elseB});
            }
            case Op.LOOP, Op.DO_WHILE, Op.SWITCH -> {
                return region(s);
            }
            default -> {
                return s;
            }
        }
    }

    private Tree kids(Tree s) {
        Tree[] nk = null;
        for (int i = 0; i < s.kids.length; i++) {
            Tree r = expr(s.kids[i]);
            if (r != s.kids[i]) {
                if (nk == null) {
                    nk = s.kids.clone();
                }
                nk[i] = r;
            }
        }
        return nk == null ? s : s.withKids(nk);
    }

    /**
     * Цикл или {@code switch}: перед входом убиваются факты о локалях, которым присваивают внутри (консервативно для
     * любых {@code break/continue}); каждая часть анализируется от этого входного окружения, после - оно же.
     */
    private Tree region(Tree s) {
        BitSet assigned = new BitSet();
        Locals.assigned(s, assigned);
        killAll(assigned);
        Tree[] head = snapshot();
        boolean headDead = dead;
        Tree[] nk = s.kids.clone();
        for (int i = 0; i < nk.length; i++) {
            facts = head.clone();
            dead = headDead;
            Tree k = s.kids[i];
            boolean isExpr = (s.op == Op.LOOP && i == 0) || (s.op == Op.DO_WHILE && i == 1) || (s.op == Op.SWITCH && i == 0);
            nk[i] = isExpr ? expr(k) : stmt(k);
        }
        facts = head;
        dead = headDead;
        return s.withKids(nk);
    }
}
