package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.function.UnaryOperator;

/**
 * Удаление неиспользуемых параметров device-функций.
 *
 * <p>Параметр мёртв, если функция его не читает, кроме передачи в такой же мёртвый параметр другой функции
 * ({@code f8(a, b) { return f7(a); }}, где {@code f7} не читает свой единственный аргумент). Анализ оптимистичный:
 * сначала все параметры считаются мёртвыми, затем живыми становятся прочитанные и переданные в живые параметры,
 * до неподвижной точки. Так снимаются и цепочки переходников, и циклы вызовов.</p>
 *
 * <p>Из сигнатуры убираются мёртвые параметры, из каждого вызова - соответствующие аргументы. Аргумент с побочным
 * эффектом выбросить нельзя: такой параметр остаётся живым. Не трогаются ядра (их сигнатура - ABI рантайма),
 * функции с fallback и сами fallback-функции (сигнатуры пары должны совпадать).</p>
 *
 * <p>Особенно важно для {@code @DeviceFunction(inline = NEVER)}: драйвер не встраивает такие функции и передаёт каждый
 * аргумент (для массива - ещё и длину) при каждом вызове.</p>
 */
final class DeadParams {

    private DeadParams() {
    }

    /**
     * @param mt поднятый модуль (функции меняются на месте)
     * @param fx эффекты функций
     * @return {@code true}, если удалён хотя бы один параметр
     */
    static boolean run(Codec.ModuleTree mt, Effects fx) {
        int n = mt.fns.length;
        boolean[] fallbackTarget = new boolean[n];
        for (FnTree fn : mt.fns) {
            if (fn.alive && fn.fallback >= 0 && fn.fallback < n) {
                fallbackTarget[fn.fallback] = true;
            }
        }
        // dead[f] == null - функцию не трогаем; иначе dead[f][i] - параметр i пока считается мёртвым
        boolean[][] dead = new boolean[n][];
        boolean any = false;
        for (int f = 0; f < n; f++) {
            FnTree fn = mt.fns[f];
            if (fn.alive && fn.kind != IrModule.KERNEL && fn.fallback < 0 && !fallbackTarget[f]
                    && fn.params.length > 0) {
                dead[f] = new boolean[fn.params.length];
                Arrays.fill(dead[f], true);
                any = true;
            }
        }
        if (!any) {
            return false;
        }
        boolean changed = true;
        while (changed) {
            Deadline.check();
            changed = false;
            for (int f = 0; f < n; f++) {
                if (mt.fns[f].alive) {
                    changed |= markUses(mt.fns[f].body, dead[f], dead, fx);
                }
            }
        }
        // новые номера параметров
        int[][] remap = new int[n][];
        any = false;
        for (int f = 0; f < n; f++) {
            if (dead[f] == null) {
                continue;
            }
            int[] map = new int[dead[f].length];
            int next = 0;
            for (int i = 0; i < map.length; i++) {
                map[i] = dead[f][i] ? -1 : next++;
            }
            if (next < map.length) {
                remap[f] = map;
                any = true;
            }
        }
        if (!any) {
            return false;
        }
        for (int f = 0; f < n; f++) {
            FnTree fn = mt.fns[f];
            if (!fn.alive) {
                continue;
            }
            int[] own = remap[f];
            fn.body = rewrite(fn.body, t -> {
                if (t.op == Op.PARAM && own != null && t.imm >= 0 && t.imm < own.length && own[t.imm] >= 0
                        && own[t.imm] != t.imm) {
                    return t.withImm(own[t.imm]);
                }
                if (t.op == Op.CALL && t.imm >= 0 && t.imm < n && remap[t.imm] != null) {
                    int[] map = remap[t.imm];
                    Tree[] kept = new Tree[t.n()];
                    int c = 0;
                    for (int k = 0; k < t.n(); k++) {
                        if (k >= map.length || map[k] >= 0) {
                            kept[c++] = t.kid(k);
                        }
                    }
                    return t.withKids(Arrays.copyOf(kept, c));
                }
                return t;
            });
            if (own != null) {
                int keep = 0;
                for (int i = 0; i < own.length; i++) {
                    if (own[i] >= 0) {
                        keep++;
                    }
                }
                int[] params = new int[keep];
                String[] names = fn.paramNames == null ? null : new String[keep];
                for (int i = 0; i < own.length; i++) {
                    if (own[i] >= 0) {
                        params[own[i]] = fn.params[i];
                        if (names != null && i < fn.paramNames.length) {
                            names[own[i]] = fn.paramNames[i];
                        }
                    }
                }
                fn.params = params;
                fn.paramNames = names;
            }
        }
        return true;
    }

    /**
     * Отмечает живыми параметры, которые тело читает. Аргумент в ещё мёртвый параметр вызываемой функции не обходится
     * (его чтения исчезнут вместе с ним), если в нём нет эффекта; аргумент с эффектом оживляет параметр вызываемой.
     *
     * @param body тело функции
     * @param own  мёртвые параметры самой функции либо {@code null}
     * @param dead мёртвые параметры всех функций
     * @param fx   эффекты
     * @return {@code true}, если хотя бы один параметр стал живым
     */
    private static boolean markUses(Tree body, boolean[] own, boolean[][] dead, Effects fx) {
        boolean changed = false;
        ArrayDeque<Tree> stack = new ArrayDeque<>();
        stack.push(body);
        while (!stack.isEmpty()) {
            Tree t = stack.pop();
            if (t.op == Op.PARAM) {
                if (own != null && t.imm >= 0 && t.imm < own.length && own[t.imm]) {
                    own[t.imm] = false;
                    changed = true;
                }
                continue;
            }
            boolean[] callee = t.op == Op.CALL && t.imm >= 0 && t.imm < dead.length ? dead[t.imm] : null;
            for (int k = 0; k < t.n(); k++) {
                Tree kid = t.kid(k);
                if (callee != null && k < callee.length && callee[k]) {
                    if (!fx.hasEffect(kid)) {
                        continue;
                    }
                    callee[k] = false;
                    changed = true;
                }
                stack.push(kid);
            }
        }
        return changed;
    }

    /**
     * Перестраивает дерево снизу вверх без рекурсии: {@code post} получает узел с уже перестроенными операндами.
     * Неизменившиеся поддеревья остаются теми же объектами.
     */
    private static Tree rewrite(Tree root, UnaryOperator<Tree> post) {
        ArrayDeque<Tree> nodes = new ArrayDeque<>();
        ArrayDeque<int[]> next = new ArrayDeque<>();
        ArrayDeque<Tree[]> built = new ArrayDeque<>();
        nodes.push(root);
        next.push(new int[]{0});
        built.push(root.kids.length == 0 ? Tree.NONE : new Tree[root.kids.length]);
        Tree result = null;
        while (!nodes.isEmpty()) {
            Tree t = nodes.peek();
            int[] i = next.peek();
            if (i[0] < t.n()) {
                Tree kid = t.kid(i[0]++);
                nodes.push(kid);
                next.push(new int[]{0});
                built.push(kid.kids.length == 0 ? Tree.NONE : new Tree[kid.kids.length]);
                continue;
            }
            nodes.pop();
            next.pop();
            Tree[] kids = built.pop();
            Tree r = post.apply(kids.length == 0 ? t : t.withKids(kids));
            if (nodes.isEmpty()) {
                result = r;
            } else {
                built.peek()[next.peek()[0] - 1] = r;
            }
        }
        return result;
    }
}
