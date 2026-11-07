package com.sixik.gpu.ir;

/**
 * Диагностика валидатора и чтения IR: значение, а не исключение (REWORK_ARCHITECTURE.md, разд. 4, правило 3).
 *
 * @param code     стабильный код правила, например {@code "IR-S02"} (список - в REWORK_ARCHITECTURE.md, 5.12)
 * @param severity серьёзность
 * @param function индекс функции либо {@code -1}, если диагностика относится ко всему модулю
 * @param node     id узла либо {@code -1}
 * @param message  краткое описание нарушения (для человека, содержит id узла и опкод)
 */
public record Diagnostic(String code, Severity severity, int function, int node, String message) {

    /** Серьёзность диагностики. */
    public enum Severity {
        /** Нарушение контракта: модуль нельзя исполнять. */
        ERROR,
        /** Подозрительная конструкция; модуль исполнять можно. */
        WARNING
    }

    /**
     * @return {@code true} для {@link Severity#ERROR}
     */
    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(severity == Severity.ERROR ? "error " : "warning ").append(code);
        if (function >= 0) {
            sb.append(" f").append(function);
        }
        if (node >= 0) {
            sb.append(" n").append(node);
        }
        return sb.append(": ").append(message).toString();
    }
}
