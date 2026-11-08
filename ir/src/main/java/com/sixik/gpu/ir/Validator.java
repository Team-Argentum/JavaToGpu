package com.sixik.gpu.ir;

import com.sixik.gpu.ir.Diagnostic.Severity;

import java.util.ArrayList;
import java.util.List;

/**
 * Валидатор IR: проверяет инварианты из REWORK_ARCHITECTURE.md, 5.12, и вычисляет производные метаданные
 * ({@code required_features}, {@code uses_group_sync}, режимы доступа параметров).
 *
 * <p>Валидатор - контракт IR: ему подчиняется любой фронтенд и любой {@code OptimizerPass}. Он не бросает
 * исключений на некорректных данных (даже на повреждённом бинарном вводе): все нарушения возвращаются как
 * {@link Diagnostic}. Правила имеют стабильные коды вида {@code IR-S02}:</p>
 * <ul>
 *   <li>{@code S} - структура (диапазоны id, порядок, достижимость, категории операндов);</li>
 *   <li>{@code T} - типы; {@code V} - области видимости; {@code E} - эффекты и порядок;</li>
 *   <li>{@code C} - control flow; {@code M} - память; {@code F} - функции; {@code B} - барьеры.</li>
 * </ul>
 *
 * <p>Работа идёт в несколько проходов: (1) таблицы типов и функций, (2) диапазоны и достижимость узлов,
 * (3) типизация каждого узла, (4) структурный обход тел функций (видимость, цикл/switch, return),
 * (5) эффекты и «открытые» узлы, (6) вычисление метаданных в порядке вызовов, (7) taint-анализ барьеров.</p>
 */
public final class Validator {

    /** Режим доступа параметра: читается. */
    public static final int ACCESS_READ = 1;
    /** Режим доступа параметра: записывается. */
    public static final int ACCESS_WRITE = 2;

    private Validator() {
    }

    /**
     * Параметры проверки.
     *
     * @param strictBarriers {@code true} - {@code IR-B01} считается ошибкой, иначе предупреждением
     */
    public record Options(boolean strictBarriers) {
        /** Параметры по умолчанию: {@code IR-B01} - предупреждение. */
        public static final Options DEFAULT = new Options(false);
    }

    /**
     * Вычисленные метаданные функции (не являются частью бинарного формата и пересчитываются при загрузке).
     *
     * @param requiredFeatures маска {@link Features}, которую должно поддерживать устройство
     * @param usesGroupSync    функция (или её вызываемые) использует {@code barrier} либо local memory
     * @param paramAccess      по параметру: комбинация {@link #ACCESS_READ} и {@link #ACCESS_WRITE}
     * @param effects          функция имеет побочные эффекты (запись, барьер, эффектный builtin)
     * @param readsMemory      функция читает память
     */
    public record FunctionInfo(int requiredFeatures, boolean usesGroupSync, int[] paramAccess,
                               boolean effects, boolean readsMemory) {
    }

    /**
     * Результат проверки.
     *
     * @param diagnostics все найденные нарушения
     * @param functions   метаданные по функциям (индекс = индекс функции); заполнены, даже если есть ошибки
     */
    public record Report(List<Diagnostic> diagnostics, FunctionInfo[] functions) {
        /**
         * @return {@code true}, если среди диагностик есть хотя бы одна ошибка
         */
        public boolean hasErrors() {
            for (Diagnostic d : diagnostics) {
                if (d.isError()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * @param code код правила, например {@code "IR-S02"}
         * @return {@code true}, если есть диагностика с таким кодом
         */
        public boolean has(String code) {
            for (Diagnostic d : diagnostics) {
                if (d.code().equals(code)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Проверяет модуль с параметрами по умолчанию.
     *
     * @param m модуль
     * @return отчёт
     */
    public static Report validate(IrModule m) {
        return validate(m, Options.DEFAULT);
    }

    /**
     * Проверяет модуль.
     *
     * @param m    модуль
     * @param opts параметры проверки
     * @return отчёт
     */
    public static Report validate(IrModule m, Options opts) {
        return new Run(m, opts).run();
    }

    // =============================================================================================

    private static final class Run {
        private final IrModule m;
        private final Options opts;
        private final List<Diagnostic> out = new ArrayList<>();
        private final int nodeCount;
        private final int funcCount;
        private final int typeCount;
        private final int listSize;

        // по узлам
        private final int[] owner;
        private final int[] parents;
        private final int[] parentOf;
        private final int[] parentSlot;
        private final boolean[] bad;
        private final boolean[] open;
        private final boolean[] effectful;
        private final boolean[] taint;
        private final boolean[] exprChecked;

        // по типам
        private final boolean[] usesF64;
        private final boolean[] typeOk;

        // по функциям
        private final boolean[] funcBodyOk;
        private final boolean[] funcShapeOk;
        private final Validator.FunctionInfo[] infos;
        private final boolean[] divergentInside;

        Run(IrModule m, Options opts) {
            this.m = m;
            this.opts = opts;
            this.nodeCount = m.nodeCount();
            this.funcCount = m.functionCount();
            this.typeCount = m.typeCount();
            this.listSize = m.listSize();
            owner = new int[nodeCount];
            parents = new int[nodeCount];
            parentOf = new int[nodeCount];
            parentSlot = new int[nodeCount];
            bad = new boolean[nodeCount];
            open = new boolean[nodeCount];
            effectful = new boolean[nodeCount];
            taint = new boolean[nodeCount];
            exprChecked = new boolean[nodeCount];
            usesF64 = new boolean[typeCount];
            typeOk = new boolean[typeCount];
            funcBodyOk = new boolean[funcCount];
            funcShapeOk = new boolean[funcCount];
            infos = new Validator.FunctionInfo[funcCount];
            divergentInside = new boolean[funcCount];
            java.util.Arrays.fill(owner, -1);
            java.util.Arrays.fill(parentOf, -1);
        }

        // ---------------------------------------------------------------------------------------
        // отчётность
        // ---------------------------------------------------------------------------------------

        private void err(String code, int f, int n, String msg) {
            out.add(new Diagnostic(code, Severity.ERROR, f, n, nodePrefix(n) + msg));
        }

        private void warn(String code, int f, int n, String msg) {
            out.add(new Diagnostic(code, Severity.WARNING, f, n, nodePrefix(n) + msg));
        }

        private String nodePrefix(int n) {
            if (n >= 0 && n < nodeCount) {
                return "n" + n + " " + Op.nameOf(m.op(n)) + ": ";
            }
            return "";
        }

        private boolean typeInRange(int t) {
            return t >= 0 && t < typeCount;
        }

        private boolean listOk(int off, int count) {
            return off >= 0 && count >= 0 && (long) off + count <= listSize;
        }

        // ---------------------------------------------------------------------------------------
        // основной ход
        // ---------------------------------------------------------------------------------------

        Report run() {
            if (m.builtinsVersion() != Builtins.VERSION) {
                err("IR-S05", -1, -1, "версия таблицы builtins модуля " + m.builtinsVersion()
                        + " не совпадает с версией читателя " + Builtins.VERSION);
            }
            checkTypes();
            checkFunctionTable();
            checkNodeRanges();
            computeReachability();
            countParents();
            propagateBad();
            for (int n = 0; n < nodeCount; n++) {
                if (owner[n] >= 0 && !bad[n] && funcShapeOk[owner[n]]) {
                    checkNodeTypes(owner[n], n);
                }
            }
            checkCallGraph();
            computeFunctionFlags();
            for (int f = 0; f < funcCount; f++) {
                if (funcBodyOk[f]) {
                    structuralWalk(f);
                }
            }
            checkEffectsAndOpenNodes();
            for (int f = 0; f < funcCount; f++) {
                if (funcBodyOk[f]) {
                    checkBarriers(f);
                }
            }
            for (int f = 0; f < funcCount; f++) {
                if (infos[f] == null) {
                    infos[f] = new Validator.FunctionInfo(0, false, new int[Math.max(0, m.fParamCount(f))], false, false);
                }
            }
            return new Report(List.copyOf(out), infos);
        }

        // ---------------------------------------------------------------------------------------
        // проход 1: таблица типов
        // ---------------------------------------------------------------------------------------

        private void checkTypes() {
            for (int t = 0; t < typeCount; t++) {
                int kind = m.typeKind(t);
                typeOk[t] = true;
                if (t < Types.FIRST_DYNAMIC) {
                    usesF64[t] = t == Types.F64;
                    typeOk[t] = t <= Types.F64;   // id 12..15 зарезервированы и не годятся для использования
                    continue;
                }
                switch (kind) {
                    case Types.KIND_VEC -> {
                        int s = m.typeA(t);
                        int lanes = m.typeB(t);
                        if (!Types.isScalar(s) || s == Types.BOOL) {
                            err("IR-T07", -1, -1, "тип t" + t + ": компонента вектора t" + s + " недопустима");
                            typeOk[t] = false;
                        }
                        if (!Types.isValidLanes(lanes)) {
                            err("IR-T07", -1, -1, "тип t" + t + ": число компонент " + lanes + " не из {2,3,4,8,16}");
                            typeOk[t] = false;
                        }
                        usesF64[t] = s == Types.F64;
                    }
                    case Types.KIND_PTR, Types.KIND_ARRAY -> {
                        int space = m.typeA(t);
                        int elem = m.typeB(t);
                        if (!AddressSpace.isValid(space)) {
                            err("IR-S01", -1, -1, "тип t" + t + ": неизвестный address space " + space);
                            typeOk[t] = false;
                        }
                        if (elem < 1 || elem >= t || !typeOk[elem]) {
                            err("IR-S01", -1, -1, "тип t" + t + ": элемент t" + elem + " вне диапазона или определён позже");
                            typeOk[t] = false;
                        } else {
                            if (elem == Types.BOOL) {
                                err("IR-T06", -1, -1, "тип t" + t + ": BOOL нельзя в элементе указателя/массива");
                                typeOk[t] = false;
                            }
                            usesF64[t] = usesF64[elem];
                        }
                    }
                    case Types.KIND_STRUCT -> checkStruct(t);
                    default -> {
                        err("IR-S01", -1, -1, "тип t" + t + ": вид " + kind + " не поддерживается в v1");
                        typeOk[t] = false;
                    }
                }
            }
        }

        private void checkStruct(int t) {
            int off = m.typeB(t);
            int count = m.typeC(t);
            int align = m.typeD(t);
            if (!listOk(off, count)) {
                err("IR-S04", -1, -1, "тип t" + t + ": список полей вне пула");
                typeOk[t] = false;
                return;
            }
            if (count < 1) {
                err("IR-T08", -1, -1, "тип t" + t + ": у struct нет полей");
                typeOk[t] = false;
            }
            if (align != 0 && (align < 0 || Integer.bitCount(align) != 1)) {
                err("IR-T08", -1, -1, "тип t" + t + ": align " + align + " не степень двойки");
                typeOk[t] = false;
            }
            boolean f64 = false;
            for (int i = 0; i < count; i++) {
                int ft = m.list(off + i);
                if (ft < 1 || ft >= t || !typeOk[ft]) {
                    err("IR-S01", -1, -1, "тип t" + t + ": поле " + i + " ссылается на t" + ft + " вне диапазона или определённый позже");
                    typeOk[t] = false;
                    continue;
                }
                if (ft == Types.BOOL) {
                    err("IR-T06", -1, -1, "тип t" + t + ": BOOL нельзя в поле struct (поле " + i + ")");
                    typeOk[t] = false;
                }
                int fk = m.typeKind(ft);
                if (fk == Types.KIND_ARRAY || fk == Types.KIND_PTR) {
                    err("IR-M05", -1, -1, "тип t" + t + ": поле " + i + " - массив или указатель (не поддерживается в v1)");
                    typeOk[t] = false;
                }
                f64 |= usesF64[ft];
            }
            usesF64[t] = f64;
        }

        // ---------------------------------------------------------------------------------------
        // проход 2: таблица функций
        // ---------------------------------------------------------------------------------------

        private void checkFunctionTable() {
            for (int f = 0; f < funcCount; f++) {
                boolean ok = true;
                int kind = m.fKind(f);
                if (kind != IrModule.KERNEL && kind != IrModule.DEVICE) {
                    err("IR-F04", f, -1, "функция f" + f + ": неизвестный вид " + kind);
                    ok = false;
                }
                int ret = m.fRet(f);
                if (!typeInRange(ret) || !typeOk[ret]) {
                    err("IR-S01", f, -1, "функция f" + f + ": тип результата t" + ret + " вне диапазона");
                    ok = false;
                } else if (kind == IrModule.KERNEL && ret != Types.VOID) {
                    err("IR-C02", f, -1, "kernel f" + f + " должен возвращать void");
                }
                int pc = m.fParamCount(f);
                int po = funcCol(IrModule.F_PARAMS_OFF, f);
                if (!listOk(po, pc)) {
                    err("IR-S04", f, -1, "функция f" + f + ": список параметров вне пула");
                    ok = false;
                } else {
                    for (int i = 0; i < pc; i++) {
                        int pt = m.fParamType(f, i);
                        if (pt < 1 || pt >= typeCount || !typeOk[pt]) {
                            err("IR-S01", f, -1, "функция f" + f + ": параметр " + i + " имеет тип t" + pt + " вне диапазона");
                            ok = false;
                            continue;
                        }
                        if (pt == Types.BOOL) {
                            err("IR-T06", f, -1, "функция f" + f + ": параметр " + i + " типа BOOL недопустим");
                        }
                        if (kind == IrModule.KERNEL) {
                            int k = m.typeKind(pt);
                            if ((k == Types.KIND_PTR || k == Types.KIND_ARRAY) && m.typeA(pt) == AddressSpace.PRIVATE) {
                                err("IR-M04", f, -1, "kernel f" + f + ": параметр " + i + " в private-памяти недопустим");
                            }
                        }
                    }
                }
                int lc = m.fLocalCount(f);
                int lo = funcCol(IrModule.F_LOCALS_OFF, f);
                if (!listOk(lo, lc)) {
                    err("IR-S04", f, -1, "функция f" + f + ": список локалей вне пула");
                    ok = false;
                } else {
                    for (int i = 0; i < lc; i++) {
                        int lt = m.fLocalType(f, i);
                        if (lt < 1 || lt >= typeCount || !typeOk[lt]) {
                            err("IR-S01", f, -1, "функция f" + f + ": локаль " + i + " имеет тип t" + lt + " вне диапазона");
                            ok = false;
                        }
                    }
                }
                int rc = m.fRequiredCount(f);
                int ro = funcCol(IrModule.F_REQUIRED_OFF, f);
                if (!listOk(ro, rc)) {
                    err("IR-S04", f, -1, "функция f" + f + ": список required вне пула");
                } else {
                    for (int i = 0; i < rc; i++) {
                        int sid = m.fRequiredName(f, i);
                        if (sid < 0 || sid >= m.stringCount()) {
                            err("IR-S01", f, -1, "функция f" + f + ": required[" + i + "] ссылается на строку " + sid + " вне таблицы");
                        }
                    }
                }
                int wx = m.fReqWorkGroup(f, 0);
                int wy = m.fReqWorkGroup(f, 1);
                int wz = m.fReqWorkGroup(f, 2);
                boolean anyWg = wx != 0 || wy != 0 || wz != 0;
                if (anyWg) {
                    if (kind != IrModule.KERNEL) {
                        err("IR-F03", f, -1, "функция f" + f + ": reqd_work_group_size допустим только у kernel");
                    }
                    if (wx <= 0 || wy <= 0 || wz <= 0) {
                        err("IR-F03", f, -1, "функция f" + f + ": reqd_work_group_size (" + wx + "," + wy + "," + wz
                                + ") должен быть положительным по всем осям");
                    }
                }
                int inline = m.fInline(f);
                if (inline < IrModule.INLINE_DEFAULT || inline > IrModule.INLINE_NEVER) {
                    err("IR-F04", f, -1, "функция f" + f + ": неизвестная политика inline " + inline);
                }
                int opt = m.fOpt(f);
                if (opt < 0 || opt > 3) {
                    err("IR-F04", f, -1, "функция f" + f + ": уровень оптимизации " + opt + " вне 0..3");
                }
                int bodyKind = m.fBodyKind(f);
                if (bodyKind != IrModule.BODY_IR && bodyKind != IrModule.BODY_NATIVE) {
                    err("IR-F04", f, -1, "функция f" + f + ": неизвестный вид тела " + bodyKind);
                }
                int fb = m.fFallback(f);
                if (fb != -1) {
                    if (fb < 0 || fb >= funcCount || fb == f) {
                        err("IR-F04", f, -1, "функция f" + f + ": fallback f" + fb + " недопустим");
                    }
                }
                funcShapeOk[f] = ok;

                int body = m.fBody(f);
                if (bodyKind == IrModule.BODY_NATIVE) {
                    funcBodyOk[f] = false;
                } else if (body < 0 || body >= nodeCount) {
                    err("IR-S01", f, -1, "функция f" + f + ": корень тела n" + body + " вне диапазона");
                } else if (m.op(body) != Op.BLOCK) {
                    err("IR-S07", f, body, "корень тела функции f" + f + " должен быть BLOCK");
                } else {
                    funcBodyOk[f] = ok;
                }
            }
            // F04: fallback должен иметь ту же ABI (параметры и результат)
            for (int f = 0; f < funcCount; f++) {
                int fb = m.fFallback(f);
                if (fb >= 0 && fb < funcCount && fb != f && funcShapeOk[f] && funcShapeOk[fb]) {
                    boolean same = m.fRet(f) == m.fRet(fb) && m.fParamCount(f) == m.fParamCount(fb);
                    for (int i = 0; same && i < m.fParamCount(f); i++) {
                        same = m.fParamType(f, i) == m.fParamType(fb, i);
                    }
                    if (!same) {
                        err("IR-F04", f, -1, "функция f" + f + ": fallback f" + fb + " имеет другую ABI");
                    }
                }
            }
        }

        private int funcCol(int col, int f) {
            return m.funcColumns()[col][f];
        }

        // ---------------------------------------------------------------------------------------
        // проход 3: диапазоны и достижимость
        // ---------------------------------------------------------------------------------------

        private void checkNodeRanges() {
            for (int n = 0; n < nodeCount; n++) {
                int op = m.op(n);
                if (!Op.isValid(op)) {
                    err("IR-S01", -1, n, "неизвестный опкод " + op);
                    bad[n] = true;
                    continue;
                }
                int t = m.type(n);
                if (!typeInRange(t) || !typeOk[t]) {
                    err("IR-S01", -1, n, "тип результата t" + t + " вне диапазона");
                    bad[n] = true;
                }
                if (Op.isStatement(op) && t != Types.VOID) {
                    err("IR-T09", -1, n, "стейтмент должен иметь тип VOID, а не t" + t);
                }
                // списки
                switch (op) {
                    case Op.BUILD, Op.STRUCT_BUILD, Op.BLOCK -> {
                        if (!listOk(m.a(n), m.b(n))) {
                            err("IR-S04", -1, n, "список [" + m.a(n) + "," + m.a(n) + "+" + m.b(n) + ") вне пула");
                            bad[n] = true;
                        }
                    }
                    case Op.CALL, Op.INTRINSIC -> {
                        if (!listOk(m.b(n), m.c(n))) {
                            err("IR-S04", -1, n, "список аргументов вне пула");
                            bad[n] = true;
                        }
                    }
                    case Op.SHUFFLE -> {
                        if (!listOk(m.b(n), m.c(n))) {
                            err("IR-S04", -1, n, "список индексов вне пула");
                            bad[n] = true;
                        }
                    }
                    case Op.SWITCH -> {
                        if (m.c(n) < 0 || !listOk(m.b(n), 3 * m.c(n))) {
                            err("IR-S04", -1, n, "список case вне пула");
                            bad[n] = true;
                        }
                    }
                    default -> {
                    }
                }
                if (bad[n]) {
                    continue;
                }
                // операнды: диапазон и порядок
                int cnt = Operands.count(m, n);
                for (int k = 0; k < cnt; k++) {
                    int o = Operands.get(m, n, k);
                    if (o < 0 || o >= nodeCount) {
                        err("IR-S01", -1, n, "операнд " + k + " ссылается на узел n" + o + " вне таблицы");
                        bad[n] = true;
                    } else if (o >= n) {
                        err("IR-S02", -1, n, "операнд " + k + " (n" + o + ") не имеет меньший id, чем узел");
                        bad[n] = true;
                    }
                }
            }
        }

        private void computeReachability() {
            for (int f = 0; f < funcCount; f++) {
                int body = m.fBody(f);
                if (m.fBodyKind(f) == IrModule.BODY_NATIVE || body < 0 || body >= nodeCount) {
                    continue;
                }
                markOwner(f, body);
            }
            for (int n = 0; n < nodeCount; n++) {
                if (owner[n] < 0 && !bad[n]) {
                    err("IR-S03", -1, n, "узел недостижим из тела какой-либо функции");
                }
            }
        }

        private void markOwner(int f, int root) {
            // итеративный обход без рекурсии, чтобы глубокие деревья не переполняли стек
            int[] stack = new int[16];
            int sp = 0;
            stack[sp++] = root;
            while (sp > 0) {
                int n = stack[--sp];
                if (owner[n] == f) {
                    continue;
                }
                if (owner[n] >= 0) {
                    err("IR-S03", f, n, "узел принадлежит и функции f" + owner[n] + ", и функции f" + f);
                    continue;
                }
                owner[n] = f;
                if (bad[n]) {
                    continue;
                }
                int cnt = Operands.count(m, n);
                for (int k = 0; k < cnt; k++) {
                    int o = Operands.get(m, n, k);
                    if (o >= 0 && o < n) {
                        if (sp == stack.length) {
                            stack = java.util.Arrays.copyOf(stack, sp * 2);
                        }
                        stack[sp++] = o;
                    }
                }
            }
        }

        /**
         * Каскад повреждений: узел, у которого повреждён любой операнд, сам считается повреждённым, и все
         * дальнейшие проходы его пропускают (читать типы и колонки повреждённых узлов небезопасно).
         * Операнды имеют меньшие id, поэтому одного прохода по возрастанию достаточно.
         */
        private void propagateBad() {
            for (int n = 0; n < nodeCount; n++) {
                if (bad[n]) {
                    continue;
                }
                int cnt = Operands.count(m, n);
                for (int k = 0; k < cnt; k++) {
                    int o = Operands.get(m, n, k);
                    if (o < 0 || o >= nodeCount || bad[o]) {
                        bad[n] = true;
                        break;
                    }
                }
            }
        }

        private void countParents() {
            for (int n = 0; n < nodeCount; n++) {
                if (bad[n] || owner[n] < 0) {
                    continue;
                }
                int cnt = Operands.count(m, n);
                for (int k = 0; k < cnt; k++) {
                    int o = Operands.get(m, n, k);
                    if (o < 0 || o >= nodeCount) {
                        continue;
                    }
                    parents[o]++;
                    parentOf[o] = n;
                    parentSlot[o] = k;
                }
            }
        }

        // ---------------------------------------------------------------------------------------
        // проход 4: типизация узла
        // ---------------------------------------------------------------------------------------

        private int ty(int n) {
            return m.type(n);
        }

        private boolean arithType(int t) {
            int s = Types.scalarOf(m, t);
            return s >= 0 && Types.isArith(s);
        }

        private boolean intBitsType(int t) {
            int s = Types.scalarOf(m, t);
            return s >= 0 && Types.isInt32or64(s);
        }

        private boolean numericScalar(int t) {
            return Types.isScalar(t) && (Types.isInteger(t) || Types.isFloat(t));
        }

        private String tn(int t) {
            return "t" + t;
        }

        private void checkNodeTypes(int f, int n) {
            // каскадные ошибки не нужны, а типы повреждённых операндов читать небезопасно
            int operandCount = Operands.count(m, n);
            for (int k = 0; k < operandCount; k++) {
                int o = Operands.get(m, n, k);
                if (o < 0 || o >= nodeCount || bad[o]) {
                    return;
                }
            }
            int op = m.op(n);
            int t = ty(n);
            int a = m.a(n);
            int b = m.b(n);
            int c = m.c(n);
            switch (op) {
                case Op.CONST_INT -> {
                    if (!Types.isInteger(t)) {
                        err("IR-T09", f, n, "константа должна иметь целый тип, а не " + tn(t));
                    } else if (Types.bits(t) == 64 && (a < 0 || a >= m.constCount())) {
                        err("IR-S01", f, n, "индекс 64-битной константы " + a + " вне таблицы");
                    }
                }
                case Op.CONST_FLOAT -> {
                    if (!Types.isFloat(t)) {
                        err("IR-T09", f, n, "вещественная константа должна иметь тип F32/F64, а не " + tn(t));
                    } else if (t == Types.F64 && (a < 0 || a >= m.constCount())) {
                        err("IR-S01", f, n, "индекс F64-константы " + a + " вне таблицы");
                    }
                }
                case Op.CONST_BOOL -> {
                    if (t != Types.BOOL || (a != 0 && a != 1)) {
                        err("IR-T09", f, n, "булева константа должна быть BOOL со значением 0 или 1");
                    }
                }
                case Op.SPEC_CONST -> {
                    if (!Types.isInteger(t)) {
                        err("IR-T09", f, n, "spec_const должна иметь целый тип");
                    }
                }
                case Op.PARAM -> {
                    if (a < 0 || a >= m.fParamCount(f)) {
                        err("IR-S01", f, n, "параметр " + a + " вне 0.." + (m.fParamCount(f) - 1));
                    } else if (m.fParamType(f, a) != t) {
                        err("IR-T09", f, n, "тип узла " + tn(t) + " не совпадает с типом параметра " + tn(m.fParamType(f, a)));
                    }
                }
                case Op.ADD, Op.SUB, Op.MUL, Op.DIV, Op.REM -> {
                    if (ty(a) != ty(b)) {
                        err("IR-T01", f, n, "типы операндов различаются: " + tn(ty(a)) + " и " + tn(ty(b)));
                    } else if (!arithType(ty(a))) {
                        err("IR-T02", f, n, "арифметика не определена на " + tn(ty(a)));
                    } else if (t != ty(a)) {
                        err("IR-T09", f, n, "тип результата " + tn(t) + " не совпадает с типом операндов " + tn(ty(a)));
                    }
                }
                case Op.NEG -> {
                    if (!arithType(ty(a))) {
                        err("IR-T02", f, n, "отрицание не определено на " + tn(ty(a)));
                    } else if (t != ty(a)) {
                        err("IR-T09", f, n, "тип результата не совпадает с типом операнда");
                    }
                }
                case Op.AND, Op.OR, Op.XOR -> {
                    if (ty(a) != ty(b)) {
                        err("IR-T01", f, n, "типы операндов различаются: " + tn(ty(a)) + " и " + tn(ty(b)));
                    } else if (!intBitsType(ty(a)) && ty(a) != Types.BOOL) {
                        err("IR-T02", f, n, "побитовая операция не определена на " + tn(ty(a)));
                    } else if (t != ty(a)) {
                        err("IR-T09", f, n, "тип результата не совпадает с типом операндов");
                    }
                }
                case Op.NOT -> {
                    if (!intBitsType(ty(a)) && ty(a) != Types.BOOL) {
                        err("IR-T02", f, n, "NOT не определён на " + tn(ty(a)));
                    } else if (t != ty(a)) {
                        err("IR-T09", f, n, "тип результата не совпадает с типом операнда");
                    }
                }
                case Op.SHL, Op.SHR, Op.USHR -> {
                    if (ty(a) != ty(b)) {
                        err("IR-T01", f, n, "тип счётчика сдвига " + tn(ty(b)) + " должен совпадать с типом значения " + tn(ty(a)));
                    } else if (!intBitsType(ty(a))) {
                        err("IR-T02", f, n, "сдвиг не определён на " + tn(ty(a)));
                    } else if (t != ty(a)) {
                        err("IR-T09", f, n, "тип результата не совпадает с типом значения");
                    }
                }
                case Op.EQ, Op.NE -> {
                    if (ty(a) != ty(b)) {
                        err("IR-T01", f, n, "типы операндов различаются: " + tn(ty(a)) + " и " + tn(ty(b)));
                    } else if (!(Types.isScalar(ty(a)) && (Types.isArith(ty(a)) || ty(a) == Types.BOOL))) {
                        err("IR-T02", f, n, "сравнение не определено на " + tn(ty(a)));
                    } else if (t != Types.BOOL) {
                        err("IR-T09", f, n, "результат сравнения должен быть BOOL");
                    }
                }
                case Op.LT, Op.LE, Op.GT, Op.GE -> {
                    if (ty(a) != ty(b)) {
                        err("IR-T01", f, n, "типы операндов различаются: " + tn(ty(a)) + " и " + tn(ty(b)));
                    } else if (!(Types.isScalar(ty(a)) && Types.isArith(ty(a)))) {
                        err("IR-T02", f, n, "упорядочивающее сравнение не определено на " + tn(ty(a)));
                    } else if (t != Types.BOOL) {
                        err("IR-T09", f, n, "результат сравнения должен быть BOOL");
                    }
                }
                case Op.SELECT -> {
                    if (ty(a) != Types.BOOL) {
                        err("IR-T04", f, n, "условие select должно быть BOOL, а не " + tn(ty(a)));
                    }
                    if (ty(b) != ty(c)) {
                        err("IR-T04", f, n, "ветки select имеют разные типы: " + tn(ty(b)) + " и " + tn(ty(c)));
                    } else if (t != ty(b) || t == Types.VOID) {
                        err("IR-T09", f, n, "тип результата select должен совпадать с типом веток и не быть VOID");
                    }
                }
                case Op.CONVERT -> {
                    if (!convertOk(ty(a), t)) {
                        err("IR-T03", f, n, "недопустимое преобразование " + tn(ty(a)) + " -> " + tn(t));
                    }
                }
                case Op.BITCAST -> {
                    if (!bitcastOk(ty(a), t)) {
                        err("IR-T03", f, n, "недопустимый bitcast " + tn(ty(a)) + " -> " + tn(t));
                    }
                }
                case Op.LOCAL_DECL, Op.LOCAL_SET -> {
                    if (a < 0 || a >= m.fLocalCount(f)) {
                        err("IR-S01", f, n, "локаль " + a + " вне диапазона");
                    } else if (b < 0) {
                        err(op == Op.LOCAL_DECL ? "IR-V02" : "IR-S01", f, n,
                                op == Op.LOCAL_DECL ? "объявление локали без инициализатора" : "присваивание без значения");
                    } else {
                        int lt = m.fLocalType(f, a);
                        if (m.typeKind(lt) == Types.KIND_ARRAY) {
                            err("IR-V04", f, n, "массив нельзя объявлять/присваивать как обычную локаль; используйте *_array_decl");
                        } else if (ty(b) != lt) {
                            err("IR-T09", f, n, "тип значения " + tn(ty(b)) + " не совпадает с типом локали " + tn(lt));
                        }
                    }
                }
                case Op.LOCAL_GET -> {
                    if (a < 0 || a >= m.fLocalCount(f)) {
                        err("IR-S01", f, n, "локаль " + a + " вне диапазона");
                    } else if (m.fLocalType(f, a) != t) {
                        err("IR-T09", f, n, "тип узла не совпадает с типом локали");
                    }
                }
                case Op.LOCAL_ADDR -> {
                    if (a < 0 || a >= m.fLocalCount(f)) {
                        err("IR-S01", f, n, "локаль " + a + " вне диапазона");
                    } else {
                        int lt = m.fLocalType(f, a);
                        int lk = m.typeKind(lt);
                        if (!(Types.isScalar(lt) || lk == Types.KIND_VEC || lk == Types.KIND_STRUCT)) {
                            err("IR-V03", f, n, "адрес можно брать только у скаляра, вектора или struct");
                        } else if (m.typeKind(t) != Types.KIND_PTR || m.typeA(t) != AddressSpace.PRIVATE
                                || m.typeB(t) != lt) {
                            err("IR-T09", f, n, "результат local_addr должен быть Ptr<private, " + tn(lt) + ">");
                        }
                    }
                }
                case Op.ELEM_PTR -> {
                    int bt = ty(a);
                    int k = m.typeKind(bt);
                    if (k != Types.KIND_ARRAY && k != Types.KIND_PTR) {
                        err("IR-T05", f, n, "база elem_ptr должна быть массивом или указателем, а не " + tn(bt));
                    } else {
                        if (ty(b) != Types.I32) {
                            err("IR-M06", f, n, "индекс elem_ptr должен быть I32, а не " + tn(ty(b)));
                        }
                        if (m.typeKind(t) != Types.KIND_PTR || m.typeA(t) != m.typeA(bt) || m.typeB(t) != m.typeB(bt)) {
                            err("IR-T09", f, n, "результат elem_ptr должен быть Ptr<space, elem> базы");
                        }
                    }
                }
                case Op.FIELD_PTR -> {
                    int pt = ty(a);
                    if (m.typeKind(pt) != Types.KIND_PTR || m.typeKind(m.typeB(pt)) != Types.KIND_STRUCT) {
                        err("IR-T08", f, n, "field_ptr ожидает указатель на struct, а не " + tn(pt));
                    } else {
                        int st = m.typeB(pt);
                        if (b < 0 || b >= m.structFieldCount(st)) {
                            err("IR-T08", f, n, "индекс поля " + b + " вне 0.." + (m.structFieldCount(st) - 1));
                        } else {
                            int ft = m.structFieldType(st, b);
                            if (m.typeKind(t) != Types.KIND_PTR || m.typeA(t) != m.typeA(pt) || m.typeB(t) != ft) {
                                err("IR-T09", f, n, "результат field_ptr должен быть Ptr<space, " + tn(ft) + ">");
                            }
                        }
                    }
                }
                case Op.LOAD -> {
                    int pt = ty(a);
                    if (m.typeKind(pt) != Types.KIND_PTR) {
                        err("IR-T05", f, n, "load ожидает указатель, а не " + tn(pt));
                    } else if (m.typeB(pt) != t) {
                        err("IR-T05", f, n, "тип результата " + tn(t) + " не совпадает с pointee " + tn(m.typeB(pt)));
                    }
                }
                case Op.STORE -> {
                    int pt = ty(a);
                    if (m.typeKind(pt) != Types.KIND_PTR) {
                        err("IR-T05", f, n, "store ожидает указатель, а не " + tn(pt));
                    } else {
                        if (m.typeB(pt) != ty(b)) {
                            err("IR-T05", f, n, "тип значения " + tn(ty(b)) + " не совпадает с pointee " + tn(m.typeB(pt)));
                        }
                        if (m.typeA(pt) == AddressSpace.CONSTANT) {
                            err("IR-M01", f, n, "запись в constant-память запрещена");
                        }
                    }
                }
                case Op.ARRAY_LEN -> {
                    if (m.typeKind(ty(a)) != Types.KIND_ARRAY) {
                        err("IR-T05", f, n, "array_len ожидает массив, а не " + tn(ty(a)));
                    } else if (t != Types.I32) {
                        err("IR-T09", f, n, "длина массива имеет тип I32");
                    }
                }
                case Op.PRIVATE_ARRAY_DECL, Op.LOCAL_ARRAY_DECL -> {
                    int space = op == Op.PRIVATE_ARRAY_DECL ? AddressSpace.PRIVATE : AddressSpace.LOCAL;
                    String code = op == Op.PRIVATE_ARRAY_DECL ? "IR-M03" : "IR-M02";
                    if (a < 0 || a >= m.fLocalCount(f)) {
                        err("IR-S01", f, n, "локаль " + a + " вне диапазона");
                    } else {
                        int lt = m.fLocalType(f, a);
                        if (m.typeKind(lt) != Types.KIND_ARRAY || m.typeA(lt) != space) {
                            err(code, f, n, "локаль " + a + " должна иметь тип Array<" + AddressSpace.name(space) + ", T>");
                        }
                    }
                    if (b >= 0 && b < nodeCount) {
                        boolean constLen = (m.op(b) == Op.CONST_INT || m.op(b) == Op.SPEC_CONST) && ty(b) == Types.I32;
                        if (!constLen) {
                            err(code, f, n, "длина массива должна быть константой I32");
                        } else if (m.op(b) == Op.CONST_INT && m.a(b) <= 0) {
                            err(code, f, n, "длина массива должна быть положительной");
                        }
                    }
                }
                case Op.SPLAT -> {
                    if (m.typeKind(t) != Types.KIND_VEC || m.typeA(t) != ty(a)) {
                        err("IR-T07", f, n, "splat: результат должен быть вектором со скаляром " + tn(ty(a)));
                    }
                }
                case Op.EXTRACT -> {
                    int vt = ty(a);
                    if (m.typeKind(vt) != Types.KIND_VEC) {
                        err("IR-T07", f, n, "extract ожидает вектор, а не " + tn(vt));
                    } else if (b < 0 || b >= m.typeB(vt)) {
                        err("IR-T07", f, n, "индекс компоненты " + b + " вне 0.." + (m.typeB(vt) - 1));
                    } else if (t != m.typeA(vt)) {
                        err("IR-T09", f, n, "результат extract должен быть скаляром компоненты");
                    }
                }
                case Op.INSERT -> {
                    int vt = ty(a);
                    if (m.typeKind(vt) != Types.KIND_VEC) {
                        err("IR-T07", f, n, "insert ожидает вектор, а не " + tn(vt));
                    } else if (b < 0 || b >= m.typeB(vt)) {
                        err("IR-T07", f, n, "индекс компоненты " + b + " вне 0.." + (m.typeB(vt) - 1));
                    } else if (ty(c) != m.typeA(vt) || t != vt) {
                        err("IR-T07", f, n, "insert: значение должно быть скаляром компоненты, результат - тем же вектором");
                    }
                }
                case Op.SHUFFLE -> {
                    int vt = ty(a);
                    if (m.typeKind(vt) != Types.KIND_VEC) {
                        err("IR-T07", f, n, "shuffle ожидает вектор, а не " + tn(vt));
                    } else {
                        boolean ok = true;
                        for (int i = 0; i < c; i++) {
                            int lane = m.list(b + i);
                            if (lane < 0 || lane >= m.typeB(vt)) {
                                err("IR-T07", f, n, "индекс " + lane + " маски вне 0.." + (m.typeB(vt) - 1));
                                ok = false;
                            }
                        }
                        if (ok && (m.typeKind(t) != Types.KIND_VEC || m.typeA(t) != m.typeA(vt) || m.typeB(t) != c)) {
                            err("IR-T09", f, n, "результат shuffle должен быть вектором той же компоненты длины " + c);
                        }
                    }
                }
                case Op.BUILD -> {
                    if (m.typeKind(t) != Types.KIND_VEC) {
                        err("IR-T07", f, n, "build: результат должен быть вектором");
                    } else if (b != m.typeB(t)) {
                        err("IR-T07", f, n, "build: " + b + " компонент для вектора из " + m.typeB(t));
                    } else {
                        for (int i = 0; i < b; i++) {
                            if (ty(m.list(a + i)) != m.typeA(t)) {
                                err("IR-T07", f, n, "build: компонента " + i + " имеет тип " + tn(ty(m.list(a + i)))
                                        + ", ожидался " + tn(m.typeA(t)));
                            }
                        }
                    }
                }
                case Op.STRUCT_BUILD -> {
                    if (m.typeKind(t) != Types.KIND_STRUCT) {
                        err("IR-T08", f, n, "struct_build: результат должен быть struct");
                    } else if (b != m.structFieldCount(t)) {
                        err("IR-T08", f, n, "struct_build: " + b + " значений для struct из " + m.structFieldCount(t) + " полей");
                    } else {
                        for (int i = 0; i < b; i++) {
                            if (ty(m.list(a + i)) != m.structFieldType(t, i)) {
                                err("IR-T08", f, n, "struct_build: поле " + i + " ожидает " + tn(m.structFieldType(t, i))
                                        + ", получено " + tn(ty(m.list(a + i))));
                            }
                        }
                    }
                }
                case Op.FIELD_GET -> {
                    int st = ty(a);
                    if (m.typeKind(st) != Types.KIND_STRUCT) {
                        err("IR-T08", f, n, "field_get ожидает struct, а не " + tn(st));
                    } else if (b < 0 || b >= m.structFieldCount(st)) {
                        err("IR-T08", f, n, "индекс поля " + b + " вне 0.." + (m.structFieldCount(st) - 1));
                    } else if (t != m.structFieldType(st, b)) {
                        err("IR-T09", f, n, "результат field_get должен иметь тип поля " + tn(m.structFieldType(st, b)));
                    }
                }
                case Op.FIELD_SET -> {
                    int st = ty(a);
                    if (m.typeKind(st) != Types.KIND_STRUCT) {
                        err("IR-T08", f, n, "field_set ожидает struct, а не " + tn(st));
                    } else if (b < 0 || b >= m.structFieldCount(st)) {
                        err("IR-T08", f, n, "индекс поля " + b + " вне 0.." + (m.structFieldCount(st) - 1));
                    } else if (ty(c) != m.structFieldType(st, b) || t != st) {
                        err("IR-T08", f, n, "field_set: значение должно иметь тип поля, результат - тот же struct");
                    }
                }
                case Op.CALL -> {
                    if (a < 0 || a >= funcCount) {
                        err("IR-S01", f, n, "вызов функции f" + a + " вне таблицы");
                    } else if (funcShapeOk[a]) {
                        if (c != m.fParamCount(a)) {
                            err("IR-F02", f, n, "вызов f" + a + ": " + c + " аргументов, ожидалось " + m.fParamCount(a));
                        } else {
                            for (int i = 0; i < c; i++) {
                                int at = ty(m.list(b + i));
                                if (at != m.fParamType(a, i)) {
                                    err("IR-F02", f, n, "вызов f" + a + ": аргумент " + i + " имеет тип " + tn(at)
                                            + ", ожидался " + tn(m.fParamType(a, i)));
                                }
                            }
                        }
                        if (t != m.fRet(a)) {
                            err("IR-T09", f, n, "тип вызова " + tn(t) + " не совпадает с результатом функции " + tn(m.fRet(a)));
                        }
                    }
                }
                case Op.INTRINSIC -> {
                    if (!Builtins.isValid(a)) {
                        err("IR-S01", f, n, "builtin #" + a + " отсутствует в таблице");
                    } else {
                        int[] argTypes = new int[c];
                        for (int i = 0; i < c; i++) {
                            argTypes[i] = ty(m.list(b + i));
                        }
                        int ret = Builtins.resultType(m, a, argTypes, c);
                        if (ret < 0) {
                            err("IR-T10", f, n, "аргументы не подходят к builtin " + Builtins.name(a));
                        } else if (ret != t) {
                            err("IR-T09", f, n, "тип результата builtin " + Builtins.name(a) + " должен быть " + tn(ret));
                        }
                    }
                }
                case Op.BARRIER -> {
                    if (a < 1 || a > 3) {
                        err("IR-S01", f, n, "флаги барьера " + a + " вне 1..3");
                    }
                }
                case Op.IF -> {
                    if (ty(a) != Types.BOOL) {
                        err("IR-T04", f, n, "условие if должно быть BOOL");
                    }
                }
                case Op.LOOP -> {
                    if (ty(a) != Types.BOOL) {
                        err("IR-T04", f, n, "условие loop должно быть BOOL");
                    }
                }
                case Op.DO_WHILE -> {
                    if (ty(b) != Types.BOOL) {
                        err("IR-T04", f, n, "условие do_while должно быть BOOL");
                    }
                }
                case Op.SWITCH -> checkSwitch(f, n);
                default -> {
                }
            }
            // категории операндов (стейтмент/выражение) и использование void-значения
            if (!bad[n]) {
                int cnt = Operands.count(m, n);
                for (int k = 0; k < cnt; k++) {
                    int o = Operands.get(m, n, k);
                    if (o < 0 || o >= nodeCount) {
                        continue;
                    }
                    boolean wantStmt = Operands.operandIsStatement(m, n, k);
                    boolean isStmt = Op.isStatement(m.op(o));
                    if (wantStmt != isStmt) {
                        err("IR-S07", f, n, "операнд " + k + " (n" + o + " " + Op.nameOf(m.op(o)) + ") должен быть "
                                + (wantStmt ? "стейтментом" : "выражением"));
                    } else if (!wantStmt && ty(o) == Types.VOID && op != Op.EXPR_STMT) {
                        err("IR-T09", f, n, "операнд " + k + " (n" + o + ") имеет тип VOID и не может использоваться как значение");
                    }
                }
            }
        }

        private void checkSwitch(int f, int n) {
            int key = m.a(n);
            int off = m.b(n);
            int cnt = m.c(n);
            int kt = ty(key);
            if (!(kt == Types.I32 || kt == Types.U32)) {
                err("IR-C03", f, n, "ключ switch должен быть I32 или U32, а не " + tn(kt));
            }
            int defaults = 0;
            for (int i = 0; i < cnt; i++) {
                int isDef = m.list(off + 3 * i);
                int value = m.list(off + 3 * i + 1);
                if (isDef != 0 && isDef != 1) {
                    err("IR-C03", f, n, "case " + i + ": признак default " + isDef + " не 0/1");
                }
                if (isDef == 1) {
                    defaults++;
                } else {
                    for (int j = 0; j < i; j++) {
                        if (m.list(off + 3 * j) == 0 && m.list(off + 3 * j + 1) == value) {
                            err("IR-C03", f, n, "значение case " + value + " повторяется");
                        }
                    }
                }
            }
            if (defaults > 1) {
                err("IR-C03", f, n, "в switch больше одной ветки default");
            }
        }

        private boolean convertOk(int from, int to) {
            if (Types.isScalar(from) && Types.isScalar(to)) {
                if (from == Types.BOOL || to == Types.BOOL) {
                    int other = from == Types.BOOL ? to : from;
                    return Types.isInteger(other) || other == Types.BOOL;
                }
                return numericScalar(from) && numericScalar(to);
            }
            if (m.typeKind(from) == Types.KIND_VEC && m.typeKind(to) == Types.KIND_VEC) {
                return m.typeB(from) == m.typeB(to) && numericScalar(m.typeA(from)) && numericScalar(m.typeA(to));
            }
            return false;
        }

        private boolean bitcastOk(int from, int to) {
            if (Types.isScalar(from) && Types.isScalar(to)) {
                return numericScalar(from) && numericScalar(to) && Types.bits(from) == Types.bits(to);
            }
            if (m.typeKind(from) == Types.KIND_VEC && m.typeKind(to) == Types.KIND_VEC) {
                return m.typeB(from) == m.typeB(to) && Types.bits(m.typeA(from)) == Types.bits(m.typeA(to));
            }
            return false;
        }

        // ---------------------------------------------------------------------------------------
        // проход 5: граф вызовов (IR-F01)
        // ---------------------------------------------------------------------------------------

        private final List<int[]> callees = new ArrayList<>();
        private int[] callState;
        private final List<Integer> topo = new ArrayList<>();
        private boolean[] inCycle;

        private void checkCallGraph() {
            for (int f = 0; f < funcCount; f++) {
                callees.add(new int[0]);
            }
            List<IntList> lists = new ArrayList<>();
            for (int f = 0; f < funcCount; f++) {
                lists.add(new IntList());
            }
            for (int n = 0; n < nodeCount; n++) {
                if (owner[n] >= 0 && !bad[n] && m.op(n) == Op.CALL && m.a(n) >= 0 && m.a(n) < funcCount) {
                    lists.get(owner[n]).add(m.a(n));
                }
            }
            for (int f = 0; f < funcCount; f++) {
                callees.set(f, lists.get(f).toArray());
            }
            callState = new int[funcCount];
            inCycle = new boolean[funcCount];
            for (int f = 0; f < funcCount; f++) {
                if (callState[f] == 0) {
                    dfs(f);
                }
            }
        }

        private void dfs(int f) {
            callState[f] = 1;
            for (int g : callees.get(f)) {
                if (callState[g] == 1) {
                    inCycle[f] = true;
                    inCycle[g] = true;
                    err("IR-F01", f, -1, "рекурсия: f" + f + " вызывает f" + g + ", который уже в цепочке вызовов");
                } else if (callState[g] == 0) {
                    dfs(g);
                }
            }
            callState[f] = 2;
            topo.add(f);
        }

        // ---------------------------------------------------------------------------------------
        // проход 6: производные флаги функций (в порядке «вызываемые раньше вызывающих»)
        // ---------------------------------------------------------------------------------------

        private void computeFunctionFlags() {
            for (int f : topo) {
                int features = 0;
                boolean sync = false;
                boolean effects = false;
                boolean reads = false;
                boolean divergent = false;
                boolean shape = funcShapeOk[f];
                int pc = shape ? Math.max(0, m.fParamCount(f)) : 0;
                int[] access = new int[pc];
                if (shape) {
                    for (int i = 0; i < pc; i++) {
                        int pt = m.fParamType(f, i);
                        if (usesF64[pt]) {
                            features |= Features.FP64;
                        }
                        int k = m.typeKind(pt);
                        if ((k == Types.KIND_PTR || k == Types.KIND_ARRAY) && m.typeA(pt) == AddressSpace.LOCAL) {
                            sync = true;
                        }
                    }
                    for (int i = 0; i < m.fLocalCount(f); i++) {
                        if (usesF64[m.fLocalType(f, i)]) {
                            features |= Features.FP64;
                        }
                    }
                }
                for (int n = 0; n < nodeCount; n++) {
                    if (owner[n] != f || bad[n]) {
                        continue;
                    }
                    int op = m.op(n);
                    if (typeInRange(m.type(n)) && usesF64[m.type(n)]) {
                        features |= Features.FP64;
                    }
                    switch (op) {
                        case Op.STORE -> {
                            effects = true;
                            markAccess(access, m.a(n), ACCESS_WRITE);
                        }
                        case Op.LOAD -> {
                            reads = true;
                            markAccess(access, m.a(n), ACCESS_READ);
                        }
                        case Op.LOCAL_DECL, Op.LOCAL_SET -> markEscapingPointer(access, m.b(n));
                        case Op.RETURN -> markEscapingPointer(access, m.a(n));
                        case Op.BARRIER -> {
                            effects = true;
                            sync = true;
                        }
                        case Op.LOCAL_ARRAY_DECL -> sync = true;
                        case Op.INTRINSIC -> {
                            int id = m.a(n);
                            if (Builtins.isValid(id)) {
                                if (Builtins.has(id, Builtins.EFFECT)) {
                                    effects = true;
                                }
                                if (Builtins.has(id, Builtins.READS)) {
                                    reads = true;
                                }
                                if (Builtins.has(id, Builtins.DIV)) {
                                    divergent = true;
                                }
                                if (Builtins.has(id, Builtins.ATOMIC) && m.c(n) > 0) {
                                    int pt = ty(m.list(m.b(n)));
                                    if (m.typeKind(pt) == Types.KIND_PTR && Types.bits(m.typeB(pt)) == 64) {
                                        features |= Features.INT64_ATOMICS;
                                    }
                                    markAccess(access, m.list(m.b(n)), ACCESS_READ | ACCESS_WRITE);
                                }
                            }
                        }
                        case Op.CALL -> {
                            int g = m.a(n);
                            if (g >= 0 && g < funcCount) {
                                Validator.FunctionInfo gi = infos[g];
                                if (gi == null || inCycle[g] || inCycle[f]) {
                                    effects = true;
                                    reads = true;
                                } else {
                                    features |= gi.requiredFeatures();
                                    sync |= gi.usesGroupSync();
                                    effects |= gi.effects();
                                    reads |= gi.readsMemory();
                                    divergent |= divergentInside[g];
                                    for (int i = 0; i < m.c(n) && i < gi.paramAccess().length; i++) {
                                        markAccess(access, m.list(m.b(n) + i), gi.paramAccess()[i]);
                                    }
                                }
                            }
                        }
                        default -> {
                        }
                    }
                }
                divergentInside[f] = divergent;
                infos[f] = new Validator.FunctionInfo(features, sync, access, effects, reads);
            }
        }

        /**
         * Указатель на элементы параметра, сохранённый в локали (или возвращённый), дальше читается и пишется по
         * имени локали, а цепочка до параметра там уже не видна. Поэтому режим доступа отмечается в момент сохранения и
         * <b>консервативно</b>: чтение и запись, а для {@code constant} - только чтение. Занижение режима опасно: рантайм
         * не вернул бы записанные данные на хост.
         *
         * @param access режимы параметров текущей функции
         * @param value  сохраняемое либо возвращаемое значение; не указатель (или {@code -1}) игнорируется
         */
        private void markEscapingPointer(int[] access, int value) {
            if (value < 0 || value >= nodeCount || !typeInRange(m.type(value))
                    || m.typeKind(m.type(value)) != Types.KIND_PTR) {
                return;
            }
            int space = m.typeA(m.type(value));
            markAccess(access, value, space == AddressSpace.CONSTANT ? ACCESS_READ : ACCESS_READ | ACCESS_WRITE);
        }

        /** Прослеживает цепочку {@code elem_ptr/field_ptr} до параметра и отмечает режим доступа. */
        private void markAccess(int[] access, int ptrNode, int mode) {
            int p = baseParam(ptrNode);
            if (p >= 0 && p < access.length) {
                access[p] |= mode;
            }
        }

        private int baseParam(int n) {
            int guard = 0;
            while (n >= 0 && n < nodeCount && guard++ < nodeCount) {
                int op = m.op(n);
                if (op == Op.PARAM) {
                    return m.a(n);
                }
                if (op == Op.ELEM_PTR || op == Op.FIELD_PTR) {
                    n = m.a(n);
                } else {
                    return -1;
                }
            }
            return -1;
        }

        // ---------------------------------------------------------------------------------------
        // проход 7: структурный обход (видимость, циклы, return, размещение local-массивов)
        // ---------------------------------------------------------------------------------------

        private int[] declared;       // по локали: глубина области, где объявлена, либо -1
        private IntList declStack;
        private int walkFunc;
        private int loopDepth;
        private int switchDepth;
        private int scopeDepth;

        private void structuralWalk(int f) {
            walkFunc = f;
            declared = new int[Math.max(0, m.fLocalCount(f))];
            java.util.Arrays.fill(declared, -1);
            declStack = new IntList();
            loopDepth = 0;
            switchDepth = 0;
            scopeDepth = 0;
            walkStmt(m.fBody(f), true);
        }

        private void walkScoped(int n) {
            if (n < 0) {
                return;
            }
            enterScope();
            walkStmt(n, false);
            leaveScope();
        }

        private int enterScope() {
            scopeDepth++;
            return declStack.size();
        }

        private void leaveScope() {
            // снимаем объявления, сделанные в этой области
            while (declStack.size() > 0 && declared[declStack.get(declStack.size() - 1)] == scopeDepth) {
                int l = declStack.get(declStack.size() - 1);
                declared[l] = -1;
                declStack.pop();
            }
            scopeDepth--;
        }

        private void declare(int n, int local) {
            if (local < 0 || local >= declared.length) {
                return;
            }
            if (declared[local] >= 0) {
                err("IR-V01", walkFunc, n, "локаль " + local + " уже объявлена в видимой области");
                return;
            }
            declared[local] = scopeDepth;
            declStack.add(local);
        }

        private void walkStmt(int n, boolean isRoot) {
            if (n < 0 || n >= nodeCount || bad[n] || !Op.isStatement(m.op(n))) {
                return;
            }
            int f = walkFunc;
            switch (m.op(n)) {
                case Op.BLOCK -> {
                    enterScope();
                    int cnt = m.b(n);
                    for (int i = 0; i < cnt; i++) {
                        int child = m.list(m.a(n) + i);
                        if (child >= 0 && child < nodeCount && m.op(child) == Op.LOCAL_ARRAY_DECL) {
                            if (!(isRoot && m.fKind(f) == IrModule.KERNEL)) {
                                err("IR-M02", f, child, "local_array_decl допустим только на верхнем уровне тела kernel");
                            }
                        }
                        walkStmt(child, false);
                    }
                    leaveScope();
                }
                case Op.LOCAL_DECL -> {
                    checkExpr(m.b(n));
                    declare(n, m.a(n));
                }
                case Op.PRIVATE_ARRAY_DECL, Op.LOCAL_ARRAY_DECL -> {
                    checkExpr(m.b(n));
                    declare(n, m.a(n));
                }
                case Op.LOCAL_SET -> {
                    checkExpr(m.b(n));
                    requireDeclared(n, m.a(n));
                }
                case Op.STORE -> {
                    checkExpr(m.a(n));
                    checkExpr(m.b(n));
                }
                case Op.EXPR_STMT -> checkExpr(m.a(n));
                case Op.IF -> {
                    checkExpr(m.a(n));
                    walkScoped(m.b(n));
                    walkScoped(m.c(n));
                }
                case Op.LOOP -> {
                    checkExpr(m.a(n));
                    loopDepth++;
                    walkScoped(m.b(n));
                    walkScoped(m.c(n));
                    loopDepth--;
                }
                case Op.DO_WHILE -> {
                    loopDepth++;
                    walkScoped(m.a(n));
                    loopDepth--;
                    checkExpr(m.b(n));
                }
                case Op.SWITCH -> {
                    checkExpr(m.a(n));
                    switchDepth++;
                    for (int i = 0; i < m.c(n); i++) {
                        walkScoped(m.list(m.b(n) + 3 * i + 2));
                    }
                    switchDepth--;
                }
                case Op.BREAK -> {
                    if (loopDepth + switchDepth == 0) {
                        err("IR-C01", f, n, "break вне цикла и switch");
                    }
                }
                case Op.CONTINUE -> {
                    if (loopDepth == 0) {
                        err("IR-C01", f, n, "continue вне цикла");
                    }
                }
                case Op.RETURN -> {
                    int ret = m.fRet(f);
                    int v = m.a(n);
                    if (v >= 0) {
                        checkExpr(v);
                        if (v < nodeCount && ret == Types.VOID) {
                            err("IR-C02", f, n, "return со значением в функции void");
                        } else if (v < nodeCount && ty(v) != ret) {
                            err("IR-C02", f, n, "тип возвращаемого значения " + tn(ty(v)) + " не совпадает с " + tn(ret));
                        }
                    } else if (ret != Types.VOID) {
                        err("IR-C02", f, n, "return без значения в функции с результатом " + tn(ret));
                    }
                }
                default -> {
                }
            }
        }

        private void requireDeclared(int n, int local) {
            if (local < 0 || local >= declared.length) {
                return;
            }
            if (declared[local] < 0) {
                err("IR-V01", walkFunc, n, "локаль " + local + " используется до объявления или вне своей области");
            }
        }

        private void checkExpr(int e) {
            if (e < 0 || e >= nodeCount || bad[e] || exprChecked[e]) {
                return;
            }
            exprChecked[e] = true;
            int cnt = Operands.count(m, e);
            for (int k = 0; k < cnt; k++) {
                int o = Operands.get(m, e, k);
                if (o >= 0 && o < e && !Op.isStatement(m.op(o))) {
                    checkExpr(o);
                }
            }
            int op = m.op(e);
            if (op == Op.LOCAL_GET || op == Op.LOCAL_ADDR) {
                requireDeclared(e, m.a(e));
            }
        }

        // ---------------------------------------------------------------------------------------
        // проход 8: эффекты и «открытые» узлы (IR-E01, IR-E02)
        // ---------------------------------------------------------------------------------------

        private void checkEffectsAndOpenNodes() {
            for (int n = 0; n < nodeCount; n++) {
                if (owner[n] < 0 || bad[n]) {
                    continue;
                }
                int op = m.op(n);
                if (Op.isStatement(op)) {
                    continue;
                }
                boolean isOpen = op == Op.LOCAL_GET || op == Op.LOAD;
                boolean isEffect = false;
                if (op == Op.INTRINSIC && Builtins.isValid(m.a(n))) {
                    isEffect = Builtins.has(m.a(n), Builtins.EFFECT);
                    isOpen |= Builtins.has(m.a(n), Builtins.READS);
                } else if (op == Op.CALL && m.a(n) >= 0 && m.a(n) < funcCount) {
                    Validator.FunctionInfo gi = infos[m.a(n)];
                    if (gi == null) {
                        isEffect = true;
                        isOpen = true;
                    } else {
                        isEffect = gi.effects();
                        isOpen |= gi.readsMemory();
                    }
                }
                int cnt = Operands.count(m, n);
                for (int k = 0; k < cnt; k++) {
                    int o = Operands.get(m, n, k);
                    if (o >= 0 && o < n) {
                        isOpen |= open[o];
                    }
                }
                open[n] = isOpen;
                effectful[n] = isEffect;
                if (isEffect) {
                    boolean placed = parents[n] == 1 && parentOf[n] >= 0
                            && Operands.allowsEffectfulOperand(m, parentOf[n], parentSlot[n]);
                    if (!placed) {
                        err("IR-E01", owner[n], n, "эффектный узел допустим только как значение стейтмента "
                                + "(expr_stmt/local_decl/local_set/store/return) и только с одним родителем");
                    }
                }
                if (isOpen && parents[n] > 1) {
                    err("IR-E02", owner[n], n, "узел читает локаль или память и имеет " + parents[n]
                            + " родителей (допустим один)");
                }
            }
        }

        // ---------------------------------------------------------------------------------------
        // проход 9: барьеры в расходящемся коде (IR-B01)
        // ---------------------------------------------------------------------------------------

        private void checkBarriers(int f) {
            int lc = Math.max(0, m.fLocalCount(f));
            boolean[] localTaint = new boolean[lc];
            boolean changed = true;
            int guard = 0;
            while (changed && guard++ <= lc + 1) {
                changed = false;
                computeTaint(f, localTaint);
                for (int n = 0; n < nodeCount; n++) {
                    if (owner[n] != f || bad[n]) {
                        continue;
                    }
                    int op = m.op(n);
                    if ((op == Op.LOCAL_DECL || op == Op.LOCAL_SET) && m.b(n) >= 0 && m.b(n) < nodeCount
                            && m.a(n) >= 0 && m.a(n) < lc && taint[m.b(n)] && !localTaint[m.a(n)]) {
                        localTaint[m.a(n)] = true;
                        changed = true;
                    }
                }
            }
            computeTaint(f, localTaint);
            barrierWalk(f, m.fBody(f), false);
        }

        private void computeTaint(int f, boolean[] localTaint) {
            for (int n = 0; n < nodeCount; n++) {
                if (owner[n] != f || bad[n] || Op.isStatement(m.op(n))) {
                    taint[n] = false;
                    continue;
                }
                int op = m.op(n);
                boolean t = false;
                if (op == Op.INTRINSIC && Builtins.isValid(m.a(n)) && Builtins.has(m.a(n), Builtins.UNIFORM)) {
                    taint[n] = false;
                    continue;
                }
                if (op == Op.INTRINSIC && Builtins.isValid(m.a(n)) && Builtins.has(m.a(n), Builtins.DIV)) {
                    t = true;
                }
                if (op == Op.LOCAL_GET && m.a(n) >= 0 && m.a(n) < localTaint.length) {
                    t = localTaint[m.a(n)];
                }
                if (op == Op.CALL && m.a(n) >= 0 && m.a(n) < funcCount && divergentInside[m.a(n)]) {
                    t = true;
                }
                int cnt = Operands.count(m, n);
                for (int k = 0; k < cnt && !t; k++) {
                    int o = Operands.get(m, n, k);
                    if (o >= 0 && o < n) {
                        t |= taint[o];
                    }
                }
                taint[n] = t;
            }
        }

        /** Выход через {@code break/continue}: расходимость живёт до конца ближайшего цикла/switch. */
        private static final int EXIT_LOOP = 1;
        /** Выход через {@code return}: расходимость живёт до конца функции. */
        private static final int EXIT_RETURN = 2;

        /**
         * Обходит стейтменты, отслеживая расходимость управления.
         *
         * @param divergent управление уже расходящееся в точке входа в стейтмент
         * @return маска выходов ({@link #EXIT_LOOP}, {@link #EXIT_RETURN}), после которых последующий код
         *         того же уровня выполняется не всеми work-item'ами группы
         */
        private int barrierWalk(int f, int n, boolean divergent) {
            if (n < 0 || n >= nodeCount || bad[n] || !Op.isStatement(m.op(n))) {
                return 0;
            }
            switch (m.op(n)) {
                case Op.BLOCK -> {
                    boolean cur = divergent;
                    int acc = 0;
                    for (int i = 0; i < m.b(n); i++) {
                        int r = barrierWalk(f, m.list(m.a(n) + i), cur);
                        acc |= r;
                        if (r != 0) {
                            cur = true;
                        }
                    }
                    return acc;
                }
                case Op.BARRIER -> {
                    if (divergent) {
                        reportBarrier(f, n, "barrier под расходящимся управлением (условие зависит от getGlobalId/getLocalId)");
                    }
                    return 0;
                }
                case Op.EXPR_STMT, Op.LOCAL_DECL, Op.LOCAL_SET, Op.STORE, Op.RETURN -> {
                    reportCallSync(f, n, divergent);
                    return 0;
                }
                case Op.IF -> {
                    boolean tainted = isTainted(m.a(n));
                    boolean d = divergent || tainted;
                    int r = barrierWalk(f, m.b(n), d) | barrierWalk(f, m.c(n), d);
                    if (tainted) {
                        r |= exitMask(m.b(n)) | exitMask(m.c(n));
                    }
                    return r;
                }
                case Op.LOOP -> {
                    boolean d = divergent || isTainted(m.a(n));
                    int r = barrierWalk(f, m.b(n), d) | barrierWalk(f, m.c(n), d);
                    return r & ~EXIT_LOOP;   // после цикла потоки сходятся (остаётся только return)
                }
                case Op.DO_WHILE -> {
                    boolean d = divergent || isTainted(m.b(n));
                    return barrierWalk(f, m.a(n), d) & ~EXIT_LOOP;
                }
                case Op.SWITCH -> {
                    boolean d = divergent || isTainted(m.a(n));
                    int r = 0;
                    for (int i = 0; i < m.c(n); i++) {
                        r |= barrierWalk(f, m.list(m.b(n) + 3 * i + 2), d);
                    }
                    return r & ~EXIT_LOOP;
                }
                default -> {
                    return 0;
                }
            }
        }

        private boolean isTainted(int n) {
            return n >= 0 && n < nodeCount && taint[n];
        }

        /**
         * Какие выходы встречаются внутри стейтмента. Выходы внутри вложенного цикла или switch
         * ({@code break/continue}) принадлежат этому циклу и наружу не выходят; {@code return} выходит всегда.
         */
        private int exitMask(int n) {
            if (n < 0 || n >= nodeCount || bad[n] || !Op.isStatement(m.op(n))) {
                return 0;
            }
            int op = m.op(n);
            if (op == Op.RETURN) {
                return EXIT_RETURN;
            }
            if (op == Op.BREAK || op == Op.CONTINUE) {
                return EXIT_LOOP;
            }
            int mask = 0;
            int cnt = Operands.count(m, n);
            for (int k = 0; k < cnt; k++) {
                int o = Operands.get(m, n, k);
                if (o >= 0 && o < n && Op.isStatement(m.op(o))) {
                    mask |= exitMask(o);
                }
            }
            if (op == Op.LOOP || op == Op.DO_WHILE || op == Op.SWITCH) {
                mask &= ~EXIT_LOOP;
            }
            return mask;
        }

        private void reportCallSync(int f, int stmt, boolean divergent) {
            if (!divergent) {
                return;
            }
            int cnt = Operands.count(m, stmt);
            for (int k = 0; k < cnt; k++) {
                findSyncCall(f, Operands.get(m, stmt, k));
            }
        }

        private void findSyncCall(int f, int e) {
            if (e < 0 || e >= nodeCount || bad[e] || Op.isStatement(m.op(e))) {
                return;
            }
            if (m.op(e) == Op.CALL && m.a(e) >= 0 && m.a(e) < funcCount && infos[m.a(e)] != null
                    && infos[m.a(e)].usesGroupSync()) {
                reportBarrier(f, e, "вызов функции f" + m.a(e) + " с barrier/local memory под расходящимся управлением");
            }
            int cnt = Operands.count(m, e);
            for (int k = 0; k < cnt; k++) {
                int o = Operands.get(m, e, k);
                if (o >= 0 && o < e) {
                    findSyncCall(f, o);
                }
            }
        }

        private void reportBarrier(int f, int n, String msg) {
            if (opts.strictBarriers()) {
                err("IR-B01", f, n, msg);
            } else {
                warn("IR-B01", f, n, msg);
            }
        }
    }
}
