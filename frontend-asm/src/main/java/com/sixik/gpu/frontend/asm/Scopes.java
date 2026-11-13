package com.sixik.gpu.frontend.asm;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
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
 */
final class Scopes {

    private int zeroGroup = -1;

    private Scopes() {
    }

    /**
     * @param root тело функции (изменяется на месте)
     */
    static void place(List<St> root) {
        Set<Var> vars = new LinkedHashSet<>();
        collect(root, vars);
        new Scopes().placeIn(root, vars);
    }

    private static void collect(List<St> list, Set<Var> vars) {
        for (St s : list) {
            if (s.kind == St.SET && !s.var.temp() && s.var.paramIndex < 0) {
                vars.add(s.var);
            }
            for (List<St> k : s.children()) {
                collect(k, vars);
            }
        }
    }

    private void placeIn(List<St> list, Set<Var> vars) {
        Map<List<St>, Set<Var>> delegated = new IdentityHashMap<>();
        TreeMap<Integer, List<St>> inserts = new TreeMap<>();
        for (Var v : vars) {
            int first = -1;
            int count = 0;
            St only = null;
            boolean direct = false;
            for (int i = 0; i < list.size(); i++) {
                St s = list.get(i);
                if (touches(s, v)) {
                    if (first < 0) {
                        first = i;
                    }
                    count++;
                    only = s;
                    direct |= direct(s, v);
                }
            }
            if (first < 0) {
                continue;
            }
            if (count == 1 && !direct) {
                List<St> hit = null;
                int hits = 0;
                for (List<St> k : only.children()) {
                    if (touches(k, v)) {
                        hits++;
                        hit = k;
                    }
                }
                if (hits == 1) {
                    delegated.computeIfAbsent(hit, z -> new LinkedHashSet<>()).add(v);
                    continue;
                }
            }
            St s = list.get(first);
            if (s.kind == St.SET && s.var == v && !s.a.reads(v)) {
                St d = new St(St.DECL, v, s.a, null, null, 0, false, s.line, s.group);
                d.lvt = s.lvt;
                list.set(first, d);
            } else {
                St d = new St(St.DECL, v, MethodLowerer.zeroOf(v), null, null, 0, false, s.line, zeroGroup--);
                inserts.computeIfAbsent(first, z -> new ArrayList<>()).add(d);
            }
        }
        for (Map.Entry<Integer, List<St>> e : inserts.descendingMap().entrySet()) {
            list.addAll(e.getKey(), e.getValue());
        }
        for (Map.Entry<List<St>, Set<Var>> e : delegated.entrySet()) {
            placeIn(e.getKey(), e.getValue());
        }
    }

    private static boolean touches(List<St> list, Var v) {
        for (St s : list) {
            if (touches(s, v)) {
                return true;
            }
        }
        return false;
    }

    private static boolean touches(St s, Var v) {
        if (direct(s, v)) {
            return true;
        }
        for (List<St> k : s.children()) {
            if (touches(k, v)) {
                return true;
            }
        }
        return false;
    }

    /** Обращение к переменной в самом стейтменте (а не во вложенных списках). */
    private static boolean direct(St s, Var v) {
        return (s.kind == St.SET || s.kind == St.DECL) && s.var == v
                || s.a != null && s.a.reads(v) || s.b != null && s.b.reads(v) || s.c != null && s.c.reads(v);
    }
}
