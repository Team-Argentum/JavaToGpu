package com.sixik.gpu.ir.optimizer;

import com.sixik.gpu.ir.IrModule;

/**
 * Шов оптимизации ядра (REWORK_ARCHITECTURE.md, разд. 10, точка расширения 2): {@code IrModule -> IrModule}.
 *
 * <p>Контракт: вход - корректный модуль (прошёл {@code Validator}); выход - корректный модуль, наблюдаемо эквивалентный
 * входу (то же отображение «входные буферы -> выходные буферы» для любого запуска, не приводящего к неопределённому
 * поведению), если реализация не оговаривает иного (например, {@link OptimizeOptions#fastMath()}). Реализация не
 * изменяет вход и детерминирована: один и тот же вход и настройки дают побайтово один и тот же модуль.</p>
 */
public interface OptimizerPass {

    /**
     * @return короткое имя прохода для отчётов
     */
    String name();

    /**
     * Преобразует модуль.
     *
     * @param module  корректный модуль
     * @param options настройки
     * @return оптимизированный модуль (может быть тем же объектом, если улучшить нечего)
     */
    IrModule run(IrModule module, OptimizeOptions options);
}
