package com.sixik.gpu.frontend.asm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Размещение объявлений локалей после структурирования (REWORK_ARCHITECTURE.md, 17.10). Исполнитель пишет каждую
 * запись в именованную локаль как {@code local_set}; здесь для каждой переменной находится наименьший список
 * стейтментов, охватывающий все обращения к ней (IR проверяет области видимости, {@code IR-V01}):
 * <ul>
 *   <li>если первое обращение в нём - запись в саму переменную, она становится {@code local_decl(init)};</li>
 *   <li>иначе перед первым обращением ставится {@code local_decl(0)} (правило (7) 5.15; так бывает у локали без
 *   инициализатора, присвоенной в ветках, - пункт (г) закрытого списка 17.14-3).</li>
 * </ul>
 *
 * <p>Наименьший охватывающий список ищется через наименьшего общего предка (LCA) всех обращений в дереве «список -
 * стейтмент - вложенные списки». Если LCA - стейтмент (он сам обращается к переменной или она есть в нескольких его
 * списках), объявление ставится в содержащий его список, иначе - в сам список LCA перед первым обращением.</p>
 *
 * <p>Шаг B7 (FRONTEND_ASM_RUNTIME.md, S9): итеративный обход в прямом порядке вместо обхода дерева на каждую
 * переменную, без рекурсии по глубине вложенности. LCA с очередным обращением - ближайший к прежнему LCA предок на
 * текущем пути обхода; прежний LCA только поднимается, поэтому подъём по переменной суммарно не длиннее глубины.</p>
 */
final class Scopes {

    /** Стейтмент узла либо {@code null} у узла-списка. */
    private final List<St> stmt = new ArrayList<>();
    /** Список узла либо {@code null} у узла-стейтмента. */
    private final List<List<St>> list = new ArrayList<>();
    private final List<Integer> parent = new ArrayList<>();
    /** Позиция стейтмента в его списке (у списка не используется). */
    private final List<Integer> index = new ArrayList<>();
    /** Узел лежит на текущем пути обхода (от корня до обрабатываемого стейтмента). */
    private boolean[] onPath = new boolean[64];

    private Scopes() {
    }

    /** Обращения к переменной: их LCA и первое в прямом порядке. */
    private static final class Acc {
        int lca = -1;
        int first = -1;
    }

    /**
     * @param root тело функции (изменяется на месте)
     */
    static void place(List<St> root) {
        Map<Var, Acc> vars = new LinkedHashMap<>();
        forEach(root, s -> {
            if (s.kind == St.SET && !s.var.temp() && s.var.paramIndex < 0) {
                vars.computeIfAbsent(s.var, z -> new Acc());
            }
        });
        if (!vars.isEmpty()) {
            Scopes sc = new Scopes();
            sc.walk(root, vars);
            sc.place(vars);
        }
    }

    /** Итеративный обход всех стейтментов в прямом порядке. */
    private static void forEach(List<St> root, java.util.function.Consumer<St> f) {
        ArrayDeque<java.util.Iterator<St>> stack = new ArrayDeque<>();
        stack.push(root.iterator());
        while (!stack.isEmpty()) {
            java.util.Iterator<St> it = stack.peek();
            if (!it.hasNext()) {
                stack.pop();
                continue;
            }
            St s = it.next();
            f.accept(s);
            List<List<St>> kids = s.children();
            for (int k = kids.size() - 1; k >= 0; k--) {
                stack.push(kids.get(k).iterator());
            }
        }
    }

    private int node(St s, List<St> l, int par, int at) {
        int id = stmt.size();
        stmt.add(s);
        list.add(l);
        parent.add(par);
        index.add(at);
        if (id >= onPath.length) {
            onPath = Arrays.copyOf(onPath, onPath.length * 2);
        }
        return id;
    }

    /** Кадр обхода: узел-список и следующий индекс либо (при {@code next < 0}) конец списков стейтмента. */
    private record Frame(int node, int[] next) {
    }

    private void walk(List<St> root, Map<Var, Acc> vars) {
        ArrayDeque<Frame> stack = new ArrayDeque<>();
        int r = node(null, root, -1, -1);
        onPath[r] = true;
        stack.push(new Frame(r, new int[]{0}));
        Set<Var> touched = new HashSet<>();
        while (!stack.isEmpty()) {
            Frame top = stack.peek();
            if (top.next()[0] < 0) {
                // все списки стейтмента обойдены
                stack.pop();
                onPath[top.node()] = false;
                continue;
            }
            List<St> l = list.get(top.node());
            if (top.next()[0] >= l.size()) {
                stack.pop();
                onPath[top.node()] = false;
                continue;
            }
            int at = top.next()[0]++;
            St s = l.get(at);
            int sn = node(s, null, top.node(), at);
            onPath[sn] = true;
            touched.clear();
            if (s.kind == St.SET || s.kind == St.DECL) {
                touched.add(s.var);
            }
            if (s.a != null) {
                s.a.collectReads(touched);
            }
            if (s.b != null) {
                s.b.collectReads(touched);
            }
            if (s.c != null) {
                s.c.collectReads(touched);
            }
            for (Var v : touched) {
                Acc acc = vars.get(v);
                if (acc != null) {
                    touch(acc, sn);
                }
            }
            List<List<St>> kids = s.children();
            if (kids.isEmpty()) {
                onPath[sn] = false;
                continue;
            }
            // стейтмент остаётся на пути, пока обходятся его списки; первым обходится первый список
            stack.push(new Frame(sn, new int[]{-1}));
            for (int k = kids.size() - 1; k >= 0; k--) {
                int kn = node(null, kids.get(k), sn, k);
                onPath[kn] = true;
                stack.push(new Frame(kn, new int[]{0}));
            }
        }
    }

    private void touch(Acc acc, int n) {
        if (acc.lca < 0) {
            acc.lca = n;
            acc.first = n;
            return;
        }
        int a = acc.lca;
        while (!onPath[a]) {
            a = parent.get(a);
        }
        acc.lca = a;
    }

    private void place(Map<Var, Acc> vars) {
        Map<List<St>, TreeMap<Integer, List<St>>> inserts = new IdentityHashMap<>();
        List<List<St>> order = new ArrayList<>();
        int zeroGroup = -1;
        for (Map.Entry<Var, Acc> e : vars.entrySet()) {
            Var v = e.getKey();
            Acc acc = e.getValue();
            if (acc.lca < 0) {
                continue;
            }
            int target;
            int first;
            if (stmt.get(acc.lca) != null) {
                target = parent.get(acc.lca);
                first = index.get(acc.lca);
            } else {
                target = acc.lca;
                int c = acc.first;
                while (parent.get(c) != target) {
                    c = parent.get(c);
                }
                first = index.get(c);
            }
            List<St> l = list.get(target);
            St s = l.get(first);
            if (s.kind == St.SET && s.var == v && !s.a.reads(v)) {
                St d = new St(St.DECL, v, s.a, null, null, 0, false, s.line, s.group);
                d.lvt = s.lvt;
                l.set(first, d);
            } else {
                // группа только отличает объявление от соседей (Emitter.declareGroup): порядок номеров не важен
                St d = new St(St.DECL, v, MethodLowerer.zeroOf(v), null, null, 0, false, s.line, zeroGroup--);
                TreeMap<Integer, List<St>> at = inserts.get(l);
                if (at == null) {
                    at = new TreeMap<>();
                    inserts.put(l, at);
                    order.add(l);
                }
                at.computeIfAbsent(first, z -> new ArrayList<>()).add(d);
            }
        }
        for (List<St> l : order) {
            for (Map.Entry<Integer, List<St>> e : inserts.get(l).descendingMap().entrySet()) {
                l.addAll(e.getKey(), e.getValue());
            }
        }
    }
}
