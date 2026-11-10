package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.AddressSpace;
import com.sixik.gpu.ir.Builtins;
import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * Разбиение «тяжёлого» ядра на несколько запусков (kernel fission) для снижения давления на регистры.
 *
 * <p>Когда пиковая оценка регистров ядра выше бюджета ({@link OptimizeOptions#registerBudget()}) даже после
 * оптимизации, драйвер вынужден «проливать» значения в память, а заполняемость устройства падает. Альтернатива -
 * разрезать тело ядра между стейтментами верхнего уровня на стадии {@code S0, S1, ...}: стадия {@code j} исполняется для
 * <b>всех</b> work-item'ов до начала стадии {@code j+1}; значения локалей, живых на границе, передаются через
 * <b>scratch-буферы</b> в global-памяти (по одному элементу на work-item).</p>
 *
 * <h2>Контракт запуска (обязан соблюдать рантайм)</h2>
 * <ol>
 *   <li>Все стадии имеют одинаковую сигнатуру: параметры исходного ядра, затем scratch-буферы
 *       ({@link Scratch#paramIndex()}, {@code Array<global, elementType>}).</li>
 *   <li>Длина каждого scratch-буфера - число work-item'ов запуска, умноженное на
 *       {@link Scratch#elementsPerWorkItem()}; элемент work-item'а лежит по индексу
 *       {@code id * elementsPerWorkItem + k}, где {@code id = gid0 + gsz0 * (gid1 + gsz1 * gid2)}.</li>
 *   <li>Стадии запускаются подряд с одним и тем же NDRange; следующая стартует после завершения предыдущей
 *       (in-order очередь либо явная зависимость).</li>
 * </ol>
 *
 * <h2>Когда разрез допустим</h2>
 * <ul>
 *   <li>В ядре нет {@code local}-массивов (shared memory не переживает запуск);</li>
 *   <li>{@code return} встречается только в последней стадии (иначе work-item, вышедший раньше, продолжил бы работу в
 *       следующей стадии);</li>
 *   <li>через границу не проходят массивы, локали с взятым адресом, указатели и struct, значения которых нужно
 *       инициализировать нулём (то есть не живые, но ссылающиеся позже) - их нельзя прозрачно передать;</li>
 *   <li>гонок между work-item'ами в исходном ядре нет: порядок «стадия за стадией» - одно из допустимых исполнений ядра без
 *       гонок, а гонки и так не определены.</li>
 * </ul>
 *
 * <h2>Выбор границ</h2>
 * <p>Динамическое программирование по границам стейтментов верхнего уровня минимизирует число стадий и суммарный трафик
 * через scratch ({@code 32} слов штрафа за стадию плюс слова загрузок и сохранений) при условии, что оценка давления
 * каждой стадии не выше бюджета. В стадии учитываются только переменные, которых она касается: живая, но не затронутая
 * стадией переменная остаётся в scratch и регистров не занимает.</p>
 */
public final class KernelSplitter {

    private static final int STAGE_PENALTY = 32;
    private static final int MAX_STATEMENTS = 400;

    private KernelSplitter() {
    }

    /**
     * Scratch-буфер, который рантайм обязан выделить и передать каждой стадии. Все передаваемые локали одного типа
     * упакованы в один буфер: элемент локали номер {@code k} work-item'а с линейным id {@code id} лежит по индексу
     * {@code id * elementsPerWorkItem + k}.
     *
     * @param paramIndex          индекс параметра стадии (одинаков у всех стадий)
     * @param elementType         id типа элемента массива
     * @param elementsPerWorkItem сколько элементов нужно каждому work-item'у (длина буфера - это число work-item'ов,
     *                            умноженное на это значение)
     */
    public record Scratch(int paramIndex, int elementType, int elementsPerWorkItem) {
    }

    /**
     * Описание выполненного разбиения.
     *
     * @param originalKernel    индекс ядра во входном модуле
     * @param stages            индексы стадий в выходном модуле в порядке запуска
     * @param scratch           scratch-буферы (общие для всех стадий)
     * @param stageRegisters    оценка регистров каждой стадии
     * @param originalRegisters оценка регистров исходного ядра
     */
    public record KernelSplit(int originalKernel, int[] stages, List<Scratch> scratch, int[] stageRegisters,
                              int originalRegisters) {
    }

    /**
     * Результат попытки разбиения.
     *
     * @param module выходной модуль (входной, если разбиение не выполнено)
     * @param split  описание разбиения либо {@code null}
     * @param reason причина отказа (пусто, если разбиение выполнено)
     */
    public record SplitResult(IrModule module, KernelSplit split, String reason) {
        /**
         * @return {@code true}, если ядро разбито
         */
        public boolean isSplit() {
            return split != null;
        }
    }

    private static SplitResult no(IrModule m, String reason) {
        return new SplitResult(m, null, reason);
    }

    /**
     * Пытается разбить ядро, превышающее бюджет регистров.
     *
     * @param m       корректный модуль (желательно уже оптимизированный)
     * @param kernel  индекс ядра
     * @param options настройки (бюджет регистров)
     * @return результат; {@link SplitResult#isSplit()} - удалось ли
     */
    public static SplitResult split(IrModule m, int kernel, OptimizeOptions options) {
        if (kernel < 0 || kernel >= m.functionCount() || m.fKind(kernel) != IrModule.KERNEL) {
            return no(m, "function " + kernel + " is not a kernel");
        }
        Codec.ModuleTree mt;
        try {
            mt = Codec.lift(m);
        } catch (UnsupportedOperationException e) {
            return no(m, "unsupported module: " + e.getMessage());
        }
        FnTree fn = mt.fns[kernel];
        int budget = options.registerBudget();
        Effects fx = Effects.analyze(mt);
        int original = RegisterPressure.analyze(fn, kernel, m, fx).registers();
        if (original <= budget) {
            return no(m, "kernel fits the register budget (" + original + " <= " + budget + ")");
        }
        if (Costs.contains(fn.body, Op.LOCAL_ARRAY_DECL)) {
            return no(m, "kernel uses local (shared) memory, which does not survive a launch boundary");
        }
        for (FnTree f : mt.fns) {
            if (f.fallback == kernel) {
                return no(m, "kernel is the fallback of another function");
            }
        }
        int n = fn.body.kids.length;
        if (n < 2) {
            return no(m, "the body has fewer than two top-level statements");
        }
        if (n > MAX_STATEMENTS) {
            return no(m, "too many top-level statements (" + n + ")");
        }
        Plan plan = Plan.of(fn, m, fx);
        int[] cuts = plan.chooseCuts(budget);
        if (cuts == null || cuts.length == 0) {
            return no(m, "no valid cut reduces the pressure below the budget");
        }
        return plan.build(mt, kernel, cuts, original, m, fx);
    }

    // =============================================================================================
    // анализ границ
    // =============================================================================================

    private static final class Plan {
        final FnTree fn;
        final IrModule src;
        final int n;
        final int locals;
        final boolean[] tracked;
        final int[] varWeight;
        final int[] declTop;
        final int[] lastRef;
        final BitSet[] touched;
        final BitSet[] assigned;
        final BitSet[] liveIn;
        final List<List<RegisterPressure.Point>> points;
        final boolean[] cutOk;
        final int firstReturn;
        /** Параметр, смещение и шаг scratch-хранилища каждой передаваемой локали (заполняет {@code build}). */
        int[] sParam;
        int[] sOffset;
        int[] sStride;

        private Plan(FnTree fn, IrModule src, Effects fx) {
            this.fn = fn;
            this.src = src;
            this.n = fn.body.kids.length;
            this.locals = fn.localCount;
            this.tracked = Locals.tracked(fn, src);
            this.varWeight = RegisterPressure.variableWeights(fn, src);
            this.declTop = new int[locals];
            this.lastRef = new int[locals];
            Arrays.fill(declTop, -1);
            Arrays.fill(lastRef, -1);
            this.touched = new BitSet[n];
            this.assigned = new BitSet[n];
            int firstRet = Integer.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                Tree s = fn.body.kids[i];
                touched[i] = new BitSet();
                assigned[i] = new BitSet();
                Locals.used(s, touched[i], locals);
                Locals.assigned(s, assigned[i]);
                touched[i].or(assigned[i]);
                int[] counts = new int[locals];
                Locals.readCounts(s, counts);
                for (int l = 0; l < locals; l++) {
                    if (counts[l] > 0) {
                        touched[i].set(l);
                    }
                }
                for (int l = touched[i].nextSetBit(0); l >= 0 && l < locals; l = touched[i].nextSetBit(l + 1)) {
                    lastRef[l] = i;
                }
                if (Simplify.isDecl(s) && declTop[s.imm] < 0) {
                    declTop[s.imm] = i;
                }
                if (Costs.contains(s, Op.RETURN)) {
                    firstRet = Math.min(firstRet, i);
                }
            }
            this.firstReturn = firstRet;
            BitSet[] ins = Liveness.topLevelLiveIn(fn, src, fx);
            this.liveIn = ins;
            this.points = RegisterPressure.pointsPerTop(fn, src, fx);
            this.cutOk = new boolean[n + 1];
            for (int c = 1; c < n; c++) {
                cutOk[c] = computeCutOk(c);
            }
        }

        static Plan of(FnTree fn, IrModule src, Effects fx) {
            return new Plan(fn, src, fx);
        }

        private boolean computeCutOk(int c) {
            if (c > firstReturn) {
                return false;
            }
            for (int l = 0; l < locals; l++) {
                if (declTop[l] < 0 || declTop[l] >= c || lastRef[l] < c) {
                    continue;
                }
                int t = fn.locals[l];
                if (!tracked[l] || !Costs.storable(src, t)) {
                    return false;
                }
                boolean live = liveIn[c].get(l);
                if (!live && !(Types.isScalar(t) || Types.kind(src, t) == Types.KIND_VEC)) {
                    return false;
                }
            }
            return true;
        }

        /** Локали, затрагиваемые стадией, объявленные раньше неё. */
        BitSet carried(int a, int b) {
            BitSet r = new BitSet();
            for (int i = a; i < b; i++) {
                for (int l = touched[i].nextSetBit(0); l >= 0 && l < locals; l = touched[i].nextSetBit(l + 1)) {
                    if (declTop[l] >= 0 && declTop[l] < a) {
                        r.set(l);
                    }
                }
            }
            return r;
        }

        BitSet loads(int a, int b) {
            BitSet r = carried(a, b);
            r.and(liveIn[a]);
            return r;
        }

        BitSet stores(int a, int b) {
            BitSet r = new BitSet();
            if (b >= n) {
                return r;
            }
            for (int i = a; i < b; i++) {
                r.or(assigned[i]);
            }
            r.and(liveIn[b]);
            // сохраняем только то, что объявлено до конца стадии
            for (int l = r.nextSetBit(0); l >= 0; l = r.nextSetBit(l + 1)) {
                if (declTop[l] < 0 || declTop[l] >= b) {
                    r.clear(l);
                }
            }
            return r;
        }

        int stagePressure(int a, int b) {
            BitSet t = new BitSet();
            for (int i = a; i < b; i++) {
                t.or(touched[i]);
            }
            BitSet l = loads(a, b);
            BitSet s = stores(a, b);
            int scratchArrays = l.cardinality() + s.cardinality();
            int worst = 0;
            for (int i = a; i < b; i++) {
                for (RegisterPressure.Point p : points.get(i)) {
                    BitSet held = (BitSet) p.across().clone();
                    held.and(t);
                    worst = Math.max(worst, RegisterPressure.weight(held, varWeight) + p.need());
                }
            }
            return worst + 1 + (scratchArrays > 0 ? 3 : 0);
        }

        long spillWords(int a, int b) {
            long w = 0;
            BitSet l = loads(a, b);
            BitSet s = stores(a, b);
            for (int v = l.nextSetBit(0); v >= 0; v = l.nextSetBit(v + 1)) {
                w += varWeight[v];
            }
            for (int v = s.nextSetBit(0); v >= 0; v = s.nextSetBit(v + 1)) {
                w += varWeight[v];
            }
            return w;
        }

        /** Динамическое программирование по границам; {@code null}, если улучшения нет. */
        int[] chooseCuts(int budget) {
            long inf = Long.MAX_VALUE / 4;
            long[] dp = new long[n + 1];
            int[] from = new int[n + 1];
            Arrays.fill(dp, inf);
            dp[0] = 0;
            for (int b = 1; b <= n; b++) {
                if (b < n && !cutOk[b]) {
                    continue;
                }
                for (int a = b - 1; a >= 0; a--) {
                    if (a > 0 && !cutOk[a]) {
                        continue;
                    }
                    if (dp[a] >= inf) {
                        continue;
                    }
                    int pressure = stagePressure(a, b);
                    long cost = STAGE_PENALTY + spillWords(a, b);
                    if (pressure > budget) {
                        if (b - a > 1) {
                            continue;
                        }
                        cost += 1000L + 8L * (pressure - budget);
                    }
                    if (dp[a] + cost < dp[b]) {
                        dp[b] = dp[a] + cost;
                        from[b] = a;
                    }
                }
            }
            if (dp[n] >= inf) {
                return null;
            }
            List<Integer> cuts = new ArrayList<>();
            for (int b = n; b > 0; b = from[b]) {
                if (from[b] > 0) {
                    cuts.add(from[b]);
                }
            }
            int[] r = new int[cuts.size()];
            for (int i = 0; i < r.length; i++) {
                r[i] = cuts.get(r.length - 1 - i);
            }
            return r;
        }

        // ---- построение ------------------------------------------------------------------------

        SplitResult build(Codec.ModuleTree mt, int kernel, int[] cuts, int originalRegisters, IrModule m,
                          Effects fx) {
            int stages = cuts.length + 1;
            int[] bounds = new int[stages + 1];
            for (int j = 1; j < stages; j++) {
                bounds[j] = cuts[j - 1];
            }
            bounds[stages] = n;

            // scratch-буфер на каждую передаваемую локаль
            BitSet spilled = new BitSet();
            for (int j = 0; j < stages; j++) {
                spilled.or(loads(bounds[j], bounds[j + 1]));
                spilled.or(stores(bounds[j], bounds[j + 1]));
            }
            int baseParams = fn.params.length;
            sParam = new int[locals];
            sOffset = new int[locals];
            sStride = new int[locals];
            Arrays.fill(sParam, -1);
            // по буферу на тип элемента (BOOL хранится как I32)
            List<Integer> elemTypes = new ArrayList<>();
            for (int l = spilled.nextSetBit(0); l >= 0; l = spilled.nextSetBit(l + 1)) {
                int elem = fn.locals[l] == Types.BOOL ? Types.I32 : fn.locals[l];
                if (!elemTypes.contains(elem)) {
                    elemTypes.add(elem);
                }
            }
            int[] newParams = Arrays.copyOf(fn.params, baseParams + elemTypes.size());
            List<Scratch> scratch = new ArrayList<>();
            for (int g = 0; g < elemTypes.size(); g++) {
                int elem = elemTypes.get(g);
                int count = 0;
                for (int l = spilled.nextSetBit(0); l >= 0; l = spilled.nextSetBit(l + 1)) {
                    if ((fn.locals[l] == Types.BOOL ? Types.I32 : fn.locals[l]) == elem) {
                        count++;
                    }
                }
                int seen = 0;
                for (int l = spilled.nextSetBit(0); l >= 0; l = spilled.nextSetBit(l + 1)) {
                    if ((fn.locals[l] == Types.BOOL ? Types.I32 : fn.locals[l]) == elem) {
                        sParam[l] = baseParams + g;
                        sOffset[l] = seen++;
                        sStride[l] = count;
                    }
                }
                newParams[baseParams + g] = mt.typeId(Types.KIND_ARRAY, AddressSpace.GLOBAL, elem);
                scratch.add(new Scratch(baseParams + g, elem, count));
            }
            int[] scratchParam = sParam;

            FnTree[] stageFns = new FnTree[stages];
            for (int j = 0; j < stages; j++) {
                stageFns[j] = stage(mt, j, bounds[j], bounds[j + 1], newParams, scratchParam);
            }

            // новая таблица функций: стадии занимают место ядра
            int delta = stages - 1;
            for (FnTree sf : stageFns) {
                sf.body = remapCalls(sf.body, kernel, delta);
                if (sf.fallback > kernel) {
                    sf.fallback += delta;
                }
            }
            FnTree[] fns = new FnTree[mt.fns.length + delta];
            int pos = 0;
            int[] stageIndex = new int[stages];
            for (int f = 0; f < mt.fns.length; f++) {
                if (f == kernel) {
                    for (int j = 0; j < stages; j++) {
                        stageIndex[j] = pos;
                        fns[pos++] = stageFns[j];
                    }
                } else {
                    FnTree c = mt.fns[f].copy();
                    c.body = remapCalls(c.body, kernel, delta);
                    if (c.fallback > kernel) {
                        c.fallback += delta;
                    }
                    fns[pos++] = c;
                }
            }
            Codec.ModuleTree out = new Codec.ModuleTree(mt.source, fns, new ArrayList<>(mt.extraTypes));
            IrModule lowered;
            try {
                lowered = Codec.lower(out);
            } catch (RuntimeException e) {
                return no(m, "cannot build the split module: " + e.getMessage());
            }
            Validator.Report r = Validator.validate(lowered);
            if (r.hasErrors()) {
                return no(m, "the split module is invalid: " + Optimizer.firstError(r));
            }
            // индексы стадий в собранном модуле (сборка могла переставить функции)
            int[] order = Codec.emissionOrder(out);
            int[] inResult = new int[stages];
            for (int j = 0; j < stages; j++) {
                for (int i = 0; i < order.length; i++) {
                    if (order[i] == stageIndex[j]) {
                        inResult[j] = i;
                    }
                }
            }
            RegisterPressure.Report rp = RegisterPressure.analyze(lowered);
            int[] regs = new int[stages];
            int worst = 0;
            for (int j = 0; j < stages; j++) {
                regs[j] = rp.functions()[inResult[j]].registers();
                worst = Math.max(worst, regs[j]);
            }
            if (worst >= originalRegisters) {
                return no(m, "splitting does not reduce the estimated pressure (" + originalRegisters + " -> "
                        + worst + ")");
            }
            return new SplitResult(lowered, new KernelSplit(kernel, inResult, scratch, regs, originalRegisters), "");
        }

        private FnTree stage(Codec.ModuleTree mt, int j, int a, int b, int[] params, int[] scratchParam) {
            FnTree s = fn.copy();
            s.params = params;
            s.name = (fn.name == null ? "kernel" : fn.name) + "$s" + j;
            if (fn.paramNames != null) {
                String[] pn = Arrays.copyOf(fn.paramNames, params.length);
                for (int i = fn.paramNames.length; i < pn.length; i++) {
                    pn[i] = "scratch" + i;
                }
                s.paramNames = pn;
            }
            int slot = s.newLocal(Types.I32, "slot");
            List<Tree> body = new ArrayList<>();
            body.add(Tree.ofImm(Op.LOCAL_DECL, Types.VOID, slot, linearId()));

            BitSet load = loads(a, b);
            BitSet carried = carried(a, b);
            for (int l = carried.nextSetBit(0); l >= 0; l = carried.nextSetBit(l + 1)) {
                Tree init = load.get(l) ? loadFrom(mt, l, scratchParam[l], slot) : zero(l);
                body.add(Tree.ofImm(Op.LOCAL_DECL, Types.VOID, l, init));
            }
            for (int i = a; i < b; i++) {
                body.add(fn.body.kids[i]);
            }
            BitSet store = stores(a, b);
            for (int l = store.nextSetBit(0); l >= 0; l = store.nextSetBit(l + 1)) {
                body.add(storeTo(mt, l, scratchParam[l], slot));
            }
            s.body = Tree.of(Op.BLOCK, Types.VOID, body.toArray(Tree.NONE));
            return s;
        }

        private Tree zero(int l) {
            int t = fn.locals[l];
            if (Types.isScalar(t)) {
                return Tree.constLike(Simplify.EMPTY, t, 0);
            }
            return Tree.of(Op.SPLAT, t, Tree.constLike(Simplify.EMPTY, Types.scalarOf(src, t), 0));
        }

        private Tree elemPtr(Codec.ModuleTree mt, int l, int param, int slot) {
            int elem = fn.locals[l] == Types.BOOL ? Types.I32 : fn.locals[l];
            int arr = mt.typeId(Types.KIND_ARRAY, AddressSpace.GLOBAL, elem);
            int ptr = mt.typeId(Types.KIND_PTR, AddressSpace.GLOBAL, elem);
            Tree index = Tree.ofImm(Op.LOCAL_GET, Types.I32, slot);
            if (sStride[l] > 1) {
                index = Tree.of(Op.MUL, Types.I32, index, Tree.constant(Op.CONST_INT, Types.I32, sStride[l]));
            }
            if (sOffset[l] > 0) {
                index = Tree.of(Op.ADD, Types.I32, index, Tree.constant(Op.CONST_INT, Types.I32, sOffset[l]));
            }
            return Tree.of(Op.ELEM_PTR, ptr, Tree.ofImm(Op.PARAM, arr, param), index);
        }

        private Tree loadFrom(Codec.ModuleTree mt, int l, int param, int slot) {
            int elem = fn.locals[l] == Types.BOOL ? Types.I32 : fn.locals[l];
            Tree v = Tree.of(Op.LOAD, elem, elemPtr(mt, l, param, slot));
            return fn.locals[l] == Types.BOOL ? Tree.of(Op.CONVERT, Types.BOOL, v) : v;
        }

        private Tree storeTo(Codec.ModuleTree mt, int l, int param, int slot) {
            Tree v = Tree.ofImm(Op.LOCAL_GET, fn.locals[l], l);
            if (fn.locals[l] == Types.BOOL) {
                v = Tree.of(Op.CONVERT, Types.I32, v);
            }
            return Tree.of(Op.STORE, Types.VOID, elemPtr(mt, l, param, slot), v);
        }
    }

    /** {@code gid0 + gsz0 * (gid1 + gsz1 * gid2)} - линейный глобальный id work-item'а. */
    private static Tree linearId() {
        int gid = Builtins.idOf("getGlobalId");
        int gsz = Builtins.idOf("getGlobalSize");
        Tree g0 = id(gid, 0);
        Tree g1 = id(gid, 1);
        Tree g2 = id(gid, 2);
        Tree s0 = id(gsz, 0);
        Tree s1 = id(gsz, 1);
        Tree inner = Tree.of(Op.ADD, Types.I32, g1, Tree.of(Op.MUL, Types.I32, s1, g2));
        return Tree.of(Op.ADD, Types.I32, g0, Tree.of(Op.MUL, Types.I32, s0, inner));
    }

    private static Tree id(int builtin, int dim) {
        return Tree.ofImm(Op.INTRINSIC, Types.I32, builtin, Tree.constant(Op.CONST_INT, Types.I32, dim));
    }

    /** Сдвигает индексы функций после разрезанного ядра. */
    private static Tree remapCalls(Tree t, int kernel, int delta) {
        Tree[] nk = null;
        for (int i = 0; i < t.kids.length; i++) {
            Tree r = remapCalls(t.kids[i], kernel, delta);
            if (r != t.kids[i]) {
                if (nk == null) {
                    nk = t.kids.clone();
                }
                nk[i] = r;
            }
        }
        Tree cur = nk == null ? t : t.withKids(nk);
        if (cur.op == Op.CALL && cur.imm > kernel) {
            return cur.withImm(cur.imm + delta);
        }
        return cur;
    }
}
