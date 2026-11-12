package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.IrModule;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Разрешения вещественной арифметики, действующие в каждой функции модуля.
 *
 * <p>Маска {@code fp} задаётся у ядра ({@code @Kernel(compilationProperties = "fp=...")}). Device-функция своей маски
 * не имеет: её тело общее для всех вызывающих ядер, поэтому в ней действует <b>пересечение</b> масок всех ядер, из
 * которых она достижима (транзитивно). Так функция, которую вызывают строгое и «быстрое» ядро, остаётся строгой.
 * Недостижимая функция строгая. Маска device-функции в IR (например, у копии ядра-производителя после слияния) не
 * учитывается: слияние ядер само записывает в итоговое ядро пересечение масок.</p>
 */
final class FpModes {

    private FpModes() {
    }

    /**
     * @param mt модуль
     * @return маска {@code IrModule.FP_*} для каждой функции (по индексу)
     */
    static int[] effective(Codec.ModuleTree mt) {
        int n = mt.fns.length;
        int[] mask = new int[n];
        boolean[] reached = new boolean[n];
        Arrays.fill(mask, IrModule.FP_ALL);
        for (int k = 0; k < n; k++) {
            FnTree kernel = mt.fns[k];
            if (kernel.kind != IrModule.KERNEL) {
                continue;
            }
            int km = kernel.fp & IrModule.FP_ALL;
            // ядро - корень своего обхода; его маска - его собственная, даже если его вызывает другое ядро
            boolean[] seen = new boolean[n];
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            seen[k] = true;
            queue.add(k);
            while (!queue.isEmpty()) {
                int f = queue.poll();
                Codec.collectCallees(mt.fns[f].body, callee -> {
                    if (callee >= 0 && callee < n && !seen[callee]) {
                        seen[callee] = true;
                        queue.add(callee);
                    }
                });
            }
            for (int f = 0; f < n; f++) {
                if (seen[f] && f != k && mt.fns[f].kind != IrModule.KERNEL) {
                    mask[f] &= km;
                    reached[f] = true;
                }
            }
        }
        for (int f = 0; f < n; f++) {
            FnTree fn = mt.fns[f];
            if (fn.kind == IrModule.KERNEL) {
                mask[f] = fn.fp & IrModule.FP_ALL;
            } else if (!reached[f]) {
                mask[f] = 0;
            }
        }
        return mask;
    }
}
