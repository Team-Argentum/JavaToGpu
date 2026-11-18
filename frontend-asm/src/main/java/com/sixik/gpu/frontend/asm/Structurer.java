package com.sixik.gpu.frontend.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Восстановление структурного потока (REWORK_ARCHITECTURE.md, 17.9) вместе с символьным исполнением блоков.
 *
 * <p>javac раскладывает структурный код вложенными интервалами адресов, поэтому регион - полуинтервал блоков
 * {@code [from, to)}, а конструкция определяется по первому блоку: заголовок цикла (в него ведёт обратное ребро),
 * условный переход (цепочка условий → {@code if}, {@code if/else} или «ромб» {@code ?:}), {@code switch}.
 * {@code follow} - куда попадает управление после конца региона: переход туда из последнего блока - просто конец
 * региона (javac сокращает цепочки {@code goto}: конец ветки {@code then} может прыгать сразу на заголовок цикла или
 * за внешний {@code if}).</p>
 *
 * <p>Переходы на выход ближайшего цикла/{@code switch} - {@code break}, на точку продолжения ближайшего цикла -
 * {@code continue}, на цели внешних - {@code GPU0104} (метки).</p>
 */
final class Structurer {

    private static final int NONE = 0;
    private static final int BREAK = 1;
    private static final int CONTINUE = 2;
    private static final int LABELED = 3;

    /** Контекст переходов: ближайший цикл или {@code switch}. */
    private record Ctx(Ctx outer, boolean loop, Set<Integer> breaks, int cont) {
    }

    /** Узел цепочки условий: переход в {@code t} при {@code cond}, иначе в {@code f}. */
    private record Node(int block, Ex cond, int t, int f, List<St> pre) {
    }

    /**
     * Свёрнутая цепочка условий.
     *
     * @param last     последний блок цепочки ({@code last + 1} - сторона «then»)
     * @param condThen условие перехода на {@code last + 1}
     * @param other    другой выход
     * @param condElse условие перехода на {@code other}
     * @param pre      стейтменты, которые свёртка условий с эффектами добавила перед условием
     */
    private record Chain(int last, Ex condThen, int other, Ex condElse, List<St> pre) {
    }

    private final MethodLowerer x;
    private final Cfg g;
    private final Map<Integer, Integer> updates;
    private final Set<Integer> activeLoops = new HashSet<>();

    /**
     * @param x       символьный исполнитель метода
     * @param g       граф
     * @param updates заголовок цикла → первый блок шага {@code for} (17.9-4)
     */
    Structurer(MethodLowerer x, Cfg g, Map<Integer, Integer> updates) {
        this.x = x;
        this.g = g;
        this.updates = updates;
    }

    /** @return стейтменты тела метода */
    List<St> run() {
        return region(0, g.size(), g.size(), null);
    }

    // =============================================================================================
    // Регионы
    // =============================================================================================

    private List<St> region(int from, int to, int follow, Ctx ctx) {
        List<St> mine = new ArrayList<>();
        List<St> saved = x.swapOut(mine);
        try {
            int i = from;
            while (i < to) {
                i = statement(i, to, follow, ctx);
            }
        } finally {
            x.swapOut(saved);
        }
        return mine;
    }

    private static int resolve(int t, int to, int follow) {
        return t == to ? follow : t;
    }

    /** Индекс конца тела блока: завершающий переход/{@code switch} исполняет структуризатор. */
    private int bodyEnd(Cfg.Block b) {
        if (b.term == Cfg.GOTO || b.term == Cfg.COND || b.term == Cfg.SWITCH) {
            return g.insns.indexOf(b.last);
        }
        return b.end;
    }

    private boolean isHeader(int i) {
        for (int p : g.get(i).preds) {
            if (p >= i) {
                return true;
            }
        }
        return false;
    }

    private int statement(int i, int to, int follow, Ctx ctx) {
        Cfg.Block b = g.get(i);
        if (!g.reachable(i)) {
            return i + 1;
        }
        if (isHeader(i) && !activeLoops.contains(i)) {
            return loop(i, to, follow, ctx);
        }
        int at = x.outSize();
        x.run(b.start, bodyEnd(b));
        int g0 = x.group();
        switch (b.term) {
            case Cfg.GOTO -> {
                jump(b.target, i, to, follow, ctx);
                return i + 1;
            }
            case Cfg.COND -> {
                return conditional(i, to, follow, ctx, at, g0);
            }
            case Cfg.SWITCH -> {
                return switchStmt(i, to, follow, ctx);
            }
            default -> {
                return i + 1;
            }
        }
    }

    private int classify(int t, Ctx ctx) {
        if (ctx != null && ctx.breaks().contains(t)) {
            return BREAK;
        }
        for (Ctx c = ctx; c != null; c = c.outer()) {
            if (c.loop()) {
                if (c.cont() == t) {
                    return CONTINUE;
                }
                break;
            }
        }
        for (Ctx c = ctx; c != null; c = c.outer()) {
            if (c.breaks().contains(t) || c.loop() && c.cont() == t) {
                return LABELED;
            }
        }
        return NONE;
    }

    /** Безусловный переход: конец региона, {@code break} или {@code continue}. */
    private void jump(int t, int i, int to, int follow, Ctx ctx) {
        if (i == to - 1 && t == follow) {
            return; // переход туда же, куда ведёт конец региона
        }
        switch (classify(t, ctx)) {
            case BREAK -> x.emit(St.of(St.BREAK, x.line(), x.group()));
            case CONTINUE -> x.emit(St.of(St.CONTINUE, x.line(), x.group()));
            case LABELED -> x.fail("GPU0104");
            default -> x.fail("GPU0304", x.name());
        }
    }

    // =============================================================================================
    // Условия, if, ?:
    // =============================================================================================

    /**
     * @param at начало стейтментов блока {@code i} в текущем списке
     * @param g0 номер исходного стейтмента, которым кончается тело блока (начало выражения условия)
     */
    private int conditional(int i, int to, int follow, Ctx ctx, int at, int g0) {
        Chain c = chain(i, to, follow, -1);
        x.emitAll(c.pre());
        int t = c.last() + 1;
        int e = c.other();
        if (e == follow || e == to) {
            // ветка до конца региона: переход - на продолжение региона (в том числе на заголовок цикла)
            return ifRegion(c.condThen(), t, to, false, to, to, follow, ctx, at, g0);
        }
        if (e < t || e > to) {
            int k = classify(e, ctx);
            if (k == BREAK || k == CONTINUE) {
                St s = St.withCond(St.IF, c.condElse(), x.line(), x.group());
                s.body = new ArrayList<>(List.of(St.of(k == BREAK ? St.BREAK : St.CONTINUE, x.line(), x.group())));
                x.emit(s);
                return t;
            }
            x.fail(k == LABELED ? "GPU0104" : "GPU0304", x.name());
            return t;
        }
        // e внутри региона: then = [t, e); else-ветка есть, если then кончается переходом за e
        Cfg.Block lastThen = g.get(e - 1);
        boolean elseSkip = false;
        int join = e;
        if (e - 1 >= t && lastThen.term == Cfg.GOTO) {
            int y = lastThen.target;
            boolean lone = e - 1 == t && onlyJump(lastThen);
            int k = classify(y, ctx);
            // then из одного перехода на цель break/continue - это сам break/continue, а не обход else
            if (y > e && (y <= to || y == follow) && !(lone && (k == BREAK || k == CONTINUE))) {
                elseSkip = true;
                join = y <= to ? y : to;
            }
        }
        return ifRegion(c.condThen(), t, e, elseSkip, join, to, follow, ctx, at, g0);
    }

    /** {@code true}, если в блоке нет ничего, кроме завершающего перехода. */
    private boolean onlyJump(Cfg.Block b) {
        for (int k = b.start; k < b.end; k++) {
            AbstractInsnNode in = g.insns.get(k);
            if (in.getOpcode() >= 0 && in != b.last) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code if}/{@code if-else} или «ромб» {@code ?:}: then = {@code [t, thenEnd)}, else = {@code [thenEnd, join)}.
     *
     * @param to конец объемлющего региона
     */
    private int ifRegion(Ex cond, int t, int thenEnd, boolean hasElse, int join, int to, int follow, Ctx ctx,
                         int at, int g0) {
        int joinFollow = resolve(join, to, follow);
        int elseStart = thenEnd;
        MethodLowerer.Snap before = x.snap();
        for (int attempt = 0; attempt < 2; attempt++) {
            int h0 = x.stackSize();
            List<MethodLowerer.Slot> base = x.stackCopy();
            List<St> th = region(t, thenEnd, joinFollow, ctx);
            int h1 = x.stackSize();
            if (h1 == h0) {
                List<St> el = null;
                if (hasElse) {
                    x.setStack(base);
                    el = region(elseStart, join, joinFollow, ctx);
                    if (x.stackSize() != h0) {
                        x.fail("GPU0305", x.name());
                    }
                }
                St s = St.withCond(St.IF, cond, x.line(), x.group());
                s.body = th;
                s.alt = el;
                x.emit(s);
                return join;
            }
            if (h1 != h0 + 1 || !hasElse) {
                x.fail("GPU0305", x.name());
            }
            Ex a = x.pop();
            x.setStack(base);
            List<St> el = region(elseStart, join, joinFollow, ctx);
            if (x.stackSize() != h0 + 1) {
                x.fail("GPU0305", x.name());
            }
            Ex b = x.pop();
            if (th.isEmpty() && el.isEmpty()) {
                x.push(x.diamond(cond, a, b));
                x.regroup(at, g0);
                return join;
            }
            if (attempt == 0 && x.hasOpenPending()) {
                // в плечах есть эффекты: ожидающие на стеке значения сохраняются раньше них (правило эффектов)
                x.restore(before);
                x.spillPending();
                before = x.snap();
                continue;
            }
            Ex d = x.diamond(cond, a, b);
            Var tmp = MethodLowerer.temp(d);
            x.emitRaw(new St(St.DECL, tmp, MethodLowerer.zeroOf(tmp), null, null, 0, false, x.line(), x.group()));
            th.add(new St(St.SET, tmp, d.kids[1], null, null, 0, false, x.line(), x.group()));
            el.add(new St(St.SET, tmp, d.kids[2], null, null, 0, false, x.line(), x.group()));
            St s = St.withCond(St.IF, cond, x.line(), x.group());
            s.body = th;
            s.alt = el;
            x.emitRaw(s);
            x.push(Ex.localGet(tmp));
            x.regroup(at, g0);
            return join;
        }
        throw new IllegalStateException("unreachable");
    }

    /**
     * Цепочка условий от блока {@code i} (его тело уже исполнено, условный переход - нет) и её свёртка
     * (17.9-1). Следующий блок входит в цепочку, если это условие, все предшественники которого в цепочке, и либо
     * без него цепочка не сворачивается (переход за единственный выход - {@code a || b}), либо он чистый и на той же
     * строке ({@code a && b}; вложенный {@code if} на новой строке остаётся вложенным).
     *
     * @param forceTo блоки до этого включительно входят в цепочку обязательно (условие {@code do-while})
     */
    private Chain chain(int i, int to, int follow, int forceTo) {
        Cfg.Block b0 = g.get(i);
        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node(i, x.jumpCond((JumpInsnNode) b0.last), b0.target, i + 1, null));
        int line = x.lineOf(b0.last);
        int k = i;
        // состояние исполнителя до каждого втянутого блока: откат цепочки, которая не сворачивается
        List<MethodLowerer.Snap> snaps = new ArrayList<>();
        while (k + 1 < to) {
            int y = k + 1;
            Cfg.Block yb = g.get(y);
            if (yb.term != Cfg.COND || isHeader(y) || !predsWithin(yb, i, k)) {
                break;
            }
            Set<Integer> exits = exits(nodes, i, k);
            exits.remove(y);
            boolean crossing = exits.size() >= 2
                    || exits.size() == 1 && (yb.target > exits.iterator().next() && yb.target != follow);
            MethodLowerer.Snap s = x.snap();
            List<MethodLowerer.Slot> base = x.stackCopy();
            List<St> pre = new ArrayList<>();
            List<St> saved = x.swapOut(pre);
            Ex cy;
            try {
                x.run(yb.start, bodyEnd(yb));
                cy = x.jumpCond((JumpInsnNode) yb.last);
            } finally {
                x.swapOut(saved);
            }
            boolean stackOk = x.sameStack(base);
            boolean pure = pre.isEmpty();
            boolean sameLine = x.lineOf(firstOpcode(yb)) == line;
            // `a && b` на одной строке: второе условие ведёт на уже существующий выход цепочки. Вложенный if/?: на той
            // же строке вводит новый выход (свою ветку else) - это не часть условия
            boolean sharesExit = exits.contains(yb.target);
            if (!stackOk || !(y <= forceTo || crossing || sameLine && sharesExit)) {
                x.restore(s);
                break;
            }
            nodes.add(new Node(y, cy, yb.target, y + 1, pure ? null : pre));
            snaps.add(s);
            k = y;
        }
        int t = k + 1;
        Node m = merge(nodes, t);
        // Втягивание по строке жадное: условный блок на той же строке может оказаться вложенным if
        // (`if (a) { x = ...; if (b) ... } else ...` в одну строку, позиции-узлы генератора, 18.2-S7). Цепочка, которая
        // не сворачивается в одно условие с выходом на «then», укорачивается: остаток - обычный вложенный регион.
        while (nodes.size() > 1 && (m == null || m.t() != t && m.f() != t)) {
            x.restore(snaps.remove(snaps.size() - 1));
            nodes.remove(nodes.size() - 1);
            k = nodes.get(nodes.size() - 1).block();
            t = k + 1;
            m = merge(nodes, t);
        }
        if (m == null) {
            x.fail("GPU0304", x.name());
        }
        List<St> pre = m.pre() == null ? List.of() : m.pre();
        if (m.t() == t) {
            return new Chain(k, m.cond(), m.f(), Conds.neg(m.cond()), pre);
        }
        if (m.f() == t) {
            return new Chain(k, Conds.neg(m.cond()), m.t(), m.cond(), pre);
        }
        x.fail("GPU0304", x.name());
        return null;
    }

    private AbstractInsnNode firstOpcode(Cfg.Block b) {
        for (int k = b.start; k < b.end; k++) {
            if (g.insns.get(k).getOpcode() >= 0) {
                return g.insns.get(k);
            }
        }
        return b.last;
    }

    private boolean predsWithin(Cfg.Block b, int from, int to) {
        for (int p : b.preds) {
            if (p < from || p > to) {
                return false;
            }
        }
        return true;
    }

    private static Set<Integer> exits(List<Node> nodes, int from, int to) {
        Set<Integer> r = new HashSet<>();
        for (Node n : nodes) {
            if (n.t() < from || n.t() > to) {
                r.add(n.t());
            }
            if (n.f() < from || n.f() > to) {
                r.add(n.f());
            }
        }
        return r;
    }

    /** Попарная свёртка узлов цепочки до одного (17.9-1); {@code null} - не сворачивается. */
    private Node merge(List<Node> nodes, int thenSide) {
        List<Node> ns = new ArrayList<>(nodes);
        while (ns.size() > 1) {
            boolean progress = false;
            search:
            for (int xi = 0; xi < ns.size(); xi++) {
                Node xn = ns.get(xi);
                for (int yBlock : new int[]{xn.t(), xn.f()}) {
                    Node yn = find(ns, yBlock);
                    if (yn == null || yn == xn || predCount(ns, yBlock) != 1) {
                        continue;
                    }
                    int o = xn.t() == yBlock ? xn.f() : xn.t();
                    if (o != yn.t() && o != yn.f()) {
                        continue;
                    }
                    int w = yn.t() == o ? yn.f() : yn.t();
                    ns.set(xi, combine(xn, yn, o, w, thenSide));
                    ns.remove(yn);
                    progress = true;
                    break search;
                }
            }
            if (!progress) {
                return null;
            }
        }
        return ns.get(0);
    }

    private static Node find(List<Node> ns, int block) {
        for (Node n : ns) {
            if (n.block() == block) {
                return n;
            }
        }
        return null;
    }

    private static int predCount(List<Node> ns, int block) {
        int c = 0;
        for (Node n : ns) {
            if (n.t() == block) {
                c++;
            }
            if (n.f() == block) {
                c++;
            }
        }
        return c;
    }

    /**
     * Свёртка {@code X → Y}: в {@code W} попадают через {@code X → Y → W}, в {@code O} - напрямую из {@code X} или
     * через {@code Y}. Форма выбирается так, чтобы истинная ветка вела на сторону «then» (иначе - на меньший адрес):
     * {@code cx && cy} → {@code W} либо {@code cx' || cy'} → {@code O}. Второе условие с эффектами - временная
     * локаль, как {@code shortCircuit} в {@code frontend-java}: {@code t = l; if (t) { эффекты; t = r; }}.
     */
    private Node combine(Node xn, Node yn, int o, int w, int thenSide) {
        Ex cxY = xn.t() == yn.block() ? xn.cond() : Conds.neg(xn.cond());
        Ex cxO = xn.t() == o ? xn.cond() : Conds.neg(xn.cond());
        Ex cyW = yn.t() == w ? yn.cond() : Conds.neg(yn.cond());
        Ex cyO = yn.t() == o ? yn.cond() : Conds.neg(yn.cond());
        boolean and = thenSide == w || thenSide != o && w < o;
        if (yn.pre() == null) {
            return and ? new Node(xn.block(), Ex.logical(Ex.LAND, cxY, cyW), w, o, xn.pre())
                    : new Node(xn.block(), Ex.logical(Ex.LOR, cxO, cyO), o, w, xn.pre());
        }
        Var tmp = MethodLowerer.temp(Kinds.K_BOOLEAN);
        List<St> pre = xn.pre() == null ? new ArrayList<>() : new ArrayList<>(xn.pre());
        St sc = new St(St.SHORT, tmp, and ? cxY : cxO, and ? cyW : cyO, null, and ? 1 : 0, false, x.line(), x.group());
        sc.body = new ArrayList<>(yn.pre());
        pre.add(sc);
        return and ? new Node(xn.block(), Ex.localGet(tmp), w, o, pre) : new Node(xn.block(), Ex.localGet(tmp), o, w, pre);
    }

    // =============================================================================================
    // Циклы
    // =============================================================================================

    private int loop(int h, int to, int follow, Ctx ctx) {
        if (x.stackSize() != 0) {
            x.fail("GPU0305", x.name());
        }
        int last = -1;
        for (int p : g.get(h).preds) {
            if (p >= h) {
                if (!g.dominates(h, p)) {
                    x.fail("GPU0304", x.name());
                }
                last = Math.max(last, p);
            }
        }
        activeLoops.add(h);
        try {
            Cfg.Block hb = g.get(h);
            Cfg.Block lb = g.get(last);
            if (hb.term == Cfg.COND) {
                Integer r = whileLoop(h, last, to, follow, ctx);
                if (r != null) {
                    return r;
                }
            }
            if (lb.term == Cfg.COND && lb.target == h) {
                return doWhile(h, last, to, follow, ctx);
            }
            if (lb.term == Cfg.GOTO) {
                Ctx inner = new Ctx(ctx, true, breaksOf(last + 1, to, follow), h);
                List<St> body = region(h, last + 1, h, inner);
                St s = St.withCond(St.LOOP, null, x.line(), x.group());
                s.body = body;
                x.emit(s);
                return last + 1;
            }
            x.fail("GPU0304", x.name());
            return last + 1;
        } finally {
            activeLoops.remove(h);
        }
    }

    private static Set<Integer> breaksOf(int exit, int to, int follow) {
        Set<Integer> r = new HashSet<>();
        r.add(exit);
        r.add(resolve(exit, to, follow));
        return r;
    }

    /**
     * {@code while}/{@code for}: заголовок - цепочка условий, второй выход которой ведёт за последний латч
     * (17.9-3); {@code null} - заголовок не такой (состояние исполнителя восстановлено).
     */
    private Integer whileLoop(int h, int last, int to, int follow, Ctx ctx) {
        Cfg.Block hb = g.get(h);
        MethodLowerer.Snap s = x.snap();
        List<St> pre = new ArrayList<>();
        List<St> saved = x.swapOut(pre);
        Chain c;
        try {
            x.run(hb.start, bodyEnd(hb));
            c = chain(h, last + 1, -1, -1);
        } finally {
            x.swapOut(saved);
        }
        int exit = c.other();
        if (exit <= last || c.last() >= last) {
            x.restore(s);
            return null;
        }
        pre.addAll(c.pre());
        int bodyStart = c.last() + 1;
        int loopEnd = exit <= to ? exit : last + 1;
        Integer update = updates.get(h);
        if (update != null && (update <= c.last() || update > last || g.get(last).term != Cfg.GOTO)) {
            update = null;
        }
        int cont = update != null ? update : h;
        Ctx inner = new Ctx(ctx, true, breaksOf(exit, to, follow), cont);
        List<St> body = region(bodyStart, update != null ? update : loopEnd, cont, inner);
        List<St> upd = update != null ? region(update, last + 1, h, inner) : null;

        St loop = St.withCond(St.LOOP, c.condThen(), x.line(), x.group());
        loop.pre = pre.isEmpty() ? null : pre;
        loop.body = body;
        loop.update = upd;
        List<St> init = x.pullForInit(g, exit);
        if (upd != null || !init.isEmpty()) {
            // цикл for: обёртка block { init; loop } (17.9-8), как forStmt в frontend-java
            St wrapper = St.of(St.BLOCK, x.line(), x.group());
            wrapper.body = new ArrayList<>(init);
            wrapper.body.add(loop);
            x.emit(wrapper);
        } else {
            x.emit(loop);
        }
        return loopEnd;
    }

    /** {@code do-while}: последний латч - условный переход на заголовок, условие - цепочка перед ним. */
    private int doWhile(int h, int last, int to, int follow, Ctx ctx) {
        int c0 = last;
        while (c0 - 1 > h && latchCandidate(c0 - 1, c0, h, last)) {
            c0--;
        }
        Ctx inner = new Ctx(ctx, true, breaksOf(last + 1, to, follow), c0);
        List<St> body = region(h, c0, c0, inner);
        Cfg.Block cb = g.get(c0);
        List<St> condPre = new ArrayList<>();
        List<St> saved = x.swapOut(condPre);
        Chain c;
        try {
            x.run(cb.start, bodyEnd(cb));
            c = chain(c0, last + 1, follow, last);
        } finally {
            x.swapOut(saved);
        }
        if (!condPre.isEmpty() || !c.pre().isEmpty()) {
            throw new MethodLowerer.NotYet("do-while with side effects in the condition (step A3)");
        }
        if (c.last() != last || c.other() != h) {
            x.fail("GPU0304", x.name());
        }
        St s = St.withCond(St.DO_WHILE, c.condElse(), x.line(), x.group());
        s.body = body;
        x.emit(s);
        return last + 1;
    }

    /** Блок {@code b} может быть началом условия {@code do-while}: условный переход внутрь условия или на выходы. */
    private boolean latchCandidate(int b, int next, int h, int last) {
        Cfg.Block bb = g.get(b);
        if (bb.term != Cfg.COND || !pureOpcodes(bb)) {
            return false;
        }
        int t = bb.target;
        if (!(t == h || t == last + 1 || t >= next && t <= last)) {
            return false;
        }
        for (int k = next; k <= last; k++) {
            for (int p : g.get(k).preds) {
                if (p < b || p > last) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Блок без эффектов: только вычисление условия. */
    private boolean pureOpcodes(Cfg.Block b) {
        for (int k = b.start; k < b.end; k++) {
            AbstractInsnNode in = g.insns.get(k);
            int op = in.getOpcode();
            if (op < 0 || in == b.last) {
                continue;
            }
            boolean store = op >= Opcodes.ISTORE && op <= Opcodes.SASTORE || op == Opcodes.IINC;
            boolean call = op == Opcodes.INVOKESTATIC && !x.pureCall((MethodInsnNode) in);
            if (store || call || op >= Opcodes.IRETURN && op <= Opcodes.RETURN) {
                return false;
            }
        }
        return true;
    }

    // =============================================================================================
    // switch
    // =============================================================================================

    private int switchStmt(int i, int to, int follow, Ctx ctx) {
        Cfg.Block b = g.get(i);
        Ex key = x.pop();
        if (key.kind != Kinds.K_INT) {
            key = Ex.convert(Kinds.K_INT, key, false);
        }
        if (x.stackSize() != 0) {
            x.fail("GPU0305", x.name());
        }
        TreeMap<Integer, List<Integer>> groups = new TreeMap<>();
        for (int k = 0; k < b.keys.length; k++) {
            groups.computeIfAbsent(b.targets[k], z -> new ArrayList<>()).add(b.keys[k]);
        }
        int exit = Math.max(b.deflt, groups.isEmpty() ? b.deflt : groups.lastKey());
        int first = Math.min(b.deflt, groups.isEmpty() ? b.deflt : groups.firstKey());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int k = first; k < exit; k++) {
                for (int s : g.get(k).succs(g.size())) {
                    if (s > exit && s <= to && classify(s, ctx) == NONE) {
                        exit = s;
                        changed = true;
                    } else if (s == follow && s > to && exit < to && classify(s, ctx) == NONE) {
                        exit = to;
                        changed = true;
                    }
                }
            }
        }
        if (exit > to) {
            x.fail("GPU0304", x.name());
        }
        boolean hasDefault = b.deflt != exit;
        if (hasDefault) {
            groups.computeIfAbsent(b.deflt, z -> new ArrayList<>());
        }
        Ctx sw = new Ctx(ctx, false, breaksOf(exit, to, follow), -1);
        List<List<St>> cases = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        List<Boolean> defaults = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        List<Integer> starts = new ArrayList<>(groups.keySet());
        for (int k = 0; k < starts.size(); k++) {
            int start = starts.get(k);
            int end = k + 1 < starts.size() ? starts.get(k + 1) : exit;
            int caseFollow = k + 1 < starts.size() ? end : resolve(exit, to, follow);
            cases.add(region(start, end, caseFollow, sw));
            List<Integer> keys = groups.get(start);
            keys.sort(null);
            for (int v : keys) {
                values.add(v);
                defaults.add(false);
            }
            int size = keys.size();
            if (hasDefault && b.deflt == start) {
                values.add(0);
                defaults.add(true);
                size++;
            }
            sizes.add(size);
        }
        St s = St.withCond(St.SWITCH, key, x.line(), x.group());
        s.cases = cases;
        s.values = values.stream().mapToInt(Integer::intValue).toArray();
        s.defaults = new boolean[defaults.size()];
        for (int k = 0; k < s.defaults.length; k++) {
            s.defaults[k] = defaults.get(k);
        }
        s.sizes = sizes.stream().mapToInt(Integer::intValue).toArray();
        x.emit(s);
        return exit;
    }
}
