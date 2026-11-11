package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.Diagnostic;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Result;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Слияние «отображение + свёртка» (REWORK_ARCHITECTURE.md, 7.1): ядро-производитель, которое пишет каждый элемент
 * массива независимо, подставляется в {@code @Reduction}, читающую этот массив, - массив не пишется в память вовсе.
 *
 * <p><b>Производитель</b> ({@link #producer}) - ядро без {@code reqd_work_group_size}, тело которого -
 * {@code <пролог>; out[gid] = value;}: индекс - {@code getGlobalId(0)} (или локаль, которой он присвоен один раз на
 * верхнем уровне), пролог и {@code value} не пишут в память (кроме private), не вызывают функций, которые пишут через
 * параметры, не используют барьеров, local-памяти, атомиков и других индексов work-item'а, не читают {@code out} и не
 * выходят {@code return}. Такое ядро при запуске размера {@code out.length} - отображение: элемент {@code i} равен
 * {@code value} при {@code gid = i}, и его можно вычислить где угодно и сколько угодно раз.</p>
 *
 * <p><b>Слияние</b> ({@link #fuse}): ядро становится device-функцией {@code produce(int i, входы...)}, возвращающей
 * {@code value}; в свёртке каждое чтение {@code arr[e]} заменяется вызовом {@code produce(e, ...)}, а
 * {@code arr.length} - новым параметром {@code outLen}. Функции производителя переносятся в модуль свёртки (типы
 * перерегистрируются, struct-типы не поддержаны). Параметры слитой свёртки: параметры исходной свёртки без
 * {@code arr}, затем параметры производителя без {@code out}, затем {@code outLen}.</p>
 */
public final class Fusion {

    /** Код диагностики: слияние неприменимо (форма ядра или свёртки не подходит). */
    public static final String NOT_FUSABLE = "OPT-F01";

    private static final int GET_GLOBAL_ID = Builtins.idOf("getGlobalId");

    private Fusion() {
    }

    /**
     * Ядро-производитель.
     *
     * @param outParam параметр-массив, который ядро перезаписывает поэлементно
     */
    public record Producer(int outParam) {
    }

    /**
     * Проверяет, что последняя функция модуля - ядро-производитель.
     *
     * @param m модуль фронтенда
     * @return производитель либо пусто
     */
    public static Optional<Producer> producer(IrModule m) {
        try {
            Codec.ModuleTree mt = Codec.lift(m);
            Shape s = shape(mt, mt.fns.length - 1);
            return s == null ? Optional.empty() : Optional.of(new Producer(s.out));
        } catch (UnsupportedOperationException e) {
            return Optional.empty();
        }
    }

    /**
     * Строит свёртку, в которой чтения массива {@code readParam} заменены вычислением производителя.
     *
     * @param producer  модуль ядра-производителя (последняя функция)
     * @param reduction модуль свёртки (последняя функция - {@code @Reduction})
     * @param readParam параметр свёртки, которому передан выход производителя
     * @return модуль слитой свёртки (последняя функция) либо {@link #NOT_FUSABLE} с причиной
     */
    public static Result<IrModule> fuse(IrModule producer, IrModule reduction, int readParam) {
        try {
            return Result.ok(build(producer, reduction, readParam));
        } catch (NotFusable e) {
            return Result.failed(List.of(new Diagnostic(NOT_FUSABLE, Diagnostic.Severity.ERROR, -1, -1, e.getMessage())));
        } catch (UnsupportedOperationException e) {
            return Result.failed(List.of(new Diagnostic(NOT_FUSABLE, Diagnostic.Severity.ERROR, -1, -1,
                    "fusion: " + e.getMessage())));
        }
    }

    // =============================================================================================
    // производитель
    // =============================================================================================

    /** Отказ слияния с причиной. */
    private static final class NotFusable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        NotFusable(String why) {
            super("fusion: " + why, null, false, false);
        }
    }

    /** Разобранное ядро-производитель. */
    private static final class Shape {
        int fn;
        int out;
        /** Стейтменты до записи. */
        Tree[] prologue;
        /** Записываемое значение. */
        Tree value;
    }

    /** Разбор ядра {@code f}; {@code null}, если оно не производитель. */
    private static Shape shape(Codec.ModuleTree mt, int f) {
        FnTree fn = mt.fns[f];
        if (fn.kind != IrModule.KERNEL || fn.wgx > 0 || fn.wgy > 0 || fn.wgz > 0) {
            return null;
        }
        Tree[] top = fn.body.kids;
        int n = top.length;
        while (n > 0 && top[n - 1].op == Op.RETURN && top[n - 1].n() == 0) {
            n--; // завершающий return; void-ядра
        }
        if (n == 0 || top[n - 1].op != Op.STORE || top[n - 1].kid(0).op != Op.ELEM_PTR) {
            return null;
        }
        Tree ptr = top[n - 1].kid(0);
        if (ptr.kid(0).op != Op.PARAM || mt.source.typeKind(fn.params[ptr.kid(0).imm]) != Types.KIND_ARRAY) {
            return null;
        }
        Shape s = new Shape();
        s.fn = f;
        s.out = ptr.kid(0).imm;
        s.prologue = Arrays.copyOf(top, n - 1);
        s.value = top[n - 1].kid(1);
        int[] writes = new int[fn.localCount];
        countWrites(fn.body, writes);
        boolean[] gidLocal = new boolean[fn.localCount];
        for (Tree t : s.prologue) {
            if (t.op == Op.LOCAL_DECL && t.n() == 1 && writes[t.imm] == 1 && isGid(t.kid(0), gidLocal)) {
                gidLocal[t.imm] = true;
            }
        }
        if (!isGid(ptr.kid(1), gidLocal)) {
            return null;
        }
        boolean[] pure = pureFunctions(mt);
        for (Tree t : s.prologue) {
            if (!pureTree(mt, fn, t, s.out, pure)) {
                return null;
            }
        }
        if (!pureTree(mt, fn, s.value, s.out, pure)) {
            return null;
        }
        return s;
    }

    private static boolean isGid(Tree e, boolean[] gidLocal) {
        if (e.op == Op.LOCAL_GET) {
            return gidLocal[e.imm];
        }
        return e.op == Op.INTRINSIC && e.imm == GET_GLOBAL_ID && e.n() == 1 && e.kid(0).op == Op.CONST_INT
                && e.kid(0).k == 0;
    }

    /**
     * Код пролога или значения: без записи в память (кроме private), выхода, синхронизации, других индексов
     * work-item'а и без обращений к {@code out}; вызываемые функции чистые.
     */
    private static boolean pureTree(Codec.ModuleTree mt, FnTree fn, Tree t, int out, boolean[] pure) {
        switch (t.op) {
            case Op.RETURN, Op.BARRIER, Op.LOCAL_ARRAY_DECL -> {
                return false;
            }
            case Op.STORE -> {
                if (!privateRoot(mt, fn, t.kid(0))) {
                    return false;
                }
            }
            case Op.PARAM -> {
                if (t.imm == out) {
                    return false;
                }
            }
            case Op.CALL -> {
                if (!pure[t.imm]) {
                    return false;
                }
            }
            case Op.INTRINSIC -> {
                if (Builtins.has(t.imm, Builtins.EFFECT)) {
                    return false;
                }
                if (isWorkItemQuery(t.imm) && !(t.imm == GET_GLOBAL_ID && t.n() == 1 && t.kid(0).op == Op.CONST_INT
                        && t.kid(0).k == 0)) {
                    return false;
                }
            }
            default -> {
            }
        }
        for (Tree c : t.kids) {
            if (!pureTree(mt, fn, c, out, pure)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWorkItemQuery(int builtin) {
        String n = Builtins.name(builtin);
        return n.startsWith("get") && (n.endsWith("Id") || n.endsWith("Size") || n.equals("getNumGroups")
                || n.equals("getWorkDim"));
    }

    /** Указатель ведёт в private-память функции (локаль либо private-массив). */
    private static boolean privateRoot(Codec.ModuleTree mt, FnTree fn, Tree ptr) {
        Tree t = ptr;
        while (t.op == Op.ELEM_PTR || t.op == Op.FIELD_PTR) {
            t = t.kid(0);
        }
        if (t.op == Op.LOCAL_ADDR) {
            return true;
        }
        if (t.op == Op.LOCAL_GET) {
            int type = fn.localType(t.imm);
            int kind = mt.source.typeKind(type);
            return (kind == Types.KIND_ARRAY || kind == Types.KIND_PTR) && mt.source.typeA(type) == AddressSpace.PRIVATE;
        }
        return false;
    }

    /**
     * Функции, вызов которых ничего не меняет вне себя: пишут только в private-память (параметры-указатели -
     * не private), без синхронизации, атомиков и индексов work-item'а (неподвижная точка по графу вызовов).
     */
    private static boolean[] pureFunctions(Codec.ModuleTree mt) {
        int n = mt.fns.length;
        boolean[] pure = new boolean[n];
        Arrays.fill(pure, true);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int f = 0; f < n; f++) {
                if (pure[f] && (mt.fns[f].kind == IrModule.KERNEL || !pureBody(mt, mt.fns[f], mt.fns[f].body, pure))) {
                    pure[f] = false;
                    changed = true;
                }
            }
        }
        return pure;
    }

    private static boolean pureBody(Codec.ModuleTree mt, FnTree fn, Tree t, boolean[] pure) {
        switch (t.op) {
            case Op.BARRIER, Op.LOCAL_ARRAY_DECL -> {
                return false;
            }
            case Op.STORE -> {
                if (!privateRoot(mt, fn, t.kid(0))) {
                    return false;
                }
            }
            case Op.CALL -> {
                if (!pure[t.imm]) {
                    return false;
                }
            }
            case Op.INTRINSIC -> {
                if (Builtins.has(t.imm, Builtins.EFFECT) || isWorkItemQuery(t.imm)) {
                    return false;
                }
            }
            default -> {
            }
        }
        for (Tree c : t.kids) {
            if (!pureBody(mt, fn, c, pure)) {
                return false;
            }
        }
        return true;
    }

    private static void countWrites(Tree t, int[] writes) {
        if (t.op == Op.LOCAL_DECL || t.op == Op.LOCAL_SET) {
            writes[t.imm]++;
        }
        if (t.op == Op.LOCAL_ADDR) {
            writes[t.imm] += 2; // по ссылке могут писать
        }
        for (Tree c : t.kids) {
            countWrites(c, writes);
        }
    }

    // =============================================================================================
    // слияние
    // =============================================================================================

    private static IrModule build(IrModule producer, IrModule reduction, int readParam) {
        Codec.ModuleTree pt = Codec.lift(producer);
        Shape s = shape(pt, pt.fns.length - 1);
        if (s == null) {
            throw new NotFusable("the kernel is not an element-wise producer `out[gid] = value`");
        }
        Codec.ModuleTree rt = Codec.lift(reduction);
        int nr = rt.fns.length;
        FnTree red = rt.fns[nr - 1];
        if (red.kind != IrModule.DEVICE || readParam < 0 || readParam >= red.params.length) {
            throw new NotFusable("the reduction has no parameter " + readParam);
        }
        int readType = red.params[readParam];
        FnTree pk = pt.fns[s.fn];
        int outType = pk.params[s.out];
        if (rt.source.typeKind(readType) != Types.KIND_ARRAY
                || rt.source.typeB(readType) != pt.source.typeB(outType)
                || !Types.isScalar(pt.source.typeB(outType))) {
            throw new NotFusable("the reduction parameter and the produced array have different element types");
        }
        int elem = pt.source.typeB(outType);

        // функции производителя в нумерации нового модуля: [свёртка без последней] + [производитель] + [свёртка]
        int offset = nr - 1;
        int np = pt.fns.length;
        int reductionIndex = offset + np;
        TypeMap types = new TypeMap(pt.source, rt);
        FnTree[] fns = new FnTree[offset + np + 1];
        for (int f = 0; f < offset; f++) {
            fns[f] = rt.fns[f];
        }
        for (int f = 0; f < np; f++) {
            FnTree c = pt.fns[f].copy();
            if (f == s.fn) {
                continue;
            }
            retype(c, types, offset);
            fns[offset + f] = c;
        }
        // ядро -> produce(int i, параметры без out) -> value
        int produceIndex = offset + s.fn;
        FnTree produce = pk.copy();
        produce.kind = IrModule.DEVICE;
        produce.wgx = 0;
        produce.wgy = 0;
        produce.wgz = 0;
        produce.fallback = -1;
        produce.name = (pk.name == null ? "producer" : pk.name) + "$produce";
        int[] inputs = new int[pk.params.length - 1];
        for (int p = 0, j = 0; p < pk.params.length; p++) {
            if (p != s.out) {
                inputs[j++] = p;
            }
        }
        produce.params = new int[inputs.length + 1];
        produce.params[0] = Types.I32;
        String[] names = new String[produce.params.length];
        names[0] = "i";
        for (int j = 0; j < inputs.length; j++) {
            produce.params[j + 1] = pk.params[inputs[j]];
            names[j + 1] = pk.paramNames == null ? null : pk.paramNames[inputs[j]];
        }
        produce.paramNames = pk.paramNames == null ? null : names;
        produce.ret = elem;
        Tree[] body = Arrays.copyOf(s.prologue, s.prologue.length + 1);
        body[s.prologue.length] = Tree.of(Op.RETURN, Types.VOID, s.value);
        int out = s.out;
        produce.body = Tree.of(Op.BLOCK, Types.VOID, body);
        produce.body = mapProducer(produce.body, out);
        retype(produce, types, offset);
        fns[produceIndex] = produce;

        // свёртка: чтения arr[e] -> produce(e, ...), arr.length -> outLen
        FnTree r = red.copy();
        int keep = red.params.length - 1;
        int[] newParams = new int[keep + inputs.length + 1];
        String[] newNames = new String[newParams.length];
        for (int p = 0, j = 0; p < red.params.length; p++) {
            if (p != readParam) {
                newParams[j] = red.params[p];
                newNames[j] = red.paramNames == null ? null : red.paramNames[p];
                j++;
            }
        }
        for (int j = 0; j < inputs.length; j++) {
            newParams[keep + j] = types.map(pk.params[inputs[j]]);
            String pn = pk.paramNames == null ? null : pk.paramNames[inputs[j]];
            newNames[keep + j] = "fused_" + (pn == null ? "p" + inputs[j] : pn);
        }
        int outLen = keep + inputs.length;
        newParams[outLen] = Types.I32;
        newNames[outLen] = "fused_length";
        r.params = newParams;
        r.paramNames = newNames;
        Tree[] extra = new Tree[inputs.length];
        for (int j = 0; j < inputs.length; j++) {
            extra[j] = Tree.ofImm(Op.PARAM, newParams[keep + j], keep + j);
        }
        r.body = mapReduction(r.body, readParam, produceIndex, elem, extra, outLen);
        fns[reductionIndex] = r;

        IrModule fused = Codec.lower(new Codec.ModuleTree(rt.source, fns, rt.extraTypes));
        Validator.Report report = Validator.validate(fused);
        if (report.hasErrors()) {
            throw new NotFusable("the fused module is invalid: " + report.diagnostics());
        }
        return fused;
    }

    /** Тело производителя: {@code getGlobalId(0)} -> параметр {@code i}, параметры сдвинуты ({@code out} убран). */
    private static Tree mapProducer(Tree t, int out) {
        if (t.op == Op.INTRINSIC && t.imm == GET_GLOBAL_ID) {
            return Tree.ofImm(Op.PARAM, Types.I32, 0);
        }
        if (t.op == Op.PARAM) {
            if (t.imm == out) {
                throw new NotFusable("the producer reads its output");
            }
            return t.withImm(t.imm < out ? t.imm + 1 : t.imm);
        }
        return mapKids(t, c -> mapProducer(c, out));
    }

    /** Тело свёртки: чтения выхода -> вызов {@code produce}, длина -> {@code outLen}, параметры сдвинуты. */
    private static Tree mapReduction(Tree t, int read, int produce, int elem, Tree[] extra, int outLen) {
        if (t.op == Op.LOAD && t.kid(0).op == Op.ELEM_PTR && t.kid(0).kid(0).op == Op.PARAM
                && t.kid(0).kid(0).imm == read) {
            Tree index = mapReduction(t.kid(0).kid(1), read, produce, elem, extra, outLen);
            Tree[] args = new Tree[extra.length + 1];
            args[0] = index;
            System.arraycopy(extra, 0, args, 1, extra.length);
            return new Tree(Op.CALL, elem, produce, 0, args, Tree.NO_INTS, t.line, t.col);
        }
        if (t.op == Op.ARRAY_LEN && t.kid(0).op == Op.PARAM && t.kid(0).imm == read) {
            return Tree.ofImm(Op.PARAM, Types.I32, outLen);
        }
        if (t.op == Op.PARAM) {
            if (t.imm == read) {
                throw new NotFusable("the reduction uses the produced array other than as `a.get(i)` or `a.length()`");
            }
            return t.withImm(t.imm < read ? t.imm : t.imm - 1);
        }
        return mapKids(t, c -> mapReduction(c, read, produce, elem, extra, outLen));
    }

    private static Tree mapKids(Tree t, java.util.function.UnaryOperator<Tree> f) {
        Tree[] nk = null;
        for (int i = 0; i < t.kids.length; i++) {
            Tree r = f.apply(t.kids[i]);
            if (r != t.kids[i]) {
                if (nk == null) {
                    nk = t.kids.clone();
                }
                nk[i] = r;
            }
        }
        return nk == null ? t : t.withKids(nk);
    }

    /** Перерегистрация типов функции производителя в таблице модуля свёртки и сдвиг индексов вызовов. */
    private static void retype(FnTree f, TypeMap types, int callOffset) {
        f.ret = types.map(f.ret);
        for (int i = 0; i < f.params.length; i++) {
            f.params[i] = types.map(f.params[i]);
        }
        for (int i = 0; i < f.localCount; i++) {
            f.locals[i] = types.map(f.locals[i]);
        }
        f.fallback = -1;
        f.body = retypeTree(f.body, types, callOffset);
    }

    private static Tree retypeTree(Tree t, TypeMap types, int callOffset) {
        Tree[] nk = t.kids.length == 0 ? t.kids : new Tree[t.kids.length];
        for (int i = 0; i < nk.length; i++) {
            nk[i] = retypeTree(t.kids[i], types, callOffset);
        }
        int imm = t.op == Op.CALL ? t.imm + callOffset : t.imm;
        return new Tree(t.op, types.map(t.type), imm, t.k, nk, t.ints, t.line, t.col);
    }

    /** Отображение типов модуля производителя в таблицу модуля свёртки. */
    private static final class TypeMap {
        private final IrModule from;
        private final Codec.ModuleTree to;
        private final int[] memo;

        TypeMap(IrModule from, Codec.ModuleTree to) {
            this.from = from;
            this.to = to;
            this.memo = new int[from.typeCount()];
            Arrays.fill(memo, -1);
        }

        int map(int t) {
            if (t < Types.FIRST_DYNAMIC) {
                return t;
            }
            if (memo[t] >= 0) {
                return memo[t];
            }
            int r = switch (from.typeKind(t)) {
                case Types.KIND_VEC -> to.typeId(Types.KIND_VEC, from.typeA(t), from.typeB(t));
                case Types.KIND_PTR, Types.KIND_ARRAY -> to.typeId(from.typeKind(t), from.typeA(t), map(from.typeB(t)));
                default -> throw new NotFusable("the producer uses a @Struct type, which fusion does not support");
            };
            memo[t] = r;
            return r;
        }
    }
}
