package com.sixik.gpu.ir;

import java.util.List;

/**
 * Результат операции, которая может не удаться: значение либо набор диагностик.
 *
 * <p>Заменяет исключения в ядре (REWORK_ARCHITECTURE.md, разд. 4): при ошибке {@code value == null}
 * и в {@code diagnostics} есть хотя бы одна ошибка.</p>
 *
 * @param value       результат либо {@code null}
 * @param diagnostics диагностики (могут быть и при успехе - предупреждения)
 * @param <T>         тип результата
 */
public record Result<T>(T value, List<Diagnostic> diagnostics) {

    /**
     * Создаёт успешный результат без диагностик.
     *
     * @param value значение
     * @param <T>   тип
     * @return успешный результат
     */
    public static <T> Result<T> ok(T value) {
        return new Result<>(value, List.of());
    }

    /**
     * Создаёт неуспешный результат.
     *
     * @param diagnostics причины
     * @param <T>         тип
     * @return результат без значения
     */
    public static <T> Result<T> failed(List<Diagnostic> diagnostics) {
        return new Result<>(null, List.copyOf(diagnostics));
    }

    /**
     * @return {@code true}, если значение получено
     */
    public boolean isOk() {
        return value != null;
    }
}
