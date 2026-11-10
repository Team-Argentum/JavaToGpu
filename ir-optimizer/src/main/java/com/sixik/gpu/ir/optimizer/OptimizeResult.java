package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.IrModule;

import java.util.List;

/**
 * Результат работы {@link Optimizer}.
 *
 * @param module         оптимизированный модуль (тот же объект, что на входе, если ничего не изменилось)
 * @param iterations     сколько раз пайплайн прошёл до неподвижной точки
 * @param applied        проходы, которые изменили модуль, по порядку применения (с номером итерации)
 * @param failures       проходы, чей результат отвергнут (исключение либо невалидный IR); пусто в норме
 * @param pressureBefore оценка давления на регистры до оптимизации
 * @param pressureAfter  оценка давления на регистры после оптимизации
 * @param overBudget     индексы ядер, у которых и после оптимизации оценка выше {@link OptimizeOptions#registerBudget()}:
 *                       кандидаты на {@link KernelSplitter}
 * @param functionMap    индекс функции входного модуля -> индекс в {@link #module()} либо {@code -1}, если функция
 *                       удалена как недостижимая (сборка также ставит вызываемые функции раньше вызывающих)
 */
public record OptimizeResult(IrModule module, int iterations, List<String> applied, List<String> failures,
                             RegisterPressure.Report pressureBefore, RegisterPressure.Report pressureAfter,
                             List<Integer> overBudget, int[] functionMap) {

    /**
     * @param inputFunction индекс функции во входном модуле
     * @return индекс той же функции в оптимизированном модуле либо {@code -1}, если она удалена
     */
    public int newIndexOf(int inputFunction) {
        return inputFunction >= 0 && inputFunction < functionMap.length ? functionMap[inputFunction] : -1;
    }

    /**
     * @return {@code true}, если ни один проход не был отвергнут
     */
    public boolean ok() {
        return failures.isEmpty();
    }
}
