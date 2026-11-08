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
 * <p>Эмиттер покрывает скаляры, векторы и struct (M8), указатели на них в любом пространстве адресов (M9: параметры и
 * локали device-функций, {@code local_addr}, {@code field_ptr}), массивы {@code global/constant/private}, управление
 * потоком, вызовы функций, builtins, {@code barrier} и {@code local_array_decl} (M10). Остальное отвергается диагностикой
 * {@code CG-U01} - так же, как интерпретатор отвечает {@code IN-U01}: {@code spec_const}, нативные тела,
 * {@code __local}-параметры ядра (вне v1: локальный массив объявляется в теле ядра) и указатель в параметре ядра
 * (у ядра параметр-массив несёт длину, у указателя её нет). Обход итеративный, каждый узел проверяется один раз.</p>
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

    /**
     * Типы, которые встречаются в достижимых функциях (параметры, локали, результаты и узлы), вместе с типами,
     * из которых они составлены: поля struct, элементы массивов и указателей.
     *
     * @param m     модуль
     * @param reach достижимые функции
     * @return признак использования по id типа
     */
    static boolean[] usedTypes(IrModule m, boolean[] reach) {
        boolean[] used = new boolean[m.typeCount()];
        boolean[] seen = new boolean[Math.max(1, m.nodeCount())];
        int[] stack = new int[Math.max(1, m.nodeCount())];
        for (int f = 0; f < m.functionCount(); f++) {
            if (!reach[f] || m.fBodyKind(f) != IrModule.BODY_IR) {
                continue;
            }
            for (int i = 0; i < m.fParamCount(f); i++) {
                mark(m, m.fParamType(f, i), used);
            }
            for (int l = 0; l < m.fLocalCount(f); l++) {
                mark(m, m.fLocalType(f, l), used);
            }
            mark(m, m.fRet(f), used);
            java.util.Arrays.fill(seen, false);
            int sp = 0;
            stack[sp++] = m.fBody(f);
            seen[m.fBody(f)] = true;
            while (sp > 0) {
                int n = stack[--sp];
                mark(m, m.type(n), used);
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
        return used;
    }

    private static void mark(IrModule m, int t, boolean[] used) {
        if (t < 0 || t >= used.length || used[t]) {
            return;
        }
        used[t] = true;
        switch (m.typeKind(t)) {
            case Types.KIND_STRUCT -> {
                for (int i = 0; i < m.structFieldCount(t); i++) {
                    mark(m, m.structFieldType(t, i), used);
                }
            }
            case Types.KIND_ARRAY, Types.KIND_PTR -> mark(m, Types.elem(m, t), used);
            default -> {
            }
        }
    }

    private static void checkSignature(IrModule m, int f, boolean kernel, List<Diagnostic> out) {
        for (int i = 0; i < m.fParamCount(f); i++) {
            int t = m.fParamType(f, i);
            if (!storageOk(m, t)) {
                out.add(unsupported(f, -1, "параметр " + i + " типа t" + t + " не поддержан"));
            } else if (kernel && m.typeKind(t) == Types.KIND_PTR) {
                out.add(unsupported(f, -1, "параметр " + i + ": указатель в параметрах ядра не поддержан, используйте массив"));
            } else if (kernel && m.typeKind(t) == Types.KIND_ARRAY && Types.space(m, t) == AddressSpace.LOCAL) {
                out.add(unsupported(f, -1, "параметр " + i + ": __local-массив в параметрах ядра - вне v1 (локальный массив объявляется в теле ядра)"));
            }
        }
        for (int l = 0; l < m.fLocalCount(f); l++) {
            if (!storageOk(m, m.fLocalType(f, l))) {
                out.add(unsupported(f, -1, "локаль " + l + " типа t" + m.fLocalType(f, l) + " не поддержана"));
            }
        }
        int ret = m.fRet(f);
        if (ret != Types.VOID && !valueOk(m, ret)) {
            out.add(unsupported(f, -1, "тип результата t" + ret + " не поддержан"));
        }
    }

    /**
     * Тип значения, который кодген умеет: скаляр, вектор из 32/64-битных чисел, struct из таких полей
     * (без {@code BOOL}, массивов и указателей внутри агрегатов).
     */
    private static boolean valueOk(IrModule m, int t) {
        if (Types.isScalar(t)) {
            return true;
        }
        return aggregateOk(m, t);
    }

    /** Указатель на скаляр (не {@code BOOL}), вектор или struct в любом пространстве адресов. */
    private static boolean pointerOk(IrModule m, int t) {
        return t >= 0 && t < m.typeCount() && m.typeKind(t) == Types.KIND_PTR && valueOk(m, Types.elem(m, t));
    }

    private static boolean aggregateOk(IrModule m, int t) {
        if (t < 0 || t >= m.typeCount()) {
            return false;
        }
        switch (m.typeKind(t)) {
            case Types.KIND_VEC: {
                int e = m.typeA(t);
                return Types.isArith(e);
            }
            case Types.KIND_STRUCT: {
                for (int i = 0; i < m.structFieldCount(t); i++) {
                    int ft = m.structFieldType(t, i);
                    boolean ok = Types.isScalar(ft) ? ft != Types.BOOL : aggregateOk(m, ft);
                    if (!ok) {
                        return false;
                    }
                }
                return m.structFieldCount(t) > 0;
            }
            default:
                return false;
        }
    }

    /** Допустимый тип параметра/локали: значение, указатель либо одномерный массив в global/constant/private/local. */
    private static boolean storageOk(IrModule m, int t) {
        if (valueOk(m, t) || pointerOk(m, t)) {
            return true;
        }
        if (m.typeKind(t) == Types.KIND_ARRAY) {
            int space = Types.space(m, t);
            int elem = Types.elem(m, t);
            return valueOk(m, elem) && (space == AddressSpace.GLOBAL || space == AddressSpace.CONSTANT
                    || space == AddressSpace.PRIVATE || space == AddressSpace.LOCAL);
        }
        return false;
    }

    private static void checkNode(IrModule m, int f, int n, List<Diagnostic> out) {
        int op = m.op(n);
        String why = null;
        switch (op) {
            case Op.SPEC_CONST -> why = "spec_const не поддержана";
            default -> {
                int t = m.type(n);
                boolean ok = t == Types.VOID || valueOk(m, t)
                        || (m.typeKind(t) == Types.KIND_ARRAY && storageOk(m, t))
                        || pointerOk(m, t);
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
