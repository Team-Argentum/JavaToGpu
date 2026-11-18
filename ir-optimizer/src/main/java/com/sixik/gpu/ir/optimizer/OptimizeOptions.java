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
 * <p>Преобразования, меняющие вещественный результат ({@code fma}, {@code x/c -> x*(1/c)}, {@code x*0 -> 0}), здесь не
 * настраиваются: их разрешает само ядро маской {@code fp} ({@code @Kernel(compilationProperties = "fp=...")},
 * {@link FpModes}). Глобального переключателя нет, чтобы настройка контекста не могла ослабить точность чужого ядра.</p>
 *
 * @param level            уровень оптимизации 0..3
 * @param registerBudget   целевой бюджет регистров на work-item (32-битных слов); CSE/LICM не заводят временные
 *                         значения, которые поднимут оценку давления выше бюджета
 * @param maxIterations    предел итераций пайплайна до неподвижной точки
 * @param validateEachPass проверять модуль валидатором после каждого прохода и откатывать проход, давший невалидный IR
 * @param fmaF32           у целевого устройства аппаратный FMA для {@code float}; без него слияние в {@code fma} не
 *                         делается (явный {@code fma()} эмулировался бы программно). Рантайм берёт значение из
 *                         {@code CL_DEVICE_SINGLE_FP_CONFIG}
 * @param fmaF64           то же для {@code double} ({@code CL_DEVICE_DOUBLE_FP_CONFIG})
 * @param timeBudget       бюджет времени на модуль ({@code null} - без предела; шаг B7, FRONTEND_ASM_RUNTIME.md, S9-2).
 *                         Превышение не ошибка: запуск прерывается и повторяется на уровне {@code 1}, затем {@code 0}
 *                         (модуль как есть); фактический уровень - {@link OptimizeResult#level()}. Результат каждого
 *                         уровня детерминирован, от времени зависит только выбор уровня
 */
public record OptimizeOptions(int level, int registerBudget, int maxIterations,
                              boolean validateEachPass, boolean fmaF32, boolean fmaF64, java.time.Duration timeBudget) {

    /** Бюджет времени по умолчанию: 2 с на модуль (S9-2). */
    public static final java.time.Duration DEFAULT_TIME_BUDGET = java.time.Duration.ofSeconds(2);

    /** Проверяет поля. */
    public OptimizeOptions {
        if (timeBudget != null && timeBudget.isNegative()) {
            throw new IllegalArgumentException("negative time budget " + timeBudget);
        }
    }

    /**
     * Настройки без бюджета времени (прежний конструктор).
     *
     * @param level            уровень
     * @param registerBudget   бюджет регистров
     * @param maxIterations    предел итераций
     * @param validateEachPass проверка после прохода
     * @param fmaF32           FMA для {@code float}
     * @param fmaF64           FMA для {@code double}
     */
    public OptimizeOptions(int level, int registerBudget, int maxIterations, boolean validateEachPass, boolean fmaF32,
                           boolean fmaF64) {
        this(level, registerBudget, maxIterations, validateEachPass, fmaF32, fmaF64, DEFAULT_TIME_BUDGET);
    }

    /** Бюджет по умолчанию: 64 регистра - типичная граница высокой заполняемости на GPU. */
    public static final int DEFAULT_BUDGET = 64;

    /** Настройки по умолчанию: уровень 2, точные преобразования, бюджет {@value #DEFAULT_BUDGET}. */
    public static final OptimizeOptions DEFAULT = new OptimizeOptions(2, DEFAULT_BUDGET, 8, true, true, true);

    /** Оптимизатор выключен. */
    public static final OptimizeOptions NONE = new OptimizeOptions(0, DEFAULT_BUDGET, 1, true, true, true);

    /**
     * @param l новый уровень
     * @return копия с другим уровнем
     */
    public OptimizeOptions withLevel(int l) {
        return new OptimizeOptions(l, registerBudget, maxIterations, validateEachPass, fmaF32, fmaF64, timeBudget);
    }

    /**
     * @param budget бюджет регистров на work-item
     * @return копия с другим бюджетом
     */
    public OptimizeOptions withRegisterBudget(int budget) {
        return new OptimizeOptions(level, budget, maxIterations, validateEachPass, fmaF32, fmaF64, timeBudget);
    }

    /**
     * @param on проверять ли модуль после каждого прохода
     * @return копия с другим флагом проверки
     */
    public OptimizeOptions withValidation(boolean on) {
        return new OptimizeOptions(level, registerBudget, maxIterations, on, fmaF32, fmaF64, timeBudget);
    }

    /**
     * @param f32 аппаратный FMA для {@code float}
     * @param f64 аппаратный FMA для {@code double}
     * @return копия с другими возможностями устройства
     */
    public OptimizeOptions withHardwareFma(boolean f32, boolean f64) {
        return new OptimizeOptions(level, registerBudget, maxIterations, validateEachPass, f32, f64, timeBudget);
    }

    /**
     * @param budget бюджет времени на модуль либо {@code null} (без предела)
     * @return копия с другим бюджетом времени
     */
    public OptimizeOptions withTimeBudget(java.time.Duration budget) {
        return new OptimizeOptions(level, registerBudget, maxIterations, validateEachPass, fmaF32, fmaF64, budget);
    }
}
