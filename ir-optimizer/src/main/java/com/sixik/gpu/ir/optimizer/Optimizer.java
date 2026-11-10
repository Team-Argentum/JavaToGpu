package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;
import com.sixik.gpu.ir.Validator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Оптимизатор IR: конвейер проходов {@code IrModule -> IrModule}.
 *
 * <h2>Архитектура</h2>
 * <p>Плоская арена {@link IrModule} неудобна для переписывания, поэтому модуль один раз поднимается в неизменяемые
 * деревья ({@link Codec#lift}), проходы работают над деревьями, а в конце модуль собирается заново через {@code IrBuilder}
 * ({@link Codec#lower}). Узлы деревьев неизменяемы, проход возвращает новое тело, а «ничего не изменилось» - это тот же
 * объект, поэтому неподвижная точка определяется сравнением ссылок.</p>
 *
 * <h2>Проходы (в порядке выполнения за итерацию)</h2>
 * <ol>
 *   <li><b>simplify</b> ({@link Simplify}): свёртка констант, алгебра, strength reduction, упрощение {@code select/convert/
 *       compare}, векторов и struct; {@code if(const)}, пустые ветки, недостижимый код после переходов;</li>
 *   <li><b>local-prop</b> ({@link LocalProp}, уровень 2+): протяжка констант и копий по локалям;</li>
 *   <li><b>dead-locals</b> ({@link DeadCode}): записи в никогда не читаемые локали и неиспользуемые массивы;</li>
 *   <li><b>dead-stores</b> ({@link Liveness}, уровень 2+): записи, перезатираемые до чтения (обратная живость);</li>
 *   <li><b>dead-functions</b>: device-функции, недостижимые из ядер;</li>
 *   <li><b>cse</b> ({@link Cse}) и <b>licm</b> ({@link Licm}) (уровень 2+): общие подвыражения и инварианты циклов
 *       материализуются во временные локали, пока хватает запаса регистров ({@link RegisterPressure}).</li>
 * </ol>
 * <p>Итерации повторяются до неподвижной точки (не более {@link OptimizeOptions#maxIterations()}).</p>
 *
 * <h2>Надёжность</h2>
 * <p>После каждого изменяющего прохода (при {@link OptimizeOptions#validateEachPass()}) модуль собирается и проверяется
 * {@link Validator}; невалидный результат или исключение внутри прохода откатывают этот проход и попадают в
 * {@link OptimizeResult#failures()}, а оптимизация продолжается без него. Оптимизатор не должен ломать ядро - в худшем
 * случае он возвращает исходный модуль.</p>
 *
 * <h2>Что оптимизатор не делает</h2>
 * <p>Разбиение ядра на несколько запусков меняет протокол запуска (дополнительные буферы, несколько вызовов), поэтому оно не
 * входит в {@code IrModule -> IrModule}: см. {@link KernelSplitter}. Оптимизатор лишь сообщает, каким ядрам оно нужно
 * ({@link OptimizeResult#overBudget()}).</p>
 */
public final class Optimizer implements OptimizerPass {

    /** Экземпляр прохода-обёртки для точки расширения {@link OptimizerPass}. */
    public static final Optimizer PASS = new Optimizer();

    private Optimizer() {
    }

    @Override
    public String name() {
        return "ir-optimizer";
    }

    @Override
    public IrModule run(IrModule module, OptimizeOptions options) {
        return optimize(module, options).module();
    }

    /**
     * Оптимизирует модуль.
     *
     * @param input   корректный модуль
     * @param options настройки
     * @return результат: модуль и отчёт
     */
    public static OptimizeResult optimize(IrModule input, OptimizeOptions options) {
        List<String> applied = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        RegisterPressure.Report before = RegisterPressure.analyze(input);
        if (options.level() <= 0) {
            return new OptimizeResult(input, 0, applied, failures, before, before, over(before, options, input), identity(input));
        }
        if (options.validateEachPass()) {
            Validator.Report r = Validator.validate(input);
            if (r.hasErrors()) {
                failures.add("input: module is invalid, optimizer skipped: " + firstError(r));
                return new OptimizeResult(input, 0, applied, failures, before, before, over(before, options, input), identity(input));
            }
        }
        Codec.ModuleTree mt;
        try {
            mt = Codec.lift(input);
        } catch (UnsupportedOperationException e) {
            failures.add("lift: " + e.getMessage());
            return new OptimizeResult(input, 0, applied, failures, before, before, over(before, options, input), identity(input));
        }

        Run run = new Run(mt, options, applied, failures);
        int iterations = run.execute();

        if (applied.isEmpty()) {
            return new OptimizeResult(input, iterations, applied, failures, before, before,
                    over(before, options, input), identity(input));
        }
        IrModule out;
        int[] map = new int[input.functionCount()];
        java.util.Arrays.fill(map, -1);
        try {
            int[] order = Codec.emissionOrder(mt);
            for (int i = 0; i < order.length; i++) {
                map[order[i]] = i;
            }
            out = Codec.lower(mt);
        } catch (RuntimeException e) {
            failures.add("lower: " + e.getMessage());
            return new OptimizeResult(input, iterations, applied, failures, before, before,
                    over(before, options, input), identity(input));
        }
        RegisterPressure.Report after = RegisterPressure.analyze(out);
        return new OptimizeResult(out, iterations, applied, failures, before, after, over(after, options, out), map);
    }

    private static int[] identity(IrModule m) {
        int[] r = new int[m.functionCount()];
        for (int i = 0; i < r.length; i++) {
            r[i] = i;
        }
        return r;
    }

    /**
     * @param r отчёт валидатора
     * @return первая ошибка отчёта (или первая диагностика, если ошибок нет) для сообщений
     */
    static String firstError(Validator.Report r) {
        for (com.sixik.gpu.ir.Diagnostic d : r.diagnostics()) {
            if (d.isError()) {
                return d.toString();
            }
        }
        return r.diagnostics().isEmpty() ? "-" : firstError(r).toString();
    }

    private static List<Integer> over(RegisterPressure.Report report, OptimizeOptions o, IrModule m) {
        List<Integer> list = new ArrayList<>();
        for (RegisterPressure.FunctionPressure f : report.functions()) {
            if (m.fKind(f.function()) == IrModule.KERNEL && f.registers() > o.registerBudget()) {
                list.add(f.function());
            }
        }
        return list;
    }

    // =============================================================================================

    /** Один запуск конвейера над поднятым модулем. */
    private static final class Run {
        final Codec.ModuleTree mt;
        final OptimizeOptions o;
        final List<String> applied;
        final List<String> failures;
        int iteration;
        Effects fx;

        Run(Codec.ModuleTree mt, OptimizeOptions o, List<String> applied, List<String> failures) {
            this.mt = mt;
            this.o = o;
            this.applied = applied;
            this.failures = failures;
        }

        int execute() {
            int max = Math.max(1, o.maxIterations());
            for (iteration = 1; iteration <= max; iteration++) {
                boolean changed = false;
                changed |= step("simplify", () -> perFunction((f, fn) -> {
                    Simplify s = new Simplify(fx, o, mt.source);
                    return s.stmt(fn.body);
                }));
                if (o.level() >= 2) {
                    changed |= step("local-prop", () -> perFunction((f, fn) -> LocalProp.run(fn, mt.source)));
                    changed |= step("simplify", () -> perFunction((f, fn) -> new Simplify(fx, o, mt.source)
                            .stmt(fn.body)));
                }
                changed |= step("dead-locals", () -> perFunction((f, fn) -> DeadCode.removeUnreadLocals(fn, fx)));
                if (o.level() >= 2) {
                    changed |= step("dead-stores", () -> perFunction((f, fn) ->
                            Liveness.eliminateDeadStores(fn, mt.source, fx)));
                }
                changed |= step("simplify", () -> perFunction((f, fn) -> new Simplify(fx, o, mt.source)
                        .stmt(fn.body)));
                changed |= step("dead-functions", () -> DeadCode.markDeadFunctions(mt));
                if (o.level() >= 2) {
                    changed |= step("cse", () -> perFunction((f, fn) -> {
                        Costs.Budget b = budgetFor(f, fn);
                        return Cse.run(fn, mt.source, fx, b);
                    }));
                    changed |= step("licm", () -> perFunction((f, fn) -> {
                        Costs.Budget b = budgetFor(f, fn);
                        return Licm.run(fn, mt.source, fx, b);
                    }));
                }
                if (!changed) {
                    return iteration;
                }
            }
            return max;
        }

        Costs.Budget budgetFor(int f, FnTree fn) {
            int used = RegisterPressure.analyze(fn, f, mt.source, fx).registers();
            return new Costs.Budget(o.registerBudget() - used, o.level() >= 3);
        }

        interface FnBody {
            Tree apply(int f, FnTree fn);
        }

        boolean perFunction(FnBody pass) {
            fx = Effects.analyze(mt);
            boolean changed = false;
            for (int f = 0; f < mt.fns.length; f++) {
                FnTree fn = mt.fns[f];
                if (!fn.alive) {
                    continue;
                }
                Tree nb = pass.apply(f, fn);
                if (nb != fn.body) {
                    fn.body = root(nb);
                    changed = true;
                }
            }
            return changed;
        }

        static Tree root(Tree s) {
            return s.op == Op.BLOCK ? s : Tree.of(Op.BLOCK, Types.VOID, s);
        }

        /** Выполняет проход с откатом при ошибке или невалидном результате. */
        boolean step(String name, BooleanSupplier body) {
            Codec.ModuleTree snap = mt.copy();
            boolean changed;
            try {
                changed = body.getAsBoolean();
                if (changed && o.validateEachPass()) {
                    Validator.Report r = Validator.validate(Codec.lower(mt));
                    if (r.hasErrors()) {
                        rollback(snap);
                        failures.add(name + "#" + iteration + ": produced invalid IR: " + firstError(r));
                        return false;
                    }
                }
            } catch (RuntimeException e) {
                rollback(snap);
                failures.add(name + "#" + iteration + ": " + e);
                return false;
            }
            if (changed) {
                applied.add(name + "#" + iteration);
            }
            return changed;
        }

        void rollback(Codec.ModuleTree snap) {
            System.arraycopy(snap.fns, 0, mt.fns, 0, mt.fns.length);
        }
    }
}
