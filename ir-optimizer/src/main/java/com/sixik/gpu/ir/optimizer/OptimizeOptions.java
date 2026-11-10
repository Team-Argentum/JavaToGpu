package com.sixik.gpu.ir.optimizer;

/**
 * Настройки оптимизатора (REWORK_ARCHITECTURE.md, разд. 7: {@code optimize(IrModule, OptimizeOptions)}).
 *
 * <p>Уровни:</p>
 * <ul>
 *   <li>{@code 0} - тождественное преобразование (модуль возвращается как есть);</li>
 *   <li>{@code 1} - свёртка констант, алгебра, мёртвый код, неиспользуемые функции;</li>
 *   <li>{@code 2} - плюс протяжка констант и копий по локалям, удаление мёртвых записей, CSE и вынос инвариантов
 *       из циклов с учётом давления на регистры (по умолчанию);</li>
 *   <li>{@code 3} - как 2, но временные значения заводятся и при исчерпанном запасе регистров, если вычисление дорогое
 *       (деление, трансцендентные функции, чтение памяти).</li>
 * </ul>
 *
 * @param level            уровень оптимизации 0..3
 * @param fastMath         разрешить преобразования, меняющие результат вещественных вычислений в особых случаях
 *                         (NaN, inf, -0) либо округление: {@code x*0 -> 0}, {@code x-x -> 0}, {@code x/c -> x*(1/c)}
 *                         для произвольной {@code c}, слияние в {@code fma}; без согласия пользователя выключено
 * @param contractFma      при {@code fastMath} сливать {@code a*b+c} в {@code fma(a,b,c)}
 * @param registerBudget   целевой бюджет регистров на work-item (32-битных слов); CSE/LICM не заводят временные
 *                         значения, которые поднимут оценку давления выше бюджета
 * @param maxIterations    предел итераций пайплайна до неподвижной точки
 * @param validateEachPass проверять модуль валидатором после каждого прохода и откатывать проход, давший невалидный IR
 */
public record OptimizeOptions(int level, boolean fastMath, boolean contractFma, int registerBudget,
                              int maxIterations, boolean validateEachPass) {

    /** Бюджет по умолчанию: 64 регистра - типичная граница высокой заполняемости на GPU. */
    public static final int DEFAULT_BUDGET = 64;

    /** Настройки по умолчанию: уровень 2, точные преобразования, бюджет {@value #DEFAULT_BUDGET}. */
    public static final OptimizeOptions DEFAULT = new OptimizeOptions(2, false, true, DEFAULT_BUDGET, 8, true);

    /** Оптимизатор выключен. */
    public static final OptimizeOptions NONE = new OptimizeOptions(0, false, true, DEFAULT_BUDGET, 1, true);

    /**
     * @param l новый уровень
     * @return копия с другим уровнем
     */
    public OptimizeOptions withLevel(int l) {
        return new OptimizeOptions(l, fastMath, contractFma, registerBudget, maxIterations, validateEachPass);
    }

    /**
     * @param on разрешить ли быструю математику
     * @return копия с другим флагом {@code fastMath}
     */
    public OptimizeOptions withFastMath(boolean on) {
        return new OptimizeOptions(level, on, contractFma, registerBudget, maxIterations, validateEachPass);
    }

    /**
     * @param budget бюджет регистров на work-item
     * @return копия с другим бюджетом
     */
    public OptimizeOptions withRegisterBudget(int budget) {
        return new OptimizeOptions(level, fastMath, contractFma, budget, maxIterations, validateEachPass);
    }

    /**
     * @param on проверять ли модуль после каждого прохода
     * @return копия с другим флагом проверки
     */
    public OptimizeOptions withValidation(boolean on) {
        return new OptimizeOptions(level, fastMath, contractFma, registerBudget, maxIterations, on);
    }
}
