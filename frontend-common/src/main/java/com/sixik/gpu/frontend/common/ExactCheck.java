package com.sixik.gpu.frontend.common;

import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Operands;
import com.sixik.gpu.ir.Types;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Проверка ядер {@code exact} (FRONTEND_ASM_RUNTIME.md, 18.2-S6, п. 1): из ядра достижимы только побитово точные
 * операции. Общая для обоих фронтендов и работает над построенным модулем IR, поэтому функция, общая для строгого и
 * {@code exact} ядра, проверяется по каждому ядру отдельно.
 *
 * <p>Неточная операция - вызов builtin с допуском больше 0 ULP для типа результата ({@code sin}, {@code cos}, ...).
 * Исключение - {@code sqrt(float)}: как и {@code float}-деление, он точен на устройстве с
 * {@code CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT}, и такое ядро валидатор помечает требованием
 * {@code Features.EXACT_DIVIDE_SQRT} (на устройстве без него - {@code RT-D02}). Флаги {@code fp} рядом с {@code exact}
 * отвергает уже разбор свойств ({@link KernelAttributes}).</p>
 */
public final class ExactCheck {

    private ExactCheck() {
    }

    /**
     * Неточная операция, достижимая из ядра {@code exact}: аргументы {@code GPU0310} после имени ядра.
     *
     * @param kernel    индекс ядра в модуле
     * @param operation имя операции ({@code sin}, ...)
     * @param reason    почему она неточна ({@code "tolerance 4 ULP"})
     */
    public record Problem(int kernel, String operation, String reason) {
    }

    /**
     * Проверяет все ядра модуля с {@link IrModule#FP_EXACT}.
     *
     * @param m модуль
     * @return неточные операции по ядрам, каждая операция ядра - один раз, в порядке обхода
     */
    public static List<Problem> check(IrModule m) {
        List<Problem> out = new ArrayList<>();
        for (int k = 0; k < m.functionCount(); k++) {
            if (m.fKind(k) != IrModule.KERNEL || (m.fFp(k) & IrModule.FP_EXACT) == 0) {
                continue;
            }
            Set<String> seen = new LinkedHashSet<>();
            boolean[] visited = new boolean[m.functionCount()];
            List<Integer> queue = new ArrayList<>(List.of(k));
            visited[k] = true;
            for (int qi = 0; qi < queue.size(); qi++) {
                int f = queue.get(qi);
                if (m.fBodyKind(f) != IrModule.BODY_IR) {
                    continue;
                }
                for (int n : nodes(m, m.fBody(f))) {
                    int op = m.op(n);
                    if (op == Op.CALL && !visited[m.a(n)]) {
                        visited[m.a(n)] = true;
                        queue.add(m.a(n));
                    } else if (op == Op.INTRINSIC) {
                        String reason = inexact(m, n);
                        String name = Builtins.name(m.a(n));
                        if (reason != null && seen.add(name + "/" + reason)) {
                            out.add(new Problem(k, name, reason));
                        }
                    }
                }
            }
        }
        return out;
    }

    /** @return почему builtin узла {@code n} неточен, либо {@code null} */
    private static String inexact(IrModule m, int n) {
        int id = m.a(n);
        int scalar = Types.scalarOf(m, m.type(n));
        if (scalar != Types.F32 && scalar != Types.F64) {
            return null;
        }
        int ulp = scalar == Types.F32 ? Builtins.ulp(id) : Builtins.ulp64(id);
        if (ulp <= 0 || scalar == Types.F32 && Builtins.name(id).equals("sqrt")) {
            return null;
        }
        return "tolerance " + ulp + " ULP";
    }

    /** Узлы дерева функции в порядке обхода в глубину (общие узлы - один раз). */
    private static List<Integer> nodes(IrModule m, int root) {
        List<Integer> out = new ArrayList<>();
        boolean[] seen = new boolean[m.nodeCount()];
        int[] stack = new int[16];
        int sp = 0;
        stack[sp++] = root;
        seen[root] = true;
        while (sp > 0) {
            int n = stack[--sp];
            out.add(n);
            for (int k = Operands.count(m, n) - 1; k >= 0; k--) {
                int c = Operands.get(m, n, k);
                if (!seen[c]) {
                    seen[c] = true;
                    if (sp == stack.length) {
                        stack = java.util.Arrays.copyOf(stack, sp * 2);
                    }
                    stack[sp++] = c;
                }
            }
        }
        return out;
    }
}
