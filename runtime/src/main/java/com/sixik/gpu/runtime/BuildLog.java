package com.sixik.gpu.runtime;

import com.sixik.gpu.ir.Diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор build log драйвера в диагностики (REWORK_ARCHITECTURE.md, 3.8: причина отказа и log - в событии и ошибке).
 *
 * <p>Формат log у вендоров разный, но почти везде строка сообщения выглядит как
 * {@code <источник>:<строка>:<колонка>: error|warning: <текст>} (clang-подобные: NVIDIA, AMD, Intel, pocl). Такие
 * строки превращаются в {@code RT-C01} (ошибка) и {@code RT-C02} (предупреждение) с позицией в сгенерированном
 * тексте; в debug-режиме {@code #line} делает позицию строкой Java-исходника. Строки-продолжения (цитата кода,
 * {@code ^}) присоединяются к сообщению. Если ни одной строки не распознано, весь log становится одной ошибкой -
 * ничего не теряется.</p>
 */
final class BuildLog {

    private static final Pattern LINE = Pattern.compile(
            "^(?:.*?:)?(\\d+):(\\d+):\\s*(fatal error|error|warning):\\s*(.*)$");
    private static final int MAX_FALLBACK = 4000;

    private BuildLog() {
    }

    /**
     * @param log      build log драйвера
     * @param function индекс функции-ядра для привязки диагностик
     * @param failed   {@code true}, если сборка завершилась неуспешно (тогда без распознанных ошибок вернётся общая)
     * @return диагностики; для пустого log и успешной сборки - пусто
     */
    static List<Diagnostic> parse(String log, int function, boolean failed) {
        List<Diagnostic> out = new ArrayList<>();
        if (log == null || log.isBlank()) {
            if (failed) {
                out.add(new Diagnostic("RT-C01", Diagnostic.Severity.ERROR, function, -1,
                        "the driver rejected the source, the build log is empty"));
            }
            return out;
        }
        StringBuilder current = null;
        String code = null;
        Diagnostic.Severity severity = null;
        for (String raw : log.split("\\R")) {
            Matcher m = LINE.matcher(raw.strip());
            if (m.matches()) {
                flush(out, current, code, severity, function);
                boolean warning = m.group(3).equals("warning");
                code = warning ? "RT-C02" : "RT-C01";
                severity = warning ? Diagnostic.Severity.WARNING : Diagnostic.Severity.ERROR;
                current = new StringBuilder("line ").append(m.group(1)).append(':').append(m.group(2)).append(": ")
                        .append(m.group(4));
            } else if (current != null && !raw.isBlank()) {
                current.append('\n').append(raw.stripTrailing());
            }
        }
        flush(out, current, code, severity, function);
        if (out.isEmpty() && failed) {
            String text = log.strip();
            if (text.length() > MAX_FALLBACK) {
                text = text.substring(0, MAX_FALLBACK) + "...";
            }
            out.add(new Diagnostic("RT-C01", Diagnostic.Severity.ERROR, function, -1, text));
        }
        return out;
    }

    private static void flush(List<Diagnostic> out, StringBuilder current, String code, Diagnostic.Severity severity,
                              int function) {
        if (current != null) {
            out.add(new Diagnostic(code, severity, function, -1, current.toString()));
        }
    }
}
