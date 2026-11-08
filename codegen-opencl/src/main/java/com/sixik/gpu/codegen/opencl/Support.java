package com.sixik.gpu.codegen.opencl;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Operands;
import com.sixik.gpu.ir.Types;

import java.util.ArrayList;
import java.util.List;

/**
 * Проверка «этот модуль кодген умеет» до генерации текста и обход достижимых из ядра функций.
 *
 * <p>Эмиттер M5 покрывает baseline: скаляры, массивы {@code global/constant/private}, управление потоком, вызовы
 * функций, builtins. Всё, что относится к следующим вехам, отвергается диагностикой {@code CG-U01} - так же, как
 * интерпретатор отвечает {@code IN-U01}: векторы и struct (M8), {@code local_addr}, {@code field_ptr} и
 * pointer-параметры (M9), {@code barrier}, {@code local_array_decl} и {@code __local}-параметры (M10),
 * {@code spec_const} и нативные тела. Обход итеративный, каждый узел проверяется один раз.</p>
 */
final class Support {

    private Support() {
    }

    /**
     * Функции, достижимые из ядра по вызовам (включая само ядро).
     *
     * @param m      модуль
     * @param kernel индекс ядра
     * @return признак достижимости по индексу функции
     */
    static boolean[] reachable(IrModule m, int kernel) {
        boolean[] reach = new boolean[m.functionCount()];
        int[] work = new int[m.functionCount()];
        int top = 0;
        reach[kernel] = true;
        work[top++] = kernel;
        int[] nodes = new int[Math.max(1, m.nodeCount())];
        boolean[] seen = new boolean[Math.max(1, m.nodeCount())];
        while (top > 0) {
            int f = work[--top];
            if (m.fBodyKind(f) != IrModule.BODY_IR || m.fBody(f) < 0) {
                continue;
            }
            int sp = 0;
            java.util.Arrays.fill(seen, false);
            nodes[sp++] = m.fBody(f);
            seen[m.fBody(f)] = true;
            while (sp > 0) {
                int n = nodes[--sp];
                if (m.op(n) == Op.CALL) {
                    int callee = m.a(n);
                    if (!reach[callee]) {
                        reach[callee] = true;
                        work[top++] = callee;
                    }
                }
                int count = Operands.count(m, n);
                for (int k = 0; k < count; k++) {
                    int c = Operands.get(m, n, k);
                    if (!seen[c]) {
                        seen[c] = true;
                        nodes[sp++] = c;
                    }
                }
            }
        }
        return reach;
    }

    /**
     * Проверяет достижимые функции.
     *
     * @param m      модуль
     * @param kernel индекс ядра
     * @param reach  результат {@link #reachable}
     * @return диагностики {@code CG-U01}; пусто, если всё поддержано
     */
    static List<Diagnostic> check(IrModule m, int kernel, boolean[] reach) {
        List<Diagnostic> out = new ArrayList<>();
        boolean[] seen = new boolean[Math.max(1, m.nodeCount())];
        int[] stack = new int[Math.max(1, m.nodeCount())];
        for (int f = 0; f < m.functionCount(); f++) {
            if (!reach[f]) {
                continue;
            }
            if (m.fBodyKind(f) != IrModule.BODY_IR) {
                out.add(unsupported(f, -1, "нативное тело функции не поддержано"));
                continue;
            }
            checkSignature(m, f, f == kernel, out);
            java.util.Arrays.fill(seen, false);
            int sp = 0;
            stack[sp++] = m.fBody(f);
            seen[m.fBody(f)] = true;
            while (sp > 0) {
                int n = stack[--sp];
                checkNode(m, f, n, out);
                int count = Operands.count(m, n);
                for (int k = 0; k < count; k++) {
                    int c = Operands.get(m, n, k);
                    if (!seen[c]) {
                        seen[c] = true;
                        stack[sp++] = c;
                    }
                }
            }
        }
        return out;
    }

    private static void checkSignature(IrModule m, int f, boolean kernel, List<Diagnostic> out) {
        for (int i = 0; i < m.fParamCount(f); i++) {
            int t = m.fParamType(f, i);
            if (!storageOk(m, t, true)) {
                out.add(unsupported(f, -1, "параметр " + i + " типа t" + t + " не поддержан"));
            } else if (kernel && m.typeKind(t) == Types.KIND_ARRAY && Types.space(m, t) == AddressSpace.LOCAL) {
                out.add(unsupported(f, -1, "параметр " + i + ": __local-массив в параметрах ядра - веха M10"));
            }
        }
        for (int l = 0; l < m.fLocalCount(f); l++) {
            if (!storageOk(m, m.fLocalType(f, l), false)) {
                out.add(unsupported(f, -1, "локаль " + l + " типа t" + m.fLocalType(f, l) + " не поддержана"));
            }
        }
        int ret = m.fRet(f);
        if (ret != Types.VOID && !(Types.isScalar(ret))) {
            out.add(unsupported(f, -1, "тип результата t" + ret + " не поддержан"));
        }
    }

    /** Допустимый тип параметра/локали: скаляр либо одномерный массив в global/constant/private. */
    private static boolean storageOk(IrModule m, int t, boolean param) {
        if (Types.isScalar(t)) {
            return true;
        }
        if (m.typeKind(t) == Types.KIND_ARRAY) {
            int space = Types.space(m, t);
            int elem = Types.elem(m, t);
            return Types.isScalar(elem) && (space == AddressSpace.GLOBAL || space == AddressSpace.CONSTANT
                    || space == AddressSpace.PRIVATE || (param && space == AddressSpace.LOCAL));
        }
        return false;
    }

    private static void checkNode(IrModule m, int f, int n, List<Diagnostic> out) {
        int op = m.op(n);
        String why = null;
        switch (op) {
            case Op.SPEC_CONST -> why = "spec_const не поддержана";
            case Op.LOCAL_ADDR, Op.FIELD_PTR -> why = Op.nameOf(op) + " - pointer-типы, веха M9";
            case Op.BARRIER, Op.LOCAL_ARRAY_DECL -> why = Op.nameOf(op) + " - local memory и barrier, веха M10";
            case Op.SPLAT, Op.EXTRACT, Op.INSERT, Op.SHUFFLE, Op.BUILD, Op.STRUCT_BUILD, Op.FIELD_GET,
                 Op.FIELD_SET -> why = Op.nameOf(op) + " - векторы и struct, веха M8";
            default -> {
                int t = m.type(n);
                boolean ok = t == Types.VOID || Types.isScalar(t)
                        || (m.typeKind(t) == Types.KIND_ARRAY && storageOk(m, t, true))
                        || (op == Op.ELEM_PTR && m.typeKind(t) == Types.KIND_PTR && Types.isScalar(Types.elem(m, t)));
                if (!ok) {
                    why = "значение типа t" + t + " не поддержано";
                }
            }
        }
        if (why != null) {
            out.add(unsupported(f, n, why));
        }
    }

    private static Diagnostic unsupported(int f, int n, String why) {
        return new Diagnostic("CG-U01", Diagnostic.Severity.ERROR, f, n, why);
    }
}
