package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Склеивание соседних обращений к буферу ядра в векторные: {@code od[4*i+0..3]} - одна загрузка {@code vload4(od, i)}
 * и одна запись {@code vstore4(od, i, ...)}. Кодоген печатает их для параметров ядра кастом к {@code float4*}, и драйвер
 * делает одну 128-битную транзакцию ({@code ld.global.v4.f32}) вместо четырёх; сам драйвер соседние скалярные
 * обращения не склеивает.
 *
 * <h2>Что склеивается</h2>
 * <p>Обращение {@code p[S*X + c]}: {@code p} - параметр-массив ядра ({@code __global}, для чтения и {@code __constant}),
 * {@code S} - постоянный шаг ({@code S*X}, {@code X*S} или {@code X<<k}), {@code 0 <= c < S} - постоянная полоса,
 * {@code X} - чистое выражение из локалей, параметров и констант. Индекс может прийти и через локаль с единственным
 * присваиванием ({@code int b = 4*i; od[b + 1]}). Обращения с одинаковыми {@code p, S, X} образуют группу; блок
 * полос {@code [bW, bW+W)} ({@code W | S}, {@code W*размер элемента <= 16} байт - одна транзакция памяти: 4 полосы
 * {@code float/int}, 2 полосы {@code double/long}) склеивается, только если в группе есть все его
 * полосы - поэтому векторное обращение не трогает элементов, которых не трогала программа, и выровнено: его индекс в
 * векторах - {@code (S/W)*X + b}.</p>
 *
 * <h2>Загрузки</h2>
 * <p>Кандидаты - загрузки, которые блок исполняет безусловно (не в ветках {@code if/select}, не в телах циклов и вложенных
 * блоках). Векторная загрузка ставится в начало стейтмента с первой загрузкой блока, а каждая скалярная заменяется
 * компонентой. Замена допустима, если между этим началом и самой загрузкой нет: выхода ({@code return}, {@code break},
 * {@code continue} своего блока), вызова с эффектом, барьера, записи по неразобранному адресу с тем же типом элемента, присваивания локали из
 * {@code X} и записи, которая может задеть этот элемент. Запись {@code q[S*X + c']} с теми же {@code S, X} и другой
 * полосой его не задевает даже при {@code q == p}; массивы с разным типом элемента не перекрываются (Java), а
 * {@code __local}/{@code __private} не перекрываются с буферами. Загрузка, для которой условие не выполнено, остаётся
 * скалярной; блок строится, если у каждой полосы есть хотя бы одна допустимая загрузка.</p>
 *
 * <h2>Записи</h2>
 * <p>Подряд идущие стейтменты {@code p[S*X + c] = v_c} (только {@code __global}) с полосами, покрывающими блок,
 * заменяются одной {@code vstoreW} на месте первой из них. Значения {@code v_c} не должны читать память, иметь эффект или
 * падать: тогда порядок их вычисления и записей не наблюдаем.</p>
 *
 * <p>Только ядра: для параметров device-функций выравнивание не известно, и кодоген печатал бы {@code vloadN},
 * который драйвер разбирает на те же скалярные обращения.</p>
 */
final class Coalesce {

    /** {@code vload2, vload4, vload8, vload16} по {@code log2(W) - 1}. */
    private static final int[] VLOAD = {Builtins.idOf("vload2"), Builtins.idOf("vload4"), Builtins.idOf("vload8"),
            Builtins.idOf("vload16")};
    /** {@code vstore2 ... vstore16}. */
    private static final int[] VSTORE = {Builtins.idOf("vstore2"), Builtins.idOf("vstore4"),
            Builtins.idOf("vstore8"), Builtins.idOf("vstore16")};
    /** Ширины блоков от большей к меньшей. */
    private static final int[] WIDTHS = {16, 8, 4, 2};
    /** Наибольший шаг. */
    private static final int MAX_STRIDE = 64;

    private final FnTree fn;
    private final Codec.ModuleTree mt;
    private final Effects fx;
    /** Инициализатор локали с единственным присваиванием (объявление без {@code LOCAL_SET} и адреса) либо {@code null}. */
    private final Tree[] single;
    /** Сколько раз объект-узел встречается в теле (дерево может разделять поддеревья). */
    private final Map<Tree, Integer> uses = new IdentityHashMap<>();

    private Coalesce(FnTree fn, Codec.ModuleTree mt, Effects fx) {
        this.fn = fn;
        this.mt = mt;
        this.fx = fx;
        this.single = new Tree[fn.localCount];
        int[] decls = new int[fn.localCount];
        boolean[] dirty = new boolean[fn.localCount];
        scanLocals(fn.body, decls, dirty);
        for (int l = 0; l < fn.localCount; l++) {
            if (decls[l] != 1 || dirty[l]) {
                single[l] = null;
            }
        }
    }

    /**
     * @param fn функция
     * @param mt модуль
     * @param fx эффекты
     * @return новое тело либо прежнее, если склеивать нечего
     */
    static Tree run(FnTree fn, Codec.ModuleTree mt, Effects fx) {
        if (fn.kind != IrModule.KERNEL) {
            return fn.body;
        }
        return new Coalesce(fn, mt, fx).block(fn.body);
    }

    // ---- локали ------------------------------------------------------------------------------

    /** Обход без рекурсии по глубине выражений: явный стек. */
    private void scanLocals(Tree root, int[] decls, boolean[] dirty) {
        ArrayList<Tree> stack = new ArrayList<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            Tree t = stack.remove(stack.size() - 1);
            uses.merge(t, 1, Integer::sum);
            switch (t.op) {
                case Op.LOCAL_DECL -> {
                    decls[t.imm]++;
                    single[t.imm] = t.kid(0);
                }
                case Op.LOCAL_SET, Op.LOCAL_ADDR -> dirty[t.imm] = true;
                case Op.PRIVATE_ARRAY_DECL, Op.LOCAL_ARRAY_DECL -> dirty[t.imm] = true;
                default -> {
                }
            }
            for (Tree k : t.kids) {
                stack.add(k);
            }
        }
    }

    // ---- разбор индекса ----------------------------------------------------------------------

    /** Индекс {@code S*X + c}. */
    private record Affine(int stride, Tree x, int lane) {
    }

    /** Обращение {@code p[S*X + c]} к параметру-массиву ядра. */
    private record Access(int param, int elem, int space, Affine at) {
    }

    /**
     * @param ptr указатель ({@code ELEM_PTR})
     * @return обращение к параметру-массиву ядра с разобранным индексом либо {@code null}
     */
    private Access access(Tree ptr) {
        if (ptr.op != Op.ELEM_PTR || ptr.kid(0).op != Op.PARAM) {
            return null;
        }
        int t = fn.params[ptr.kid(0).imm];
        IrModule m = mt.source;
        if (t >= m.typeCount() || m.typeKind(t) != Types.KIND_ARRAY) {
            return null;
        }
        int elem = Types.elem(m, t);
        int space = Types.space(m, t);
        if (!(elem == Types.F32 || elem == Types.F64 || elem == Types.I32 || elem == Types.I64)
                || !(space == AddressSpace.GLOBAL || space == AddressSpace.CONSTANT)) {
            return null;
        }
        Affine a = affine(ptr.kid(1), true);
        return a == null ? null : new Access(ptr.kid(0).imm, elem, space, a);
    }

    /**
     * @param idx     индекс
     * @param viaInit можно ли раскрыть локаль с единственным присваиванием
     * @return {@code S*X + c} либо {@code null}
     */
    private Affine affine(Tree idx, boolean viaInit) {
        if (idx.type != Types.I32) {
            return null;
        }
        long c = 0;
        Tree t = idx;
        if (t.op == Op.ADD && t.kid(1).op == Op.CONST_INT) {
            c = t.kid(1).k;
            t = t.kid(0);
        } else if (t.op == Op.ADD && t.kid(0).op == Op.CONST_INT) {
            c = t.kid(0).k;
            t = t.kid(1);
        }
        Affine base = null;
        if (t.op == Op.MUL && strideConst(t.kid(1))) {
            base = new Affine((int) t.kid(1).k, t.kid(0), 0);
        } else if (t.op == Op.MUL && strideConst(t.kid(0))) {
            base = new Affine((int) t.kid(0).k, t.kid(1), 0);
        } else if (t.op == Op.SHL && t.kid(1).op == Op.CONST_INT && t.kid(1).k >= 1 && t.kid(1).k <= 6) {
            base = new Affine(1 << t.kid(1).k, t.kid(0), 0);
        } else if (viaInit && t.op == Op.LOCAL_GET && t.imm < single.length && single[t.imm] != null) {
            Affine inner = affine(single[t.imm], false);
            if (inner != null && stableLocals(inner.x())) {
                base = inner;
            }
        }
        if (base == null || base.stride() < 2 || base.stride() > MAX_STRIDE) {
            return null;
        }
        long lane = base.lane() + c;
        if (lane < 0 || lane >= base.stride() || !pure(base.x())) {
            return null;
        }
        return new Affine(base.stride(), base.x(), (int) lane);
    }

    private static boolean strideConst(Tree t) {
        return t.op == Op.CONST_INT && t.k >= 2 && t.k <= MAX_STRIDE;
    }

    /** @return выражение без памяти, эффектов и делений, способных упасть */
    private boolean pure(Tree x) {
        return !fx.hasEffect(x) && !fx.readsMemory(x) && !Effects.mayTrap(x) && !hasCall(x);
    }

    private static boolean hasCall(Tree x) {
        if (x.op == Op.CALL || x.op == Op.LOCAL_ADDR) {
            return true;
        }
        for (Tree k : x.kids) {
            if (hasCall(k)) {
                return true;
            }
        }
        return false;
    }

    /** @return все локали выражения - с единственным присваиванием (значение не меняется после объявления) */
    private boolean stableLocals(Tree x) {
        if (x.op == Op.LOCAL_GET && (x.imm >= single.length || single[x.imm] == null)) {
            return false;
        }
        for (Tree k : x.kids) {
            if (!stableLocals(k)) {
                return false;
            }
        }
        return true;
    }

    private static void localsOf(Tree x, List<Integer> out) {
        if (x.op == Op.LOCAL_GET && !out.contains(x.imm)) {
            out.add(x.imm);
        }
        for (Tree k : x.kids) {
            localsOf(k, out);
        }
    }

    private static int hash(Tree t) {
        int h = t.op * 31 + t.imm;
        h = h * 31 + Long.hashCode(t.k);
        for (Tree k : t.kids) {
            h = h * 31 + hash(k);
        }
        return h;
    }

    // ---- события блока -----------------------------------------------------------------------

    private static final int E_LOAD = 0;
    private static final int E_STORE = 1;
    private static final int E_WRITE = 2;
    private static final int E_ASSIGN = 3;
    private static final int E_EXIT = 4;

    /**
     * Событие в порядке вычисления.
     *
     * @param kind   {@code E_*}
     * @param stmt   номер стейтмента блока
     * @param node   узел загрузки ({@code E_LOAD})
     * @param access разобранное обращение ({@code E_LOAD}, {@code E_STORE})
     * @param local  локаль ({@code E_ASSIGN})
     * @param pos    номер события в блоке
     */
    private record Event(int kind, int stmt, Tree node, Access access, int local, int pos) {
    }

    /** Сборщик событий одного стейтмента. */
    private final class Walk {
        final List<Event> out;
        int stmt;

        Walk(List<Event> out) {
            this.out = out;
        }

        void add(int kind, Tree node, Access access, int local) {
            out.add(new Event(kind, stmt, node, access, local, out.size()));
        }

        /**
         * @param t     узел
         * @param cond  исполняется ли узел не всегда (ветка, тело цикла, вложенный блок)
         * @param loops глубина вложенных циклов и switch (их break/continue не выходят из блока)
         */
        void walk(Tree t, boolean cond, int loops) {
            switch (t.op) {
                case Op.LOCAL_DECL, Op.LOCAL_SET -> {
                    walk(t.kid(0), cond, loops);
                    add(E_ASSIGN, null, null, t.imm);
                }
                case Op.PRIVATE_ARRAY_DECL, Op.LOCAL_ARRAY_DECL, Op.LOCAL_ADDR -> {
                    kids(t, cond, loops);
                    add(E_ASSIGN, null, null, t.imm);
                }
                case Op.STORE -> {
                    kids(t, cond, loops);
                    Access a = access(t.kid(0));
                    if (a == null) {
                        // адрес не разобран: важны только пространство и тип элемента указателя
                        int[] se = pointee(t.kid(0).type);
                        a = se == null ? null : new Access(-1, se[1], se[0], null);
                    }
                    if (a == null) {
                        add(E_WRITE, null, null, -1);
                    } else if (a.space() == AddressSpace.GLOBAL || a.space() == AddressSpace.CONSTANT) {
                        add(E_STORE, null, a, -1);
                    }
                }
                case Op.LOAD -> {
                    kids(t, cond, loops);
                    if (!cond) {
                        Access a = access(t.kid(0));
                        if (a != null && uses.getOrDefault(t, 0) == 1) {
                            add(E_LOAD, t, a, -1);
                        }
                    }
                }
                case Op.INTRINSIC -> {
                    kids(t, cond, loops);
                    if (Builtins.has(t.imm, Builtins.EFFECT)) {
                        add(E_WRITE, null, null, -1);
                    }
                }
                case Op.CALL -> {
                    kids(t, cond, loops);
                    if (fx.functionHasEffect(t.imm)) {
                        add(E_WRITE, null, null, -1);
                    }
                }
                case Op.BARRIER -> add(E_WRITE, null, null, -1);
                case Op.RETURN -> {
                    kids(t, cond, loops);
                    add(E_EXIT, null, null, -1);
                }
                case Op.BREAK, Op.CONTINUE -> {
                    if (loops == 0) {
                        add(E_EXIT, null, null, -1);
                    }
                }
                case Op.SELECT, Op.IF -> {
                    walk(t.kid(0), cond, loops);
                    for (int i = 1; i < t.n(); i++) {
                        walk(t.kid(i), true, loops);
                    }
                }
                case Op.SWITCH -> {
                    walk(t.kid(0), cond, loops);
                    for (int i = 1; i < t.n(); i++) {
                        walk(t.kid(i), true, loops + 1);
                    }
                }
                case Op.LOOP, Op.DO_WHILE -> {
                    for (Tree k : t.kids) {
                        walk(k, true, loops + 1);
                    }
                }
                case Op.BLOCK -> kids(t, true, loops);
                default -> kids(t, cond, loops);
            }
        }

        private void kids(Tree t, boolean cond, int loops) {
            for (Tree k : t.kids) {
                walk(k, cond, loops);
            }
        }
    }

    /** @return {@code {пространство, тип элемента}} указателя либо {@code null}, если тип - не указатель */
    private int[] pointee(int t) {
        IrModule m = mt.source;
        int[] row = t < m.typeCount() ? new int[]{m.typeKind(t), m.typeA(t), m.typeB(t)}
                : mt.extraTypes.get(t - m.typeCount());
        return row[0] == Types.KIND_PTR ? new int[]{row[1], row[2]} : null;
    }

    // ---- блоки -------------------------------------------------------------------------------

    /** Сначала вложенные блоки (снизу вверх), затем загрузки и записи этого блока. */
    private Tree block(Tree blk) {
        Tree[] st = blk.kids.clone();
        boolean changed = false;
        for (int i = 0; i < st.length; i++) {
            Tree n = nested(st[i]);
            changed |= n != st[i];
            st[i] = n;
        }
        List<Tree> list = loads(st);
        List<Tree> stored = stores(list);
        if (!changed && stored.size() == blk.kids.length) {
            boolean same = true;
            for (int i = 0; i < stored.size(); i++) {
                same &= stored.get(i) == blk.kids[i];
            }
            if (same) {
                return blk;
            }
        }
        return blk.withKids(stored.toArray(Tree.NONE));
    }

    /** Переписывает блоки внутри стейтмента (тела {@code if}, циклов, {@code switch}, вложенные блоки). */
    private Tree nested(Tree t) {
        if (t.op == Op.BLOCK) {
            return block(t);
        }
        if (!t.isStmt()) {
            return t;
        }
        boolean control = t.op == Op.IF || t.op == Op.LOOP || t.op == Op.DO_WHILE || t.op == Op.SWITCH;
        Tree[] kids = null;
        for (int i = 0; i < t.n(); i++) {
            Tree k = t.kid(i);
            Tree n = k;
            if (control && k.isStmt() && k.op != Op.BLOCK) {
                // ветка из одного стейтмента (Simplify разворачивает такие блоки) - блок из него
                Tree wrapped = Tree.of(Op.BLOCK, Types.VOID, k);
                Tree done = block(wrapped);
                n = done == wrapped ? k : done;
            } else if (k.isStmt()) {
                n = nested(k);
            }
            if (n != k) {
                if (kids == null) {
                    kids = t.kids.clone();
                }
                kids[i] = n;
            }
        }
        return kids == null ? t : t.withKids(kids);
    }

    /** Группа обращений с одинаковыми {@code p, S, X}. */
    private static final class Group {
        final Access key;
        final List<Event> loads = new ArrayList<>();

        Group(Access key) {
            this.key = key;
        }
    }

    private List<Tree> loads(Tree[] st) {
        List<Event> events = new ArrayList<>();
        int[] firstEvent = new int[st.length + 1];
        Walk w = new Walk(events);
        for (int i = 0; i < st.length; i++) {
            firstEvent[i] = events.size();
            w.stmt = i;
            w.walk(st[i], false, 0);
        }
        firstEvent[st.length] = events.size();

        List<Group> groups = new ArrayList<>();
        Map<Integer, List<Group>> byHash = new HashMap<>();
        for (Event e : events) {
            if (e.kind() != E_LOAD) {
                continue;
            }
            Access a = e.access();
            int h = (a.param() * 31 + a.at().stride()) * 31 + hash(a.at().x());
            List<Group> bucket = byHash.computeIfAbsent(h, x -> new ArrayList<>());
            Group g = null;
            for (Group c : bucket) {
                if (c.key.param() == a.param() && c.key.at().stride() == a.at().stride()
                        && Tree.same(c.key.at().x(), a.at().x())) {
                    g = c;
                    break;
                }
            }
            if (g == null) {
                g = new Group(a);
                bucket.add(g);
                groups.add(g);
            }
            g.loads.add(e);
        }
        if (groups.isEmpty()) {
            return List.of(st);
        }

        Map<Tree, Tree> replace = new IdentityHashMap<>();
        List<List<Tree>> before = new ArrayList<>();
        for (int i = 0; i < st.length; i++) {
            before.add(null);
        }
        for (Group g : groups) {
            int s = g.key.at().stride();
            boolean[] taken = new boolean[s];
            for (int width : WIDTHS) {
                if (s % width != 0 || width > maxWidth(g.key.elem())) {
                    continue;
                }
                for (int b = 0; b * width < s; b++) {
                    tryBlock(g, width, b, taken, events, firstEvent, st, replace, before);
                }
            }
        }
        if (replace.isEmpty()) {
            return List.of(st);
        }
        List<Tree> out = new ArrayList<>();
        for (int i = 0; i < st.length; i++) {
            if (before.get(i) != null) {
                out.addAll(before.get(i));
            }
            out.add(subst(st[i], replace));
        }
        return out;
    }

    private void tryBlock(Group g, int width, int b, boolean[] taken, List<Event> events, int[] firstEvent, Tree[] st,
                          Map<Tree, Tree> replace, List<List<Tree>> before) {
        int lo = b * width;
        for (int c = lo; c < lo + width; c++) {
            if (taken[c]) {
                return;
            }
        }
        boolean[] present = new boolean[width];
        for (Event e : g.loads) {
            int c = e.access().at().lane();
            if (c >= lo && c < lo + width) {
                present[c - lo] = true;
            }
        }
        for (boolean v : present) {
            if (!v) {
                return;
            }
        }
        // первая загрузка блока задаёт место векторной
        int first = -1;
        for (Event e : g.loads) {
            int c = e.access().at().lane();
            if (c >= lo && c < lo + width) {
                first = first < 0 ? e.pos() : Math.min(first, e.pos());
            }
        }
        if (first < 0) {
            return;
        }
        int stmt = events.get(first).stmt();
        int start = firstEvent[stmt];
        List<Integer> xLocals = new ArrayList<>();
        localsOf(g.key.at().x(), xLocals);
        List<Event> valid = new ArrayList<>();
        boolean[] covered = new boolean[width];
        for (Event e : g.loads) {
            int c = e.access().at().lane();
            if (c < lo || c >= lo + width) {
                continue;
            }
            if (safe(events, start, e.pos(), e.access(), xLocals)) {
                valid.add(e);
                covered[c - lo] = true;
            }
        }
        for (boolean v : covered) {
            if (!v) {
                return;
            }
        }
        Access key = g.key;
        int vec = mt.typeId(Types.KIND_VEC, key.elem(), width);
        int tmp = fn.newLocal(vec, null);
        Tree base = null;
        for (Event e : valid) {
            base = e.node().kid(0).kid(0); // PARAM
            Tree get = Tree.ofImm(Op.LOCAL_GET, vec, tmp);
            replace.put(e.node(), Tree.ofImm(Op.EXTRACT, key.elem(), e.access().at().lane() - lo, get));
        }
        Tree load = Tree.make(Op.INTRINSIC, vec, VLOAD[log2(width) - 1], 0,
                new Tree[]{base, vectorIndex(key.at(), width, b)}, Tree.NO_INTS);
        if (before.get(stmt) == null) {
            before.set(stmt, new ArrayList<>());
        }
        before.get(stmt).add(Tree.ofImm(Op.LOCAL_DECL, Types.VOID, tmp, load));
        for (int c = lo; c < lo + width; c++) {
            taken[c] = true;
        }
    }

    /** @return можно ли прочитать элемент в событии {@code start}, а не {@code pos} */
    private static boolean safe(List<Event> events, int start, int pos, Access a, List<Integer> xLocals) {
        for (int i = start; i < pos; i++) {
            Event e = events.get(i);
            switch (e.kind()) {
                case E_EXIT, E_WRITE -> {
                    return false;
                }
                case E_ASSIGN -> {
                    if (xLocals.contains(e.local())) {
                        return false;
                    }
                }
                case E_STORE -> {
                    Access s = e.access();
                    if (s.elem() != a.elem()) {
                        continue; // массивы Java разного типа не перекрываются
                    }
                    if (s.at() == null) {
                        return false;
                    }
                    boolean otherLane = s.at().stride() == a.at().stride() && Tree.same(s.at().x(), a.at().x())
                            && s.at().lane() != a.at().lane();
                    if (!otherLane) {
                        return false;
                    }
                }
                default -> {
                }
            }
        }
        return true;
    }

    /** @return индекс в векторах ширины {@code width}: {@code (S/W)*X + b} */
    private static Tree vectorIndex(Affine at, int width, int b) {
        Tree x = at.x();
        int scale = at.stride() / width;
        if (scale != 1) {
            x = Tree.of(Op.MUL, Types.I32, x, Tree.constant(Op.CONST_INT, Types.I32, scale));
        }
        if (b != 0) {
            x = Tree.of(Op.ADD, Types.I32, x, Tree.constant(Op.CONST_INT, Types.I32, b));
        }
        return x;
    }

    /**
     * @param elem скаляр
     * @return наибольшая полезная ширина: одна транзакция памяти - 16 байт ({@code ld.global.v4.f32},
     *         {@code v2.f64}); шире - те же транзакции и больше регистров
     */
    private static int maxWidth(int elem) {
        return 16 / (Types.bits(elem) / 8);
    }

    private static int log2(int w) {
        return Integer.numberOfTrailingZeros(w);
    }

    /** Подстановка по тождеству узлов; неизменившиеся поддеревья - те же объекты. */
    private static Tree subst(Tree t, Map<Tree, Tree> replace) {
        Tree r = replace.get(t);
        if (r != null) {
            return r;
        }
        Tree[] kids = null;
        for (int i = 0; i < t.n(); i++) {
            Tree k = t.kid(i);
            Tree n = subst(k, replace);
            if (n != k) {
                if (kids == null) {
                    kids = t.kids.clone();
                }
                kids[i] = n;
            }
        }
        return kids == null ? t : t.withKids(kids);
    }

    // ---- записи ------------------------------------------------------------------------------

    /** Запись {@code p[S*X + c] = v} в {@code __global}-буфер с чистым значением. */
    private record Store(Access access, Tree value) {
    }

    private Store store(Tree t) {
        if (t.op != Op.STORE) {
            return null;
        }
        Access a = access(t.kid(0));
        Tree v = t.kid(1);
        if (a == null || a.space() != AddressSpace.GLOBAL || fx.hasEffect(v) || fx.readsMemory(v)
                || Effects.mayTrap(v) || hasCall(v)) {
            return null;
        }
        return new Store(a, v);
    }

    private List<Tree> stores(List<Tree> st) {
        List<Tree> out = new ArrayList<>(st.size());
        int i = 0;
        while (i < st.size()) {
            Store first = store(st.get(i));
            if (first == null) {
                out.add(st.get(i));
                i++;
                continue;
            }
            // серия подряд идущих записей с теми же p, S, X и разными полосами
            int s = first.access().at().stride();
            Store[] lanes = new Store[s];
            int[] at = new int[s];
            int j = i;
            while (j < st.size()) {
                Store x = store(st.get(j));
                if (x == null || x.access().param() != first.access().param() || x.access().at().stride() != s
                        || !Tree.same(x.access().at().x(), first.access().at().x())
                        || lanes[x.access().at().lane()] != null) {
                    break;
                }
                lanes[x.access().at().lane()] = x;
                at[x.access().at().lane()] = j;
                j++;
            }
            Tree[] run = st.subList(i, j).toArray(Tree.NONE);
            boolean[] gone = new boolean[run.length];
            Tree[] placed = new Tree[run.length];
            boolean[] done = new boolean[s];
            for (int width : WIDTHS) {
                if (s % width != 0 || width > maxWidth(first.access().elem())) {
                    continue;
                }
                for (int b = 0; b * width < s; b++) {
                    int lo = b * width;
                    boolean full = true;
                    for (int c = lo; c < lo + width; c++) {
                        full &= lanes[c] != null && !done[c];
                    }
                    if (!full) {
                        continue;
                    }
                    Access key = first.access();
                    int vec = mt.typeId(Types.KIND_VEC, key.elem(), width);
                    Tree[] vals = new Tree[width];
                    int firstAt = Integer.MAX_VALUE;
                    Tree base = null;
                    for (int c = lo; c < lo + width; c++) {
                        vals[c - lo] = lanes[c].value();
                        firstAt = Math.min(firstAt, at[c] - i);
                        gone[at[c] - i] = true;
                        done[c] = true;
                        base = run[at[c] - i].kid(0).kid(0);
                    }
                    Tree call = Tree.make(Op.INTRINSIC, Types.VOID, VSTORE[log2(width) - 1], 0,
                            new Tree[]{base, vectorIndex(key.at(), width, b), Tree.of(Op.BUILD, vec, vals)},
                            Tree.NO_INTS);
                    placed[firstAt] = Tree.of(Op.EXPR_STMT, Types.VOID, call);
                }
            }
            for (int k = 0; k < run.length; k++) {
                if (placed[k] != null) {
                    out.add(placed[k]);
                } else if (!gone[k]) {
                    out.add(run[k]);
                }
            }
            i = j;
        }
        return out;
    }
}
