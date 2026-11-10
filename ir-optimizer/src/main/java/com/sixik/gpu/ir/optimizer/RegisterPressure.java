package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.IrModule;
import com.sixik.gpu.ir.Op;
import com.sixik.gpu.ir.Types;

import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Оценка давления на регистры: сколько 32-битных регистров на work-item нужно функции в самой тяжёлой точке.
 *
 * <p>Модель (оценка сверху до выбора регистров драйвером; единицы - 32-битные слова):</p>
 * <ul>
 *   <li><b>Живые переменные.</b> Обратный анализ живости ({@link Liveness}) даёт на каждой точке вычисления
 *       множество локалей и параметров, значения которых нужны позже. Вес переменной - размер её значения в словах:
 *       {@code BOOL}, до 32 бит - 1; 64 бита - 2; вектор - {@code lanes * вес скаляра} (3 компоненты занимают как 4);
 *       struct - сумма полей; указатель - 2; массив-параметр (указатель плюс длина) - 3. Private-массивы в регистры
 *       не считаются и учитываются отдельно ({@code privateArrayWords}) - это кандидаты на spill в память.</li>
 *   <li><b>Временные значения выражения.</b> Число Сети-Ульмана: операнды вычисляются в порядке убывания потребности,
 *       пока вычисляется очередной, уже готовые держат свои регистры. Константы, параметры и чтение локалей
 *       временных регистров не требуют (локаль уже учтена среди живых).</li>
 * </ul>
 *
 * <p>Давление в точке = вес живых + потребность выражения. Максимум по функции и есть оценка. Модель
 * намеренно консервативна и монотонна: добавление живой переменной не уменьшает оценку, поэтому по ней
 * корректно принимать решения «заводить ли временную локаль» ({@link Cse}, {@link Licm}) и «резать ли ядро»
 * ({@link KernelSplitter}).</p>
 */
public final class RegisterPressure {

    private RegisterPressure() {
    }

    /**
     * Результат оценки одной функции.
     *
     * @param function          индекс функции в модуле
     * @param name              имя функции (может быть {@code null})
     * @param registers         оценка пикового числа 32-битных регистров
     * @param peakLiveWords     вклад живых переменных в пике
     * @param peakTempWords     вклад временных значений выражения в пике
     * @param peakStatement     индекс стейтмента верхнего уровня тела, в котором достигнут пик (либо {@code -1})
     * @param privateArrayWords суммарный размер private-массивов в словах (кандидаты на spill)
     */
    public record FunctionPressure(int function, String name, int registers, int peakLiveWords, int peakTempWords,
                                   int peakStatement, int privateArrayWords) {
    }

    /**
     * Оценка давления для каждой функции модуля.
     *
     * @param functions результаты по функциям в порядке модуля
     */
    public record Report(FunctionPressure[] functions) {

        /**
         * @return максимум оценок по ядрам (kernel)
         */
        public int maxKernelRegisters() {
            int m = 0;
            for (FunctionPressure f : functions) {
                m = Math.max(m, f.registers());
            }
            return m;
        }
    }

    /**
     * Оценивает давление на регистры во всех функциях модуля.
     *
     * @param m корректный модуль
     * @return отчёт
     */
    public static Report analyze(IrModule m) {
        Codec.ModuleTree mt = Codec.lift(m);
        Effects fx = Effects.analyze(mt);
        FunctionPressure[] out = new FunctionPressure[mt.fns.length];
        for (int f = 0; f < out.length; f++) {
            out[f] = analyze(mt.fns[f], f, m, fx);
        }
        return new Report(out);
    }

    /** Оценка одной функции; внутренний вход для проходов. */
    static FunctionPressure analyze(FnTree fn, int index, IrModule src, Effects fx) {
        int[] varWeight = variableWeights(fn, src);
        Map<Tree, Integer> topIndex = new IdentityHashMap<>();
        for (int i = 0; i < fn.body.kids.length; i++) {
            index(fn.body.kids[i], i, topIndex);
        }
        int[] best = {0, 0, 0, -1};
        Liveness.scan(fn, src, fx, (t, across) -> {
            int live = weight(across, varWeight);
            int temp = need(t, src);
            if (live + temp > best[0]) {
                best[0] = live + temp;
                best[1] = live;
                best[2] = temp;
                Integer top = topIndex.get(t);
                best[3] = top == null ? best[3] : top;
            }
        });
        return new FunctionPressure(index, fn.name, best[0], best[1], best[2], best[3], privateWords(fn, src));
    }

    /**
     * Пик давления внутри каждого стейтмента верхнего уровня тела (для выбора точек разрезания).
     *
     * @param fn  функция
     * @param src модуль
     * @param fx  анализ эффектов
     * @return массив длиной в число стейтментов верхнего уровня
     */
    static int[] perTopLevel(FnTree fn, IrModule src, Effects fx) {
        int[] varWeight = variableWeights(fn, src);
        Map<Tree, Integer> topIndex = new IdentityHashMap<>();
        for (int i = 0; i < fn.body.kids.length; i++) {
            index(fn.body.kids[i], i, topIndex);
        }
        int[] peak = new int[fn.body.kids.length];
        Liveness.scan(fn, src, fx, (t, across) -> {
            Integer top = topIndex.get(t);
            if (top != null) {
                peak[top] = Math.max(peak[top], weight(across, varWeight) + need(t, src));
            }
        });
        return peak;
    }

    /** Точка вычисления: удерживаемые переменные и потребность выражения во временных регистрах. */
    record Point(BitSet across, int need) {
    }

    /**
     * Все точки вычисления, сгруппированные по стейтментам верхнего уровня тела.
     *
     * @param fn  функция
     * @param src модуль
     * @param fx  анализ эффектов
     * @return по стейтменту верхнего уровня - список его точек
     */
    static java.util.List<java.util.List<Point>> pointsPerTop(FnTree fn, IrModule src, Effects fx) {
        Map<Tree, Integer> topIndex = new IdentityHashMap<>();
        java.util.List<java.util.List<Point>> out = new java.util.ArrayList<>();
        for (int i = 0; i < fn.body.kids.length; i++) {
            index(fn.body.kids[i], i, topIndex);
            out.add(new java.util.ArrayList<>());
        }
        Liveness.scan(fn, src, fx, (t, across) -> {
            Integer top = topIndex.get(t);
            if (top != null) {
                out.get(top).add(new Point((BitSet) across.clone(), need(t, src)));
            }
        });
        return out;
    }

    private static void index(Tree t, int top, Map<Tree, Integer> out) {
        out.put(t, top);
        for (Tree c : t.kids) {
            index(c, top, out);
        }
    }

    static int weight(BitSet vars, int[] varWeight) {
        int w = 0;
        for (int v = vars.nextSetBit(0); v >= 0 && v < varWeight.length; v = vars.nextSetBit(v + 1)) {
            w += varWeight[v];
        }
        return w;
    }

    /** Вес каждой переменной (локали, затем параметры) в словах; массивы-локали весят 0. */
    static int[] variableWeights(FnTree fn, IrModule src) {
        int[] w = new int[fn.localCount + fn.params.length];
        for (int l = 0; l < fn.localCount; l++) {
            w[l] = Types.kind(src, fn.locals[l]) == Types.KIND_ARRAY ? 0 : words(src, fn.locals[l]);
        }
        for (int p = 0; p < fn.params.length; p++) {
            w[fn.localCount + p] = words(src, fn.params[p]);
        }
        return w;
    }

    /**
     * Размер значения типа в 32-битных словах (для регистров).
     *
     * @param m модуль
     * @param t тип
     * @return вес в словах
     */
    static int words(IrModule m, int t) {
        switch (Types.kind(m, t)) {
            case Types.KIND_SCALAR -> {
                return t == Types.VOID ? 0 : Types.bits(t) == 64 ? 2 : 1;
            }
            case Types.KIND_VEC -> {
                int lanes = Types.lanes(m, t);
                return (lanes == 3 ? 4 : lanes) * words(m, Types.scalarOf(m, t));
            }
            case Types.KIND_PTR -> {
                return 2;
            }
            case Types.KIND_ARRAY -> {
                return 3;
            }
            case Types.KIND_STRUCT -> {
                int sum = 0;
                for (int i = 0; i < m.structFieldCount(t); i++) {
                    sum += words(m, m.structFieldType(t, i));
                }
                return sum;
            }
            default -> {
                return 1;
            }
        }
    }

    private static boolean isLeaf(Tree t) {
        return t.kids.length == 0;
    }

    /**
     * Потребность выражения во временных регистрах (число Сети-Ульмана с учётом размера значений).
     *
     * @param t   выражение либо стейтмент
     * @param src модуль
     * @return число слов сверх уже занятых живыми переменными
     */
    static int need(Tree t, IrModule src) {
        int n = t.kids.length;
        if (n == 0) {
            return 0;
        }
        int[] needs = new int[n];
        int[] weights = new int[n];
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            Tree c = t.kids[i];
            if (c.isStmt()) {
                // вложенные стейтменты считаются отдельными точками
                needs[i] = 0;
                weights[i] = 0;
            } else {
                needs[i] = need(c, src);
                weights[i] = isLeaf(c) ? 0 : words(src, c.type);
            }
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Integer.compare(needs[b], needs[a]));
        int total = 0;
        int held = 0;
        for (int idx : order) {
            total = Math.max(total, needs[idx] + held);
            held += weights[idx];
        }
        int own = t.isStmt() ? 0 : words(src, t.type);
        return Math.max(Math.max(total, held), own);
    }

    /** Размер private-массивов функции в словах. */
    static int privateWords(FnTree fn, IrModule src) {
        int[] total = {0};
        sumPrivate(fn.body, fn, src, total);
        return total[0];
    }

    private static void sumPrivate(Tree t, FnTree fn, IrModule src, int[] total) {
        if (t.op == Op.PRIVATE_ARRAY_DECL && t.kids[0].op == Op.CONST_INT) {
            int elem = Types.elem(src, fn.locals[t.imm]);
            total[0] += (int) t.kids[0].k * Math.max(1, words(src, elem));
        }
        for (Tree c : t.kids) {
            sumPrivate(c, fn, src, total);
        }
    }
}
