package com.sixik.gpu.frontend.asm;

import com.sixik.gpu.frontend.common.Diag;

/**
 * Диагностика фронтенда байткода (REWORK_ARCHITECTURE.md, 17.12): значение, а не исключение. Позиция - класс, метод
 * и строка из {@code LineNumberTable}; колонок в байткоде нет.
 *
 * @param code      код {@code GPU####} из {@code diagnostics.def}
 * @param error     {@code true} для ошибки, {@code false} для предупреждения
 * @param message   полный текст ({@link Diag#format})
 * @param className двоичное имя класса ({@code pkg.Outer$Inner})
 * @param method    имя метода либо пустая строка для диагностики класса
 * @param line      строка исходника либо 0, если неизвестна
 */
public record AsmDiagnostic(String code, boolean error, String message, String className, String method, int line) {

    /**
     * Создаёт диагностику по коду таблицы.
     *
     * @param code      код
     * @param className двоичное имя класса
     * @param method    имя метода либо пустая строка
     * @param line      строка либо 0
     * @param args      значения для подстановки в сообщение
     * @return диагностика
     */
    static AsmDiagnostic of(String code, String className, String method, int line, Object... args) {
        return new AsmDiagnostic(code, Diag.entry(code).error(), Diag.format(code, args), className, method, line);
    }

    /** @return {@code pkg.Cls.method:line: GPU####: текст} */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(className);
        if (!method.isEmpty()) {
            sb.append('.').append(method);
        }
        if (line > 0) {
            sb.append(':').append(line);
        }
        return sb.append(": ").append(message).toString();
    }
}
