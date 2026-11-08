package com.sixik.gpu.ir.interpreter;

import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Operands;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

import java.util.ArrayList;
import java.util.List;

/**
 * Предварительная проверка: достижимый из ядра код не содержит того, что интерпретатор пока не исполняет.
 *
 * <p>Так «ещё не сделано» превращается в понятную диагностику {@code IN-U01} <em>до</em> запуска, а не в сбой
 * посреди работы. Список сужается по мере вех: векторы и struct - M8, {@code local_addr} - M9, local memory и
 * {@code barrier} - M10.</p>
 */
final class Support {

    private Support() {
    }

    /**
     * Обходит ядро и все достижимые функции.
     *
     * @param m     модуль, прошедший валидатор
     * @param entry индекс ядра
     * @return ошибки {@code IN-U01}; пусто, если исполнять можно
     */
    static List<Diagnostic> check(IrModule m, int entry) {
        List<Diagnostic> out = new ArrayList<>();
        boolean[] fseen = new boolean[m.functionCount()];
        boolean[] nseen = new boolean[m.nodeCount()];
        int[] fwork = new int[m.functionCount()];
        int fsize = 0;
        fwork[fsize++] = entry;
        fseen[entry] = true;
        int[] nwork = new int[Math.max(1, m.nodeCount())];
        while (fsize > 0) {
            int f = fwork[--fsize];
            checkSignature(m, f, out);
            if (m.fBodyKind(f) != IrModule.BODY_IR || m.fBody(f) < 0) {
                out.add(unsupported(f, -1, "функция f" + f + " с нативным телом (не v1)"));
                continue;
            }
            int nsize = 0;
            if (!nseen[m.fBody(f)]) {
                nseen[m.fBody(f)] = true;
                nwork[nsize++] = m.fBody(f);
            }
            while (nsize > 0) {
                int n = nwork[--nsize];
                checkNode(m, f, n, out);
                if (m.op(n) == Op.CALL) {
                    int callee = m.a(n);
                    if (!fseen[callee]) {
                        fseen[callee] = true;
                        fwork[fsize++] = callee;
                    }
                }
                int cnt = Operands.count(m, n);
                for (int k = 0; k < cnt; k++) {
                    int child = Operands.get(m, n, k);
                    if (!nseen[child]) {
                        nseen[child] = true; // отметка при постановке в очередь: узел попадает в неё не более раза
                        nwork[nsize++] = child;
                    }
                }
            }
        }
        return out;
    }

    private static void checkSignature(IrModule m, int f, List<Diagnostic> out) {
        for (int i = 0; i < m.fParamCount(f); i++) {
            String why = unsupportedType(m, m.fParamType(f, i));
            if (why != null) {
                out.add(unsupported(f, -1, "параметр " + i + " функции f" + f + ": " + why));
            }
        }
        for (int l = 0; l < m.fLocalCount(f); l++) {
            String why = unsupportedType(m, m.fLocalType(f, l));
            if (why != null) {
                out.add(unsupported(f, -1, "локаль " + l + " функции f" + f + ": " + why));
            }
        }
    }

    private static void checkNode(IrModule m, int f, int n, List<Diagnostic> out) {
        int op = m.op(n);
        switch (op) {
            case Op.SPEC_CONST ->
                    out.add(unsupported(f, n, "spec_const зарезервирован под AutoTune (post-v1)"));
            case Op.BARRIER, Op.LOCAL_ARRAY_DECL ->
                    out.add(unsupported(f, n, "узел " + Op.nameOf(op) + ": local memory и barrier - веха M10"));
            case Op.LOCAL_ADDR, Op.FIELD_PTR ->
                    out.add(unsupported(f, n, "узел " + Op.nameOf(op) + ": указатели на локали и поля - вехи M8/M9"));
            case Op.SPLAT, Op.EXTRACT, Op.INSERT, Op.SHUFFLE, Op.BUILD, Op.STRUCT_BUILD, Op.FIELD_GET, Op.FIELD_SET ->
                    out.add(unsupported(f, n, "узел " + Op.nameOf(op) + ": векторы и struct - веха M8"));
            case Op.INTRINSIC -> {
                if (!Machine.supportsBuiltin(m.a(n))) {
                    out.add(unsupported(f, n, "builtin #" + m.a(n) + " не поддерживается интерпретатором"));
                }
            }
            default -> {
                // остальные узлы исполняются
            }
        }
        String why = unsupportedType(m, m.type(n));
        if (why != null) {
            out.add(unsupported(f, n, "узел " + Op.nameOf(op) + ": " + why));
        }
    }

    /** @return причина, по которой тип не поддерживается, либо {@code null} */
    private static String unsupportedType(IrModule m, int t) {
        int kind = m.typeKind(t);
        if (kind == Types.KIND_VEC) {
            return "векторные типы - веха M8";
        }
        if (kind == Types.KIND_STRUCT) {
            return "struct - веха M8";
        }
        if (kind == Types.KIND_IMAGE || kind == Types.KIND_SAMPLER) {
            return "images и samplers не входят в v1";
        }
        return null;
    }

    private static Diagnostic unsupported(int f, int n, String message) {
        return new Diagnostic("IN-U01", Diagnostic.Severity.ERROR, f, n, message);
    }
}
