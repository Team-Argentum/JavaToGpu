package com.sixik.gpu.frontend.java;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Таблица диагностик {@code GPU####} (REWORK_ARCHITECTURE.md, 3.3): код, серьёзность, сообщение и подсказка берутся
 * из файла данных {@code diagnostics/diagnostics.def}, а не из Java-кода, чтобы таблицу могли использовать будущие
 * фронтенды (в том числе на Rust).
 *
 * <p>Таблица неизменяема и читается один раз при загрузке класса. Обращение к коду, которого нет в таблице, -
 * ошибка программиста ({@link IllegalArgumentException}); тесты проверяют, что все коды из исходников есть в таблице,
 * а для каждого кода таблицы существует негативный тест.</p>
 */
final class Diag {

    /**
     * Запись таблицы.
     *
     * @param code    код, например {@code GPU0101}
     * @param error   {@code true} для ошибки, {@code false} для предупреждения
     * @param message сообщение с местами подстановки {@code {0}}, {@code {1}}...
     * @param hint    подсказка «что делать» либо пустая строка
     */
    record Entry(String code, boolean error, String message, String hint) {
    }

    private static final Map<String, Entry> TABLE = load();

    private Diag() {
    }

    private static Map<String, Entry> load() {
        Map<String, Entry> table = new LinkedHashMap<>();
        try (InputStream in = Diag.class.getResourceAsStream("diagnostics.def")) {
            if (in == null) {
                throw new IllegalStateException("diagnostics.def not found in the frontend-java module resources");
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int no = 0;
            for (String raw : text.split("\\R")) {
                no++;
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] head = line.split("\\s+", 3);
                if (head.length < 3 || !head[0].matches("GPU\\d{4}")
                        || !(head[1].equals("error") || head[1].equals("warning"))) {
                    throw new IllegalStateException("diagnostics.def:" + no + ": expected '<GPU####> <error|warning> <message> | <hint>'");
                }
                String[] parts = head[2].split("\\s\\|\\s?", 2);
                String message = parts[0].strip();
                String hint = parts.length > 1 ? parts[1].strip() : "";
                if (table.put(head[0], new Entry(head[0], head[1].equals("error"), message, hint)) != null) {
                    throw new IllegalStateException("diagnostics.def:" + no + ": duplicate code " + head[0]);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to read diagnostics.def", e);
        }
        return Collections.unmodifiableMap(table);
    }

    /**
     * @return все коды таблицы в порядке файла
     */
    static Set<String> codes() {
        return TABLE.keySet();
    }

    /**
     * @param code код диагностики
     * @return запись таблицы
     * @throws IllegalArgumentException если кода нет в таблице
     */
    static Entry entry(String code) {
        Entry e = TABLE.get(code);
        if (e == null) {
            throw new IllegalArgumentException("diagnostic code " + code + " is missing from diagnostics.def");
        }
        return e;
    }

    /**
     * Формирует текст диагностики: {@code "GPU0101: сообщение. Подсказка: ..."}.
     *
     * @param code код диагностики
     * @param args значения для подстановки вместо {@code {0}}, {@code {1}}...
     * @return итоговая строка
     */
    static String format(String code, Object... args) {
        Entry e = entry(code);
        StringBuilder sb = new StringBuilder(code).append(": ").append(substitute(e.message(), args));
        if (!e.hint().isEmpty()) {
            sb.append(". Hint: ").append(substitute(e.hint(), args));
        }
        return sb.toString();
    }

    private static String substitute(String template, Object[] args) {
        String s = template;
        for (int i = 0; i < args.length; i++) {
            s = s.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return s;
    }
}
