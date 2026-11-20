package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Operands;
import com.sixik.gpu.ir.Types;

import java.util.ArrayList;
import java.util.List;

/**
 * Какие параметры-массивы функций нуждаются в аргументе длины ({@code pN_len}).
 *
 * <p>Длина массива-параметра читается только узлом {@code ARRAY_LEN} и передаётся дальше при вызове. Поэтому
 * device-функции длина нужна, если функция сама читает {@code ARRAY_LEN} этого параметра либо передаёт его в
 * параметр вызываемой функции, которому длина нужна (неподвижная точка по графу вызовов). Остальные длины не
 * передаются: у функций {@code noinline} каждый лишний аргумент - работа при каждом вызове. Ядрам длина нужна всегда
 * (это ABI рантайма).</p>
 */
final class Lengths {

    private Lengths() {
    }

    /**
     * @param m        модуль
     * @param reach    функции, попадающие в программу
     * @param isKernel точки входа программы
     * @return {@code used[f][i]} - параметру {@code i} функции {@code f} нужна длина; {@code null} для непопавших функций
     */
    static boolean[][] used(IrModule m, boolean[] reach, boolean[] isKernel) {
        int n = m.functionCount();
        int nodes = m.nodeCount();
        boolean[][] used = new boolean[n][];
        // рёбра «длина (g, k) нужна -> нужна длина (f, i)»: {g, k, f, i}
        List<int[]> edges = new ArrayList<>();
        List<int[]> work = new ArrayList<>();
        boolean[] seen = new boolean[nodes];
        int[] stack = new int[16];
        for (int f = 0; f < n; f++) {
            if (!reach[f]) {
                continue;
            }
            used[f] = new boolean[Math.max(0, m.fParamCount(f))];
            if (isKernel[f] || m.fKind(f) == IrModule.KERNEL) {
                java.util.Arrays.fill(used[f], true);
            }
            int body = m.fBody(f);
            if (body < 0 || body >= nodes) {
                continue;
            }
            int sp = 0;
            stack[sp++] = body;
            while (sp > 0) {
                int x = stack[--sp];
                if (seen[x]) {
                    continue;
                }
                seen[x] = true;
                int op = m.op(x);
                if (op == Op.ARRAY_LEN) {
                    int p = arrayParam(m, m.a(x));
                    if (p >= 0 && p < used[f].length && !used[f][p]) {
                        used[f][p] = true;
                        work.add(new int[]{f, p});
                    }
                } else if (op == Op.CALL) {
                    int cnt = Operands.count(m, x);
                    for (int k = 0; k < cnt; k++) {
                        int p = arrayParam(m, Operands.get(m, x, k));
                        if (p >= 0) {
                            edges.add(new int[]{m.a(x), k, f, p});
                        }
                    }
                }
                int cnt = Operands.count(m, x);
                for (int k = 0; k < cnt; k++) {
                    int o = Operands.get(m, x, k);
                    if (o >= 0 && o < nodes && !seen[o]) {
                        if (sp == stack.length) {
                            stack = java.util.Arrays.copyOf(stack, sp * 2);
                        }
                        stack[sp++] = o;
                    }
                }
            }
        }
        for (int f = 0; f < n; f++) {
            if (used[f] != null && (isKernel[f] || m.fKind(f) == IrModule.KERNEL)) {
                for (int i = 0; i < used[f].length; i++) {
                    work.add(new int[]{f, i});
                }
            }
        }
        // распространение от вызываемых к вызывающим: рёбер немного, поэтому индекс по (g, k) не нужен
        java.util.Map<Long, List<int[]>> byCallee = new java.util.HashMap<>();
        for (int[] e : edges) {
            byCallee.computeIfAbsent(key(e[0], e[1]), q -> new ArrayList<>()).add(e);
        }
        while (!work.isEmpty()) {
            int[] w = work.remove(work.size() - 1);
            List<int[]> callers = byCallee.get(key(w[0], w[1]));
            if (callers == null) {
                continue;
            }
            for (int[] e : callers) {
                if (used[e[2]] != null && e[3] < used[e[2]].length && !used[e[2]][e[3]]) {
                    used[e[2]][e[3]] = true;
                    work.add(new int[]{e[2], e[3]});
                }
            }
        }
        return used;
    }

    private static long key(int f, int k) {
        return ((long) f << 32) | (k & 0xffffffffL);
    }

    /** @return номер параметра, если узел - параметр-массив, иначе {@code -1} */
    private static int arrayParam(IrModule m, int node) {
        if (node < 0 || node >= m.nodeCount() || m.op(node) != Op.PARAM) {
            return -1;
        }
        return m.typeKind(m.type(node)) == Types.KIND_ARRAY ? m.a(node) : -1;
    }
}
