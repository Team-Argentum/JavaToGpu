package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Свёртка ({@code @Reduction}): распознавание шаблона и построение параллельного ядра (REWORK_ARCHITECTURE.md, 7.1).
 *
 * <p>Исходная функция - последняя функция модуля, device-функция с результатом {@code int/long/float/double}, тело
 * которой имеет вид</p>
 * <pre>{@code
 *   <пролог: объявления и вычисления без записи в память>
 *   T acc = <начальное значение>;          // где угодно в прологе
 *   for (int i = start; i < end; i++) {
 *       <тело: acc меняется только как acc = acc OP e>
 *   }
 *   return acc;
 * }</pre>
 * <p>где {@code OP} - одна операция на всё тело: {@code +} ({@code -=} читается как прибавление {@code -e}),
 * {@code Gpu.min}, {@code Gpu.max}, {@code &}, {@code |}, {@code ^}. Итерации тела независимы: тело не пишет в память
 * (кроме своих private-массивов), не меняет переменные, объявленные вне тела, и не выходит из цикла {@code break} или
 * {@code return}; {@code end} и {@code start} не зависят от тела.</p>
 *
 * <p>Построенное ядро ({@link #lower}) - work-group фиксированного размера, каждый work-item проходит итерации
 * {@code start + gid, start + gid + G, ...} ({@code G} - число work-item'ов запуска), начиная с нейтрального элемента
 * операции (work-item 0 - с исходного начального значения), затем группа складывает свои значения деревом в
 * local-памяти, и work-item 0 группы пишет сумму группы в последний, добавленный параметр {@code partial}. Хосту
 * остаётся свернуть {@code partial} по порядку групп. Порядок сложения отличается от последовательного, поэтому для
 * {@code float/double} результат может отличаться в младших битах: аннотация {@code @Reduction} - согласие на это.</p>
 */
public final class Reductions {

    /** Размер work-group по умолчанию. */
    public static final int DEFAULT_WORK_GROUP = 256;
    /** Код диагностики: тело не подходит под шаблон свёртки. */
    public static final String NOT_A_REDUCTION = "OPT-R01";
    /** Код диагностики: внутренняя ошибка построения ядра. */
    public static final String LOWERING_FAILED = "OPT-R02";

    private Reductions() {
    }

    /** Операция свёртки. */
    public enum Combine {
        /** Сумма ({@code +}, {@code -=} как прибавление противоположного). */
        ADD,
        /** Минимум ({@code Gpu.min}). */
        MIN,
        /** Максимум ({@code Gpu.max}). */
        MAX,
        /** Побитовое И. */
        AND,
        /** Побитовое ИЛИ. */
        OR,
        /** Исключающее ИЛИ. */
        XOR
    }

    /**
     * Граница цикла свёртки, насколько её можно вычислить на хосте до запуска (для выбора размера запуска).
     *
     * @param kind  вид границы
     * @param value константа ({@link Kind#CONST}) либо индекс параметра ({@link Kind#PARAM}, {@link Kind#LENGTH})
     */
    public record Bound(Kind kind, long value) {
        /** Вид границы. */
        public enum Kind {
            /** Константа {@code value}. */
            CONST,
            /** Значение скалярного параметра {@code value}. */
            PARAM,
            /** Длина массива-параметра {@code value}. */
            LENGTH,
            /** На хосте не вычисляется. */
            UNKNOWN
        }

        static final Bound UNKNOWN = new Bound(Kind.UNKNOWN, 0);
    }

    /**
     * Распознанная свёртка.
     *
     * @param combine операция
     * @param type    тип результата ({@code Types.I32/I64/F32/F64})
     * @param start   начало цикла
     * @param end     конец цикла (исключительно)
     */
    public record Plan(Combine combine, int type, Bound start, Bound end) {
    }

    /**
     * Построенное ядро свёртки.
     *
     * @param module       модуль, последняя функция - ядро; его параметры - параметры исходной функции и затем
     *                     {@code partial} (global-массив типа результата, по элементу на группу)
     * @param plan         распознанная свёртка
     * @param workGroup    размер work-group ({@code reqd_work_group_size} ядра)
     * @param partialParam индекс параметра {@code partial}
     */
    public record Lowered(IrModule module, Plan plan, int workGroup, int partialParam) {
    }

    /**
     * Проверяет, что последняя функция модуля - свёртка.
     *
     * @param m модуль фронтенда
     * @return распознанная свёртка либо диагностика {@link #NOT_A_REDUCTION} с причиной
     */
    public static Result<Plan> analyze(IrModule m) {
        try {
            return Result.ok(shape(m).plan());
        } catch (Reject r) {
            return Result.failed(List.of(r.diagnostic));
        }
    }

    /**
     * Строит параллельное ядро свёртки.
     *
     * @param m         модуль фронтенда (последняя функция - свёртка)
     * @param workGroup размер work-group, степень двойки от 1 до 1024
     * @return ядро либо диагностика {@link #NOT_A_REDUCTION} / {@link #LOWERING_FAILED}
     * @throws IllegalArgumentException если {@code workGroup} не степень двойки в [1, 1024]
     */
    public static Result<Lowered> lower(IrModule m, int workGroup) {
        if (workGroup < 1 || workGroup > 1024 || Integer.bitCount(workGroup) != 1) {
            throw new IllegalArgumentException("work-group size must be a power of two in [1, 1024]: " + workGroup);
        }
        Shape s;
        try {
            s = shape(m);
        } catch (Reject r) {
            return Result.failed(List.of(r.diagnostic));
        }
        IrModule lowered;
        try {
            lowered = build(s, workGroup);
        } catch (RuntimeException e) {
            return Result.failed(List.of(new Diagnostic(LOWERING_FAILED, Diagnostic.Severity.ERROR, s.fn, -1,
                    "reduction " + s.name() + ": building the parallel kernel failed: " + e.getMessage())));
        }
        Validator.Report report = Validator.validate(lowered);
        if (report.hasErrors()) {
            return Result.failed(List.of(new Diagnostic(LOWERING_FAILED, Diagnostic.Severity.ERROR, s.fn, -1,
                    "reduction " + s.name() + ": the parallel kernel is invalid: " + report.diagnostics())));
        }
        return Result.ok(new Lowered(lowered, s.plan(), workGroup, s.f.params.length));
    }

    /**
     * Нейтральный элемент операции в сырых битах константы IR (32-битные типы - знаково расширенные биты).
     *
     * @param c    операция
     * @param type {@code Types.I32/I64/F32/F64}
     * @return биты нейтрального элемента
     */
    public static long identityBits(Combine c, int type) {
        return switch (c) {
            case ADD, OR, XOR -> type == Types.F32 ? Float.floatToRawIntBits(0f)
                    : type == Types.F64 ? Double.doubleToRawLongBits(0d) : 0L;
            case AND -> -1L;
            case MIN -> switch (type) {
                case Types.I32 -> Integer.MAX_VALUE;
                case Types.I64 -> Long.MAX_VALUE;
                case Types.F32 -> Float.floatToRawIntBits(Float.POSITIVE_INFINITY);
                default -> Double.doubleToRawLongBits(Double.POSITIVE_INFINITY);
            };
            case MAX -> switch (type) {
                case Types.I32 -> Integer.MIN_VALUE;
                case Types.I64 -> Long.MIN_VALUE;
                case Types.F32 -> Float.floatToRawIntBits(Float.NEGATIVE_INFINITY);
                default -> Double.doubleToRawLongBits(Double.NEGATIVE_INFINITY);
            };
        };
    }

    // =============================================================================================
    // распознавание
    // =============================================================================================

    /** Отказ распознавания: несёт готовую диагностику. */
    private static final class Reject extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final transient Diagnostic diagnostic;

        Reject(Diagnostic d) {
            super(d.message(), null, false, false);
            this.diagnostic = d;
        }
    }

    /** Разобранная свёртка. */
    private static final class Shape {
        Codec.ModuleTree mt;
        int fn;
        FnTree f;
        Tree[] prologue;
        int acc;
        int index;
        Tree start;
        Tree end;
        Tree loopBody;
        Combine combine;
        Bound startBound;
        Bound endBound;

        String name() {
            return f == null || f.name == null ? "f" + fn : f.name;
        }

        Plan plan() {
            return new Plan(combine, f.ret, startBound, endBound);
        }

        Reject reject(String why) {
            return new Reject(new Diagnostic(NOT_A_REDUCTION, Diagnostic.Severity.ERROR, fn, -1,
                    "reduction " + name() + ": " + why));
        }

        String localName(int l) {
            String n = l < f.localNames.length ? f.localNames[l] : null;
            return n == null ? "#" + l : n;
        }
    }

    private static Shape shape(IrModule m) {
        Shape s = new Shape();
        s.fn = m.functionCount() - 1;
        if (s.fn < 0) {
            throw s.reject("the module has no functions");
        }
        try {
            s.mt = Codec.lift(m);
        } catch (UnsupportedOperationException e) {
            throw s.reject(e.getMessage());
        }
        s.f = s.mt.fns[s.fn];
        FnTree f = s.f;
        if (f.kind != IrModule.DEVICE) {
            throw s.reject("the entry function is a kernel, not a reduction");
        }
        if (f.ret != Types.I32 && f.ret != Types.I64 && f.ret != Types.F32 && f.ret != Types.F64) {
            throw s.reject("a reduction returns int, long, float or double");
        }
        Tree[] top = f.body.kids;
        int n = top.length;
        Tree ret = n > 0 ? top[n - 1] : null;
        if (ret == null || ret.op != Op.RETURN || ret.n() != 1 || ret.kid(0).op != Op.LOCAL_GET) {
            throw s.reject("the method must end with `return <accumulator>;` right after the loop");
        }
        s.acc = ret.kid(0).imm;
        Tree loopBlock = n > 1 ? top[n - 2] : null;
        if (loopBlock == null || loopBlock.op != Op.BLOCK || loopBlock.n() != 2
                || loopBlock.kid(0).op != Op.LOCAL_DECL || loopBlock.kid(1).op != Op.LOOP) {
            throw s.reject("the statement before `return` must be a loop `for (int i = start; i < end; i++)`");
        }
        s.prologue = Arrays.copyOf(top, n - 2);
        Tree decl = loopBlock.kid(0);
        Tree loop = loopBlock.kid(1);
        s.index = decl.imm;
        s.start = decl.kid(0);
        if (f.localType(s.index) != Types.I32) {
            throw s.reject("the loop index must be an int");
        }
        Tree cond = loop.kid(0);
        if (cond.op != Op.LT || cond.kid(0).op != Op.LOCAL_GET || cond.kid(0).imm != s.index) {
            throw s.reject("the loop condition must be `i < end`");
        }
        s.end = cond.kid(1);
        Tree update = loop.n() > 2 ? loop.kid(2) : null;
        if (update != null && update.op == Op.BLOCK && update.n() == 1) {
            update = update.kid(0);
        }
        if (update == null || update.op != Op.LOCAL_SET || update.imm != s.index || update.kid(0).op != Op.ADD
                || update.kid(0).kid(0).op != Op.LOCAL_GET || update.kid(0).kid(0).imm != s.index
                || !isIntConst(update.kid(0).kid(1), 1)) {
            throw s.reject("the loop must step by one: `i++`");
        }
        s.loopBody = loop.kid(1);
        if (f.localType(s.acc) != f.ret) {
            throw s.reject("the accumulator " + s.localName(s.acc) + " must have the result type");
        }
        boolean declared = false;
        for (Tree t : s.prologue) {
            declared |= t.op == Op.LOCAL_DECL && t.imm == s.acc;
        }
        if (!declared) {
            throw s.reject("the accumulator " + s.localName(s.acc) + " must be declared before the loop");
        }

        Calls calls = Calls.analyze(s.mt);
        for (Tree t : s.prologue) {
            checkReplicated(s, calls, t, "before the loop");
        }
        checkReplicated(s, calls, s.start, "in the loop start");
        boolean[] bodyLocal = new boolean[f.localCount];
        markDeclared(s.loopBody, bodyLocal);
        checkInvariant(s, calls, s.end, bodyLocal);
        checkBody(s, calls, s.loopBody, bodyLocal, 0);
        if (s.combine == null) {
            throw s.reject("the loop never updates the accumulator " + s.localName(s.acc));
        }
        s.startBound = bound(s, s.start);
        s.endBound = bound(s, s.end);
        return s;
    }

    private static boolean isIntConst(Tree t, long v) {
        return t.op == Op.CONST_INT && t.k == v;
    }

    /** Код пролога и {@code start}: исполняется в каждом work-item'е, поэтому не должен писать в память вне private. */
    private static void checkReplicated(Shape s, Calls calls, Tree t, String where) {
        switch (t.op) {
            case Op.RETURN -> throw s.reject("`return` " + where + " is not supported");
            case Op.STORE -> {
                if (!isPrivateRoot(s, t.kid(0), null)) {
                    throw s.reject("writing memory " + where + " is not supported: it would run in every work-item");
                }
            }
            case Op.CALL -> checkCall(s, calls, t, null, where);
            case Op.LOCAL_SET, Op.LOCAL_DECL, Op.LOCAL_GET, Op.LOCAL_ADDR -> {
                if (t.imm == s.index) {
                    throw s.reject("the loop index is used " + where);
                }
            }
            default -> checkCommon(s, t, where);
        }
        for (Tree c : t.kids) {
            checkReplicated(s, calls, c, where);
        }
    }

    /** {@code end} вычисляется заново на каждой итерации: он не должен зависеть от тела и счётчика. */
    private static void checkInvariant(Shape s, Calls calls, Tree t, boolean[] bodyLocal) {
        if ((t.op == Op.LOCAL_GET || t.op == Op.LOCAL_ADDR)
                && (t.imm == s.acc || t.imm == s.index || bodyLocal[t.imm])) {
            throw s.reject("the loop end must not depend on the loop index or the accumulator");
        }
        if (t.op == Op.STORE || t.op == Op.LOCAL_SET || t.op == Op.LOCAL_DECL) {
            throw s.reject("the loop end must have no side effects");
        }
        if (t.op == Op.CALL) {
            checkCall(s, calls, t, new boolean[s.f.localCount], "in the loop end");
            if (calls.writes(t.imm)) {
                throw s.reject("the loop end must have no side effects");
            }
        } else {
            checkCommon(s, t, "in the loop end");
        }
        for (Tree c : t.kids) {
            checkInvariant(s, calls, c, bodyLocal);
        }
    }

    /** Общие запреты: барьеры, local-память, атомики, индексы work-item'а. */
    private static void checkCommon(Shape s, Tree t, String where) {
        switch (t.op) {
            case Op.BARRIER -> throw s.reject("barriers " + where + " are not supported");
            case Op.LOCAL_ARRAY_DECL -> throw s.reject("local memory " + where + " is not supported");
            case Op.INTRINSIC -> {
                if (Builtins.has(t.imm, Builtins.EFFECT)) {
                    throw s.reject(Builtins.name(t.imm) + " " + where + " is not supported: iterations run in parallel");
                }
                if (isWorkItemQuery(t.imm)) {
                    throw s.reject(Builtins.name(t.imm) + " " + where
                            + " is not supported: a reduction does not choose its work-items");
                }
            }
            default -> {
            }
        }
    }

    private static boolean isWorkItemQuery(int builtin) {
        String n = Builtins.name(builtin);
        return n.startsWith("get") && (n.endsWith("Id") || n.endsWith("Size") || n.equals("getNumGroups")
                || n.equals("getWorkDim"));
    }

    private static void checkCall(Shape s, Calls calls, Tree t, boolean[] bodyLocal, String where) {
        int g = t.imm;
        if (calls.sync[g]) {
            throw s.reject("function " + fnName(s, g) + " called " + where
                    + " uses barriers, local memory, atomics or work-item indices");
        }
        if (calls.unknown[g]) {
            throw s.reject("function " + fnName(s, g) + " called " + where + " writes memory through a pointer it does "
                    + "not get as a parameter");
        }
        for (int i = 0; i < t.n(); i++) {
            if (calls.writes[g][i] && !isPrivateRoot(s, t.kid(i), bodyLocal)) {
                throw s.reject("function " + fnName(s, g) + " called " + where + " writes to its parameter " + i
                        + (bodyLocal == null ? ", which is not private memory" : ", which is not declared in the loop body"));
            }
        }
    }

    private static String fnName(Shape s, int g) {
        String n = s.mt.fns[g].name;
        return n == null ? "f" + g : n;
    }

    /**
     * Указатель ведёт в private-память. С {@code bodyLocal} - ещё и в переменную, объявленную в теле цикла (иначе
     * запись связала бы итерации).
     */
    private static boolean isPrivateRoot(Shape s, Tree ptr, boolean[] bodyLocal) {
        Tree t = ptr;
        while (t.op == Op.ELEM_PTR || t.op == Op.FIELD_PTR) {
            t = t.kid(0);
        }
        if (t.op == Op.LOCAL_ADDR) {
            return bodyLocal == null || bodyLocal[t.imm];
        }
        if (t.op == Op.LOCAL_GET) {
            int type = s.f.localType(t.imm);
            boolean priv = (s.mt.source.typeKind(type) == Types.KIND_ARRAY
                    || s.mt.source.typeKind(type) == Types.KIND_PTR)
                    && s.mt.source.typeA(type) == AddressSpace.PRIVATE;
            return priv && (bodyLocal == null || bodyLocal[t.imm]);
        }
        return false;
    }

    private static void markDeclared(Tree t, boolean[] out) {
        if (t.op == Op.LOCAL_DECL || t.op == Op.PRIVATE_ARRAY_DECL) {
            out[t.imm] = true;
        }
        for (Tree c : t.kids) {
            markDeclared(c, out);
        }
    }

    /**
     * Тело цикла: аккумулятор меняется только как {@code acc = acc OP e}, другие внешние переменные и память не
     * меняются, выхода из цикла нет.
     *
     * @param breakable глубина вложенных циклов и switch (break внутри них не выходит из цикла свёртки)
     */
    private static void checkBody(Shape s, Calls calls, Tree t, boolean[] bodyLocal, int breakable) {
        switch (t.op) {
            case Op.LOCAL_SET -> {
                if (t.imm == s.acc) {
                    Tree e = updateOperand(s, t.kid(0));
                    checkBody(s, calls, e, bodyLocal, breakable);
                    return;
                }
                if (t.imm == s.index) {
                    throw s.reject("the loop index must not be changed in the loop body");
                }
                if (!bodyLocal[t.imm]) {
                    throw s.reject("variable " + s.localName(t.imm) + " is declared before the loop and changed in it: "
                            + "iterations of a reduction must be independent");
                }
            }
            case Op.LOCAL_GET -> {
                if (t.imm == s.acc) {
                    throw s.reject("the accumulator " + s.localName(s.acc) + " may only be updated as `acc = acc op e`"
                            + " (+=, -=, ++, Gpu.min, Gpu.max, &=, |=, ^=), not read otherwise");
                }
            }
            case Op.LOCAL_ADDR -> {
                if (t.imm == s.acc || t.imm == s.index || !bodyLocal[t.imm]) {
                    throw s.reject("taking a reference to " + s.localName(t.imm) + " in the loop body is not supported");
                }
            }
            case Op.STORE -> {
                if (!isPrivateRoot(s, t.kid(0), bodyLocal)) {
                    throw s.reject("writing memory in the loop body is not supported: iterations run in parallel");
                }
            }
            case Op.CALL -> checkCall(s, calls, t, bodyLocal, "in the loop body");
            case Op.RETURN -> throw s.reject("`return` in the loop body is not supported");
            case Op.BREAK -> {
                if (breakable == 0) {
                    throw s.reject("`break` out of the reduction loop is not supported: iterations run in parallel");
                }
            }
            case Op.LOOP, Op.DO_WHILE, Op.SWITCH -> {
                for (Tree c : t.kids) {
                    checkBody(s, calls, c, bodyLocal, breakable + 1);
                }
                return;
            }
            default -> checkCommon(s, t, "in the loop body");
        }
        for (Tree c : t.kids) {
            checkBody(s, calls, c, bodyLocal, breakable);
        }
    }

    /** Разбирает {@code acc = acc OP e}, запоминает операцию и возвращает {@code e}. */
    private static Tree updateOperand(Shape s, Tree v) {
        Combine c;
        Tree a;
        Tree b;
        if (v.op == Op.INTRINSIC && v.n() == 2
                && (v.imm == Builtins.idOf("min") || v.imm == Builtins.idOf("max"))) {
            c = v.imm == Builtins.idOf("min") ? Combine.MIN : Combine.MAX;
            a = v.kid(0);
            b = v.kid(1);
        } else if (v.op == Op.ADD || v.op == Op.SUB || v.op == Op.AND || v.op == Op.OR || v.op == Op.XOR) {
            c = switch (v.op) {
                case Op.AND -> Combine.AND;
                case Op.OR -> Combine.OR;
                case Op.XOR -> Combine.XOR;
                default -> Combine.ADD;
            };
            a = v.kid(0);
            b = v.kid(1);
        } else {
            throw s.reject("the accumulator " + s.localName(s.acc) + " may only be updated as `acc = acc op e`"
                    + " (+=, -=, ++, Gpu.min, Gpu.max, &=, |=, ^=)");
        }
        if (v.type != s.f.ret) {
            throw s.reject("the accumulator update changes the type");
        }
        Tree e;
        if (isAcc(s, a)) {
            e = b;
        } else if (isAcc(s, b) && v.op != Op.SUB) {
            e = a;
        } else {
            throw s.reject("the accumulator " + s.localName(s.acc) + " may only be updated as `acc = acc op e`");
        }
        if (s.combine != null && s.combine != c) {
            throw s.reject("the accumulator is updated with different operations (" + s.combine + " and " + c + ")");
        }
        s.combine = c;
        return e;
    }

    private static boolean isAcc(Shape s, Tree t) {
        return t.op == Op.LOCAL_GET && t.imm == s.acc;
    }

    /** Граница, вычислимая на хосте: константа, параметр, длина массива-параметра (в том числе через локаль пролога). */
    private static Bound bound(Shape s, Tree t) {
        for (int depth = 0; depth < 8 && t.op == Op.LOCAL_GET; depth++) {
            Tree init = singleDefinition(s, t.imm);
            if (init == null) {
                return Bound.UNKNOWN;
            }
            t = init;
        }
        if (t.op == Op.CONST_INT && t.type == Types.I32) {
            return new Bound(Bound.Kind.CONST, t.k);
        }
        if (t.op == Op.PARAM && t.type == Types.I32) {
            return new Bound(Bound.Kind.PARAM, t.imm);
        }
        if (t.op == Op.ARRAY_LEN && t.kid(0).op == Op.PARAM) {
            return new Bound(Bound.Kind.LENGTH, t.kid(0).imm);
        }
        return Bound.UNKNOWN;
    }

    /** Значение локали, которой присваивают один раз - объявлением на верхнем уровне пролога. */
    private static Tree singleDefinition(Shape s, int l) {
        int[] writes = new int[1];
        countWrites(s.f.body, l, writes);
        if (writes[0] != 1) {
            return null;
        }
        for (Tree t : s.prologue) {
            if (t.op == Op.LOCAL_DECL && t.imm == l) {
                return t.kid(0);
            }
        }
        return null;
    }

    private static void countWrites(Tree t, int l, int[] out) {
        if ((t.op == Op.LOCAL_DECL || t.op == Op.LOCAL_SET) && t.imm == l) {
            out[0]++;
        }
        if (t.op == Op.LOCAL_ADDR && t.imm == l) {
            out[0] += 2; // по ссылке могут писать
        }
        for (Tree c : t.kids) {
            countWrites(c, l, out);
        }
    }

    /** Что функции модуля делают с памятью (неподвижная точка по графу вызовов). */
    private static final class Calls {
        /** Барьер, local-память, атомики или индексы work-item'а (транзитивно). */
        boolean[] sync;
        /** Пишет через указатель, происхождение которого неизвестно. */
        boolean[] unknown;
        /** {@code writes[f][p]} - функция пишет через параметр {@code p}. */
        boolean[][] writes;

        boolean writes(int f) {
            for (boolean w : writes[f]) {
                if (w) {
                    return true;
                }
            }
            return unknown[f];
        }

        static Calls analyze(Codec.ModuleTree mt) {
            int n = mt.fns.length;
            Calls c = new Calls();
            c.sync = new boolean[n];
            c.unknown = new boolean[n];
            c.writes = new boolean[n][];
            for (int f = 0; f < n; f++) {
                c.writes[f] = new boolean[mt.fns[f].params.length];
            }
            boolean changed = true;
            while (changed) {
                changed = false;
                for (int f = 0; f < n; f++) {
                    changed |= c.scan(mt, f, mt.fns[f].body);
                }
            }
            return c;
        }

        private boolean scan(Codec.ModuleTree mt, int f, Tree t) {
            boolean changed = false;
            switch (t.op) {
                case Op.BARRIER, Op.LOCAL_ARRAY_DECL -> changed |= set(sync, f);
                case Op.INTRINSIC -> {
                    if (Builtins.has(t.imm, Builtins.EFFECT) || isWorkItemQuery(t.imm)) {
                        changed |= set(sync, f);
                    }
                }
                case Op.STORE -> changed |= written(mt, f, t.kid(0));
                case Op.CALL -> {
                    int g = t.imm;
                    if (sync[g]) {
                        changed |= set(sync, f);
                    }
                    if (unknown[g]) {
                        changed |= set(unknown, f);
                    }
                    for (int i = 0; i < t.n(); i++) {
                        if (writes[g][i]) {
                            changed |= written(mt, f, t.kid(i));
                        }
                    }
                }
                default -> {
                }
            }
            for (Tree k : t.kids) {
                changed |= scan(mt, f, k);
            }
            return changed;
        }

        /** Запись через указатель {@code ptr} в функции {@code f}. */
        private boolean written(Codec.ModuleTree mt, int f, Tree ptr) {
            Tree t = ptr;
            while (t.op == Op.ELEM_PTR || t.op == Op.FIELD_PTR) {
                t = t.kid(0);
            }
            if (t.op == Op.PARAM) {
                if (!writes[f][t.imm]) {
                    writes[f][t.imm] = true;
                    return true;
                }
                return false;
            }
            if (t.op == Op.LOCAL_ADDR) {
                return false;
            }
            if (t.op == Op.LOCAL_GET) {
                int type = mt.fns[f].localType(t.imm);
                if (mt.source.typeA(type) == AddressSpace.PRIVATE) {
                    return false;
                }
            }
            return set(unknown, f);
        }

        private static boolean set(boolean[] a, int i) {
            if (a[i]) {
                return false;
            }
            a[i] = true;
            return true;
        }
    }

    // =============================================================================================
    // построение ядра
    // =============================================================================================

    private static IrModule build(Shape s, int wg) {
        Codec.ModuleTree mt = s.mt;
        int t = s.f.ret;
        int localArray = mt.typeId(Types.KIND_ARRAY, AddressSpace.LOCAL, t);
        int localPtr = mt.typeId(Types.KIND_PTR, AddressSpace.LOCAL, t);
        int globalArray = mt.typeId(Types.KIND_ARRAY, AddressSpace.GLOBAL, t);
        int globalPtr = mt.typeId(Types.KIND_PTR, AddressSpace.GLOBAL, t);

        FnTree k = s.f.copy();
        k.kind = IrModule.KERNEL;
        k.ret = Types.VOID;
        int partial = k.params.length;
        k.params = Arrays.copyOf(k.params, partial + 1);
        k.params[partial] = globalArray;
        if (k.paramNames != null) {
            k.paramNames = Arrays.copyOf(k.paramNames, partial + 1);
            k.paramNames[partial] = "partial";
        }
        k.wgx = wg;
        k.wgy = 1;
        k.wgz = 1;
        int tile = k.newLocal(localArray, "tile");
        int lid = k.newLocal(Types.I32, "lid");
        int gid = k.newLocal(Types.I32, "gid");
        int gsz = k.newLocal(Types.I32, "gsz");

        List<Tree> out = new ArrayList<>();
        out.add(Tree.ofImm(Op.LOCAL_ARRAY_DECL, Types.VOID, tile, i32(wg)));
        out.add(Tree.ofImm(Op.LOCAL_DECL, Types.VOID, lid, workItem("getLocalId")));
        out.add(Tree.ofImm(Op.LOCAL_DECL, Types.VOID, gid, workItem("getGlobalId")));
        out.add(Tree.ofImm(Op.LOCAL_DECL, Types.VOID, gsz, workItem("getGlobalSize")));
        out.addAll(Arrays.asList(s.prologue));
        // начальное значение входит в сумму один раз - у work-item'а 0, остальные начинают с нейтрального элемента
        Tree identity = Tree.constant(Types.isFloat(t) ? Op.CONST_FLOAT : Op.CONST_INT, t, identityBits(s.combine, t));
        out.add(Tree.of(Op.IF, Types.VOID, Tree.of(Op.NE, Types.BOOL, get(k, gid), i32(0)),
                Tree.of(Op.BLOCK, Types.VOID, Tree.ofImm(Op.LOCAL_SET, Types.VOID, s.acc, identity))));
        // шаг по сетке без переполнения: i = end - i > G ? i + G : end
        Tree i = get(k, s.index);
        Tree next = Tree.of(Op.SELECT, Types.I32,
                Tree.of(Op.GT, Types.BOOL, Tree.of(Op.SUB, Types.I32, s.end, i), get(k, gsz)),
                Tree.of(Op.ADD, Types.I32, i, get(k, gsz)), s.end);
        Tree loop = Tree.of(Op.LOOP, Types.VOID, Tree.of(Op.LT, Types.BOOL, i, s.end), rewriteUpdates(s, s.loopBody),
                Tree.ofImm(Op.LOCAL_SET, Types.VOID, s.index, next));
        out.add(Tree.of(Op.BLOCK, Types.VOID,
                Tree.ofImm(Op.LOCAL_DECL, Types.VOID, s.index, Tree.of(Op.ADD, Types.I32, s.start, get(k, gid))), loop));
        // дерево в local-памяти
        out.add(Tree.of(Op.STORE, Types.VOID, Tree.of(Op.ELEM_PTR, localPtr, get(k, tile), get(k, lid)), get(k, s.acc)));
        out.add(barrier());
        for (int step = wg / 2; step > 0; step >>= 1) {
            Tree mine = Tree.of(Op.ELEM_PTR, localPtr, get(k, tile), get(k, lid));
            Tree other = Tree.of(Op.ELEM_PTR, localPtr, get(k, tile), Tree.of(Op.ADD, Types.I32, get(k, lid), i32(step)));
            Tree sum = combine(s.combine, t, Tree.of(Op.LOAD, t, mine), Tree.of(Op.LOAD, t, other));
            out.add(Tree.of(Op.IF, Types.VOID, Tree.of(Op.LT, Types.BOOL, get(k, lid), i32(step)),
                    Tree.of(Op.BLOCK, Types.VOID, Tree.of(Op.STORE, Types.VOID, mine, sum))));
            out.add(barrier());
        }
        Tree slot = Tree.of(Op.ELEM_PTR, globalPtr, Tree.ofImm(Op.PARAM, globalArray, partial), workItem("getGroupId"));
        Tree first = Tree.of(Op.LOAD, t, Tree.of(Op.ELEM_PTR, localPtr, get(k, tile), i32(0)));
        out.add(Tree.of(Op.IF, Types.VOID, Tree.of(Op.EQ, Types.BOOL, get(k, lid), i32(0)),
                Tree.of(Op.BLOCK, Types.VOID, Tree.of(Op.STORE, Types.VOID, slot, first))));
        k.body = Tree.of(Op.BLOCK, Types.VOID, out.toArray(Tree.NONE));

        FnTree[] fns = Arrays.copyOf(mt.fns, mt.fns.length + 1);
        fns[s.fn] = s.f.copy();
        fns[s.fn].alive = false;
        fns[fns.length - 1] = k;
        return Codec.lower(new Codec.ModuleTree(mt.source, fns, mt.extraTypes));
    }

    private static Tree get(FnTree fn, int local) {
        return Tree.ofImm(Op.LOCAL_GET, fn.localType(local), local);
    }

    private static Tree i32(long v) {
        return Tree.constant(Op.CONST_INT, Types.I32, v);
    }

    private static Tree workItem(String builtin) {
        return Tree.ofImm(Op.INTRINSIC, Types.I32, Builtins.idOf(builtin), i32(0));
    }

    private static Tree barrier() {
        return Tree.ofImm(Op.BARRIER, Types.VOID, 1);
    }

    private static Tree combine(Combine c, int type, Tree a, Tree b) {
        return switch (c) {
            case ADD -> Tree.of(Op.ADD, type, a, b);
            case AND -> Tree.of(Op.AND, type, a, b);
            case OR -> Tree.of(Op.OR, type, a, b);
            case XOR -> Tree.of(Op.XOR, type, a, b);
            case MIN -> Tree.ofImm(Op.INTRINSIC, type, Builtins.idOf("min"), a, b);
            case MAX -> Tree.ofImm(Op.INTRINSIC, type, Builtins.idOf("max"), a, b);
        };
    }

    /** {@code acc = acc - e} становится {@code acc = acc + (-e)}: сумма частичных значений складывается. */
    private static Tree rewriteUpdates(Shape s, Tree t) {
        if (t.op == Op.LOCAL_SET && t.imm == s.acc && t.kid(0).op == Op.SUB) {
            Tree v = t.kid(0);
            return t.withKid(0, Tree.of(Op.ADD, v.type, v.kid(0), Tree.of(Op.NEG, v.type, v.kid(1))));
        }
        Tree[] kids = t.kids;
        Tree[] nk = null;
        for (int i = 0; i < kids.length; i++) {
            Tree r = rewriteUpdates(s, kids[i]);
            if (r != kids[i]) {
                if (nk == null) {
                    nk = kids.clone();
                }
                nk[i] = r;
            }
        }
        return nk == null ? t : t.withKids(nk);
    }
}
